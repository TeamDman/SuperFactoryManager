use eyre::Context;
use sha2::Digest;
use sha2::Sha256;
use std::collections::HashSet;
use std::fs;
use std::fs::File;
use std::io;
use std::io::Read;
use std::path::Component;
use std::path::Path;
use zip::ZipArchive;

const MAX_ENTRIES: usize = 250_000;
const MAX_ENTRY_BYTES: u64 = 512 * 1024 * 1024;
const MAX_TOTAL_BYTES: u64 = 4 * 1024 * 1024 * 1024;

/// Stable source payload identity: entry names, lengths and decompressed bytes,
/// independent of ZIP timestamps, ordering and compression. The full archive
/// remains separately checksummed in the producer's immutable cache receipt.
pub(crate) fn archive_payload_sha256(input: &Path) -> eyre::Result<String> {
    let file = File::open(input)?;
    let mut archive = ZipArchive::new(file)?;
    eyre::ensure!(
        archive.len() <= MAX_ENTRIES,
        "source archive exceeds entry limit"
    );
    let mut entries = Vec::new();
    let mut seen = HashSet::new();
    let mut total_bytes = 0_u64;
    for index in 0..archive.len() {
        let mut entry = archive.by_index(index)?;
        let relative = safe_entry_path(entry.name())?;
        reject_special_entry(entry.name(), entry.unix_mode())?;
        if entry.is_dir() {
            continue;
        }
        let name = relative.to_string_lossy().replace('\\', "/");
        eyre::ensure!(
            seen.insert(name.clone()),
            "duplicate source archive entry {name}"
        );
        let size = entry.size();
        eyre::ensure!(
            size <= MAX_ENTRY_BYTES,
            "source archive entry exceeds byte limit"
        );
        total_bytes = total_bytes
            .checked_add(size)
            .ok_or_else(|| eyre::eyre!("source archive size overflow"))?;
        eyre::ensure!(
            total_bytes <= MAX_TOTAL_BYTES,
            "source archive exceeds byte limit"
        );
        let mut digest = Sha256::new();
        let mut buffer = [0_u8; 16 * 1024];
        let mut read_bytes = 0_u64;
        loop {
            let count = entry.read(&mut buffer)?;
            if count == 0 {
                break;
            }
            read_bytes += count as u64;
            digest.update(&buffer[..count]);
        }
        eyre::ensure!(read_bytes == size, "source archive entry length changed");
        entries.push((name, size, digest.finalize()));
    }
    entries.sort_by(|left, right| left.0.cmp(&right.0));
    let mut digest = Sha256::new();
    digest.update(b"sfm:source-archive-payload@1\0");
    for (name, size, entry_digest) in entries {
        digest.update((name.len() as u64).to_be_bytes());
        digest.update(name.as_bytes());
        digest.update(size.to_be_bytes());
        digest.update(entry_digest);
    }
    Ok(format!("sha256:{:x}", digest.finalize()))
}

pub(crate) fn matches_catalog_source_identity(input: &Path, expected: &str) -> eyre::Result<bool> {
    if let Some(payload) = expected.strip_prefix("payload-") {
        Ok(archive_payload_sha256(input)? == payload)
    } else {
        Ok(crate::source_projection::provenance::sha256(&fs::read(input)?) == expected)
    }
}

/// Extracts a source ZIP through a temporary sibling directory and publishes the completed tree.
///
/// # Errors
///
/// Returns an error for unsafe paths, links and special entries, excessive declared sizes, invalid
/// ZIP data, or filesystem failures. An invalid archive never replaces an existing output tree.
pub fn extract_zip_atomically(input: &Path, output: &Path) -> eyre::Result<()> {
    if output.exists() {
        if extracted_tree_matches_archive(input, output)? {
            return Ok(());
        }
        eyre::bail!(
            "Existing source tree differs from its authenticated archive; refusing to replace a possible live reader: {}",
            output.display()
        );
    }
    let parent = output
        .parent()
        .ok_or_else(|| eyre::eyre!("Source tree has no parent: {}", output.display()))?;
    fs::create_dir_all(parent)
        .wrap_err_with(|| format!("Failed to create source cache parent {}", parent.display()))?;
    let temporary = tempfile::Builder::new()
        .prefix(".sfm-source-extract-")
        .tempdir_in(parent)
        .wrap_err_with(|| {
            format!(
                "Failed to create temporary directory in {}",
                parent.display()
            )
        })?;
    let tree = temporary.path().join("tree");
    fs::create_dir(&tree).wrap_err_with(|| format!("Failed to create {}", tree.display()))?;
    extract_zip_to_tree(input, &tree)?;

    if output.exists() {
        if extracted_tree_matches_archive(input, output)? {
            return Ok(());
        }
        eyre::bail!(
            "Source tree appeared during publication: {}",
            output.display()
        );
    }
    fs::rename(&tree, output).wrap_err_with(|| {
        format!(
            "Failed to publish extracted source tree {} to {}",
            tree.display(),
            output.display()
        )
    })?;
    Ok(())
}

