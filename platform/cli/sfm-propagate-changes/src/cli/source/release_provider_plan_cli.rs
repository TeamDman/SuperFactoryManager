//! Read-only provider metadata preflight for one verified projection package.
//!
//! Every successful invocation rehashes the reviewed completion manifest,
//! frozen inventory and all ten packaged JARs. Provider identities and the
//! changelog are explicit reviewer inputs, never inferred from branch tags,
//! shared JAR directories, credentials or remote APIs.

use super::release_package_verify_cli::ReleasePackageVerifyArgs;
use super::release_package_verify_cli::VerifiedReleasePackage;
use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use crate::modrinth::ModrinthCreateVersionPayload;
use crate::source_projection::candidate_lock::checked_directory;
use crate::source_projection::candidate_lock::checked_file;
use crate::source_projection::provenance::sha256;
use eyre::Result;
use eyre::ensure;
use facet::Facet;
use figue::{self as args};
use std::fs::File;
use std::io::Read as _;
use std::path::Path;
use std::path::PathBuf;

const PLAN_SCHEMA: &str = "sfm:source_release_provider_plan@1";
const PLAN_SCOPE: &str = "read-only reviewed-provider-intent for one verified ten-JAR package; no credentials, remote checks, tag, upload, promotion or publication";
const MAX_CHANGELOG_BYTES: u64 = 1024 * 1024;
const DUAL_1201_POLICY: &str = "dual-forge-neoforge";
const NEOFORGE_ONLY_1201_POLICY: &str = "neoforge-only";

#[derive(Clone, Debug, Facet)]
pub struct ReleaseProviderPlanArgs {
    /// Existing absolute directory created by source release-package.
    #[facet(args::named)]
    pub package_root: PathBuf,
    /// Separately reviewed SHA-256 of the exact release-package.json bytes.
    #[facet(args::named)]
    pub completion_manifest_sha256: String,
    /// Full source commit reviewed for this package, including if historical.
    #[facet(args::named)]
    pub reviewed_source_commit: String,
    /// Reviewer-selected GitHub release tag; existence and target are not checked.
    #[facet(args::named)]
    pub reviewed_tag: String,
    /// Explicit GitHub repository in owner/name form.
    #[facet(args::named)]
    pub github_repo: String,
    /// Explicit Modrinth project ID or slug.
    #[facet(args::named)]
    pub modrinth_project: String,
    /// Explicit positive `CurseForge` project ID.
    #[facet(args::named)]
    pub curseforge_project: u64,
    /// Absolute path to the exact reviewed UTF-8 release notes body.
    #[facet(args::named)]
    pub changelog_file: PathBuf,
    /// Separately reviewed SHA-256 of the exact changelog file bytes.
    #[facet(args::named)]
    pub changelog_sha256: String,
    /// Explicit 1.20.1 Modrinth choice: dual-forge-neoforge or neoforge-only.
    #[facet(args::named)]
    pub modrinth_1201_loader_policy: String,
}

#[derive(Debug, Facet)]
#[expect(
    clippy::struct_excessive_bools,
    reason = "independent, machine-readable negative verification and authorization assertions are the report's safety boundary"
)]
struct ReleaseProviderPlanReport {
    schema: String,
    scope: String,
    completion_manifest_sha256: String,
    inventory_sha256: String,
    lock_sha256: String,
    package_source_commit: String,
    reviewed_source_commit_matches_package: bool,
    current_head_checked: bool,
    tag_object_checked: bool,
    remote_project_ownership_checked: bool,
    curseforge_game_version_ids_checked: bool,
    publication_authorized: bool,
    candidate_preset_id: String,
    mod_version: String,
    changelog_sha256: String,
    changelog_utf8_bytes: usize,
    github: GitHubProviderIntent,
    modrinth: ModrinthProviderIntent,
    curseforge: CurseForgeProviderIntent,
    target_count: usize,
    targets: Vec<ReleaseProviderTarget>,
}

#[derive(Debug, Facet)]
struct GitHubProviderIntent {
    repository: String,
    reviewed_tag: String,
    release_title: String,
}

#[derive(Debug, Facet)]
struct ModrinthProviderIntent {
    project: String,
    loader_policy_1201: String,
}

