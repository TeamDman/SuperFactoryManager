use std::path::PathBuf;

pub(super) fn invoke(path: PathBuf) -> eyre::Result<()> {
    super::client_command::set_launcher(&path)
}
