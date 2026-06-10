pub(super) fn invoke(
    mc: Option<&str>,
    project: Option<String>,
    token: Option<String>,
    op_secret: Option<String>,
) -> eyre::Result<()> {
    super::modrinth_command::invoke_amend(mc, project, token, op_secret)
}
