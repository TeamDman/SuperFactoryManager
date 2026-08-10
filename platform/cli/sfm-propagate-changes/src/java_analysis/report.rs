use facet::Facet;

pub const SYMBOL_DEFINITION_SCHEMA: &str = "sfm.symbol-definition/2";
pub const SYMBOL_LIST_SCHEMA: &str = "sfm.symbol-list/1";
pub const SYMBOL_USAGE_LIST_SCHEMA: &str = "sfm.symbol-usage-list/2";
pub const SYMBOL_MUTATION_SCHEMA: &str = "sfm.symbol-mutation/1";

#[derive(Facet, Clone, Copy, Debug, Default, PartialEq, Eq, PartialOrd, Ord)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum JavaClasspathMode {
    #[default]
    Branch,
    Isolated,
}

#[derive(Facet, Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum JavaSourceRootKind {
    Declared,
    Generated,
    Custom,
}

#[derive(Facet, Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum JavaSymbolKind {
    Class,
    Interface,
    Enum,
    Record,
    Annotation,
    Field,
    Method,
    Constructor,
}

#[derive(Facet, Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum JavaUsageKind {
    Declaration,
    Import,
    TypeReference,
    FieldReference,
    Invocation,
    MethodReference,
}

#[derive(Facet, Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum ResolutionConfidence {
    Resolved,
    PartiallyResolved,
    Unresolved,
}

#[derive(Facet, Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum SymbolCommandOutcome {
    Success,
    NoMatch,
    Ambiguous,
    Unsupported,
}

#[derive(Facet, Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum SymbolQueryCompleteness {
    Complete,
    Incomplete,
}

#[derive(Facet, Clone, Debug, PartialEq, Eq)]
pub struct DependencySymbolIndexQueryOutput {
    pub status: super::DependencySymbolIndexProbeStatus,
    pub completeness: SymbolQueryCompleteness,
    pub expected_identity: String,
    pub portable_path: String,
    pub path: String,
    pub reason: String,
    pub refresh_command: String,
    pub acquisition_commands: Vec<String>,
}

impl SymbolCommandOutcome {
    #[must_use]
    pub const fn exit_code(self) -> u8 {
        match self {
            Self::Success => 0,
            Self::NoMatch => 2,
            Self::Ambiguous => 3,
            Self::Unsupported => 4,
        }
    }
}

#[derive(Facet, Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum DiagnosticSeverity {
    Info,
    Warning,
    Error,
}

#[derive(Facet, Clone, Debug, PartialEq, Eq)]
pub struct JavaSourceRootOutput {
    pub id: String,
    pub source_set: String,
    pub path: String,
    pub kind: JavaSourceRootKind,
    pub exists: bool,
}

#[derive(Facet, Clone, Debug, PartialEq, Eq)]
pub struct JavaSourceSetOutput {
    pub id: String,
    pub visible_source_sets: Vec<String>,
}

#[derive(Facet, Clone, Debug, PartialEq, Eq, PartialOrd, Ord)]
pub struct JavaSourceExclusionOutput {
    pub source_set: String,
    pub path: String,
    pub origin: String,
}

#[derive(Facet, Clone, Debug, PartialEq, Eq)]
pub struct JavaAnalysisContextOutput {
    pub branch: String,
    pub minecraft_version: String,
    pub java_release: String,
    pub jdk: String,
    pub source_roots: Vec<JavaSourceRootOutput>,
    pub source_sets: Vec<JavaSourceSetOutput>,
    pub source_exclusions: Vec<JavaSourceExclusionOutput>,
    pub classpath_mode: JavaClasspathMode,
    pub classpath_fingerprint: String,
    pub parser_fingerprint: String,
    pub index_fingerprint: String,
}

#[derive(Facet, Clone, Debug, PartialEq, Eq)]
pub struct JavaSymbolSelectorOutput {
    pub canonical: String,
    pub owner: String,
    pub member: Option<String>,
    pub descriptor: Option<String>,
    pub kind: JavaSymbolSelectorKind,
}

