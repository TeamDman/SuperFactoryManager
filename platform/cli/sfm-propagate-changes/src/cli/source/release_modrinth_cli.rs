//! Prepare one owned Modrinth request without constructing an HTTP client.
//!
//! The complete package and promoted local target are reverified first. The
//! selected JAR is then read once into bounded, hash-checked bytes consumed by
//! the shared provider service. Keep the package and checkout quiescent: this
//! is not an atomic snapshot of the other JARs or Git state.

use super::release_target_plan_cli::ReleaseTargetPlanArgs;
use super::release_target_plan_cli::ReleaseTargetPlanReport;
use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use crate::modrinth::ModrinthVersionUpload;
use crate::source_projection::candidate_lock::checked_directory;
use crate::source_projection::candidate_lock::checked_file;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;
use figue::{self as args};
use sha2::Digest as _;
use sha2::Sha256;
use std::fs::File;
use std::io::Read as _;
use std::path::Path;

const REPORT_SCHEMA: &str = "sfm:source_release_modrinth@1";
const REPORT_SCOPE: &str = "read-only owned Modrinth request preparation; no HTTP client, credentials, remote checks, tag creation, upload or publication";
const MAX_SELECTED_JAR_BYTES: u64 = 64 * 1024 * 1024;

#[derive(Clone, Debug, Facet)]
pub struct ReleaseModrinthArgs {
    /// Reverify the complete package, reviewed metadata and promoted local target.
    #[facet(flatten)]
    pub target_plan: ReleaseTargetPlanArgs,
    /// Caller-reviewed SHA-256 of the exact target-plan Modrinth metadata JSON.
    #[facet(args::named)]
    pub request_metadata_sha256: String,
}

#[derive(Debug, Facet)]
struct ReleaseModrinthReport {
    schema: String,
    scope: String,
    target_plan: ReleaseTargetPlanReport,
    owned_jar_bytes: usize,
    dry_run: bool,
    upload_performed: bool,
}

struct PreparedModrinthRelease {
    report: ReleaseModrinthReport,
    upload: ModrinthVersionUpload,
}

impl ReleaseModrinthArgs {
    /// Prepare a verified JAR of at most 64 MiB, then report without sending it.
    ///
    /// # Errors
    ///
    /// Rejects changed package/local release state, unreviewed metadata, unsafe
    /// or nonregular JAR paths, empty/oversized JARs, hash drift or cancellation.
    pub(super) fn invoke_in(self, cancellation: &CancellationToken) -> Result<CliOutput> {
        let prepared = self.prepare_in(cancellation)?;
        drop(prepared.upload);
        Ok(CliOutput::facet(prepared.report))
    }

    fn prepare_in(self, cancellation: &CancellationToken) -> Result<PreparedModrinthRelease> {
        cancellation.bail_if_cancelled()?;
        let package_root = self.target_plan.provider_plan.package_root.clone();
        let target_plan = self.target_plan.plan_in(cancellation)?;
        ensure!(
            self.request_metadata_sha256 == target_plan.modrinth_request_metadata_sha256,
            "--request-metadata-sha256 differs from the verified target-plan metadata"
        );
        cancellation.bail_if_cancelled()?;
        let bytes = read_selected_jar(
            &package_root,
            &target_plan.target.file_name,
            &target_plan.target.sha256,
            cancellation,
        )?;
        let owned_jar_bytes = bytes.len();
        let upload = ModrinthVersionUpload::new(
            target_plan.modrinth_request_metadata_json.clone(),
            target_plan.target.file_name.clone(),
            bytes,
        )?;
        cancellation.bail_if_cancelled()?;
        Ok(PreparedModrinthRelease {
            report: ReleaseModrinthReport {
                schema: REPORT_SCHEMA.to_owned(),
                scope: REPORT_SCOPE.to_owned(),
                target_plan,
                owned_jar_bytes,
                dry_run: true,
                upload_performed: false,
            },
            upload,
        })
    }
}

