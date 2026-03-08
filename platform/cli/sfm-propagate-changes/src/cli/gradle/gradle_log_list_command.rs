pub(super) fn invoke(latest: bool) -> eyre::Result<()> {
    super::gradle_command::invoke_gradle_logs_list(latest)
}
