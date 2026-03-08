use crate::cli::curseforge::CurseforgeProjectDefaultCommand;

pub(super) fn invoke(command: CurseforgeProjectDefaultCommand) -> eyre::Result<()> {
    command.invoke()
}

pub(super) fn invoke_set(project: u64) -> eyre::Result<()> {
    super::curseforge_command::invoke_project_default_set(project)
}

pub(super) fn invoke_show() -> eyre::Result<()> {
    super::curseforge_command::invoke_project_default_show()
}
