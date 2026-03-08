use crate::cli::curseforge::CurseforgeProjectCommand;

pub(super) fn invoke(command: CurseforgeProjectCommand) -> eyre::Result<()> {
    command.invoke()
}
