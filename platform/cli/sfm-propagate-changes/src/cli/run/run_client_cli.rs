use crate::cancellation::CancellationToken;
use crate::cli::jar::JarBuildOptionsArgs;
use crate::jar_build::BuildMode;
use crate::jar_build::ClientTitleScreen;
use crate::jar_build::RunCommand;
use crate::jar_build::RunKind;
use crate::jar_build::RunOptions;
use facet::Facet;
use figue as args;

/// Arguments for launching the Forge client userdev run config.
#[derive(Facet, Debug, Clone)]
#[expect(
    clippy::struct_excessive_bools,
    reason = "Each bool maps directly to a client launch flag."
)]
pub struct RunClientArgs {
    /// Build and launch options.
    #[facet(flatten)]
    pub options: JarBuildOptionsArgs,
    /// Open the SFM text editor when the client first reaches the title screen.
    #[facet(default, args::named)]
    pub text_editor: bool,
    /// Open the input diagnostics screen when the client first reaches the title screen.
    #[facet(default, args::named)]
    pub input_diag: bool,
    /// Open a supported SFM dev screen when the client first reaches the title screen.
    #[facet(default, args::named)]
    pub title_screen: Option<ClientTitleScreen>,
    /// Launch SFM without dependency mod jars declared by the schema v3 lockfile.
    #[facet(default, args::named)]
    pub solo: bool,
    /// Open a JDWP port so `sfm-propagate-changes run hotswap` can redefine classes.
    #[facet(default, args::named)]
    pub hotswap: bool,
    /// JDWP port used by `--hotswap`.
    #[facet(default, args::named)]
    pub hotswap_port: Option<u16>,
    /// Exit when the title screen opens after confirming a healthy client boot.
    #[facet(default, args::named)]
    pub smoke: bool,
    /// Run selected SFM game puppets. Supports unqualified names, `*`, `?`, and comma-separated selectors.
    #[facet(default, args::named)]
    pub puppet: Option<String>,
    /// Exact SFM `GameTest` id supplied to a parameterized puppet. Accepts `sfm:<name>` or `<name>`.
    #[facet(default, args::named)]
    pub game_test: Option<String>,
    /// Window width used for puppet native screenshot captures.
    #[facet(default, args::named)]
    pub width: Option<u16>,
    /// Window height used for puppet native screenshot captures.
    #[facet(default, args::named)]
    pub height: Option<u16>,
    /// Mute Minecraft audio during a puppet run by default. Use `--no-mute` to hear it.
    #[facet(default = true, args::named)]
    pub mute: bool,
    /// Hold the final puppet world. Bare keeps it open forever; values accept humantime durations.
    #[facet(default, args::named)]
    pub keep_open: Option<Option<String>>,
}

impl RunClientArgs {
    /// # Errors
    ///
    /// Returns an error if planning, building, or launching fails.
    pub fn invoke(self, cancellation_token: CancellationToken) -> eyre::Result<()> {
        let Self {
            options,
            text_editor,
            input_diag,
            title_screen,
            solo,
            hotswap,
            hotswap_port,
            smoke,
            puppet,
            game_test,
            width,
            height,
            mute,
            keep_open,
        } = self;
        if smoke && puppet.is_some() {
            eyre::bail!("--smoke and --puppet cannot be used together.");
        }
        if let Some(puppet) = puppet {
            if text_editor
                || input_diag
                || title_screen.is_some()
                || solo
                || hotswap
                || hotswap_port.is_some()
            {
                eyre::bail!("--puppet cannot be combined with interactive client flags.");
            }
            return super::invoke_game_puppet(
                options,
                &puppet,
                game_test,
                width,
                height,
                "declared",
                mute,
                keep_open,
                cancellation_token,
            );
        }
        if smoke {
            if text_editor
                || input_diag
                || title_screen.is_some()
                || hotswap
                || hotswap_port.is_some()
                || width.is_some()
                || height.is_some()
                || keep_open.is_some()
                || game_test.is_some()
            {
                eyre::bail!("--smoke cannot be combined with interactive or puppet-only flags.");
            }
            return RunCommand::with_run_options(
                options.into_options(BuildMode::Build)?,
                RunKind::ClientSmoke,
                RunOptions {
                    client_solo: solo,
                    ..RunOptions::default()
                },
                cancellation_token,
            )
            .invoke();
        }
        if width.is_some()
            || height.is_some()
            || keep_open.is_some()
            || game_test.is_some()
            || !mute
        {
            eyre::bail!(
                "--width, --height, --mute, --keep-open, and --game-test require --puppet."
            );
        }
        let title_screen = resolve_title_screen(title_screen, text_editor, input_diag)?;
        let hotswap_port = hotswap.then_some(hotswap_port.unwrap_or(5005));
        RunCommand::with_run_options(
            options.into_options(BuildMode::Build)?,
            RunKind::Client,
            RunOptions {
                client_title_screen: title_screen,
                client_solo: solo,
                client_hotswap_port: hotswap_port,
                ..RunOptions::default()
            },
            cancellation_token,
        )
        .invoke()
    }
}

fn resolve_title_screen(
    title_screen: Option<ClientTitleScreen>,
    text_editor: bool,
    input_diag: bool,
) -> eyre::Result<Option<ClientTitleScreen>> {
    let mut selected = Vec::new();
    if let Some(title_screen) = title_screen {
        selected.push(("--title-screen", title_screen));
    }
    if text_editor {
        selected.push(("--text-editor", ClientTitleScreen::TextEditor));
    }
    if input_diag {
        selected.push(("--input-diag", ClientTitleScreen::InputDiag));
    }
    if selected.len() > 1 {
        let flags = selected
            .iter()
            .map(|(flag, _)| *flag)
            .collect::<Vec<_>>()
            .join(", ");
        eyre::bail!("Only one title-screen dev screen can be selected; got {flags}.");
    }
    Ok(selected.into_iter().next().map(|(_, screen)| screen))
}
