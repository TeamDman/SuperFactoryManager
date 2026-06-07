use std::path::Path;

pub(super) fn invoke(path: &Path) -> eyre::Result<()> {
    super::client_command::set_launcher(path)
}
