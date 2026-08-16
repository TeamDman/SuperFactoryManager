use super::DefinitionAtPositionDefinitionOutput;
use super::DefinitionAtPositionOutcome;
use super::DefinitionAtPositionRequest;
use super::DefinitionAtPositionResult;
use super::DefinitionDocumentIdentityOutput;
use super::DefinitionDocumentInput;
use super::DefinitionRecoveryActionOutput;
use super::DefinitionSourceSpanOutput;
use super::DefinitionTextPositionInput;
use super::DefinitionWorkspaceIdentityInput;
use super::DependencySymbolIndexQueryOutput;
use super::JavaAnalysisContextOutput;
use super::JavaAnalysisDiagnosticOutput;
use super::JavaSourceSpanOutput;
use super::JavaSymbolIdentityOutput;
use super::JavaSymbolKind;
use super::JavaUsageKind;
use super::ResolutionConfidence;
use super::SymbolQueryCompleteness;
use facet::Facet;

pub const USAGE_AT_POSITION_REQUEST_SCHEMA: &str = "sfm.usage-at-position-request/1";
pub const USAGE_AT_POSITION_RESULT_SCHEMA: &str = "sfm.usage-at-position-result/1";
/// Leaves at least half of the 16 MiB worker frame for the response envelope,
/// telemetry, and future additive protocol fields.
pub(crate) const USAGE_AT_POSITION_MAX_ENCODED_BYTES: usize = 8 * 1024 * 1024;
/// Interactive reference responses are one bounded page. Larger result sets
/// remain explicitly incomplete through `result_limit` and the truncation
/// diagnostic; a later cursor/page protocol can retrieve further stable rows
/// without forcing Minecraft to decode thousands of spans in one frame.
pub(crate) const USAGE_AT_POSITION_MAX_RESULTS: usize = 512;

/// Provider-neutral request asking which symbol occupies one immutable editor
/// position and where that exact symbol is used.
#[derive(Facet, Clone, Debug, Eq, PartialEq)]
pub struct UsageAtPositionRequest {
    pub schema: String,
    pub request_id: u64,
    pub request_generation: u64,
    pub workspace: DefinitionWorkspaceIdentityInput,
    pub document: DefinitionDocumentInput,
    pub position: DefinitionTextPositionInput,
}

impl UsageAtPositionRequest {
    #[must_use]
    pub fn new(
        request_id: u64,
        request_generation: u64,
        workspace: DefinitionWorkspaceIdentityInput,
        document: DefinitionDocumentInput,
        position: DefinitionTextPositionInput,
    ) -> Self {
        Self {
            schema: USAGE_AT_POSITION_REQUEST_SCHEMA.to_owned(),
            request_id,
            request_generation,
            workspace,
            document,
            position,
        }
    }

    /// Validate every request invariant without consulting mutable process
    /// state or filesystem authority.
    ///
    /// # Errors
    ///
    /// Returns an error when the schema, workspace, document identity, or
    /// exact UTF-8 position is invalid.
    pub fn validate(&self) -> eyre::Result<()> {
        if self.schema != USAGE_AT_POSITION_REQUEST_SCHEMA {
            eyre::bail!("usage-at-position request schema is unsupported");
        }
        self.workspace.validate()?;
        self.document.validate(&self.workspace)?;
        self.position.validate(&self.document.text)
    }

    #[must_use]
    pub(crate) fn as_definition_request(&self) -> DefinitionAtPositionRequest {
        DefinitionAtPositionRequest::new(
            self.request_id,
            self.request_generation,
            self.workspace.clone(),
            self.document.clone(),
            self.position,
        )
    }
}

#[derive(Facet, Clone, Copy, Debug, Eq, PartialEq, Ord, PartialOrd)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum UsageAtPositionOutcome {
    Success,
    NoSymbol,
    NoDefinition,
    Ambiguous,
    StaleDocument,
    InvalidRequest,
    Unavailable,
}

