use super::DependencySymbolIndexQueryOutput;
use super::DiagnosticSeverity;
use super::JavaAnalysisContextOutput;
use super::JavaAnalysisDiagnosticOutput;
use super::JavaClasspathMode;
use super::JavaSourceExclusionOutput;
use super::JavaSourceRootOutput;
use super::JavaSourceSetOutput;
use super::JavaSourceSpanOutput;
use super::JavaSymbolIdentityOutput;
use super::ResolutionConfidence;
use super::SymbolQueryCompleteness;
use eyre::WrapErr as _;
use facet::Facet;
use sha2::Digest as _;
use sha2::Sha256;
use std::collections::BTreeSet;

pub const DEFINITION_AT_POSITION_REQUEST_SCHEMA: &str = "sfm.definition-at-position-request/2";
pub const DEFINITION_AT_POSITION_RESULT_SCHEMA: &str = "sfm.definition-at-position-result/3";

/// Immutable workspace identity carried by an editor-location request.
///
/// Concrete root paths are resolved and authorized outside this DTO. The
/// request carries the ordered, portable root projection so a long-lived
/// worker can reject a request aimed at a different workspace generation.
#[derive(Facet, Clone, Debug, Eq, PartialEq)]
pub struct DefinitionWorkspaceIdentityInput {
    pub branch: String,
    pub classpath_mode: JavaClasspathMode,
    pub source_roots: Vec<JavaSourceRootOutput>,
    pub classpath_fingerprint: String,
    #[facet(default, skip_serializing_if = Option::is_none)]
    pub dependency_index_identity: Option<String>,
    /// Hash of the branch, Java/Minecraft language context, ordered roots,
    /// source-set visibility, exclusions, classpath/parser identities, and
    /// dependency-index identity. This lets a worker reject semantically
    /// different workspaces without exposing machine-local paths.
    pub workspace_fingerprint: String,
    pub workspace_generation: u64,
}

impl DefinitionWorkspaceIdentityInput {
    /// Validate the portable workspace identity without resolving filesystem
    /// authority or consulting mutable process state.
    ///
    /// # Errors
    ///
    /// Returns an error for blank identity fields or duplicate roots.
    pub fn validate(&self) -> eyre::Result<()> {
        require_nonblank(&self.branch, "workspace branch")?;
        require_nonblank(&self.classpath_fingerprint, "classpath fingerprint")?;
        validate_hash_shape(&self.workspace_fingerprint)?;
        let mut root_ids = BTreeSet::new();
        for root in &self.source_roots {
            require_nonblank(&root.id, "source-root id")?;
            require_nonblank(&root.source_set, "source-root source set")?;
            require_nonblank(&root.path, "source-root path")?;
            if !root_ids.insert(root.id.as_str()) {
                eyre::bail!(
                    "definition workspace contains duplicate source-root id `{}`",
                    root.id
                );
            }
        }
        if self
            .dependency_index_identity
            .as_ref()
            .is_some_and(|identity| identity.trim().is_empty())
        {
            eyre::bail!("dependency-index identity must be omitted rather than blank");
        }
        Ok(())
    }
}

/// Exact current editor document supplied to definition analysis.
#[derive(Facet, Clone, Debug, Eq, PartialEq)]
pub struct DefinitionDocumentInput {
    /// Opaque provider-issued document address used by the caller. Root and
    /// path authority are carried separately and validated by the provider.
    pub address: String,
    /// Resolver-issued root identity containing `path`.
    pub root_id: String,
    /// Canonical path relative to `root_id`, using `/` separators.
    pub root_relative_path: String,
    /// Stable workspace report path used by public source spans.
    pub report_path: String,
    pub source_set: String,
    /// Exact current editor snapshot. It may differ from disk and is never
    /// written by definition analysis.
    pub text: String,
    /// `blake3:<hex>` or `sha256:<hex>` over `text` as UTF-8.
    pub content_hash: String,
    /// Hash of the disk snapshot when known, allowing an overlay to state its
    /// relationship to the immutable workspace snapshot explicitly.
    #[facet(default, skip_serializing_if = Option::is_none)]
    pub disk_content_hash: Option<String>,
}

