use crate::cancellation::CancellationToken;
use crate::cli::InvocationHomes;
use crate::cli::jar::JarBuildOptionsArgs;
use crate::jar_build::BuildMode;
use crate::jar_build::ClientTitleScreen;
use crate::jar_build::GamePuppetKeepOpen;
use crate::jar_build::RunCommand;
use crate::jar_build::RunKind;
use crate::jar_build::RunOptions;
use crate::paths::InvocationDirectory;
use facet::Facet;
use figue as args;
use std::fs::canonicalize;
use std::path::Path;
use std::path::PathBuf;

/// Arguments for launching the selected projection's native client runtime.
#[derive(Facet, Debug, Clone)]
#[expect(
    clippy::struct_excessive_bools,
    reason = "Each bool maps directly to a client launch flag."
)]
pub struct RunClientArgs {
    /// Build and launch options.
    #[facet(flatten)]
    pub options: ProjectionClientOptions,
    /// Persistent runtime slot; defaults to the lowest free positive slot for this projection.
    #[facet(default, args::named)]
    pub runtime_slot: Option<u32>,
    /// Explicitly share the selected slot's gameDir; options/log/config writes may contend. Minecraft retains its native world lock.
    #[facet(default, args::named)]
    pub share_runtime: bool,
    /// SFM checkout containing platform/cli/sfm to build with its existing lockfile. Also enables the worker in an interactive client.
    #[facet(default, args::named)]
    pub control_cli_source_root: Option<PathBuf>,
    /// Open a prepared Explorer workspace at the title screen, or after a puppet yields in-world.
    #[facet(default, args::named)]
    pub inspection_workspace: Option<PathBuf>,
    /// Existing Python executable for optional on-demand JAR class evidence.
    #[facet(default, args::named)]
    pub inspection_python: Option<PathBuf>,
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
    /// Diagnostic handshake directory; wait for an armed trace and release before spawning the smoke JVM.
    #[facet(default, args::named)]
    pub diagnostic_launch_gate: Option<PathBuf>,
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

type ProjectionClientOptions = JarBuildOptionsArgs;

impl ProjectionClientOptions {
    fn invoke(
        self,
        kind: RunKind,
        run_options: RunOptions,
        cancellation: CancellationToken,
        invocation_dir: &Path,
        homes: &InvocationHomes,
    ) -> eyre::Result<()> {
        let app_home = homes.app_home();
        RunCommand::with_run_options(
            self.into_options(BuildMode::Build, app_home, invocation_dir)?
                .with_cache_home(homes.available_cache_home()),
            kind,
            run_options,
            cancellation,
        )
        .invoke()
    }
}

impl RunClientArgs {
    /// # Errors
    ///
    /// Returns an error if planning, building, or launching fails.
    pub fn invoke(
        mut self,
        cancellation_token: CancellationToken,
        invocation_dir: &Path,
        homes: &InvocationHomes,
    ) -> eyre::Result<()> {
        if self.smoke && self.puppet.is_none() {
            self.validate_smoke_options()?;
        }
        self.validate_launch_handoff()?;
        self.resolve_launch_paths(invocation_dir)?;
        let Self {
            options,
            runtime_slot,
            share_runtime,
            control_cli_source_root,
            inspection_workspace,
            inspection_python,
            text_editor,
            input_diag,
            title_screen,
            solo,
            hotswap,
            hotswap_port,
            smoke,
            diagnostic_launch_gate,
            puppet,
            game_test,
            width,
            height,
            mute,
            keep_open,
        } = self;
        let client_hotswap_port = resolve_hotswap_port(hotswap, hotswap_port)?;
        if let Some(puppet) = puppet {
            if text_editor || input_diag || title_screen.is_some() || solo {
                eyre::bail!("--puppet cannot be combined with interactive client flags.");
            }
            let puppet = puppet.trim();
            eyre::ensure!(!puppet.is_empty(), "puppet selector must not be empty");
            let (preview_width, preview_height) =
                super::run_game_test_preview_cli::validate_viewport(width, height)?;
            return options.invoke(
                RunKind::GameTestPreview,
                RunOptions {
                    runtime_slot,
                    share_runtime,
                    game_puppet_filter: Some(puppet.to_owned()),
                    game_puppet_game_test: super::normalize_game_puppet_game_test(game_test)?,
                    game_puppet_keep_open: GamePuppetKeepOpen::from_cli(keep_open)?,
                    game_puppet_viewport_selection: "declared".to_owned(),
                    game_puppet_mute: mute,
                    preview_width,
                    preview_height,
                    client_hotswap_port,
                    control_cli_source_root,
                    inspection_workspace,
                    inspection_python,
                    ..RunOptions::default()
                },
                cancellation_token,
                invocation_dir,
                homes,
            );
        }
        if smoke {
            return options.invoke(
                RunKind::ClientSmoke,
                RunOptions {
                    runtime_slot,
                    share_runtime,
                    client_solo: solo,
                    diagnostic_launch_gate,
                    ..RunOptions::default()
                },
                cancellation_token,
                invocation_dir,
                homes,
            );
        }
        let title_screen = resolve_title_screen(title_screen, text_editor, input_diag)?;
        options.invoke(
            RunKind::Client,
            RunOptions {
                runtime_slot,
                share_runtime,
                client_title_screen: title_screen,
                inspection_workspace,
                inspection_python,
                control_cli_source_root,
                client_solo: solo,
                client_hotswap_port,
                ..RunOptions::default()
            },
            cancellation_token,
            invocation_dir,
            homes,
        )
    }

