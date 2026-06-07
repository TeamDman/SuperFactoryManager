pub(super) fn invoke(glob: &str) -> eyre::Result<()> {
    super::client_command::remove_clients(glob)
}
