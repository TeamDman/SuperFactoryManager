pub(super) fn invoke(glob: &str) -> eyre::Result<()> {
    super::server_command::remove_servers(glob)
}