    fn resolve_launch_paths(&mut self, invocation_dir: &Path) -> eyre::Result<()> {
        let directory = InvocationDirectory::new(invocation_dir)?;
        self.inspection_workspace = resolve_inspection_file(
            &directory,
            self.inspection_workspace.as_deref(),
            "--inspection-workspace",
        )?;
        self.inspection_python = resolve_inspection_file(
            &directory,
            self.inspection_python.as_deref(),
            "--inspection-python",
        )?;
        self.control_cli_source_root = self
            .control_cli_source_root
            .as_deref()
            .map(|path| directory.resolve(path))
            .transpose()?;
        Ok(())
    }

    fn validate_launch_handoff(&self) -> eyre::Result<()> {
        eyre::ensure!(
            !self.share_runtime || self.runtime_slot.is_some(),
            "--share-runtime requires explicit --runtime-slot"
        );
        validate_inspection_launch(
            self.inspection_workspace.as_deref(),
            self.inspection_python.as_deref(),
            self.puppet.as_deref(),
            self.smoke,
            self.text_editor || self.input_diag || self.title_screen.is_some(),
            self.keep_open.as_ref(),
        )?;
        if self.smoke && self.puppet.is_some() {
            eyre::bail!("--smoke and --puppet cannot be used together.");
        }
        eyre::ensure!(
            self.diagnostic_launch_gate.is_none() || (self.smoke && !self.options.dry_run),
            "--diagnostic-launch-gate requires --smoke without --dry-run"
        );
        if self.puppet.is_none()
            && (self.width.is_some()
                || self.height.is_some()
                || self.keep_open.is_some()
                || self.game_test.is_some()
                || !self.mute)
        {
            eyre::bail!(
                "--width, --height, --mute, --keep-open, and --game-test require --puppet."
            );
        }
        Ok(())
    }

