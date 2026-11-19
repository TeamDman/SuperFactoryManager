use crate::jar_build::projection_preview_artifact_root;
use crate::source_projection::core_catalog::CoreCatalog;
use crate::terminal_output::stdout_line;
use eyre::Context;
use facet::Facet;
use figue as args;
use std::path::Path;
use std::path::PathBuf;

/// Locate or open preview artifacts produced by an existing game-puppet run.
#[derive(Facet, Debug)]
pub struct PuppetArtifactsArgs {
    /// Artifact subcommand.
    #[facet(args::subcommand)]
    pub command: PuppetArtifactsCommand,
}

#[derive(Facet, Debug)]
#[repr(u8)]
pub enum PuppetArtifactsCommand {
    /// Print the existing preview artifact directory. Suitable for scripts and CI.
    Path(PuppetArtifactsPathArgs),
    /// Open the existing preview artifact directory in the system file explorer.
    Open(PuppetArtifactsOpenArgs),
}

#[derive(Facet, Debug)]
pub struct PuppetArtifactsPathArgs {
    /// Projection selector that must identify exactly one project with existing preview artifacts.
    #[facet(args::named)]
    pub projection: String,
    #[facet(default, args::named)]
    pub repo_root: Option<PathBuf>,
}

#[derive(Facet, Debug)]
pub struct PuppetArtifactsOpenArgs {
    /// Projection selector that must identify exactly one project with existing preview artifacts.
    #[facet(args::named)]
    pub projection: String,
    #[facet(default, args::named)]
    pub repo_root: Option<PathBuf>,
}

impl PuppetArtifactsArgs {
    /// # Errors
    ///
    /// Returns an error if the projection does not select exactly one project, or
    /// if that project has no complete preview artifact root.
    pub fn invoke(self, invocation_dir: &Path) -> eyre::Result<()> {
        match self.command {
            PuppetArtifactsCommand::Path(args) => stdout_line(
                resolve_existing_preview_artifact_root(
                    &args.projection,
                    args.repo_root.as_deref(),
                    invocation_dir,
                )?
                .display(),
            ),
            PuppetArtifactsCommand::Open(args) => {
                let artifact_root = resolve_existing_preview_artifact_root(
                    &args.projection,
                    args.repo_root.as_deref(),
                    invocation_dir,
                )?;
                open::that(&artifact_root).wrap_err_with(|| {
                    format!(
                        "Failed to open game-puppet preview artifacts at {}",
                        artifact_root.display()
                    )
                })
            }
        }
    }
}

fn resolve_existing_preview_artifact_root(
    selector: &str,
    repo_root: Option<&Path>,
    invocation_dir: &Path,
) -> eyre::Result<PathBuf> {
    let loaded = CoreCatalog::load_for_invocation(repo_root, invocation_dir)?;
    let keys = loaded.catalog.matching_glob_names(selector)?;
    eyre::ensure!(
        keys.len() == 1,
        "artifact lookup requires exactly one projection"
    );
    let artifact_root = projection_preview_artifact_root(
        &loaded
            .repo_root
            .join("platform/minecraft/projections")
            .join(&keys[0]),
    );
    require_complete_preview_artifact_root(&artifact_root, selector)?;
    Ok(artifact_root)
}

fn require_complete_preview_artifact_root(
    artifact_root: &Path,
    selector: &str,
) -> eyre::Result<()> {
    if !artifact_root.is_dir() {
        eyre::bail!(
            "No game-puppet preview artifacts exist for --projection '{selector}' at {}. Run `puppet run <puppet> --projection {selector}` first.",
            artifact_root.display()
        );
    }

    let manifest = artifact_root.join("preview-manifest.json");
    if !manifest.is_file() {
        eyre::bail!(
            "Game-puppet preview artifacts for --projection '{selector}' are incomplete: {} is missing.",
            manifest.display()
        );
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::require_complete_preview_artifact_root;
    use super::resolve_existing_preview_artifact_root;
    use crate::jar_build::projection_preview_artifact_root;
    use crate::source_projection::core_catalog::write_project_catalog_fixture;
    use std::fs;
    use std::path::Path;
    use tempfile::tempdir;

    #[test]
    fn preview_artifact_lookup_resolves_relative_repo_from_invocation_directory() -> eyre::Result<()>
    {
        let invocation = tempdir()?;
        let checkout = invocation.path().join("checkout");
        let projection = "sfm-dev/mc-1.19.2";
        let project = write_project_catalog_fixture(&checkout, projection, "1.19.2")?;
        let artifact_root = projection_preview_artifact_root(&project);
        assert_eq!(
            artifact_root,
            project.join("build/sfm-toolchain/artifacts/game-test-preview")
        );
        fs::create_dir_all(&artifact_root)?;
        fs::write(artifact_root.join("preview-manifest.json"), "{}")?;
        let resolved = resolve_existing_preview_artifact_root(
            projection,
            Some(Path::new("checkout")),
            invocation.path(),
        )?;
        assert_eq!(resolved.canonicalize()?, artifact_root.canonicalize()?);
        Ok(())
    }

    #[test]
    fn preview_artifact_root_requires_an_existing_manifest() {
        let temporary = tempdir().expect("temporary artifact root");
        let artifact_root = temporary.path().join("game-test-preview");
        let missing_root = require_complete_preview_artifact_root(&artifact_root, "1.19.2")
            .expect_err("missing artifact root should fail");
        assert!(
            missing_root
                .to_string()
                .contains("No game-puppet preview artifacts exist")
        );

        fs::create_dir_all(&artifact_root).expect("create artifact root");
        let missing_manifest = require_complete_preview_artifact_root(&artifact_root, "1.19.2")
            .expect_err("missing manifest should fail");
        assert!(missing_manifest.to_string().contains("are incomplete"));

        fs::write(artifact_root.join("preview-manifest.json"), "{}").expect("write manifest");
        require_complete_preview_artifact_root(&artifact_root, "1.19.2")
            .expect("complete artifact root should validate");
    }
}
