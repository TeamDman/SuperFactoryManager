use super::DependencySymbolIndexCounts;
use super::DependencySymbolIndexIdentity;
use super::DiagnosticSeverity;
use super::JavaAnalysisDiagnosticOutput;
use super::JavaDependencyResolutionDefinition;
use super::JavaSourceSpanOutput;
use super::JavaSymbolDefinitionOutput;
use super::JavaSymbolIdentityOutput;
use super::JavaSymbolKind;
use super::JavaSymbolUsageOutput;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::io::BufRead;
use std::io::BufReader;
use std::path::Path;

pub const DEPENDENCY_JAVA_SYMBOL_INDEX_BODY_SCHEMA: &str =
    "sfm.dependency-java-symbol-index-body/1";
pub const DEPENDENCY_JAVA_SYMBOL_INDEX_STREAM_SCHEMA: &str =
    "sfm.dependency-java-symbol-index-stream/2";

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub struct DependencyJavaSymbolIndexStreamHeader {
    pub schema: String,
    pub identity: DependencySymbolIndexIdentity,
    pub body_schema: String,
}

impl DependencyJavaSymbolIndexStreamHeader {
    #[must_use]
    pub fn new(identity: DependencySymbolIndexIdentity) -> Self {
        Self {
            schema: DEPENDENCY_JAVA_SYMBOL_INDEX_STREAM_SCHEMA.to_owned(),
            identity,
            body_schema: DEPENDENCY_JAVA_SYMBOL_INDEX_BODY_SCHEMA.to_owned(),
        }
    }

    /// Validate the stream envelope against the identity selected by the
    /// current lockfile projection.
    ///
    /// # Errors
    ///
    /// Returns an error for incompatible schemas or a stale identity.
    pub fn validate(&self, expected: &DependencySymbolIndexIdentity) -> eyre::Result<()> {
        if self.schema != DEPENDENCY_JAVA_SYMBOL_INDEX_STREAM_SCHEMA {
            eyre::bail!("dependency Java symbol stream schema is unsupported");
        }
        if self.body_schema != DEPENDENCY_JAVA_SYMBOL_INDEX_BODY_SCHEMA {
            eyre::bail!("dependency Java symbol body schema is unsupported");
        }
        self.identity.validate()?;
        if self.identity != *expected {
            eyre::bail!("dependency Java symbol stream identity is stale");
        }
        Ok(())
    }
}

/// Portable Java declarations and references persisted in the immutable
/// dependency index. SFM-owned sources are deliberately absent and remain
/// live-indexed for every query.
#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub struct DependencyJavaSymbolIndexBody {
    pub definitions: Vec<JavaSymbolDefinitionOutput>,
    pub usages: Vec<JavaSymbolUsageOutput>,
    pub diagnostics: Vec<JavaAnalysisDiagnosticOutput>,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub struct DependencyJavaSourceOrigin {
    /// Root emitted by `JavaSourceWorkspace`, such as `custom-0`.
    pub report_root: String,
    /// Stable dependency provenance without a machine-local prefix.
    pub portable_prefix: String,
    /// Stable source-set identity used by cross-index visibility checks.
    pub source_set: String,
}

pub(crate) struct DependencyJavaSymbolIndexScan {
    pub(crate) body: DependencyJavaSymbolIndexBody,
    pub(crate) resolution: Vec<JavaDependencyResolutionDefinition>,
}

#[derive(Clone, Copy)]
pub(crate) enum DependencyResolutionSelection<'a> {
    None,
    MatchingVocabulary {
        identifiers: &'a BTreeSet<String>,
        member_accesses: &'a BTreeSet<String>,
    },
    AllTypes,
}

impl DependencyResolutionSelection<'_> {
    fn retains(self, route: RoutedJavaSymbol<'_>) -> bool {
        match self {
            Self::None => false,
            Self::MatchingVocabulary {
                identifiers,
                member_accesses,
            } => route.is_resolution_relevant(identifiers, member_accesses),
            Self::AllTypes => is_type_kind(route.kind),
        }
    }
}