impl DefinitionDocumentInput {
    /// Validate canonical document identity and prove the supplied current
    /// content hash before any parser consumes the text.
    ///
    /// # Errors
    ///
    /// Returns an error for malformed identity, traversal, unsupported hashes,
    /// or content/hash disagreement.
    pub fn validate(&self, workspace: &DefinitionWorkspaceIdentityInput) -> eyre::Result<()> {
        require_nonblank(&self.address, "document address")?;
        require_nonblank(&self.root_id, "document root id")?;
        require_nonblank(&self.root_relative_path, "document root-relative path")?;
        require_nonblank(&self.report_path, "document report path")?;
        require_nonblank(&self.source_set, "document source set")?;
        if self.root_relative_path.contains('\\') || self.report_path.contains('\\') {
            eyre::bail!("definition document paths must use canonical `/` separators");
        }
        for (label, path) in [
            ("root-relative", self.root_relative_path.as_str()),
            ("report", self.report_path.as_str()),
        ] {
            if path
                .split('/')
                .any(|segment| segment.is_empty() || matches!(segment, "." | ".."))
            {
                eyre::bail!("definition document {label} path contains a non-canonical segment");
            }
        }
        let root = workspace
            .source_roots
            .iter()
            .find(|root| root.id == self.root_id)
            .ok_or_else(|| {
                eyre::eyre!(
                    "definition document references unknown source-root id `{}`",
                    self.root_id
                )
            })?;
        if !root.exists {
            eyre::bail!(
                "definition document source root `{}` is unavailable",
                root.id
            );
        }
        if root.source_set != self.source_set {
            eyre::bail!(
                "definition document source set `{}` disagrees with root `{}` source set `{}`",
                self.source_set,
                root.id,
                root.source_set
            );
        }
        validate_content_hash(&self.text, &self.content_hash)?;
        if let Some(disk_hash) = &self.disk_content_hash {
            validate_hash_shape(disk_hash)?;
        }
        Ok(())
    }
}

/// One-based Unicode-scalar source position plus its exact UTF-8 byte offset.
///
/// Line endings may be LF or CRLF. The column counts Unicode scalar values,
/// matching existing Java-analysis span reports rather than UTF-16 code units
/// or terminal cells.
#[derive(Facet, Clone, Copy, Debug, Eq, PartialEq)]
pub struct DefinitionTextPositionInput {
    pub line: u64,
    pub column: u64,
    pub byte_offset: u64,
}

impl DefinitionTextPositionInput {
    /// Derive the UTF-8 byte offset for a one-based Unicode-scalar position.
    ///
    /// # Errors
    ///
    /// Returns an error when the line or column is outside the source.
    pub fn from_line_column(text: &str, line: u64, column: u64) -> eyre::Result<Self> {
        let byte_offset = byte_offset_for_line_column(text, line, column)?;
        Ok(Self {
            line,
            column,
            byte_offset: u64::try_from(byte_offset)
                .wrap_err("definition byte offset does not fit u64")?,
        })
    }

    /// Prove that all three coordinate projections identify one insertion
    /// point in the supplied text.
    ///
    /// # Errors
    ///
    /// Returns an error for invalid positions, non-character byte boundaries,
    /// or disagreement between line/column and byte offset.
    pub fn validate(&self, text: &str) -> eyre::Result<()> {
        let expected = byte_offset_for_line_column(text, self.line, self.column)?;
        let supplied = usize::try_from(self.byte_offset)
            .wrap_err("definition byte offset does not fit usize")?;
        if supplied > text.len() || !text.is_char_boundary(supplied) {
            eyre::bail!("definition byte offset is not a UTF-8 character boundary");
        }
        if supplied != expected {
            eyre::bail!(
                "definition line/column resolves to byte {expected}, not supplied byte {supplied}"
            );
        }
        Ok(())
    }
}

/// Versioned provider-neutral request shared by direct CLI, worker, and game.
#[derive(Facet, Clone, Debug, Eq, PartialEq)]
pub struct DefinitionAtPositionRequest {
    pub schema: String,
    pub request_id: u64,
    /// Generation within the provider-issued origin represented by this
    /// request. It is correlation data, not a worker-global ordering: clients
    /// supersede older same-origin work with an explicit cancellation frame.
    pub request_generation: u64,
    pub workspace: DefinitionWorkspaceIdentityInput,
    pub document: DefinitionDocumentInput,
    pub position: DefinitionTextPositionInput,
}

impl DefinitionAtPositionRequest {
    #[must_use]
    pub fn new(
        request_id: u64,
        request_generation: u64,
        workspace: DefinitionWorkspaceIdentityInput,
        document: DefinitionDocumentInput,
        position: DefinitionTextPositionInput,
    ) -> Self {
        Self {
            schema: DEFINITION_AT_POSITION_REQUEST_SCHEMA.to_owned(),
            request_id,
            request_generation,
            workspace,
            document,
            position,
        }
    }

    /// Validate every self-contained request invariant without consulting the
    /// filesystem or a worker session.
    ///
    /// # Errors
    ///
    /// Returns an error for incompatible schema, workspace/document mismatch,
    /// hash disagreement, or invalid position.
    pub fn validate(&self) -> eyre::Result<()> {
        if self.schema != DEFINITION_AT_POSITION_REQUEST_SCHEMA {
            eyre::bail!("definition-at-position request schema is unsupported");
        }
        self.workspace.validate()?;
        self.document.validate(&self.workspace)?;
        self.position.validate(&self.document.text)
    }
}

#[derive(Facet, Clone, Copy, Debug, Eq, PartialEq, Ord, PartialOrd)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum DefinitionAtPositionOutcome {
    Success,
    NoSymbol,
    NoDefinition,
    Ambiguous,
    StaleDocument,
    InvalidRequest,
    Unavailable,
}

impl DefinitionAtPositionOutcome {
    #[must_use]
    pub const fn exit_code(self) -> u8 {
        match self {
            Self::Success => 0,
            Self::NoSymbol | Self::NoDefinition => 2,
            Self::Ambiguous => 3,
            Self::StaleDocument | Self::InvalidRequest | Self::Unavailable => 4,
        }
    }
}