#[derive(Debug, Facet)]
struct CurseForgeProviderIntent {
    project_id: u64,
    game_version_ids_require_remote_resolution: bool,
}

#[derive(Debug, Facet)]
struct ReleaseProviderTarget {
    target_id: String,
    minecraft_version: String,
    package_loader: String,
    file_name: String,
    sha256: String,
    display_name: String,
    modrinth_version_number: String,
    modrinth_game_versions: Vec<String>,
    modrinth_loaders: Vec<String>,
    curseforge_metadata_names: Vec<String>,
    curseforge_release_type: String,
    curseforge_changelog_type: String,
}

impl ReleaseProviderPlanArgs {
    /// Reverify the entire package and emit only reviewed, path-free intent.
    ///
    /// # Errors
    ///
    /// Rejects an invalid reviewer input, changed package or changelog, or
    /// any provider metadata that disagrees with the typed package mapping.
    pub(super) fn invoke_in(self, cancellation: &CancellationToken) -> Result<CliOutput> {
        Ok(CliOutput::facet(self.plan_in(cancellation)?))
    }

    fn plan_in(self, cancellation: &CancellationToken) -> Result<ReleaseProviderPlanReport> {
        validate_provider_identity(&self)?;
        let verified = ReleasePackageVerifyArgs {
            package_root: self.package_root.clone(),
            completion_manifest_sha256: self.completion_manifest_sha256.clone(),
        }
        .verify_in(cancellation)?;
        ensure!(
            self.reviewed_source_commit == verified.manifest.source_commit,
            "--reviewed-source-commit differs from the verified package source commit"
        );
        ensure!(
            self.reviewed_tag == verified.manifest.mod_version
                || self
                    .reviewed_tag
                    .starts_with(&format!("{}-", verified.manifest.mod_version)),
            "--reviewed-tag does not identify the verified package mod version"
        );
        let changelog = read_reviewed_changelog(&self.changelog_file, &self.changelog_sha256)?;
        ReleaseProviderPlanReport::from_verified(self, verified, &changelog)
    }
}

