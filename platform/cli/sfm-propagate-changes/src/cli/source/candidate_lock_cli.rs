//! Local, read-only entry point for a portable source-candidate lock.

use super::source_cli::SourceProjectArgs;
use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use crate::source_projection::candidate_lock::CandidateVerificationReport;
use crate::source_projection::candidate_lock::SourceCandidateLock;
use crate::source_projection::manifest::ProjectionPreset;
use crate::source_projection::manifest::ProjectionTarget;
use crate::source_projection::manifest::SourceProjectionManifest;
use crate::source_projection::provenance::sha256;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;
use figue::{self as args};
use std::collections::BTreeMap;
use std::fs;
use std::io::Read as _;
use std::path::Path;
use std::path::PathBuf;

const MAX_LOCK_BYTES: u64 = 1024 * 1024;

#[derive(Clone, Debug, Facet)]
pub struct CandidateVerifyArgs {
    /// Git worktree containing the reviewed source-projection definition.
    #[facet(args::named)]
    pub repo_root: PathBuf,
    /// Portable source-candidate lock JSON.
    #[facet(args::named)]
    pub lock: PathBuf,
    /// Local target=absolute-project-root mapping; repeat for all ten targets.
    #[facet(default, args::named)]
    pub candidate_root: Vec<String>,
}

impl CandidateVerifyArgs {
    /// Verify all ten exact candidate JARs and projected outputs without writes.
    ///
    /// # Errors
    ///
    /// Fails on an invalid lock, incomplete local roots or changed artifacts.
    pub(super) fn invoke_in(
        self,
        cancellation: &CancellationToken,
        invocation_dir: &Path,
    ) -> Result<CliOutput> {
        cancellation.bail_if_cancelled()?;
        let repo_root = resolve_path(self.repo_root, invocation_dir);
        let lock_path = resolve_path(self.lock, invocation_dir);
        let (lock, lock_sha256) = read_candidate_lock(&lock_path)?;
        let roots = parse_roots(&self.candidate_root)?;
        let report = verify_candidate_in(
            cancellation,
            &repo_root,
            &lock,
            &roots,
            lock_sha256,
            #[cfg(test)]
            None,
        )?;
        Ok(CliOutput::facet(report))
    }
}

/// Read and parse one bounded, immutable byte snapshot. The promotion gate
/// binds this digest to its request and verifies this same parsed snapshot.
pub(super) fn read_candidate_lock(lock_path: &Path) -> Result<(SourceCandidateLock, String)> {
    let file = fs::File::open(lock_path)
        .wrap_err_with(|| format!("cannot open candidate lock '{}'", lock_path.display()))?;
    let metadata = file.metadata()?;
    ensure!(metadata.is_file(), "candidate lock must be a regular file");
    ensure!(
        metadata.len() <= MAX_LOCK_BYTES,
        "candidate lock exceeds the {MAX_LOCK_BYTES}-byte limit"
    );
    let mut bytes = Vec::new();
    file.take(MAX_LOCK_BYTES + 1).read_to_end(&mut bytes)?;
    ensure!(
        bytes.len() as u64 <= MAX_LOCK_BYTES,
        "candidate lock exceeds the {MAX_LOCK_BYTES}-byte limit"
    );
    let lock = SourceCandidateLock::from_json(
        std::str::from_utf8(&bytes).wrap_err("candidate lock is not UTF-8")?,
    )?;
    Ok((lock, sha256(&bytes)))
}