#[derive(Facet, Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum JavaSymbolSelectorKind {
    Type,
    Field,
    Method,
}

#[derive(Facet, Clone, Debug, PartialEq, Eq, PartialOrd, Ord)]
pub struct JavaSourceSpanOutput {
    pub path: String,
    pub source_set: String,
    pub source_hash: String,
    pub start_byte: u64,
    pub end_byte: u64,
    pub start_line: u64,
    pub start_column: u64,
    pub end_line: u64,
    pub end_column: u64,
}

#[derive(Facet, Clone, Debug, PartialEq, Eq, PartialOrd, Ord)]
pub struct JavaSymbolIdentityOutput {
    pub kind: JavaSymbolKind,
    pub owner: String,
    pub name: String,
    pub descriptor: Option<String>,
    pub qualified_name: String,
}

impl JavaSymbolIdentityOutput {
    /// Return the exact Access Transformer style selector accepted by the
    /// definition, usage, and mutation commands.
    #[must_use]
    pub fn canonical_selector(&self) -> String {
        match self.kind {
            JavaSymbolKind::Class
            | JavaSymbolKind::Interface
            | JavaSymbolKind::Enum
            | JavaSymbolKind::Record
            | JavaSymbolKind::Annotation => self.qualified_name.clone(),
            JavaSymbolKind::Field => format!("{} {}", self.owner, self.name),
            JavaSymbolKind::Method | JavaSymbolKind::Constructor => format!(
                "{} {}{}",
                self.owner,
                self.name,
                self.descriptor.as_deref().unwrap_or_default()
            ),
        }
    }
}

#[derive(Facet, Clone, Debug, PartialEq, Eq, PartialOrd, Ord)]
pub struct JavaSymbolDefinitionOutput {
    pub symbol: JavaSymbolIdentityOutput,
    pub identifier_span: JavaSourceSpanOutput,
    pub declaration_span: JavaSourceSpanOutput,
    pub confidence: ResolutionConfidence,
}

#[derive(Facet, Clone, Debug, PartialEq, Eq, PartialOrd, Ord)]
pub struct JavaSymbolUsageOutput {
    pub target: JavaSymbolIdentityOutput,
    pub kind: JavaUsageKind,
    pub span: JavaSourceSpanOutput,
    pub confidence: ResolutionConfidence,
}

#[derive(Facet, Clone, Debug, PartialEq, Eq, PartialOrd, Ord)]
pub struct JavaAnalysisDiagnosticOutput {
    pub code: String,
    pub severity: DiagnosticSeverity,
    pub message: String,
    pub span: Option<JavaSourceSpanOutput>,
}

#[derive(Facet, Clone, Debug, PartialEq, Eq)]
pub struct SymbolDefinitionOutput {
    pub schema: String,
    pub outcome: SymbolCommandOutcome,
    pub context: JavaAnalysisContextOutput,
    pub selector: JavaSymbolSelectorOutput,
    pub definitions: Vec<JavaSymbolDefinitionOutput>,
    pub diagnostics: Vec<JavaAnalysisDiagnosticOutput>,
    #[facet(default, skip_serializing_if = Option::is_none)]
    pub dependency_index: Option<DependencySymbolIndexQueryOutput>,
}

impl SymbolDefinitionOutput {
    #[must_use]
    pub fn new(
        outcome: SymbolCommandOutcome,
        context: JavaAnalysisContextOutput,
        selector: JavaSymbolSelectorOutput,
        definitions: Vec<JavaSymbolDefinitionOutput>,
        diagnostics: Vec<JavaAnalysisDiagnosticOutput>,
    ) -> Self {
        Self {
            schema: SYMBOL_DEFINITION_SCHEMA.to_owned(),
            outcome,
            context,
            selector,
            definitions,
            diagnostics,
            dependency_index: None,
        }
    }