impl UsageAtPositionOutcome {
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

impl From<DefinitionAtPositionOutcome> for UsageAtPositionOutcome {
    fn from(value: DefinitionAtPositionOutcome) -> Self {
        match value {
            DefinitionAtPositionOutcome::Success => Self::Success,
            DefinitionAtPositionOutcome::NoSymbol => Self::NoSymbol,
            DefinitionAtPositionOutcome::NoDefinition => Self::NoDefinition,
            DefinitionAtPositionOutcome::Ambiguous => Self::Ambiguous,
            DefinitionAtPositionOutcome::StaleDocument => Self::StaleDocument,
            DefinitionAtPositionOutcome::InvalidRequest => Self::InvalidRequest,
            DefinitionAtPositionOutcome::Unavailable => Self::Unavailable,
        }
    }
}

#[derive(Facet, Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
pub struct UsageAtPositionUsageOutput {
    pub target: JavaSymbolIdentityOutput,
    pub kind: JavaUsageKind,
    pub span: DefinitionSourceSpanOutput,
    pub confidence: ResolutionConfidence,
}

#[derive(Facet, Clone, Copy, Debug, Eq, PartialEq, Ord, PartialOrd)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum UsageAtPositionSkippedCategory {
    ReadWriteClassification,
    OverrideImplementation,
    Javadoc,
    StringLiteral,
    Reflection,
    DynamicDispatch,
}

#[derive(Facet, Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
pub struct UsageAtPositionSkippedOutput {
    pub category: UsageAtPositionSkippedCategory,
    pub reason: String,
}

/// Typed evidence that a successful usage query returned a deterministic
/// bounded prefix instead of attempting to materialize or frame every match.
#[derive(Facet, Clone, Debug, Eq, PartialEq)]
pub struct UsageAtPositionLimitOutput {
    pub count_limit: u64,
    pub encoded_size_limit: u64,
    pub returned_count: u64,
    pub discarded_candidate_count: u64,
    pub count_truncated: bool,
    pub encoded_size_truncated: bool,
}

/// Canonical semantic result shared by direct CLI and the supervised worker.
/// Runtime telemetry is deliberately excluded so both paths can be compared
/// byte-for-byte after deterministic sorting.
#[derive(Facet, Clone, Debug, Eq, PartialEq)]
pub struct UsageAtPositionResult {
    pub schema: String,
    pub request_id: u64,
    pub request_generation: u64,
    pub workspace_generation: u64,
    pub outcome: UsageAtPositionOutcome,
    pub context: JavaAnalysisContextOutput,
    pub document: DefinitionDocumentIdentityOutput,
    pub position: DefinitionTextPositionInput,
    pub targets: Vec<JavaSymbolIdentityOutput>,
    pub definitions: Vec<DefinitionAtPositionDefinitionOutput>,
    pub usages: Vec<UsageAtPositionUsageOutput>,
    pub skipped_categories: Vec<UsageAtPositionSkippedOutput>,
    pub completeness: SymbolQueryCompleteness,
    pub diagnostics: Vec<JavaAnalysisDiagnosticOutput>,
    pub recovery_actions: Vec<DefinitionRecoveryActionOutput>,
    #[facet(default, skip_serializing_if = Option::is_none)]
    pub dependency_index: Option<DependencySymbolIndexQueryOutput>,
    #[facet(default, skip_serializing_if = Option::is_none)]
    pub result_limit: Option<UsageAtPositionLimitOutput>,
}

impl UsageAtPositionResult {
    #[must_use]
    pub fn from_definition(result: DefinitionAtPositionResult) -> Self {
        Self {
            schema: USAGE_AT_POSITION_RESULT_SCHEMA.to_owned(),
            request_id: result.request_id,
            request_generation: result.request_generation,
            workspace_generation: result.workspace_generation,
            outcome: result.outcome.into(),
            context: result.context,
            document: result.document,
            position: result.position,
            targets: result.symbols,
            definitions: result.definitions,
            usages: Vec::new(),
            skipped_categories: default_skipped_categories(),
            completeness: result.completeness,
            diagnostics: result.diagnostics,
            recovery_actions: result.recovery_actions,
            dependency_index: result.dependency_index,
            result_limit: None,
        }
    }

    #[must_use]
    pub fn status(&self) -> u8 {
        match self.outcome {
            UsageAtPositionOutcome::InvalidRequest
            | UsageAtPositionOutcome::StaleDocument
            | UsageAtPositionOutcome::Unavailable => self.outcome.exit_code(),
            _ if self.completeness == SymbolQueryCompleteness::Incomplete => 5,
            _ => self.outcome.exit_code(),
        }
    }

