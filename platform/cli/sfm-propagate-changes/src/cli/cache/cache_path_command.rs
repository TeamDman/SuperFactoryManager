use crate::paths::CACHE_DIR;

pub(super) fn invoke() -> eyre::Result<()> {
    println!("{}", CACHE_DIR.0.display());
    Ok(())
}
