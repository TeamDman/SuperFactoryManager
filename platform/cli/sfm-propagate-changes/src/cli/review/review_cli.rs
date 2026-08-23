use crate::cli::output::CliOutput;
use crate::release_review_git;
use crate::release_review_materialize;
use crate::release_review_v1;
use crate::release_review_v1::CompletionReportV1;
use crate::release_review_v1::CompletionStatusV1;
use eyre::Context as _;
use facet::Facet;
use figue::{self as args};
use std::io::Write as _;
use std::path::Path;
use std::path::PathBuf;

const REVIEW_NOT_COMPLETE_EXIT_CODE: u8 = 5;

#[derive(Facet, Debug)]
pub struct ReviewArgs {
    #[facet(args::subcommand)]
    pub command: ReviewCommand,
}

impl ReviewArgs {
    /// # Errors
    ///
    /// Returns an error when the selected review command cannot read, parse,
    /// validate, query, or summarize its review document.
    pub fn invoke_in(self, invocation_dir: &Path) -> eyre::Result<CliOutput> {
        self.command.invoke_in(invocation_dir)
    }
}

#[derive(Facet, Debug)]
#[repr(u8)]
pub enum ReviewCommand {
    /// Query or summarize a portable review-session document.
    Session(ReviewSessionArgs),
}

impl ReviewCommand {
    fn invoke_in(self, invocation_dir: &Path) -> eyre::Result<CliOutput> {
        match self {
            Self::Session(args) => args.invoke_in(invocation_dir),
        }
    }
}

#[derive(Facet, Debug)]
pub struct ReviewSessionArgs {
    #[facet(args::subcommand)]
    pub command: ReviewSessionCommand,
}

impl ReviewSessionArgs {
    fn invoke_in(self, invocation_dir: &Path) -> eyre::Result<CliOutput> {
        match self.command {
            ReviewSessionCommand::Create(args) => args.invoke_in(invocation_dir),
            ReviewSessionCommand::Refresh(args) => args.invoke_in(invocation_dir),
            ReviewSessionCommand::Query(args) => args.invoke_in(invocation_dir),
            ReviewSessionCommand::Status(args) => args.invoke_in(invocation_dir),
        }
    }
}

#[derive(Facet, Debug)]
#[repr(u8)]
pub enum ReviewSessionCommand {
    /// Create one canonical, commit-friendly review document from a pinned Git range.
    Create(ReviewSessionCreateArgs),
    /// Refresh producer-owned state while preserving human review progress.
    Refresh(ReviewSessionRefreshArgs),
    /// Evaluate a set expression against the pinned review domain.
    Query(ReviewSessionQueryArgs),
    /// Report fail-closed completion and semantic-state identity.
    Status(ReviewSessionStatusArgs),
}

#[derive(Facet, Debug)]
pub struct ReviewSessionRefreshArgs {
    /// Existing portable `.sfm-review.json` document to refresh in place.
    #[facet(args::named)]
    pub file: PathBuf,
}

#[derive(Clone, Copy, Debug, Facet)]
#[facet(rename_all = "snake_case")]
#[repr(u8)]
pub enum ReviewSessionRefreshOutcomeOutput {
    Refreshed,
    Refused,
}

#[derive(Debug, Facet)]
pub struct ReviewSessionRefreshDiagnosticOutput {
    pub code: String,
    pub message: String,
}

#[derive(Debug, Facet)]
pub struct ReviewSessionRefreshOutput {
    pub schema: String,
    pub outcome: ReviewSessionRefreshOutcomeOutput,
    pub file: String,
    #[facet(skip_serializing_if = Option::is_none)]
    pub repository_relationship: Option<ReviewSessionRepositoryRelationshipOutput>,
    #[facet(skip_serializing_if = Option::is_none)]
    pub previous_generation: Option<String>,
    #[facet(skip_serializing_if = Option::is_none)]
    pub refreshed_generation: Option<String>,
    pub active_units_added: usize,
    pub active_units_updated: usize,
    pub active_units_unchanged: usize,
    pub retired_units_added: usize,
    pub retired_units_carried: usize,
    pub generated_comments: usize,
    pub human_comments_preserved: usize,
    pub selector_bindings_preserved: usize,
    pub migration_reports_preserved: usize,
    pub named_queries_preserved: usize,
    pub deferred_units_preserved: usize,
    pub completion_attestations_preserved: usize,
    #[facet(skip_serializing_if = Option::is_none)]
    pub completion_status: Option<CompletionStatusV1>,
    pub diagnostics: Vec<ReviewSessionRefreshDiagnosticOutput>,
}

#[derive(Facet, Debug)]
pub struct ReviewSessionCreateArgs {
    /// Canonical `.sfm-review.json` output path. The command refuses to replace an existing file.
    #[facet(args::named)]
    pub file: PathBuf,
    /// Minecraft-version lane identifier used by review queries, for example `1.19.2`.
    #[facet(args::named)]
    pub branch: String,
    /// Prior release tag or commit-ish to resolve and pin.
    #[facet(args::named)]
    pub before: String,
    /// Candidate commit-ish to resolve and pin. Use `HEAD` to pin the current commit.
    #[facet(args::named)]
    pub candidate: String,
    /// Git repository root; defaults to the command invocation directory.
    #[facet(default, args::named)]
    pub repository_root: Option<PathBuf>,
}

#[derive(Facet, Debug)]
pub struct ReviewSessionCreateOutput {
    pub schema: String,
    pub file: String,
    pub lane_id: String,
    pub before_label: String,
    pub before_commit: String,
    pub before_tree: String,
    pub candidate_label: String,
    pub candidate_commit: String,
    pub candidate_tree: String,
    pub raw_change_count: usize,
    pub raw_side_path_count: usize,
    pub review_unit_count: usize,
    pub represented_corpus_side_path_count: usize,
    pub explicitly_unsupported_corpus_side_path_count: usize,
    pub reconciliation_complete: bool,
    pub completion_status: CompletionStatusV1,
}

