pub(super) fn invoke(
    project: Option<String>,
    token: Option<String>,
    op_secret: Option<String>,
) -> eyre::Result<()> {
    super::modrinth_command::invoke_amend(project, token, op_secret)
}
