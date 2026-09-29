//! A reviewed JSON boundary for promoting all checked-in Minecraft projects.

use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use crate::source_projection::promotion::PromotionCandidate;
use crate::source_projection::promotion::PromotionMode;
use crate::source_projection::promotion::PromotionReport;
use crate::source_projection::promotion::PromotionRequest;
use crate::source_projection::promotion::PromotionTransition;
use crate::source_projection::promotion::ReviewedOperationKind;
use crate::source_projection::promotion::ReviewedPromotionOperation;
use crate::source_projection::promotion::TargetPromotionReport;
use crate::source_projection::promotion::promote;
use crate::source_projection::promotion::validate_relative_path;
use eyre::Result;
use eyre::WrapErr;
use eyre::bail;
use eyre::ensure;
use facet::Facet;
use figue::{self as args};
use std::collections::BTreeMap;
use std::fs;
use std::io::Read as _;
use std::path::Path;
use std::path::PathBuf;

const REQUEST_SCHEMA: &str = "sfm:source_promotion_request@2";
const REPORT_SCHEMA: &str = "sfm:source_promotion_report@1";
const MAX_REQUEST_BYTES: u64 = 1024 * 1024;

#[derive(Debug, Facet)]
pub struct PromotionArgs {
    /// Reviewed local JSON request containing the ten candidate roots and hashes.
    #[facet(args::named)]
    pub request: PathBuf,
    /// Reserved for a future accepted candidate lock; currently fails closed.
    #[facet(default = false, args::named)]
    pub apply: bool,
    /// Extra confirmation reserved for a pre-acceptance baseline repair.
    #[facet(default = false, args::named)]
    pub ack_pre_acceptance_baseline_repair: bool,
}

#[derive(Debug, Facet)]
#[facet(deny_unknown_fields)]
struct PromotionRequestFile {
    schema: String,
    repository_root: String,
    reviewed_head_commit: String,
    reviewed_source_manifest_sha256: String,
    compatibility_evidence_relative_path: String,
    reviewed_compatibility_evidence_sha256: String,
    candidate_preset_id: String,
    candidate_definition_identity: String,
    transition: PromotionTransitionFile,
    accept_identical_edits: bool,
    candidates: Vec<PromotionCandidateFile>,
}

#[derive(Debug, Facet)]
#[facet(deny_unknown_fields)]
struct PromotionCandidateFile {
    target_id: String,
    project_root: String,
    reviewed_manifest_sha256: String,
    production_jar_relative_path: String,
    production_jar_sha256: String,
    production_task: String,
    jdk_major: u16,
    jdk_build_id: String,
}

#[derive(Debug, Facet)]
#[facet(deny_unknown_fields)]
struct PromotionTransitionFile {
    kind: String,
    #[facet(default)]
    expected_old_preset_id: Option<String>,
    #[facet(default)]
    expected_old_definition_identity: Option<String>,
    #[facet(default)]
    reviewed_operations: Option<Vec<ReviewedPromotionOperationFile>>,
}

#[derive(Debug, Facet)]
#[facet(deny_unknown_fields)]
struct ReviewedPromotionOperationFile {
    target_id: String,
    relative_path: String,
    kind: String,
    old_sha256: Option<String>,
    new_sha256: String,
}

#[derive(Debug, Facet)]
struct PromotionCliReport {
    schema: String,
    mode: String,
    head_commit: String,
    source_manifest_sha256: String,
    old_preset_id: String,
    old_definition_identity: String,
    candidate_preset_id: String,
    candidate_definition_identity: String,
    targets: BTreeMap<String, PromotionTargetCliReport>,
    recovery_stage: Option<String>,
}

#[derive(Debug, Facet)]
struct PromotionTargetCliReport {
    counts: PromotionTargetCounts,
    changes: PromotionTargetChanges,
    old_manifest_sha256: String,
    candidate_manifest_sha256: String,
}

#[derive(Debug, Facet)]
struct PromotionTargetCounts {
    created: usize,
    updated: usize,
    removed: usize,
    unchanged: usize,
    accepted_identical_edits: usize,
}

#[derive(Debug, Facet)]
struct PromotionTargetChanges {
    created: Vec<String>,
    updated: Vec<String>,
    removed: Vec<String>,
    accepted_identical_edits: Vec<String>,
}

