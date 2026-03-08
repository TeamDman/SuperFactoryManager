use crate::paths::APP_HOME;

pub(super) fn invoke() -> eyre::Result<()> {
    println!("{}", APP_HOME.0.display());
    Ok(())
}