#[derive(Facet, Clone, Copy, Debug, Eq, PartialEq, Ord, PartialOrd)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum DefinitionRecoveryActionKind {
    Retry,
    AcquireDependencySources,
    RefreshDependencyIndex,
    RestartWorker,
}

#[derive(Facet, Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
pub struct DefinitionRecoveryActionOutput {
    pub kind: DefinitionRecoveryActionKind,
    pub label: String,
    #[facet(default, skip_serializing_if = Option::is_none)]
    pub command: Option<String>,
}

#[derive(Facet, Clone, Debug, Eq, PartialEq)]
pub struct DefinitionDocumentIdentityOutput {
    pub address: String,
    pub root_id: String,
    pub root_relative_path: String,
    pub report_path: String,
    pub source_set: String,
    pub content_hash: String,
    #[facet(default, skip_serializing_if = Option::is_none)]
    pub disk_content_hash: Option<String>,
}

/// Resolver-addressable source range returned specifically by location-based
/// definition analysis. The embedded report path remains compatible with the
/// established symbol reports, while the resolver/root pair disambiguates
/// equal relative paths across live and dependency roots.
#[derive(Facet, Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
pub struct DefinitionSourceSpanOutput {
    pub address: String,
    pub resolver_id: String,
    pub root_id: String,
    pub root_relative_path: String,
    pub report_path: String,
    pub source_set: String,
    pub source_hash: String,
    /// SHA-256 over the exact same UTF-8 source bytes as `source_hash`.
    ///
    /// Workspace-backed definitions carry this witness so clients without a
    /// BLAKE3 implementation can still pin an asynchronous file read. A span
    /// without a witness is not safe for file navigation.
    #[facet(default, skip_serializing_if = Option::is_none)]
    pub source_sha256: Option<String>,
    pub start_byte: u64,
    pub end_byte: u64,
    pub start_line: u64,
    pub start_column: u64,
    pub end_line: u64,
    pub end_column: u64,
}

impl DefinitionSourceSpanOutput {
    #[must_use]
    pub fn from_report_span(
        span: &JavaSourceSpanOutput,
        resolver_id: impl Into<String>,
        root_id: impl Into<String>,
        root_relative_path: impl Into<String>,
        address: impl Into<String>,
        source_sha256: Option<String>,
    ) -> Self {
        Self {
            address: address.into(),
            resolver_id: resolver_id.into(),
            root_id: root_id.into(),
            root_relative_path: root_relative_path.into(),
            report_path: span.path.clone(),
            source_set: span.source_set.clone(),
            source_hash: span.source_hash.clone(),
            source_sha256,
            start_byte: span.start_byte,
            end_byte: span.end_byte,
            start_line: span.start_line,
            start_column: span.start_column,
            end_line: span.end_line,
            end_column: span.end_column,
        }
    }
}

#[derive(Facet, Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
pub struct DefinitionAtPositionDefinitionOutput {
    pub symbol: JavaSymbolIdentityOutput,
    pub identifier_span: DefinitionSourceSpanOutput,
    pub declaration_span: DefinitionSourceSpanOutput,
    pub confidence: ResolutionConfidence,
}

impl From<&DefinitionDocumentInput> for DefinitionDocumentIdentityOutput {
    fn from(document: &DefinitionDocumentInput) -> Self {
        Self {
            address: document.address.clone(),
            root_id: document.root_id.clone(),
            root_relative_path: document.root_relative_path.clone(),
            report_path: document.report_path.clone(),
            source_set: document.source_set.clone(),
            content_hash: document.content_hash.clone(),
            disk_content_hash: document.disk_content_hash.clone(),
        }
    }
}

/// Typed definition-at-position result. Transport telemetry is deliberately
/// absent so direct and worker modes can produce canonical-byte-equivalent
/// values.
#[derive(Facet, Clone, Debug, Eq, PartialEq)]
pub struct DefinitionAtPositionResult {
    pub schema: String,
    pub request_id: u64,
    pub request_generation: u64,
    pub workspace_generation: u64,
    pub outcome: DefinitionAtPositionOutcome,
    pub context: JavaAnalysisContextOutput,
    pub document: DefinitionDocumentIdentityOutput,
    pub position: DefinitionTextPositionInput,
    pub symbols: Vec<JavaSymbolIdentityOutput>,
    pub definitions: Vec<DefinitionAtPositionDefinitionOutput>,
    pub completeness: SymbolQueryCompleteness,
    pub diagnostics: Vec<JavaAnalysisDiagnosticOutput>,
    pub recovery_actions: Vec<DefinitionRecoveryActionOutput>,
    #[facet(default, skip_serializing_if = Option::is_none)]
    pub dependency_index: Option<DependencySymbolIndexQueryOutput>,
}

