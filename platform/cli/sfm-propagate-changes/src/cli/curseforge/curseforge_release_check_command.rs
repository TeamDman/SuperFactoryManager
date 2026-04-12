pub(super) fn invoke(
    mc: Option<String>,
    project: Option<u64>,
    api_key: Option<String>,
    token: Option<String>,
    op_secret: Option<String>,
) -> eyre::Result<()> {
    super::curseforge_command::invoke_release_check(mc, project, api_key, token, op_secret)
}
