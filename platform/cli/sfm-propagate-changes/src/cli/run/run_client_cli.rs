use crate::cancellation::CancellationToken;
use crate::jar_build::ClientTitleScreen;
use crate::jar_build::RunKind;
use crate::jar_build::RunOptions;
use facet::Facet;
use figue as args;
use std::path::PathBuf;

/// Arguments for launching the Forge client userdev run config.
#[derive(Facet, Debug, Clone)]
#[expect(
    clippy::struct_excessive_bools,
    reason = "Each bool maps directly to a client launch flag."
)]
pub struct RunClientArgs {
    /// Build and launch options.
    #[facet(flatten)]
    pub options: ProjectionClientOptions,
    /// SFM checkout containing platform/cli/sfm to build with its existing lockfile. Also enables the worker in an interactive client.
    #[facet(default, args::named)]
    pub control_cli_source_root: Option<PathBuf>,
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

/// Native launch selection. A projection is never interpreted as a Git branch.
#[derive(Facet, Debug, Clone)]
pub struct ProjectionClientOptions {
    /// Exact projection key from projections.json.
    #[facet(args::named)]
    pub projection: String,
    /// Repository containing the core templates; defaults to the current Git root.
    #[facet(default, args::named)]
    pub repo_root: Option<PathBuf>,
    /// Explicit compatible JDK; otherwise resolve the selected projection's SDK.
    #[facet(default, args::named)]
    pub java_home: Option<PathBuf>,
    /// Explain native build cache decisions.
    #[facet(default, args::named)]
    pub explain_rebuild: bool,
    /// Prepare the native launch without starting Minecraft.
    #[facet(default, args::named)]
    pub dry_run: bool,
    /// Wait for this projection's build cache lock.
    #[facet(default, args::named)]
    pub wait_for_build_lock: bool,
}

impl ProjectionClientOptions {
    #[expect(
        clippy::needless_pass_by_value,
        reason = "Owned CLI launch boundary matches the run adapters and retains cancellation for the synchronous launch lifetime"
    )]
    fn invoke(
        self,
        kind: RunKind,
        run_options: RunOptions,
        cancellation: CancellationToken,
    ) -> eyre::Result<()> {
        let invocation_dir = std::env::current_dir()?;
        let root = match self.repo_root {
            Some(path) => path,
            None => gix::discover(&invocation_dir)?
                .workdir()
                .ok_or_else(|| eyre::eyre!("client launch requires a repository worktree"))?
                .to_owned(),
        };
        let collected = crate::source_projection::catalog_owned_project::collect_catalog_project(
            &root,
            &invocation_dir,
            &self.projection,
        )?;
        let profile = crate::source_projection::native_project_target::NATIVE_DEPENDENCY_PROFILE;
        let checked = match collected.environment() {
            crate::source_projection::projection_catalog::ProjectionEnvironment::Dev => {
                crate::source_projection::native_project_target::validate_collected_native_profile(
                    &collected, profile,
                )?;
                collected.prepare_development()?
            }
            crate::source_projection::projection_catalog::ProjectionEnvironment::Release => {
                collected.check_current()?
            }
        };
        crate::jar_build::invoke_projection_client(
            checked,
            profile,
            self.java_home,
            self.explain_rebuild,
            self.wait_for_build_lock,
            self.dry_run,
            kind,
            &run_options,
            &cancellation,
        )
    }
}

impl RunClientArgs {
    /// # Errors
    ///
    /// Returns an error if planning, building, or launching fails.
    pub fn invoke(self, cancellation_token: CancellationToken) -> eyre::Result<()> {
        let Self {
            options,
            control_cli_source_root,
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
        let client_hotswap_port = resolve_hotswap_port(hotswap, hotswap_port)?;
        if smoke && puppet.is_some() {
            eyre::bail!("--smoke and --puppet cannot be used together.");
        }
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
                    game_puppet_filter: Some(puppet.to_owned()),
                    game_puppet_game_test: super::normalize_game_puppet_game_test(game_test)?,
                    game_puppet_keep_open: crate::jar_build::GamePuppetKeepOpen::from_cli(
                        keep_open,
                    )?,
                    game_puppet_viewport_selection: "declared".to_owned(),
                    game_puppet_mute: mute,
                    preview_width,
                    preview_height,
                    client_hotswap_port,
                    control_cli_source_root,
                    ..RunOptions::default()
                },
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
                || control_cli_source_root.is_some()
            {
                eyre::bail!("--smoke cannot be combined with interactive or puppet-only flags.");
            }
            return options.invoke(
                RunKind::ClientSmoke,
                RunOptions {
                    client_solo: solo,
                    ..RunOptions::default()
                },
                cancellation_token,
            );
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
        options.invoke(
            RunKind::Client,
            RunOptions {
                client_title_screen: title_screen,
                control_cli_source_root,
                client_solo: solo,
                client_hotswap_port,
                ..RunOptions::default()
            },
            cancellation_token,
        )
    }
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
