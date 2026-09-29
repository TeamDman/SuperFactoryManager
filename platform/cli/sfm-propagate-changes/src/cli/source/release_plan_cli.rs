//! Read-only asset plan for one independently verified local release package.
//!
//! The plan selects only the ten exact package files. It does not infer a tag,
//! provider project, release notes or upload metadata, and cannot publish.

use super::release_package_verify_cli::ReleasePackageVerifyArgs;
use super::release_package_verify_cli::VerifiedReleasePackage;
use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use eyre::Result;
use facet::Facet;
use figue::{self as args};
use std::path::PathBuf;

const PLAN_SCHEMA: &str = "sfm:source_release_plan@1";
const PLAN_SCOPE: &str = "verified-local-asset-plan-only; package source commit may be historical; current HEAD not checked; no promotion, tag, push or publication";

#[derive(Clone, Debug, Facet)]
pub struct ReleasePlanArgs {
    /// Existing absolute local directory created by source release-package.
    #[facet(args::named)]
    pub package_root: PathBuf,
    /// Separately reviewed SHA-256 of the exact release-package.json bytes.
    #[facet(args::named)]
    pub completion_manifest_sha256: String,
}

#[derive(Debug, Facet)]
struct ReleasePlanReport {
    schema: String,
    scope: String,
    completion_manifest_sha256: String,
    inventory_sha256: String,
    lock_sha256: String,
    package_source_commit: String,
    current_head_checked: bool,
    candidate_preset_id: String,
    mod_version: String,
    target_count: usize,
    targets: Vec<ReleasePlanTarget>,
}

#[derive(Debug, Facet)]
struct ReleasePlanTarget {
    target_id: String,
    minecraft_version: String,
    loader: String,
    file_name: String,
    sha256: String,
}

impl ReleasePlanArgs {
    /// Verify the complete package in this invocation and report its asset plan.
    ///
    /// # Errors
    ///
    /// Rejects a changed completion manifest, inventory, JAR or package entry.
    pub(super) fn invoke_in(self, cancellation: &CancellationToken) -> Result<CliOutput> {
        Ok(CliOutput::facet(self.plan_in(cancellation)?))
    }

    fn plan_in(self, cancellation: &CancellationToken) -> Result<ReleasePlanReport> {
        let verified = ReleasePackageVerifyArgs {
            package_root: self.package_root,
            completion_manifest_sha256: self.completion_manifest_sha256,
        }
        .verify_in(cancellation)?;
        Ok(ReleasePlanReport::from_verified(verified))
    }
}

impl ReleasePlanReport {
    fn from_verified(verified: VerifiedReleasePackage) -> Self {
        let VerifiedReleasePackage {
            completion_manifest_sha256,
            manifest,
            inventory,
        } = verified;
        // The verifier already checked equality and order across both files.
        let targets = inventory
            .targets
            .into_iter()
            .zip(manifest.targets)
            .map(|(source, packaged)| ReleasePlanTarget {
                target_id: source.target_id,
                minecraft_version: source.minecraft_version,
                loader: source.loader,
                file_name: packaged.file_name,
                sha256: packaged.sha256,
            })
            .collect::<Vec<_>>();
        Self {
            schema: PLAN_SCHEMA.to_owned(),
            scope: PLAN_SCOPE.to_owned(),
            completion_manifest_sha256,
            inventory_sha256: manifest.inventory_sha256,
            lock_sha256: manifest.lock_sha256,
            package_source_commit: manifest.source_commit,
            current_head_checked: false,
            candidate_preset_id: manifest.candidate_preset_id,
            mod_version: manifest.mod_version,
            target_count: targets.len(),
            targets,
        }
    }
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
    use crate::source_projection::provenance::sha256;
    use std::fs;
    use std::path::Path;

    fn packaged_candidate() -> (Fixture, ReleasePlanArgs) {
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
        (
            fixture,
            ReleasePlanArgs {
                package_root,
                completion_manifest_sha256,
            },
        )
    }

    fn assert_rejected(args: ReleasePlanArgs) {
        assert!(args.plan_in(&CancellationToken::new()).is_err());
    }

