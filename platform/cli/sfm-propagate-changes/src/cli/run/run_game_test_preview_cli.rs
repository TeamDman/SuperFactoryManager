use crate::cancellation::CancellationToken;
use crate::cli::jar::JarBuildOptionsArgs;
use crate::jar_build::BuildMode;
use crate::jar_build::GamePuppetKeepOpen;
use crate::jar_build::RunCommand;
use crate::jar_build::RunKind;
use crate::jar_build::RunOptions;
use facet::Facet;
use figue as args;

/// Arguments for launching the Forge client and running selected SFM game puppet definitions.
#[derive(Facet, Debug, Clone)]
pub struct RunGameTestPreviewArgs {
    /// Build and launch options.
    #[facet(flatten)]
    pub options: JarBuildOptionsArgs,

    /// Puppet selector. Supports unqualified names, `*`, `?`, and comma-separated selectors.
    #[facet(args::named)]
    pub puppet: String,

    /// Exact SFM `GameTest` id supplied to a parameterized puppet. Accepts `sfm:<name>` or `<name>`.
    #[facet(default, args::named)]
    pub game_test: Option<String>,

    /// Window width used for native screenshot captures.
    #[facet(default, args::named)]
    pub width: Option<u16>,

    /// Window height used for native screenshot captures.
    #[facet(default, args::named)]
    pub height: Option<u16>,

    /// Viewport variants to run: declared, preferred, or WIDTHxHEIGHT@auto|SCALE.
    #[facet(default = "declared", args::named)]
    pub variant: String,

    /// Mute Minecraft audio during the puppet run by default. Use `--no-mute` to hear it.
    #[facet(default = true, args::named)]
    pub mute: bool,

    /// Keep the client open after all selected puppets finish. Bare `--keep-open` keeps it open forever; a value accepts humantime durations such as `30s` or `5m`.
    #[facet(default, args::named)]
    pub keep_open: Option<Option<String>>,
}

impl RunGameTestPreviewArgs {
    /// # Errors
    ///
    /// Returns an error if planning, building, launching, or puppet validation fails.
    pub fn invoke(self, cancellation_token: CancellationToken) -> eyre::Result<()> {
        invoke_game_puppet(
            self.options,
            &self.puppet,
            self.game_test,
            self.width,
            self.height,
            &self.variant,
            self.mute,
            self.keep_open,
            cancellation_token,
        )
    }
}

/// Invoke a selected game-puppet preview from any CLI entry point.
///
/// # Errors
///
/// Returns an error if arguments are invalid or the preview build or launch fails.
#[expect(
    clippy::option_option,
    clippy::too_many_arguments,
    reason = "Figue represents a named optional value as absent, bare, or supplied, and this boundary keeps every shared puppet launch input explicit for its CLI adapters."
)]
pub(crate) fn invoke_game_puppet(
    options: JarBuildOptionsArgs,
    puppet: &str,
    game_test: Option<String>,
    width: Option<u16>,
    height: Option<u16>,
    variant: &str,
    mute: bool,
    keep_open: Option<Option<String>>,
    cancellation_token: CancellationToken,
) -> eyre::Result<()> {
    let puppet_filter = puppet.trim();
    if puppet_filter.is_empty() {
        eyre::bail!("puppet selector must not be empty");
    }
    let game_puppet_game_test = normalize_game_puppet_game_test(game_test)?;
    let (preview_width, preview_height) = validate_viewport(width, height)?;
    let viewport_selection = normalize_viewport_selection(variant)?;
    let game_puppet_keep_open = GamePuppetKeepOpen::from_cli(keep_open)?;
    RunCommand::with_run_options(
        options.into_options(BuildMode::Build)?,
        RunKind::GameTestPreview,
        RunOptions {
            game_puppet_filter: Some(puppet_filter.to_string()),
            game_puppet_game_test,
            game_puppet_viewport_selection: viewport_selection,
            game_puppet_keep_open,
            game_puppet_mute: mute,
            preview_width,
            preview_height,
            ..RunOptions::default()
        },
        cancellation_token,
    )
    .invoke()
}

