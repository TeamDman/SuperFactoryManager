//! Explicit, fail-closed source-projection commands.

use std::collections::BTreeMap;
use std::fs;
use std::path::{Component, Path, PathBuf};

use eyre::{Result, WrapErr, ensure};
use facet::Facet;
use figue::{self as args};

use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use crate::source_projection::inputs::{
    apply_explicit_inputs, collect_projected_inputs_with_allowlist,
};
use crate::source_projection::manifest::SourceProjectionManifest;
use crate::source_projection::project_layout::{
    collect_gradle_project_inputs, collect_gradle_project_inputs_for_target,
};
use crate::source_projection::release_apply::{apply_release_baseline, release_source_paths};
use crate::source_projection::release_baseline::materialize_released_4_34_0_imports;
use crate::source_projection::selection::{ProjectionSelection, select};
use crate::source_projection::sync::{
    ProjectedArtifact, ProjectionIdentity, SyncMode, sync_projection,
};
use crate::terminal_output::stdout_line;

#[derive(Debug, Facet)]
pub struct SourceArgs {
    #[facet(args::subcommand)]
    pub command: SourceCommand,
}

#[derive(Debug, Facet)]
#[repr(u8)]
pub enum SourceCommand {
    /// Calculate a candidate preset-definition fingerprint before publishing it.
    PresetIdentity(SourcePresetIdentityArgs),
    /// Import pinned 4.34.0 tag sources and Gradle inputs without touching generated projects.
    ImportRelease(SourceImportReleaseArgs),
    /// Preview a projection without writing.
    DryRun(SourceProjectArgs),
    /// Require an existing projection to match the selected inputs.
    Check(SourceProjectArgs),
    /// Synchronize a generated source root after conflict checks.
    Sync(SourceProjectArgs),
    /// Accept contributor edits only after authored inputs render to identical output bytes.
    Reconcile(SourceProjectArgs),
}

#[derive(Debug, Facet)]
pub struct SourceImportReleaseArgs {
    /// Repository root with all ten 4.34.0 tags available locally.
    #[facet(args::named)]
    pub repo_root: PathBuf,
}

#[derive(Debug, Facet)]
pub struct SourcePresetIdentityArgs {
    /// Repository root containing the candidate source-projection manifest.
    #[facet(args::named)]
    pub repo_root: PathBuf,
    /// Manifest relative to the repository root.
    #[facet(default, args::named)]
    pub manifest: Option<PathBuf>,
    /// Preset whose definition should be fingerprinted.
    #[facet(args::named)]
    pub preset: String,
}

#[derive(Debug, Facet)]
pub struct SourceProjectArgs {
    /// Repository root containing the source-projection manifest.
    #[facet(args::named)]
    pub repo_root: PathBuf,
    /// Stable Minecraft target ID, for example 1.19.2.
    #[facet(args::named)]
    pub target: String,
    /// Immutable feature-preset ID.
    #[facet(args::named)]
    pub preset: String,
    /// Manifest relative to the repository root.
    #[facet(default, args::named)]
    pub manifest: Option<PathBuf>,
    /// Primary src directory relative to the repository root.
    #[facet(default, args::named)]
    pub primary_src_root: Option<PathBuf>,
    /// Version-appropriate Gradle project inputs relative to the repository root.
    #[facet(default, args::named)]
    pub gradle_project_root: Option<PathBuf>,
    /// Generated project root; required to avoid an accidental checked-in sync.
    #[facet(args::named)]
    pub output_root: PathBuf,
    /// Ordered source overlay as NAME=PATH, with PATH relative to the repository root.
    #[facet(default, args::named)]
    pub overlay: Vec<String>,
    /// Ordered Gradle-project overlay as NAME=PATH, relative to the repository root.
    #[facet(default, args::named)]
    pub gradle_overlay: Vec<String>,
}

impl SourceArgs {
    /// # Errors
    ///
    /// Returns an error for invalid inputs, changed generated files or I/O.
    pub fn invoke_in(
        self,
        cancellation: &CancellationToken,
        invocation_dir: &Path,
    ) -> Result<CliOutput> {
        let (args, mode) = match self.command {
            SourceCommand::PresetIdentity(args) => return args.invoke_in(invocation_dir),
            SourceCommand::ImportRelease(args) => return args.invoke_in(invocation_dir),
            SourceCommand::DryRun(args) => (args, SyncMode::DryRun),
            SourceCommand::Check(args) => (args, SyncMode::Check),
            SourceCommand::Sync(args) => (args, SyncMode::Apply),
            SourceCommand::Reconcile(args) => (args, SyncMode::Reconcile),
        };
        args.invoke_in(cancellation, invocation_dir, mode)
    }
}

