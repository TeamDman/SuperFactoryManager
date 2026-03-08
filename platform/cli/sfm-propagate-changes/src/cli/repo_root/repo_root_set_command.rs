use crate::paths::APP_HOME;
use eyre::Context;
use std::path::PathBuf;
use tracing::info;

pub(super) fn invoke(path: PathBuf) -> eyre::Result<()> {
    let canonical = dunce::canonicalize(&path)
        .wrap_err_with(|| format!("Failed to canonicalize path: {}", path.display()))?;
    let repo_root_file = APP_HOME.file_path(super::repo_root_command::REPO_ROOT_FILE);

    APP_HOME.ensure_dir()?;

    std::fs::write(&repo_root_file, canonical.display().to_string())
        .wrap_err("Failed to write repo root file")?;

    info!("Set repo root to: {}", canonical.display());
    Ok(())
}
