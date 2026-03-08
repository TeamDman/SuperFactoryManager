pub(super) fn invoke(
    project: Option<u64>,
    api_key: Option<String>,
    token: Option<String>,
    op_secret: Option<String>,
    safety_age: Option<String>,
) -> eyre::Result<()> {
    super::curseforge_command::invoke_release_amend(project, api_key, token, op_secret, safety_age)
}
