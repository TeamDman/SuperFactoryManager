use crate::cli::curseforge::CurseforgeMinecraftVersionCommand;

pub(super) fn invoke(command: CurseforgeMinecraftVersionCommand) -> eyre::Result<()> {
    command.invoke()
}

pub(super) fn invoke_list(
    mc: Option<String>,
    token: Option<String>,
    op_secret: Option<String>,
) -> eyre::Result<()> {
    super::curseforge_command::invoke_minecraft_version_list(mc, token, op_secret)
}
