//! Join one selected target's local release state with verified provider intent.
//!
//! This is a read-only handoff. It rechecks the complete ten-JAR package in
//! both existing preflights, requires their identities to agree, and never
//! creates a tag or contacts a provider. Keep the package and worktree quiet
//! during the checks; neither preflight pins open file handles against hostile
//! concurrent replacement.

use super::release_provider_plan_cli::CurseForgeProviderIntent;
use super::release_provider_plan_cli::GitHubProviderIntent;
use super::release_provider_plan_cli::MODRINTH_PREVIEW_NOTES_WARNING;
use super::release_provider_plan_cli::ModrinthProviderIntent;
use super::release_provider_plan_cli::ReleaseProviderPlanArgs;
use super::release_provider_plan_cli::ReleaseProviderTarget;
use super::release_provider_plan_cli::ReviewedProviderPlan;
use super::release_provider_plan_cli::validate_modrinth_request_mapping;
use super::release_tag_preflight_cli::ReleaseTagPreflightArgs;
use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use crate::modrinth::ModrinthCreateVersionPayload;
use crate::source_projection::provenance::sha256;
use eyre::Result;
use eyre::ensure;
use facet::Facet;
use figue::{self as args};
use std::path::PathBuf;

const REPORT_SCHEMA: &str = "sfm:source_release_target_plan@1";
const REPORT_SCOPE: &str = "read-only selected-target handoff from a verified ten-JAR package and promoted local HEAD; no credentials, remote checks, tag creation, upload or publication";

#[derive(Clone, Debug, Facet)]
pub struct ReleaseTargetPlanArgs {
    /// Reviewed package, provider identities, changelog and loader policy.
    #[facet(flatten)]
    pub provider_plan: ReleaseProviderPlanArgs,
    /// Git worktree root containing the reviewed promoted release commit.
    #[facet(args::named)]
    pub repo_root: PathBuf,
    /// Exact projection target ID, such as 1.21.0, not its MC filename marker.
    #[facet(args::named)]
    pub target_id: String,
    /// Explicitly reviewed post-promotion commit at current local HEAD.
    #[facet(args::named)]
    pub reviewed_release_commit: String,
}

#[derive(Debug, Facet)]
#[expect(
    clippy::struct_excessive_bools,
    reason = "independent local checks and explicit remote/publication limits are part of the handoff"
)]
pub(super) struct ReleaseTargetPlanReport {
    schema: String,
    scope: String,
    reviewed_notes_disclosure_warning: String,
    completion_manifest_sha256: String,
    inventory_sha256: String,
    package_source_commit: String,
    reviewed_release_commit: String,
    candidate_preset_id: String,
    mod_version: String,
    current_head_checked: bool,
    authored_tree_unchanged_outside_generated_roots: bool,
    selected_root_provenance_checked: bool,
    selected_root_commit_tree_checked: bool,
    pub(super) target: ReleaseProviderTarget,
    local_tag: String,
    local_tag_state: String,
    local_tag_object_id: Option<String>,
    github: GitHubProviderIntent,
    modrinth: ModrinthProviderIntent,
    pub(super) modrinth_request_metadata_json: String,
    pub(super) modrinth_request_metadata_sha256: String,
    curseforge: CurseForgeProviderIntent,
    changelog_sha256: String,
    remote_tag_checked: bool,
    remote_project_ownership_checked: bool,
    curseforge_game_version_ids_checked: bool,
    publication_authorized: bool,
}

impl ReleaseTargetPlanArgs {
    /// Reverify the whole package and join one exact target with local HEAD.
    ///
    /// # Errors
    ///
    /// Rejects a changed package or notes, an imprecise reviewed tag, a stale
    /// or unpromoted local checkout, and provider metadata drift.
    pub(super) fn invoke_in(self, cancellation: &CancellationToken) -> Result<CliOutput> {
        Ok(CliOutput::facet(self.plan_in(cancellation)?))
    }