/// Check the complete extracted tree against an already authenticated source ZIP.
/// This includes unexpected files and links, not only the configured Java roots.
pub(crate) fn extracted_tree_matches_archive(input: &Path, tree: &Path) -> eyre::Result<bool> {
    if !fs::symlink_metadata(tree).is_ok_and(|metadata| metadata.file_type().is_dir()) {
        return Ok(false);
    }
    let file = File::open(input).wrap_err_with(|| format!("Failed to open {}", input.display()))?;
    let mut archive = ZipArchive::new(file)
        .wrap_err_with(|| format!("Failed to read ZIP archive {}", input.display()))?;
    if archive.len() > MAX_ENTRIES {
        eyre::bail!(
            "Source archive contains too many entries: {}",
            input.display()
        );
    }
    let mut expected = HashSet::new();
    let mut total_bytes = 0_u64;
    for index in 0..archive.len() {
        let mut entry = archive.by_index(index)?;
        let relative = safe_entry_path(entry.name()).wrap_err_with(|| {
            format!(
                "Unsafe source archive entry {:?} in {}",
                entry.name(),
                input.display()
            )
        })?;
        reject_special_entry(entry.name(), entry.unix_mode())?;
        if entry.size() > MAX_ENTRY_BYTES {
            return Ok(false);
        }
        total_bytes = total_bytes
            .checked_add(entry.size())
            .ok_or_else(|| eyre::eyre!("Source archive declared size overflow"))?;
        if total_bytes > MAX_TOTAL_BYTES {
            return Ok(false);
        }
        for parent in relative.ancestors().skip(1) {
            if parent.as_os_str().is_empty() {
                break;
            }
            expected.insert(parent.to_path_buf());
        }
        if !expected.insert(relative.to_path_buf()) && !entry.is_dir() {
            return Ok(false);
        }
        let path = tree.join(relative);
        let metadata = match fs::symlink_metadata(&path) {
            Ok(metadata) => metadata,
            Err(error) if error.kind() == io::ErrorKind::NotFound => return Ok(false),
            Err(error) => return Err(error.into()),
        };
        if entry.is_dir() {
            if !metadata.file_type().is_dir() {
                return Ok(false);
            }
        } else {
            if !metadata.file_type().is_file() || metadata.len() != entry.size() {
                return Ok(false);
            }
            let mut extracted = File::open(&path)?;
            let mut left = [0_u8; 16 * 1024];
            let mut right = [0_u8; 16 * 1024];
            loop {
                let count = entry.read(&mut left)?;
                if count == 0 {
                    break;
                }
                extracted.read_exact(&mut right[..count])?;
                if left[..count] != right[..count] {
                    return Ok(false);
                }
            }
        }
    }
    let mut pending = vec![tree.to_path_buf()];
    while let Some(directory) = pending.pop() {
        for item in fs::read_dir(&directory)? {
            let item = item?;
            let path = item.path();
            let relative = path.strip_prefix(tree)?;
            if !expected.contains(relative) {
                return Ok(false);
            }
            let kind = item.file_type()?;
            if kind.is_dir() {
                pending.push(path);
            } else if !kind.is_file() {
                return Ok(false);
            }
        }
    }
    Ok(true)
}

