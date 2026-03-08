use crate::cli::gradle::GradleRunCommand;

pub(super) fn invoke(command: GradleRunCommand) -> eyre::Result<()> {
    command.invoke()
}
