pub(super) fn invoke(
    mc: Option<&str>,
    project: Option<String>,
    token: Option<String>,
    op_secret: Option<String>,
    dry_run: bool,
) -> eyre::Result<()> {
    super::modrinth_command::invoke_amend(mc, project, token, op_secret, dry_run)
}
