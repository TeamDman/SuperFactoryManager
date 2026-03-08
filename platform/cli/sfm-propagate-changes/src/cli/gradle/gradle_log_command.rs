use crate::cli::gradle::GradleLogsCommand;

pub(super) fn invoke(command: GradleLogsCommand) -> eyre::Result<()> {
    command.invoke()
}