impl PromotionArgs {
    pub(super) fn invoke_in(
        self,
        cancellation: &CancellationToken,
        invocation_dir: &Path,
    ) -> Result<CliOutput> {
        cancellation.bail_if_cancelled()?;
        let request_path = if self.request.is_absolute() {
            self.request.clone()
        } else {
            invocation_dir.join(&self.request)
        };
        let file = fs::File::open(&request_path).wrap_err_with(|| {
            format!("cannot open promotion request '{}'", request_path.display())
        })?;
        let metadata = file.metadata().wrap_err_with(|| {
            format!(
                "cannot inspect promotion request '{}'",
                request_path.display()
            )
        })?;
        ensure!(
            metadata.is_file(),
            "promotion request must be a regular file"
        );
        ensure!(
            metadata.len() <= MAX_REQUEST_BYTES,
            "promotion request exceeds the {MAX_REQUEST_BYTES}-byte limit"
        );
        let mut bytes = Vec::new();
        file.take(MAX_REQUEST_BYTES + 1)
            .read_to_end(&mut bytes)
            .wrap_err_with(|| {
                format!("cannot read promotion request '{}'", request_path.display())
            })?;
        ensure!(
            bytes.len() as u64 <= MAX_REQUEST_BYTES,
            "promotion request exceeds the {MAX_REQUEST_BYTES}-byte limit"
        );
        let text = String::from_utf8(bytes).wrap_err("promotion request is not UTF-8")?;
        let request = parse_request(&text)?;
        let mode = self.validate_mode(&request.transition)?;
        cancellation.bail_if_cancelled()?;
        let report = promote(&request, mode)?;
        Ok(CliOutput::facet(PromotionCliReport::from_core(
            report, mode,
        )))
    }

    fn validate_mode(&self, transition: &PromotionTransition) -> Result<PromotionMode> {
        match (
            self.apply,
            self.ack_pre_acceptance_baseline_repair,
            transition,
        ) {
            (false, false, _) => Ok(PromotionMode::DryRun),
            (true, false, PromotionTransition::NewImmutablePreset) => bail!(
                "source-promotion Apply remains disabled pending joint review of the artifact-bound candidate request, toolchain evidence, compatibility acceptance, and tests"
            ),
            (true, false, PromotionTransition::PreAcceptanceBaselineRepair { .. }) => bail!(
                "pre-acceptance baseline repair requires --ack-pre-acceptance-baseline-repair; repair Apply remains disabled pending joint evidence and test review"
            ),
            (true, true, PromotionTransition::PreAcceptanceBaselineRepair { .. }) => bail!(
                "pre-acceptance baseline repair Apply remains disabled pending joint review of the artifact-bound candidate request, toolchain evidence, compatibility acceptance, and tests"
            ),
            _ => bail!(
                "--ack-pre-acceptance-baseline-repair is valid only with --apply for a pre-acceptance repair"
            ),
        }
    }
}

