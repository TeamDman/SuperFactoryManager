use super::SourceFiletreePath;
use super::SourceFiletreeStatePath;
use super::SourceJarPath;
use super::SourceOutputCacheRoot;
use super::hash::ContentHash;
use super::hash::ContentHashAlgorithm;
use eyre::Context;
use std::fs;
use std::io;

pub(super) fn materialize(
    cache_root: &SourceOutputCacheRoot,
    source_jar: &SourceJarPath,
) -> eyre::Result<SourceFiletreePath> {
    let output = SourceFiletreePath::for_source_jar(source_jar)?;
    if !output.as_path().starts_with(cache_root.as_path()) {
        eyre::bail!(
            "Refusing source output outside cache root: {} (cache root {})",
            output.as_path().display(),
            cache_root.as_path().display()
        );
    }
    let source_hash = ContentHash::from_path(source_jar.as_path(), ContentHashAlgorithm::Blake3)?;
    let state_path = SourceFiletreeStatePath::for_source_jar(source_jar)?;
    match fs::symlink_metadata(output.as_path()) {
        Ok(_) => {
            eyre::ensure!(
                crate::source_archive::extracted_tree_matches_archive(
                    source_jar.as_path(),
                    output.as_path()
                )?,
                "Existing source filetree differs from its source JAR; refusing to replace a possible live reader: {}",
                output.as_path().display()
            );
            // The marker is advisory. A complete tree published before a crash
            // remains reusable only after checking every extracted entry.
            return Ok(output);
        }
        Err(error) if error.kind() == io::ErrorKind::NotFound => {}
        Err(error) => return Err(error.into()),
    }

    crate::source_archive::extract_zip_atomically(source_jar.as_path(), output.as_path())?;
    eyre::ensure!(
        crate::source_archive::extracted_tree_matches_archive(
            source_jar.as_path(),
            output.as_path()
        )?,
        "new source filetree failed sealed archive validation"
    );
    fs::write(state_path.as_path(), format!("{source_hash}\n"))
        .wrap_err_with(|| format!("Failed to write {}", state_path.as_path().display()))?;
    Ok(output)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write as _;
    use zip::ZipWriter;
    use zip::write::SimpleFileOptions;

    fn write_jar(path: &std::path::Path, entries: &[(&str, &[u8])]) -> eyre::Result<()> {
        let mut archive = ZipWriter::new(fs::File::create(path)?);
        for (name, bytes) in entries {
            archive.start_file(*name, SimpleFileOptions::default())?;
            archive.write_all(bytes)?;
        }
        archive.finish()?;
        Ok(())
    }

    #[test]
    fn validates_existing_tree_even_with_matching_input_marker() -> eyre::Result<()> {
        let temporary = tempfile::tempdir()?;
        let root = temporary.path().join("cache");
        fs::create_dir(&root)?;
        let jar = SourceJarPath::new(root.join("mapped.jar"));
        write_jar(jar.as_path(), &[("src/Example.java", b"class Example {}")])?;
        let cache = SourceOutputCacheRoot::new(root);
        let output = materialize(&cache, &jar)?;
        let source = output.as_path().join("src/Example.java");
        let original = fs::read(&source)?;
        assert_eq!(materialize(&cache, &jar)?.as_path(), output.as_path());

        // A crash after publishing the tree but before its advisory marker
        // must not require deleting a valid tree.
        let marker = SourceFiletreeStatePath::for_source_jar(&jar)?;
        fs::remove_file(marker.as_path())?;
        assert_eq!(materialize(&cache, &jar)?.as_path(), output.as_path());

        fs::write(&source, b"tampered")?;
        assert!(materialize(&cache, &jar).is_err());
        assert_eq!(fs::read(&source)?, b"tampered");
        assert_ne!(fs::read(&source)?, original);
        Ok(())
    }

    #[test]
    fn rejects_archive_traversal_without_publishing_or_escaping() -> eyre::Result<()> {
        let temporary = tempfile::tempdir()?;
        let root = temporary.path().join("cache");
        fs::create_dir(&root)?;
        let jar = SourceJarPath::new(root.join("mapped.jar"));
        write_jar(jar.as_path(), &[("../escape.java", b"unsafe")])?;
        let cache = SourceOutputCacheRoot::new(root.clone());
        assert!(materialize(&cache, &jar).is_err());
        assert!(!root.join("escape.java").exists());
        assert!(!SourceFiletreePath::for_source_jar(&jar)?.as_path().exists());
        Ok(())
    }

    #[test]
    fn changed_source_jar_does_not_replace_existing_tree() -> eyre::Result<()> {
        let temporary = tempfile::tempdir()?;
        let root = temporary.path().join("cache");
        fs::create_dir(&root)?;
        let jar = SourceJarPath::new(root.join("mapped.jar"));
        write_jar(jar.as_path(), &[("src/Example.java", b"original")])?;
        let cache = SourceOutputCacheRoot::new(root);
        let output = materialize(&cache, &jar)?;
        write_jar(jar.as_path(), &[("src/Example.java", b"changed")])?;
        assert!(materialize(&cache, &jar).is_err());
        assert_eq!(
            fs::read(output.as_path().join("src/Example.java"))?,
            b"original"
        );
        Ok(())
    }
}
