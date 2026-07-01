use super::SourceFiletreePath;
use super::SourceFiletreeStatePath;
use super::SourceJarPath;
use super::SourceOutputCacheRoot;
use super::hash::ContentHash;
use super::hash::ContentHashAlgorithm;
use eyre::Context;
use std::fs;
use std::fs::File;
use std::io::Cursor;
use std::path::Path;
use std::path::PathBuf;
use zip::ZipArchive;

pub(super) fn materialize(
    cache_root: &SourceOutputCacheRoot,
    source_jar: &SourceJarPath,
) -> eyre::Result<SourceFiletreePath> {
    let output = SourceFiletreePath::for_source_jar(source_jar)?;
    let source_hash = ContentHash::from_path(source_jar.as_path(), ContentHashAlgorithm::Blake3)?;
    let state_path = SourceFiletreeStatePath::for_source_jar(source_jar)?;
    let current_state = fs::read_to_string(state_path.as_path()).unwrap_or_default();
    if output.as_path().is_dir() && current_state.trim() == source_hash.to_string() {
        return Ok(output);
    }

    reset_cache_directory(cache_root, output.as_path())?;
    extract_zip_to_tree(source_jar.as_path(), output.as_path())?;
    fs::write(state_path.as_path(), format!("{source_hash}\n"))
        .wrap_err_with(|| format!("Failed to write {}", state_path.as_path().display()))?;
    Ok(output)
}

fn reset_cache_directory(cache_root: &SourceOutputCacheRoot, path: &Path) -> eyre::Result<()> {
    if !path.starts_with(cache_root.as_path()) {
        eyre::bail!(
            "Refusing to reset source output outside cache root: {} (cache root {})",
            path.display(),
            cache_root.as_path().display()
        );
    }
    if path.exists() {
        fs::remove_dir_all(path)
            .wrap_err_with(|| format!("Failed to remove {}", path.display()))?;
    }
    fs::create_dir_all(path).wrap_err_with(|| format!("Failed to create {}", path.display()))?;
    Ok(())
}

fn extract_zip_to_tree(input: &Path, output: &Path) -> eyre::Result<()> {
    let bytes = fs::read(input).wrap_err_with(|| format!("Failed to read {}", input.display()))?;
    let mut archive = ZipArchive::new(Cursor::new(bytes))
        .wrap_err_with(|| format!("Failed to open {}", input.display()))?;
    for index in 0..archive.len() {
        let mut entry = archive
            .by_index(index)
            .wrap_err_with(|| format!("Failed to read {} entry #{index}", input.display()))?;
        let name = entry.name().replace('\\', "/");
        if name.is_empty() || name.ends_with('/') {
            continue;
        }
        let output_path = zip_name_to_path(output, &name);
        if let Some(parent) = output_path.parent() {
            fs::create_dir_all(parent)?;
        }
        let mut file = File::create(&output_path)
            .wrap_err_with(|| format!("Failed to create {}", output_path.display()))?;
        std::io::copy(&mut entry, &mut file).wrap_err_with(|| {
            format!(
                "Failed to extract {name} from {} to {}",
                input.display(),
                output_path.display()
            )
        })?;
    }
    Ok(())
}

fn zip_name_to_path(root: &Path, name: &str) -> PathBuf {
    name.split('/')
        .filter(|part| !part.is_empty())
        .fold(root.to_path_buf(), |path, part| path.join(part))
}
