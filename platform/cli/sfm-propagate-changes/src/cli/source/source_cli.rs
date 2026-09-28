//! Explicit, fail-closed source-projection commands.

use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use crate::jdk::resolve_exact_java_for_minecraft_dir;
use crate::source_projection::development_baseline::CANONICAL_COMMIT;
use crate::source_projection::development_baseline::DevelopmentHeadSpec;
use crate::source_projection::development_baseline::compare_committed_source_heads;
use crate::source_projection::development_baseline::materialize_committed_source_import;
use crate::source_projection::development_fixtures::collect_verified_development_project_fixtures;
use crate::source_projection::development_fixtures::materialize_development_project_fixtures;
use crate::source_projection::development_gradle::apply_post_baseline_gradle_sources;
use crate::source_projection::development_gradle::materialize_development_gradle_inputs;
use crate::source_projection::inputs::apply_explicit_inputs;
use crate::source_projection::inputs::collect_projected_inputs_with_allowlist;
use crate::source_projection::manifest::SourceProjectionManifest;
use crate::source_projection::project_layout::collect_gradle_project_inputs;
use crate::source_projection::project_layout::collect_gradle_project_inputs_for_target;
use crate::source_projection::provenance::sha256;
use crate::source_projection::release_apply::apply_release_baseline;
use crate::source_projection::release_apply::release_source_paths;
use crate::source_projection::release_baseline::ImportFile;
use crate::source_projection::release_baseline::collect_tagged_gradle_tree;
use crate::source_projection::release_baseline::insert_import_file;
use crate::source_projection::release_baseline::materialize_released_4_34_0_imports;
use crate::source_projection::release_baseline::preflight_imports;
use crate::source_projection::release_baseline::read_pinned_blob;
use crate::source_projection::selection::ProjectionSelection;
use crate::source_projection::selection::select;
use crate::source_projection::sync::MANIFEST_FILE;
use crate::source_projection::sync::ProjectedArtifact;
use crate::source_projection::sync::ProjectionIdentity;
use crate::source_projection::sync::SyncMode;
use crate::source_projection::sync::sync_projection;
use crate::terminal_output::stdout_line;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;
use figue::{self as args};
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fs;
use std::path::Component;
use std::path::Path;
use std::path::PathBuf;
use std::process::Command as ProcessCommand;
use std::thread;
use std::time::Duration;

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
    /// Import one reviewed pair of committed development heads without touching generated projects.
    ImportDevelopment(SourceImportDevelopmentArgs),
    /// Import pinned project-root test fixtures from one reviewed development head.
    ImportDevelopmentFixtures(SourceImportDevelopmentFixturesArgs),
    /// Preview a projection without writing.
    DryRun(SourceProjectArgs),
    /// Require an existing projection to match the selected inputs.
    Check(SourceProjectArgs),
    /// Synchronize a generated source root after conflict checks.
    Sync(SourceProjectArgs),
    /// Accept contributor edits only after authored inputs render to identical output bytes.
    Reconcile(SourceProjectArgs),
    /// Build a caller-owned development projection without changing checked-in release roots.
    Build(SourceGradleArgs),
    /// Run a caller-owned development projection, retaining its saves and configuration.
    Run(SourceGradleArgs),
}

#[derive(Debug, Facet)]
pub struct SourceImportReleaseArgs {
    /// Repository root with all ten 4.34.0 tags available locally.
    #[facet(args::named)]
    pub repo_root: PathBuf,
}

#[derive(Debug, Facet)]
pub struct SourceImportDevelopmentArgs {
    /// Git worktree root containing both exact committed heads.
    #[facet(args::named)]
    pub repo_root: PathBuf,
    /// Version target ID whose import directory will be populated.
    #[facet(args::named)]
    pub target: String,
    /// Full canonical-source commit OID reviewed for this import.
    #[facet(args::named)]
    pub canonical_commit: String,
    /// Full version-branch commit OID reviewed for this import.
    #[facet(args::named)]
    pub target_commit: String,
    /// Reviewed number of target Gradle project inputs.
    #[facet(args::named)]
    pub expected_gradle_files: usize,
    /// Reviewed canonical-to-target Gradle difference; repeat for each path.
    #[facet(default, args::named)]
    pub expected_gradle_changed_path: Vec<String>,
}

#[derive(Clone, Debug, Facet)]
pub struct SourceImportDevelopmentFixturesArgs {
    /// Git worktree root containing both exact committed heads.
    #[facet(args::named)]
    pub repo_root: PathBuf,
    /// Version target ID whose fixture import directory will be populated.
    #[facet(args::named)]
    pub target: String,
    /// Full canonical-source commit OID reviewed for this import.
    #[facet(args::named)]
    pub canonical_commit: String,
    /// Full version-branch commit OID reviewed for this import.
    #[facet(args::named)]
    pub target_commit: String,
    /// Reviewed total number of files in the two fixture roots.
    #[facet(args::named)]
    pub expected_fixture_files: usize,
    /// Reviewed root; repeat for `examples` and `docs/architecture/fixtures`.
    #[facet(default, args::named)]
    pub expected_fixture_root: Vec<String>,
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
    /// Generated project root; build/run require an absolute path outside this repository.
    #[facet(args::named)]
    pub output_root: PathBuf,
    /// Ordered source overlay as NAME=PATH, with PATH relative to the repository root.
    #[facet(default, args::named)]
    pub overlay: Vec<String>,
    /// Ordered Gradle-project overlay as NAME=PATH, relative to the repository root.
    #[facet(default, args::named)]
    pub gradle_overlay: Vec<String>,
}

