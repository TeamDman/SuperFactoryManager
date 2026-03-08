pub(super) fn invoke(glob: String) -> eyre::Result<()> {
    super::client_command::remove_clients(&glob)
}
