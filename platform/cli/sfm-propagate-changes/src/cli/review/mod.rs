use crate::repository_review_bundle_v1::BundleStatsV1;
use crate::repository_review_bundle_v1::SnapshotProviderV1;
use crate::terminal_output::stdout_line;
use eyre::Context as _;
use facet::Facet;
use figue as args;
use std::path::Path;
use std::path::PathBuf;
use unicode_normalization::UnicodeNormalization as _;

/// Repository review bundle commands.
#[derive(Debug, Facet)]
pub struct ReviewArgs {
    /// Review subcommand.
    #[facet(args::subcommand)]
    pub command: ReviewCommand,
}

impl ReviewArgs {
    /// Run the selected review command.
    ///
    /// # Errors
    ///
    /// Returns an error when bundle preparation or publication fails.
    pub fn invoke(self) -> eyre::Result<()> {
        match self.command {
            ReviewCommand::Prepare(arguments) => arguments.invoke(),
        }
    }
}

/// Repository review commands.
#[derive(Debug, Facet)]
#[repr(u8)]
pub enum ReviewCommand {
    /// Prepare a portable v1 repository-review bundle.
    Prepare(ReviewPrepareArgs),
}

/// Prepare a v1 repository-review bundle from one explicit source provider.
#[derive(Debug, Facet)]
pub struct ReviewPrepareArgs {
    /// Source provider for both snapshots: `git` revisions or prepared `directory` trees.
    #[facet(args::named)]
    pub provider: SnapshotProviderV1,
    /// Repository root/identity anchor. Git reads occur beneath this path.
    #[facet(args::named)]
    pub repository: PathBuf,
    /// Stable NFC repository identity. Defaults to the repository directory name.
    #[facet(default, args::named)]
    pub repository_name: Option<String>,
    /// Before Git revision or prepared directory path.
    #[facet(args::named)]
    pub before: String,
    /// After Git revision or prepared directory path.
    #[facet(args::named)]
    pub after: String,
    /// Human-facing bundle name.
    #[facet(default, args::named)]
    pub name: Option<String>,
    /// Exact JSON path, or a safe bare name in the managed review-bundle inbox.
    #[facet(args::named)]
    pub output: String,
}

#[derive(Debug, Facet)]
struct ReviewPrepareReport {
    path: String,
    bundle_id: String,
    semantic_hash: String,
    stats: BundleStatsV1,
}

impl ReviewPrepareArgs {
    fn invoke(self) -> eyre::Result<()> {
        let repository = dunce::canonicalize(&self.repository).wrap_err_with(|| {
            format!(
                "could not resolve repository anchor {}",
                self.repository.display()
            )
        })?;
        let repository_name = self.repository_name.unwrap_or_else(|| {
            repository
                .file_name()
                .and_then(|value| value.to_str())
                .unwrap_or_default()
                .to_owned()
        });
        if repository_name.is_empty()
            || repository_name.nfc().collect::<String>() != repository_name
        {
            eyre::bail!("--repository-name must be a non-empty NFC string");
        }
        let name = self
            .name
            .unwrap_or_else(|| format!("{repository_name}: {} -> {}", self.before, self.after));
        let bundle = crate::repository_review_bundle_v1::prepare(
            self.provider,
            &repository,
            &repository_name,
            &name,
            &self.before,
            &self.after,
        )?;
        let canonical = crate::repository_review_bundle_v1::to_canonical_json(&bundle)?;
        let output = resolve_output(&self.output)?;
        crate::payload_fetcher::write_payload_atomically(&output, canonical.as_bytes())?;
        let report = ReviewPrepareReport {
            path: output.display().to_string(),
            bundle_id: bundle.id.clone(),
            semantic_hash: crate::repository_review_bundle_v1::semantic_hash(&bundle)?,
            stats: crate::repository_review_bundle_v1::stats(&bundle),
        };
        stdout_line(facet_json::to_string(&report)?)
    }
}

fn resolve_output(value: &str) -> eyre::Result<PathBuf> {
    let path = Path::new(value);
    let explicit = path.components().count() > 1
        || path.is_absolute()
        || path
            .extension()
            .is_some_and(|extension| extension.eq_ignore_ascii_case("json"))
        || value.contains(['/', '\\']);
    if explicit {
        let parent = path
            .parent()
            .filter(|parent| !parent.as_os_str().is_empty());
        return Ok(match parent {
            Some(_) => path.to_owned(),
            None => std::env::current_dir()?.join(path),
        });
    }
    validate_managed_name(value)?;
    Ok(managed_inbox()?.join(format!("{value}.json")))
}

fn managed_inbox() -> eyre::Result<PathBuf> {
    let project = directories_next::ProjectDirs::from("ca", "teamdman", "SFM")
        .ok_or_else(|| eyre::eyre!("application-data directory is unavailable"))?;
    Ok(project.data_local_dir().join("review-bundles"))
}

fn validate_managed_name(value: &str) -> eyre::Result<()> {
    if value.is_empty()
        || value.len() > 128
        || matches!(value, "." | "..")
        || !value
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || matches!(byte, b'-' | b'_' | b'.'))
        || !value
            .as_bytes()
            .first()
            .is_some_and(u8::is_ascii_alphanumeric)
    {
        eyre::bail!(
            "managed output name must begin with an ASCII letter/digit and contain only letters, digits, '.', '-', or '_'"
        );
    }
    let stem = value
        .split('.')
        .next()
        .unwrap_or_default()
        .to_ascii_uppercase();
    if matches!(stem.as_str(), "CON" | "PRN" | "AUX" | "NUL")
        || (stem.len() == 4
            && (stem.starts_with("COM") || stem.starts_with("LPT"))
            && matches!(stem.as_bytes()[3], b'1'..=b'9'))
    {
        eyre::bail!("managed output name is reserved by Windows: {value:?}");
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::cli::Cli;
    use crate::cli::Command;

    #[test]
    fn parses_explicit_review_prepare_surface() {
        let cli = figue::from_slice::<Cli>(&[
            "review",
            "prepare",
            "--provider",
            "git",
            "--repository",
            "D:/Repos/SFM",
            "--before",
            "before",
            "--after",
            "after",
            "--output",
            "release-review",
        ])
        .into_result()
        .expect("review prepare parses")
        .get_silent();
        let Command::Review(ReviewArgs {
            command: ReviewCommand::Prepare(arguments),
        }) = cli.command
        else {
            panic!("expected review prepare");
        };
        assert_eq!(arguments.provider, SnapshotProviderV1::Git);
        assert_eq!(arguments.before, "before");
        assert_eq!(arguments.output, "release-review");
    }

    #[test]
    fn managed_names_reject_separators_dot_names_and_reserved_names() {
        for invalid in ["", ".", "..", "../escape", "nested/name", "CON", "LPT1"] {
            assert!(
                validate_managed_name(invalid).is_err(),
                "accepted {invalid:?}"
            );
        }
        validate_managed_name("sfm-review_1.19.2").expect("valid managed name");
    }
}
