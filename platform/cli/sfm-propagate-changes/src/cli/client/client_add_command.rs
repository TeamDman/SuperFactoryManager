pub(super) fn invoke(glob: String) -> eyre::Result<()> {
    super::client_command::add_clients(&glob)
}