fn extract_zip_to_tree(input: &Path, output: &Path) -> eyre::Result<()> {
    let file = File::open(input).wrap_err_with(|| format!("Failed to open {}", input.display()))?;
    let mut archive = ZipArchive::new(file)
        .wrap_err_with(|| format!("Failed to read ZIP archive {}", input.display()))?;
    if archive.len() > MAX_ENTRIES {
        eyre::bail!(
            "Source archive {} contains {} entries; limit is {MAX_ENTRIES}",
            input.display(),
            archive.len()
        );
    }

    let mut total_bytes = 0_u64;
    let mut seen = HashSet::new();
    for index in 0..archive.len() {
        let mut entry = archive
            .by_index(index)
            .wrap_err_with(|| format!("Failed to read {} entry #{index}", input.display()))?;
        let relative = safe_entry_path(entry.name()).wrap_err_with(|| {
            format!(
                "Unsafe source archive entry {:?} in {}",
                entry.name(),
                input.display()
            )
        })?;
        reject_special_entry(entry.name(), entry.unix_mode())?;
        if !seen.insert(relative.to_path_buf()) {
            eyre::bail!("Duplicate source archive entry {:?}", entry.name());
        }
        if entry.size() > MAX_ENTRY_BYTES {
            eyre::bail!(
                "Source archive entry {:?} declares {} bytes; per-entry limit is {MAX_ENTRY_BYTES}",
                entry.name(),
                entry.size()
            );
        }
        total_bytes = total_bytes
            .checked_add(entry.size())
            .ok_or_else(|| eyre::eyre!("Source archive declared size overflow"))?;
        if total_bytes > MAX_TOTAL_BYTES {
            eyre::bail!(
                "Source archive {} declares more than {MAX_TOTAL_BYTES} extracted bytes",
                input.display()
            );
        }

        let destination = output.join(relative);
        if entry.is_dir() {
            fs::create_dir_all(&destination)
                .wrap_err_with(|| format!("Failed to create {}", destination.display()))?;
            continue;
        }
        if let Some(parent) = destination.parent() {
            fs::create_dir_all(parent)
                .wrap_err_with(|| format!("Failed to create {}", parent.display()))?;
        }
        let mut destination_file = File::create(&destination)
            .wrap_err_with(|| format!("Failed to create {}", destination.display()))?;
        let copied = io::copy(&mut entry, &mut destination_file).wrap_err_with(|| {
            format!(
                "Failed to extract {:?} from {}",
                entry.name(),
                input.display()
            )
        })?;
        if copied != entry.size() {
            eyre::bail!(
                "Source archive entry {:?} declared {} bytes but extracted {copied}",
                entry.name(),
                entry.size()
            );
        }
    }
    Ok(())
}

fn safe_entry_path(name: &str) -> eyre::Result<&Path> {
    let has_drive_prefix = name.as_bytes().get(1) == Some(&b':');
    if name.is_empty() || name.contains('\\') || name.starts_with('/') || has_drive_prefix {
        eyre::bail!("entry path is empty, absolute, or uses backslashes");
    }
    let path = Path::new(name);
    for component in path.components() {
        if !matches!(component, Component::Normal(_)) {
            eyre::bail!("entry path contains a non-normal component");
        }
    }
    Ok(path)
}

