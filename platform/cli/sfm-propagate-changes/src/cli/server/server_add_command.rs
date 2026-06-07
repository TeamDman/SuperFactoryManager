pub(super) fn invoke(glob: &str) -> eyre::Result<()> {
    super::server_command::add_servers(glob)
}