impl ReviewSessionCreateArgs {
    fn invoke_in(self, invocation_dir: &Path) -> eyre::Result<CliOutput> {
        if self.branch.trim().is_empty() {
            return Err(eyre::eyre!("release-review branch/lane must not be blank"));
        }
        let requested_root = self.repository_root.as_ref().map_or_else(
            || invocation_dir.to_owned(),
            |path| resolve_path(invocation_dir, path),
        );
        let repository_root = std::fs::canonicalize(&requested_root).wrap_err_with(|| {
            format!(
                "could not resolve Git repository root {}",
                requested_root.display()
            )
        })?;
        let output = resolve_output_path(invocation_dir, &repository_root, &self.file)?;
        let review_evidence_path = output
            .strip_prefix(&repository_root)
            .expect("output authorization checked by resolve_output_path")
            .to_string_lossy()
            .replace('\\', "/");

        let domain = release_review_git::produce_git_review_domain(
            &repository_root,
            &self.before,
            &self.candidate,
        )
        .map_err(|error| eyre::eyre!(error))?;
        let config = release_review_materialize::ReleaseReviewMaterializeConfig {
            repository_root: repository_root.clone(),
            lane_id: self.branch.clone(),
            repository_id: "sfm".to_owned(),
            root_hint: ".".to_owned(),
            before_label: self.before.clone(),
            before_revision: domain.before.commit_id.clone(),
            after_label: self.candidate.clone(),
            candidate_revision: domain.candidate.commit_id.clone(),
            review_evidence_path,
        };
        let document = release_review_materialize::materialize_release_review(&domain, &config)?;
        let reconciliation =
            release_review_materialize::reconcile_materialization(&domain, &document)?;
        if !reconciliation.complete {
            return Err(eyre::eyre!(
                "release-review materialization did not reconcile completely"
            ));
        }
        let canonical = release_review_v1::to_canonical_json(&document)?;
        write_new_atomically(&output, canonical.as_bytes())?;
        let completion = release_review_v1::completion(&document)?;

        Ok(CliOutput::facet(ReviewSessionCreateOutput {
            schema: "sfm.release-review-create/1".to_owned(),
            file: output.to_string_lossy().into_owned(),
            lane_id: self.branch,
            before_label: self.before,
            before_commit: domain.before.commit_id,
            before_tree: domain.before.tree_id,
            candidate_label: self.candidate,
            candidate_commit: domain.candidate.commit_id,
            candidate_tree: domain.candidate.tree_id,
            raw_change_count: reconciliation.raw_change_count,
            raw_side_path_count: reconciliation.raw_side_path_count,
            review_unit_count: reconciliation.review_unit_count,
            represented_corpus_side_path_count: reconciliation.represented_corpus_side_path_count,
            explicitly_unsupported_corpus_side_path_count: reconciliation
                .explicitly_unsupported_corpus_side_path_count,
            reconciliation_complete: reconciliation.complete,
            completion_status: completion.status,
        }))
    }
}

