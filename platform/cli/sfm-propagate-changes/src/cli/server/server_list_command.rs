pub(super) fn invoke(glob: Option<String>) -> eyre::Result<()> {
    let glob = glob.unwrap_or_else(|| "*".to_string());
    super::server_command::list_servers(&glob)
}