pub(crate) fn normalize_viewport_selection(input: &str) -> eyre::Result<String> {
    let normalized = input.trim().to_ascii_lowercase();
    if matches!(normalized.as_str(), "declared" | "preferred") {
        return Ok(normalized);
    }
    let Some((size, scale)) = normalized.split_once('@') else {
        eyre::bail!("--variant must be declared, preferred, or WIDTHxHEIGHT@auto|SCALE");
    };
    let Some((width, height)) = size.split_once('x') else {
        eyre::bail!("--variant must be declared, preferred, or WIDTHxHEIGHT@auto|SCALE");
    };
    let width = width
        .parse::<u16>()
        .map_err(|error| eyre::eyre!("invalid --variant width: {error}"))?;
    let height = height
        .parse::<u16>()
        .map_err(|error| eyre::eyre!("invalid --variant height: {error}"))?;
    validate_viewport(Some(width), Some(height))?;
    if scale != "auto" && scale.parse::<std::num::NonZeroU16>().is_err() {
        eyre::bail!("--variant GUI scale must be auto or a positive integer");
    }
    Ok(format!("{width}x{height}@{scale}"))
}

pub(crate) fn normalize_game_puppet_game_test(
    game_test: Option<String>,
) -> eyre::Result<Option<String>> {
    let Some(game_test) = game_test else {
        return Ok(None);
    };
    let normalized = game_test.trim();
    if normalized.is_empty() {
        eyre::bail!("--game-test must not be empty");
    }
    let normalized = normalized.strip_prefix("sfm:").unwrap_or(normalized);
    if normalized.contains(':') {
        eyre::bail!("--game-test must use the sfm namespace: {game_test:?}");
    }
    if normalized.contains(['*', '?', ',']) {
        eyre::bail!("--game-test must identify exactly one GameTest: {game_test:?}");
    }
    Ok(Some(normalized.to_string()))
}

fn validate_viewport(width: Option<u16>, height: Option<u16>) -> eyre::Result<(u16, u16)> {
    const DEFAULT_WIDTH: u16 = 1280;
    const DEFAULT_HEIGHT: u16 = 720;
    const MIN_DIMENSION: u16 = 320;
    let width = width.unwrap_or(DEFAULT_WIDTH);
    let height = height.unwrap_or(DEFAULT_HEIGHT);
    if width < MIN_DIMENSION || height < MIN_DIMENSION {
        eyre::bail!(
            "--width and --height must both be at least {MIN_DIMENSION}; got {width}x{height}"
        );
    }
    Ok((width, height))
}

#[cfg(test)]
mod tests {
    use super::normalize_game_puppet_game_test;
    use super::normalize_viewport_selection;
    use super::validate_viewport;

    #[test]
    fn viewport_defaults_and_bounds_are_stable() {
        assert_eq!(validate_viewport(None, None).unwrap(), (1280, 720));
        assert_eq!(
            validate_viewport(Some(1600), Some(900)).unwrap(),
            (1600, 900)
        );
        let _ = validate_viewport(Some(319), Some(720)).unwrap_err();
        let _ = validate_viewport(Some(1280), Some(319)).unwrap_err();
    }

    #[test]
    fn parameterized_puppet_game_test_is_exact_and_namespace_normalized() {
        assert_eq!(
            normalize_game_puppet_game_test(Some(" sfm:move_1_stack_direct ".to_string())).unwrap(),
            Some("move_1_stack_direct".to_string())
        );
        let _ = normalize_game_puppet_game_test(Some("other:test".to_string())).unwrap_err();
        let _ = normalize_game_puppet_game_test(Some("move_*".to_string())).unwrap_err();
    }

    #[test]
    fn viewport_selection_is_typed_and_canonical() {
        assert_eq!(
            normalize_viewport_selection(" Preferred ").unwrap(),
            "preferred"
        );
        assert_eq!(
            normalize_viewport_selection("640x480@AUTO").unwrap(),
            "640x480@auto"
        );
        let _ = normalize_viewport_selection("640x480@0").unwrap_err();
        let _ = normalize_viewport_selection("tiny").unwrap_err();
    }
}