#[derive(Debug, Facet)]
pub struct SourceGradleArgs {
    /// Projection selection and the caller-owned output root.
    #[facet(flatten)]
    pub project: SourceProjectArgs,
    /// Explicit JDK home; otherwise use the exact JDK selected by the projected lockfile.
    #[facet(default, args::named)]
    pub java_home: Option<PathBuf>,
    /// Gradle task to execute. Defaults to `jar` for build or `runClient` for run.
    #[facet(default, args::named)]
    pub task: Option<String>,
    /// Optional declared Gradle source/dependency profile, such as rust-toolchain.
    #[facet(default, args::named)]
    pub gradle_profile: Option<String>,
    /// Require Gradle to use already-cached dependencies and Minecraft assets.
    #[facet(default = false, args::named)]
    pub offline: bool,
}

#[derive(Clone, Copy)]
enum SourceGradleMode {
    Build,
    Run,
}

impl SourceGradleMode {
    const fn default_task(self) -> &'static str {
        match self {
            Self::Build => "jar",
            Self::Run => "runClient",
        }
    }

    const fn verb(self) -> &'static str {
        match self {
            Self::Build => "build",
            Self::Run => "run",
        }
    }
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
            SourceCommand::ImportDevelopment(args) => return args.invoke_in(invocation_dir),
            SourceCommand::ImportDevelopmentFixtures(args) => {
                return args.invoke_in(invocation_dir);
            }
            SourceCommand::Build(args) => {
                return args.invoke_in(cancellation, invocation_dir, SourceGradleMode::Build);
            }
            SourceCommand::Run(args) => {
                return args.invoke_in(cancellation, invocation_dir, SourceGradleMode::Run);
            }
            SourceCommand::DryRun(args) => (args, SyncMode::DryRun),
            SourceCommand::Check(args) => (args, SyncMode::Check),
            SourceCommand::Sync(args) => (args, SyncMode::Apply),
            SourceCommand::Reconcile(args) => (args, SyncMode::Reconcile),
        };
        args.invoke_in(cancellation, invocation_dir, mode)
    }
}

impl SourceGradleArgs {
    fn invoke_in(
        self,
        cancellation: &CancellationToken,
        invocation_dir: &Path,
        mode: SourceGradleMode,
    ) -> Result<CliOutput> {
        cancellation.bail_if_cancelled()?;
        let Self {
            mut project,
            java_home,
            task,
            gradle_profile,
            offline,
        } = self;
        let repo_root = resolve_repository_root(project.repo_root.clone(), invocation_dir)?;
        let manifest_path = source_path(
            &repo_root,
            project
                .manifest
                .as_deref()
                .unwrap_or(Path::new("platform/minecraft/source-projection.json")),
        )?;
        let manifest_text = fs::read_to_string(&manifest_path)
            .wrap_err_with(|| format!("cannot read '{}'", manifest_path.display()))?;
        let manifest = SourceProjectionManifest::from_json(&manifest_text)?;
        let java_major = u32::from(manifest.target(&project.target)?.java_major);
        let output_root = development_output_root(&repo_root, &project.output_root)?;
        let task = task.unwrap_or_else(|| mode.default_task().to_owned());
        validate_gradle_task(&task)?;
        if let Some(profile) = &gradle_profile {
            validate_gradle_profile(profile)?;
        }
        let test_exit_override = game_test_exit_override(&repo_root, &project.target, &task);

        project.output_root.clone_from(&output_root);
        project.invoke_in(cancellation, invocation_dir, SyncMode::Apply)?;
        cancellation.bail_if_cancelled()?;
        let development_version = development_mod_version(&output_root)?;

        let explicit_java_home = java_home.map(|path| {
            if path.is_absolute() {
                path
            } else {
                invocation_dir.join(path)
            }
        });
        let resolved_java = resolve_exact_java_for_minecraft_dir(
            &output_root,
            java_major,
            explicit_java_home.as_deref(),
        )?;
        let home = resolved_java.home.as_deref().ok_or_else(|| {
            eyre::eyre!(
                "selected Java {} has no JDK home; specify --java-home for this Gradle run",
                resolved_java.executable.display()
            )
        })?;
        stdout_line(format!(
            "source {} project retained at {} (mod version {})",
            mode.verb(),
            output_root.display(),
            development_version
        ))?;
        run_project_gradle(
            &output_root,
            &task,
            &development_version,
            home,
            &GradleRunOptions {
                profile: gradle_profile.as_deref(),
                offline,
                init_script: test_exit_override.as_deref(),
            },
            cancellation,
        )?;
        if matches!(mode, SourceGradleMode::Build) {
            report_build_artifacts(&output_root, &task, &development_version)?;
        }
        Ok(CliOutput::none())
    }
}

fn development_output_root(repo_root: &Path, output_root: &Path) -> Result<PathBuf> {
    ensure!(
        !output_root
            .components()
            .any(|part| matches!(part, Component::ParentDir)),
        "development output root cannot contain parent traversal"
    );
    let candidate = if output_root.is_absolute() {
        output_root.to_path_buf()
    } else {
        repo_root.join(output_root)
    };
    let mut cursor = PathBuf::new();
    for component in candidate.components() {
        cursor.push(component);
        match fs::symlink_metadata(&cursor) {
            Ok(metadata) => ensure!(
                !metadata.file_type().is_symlink(),
                "development output root traverses a symlink: '{}'",
                cursor.display()
            ),
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => break,
            Err(error) => {
                return Err(error).wrap_err_with(|| {
                    format!(
                        "cannot inspect development output root '{}'",
                        cursor.display()
                    )
                });
            }
        }
    }
    let authored_root = repo_root.join("platform/minecraft");
    ensure!(
        !paths_overlap(&candidate, &authored_root),
        "development output root must be outside authored and checked-in Minecraft roots '{}'",
        authored_root.display()
    );
    ensure!(
        !paths_overlap(&candidate, &repo_root.join(".git")),
        "development output root cannot overlap repository Git state"
    );
    ensure!(
        !path_is_prefix(&candidate, repo_root),
        "development output root cannot be the repository root or an ancestor"
    );
    ensure!(
        !path_is_prefix(repo_root, &candidate),
        "development output root must be outside the repository so temporary projections cannot be committed accidentally"
    );
    Ok(candidate)
}