impl ReviewSessionRefreshArgs {
    #[expect(
        clippy::too_many_lines,
        reason = "the guarded read-classify-produce-merge-replace transaction remains visibly auditable"
    )]
    fn invoke_in(self, invocation_dir: &Path) -> eyre::Result<CliOutput> {
        let review_path = resolve_review_document_path(invocation_dir, &self.file);
        let display_path = review_path.to_string_lossy().into_owned();
        let original = match std::fs::read_to_string(&review_path) {
            Ok(original) => original,
            Err(error) => {
                return Ok(refresh_refused(
                    display_path,
                    None,
                    "refresh.document-unavailable",
                    format!(
                        "could not read release-review document {}: {error}",
                        review_path.display()
                    ),
                ));
            }
        };
        let existing = match release_review_v1::parse(&original) {
            Ok(document) => document,
            Err(error) => {
                return Ok(refresh_refused(
                    display_path,
                    None,
                    "refresh.document-invalid",
                    format!(
                        "could not parse release-review document {}: {error:#}",
                        review_path.display()
                    ),
                ));
            }
        };
        if existing.repository_bindings.len() != 1 {
            return Ok(refresh_refused(
                display_path,
                None,
                "refresh.repository-binding-cardinality",
                format!(
                    "refresh currently requires exactly one pinned repository binding; found {}",
                    existing.repository_bindings.len()
                ),
            ));
        }
        let binding = &existing.repository_bindings[0];
        let repository_root = resolve_bound_repository_root(invocation_dir, &review_path, binding);
        let relationship = release_review_git::classify_ambient_repository_relationship(
            &repository_root,
            &binding.candidate_commit,
            &binding.review_evidence_paths,
        );
        let relationship_diagnostics = relationship
            .diagnostics
            .iter()
            .map(|diagnostic| ReviewSessionRefreshDiagnosticOutput {
                code: diagnostic.code.clone(),
                message: diagnostic.message.clone(),
            })
            .collect::<Vec<_>>();
        let relationship_is_safe = matches!(
            relationship.classification,
            release_review_git::AmbientRepositoryRelationshipKind::Exact
                | release_review_git::AmbientRepositoryRelationshipKind::ReviewEvidenceOnly
        );
        let relationship_output = repository_relationship_output(binding, relationship);
        if !relationship_is_safe {
            return Ok(refresh_refused(
                display_path,
                Some(relationship_output),
                "refresh.repository-diverged",
                format!(
                    "ambient repository does not safely reproduce pinned candidate {}; check out the candidate or retain only commits touching declared review-evidence paths",
                    binding.candidate_commit
                ),
            ));
        }

        let repository_root = match std::fs::canonicalize(&repository_root) {
            Ok(root) => root,
            Err(error) => {
                return Ok(refresh_refused(
                    display_path,
                    Some(relationship_output),
                    "refresh.repository-root-unavailable",
                    format!(
                        "could not canonicalize bound repository root {}: {error}",
                        repository_root.display()
                    ),
                ));
            }
        };
        let review_path_canonical = match std::fs::canonicalize(&review_path) {
            Ok(path) => path,
            Err(error) => {
                return Ok(refresh_refused(
                    display_path,
                    Some(relationship_output),
                    "refresh.review-path-unavailable",
                    format!(
                        "could not canonicalize release-review path {}: {error}",
                        review_path.display()
                    ),
                ));
            }
        };
        let Ok(relative_review_path) = review_path_canonical.strip_prefix(&repository_root) else {
            return Ok(refresh_refused(
                display_path,
                Some(relationship_output),
                "refresh.review-path-outside-repository",
                format!(
                    "release-review path {} is outside bound repository {}",
                    review_path_canonical.display(),
                    repository_root.display()
                ),
            ));
        };
        let relative_review_path = relative_review_path.to_string_lossy().replace('\\', "/");
        if !binding
            .review_evidence_paths
            .iter()
            .any(|path| path == &relative_review_path)
        {
            return Ok(refresh_refused(
                display_path,
                Some(relationship_output),
                "refresh.review-path-not-authorized",
                format!(
                    "release-review path '{relative_review_path}' is not listed in review_evidence_paths"
                ),
            ));
        }

        let domain = match release_review_git::produce_git_review_domain(
            &repository_root,
            &binding.before_commit,
            &binding.candidate_commit,
        ) {
            Ok(domain) => domain,
            Err(error) => {
                return Ok(refresh_refused(
                    display_path,
                    Some(relationship_output),
                    "refresh.git-producer-failed",
                    format!("could not reproduce pinned Git review domain: {error}"),
                ));
            }
        };
        let config = release_review_materialize::ReleaseReviewMaterializeConfig {
            repository_root,
            lane_id: binding.lane_id.clone(),
            repository_id: binding.repository_id.clone(),
            root_hint: binding.root_hint.clone(),
            before_label: binding.before_label.clone(),
            before_revision: binding.before_commit.clone(),
            after_label: binding.after_label.clone(),
            candidate_revision: binding.candidate_commit.clone(),
            review_evidence_path: relative_review_path,
        };
        let refreshed =
            match release_review_materialize::refresh_release_review(&existing, &domain, &config) {
                Ok(refreshed) => refreshed,
                Err(error) => {
                    return Ok(refresh_refused(
                        display_path,
                        Some(relationship_output),
                        error.code,
                        error.message,
                    ));
                }
            };
        let canonical = release_review_v1::to_canonical_json(&refreshed.document)?;
        if let Err(error) = write_existing_atomically_if_unchanged(
            &review_path,
            original.as_bytes(),
            canonical.as_bytes(),
        ) {
            return Ok(refresh_refused(
                display_path,
                Some(relationship_output),
                error.code(),
                error.to_string(),
            ));
        }
        let completion = release_review_v1::completion(&refreshed.document)?;
        let reconciliation = refreshed.reconciliation;
        Ok(CliOutput::facet(ReviewSessionRefreshOutput {
            schema: "sfm.release-review-refresh/1".to_owned(),
            outcome: ReviewSessionRefreshOutcomeOutput::Refreshed,
            file: display_path,
            repository_relationship: Some(relationship_output),
            previous_generation: Some(reconciliation.previous_generation),
            refreshed_generation: Some(reconciliation.refreshed_generation),
            active_units_added: reconciliation.active_units_added,
            active_units_updated: reconciliation.active_units_updated,
            active_units_unchanged: reconciliation.active_units_unchanged,
            retired_units_added: reconciliation.retired_units_added,
            retired_units_carried: reconciliation.retired_units_carried,
            generated_comments: reconciliation.generated_comments,
            human_comments_preserved: reconciliation.human_comments_preserved,
            selector_bindings_preserved: reconciliation.selector_bindings_preserved,
            migration_reports_preserved: reconciliation.migration_reports_preserved,
            named_queries_preserved: reconciliation.named_queries_preserved,
            deferred_units_preserved: reconciliation.deferred_units_preserved,
            completion_attestations_preserved: reconciliation.completion_attestations_preserved,
            completion_status: Some(completion.status),
            diagnostics: relationship_diagnostics,
        }))
    }
}

#[derive(Facet, Debug)]
pub struct ReviewSessionQueryArgs {
    /// Portable `sfm.release-review/1` JSON document.
    #[facet(args::named)]
    pub file: PathBuf,
    /// Query expression; unquoted words are joined with one space.
    #[facet(args::positional)]
    pub expression: Vec<String>,
}

impl ReviewSessionQueryArgs {
    fn invoke_in(self, invocation_dir: &Path) -> eyre::Result<CliOutput> {
        let expression = self.expression.join(" ");
        if expression.trim().is_empty() {
            return Err(eyre::eyre!(
                "release-review query expression must not be blank"
            ));
        }
        let document = read_review_document(invocation_dir, &self.file)?;
        Ok(CliOutput::facet(release_review_v1::query(
            &document,
            &expression,
        )?))
    }
}

#[derive(Facet, Debug)]
pub struct ReviewSessionStatusArgs {
    /// Portable `sfm.release-review/1` JSON document.
    #[facet(args::named)]
    pub file: PathBuf,
}

#[derive(Clone, Copy, Debug, Facet)]
#[facet(rename_all = "snake_case")]
#[repr(u8)]
pub enum ReviewSessionRepositoryRelationshipKindOutput {
    Exact,
    ReviewEvidenceOnly,
    SourceAffectingDivergence,
}

#[derive(Debug, Facet)]
pub struct ReviewSessionGitPathWitnessOutput {
    #[facet(skip_serializing_if = Option::is_none)]
    pub utf8: Option<String>,
    pub display: String,
    pub bytes_hex: String,
}

#[derive(Debug, Facet)]
pub struct ReviewSessionRepositoryDiagnosticOutput {
    pub code: String,
    pub message: String,
}

