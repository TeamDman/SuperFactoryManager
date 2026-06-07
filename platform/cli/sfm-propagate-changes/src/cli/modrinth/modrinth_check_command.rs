pub(super) fn invoke(mc: Option<&str>, project: Option<String>) -> eyre::Result<()> {
    super::modrinth_command::invoke_check(mc, project)
}