    #[must_use]
    pub fn with_dependency_index(mut self, index: DependencySymbolIndexQueryOutput) -> Self {
        self.dependency_index = Some(index);
        self
    }

    #[must_use]
    pub fn status(&self) -> u8 {
        query_status(self.outcome, self.dependency_index.as_ref())
    }

    #[must_use]
    pub fn to_csv(&self) -> String {
        csv_for_definitions(self)
    }
}

#[derive(Facet, Clone, Debug, PartialEq, Eq)]
pub struct SymbolListOutput {
    pub schema: String,
    pub outcome: SymbolCommandOutcome,
    pub context: JavaAnalysisContextOutput,
    pub pattern: String,
    pub definitions: Vec<JavaSymbolDefinitionOutput>,
    pub diagnostics: Vec<JavaAnalysisDiagnosticOutput>,
    #[facet(default, skip_serializing_if = Option::is_none)]
    pub dependency_index: Option<DependencySymbolIndexQueryOutput>,
}

impl SymbolListOutput {
    #[must_use]
    pub fn new(
        outcome: SymbolCommandOutcome,
        context: JavaAnalysisContextOutput,
        pattern: String,
        definitions: Vec<JavaSymbolDefinitionOutput>,
        diagnostics: Vec<JavaAnalysisDiagnosticOutput>,
    ) -> Self {
        Self {
            schema: SYMBOL_LIST_SCHEMA.to_owned(),
            outcome,
            context,
            pattern,
            definitions,
            diagnostics,
            dependency_index: None,
        }
    }

    #[must_use]
    pub fn with_dependency_index(mut self, index: DependencySymbolIndexQueryOutput) -> Self {
        self.dependency_index = Some(index);
        self
    }

    #[must_use]
    pub fn status(&self) -> u8 {
        query_status(self.outcome, self.dependency_index.as_ref())
    }

    #[must_use]
    pub fn to_csv(&self) -> String {
        csv_for_symbol_list(self)
    }
}

#[derive(Facet, Clone, Debug, PartialEq, Eq)]
pub struct SymbolUsageListOutput {
    pub schema: String,
    pub outcome: SymbolCommandOutcome,
    pub context: JavaAnalysisContextOutput,
    pub selector: JavaSymbolSelectorOutput,
    pub usages: Vec<JavaSymbolUsageOutput>,
    pub diagnostics: Vec<JavaAnalysisDiagnosticOutput>,
    #[facet(default, skip_serializing_if = Option::is_none)]
    pub dependency_index: Option<DependencySymbolIndexQueryOutput>,
}

impl SymbolUsageListOutput {
    #[must_use]
    pub fn new(
        outcome: SymbolCommandOutcome,
        context: JavaAnalysisContextOutput,
        selector: JavaSymbolSelectorOutput,
        usages: Vec<JavaSymbolUsageOutput>,
        diagnostics: Vec<JavaAnalysisDiagnosticOutput>,
    ) -> Self {
        Self {
            schema: SYMBOL_USAGE_LIST_SCHEMA.to_owned(),
            outcome,
            context,
            selector,
            usages,
            diagnostics,
            dependency_index: None,
        }
    }

    #[must_use]
    pub fn with_dependency_index(mut self, index: DependencySymbolIndexQueryOutput) -> Self {
        self.dependency_index = Some(index);
        self
    }

    #[must_use]
    pub fn status(&self) -> u8 {
        query_status(self.outcome, self.dependency_index.as_ref())
    }

    #[must_use]
    pub fn to_csv(&self) -> String {
        csv_for_usages(self)
    }
}

fn query_status(
    outcome: SymbolCommandOutcome,
    dependency_index: Option<&DependencySymbolIndexQueryOutput>,
) -> u8 {
    if dependency_index
        .is_some_and(|index| index.completeness == SymbolQueryCompleteness::Incomplete)
    {
        5
    } else {
        outcome.exit_code()
    }
}

