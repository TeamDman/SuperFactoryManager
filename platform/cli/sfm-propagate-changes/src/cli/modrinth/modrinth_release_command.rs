use crate::cli::modrinth::ModrinthReleaseCommand;

pub(super) fn invoke(command: ModrinthReleaseCommand) -> eyre::Result<()> {
    command.invoke()
}
