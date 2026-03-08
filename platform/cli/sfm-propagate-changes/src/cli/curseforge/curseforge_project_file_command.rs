use crate::cli::curseforge::CurseforgeProjectFileCommand;

pub(super) fn invoke(command: CurseforgeProjectFileCommand) -> eyre::Result<()> {
    command.invoke()
}

pub(super) fn invoke_list(
    project: Option<u64>,
    api_key: Option<String>,
    token: Option<String>,
    op_secret: Option<String>,
) -> eyre::Result<()> {
    super::curseforge_command::invoke_project_file_list(project, api_key, token, op_secret)
}
