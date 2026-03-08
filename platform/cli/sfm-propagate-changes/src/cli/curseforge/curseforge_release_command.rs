use crate::cli::curseforge::CurseforgeReleaseCommand;

pub(super) fn invoke(command: CurseforgeReleaseCommand) -> eyre::Result<()> {
    command.invoke()
}