impl SourceImportReleaseArgs {
    fn invoke_in(self, invocation_dir: &Path) -> Result<CliOutput> {
        let repo_root = resolve_repository_root(self.repo_root, invocation_dir)?;
        let imports = materialize_released_4_34_0_imports(&repo_root)?;
        for import in imports {
            stdout_line(format!(
                "release import {}: {} (sha256:{})",
                import.target_id, import.import_manifest_path, import.import_manifest_sha256
            ))?;
        }
        Ok(CliOutput::none())
    }
}

impl SourceProjectArgs {
    fn invoke_in(
        self,
        cancellation: &CancellationToken,
        invocation_dir: &Path,
        mode: SyncMode,
    ) -> Result<CliOutput> {
        cancellation.bail_if_cancelled()?;
        let repo_root = resolve_repository_root(self.repo_root.clone(), invocation_dir)?;

        let manifest_path = source_path(
            &repo_root,
            self.manifest
                .as_deref()
                .unwrap_or(Path::new("platform/minecraft/source-projection.json")),
        )?;
        let manifest_text = fs::read_to_string(&manifest_path).wrap_err_with(|| {
            format!(
                "cannot read source-projection manifest '{}'",
                manifest_path.display()
            )
        })?;
        let manifest = SourceProjectionManifest::from_json(&manifest_text)?;
        let target = manifest.target(&self.target)?;
        let preset = manifest.preset(&self.preset)?;
        let selection = select(&manifest, &self.target, &self.preset)?;

        let (primary_root, mut artifacts) =
            self.collect_source_artifacts(&repo_root, &selection, cancellation)?;
        for (path, artifact) in self.collect_gradle_artifacts(
            &repo_root,
            &selection,
            &target.id,
            &target.minecraft_version,
        )? {
            ensure!(
                artifacts.insert(path.clone(), artifact).is_none(),
                "Gradle project input '{path}' conflicts with a generated source"
            );
        }
        cancellation.bail_if_cancelled()?;

        let output_root = if self.output_root.is_absolute() {
            self.output_root
        } else {
            repo_root.join(self.output_root)
        };
        ensure!(
            output_root != repo_root && !repo_root.starts_with(&output_root),
            "generated output cannot be the repository root or one of its ancestors"
        );
        ensure!(
            !output_root.starts_with(&primary_root),
            "generated output cannot be inside the primary source root"
        );
        let identity = ProjectionIdentity {
            target_id: target.id.clone(),
            minecraft_version: target.minecraft_version.clone(),
            preset_id: preset.id.clone(),
            preset_definition_identity: preset.identity.clone(),
        };
        let report = sync_projection(&output_root, &identity, &artifacts, mode)?;
        stdout_line(format!(
            "source projection {} {}: {} created, {} updated, {} unchanged, manifest_changed={}",
            target.id,
            preset.id,
            report.created.len(),
            report.updated.len(),
            report.unchanged.len(),
            report.manifest_changed
        ))?;
        Ok(CliOutput::none())
    }

    fn collect_source_artifacts(
        &self,
        repo_root: &Path,
        selection: &ProjectionSelection,
        cancellation: &CancellationToken,
    ) -> Result<(PathBuf, BTreeMap<String, ProjectedArtifact>)> {
        let primary_root = source_path(
            repo_root,
            self.primary_src_root
                .as_deref()
                .unwrap_or(Path::new("platform/minecraft/src")),
        )?;
        let overlays = self
            .overlay
            .iter()
            .map(|spec| {
                let (name, path) = spec
                    .split_once('=')
                    .ok_or_else(|| eyre::eyre!("overlay '{spec}' must use NAME=PATH"))?;
                Ok((name.to_owned(), source_path(repo_root, Path::new(path))?))
            })
            .collect::<Result<Vec<_>>>()?;
        let release_paths = selection
            .release_baseline
            .as_ref()
            .map(|binding| release_source_paths(repo_root, binding))
            .transpose()?;
        cancellation.bail_if_cancelled()?;
        let mut artifacts = collect_projected_inputs_with_allowlist(
            &primary_root,
            &overlays,
            &selection.context,
            &selection.excluded_paths,
            release_paths.as_ref(),
        )?;
        apply_explicit_inputs(
            repo_root,
            &mut artifacts,
            &selection.explicit_inputs,
            &selection.context,
        )?;
        if let Some(binding) = &selection.release_baseline {
            apply_release_baseline(repo_root, binding, &selection.context, &mut artifacts)?;
        }
        Ok((primary_root, artifacts))
    }