#[derive(Debug, Facet)]
pub struct ReviewSessionRepositoryRelationshipOutput {
    pub schema: String,
    pub lane_id: String,
    pub repository_id: String,
    pub root_hint: String,
    pub repository_root: String,
    pub candidate_commit: String,
    #[facet(skip_serializing_if = Option::is_none)]
    pub ambient_head: Option<String>,
    pub classification: ReviewSessionRepositoryRelationshipKindOutput,
    #[facet(skip_serializing_if = Option::is_none)]
    pub candidate_is_ancestor: Option<bool>,
    pub intervening_commits: Vec<String>,
    pub permitted_review_evidence_paths: Vec<String>,
    pub changed_paths: Vec<ReviewSessionGitPathWitnessOutput>,
    pub source_affecting_paths: Vec<ReviewSessionGitPathWitnessOutput>,
    pub diagnostics: Vec<ReviewSessionRepositoryDiagnosticOutput>,
}

#[derive(Debug, Facet)]
pub struct ReviewSessionStatusOutput {
    pub schema: String,
    /// Effective status after fail-closed ambient-repository classification.
    pub status: CompletionStatusV1,
    /// Completion derived solely from the immutable review document.
    pub document_completion_status: CompletionStatusV1,
    pub completion: CompletionReportV1,
    pub repository_relationships: Vec<ReviewSessionRepositoryRelationshipOutput>,
    pub diagnostics: Vec<String>,
}

impl ReviewSessionStatusArgs {
    fn invoke_in(self, invocation_dir: &Path) -> eyre::Result<CliOutput> {
        let review_path = resolve_review_document_path(invocation_dir, &self.file);
        let document = read_review_document_at(&review_path)?;
        let report = release_review_v1::completion(&document)?;
        let mut repository_relationships = document
            .repository_bindings
            .iter()
            .map(|binding| {
                let repository_root =
                    resolve_bound_repository_root(invocation_dir, &review_path, binding);
                let relationship = release_review_git::classify_ambient_repository_relationship(
                    &repository_root,
                    &binding.candidate_commit,
                    &binding.review_evidence_paths,
                );
                repository_relationship_output(binding, relationship)
            })
            .collect::<Vec<_>>();
        repository_relationships.sort_by(|left, right| {
            left.lane_id
                .cmp(&right.lane_id)
                .then_with(|| left.repository_id.cmp(&right.repository_id))
                .then_with(|| left.repository_root.cmp(&right.repository_root))
        });
        let repository_diverged = repository_relationships.iter().any(|relationship| {
            matches!(
                relationship.classification,
                ReviewSessionRepositoryRelationshipKindOutput::SourceAffectingDivergence
            )
        });
        let status = if repository_diverged {
            CompletionStatusV1::Stale
        } else {
            report.status
        };
        let diagnostics = repository_relationships
            .iter()
            .filter(|relationship| {
                matches!(
                    relationship.classification,
                    ReviewSessionRepositoryRelationshipKindOutput::SourceAffectingDivergence
                )
            })
            .flat_map(|relationship| {
                relationship.diagnostics.iter().map(|diagnostic| {
                    format!(
                        "repository lane '{}' ({}) failed closed: {}: {}",
                        relationship.lane_id,
                        relationship.repository_id,
                        diagnostic.code,
                        diagnostic.message
                    )
                })
            })
            .collect::<Vec<_>>();
        let exit_code = if matches!(
            status,
            CompletionStatusV1::InProgress | CompletionStatusV1::Stale
        ) {
            REVIEW_NOT_COMPLETE_EXIT_CODE
        } else {
            0
        };
        Ok(CliOutput::facet_with_status(
            ReviewSessionStatusOutput {
                schema: "sfm.release-review-status/1".to_owned(),
                status,
                document_completion_status: report.status,
                completion: report,
                repository_relationships,
                diagnostics,
            },
            exit_code,
        ))
    }
}

impl From<release_review_git::AmbientRepositoryRelationshipKind>
    for ReviewSessionRepositoryRelationshipKindOutput
{
    fn from(value: release_review_git::AmbientRepositoryRelationshipKind) -> Self {
        match value {
            release_review_git::AmbientRepositoryRelationshipKind::Exact => Self::Exact,
            release_review_git::AmbientRepositoryRelationshipKind::ReviewEvidenceOnly => {
                Self::ReviewEvidenceOnly
            }
            release_review_git::AmbientRepositoryRelationshipKind::SourceAffectingDivergence => {
                Self::SourceAffectingDivergence
            }
        }
    }
}

impl From<release_review_git::AmbientGitPathWitness> for ReviewSessionGitPathWitnessOutput {
    fn from(value: release_review_git::AmbientGitPathWitness) -> Self {
        Self {
            utf8: value.utf8,
            display: value.display,
            bytes_hex: value.bytes_hex,
        }
    }
}

impl From<release_review_git::AmbientRepositoryDiagnostic>
    for ReviewSessionRepositoryDiagnosticOutput
{
    fn from(value: release_review_git::AmbientRepositoryDiagnostic) -> Self {
        Self {
            code: value.code,
            message: value.message,
        }
    }
}

fn repository_relationship_output(
    binding: &release_review_v1::RepositoryBindingV1,
    relationship: release_review_git::AmbientRepositoryRelationship,
) -> ReviewSessionRepositoryRelationshipOutput {
    ReviewSessionRepositoryRelationshipOutput {
        schema: relationship.schema.to_owned(),
        lane_id: binding.lane_id.clone(),
        repository_id: binding.repository_id.clone(),
        root_hint: binding.root_hint.clone(),
        repository_root: relationship.repository_root,
        candidate_commit: relationship.candidate_commit,
        ambient_head: relationship.ambient_head,
        classification: relationship.classification.into(),
        candidate_is_ancestor: relationship.candidate_is_ancestor,
        intervening_commits: relationship.intervening_commits,
        permitted_review_evidence_paths: relationship.permitted_review_evidence_paths,
        changed_paths: relationship
            .changed_paths
            .into_iter()
            .map(Into::into)
            .collect(),
        source_affecting_paths: relationship
            .source_affecting_paths
            .into_iter()
            .map(Into::into)
            .collect(),
        diagnostics: relationship
            .diagnostics
            .into_iter()
            .map(Into::into)
            .collect(),
    }
}