fn paths_overlap(left: &Path, right: &Path) -> bool {
    path_is_prefix(left, right) || path_is_prefix(right, left)
}

fn path_is_prefix(prefix: &Path, path: &Path) -> bool {
    fn components(path: &Path) -> Vec<String> {
        path.components()
            .map(|component| {
                let value = component.as_os_str().to_string_lossy().into_owned();
                if cfg!(windows) {
                    value.to_ascii_lowercase()
                } else {
                    value
                }
            })
            .collect()
    }
    components(path).starts_with(&components(prefix))
}

fn validate_gradle_task(task: &str) -> Result<()> {
    let name = task.strip_prefix(':').unwrap_or(task);
    ensure!(
        !name.is_empty()
            && name.split(':').all(|segment| {
                segment
                    .as_bytes()
                    .first()
                    .is_some_and(u8::is_ascii_alphanumeric)
                    && segment
                        .bytes()
                        .all(|byte| byte.is_ascii_alphanumeric() || matches!(byte, b'_' | b'-'))
            }),
        "Gradle task must be one task path containing only letters, digits, '_', '-' and ':'"
    );
    Ok(())
}

fn validate_gradle_profile(profile: &str) -> Result<()> {
    ensure!(
        !profile.is_empty()
            && profile.len() <= 64
            && profile.as_bytes()[0].is_ascii_alphanumeric()
            && profile
                .bytes()
                .all(|byte| { byte.is_ascii_alphanumeric() || matches!(byte, b'_' | b'-' | b'.') }),
        "Gradle profile must be a nonempty ID of at most 64 letters, digits, '_', '-' or '.'"
    );
    Ok(())
}

fn game_test_exit_override(repo_root: &Path, target: &str, task: &str) -> Option<PathBuf> {
    (task == "runGameTestServer" && matches!(target, "1.19.2" | "1.19.4")).then(|| {
        repo_root.join(
            "platform/cli/sfm-propagate-changes/gradle/forge-game-test-no-force-exit.init.gradle",
        )
    })
}

struct GradleRunOptions<'a> {
    profile: Option<&'a str>,
    offline: bool,
    init_script: Option<&'a Path>,
}

fn run_project_gradle(
    project_root: &Path,
    task: &str,
    development_version: &str,
    java_home: &Path,
    options: &GradleRunOptions<'_>,
    cancellation: &CancellationToken,
) -> Result<()> {
    let wrapper = project_root.join(if cfg!(windows) {
        "gradlew.bat"
    } else {
        "gradlew"
    });
    ensure!(wrapper.is_file(), "projected Gradle wrapper is missing");
    let mut command = ProcessCommand::new(&wrapper);
    command
        .current_dir(project_root)
        .arg("--no-daemon")
        .arg(format!("-Pmod_version={development_version}"));
    if options.offline {
        command.arg("--offline");
    }
    if let Some(profile) = options.profile {
        command.arg(format!("-PsfmProfile={profile}"));
    }
    if let Some(script) = options.init_script {
        ensure!(script.is_file(), "GameTest Gradle init script is missing");
        // Windows fs::canonicalize yields a \\?\ path that cmd.exe splits when it
        // launches gradlew.bat. Gradle then sees only a stray backslash as the script.
        let script =
            dunce::canonicalize(script).wrap_err("cannot resolve GameTest Gradle init script")?;
        command.arg("--init-script").arg(script);
    }
    let mut child = command
        .arg(task)
        .env("JAVA_HOME", java_home)
        .spawn()
        .wrap_err_with(|| format!("cannot start projected Gradle task '{task}'"))?;
    loop {
        if let Some(status) = child
            .try_wait()
            .wrap_err("cannot wait for projected Gradle")?
        {
            ensure!(
                status.success(),
                "projected Gradle task '{task}' failed with status {status}; project retained at '{}'",
                project_root.display()
            );
            return Ok(());
        }
        if cancellation.is_cancelled() {
            let _ = child.kill();
            let _ = child.wait();
            eyre::bail!(
                "projected Gradle task '{task}' was cancelled; project retained at '{}'",
                project_root.display()
            );
        }
        thread::sleep(Duration::from_millis(200));
    }
}

fn development_mod_version(project_root: &Path) -> Result<String> {
    let base = gradle_property(project_root, "mod_version")?;
    ensure!(
        base.len() <= 64
            && base
                .bytes()
                .all(|byte| { byte.is_ascii_alphanumeric() || matches!(byte, b'.' | b'_' | b'-') }),
        "projected mod_version is not safe to use in a development artifact name"
    );
    let provenance = fs::read(project_root.join(MANIFEST_FILE))
        .wrap_err("cannot read projected provenance for development version")?;
    let digest = sha256(&provenance);
    let short = digest
        .strip_prefix("sha256:")
        .and_then(|hex| hex.get(..12))
        .ok_or_else(|| eyre::eyre!("invalid projected provenance digest"))?;
    Ok(format!("{base}-dev.{short}"))
}

fn gradle_property(project_root: &Path, name: &str) -> Result<String> {
    let properties = fs::read_to_string(project_root.join("gradle.properties"))
        .wrap_err("cannot read projected gradle.properties")?;
    let values = properties
        .lines()
        .filter_map(|line| {
            let line = line.trim();
            if line.starts_with('#') || line.starts_with('!') {
                return None;
            }
            let (key, value) = line.split_once('=')?;
            (key.trim() == name).then(|| value.trim())
        })
        .collect::<Vec<_>>();
    ensure!(
        values.len() == 1 && !values[0].is_empty(),
        "projected gradle.properties needs exactly one nonempty '{name}'"
    );
    Ok(values[0].to_owned())
}