#[derive(Facet, Clone, Debug, PartialEq, Eq)]
pub struct SymbolMutationUnsupportedOutput {
    pub schema: String,
    pub operation: String,
    pub outcome: SymbolCommandOutcome,
    pub status: u8,
    pub selector: String,
    pub destination: String,
    pub message: String,
}

impl SymbolMutationUnsupportedOutput {
    #[must_use]
    pub fn new(operation: &str, selector: &str, destination: &str) -> Self {
        Self {
            schema: SYMBOL_MUTATION_SCHEMA.to_owned(),
            operation: operation.to_owned(),
            outcome: SymbolCommandOutcome::Unsupported,
            status: SymbolCommandOutcome::Unsupported.exit_code(),
            selector: selector.to_owned(),
            destination: destination.to_owned(),
            message: "symbol mutation is intentionally unavailable in Phase 0".to_owned(),
        }
    }
}

fn csv_for_definitions(report: &SymbolDefinitionOutput) -> String {
    let mut report_row = SymbolCsvRow::report(
        &report.schema,
        report.outcome,
        &report.context,
        &report.selector,
    );
    report_row.status = report.status();
    let mut output = csv_header();
    append_csv_row(&mut output, &report_row);
    for definition in &report.definitions {
        let mut row = report_row.clone();
        "definition".clone_into(&mut row.record_kind);
        confidence_name(definition.confidence).clone_into(&mut row.confidence);
        row.set_symbol(&definition.symbol);
        row.set_span(&definition.identifier_span);
        row.set_declaration_span(&definition.declaration_span);
        append_csv_row(&mut output, &row);
    }
    append_diagnostics(&mut output, &report_row, &report.diagnostics);
    output
}

fn csv_for_symbol_list(report: &SymbolListOutput) -> String {
    let selector = JavaSymbolSelectorOutput {
        canonical: report.pattern.clone(),
        owner: String::new(),
        member: None,
        descriptor: None,
        kind: JavaSymbolSelectorKind::Type,
    };
    let mut report_row =
        SymbolCsvRow::report(&report.schema, report.outcome, &report.context, &selector);
    report_row.status = report.status();
    "glob".clone_into(&mut report_row.selector_kind);
    let mut output = csv_header();
    append_csv_row(&mut output, &report_row);
    for definition in &report.definitions {
        let mut row = report_row.clone();
        "definition".clone_into(&mut row.record_kind);
        confidence_name(definition.confidence).clone_into(&mut row.confidence);
        row.set_symbol(&definition.symbol);
        row.set_span(&definition.identifier_span);
        row.set_declaration_span(&definition.declaration_span);
        append_csv_row(&mut output, &row);
    }
    append_diagnostics(&mut output, &report_row, &report.diagnostics);
    output
}

fn csv_for_usages(report: &SymbolUsageListOutput) -> String {
    let mut report_row = SymbolCsvRow::report(
        &report.schema,
        report.outcome,
        &report.context,
        &report.selector,
    );
    report_row.status = report.status();
    let mut output = csv_header();
    append_csv_row(&mut output, &report_row);
    for usage in &report.usages {
        let mut row = report_row.clone();
        "usage".clone_into(&mut row.record_kind);
        confidence_name(usage.confidence).clone_into(&mut row.confidence);
        usage_kind_name(usage.kind).clone_into(&mut row.usage_kind);
        row.set_symbol(&usage.target);
        row.set_span(&usage.span);
        append_csv_row(&mut output, &row);
    }
    append_diagnostics(&mut output, &report_row, &report.diagnostics);
    output
}