impl DefinitionAtPositionResult {
    #[must_use]
    pub fn status(&self) -> u8 {
        match self.outcome {
            DefinitionAtPositionOutcome::InvalidRequest
            | DefinitionAtPositionOutcome::StaleDocument
            | DefinitionAtPositionOutcome::Unavailable => self.outcome.exit_code(),
            _ if self.completeness == SymbolQueryCompleteness::Incomplete => 5,
            _ => self.outcome.exit_code(),
        }
    }

    /// Attach dependency-index completeness and actionable refresh/acquisition
    /// commands without changing the transport-independent symbol result.
    #[must_use]
    pub fn with_dependency_index(mut self, index: DependencySymbolIndexQueryOutput) -> Self {
        self.completeness = index.completeness;
        if index.completeness == SymbolQueryCompleteness::Incomplete {
            if !index.refresh_command.trim().is_empty() {
                self.recovery_actions.push(DefinitionRecoveryActionOutput {
                    kind: DefinitionRecoveryActionKind::RefreshDependencyIndex,
                    label: "Refresh the dependency symbol index".to_owned(),
                    command: Some(index.refresh_command.clone()),
                });
            }
            self.recovery_actions
                .extend(index.acquisition_commands.iter().map(|command| {
                    DefinitionRecoveryActionOutput {
                        kind: DefinitionRecoveryActionKind::AcquireDependencySources,
                        label: "Acquire a missing dependency source".to_owned(),
                        command: Some(command.clone()),
                    }
                }));
        }
        self.recovery_actions.sort();
        self.recovery_actions.dedup();
        self.dependency_index = Some(index);
        self
    }

    #[must_use]
    pub fn stale_document(
        request: &DefinitionAtPositionRequest,
        context: JavaAnalysisContextOutput,
        message: impl Into<String>,
    ) -> Self {
        Self {
            schema: DEFINITION_AT_POSITION_RESULT_SCHEMA.to_owned(),
            request_id: request.request_id,
            request_generation: request.request_generation,
            workspace_generation: request.workspace.workspace_generation,
            outcome: DefinitionAtPositionOutcome::StaleDocument,
            context,
            document: (&request.document).into(),
            position: request.position,
            symbols: Vec::new(),
            definitions: Vec::new(),
            completeness: SymbolQueryCompleteness::Complete,
            diagnostics: vec![JavaAnalysisDiagnosticOutput {
                code: "java.definition-position-stale-document".to_owned(),
                severity: DiagnosticSeverity::Error,
                message: message.into(),
                span: None,
            }],
            recovery_actions: vec![DefinitionRecoveryActionOutput {
                kind: DefinitionRecoveryActionKind::Retry,
                label: "Capture the current document and retry".to_owned(),
                command: None,
            }],
            dependency_index: None,
        }
    }

    /// Add one stable diagnostic while retaining deterministic result order.
    #[must_use]
    pub fn with_diagnostic(mut self, diagnostic: JavaAnalysisDiagnosticOutput) -> Self {
        self.diagnostics.push(diagnostic);
        self.diagnostics.sort();
        self.diagnostics.dedup();
        self
    }

    #[must_use]
    pub fn to_csv(&self) -> String {
        let report_row = DefinitionAtPositionCsvRow::report(self);
        let mut output = DefinitionAtPositionCsvRow::header();
        append_definition_position_csv_row(&mut output, &report_row);
        for definition in &self.definitions {
            let mut row = report_row.clone();
            "definition".clone_into(&mut row.record_kind);
            row.set_definition(definition);
            append_definition_position_csv_row(&mut output, &row);
        }
        for diagnostic in &self.diagnostics {
            let mut row = report_row.clone();
            "diagnostic".clone_into(&mut row.record_kind);
            row.diagnostic_code.clone_from(&diagnostic.code);
            diagnostic_severity_name(diagnostic.severity).clone_into(&mut row.diagnostic_severity);
            row.message.clone_from(&diagnostic.message);
            append_definition_position_csv_row(&mut output, &row);
        }
        for recovery in &self.recovery_actions {
            let mut row = report_row.clone();
            "recovery".clone_into(&mut row.record_kind);
            recovery_kind_name(recovery.kind).clone_into(&mut row.recovery_kind);
            row.message.clone_from(&recovery.label);
            row.recovery_command = recovery.command.clone().unwrap_or_default();
            append_definition_position_csv_row(&mut output, &row);
        }
        output
    }

    #[must_use]
    pub fn invalid_request(
        request: &DefinitionAtPositionRequest,
        context: JavaAnalysisContextOutput,
        message: impl Into<String>,
    ) -> Self {
        Self {
            schema: DEFINITION_AT_POSITION_RESULT_SCHEMA.to_owned(),
            request_id: request.request_id,
            request_generation: request.request_generation,
            workspace_generation: request.workspace.workspace_generation,
            outcome: DefinitionAtPositionOutcome::InvalidRequest,
            context,
            document: (&request.document).into(),
            position: request.position,
            symbols: Vec::new(),
            definitions: Vec::new(),
            completeness: SymbolQueryCompleteness::Incomplete,
            diagnostics: vec![JavaAnalysisDiagnosticOutput {
                code: "java.definition-position-invalid-request".to_owned(),
                severity: DiagnosticSeverity::Error,
                message: message.into(),
                span: None,
            }],
            recovery_actions: Vec::new(),
            dependency_index: None,
        }
    }
}