    #[must_use]
    pub fn with_dependency_index(mut self, index: DependencySymbolIndexQueryOutput) -> Self {
        let definition = DefinitionAtPositionResult {
            schema: super::DEFINITION_AT_POSITION_RESULT_SCHEMA.to_owned(),
            request_id: self.request_id,
            request_generation: self.request_generation,
            workspace_generation: self.workspace_generation,
            outcome: definition_outcome(self.outcome),
            context: self.context.clone(),
            document: self.document.clone(),
            position: self.position,
            symbols: self.targets.clone(),
            definitions: self.definitions.clone(),
            completeness: self.completeness,
            diagnostics: self.diagnostics.clone(),
            recovery_actions: self.recovery_actions.clone(),
            dependency_index: self.dependency_index.clone(),
        }
        .with_dependency_index(index);
        self.completeness = definition.completeness;
        self.recovery_actions = definition.recovery_actions;
        self.dependency_index = definition.dependency_index;
        self
    }

    #[must_use]
    pub fn to_csv(&self) -> String {
        let mut output = UsageAtPositionCsvRow::header();
        let report = UsageAtPositionCsvRow::report(self);
        append_csv_row(&mut output, &report);
        for definition in &self.definitions {
            let mut row = report.clone();
            "definition".clone_into(&mut row.record_kind);
            row.set_symbol(&definition.symbol, definition.confidence);
            row.set_span(&definition.identifier_span);
            append_csv_row(&mut output, &row);
        }
        for usage in &self.usages {
            let mut row = report.clone();
            "usage".clone_into(&mut row.record_kind);
            row.set_symbol(&usage.target, usage.confidence);
            usage_kind_name(usage.kind).clone_into(&mut row.usage_kind);
            row.set_span(&usage.span);
            append_csv_row(&mut output, &row);
        }
        for skipped in &self.skipped_categories {
            let mut row = report.clone();
            "skipped-category".clone_into(&mut row.record_kind);
            skipped_category_name(skipped.category).clone_into(&mut row.skipped_category);
            row.message.clone_from(&skipped.reason);
            append_csv_row(&mut output, &row);
        }
        for diagnostic in &self.diagnostics {
            let mut row = report.clone();
            "diagnostic".clone_into(&mut row.record_kind);
            row.diagnostic_code.clone_from(&diagnostic.code);
            row.message.clone_from(&diagnostic.message);
            append_csv_row(&mut output, &row);
        }
        output
    }

    pub(crate) fn refresh_index_fingerprint(&mut self) {
        let mut hasher = blake3::Hasher::new();
        hasher.update(b"sfm-usage-at-position/1\0");
        hash_text(&mut hasher, &self.context.index_fingerprint);
        for target in &self.targets {
            hash_symbol(&mut hasher, target);
        }
        for usage in &self.usages {
            hash_symbol(&mut hasher, &usage.target);
            hasher.update(&[usage.kind as u8, usage.confidence as u8]);
            hash_definition_span(&mut hasher, &usage.span);
        }
        for diagnostic in &self.diagnostics {
            hash_text(&mut hasher, &diagnostic.code);
            hasher.update(&[diagnostic.severity as u8]);
            hash_text(&mut hasher, &diagnostic.message);
            if let Some(span) = &diagnostic.span {
                hasher.update(&[1]);
                hash_report_span(&mut hasher, span);
            } else {
                hasher.update(&[0]);
            }
        }
        self.context.index_fingerprint = format!("blake3:{}", hasher.finalize().to_hex());
    }