impl ReleaseProviderPlanReport {
    fn from_verified(
        args: ReleaseProviderPlanArgs,
        verified: VerifiedReleasePackage,
        changelog: &str,
    ) -> Result<Self> {
        let VerifiedReleasePackage {
            completion_manifest_sha256,
            manifest,
            inventory,
        } = verified;
        // The verifier established full equality and target order. Use the
        // typed target mapping for provider metadata, never a guessed version
        // from the packaged filename. The extra marker check prevents a future
        // filename-based publisher from silently choosing a different version.
        let mut targets = Vec::with_capacity(manifest.targets.len());
        for (source, packaged) in inventory.targets.into_iter().zip(manifest.targets) {
            validate_single_mc_marker(
                &packaged.file_name,
                &source.minecraft_version,
                &manifest.mod_version,
            )?;
            let modrinth_loaders = if source.target_id == "1.20.1" {
                match args.modrinth_1201_loader_policy.as_str() {
                    DUAL_1201_POLICY => vec!["forge".to_owned(), "neoforge".to_owned()],
                    NEOFORGE_ONLY_1201_POLICY => vec!["neoforge".to_owned()],
                    _ => unreachable!("loader policy was validated before package verification"),
                }
            } else {
                vec![source.loader.clone()]
            };
            let curseforge_loaders = if source.target_id == "1.20.1" {
                // This is the existing CurseForge publisher's reviewed name
                // intent, not a claim that remote numeric IDs were resolved.
                vec!["Forge".to_owned(), "NeoForge".to_owned()]
            } else if source.loader == "forge" {
                vec!["Forge".to_owned()]
            } else {
                vec!["NeoForge".to_owned()]
            };
            let mut curseforge_metadata_names = vec![
                "Client".to_owned(),
                "Server".to_owned(),
                source.minecraft_version.clone(),
            ];
            curseforge_metadata_names.extend(curseforge_loaders);
            curseforge_metadata_names.push(format!("Java {}", source.jdk_major));
            let display_name = format!(
                "Super Factory Manager MC{} v{}",
                source.minecraft_version, manifest.mod_version
            );
            let modrinth_game_versions = vec![source.minecraft_version.clone()];
            let modrinth_payload = ModrinthCreateVersionPayload::for_release(
                &display_name,
                &manifest.mod_version,
                changelog,
                &modrinth_game_versions,
                &modrinth_loaders,
                &args.modrinth_project,
            );
            targets.push(ReleaseProviderTarget {
                target_id: source.target_id,
                minecraft_version: source.minecraft_version.clone(),
                package_loader: source.loader,
                file_name: packaged.file_name,
                sha256: packaged.sha256,
                display_name: modrinth_payload.name,
                modrinth_version_number: modrinth_payload.version_number,
                modrinth_game_versions: modrinth_payload.game_versions,
                modrinth_loaders: modrinth_payload.loaders,
                curseforge_metadata_names,
                curseforge_release_type: "release".to_owned(),
                curseforge_changelog_type: "markdown".to_owned(),
            });
        }
        Ok(Self {
            schema: PLAN_SCHEMA.to_owned(),
            scope: PLAN_SCOPE.to_owned(),
            completion_manifest_sha256,
            inventory_sha256: manifest.inventory_sha256,
            lock_sha256: manifest.lock_sha256,
            package_source_commit: manifest.source_commit,
            reviewed_source_commit_matches_package: true,
            current_head_checked: false,
            tag_object_checked: false,
            remote_project_ownership_checked: false,
            curseforge_game_version_ids_checked: false,
            publication_authorized: false,
            candidate_preset_id: manifest.candidate_preset_id,
            mod_version: manifest.mod_version.clone(),
            changelog_sha256: args.changelog_sha256,
            changelog_utf8_bytes: changelog.len(),
            github: GitHubProviderIntent {
                repository: args.github_repo,
                reviewed_tag: args.reviewed_tag,
                release_title: format!("v{}", manifest.mod_version),
            },
            modrinth: ModrinthProviderIntent {
                project: args.modrinth_project,
                loader_policy_1201: args.modrinth_1201_loader_policy,
            },
            curseforge: CurseForgeProviderIntent {
                project_id: args.curseforge_project,
                game_version_ids_require_remote_resolution: true,
            },
            target_count: targets.len(),
            targets,
        })
    }
}

fn validate_provider_identity(args: &ReleaseProviderPlanArgs) -> Result<()> {
    let (owner, repo) = args
        .github_repo
        .split_once('/')
        .ok_or_else(|| eyre::eyre!("--github-repo must be owner/name"))?;
    ensure!(
        !repo.contains('/')
            && safe_provider_component(owner, 100)
            && safe_provider_component(repo, 100),
        "--github-repo must contain safe owner/name components"
    );
    ensure!(
        safe_provider_component(&args.modrinth_project, 100),
        "--modrinth-project must be a safe project ID or slug"
    );
    ensure!(
        args.curseforge_project > 0,
        "--curseforge-project must be a positive project ID"
    );
    ensure!(
        safe_provider_component(&args.reviewed_tag, 128),
        "--reviewed-tag must be a safe single Git tag component"
    );
    ensure!(
        matches!(
            args.modrinth_1201_loader_policy.as_str(),
            DUAL_1201_POLICY | NEOFORGE_ONLY_1201_POLICY
        ),
        "--modrinth-1201-loader-policy must be dual-forge-neoforge or neoforge-only"
    );
    Ok(())
}

fn safe_provider_component(value: &str, maximum_len: usize) -> bool {
    !value.is_empty()
        && value.len() <= maximum_len
        && value != "."
        && !value.contains("..")
        && value
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || matches!(byte, b'-' | b'_' | b'.'))
}

fn validate_single_mc_marker(file_name: &str, mc_version: &str, mod_version: &str) -> Result<()> {
    let marker = file_name
        .split_once("-MC")
        .and_then(|(_, rest)| rest.split_once('-').map(|(version, _)| version))
        .ok_or_else(|| eyre::eyre!("packaged JAR has no Minecraft version marker"))?;
    ensure!(
        file_name.matches("-MC").count() == 1
            && marker == mc_version
            && file_name.ends_with(&format!("-MC{mc_version}-{mod_version}.jar")),
        "packaged JAR filename-derived Minecraft version differs from typed target mapping"
    );
    Ok(())
}

