use crate::paths::CACHE_DIR;
use tracing::info;

pub(super) fn invoke() -> eyre::Result<()> {
    let path = &CACHE_DIR.0;
    if path.exists() {
        std::fs::remove_dir_all(path)?;
        info!("Cleaned cache directory: {}", path.display());
    } else {
        info!("Cache directory does not exist: {}", path.display());
    }
    Ok(())
}
