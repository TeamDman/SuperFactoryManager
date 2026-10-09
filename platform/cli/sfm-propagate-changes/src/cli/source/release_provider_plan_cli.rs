//! Read-only provider metadata preflight for one verified projection package.
//!
//! Every successful invocation rehashes the reviewed completion manifest,
//! frozen inventory and all ten packaged JARs. Provider identities and the
//! changelog are explicit reviewer inputs, never inferred from branch tags,
//! shared JAR directories, credentials or remote APIs.

use super::release_package_verify_cli::ReleasePackageVerifyArgs;
use super::release_package_verify_cli::VerifiedReleasePackage;
use crate::cancellation::CancellationToken;
use crate::cli::curseforge::RELEASE_CHANGELOG_TYPE;
use crate::cli::curseforge::RELEASE_FILE_TYPE;
use crate::cli::curseforge::game_version_names_for_release;
use crate::cli::github::release_title;
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
const MODRINTH_PREVIEW_SCHEMA: &str = "sfm:source_release_modrinth_request_preview@1";
const MODRINTH_PREVIEW_SCOPE: &str = "read-only exact Modrinth request metadata and verified package asset pairs; no credentials, network, tag, upload or publication";
pub(super) const MODRINTH_PREVIEW_NOTES_WARNING: &str = "Generated local paths are absent. Each request_metadata_json contains the complete caller-supplied reviewed notes in its changelog field. Notes may contain sensitive data or local paths. Review this output before storing or sharing it.";
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

#[derive(Clone, Debug, Facet)]
pub struct ReleaseModrinthRequestPreviewArgs {
    /// Reuse the complete reviewed provider-plan input and verification gate.
    #[facet(flatten)]
    pub provider_plan: ReleaseProviderPlanArgs,
}

pub(super) struct ReviewedProviderPlan {
    pub(super) report: ReleaseProviderPlanReport,
    pub(super) changelog: String,
    pub(super) verified: VerifiedReleasePackage,
}

#[derive(Debug, Facet)]
#[expect(
    clippy::struct_excessive_bools,
    reason = "these independent unchecked and authorization flags must match the provider-plan safety boundary"
)]
struct ReleaseModrinthRequestPreviewReport {
    schema: String,
    scope: String,
    reviewed_notes_disclosure_warning: String,
    completion_manifest_sha256: String,
    inventory_sha256: String,
    package_source_commit: String,
    current_head_checked: bool,
    tag_object_checked: bool,
    remote_project_ownership_checked: bool,
    candidate_preset_id: String,
    mod_version: String,
    modrinth_project: String,
    loader_policy_1201: String,
    changelog_sha256: String,
    publication_authorized: bool,
    target_count: usize,
    targets: Vec<ModrinthRequestPreviewTarget>,
}

#[derive(Debug, Facet)]
struct ModrinthRequestPreviewTarget {
    target_id: String,
    file_name: String,
    jar_sha256: String,
    request_metadata_json: String,
    request_metadata_sha256: String,
}

#[derive(Debug, Facet)]
#[expect(
    clippy::struct_excessive_bools,
    reason = "independent, machine-readable negative verification and authorization assertions are the report's safety boundary"
)]
pub(super) struct ReleaseProviderPlanReport {
    schema: String,
    scope: String,
    pub(super) completion_manifest_sha256: String,
    pub(super) inventory_sha256: String,
    lock_sha256: String,
    pub(super) package_source_commit: String,
    reviewed_source_commit_matches_package: bool,
    current_head_checked: bool,
    tag_object_checked: bool,
    remote_project_ownership_checked: bool,
    curseforge_game_version_ids_checked: bool,
    publication_authorized: bool,
    pub(super) candidate_preset_id: String,
    pub(super) mod_version: String,
    pub(super) changelog_sha256: String,
    changelog_utf8_bytes: usize,
    pub(super) github: GitHubProviderIntent,
    pub(super) modrinth: ModrinthProviderIntent,
    pub(super) curseforge: CurseForgeProviderIntent,
    target_count: usize,
    pub(super) targets: Vec<ReleaseProviderTarget>,
}