#[derive(Facet, Clone)]
struct DefinitionAtPositionCsvRow {
    schema: String,
    record_kind: String,
    outcome: String,
    status: u8,
    request_id: u64,
    request_generation: u64,
    workspace_generation: u64,
    completeness: String,
    context_json: String,
    document_address: String,
    document_root_id: String,
    document_root_relative_path: String,
    document_report_path: String,
    document_source_set: String,
    document_content_hash: String,
    document_disk_content_hash: String,
    position_line: u64,
    position_column: u64,
    position_byte_offset: u64,
    symbol_kind: String,
    symbol_owner: String,
    symbol_name: String,
    symbol_descriptor: String,
    symbol_qualified_name: String,
    confidence: String,
    resolver_id: String,
    root_id: String,
    root_relative_path: String,
    address: String,
    report_path: String,
    source_set: String,
    source_hash: String,
    start_byte: String,
    end_byte: String,
    start_line: String,
    start_column: String,
    end_line: String,
    end_column: String,
    diagnostic_code: String,
    diagnostic_severity: String,
    recovery_kind: String,
    recovery_command: String,
    message: String,
}

impl DefinitionAtPositionCsvRow {
    fn report(result: &DefinitionAtPositionResult) -> Self {
        Self {
            schema: result.schema.clone(),
            record_kind: "report".to_owned(),
            outcome: definition_outcome_name(result.outcome).to_owned(),
            status: result.status(),
            request_id: result.request_id,
            request_generation: result.request_generation,
            workspace_generation: result.workspace_generation,
            completeness: completeness_name(result.completeness).to_owned(),
            context_json: facet_json::to_string(&result.context)
                .expect("definition context must remain JSON serializable"),
            document_address: result.document.address.clone(),
            document_root_id: result.document.root_id.clone(),
            document_root_relative_path: result.document.root_relative_path.clone(),
            document_report_path: result.document.report_path.clone(),
            document_source_set: result.document.source_set.clone(),
            document_content_hash: result.document.content_hash.clone(),
            document_disk_content_hash: result
                .document
                .disk_content_hash
                .clone()
                .unwrap_or_default(),
            position_line: result.position.line,
            position_column: result.position.column,
            position_byte_offset: result.position.byte_offset,
            symbol_kind: String::new(),
            symbol_owner: String::new(),
            symbol_name: String::new(),
            symbol_descriptor: String::new(),
            symbol_qualified_name: String::new(),
            confidence: String::new(),
            resolver_id: String::new(),
            root_id: String::new(),
            root_relative_path: String::new(),
            address: String::new(),
            report_path: String::new(),
            source_set: String::new(),
            source_hash: String::new(),
            start_byte: String::new(),
            end_byte: String::new(),
            start_line: String::new(),
            start_column: String::new(),
            end_line: String::new(),
            end_column: String::new(),
            diagnostic_code: String::new(),
            diagnostic_severity: String::new(),
            recovery_kind: String::new(),
            recovery_command: String::new(),
            message: String::new(),
        }
    }

    fn header() -> String {
        concat!(
            "schema,record_kind,outcome,status,request_id,request_generation,workspace_generation,",
            "completeness,context_json,document_address,document_root_id,",
            "document_root_relative_path,document_report_path,document_source_set,",
            "document_content_hash,document_disk_content_hash,position_line,position_column,",
            "position_byte_offset,symbol_kind,symbol_owner,symbol_name,symbol_descriptor,",
            "symbol_qualified_name,confidence,resolver_id,root_id,root_relative_path,address,",
            "report_path,source_set,source_hash,start_byte,end_byte,start_line,start_column,",
            "end_line,end_column,diagnostic_code,diagnostic_severity,recovery_kind,",
            "recovery_command,message\n"
        )
        .to_owned()
    }

    fn set_definition(&mut self, definition: &DefinitionAtPositionDefinitionOutput) {
        symbol_kind_name(definition.symbol.kind).clone_into(&mut self.symbol_kind);
        self.symbol_owner.clone_from(&definition.symbol.owner);
        self.symbol_name.clone_from(&definition.symbol.name);
        self.symbol_descriptor = definition.symbol.descriptor.clone().unwrap_or_default();
        self.symbol_qualified_name
            .clone_from(&definition.symbol.qualified_name);
        confidence_name(definition.confidence).clone_into(&mut self.confidence);
        self.set_span(&definition.identifier_span);
    }

    fn set_span(&mut self, span: &DefinitionSourceSpanOutput) {
        self.resolver_id.clone_from(&span.resolver_id);
        self.root_id.clone_from(&span.root_id);
        self.root_relative_path.clone_from(&span.root_relative_path);
        self.address.clone_from(&span.address);
        self.report_path.clone_from(&span.report_path);
        self.source_set.clone_from(&span.source_set);
        self.source_hash.clone_from(&span.source_hash);
        self.start_byte = span.start_byte.to_string();
        self.end_byte = span.end_byte.to_string();
        self.start_line = span.start_line.to_string();
        self.start_column = span.start_column.to_string();
        self.end_line = span.end_line.to_string();
        self.end_column = span.end_column.to_string();
    }
}

