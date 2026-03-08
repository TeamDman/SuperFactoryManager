pub(super) fn invoke(
    project: Option<u64>,
    token: Option<String>,
    op_secret: Option<String>,
    dry_run: bool,
) -> eyre::Result<()> {
    super::curseforge_command::invoke_release_now(project, token, op_secret, dry_run)
}
