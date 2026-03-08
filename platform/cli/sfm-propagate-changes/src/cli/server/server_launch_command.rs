pub(super) fn invoke(mc: Option<String>) -> eyre::Result<()> {
    super::server_command::launch_servers(mc.as_deref())
}