/// Full source verification: the core inventory plus deterministic checks of
/// all ten generated roots. Both CLIs use the same implementation.
pub(super) fn verify_candidate_in(
    cancellation: &CancellationToken,
    repo_root: &Path,
    lock: &SourceCandidateLock,
    roots: &BTreeMap<String, PathBuf>,
    lock_sha256: String,
    #[cfg(test)] test_gradle_overlays: Option<&BTreeMap<String, String>>,
) -> Result<CandidateVerificationReport> {
    cancellation.bail_if_cancelled()?;
    tracing::info!("verifying frozen candidate inventory");
    let mut report = lock.verify_in(repo_root, roots, lock_sha256)?;
    tracing::info!("frozen candidate inventory verified; checking deterministic sources");
    let manifest_bytes = fs::read(repo_root.join("platform/minecraft/source-projection.json"))?;
    let manifest = SourceProjectionManifest::from_json(std::str::from_utf8(&manifest_bytes)?)?;
    let preset = manifest.preset(&lock.candidate_preset_id)?;
    for target in &lock.targets {
        cancellation.bail_if_cancelled()?;
        tracing::info!(target_id = %target.target_id, "checking deterministic candidate sources");
        let declared = manifest.target(&target.target_id)?;
        let gradle_overlay = candidate_gradle_overlay(preset, declared);
        #[cfg(test)]
        let gradle_overlay = if let Some(overrides) = test_gradle_overlays {
            let relative = overrides.get(&target.target_id).ok_or_else(|| {
                eyre::eyre!(
                    "missing synthetic Gradle overlay for '{}'",
                    target.target_id
                )
            })?;
            vec![format!("synthetic={relative}")]
        } else {
            gradle_overlay
        };
        SourceProjectArgs {
            repo_root: repo_root.to_path_buf(),
            target: target.target_id.clone(),
            preset: lock.candidate_preset_id.clone(),
            manifest: None,
            primary_src_root: None,
            gradle_project_root: None,
            output_root: roots[&target.target_id].clone(),
            overlay: Vec::new(),
            gradle_overlay,
        }
        .check_candidate_in(cancellation, repo_root)?;
        tracing::info!(target_id = %target.target_id, "deterministic candidate sources verified");
    }
    report.schema.clear();
    report
        .schema
        .push_str("sfm:source_candidate_verification@2");
    report.deterministic_source_check = true;
    tracing::info!("all candidate sources verified");
    Ok(report)
}

fn candidate_gradle_overlay(preset: &ProjectionPreset, target: &ProjectionTarget) -> Vec<String> {
    if preset.release_baselines.is_empty() && preset.frozen_source_commit.is_none() {
        vec![format!("target={}", target.project_dir)]
    } else {
        Vec::new()
    }
}

pub(super) fn resolve_path(path: PathBuf, invocation_dir: &Path) -> PathBuf {
    if path.is_absolute() {
        path
    } else {
        invocation_dir.join(path)
    }
}