fn csv_header() -> String {
    concat!(
        "schema,record_kind,outcome,status,selector,selector_kind,selector_owner,",
        "selector_member,selector_descriptor,confidence,branch,minecraft_version,java_release,",
        "jdk,classpath_mode,classpath_fingerprint,parser_fingerprint,index_fingerprint,",
        "source_roots_json,source_sets_json,source_exclusions_json,symbol_kind,symbol_owner,",
        "symbol_name,symbol_descriptor,symbol_qualified_name,usage_kind,path,source_set,",
        "source_hash,start_byte,end_byte,start_line,start_column,end_line,end_column,",
        "declaration_path,declaration_source_set,declaration_source_hash,",
        "declaration_start_byte,declaration_end_byte,declaration_start_line,",
        "declaration_start_column,declaration_end_line,declaration_end_column,",
        "diagnostic_code,diagnostic_severity,message\n"
    )
    .to_owned()
}

#[derive(Facet, Clone, Debug, PartialEq, Eq)]
struct SymbolCsvRow {
    schema: String,
    record_kind: String,
    outcome: String,
    status: u8,
    selector: String,
    selector_kind: String,
    selector_owner: String,
    selector_member: String,
    selector_descriptor: String,
    confidence: String,
    branch: String,
    minecraft_version: String,
    java_release: String,
    jdk: String,
    classpath_mode: String,
    classpath_fingerprint: String,
    parser_fingerprint: String,
    index_fingerprint: String,
    source_roots_json: String,
    source_sets_json: String,
    source_exclusions_json: String,
    symbol_kind: String,
    symbol_owner: String,
    symbol_name: String,
    symbol_descriptor: String,
    symbol_qualified_name: String,
    usage_kind: String,
    path: String,
    source_set: String,
    source_hash: String,
    start_byte: String,
    end_byte: String,
    start_line: String,
    start_column: String,
    end_line: String,
    end_column: String,
    declaration_path: String,
    declaration_source_set: String,
    declaration_source_hash: String,
    declaration_start_byte: String,
    declaration_end_byte: String,
    declaration_start_line: String,
    declaration_start_column: String,
    declaration_end_line: String,
    declaration_end_column: String,
    diagnostic_code: String,
    diagnostic_severity: String,
    message: String,
}

impl SymbolCsvRow {
    fn report(
        schema: &str,
        outcome: SymbolCommandOutcome,
        context: &JavaAnalysisContextOutput,
        selector: &JavaSymbolSelectorOutput,
    ) -> Self {
        Self {
            schema: schema.to_owned(),
            record_kind: "report".to_owned(),
            outcome: outcome_name(outcome).to_owned(),
            status: outcome.exit_code(),
            selector: selector.canonical.clone(),
            selector_kind: selector_kind_name(selector.kind).to_owned(),
            selector_owner: selector.owner.clone(),
            selector_member: selector.member.clone().unwrap_or_default(),
            selector_descriptor: selector.descriptor.clone().unwrap_or_default(),
            confidence: String::new(),
            branch: context.branch.clone(),
            minecraft_version: context.minecraft_version.clone(),
            java_release: context.java_release.clone(),
            jdk: context.jdk.clone(),
            classpath_mode: classpath_mode_name(context.classpath_mode).to_owned(),
            classpath_fingerprint: context.classpath_fingerprint.clone(),
            parser_fingerprint: context.parser_fingerprint.clone(),
            index_fingerprint: context.index_fingerprint.clone(),
            source_roots_json: compact_json(&context.source_roots),
            source_sets_json: compact_json(&context.source_sets),
            source_exclusions_json: compact_json(&context.source_exclusions),
            symbol_kind: String::new(),
            symbol_owner: String::new(),
            symbol_name: String::new(),
            symbol_descriptor: String::new(),
            symbol_qualified_name: String::new(),
            usage_kind: String::new(),
            path: String::new(),
            source_set: String::new(),
            source_hash: String::new(),
            start_byte: String::new(),
            end_byte: String::new(),
            start_line: String::new(),
            start_column: String::new(),
            end_line: String::new(),
            end_column: String::new(),
            declaration_path: String::new(),
            declaration_source_set: String::new(),
            declaration_source_hash: String::new(),
            declaration_start_byte: String::new(),
            declaration_end_byte: String::new(),
            declaration_start_line: String::new(),
            declaration_start_column: String::new(),
            declaration_end_line: String::new(),
            declaration_end_column: String::new(),
            diagnostic_code: String::new(),
            diagnostic_severity: String::new(),
            message: String::new(),
        }
    }

