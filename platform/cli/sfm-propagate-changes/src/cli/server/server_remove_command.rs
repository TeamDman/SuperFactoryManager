pub(super) fn invoke(glob: String) -> eyre::Result<()> {
    super::server_command::remove_servers(&glob)
}