/// Scan a validated dependency payload while retaining only records needed by
/// one query and compact identities whose type/owner and dot-qualified member
/// names occur in live SFM sources.
///
/// # Errors
///
/// Returns an error for a stale/invalid envelope, malformed records, unknown
/// record tags, or manifest-count disagreement.
pub(crate) fn scan_dependency_java_symbol_index<DefinitionFilter, UsageFilter>(
    path: &Path,
    expected_identity: &DependencySymbolIndexIdentity,
    expected_counts: &DependencySymbolIndexCounts,
    resolution_selection: DependencyResolutionSelection<'_>,
    definition_filter: DefinitionFilter,
    usage_filter: UsageFilter,
) -> eyre::Result<DependencyJavaSymbolIndexScan>
where
    DefinitionFilter: Fn(JavaSymbolKind, &str, &str, Option<&str>, &str) -> bool,
    UsageFilter: Fn(JavaSymbolKind, &str, &str, Option<&str>, &str) -> bool,
{
    const DIAGNOSTIC_LIMIT: usize = 256;
    let file = std::fs::File::open(path)?;
    let mut reader = BufReader::new(file);
    let mut line = Vec::new();
    if reader.read_until(b'\n', &mut line)? == 0 {
        eyre::bail!("dependency Java symbol stream is empty");
    }
    trim_line_ending(&mut line);
    let header: DependencyJavaSymbolIndexStreamHeader =
        facet_json::from_str(std::str::from_utf8(&line)?)?;
    header.validate(expected_identity)?;
    let definition_decoder = facet_json::JsonWeavyPlan::<JavaSymbolDefinitionOutput>::build()?;
    let usage_decoder = facet_json::JsonWeavyPlan::<JavaSymbolUsageOutput>::build()?;
    let diagnostic_decoder = facet_json::JsonWeavyPlan::<JavaAnalysisDiagnosticOutput>::build()?;
    let mut body = DependencyJavaSymbolIndexBody::new(Vec::new(), Vec::new(), Vec::new());
    let mut resolution = Vec::new();
    let mut observed = DependencySymbolIndexCounts {
        source_files: expected_counts.source_files,
        definitions: 0,
        usages: 0,
        diagnostics: 0,
    };
    let mut line_index = 1_usize;
    loop {
        line.clear();
        if reader.read_until(b'\n', &mut line)? == 0 {
            break;
        }
        line_index += 1;
        trim_line_ending(&mut line);
        if line.is_empty() {
            continue;
        }
        let line = std::str::from_utf8(&line)?;
        let (record_kind, record) = line.split_once('\t').ok_or_else(|| {
            eyre::eyre!("dependency Java symbol stream line {line_index} has no record tag")
        })?;
        match record_kind {
            "definition" => {
                observed.definitions = observed.definitions.saturating_add(1);
                let route = RoutedJavaSymbol::parse(record, true)?;
                if resolution_selection.retains(route) {
                    resolution.push(route.to_resolution_definition());
                }
                if definition_filter(
                    route.kind,
                    route.owner,
                    route.name,
                    route.descriptor,
                    route.qualified_name,
                ) {
                    let definition = definition_decoder.from_str(route.json)?;
                    route.validate_definition(&definition)?;
                    body.definitions.push(definition);
                }
            }
            "usage" => {
                observed.usages = observed.usages.saturating_add(1);
                let route = RoutedJavaSymbol::parse(record, false)?;
                if usage_filter(
                    route.kind,
                    route.owner,
                    route.name,
                    route.descriptor,
                    route.qualified_name,
                ) {
                    let usage = usage_decoder.from_str(route.json)?;
                    route.validate_usage(&usage)?;
                    body.usages.push(usage);
                }
            }
            "diagnostic" => {
                observed.diagnostics = observed.diagnostics.saturating_add(1);
                if body.diagnostics.len() < DIAGNOSTIC_LIMIT {
                    body.diagnostics.push(diagnostic_decoder.from_str(record)?);
                }
            }
            _ => eyre::bail!(
                "dependency Java symbol stream line {line_index} has unknown record tag `{record_kind}`"
            ),
        }
    }
    finish_dependency_scan(body, resolution, &observed, expected_counts)
}

