use std::path::PathBuf;

pub(super) fn invoke(latest: bool, path: Option<PathBuf>) -> eyre::Result<()> {
    super::gradle_command::invoke_gradle_logs_tldr(latest, path)
}