fn read_selected_jar(
    package_root: &Path,
    file_name: &str,
    expected_sha256: &str,
    cancellation: &CancellationToken,
) -> Result<Vec<u8>> {
    cancellation.bail_if_cancelled()?;
    let root = checked_directory(package_root)?;
    let path = checked_file(&root, file_name)?;
    cancellation.bail_if_cancelled()?;
    let mut file = File::open(path).wrap_err("cannot open the selected production JAR")?;
    let metadata = file.metadata()?;
    ensure!(
        metadata.is_file(),
        "selected production JAR is not a regular file"
    );
    ensure!(
        metadata.len() > 0 && metadata.len() <= MAX_SELECTED_JAR_BYTES,
        "selected production JAR must be nonempty and at most 64 MiB ({MAX_SELECTED_JAR_BYTES} bytes)"
    );
    let bytes = read_owned_bytes(
        &mut file,
        expected_sha256,
        MAX_SELECTED_JAR_BYTES,
        cancellation,
    )?;
    ensure!(
        metadata.len() == bytes.len() as u64 && file.metadata()?.len() == metadata.len(),
        "selected production JAR length changed during preparation"
    );
    cancellation.bail_if_cancelled()?;
    Ok(bytes)
}

fn read_owned_bytes(
    reader: &mut impl std::io::Read,
    expected_sha256: &str,
    limit: u64,
    cancellation: &CancellationToken,
) -> Result<Vec<u8>> {
    cancellation.bail_if_cancelled()?;
    let read_limit = limit
        .checked_add(1)
        .ok_or_else(|| eyre::eyre!("invalid selected production JAR read limit"))?;
    let mut reader = reader.take(read_limit);
    let mut bytes = Vec::new();
    let mut hasher = Sha256::new();
    let mut buffer = Vec::new();
    buffer
        .try_reserve_exact(65_536)
        .wrap_err("cannot allocate the selected production JAR read buffer")?;
    buffer.resize(65_536, 0);
    loop {
        cancellation.bail_if_cancelled()?;
        let count = reader.read(&mut buffer)?;
        cancellation.bail_if_cancelled()?;
        if count == 0 {
            break;
        }
        ensure!(
            bytes.len() as u64 + count as u64 <= limit,
            "selected production JAR exceeds the {limit}-byte limit"
        );
        bytes
            .try_reserve(count)
            .wrap_err("cannot allocate the selected production JAR bytes")?;
        bytes.extend_from_slice(&buffer[..count]);
        hasher.update(&buffer[..count]);
    }
    ensure!(!bytes.is_empty(), "selected production JAR is empty");
    ensure!(
        format!("sha256:{:x}", hasher.finalize()) == expected_sha256,
        "selected production JAR SHA-256 differs during preparation"
    );
    cancellation.bail_if_cancelled()?;
    Ok(bytes)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::cli::Cli;
    use crate::cli::Command;
    use crate::cli::output::OutputFormat;
    use crate::cli::source::LegacySourceArgs;
    use crate::cli::source::LegacySourceCommand;
    use crate::cli::source::release_target_plan_cli::tests::git;
    use crate::cli::source::release_target_plan_cli::tests::packaged_candidate;
    use crate::modrinth::ModrinthCreateVersionPayload;
    use crate::modrinth::create_version_with;
    use crate::source_projection::provenance::sha256;
    use std::cell::Cell;
    use std::fs;
    use std::io::Cursor;
    use std::io::Read;

    fn reviewed_args(target_plan: ReleaseTargetPlanArgs) -> ReleaseModrinthArgs {
        let report = target_plan
            .clone()
            .plan_in(&CancellationToken::new())
            .unwrap();
        ReleaseModrinthArgs {
            target_plan,
            request_metadata_sha256: report.modrinth_request_metadata_sha256,
        }
    }

    fn offline_client() -> reqwest::blocking::Client {
        reqwest::blocking::Client::builder()
            .no_proxy()
            .build()
            .unwrap()
    }

    fn submit_offline(
        args: ReleaseModrinthArgs,
        cancellation: &CancellationToken,
        calls: &Cell<usize>,
    ) -> Result<String> {
        let prepared = args.prepare_in(cancellation)?;
        cancellation.bail_if_cancelled()?;
        create_version_with(&offline_client(), prepared.upload, |_request| {
            calls.set(calls.get() + 1);
            Ok((reqwest::StatusCode::OK, r#"{"id":"Created123"}"#.to_owned()))
        })
    }

    fn assert_rejected_without_send(args: ReleaseModrinthArgs) {
        let calls = Cell::new(0);
        assert!(submit_offline(args, &CancellationToken::new(), &calls).is_err());
        assert_eq!(calls.get(), 0);
    }

    fn assert_multipart(
        prepared: PreparedModrinthRelease,
        expected_metadata: &str,
        expected_file_name: &str,
        expected_bytes: &[u8],
    ) {
        let calls = Cell::new(0);
        let version = create_version_with(&offline_client(), prepared.upload, |mut request| {
            calls.set(calls.get() + 1);
            assert_eq!(request.method(), reqwest::Method::POST);
            assert_eq!(
                request.url().as_str(),
                "https://api.modrinth.com/v2/version"
            );
            let content_type = request
                .headers()
                .get(reqwest::header::CONTENT_TYPE)
                .unwrap()
                .to_str()
                .unwrap();
            assert!(content_type.starts_with("multipart/form-data; boundary="));
            let body = request.body_mut().as_mut().unwrap().buffer()?.to_vec();
            let text = String::from_utf8_lossy(&body);
            assert!(text.contains("name=\"data\""));
            assert!(text.contains(expected_metadata));
            assert!(text.contains(&format!("filename=\"{expected_file_name}\"")));
            assert!(text.contains("name=\"file\""));
            assert!(text.contains("Content-Type: application/java-archive"));
            assert!(
                body.windows(expected_bytes.len())
                    .any(|part| part == expected_bytes)
            );
            Ok((reqwest::StatusCode::OK, r#"{"id":"Created123"}"#.to_owned()))
        })
        .unwrap();
        assert_eq!(version, "Created123");
        assert_eq!(calls.get(), 1);
    }

    #[test]
    fn cli_requires_reviewed_metadata_and_rejects_effect_or_secret_options() {
        let arguments = [
            "source",
            "legacy",
            "release-modrinth",
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
            "--request-metadata-sha256",
            "sha256:eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee",
        ];
        let parsed = figue::from_slice::<Cli>(&arguments)
            .into_result()
            .unwrap()
            .get_silent();
        let Command::Source(crate::cli::source::SourceArgs {
            command:
                crate::cli::source::SourceCommand::Legacy(LegacySourceArgs {
                    command: LegacySourceCommand::ReleaseModrinth(args),
                }),
        }) = parsed.command
        else {
            panic!("expected source release-modrinth command");
        };
        assert_eq!(args.target_plan.target_id, "1.21.0");
        assert_eq!(args.request_metadata_sha256, arguments[arguments.len() - 1]);
        assert!(
            figue::from_slice::<Cli>(&arguments[..arguments.len() - 2])
                .into_result()
                .is_err()
        );
        for forbidden in [
            "--apply",
            "--token",
            "--credential",
            "--api-token",
            "--secret",
        ] {
            let mut rejected = arguments.to_vec();
            rejected.push(forbidden);
            assert!(figue::from_slice::<Cli>(&rejected).into_result().is_err());
        }
    }

    #[test]
    fn public_report_preserves_target_plan_and_has_no_file_or_provider_effects() {
        let (fixture, target_plan) = packaged_candidate();
        let expected = facet_json::to_string(
            &target_plan
                .clone()
                .plan_in(&CancellationToken::new())
                .unwrap(),
        )
        .unwrap();
        let args = reviewed_args(target_plan);
        let before_files = fs::read_dir(&args.target_plan.provider_plan.package_root)
            .unwrap()
            .count();
        let rendered = args
            .clone()
            .invoke_in(&CancellationToken::new())
            .unwrap()
            .render(Some(OutputFormat::Json), false)
            .unwrap()
            .unwrap();
        let report: ReleaseModrinthReport = facet_json::from_str(&rendered).unwrap();
        assert_eq!(report.schema, REPORT_SCHEMA);
        assert_eq!(
            facet_json::to_string(&report.target_plan).unwrap(),
            expected
        );
        assert!(report.dry_run);
        assert!(!report.upload_performed);
        assert_eq!(report.target_plan.target.target_id, "1.21.0");
        assert_eq!(report.target_plan.target.minecraft_version, "1.21");
        let selected = args
            .target_plan
            .provider_plan
            .package_root
            .join(&report.target_plan.target.file_name);
        let selected_bytes = fs::read(&selected).unwrap();
        assert_eq!(report.owned_jar_bytes, selected_bytes.len());
        assert!(!rendered.contains(&fixture.repo().display().to_string()));
        assert!(
            !rendered.contains(
                &args
                    .target_plan
                    .provider_plan
                    .package_root
                    .display()
                    .to_string()
            )
        );
        assert!(
            !rendered.contains(
                &args
                    .target_plan
                    .provider_plan
                    .changelog_file
                    .display()
                    .to_string()
            )
        );
        assert!(!rendered.contains("file_bytes"));
        assert_eq!(
            fs::read_dir(&args.target_plan.provider_plan.package_root)
                .unwrap()
                .count(),
            before_files
        );
        assert_eq!(fs::read(&selected).unwrap(), selected_bytes);
        assert!(git(fixture.repo(), &["status", "--porcelain"]).is_empty());
        assert!(git(fixture.repo(), &["tag", "--list"]).is_empty());
    }

    #[test]
    fn exact_target_owned_bytes_survive_replacement_of_original_filename() {
        let (_fixture, target_plan) = packaged_candidate();
        let args = reviewed_args(target_plan);
        let prepared = args.clone().prepare_in(&CancellationToken::new()).unwrap();
        let metadata = prepared
            .report
            .target_plan
            .modrinth_request_metadata_json
            .clone();
        let file_name = prepared.report.target_plan.target.file_name.clone();
        assert_eq!(prepared.report.target_plan.target.target_id, "1.21.0");
        assert_eq!(file_name, "SFM-MC1.21-4.35.0.jar");
        let path = args.target_plan.provider_plan.package_root.join(&file_name);
        let bytes = fs::read(&path).unwrap();
        fs::rename(&path, path.with_extension("retained")).unwrap();
        fs::write(&path, b"replacement that must never be uploaded").unwrap();
        assert_multipart(prepared, &metadata, &file_name, &bytes);
        assert_eq!(
            fs::read(&path).unwrap(),
            b"replacement that must never be uploaded"
        );
    }

    #[test]
    fn both_reviewed_transitional_loader_policies_use_the_shared_request() {
        let (_fixture, mut target_plan) = packaged_candidate();
        target_plan.target_id = "1.20.1".to_owned();
        target_plan.provider_plan.reviewed_tag = "4.35.0-1.20.1".to_owned();
        for (policy, loaders) in [
            ("dual-forge-neoforge", vec!["forge", "neoforge"]),
            ("neoforge-only", vec!["neoforge"]),
        ] {
            target_plan.provider_plan.modrinth_1201_loader_policy = policy.to_owned();
            let args = reviewed_args(target_plan.clone());
            let prepared = args.clone().prepare_in(&CancellationToken::new()).unwrap();
            let metadata = prepared
                .report
                .target_plan
                .modrinth_request_metadata_json
                .clone();
            let payload: ModrinthCreateVersionPayload = facet_json::from_str(&metadata).unwrap();
            assert_eq!(payload.loaders, loaders);
            assert_eq!(payload.game_versions, ["1.20.1"]);
            let file_name = prepared.report.target_plan.target.file_name.clone();
            let bytes =
                fs::read(args.target_plan.provider_plan.package_root.join(&file_name)).unwrap();
            assert_multipart(prepared, &metadata, &file_name, &bytes);
        }
    }

    #[test]
    fn changed_artifacts_metadata_local_head_tag_and_notes_never_reach_send() {
        let (fixture, target_plan) = packaged_candidate();
        let args = reviewed_args(target_plan);
        let mut wrong_package_digest = args.clone();
        wrong_package_digest
            .target_plan
            .provider_plan
            .completion_manifest_sha256 = sha256(b"different completion manifest");
        assert_rejected_without_send(wrong_package_digest);
        let mut stale_package_source = args.clone();
        stale_package_source
            .target_plan
            .provider_plan
            .reviewed_source_commit = "a".repeat(40);
        assert_rejected_without_send(stale_package_source);
        let mut wrong_metadata = args.clone();
        wrong_metadata.request_metadata_sha256 = sha256(b"different metadata");
        assert_rejected_without_send(wrong_metadata);
        let mut stale = args.clone();
        stale.target_plan.reviewed_release_commit = fixture.lock().source_commit.clone();
        assert_rejected_without_send(stale);
        let mut wrong_target = args.clone();
        wrong_target.target_plan.provider_plan.reviewed_tag = "4.35.0-1.21".to_owned();
        assert_rejected_without_send(wrong_target);
        let mut wrong_target = args.clone();
        wrong_target.target_plan.target_id = "1.21".to_owned();
        assert_rejected_without_send(wrong_target);
        for file_name in ["SFM-MC1.21-4.35.0.jar", "SFM-MC26.1.2-4.35.0.jar"] {
            let path = args.target_plan.provider_plan.package_root.join(file_name);
            let original = fs::read(&path).unwrap();
            fs::write(&path, b"changed selected or unselected JAR").unwrap();
            assert_rejected_without_send(args.clone());
            fs::write(&path, original).unwrap();
        }
        let notes = &args.target_plan.provider_plan.changelog_file;
        let original = fs::read(notes).unwrap();
        fs::write(notes, b"changed notes").unwrap();
        assert_rejected_without_send(args.clone());
        fs::write(notes, original).unwrap();
        git(
            fixture.repo(),
            &["tag", "4.35.0-1.21.0", &fixture.lock().source_commit],
        );
        assert_rejected_without_send(args);
    }

    #[test]
    fn cancellation_before_preparation_never_reaches_send() {
        let (_fixture, target_plan) = packaged_candidate();
        let args = reviewed_args(target_plan);
        let cancellation = CancellationToken::new();
        cancellation.request_cancel("cancel before request preparation");
        let calls = Cell::new(0);
        let error = submit_offline(args, &cancellation, &calls).unwrap_err();
        assert!(
            error
                .to_string()
                .contains("cancel before request preparation")
        );
        assert_eq!(calls.get(), 0);
    }

    #[test]
    fn owned_reader_enforces_bound_hash_empty_and_mid_read_cancellation() {
        let bytes = b"reviewed";
        assert_eq!(
            read_owned_bytes(
                &mut Cursor::new(bytes),
                &sha256(bytes),
                8,
                &CancellationToken::new()
            )
            .unwrap(),
            bytes
        );
        let error = read_owned_bytes(
            &mut Cursor::new(bytes),
            &sha256(bytes),
            7,
            &CancellationToken::new(),
        )
        .unwrap_err();
        assert!(error.to_string().contains("exceeds the 7-byte limit"));
        assert!(
            read_owned_bytes(
                &mut Cursor::new(bytes),
                &sha256(b"different"),
                8,
                &CancellationToken::new()
            )
            .is_err()
        );
        assert!(
            read_owned_bytes(
                &mut Cursor::new(b""),
                &sha256(b""),
                8,
                &CancellationToken::new()
            )
            .is_err()
        );

        struct CancellingReader {
            inner: Cursor<&'static [u8]>,
            cancellation: CancellationToken,
            reads: usize,
        }
        impl Read for CancellingReader {
            fn read(&mut self, buffer: &mut [u8]) -> std::io::Result<usize> {
                self.reads += 1;
                let count = self.inner.read(buffer)?;
                self.cancellation
                    .request_cancel("cancel during selected JAR read");
                Ok(count)
            }
        }
        let cancellation = CancellationToken::new();
        let mut reader = CancellingReader {
            inner: Cursor::new(bytes.as_slice()),
            cancellation: cancellation.clone(),
            reads: 0,
        };
        let error = read_owned_bytes(&mut reader, &sha256(bytes), 8, &cancellation).unwrap_err();
        assert!(
            error
                .to_string()
                .contains("cancel during selected JAR read")
        );
        assert_eq!(reader.reads, 1);
    }

    #[test]
    fn selected_jar_reader_rejects_nonregular_or_unsafe_paths() {
        let root = tempfile::tempdir().unwrap();
        fs::write(root.path().join("selected.jar"), b"reviewed").unwrap();
        let cancellation = CancellationToken::new();
        assert_eq!(
            read_selected_jar(
                root.path(),
                "selected.jar",
                &sha256(b"reviewed"),
                &cancellation
            )
            .unwrap(),
            b"reviewed"
        );
        fs::create_dir(root.path().join("directory.jar")).unwrap();
        assert!(
            read_selected_jar(root.path(), "directory.jar", &sha256(b""), &cancellation).is_err()
        );
        assert!(
            read_selected_jar(
                root.path(),
                "../selected.jar",
                &sha256(b"reviewed"),
                &cancellation
            )
            .is_err()
        );
    }
}