    #[test]
    fn parses_and_plans_exact_verified_package_without_paths_or_side_effects() {
        let parsed = figue::from_slice::<Cli>(&[
            "source",
            "release-plan",
            "--package-root",
            "C:/reviewed/package",
            "--completion-manifest-sha256",
            "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        ])
        .into_result()
        .unwrap()
        .get_silent();
        let Command::Source(SourceArgs {
            command: SourceCommand::ReleasePlan(parsed_args),
        }) = parsed.command
        else {
            panic!("expected source release-plan command");
        };
        assert_eq!(
            parsed_args.package_root,
            PathBuf::from("C:/reviewed/package")
        );

        let (fixture, args) = packaged_candidate();
        let package_path = args.package_root.clone();
        let before = fs::read_dir(&args.package_root).unwrap().count();
        let plan = args.clone().plan_in(&CancellationToken::new()).unwrap();
        assert_eq!(plan.target_count, 10);
        assert_eq!(plan.package_source_commit, fixture.lock().source_commit);
        assert!(!plan.current_head_checked);
        assert_eq!(plan.candidate_preset_id, fixture.lock().candidate_preset_id);
        assert_eq!(plan.mod_version, fixture.lock().mod_version);
        assert_eq!(
            plan.completion_manifest_sha256,
            args.completion_manifest_sha256
        );
        for target in &plan.targets {
            let locked = fixture
                .lock()
                .targets
                .iter()
                .find(|locked| locked.target_id == target.target_id)
                .unwrap();
            assert_eq!(target.minecraft_version, locked.minecraft_version);
            assert_eq!(target.loader, locked.loader);
            assert_eq!(target.sha256, locked.production_jar_sha256);
            assert_eq!(
                target.file_name,
                Path::new(&locked.production_jar_relative_path)
                    .file_name()
                    .unwrap()
                    .to_str()
                    .unwrap()
            );
        }
        let mc_121 = plan
            .targets
            .iter()
            .find(|target| target.target_id == "1.21.0")
            .unwrap();
        assert_eq!(mc_121.minecraft_version, "1.21");
        let mc_1201 = plan
            .targets
            .iter()
            .find(|target| target.target_id == "1.20.1")
            .unwrap();
        assert_eq!(mc_1201.loader, "neoforge");

        let first = args
            .clone()
            .invoke_in(&CancellationToken::new())
            .unwrap()
            .render(Some(OutputFormat::Json), false)
            .unwrap()
            .unwrap();
        let second = args
            .invoke_in(&CancellationToken::new())
            .unwrap()
            .render(Some(OutputFormat::Json), false)
            .unwrap()
            .unwrap();
        assert_eq!(first, second);
        assert!(first.contains(PLAN_SCHEMA));
        assert!(first.contains("current HEAD not checked"));
        assert!(first.contains("\"current_head_checked\": false"));
        assert!(!first.contains(&package_path.display().to_string()));
        assert!(!first.contains(&fixture.repo().display().to_string()));
        assert_eq!(fs::read_dir(&package_path).unwrap().count(), before);
    }

    #[test]
    fn rejects_wrong_digest_and_changed_or_incomplete_package() {
        let (_fixture, args) = packaged_candidate();
        let mut wrong = args.clone();
        wrong.completion_manifest_sha256 = sha256(b"wrong reviewed digest");
        assert_rejected(wrong);

        let completion = args.package_root.join(COMPLETION_FILE);
        let original_completion = fs::read(&completion).unwrap();
        fs::write(&completion, b"changed completion manifest").unwrap();
        assert_rejected(args.clone());
        fs::write(&completion, original_completion).unwrap();

        let inventory = args.package_root.join("release-inventory.json");
        let original_inventory = fs::read(&inventory).unwrap();
        fs::write(&inventory, b"changed inventory").unwrap();
        assert_rejected(args.clone());
        fs::write(&inventory, original_inventory).unwrap();

        let plan = args.clone().plan_in(&CancellationToken::new()).unwrap();
        let jar = args.package_root.join(&plan.targets[0].file_name);
        let original_jar = fs::read(&jar).unwrap();
        fs::write(&jar, b"changed jar").unwrap();
        assert_rejected(args.clone());
        fs::remove_file(&jar).unwrap();
        assert_rejected(args.clone());
        fs::write(&jar, original_jar).unwrap();

        let extra = args.package_root.join("unlisted.jar");
        fs::write(&extra, b"extra jar").unwrap();
        assert_rejected(args);
    }
}