fn parse_request(text: &str) -> Result<PromotionRequest> {
    let input: PromotionRequestFile =
        facet_json::from_str(text).wrap_err("cannot parse strict source-promotion request JSON")?;
    ensure!(
        input.schema == REQUEST_SCHEMA,
        "unsupported source-promotion request schema '{}' (expected '{REQUEST_SCHEMA}')",
        input.schema
    );
    let repository_root = PathBuf::from(&input.repository_root);
    ensure!(
        repository_root.is_absolute(),
        "promotion repository_root must be absolute"
    );
    validate_relative_path(&input.compatibility_evidence_relative_path)
        .wrap_err("unsafe compatibility evidence path")?;
    ensure!(
        input.candidates.len() == 10,
        "source promotion requires exactly ten candidate targets"
    );
    let mut candidates = BTreeMap::new();
    for candidate in input.candidates {
        let target = candidate.target_id;
        let project_root = PathBuf::from(&candidate.project_root);
        ensure!(
            project_root.is_absolute(),
            "candidate project_root for '{target}' must be absolute"
        );
        validate_relative_path(&candidate.production_jar_relative_path)
            .wrap_err_with(|| format!("unsafe production JAR path for '{target}'"))?;
        ensure!(
            candidates
                .insert(
                    target.clone(),
                    PromotionCandidate {
                        project_root,
                        reviewed_manifest_sha256: candidate.reviewed_manifest_sha256,
                        production_jar_relative_path: candidate.production_jar_relative_path,
                        production_jar_sha256: candidate.production_jar_sha256,
                        production_task: candidate.production_task,
                        jdk_major: candidate.jdk_major,
                        jdk_build_id: candidate.jdk_build_id,
                    },
                )
                .is_none(),
            "duplicate source-promotion candidate target '{target}'"
        );
    }
    let transition = match (
        input.transition.kind.as_str(),
        input.transition.expected_old_preset_id,
        input.transition.expected_old_definition_identity,
        input.transition.reviewed_operations,
    ) {
        ("new_immutable_preset", None, None, None) => PromotionTransition::NewImmutablePreset,
        (
            "pre_acceptance_baseline_repair",
            Some(expected_old_preset_id),
            Some(expected_old_definition_identity),
            Some(reviewed_operations),
        ) => PromotionTransition::PreAcceptanceBaselineRepair {
            expected_old_preset_id,
            expected_old_definition_identity,
            reviewed_operations: reviewed_operations
                .into_iter()
                .map(ReviewedPromotionOperationFile::into_core)
                .collect::<Result<Vec<_>>>()?,
        },
        ("new_immutable_preset", _, _, _) => {
            bail!("immutable-preset transition cannot contain repair-only fields")
        }
        ("pre_acceptance_baseline_repair", _, _, _) => {
            bail!(
                "pre-acceptance repair requires both expected-old identity fields and reviewed operations"
            )
        }
        (kind, _, _, _) => bail!("unknown source-promotion transition kind '{kind}'"),
    };
    Ok(PromotionRequest {
        repository_root,
        reviewed_head_commit: input.reviewed_head_commit,
        reviewed_source_manifest_sha256: input.reviewed_source_manifest_sha256,
        compatibility_evidence_relative_path: input.compatibility_evidence_relative_path,
        reviewed_compatibility_evidence_sha256: input.reviewed_compatibility_evidence_sha256,
        candidates,
        candidate_preset_id: input.candidate_preset_id,
        candidate_definition_identity: input.candidate_definition_identity,
        transition,
        accept_identical_edits: input.accept_identical_edits,
    })
}

impl ReviewedPromotionOperationFile {
    fn into_core(self) -> Result<ReviewedPromotionOperation> {
        let kind = match self.kind.as_str() {
            "create" => ReviewedOperationKind::Create,
            "manifest" => ReviewedOperationKind::Manifest,
            other => bail!("unknown reviewed promotion operation kind '{other}'"),
        };
        Ok(ReviewedPromotionOperation {
            target_id: self.target_id,
            relative_path: self.relative_path,
            kind,
            old_sha256: self.old_sha256,
            new_sha256: self.new_sha256,
        })
    }
}

impl PromotionCliReport {
    fn from_core(report: PromotionReport, mode: PromotionMode) -> Self {
        Self {
            schema: REPORT_SCHEMA.to_owned(),
            mode: match mode {
                PromotionMode::DryRun => "dry_run",
                PromotionMode::Apply => "apply",
            }
            .to_owned(),
            head_commit: report.head_commit,
            source_manifest_sha256: report.source_manifest_sha256,
            old_preset_id: report.old_preset_id,
            old_definition_identity: report.old_definition_identity,
            candidate_preset_id: report.candidate_preset_id,
            candidate_definition_identity: report.candidate_definition_identity,
            targets: report
                .targets
                .into_iter()
                .map(|(target, report)| (target, PromotionTargetCliReport::from_core(report)))
                .collect(),
            recovery_stage: report.recovery_stage.map(|path| path.display().to_string()),
        }
    }
}