    fn validate_smoke_options(&self) -> eyre::Result<()> {
        eyre::ensure!(
            !self.share_runtime || self.runtime_slot.is_some(),
            "--share-runtime requires explicit --runtime-slot"
        );
        resolve_hotswap_port(self.hotswap, self.hotswap_port)?;
        eyre::ensure!(
            !self.text_editor
                && !self.input_diag
                && self.title_screen.is_none()
                && !self.hotswap
                && self.hotswap_port.is_none()
                && self.width.is_none()
                && self.height.is_none()
                && self.keep_open.is_none()
                && self.game_test.is_none()
                && self.control_cli_source_root.is_none()
                && self.inspection_workspace.is_none()
                && self.inspection_python.is_none(),
            "--smoke cannot be combined with interactive or puppet-only flags."
        );
        Ok(())
    }
}

fn resolve_inspection_file(
    directory: &InvocationDirectory<'_>,
    path: Option<&Path>,
    option: &str,
) -> eyre::Result<Option<PathBuf>> {
    let Some(path) = path else {
        return Ok(None);
    };
    let path = canonicalize(directory.resolve(path)?)?;
    eyre::ensure!(path.is_file(), "{option} requires an existing file");
    Ok(Some(path))
}

fn validate_inspection_launch(
    workspace: Option<&Path>,
    python: Option<&Path>,
    puppet: Option<&str>,
    smoke: bool,
    another_screen: bool,
    keep_open: Option<&Option<String>>,
) -> eyre::Result<()> {
    if workspace.is_some() && (smoke || another_screen) {
        eyre::bail!(
            "--inspection-workspace cannot be combined with --smoke or another launch screen"
        );
    }
    if python.is_some() && puppet.is_some() && workspace.is_none() {
        eyre::bail!("--inspection-python with --puppet requires --inspection-workspace");
    }
    if workspace.is_some() && puppet.is_some() {
        eyre::ensure!(
            keep_open.is_some_and(Option::is_none),
            "--inspection-workspace with --puppet requires bare --keep-open so handoff stays open"
        );
    }
    Ok(())
}

fn resolve_hotswap_port(enabled: bool, port: Option<u16>) -> eyre::Result<Option<u16>> {
    if !enabled && port.is_some() {
        eyre::bail!("--hotswap-port requires --hotswap.");
    }
    if port == Some(0) {
        eyre::bail!("--hotswap-port must be greater than zero.");
    }
    Ok(enabled.then_some(port.unwrap_or(5005)))
}

#[cfg(test)]
mod inspection_launch_tests {
    use super::resolve_inspection_file;
    use super::validate_inspection_launch;
    use crate::paths::InvocationDirectory;
    use std::fs;
    use std::path::Path;

    #[test]
    fn inspection_files_are_resolved_at_the_invocation_directory() -> eyre::Result<()> {
        let invocation = tempfile::tempdir()?;
        let directory = InvocationDirectory::new(invocation.path())?;
        for (path, option) in [
            ("workspace.json", "--inspection-workspace"),
            ("python", "--inspection-python"),
        ] {
            let file = invocation.path().join(path);
            fs::write(&file, b"fixture")?;
            assert_eq!(
                resolve_inspection_file(&directory, Some(Path::new(path)), option)?,
                Some(file.canonicalize()?)
            );
        }
        Ok(())
    }

    #[test]
    fn prepared_workspace_opens_only_in_supported_client_handoffs() {
        assert!(
            validate_inspection_launch(
                Some(std::path::Path::new("fixture")),
                None,
                None,
                false,
                false,
                None
            )
            .is_ok()
        );
        assert!(
            validate_inspection_launch(
                Some(std::path::Path::new("fixture")),
                Some(std::path::Path::new("fixture")),
                Some("fixture"),
                false,
                false,
                Some(&None)
            )
            .is_ok()
        );
        assert!(
            validate_inspection_launch(
                Some(std::path::Path::new("fixture")),
                None,
                Some("fixture"),
                false,
                false,
                None
            )
            .is_err()
        );
        assert!(
            validate_inspection_launch(
                Some(std::path::Path::new("fixture")),
                None,
                Some("fixture"),
                false,
                false,
                Some(&Some("5m".into()))
            )
            .is_err()
        );
        assert!(
            validate_inspection_launch(
                Some(std::path::Path::new("fixture")),
                None,
                None,
                true,
                false,
                None
            )
            .is_err()
        );
        assert!(
            validate_inspection_launch(
                Some(std::path::Path::new("fixture")),
                None,
                None,
                false,
                true,
                None
            )
            .is_err()
        );
        assert!(
            validate_inspection_launch(
                None,
                Some(std::path::Path::new("fixture")),
                Some("fixture"),
                false,
                false,
                None
            )
            .is_err()
        );
        assert!(
            validate_inspection_launch(None, None, Some("fixture"), false, false, None).is_ok()
        );
    }
}

#[cfg(test)]
mod hotswap_tests {
    use super::resolve_hotswap_port;

    #[test]
    fn hotswap_port_is_explicit_and_never_silently_ignored() {
        assert_eq!(resolve_hotswap_port(false, None).unwrap(), None);
        assert_eq!(resolve_hotswap_port(true, None).unwrap(), Some(5005));
        assert_eq!(resolve_hotswap_port(true, Some(5006)).unwrap(), Some(5006));
        assert!(resolve_hotswap_port(false, Some(5006)).is_err());
        assert!(resolve_hotswap_port(true, Some(0)).is_err());
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