const fn is_type_kind(kind: JavaSymbolKind) -> bool {
    matches!(
        kind,
        JavaSymbolKind::Class
            | JavaSymbolKind::Interface
            | JavaSymbolKind::Enum
            | JavaSymbolKind::Record
            | JavaSymbolKind::Annotation
    )
}

fn finish_dependency_scan(
    mut body: DependencyJavaSymbolIndexBody,
    mut resolution: Vec<JavaDependencyResolutionDefinition>,
    observed: &DependencySymbolIndexCounts,
    expected_counts: &DependencySymbolIndexCounts,
) -> eyre::Result<DependencyJavaSymbolIndexScan> {
    if observed != expected_counts {
        eyre::bail!("dependency Java symbol stream counts do not match the authenticated manifest");
    }
    let suppressed = usize::try_from(observed.diagnostics)
        .unwrap_or(usize::MAX)
        .saturating_sub(body.diagnostics.len());
    if suppressed > 0 {
        body.diagnostics.push(JavaAnalysisDiagnosticOutput {
            code: "java.dependency-query-diagnostics-suppressed".to_owned(),
            severity: DiagnosticSeverity::Info,
            message: format!(
                "Suppressed {suppressed} additional dependency diagnostics for this query"
            ),
            span: None,
        });
    }
    let body = DependencyJavaSymbolIndexBody::new(body.definitions, body.usages, body.diagnostics);
    resolution.sort();
    resolution.dedup();
    Ok(DependencyJavaSymbolIndexScan { body, resolution })
}

#[derive(Clone, Copy)]
struct RoutedJavaSymbol<'a> {
    kind: JavaSymbolKind,
    owner: &'a str,
    name: &'a str,
    descriptor: Option<&'a str>,
    qualified_name: &'a str,
    source_set: &'a str,
    json: &'a str,
}

impl<'a> RoutedJavaSymbol<'a> {
    fn parse(record: &'a str, require_source_set: bool) -> eyre::Result<Self> {
        let mut fields = record.splitn(7, '\t');
        let kind = parse_route_kind(fields.next().unwrap_or_default())?;
        let owner = fields.next().unwrap_or_default();
        let name = fields.next().unwrap_or_default();
        let descriptor = fields.next().unwrap_or_default();
        let qualified_name = fields.next().unwrap_or_default();
        let source_set = fields.next().unwrap_or_default();
        let json = fields.next().unwrap_or_default();
        if owner.is_empty() || name.is_empty() || qualified_name.is_empty() || json.is_empty() {
            eyre::bail!("dependency Java symbol route contains an empty required field");
        }
        if require_source_set == source_set.is_empty() {
            eyre::bail!("dependency Java symbol route has an invalid source-set field");
        }
        Ok(Self {
            kind,
            owner,
            name,
            descriptor: (!descriptor.is_empty()).then_some(descriptor),
            qualified_name,
            source_set,
            json,
        })
    }

    fn is_resolution_relevant(
        self,
        identifiers: &BTreeSet<String>,
        member_accesses: &BTreeSet<String>,
    ) -> bool {
        let owner_name = self.owner.rsplit(['.', '$']).next().unwrap_or(self.owner);
        match self.kind {
            JavaSymbolKind::Class
            | JavaSymbolKind::Interface
            | JavaSymbolKind::Enum
            | JavaSymbolKind::Record
            | JavaSymbolKind::Annotation => identifiers.contains(self.name),
            JavaSymbolKind::Constructor => identifiers.contains(owner_name),
            JavaSymbolKind::Field | JavaSymbolKind::Method => {
                identifiers.contains(owner_name) && member_accesses.contains(self.name)
            }
            JavaSymbolKind::LocalVariable | JavaSymbolKind::Parameter => false,
        }
    }