    /// Apply both the deterministic row bound and encoded-result budget before
    /// the result reaches the worker frame serializer.
    ///
    /// # Errors
    ///
    /// Returns an error only when the non-usage result envelope alone exceeds
    /// the configured encoded-size budget.
    pub(crate) fn apply_result_limits(
        &mut self,
        count_limit: usize,
        encoded_size_limit: usize,
        mut discarded_candidate_count: u64,
    ) -> eyre::Result<()> {
        self.usages.sort();
        self.usages.dedup();
        let count_truncated = self.usages.len() > count_limit || discarded_candidate_count > 0;
        if self.usages.len() > count_limit {
            let discarded = self.usages.len() - count_limit;
            discarded_candidate_count = discarded_candidate_count
                .saturating_add(u64::try_from(discarded).unwrap_or(u64::MAX));
            self.usages.truncate(count_limit);
        }

        if !count_truncated && encoded_result_len(self)? <= encoded_size_limit {
            return Ok(());
        }

        let count_bounded = self.usages.clone();
        self.set_limit_reporting(
            count_limit,
            encoded_size_limit,
            discarded_candidate_count,
            count_truncated,
            false,
        );
        if encoded_result_len(self)? <= encoded_size_limit {
            return Ok(());
        }

        let mut low = 0_usize;
        let mut high = count_bounded.len();
        while low < high {
            let middle = low + (high - low).div_ceil(2);
            self.usages = count_bounded[..middle].to_vec();
            let discarded_for_size = discarded_candidate_count
                .saturating_add(u64::try_from(count_bounded.len() - middle).unwrap_or(u64::MAX));
            self.set_limit_reporting(
                count_limit,
                encoded_size_limit,
                discarded_for_size,
                count_truncated,
                true,
            );
            if encoded_result_len(self)? <= encoded_size_limit {
                low = middle;
            } else {
                high = middle - 1;
            }
        }

        self.usages = count_bounded[..low].to_vec();
        let discarded_for_size = discarded_candidate_count
            .saturating_add(u64::try_from(count_bounded.len() - low).unwrap_or(u64::MAX));
        self.set_limit_reporting(
            count_limit,
            encoded_size_limit,
            discarded_for_size,
            count_truncated,
            true,
        );
        let encoded = encoded_result_len(self)?;
        if encoded > encoded_size_limit {
            eyre::bail!(
                "usage-at-position result envelope is {encoded} bytes with no usage rows, exceeding the {encoded_size_limit}-byte safety budget"
            );
        }
        Ok(())
    }

    fn set_limit_reporting(
        &mut self,
        count_limit: usize,
        encoded_size_limit: usize,
        discarded_candidate_count: u64,
        count_truncated: bool,
        encoded_size_truncated: bool,
    ) {
        self.completeness = SymbolQueryCompleteness::Incomplete;
        self.diagnostics
            .retain(|diagnostic| diagnostic.code != "java.usage-result-truncated");
        let returned_count = u64::try_from(self.usages.len()).unwrap_or(u64::MAX);
        self.result_limit = Some(UsageAtPositionLimitOutput {
            count_limit: u64::try_from(count_limit).unwrap_or(u64::MAX),
            encoded_size_limit: u64::try_from(encoded_size_limit).unwrap_or(u64::MAX),
            returned_count,
            discarded_candidate_count,
            count_truncated,
            encoded_size_truncated,
        });
        self.diagnostics.push(JavaAnalysisDiagnosticOutput {
            code: "java.usage-result-truncated".to_owned(),
            severity: super::DiagnosticSeverity::Warning,
            message: format!(
                "Usage results were bounded before serialization: returned {returned_count}, discarded at least {discarded_candidate_count} candidates (count limit {count_limit}, encoded-size limit {encoded_size_limit} bytes)."
            ),
            span: None,
        });
        self.diagnostics.sort();
        self.diagnostics.dedup();
    }
}

fn hash_text(hasher: &mut blake3::Hasher, value: &str) {
    hasher.update(&u64::try_from(value.len()).unwrap_or(u64::MAX).to_le_bytes());
    hasher.update(value.as_bytes());
}

fn hash_optional_text(hasher: &mut blake3::Hasher, value: Option<&str>) {
    if let Some(value) = value {
        hasher.update(&[1]);
        hash_text(hasher, value);
    } else {
        hasher.update(&[0]);
    }
}

fn hash_symbol(hasher: &mut blake3::Hasher, symbol: &JavaSymbolIdentityOutput) {
    hasher.update(&[symbol.kind as u8]);
    hash_text(hasher, &symbol.owner);
    hash_text(hasher, &symbol.name);
    hash_optional_text(hasher, symbol.descriptor.as_deref());
    hash_text(hasher, &symbol.qualified_name);
}