    fn set_symbol(&mut self, symbol: &JavaSymbolIdentityOutput) {
        symbol_kind_name(symbol.kind).clone_into(&mut self.symbol_kind);
        self.symbol_owner.clone_from(&symbol.owner);
        self.symbol_name.clone_from(&symbol.name);
        self.symbol_descriptor = symbol.descriptor.clone().unwrap_or_default();
        self.symbol_qualified_name
            .clone_from(&symbol.qualified_name);
    }

    fn set_span(&mut self, span: &JavaSourceSpanOutput) {
        self.path.clone_from(&span.path);
        self.source_set.clone_from(&span.source_set);
        self.source_hash.clone_from(&span.source_hash);
        self.start_byte = span.start_byte.to_string();
        self.end_byte = span.end_byte.to_string();
        self.start_line = span.start_line.to_string();
        self.start_column = span.start_column.to_string();
        self.end_line = span.end_line.to_string();
        self.end_column = span.end_column.to_string();
    }

    fn set_declaration_span(&mut self, span: &JavaSourceSpanOutput) {
        self.declaration_path.clone_from(&span.path);
        self.declaration_source_set.clone_from(&span.source_set);
        self.declaration_source_hash.clone_from(&span.source_hash);
        self.declaration_start_byte = span.start_byte.to_string();
        self.declaration_end_byte = span.end_byte.to_string();
        self.declaration_start_line = span.start_line.to_string();
        self.declaration_start_column = span.start_column.to_string();
        self.declaration_end_line = span.end_line.to_string();
        self.declaration_end_column = span.end_column.to_string();
    }
}

fn append_csv_row(output: &mut String, row: &SymbolCsvRow) {
    output.push_str(
        &facet_csv::to_string(row)
            .expect("the flat symbol CSV row schema must remain serializable"),
    );
}

fn append_diagnostics(
    output: &mut String,
    report_row: &SymbolCsvRow,
    diagnostics: &[JavaAnalysisDiagnosticOutput],
) {
    for diagnostic in diagnostics {
        let mut row = report_row.clone();
        "diagnostic".clone_into(&mut row.record_kind);
        row.diagnostic_code.clone_from(&diagnostic.code);
        diagnostic_severity_name(diagnostic.severity).clone_into(&mut row.diagnostic_severity);
        row.message.clone_from(&diagnostic.message);
        if let Some(span) = &diagnostic.span {
            row.set_span(span);
        }
        append_csv_row(output, &row);
    }
}

fn compact_json<T: Facet<'static>>(value: &T) -> String {
    facet_json::to_string(value).expect("report context must remain JSON serializable")
}

const fn outcome_name(outcome: SymbolCommandOutcome) -> &'static str {
    match outcome {
        SymbolCommandOutcome::Success => "success",
        SymbolCommandOutcome::NoMatch => "no-match",
        SymbolCommandOutcome::Ambiguous => "ambiguous",
        SymbolCommandOutcome::Unsupported => "unsupported",
    }
}

const fn classpath_mode_name(mode: JavaClasspathMode) -> &'static str {
    match mode {
        JavaClasspathMode::Branch => "branch",
        JavaClasspathMode::Isolated => "isolated",
    }
}

const fn selector_kind_name(kind: JavaSymbolSelectorKind) -> &'static str {
    match kind {
        JavaSymbolSelectorKind::Type => "type",
        JavaSymbolSelectorKind::Field => "field",
        JavaSymbolSelectorKind::Method => "method",
    }
}