    fn to_identity(self) -> JavaSymbolIdentityOutput {
        JavaSymbolIdentityOutput {
            kind: self.kind,
            owner: self.owner.to_owned(),
            name: self.name.to_owned(),
            descriptor: self.descriptor.map(str::to_owned),
            qualified_name: self.qualified_name.to_owned(),
        }
    }

    fn to_resolution_definition(self) -> JavaDependencyResolutionDefinition {
        JavaDependencyResolutionDefinition {
            symbol: self.to_identity(),
            source_set: self.source_set.to_owned(),
        }
    }

    fn matches_identity(self, symbol: &JavaSymbolIdentityOutput) -> bool {
        self.kind == symbol.kind
            && self.owner == symbol.owner
            && self.name == symbol.name
            && self.descriptor == symbol.descriptor.as_deref()
            && self.qualified_name == symbol.qualified_name
    }

    fn validate_definition(self, definition: &JavaSymbolDefinitionOutput) -> eyre::Result<()> {
        if !self.matches_identity(&definition.symbol)
            || self.source_set != definition.identifier_span.source_set
        {
            eyre::bail!("dependency Java definition route does not match its JSON body");
        }
        Ok(())
    }

    fn validate_usage(self, usage: &JavaSymbolUsageOutput) -> eyre::Result<()> {
        if !self.matches_identity(&usage.target) {
            eyre::bail!("dependency Java usage route does not match its JSON body");
        }
        Ok(())
    }
}

fn parse_route_kind(value: &str) -> eyre::Result<JavaSymbolKind> {
    match value {
        "class" => Ok(JavaSymbolKind::Class),
        "interface" => Ok(JavaSymbolKind::Interface),
        "enum" => Ok(JavaSymbolKind::Enum),
        "record" => Ok(JavaSymbolKind::Record),
        "annotation" => Ok(JavaSymbolKind::Annotation),
        "field" => Ok(JavaSymbolKind::Field),
        "method" => Ok(JavaSymbolKind::Method),
        "constructor" => Ok(JavaSymbolKind::Constructor),
        "local-variable" => Ok(JavaSymbolKind::LocalVariable),
        "parameter" => Ok(JavaSymbolKind::Parameter),
        _ => eyre::bail!("unknown dependency Java symbol route kind `{value}`"),
    }
}

fn trim_line_ending(line: &mut Vec<u8>) {
    if line.last() == Some(&b'\n') {
        line.pop();
    }
    if line.last() == Some(&b'\r') {
        line.pop();
    }
}

impl DependencyJavaSymbolIndexBody {
    #[must_use]
    pub fn new(
        mut definitions: Vec<JavaSymbolDefinitionOutput>,
        mut usages: Vec<JavaSymbolUsageOutput>,
        mut diagnostics: Vec<JavaAnalysisDiagnosticOutput>,
    ) -> Self {
        definitions.sort();
        definitions.dedup();
        usages.sort();
        usages.dedup();
        diagnostics.sort();
        diagnostics.dedup();
        Self {
            definitions,
            usages,
            diagnostics,
        }
    }

    #[must_use]
    pub fn counts(&self, source_files: u64) -> DependencySymbolIndexCounts {
        DependencySymbolIndexCounts {
            source_files,
            definitions: u64::try_from(self.definitions.len()).unwrap_or(u64::MAX),
            usages: u64::try_from(self.usages.len()).unwrap_or(u64::MAX),
            diagnostics: u64::try_from(self.diagnostics.len()).unwrap_or(u64::MAX),
        }
    }