fn hash_definition_span(hasher: &mut blake3::Hasher, span: &DefinitionSourceSpanOutput) {
    hash_text(hasher, &span.address);
    hash_text(hasher, &span.resolver_id);
    hash_text(hasher, &span.root_id);
    hash_text(hasher, &span.root_relative_path);
    hash_text(hasher, &span.report_path);
    hash_text(hasher, &span.source_set);
    hash_text(hasher, &span.source_hash);
    hash_optional_text(hasher, span.source_sha256.as_deref());
    for value in [
        span.start_byte,
        span.end_byte,
        span.start_line,
        span.start_column,
        span.end_line,
        span.end_column,
    ] {
        hasher.update(&value.to_le_bytes());
    }
}

fn hash_report_span(hasher: &mut blake3::Hasher, span: &JavaSourceSpanOutput) {
    hash_text(hasher, &span.path);
    hash_text(hasher, &span.source_set);
    hash_text(hasher, &span.source_hash);
    for value in [
        span.start_byte,
        span.end_byte,
        span.start_line,
        span.start_column,
        span.end_line,
        span.end_column,
    ] {
        hasher.update(&value.to_le_bytes());
    }
}

fn encoded_result_len(result: &UsageAtPositionResult) -> eyre::Result<usize> {
    facet_json::to_string(result)
        .map(|encoded| encoded.len())
        .map_err(|error| eyre::eyre!("failed to encode bounded usage-at-position result: {error}"))
}

fn default_skipped_categories() -> Vec<UsageAtPositionSkippedOutput> {
    [
        (
            UsageAtPositionSkippedCategory::ReadWriteClassification,
            "References are located, but read/write roles are not yet proven for every Java expression.",
        ),
        (
            UsageAtPositionSkippedCategory::OverrideImplementation,
            "Override and implementation relations require an inheritance-aware semantic pass.",
        ),
        (
            UsageAtPositionSkippedCategory::Javadoc,
            "Javadoc references are not promoted to semantic usages by the Arborium syntax pass.",
        ),
        (
            UsageAtPositionSkippedCategory::StringLiteral,
            "String-literal spelling is intentionally not treated as a symbol reference.",
        ),
        (
            UsageAtPositionSkippedCategory::Reflection,
            "Reflection-like references require API-specific evidence and are not guessed.",
        ),
        (
            UsageAtPositionSkippedCategory::DynamicDispatch,
            "Targets that cannot be selected statically remain uncertain instead of choosing a first match.",
        ),
    ]
    .into_iter()
    .map(|(category, reason)| UsageAtPositionSkippedOutput {
        category,
        reason: reason.to_owned(),
    })
    .collect()
}

const fn definition_outcome(outcome: UsageAtPositionOutcome) -> DefinitionAtPositionOutcome {
    match outcome {
        UsageAtPositionOutcome::Success => DefinitionAtPositionOutcome::Success,
        UsageAtPositionOutcome::NoSymbol => DefinitionAtPositionOutcome::NoSymbol,
        UsageAtPositionOutcome::NoDefinition => DefinitionAtPositionOutcome::NoDefinition,
        UsageAtPositionOutcome::Ambiguous => DefinitionAtPositionOutcome::Ambiguous,
        UsageAtPositionOutcome::StaleDocument => DefinitionAtPositionOutcome::StaleDocument,
        UsageAtPositionOutcome::InvalidRequest => DefinitionAtPositionOutcome::InvalidRequest,
        UsageAtPositionOutcome::Unavailable => DefinitionAtPositionOutcome::Unavailable,
    }
}

#[derive(Facet, Clone)]
struct UsageAtPositionCsvRow {
    schema: String,
    record_kind: String,
    outcome: String,
    status: u8,
    completeness: String,
    request_id: u64,
    request_generation: u64,
    workspace_generation: u64,
    document_address: String,
    position_line: u64,
    position_column: u64,
    symbol_kind: String,
    symbol_qualified_name: String,
    symbol_descriptor: String,
    usage_kind: String,
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
    skipped_category: String,
    diagnostic_code: String,
    message: String,
}