impl PromotionTargetCliReport {
    fn from_core(report: TargetPromotionReport) -> Self {
        Self {
            counts: PromotionTargetCounts {
                created: report.created.len(),
                updated: report.updated.len(),
                removed: report.removed.len(),
                unchanged: report.unchanged.len(),
                accepted_identical_edits: report.accepted_identical_edits.len(),
            },
            changes: PromotionTargetChanges {
                created: report.created,
                updated: report.updated,
                removed: report.removed,
                accepted_identical_edits: report.accepted_identical_edits,
            },
            old_manifest_sha256: report.old_manifest_sha256,
            candidate_manifest_sha256: report.candidate_manifest_sha256,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::cli::Cli;
    use crate::cli::Command;
    use crate::cli::output::OutputFormat;
    use crate::cli::source::SourceArgs;
    use crate::cli::source::SourceCommand;

    fn request_json(transition: &str) -> String {
        let candidates = (0..10)
            .map(|index| {
                let root = std::env::temp_dir().join(format!("candidate-v{index}"));
                format!(
                    "{{\"target_id\":\"v{index}\",\"project_root\":{},\"reviewed_manifest_sha256\":\"{}\",\"production_jar_relative_path\":\"build/libs/sfm-v{index}.jar\",\"production_jar_sha256\":\"{}\",\"production_task\":\"jar\",\"jdk_major\":17,\"jdk_build_id\":\"JBRSDK-17.0.14\"}}",
                    facet_json::to_string(&root.display().to_string()).unwrap(),
                    format!("sha256:{}", "a".repeat(64)),
                    format!("sha256:{}", "d".repeat(64))
                )
            })
            .collect::<Vec<_>>()
            .join(",");
        format!(
            "{{\"schema\":\"{REQUEST_SCHEMA}\",\"repository_root\":{},\"reviewed_head_commit\":\"{}\",\"reviewed_source_manifest_sha256\":\"{}\",\"compatibility_evidence_relative_path\":\"docs/acceptance.md\",\"reviewed_compatibility_evidence_sha256\":\"{}\",\"candidate_preset_id\":\"released-new\",\"candidate_definition_identity\":\"blake3:new\",\"transition\":{transition},\"accept_identical_edits\":false,\"candidates\":[{candidates}]}}",
            facet_json::to_string(&std::env::temp_dir().display().to_string()).unwrap(),
            "b".repeat(40),
            format!("sha256:{}", "c".repeat(64)),
            format!("sha256:{}", "e".repeat(64)),
        )
    }

    #[test]
    fn source_promote_parses_as_dry_run_by_default() {
        let parsed = figue::from_slice::<Cli>(&[
            "--output-format",
            "json",
            "source",
            "promote",
            "--request",
            "reviewed.json",
        ])
        .into_result()
        .unwrap()
        .get_silent();
        assert_eq!(parsed.global_args.output_format, Some(OutputFormat::Json));
        let Command::Source(SourceArgs {
            command: SourceCommand::Promote(args),
        }) = parsed.command
        else {
            panic!("expected source promote command");
        };
        assert_eq!(args.request, PathBuf::from("reviewed.json"));
        assert_eq!(
            args.validate_mode(&PromotionTransition::NewImmutablePreset)
                .unwrap(),
            PromotionMode::DryRun
        );
        assert!(!args.apply);
    }

    #[test]
    fn apply_and_repair_acknowledgement_are_explicit_flags() {
        let parsed = figue::from_slice::<Cli>(&[
            "source",
            "promote",
            "--request",
            "reviewed.json",
            "--apply",
            "--ack-pre-acceptance-baseline-repair",
        ])
        .into_result()
        .unwrap()
        .get_silent();
        let Command::Source(SourceArgs {
            command: SourceCommand::Promote(args),
        }) = parsed.command
        else {
            panic!("expected source promote command");
        };
        assert!(args.apply);
        assert!(args.ack_pre_acceptance_baseline_repair);
    }

    #[test]
    fn request_rejects_unknown_schema_fields_and_incomplete_matrix() {
        let json = request_json("{\"kind\":\"new_immutable_preset\"}");
        let parsed = parse_request(&json).unwrap();
        assert!(
            parsed
                .reviewed_source_manifest_sha256
                .starts_with("sha256:")
        );
        assert!(
            parsed.candidates["v0"]
                .reviewed_manifest_sha256
                .starts_with("sha256:")
        );
        let unknown = json.replacen(
            "\"accept_identical_edits\":false",
            "\"surprise\":true,\"accept_identical_edits\":false",
            1,
        );
        assert!(parse_request(&unknown).is_err());
        let unknown_candidate = json.replacen(
            "\"reviewed_manifest_sha256\"",
            "\"surprise\":true,\"reviewed_manifest_sha256\"",
            1,
        );
        assert!(parse_request(&unknown_candidate).is_err());
        let missing_jdk_build_id = json.replacen(",\"jdk_build_id\":\"JBRSDK-17.0.14\"", "", 1);
        assert!(parse_request(&missing_jdk_build_id).is_err());
        let wrong_schema = json.replacen(REQUEST_SCHEMA, "sfm:source_promotion_request@1", 1);
        assert!(
            parse_request(&wrong_schema)
                .unwrap_err()
                .to_string()
                .contains("unsupported")
        );
        let last_entry = json.find(",{\"target_id\":\"v9\"").unwrap();
        let incomplete = format!("{}]}}", &json[..last_entry]);
        assert!(
            parse_request(&incomplete)
                .unwrap_err()
                .to_string()
                .contains("exactly ten")
        );
        let duplicate = json.replacen("\"target_id\":\"v9\"", "\"target_id\":\"v8\"", 1);
        assert!(
            parse_request(&duplicate)
                .unwrap_err()
                .to_string()
                .contains("duplicate")
        );
    }

    #[test]
    fn request_rejects_unknown_transition_fields_and_relative_roots() {
        let repair = request_json(
            "{\"kind\":\"pre_acceptance_baseline_repair\",\"expected_old_preset_id\":\"released-old\",\"expected_old_definition_identity\":\"blake3:old\",\"reviewed_operations\":[]}",
        );
        assert!(matches!(
            parse_request(&repair).unwrap().transition,
            PromotionTransition::PreAcceptanceBaselineRepair { .. }
        ));
        let extra = repair.replacen(
            "\"expected_old_preset_id\"",
            "\"surprise\":true,\"expected_old_preset_id\"",
            1,
        );
        assert!(parse_request(&extra).is_err());
        let relative = repair.replacen(
            &facet_json::to_string(&std::env::temp_dir().display().to_string()).unwrap(),
            "\"relative/repo\"",
            1,
        );
        assert!(
            parse_request(&relative)
                .unwrap_err()
                .to_string()
                .contains("absolute")
        );
        let relative_candidate = repair.replacen(
            &facet_json::to_string(
                &std::env::temp_dir()
                    .join("candidate-v0")
                    .display()
                    .to_string(),
            )
            .unwrap(),
            "\"relative/candidate\"",
            1,
        );
        assert!(
            parse_request(&relative_candidate)
                .unwrap_err()
                .to_string()
                .contains("candidate project_root")
        );
        let escaping_evidence = repair.replacen(
            "\"compatibility_evidence_relative_path\":\"docs/acceptance.md\"",
            "\"compatibility_evidence_relative_path\":\"../acceptance.md\"",
            1,
        );
        assert!(
            parse_request(&escaping_evidence)
                .unwrap_err()
                .to_string()
                .contains("unsafe compatibility evidence path")
        );
        let escaping_jar = repair.replacen(
            "\"production_jar_relative_path\":\"build/libs/sfm-v0.jar\"",
            "\"production_jar_relative_path\":\"build/libs/../../outside.jar\"",
            1,
        );
        assert!(
            parse_request(&escaping_jar)
                .unwrap_err()
                .to_string()
                .contains("unsafe production JAR path")
        );
        let immutable_with_repair_field = request_json(
            "{\"kind\":\"new_immutable_preset\",\"expected_old_preset_id\":\"released-old\"}",
        );
        assert!(parse_request(&immutable_with_repair_field).is_err());
    }

    #[test]
    fn repair_operation_lock_has_strict_typed_fields() {
        let transition = format!(
            "{{\"kind\":\"pre_acceptance_baseline_repair\",\"expected_old_preset_id\":\"released-old\",\"expected_old_definition_identity\":\"blake3:old\",\"reviewed_operations\":[{{\"target_id\":\"1.20.2\",\"relative_path\":\"src/main/resources/sfm.refmap.json\",\"kind\":\"create\",\"old_sha256\":null,\"new_sha256\":\"sha256:{}\"}}]}}",
            "d".repeat(64)
        );
        let json = request_json(&transition);
        let parsed = parse_request(&json).unwrap();
        let PromotionTransition::PreAcceptanceBaselineRepair {
            reviewed_operations,
            ..
        } = parsed.transition
        else {
            panic!("expected repair transition");
        };
        assert_eq!(reviewed_operations.len(), 1);
        assert_eq!(reviewed_operations[0].kind, ReviewedOperationKind::Create);
        assert!(reviewed_operations[0].old_sha256.is_none());
        let unknown = json.replacen("\"new_sha256\"", "\"surprise\":true,\"new_sha256\"", 1);
        assert!(parse_request(&unknown).is_err());
        let bad_kind = json.replacen("\"kind\":\"create\"", "\"kind\":\"update\"", 1);
        assert!(parse_request(&bad_kind).is_err());
    }

    #[test]
    fn repair_apply_fails_closed_even_with_acknowledgement() {
        let repair = PromotionTransition::PreAcceptanceBaselineRepair {
            expected_old_preset_id: "released-old".to_owned(),
            expected_old_definition_identity: "blake3:old".to_owned(),
            reviewed_operations: vec![],
        };
        for acknowledged in [false, true] {
            let args = PromotionArgs {
                request: PathBuf::from("reviewed.json"),
                apply: true,
                ack_pre_acceptance_baseline_repair: acknowledged,
            };
            assert!(args.validate_mode(&repair).is_err());
        }
        let dry_run = PromotionArgs {
            request: PathBuf::from("reviewed.json"),
            apply: false,
            ack_pre_acceptance_baseline_repair: false,
        };
        assert_eq!(
            dry_run.validate_mode(&repair).unwrap(),
            PromotionMode::DryRun
        );
        let invalid_ack = PromotionArgs {
            ack_pre_acceptance_baseline_repair: true,
            ..dry_run
        };
        assert!(invalid_ack.validate_mode(&repair).is_err());

        let normal_apply = PromotionArgs {
            request: PathBuf::from("reviewed.json"),
            apply: true,
            ack_pre_acceptance_baseline_repair: false,
        };
        assert!(
            normal_apply
                .validate_mode(&PromotionTransition::NewImmutablePreset)
                .is_err()
        );
        let wrongly_acknowledged = PromotionArgs {
            ack_pre_acceptance_baseline_repair: true,
            ..normal_apply
        };
        assert!(
            wrongly_acknowledged
                .validate_mode(&PromotionTransition::NewImmutablePreset)
                .is_err()
        );
    }

    #[test]
    fn review_report_is_machine_readable_without_running_apply() {
        let output = CliOutput::facet(PromotionCliReport::from_core(
            PromotionReport {
                head_commit: "a".repeat(40),
                candidate_preset_id: "released-next".to_owned(),
                ..PromotionReport::default()
            },
            PromotionMode::DryRun,
        ));
        let json = output
            .render(Some(OutputFormat::Json), false)
            .unwrap()
            .unwrap();
        assert!(json.contains(REPORT_SCHEMA));
        assert!(json.contains("\"mode\": \"dry_run\""));
        assert!(json.contains("\"candidate_preset_id\": \"released-next\""));
    }

    #[test]
    fn report_counts_unchanged_files_without_listing_their_paths() {
        let report = PromotionReport {
            targets: BTreeMap::from([(
                "1.19.2".to_owned(),
                TargetPromotionReport {
                    created: vec!["src/main/resources/new.json".to_owned()],
                    unchanged: (0..1_000)
                        .map(|index| format!("unchanged_path_that_must_not_render_{index}.java"))
                        .collect(),
                    ..TargetPromotionReport::default()
                },
            )]),
            ..PromotionReport::default()
        };
        let output = CliOutput::facet(PromotionCliReport::from_core(report, PromotionMode::DryRun));
        let json = output
            .render(Some(OutputFormat::Json), false)
            .unwrap()
            .unwrap();
        assert!(json.contains("\"unchanged\": 1000"));
        assert!(json.contains("src/main/resources/new.json"));
        assert!(!json.contains("unchanged_path_that_must_not_render"));
        assert!(
            json.len() < 2_000,
            "compact report grew to {} bytes",
            json.len()
        );
    }
}