fn append_definition_position_csv_row(output: &mut String, row: &DefinitionAtPositionCsvRow) {
    output.push_str(
        &facet_csv::to_string(row)
            .expect("the flat definition-at-position CSV row must remain serializable"),
    );
}

const fn definition_outcome_name(outcome: DefinitionAtPositionOutcome) -> &'static str {
    match outcome {
        DefinitionAtPositionOutcome::Success => "success",
        DefinitionAtPositionOutcome::NoSymbol => "no-symbol",
        DefinitionAtPositionOutcome::NoDefinition => "no-definition",
        DefinitionAtPositionOutcome::Ambiguous => "ambiguous",
        DefinitionAtPositionOutcome::StaleDocument => "stale-document",
        DefinitionAtPositionOutcome::InvalidRequest => "invalid-request",
        DefinitionAtPositionOutcome::Unavailable => "unavailable",
    }
}

const fn completeness_name(completeness: SymbolQueryCompleteness) -> &'static str {
    match completeness {
        SymbolQueryCompleteness::Complete => "complete",
        SymbolQueryCompleteness::Incomplete => "incomplete",
    }
}

const fn recovery_kind_name(kind: DefinitionRecoveryActionKind) -> &'static str {
    match kind {
        DefinitionRecoveryActionKind::Retry => "retry",
        DefinitionRecoveryActionKind::AcquireDependencySources => "acquire-dependency-sources",
        DefinitionRecoveryActionKind::RefreshDependencyIndex => "refresh-dependency-index",
        DefinitionRecoveryActionKind::RestartWorker => "restart-worker",
    }
}

const fn diagnostic_severity_name(severity: DiagnosticSeverity) -> &'static str {
    match severity {
        DiagnosticSeverity::Info => "info",
        DiagnosticSeverity::Warning => "warning",
        DiagnosticSeverity::Error => "error",
    }
}

const fn confidence_name(confidence: ResolutionConfidence) -> &'static str {
    match confidence {
        ResolutionConfidence::Resolved => "resolved",
        ResolutionConfidence::PartiallyResolved => "partially-resolved",
        ResolutionConfidence::Unresolved => "unresolved",
    }
}

const fn symbol_kind_name(kind: super::JavaSymbolKind) -> &'static str {
    match kind {
        super::JavaSymbolKind::Class => "class",
        super::JavaSymbolKind::Interface => "interface",
        super::JavaSymbolKind::Enum => "enum",
        super::JavaSymbolKind::Record => "record",
        super::JavaSymbolKind::Annotation => "annotation",
        super::JavaSymbolKind::Field => "field",
        super::JavaSymbolKind::Method => "method",
        super::JavaSymbolKind::Constructor => "constructor",
    }
}

#[derive(Facet)]
struct DefinitionWorkspaceFingerprintInput {
    branch: String,
    minecraft_version: String,
    java_release: String,
    source_roots: Vec<JavaSourceRootOutput>,
    source_sets: Vec<JavaSourceSetOutput>,
    source_exclusions: Vec<JavaSourceExclusionOutput>,
    classpath_mode: JavaClasspathMode,
    classpath_fingerprint: String,
    parser_fingerprint: String,
    index_fingerprint: String,
    dependency_index_identity: Option<String>,
}

/// Hash the complete portable workspace semantics used by a location request.
///
/// # Errors
///
/// Returns an error if the typed fingerprint input cannot be encoded.
pub fn definition_workspace_fingerprint(
    context: &JavaAnalysisContextOutput,
    dependency_index_identity: Option<&str>,
) -> eyre::Result<String> {
    let input = DefinitionWorkspaceFingerprintInput {
        branch: context.branch.clone(),
        minecraft_version: context.minecraft_version.clone(),
        java_release: context.java_release.clone(),
        source_roots: context.source_roots.clone(),
        source_sets: context.source_sets.clone(),
        source_exclusions: context.source_exclusions.clone(),
        classpath_mode: context.classpath_mode,
        classpath_fingerprint: context.classpath_fingerprint.clone(),
        parser_fingerprint: context.parser_fingerprint.clone(),
        index_fingerprint: context.index_fingerprint.clone(),
        dependency_index_identity: dependency_index_identity.map(str::to_owned),
    };
    let bytes = facet_json::to_string(&input)
        .wrap_err("failed to encode definition workspace fingerprint input")?;
    Ok(blake3_content_hash(&bytes))
}

#[must_use]
pub fn blake3_content_hash(text: &str) -> String {
    format!("blake3:{}", blake3::hash(text.as_bytes()).to_hex())
}

#[must_use]
pub fn sha256_content_hash(text: &str) -> String {
    format!("sha256:{:x}", Sha256::digest(text.as_bytes()))
}

fn validate_content_hash(text: &str, expected: &str) -> eyre::Result<()> {
    let actual = content_hash_with_expected_algorithm(text, expected)?;
    if actual != expected {
        eyre::bail!("definition document content hash does not match supplied text");
    }
    Ok(())
}

