pub(super) fn invoke(
    mc: Option<&str>,
    project: Option<u64>,
    api_key: Option<String>,
    token: Option<String>,
    op_secret: Option<String>,
    safety_age: Option<String>,
    dry_run: bool,
) -> eyre::Result<()> {
    super::curseforge_command::invoke_release_amend(
        mc, project, api_key, token, op_secret, safety_age, dry_run,
    )
}