pub(super) fn parse_roots(values: &[String]) -> Result<BTreeMap<String, PathBuf>> {
    let mut roots = BTreeMap::new();
    for value in values {
        let (target, raw_path) = value
            .split_once('=')
            .ok_or_else(|| eyre::eyre!("candidate root must use target=absolute-project-root"))?;
        ensure!(!target.is_empty(), "candidate root has empty target ID");
        let path = PathBuf::from(raw_path);
        ensure!(
            path.is_absolute(),
            "candidate root for '{target}' must be absolute"
        );
        ensure!(
            roots.insert(target.to_owned(), path).is_none(),
            "duplicate local candidate root for '{target}'"
        );
    }
    Ok(roots)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::cli::Cli;
    use crate::cli::Command;
    use crate::cli::output::OutputFormat;
    use crate::cli::source::SourceArgs;
    use crate::cli::source::SourceCommand;
    use crate::cli::source::SourceFrozenInventoryMatrixPreviewArgs;
    use crate::source_projection::manifest::FrozenSourceBinding;
    use crate::source_projection::manifest::SCHEMA_VERSION;
    use crate::source_projection::sync::MANIFEST_FILE;
    use std::collections::BTreeSet;
    use std::process::Command as GitCommand;

    const FROZEN_TARGETS: [&str; 10] = [
        "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
        "26.1.2",
    ];
    const FROZEN_VERSION: &str = "9.99.99-fixture";
    const FROZEN_PRESET: &str = "released-9.99.99-fixture";

    #[derive(Facet)]
    struct MatrixPreview {
        schema: String,
        scope: String,
        source_commit: String,
        source_manifest_sha256: String,
        release_mod_version: String,
        release_preset_id: String,
        targets: Vec<MatrixTargetPreview>,
    }

    #[derive(Facet)]
    struct MatrixTargetPreview {
        target_id: String,
        minecraft_version: String,
        development_preset_id: String,
        development_preset_identity: String,
        enabled_features: Vec<String>,
        inventory_path: String,
        inventory_sha256: String,
        canonical_inventory_json: String,
    }

    struct FrozenCandidateFixture {
        _temp: tempfile::TempDir,
        repo: PathBuf,
        authored_commit: String,
        source_commit: String,
        roots: BTreeMap<String, PathBuf>,
        lock: SourceCandidateLock,
    }

    fn write_fixture(root: &Path, relative: &str, bytes: &[u8]) {
        let path = root.join(relative);
        fs::create_dir_all(path.parent().unwrap()).unwrap();
        fs::write(path, bytes).unwrap();
    }

    fn git_fixture(root: &Path, args: &[&str]) -> String {
        let output = GitCommand::new("git")
            .arg("-C")
            .arg(root)
            .args(args)
            .output()
            .unwrap();
        assert!(
            output.status.success(),
            "git {args:?}: {}",
            String::from_utf8_lossy(&output.stderr)
        );
        String::from_utf8(output.stdout).unwrap().trim().to_owned()
    }

    fn frozen_project_args(repo: &Path, target: &str, output: &Path) -> SourceProjectArgs {
        SourceProjectArgs {
            repo_root: repo.to_path_buf(),
            target: target.to_owned(),
            preset: FROZEN_PRESET.to_owned(),
            manifest: None,
            primary_src_root: None,
            gradle_project_root: None,
            output_root: output.to_path_buf(),
            overlay: Vec::new(),
            gradle_overlay: Vec::new(),
        }
    }

    impl FrozenCandidateFixture {
        fn new() -> Self {
            let temp = tempfile::tempdir().unwrap();
            let repo = temp.path().join("authored-repo");
            fs::create_dir_all(&repo).unwrap();
            let repo = fs::canonicalize(repo).unwrap();
            git_fixture(&repo, &["init", "--quiet"]);
            git_fixture(&repo, &["config", "user.name", "Frozen candidate fixture"]);
            git_fixture(
                &repo,
                &["config", "user.email", "frozen-candidate@example.invalid"],
            );
            write_fixture(
                &repo,
                "platform/minecraft/src/main/java/example/Proof.java",
                b"class Proof {}\n",
            );
            for name in [
                "build.gradle",
                "settings.gradle",
                "gradlew",
                "gradlew.bat",
                "sfm-toolchain.lock.json",
            ] {
                write_fixture(
                    &repo,
                    &format!("platform/minecraft/{name}"),
                    name.as_bytes(),
                );
            }
            write_fixture(
                &repo,
                "platform/minecraft/gradle.properties",
                b"minecraft_version=1.19.2\nmod_version=4.34.0\nneo_version=1.2.3\n",
            );
            write_fixture(
                &repo,
                "platform/minecraft/gradle/wrapper/gradle-wrapper.jar",
                b"synthetic wrapper\n",
            );
            write_fixture(
                &repo,
                "platform/minecraft/gradle/wrapper/gradle-wrapper.properties",
                b"distributionUrl=https://example.invalid/gradle-8.12-bin.zip\n",
            );
            let mut manifest = SourceProjectionManifest {
                schema_version: SCHEMA_VERSION,
                targets: FROZEN_TARGETS
                    .into_iter()
                    .map(|id| ProjectionTarget {
                        id: id.to_owned(),
                        template_key: format!("mc_{}", id.replace('.', "_")),
                        minecraft_version: "1.19.2".to_owned(),
                        loader: "forge".to_owned(),
                        java_major: 17,
                        project_dir: format!("platform/minecraft/mc-version/{id}"),
                    })
                    .collect(),
                features: Vec::new(),
                presets: FROZEN_TARGETS
                    .into_iter()
                    .map(|id| ProjectionPreset {
                        id: format!("current-development-head-{id}"),
                        release_mod_version: None,
                        targets: vec![id.to_owned()],
                        enabled_features: Vec::new(),
                        target_features: BTreeMap::new(),
                        release_baselines: Vec::new(),
                        frozen_source_commit: None,
                        frozen_sources: Vec::new(),
                        canonical_project_fixture_provenance_sha256: None,
                        identity: String::new(),
                    })
                    .collect(),
            };
            for index in 0..manifest.presets.len() {
                manifest.presets[index].identity = manifest
                    .compute_preset_identity(&manifest.presets[index])
                    .unwrap();
            }
            let development_identities = manifest
                .presets
                .iter()
                .map(|preset| (preset.id.clone(), preset.identity.clone()))
                .collect::<BTreeMap<_, _>>();
            write_fixture(
                &repo,
                "platform/minecraft/source-projection.json",
                manifest.to_json().unwrap().as_bytes(),
            );
            git_fixture(&repo, &["add", "--", "platform/minecraft"]);
            git_fixture(&repo, &["commit", "--quiet", "-m", "authored inputs A"]);
            let authored_commit = git_fixture(&repo, &["rev-parse", "HEAD"]);

            let output = SourceFrozenInventoryMatrixPreviewArgs {
                repo_root: repo.clone(),
                source_commit: authored_commit.clone(),
                release_mod_version: FROZEN_VERSION.to_owned(),
                manifest: None,
                selection: FROZEN_TARGETS
                    .into_iter()
                    .map(|id| format!("{id}=current-development-head-{id}"))
                    .collect(),
            }
            .invoke_in(&CancellationToken::new(), &repo)
            .unwrap();
            let preview: MatrixPreview = facet_json::from_str(
                &output
                    .render(Some(OutputFormat::Json), false)
                    .unwrap()
                    .unwrap(),
            )
            .unwrap();
            assert_eq!(preview.schema, "sfm:frozen_inventory_matrix_preview@1");
            assert!(preview.scope.contains("read-only authoring preview"));
            assert_eq!(preview.source_commit, authored_commit);
            assert_eq!(preview.release_mod_version, FROZEN_VERSION);
            assert_eq!(preview.release_preset_id, FROZEN_PRESET);
            assert_eq!(preview.targets.len(), FROZEN_TARGETS.len());
            assert_eq!(
                preview.source_manifest_sha256,
                sha256(&fs::read(repo.join("platform/minecraft/source-projection.json")).unwrap())
            );

            let mut bindings = Vec::new();
            let mut inventory_hashes = BTreeSet::new();
            for (id, target) in FROZEN_TARGETS.into_iter().zip(&preview.targets) {
                assert_eq!(target.target_id, id);
                assert_eq!(target.minecraft_version, "1.19.2");
                assert_eq!(
                    target.development_preset_id,
                    format!("current-development-head-{id}")
                );
                assert_eq!(
                    target.development_preset_identity,
                    manifest
                        .preset(&target.development_preset_id)
                        .unwrap()
                        .identity
                );
                assert!(target.enabled_features.is_empty());
                assert!(target.canonical_inventory_json.ends_with('\n'));
                assert_eq!(
                    sha256(target.canonical_inventory_json.as_bytes()),
                    format!("sha256:{}", target.inventory_sha256)
                );
                assert!(inventory_hashes.insert(target.inventory_sha256.clone()));
                write_fixture(
                    &repo,
                    &target.inventory_path,
                    target.canonical_inventory_json.as_bytes(),
                );
                bindings.push(FrozenSourceBinding {
                    target_id: id.to_owned(),
                    inventory_path: target.inventory_path.clone(),
                    inventory_sha256: target.inventory_sha256.clone(),
                });
                write_fixture(
                    &repo,
                    &format!("platform/minecraft/mc-version/{id}/sentinel.txt"),
                    b"original checked-in project\n",
                );
            }
            assert_eq!(inventory_hashes.len(), FROZEN_TARGETS.len());
            let mut frozen_preset = ProjectionPreset {
                id: FROZEN_PRESET.to_owned(),
                release_mod_version: Some(FROZEN_VERSION.to_owned()),
                targets: FROZEN_TARGETS.into_iter().map(str::to_owned).collect(),
                enabled_features: Vec::new(),
                target_features: BTreeMap::new(),
                release_baselines: Vec::new(),
                frozen_source_commit: Some(authored_commit.clone()),
                frozen_sources: bindings,
                canonical_project_fixture_provenance_sha256: None,
                identity: String::new(),
            };
            frozen_preset.identity = manifest.compute_preset_identity(&frozen_preset).unwrap();
            let frozen_identity = frozen_preset.identity.clone();
            manifest.presets.push(frozen_preset);
            let frozen_manifest = manifest.to_json().unwrap();
            write_fixture(
                &repo,
                "platform/minecraft/source-projection.json",
                frozen_manifest.as_bytes(),
            );
            let note = b"Synthetic compatibility evidence for a candidate-lock rehearsal.\n";
            write_fixture(&repo, "docs/compatibility.md", note);
            git_fixture(&repo, &["add", "--", "platform/minecraft", "docs"]);
            git_fixture(&repo, &["commit", "--quiet", "-m", "frozen preset B"]);
            let source_commit = git_fixture(&repo, &["rev-parse", "HEAD"]);
            assert_ne!(source_commit, authored_commit);
            assert_eq!(git_fixture(&repo, &["status", "--porcelain"]), "");
            let persisted = SourceProjectionManifest::from_json(
                &fs::read_to_string(repo.join("platform/minecraft/source-projection.json"))
                    .unwrap(),
            )
            .unwrap();
            for (id, identity) in development_identities {
                assert_eq!(persisted.preset(&id).unwrap().identity, identity);
            }
            assert_eq!(
                persisted
                    .preset(FROZEN_PRESET)
                    .unwrap()
                    .frozen_source_commit,
                Some(authored_commit.clone())
            );

            let external = temp.path().join("external-candidates");
            fs::create_dir_all(&external).unwrap();
            let external = fs::canonicalize(external).unwrap();
            assert!(!external.starts_with(&repo));
            let mut roots = BTreeMap::new();
            let mut targets = Vec::new();
            let mut jar_hashes = BTreeSet::new();
            let cancellation = CancellationToken::new();
            for id in FROZEN_TARGETS {
                let root = external.join(id);
                SourceArgs {
                    command: SourceCommand::Sync(frozen_project_args(&repo, id, &root)),
                }
                .invoke_in(&cancellation, &repo)
                .unwrap();
                frozen_project_args(&repo, id, &root)
                    .check_candidate_in(&cancellation, &repo)
                    .unwrap();
                assert_eq!(
                    fs::read(repo.join(format!("platform/minecraft/mc-version/{id}/sentinel.txt")))
                        .unwrap(),
                    b"original checked-in project\n"
                );
                let jar_name = format!("SFM-{id}-MC1.19.2-{FROZEN_VERSION}.jar");
                let jar_relative = format!("build/libs/{jar_name}");
                let jar_bytes = format!("synthetic production JAR bytes for {id}\n");
                write_fixture(&root, &jar_relative, jar_bytes.as_bytes());
                let jar_hash = sha256(jar_bytes.as_bytes());
                assert!(jar_hashes.insert(jar_hash.clone()));
                let task = match id {
                    "1.19.2" | "1.19.4" | "1.20" | "1.20.1" => "reobfJar",
                    "1.20.2" | "1.20.3" | "1.20.4" | "1.21.0" | "1.21.1" => "jar",
                    "26.1.2" => "jarJar",
                    _ => unreachable!(),
                };
                targets.push(
                    crate::source_projection::candidate_lock::CandidateTargetLock {
                        target_id: id.to_owned(),
                        minecraft_version: "1.19.2".to_owned(),
                        loader: "forge".to_owned(),
                        loader_version: "1.2.3".to_owned(),
                        gradle_profile: "default".to_owned(),
                        production_task: task.to_owned(),
                        jdk_major: 17,
                        jdk_build_id: "JBRSDK-17.0.1".to_owned(),
                        provenance_manifest_sha256: sha256(
                            &fs::read(root.join(MANIFEST_FILE)).unwrap(),
                        ),
                        production_jar_relative_path: jar_relative,
                        production_jar_sha256: jar_hash,
                    },
                );
                roots.insert(id.to_owned(), fs::canonicalize(root).unwrap());
            }
            assert_eq!(jar_hashes.len(), FROZEN_TARGETS.len());
            let lock = SourceCandidateLock {
                schema: "sfm:source_candidate_lock@1".to_owned(),
                source_commit: source_commit.clone(),
                source_manifest_sha256: sha256(frozen_manifest.as_bytes()),
                mod_version: FROZEN_VERSION.to_owned(),
                candidate_preset_id: FROZEN_PRESET.to_owned(),
                candidate_definition_identity: frozen_identity,
                compatibility_evidence_relative_path: "docs/compatibility.md".to_owned(),
                compatibility_evidence_sha256: sha256(note),
                targets,
            };
            Self {
                _temp: temp,
                repo,
                authored_commit,
                source_commit,
                roots,
                lock,
            }
        }

        fn verify(
            &self,
            lock: &SourceCandidateLock,
            roots: &BTreeMap<String, PathBuf>,
            overlays: Option<&BTreeMap<String, String>>,
        ) -> Result<CandidateVerificationReport> {
            let lock_bytes = facet_json::to_string(lock)?;
            verify_candidate_in(
                &CancellationToken::new(),
                &self.repo,
                lock,
                roots,
                sha256(lock_bytes.as_bytes()),
                overlays,
            )
        }
    }

    fn ordinary_preset() -> ProjectionPreset {
        ProjectionPreset {
            id: "released-9.99.99-fixture".to_owned(),
            release_mod_version: Some("9.99.99".to_owned()),
            targets: vec!["1.19.2".to_owned()],
            enabled_features: Vec::new(),
            target_features: BTreeMap::new(),
            release_baselines: Vec::new(),
            frozen_source_commit: None,
            frozen_sources: Vec::new(),
            canonical_project_fixture_provenance_sha256: None,
            identity: String::new(),
        }
    }

    fn target() -> ProjectionTarget {
        ProjectionTarget {
            id: "1.19.2".to_owned(),
            template_key: "mc_1_19_2".to_owned(),
            minecraft_version: "1.19.2".to_owned(),
            loader: "forge".to_owned(),
            java_major: 17,
            project_dir: "platform/minecraft/mc-version/1.19.2".to_owned(),
        }
    }

    #[test]
    fn frozen_candidate_gradle_overlay_is_empty() {
        let mut preset = ordinary_preset();
        preset.frozen_source_commit = Some("a".repeat(40));
        preset.frozen_sources.push(FrozenSourceBinding {
            target_id: "1.19.2".to_owned(),
            inventory_path:
                "platform/minecraft/frozen-releases/released-9.99.99-fixture/1.19.2/inventory.json"
                    .to_owned(),
            inventory_sha256: "b".repeat(64),
        });

        assert!(candidate_gradle_overlay(&preset, &target()).is_empty());
    }

    #[test]
    fn ordinary_candidate_gradle_overlay_uses_target_project_dir() {
        assert_eq!(
            candidate_gradle_overlay(&ordinary_preset(), &target()),
            vec!["target=platform/minecraft/mc-version/1.19.2".to_owned()]
        );
    }

    #[test]
    fn candidate_verify_parses_distinct_portable_lock_and_local_roots() {
        let parsed = figue::from_slice::<Cli>(&[
            "source",
            "candidate-verify",
            "--repo-root",
            "C:/reviewed/repo",
            "--lock",
            "candidate.json",
            "--candidate-root",
            "1.19.2=C:/candidate/1.19.2",
        ])
        .into_result()
        .unwrap()
        .get_silent();
        let Command::Source(SourceArgs {
            command: SourceCommand::CandidateVerify(args),
        }) = parsed.command
        else {
            panic!("expected source candidate-verify command");
        };
        assert_eq!(args.lock, PathBuf::from("candidate.json"));
        assert_eq!(args.candidate_root.len(), 1);
    }

    #[test]
    fn local_root_mapping_rejects_duplicates_and_relative_paths() {
        let absolute = std::env::temp_dir().join("candidate");
        let entry = format!("1.19.2={}", absolute.display());
        let _ = parse_roots(&[entry.clone(), entry]).unwrap_err();
        let _ = parse_roots(&["1.19.2=relative/path".to_owned()]).unwrap_err();
        let _ = parse_roots(&["1.19.2".to_owned()]).unwrap_err();
    }

    #[test]
    fn frozen_candidate_lock_rehearses_ten_external_roots_and_rejects_drift() {
        let fixture = FrozenCandidateFixture::new();
        assert_eq!(fixture.lock.source_commit, fixture.source_commit);
        assert_ne!(fixture.authored_commit, fixture.source_commit);
        assert_eq!(fixture.lock.targets.len(), FROZEN_TARGETS.len());
        assert_eq!(fixture.roots.len(), FROZEN_TARGETS.len());
        let report = fixture.verify(&fixture.lock, &fixture.roots, None).unwrap();
        assert_eq!(report.schema, "sfm:source_candidate_verification@2");
        assert_eq!(report.source_commit, fixture.source_commit);
        assert_eq!(report.candidate_preset_id, FROZEN_PRESET);
        assert_eq!(report.verified_targets.len(), FROZEN_TARGETS.len());
        assert!(report.deterministic_source_check);
        assert!(report.toolchain_fields_are_reviewed_assertions);

        let mut wrong_commit =
            SourceCandidateLock::from_json(&facet_json::to_string(&fixture.lock).unwrap()).unwrap();
        wrong_commit.source_commit = fixture.authored_commit.clone();
        let error = fixture
            .verify(&wrong_commit, &fixture.roots, None)
            .unwrap_err()
            .to_string();
        assert!(
            error
                .contains("source commit does not contain the locked source-projection definition"),
            "{error}"
        );

        let mut missing_root = fixture.roots.clone();
        missing_root.remove("26.1.2");
        let error = fixture
            .verify(&fixture.lock, &missing_root, None)
            .unwrap_err()
            .to_string();
        assert!(
            error.contains("local candidate roots do not match complete locked target matrix"),
            "{error}"
        );

        let mut overlapping_roots = fixture.roots.clone();
        overlapping_roots.insert("1.19.4".to_owned(), fixture.roots["1.19.2"].clone());
        let error = fixture
            .verify(&fixture.lock, &overlapping_roots, None)
            .unwrap_err()
            .to_string();
        assert!(error.contains("candidate roots overlap"), "{error}");

        let manifest_path = fixture
            .repo
            .join("platform/minecraft/source-projection.json");
        let manifest_bytes = fs::read(&manifest_path).unwrap();
        let manifest =
            SourceProjectionManifest::from_json(std::str::from_utf8(&manifest_bytes).unwrap())
                .unwrap();
        fs::write(&manifest_path, b"{}\n").unwrap();
        let error = fixture
            .verify(&fixture.lock, &fixture.roots, None)
            .unwrap_err()
            .to_string();
        assert!(
            error.contains("current source-projection definition differs from candidate lock"),
            "{error}"
        );
        fs::write(&manifest_path, manifest_bytes).unwrap();

        let inventory_path = fixture
            .repo
            .join(&manifest.preset(FROZEN_PRESET).unwrap().frozen_sources[0].inventory_path);
        let inventory_bytes = fs::read(&inventory_path).unwrap();
        fs::write(&inventory_path, b"changed committed inventory\n").unwrap();
        let error = fixture
            .verify(&fixture.lock, &fixture.roots, None)
            .unwrap_err()
            .to_string();
        assert!(
            error.contains("authored platform/minecraft inputs differ from source commit"),
            "{error}"
        );
        fs::write(&inventory_path, inventory_bytes).unwrap();

        let first = &fixture.lock.targets[0];
        let first_root = &fixture.roots[&first.target_id];
        let jar = first_root.join(&first.production_jar_relative_path);
        let jar_bytes = fs::read(&jar).unwrap();
        fs::write(&jar, b"changed synthetic production JAR\n").unwrap();
        let error = fixture
            .verify(&fixture.lock, &fixture.roots, None)
            .unwrap_err()
            .to_string();
        assert!(
            error.contains("candidate production JAR hash mismatch"),
            "{error}"
        );
        fs::write(&jar, jar_bytes).unwrap();

        let output = first_root.join("src/main/java/example/Proof.java");
        let output_bytes = fs::read(&output).unwrap();
        fs::write(&output, b"class Proof { int changed = 1; }\n").unwrap();
        let error = fixture
            .verify(&fixture.lock, &fixture.roots, None)
            .unwrap_err()
            .to_string();
        assert!(error.contains("candidate output hash mismatch"), "{error}");
        fs::write(&output, output_bytes).unwrap();

        let manual_overlays = FROZEN_TARGETS
            .into_iter()
            .map(|id| (id.to_owned(), "platform/minecraft".to_owned()))
            .collect::<BTreeMap<_, _>>();
        let error = fixture
            .verify(&fixture.lock, &fixture.roots, Some(&manual_overlays))
            .unwrap_err()
            .to_string();
        assert!(
            error.contains("frozen release preset cannot use manual source or Gradle overrides"),
            "{error}"
        );
        assert_eq!(git_fixture(&fixture.repo, &["status", "--porcelain"]), "");
    }
}