    fn collect_gradle_artifacts(
        &self,
        repo_root: &Path,
        selection: &ProjectionSelection,
        target_id: &str,
        minecraft_version: &str,
    ) -> Result<BTreeMap<String, ProjectedArtifact>> {
        let (gradle_root, gradle_overlays) = if let Some(binding) = &selection.release_baseline {
            ensure!(
                self.gradle_project_root.is_none() && self.gradle_overlay.is_empty(),
                "release preset uses its pinned tagged Gradle inputs; manual Gradle overrides are not permitted"
            );
            let import_parent = Path::new(&binding.import_manifest)
                .parent()
                .ok_or_else(|| eyre::eyre!("release import manifest has no parent"))?;
            let release_gradle_root =
                source_path(repo_root, &import_parent.join("gradle-project"))?;
            (
                release_gradle_root.clone(),
                vec![("release-tag".to_owned(), release_gradle_root)],
            )
        } else {
            let gradle_root = source_path(
                repo_root,
                self.gradle_project_root
                    .as_deref()
                    .unwrap_or(Path::new("platform/minecraft")),
            )?;
            let gradle_overlays = self
                .gradle_overlay
                .iter()
                .map(|spec| {
                    let (name, path) = spec
                        .split_once('=')
                        .ok_or_else(|| eyre::eyre!("Gradle overlay '{spec}' must use NAME=PATH"))?;
                    Ok((name.to_owned(), source_path(repo_root, Path::new(path))?))
                })
                .collect::<Result<Vec<_>>>()?;
            (gradle_root, gradle_overlays)
        };
        if gradle_overlays.is_empty() {
            collect_gradle_project_inputs(&gradle_root)
        } else {
            collect_gradle_project_inputs_for_target(
                &gradle_root,
                &gradle_overlays,
                target_id,
                minecraft_version,
            )
        }
    }
}

impl SourcePresetIdentityArgs {
    /// # Errors
    ///
    /// Returns an error if the candidate manifest cannot be read or fingerprinted.
    pub fn invoke_in(self, invocation_dir: &Path) -> Result<CliOutput> {
        let repo_root = resolve_repository_root(self.repo_root, invocation_dir)?;
        let manifest_path = source_path(
            &repo_root,
            self.manifest
                .as_deref()
                .unwrap_or(Path::new("platform/minecraft/source-projection.json")),
        )?;
        let manifest_text = fs::read_to_string(&manifest_path).wrap_err_with(|| {
            format!(
                "cannot read candidate manifest '{}'",
                manifest_path.display()
            )
        })?;
        // A candidate has no published identity yet, so parsing must precede
        // full validation. The normal sync/check path verifies the identity.
        let manifest: SourceProjectionManifest = facet_json::from_str(&manifest_text)
            .wrap_err("cannot parse candidate source-projection manifest")?;
        let preset = manifest.preset(&self.preset)?;
        stdout_line(manifest.compute_preset_identity(preset)?)?;
        Ok(CliOutput::none())
    }
}

fn resolve_repository_root(path: PathBuf, invocation_dir: &Path) -> Result<PathBuf> {
    let candidate = if path.is_absolute() {
        path
    } else {
        invocation_dir.join(path)
    };
    let root = fs::canonicalize(&candidate)
        .wrap_err_with(|| format!("cannot resolve repository root '{}'", candidate.display()))?;
    ensure!(root.is_dir(), "repository root must be a directory");
    Ok(root)
}