fn read_reviewed_changelog(path: &Path, expected_sha256: &str) -> Result<String> {
    let parent = path
        .parent()
        .ok_or_else(|| eyre::eyre!("--changelog-file needs an absolute parent directory"))?;
    let root = checked_directory(parent)?;
    let name = path
        .file_name()
        .and_then(|name| name.to_str())
        .ok_or_else(|| eyre::eyre!("--changelog-file needs a UTF-8 filename"))?;
    let file_path = checked_file(&root, name)?;
    let file = File::open(file_path)?;
    ensure!(
        file.metadata()?.len() <= MAX_CHANGELOG_BYTES,
        "reviewed changelog exceeds the {MAX_CHANGELOG_BYTES}-byte limit"
    );
    let mut bytes = Vec::new();
    file.take(MAX_CHANGELOG_BYTES + 1).read_to_end(&mut bytes)?;
    ensure!(
        bytes.len() as u64 <= MAX_CHANGELOG_BYTES,
        "reviewed changelog exceeds the {MAX_CHANGELOG_BYTES}-byte limit"
    );
    let text = std::str::from_utf8(&bytes)?;
    ensure!(!text.trim().is_empty(), "reviewed changelog is empty");
    ensure!(
        sha256(&bytes) == expected_sha256,
        "reviewed changelog SHA-256 differs from --changelog-sha256"
    );
    Ok(text.to_owned())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::cli::Cli;
    use crate::cli::Command;
    use crate::cli::output::OutputFormat;
    use crate::cli::source::CandidateVerifyArgs;
    use crate::cli::source::ReleasePackageArgs;
    use crate::cli::source::SourceArgs;
    use crate::cli::source::SourceCommand;
    use crate::cli::source::candidate_lock_cli::verify_candidate_in;
    use crate::cli::source::release_inventory_cli::ReleaseInventory;
    use crate::cli::source::release_package_cli::COMPLETION_FILE;
    use crate::source_projection::candidate_lock::tests::Fixture;
    use std::fs;

    fn packaged_candidate() -> (Fixture, ReleaseProviderPlanArgs) {
        let fixture = Fixture::new();
        let scratch = fixture.repo().parent().unwrap();
        let lock_path = scratch.join("reviewed-candidate-lock.json");
        let lock_bytes = facet_json::to_string_pretty(fixture.lock()).unwrap() + "\n";
        fs::write(&lock_path, &lock_bytes).unwrap();
        let lock_sha256 = sha256(lock_bytes.as_bytes());
        let verified = verify_candidate_in(
            &CancellationToken::new(),
            fixture.repo(),
            fixture.lock(),
            fixture.roots(),
            lock_sha256.clone(),
            None,
        )
        .unwrap();
        let inventory = ReleaseInventory::from_verified(fixture.lock(), &verified).unwrap();
        let inventory_path = scratch.join("frozen-inventory.json");
        fs::write(
            &inventory_path,
            facet_json::to_string_pretty(&inventory).unwrap() + "\n",
        )
        .unwrap();
        let package_root = scratch.join("package");
        ReleasePackageArgs {
            candidate: CandidateVerifyArgs {
                repo_root: fixture.repo().to_path_buf(),
                lock: lock_path,
                candidate_root: fixture
                    .roots()
                    .iter()
                    .map(|(target, root)| format!("{target}={}", root.display()))
                    .collect(),
            },
            inventory: inventory_path,
            candidate_lock_sha256: lock_sha256,
            output_root: package_root.clone(),
        }
        .invoke_in(&CancellationToken::new(), fixture.repo())
        .unwrap();
        let completion_manifest_sha256 =
            sha256(&fs::read(package_root.join(COMPLETION_FILE)).unwrap());
        let changelog_file = scratch.join("reviewed-changelog.md");
        let changelog = format!("Reviewed notes for {}\n", fixture.lock().mod_version);
        fs::write(&changelog_file, &changelog).unwrap();
        let reviewed_source_commit = fixture.lock().source_commit.clone();
        let reviewed_tag = format!("{}-26.1.2", fixture.lock().mod_version);
        (
            fixture,
            ReleaseProviderPlanArgs {
                package_root,
                completion_manifest_sha256,
                reviewed_source_commit,
                reviewed_tag,
                github_repo: "example/sfm".to_owned(),
                modrinth_project: "example-project".to_owned(),
                curseforge_project: 123,
                changelog_file,
                changelog_sha256: sha256(changelog.as_bytes()),
                modrinth_1201_loader_policy: DUAL_1201_POLICY.to_owned(),
            },
        )
    }

    fn assert_rejected(args: ReleaseProviderPlanArgs) {
        assert!(args.plan_in(&CancellationToken::new()).is_err());
    }

    #[test]
    fn cli_requires_explicit_reviewer_and_provider_inputs() {
        let parsed = figue::from_slice::<Cli>(&[
            "source",
            "release-provider-plan",
            "--package-root",
            "C:/reviewed/package",
            "--completion-manifest-sha256",
            "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "--reviewed-source-commit",
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            "--reviewed-tag",
            "4.34.0-26.1.2",
            "--github-repo",
            "example/sfm",
            "--modrinth-project",
            "example-project",
            "--curseforge-project",
            "123",
            "--changelog-file",
            "C:/reviewed/changelog.md",
            "--changelog-sha256",
            "sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
            "--modrinth-1201-loader-policy",
            DUAL_1201_POLICY,
        ])
        .into_result()
        .unwrap()
        .get_silent();
        let Command::Source(SourceArgs {
            command: SourceCommand::ReleaseProviderPlan(args),
        }) = parsed.command
        else {
            panic!("expected source release-provider-plan command");
        };
        assert_eq!(args.github_repo, "example/sfm");
        assert_eq!(args.modrinth_1201_loader_policy, DUAL_1201_POLICY);
        assert!(
            figue::from_slice::<Cli>(&[
                "source",
                "release-provider-plan",
                "--package-root",
                "C:/reviewed/package",
            ])
            .into_result()
            .is_err()
        );
    }

    #[test]
    fn verified_package_emits_path_free_ten_target_provider_intent() {
        let (fixture, args) = packaged_candidate();
        let before = fs::read_dir(&args.package_root).unwrap().count();
        let report = args.clone().plan_in(&CancellationToken::new()).unwrap();
        assert_eq!(report.target_count, 10);
        assert_eq!(report.package_source_commit, fixture.lock().source_commit);
        assert!(report.reviewed_source_commit_matches_package);
        assert!(!report.current_head_checked);
        assert!(!report.tag_object_checked);
        assert!(!report.remote_project_ownership_checked);
        assert!(!report.curseforge_game_version_ids_checked);
        assert!(!report.publication_authorized);
        assert!(report.curseforge.game_version_ids_require_remote_resolution);
        assert_eq!(report.github.reviewed_tag, args.reviewed_tag);
        assert_eq!(report.modrinth.loader_policy_1201, DUAL_1201_POLICY);
        assert_eq!(
            report.changelog_utf8_bytes,
            fs::read(&args.changelog_file).unwrap().len()
        );
        for target in &report.targets {
            let locked = fixture
                .lock()
                .targets
                .iter()
                .find(|locked| locked.target_id == target.target_id)
                .unwrap();
            assert_eq!(target.minecraft_version, locked.minecraft_version);
            assert_eq!(target.package_loader, locked.loader);
            assert_eq!(target.sha256, locked.production_jar_sha256);
            assert_eq!(
                target.display_name,
                format!(
                    "Super Factory Manager MC{} v{}",
                    locked.minecraft_version,
                    fixture.lock().mod_version
                )
            );
            assert_eq!(target.modrinth_version_number, fixture.lock().mod_version);
            assert_eq!(
                target.modrinth_game_versions,
                [locked.minecraft_version.clone()]
            );
            let expected_loaders = if locked.target_id == "1.20.1" {
                vec!["forge".to_owned(), "neoforge".to_owned()]
            } else {
                vec![locked.loader.clone()]
            };
            assert_eq!(target.modrinth_loaders, expected_loaders);
        }
        let transitional = report
            .targets
            .iter()
            .find(|target| target.target_id == "1.20.1")
            .unwrap();
        assert_eq!(transitional.package_loader, "neoforge");
        assert_eq!(transitional.modrinth_loaders, ["forge", "neoforge"]);
        assert!(
            transitional
                .curseforge_metadata_names
                .contains(&"Forge".to_owned())
        );
        let mc_121 = report
            .targets
            .iter()
            .find(|target| target.target_id == "1.21.0")
            .unwrap();
        assert_eq!(mc_121.minecraft_version, "1.21");
        assert_eq!(mc_121.modrinth_game_versions, ["1.21"]);

        let output = args
            .clone()
            .invoke_in(&CancellationToken::new())
            .unwrap()
            .render(Some(OutputFormat::Json), false)
            .unwrap()
            .unwrap();
        assert!(output.contains(PLAN_SCHEMA));
        assert!(!output.contains(&args.package_root.display().to_string()));
        assert!(!output.contains(&args.changelog_file.display().to_string()));
        assert!(!output.contains(&fixture.repo().display().to_string()));
        assert_eq!(fs::read_dir(&args.package_root).unwrap().count(), before);
    }

    #[test]
    fn transitional_modrinth_loader_choice_is_not_implicit() {
        let (_fixture, mut args) = packaged_candidate();
        args.modrinth_1201_loader_policy = NEOFORGE_ONLY_1201_POLICY.to_owned();
        let report = args.clone().plan_in(&CancellationToken::new()).unwrap();
        let transitional = report
            .targets
            .iter()
            .find(|target| target.target_id == "1.20.1")
            .unwrap();
        assert_eq!(transitional.modrinth_loaders, ["neoforge"]);
        assert_eq!(
            report.modrinth.loader_policy_1201,
            NEOFORGE_ONLY_1201_POLICY
        );
        args.modrinth_1201_loader_policy = "implicit".to_owned();
        assert_rejected(args);
    }

    #[test]
    fn rejects_unreviewed_identity_or_changelog() {
        let (_fixture, args) = packaged_candidate();
        let mut changed = args.clone();
        changed.reviewed_source_commit = "a".repeat(40);
        assert_rejected(changed);
        let mut changed = args.clone();
        changed.reviewed_tag = "4.33.0-26.1.2".to_owned();
        assert_rejected(changed);
        let mut changed = args.clone();
        changed.github_repo = "example/sfm/extra".to_owned();
        assert_rejected(changed);
        let mut changed = args.clone();
        changed.modrinth_project = "../project".to_owned();
        assert_rejected(changed);
        let mut changed = args.clone();
        changed.curseforge_project = 0;
        assert_rejected(changed);
        let mut changed = args.clone();
        changed.changelog_sha256 = sha256(b"different reviewed notes");
        assert_rejected(changed);
        fs::write(&args.changelog_file, "Changed notes\n").unwrap();
        assert_rejected(args);
    }

    #[test]
    fn rechecks_reviewed_package_bytes_in_same_invocation() {
        let (_fixture, args) = packaged_candidate();
        let mut wrong_digest = args.clone();
        wrong_digest.completion_manifest_sha256 = sha256(b"wrong reviewed package");
        assert_rejected(wrong_digest);
        let plan = args.clone().plan_in(&CancellationToken::new()).unwrap();
        let jar = args.package_root.join(&plan.targets[0].file_name);
        let original = fs::read(&jar).unwrap();
        fs::write(&jar, b"changed packaged JAR").unwrap();
        assert_rejected(args.clone());
        fs::write(&jar, original).unwrap();
        fs::write(args.package_root.join("unlisted.jar"), b"extra").unwrap();
        assert_rejected(args);
    }

    #[test]
    fn rejects_ambiguous_filename_markers_without_guessing_target() {
        assert!(validate_single_mc_marker("SFM-MC1.21-4.34.0.jar", "1.21", "4.34.0").is_ok());
        assert!(
            validate_single_mc_marker("SFM-MC1.20-MC1.21-4.34.0.jar", "1.21", "4.34.0").is_err()
        );
        assert!(validate_single_mc_marker("SFM-MC1.20-4.34.0.jar", "1.21", "4.34.0").is_err());
    }
}