#[derive(Debug, Facet)]
pub(super) struct GitHubProviderIntent {
    pub(super) repository: String,
    pub(super) reviewed_tag: String,
    pub(super) release_title: String,
}

#[derive(Debug, Facet)]
pub(super) struct ModrinthProviderIntent {
    pub(super) project: String,
    pub(super) loader_policy_1201: String,
}

#[derive(Debug, Facet)]
pub(super) struct CurseForgeProviderIntent {
    pub(super) project_id: u64,
    pub(super) game_version_ids_require_remote_resolution: bool,
}

#[derive(Clone, Debug, Facet)]
pub(super) struct ReleaseProviderTarget {
    pub(super) target_id: String,
    pub(super) minecraft_version: String,
    pub(super) package_loader: String,
    pub(super) file_name: String,
    pub(super) sha256: String,
    pub(super) display_name: String,
    pub(super) modrinth_version_number: String,
    pub(super) modrinth_game_versions: Vec<String>,
    pub(super) modrinth_loaders: Vec<String>,
    pub(super) curseforge_metadata_names: Vec<String>,
    pub(super) curseforge_release_type: String,
    pub(super) curseforge_changelog_type: String,
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
        Ok(self.review_in(cancellation)?.report)
    }

    pub(super) fn review_in(
        self,
        cancellation: &CancellationToken,
    ) -> Result<ReviewedProviderPlan> {
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
        let report = ReleaseProviderPlanReport::from_verified(self, &verified, &changelog)?;
        Ok(ReviewedProviderPlan {
            report,
            changelog,
            verified,
        })
    }
}

impl ReleaseModrinthRequestPreviewArgs {
    /// Render the exact, reviewed Modrinth metadata beside each verified JAR.
    ///
    /// # Errors
    ///
    /// Rejects changed package or changelog bytes and invalid provider mapping.
    pub(super) fn invoke_in(self, cancellation: &CancellationToken) -> Result<CliOutput> {
        Ok(CliOutput::facet(self.preview_in(cancellation)?))
    }

    fn preview_in(
        self,
        cancellation: &CancellationToken,
    ) -> Result<ReleaseModrinthRequestPreviewReport> {
        let ReviewedProviderPlan {
            report, changelog, ..
        } = self.provider_plan.review_in(cancellation)?;
        let mut targets = Vec::with_capacity(report.target_count);
        for target in report.targets {
            cancellation.bail_if_cancelled()?;
            validate_modrinth_request_mapping(&target, &report.modrinth.loader_policy_1201)?;
            let payload = ModrinthCreateVersionPayload::for_release(
                &target.display_name,
                &target.modrinth_version_number,
                &changelog,
                &target.modrinth_game_versions,
                &target.modrinth_loaders,
                &report.modrinth.project,
            );
            let request_metadata_json = facet_json::to_string(&payload)?;
            targets.push(ModrinthRequestPreviewTarget {
                target_id: target.target_id,
                file_name: target.file_name,
                jar_sha256: target.sha256,
                request_metadata_sha256: sha256(request_metadata_json.as_bytes()),
                request_metadata_json,
            });
        }
        ensure!(
            targets.len() == 10,
            "Modrinth preview requires all ten verified package targets"
        );
        Ok(ReleaseModrinthRequestPreviewReport {
            schema: MODRINTH_PREVIEW_SCHEMA.to_owned(),
            scope: MODRINTH_PREVIEW_SCOPE.to_owned(),
            reviewed_notes_disclosure_warning: MODRINTH_PREVIEW_NOTES_WARNING.to_owned(),
            completion_manifest_sha256: report.completion_manifest_sha256,
            inventory_sha256: report.inventory_sha256,
            package_source_commit: report.package_source_commit,
            current_head_checked: report.current_head_checked,
            tag_object_checked: report.tag_object_checked,
            remote_project_ownership_checked: report.remote_project_ownership_checked,
            candidate_preset_id: report.candidate_preset_id,
            mod_version: report.mod_version,
            modrinth_project: report.modrinth.project,
            loader_policy_1201: report.modrinth.loader_policy_1201,
            changelog_sha256: report.changelog_sha256,
            publication_authorized: report.publication_authorized,
            target_count: targets.len(),
            targets,
        })
    }
}

