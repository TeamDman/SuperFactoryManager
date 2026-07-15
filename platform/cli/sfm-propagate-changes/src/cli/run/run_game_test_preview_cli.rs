use crate::cancellation::CancellationToken;
use crate::cli::jar::JarBuildOptionsArgs;
use crate::jar_build::BuildMode;
use crate::jar_build::BuildOptions;
use crate::jar_build::ClientPuppetKeepOpen;
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

    /// Window width used for native screenshot captures.
    #[facet(default, args::named)]
    pub width: Option<u16>,

    /// Window height used for native screenshot captures.
    #[facet(default, args::named)]
    pub height: Option<u16>,

    /// Keep the client open after all selected puppets finish. Bare `--keep-open` keeps it open forever; a value accepts humantime durations such as `30s` or `5m`.
    #[facet(default, args::named)]
    pub keep_open: Option<Option<String>>,
}

impl RunGameTestPreviewArgs {
    pub(crate) fn into_options(self, mode: BuildMode) -> eyre::Result<BuildOptions> {
        self.options.into_options(mode)
    }

    /// # Errors
    ///
    /// Returns an error if planning, building, launching, or puppet validation fails.
    pub fn invoke(self, cancellation_token: CancellationToken) -> eyre::Result<()> {
        let puppet_filter = self.puppet.trim().to_string();
        if puppet_filter.is_empty() {
            eyre::bail!("--puppet must not be empty");
        }
        let (preview_width, preview_height) = validate_viewport(self.width, self.height)?;
        let client_puppet_keep_open = ClientPuppetKeepOpen::from_cli(self.keep_open.clone())?;
        RunCommand::with_run_options(
            self.into_options(BuildMode::Build)?,
            RunKind::GameTestPreview,
            RunOptions {
                game_puppet_filter: Some(puppet_filter),
                client_puppet_keep_open,
                preview_width,
                preview_height,
                ..RunOptions::default()
            },
            cancellation_token,
        )
        .invoke()
    }
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
    use super::validate_viewport;

    #[test]
    fn viewport_defaults_and_bounds_are_stable() {
        assert_eq!(validate_viewport(None, None).unwrap(), (1280, 720));
        assert_eq!(
            validate_viewport(Some(1600), Some(900)).unwrap(),
            (1600, 900)
        );
        assert!(validate_viewport(Some(319), Some(720)).is_err());
        assert!(validate_viewport(Some(1280), Some(319)).is_err());
    }
}
