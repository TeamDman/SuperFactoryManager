use crate::cancellation::CancellationToken;
use crate::jar_build::CleanLoaderProbeCommand;
use crate::jar_build::CleanLoaderProbeOptions;
use facet::Facet;
use figue as args;
use std::path::PathBuf;

/// Options for proving a release JAR in a fresh production loader installation.
#[derive(Clone, Debug, Facet)]
pub struct JarCleanLoaderProbeArgs {
    /// Built SFM release JAR to place in the otherwise-empty mods directory.
    #[facet(args::named)]
    pub release_jar: PathBuf,

    /// Required SHA-256 of the exact release JAR, as 64 hexadecimal characters.
    #[facet(args::named)]
    pub expected_release_sha256: String,

    /// Forge installer JAR whose exact bytes will be recorded in the report.
    #[facet(args::named)]
    pub forge_installer: PathBuf,

    /// Required SHA-256 of the exact Forge installer, as 64 hexadecimal characters.
    #[facet(args::named)]
    pub expected_forge_installer_sha256: String,

    /// New, absent directory in which to install and launch Forge.
    #[facet(args::named)]
    pub instance_dir: PathBuf,

    /// Log marker emitted only after SFM invokes the nested library.
    #[facet(args::named)]
    pub success_marker: String,

    /// Required nested Maven identity in `group:artifact` form. Repeatable.
    #[facet(default, args::named)]
    pub expected_nested: Vec<String>,

    /// Required class inside any nested JAR, in Java binary-name form. Repeatable.
    #[facet(default, args::named)]
    pub required_nested_class: Vec<String>,

    /// Maximum production-loader runtime, as a human duration.
    #[facet(default = "5m".to_string(), args::named)]
    pub timeout: String,

    /// Maximum Forge installer runtime, as a human duration.
    #[facet(default = "15m".to_string(), args::named)]
    pub install_timeout: String,

    /// Explicit Java home. Java 17 or newer is required.
    #[facet(default, args::named)]
    pub java_home: Option<PathBuf>,

    /// Optional structured report destination.
    #[facet(default, args::named)]
    pub report_json: Option<PathBuf>,

    /// Validate the release artifact and print the launch plan without installing or launching.
    #[facet(default = false, args::named)]
    pub plan_only: bool,
}

impl JarCleanLoaderProbeArgs {
    /// # Errors
    ///
    /// Returns an error when arguments, artifact inspection, installation, or launch proof fails.
    pub fn invoke(self, cancellation_token: CancellationToken) -> eyre::Result<()> {
        CleanLoaderProbeCommand::new(
            CleanLoaderProbeOptions {
                release_jar: self.release_jar,
                expected_release_sha256: self.expected_release_sha256,
                forge_installer: self.forge_installer,
                expected_forge_installer_sha256: self.expected_forge_installer_sha256,
                instance_dir: self.instance_dir,
                success_marker: self.success_marker,
                expected_nested: self.expected_nested,
                required_nested_classes: self.required_nested_class,
                timeout: parse_positive_duration("--timeout", &self.timeout)?,
                install_timeout: parse_positive_duration(
                    "--install-timeout",
                    &self.install_timeout,
                )?,
                java_home: self.java_home,
                report_json: self.report_json,
                plan_only: self.plan_only,
            },
            cancellation_token,
        )
        .invoke()
    }
}

fn parse_positive_duration(name: &str, value: &str) -> eyre::Result<std::time::Duration> {
    let duration = humantime::parse_duration(value)
        .map_err(|error| eyre::eyre!("Invalid {name} duration {value:?}: {error}"))?;
    if duration.is_zero() {
        eyre::bail!("{name} must be greater than zero");
    }
    Ok(duration)
}
