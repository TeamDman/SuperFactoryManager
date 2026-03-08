use crate::cli::curseforge::CurseforgeMinecraftCommand;

pub(super) fn invoke(command: CurseforgeMinecraftCommand) -> eyre::Result<()> {
    command.invoke()
}