fn refresh_refused(
    file: String,
    repository_relationship: Option<ReviewSessionRepositoryRelationshipOutput>,
    code: &'static str,
    message: String,
) -> CliOutput {
    CliOutput::facet_with_status(
        ReviewSessionRefreshOutput {
            schema: "sfm.release-review-refresh/1".to_owned(),
            outcome: ReviewSessionRefreshOutcomeOutput::Refused,
            file,
            repository_relationship,
            previous_generation: None,
            refreshed_generation: None,
            active_units_added: 0,
            active_units_updated: 0,
            active_units_unchanged: 0,
            retired_units_added: 0,
            retired_units_carried: 0,
            generated_comments: 0,
            human_comments_preserved: 0,
            selector_bindings_preserved: 0,
            migration_reports_preserved: 0,
            named_queries_preserved: 0,
            deferred_units_preserved: 0,
            completion_attestations_preserved: 0,
            completion_status: None,
            diagnostics: vec![ReviewSessionRefreshDiagnosticOutput {
                code: code.to_owned(),
                message,
            }],
        },
        REVIEW_NOT_COMPLETE_EXIT_CODE,
    )
}

#[derive(Debug)]
enum ReplaceExistingError {
    ConcurrentModification,
    Io(String),
}

impl ReplaceExistingError {
    const fn code(&self) -> &'static str {
        match self {
            Self::ConcurrentModification => "refresh.concurrent-modification",
            Self::Io(_) => "refresh.atomic-replace-failed",
        }
    }
}

impl std::fmt::Display for ReplaceExistingError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::ConcurrentModification => f.write_str(
                "release-review document changed while refresh was running; no refreshed bytes were published",
            ),
            Self::Io(message) => f.write_str(message),
        }
    }
}

fn write_existing_atomically_if_unchanged(
    path: &Path,
    expected: &[u8],
    output: &[u8],
) -> Result<(), ReplaceExistingError> {
    let parent = path.parent().ok_or_else(|| {
        ReplaceExistingError::Io(format!(
            "release-review document has no parent: {}",
            path.display()
        ))
    })?;
    let mut temporary = tempfile::Builder::new()
        .prefix(".sfm-release-review-refresh-")
        .suffix(".tmp")
        .tempfile_in(parent)
        .map_err(|error| {
            ReplaceExistingError::Io(format!(
                "could not prepare atomic refresh beside {}: {error}",
                path.display()
            ))
        })?;
    temporary.write_all(output).map_err(|error| {
        ReplaceExistingError::Io(format!(
            "could not stage refreshed release-review document {}: {error}",
            path.display()
        ))
    })?;
    temporary.as_file_mut().sync_all().map_err(|error| {
        ReplaceExistingError::Io(format!(
            "could not flush refreshed release-review document {}: {error}",
            path.display()
        ))
    })?;
    let current = std::fs::read(path).map_err(|error| {
        ReplaceExistingError::Io(format!(
            "could not re-read release-review document {} before replacement: {error}",
            path.display()
        ))
    })?;
    if current != expected {
        return Err(ReplaceExistingError::ConcurrentModification);
    }
    temporary.persist(path).map_err(|error| {
        ReplaceExistingError::Io(format!(
            "could not atomically replace release-review document {}: {}",
            path.display(),
            error.error
        ))
    })?;
    Ok(())
}

fn read_review_document(
    invocation_dir: &Path,
    requested_path: &Path,
) -> eyre::Result<release_review_v1::ReleaseReviewDocumentV1> {
    let path = resolve_review_document_path(invocation_dir, requested_path);
    read_review_document_at(&path)
}

fn resolve_review_document_path(invocation_dir: &Path, requested_path: &Path) -> PathBuf {
    if requested_path.is_absolute() {
        requested_path.to_owned()
    } else {
        invocation_dir.join(requested_path)
    }
}

fn read_review_document_at(
    path: &Path,
) -> eyre::Result<release_review_v1::ReleaseReviewDocumentV1> {
    let input = std::fs::read_to_string(path)
        .wrap_err_with(|| format!("could not read release-review document {}", path.display()))?;
    release_review_v1::parse(&input)
        .wrap_err_with(|| format!("could not load release-review document {}", path.display()))
}

fn resolve_bound_repository_root(
    invocation_dir: &Path,
    review_path: &Path,
    binding: &release_review_v1::RepositoryBindingV1,
) -> PathBuf {
    for evidence_path in &binding.review_evidence_paths {
        let evidence_path = Path::new(evidence_path);
        if review_path.ends_with(evidence_path) {
            let mut root = review_path.to_path_buf();
            for _ in evidence_path.components() {
                let _ = root.pop();
            }
            return root;
        }
    }
    resolve_path(invocation_dir, Path::new(&binding.root_hint))
}

fn resolve_path(invocation_dir: &Path, requested: &Path) -> PathBuf {
    if requested.is_absolute() {
        requested.to_owned()
    } else {
        invocation_dir.join(requested)
    }
}

fn resolve_output_path(
    invocation_dir: &Path,
    repository_root: &Path,
    requested: &Path,
) -> eyre::Result<PathBuf> {
    let unresolved = resolve_path(invocation_dir, requested);
    let file_name = unresolved
        .file_name()
        .ok_or_else(|| eyre::eyre!("release-review output path must name a file"))?;
    let requested_parent = unresolved
        .parent()
        .ok_or_else(|| eyre::eyre!("release-review output path has no parent"))?;
    std::fs::create_dir_all(requested_parent).wrap_err_with(|| {
        format!(
            "could not create release-review directory {}",
            requested_parent.display()
        )
    })?;
    let parent = std::fs::canonicalize(requested_parent).wrap_err_with(|| {
        format!(
            "could not resolve release-review directory {}",
            requested_parent.display()
        )
    })?;
    if !parent.starts_with(repository_root) {
        return Err(eyre::eyre!(
            "release-review file must live inside repository root {}; requested {}",
            repository_root.display(),
            unresolved.display()
        ));
    }
    Ok(parent.join(file_name))
}