fn reject_special_entry(name: &str, unix_mode: Option<u32>) -> eyre::Result<()> {
    let Some(mode) = unix_mode else {
        return Ok(());
    };
    let file_type = mode & 0o170_000;
    if file_type != 0 && file_type != 0o100_000 && file_type != 0o040_000 {
        eyre::bail!("Source archive entry {name:?} is a link or special file");
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    #[test]
    fn payload_identity_ignores_container_order_but_detects_source_changes() {
        let temp = tempfile::tempdir().expect("temporary directory");
        let first = temp.path().join("first.jar");
        let reordered = temp.path().join("reordered.jar");
        let changed = temp.path().join("changed.jar");
        write_zip(
            &first,
            &[("src/A.java", b"class A {}"), ("src/B.java", b"class B {}")],
        );
        write_zip(
            &reordered,
            &[("src/B.java", b"class B {}"), ("src/A.java", b"class A {}")],
        );
        write_zip(
            &changed,
            &[("src/A.java", b"class A {}"), ("src/B.java", b"class C {}")],
        );
        assert_ne!(fs::read(&first).unwrap(), fs::read(&reordered).unwrap());
        let expected = archive_payload_sha256(&first).unwrap();
        assert_eq!(archive_payload_sha256(&reordered).unwrap(), expected);
        assert_ne!(archive_payload_sha256(&changed).unwrap(), expected);
        assert!(
            matches_catalog_source_identity(&reordered, &format!("payload-{expected}")).unwrap()
        );
    }
    use super::*;
    use std::io::Write;
    use zip::ZipWriter;
    use zip::write::SimpleFileOptions;

    #[test]
    fn extracts_regular_files_and_directories() {
        let temp = tempfile::tempdir().expect("temporary directory");
        let archive = temp.path().join("sources.jar");
        write_zip(
            &archive,
            &[("src/main/java/Example.java", b"class Example {}")],
        );
        let output = temp.path().join("tree");

        extract_zip_atomically(&archive, &output).expect("safe extraction");

        assert_eq!(
            fs::read(output.join("src/main/java/Example.java")).expect("source file"),
            b"class Example {}"
        );
    }

    #[test]
    fn rejects_traversal_absolute_drive_and_backslash_entries_without_replacing_output() {
        for name in [
            "../escape.java",
            "/absolute.java",
            "C:/drive.java",
            "..\\escape.java",
        ] {
            let temp = tempfile::tempdir().expect("temporary directory");
            let archive = temp.path().join("malicious.jar");
            write_zip(&archive, &[(name, b"malicious")]);
            let output = temp.path().join("tree");
            fs::create_dir(&output).expect("existing output");
            fs::write(output.join("sentinel"), b"existing").expect("sentinel");

            let error = extract_zip_atomically(&archive, &output).expect_err("unsafe entry");

            assert!(error.to_string().contains("Unsafe source archive entry"));
            assert_eq!(fs::read(output.join("sentinel")).unwrap(), b"existing");
            assert!(!temp.path().join("escape.java").exists());
        }
    }

    #[test]
    fn rejects_symbolic_links() {
        let temp = tempfile::tempdir().expect("temporary directory");
        let archive = temp.path().join("link.jar");
        let file = File::create(&archive).expect("ZIP file");
        let mut writer = ZipWriter::new(file);
        writer
            .add_symlink("linked.java", "target.java", SimpleFileOptions::default())
            .expect("link entry");
        writer.finish().expect("finish ZIP");

        let error = extract_zip_atomically(&archive, &temp.path().join("tree"))
            .expect_err("symbolic link should be rejected");

        assert!(error.to_string().contains("link or special file"));
    }

    #[test]
    fn repeated_extraction_keeps_the_existing_tree_and_rejects_changes() {
        let temp = tempfile::tempdir().expect("temporary directory");
        let archive = temp.path().join("sources.jar");
        write_zip(&archive, &[("src/Example.java", b"class Example {}")]);
        let output = temp.path().join("tree");
        extract_zip_atomically(&archive, &output).unwrap();
        assert!(extracted_tree_matches_archive(&archive, &output).unwrap());
        extract_zip_atomically(&archive, &output).expect("identical tree is reused");

        let source = output.join("src/Example.java");
        fs::write(&source, b"class Example { changed }").unwrap();
        assert!(!extracted_tree_matches_archive(&archive, &output).unwrap());
        let error =
            extract_zip_atomically(&archive, &output).expect_err("changed tree is retained");
        assert!(error.to_string().contains("refusing to replace"));
        assert_eq!(fs::read(&source).unwrap(), b"class Example { changed }");
    }

    #[test]
    fn unexpected_extracted_file_is_not_an_authenticated_tree() {
        let temp = tempfile::tempdir().expect("temporary directory");
        let archive = temp.path().join("sources.jar");
        write_zip(&archive, &[("src/Example.java", b"class Example {}")]);
        let output = temp.path().join("tree");
        extract_zip_atomically(&archive, &output).unwrap();
        fs::write(output.join("unlisted.java"), b"class Unlisted {}").unwrap();
        assert!(!extracted_tree_matches_archive(&archive, &output).unwrap());
    }

    #[test]
    fn existing_tree_is_preserved_while_a_reader_holds_a_file() {
        let temp = tempfile::tempdir().expect("temporary directory");
        let archive = temp.path().join("sources.jar");
        write_zip(&archive, &[("src/Example.java", b"class Example {}")]);
        let output = temp.path().join("tree");
        extract_zip_atomically(&archive, &output).unwrap();
        let source = output.join("src/Example.java");
        let held = File::open(&source).unwrap();
        extract_zip_atomically(&archive, &output).expect("same tree must not be replaced");
        assert_eq!(held.metadata().unwrap().len(), 16);
        assert_eq!(fs::read(&source).unwrap(), b"class Example {}");
    }

    #[test]
    fn duplicate_archive_entry_is_not_published() {
        let temp = tempfile::tempdir().expect("temporary directory");
        let archive = temp.path().join("sources.jar");
        write_zip(
            &archive,
            &[
                ("src/Example.java", b"first"),
                ("src//Example.java", b"second"),
            ],
        );
        let output = temp.path().join("tree");
        let error = extract_zip_atomically(&archive, &output).expect_err("duplicate entry");
        assert!(error.to_string().contains("Duplicate source archive entry"));
        assert!(!output.exists());
    }

    fn write_zip(path: &Path, entries: &[(&str, &[u8])]) {
        let file = File::create(path).expect("ZIP file");
        let mut writer = ZipWriter::new(file);
        for (name, contents) in entries {
            writer
                .start_file(*name, SimpleFileOptions::default())
                .expect("ZIP entry");
            writer.write_all(contents).expect("ZIP contents");
        }
        writer.finish().expect("finish ZIP");
    }
}