    pub(super) fn plan_in(
        self,
        cancellation: &CancellationToken,
    ) -> Result<ReleaseTargetPlanReport> {
        cancellation.bail_if_cancelled()?;
        let ReviewedProviderPlan { report, changelog } =
            self.provider_plan.clone().review_in(cancellation)?;
        let exact_tag = format!("{}-{}", report.mod_version, self.target_id);
        ensure!(
            report.github.reviewed_tag == exact_tag,
            "--reviewed-tag must equal the selected target's exact '{exact_tag}' tag"
        );
        let target = report
            .targets
            .into_iter()
            .find(|target| target.target_id == self.target_id)
            .ok_or_else(|| eyre::eyre!("unknown verified package target '{}'", self.target_id))?;

        let local = ReleaseTagPreflightArgs {
            repo_root: self.repo_root,
            package_root: self.provider_plan.package_root,
            completion_manifest_sha256: self.provider_plan.completion_manifest_sha256,
            target_id: self.target_id,
            reviewed_release_commit: self.reviewed_release_commit,
        }
        .preflight_in(cancellation)?;
        ensure!(
            report.completion_manifest_sha256 == local.completion_manifest_sha256
                && report.inventory_sha256 == local.inventory_sha256
                && report.package_source_commit == local.package_source_commit
                && target.target_id == local.target_id
                && target.minecraft_version == local.minecraft_version
                && target.package_loader == local.loader
                && target.file_name == local.file_name
                && target.sha256 == local.jar_sha256
                && exact_tag == local.local_tag,
            "selected local release state differs from verified provider package"
        );
        ensure!(
            local.current_head_checked
                && local.authored_tree_unchanged_outside_generated_roots
                && local.selected_root_provenance_checked
                && local.selected_root_commit_tree_checked
                && !local.remote_tag_checked
                && !local.publication_authorized,
            "local release preflight did not establish the expected read-only boundary"
        );

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
        Ok(ReleaseTargetPlanReport {
            schema: REPORT_SCHEMA.to_owned(),
            scope: REPORT_SCOPE.to_owned(),
            reviewed_notes_disclosure_warning: MODRINTH_PREVIEW_NOTES_WARNING.to_owned(),
            completion_manifest_sha256: local.completion_manifest_sha256,
            inventory_sha256: local.inventory_sha256,
            package_source_commit: local.package_source_commit,
            reviewed_release_commit: local.reviewed_release_commit,
            candidate_preset_id: report.candidate_preset_id,
            mod_version: report.mod_version,
            current_head_checked: local.current_head_checked,
            authored_tree_unchanged_outside_generated_roots: local
                .authored_tree_unchanged_outside_generated_roots,
            selected_root_provenance_checked: local.selected_root_provenance_checked,
            selected_root_commit_tree_checked: local.selected_root_commit_tree_checked,
            target,
            local_tag: local.local_tag,
            local_tag_state: local.local_tag_state,
            local_tag_object_id: local.local_tag_object_id,
            github: report.github,
            modrinth: report.modrinth,
            modrinth_request_metadata_sha256: sha256(request_metadata_json.as_bytes()),
            modrinth_request_metadata_json: request_metadata_json,
            curseforge: report.curseforge,
            changelog_sha256: report.changelog_sha256,
            remote_tag_checked: false,
            remote_project_ownership_checked: false,
            curseforge_game_version_ids_checked: false,
            publication_authorized: false,
        })
    }
}

#[cfg(test)]
pub(super) mod tests {
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
    use crate::source_projection::candidate_lock::tests::Fixture;
    use crate::source_projection::provenance::ProjectionProvenance;
    use crate::source_projection::sync::MANIFEST_FILE;
    use std::fs;
    use std::path::Path;
    use std::process::Command as ProcessCommand;

    pub(in crate::cli::source) fn git(repo: &Path, args: &[&str]) -> String {
        let output = ProcessCommand::new("git")
            .args(args)
            .current_dir(repo)
            .output()
            .unwrap();
        assert!(
            output.status.success(),
            "git {args:?}: {}",
            String::from_utf8_lossy(&output.stderr)
        );
        String::from_utf8(output.stdout).unwrap().trim().to_owned()
    }

    fn promote_fixture(fixture: &Fixture) -> String {
        for (target_id, candidate_root) in fixture.roots() {
            let destination = fixture
                .repo()
                .join(format!("platform/minecraft/mc-version/{target_id}"));
            let manifest_bytes = fs::read(candidate_root.join(MANIFEST_FILE)).unwrap();
            let manifest =
                ProjectionProvenance::from_json(std::str::from_utf8(&manifest_bytes).unwrap())
                    .unwrap();
            for relative in manifest.files.keys() {
                let output = destination.join(relative);
                fs::create_dir_all(output.parent().unwrap()).unwrap();
                fs::copy(candidate_root.join(relative), output).unwrap();
            }
            fs::write(destination.join(MANIFEST_FILE), manifest_bytes).unwrap();
        }
        git(
            fixture.repo(),
            &["add", "--", "platform/minecraft/mc-version"],
        );
        for (target_id, _) in fixture.roots() {
            git(
                fixture.repo(),
                &[
                    "update-index",
                    "--chmod=+x",
                    "--",
                    &format!("platform/minecraft/mc-version/{target_id}/gradlew"),
                ],
            );
        }
        git(
            fixture.repo(),
            &["commit", "-qm", "promote synthetic target plan fixture"],
        );
        git(fixture.repo(), &["rev-parse", "HEAD"])
    }