fn write_new_atomically(path: &Path, bytes: &[u8]) -> eyre::Result<()> {
    if path.exists() {
        return Err(eyre::eyre!(
            "refusing to replace existing release-review document {}",
            path.display()
        ));
    }
    let parent = path.parent().expect("validated output path has a parent");
    let mut temporary = tempfile::Builder::new()
        .prefix(".sfm-release-review-")
        .tempfile_in(parent)
        .wrap_err_with(|| format!("could not prepare atomic write beside {}", path.display()))?;
    temporary
        .write_all(bytes)
        .wrap_err_with(|| format!("could not write release-review document {}", path.display()))?;
    temporary
        .as_file()
        .sync_all()
        .wrap_err_with(|| format!("could not flush release-review document {}", path.display()))?;
    temporary.persist_noclobber(path).map_err(|error| {
        eyre::eyre!(
            "could not atomically create release-review document {}: {}",
            path.display(),
            error.error
        )
    })?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::cli::Cli;
    use crate::cli::Command;
    use crate::cli::output::OutputFormat;
    use crate::release_review_v1::test_support;
    use std::process::Command as ProcessCommand;

    fn parse(arguments: &[&str]) -> Cli {
        figue::from_slice::<Cli>(arguments)
            .into_result()
            .expect("review CLI should parse")
            .get_silent()
    }

    #[test]
    fn parses_exact_unquoted_release_review_query() {
        let cli = parse(&[
            "review",
            "session",
            "query",
            "--file",
            "review.sfm-review.json",
            "#approved",
            "intersect",
            "1.19.2",
            "HEAD",
        ]);
        let Command::Review(ReviewArgs {
            command:
                ReviewCommand::Session(ReviewSessionArgs {
                    command: ReviewSessionCommand::Query(query),
                }),
        }) = cli.command
        else {
            panic!("expected review session query command");
        };
        assert_eq!(query.file, PathBuf::from("review.sfm-review.json"));
        assert_eq!(
            query.expression,
            ["#approved", "intersect", "1.19.2", "HEAD"]
        );
    }

    #[test]
    fn parses_explicit_release_review_creation_boundary() {
        let cli = parse(&[
            "review",
            "session",
            "create",
            "--file",
            "docs/reviews/previous-to-head.sfm-review.json",
            "--branch",
            "1.19.2",
            "--before",
            "4.34.0-1.19.2",
            "--candidate",
            "HEAD",
        ]);
        let Command::Review(ReviewArgs {
            command:
                ReviewCommand::Session(ReviewSessionArgs {
                    command: ReviewSessionCommand::Create(create),
                }),
        }) = cli.command
        else {
            panic!("expected review session create command");
        };
        assert_eq!(create.branch, "1.19.2");
        assert_eq!(create.before, "4.34.0-1.19.2");
        assert_eq!(create.candidate, "HEAD");
        assert_eq!(
            create.file,
            PathBuf::from("docs/reviews/previous-to-head.sfm-review.json")
        );
    }

    #[test]
    fn parses_canonical_release_review_refresh_boundary() {
        let cli = parse(&[
            "review",
            "session",
            "refresh",
            "--file",
            "docs/reviews/previous-to-head.sfm-review.json",
        ]);
        let Command::Review(ReviewArgs {
            command:
                ReviewCommand::Session(ReviewSessionArgs {
                    command: ReviewSessionCommand::Refresh(refresh),
                }),
        }) = cli.command
        else {
            panic!("expected review session refresh command");
        };
        assert_eq!(
            refresh.file,
            PathBuf::from("docs/reviews/previous-to-head.sfm-review.json")
        );
    }

    #[test]
    fn creation_pins_a_real_git_range_reconciles_it_and_refuses_to_clobber() {
        let repository = tempfile::tempdir().expect("temporary Git repository");
        git(repository.path(), &["init", "--quiet"]);
        std::fs::create_dir_all(repository.path().join("src")).expect("source directory");
        std::fs::write(
            repository.path().join("src/Example.java"),
            "class Example { int value() { return 1; } }\n",
        )
        .expect("before source");
        git(repository.path(), &["add", "."]);
        git(
            repository.path(),
            &[
                "-c",
                "user.name=SFM Review Fixture",
                "-c",
                "user.email=fixture@example.invalid",
                "commit",
                "--quiet",
                "-m",
                "before",
            ],
        );
        let before = git_stdout(repository.path(), &["rev-parse", "HEAD"]);
        std::fs::write(
            repository.path().join("src/Example.java"),
            "class Example { int value() { return 2; } }\n",
        )
        .expect("candidate source");
        git(repository.path(), &["add", "."]);
        git(
            repository.path(),
            &[
                "-c",
                "user.name=SFM Review Fixture",
                "-c",
                "user.email=fixture@example.invalid",
                "commit",
                "--quiet",
                "-m",
                "candidate",
            ],
        );
        let candidate = git_stdout(repository.path(), &["rev-parse", "HEAD"]);
        let output = PathBuf::from("docs/reviews/before-to-head.sfm-review.json");
        let create = || ReviewSessionCreateArgs {
            file: output.clone(),
            branch: "1.19.2".to_owned(),
            before: before.clone(),
            candidate: "HEAD".to_owned(),
            repository_root: None,
        };

        let rendered = create()
            .invoke_in(repository.path())
            .expect("real Git review should materialize")
            .render(Some(OutputFormat::Json), false)
            .expect("creation output JSON")
            .expect("creation output");
        assert!(rendered.contains(&candidate));
        assert!(rendered.contains("\"reconciliation_complete\": true"));
        let review_path = repository.path().join(&output);
        let document = release_review_v1::parse(
            &std::fs::read_to_string(&review_path).expect("portable review file"),
        )
        .expect("canonical release-review document");
        assert_eq!(document.repository_bindings[0].before_commit, before);
        assert_eq!(document.repository_bindings[0].candidate_commit, candidate);
        assert_eq!(document.review_units.len(), 1);
        assert!(
            create().invoke_in(repository.path()).is_err(),
            "existing file must not be replaced"
        );
    }

    #[test]
    fn refresh_clears_stale_generation_and_refuses_source_affecting_divergence() {
        let repository = tempfile::tempdir().expect("temporary Git repository");
        git(repository.path(), &["init", "--quiet"]);
        std::fs::create_dir_all(repository.path().join("src")).expect("source directory");
        std::fs::write(
            repository.path().join("src/Example.java"),
            "class Example { int value() { return 1; } }\n",
        )
        .expect("before source");
        git(repository.path(), &["add", "."]);
        git(
            repository.path(),
            &[
                "-c",
                "user.name=SFM Review Fixture",
                "-c",
                "user.email=fixture@example.invalid",
                "commit",
                "--quiet",
                "-m",
                "before",
            ],
        );
        let before = git_stdout(repository.path(), &["rev-parse", "HEAD"]);
        std::fs::write(
            repository.path().join("src/Example.java"),
            "class Example { int value() { return 2; } }\n",
        )
        .expect("candidate source");
        git(repository.path(), &["add", "."]);
        git(
            repository.path(),
            &[
                "-c",
                "user.name=SFM Review Fixture",
                "-c",
                "user.email=fixture@example.invalid",
                "commit",
                "--quiet",
                "-m",
                "candidate",
            ],
        );
        let relative = PathBuf::from("docs/reviews/refresh.sfm-review.json");
        ReviewSessionCreateArgs {
            file: relative.clone(),
            branch: "1.19.2".to_owned(),
            before,
            candidate: "HEAD".to_owned(),
            repository_root: None,
        }
        .invoke_in(repository.path())
        .expect("create review");
        let path = repository.path().join(&relative);
        let mut stale = read_review_document_at(&path).expect("created review");
        stale.producer_generations[0].generation = "git-materializer-v1:stale".to_owned();
        for unit in &mut stale.review_units {
            unit.producer_generation = "git-materializer-v1:stale".to_owned();
        }
        for comment in &mut stale.review_session.comments {
            if comment.provenance.producer == release_review_materialize::PRODUCER_ID {
                comment.provenance.version = "git-materializer-v1:stale".to_owned();
            }
        }
        std::fs::write(
            &path,
            release_review_v1::to_canonical_json(&stale).expect("stale canonical review"),
        )
        .expect("write stale review");

        let refreshed = ReviewSessionRefreshArgs {
            file: relative.clone(),
        }
        .invoke_in(repository.path())
        .expect("refresh output");
        assert_eq!(refreshed.exit_code(), 0);
        let rendered = refreshed
            .render(Some(OutputFormat::Json), false)
            .expect("refresh JSON")
            .expect("refresh output");
        assert!(rendered.contains("\"outcome\": \"refreshed\""));
        assert!(rendered.contains("git-materializer-v1:stale"));
        let document = read_review_document_at(&path).expect("refreshed document");
        assert_eq!(
            release_review_v1::completion(&document)
                .expect("refreshed completion")
                .stale_producer,
            0
        );
        let generated_ids = document
            .review_session
            .comments
            .iter()
            .filter(|comment| {
                comment.provenance.producer == release_review_materialize::PRODUCER_ID
            })
            .map(|comment| comment.id.as_str())
            .collect::<std::collections::BTreeSet<_>>();
        assert_eq!(generated_ids.len(), document.review_units.len());

        git(
            repository.path(),
            &["add", "docs/reviews/refresh.sfm-review.json"],
        );
        git(
            repository.path(),
            &[
                "-c",
                "user.name=SFM Review Fixture",
                "-c",
                "user.email=fixture@example.invalid",
                "commit",
                "--quiet",
                "-m",
                "review evidence",
            ],
        );
        let evidence_only = ReviewSessionRefreshArgs {
            file: relative.clone(),
        }
        .invoke_in(repository.path())
        .expect("review-evidence-only refresh");
        assert_eq!(evidence_only.exit_code(), 0);
        let rendered = evidence_only
            .render(Some(OutputFormat::Json), false)
            .expect("review-evidence refresh JSON")
            .expect("review-evidence refresh output");
        assert!(rendered.contains("review_evidence_only"));

        let accepted_bytes = std::fs::read(&path).expect("accepted refresh bytes");
        std::fs::write(
            repository.path().join("src/Example.java"),
            "class Example { int value() { return 3; } }\n",
        )
        .expect("diverged source");
        git(repository.path(), &["add", "src/Example.java"]);
        git(
            repository.path(),
            &[
                "-c",
                "user.name=SFM Review Fixture",
                "-c",
                "user.email=fixture@example.invalid",
                "commit",
                "--quiet",
                "-m",
                "source divergence",
            ],
        );
        let refused = ReviewSessionRefreshArgs { file: relative }
            .invoke_in(repository.path())
            .expect("typed refresh refusal");
        assert_eq!(refused.exit_code(), REVIEW_NOT_COMPLETE_EXIT_CODE);
        let rendered = refused
            .render(Some(OutputFormat::Json), false)
            .expect("refusal JSON")
            .expect("refusal output");
        assert!(rendered.contains("\"outcome\": \"refused\""));
        assert!(rendered.contains("refresh.repository-diverged"));
        assert!(rendered.contains("source_affecting_divergence"));
        assert_eq!(
            std::fs::read(path).expect("review bytes after refusal"),
            accepted_bytes,
            "unsafe refresh must not replace the review document"
        );
    }

    #[test]
    fn query_and_status_use_typed_output_and_fail_closed_exit_status() {
        let directory = tempfile::tempdir().expect("temporary review directory");
        let ready_path = directory.path().join("ready.sfm-review.json");
        std::fs::write(
            &ready_path,
            release_review_v1::to_canonical_json(&test_support::exact_document())
                .expect("ready fixture"),
        )
        .expect("write ready fixture");

        let query = ReviewSessionQueryArgs {
            file: PathBuf::from("ready.sfm-review.json"),
            expression: vec![
                "#approved".to_owned(),
                "intersect".to_owned(),
                "1.19.2".to_owned(),
                "HEAD".to_owned(),
            ],
        }
        .invoke_in(directory.path())
        .expect("query should succeed");
        let rendered = query
            .render(Some(OutputFormat::Json), false)
            .expect("query JSON")
            .expect("query output");
        assert!(rendered.contains("#approved intersect 1.19.2 HEAD"));
        assert!(rendered.contains("unit-1"));

        let unavailable_repository = ReviewSessionStatusArgs {
            file: PathBuf::from("ready.sfm-review.json"),
        }
        .invoke_in(directory.path())
        .expect("missing repository should produce a typed fail-closed report");
        assert_eq!(
            unavailable_repository.exit_code(),
            REVIEW_NOT_COMPLETE_EXIT_CODE
        );
        let rendered = unavailable_repository
            .render(Some(OutputFormat::Json), false)
            .expect("status JSON")
            .expect("status output");
        assert!(rendered.contains("source_affecting_divergence"));
        assert!(rendered.contains("ambient.repository-unavailable"));

        let incomplete_path = directory.path().join("incomplete.sfm-review.json");
        std::fs::write(
            &incomplete_path,
            release_review_v1::to_canonical_json(&test_support::in_progress_document())
                .expect("incomplete fixture"),
        )
        .expect("write incomplete fixture");
        let incomplete = ReviewSessionStatusArgs {
            file: PathBuf::from("incomplete.sfm-review.json"),
        }
        .invoke_in(directory.path())
        .expect("incomplete status report should still render");
        assert_eq!(incomplete.exit_code(), REVIEW_NOT_COMPLETE_EXIT_CODE);

        let stale_path = directory.path().join("stale.sfm-review.json");
        std::fs::write(
            &stale_path,
            release_review_v1::to_canonical_json(&test_support::stale_document())
                .expect("stale fixture"),
        )
        .expect("write stale fixture");
        let stale = ReviewSessionStatusArgs {
            file: PathBuf::from("stale.sfm-review.json"),
        }
        .invoke_in(directory.path())
        .expect("stale status report should still render");
        assert_eq!(stale.exit_code(), REVIEW_NOT_COMPLETE_EXIT_CODE);
    }

    #[test]
    fn status_accepts_exact_and_review_evidence_only_ambient_commits() {
        let repository = tempfile::tempdir().expect("temporary Git repository");
        git(repository.path(), &["init", "--quiet"]);
        std::fs::write(repository.path().join("README.md"), "candidate\n")
            .expect("candidate content");
        git(repository.path(), &["add", "."]);
        git(
            repository.path(),
            &[
                "-c",
                "user.name=SFM Review Fixture",
                "-c",
                "user.email=fixture@example.invalid",
                "commit",
                "--quiet",
                "-m",
                "candidate",
            ],
        );
        let candidate = git_stdout(repository.path(), &["rev-parse", "HEAD"]);
        let tree = git_stdout(repository.path(), &["rev-parse", "HEAD^{tree}"]);
        let relative_review_path = PathBuf::from("docs/reviews/ready.sfm-review.json");
        let review_path = repository.path().join(&relative_review_path);
        std::fs::create_dir_all(review_path.parent().expect("review parent"))
            .expect("create review directory");
        let mut document = test_support::exact_document();
        let binding = &mut document.repository_bindings[0];
        binding.root_hint = ".".to_owned();
        binding.before_commit.clone_from(&candidate);
        binding.before_tree.clone_from(&tree);
        binding.candidate_commit.clone_from(&candidate);
        binding.candidate_tree = tree;
        binding.review_evidence_paths =
            vec![relative_review_path.to_string_lossy().replace('\\', "/")];
        std::fs::write(
            &review_path,
            release_review_v1::to_canonical_json(&document).expect("canonical ready document"),
        )
        .expect("write review document");

        let exact = ReviewSessionStatusArgs {
            file: relative_review_path.clone(),
        }
        .invoke_in(repository.path())
        .expect("exact status");
        assert_eq!(exact.exit_code(), 0);
        let exact_json = exact
            .render(Some(OutputFormat::Json), false)
            .expect("exact JSON")
            .expect("exact output");
        assert!(exact_json.contains("\"classification\": \"exact\""));

        git(repository.path(), &["add", "."]);
        git(
            repository.path(),
            &[
                "-c",
                "user.name=SFM Review Fixture",
                "-c",
                "user.email=fixture@example.invalid",
                "commit",
                "--quiet",
                "-m",
                "review evidence",
            ],
        );
        let evidence_only = ReviewSessionStatusArgs {
            file: relative_review_path,
        }
        .invoke_in(repository.path())
        .expect("evidence-only status");
        assert_eq!(evidence_only.exit_code(), 0);
        let evidence_json = evidence_only
            .render(Some(OutputFormat::Json), false)
            .expect("evidence JSON")
            .expect("evidence output");
        assert!(evidence_json.contains("\"classification\": \"review_evidence_only\""));
        assert!(evidence_json.contains("docs/reviews/ready.sfm-review.json"));
    }

    fn git(repository: &Path, arguments: &[&str]) {
        let output = ProcessCommand::new("git")
            .args(arguments)
            .current_dir(repository)
            .output()
            .expect("run Git fixture command");
        assert!(
            output.status.success(),
            "Git fixture command failed: {}",
            String::from_utf8_lossy(&output.stderr)
        );
    }

    fn git_stdout(repository: &Path, arguments: &[&str]) -> String {
        let output = ProcessCommand::new("git")
            .args(arguments)
            .current_dir(repository)
            .output()
            .expect("run Git fixture query");
        assert!(
            output.status.success(),
            "Git fixture query failed: {}",
            String::from_utf8_lossy(&output.stderr)
        );
        String::from_utf8(output.stdout)
            .expect("Git fixture UTF-8 output")
            .trim()
            .to_owned()
    }
}