pub(crate) fn content_hash_with_expected_algorithm(
    text: &str,
    expected: &str,
) -> eyre::Result<String> {
    validate_hash_shape(expected)?;
    Ok(if expected.starts_with("blake3:") {
        blake3_content_hash(text)
    } else {
        sha256_content_hash(text)
    })
}

fn validate_hash_shape(value: &str) -> eyre::Result<()> {
    let (algorithm, digest) = value
        .split_once(':')
        .ok_or_else(|| eyre::eyre!("content hash must include an algorithm prefix"))?;
    let expected_length = match algorithm {
        "blake3" | "sha256" => 64,
        _ => eyre::bail!("unsupported content-hash algorithm `{algorithm}`"),
    };
    if digest.len() != expected_length
        || !digest
            .bytes()
            .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte))
    {
        eyre::bail!("content hash digest must be lowercase hexadecimal");
    }
    Ok(())
}

fn byte_offset_for_line_column(text: &str, line: u64, column: u64) -> eyre::Result<usize> {
    if line == 0 || column == 0 {
        eyre::bail!("definition line and column are one-based");
    }
    let requested_line = usize::try_from(line).wrap_err("definition line does not fit usize")?;
    let requested_column =
        usize::try_from(column).wrap_err("definition column does not fit usize")?;
    let mut current_line = 1_usize;
    let mut line_start = 0_usize;
    for (offset, byte) in text.bytes().enumerate() {
        if current_line == requested_line {
            break;
        }
        if byte == b'\n' {
            current_line += 1;
            line_start = offset + 1;
        }
    }
    if current_line != requested_line {
        eyre::bail!("definition line {line} is outside the document");
    }
    let physical_end = text[line_start..]
        .find('\n')
        .map_or(text.len(), |relative| line_start + relative);
    let logical_end =
        if physical_end > line_start && text.as_bytes().get(physical_end - 1) == Some(&b'\r') {
            physical_end - 1
        } else {
            physical_end
        };
    let line_text = &text[line_start..logical_end];
    let scalar_index = requested_column - 1;
    let scalar_count = line_text.chars().count();
    if scalar_index > scalar_count {
        eyre::bail!(
            "definition column {column} is outside line {line}; maximum insertion column is {}",
            scalar_count + 1
        );
    }
    let relative = if scalar_index == scalar_count {
        line_text.len()
    } else {
        line_text
            .char_indices()
            .nth(scalar_index)
            .map(|(offset, _)| offset)
            .expect("bounded Unicode scalar index must exist")
    };
    Ok(line_start + relative)
}

