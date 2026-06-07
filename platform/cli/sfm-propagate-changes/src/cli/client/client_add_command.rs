pub(super) fn invoke(glob: &str) -> eyre::Result<()> {
    super::client_command::add_clients(glob)
}