pub(super) fn validate_modrinth_request_mapping(
    target: &ReleaseProviderTarget,
    loader_policy_1201: &str,
) -> Result<()> {
    let expected_loaders = if target.target_id == "1.20.1" {
        ensure!(
            target.minecraft_version == "1.20.1" && target.package_loader == "neoforge",
            "Modrinth 1.20.1 request differs from the verified transitional target"
        );
        match loader_policy_1201 {
            DUAL_1201_POLICY => vec!["forge", "neoforge"],
            NEOFORGE_ONLY_1201_POLICY => vec!["neoforge"],
            _ => eyre::bail!("invalid reviewed Modrinth 1.20.1 loader policy"),
        }
    } else {
        vec![target.package_loader.as_str()]
    };
    if target.target_id == "1.21.0" {
        ensure!(
            target.minecraft_version == "1.21" && target.package_loader == "neoforge",
            "Modrinth 1.21.0 request must use Minecraft 1.21 and NeoForge"
        );
    }
    ensure!(
        target.modrinth_game_versions == [target.minecraft_version.as_str()]
            && target.modrinth_loaders == expected_loaders,
        "Modrinth request metadata differs from the verified target mapping for '{}'",
        target.target_id
    );
    Ok(())
}

impl ReleaseProviderPlanReport {
    fn from_verified(
        args: ReleaseProviderPlanArgs,
        verified: &VerifiedReleasePackage,
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
        for (source, packaged) in inventory.targets.iter().zip(&manifest.targets) {
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
            let curseforge_metadata_names = checked_curseforge_metadata_names(
                &source.target_id,
                &source.minecraft_version,
                &source.loader,
                source.jdk_major,
            )?;
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
                target_id: source.target_id.clone(),
                minecraft_version: source.minecraft_version.clone(),
                package_loader: source.loader.clone(),
                file_name: packaged.file_name.clone(),
                sha256: packaged.sha256.clone(),
                display_name: modrinth_payload.name,
                modrinth_version_number: modrinth_payload.version_number,
                modrinth_game_versions: modrinth_payload.game_versions,
                modrinth_loaders: modrinth_payload.loaders,
                curseforge_metadata_names,
                curseforge_release_type: RELEASE_FILE_TYPE.to_owned(),
                curseforge_changelog_type: RELEASE_CHANGELOG_TYPE.to_owned(),
            });
        }
        Ok(Self {
            schema: PLAN_SCHEMA.to_owned(),
            scope: PLAN_SCOPE.to_owned(),
            completion_manifest_sha256: completion_manifest_sha256.clone(),
            inventory_sha256: manifest.inventory_sha256.clone(),
            lock_sha256: manifest.lock_sha256.clone(),
            package_source_commit: manifest.source_commit.clone(),
            reviewed_source_commit_matches_package: true,
            current_head_checked: false,
            tag_object_checked: false,
            remote_project_ownership_checked: false,
            curseforge_game_version_ids_checked: false,
            publication_authorized: false,
            candidate_preset_id: manifest.candidate_preset_id.clone(),
            mod_version: manifest.mod_version.clone(),
            changelog_sha256: args.changelog_sha256,
            changelog_utf8_bytes: changelog.len(),
            github: GitHubProviderIntent {
                repository: args.github_repo,
                reviewed_tag: args.reviewed_tag,
                release_title: release_title(&manifest.mod_version),
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

fn checked_curseforge_metadata_names(
    target_id: &str,
    minecraft_version: &str,
    package_loader: &str,
    jdk_major: u16,
) -> Result<Vec<String>> {
    // Keep the legacy publisher's exact name selection, but reject a future
    // divergence from the verified package's typed loader and JDK mapping.
    // Numeric game-version IDs remain unresolved here by design.
    let names = game_version_names_for_release(minecraft_version)?;
    let mut expected = vec![
        "Client".to_owned(),
        "Server".to_owned(),
        minecraft_version.to_owned(),
    ];
    if target_id == "1.20.1" {
        ensure!(
            package_loader == "neoforge",
            "transitional CurseForge metadata requires the verified NeoForge package"
        );
        expected.extend(["Forge".to_owned(), "NeoForge".to_owned()]);
    } else {
        let loader_name = match package_loader {
            "forge" => "Forge",
            "neoforge" => "NeoForge",
            _ => eyre::bail!("unsupported package loader for '{target_id}'"),
        };
        expected.push(loader_name.to_owned());
    }
    expected.push(format!("Java {jdk_major}"));
    ensure!(
        names == expected,
        "CurseForge metadata names differ from verified target mapping for '{target_id}'"
    );
    Ok(names)
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
    use crate::cli::source::LegacySourceArgs;
    use crate::cli::source::LegacySourceCommand;
    use crate::cli::source::ReleasePackageArgs;
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
            "legacy",
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
        let Command::Source(crate::cli::source::SourceArgs {
            command:
                crate::cli::source::SourceCommand::Legacy(LegacySourceArgs {
                    command: LegacySourceCommand::ReleaseProviderPlan(args),
                }),
        }) = parsed.command
        else {
            panic!("expected source release-provider-plan command");
        };
        assert_eq!(args.github_repo, "example/sfm");
        assert_eq!(args.modrinth_1201_loader_policy, DUAL_1201_POLICY);
        assert!(
            figue::from_slice::<Cli>(&[
                "source",
                "legacy",
                "release-provider-plan",
                "--package-root",
                "C:/reviewed/package",
            ])
            .into_result()
            .is_err()
        );
    }

    #[test]
    fn modrinth_preview_cli_reuses_all_explicit_provider_review_inputs() {
        let parsed = figue::from_slice::<Cli>(&[
            "source",
            "legacy",
            "release-modrinth-request-preview",
            "--package-root",
            "C:/reviewed/package",
            "--completion-manifest-sha256",
            "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "--reviewed-source-commit",
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            "--reviewed-tag",
            "9.99.99-fixture",
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
        let Command::Source(crate::cli::source::SourceArgs {
            command:
                crate::cli::source::SourceCommand::Legacy(LegacySourceArgs {
                    command: LegacySourceCommand::ReleaseModrinthRequestPreview(args),
                }),
        }) = parsed.command
        else {
            panic!("expected source release-modrinth-request-preview command");
        };
        assert_eq!(args.provider_plan.modrinth_project, "example-project");
        assert_eq!(
            args.provider_plan.modrinth_1201_loader_policy,
            DUAL_1201_POLICY
        );
        assert!(
            figue::from_slice::<Cli>(&[
                "source",
                "legacy",
                "release-modrinth-request-preview",
                "--package-root",
                "C:/reviewed/package",
            ])
            .into_result()
            .is_err()
        );
    }

    #[test]
    fn modrinth_preview_binds_ten_verified_jars_to_exact_request_json_without_writing() {
        let (fixture, provider_plan) = packaged_candidate();
        let before_entries = fs::read_dir(&provider_plan.package_root).unwrap().count();
        let notes = fs::read_to_string(&provider_plan.changelog_file).unwrap();
        let args = ReleaseModrinthRequestPreviewArgs { provider_plan };
        let preview = args.clone().preview_in(&CancellationToken::new()).unwrap();
        assert_eq!(preview.schema, MODRINTH_PREVIEW_SCHEMA);
        assert_eq!(preview.target_count, 10);
        assert_eq!(preview.targets.len(), fixture.lock().targets.len());
        assert_eq!(preview.package_source_commit, fixture.lock().source_commit);
        assert_eq!(preview.changelog_sha256, sha256(notes.as_bytes()));
        assert_eq!(
            preview.reviewed_notes_disclosure_warning,
            MODRINTH_PREVIEW_NOTES_WARNING
        );
        assert!(!preview.current_head_checked);
        assert!(!preview.tag_object_checked);
        assert!(!preview.remote_project_ownership_checked);
        assert!(!preview.publication_authorized);
        for (target, locked) in preview.targets.iter().zip(&fixture.lock().targets) {
            assert_eq!(target.target_id, locked.target_id);
            assert_eq!(target.jar_sha256, locked.production_jar_sha256);
            assert_eq!(
                target.file_name,
                locked
                    .production_jar_relative_path
                    .split('/')
                    .next_back()
                    .unwrap()
            );
            assert_eq!(
                target.request_metadata_sha256,
                sha256(target.request_metadata_json.as_bytes())
            );
            let payload: ModrinthCreateVersionPayload =
                facet_json::from_str(&target.request_metadata_json).unwrap();
            assert_eq!(payload.version_number, fixture.lock().mod_version);
            assert_eq!(payload.changelog, notes);
            assert_eq!(payload.project_id, "example-project");
            assert_eq!(payload.version_type, "release");
            assert_eq!(payload.file_parts, ["file"]);
            assert_eq!(payload.game_versions, [locked.minecraft_version.clone()]);
            assert_eq!(
                target.request_metadata_json,
                facet_json::to_string(&payload).unwrap()
            );
        }
        let transitional = &preview.targets[3];
        let transitional_payload: ModrinthCreateVersionPayload =
            facet_json::from_str(&transitional.request_metadata_json).unwrap();
        assert_eq!(transitional.target_id, "1.20.1");
        assert_eq!(transitional_payload.loaders, ["forge", "neoforge"]);
        let minecraft_121 = &preview.targets[7];
        let minecraft_121_payload: ModrinthCreateVersionPayload =
            facet_json::from_str(&minecraft_121.request_metadata_json).unwrap();
        assert_eq!(minecraft_121.target_id, "1.21.0");
        assert_eq!(minecraft_121_payload.game_versions, ["1.21"]);
        assert_eq!(minecraft_121_payload.loaders, ["neoforge"]);

        let rendered = args
            .clone()
            .invoke_in(&CancellationToken::new())
            .unwrap()
            .render(Some(OutputFormat::Json), false)
            .unwrap()
            .unwrap();
        assert!(rendered.contains(MODRINTH_PREVIEW_SCHEMA));
        let rendered_report: ReleaseModrinthRequestPreviewReport =
            facet_json::from_str(&rendered).unwrap();
        assert_eq!(
            rendered_report.reviewed_notes_disclosure_warning,
            MODRINTH_PREVIEW_NOTES_WARNING
        );
        assert!(!rendered_report.current_head_checked);
        assert!(!rendered_report.tag_object_checked);
        assert!(!rendered_report.remote_project_ownership_checked);
        assert!(!rendered_report.publication_authorized);
        assert!(!rendered.contains(&args.provider_plan.package_root.display().to_string()));
        assert!(!rendered.contains(&args.provider_plan.changelog_file.display().to_string()));
        assert!(!rendered.contains(&fixture.repo().display().to_string()));
        assert_eq!(
            fs::read_dir(&args.provider_plan.package_root)
                .unwrap()
                .count(),
            before_entries
        );
        assert_eq!(
            fs::read_to_string(&args.provider_plan.changelog_file).unwrap(),
            notes
        );
    }

    #[test]
    fn modrinth_preview_serializes_explicit_neoforge_only_1201_choice() {
        let (_fixture, mut provider_plan) = packaged_candidate();
        provider_plan.modrinth_1201_loader_policy = NEOFORGE_ONLY_1201_POLICY.to_owned();
        let preview = ReleaseModrinthRequestPreviewArgs { provider_plan }
            .preview_in(&CancellationToken::new())
            .unwrap();
        assert_eq!(preview.loader_policy_1201, NEOFORGE_ONLY_1201_POLICY);
        let transitional = &preview.targets[3];
        assert_eq!(transitional.target_id, "1.20.1");
        let payload: ModrinthCreateVersionPayload =
            facet_json::from_str(&transitional.request_metadata_json).unwrap();
        assert_eq!(payload.loaders, ["neoforge"]);
        assert_eq!(payload.game_versions, ["1.20.1"]);
    }

    #[test]
    fn modrinth_preview_rejects_package_notes_and_mapping_drift() {
        let (_fixture, provider_plan) = packaged_candidate();
        let args = ReleaseModrinthRequestPreviewArgs { provider_plan };
        let preview = args.clone().preview_in(&CancellationToken::new()).unwrap();
        let mut changed_digest = args.clone();
        changed_digest.provider_plan.completion_manifest_sha256 = sha256(b"other manifest");
        assert!(
            changed_digest
                .preview_in(&CancellationToken::new())
                .is_err()
        );
        let jar = args
            .provider_plan
            .package_root
            .join(&preview.targets[0].file_name);
        fs::write(&jar, b"changed packaged JAR").unwrap();
        assert!(args.clone().preview_in(&CancellationToken::new()).is_err());
        let (_fixture, provider_plan) = packaged_candidate();
        let args = ReleaseModrinthRequestPreviewArgs { provider_plan };
        fs::write(&args.provider_plan.changelog_file, b"changed notes\n").unwrap();
        assert!(args.preview_in(&CancellationToken::new()).is_err());

        let (_fixture, provider_plan) = packaged_candidate();
        let mut args = provider_plan;
        args.modrinth_1201_loader_policy = NEOFORGE_ONLY_1201_POLICY.to_owned();
        let report = args.plan_in(&CancellationToken::new()).unwrap();
        let transitional = &report.targets[3];
        assert!(validate_modrinth_request_mapping(transitional, NEOFORGE_ONLY_1201_POLICY).is_ok());
        let mut wrong_loaders = transitional.clone();
        wrong_loaders.modrinth_loaders = vec!["forge".to_owned()];
        assert!(
            validate_modrinth_request_mapping(&wrong_loaders, NEOFORGE_ONLY_1201_POLICY).is_err()
        );
        wrong_loaders.modrinth_loaders = vec!["neoforge".to_owned()];
        wrong_loaders.modrinth_game_versions = vec!["1.21".to_owned()];
        assert!(
            validate_modrinth_request_mapping(&wrong_loaders, NEOFORGE_ONLY_1201_POLICY).is_err()
        );
        let minecraft_121 = &report.targets[7];
        let mut wrong_121 = minecraft_121.clone();
        wrong_121.minecraft_version = "1.21.0".to_owned();
        assert!(validate_modrinth_request_mapping(&wrong_121, DUAL_1201_POLICY).is_err());
        wrong_121.minecraft_version = "1.21".to_owned();
        wrong_121.modrinth_loaders = vec!["forge".to_owned()];
        assert!(validate_modrinth_request_mapping(&wrong_121, DUAL_1201_POLICY).is_err());
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
        assert_eq!(
            report.github.release_title,
            format!("v{}", fixture.lock().mod_version)
        );
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
        assert_eq!(
            transitional.curseforge_metadata_names,
            ["Client", "Server", "1.20.1", "Forge", "NeoForge", "Java 17"]
        );
        assert_eq!(transitional.curseforge_release_type, "release");
        assert_eq!(transitional.curseforge_changelog_type, "markdown");
        let mc_121 = report
            .targets
            .iter()
            .find(|target| target.target_id == "1.21.0")
            .unwrap();
        assert_eq!(mc_121.minecraft_version, "1.21");
        assert_eq!(mc_121.modrinth_game_versions, ["1.21"]);
        assert_eq!(
            mc_121.curseforge_metadata_names,
            ["Client", "Server", "1.21", "NeoForge", "Java 21"]
        );
        let modern = report
            .targets
            .iter()
            .find(|target| target.target_id == "26.1.2")
            .unwrap();
        assert_eq!(
            modern.curseforge_metadata_names,
            ["Client", "Server", "26.1.2", "NeoForge", "Java 25"]
        );

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
    fn curseforge_name_adapter_rejects_typed_loader_or_jdk_drift() {
        assert!(checked_curseforge_metadata_names("1.19.2", "1.19.2", "forge", 17).is_ok());
        assert!(checked_curseforge_metadata_names("1.19.2", "1.19.2", "neoforge", 17).is_err());
        assert!(checked_curseforge_metadata_names("1.19.2", "1.19.2", "forge", 21).is_err());
        assert!(checked_curseforge_metadata_names("1.20.1", "1.20.1", "forge", 17).is_err());
        assert!(checked_curseforge_metadata_names("1.20.1", "1.20.1", "neoforge", 17).is_ok());
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