impl UsageAtPositionCsvRow {
    fn report(result: &UsageAtPositionResult) -> Self {
        Self {
            schema: result.schema.clone(),
            record_kind: "report".to_owned(),
            outcome: outcome_name(result.outcome).to_owned(),
            status: result.status(),
            completeness: completeness_name(result.completeness).to_owned(),
            request_id: result.request_id,
            request_generation: result.request_generation,
            workspace_generation: result.workspace_generation,
            document_address: result.document.address.clone(),
            position_line: result.position.line,
            position_column: result.position.column,
            symbol_kind: String::new(),
            symbol_qualified_name: String::new(),
            symbol_descriptor: String::new(),
            usage_kind: String::new(),
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
            skipped_category: String::new(),
            diagnostic_code: String::new(),
            message: String::new(),
        }
    }

    fn header() -> String {
        concat!(
            "schema,record_kind,outcome,status,completeness,request_id,request_generation,",
            "workspace_generation,document_address,position_line,position_column,symbol_kind,",
            "symbol_qualified_name,symbol_descriptor,usage_kind,confidence,resolver_id,root_id,",
            "root_relative_path,address,report_path,source_set,source_hash,start_byte,end_byte,",
            "start_line,start_column,end_line,end_column,skipped_category,diagnostic_code,message\n"
        )
        .to_owned()
    }

    fn set_symbol(&mut self, symbol: &JavaSymbolIdentityOutput, confidence: ResolutionConfidence) {
        symbol_kind_name(symbol.kind).clone_into(&mut self.symbol_kind);
        self.symbol_qualified_name
            .clone_from(&symbol.qualified_name);
        self.symbol_descriptor = symbol.descriptor.clone().unwrap_or_default();
        confidence_name(confidence).clone_into(&mut self.confidence);
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

fn append_csv_row(output: &mut String, row: &UsageAtPositionCsvRow) {
    output.push_str(
        &facet_csv::to_string(row)
            .expect("the flat usage-at-position CSV row must remain serializable"),
    );
}

const fn outcome_name(outcome: UsageAtPositionOutcome) -> &'static str {
    match outcome {
        UsageAtPositionOutcome::Success => "success",
        UsageAtPositionOutcome::NoSymbol => "no-symbol",
        UsageAtPositionOutcome::NoDefinition => "no-definition",
        UsageAtPositionOutcome::Ambiguous => "ambiguous",
        UsageAtPositionOutcome::StaleDocument => "stale-document",
        UsageAtPositionOutcome::InvalidRequest => "invalid-request",
        UsageAtPositionOutcome::Unavailable => "unavailable",
    }
}

const fn completeness_name(value: SymbolQueryCompleteness) -> &'static str {
    match value {
        SymbolQueryCompleteness::Complete => "complete",
        SymbolQueryCompleteness::Incomplete => "incomplete",
    }
}

const fn usage_kind_name(kind: JavaUsageKind) -> &'static str {
    match kind {
        JavaUsageKind::Declaration => "declaration",
        JavaUsageKind::Import => "import",
        JavaUsageKind::TypeReference => "type-reference",
        JavaUsageKind::FieldReference => "field-reference",
        JavaUsageKind::Invocation => "invocation",
        JavaUsageKind::MethodReference => "method-reference",
        JavaUsageKind::LocalReference => "local-reference",
    }
}

const fn symbol_kind_name(kind: JavaSymbolKind) -> &'static str {
    match kind {
        JavaSymbolKind::Class => "class",
        JavaSymbolKind::Interface => "interface",
        JavaSymbolKind::Enum => "enum",
        JavaSymbolKind::Record => "record",
        JavaSymbolKind::Annotation => "annotation",
        JavaSymbolKind::Field => "field",
        JavaSymbolKind::Method => "method",
        JavaSymbolKind::Constructor => "constructor",
        JavaSymbolKind::LocalVariable => "local-variable",
        JavaSymbolKind::Parameter => "parameter",
    }
}

const fn confidence_name(confidence: ResolutionConfidence) -> &'static str {
    match confidence {
        ResolutionConfidence::Resolved => "resolved",
        ResolutionConfidence::PartiallyResolved => "partially-resolved",
        ResolutionConfidence::Unresolved => "unresolved",
    }
}