    pub(in crate::cli::source) fn packaged_candidate() -> (Fixture, ReleaseTargetPlanArgs) {
        let fixture = Fixture::new();
        let scratch = fixture.repo().parent().unwrap();
        let lock = scratch.join("reviewed-candidate-lock.json");
        let lock_bytes = facet_json::to_string_pretty(fixture.lock()).unwrap() + "\n";
        fs::write(&lock, &lock_bytes).unwrap();
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
                lock,
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
            sha256(&fs::read(package_root.join("release-package.json")).unwrap());
        let changelog_file = scratch.join("reviewed-changelog.md");
        let changelog = "Reviewed target release notes\n";
        fs::write(&changelog_file, changelog).unwrap();
        let reviewed_release_commit = promote_fixture(&fixture);
        let provider_plan = ReleaseProviderPlanArgs {
            package_root,
            completion_manifest_sha256,
            reviewed_source_commit: fixture.lock().source_commit.clone(),
            reviewed_tag: format!("{}-1.21.0", fixture.lock().mod_version),
            github_repo: "example/sfm".to_owned(),
            modrinth_project: "example-project".to_owned(),
            curseforge_project: 123,
            changelog_file,
            changelog_sha256: sha256(changelog.as_bytes()),
            modrinth_1201_loader_policy: "dual-forge-neoforge".to_owned(),
        };
        let args = ReleaseTargetPlanArgs {
            provider_plan,
            repo_root: fixture.repo().to_path_buf(),
            target_id: "1.21.0".to_owned(),
            reviewed_release_commit,
        };
        (fixture, args)
    }

    #[test]
    fn cli_requires_exact_target_and_reviewed_promotion() {
        let parsed = figue::from_slice::<Cli>(&[
            "source",
            "legacy",
            "release-target-plan",
            "--package-root",
            "C:/reviewed/package",
            "--completion-manifest-sha256",
            "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "--reviewed-source-commit",
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            "--reviewed-tag",
            "4.35.0-1.21.0",
            "--github-repo",
            "example/sfm",
            "--modrinth-project",
            "example-project",
            "--curseforge-project",
            "123",
            "--changelog-file",
            "C:/reviewed/notes.md",
            "--changelog-sha256",
            "sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
            "--modrinth-1201-loader-policy",
            "dual-forge-neoforge",
            "--repo-root",
            "C:/reviewed/repo",
            "--target-id",
            "1.21.0",
            "--reviewed-release-commit",
            "dddddddddddddddddddddddddddddddddddddddd",
        ])
        .into_result()
        .unwrap()
        .get_silent();
        let Command::Source(crate::cli::source::SourceArgs {
            command:
                crate::cli::source::SourceCommand::Legacy(LegacySourceArgs {
                    command: LegacySourceCommand::ReleaseTargetPlan(args),
                }),
        }) = parsed.command
        else {
            panic!("expected source release-target-plan command");
        };
        assert_eq!(args.target_id, "1.21.0");
        assert_eq!(args.provider_plan.reviewed_tag, "4.35.0-1.21.0");
        assert!(
            figue::from_slice::<Cli>(&[
                "source",
                "legacy",
                "release-target-plan",
                "--repo-root",
                "C:/reviewed/repo",
            ])
            .into_result()
            .is_err()
        );
    }

