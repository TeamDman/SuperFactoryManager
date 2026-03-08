use crate::paths::APP_HOME;
use eyre::Context;
use tracing::info;

pub(super) fn invoke() -> eyre::Result<()> {
    let repo_root_file = APP_HOME.file_path(super::repo_root_command::REPO_ROOT_FILE);

    if repo_root_file.exists() {
        std::fs::remove_file(&repo_root_file).wrap_err("Failed to remove repo root file")?;
        info!("Repo root unset");
    } else {
        info!("Repo root was not set");
    }

    Ok(())
}
