use crate::paths::APP_HOME;

pub(super) fn invoke() -> eyre::Result<()> {
    let path = &APP_HOME.0;
    if !path.exists() {
        std::fs::create_dir_all(path)?;
    }
    open::that(path)?;
    Ok(())
}