    #[test]
    fn joins_one_exact_target_without_tag_or_provider_effects() {
        let (fixture, args) = packaged_candidate();
        let package_entries = fs::read_dir(&args.provider_plan.package_root)
            .unwrap()
            .count();
        let report = args.clone().plan_in(&CancellationToken::new()).unwrap();
        assert_eq!(report.target.target_id, "1.21.0");
        assert_eq!(report.target.minecraft_version, "1.21");
        assert_eq!(report.target.file_name, "SFM-MC1.21-4.35.0.jar");
        assert_eq!(report.local_tag, "4.35.0-1.21.0");
        assert_eq!(report.local_tag_state, "absent");
        assert!(report.current_head_checked);
        assert!(report.authored_tree_unchanged_outside_generated_roots);
        assert!(report.selected_root_commit_tree_checked);
        assert!(!report.remote_tag_checked);
        assert!(!report.remote_project_ownership_checked);
        assert!(!report.curseforge_game_version_ids_checked);
        assert!(!report.publication_authorized);
        assert_eq!(report.github.reviewed_tag, report.local_tag);
        assert_eq!(report.modrinth.project, "example-project");
        assert_eq!(report.curseforge.project_id, 123);
        assert_eq!(
            report.modrinth_request_metadata_sha256,
            sha256(report.modrinth_request_metadata_json.as_bytes())
        );
        assert!(
            report
                .modrinth_request_metadata_json
                .contains("Reviewed target release notes")
        );
        assert!(
            report
                .reviewed_notes_disclosure_warning
                .contains("sensitive data or local paths")
        );
        let output = args
            .clone()
            .invoke_in(&CancellationToken::new())
            .unwrap()
            .render(Some(OutputFormat::Json), false)
            .unwrap()
            .unwrap();
        assert!(output.contains(REPORT_SCHEMA));
        assert_eq!(output.matches("\"target_id\"").count(), 1);
        assert_eq!(
            output.matches("\"modrinth_request_metadata_json\"").count(),
            1
        );
        assert!(!output.contains("SFM-MC1.20.1-4.35.0.jar"));
        assert!(!output.contains(&fixture.repo().display().to_string()));
        assert!(!output.contains(&args.provider_plan.package_root.display().to_string()));
        assert!(!output.contains(&args.provider_plan.changelog_file.display().to_string()));
        assert_eq!(
            fs::read_dir(&args.provider_plan.package_root)
                .unwrap()
                .count(),
            package_entries
        );
        assert!(git(fixture.repo(), &["status", "--porcelain"]).is_empty());

        git(
            fixture.repo(),
            &["tag", "4.35.0-1.21.0", &args.reviewed_release_commit],
        );
        let matching = args.plan_in(&CancellationToken::new()).unwrap();
        assert_eq!(matching.local_tag_state, "matches-reviewed-release-commit");
    }

    #[test]
    fn exact_provider_tag_and_complete_package_are_mandatory() {
        let (_fixture, args) = packaged_candidate();
        let mut bare = args.clone();
        bare.provider_plan.reviewed_tag = "4.35.0".to_owned();
        assert!(
            bare.plan_in(&CancellationToken::new())
                .unwrap_err()
                .to_string()
                .contains("must equal the selected target's exact")
        );
        let mut wrong_target = args.clone();
        wrong_target.provider_plan.reviewed_tag = "4.35.0-1.19.2".to_owned();
        assert!(wrong_target.plan_in(&CancellationToken::new()).is_err());
        let mut filename_marker = args.clone();
        filename_marker.provider_plan.reviewed_tag = "4.35.0-1.21".to_owned();
        assert!(
            filename_marker
                .plan_in(&CancellationToken::new())
                .unwrap_err()
                .to_string()
                .contains("must equal the selected target's exact '4.35.0-1.21.0' tag")
        );

        let unselected_jar = args
            .provider_plan
            .package_root
            .join("SFM-MC26.1.2-4.35.0.jar");
        fs::write(unselected_jar, b"changed unselected JAR\n").unwrap();
        assert!(
            args.plan_in(&CancellationToken::new())
                .unwrap_err()
                .to_string()
                .contains("packaged JAR SHA-256 differs")
        );
    }

    #[test]
    fn stale_head_wrong_tag_and_reviewed_notes_fail_closed() {
        let (fixture, args) = packaged_candidate();
        let mut stale = args.clone();
        stale.reviewed_release_commit = fixture.lock().source_commit.clone();
        assert!(stale.plan_in(&CancellationToken::new()).is_err());

        fs::write(&args.provider_plan.changelog_file, "changed notes\n").unwrap();
        assert!(args.clone().plan_in(&CancellationToken::new()).is_err());
        fs::write(
            &args.provider_plan.changelog_file,
            "Reviewed target release notes\n",
        )
        .unwrap();

        git(
            fixture.repo(),
            &["tag", "4.35.0-1.21.0", &fixture.lock().source_commit],
        );
        assert!(
            args.plan_in(&CancellationToken::new())
                .unwrap_err()
                .to_string()
                .contains("does not point to reviewed release commit")
        );
    }

    #[test]
    fn transitional_loader_policy_is_explicit_for_selected_target() {
        let (_fixture, mut args) = packaged_candidate();
        args.target_id = "1.20.1".to_owned();
        args.provider_plan.reviewed_tag = "4.35.0-1.20.1".to_owned();
        let dual = args.clone().plan_in(&CancellationToken::new()).unwrap();
        assert_eq!(dual.target.modrinth_loaders, ["forge", "neoforge"]);
        args.provider_plan.modrinth_1201_loader_policy = "neoforge-only".to_owned();
        let single = args.clone().plan_in(&CancellationToken::new()).unwrap();
        assert_eq!(single.target.modrinth_loaders, ["neoforge"]);
        args.provider_plan.modrinth_1201_loader_policy = "implicit".to_owned();
        assert!(args.plan_in(&CancellationToken::new()).is_err());
    }
}