fn source_path(repo_root: &Path, relative: &Path) -> Result<PathBuf> {
    ensure!(
        !relative.is_absolute()
            && relative
                .components()
                .all(|component| matches!(component, Component::Normal(_))),
        "source input path '{}' must be relative without parent traversal",
        relative.display()
    );
    let mut cursor = repo_root.to_path_buf();
    for component in relative.components() {
        cursor.push(component);
        let metadata = fs::symlink_metadata(&cursor)
            .wrap_err_with(|| format!("cannot inspect source input '{}'", cursor.display()))?;
        ensure!(
            !metadata.file_type().is_symlink(),
            "source input '{}' traverses a symlink",
            relative.display()
        );
    }
    let path = fs::canonicalize(repo_root.join(relative))
        .wrap_err_with(|| format!("cannot resolve source input '{}'", relative.display()))?;
    ensure!(
        path.starts_with(repo_root),
        "source input '{}' escapes the repository",
        relative.display()
    );
    Ok(path)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::cli::{Cli, Command};
    use crate::source_projection::manifest::{ProjectionPreset, ProjectionTarget};

    #[test]
    fn source_commands_parse_with_explicit_roots() {
        for verb in ["dry-run", "check", "sync", "reconcile"] {
            let parsed = figue::from_slice::<Cli>(&[
                "source",
                verb,
                "--repo-root",
                ".",
                "--target",
                "1.19.2",
                "--preset",
                "released-4.34.0",
                "--output-root",
                "platform/minecraft/mc-version/1.19.2",
            ])
            .into_result()
            .expect("source command should parse")
            .get_silent();
            assert!(matches!(parsed.command, Command::Source(_)));
        }
        let identity = figue::from_slice::<Cli>(&[
            "source",
            "preset-identity",
            "--repo-root",
            ".",
            "--preset",
            "released-4.34.0",
        ])
        .into_result()
        .expect("preset identity command should parse")
        .get_silent();
        assert!(matches!(identity.command, Command::Source(_)));
        let importer = figue::from_slice::<Cli>(&["source", "import-release", "--repo-root", "."])
            .into_result()
            .expect("release import command should parse")
            .get_silent();
        assert!(matches!(importer.command, Command::Source(_)));
    }

    #[test]
    fn rejects_external_source_inputs() {
        let repo = tempfile::tempdir().unwrap();
        let _ = source_path(repo.path(), Path::new("../other")).unwrap_err();
        let _ = source_path(repo.path(), Path::new("C:/other")).unwrap_err();
    }

    fn fixture_args(repo_root: &Path) -> SourceProjectArgs {
        SourceProjectArgs {
            repo_root: repo_root.to_path_buf(),
            target: "1.19.2".to_owned(),
            preset: "released-4.34.0".to_owned(),
            manifest: None,
            primary_src_root: None,
            gradle_project_root: None,
            output_root: PathBuf::from("generated"),
            overlay: vec![],
            gradle_overlay: vec![],
        }
    }

    #[test]
    fn sync_then_check_a_fixture_and_refuse_a_contributor_edit() {
        let repo = tempfile::tempdir().unwrap();
        let minecraft = repo.path().join("platform/minecraft");
        fs::create_dir_all(minecraft.join("src/main/java")).unwrap();
        fs::create_dir(minecraft.join("gradle")).unwrap();
        fs::write(
            minecraft.join("src/main/java/Example.java"),
            "class Example {}\n",
        )
        .unwrap();
        for name in [
            "build.gradle",
            "settings.gradle",
            "gradle.properties",
            "gradlew",
            "gradlew.bat",
            "sfm-toolchain.lock.json",
        ] {
            fs::write(minecraft.join(name), name.as_bytes()).unwrap();
        }
        fs::write(minecraft.join("gradle/wrapper.gradle"), b"wrapper\n").unwrap();
        let mut manifest = SourceProjectionManifest {
            schema_version: 1,
            targets: vec![ProjectionTarget {
                id: "1.19.2".to_owned(),
                template_key: "mc_1_19_2".to_owned(),
                minecraft_version: "1.19.2".to_owned(),
                loader: "forge".to_owned(),
                java_major: 17,
                project_dir: "platform/minecraft/mc-version/1.19.2".to_owned(),
            }],
            features: vec![],
            presets: vec![ProjectionPreset {
                id: "released-4.34.0".to_owned(),
                targets: vec!["1.19.2".to_owned()],
                enabled_features: vec![],
                release_baselines: vec![],
                identity: String::new(),
            }],
        };
        manifest.presets[0].identity = manifest
            .compute_preset_identity(&manifest.presets[0])
            .unwrap();
        fs::write(
            minecraft.join("source-projection.json"),
            manifest.to_json().unwrap(),
        )
        .unwrap();

        let cancellation = CancellationToken::new();
        SourceArgs {
            command: SourceCommand::Sync(fixture_args(repo.path())),
        }
        .invoke_in(&cancellation, repo.path())
        .unwrap();
        let output = repo.path().join("generated/src/main/java/Example.java");
        assert!(
            fs::read_to_string(&output)
                .unwrap()
                .starts_with("// GENERATED")
        );
        SourceArgs {
            command: SourceCommand::Check(fixture_args(repo.path())),
        }
        .invoke_in(&cancellation, repo.path())
        .unwrap();
        fs::write(&output, "contributor change\n").unwrap();
        let _ = SourceArgs {
            command: SourceCommand::Sync(fixture_args(repo.path())),
        }
        .invoke_in(&cancellation, repo.path())
        .unwrap_err();
        assert_eq!(fs::read_to_string(&output).unwrap(), "contributor change\n");
    }
}