const fn skipped_category_name(category: UsageAtPositionSkippedCategory) -> &'static str {
    match category {
        UsageAtPositionSkippedCategory::ReadWriteClassification => "read-write-classification",
        UsageAtPositionSkippedCategory::OverrideImplementation => "override-implementation",
        UsageAtPositionSkippedCategory::Javadoc => "javadoc",
        UsageAtPositionSkippedCategory::StringLiteral => "string-literal",
        UsageAtPositionSkippedCategory::Reflection => "reflection",
        UsageAtPositionSkippedCategory::DynamicDispatch => "dynamic-dispatch",
    }
}

#[cfg(test)]
mod tests {
    use super::super::JavaClasspathMode;
    use super::super::JavaSourceRootKind;
    use super::super::JavaSourceRootOutput;
    use super::*;
    use crate::java_analysis::DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES;
    use crate::java_analysis::SYMBOL_SERVER_USAGE_AT_POSITION_SCHEMA;
    use crate::java_analysis::SymbolServerFrame;
    use crate::java_analysis::encode_symbol_server_frame;

    fn request() -> UsageAtPositionRequest {
        let text = "class A {}\n";
        UsageAtPositionRequest::new(
            7,
            3,
            DefinitionWorkspaceIdentityInput {
                branch: "1.19.2".to_owned(),
                classpath_mode: JavaClasspathMode::Isolated,
                source_roots: vec![JavaSourceRootOutput {
                    id: "source".to_owned(),
                    source_set: "scenario".to_owned(),
                    path: "source".to_owned(),
                    kind: JavaSourceRootKind::Custom,
                    exists: true,
                }],
                classpath_fingerprint: "blake3:fixture".to_owned(),
                dependency_index_identity: None,
                workspace_fingerprint: format!("blake3:{}", "0".repeat(64)),
                workspace_generation: 2,
            },
            DefinitionDocumentInput {
                address: "workspace://source/A.java".to_owned(),
                root_id: "source".to_owned(),
                root_relative_path: "A.java".to_owned(),
                report_path: "source/A.java".to_owned(),
                source_set: "scenario".to_owned(),
                text: text.to_owned(),
                content_hash: super::super::blake3_content_hash(text),
                disk_content_hash: None,
            },
            DefinitionTextPositionInput::from_line_column(text, 1, 7).expect("position"),
        )
    }

    #[test]
    fn request_round_trips_and_validates() {
        let request = request();
        request.validate().expect("request validates");
        let encoded = facet_json::to_string(&request).expect("request JSON");
        let decoded: UsageAtPositionRequest =
            facet_json::from_str(&encoded).expect("request round trip");
        assert_eq!(decoded, request);
    }

    #[test]
    fn result_preserves_definition_failure_and_lists_skipped_categories() {
        let request = request();
        let definition = DefinitionAtPositionResult::invalid_request(
            &request.as_definition_request(),
            JavaAnalysisContextOutput {
                branch: "1.19.2".to_owned(),
                minecraft_version: "1.19.2".to_owned(),
                java_release: "17".to_owned(),
                jdk: "fixture".to_owned(),
                source_roots: request.workspace.source_roots.clone(),
                source_sets: Vec::new(),
                source_exclusions: Vec::new(),
                classpath_mode: JavaClasspathMode::Isolated,
                classpath_fingerprint: "blake3:fixture".to_owned(),
                parser_fingerprint: "fixture".to_owned(),
                index_fingerprint: "blake3:fixture".to_owned(),
            },
            "fixture failure",
        );
        let result = UsageAtPositionResult::from_definition(definition);
        assert_eq!(result.outcome, UsageAtPositionOutcome::InvalidRequest);
        assert!(!result.skipped_categories.is_empty());
        assert!(result.to_csv().contains("skipped-category"));
        assert!(result.diagnostics.iter().any(|diagnostic| {
            diagnostic.severity == super::super::DiagnosticSeverity::Error
                && diagnostic.message == "fixture failure"
        }));
    }