fn report_build_artifacts(project_root: &Path, task: &str, version: &str) -> Result<()> {
    let libs = project_root.join("build/libs");
    let name = gradle_property(project_root, "mod_name")?;
    let minecraft = gradle_property(project_root, "minecraft_version")?;
    let prefix = format!("{name}-MC{minecraft}-{version}");
    let mut jars = if libs.is_dir() {
        fs::read_dir(&libs)?
            .map(|entry| {
                let entry = entry?;
                let path = entry.path();
                let file_name = entry.file_name();
                let file_name = file_name.to_string_lossy();
                let matches = (file_name == format!("{prefix}.jar")
                    || file_name.starts_with(&format!("{prefix}-")) && file_name.ends_with(".jar"))
                    && !file_name.ends_with("-sources.jar")
                    && !file_name.ends_with("-javadoc.jar")
                    && path.is_file();
                Ok(matches.then_some(path))
            })
            .collect::<Result<Vec<_>>>()?
            .into_iter()
            .flatten()
            .collect::<Vec<_>>()
    } else {
        Vec::new()
    };
    jars.sort();
    if !jars.is_empty() {
        for jar in jars {
            stdout_line(format!("built JAR: {}", jar.display()))?;
        }
    } else if task == "jar" {
        eyre::bail!(
            "projected jar task succeeded but no non-source JAR for '{prefix}' exists under '{}'",
            libs.display()
        );
    } else {
        stdout_line(format!("build outputs retained under {}", libs.display()))?;
    }
    Ok(())
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

impl SourceImportDevelopmentArgs {
    fn invoke_in(self, invocation_dir: &Path) -> Result<CliOutput> {
        let repo_root = resolve_repository_root(self.repo_root, invocation_dir)?;
        let spec = DevelopmentHeadSpec {
            target_id: self.target,
            canonical_commit: self.canonical_commit,
            target_commit: self.target_commit,
        };
        let source_report = compare_committed_source_heads(&repo_root, &spec)?;
        preflight_development_gradle_expectations(
            &repo_root,
            &spec,
            self.expected_gradle_files,
            &self.expected_gradle_changed_path,
        )?;
        preflight_development_source_import(&repo_root, &spec, &source_report)?;

        // Both committed inventories and every existing source destination
        // were checked before either importer can install a file. The Gradle
        // importer preflights its complete desired set before installing it.
        let changed_paths = self
            .expected_gradle_changed_path
            .iter()
            .map(String::as_str)
            .collect::<Vec<_>>();
        let gradle = materialize_development_gradle_inputs(
            &repo_root,
            &spec,
            self.expected_gradle_files,
            &changed_paths,
        )?;
        let source = materialize_committed_source_import(&repo_root, &spec)?;
        stdout_line(format!(
            "development source import {}: {} (sha256:{}); unchanged={}, changed={}, target-only={}, canonical-only={}, created={}, reused={}",
            spec.target_id,
            source.import_manifest_path,
            source.import_manifest_sha256,
            source.unchanged,
            source.changed,
            source.target_only,
            source.canonical_only,
            source.created_files,
            source.reused_files,
        ))?;
        stdout_line(format!(
            "development Gradle import {}: {} ({} inputs), {} (sha256:{}); created={}, reused={}",
            spec.target_id,
            gradle.gradle_project_path,
            self.expected_gradle_files,
            gradle.provenance_path,
            gradle.provenance_sha256,
            gradle.created_files,
            gradle.reused_files,
        ))?;
        Ok(CliOutput::none())
    }
}

impl SourceImportDevelopmentFixturesArgs {
    fn invoke_in(self, invocation_dir: &Path) -> Result<CliOutput> {
        let repo_root = resolve_repository_root(self.repo_root, invocation_dir)?;
        ensure!(
            self.expected_fixture_files > 0,
            "expected fixture file count must be positive"
        );
        let mut roots = self.expected_fixture_root;
        roots.sort();
        ensure!(
            roots == ["docs/architecture/fixtures", "examples"],
            "reviewed fixture roots must be exactly 'examples' and 'docs/architecture/fixtures'"
        );
        let spec = DevelopmentHeadSpec {
            target_id: self.target,
            canonical_commit: self.canonical_commit,
            target_commit: self.target_commit,
        };
        let report = materialize_development_project_fixtures(
            &repo_root,
            &spec,
            self.expected_fixture_files,
        )?;
        stdout_line(format!(
            "development fixture import {}: {} ({} files), {} (sha256:{}); created={}, reused={}",
            report.target_id,
            report.project_fixtures_path,
            report.file_count,
            report.provenance_path,
            report.provenance_sha256,
            report.created_files,
            report.reused_files,
        ))?;
        Ok(CliOutput::none())
    }
}

fn preflight_development_gradle_expectations(
    repo_root: &Path,
    spec: &DevelopmentHeadSpec,
    expected_file_count: usize,
    expected_changed_paths: &[String],
) -> Result<()> {
    ensure!(
        expected_file_count > 0,
        "expected Gradle file count must be positive"
    );
    ensure!(
        !expected_changed_paths.is_empty(),
        "at least one --expected-gradle-changed-path is required"
    );
    let canonical = collect_tagged_gradle_tree(repo_root, &spec.canonical_commit)?;
    let target = collect_tagged_gradle_tree(repo_root, &spec.target_commit)?;
    ensure!(
        target.len() == expected_file_count,
        "development Gradle tree has {} files, expected {expected_file_count}",
        target.len()
    );
    let changed = canonical
        .keys()
        .chain(target.keys())
        .collect::<BTreeSet<_>>()
        .into_iter()
        .filter_map(|path| {
            let old = canonical.get(path);
            let new = target.get(path);
            (old.map(|entry| (&entry.oid, &entry.mode))
                != new.map(|entry| (&entry.oid, &entry.mode)))
            .then_some(path.as_str())
        })
        .collect::<Vec<_>>();
    let mut expected = expected_changed_paths
        .iter()
        .map(String::as_str)
        .collect::<Vec<_>>();
    expected.sort_unstable();
    ensure!(
        expected.windows(2).all(|pair| pair[0] != pair[1]),
        "duplicate expected Gradle changed path"
    );
    ensure!(
        changed == expected,
        "development Gradle differences changed: found {changed:?}, expected {expected:?}"
    );
    Ok(())
}

fn preflight_development_source_import(
    repo_root: &Path,
    spec: &DevelopmentHeadSpec,
    report: &crate::source_projection::release_baseline::ReleaseBaselineReport,
) -> Result<()> {
    let target = report
        .targets
        .first()
        .ok_or_else(|| eyre::eyre!("development source comparison has no target"))?;
    let import_root = format!(
        "platform/minecraft/development-baselines/{}",
        spec.target_id
    );
    let mut desired = BTreeMap::<String, ImportFile>::new();
    insert_import_file(
        &mut desired,
        &format!("{import_root}/import.json"),
        report.to_json()?.into_bytes(),
        "100644",
    )?;
    for (path, record) in &target.paths {
        let Some(overlay) = &record.release_overlay_path else {
            continue;
        };
        ensure!(
            overlay == &format!("{import_root}/overlays/{path}"),
            "development source overlay path changed at '{path}'"
        );
        let oid = record
            .release_blob_oid
            .as_deref()
            .ok_or_else(|| eyre::eyre!("development source '{path}' has no Git blob ID"))?;
        let hash = record
            .release_sha256
            .as_deref()
            .ok_or_else(|| eyre::eyre!("development source '{path}' has no SHA-256"))?;
        let mode = record
            .release_git_mode
            .as_deref()
            .ok_or_else(|| eyre::eyre!("development source '{path}' has no Git mode"))?;
        let bytes = read_pinned_blob(repo_root, &spec.target_commit, path, oid, hash)?;
        insert_import_file(&mut desired, overlay, bytes, mode)?;
    }
    preflight_imports(repo_root, &desired)?;
    Ok(())
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
        if let Some(hash) = &selection.canonical_project_fixture_provenance_sha256 {
            ensure!(
                selection.release_baseline.is_none(),
                "canonical project fixtures cannot be applied to a release baseline"
            );
            let spec = DevelopmentHeadSpec {
                target_id: self.target.clone(),
                canonical_commit: CANONICAL_COMMIT.to_owned(),
                target_commit: CANONICAL_COMMIT.to_owned(),
            };
            for (path, artifact) in
                collect_verified_development_project_fixtures(repo_root, &spec, hash)?
            {
                ensure!(
                    artifacts.insert(path.clone(), artifact).is_none(),
                    "canonical project fixture output '{path}' collides with a source artifact"
                );
            }
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
        let mut artifacts = if gradle_overlays.is_empty() {
            collect_gradle_project_inputs(&gradle_root)
        } else {
            collect_gradle_project_inputs_for_target(
                &gradle_root,
                &gradle_overlays,
                target_id,
                minecraft_version,
            )
        }?;
        if let Some(binding) = &selection.release_baseline {
            apply_post_baseline_gradle_sources(repo_root, binding, &mut artifacts)?;
        }
        Ok(artifacts)
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
    use crate::cli::Cli;
    use crate::cli::Command;
    use crate::source_projection::manifest::ProjectionPreset;
    use crate::source_projection::manifest::ProjectionTarget;
    use std::process::Command as TestCommand;

    fn git_test(root: &Path, args: &[&str]) -> String {
        let output = TestCommand::new("git")
            .current_dir(root)
            .args(args)
            .output()
            .unwrap();
        assert!(
            output.status.success(),
            "git {args:?} failed: {}",
            String::from_utf8_lossy(&output.stderr)
        );
        String::from_utf8(output.stdout).unwrap().trim().to_owned()
    }

    fn write_test(root: &Path, path: &str, bytes: &[u8]) {
        let destination = root.join(path);
        fs::create_dir_all(destination.parent().unwrap()).unwrap();
        fs::write(destination, bytes).unwrap();
    }

    fn development_import_fixture() -> (tempfile::TempDir, SourceImportDevelopmentArgs) {
        let temp = tempfile::tempdir().unwrap();
        let root = temp.path();
        git_test(root, &["init", "-q"]);
        git_test(root, &["config", "user.name", "SFM Test"]);
        git_test(root, &["config", "user.email", "sfm-test@example.invalid"]);
        let minecraft = "platform/minecraft/";
        for name in [
            "build.gradle",
            "settings.gradle",
            "gradle.properties",
            "gradlew",
            "gradlew.bat",
            "sfm-toolchain.lock.json",
            "gradle/wrapper/gradle-wrapper.jar",
            "gradle/wrapper/gradle-wrapper.properties",
        ] {
            write_test(root, &format!("{minecraft}{name}"), name.as_bytes());
        }
        for (name, bytes) in [
            ("same.txt", b"same\n".as_slice()),
            ("changed.txt", b"canonical\n".as_slice()),
            ("canonical-only.txt", b"canonical\n".as_slice()),
        ] {
            write_test(root, &format!("{minecraft}src/{name}"), bytes);
        }
        git_test(root, &["add", "--", "platform/minecraft"]);
        git_test(root, &["commit", "-qm", "canonical"]);
        let canonical_commit = git_test(root, &["rev-parse", "HEAD"]);
        write_test(root, &format!("{minecraft}src/changed.txt"), b"target\n");
        fs::remove_file(root.join(format!("{minecraft}src/canonical-only.txt"))).unwrap();
        write_test(
            root,
            &format!("{minecraft}src/target-only.txt"),
            b"target\n",
        );
        write_test(
            root,
            &format!("{minecraft}gradle.properties"),
            b"target gradle\n",
        );
        git_test(root, &["add", "-A", "--", "platform/minecraft"]);
        git_test(root, &["commit", "-qm", "target"]);
        let target_commit = git_test(root, &["rev-parse", "HEAD"]);
        let args = SourceImportDevelopmentArgs {
            repo_root: root.to_path_buf(),
            target: "test".to_owned(),
            canonical_commit,
            target_commit,
            expected_gradle_files: 8,
            expected_gradle_changed_path: vec!["gradle.properties".to_owned()],
        };
        (temp, args)
    }

    fn development_fixture_import_fixture()
    -> (tempfile::TempDir, SourceImportDevelopmentFixturesArgs) {
        let (temp, source_args) = development_import_fixture();
        let root = temp.path();
        write_test(root, "examples/01.sfm", b"example\n");
        write_test(
            root,
            "docs/architecture/fixtures/review.json",
            b"{\"pinned\":true}\n",
        );
        write_test(root, "docs/architecture/other.json", b"not a fixture\n");
        git_test(root, &["add", "--", "examples", "docs/architecture"]);
        git_test(root, &["commit", "-qm", "fixtures"]);
        let args = SourceImportDevelopmentFixturesArgs {
            repo_root: root.to_path_buf(),
            target: "test".to_owned(),
            canonical_commit: source_args.canonical_commit,
            target_commit: git_test(root, &["rev-parse", "HEAD"]),
            expected_fixture_files: 2,
            expected_fixture_root: vec![
                "examples".to_owned(),
                "docs/architecture/fixtures".to_owned(),
            ],
        };
        (temp, args)
    }

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
        let importer = figue::from_slice::<Cli>(&[
            "source",
            "import-development",
            "--repo-root",
            ".",
            "--target",
            "1.19.4",
            "--canonical-commit",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "--target-commit",
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            "--expected-gradle-files",
            "153",
            "--expected-gradle-changed-path",
            "gradle.properties",
            "--expected-gradle-changed-path",
            "settings.gradle",
        ])
        .into_result()
        .expect("development import command should parse")
        .get_silent();
        let Command::Source(SourceArgs {
            command: SourceCommand::ImportDevelopment(importer),
        }) = importer.command
        else {
            panic!("expected development import command");
        };
        assert_eq!(importer.target, "1.19.4");
        assert_eq!(importer.expected_gradle_files, 153);
        assert_eq!(
            importer.expected_gradle_changed_path,
            ["gradle.properties", "settings.gradle"]
        );
        let fixture_importer = figue::from_slice::<Cli>(&[
            "source",
            "import-development-fixtures",
            "--repo-root",
            ".",
            "--target",
            "1.19.4",
            "--canonical-commit",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "--target-commit",
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            "--expected-fixture-files",
            "26",
            "--expected-fixture-root",
            "examples",
            "--expected-fixture-root",
            "docs/architecture/fixtures",
        ])
        .into_result()
        .expect("development fixture import command should parse")
        .get_silent();
        let Command::Source(SourceArgs {
            command: SourceCommand::ImportDevelopmentFixtures(fixture_importer),
        }) = fixture_importer.command
        else {
            panic!("expected development fixture import command");
        };
        assert_eq!(fixture_importer.expected_fixture_files, 26);
        assert_eq!(fixture_importer.expected_fixture_root.len(), 2);
        for verb in ["build", "run"] {
            let parsed = figue::from_slice::<Cli>(&[
                "source",
                verb,
                "--repo-root",
                ".",
                "--target",
                "1.19.2",
                "--preset",
                "current-development-pilot",
                "--output-root",
                "source-projections/1.19.2",
            ])
            .into_result()
            .expect("development command should parse")
            .get_silent();
            assert!(matches!(parsed.command, Command::Source(_)));
        }
        let parsed = figue::from_slice::<Cli>(&[
            "source",
            "build",
            "--repo-root",
            ".",
            "--target",
            "1.19.2",
            "--preset",
            "current-development-pilot",
            "--output-root",
            "C:/scratch/projected",
            "--gradle-profile",
            "rust-toolchain",
            "--offline",
        ])
        .into_result()
        .expect("development build options should parse")
        .get_silent();
        let Command::Source(SourceArgs {
            command: SourceCommand::Build(build),
        }) = parsed.command
        else {
            panic!("expected source build command");
        };
        assert_eq!(build.gradle_profile.as_deref(), Some("rust-toolchain"));
        assert!(build.offline);
    }

    #[test]
    fn development_import_uses_reviewed_gradle_inventory_and_is_repeatable() {
        let (temp, mut args) = development_import_fixture();
        let import = temp
            .path()
            .join("platform/minecraft/development-baselines/test");
        args.expected_gradle_changed_path = vec!["settings.gradle".to_owned()];
        let error = args.invoke_in(temp.path()).unwrap_err();
        assert!(format!("{error:?}").contains("Gradle differences changed"));
        assert!(!import.exists());

        let (temp, args) = development_import_fixture();
        let import = temp
            .path()
            .join("platform/minecraft/development-baselines/test");
        args.invoke_in(temp.path()).unwrap();
        let source = fs::read(import.join("import.json")).unwrap();
        let report: crate::source_projection::release_baseline::ReleaseBaselineReport =
            facet_json::from_str(std::str::from_utf8(&source).unwrap()).unwrap();
        assert_eq!(
            (
                report.targets[0].unchanged,
                report.targets[0].changed,
                report.targets[0].release_only,
                report.targets[0].canonical_only,
            ),
            (1, 1, 1, 1)
        );
        assert_eq!(
            fs::read(import.join("overlays/src/changed.txt")).unwrap(),
            b"target\n"
        );
        assert_eq!(
            fs::read(import.join("gradle-project/gradle.properties")).unwrap(),
            b"target gradle\n"
        );
        let provenance = fs::read(import.join("gradle-provenance.json")).unwrap();
        let repeat = SourceImportDevelopmentArgs {
            repo_root: temp.path().to_path_buf(),
            target: "test".to_owned(),
            canonical_commit: report.canonical_head.clone(),
            target_commit: report.targets[0].tag_commit.clone(),
            expected_gradle_files: 8,
            expected_gradle_changed_path: vec!["gradle.properties".to_owned()],
        };
        repeat.invoke_in(temp.path()).unwrap();
        assert_eq!(fs::read(import.join("import.json")).unwrap(), source);
        assert_eq!(
            fs::read(import.join("gradle-provenance.json")).unwrap(),
            provenance
        );
    }

    #[test]
    fn development_import_rejects_edited_source_before_writing_gradle() {
        let (temp, args) = development_import_fixture();
        let spec = DevelopmentHeadSpec {
            target_id: args.target.clone(),
            canonical_commit: args.canonical_commit.clone(),
            target_commit: args.target_commit.clone(),
        };
        materialize_committed_source_import(temp.path(), &spec).unwrap();
        let import = temp
            .path()
            .join("platform/minecraft/development-baselines/test");
        fs::write(import.join("overlays/src/changed.txt"), b"edited\n").unwrap();
        let error = args.invoke_in(temp.path()).unwrap_err();
        assert!(format!("{error:?}").contains("differs from pinned tag bytes"));
        assert!(!import.join("gradle-project").exists());
        assert!(!import.join("gradle-provenance.json").exists());
    }

    #[test]
    fn development_import_rejects_edited_gradle_before_writing_source() {
        let (temp, args) = development_import_fixture();
        let spec = DevelopmentHeadSpec {
            target_id: args.target.clone(),
            canonical_commit: args.canonical_commit.clone(),
            target_commit: args.target_commit.clone(),
        };
        materialize_development_gradle_inputs(temp.path(), &spec, 8, &["gradle.properties"])
            .unwrap();
        let import = temp
            .path()
            .join("platform/minecraft/development-baselines/test");
        fs::write(import.join("gradle-project/gradle.properties"), b"edited\n").unwrap();
        let error = args.invoke_in(temp.path()).unwrap_err();
        assert!(format!("{error:?}").contains("differs from pinned tag bytes"));
        assert!(!import.join("import.json").exists());
        assert!(!import.join("overlays").exists());
    }

    #[test]
    fn development_fixture_import_requires_reviewed_roots_and_count_before_writing() {
        let (temp, mut args) = development_fixture_import_fixture();
        let import = temp
            .path()
            .join("platform/minecraft/development-baselines/test");
        args.expected_fixture_root = vec!["examples".to_owned()];
        let error = args.invoke_in(temp.path()).unwrap_err();
        assert!(format!("{error:?}").contains("reviewed fixture roots"));
        assert!(!import.exists());

        let (temp, mut args) = development_fixture_import_fixture();
        let import = temp
            .path()
            .join("platform/minecraft/development-baselines/test");
        args.expected_fixture_files = 3;
        let error = args.invoke_in(temp.path()).unwrap_err();
        assert!(format!("{error:?}").contains("pinned fixture tree has 2 files"));
        assert!(!import.exists());
    }

    #[test]
    fn development_fixture_import_is_idempotent_and_rejects_edited_imports() {
        let (temp, args) = development_fixture_import_fixture();
        let import = temp
            .path()
            .join("platform/minecraft/development-baselines/test");
        args.clone().invoke_in(temp.path()).unwrap();
        let provenance = fs::read(import.join("project-fixtures-provenance.json")).unwrap();
        assert_eq!(
            fs::read(import.join("project-fixtures/examples/01.sfm")).unwrap(),
            b"example\n"
        );
        assert_eq!(
            fs::read(import.join("project-fixtures/docs/architecture/fixtures/review.json"))
                .unwrap(),
            b"{\"pinned\":true}\n"
        );
        assert!(
            !import
                .join("project-fixtures/docs/architecture/other.json")
                .exists()
        );
        args.clone().invoke_in(temp.path()).unwrap();
        assert_eq!(
            fs::read(import.join("project-fixtures-provenance.json")).unwrap(),
            provenance
        );

        let example = import.join("project-fixtures/examples/01.sfm");
        fs::write(&example, b"edited\n").unwrap();
        let error = args.invoke_in(temp.path()).unwrap_err();
        assert!(format!("{error:?}").contains("differs from pinned tag bytes"));
        assert_eq!(fs::read(&example).unwrap(), b"edited\n");
        assert_eq!(
            fs::read(import.join("project-fixtures-provenance.json")).unwrap(),
            provenance
        );
    }

    #[test]
    fn development_root_rejects_checked_in_and_authored_roots() {
        let repo = tempfile::tempdir().unwrap();
        let scratch = repo.path().parent().unwrap().join("source-projections/dev");
        assert_eq!(
            development_output_root(repo.path(), &scratch).unwrap(),
            scratch
        );
        for forbidden in [
            ".",
            "source-projections/dev",
            "platform/minecraft",
            "platform/minecraft/src",
            "platform/minecraft/mc-version/1.19.2",
            "platform/minecraft/release-baselines/4.34.0-1.19.2",
            ".git/objects",
            "../outside",
        ] {
            let _ = development_output_root(repo.path(), Path::new(forbidden)).unwrap_err();
        }
    }

    #[test]
    fn gradle_task_is_one_safe_task_path() {
        for valid in ["jar", "runClient", ":subproject:reobfJar"] {
            validate_gradle_task(valid).unwrap();
        }
        for invalid in ["", ":", "foo::bar", "--offline", "jar clean", "../jar"] {
            let _ = validate_gradle_task(invalid).unwrap_err();
        }
        validate_gradle_profile("rust-toolchain").unwrap();
        for invalid in ["", "rust toolchain", "../release", "--help"] {
            let _ = validate_gradle_profile(invalid).unwrap_err();
        }
    }

    #[test]
    fn only_legacy_forge_game_test_runs_get_the_exit_override() {
        let repo = Path::new("repo");
        let script = Path::new(
            "repo/platform/cli/sfm-propagate-changes/gradle/forge-game-test-no-force-exit.init.gradle",
        );
        for target in ["1.19.2", "1.19.4"] {
            assert_eq!(
                game_test_exit_override(repo, target, "runGameTestServer").as_deref(),
                Some(script)
            );
        }
        for target in ["1.20", "1.21.1", "26.1.2"] {
            assert!(game_test_exit_override(repo, target, "runGameTestServer").is_none());
        }
        assert!(game_test_exit_override(repo, "1.19.4", "jar").is_none());
    }

    #[test]
    fn projected_gradle_task_uses_child_java_home_and_retains_project() {
        let project = tempfile::tempdir().unwrap();
        let wrapper = project.path().join(if cfg!(windows) {
            "gradlew.bat"
        } else {
            "gradlew"
        });
        #[cfg(windows)]
        fs::write(
            &wrapper,
            b"@echo off\r\necho %JAVA_HOME%>invocation.txt\r\necho %1 %2 %3 %4 %5 %6>>invocation.txt\r\nexit /b 0\r\n",
        )
        .unwrap();
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt as _;
            fs::write(
                &wrapper,
                b"#!/bin/sh\nprintf '%s\\n' \"$JAVA_HOME\" > invocation.txt\nprintf '%s %s %s %s %s %s\\n' \"$1\" \"$2\" \"$3\" \"$4\" \"$5\" \"$6\" >> invocation.txt\n",
            )
            .unwrap();
            fs::set_permissions(&wrapper, fs::Permissions::from_mode(0o755)).unwrap();
        }
        let java_home = project.path().join("fake-jdk");
        run_project_gradle(
            project.path(),
            "jar",
            "4.34.0-dev.abcdef012345",
            &java_home,
            &GradleRunOptions {
                profile: Some("rust-toolchain"),
                offline: false,
                init_script: None,
            },
            &CancellationToken::new(),
        )
        .unwrap();
        let invocation = fs::read_to_string(project.path().join("invocation.txt")).unwrap();
        assert!(invocation.contains("fake-jdk"));
        assert!(invocation.contains("--no-daemon"), "{invocation:?}");
        assert!(
            invocation.contains("-Pmod_version=4.34.0-dev.abcdef012345"),
            "{invocation:?}"
        );
        assert!(invocation.contains("jar"), "{invocation:?}");
        assert!(
            invocation.contains("-PsfmProfile=rust-toolchain"),
            "{invocation:?}"
        );
        assert!(project.path().is_dir());

        let init_script = project.path().join("test.init.gradle");
        fs::write(&init_script, b"// Test-only Gradle script\n").unwrap();
        run_project_gradle(
            project.path(),
            "runGameTestServer",
            "4.34.0-dev.abcdef012345",
            &java_home,
            &GradleRunOptions {
                profile: None,
                offline: true,
                init_script: Some(&init_script),
            },
            &CancellationToken::new(),
        )
        .unwrap();
        let invocation = fs::read_to_string(project.path().join("invocation.txt")).unwrap();
        assert!(invocation.contains("--init-script"), "{invocation:?}");
        assert!(invocation.contains("test.init.gradle"), "{invocation:?}");
        assert!(invocation.contains("--offline"), "{invocation:?}");
        assert!(invocation.contains("runGameTestServer"), "{invocation:?}");
    }

    #[test]
    fn development_version_changes_with_projected_content_and_never_equals_release() {
        let project = tempfile::tempdir().unwrap();
        fs::write(
            project.path().join("gradle.properties"),
            b"mod_name=SFM\nminecraft_version=1.19.2\nmod_version=4.34.0\n",
        )
        .unwrap();
        fs::write(project.path().join(MANIFEST_FILE), b"first projection").unwrap();
        let first = development_mod_version(project.path()).unwrap();
        assert!(first.starts_with("4.34.0-dev."));
        assert_ne!(first, "4.34.0");
        assert_eq!(first, development_mod_version(project.path()).unwrap());
        fs::write(project.path().join(MANIFEST_FILE), b"second projection").unwrap();
        assert_ne!(first, development_mod_version(project.path()).unwrap());

        fs::create_dir_all(project.path().join("build/libs")).unwrap();
        let jar = project
            .path()
            .join("build/libs")
            .join(format!("SFM-MC1.19.2-{first}.jar"));
        fs::write(&jar, b"jar").unwrap();
        report_build_artifacts(project.path(), "jar", &first).unwrap();
        fs::remove_file(&jar).unwrap();
        let _ = report_build_artifacts(project.path(), "jar", &first).unwrap_err();

        let slim = project
            .path()
            .join("build/libs")
            .join(format!("SFM-MC1.19.2-{first}-slim.jar"));
        fs::write(&slim, b"jar").unwrap();
        report_build_artifacts(project.path(), "jar", &first).unwrap();
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
                canonical_project_fixture_provenance_sha256: None,
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
