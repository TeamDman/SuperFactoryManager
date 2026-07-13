use eyre::Context;
use std::fs;
use std::fs::File;
use std::io;
use std::path::Component;
use std::path::Path;
use zip::ZipArchive;

const MAX_ENTRIES: usize = 250_000;
const MAX_ENTRY_BYTES: u64 = 512 * 1024 * 1024;
const MAX_TOTAL_BYTES: u64 = 4 * 1024 * 1024 * 1024;

/// Extracts a source ZIP through a temporary sibling directory and publishes the completed tree.
///
/// # Errors
///
/// Returns an error for unsafe paths, links and special entries, excessive declared sizes, invalid
/// ZIP data, or filesystem failures. An invalid archive never replaces an existing output tree.
pub fn extract_zip_atomically(input: &Path, output: &Path) -> eyre::Result<()> {
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
        fs::remove_dir_all(output)
            .wrap_err_with(|| format!("Failed to replace source tree {}", output.display()))?;
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