fn require_nonblank(value: &str, label: &str) -> eyre::Result<()> {
    if value.trim().is_empty() {
        eyre::bail!("{label} must not be blank");
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::super::DependencySymbolIndexProbeStatus;
    use super::super::JavaSourceRootKind;
    use super::*;

    fn context() -> JavaAnalysisContextOutput {
        JavaAnalysisContextOutput {
            branch: "1.19.2".to_owned(),
            minecraft_version: "1.19.2".to_owned(),
            java_release: "17".to_owned(),
            jdk: "java-17".to_owned(),
            source_roots: vec![JavaSourceRootOutput {
                id: "custom-0".to_owned(),
                source_set: "custom".to_owned(),
                path: "source".to_owned(),
                kind: JavaSourceRootKind::Custom,
                exists: true,
            }],
            source_sets: vec![JavaSourceSetOutput {
                id: "custom".to_owned(),
                visible_source_sets: vec!["custom".to_owned()],
            }],
            source_exclusions: Vec::new(),
            classpath_mode: JavaClasspathMode::Isolated,
            classpath_fingerprint: "isolated".to_owned(),
            parser_fingerprint: "parser".to_owned(),
            index_fingerprint: "index".to_owned(),
        }
    }

    fn request(text: &str, line: u64, column: u64) -> DefinitionAtPositionRequest {
        let workspace = DefinitionWorkspaceIdentityInput {
            branch: "1.19.2".to_owned(),
            classpath_mode: JavaClasspathMode::Isolated,
            source_roots: vec![JavaSourceRootOutput {
                id: "custom-0".to_owned(),
                source_set: "custom".to_owned(),
                path: "source".to_owned(),
                kind: JavaSourceRootKind::Custom,
                exists: true,
            }],
            classpath_fingerprint: "blake3:workspace".to_owned(),
            dependency_index_identity: None,
            workspace_fingerprint: blake3_content_hash("workspace"),
            workspace_generation: 7,
        };
        let document = DefinitionDocumentInput {
            address: "file://source/example/A.java".to_owned(),
            root_id: "custom-0".to_owned(),
            root_relative_path: "example/A.java".to_owned(),
            report_path: "source/example/A.java".to_owned(),
            source_set: "custom".to_owned(),
            text: text.to_owned(),
            content_hash: blake3_content_hash(text),
            disk_content_hash: Some(sha256_content_hash(text)),
        };
        let position = DefinitionTextPositionInput::from_line_column(text, line, column)
            .expect("fixture position");
        DefinitionAtPositionRequest::new(11, 3, workspace, document, position)
    }

    #[test]
    fn definition_request_round_trips_through_facet_json() {
        let request = request("class A { String value; }\n", 1, 11);
        request.validate().expect("valid request");
        let encoded = facet_json::to_string(&request).expect("encode request");
        let decoded: DefinitionAtPositionRequest =
            facet_json::from_str(&encoded).expect("decode request");
        assert_eq!(decoded, request);
    }

    #[test]
    fn workspace_fingerprint_covers_visibility_and_dependency_identity() {
        let baseline = context();
        let first = definition_workspace_fingerprint(&baseline, None).expect("fingerprint");
        let mut changed = baseline.clone();
        changed.source_sets[0]
            .visible_source_sets
            .push("gametest".to_owned());
        let visibility =
            definition_workspace_fingerprint(&changed, None).expect("visibility fingerprint");
        let dependency = definition_workspace_fingerprint(&baseline, Some("dependency-v1"))
            .expect("dependency fingerprint");
        assert_ne!(first, visibility);
        assert_ne!(first, dependency);
    }

    #[test]
    fn invalid_and_incomplete_statuses_remain_distinct_and_csv_is_stable() {
        let request = request("class A {}\n", 1, 7);
        let invalid =
            DefinitionAtPositionResult::invalid_request(&request, context(), "invalid fixture");
        assert_eq!(invalid.status(), 4);
        let csv = invalid.to_csv();
        assert!(csv.starts_with("schema,record_kind,outcome,status,"));
        assert!(csv.contains("invalid-request"));
        let encoded = facet_json::to_string(&invalid).expect("encode result");
        let decoded: DefinitionAtPositionResult =
            facet_json::from_str(&encoded).expect("decode result");
        assert_eq!(decoded, invalid);

        let incomplete = decoded.with_dependency_index(DependencySymbolIndexQueryOutput {
            status: DependencySymbolIndexProbeStatus::Missing,
            completeness: SymbolQueryCompleteness::Incomplete,
            expected_identity: "expected".to_owned(),
            portable_path: "symbol-index/v3/expected".to_owned(),
            path: "cache/symbol-index/v3/expected".to_owned(),
            reason: "missing".to_owned(),
            refresh_command: "sfm-propagate-changes symbol index refresh --branch 1.19.2"
                .to_owned(),
            acquisition_commands: vec![
                "sfm-propagate-changes dependency source acquire --branch 1.19.2".to_owned(),
            ],
        });
        assert_eq!(incomplete.status(), 4);
        assert_eq!(incomplete.recovery_actions.len(), 2);
    }

    #[test]
    fn unicode_scalar_positions_derive_exact_utf8_offsets() {
        let text = "class A {\r\n  String café = \"🦀\";\r\n}\r\n";
        let position =
            DefinitionTextPositionInput::from_line_column(text, 2, 13).expect("Unicode position");
        let byte_offset = usize::try_from(position.byte_offset).expect("fixture offset");
        assert_eq!(&text.as_bytes()[byte_offset..][..2], "é".as_bytes());
        position.validate(text).expect("matching projections");

        let mut mismatched = position;
        mismatched.byte_offset += 1;
        let _ = mismatched.validate(text).unwrap_err();
    }

    #[test]
    fn request_rejects_hash_path_root_and_position_disagreement() {
        let mut value = request("class A {}\n", 1, 7);
        value.document.content_hash = blake3_content_hash("different");
        let _ = value.validate().unwrap_err();

        let mut value = request("class A {}\n", 1, 7);
        value.document.root_relative_path = "example/../A.java".to_owned();
        let _ = value.validate().unwrap_err();

        let mut value = request("class A {}\n", 1, 7);
        value.document.root_id = "missing".to_owned();
        let _ = value.validate().unwrap_err();

        let mut value = request("class A {}\n", 1, 7);
        value.position.byte_offset += 1;
        let _ = value.validate().unwrap_err();
    }

    #[test]
    fn content_hash_contract_accepts_java_sha256_snapshots() {
        let text = "class A {}\n";
        let mut value = request(text, 1, 7);
        value.document.content_hash = sha256_content_hash(text);
        value.validate().expect("SHA-256 snapshot");
    }

    #[test]
    fn position_rejects_line_column_outside_document() {
        let _ = DefinitionTextPositionInput::from_line_column("A\n", 3, 1).unwrap_err();
        let _ = DefinitionTextPositionInput::from_line_column("A\n", 1, 3).unwrap_err();
        let _ = DefinitionTextPositionInput::from_line_column("A\n", 0, 1).unwrap_err();
    }

    #[test]
    fn span_type_remains_available_to_result_consumers() {
        let span = JavaSourceSpanOutput {
            path: "source/A.java".to_owned(),
            source_set: "custom".to_owned(),
            source_hash: blake3_content_hash("A"),
            start_byte: 0,
            end_byte: 1,
            start_line: 1,
            start_column: 1,
            end_line: 1,
            end_column: 2,
        };
        assert_eq!(span.end_byte, 1);
    }
}