    /// Replace temporary custom-root paths with stable dependency provenance.
    #[must_use]
    pub fn with_portable_origins(mut self, origins: &[DependencyJavaSourceOrigin]) -> Self {
        let origins = origins
            .iter()
            .map(|origin| (origin.report_root.as_str(), origin))
            .collect::<BTreeMap<_, _>>();
        for definition in &mut self.definitions {
            rewrite_span(&mut definition.identifier_span, &origins);
            rewrite_span(&mut definition.declaration_span, &origins);
        }
        for usage in &mut self.usages {
            rewrite_span(&mut usage.span, &origins);
        }
        for diagnostic in &mut self.diagnostics {
            if let Some(span) = &mut diagnostic.span {
                rewrite_span(span, &origins);
            }
        }
        self
    }
}

fn rewrite_span(
    span: &mut JavaSourceSpanOutput,
    origins: &BTreeMap<&str, &DependencyJavaSourceOrigin>,
) {
    let Some((root, relative)) = span.path.split_once('/') else {
        return;
    };
    let Some(origin) = origins.get(root) else {
        return;
    };
    span.path = format!(
        "{}/{}",
        origin.portable_prefix.trim_end_matches('/'),
        relative
    );
    span.source_set.clone_from(&origin.source_set);
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::java_analysis::ResolutionConfidence;

    #[test]
    fn portable_origins_replace_only_the_matching_custom_root() {
        let span = JavaSourceSpanOutput {
            path: "custom-0/net/minecraft/A.java".to_owned(),
            source_set: "custom".to_owned(),
            source_hash: "blake3:source".to_owned(),
            start_byte: 0,
            end_byte: 1,
            start_line: 1,
            start_column: 1,
            end_line: 1,
            end_column: 2,
        };
        let body = DependencyJavaSymbolIndexBody::new(
            vec![JavaSymbolDefinitionOutput {
                symbol: JavaSymbolIdentityOutput {
                    kind: JavaSymbolKind::Class,
                    owner: "net.minecraft".to_owned(),
                    name: "A".to_owned(),
                    descriptor: None,
                    qualified_name: "net.minecraft.A".to_owned(),
                },
                identifier_span: span.clone(),
                declaration_span: span,
                confidence: ResolutionConfidence::Resolved,
            }],
            Vec::new(),
            Vec::new(),
        )
        .with_portable_origins(&[DependencyJavaSourceOrigin {
            report_root: "custom-0".to_owned(),
            portable_prefix: "dependency/minecraft/main/pipeline".to_owned(),
            source_set: "dependency:minecraft:main".to_owned(),
        }]);

        assert_eq!(
            body.definitions[0].identifier_span.path,
            "dependency/minecraft/main/pipeline/net/minecraft/A.java"
        );
        assert_eq!(
            body.definitions[0].identifier_span.source_set,
            "dependency:minecraft:main"
        );
    }

    #[test]
    fn routed_resolution_requires_owner_and_dot_qualified_member_evidence() {
        let identifiers = BTreeSet::from(["Editor".to_owned()]);
        let member_accesses = BTreeSet::from(["render".to_owned()]);
        let relevant = |record: &str| {
            RoutedJavaSymbol::parse(record, true)
                .expect("valid route")
                .is_resolution_relevant(&identifiers, &member_accesses)
        };

        assert!(relevant(
            "class\texample\tEditor\t\texample.Editor\tdependency:example\t{}"
        ));
        assert!(relevant(
            "constructor\texample.Editor\t<init>\t()V\texample.Editor.<init>()V\tdependency:example\t{}"
        ));
        assert!(relevant(
            "method\texample.Editor\trender\t()V\texample.Editor.render()V\tdependency:example\t{}"
        ));
        assert!(!relevant(
            "method\texample.Unused\trender\t()V\texample.Unused.render()V\tdependency:example\t{}"
        ));
        assert!(!relevant(
            "field\texample.Editor\tvalue\tI\texample.Editor.value\tdependency:example\t{}"
        ));
    }
}
