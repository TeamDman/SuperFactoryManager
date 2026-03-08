use crate::paths::CACHE_DIR;

pub(super) fn invoke() -> eyre::Result<()> {
    let path = &CACHE_DIR.0;
    if !path.exists() {
        std::fs::create_dir_all(path)?;
    }
    open::that(path)?;
    Ok(())
}