    #[test]
    fn result_limits_preserve_sorted_prefix_and_report_count_truncation() {
        let mut result = successful_result();
        result.usages = (0..10_000).rev().map(synthetic_usage).collect();

        result
            .apply_result_limits(128, USAGE_AT_POSITION_MAX_ENCODED_BYTES, 0)
            .expect("bounded result");

        assert_eq!(result.usages.len(), 128);
        assert!(result.usages.windows(2).all(|pair| pair[0] < pair[1]));
        let limit = result.result_limit.as_ref().expect("limit metadata");
        assert!(limit.count_truncated);
        assert!(!limit.encoded_size_truncated);
        assert_eq!(limit.returned_count, 128);
        assert_eq!(limit.discarded_candidate_count, 9_872);
        assert_eq!(result.completeness, SymbolQueryCompleteness::Incomplete);
        assert!(
            result
                .diagnostics
                .iter()
                .any(|diagnostic| diagnostic.code == "java.usage-result-truncated")
        );
    }

    #[test]
    fn adversarial_usage_payload_is_bounded_below_worker_frame_limit() {
        let mut result = successful_result();
        result.usages = (0..5_000)
            .map(|index| {
                let mut usage = synthetic_usage(index);
                usage.span.root_relative_path.push_str(&"x".repeat(16_384));
                usage.span.address.push_str(&"y".repeat(16_384));
                usage
            })
            .collect();

        result
            .apply_result_limits(
                USAGE_AT_POSITION_MAX_RESULTS,
                USAGE_AT_POSITION_MAX_ENCODED_BYTES,
                0,
            )
            .expect("adversarial result is bounded");
        let limit = result.result_limit.as_ref().expect("limit metadata");
        assert!(limit.count_truncated);
        assert!(limit.encoded_size_truncated);
        assert!(limit.discarded_candidate_count > 0);

        let frame = SymbolServerFrame::UsageAtPositionResult {
            schema: SYMBOL_SERVER_USAGE_AT_POSITION_SCHEMA.to_owned(),
            result: Box::new(result),
        };
        let encoded = encode_symbol_server_frame(&frame, DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES)
            .expect("bounded result fits worker frame");
        assert!(encoded.len() < DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES);
    }

    fn successful_result() -> UsageAtPositionResult {
        let request = request();
        let definition = DefinitionAtPositionResult::invalid_request(
            &request.as_definition_request(),
            JavaAnalysisContextOutput {
                branch: "1.19.2".to_owned(),
                minecraft_version: "1.19.2".to_owned(),
                java_release: "17".to_owned(),
                jdk: "fixture".to_owned(),
                source_roots: request.workspace.source_roots.clone(),
                source_sets: Vec::new(),
                source_exclusions: Vec::new(),
                classpath_mode: JavaClasspathMode::Isolated,
                classpath_fingerprint: "blake3:fixture".to_owned(),
                parser_fingerprint: "fixture".to_owned(),
                index_fingerprint: "blake3:fixture".to_owned(),
            },
            "fixture",
        );
        let mut result = UsageAtPositionResult::from_definition(definition);
        result.outcome = UsageAtPositionOutcome::Success;
        result.completeness = SymbolQueryCompleteness::Complete;
        result.diagnostics.clear();
        result.recovery_actions.clear();
        result
    }

    fn synthetic_usage(index: usize) -> UsageAtPositionUsageOutput {
        let byte = u64::try_from(index).expect("fixture index");
        UsageAtPositionUsageOutput {
            target: JavaSymbolIdentityOutput {
                kind: JavaSymbolKind::Class,
                owner: "example".to_owned(),
                name: "Target".to_owned(),
                descriptor: None,
                qualified_name: "example.Target".to_owned(),
            },
            kind: JavaUsageKind::TypeReference,
            span: DefinitionSourceSpanOutput {
                address: format!("workspace://source/example/Use{index:05}.java"),
                resolver_id: "workspace".to_owned(),
                root_id: "source".to_owned(),
                root_relative_path: format!("example/Use{index:05}.java"),
                report_path: format!("source/example/Use{index:05}.java"),
                source_set: "scenario".to_owned(),
                source_hash: format!("blake3:{index:064x}"),
                source_sha256: None,
                start_byte: byte,
                end_byte: byte.saturating_add(1),
                start_line: byte.saturating_add(1),
                start_column: 1,
                end_line: byte.saturating_add(1),
                end_column: 2,
            },
            confidence: ResolutionConfidence::Resolved,
        }
    }
}