const fn confidence_name(confidence: ResolutionConfidence) -> &'static str {
    match confidence {
        ResolutionConfidence::Resolved => "resolved",
        ResolutionConfidence::PartiallyResolved => "partially-resolved",
        ResolutionConfidence::Unresolved => "unresolved",
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
    }
}

const fn diagnostic_severity_name(severity: DiagnosticSeverity) -> &'static str {
    match severity {
        DiagnosticSeverity::Info => "info",
        DiagnosticSeverity::Warning => "warning",
        DiagnosticSeverity::Error => "error",
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::cli::output::CliOutput;
    use crate::cli::output::OutputFormat;

    fn context() -> JavaAnalysisContextOutput {
        JavaAnalysisContextOutput {
            branch: "1.19.2".to_owned(),
            minecraft_version: "1.19.2".to_owned(),
            java_release: "17".to_owned(),
            jdk: "jdk-17".to_owned(),
            source_roots: vec![JavaSourceRootOutput {
                id: "scenario".to_owned(),
                source_set: "scenario".to_owned(),
                path: "source".to_owned(),
                kind: JavaSourceRootKind::Custom,
                exists: true,
            }],
            source_sets: vec![JavaSourceSetOutput {
                id: "scenario".to_owned(),
                visible_source_sets: vec!["scenario".to_owned()],
            }],
            source_exclusions: Vec::new(),
            classpath_mode: JavaClasspathMode::Isolated,
            classpath_fingerprint: "blake3:classpath".to_owned(),
            parser_fingerprint: "arborium-java/2.18.1".to_owned(),
            index_fingerprint: "blake3:index".to_owned(),
        }
    }

    fn selector() -> JavaSymbolSelectorOutput {
        JavaSymbolSelectorOutput {
            canonical: "example.A".to_owned(),
            owner: "example.A".to_owned(),
            member: None,
            descriptor: None,
            kind: JavaSymbolSelectorKind::Type,
        }
    }

    fn span() -> JavaSourceSpanOutput {
        JavaSourceSpanOutput {
            path: "source/example/A.java".to_owned(),
            source_set: "scenario".to_owned(),
            source_hash: "blake3:source".to_owned(),
            start_byte: 24,
            end_byte: 25,
            start_line: 3,
            start_column: 14,
            end_line: 3,
            end_column: 15,
        }
    }

    fn definition_report() -> SymbolDefinitionOutput {
        let identifier_span = span();
        SymbolDefinitionOutput::new(
            SymbolCommandOutcome::Success,
            context(),
            selector(),
            vec![JavaSymbolDefinitionOutput {
                symbol: JavaSymbolIdentityOutput {
                    kind: JavaSymbolKind::Class,
                    owner: "example".to_owned(),
                    name: "A".to_owned(),
                    descriptor: None,
                    qualified_name: "example.A".to_owned(),
                },
                identifier_span: identifier_span.clone(),
                declaration_span: identifier_span,
                confidence: ResolutionConfidence::Resolved,
            }],
            Vec::new(),
        )
    }

    #[test]
    fn symbol_output_round_trips_versioned_facet_json() {
        let report = definition_report();
        let json = facet_json::to_string_pretty(&report).expect("definition JSON");
        let reparsed: SymbolDefinitionOutput =
            facet_json::from_str(&json).expect("definition JSON round trip");
        assert_eq!(reparsed, report);
        assert!(json.contains(SYMBOL_DEFINITION_SCHEMA));
    }

    #[test]
    fn symbol_output_is_byte_deterministic() {
        let report = definition_report();
        assert_eq!(
            facet_json::to_string_pretty(&report).expect("first JSON"),
            facet_json::to_string_pretty(&report).expect("second JSON")
        );
    }

    #[test]
    fn symbol_output_renders_text_json_and_report_specific_csv() {
        for format in [OutputFormat::Text, OutputFormat::Json, OutputFormat::Csv] {
            let output =
                CliOutput::facet_with_csv(definition_report(), |report| Ok(report.to_csv()));
            let rendered = output
                .render(Some(format), false)
                .expect("symbol rendering")
                .expect("typed output");
            assert!(rendered.contains("example.A"));
        }

        let csv = definition_report().to_csv();
        let rows = csv.lines().collect::<Vec<_>>();
        assert_eq!(rows.len(), 3, "header, report, and definition rows");
        let report_row: SymbolCsvRow = facet_csv::from_str(rows[1]).expect("typed report CSV row");
        let definition_row: SymbolCsvRow =
            facet_csv::from_str(rows[2]).expect("typed definition CSV row");
        assert_eq!(report_row.record_kind, "report");
        assert_eq!(report_row.outcome, "success");
        assert_eq!(report_row.status, 0);
        assert_eq!(report_row.classpath_fingerprint, "blake3:classpath");
        assert_eq!(report_row.index_fingerprint, "blake3:index");
        assert!(report_row.source_roots_json.contains("scenario"));
        assert_eq!(definition_row.confidence, "resolved");
        assert_eq!(definition_row.source_hash, "blake3:source");
        assert_eq!(definition_row.declaration_source_hash, "blake3:source");
        assert_eq!(csv, definition_report().to_csv());
    }

    #[test]
    fn symbol_output_usage_round_trips_and_distinguishes_outcomes() {
        let usage = JavaSymbolUsageOutput {
            target: definition_report().definitions[0].symbol.clone(),
            kind: JavaUsageKind::TypeReference,
            span: span(),
            confidence: ResolutionConfidence::Resolved,
        };
        for outcome in [
            SymbolCommandOutcome::Success,
            SymbolCommandOutcome::NoMatch,
            SymbolCommandOutcome::Ambiguous,
        ] {
            let report = SymbolUsageListOutput::new(
                outcome,
                context(),
                selector(),
                if outcome == SymbolCommandOutcome::Success {
                    vec![usage.clone()]
                } else {
                    Vec::new()
                },
                Vec::new(),
            );
            let json = facet_json::to_string_pretty(&report).expect("usage JSON");
            let reparsed: SymbolUsageListOutput =
                facet_json::from_str(&json).expect("usage JSON round trip");
            assert_eq!(reparsed, report);
            assert!(json.contains(SYMBOL_USAGE_LIST_SCHEMA));
            let csv = report.to_csv();
            assert!(csv.contains("schema,record_kind"));
            let report_row: SymbolCsvRow =
                facet_csv::from_str(csv.lines().nth(1).expect("CSV must contain a report row"))
                    .expect("typed usage report CSV row");
            assert_eq!(report_row.record_kind, "report");
            assert_eq!(report_row.outcome, outcome_name(outcome));
            assert_eq!(report_row.status, outcome.exit_code());
        }

        let no_match = SymbolUsageListOutput::new(
            SymbolCommandOutcome::NoMatch,
            context(),
            selector(),
            Vec::new(),
            Vec::new(),
        )
        .to_csv();
        let ambiguous = SymbolUsageListOutput::new(
            SymbolCommandOutcome::Ambiguous,
            context(),
            selector(),
            Vec::new(),
            Vec::new(),
        )
        .to_csv();
        assert_ne!(no_match, ambiguous);
        assert_eq!(no_match.lines().count(), 2, "empty report still has a row");
        assert_eq!(ambiguous.lines().count(), 2, "empty report still has a row");
    }

    #[test]
    fn mutation_csv_exposes_unsupported_outcome_and_public_status() {
        let report = SymbolMutationUnsupportedOutput::new("rename", "example.A", "RenamedA");
        let csv = CliOutput::facet_with_status(report, 4)
            .render(Some(OutputFormat::Csv), false)
            .expect("mutation CSV rendering")
            .expect("typed mutation output");
        assert!(csv.contains("unsupported,4"));
    }
}
