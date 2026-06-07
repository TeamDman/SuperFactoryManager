pub(super) fn invoke(mc: Option<&str>) -> eyre::Result<()> {
    super::server_command::launch_servers(mc)
}
