pub(super) fn invoke(glob: String) -> eyre::Result<()> {
    super::server_command::add_servers(&glob)
}
