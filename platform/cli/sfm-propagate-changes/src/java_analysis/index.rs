use super::DefinitionAtPositionDefinitionOutput;
use super::DefinitionAtPositionOutcome;
use super::DefinitionAtPositionRequest;
use super::DefinitionAtPositionResult;
use super::DefinitionDocumentIdentityOutput;
use super::DefinitionSourceSpanOutput;
use super::DependencyJavaSourceOrigin;
use super::DependencyJavaSymbolIndexBody;
use super::DiagnosticSeverity;
use super::JavaAnalysisContextOutput;
use super::JavaAnalysisDiagnosticOutput;
use super::JavaFileFactIdentity;
use super::JavaSourceFile;
use super::JavaSourceSpanOutput;
use super::JavaSourceWorkspace;
use super::JavaSymbolDefinitionOutput;
use super::JavaSymbolGlob;
use super::JavaSymbolIdentityOutput;
use super::JavaSymbolKind;
use super::JavaSymbolSelector;
use super::JavaSymbolUsageOutput;
use super::JavaUsageKind;
#[cfg(test)]
use super::JdkSourceDomainState;
use super::ResolutionConfidence;
use super::SymbolCommandOutcome;
use super::SymbolDefinitionOutput;
use super::SymbolListOutput;
use super::SymbolUsageListOutput;
use super::UsageAtPositionRequest;
use super::UsageAtPositionResult;
use super::UsageAtPositionUsageOutput;
use super::blake3_content_hash;
use super::definition_workspace_fingerprint;
use super::java_file_facts::raw_parameter_type;
use super::sha256_content_hash;
use super::syntax::JAVA_PARSER_FINGERPRINT;
use super::syntax::JavaSyntaxFile;
use super::syntax::declaration_name_node;
use super::syntax::first_named_child;
use super::syntax::is_nonsemantic_literal_or_comment;
use super::syntax::is_type_declaration;
use super::syntax::named_children;
use super::usage_at_position::USAGE_AT_POSITION_MAX_RESULTS;
use crate::cancellation::CancellationToken;
use rayon::prelude::*;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::sync::Arc;
use std::sync::Mutex;
use std::time::Instant;
use tree_sitter_patched_arborium::Node;

const USAGE_AT_POSITION_MAX_DIAGNOSTICS: usize = 512;
const USAGE_TARGET_CACHE_MAX_ENTRIES: usize = 16;
const USAGE_TARGET_CACHE_MAX_RETAINED_BYTES: usize = 64 * 1024 * 1024;

/// Exact-target reference results cached against one immutable linked surface.
///
/// A previous implementation eagerly retained usages for every symbol in the
/// workspace. The real SFM/JDK surface peaked above 50 GiB. This cache instead
/// keeps only symbols the user actually requests. A cheap, correctness-safe
/// identifier prefilter narrows semantic parsing to candidate files; the full
/// resolver still verifies every retained row.
pub(crate) struct JavaUsageResolutionSurface {
    targets: Mutex<UsageTargetCache>,
    pool: rayon::ThreadPool,
}

#[derive(Clone, Copy)]
pub(crate) struct JavaUsageSourceSnapshot<'snapshot> {
    pub(crate) files: &'snapshot [super::JavaSourceFile],
    pub(crate) sources: &'snapshot [String],
    pub(crate) dependencies: Option<&'snapshot DependencyJavaSymbolIndexBody>,
}

struct CachedUsageTarget {
    scan: Arc<UsageAtPositionScan>,
    retained_bytes: usize,
    last_access: u64,
}

#[derive(Default)]
struct UsageTargetCache {
    entries: BTreeMap<JavaSymbolIdentityOutput, CachedUsageTarget>,
    retained_bytes: usize,
    access_clock: u64,
    evictions: u64,
}

#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub(crate) struct JavaUsageResolutionTelemetry {
    pub(crate) target_cache_hits: u64,
    pub(crate) target_build_micros: u64,
    pub(crate) candidate_files: u64,
    pub(crate) parsed_files: u64,
    pub(crate) retained_rows: u64,
    pub(crate) discarded_candidates: u64,
    pub(crate) cache_entries: u64,
    pub(crate) cache_retained_bytes: u64,
    pub(crate) cache_evictions: u64,
}

impl UsageTargetCache {
    fn get(&mut self, target: &JavaSymbolIdentityOutput) -> Option<Arc<UsageAtPositionScan>> {
        self.access_clock = self.access_clock.saturating_add(1);
        let cached = self.entries.get_mut(target)?;
        cached.last_access = self.access_clock;
        Some(Arc::clone(&cached.scan))
    }

    fn insert(&mut self, target: JavaSymbolIdentityOutput, scan: Arc<UsageAtPositionScan>) {
        self.access_clock = self.access_clock.saturating_add(1);
        if let Some(replaced) = self.entries.remove(&target) {
            self.retained_bytes = self.retained_bytes.saturating_sub(replaced.retained_bytes);
        }
        let retained_bytes = symbol_retained_bytes(&target).saturating_add(scan.retained_bytes());
        self.retained_bytes = self.retained_bytes.saturating_add(retained_bytes);
        self.entries.insert(
            target,
            CachedUsageTarget {
                scan,
                retained_bytes,
                last_access: self.access_clock,
            },
        );
        while self.entries.len() > USAGE_TARGET_CACHE_MAX_ENTRIES
            || self.retained_bytes > USAGE_TARGET_CACHE_MAX_RETAINED_BYTES
        {
            let Some(oldest) = self
                .entries
                .iter()
                .min_by_key(|(target, cached)| (cached.last_access, *target))
                .map(|(target, _)| target.clone())
            else {
                break;
            };
            if let Some(removed) = self.entries.remove(&oldest) {
                self.retained_bytes = self.retained_bytes.saturating_sub(removed.retained_bytes);
                self.evictions = self.evictions.saturating_add(1);
            }
        }
    }

    fn telemetry(&self) -> (u64, u64, u64) {
        (
            self.entries.len().try_into().unwrap_or(u64::MAX),
            self.retained_bytes.try_into().unwrap_or(u64::MAX),
            self.evictions,
        )
    }
}

impl JavaUsageResolutionSurface {
    pub(crate) fn new() -> eyre::Result<Arc<Self>> {
        let worker_count = std::thread::available_parallelism()
            .map_or(1, std::num::NonZeroUsize::get)
            .min(16);
        let pool = rayon::ThreadPoolBuilder::new()
            .num_threads(worker_count)
            .thread_name(|index| format!("sfm-java-usage-target-{index}"))
            .build()?;
        Ok(Arc::new(Self {
            targets: Mutex::new(UsageTargetCache::default()),
            pool,
        }))
    }

    fn selected_or_build(
        &self,
        resolution: &Arc<JavaDefinitionResolutionSurface>,
        files: &[super::JavaSourceFile],
        sources: &[String],
        target: &JavaSymbolIdentityOutput,
        cancellation_token: &CancellationToken,
    ) -> eyre::Result<(UsageAtPositionScan, JavaUsageResolutionTelemetry)> {
        cancellation_token.bail_if_cancelled()?;
        let mut targets = loop {
            match self.targets.try_lock() {
                Ok(targets) => break targets,
                Err(std::sync::TryLockError::WouldBlock) => {
                    cancellation_token.bail_if_cancelled()?;
                    std::thread::park_timeout(std::time::Duration::from_millis(2));
                }
                Err(std::sync::TryLockError::Poisoned(poisoned)) => {
                    break poisoned.into_inner();
                }
            }
        };
        if let Some(cached) = targets.get(target) {
            let (cache_entries, cache_retained_bytes, cache_evictions) = targets.telemetry();
            return Ok((
                cached.as_ref().clone(),
                JavaUsageResolutionTelemetry {
                    target_cache_hits: 1,
                    retained_rows: cached.usages.len().try_into().unwrap_or(u64::MAX),
                    discarded_candidates: cached.discarded_candidate_count,
                    cache_entries,
                    cache_retained_bytes,
                    cache_evictions,
                    ..JavaUsageResolutionTelemetry::default()
                },
            ));
        }

        let started = Instant::now();
        let needle = usage_candidate_identifier(target);
        let candidate_indices = sources
            .iter()
            .enumerate()
            .filter_map(|(index, source)| {
                source_contains_java_identifier(source, needle).then_some(index)
            })
            .collect::<Vec<_>>();
        let selected = BTreeSet::from([target.clone()]);
        let scan = self.pool.install(|| {
            candidate_indices
                .par_iter()
                .map(|index| {
                    cancellation_token.bail_if_cancelled()?;
                    let mut file = files[*index].clone();
                    file.source_override = Some(sources[*index].clone());
                    let parsed = JavaSyntaxFile::parse_with_diagnostic_limit(
                        &file,
                        Some(USAGE_AT_POSITION_MAX_DIAGNOSTICS),
                    )?;
                    let mut diagnostics = parsed.diagnostics.clone();
                    let model = JavaIndexModel {
                        files: vec![parsed],
                        resolution: Arc::clone(resolution),
                    };
                    let (usages, mut usage_diagnostics, _, discarded_candidates) =
                        collect_selected_file_usages(
                            &model,
                            0,
                            &selected,
                            USAGE_AT_POSITION_MAX_RESULTS,
                            USAGE_AT_POSITION_MAX_DIAGNOSTICS,
                            cancellation_token,
                        )?;
                    diagnostics.append(&mut usage_diagnostics);
                    let mut partial = UsageAtPositionScan::new();
                    partial.discarded_candidate_count = discarded_candidates;
                    for usage in usages {
                        partial.record_usage(usage);
                    }
                    for diagnostic in diagnostics {
                        partial.record_diagnostic(diagnostic);
                    }
                    cancellation_token.bail_if_cancelled()?;
                    Ok::<_, eyre::Report>(partial)
                })
                .try_reduce(UsageAtPositionScan::new, |mut left, right| {
                    left.merge(right);
                    Ok(left)
                })
        })?;
        cancellation_token.bail_if_cancelled()?;
        targets.insert(target.clone(), Arc::new(scan.clone()));
        let (cache_entries, cache_retained_bytes, cache_evictions) = targets.telemetry();
        let telemetry = JavaUsageResolutionTelemetry {
            target_cache_hits: 0,
            target_build_micros: started.elapsed().as_micros().try_into().unwrap_or(u64::MAX),
            candidate_files: candidate_indices.len().try_into().unwrap_or(u64::MAX),
            parsed_files: candidate_indices.len().try_into().unwrap_or(u64::MAX),
            retained_rows: scan.usages.len().try_into().unwrap_or(u64::MAX),
            discarded_candidates: scan.discarded_candidate_count,
            cache_entries,
            cache_retained_bytes,
            cache_evictions,
        };
        Ok((scan, telemetry))
    }
}

fn usage_candidate_identifier(target: &JavaSymbolIdentityOutput) -> &str {
    if target.kind == JavaSymbolKind::Constructor {
        target.owner.rsplit('.').next().unwrap_or(&target.name)
    } else {
        &target.name
    }
}

fn source_contains_java_identifier(source: &str, needle: &str) -> bool {
    if needle.is_empty() || !needle.is_ascii() {
        return true;
    }
    source.match_indices(needle).any(|(start, matched)| {
        let before = start.checked_sub(1).map(|index| source.as_bytes()[index]);
        let after = source.as_bytes().get(start + matched.len()).copied();
        !before.is_some_and(is_java_identifier_byte) && !after.is_some_and(is_java_identifier_byte)
    })
}

fn is_java_identifier_byte(byte: u8) -> bool {
    byte.is_ascii_alphanumeric() || matches!(byte, b'_' | b'$') || !byte.is_ascii()
}

#[derive(Clone, Debug)]
struct UsageAtPositionScan {
    usages: BTreeSet<JavaSymbolUsageOutput>,
    diagnostics: BTreeSet<JavaAnalysisDiagnosticOutput>,
    discarded_candidate_count: u64,
}

impl UsageAtPositionScan {
    fn new() -> Self {
        Self {
            usages: BTreeSet::new(),
            diagnostics: BTreeSet::new(),
            discarded_candidate_count: 0,
        }
    }

    fn record_usage(&mut self, usage: JavaSymbolUsageOutput) {
        if self.usages.contains(&usage) {
            return;
        }
        if self.usages.len() < USAGE_AT_POSITION_MAX_RESULTS {
            self.usages.insert(usage);
            return;
        }
        let replace_largest = self.usages.last().is_some_and(|largest| usage < *largest);
        if replace_largest {
            self.usages.pop_last();
            self.usages.insert(usage);
        }
        self.discarded_candidate_count = self.discarded_candidate_count.saturating_add(1);
    }

    fn record_diagnostic(&mut self, diagnostic: JavaAnalysisDiagnosticOutput) {
        if self.diagnostics.contains(&diagnostic) {
            return;
        }
        if self.diagnostics.len() < USAGE_AT_POSITION_MAX_DIAGNOSTICS {
            self.diagnostics.insert(diagnostic);
            return;
        }
        let replace_largest = self
            .diagnostics
            .last()
            .is_some_and(|largest| diagnostic < *largest);
        if replace_largest {
            self.diagnostics.pop_last();
            self.diagnostics.insert(diagnostic);
        }
    }

    fn merge(&mut self, other: Self) {
        self.discarded_candidate_count = self
            .discarded_candidate_count
            .saturating_add(other.discarded_candidate_count);
        for usage in other.usages {
            self.record_usage(usage);
        }
        for diagnostic in other.diagnostics {
            self.record_diagnostic(diagnostic);
        }
    }

    fn retained_bytes(&self) -> usize {
        std::mem::size_of::<Self>()
            .saturating_add(self.usages.iter().map(usage_retained_bytes).sum::<usize>())
            .saturating_add(
                self.diagnostics
                    .iter()
                    .map(diagnostic_retained_bytes)
                    .sum::<usize>(),
            )
    }
}

fn usage_retained_bytes(usage: &JavaSymbolUsageOutput) -> usize {
    std::mem::size_of::<JavaSymbolUsageOutput>()
        .saturating_add(symbol_retained_bytes(&usage.target))
        .saturating_add(span_retained_bytes(&usage.span))
}

fn diagnostic_retained_bytes(diagnostic: &JavaAnalysisDiagnosticOutput) -> usize {
    std::mem::size_of::<JavaAnalysisDiagnosticOutput>()
        .saturating_add(diagnostic.code.capacity())
        .saturating_add(diagnostic.message.capacity())
        .saturating_add(diagnostic.span.as_ref().map_or(0, span_retained_bytes))
}

fn symbol_retained_bytes(symbol: &JavaSymbolIdentityOutput) -> usize {
    std::mem::size_of::<JavaSymbolIdentityOutput>()
        .saturating_add(symbol.owner.capacity())
        .saturating_add(symbol.name.capacity())
        .saturating_add(symbol.descriptor.as_ref().map_or(0, String::capacity))
        .saturating_add(symbol.qualified_name.capacity())
}

fn span_retained_bytes(span: &JavaSourceSpanOutput) -> usize {
    std::mem::size_of::<JavaSourceSpanOutput>()
        .saturating_add(span.path.capacity())
        .saturating_add(span.source_set.capacity())
        .saturating_add(span.source_hash.capacity())
}

#[derive(Clone, Debug)]
pub struct JavaSymbolIndex {
    context: JavaAnalysisContextOutput,
    definitions: Vec<JavaSymbolDefinitionOutput>,
    location_definitions: Vec<JavaSymbolDefinitionOutput>,
    usages: Vec<JavaSymbolUsageOutput>,
    diagnostics: Vec<JavaAnalysisDiagnosticOutput>,
}

/// A dependency declaration used only while resolving another source shard.
///
/// Unlike [`JavaSymbolDefinitionOutput`], this deliberately carries no source
/// spans or report confidence. Private dependency-index workers can therefore
/// load the global resolution surface without expanding every compact record
/// into a synthetic report declaration.
#[derive(Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
pub(crate) struct JavaDependencyResolutionDefinition {
    pub(crate) symbol: JavaSymbolIdentityOutput,
    pub(crate) source_set: String,
}

/// Rich declaration surface retained by the warm linker. Unlike the portable
/// dependency body, this keeps member types needed to resolve chained
/// expressions without reparsing every live source file.
#[derive(Clone, Debug)]
pub(crate) struct JavaLiveDefinitionSurface {
    pub(crate) context: JavaAnalysisContextOutput,
    pub(crate) files: Vec<JavaFileFactIdentity>,
    pub(crate) types: Vec<JavaSymbolDefinitionOutput>,
    pub(crate) fields: Vec<JavaLiveFieldDefinition>,
    pub(crate) methods: Vec<JavaLiveMethodDefinition>,
    pub(crate) diagnostics: Vec<JavaAnalysisDiagnosticOutput>,
}

/// Immutable name/member lookup surface shared by many editor-location queries.
///
/// Building these sorted declaration vectors and lookup maps dominates a fresh
/// location query. The symbol server therefore constructs one surface from a
/// linked fact snapshot and reuses it while that exact workspace/content
/// identity remains current. Request-local syntax trees never enter this value.
#[derive(Clone, Debug)]
pub(crate) struct JavaDefinitionResolutionSurface {
    context: JavaAnalysisContextOutput,
    files: Vec<JavaFileFactIdentity>,
    types: Vec<TypeDeclaration>,
    fields: Vec<FieldDeclaration>,
    methods: Vec<MethodDeclaration>,
    type_lookup: TypeLookup,
    field_lookup: BTreeMap<(String, String), Vec<usize>>,
    method_lookup: BTreeMap<(String, String), Vec<usize>>,
    diagnostics: Vec<JavaAnalysisDiagnosticOutput>,
}

/// One parsed editor snapshot resolved against the immutable workspace symbol
/// surface. Interaction-map generation consumes this value directly so a
/// document is not reparsed once per token or glyph.
pub(crate) struct JavaResolvedInteractionDocument {
    pub(crate) syntax: JavaSyntaxFile,
    pub(crate) usages: Vec<JavaSymbolUsageOutput>,
    pub(crate) local_definitions: Vec<JavaSymbolDefinitionOutput>,
    pub(crate) diagnostics: Vec<JavaAnalysisDiagnosticOutput>,
}

#[derive(Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
pub(crate) struct JavaLiveFieldDefinition {
    pub(crate) definition: JavaSymbolDefinitionOutput,
    pub(crate) value_type: Option<String>,
}

#[derive(Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
pub(crate) struct JavaLiveMethodDefinition {
    pub(crate) definition: JavaSymbolDefinitionOutput,
    pub(crate) parameter_types: Vec<String>,
    pub(crate) return_type: Option<String>,
}

impl JavaLiveDefinitionSurface {
    pub(crate) fn into_index(self) -> JavaSymbolIndex {
        let mut definitions = self.types;
        definitions.extend(self.fields.into_iter().map(|field| field.definition));
        definitions.extend(self.methods.into_iter().map(|method| method.definition));
        JavaSymbolIndex::from_linked_definitions(
            self.context,
            &self.files,
            definitions,
            self.diagnostics,
        )
    }

    fn source_sets(&self) -> BTreeSet<String> {
        self.types
            .iter()
            .map(|definition| definition.identifier_span.source_set.clone())
            .chain(
                self.fields
                    .iter()
                    .map(|field| field.definition.identifier_span.source_set.clone()),
            )
            .chain(
                self.methods
                    .iter()
                    .map(|method| method.definition.identifier_span.source_set.clone()),
            )
            .collect()
    }
}

impl JavaDefinitionResolutionSurface {
    /// Build the immutable declaration/lookup state used by request-local
    /// syntax walks. This is intentionally separate from `JavaSymbolIndex`:
    /// editor queries need the global resolution vocabulary, but they do not
    /// need to clone/sort every global usage and definition for each cursor.
    pub(crate) fn build(
        workspace: &JavaSourceWorkspace,
        live: &JavaLiveDefinitionSurface,
        dependencies: Option<&DependencyJavaSymbolIndexBody>,
    ) -> Arc<Self> {
        let mut context = workspace.context.clone();
        JAVA_PARSER_FINGERPRINT.clone_into(&mut context.parser_fingerprint);
        if let Some(dependencies) = dependencies {
            add_dependency_source_sets(&mut context, dependencies);
        }

        let live_source_sets = live.source_sets();
        let mut visibility = BTreeSet::new();
        for from in &live_source_sets {
            for to in &live_source_sets {
                if workspace.is_visible(from, to) {
                    visibility.insert((from.clone(), to.clone()));
                }
            }
        }
        let dependency_sets = dependencies.map_or_else(BTreeSet::new, dependency_source_sets);
        for from in &live_source_sets {
            for target in &dependency_sets {
                visibility.insert((from.clone(), target.clone()));
            }
        }
        for from in &dependency_sets {
            for target in &dependency_sets {
                visibility.insert((from.clone(), target.clone()));
            }
        }

        let mut diagnostics = live.diagnostics.clone();
        let mut types = Vec::new();
        append_live_types(live, &mut types);
        if let Some(dependencies) = dependencies {
            diagnostics.extend(dependencies.diagnostics.iter().cloned());
            append_dependency_types(dependencies, &mut types);
        }
        let type_lookup = TypeLookup::new(&types, visibility);

        let mut fields = Vec::new();
        let mut methods = Vec::new();
        append_live_members(live, &mut fields, &mut methods);
        if let Some(dependencies) = dependencies {
            append_dependency_members(dependencies, &mut fields, &mut methods);
        }
        diagnostics.sort();
        diagnostics.dedup();
        let field_lookup = member_lookup(fields.iter().map(FieldDeclaration::symbol));
        let method_lookup = member_lookup(methods.iter().map(MethodDeclaration::symbol));

        Arc::new(Self {
            context,
            files: live.files.clone(),
            types,
            fields,
            methods,
            type_lookup,
            field_lookup,
            method_lookup,
            diagnostics,
        })
    }

    /// Parse and walk only the addressed current document against this warm
    /// global resolution surface.
    pub(crate) fn definition_at_position(
        self: &Arc<Self>,
        workspace: &JavaSourceWorkspace,
        target: &JavaSourceFile,
        request: &DefinitionAtPositionRequest,
    ) -> eyre::Result<DefinitionAtPositionResult> {
        let resolved = self.resolve_interaction_document(target, request)?;
        Ok(definition_at_position_from_parts(
            self.context.clone(),
            |selected_symbols| {
                let mut definitions = self.definitions_for_symbols(selected_symbols);
                definitions.extend(
                    resolved
                        .local_definitions
                        .iter()
                        .filter(|definition| selected_symbols.contains(&definition.symbol))
                        .cloned(),
                );
                definitions
            },
            &resolved.usages,
            self.diagnostics.iter().chain(resolved.diagnostics.iter()),
            request,
            workspace,
        ))
    }

    /// Parse and semantically resolve an addressed document exactly once.
    ///
    /// The returned syntax tree remains request-owned. It is deliberately not
    /// admitted to the workspace cache: its identity is the caller supplied
    /// content hash and request generation, and stale consumers must discard
    /// the complete interaction map.
    pub(crate) fn resolve_interaction_document(
        self: &Arc<Self>,
        target: &JavaSourceFile,
        request: &DefinitionAtPositionRequest,
    ) -> eyre::Result<JavaResolvedInteractionDocument> {
        let mut target = target.clone();
        target.source_override = Some(request.document.text.clone());
        let parsed = JavaSyntaxFile::parse_with_diagnostic_limit(&target, None)?;
        let mut diagnostics = parsed.diagnostics.clone();
        let model = JavaIndexModel {
            files: vec![parsed],
            resolution: Arc::clone(self),
        };
        let mut usages = declaration_usages_for_files(&model);
        let (mut file_usages, mut file_diagnostics, local_definitions) =
            collect_file_usages(&model, 0);
        usages.append(&mut file_usages);
        diagnostics.append(&mut file_diagnostics);
        usages.sort();
        usages.dedup();
        diagnostics.sort();
        diagnostics.dedup();

        Ok(JavaResolvedInteractionDocument {
            syntax: model
                .files
                .into_iter()
                .next()
                .expect("one addressed Java document remains in the interaction model"),
            usages,
            local_definitions,
            diagnostics,
        })
    }

    /// Resolve only the declarations selected by the addressed document.
    ///
    /// The previous warm path streamed every declaration through
    /// `definition_at_position_from_parts`, which made every cursor query scan
    /// the complete dependency/JDK vocabulary. These lookup tables already
    /// exist for semantic linking, so reuse them as an exact identity index.
    pub(crate) fn definitions_for_symbols(
        &self,
        selected_symbols: &BTreeSet<JavaSymbolIdentityOutput>,
    ) -> Vec<JavaSymbolDefinitionOutput> {
        let mut definitions = Vec::new();
        for symbol in selected_symbols {
            let indices = match symbol.kind {
                JavaSymbolKind::Class
                | JavaSymbolKind::Interface
                | JavaSymbolKind::Enum
                | JavaSymbolKind::Record
                | JavaSymbolKind::Annotation => self
                    .type_lookup
                    .by_name
                    .get(&symbol.qualified_name)
                    .map(Vec::as_slice)
                    .unwrap_or_default(),
                JavaSymbolKind::Field => self
                    .field_lookup
                    .get(&(symbol.owner.clone(), symbol.name.clone()))
                    .map(Vec::as_slice)
                    .unwrap_or_default(),
                JavaSymbolKind::Method | JavaSymbolKind::Constructor => self
                    .method_lookup
                    .get(&(symbol.owner.clone(), symbol.name.clone()))
                    .map(Vec::as_slice)
                    .unwrap_or_default(),
                JavaSymbolKind::LocalVariable | JavaSymbolKind::Parameter => &[],
            };
            match symbol.kind {
                JavaSymbolKind::Class
                | JavaSymbolKind::Interface
                | JavaSymbolKind::Enum
                | JavaSymbolKind::Record
                | JavaSymbolKind::Annotation => {
                    definitions.extend(indices.iter().filter_map(|index| {
                        let declaration = &self.types[*index];
                        (declaration.symbol() == symbol)
                            .then(|| declaration.output().cloned())
                            .flatten()
                    }));
                }
                JavaSymbolKind::Field => definitions.extend(indices.iter().filter_map(|index| {
                    let declaration = &self.fields[*index];
                    (declaration.symbol() == symbol)
                        .then(|| declaration.output().cloned())
                        .flatten()
                })),
                JavaSymbolKind::Method | JavaSymbolKind::Constructor => {
                    definitions.extend(indices.iter().filter_map(|index| {
                        let declaration = &self.methods[*index];
                        (declaration.symbol() == symbol)
                            .then(|| declaration.output().cloned())
                            .flatten()
                    }));
                }
                JavaSymbolKind::LocalVariable | JavaSymbolKind::Parameter => {}
            }
        }
        definitions
    }

    /// Resolve the symbol at the addressed document location, then reuse or
    /// build its exact-target reference set from the same immutable source
    /// snapshot that keyed the linked declaration surface.
    pub(crate) fn usages_at_position(
        self: &Arc<Self>,
        usage_surface: &JavaUsageResolutionSurface,
        workspace: &JavaSourceWorkspace,
        target: &JavaSourceFile,
        snapshot: JavaUsageSourceSnapshot<'_>,
        request: &UsageAtPositionRequest,
        cancellation_token: &CancellationToken,
    ) -> eyre::Result<(UsageAtPositionResult, JavaUsageResolutionTelemetry)> {
        cancellation_token.bail_if_cancelled()?;
        let definition_request = request.as_definition_request();
        let definition = self.definition_at_position(workspace, target, &definition_request)?;
        let mut result = UsageAtPositionResult::from_definition(definition);
        if result.outcome != super::UsageAtPositionOutcome::Success || result.targets.len() != 1 {
            result.refresh_index_fingerprint();
            return Ok((result, JavaUsageResolutionTelemetry::default()));
        }

        let selected = result.targets.iter().cloned().collect::<BTreeSet<_>>();
        let target = result.targets.first().expect("one successful target");
        let (mut scanned, mut usage_telemetry) = usage_surface.selected_or_build(
            self,
            snapshot.files,
            snapshot.sources,
            target,
            cancellation_token,
        )?;
        if let Some(dependencies) = snapshot.dependencies {
            for usage in &dependencies.usages {
                if selected.contains(&usage.target) {
                    scanned.record_usage(usage.clone());
                }
            }
        }
        cancellation_token.bail_if_cancelled()?;
        let discarded_candidate_count = scanned.discarded_candidate_count;
        usage_telemetry.discarded_candidates = discarded_candidate_count;
        let raw_usages = scanned.usages.into_iter().collect::<Vec<_>>();

        let selected_paths = raw_usages
            .iter()
            .map(|usage| usage.span.path.as_str())
            .collect::<BTreeSet<_>>();
        let mut diagnostics = result.diagnostics.clone();
        diagnostics.extend(scanned.diagnostics);
        diagnostics.retain(|diagnostic| {
            diagnostic.span.as_ref().is_some_and(|span| {
                selected_paths.contains(span.path.as_str())
                    || (span.path == request.document.report_path
                        && span.source_hash == request.document.content_hash)
            })
        });
        diagnostics.sort();
        diagnostics.dedup();
        result.diagnostics = diagnostics;
        let mut span_resolver =
            SnapshotDefinitionSpanResolver::new(workspace, snapshot.files, snapshot.sources);
        result.usages = raw_usages
            .into_iter()
            .map(|usage| UsageAtPositionUsageOutput {
                target: usage.target,
                kind: usage.kind,
                span: span_resolver.resolve(&usage.span),
                confidence: usage.confidence,
            })
            .collect();

        // A JDK declaration is part of the live resolution surface rather
        // than the dependency usage body. Ensure every successful target has
        // a declaration row even when no indexed usage supplied one.
        result
            .usages
            .extend(
                result
                    .definitions
                    .iter()
                    .map(|definition| UsageAtPositionUsageOutput {
                        target: definition.symbol.clone(),
                        kind: JavaUsageKind::Declaration,
                        span: definition.identifier_span.clone(),
                        confidence: definition.confidence,
                    }),
            );
        result.usages.sort();
        result.usages.dedup();
        cancellation_token.bail_if_cancelled()?;
        Ok((result, usage_telemetry))
    }

    #[must_use]
    pub(crate) fn declaration_count(&self) -> usize {
        self.types
            .len()
            .saturating_add(self.fields.len())
            .saturating_add(self.methods.len())
    }

    #[must_use]
    pub(crate) fn declaration_counts(&self) -> (usize, usize, usize) {
        (self.types.len(), self.fields.len(), self.methods.len())
    }

    #[must_use]
    pub(crate) const fn context(&self) -> &JavaAnalysisContextOutput {
        &self.context
    }

    #[must_use]
    pub(crate) fn files(&self) -> &[JavaFileFactIdentity] {
        &self.files
    }
}

/// Analyze the exact immutable editor snapshot in `request` against the
/// selected workspace and optional dependency index.
///
/// This is the shared direct-analysis entry point for the CLI and a future
/// long-lived provider. It validates the request's root identity, replaces
/// only the addressed document while parsing, and delegates symbol discovery
/// to the same [`UsageCollector`] used by ordinary usage queries.
///
/// # Errors
///
/// Returns an error when a workspace source cannot be read or parsed. Invalid
/// request/workspace combinations are represented as typed
/// [`DefinitionAtPositionResult`] values.
pub fn analyze_definition_at_position(
    workspace: &JavaSourceWorkspace,
    request: &DefinitionAtPositionRequest,
    dependencies: Option<&DependencyJavaSymbolIndexBody>,
) -> eyre::Result<DefinitionAtPositionResult> {
    if let Err(error) = validate_definition_request_workspace(workspace, request) {
        return Ok(DefinitionAtPositionResult::invalid_request(
            request,
            workspace.context.clone(),
            format!("{error:#}"),
        ));
    }
    let workspace = workspace.clone().with_source_overlay(
        &request.document.root_id,
        &request.document.root_relative_path,
        request.document.text.clone(),
    )?;
    let index = JavaSymbolIndex::build_workspace(&workspace, true, dependencies, None, None, None)?;
    let mut result = index.definition_at_position(request, &workspace);
    normalize_definition_at_position_context(workspace.context.clone(), dependencies, &mut result);
    for definition in &mut result.definitions {
        workspace
            .jdk_sources
            .enrich_span(&mut definition.identifier_span);
        workspace
            .jdk_sources
            .enrich_span(&mut definition.declaration_span);
    }
    Ok(result)
}

impl JavaSymbolIndex {
    pub(crate) fn from_linked_definitions(
        mut context: JavaAnalysisContextOutput,
        files: &[JavaFileFactIdentity],
        mut definitions: Vec<JavaSymbolDefinitionOutput>,
        mut diagnostics: Vec<JavaAnalysisDiagnosticOutput>,
    ) -> Self {
        definitions.sort();
        definitions.dedup();
        diagnostics.sort();
        diagnostics.dedup();
        JAVA_PARSER_FINGERPRINT.clone_into(&mut context.parser_fingerprint);
        let evidence = files
            .iter()
            .map(|file| IndexSourceEvidence {
                report_path: file.report_path.clone(),
                source_set: file.source_set.clone(),
                source_hash: file.source_hash.clone(),
            })
            .collect::<Vec<_>>();
        context.index_fingerprint = index_fingerprint(&evidence, &definitions, &[], &diagnostics);
        Self {
            context,
            definitions,
            location_definitions: Vec::new(),
            usages: Vec::new(),
            diagnostics,
        }
    }

    /// Build a deterministic, read-only source index for every file in the workspace.
    ///
    /// # Errors
    ///
    /// Returns an error when a source cannot be read or Arborium cannot create a parse tree.
    pub fn build(workspace: &JavaSourceWorkspace) -> eyre::Result<Self> {
        Self::build_workspace(workspace, true, None, None, None, None)
    }

    /// Build only declarations and declaration-time diagnostics. Definition
    /// queries do not need to traverse every expression in the workspace.
    ///
    /// # Errors
    ///
    /// Returns an error when a source cannot be read or parsed.
    pub fn build_definitions(workspace: &JavaSourceWorkspace) -> eyre::Result<Self> {
        Self::build_workspace(workspace, false, None, None, None, None)
    }

    /// Live-index SFM sources while allowing an immutable dependency payload
    /// to participate in type and member resolution.
    ///
    /// # Errors
    ///
    /// Returns an error when a live source cannot be read or parsed.
    pub fn build_with_dependencies(
        workspace: &JavaSourceWorkspace,
        dependencies: &DependencyJavaSymbolIndexBody,
        include_usages: bool,
    ) -> eyre::Result<Self> {
        Self::build_workspace(
            workspace,
            include_usages,
            Some(dependencies),
            None,
            None,
            None,
        )
    }

    pub(crate) fn build_dependency_worker(
        workspace: &JavaSourceWorkspace,
        resolution_dependencies: Option<&[JavaDependencyResolutionDefinition]>,
        include_usages: bool,
        diagnostic_limit: usize,
    ) -> eyre::Result<Self> {
        Self::build_workspace(
            workspace,
            include_usages,
            None,
            resolution_dependencies,
            Some(diagnostic_limit),
            None,
        )
    }

    pub(crate) fn from_sharded_query_bodies(
        mut context: JavaAnalysisContextOutput,
        mut live: DependencyJavaSymbolIndexBody,
        mut dependencies: DependencyJavaSymbolIndexBody,
    ) -> Self {
        add_dependency_source_sets(&mut context, &dependencies);
        let live_symbols = live
            .definitions
            .iter()
            .map(|definition| definition.symbol.clone())
            .collect::<BTreeSet<_>>();
        dependencies
            .definitions
            .retain(|definition| !live_symbols.contains(&definition.symbol));

        live.definitions.append(&mut dependencies.definitions);
        live.usages.append(&mut dependencies.usages);
        live.diagnostics.append(&mut dependencies.diagnostics);
        live.definitions.sort();
        live.definitions.dedup();
        live.usages.sort();
        live.usages.dedup();
        live.diagnostics.sort();
        live.diagnostics.dedup();
        context.index_fingerprint =
            index_fingerprint(&[], &live.definitions, &live.usages, &live.diagnostics);

        Self {
            context,
            definitions: live.definitions,
            location_definitions: Vec::new(),
            usages: live.usages,
            diagnostics: live.diagnostics,
        }
    }

    fn build_workspace(
        workspace: &JavaSourceWorkspace,
        include_usages: bool,
        dependencies: Option<&DependencyJavaSymbolIndexBody>,
        resolution_dependencies: Option<&[JavaDependencyResolutionDefinition]>,
        diagnostic_limit: Option<usize>,
        live: Option<&JavaLiveDefinitionSurface>,
    ) -> eyre::Result<Self> {
        let mut files = workspace.files.iter().collect::<Vec<_>>();
        files.sort_by(|left, right| {
            (&left.report_path, &left.source_set, &left.absolute_path).cmp(&(
                &right.report_path,
                &right.source_set,
                &right.absolute_path,
            ))
        });
        let mut parsed = files
            .into_iter()
            .map(|file| JavaSyntaxFile::parse_with_diagnostic_limit(file, diagnostic_limit))
            .collect::<eyre::Result<Vec<_>>>()?;
        let mut jdk_files = workspace
            .jdk_sources
            .syntax_files_for_project(&parsed, &crate::cancellation::CancellationToken::new())?;
        parsed.append(&mut jdk_files);

        let source_sets = parsed
            .iter()
            .map(|file| file.source_set.clone())
            .collect::<BTreeSet<_>>();
        let mut visibility = BTreeSet::new();
        for from in &source_sets {
            for to in &source_sets {
                if workspace.is_visible(from, to) {
                    visibility.insert((from.clone(), to.clone()));
                }
            }
        }
        let mut dependency_sets = dependencies.map_or_else(BTreeSet::new, dependency_source_sets);
        if let Some(resolution_dependencies) = resolution_dependencies {
            dependency_sets.extend(resolution_dependency_source_sets(resolution_dependencies));
        }
        if let Some(live) = live {
            dependency_sets.extend(live.source_sets());
        }
        if !dependency_sets.is_empty() {
            for from in &source_sets {
                for target in &dependency_sets {
                    visibility.insert((from.clone(), target.clone()));
                }
            }
            for from in &dependency_sets {
                for target in &dependency_sets {
                    visibility.insert((from.clone(), target.clone()));
                }
            }
        }
        Ok(Self::build_from_parsed_mode(
            workspace.context.clone(),
            parsed,
            visibility,
            include_usages,
            dependencies,
            resolution_dependencies,
            live,
        ))
    }

    #[must_use]
    pub fn definition(&self, selector: &JavaSymbolSelector) -> SymbolDefinitionOutput {
        let definitions = self
            .matching_definitions(selector)
            .into_iter()
            .cloned()
            .collect::<Vec<_>>();
        let diagnostics = relevant_diagnostics(selector, &definitions, &[], &self.diagnostics);
        let mut context = self.context.clone();
        // A definition report fingerprint describes the canonical surface the
        // query actually exposes. Unrelated declarations and diagnostics may
        // be collected by different bounded implementations without making an
        // otherwise byte-identical definition unstable.
        context.index_fingerprint = index_fingerprint(&[], &definitions, &[], &diagnostics);
        SymbolDefinitionOutput::new(
            outcome_for_match_count(definitions.len()),
            context,
            selector.to_output(),
            definitions,
            diagnostics,
        )
    }

    #[must_use]
    pub fn list(&self, pattern: &JavaSymbolGlob) -> SymbolListOutput {
        let mut definitions = self
            .definitions
            .iter()
            .filter(|definition| pattern.matches(&definition.symbol.canonical_selector()))
            .cloned()
            .collect::<Vec<_>>();
        definitions.sort_by(|left, right| {
            left.symbol
                .canonical_selector()
                .cmp(&right.symbol.canonical_selector())
                .then_with(|| left.symbol.kind.cmp(&right.symbol.kind))
                .then_with(|| {
                    left.identifier_span
                        .source_set
                        .cmp(&right.identifier_span.source_set)
                })
                .then_with(|| left.identifier_span.path.cmp(&right.identifier_span.path))
                .then_with(|| left.identifier_span.cmp(&right.identifier_span))
        });
        let diagnostics = relevant_list_diagnostics(pattern, &definitions, &self.diagnostics);
        SymbolListOutput::new(
            if definitions.is_empty() {
                SymbolCommandOutcome::NoMatch
            } else {
                SymbolCommandOutcome::Success
            },
            self.context.clone(),
            pattern.pattern().to_owned(),
            definitions,
            diagnostics,
        )
    }

    #[must_use]
    pub fn dependency_body(
        &self,
        origins: &[DependencyJavaSourceOrigin],
    ) -> DependencyJavaSymbolIndexBody {
        DependencyJavaSymbolIndexBody::new(
            self.definitions.clone(),
            self.usages.clone(),
            self.diagnostics.clone(),
        )
        .with_portable_origins(origins)
    }

    #[must_use]
    pub fn usages(&self, selector: &JavaSymbolSelector) -> SymbolUsageListOutput {
        let definitions = self.matching_definitions(selector);
        let identities = definitions
            .iter()
            .map(|definition| definition.symbol.clone())
            .collect::<BTreeSet<_>>();
        let usages = self
            .usages
            .iter()
            .filter(|usage| identities.contains(&usage.target))
            .cloned()
            .collect::<Vec<_>>();
        let outcome = outcome_for_match_count(definitions.len());
        let owned_definitions = definitions.into_iter().cloned().collect::<Vec<_>>();
        let diagnostics =
            relevant_diagnostics(selector, &owned_definitions, &usages, &self.diagnostics);
        SymbolUsageListOutput::new(
            outcome,
            self.context.clone(),
            selector.to_output(),
            usages,
            diagnostics,
        )
    }

    pub(crate) fn definition_at_position(
        &self,
        request: &DefinitionAtPositionRequest,
        workspace: &JavaSourceWorkspace,
    ) -> DefinitionAtPositionResult {
        definition_at_position_from_parts(
            self.context.clone(),
            |selected_symbols| {
                self.definitions
                    .iter()
                    .chain(&self.location_definitions)
                    .filter(|definition| selected_symbols.contains(&definition.symbol))
                    .cloned()
                    .collect()
            },
            &self.usages,
            self.diagnostics.iter(),
            request,
            workspace,
        )
    }

    fn matching_definitions(
        &self,
        selector: &JavaSymbolSelector,
    ) -> Vec<&JavaSymbolDefinitionOutput> {
        let exact = self
            .definitions
            .iter()
            .filter(|definition| selector.matches_exact(&definition.symbol))
            .collect::<Vec<_>>();
        if exact.is_empty() {
            self.definitions
                .iter()
                .filter(|definition| selector.matches(&definition.symbol))
                .collect()
        } else {
            exact
        }
    }

    #[cfg(test)]
    fn build_from_parsed(
        context: JavaAnalysisContextOutput,
        files: Vec<JavaSyntaxFile>,
        visibility: BTreeSet<(String, String)>,
    ) -> Self {
        Self::build_from_parsed_mode(context, files, visibility, true, None, None, None)
    }

    fn build_from_parsed_mode(
        mut context: JavaAnalysisContextOutput,
        mut files: Vec<JavaSyntaxFile>,
        visibility: BTreeSet<(String, String)>,
        include_usages: bool,
        dependencies: Option<&DependencyJavaSymbolIndexBody>,
        resolution_dependencies: Option<&[JavaDependencyResolutionDefinition]>,
        live: Option<&JavaLiveDefinitionSurface>,
    ) -> Self {
        files.sort_by(|left, right| {
            (&left.report_path, &left.source_set).cmp(&(&right.report_path, &right.source_set))
        });
        JAVA_PARSER_FINGERPRINT.clone_into(&mut context.parser_fingerprint);

        let mut diagnostics = files
            .iter()
            .flat_map(|file| file.diagnostics.clone())
            .collect::<Vec<_>>();
        if let Some(dependencies) = dependencies {
            diagnostics.extend(dependencies.diagnostics.iter().cloned());
            add_dependency_source_sets(&mut context, dependencies);
        }
        if let Some(live) = live {
            diagnostics.extend(live.diagnostics.iter().cloned());
        }
        let mut types = collect_type_declarations(&files);
        if let Some(live) = live {
            append_live_types(live, &mut types);
        }
        if let Some(dependencies) = dependencies {
            append_dependency_types(dependencies, &mut types);
        }
        if let Some(resolution_dependencies) = resolution_dependencies {
            append_resolution_types(resolution_dependencies, &mut types);
        }
        let type_lookup = TypeLookup::new(&types, visibility);
        let (mut fields, mut methods, mut member_diagnostics) =
            collect_member_declarations(&files, &type_lookup);
        if let Some(live) = live {
            append_live_members(live, &mut fields, &mut methods);
        }
        if let Some(dependencies) = dependencies {
            append_dependency_members(dependencies, &mut fields, &mut methods);
        }
        if let Some(resolution_dependencies) = resolution_dependencies {
            append_resolution_members(resolution_dependencies, &mut fields, &mut methods);
        }
        fields.sort();
        fields.dedup();
        methods.sort();
        methods.dedup();
        diagnostics.append(&mut member_diagnostics);

        let field_lookup = member_lookup(fields.iter().map(FieldDeclaration::symbol));
        let method_lookup = member_lookup(methods.iter().map(MethodDeclaration::symbol));
        let resolution_files = files
            .iter()
            .enumerate()
            .map(|(sequence, file)| JavaFileFactIdentity {
                sequence: u64::try_from(sequence).unwrap_or(u64::MAX),
                report_path: file.report_path.clone(),
                source_set: file.source_set.clone(),
                source_hash: file.source_hash.clone(),
            })
            .collect();
        let model = JavaIndexModel {
            files,
            resolution: Arc::new(JavaDefinitionResolutionSurface {
                context: context.clone(),
                files: resolution_files,
                types,
                fields,
                methods,
                type_lookup,
                field_lookup,
                method_lookup,
                diagnostics: diagnostics.clone(),
            }),
        };
        Self::finish_build(context, &model, include_usages, dependencies, diagnostics)
    }

    fn finish_build(
        mut context: JavaAnalysisContextOutput,
        model: &JavaIndexModel,
        include_usages: bool,
        dependencies: Option<&DependencyJavaSymbolIndexBody>,
        mut diagnostics: Vec<JavaAnalysisDiagnosticOutput>,
    ) -> Self {
        let mut usages = dependencies
            .filter(|_| include_usages)
            .map_or_else(Vec::new, |dependencies| dependencies.usages.clone());
        let mut location_definitions = Vec::new();
        if include_usages {
            usages.extend(declaration_usages(model));
            for file_index in 0..model.files.len() {
                let (mut file_usages, mut file_diagnostics, mut file_location_definitions) =
                    collect_file_usages(model, file_index);
                usages.append(&mut file_usages);
                diagnostics.append(&mut file_diagnostics);
                location_definitions.append(&mut file_location_definitions);
            }
        }

        let mut definitions = model
            .resolution
            .types
            .iter()
            .filter_map(TypeDeclaration::output)
            .cloned()
            .chain(
                model
                    .resolution
                    .fields
                    .iter()
                    .filter_map(FieldDeclaration::output)
                    .cloned(),
            )
            .chain(
                model
                    .resolution
                    .methods
                    .iter()
                    .filter_map(MethodDeclaration::output)
                    .cloned(),
            )
            .collect::<Vec<_>>();
        definitions.sort();
        definitions.dedup();
        location_definitions.sort();
        location_definitions.dedup();
        usages.sort();
        usages.dedup();
        diagnostics.sort();
        diagnostics.dedup();
        let evidence = model
            .files
            .iter()
            .map(IndexSourceEvidence::from_file)
            .collect::<Vec<_>>();
        let mut fingerprint_definitions = definitions.clone();
        fingerprint_definitions.extend(location_definitions.iter().cloned());
        fingerprint_definitions.sort();
        fingerprint_definitions.dedup();
        context.index_fingerprint =
            index_fingerprint(&evidence, &fingerprint_definitions, &usages, &diagnostics);

        Self {
            context,
            definitions,
            location_definitions,
            usages,
            diagnostics,
        }
    }
}

fn definition_at_position_from_parts<'diagnostic>(
    mut context: JavaAnalysisContextOutput,
    resolve_definitions: impl FnOnce(
        &BTreeSet<JavaSymbolIdentityOutput>,
    ) -> Vec<JavaSymbolDefinitionOutput>,
    usages: &[JavaSymbolUsageOutput],
    diagnostics: impl IntoIterator<Item = &'diagnostic JavaAnalysisDiagnosticOutput>,
    request: &DefinitionAtPositionRequest,
    workspace: &JavaSourceWorkspace,
) -> DefinitionAtPositionResult {
    let current_source_hash = blake3_content_hash(&request.document.text);
    let offset = request.position.byte_offset;
    let containing = usages
        .iter()
        .filter(|usage| {
            usage.span.path == request.document.report_path
                && usage.span.source_set == request.document.source_set
                && usage.span.source_hash == current_source_hash
                && usage.span.start_byte <= offset
                && offset < usage.span.end_byte
        })
        .collect::<Vec<_>>();
    let narrowest_width = containing
        .iter()
        .map(|usage| usage.span.end_byte.saturating_sub(usage.span.start_byte))
        .min();
    let mut symbols = containing
        .into_iter()
        .filter(|usage| {
            narrowest_width.is_some_and(|width| {
                usage.span.end_byte.saturating_sub(usage.span.start_byte) == width
            })
        })
        .map(|usage| usage.target.clone())
        .collect::<Vec<_>>();
    symbols.sort();
    symbols.dedup();

    let selected_symbols = symbols.iter().cloned().collect::<BTreeSet<_>>();
    let mut report_definitions = resolve_definitions(&selected_symbols);
    report_definitions.sort();
    report_definitions.dedup();
    let outcome = if symbols.is_empty() {
        DefinitionAtPositionOutcome::NoSymbol
    } else if symbols.len() > 1 || report_definitions.len() > 1 {
        DefinitionAtPositionOutcome::Ambiguous
    } else if report_definitions.is_empty() {
        DefinitionAtPositionOutcome::NoDefinition
    } else {
        DefinitionAtPositionOutcome::Success
    };
    let mut diagnostics = diagnostics
        .into_iter()
        .filter(|diagnostic| {
            diagnostic.span.as_ref().is_some_and(|span| {
                span.path == request.document.report_path
                    && span.source_set == request.document.source_set
                    && span.source_hash == current_source_hash
            })
        })
        .cloned()
        .collect::<Vec<_>>();
    diagnostics.sort();
    diagnostics.dedup();

    context.index_fingerprint = index_fingerprint(
        &[IndexSourceEvidence {
            report_path: request.document.report_path.clone(),
            source_set: request.document.source_set.clone(),
            source_hash: current_source_hash,
        }],
        &report_definitions,
        &[],
        &diagnostics,
    );
    let mut definitions = report_definitions
        .iter()
        .map(|definition| definition_at_position_definition(definition, workspace, request))
        .collect::<Vec<_>>();
    definitions.sort();
    definitions.dedup();

    DefinitionAtPositionResult {
        schema: super::DEFINITION_AT_POSITION_RESULT_SCHEMA.to_owned(),
        request_id: request.request_id,
        request_generation: request.request_generation,
        workspace_generation: request.workspace.workspace_generation,
        outcome,
        context,
        document: DefinitionDocumentIdentityOutput::from(&request.document),
        position: request.position,
        symbols,
        definitions,
        completeness: super::SymbolQueryCompleteness::Complete,
        diagnostics,
        recovery_actions: Vec::new(),
        dependency_index: None,
    }
}

pub(crate) fn validate_definition_request_workspace(
    workspace: &JavaSourceWorkspace,
    request: &DefinitionAtPositionRequest,
) -> eyre::Result<()> {
    validate_definition_request_workspace_identity(workspace, request)?;
    let matches = workspace
        .files
        .iter()
        .filter(|file| {
            file.root_id == request.document.root_id
                && file.root_relative_path == request.document.root_relative_path
        })
        .collect::<Vec<_>>();
    let [file] = matches.as_slice() else {
        eyre::bail!(
            "definition document `{}`:`{}` matched {} workspace files",
            request.document.root_id,
            request.document.root_relative_path,
            matches.len()
        );
    };
    validate_definition_document_projection(file, request, "workspace")
}

pub(crate) fn validate_definition_request_workspace_identity(
    workspace: &JavaSourceWorkspace,
    request: &DefinitionAtPositionRequest,
) -> eyre::Result<()> {
    request.validate()?;
    let requested = &request.workspace;
    let actual = &workspace.context;
    if requested.branch != actual.branch {
        eyre::bail!(
            "definition request branch `{}` does not match workspace branch `{}`",
            requested.branch,
            actual.branch
        );
    }
    if requested.classpath_mode != actual.classpath_mode {
        eyre::bail!("definition request classpath mode does not match the workspace");
    }
    if requested.classpath_fingerprint != actual.classpath_fingerprint {
        eyre::bail!("definition request classpath fingerprint does not match the workspace");
    }
    if requested.source_roots != actual.source_roots {
        eyre::bail!("definition request source-root projection does not match the workspace");
    }
    let expected_workspace_fingerprint =
        definition_workspace_fingerprint(actual, requested.dependency_index_identity.as_deref())?;
    if requested.workspace_fingerprint != expected_workspace_fingerprint {
        eyre::bail!("definition request workspace fingerprint does not match the workspace");
    }
    Ok(())
}

pub(crate) fn validate_definition_document_projection(
    file: &JavaSourceFile,
    request: &DefinitionAtPositionRequest,
    resolver_id: &str,
) -> eyre::Result<()> {
    if file.report_path != request.document.report_path {
        eyre::bail!("definition document report path does not match the resolved source file");
    }
    if file.source_set != request.document.source_set {
        eyre::bail!("definition document source set does not match the resolved source file");
    }
    if resolver_id != "workspace" {
        let expected_address =
            super::contributed_address(resolver_id, &file.root_id, &file.root_relative_path);
        if request.document.address != expected_address {
            eyre::bail!(
                "definition document address does not match the resolved `{resolver_id}` source file"
            );
        }
    }
    Ok(())
}

pub(crate) fn definition_at_position_definition(
    definition: &JavaSymbolDefinitionOutput,
    workspace: &JavaSourceWorkspace,
    request: &DefinitionAtPositionRequest,
) -> DefinitionAtPositionDefinitionOutput {
    DefinitionAtPositionDefinitionOutput {
        symbol: definition.symbol.clone(),
        identifier_span: definition_at_position_span(
            &definition.identifier_span,
            workspace,
            request,
        ),
        declaration_span: definition_at_position_span(
            &definition.declaration_span,
            workspace,
            request,
        ),
        confidence: definition.confidence,
    }
}

struct SnapshotDefinitionSpanResolver<'source> {
    files: BTreeMap<(String, String), (&'source super::JavaSourceFile, &'source str, bool)>,
    witnesses: BTreeMap<(String, String, String), Option<String>>,
}

impl<'source> SnapshotDefinitionSpanResolver<'source> {
    fn new(
        workspace: &super::JavaSourceWorkspace,
        files: &'source [super::JavaSourceFile],
        sources: &'source [String],
    ) -> Self {
        let mut ordered = files
            .iter()
            .zip(sources.iter().map(String::as_str))
            .collect::<Vec<_>>();
        ordered.sort_by(|(left, _), (right, _)| {
            (&left.root_id, &left.root_relative_path, &left.absolute_path).cmp(&(
                &right.root_id,
                &right.root_relative_path,
                &right.absolute_path,
            ))
        });
        let mut by_report_identity = BTreeMap::new();
        for (file, source) in ordered {
            let editable = workspace.files.iter().any(|candidate| {
                candidate.root_id == file.root_id
                    && candidate.root_relative_path == file.root_relative_path
            });
            by_report_identity
                .entry((file.report_path.clone(), file.source_set.clone()))
                .or_insert((file, source, editable));
        }
        Self {
            files: by_report_identity,
            witnesses: BTreeMap::new(),
        }
    }

    fn resolve(&mut self, span: &JavaSourceSpanOutput) -> DefinitionSourceSpanOutput {
        let key = (span.path.clone(), span.source_set.clone());
        if let Some((file, source, editable)) = self.files.get(&key).copied() {
            let witness_key = (
                span.path.clone(),
                span.source_set.clone(),
                span.source_hash.clone(),
            );
            let source_sha256 = self
                .witnesses
                .entry(witness_key)
                .or_insert_with(|| sha256_witness_for_blake3(&span.source_hash, source))
                .clone();
            if editable {
                return DefinitionSourceSpanOutput::from_report_span(
                    span,
                    "workspace",
                    file.root_id.clone(),
                    file.root_relative_path.clone(),
                    format!("workspace://{}/{}", file.root_id, file.root_relative_path),
                    source_sha256,
                );
            }
            return DefinitionSourceSpanOutput::from_report_span(
                span,
                "dependency-index",
                "dependency-index",
                span.path.clone(),
                format!("dependency-index://dependency-index/{}", span.path),
                source_sha256,
            );
        }
        DefinitionSourceSpanOutput::from_report_span(
            span,
            "dependency-index",
            "dependency-index",
            span.path.clone(),
            format!("dependency-index://dependency-index/{}", span.path),
            None,
        )
    }
}

pub(crate) fn definition_at_position_span(
    span: &JavaSourceSpanOutput,
    workspace: &JavaSourceWorkspace,
    request: &DefinitionAtPositionRequest,
) -> DefinitionSourceSpanOutput {
    let mut matching_files = workspace
        .files
        .iter()
        .filter(|file| file.report_path == span.path && file.source_set == span.source_set)
        .collect::<Vec<_>>();
    matching_files.sort_by(|left, right| {
        (&left.root_id, &left.root_relative_path, &left.absolute_path).cmp(&(
            &right.root_id,
            &right.root_relative_path,
            &right.absolute_path,
        ))
    });
    if let Some(file) = matching_files.first() {
        let source_sha256 = verified_source_sha256(span, file, request);
        return DefinitionSourceSpanOutput::from_report_span(
            span,
            "workspace",
            file.root_id.clone(),
            file.root_relative_path.clone(),
            format!("workspace://{}/{}", file.root_id, file.root_relative_path),
            source_sha256,
        );
    }
    DefinitionSourceSpanOutput::from_report_span(
        span,
        "dependency-index",
        "dependency-index",
        span.path.clone(),
        format!("dependency-index://dependency-index/{}", span.path),
        None,
    )
}

fn verified_source_sha256(
    span: &JavaSourceSpanOutput,
    file: &super::JavaSourceFile,
    request: &DefinitionAtPositionRequest,
) -> Option<String> {
    if file.root_id == request.document.root_id
        && file.root_relative_path == request.document.root_relative_path
        && file.report_path == request.document.report_path
        && file.source_set == request.document.source_set
    {
        return sha256_witness_for_blake3(&span.source_hash, &request.document.text);
    }
    if let Some(source) = file.source_override.as_deref() {
        return sha256_witness_for_blake3(&span.source_hash, source);
    }
    let source = std::fs::read_to_string(&file.absolute_path).ok()?;
    sha256_witness_for_blake3(&span.source_hash, &source)
}

fn sha256_witness_for_blake3(expected_blake3: &str, source: &str) -> Option<String> {
    (blake3_content_hash(source) == expected_blake3).then(|| sha256_content_hash(source))
}

pub(crate) fn normalize_definition_at_position_context(
    mut context: JavaAnalysisContextOutput,
    dependencies: Option<&DependencyJavaSymbolIndexBody>,
    result: &mut DefinitionAtPositionResult,
) {
    if let Some(dependencies) = dependencies {
        add_dependency_source_sets(&mut context, dependencies);
    }
    JAVA_PARSER_FINGERPRINT.clone_into(&mut context.parser_fingerprint);
    context
        .index_fingerprint
        .clone_from(&result.context.index_fingerprint);
    result.context = context;
}

pub(crate) fn normalize_usage_at_position_context(
    mut context: JavaAnalysisContextOutput,
    dependencies: Option<&DependencyJavaSymbolIndexBody>,
    result: &mut UsageAtPositionResult,
) {
    if let Some(dependencies) = dependencies {
        add_dependency_source_sets(&mut context, dependencies);
    }
    JAVA_PARSER_FINGERPRINT.clone_into(&mut context.parser_fingerprint);
    context
        .index_fingerprint
        .clone_from(&result.context.index_fingerprint);
    result.context = context;
}

#[derive(Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
enum IndexedDeclaration {
    Report(Box<JavaSymbolDefinitionOutput>),
    Resolution(JavaDependencyResolutionDefinition),
}

impl IndexedDeclaration {
    fn symbol(&self) -> &JavaSymbolIdentityOutput {
        match self {
            Self::Report(output) => &output.symbol,
            Self::Resolution(definition) => &definition.symbol,
        }
    }

    fn source_set(&self) -> &str {
        match self {
            Self::Report(output) => &output.identifier_span.source_set,
            Self::Resolution(definition) => &definition.source_set,
        }
    }

    fn output(&self) -> Option<&JavaSymbolDefinitionOutput> {
        match self {
            Self::Report(output) => Some(output.as_ref()),
            Self::Resolution(_) => None,
        }
    }
}

#[derive(Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
struct TypeDeclaration {
    declaration: IndexedDeclaration,
}

impl TypeDeclaration {
    fn symbol(&self) -> &JavaSymbolIdentityOutput {
        self.declaration.symbol()
    }

    fn source_set(&self) -> &str {
        self.declaration.source_set()
    }

    fn output(&self) -> Option<&JavaSymbolDefinitionOutput> {
        self.declaration.output()
    }
}

#[derive(Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
struct FieldDeclaration {
    declaration: IndexedDeclaration,
    value_type: Option<String>,
}

impl FieldDeclaration {
    fn symbol(&self) -> &JavaSymbolIdentityOutput {
        self.declaration.symbol()
    }

    fn source_set(&self) -> &str {
        self.declaration.source_set()
    }

    fn output(&self) -> Option<&JavaSymbolDefinitionOutput> {
        self.declaration.output()
    }
}

#[derive(Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
struct MethodDeclaration {
    declaration: IndexedDeclaration,
    parameter_types: Vec<String>,
    return_type: Option<String>,
}

impl MethodDeclaration {
    fn symbol(&self) -> &JavaSymbolIdentityOutput {
        self.declaration.symbol()
    }

    fn source_set(&self) -> &str {
        self.declaration.source_set()
    }

    fn output(&self) -> Option<&JavaSymbolDefinitionOutput> {
        self.declaration.output()
    }
}

#[derive(Clone, Debug)]
struct IndexSourceEvidence {
    report_path: String,
    source_set: String,
    source_hash: String,
}

impl IndexSourceEvidence {
    fn from_file(file: &JavaSyntaxFile) -> Self {
        Self {
            report_path: file.report_path.clone(),
            source_set: file.source_set.clone(),
            source_hash: file.source_hash.clone(),
        }
    }
}

fn dependency_source_sets(dependencies: &DependencyJavaSymbolIndexBody) -> BTreeSet<String> {
    dependencies
        .definitions
        .iter()
        .flat_map(|definition| {
            [
                definition.identifier_span.source_set.clone(),
                definition.declaration_span.source_set.clone(),
            ]
        })
        .chain(
            dependencies
                .usages
                .iter()
                .map(|usage| usage.span.source_set.clone()),
        )
        .chain(
            dependencies
                .diagnostics
                .iter()
                .filter_map(|diagnostic| diagnostic.span.as_ref())
                .map(|span| span.source_set.clone()),
        )
        .collect()
}

fn resolution_dependency_source_sets(
    dependencies: &[JavaDependencyResolutionDefinition],
) -> BTreeSet<String> {
    dependencies
        .iter()
        .map(|definition| definition.source_set.clone())
        .collect()
}

fn add_dependency_source_sets(
    context: &mut JavaAnalysisContextOutput,
    dependencies: &DependencyJavaSymbolIndexBody,
) {
    let dependency_sets = dependency_source_sets(dependencies);
    let jdk_sets = context
        .source_roots
        .iter()
        .filter(|root| root.kind == super::JavaSourceRootKind::Jdk)
        .map(|root| root.source_set.clone())
        .collect::<BTreeSet<_>>();
    let mut dependency_visibility = dependency_sets.clone();
    dependency_visibility.extend(jdk_sets.iter().cloned());
    for source_set in &mut context.source_sets {
        source_set
            .visible_source_sets
            .extend(dependency_sets.iter().cloned());
        if dependency_sets.contains(&source_set.id) {
            source_set
                .visible_source_sets
                .extend(jdk_sets.iter().cloned());
        }
        source_set.visible_source_sets.sort();
        source_set.visible_source_sets.dedup();
    }
    for source_set in &dependency_sets {
        if context
            .source_sets
            .iter()
            .any(|existing| existing.id == *source_set)
        {
            continue;
        }
        context.source_sets.push(super::JavaSourceSetOutput {
            id: source_set.clone(),
            visible_source_sets: dependency_visibility.iter().cloned().collect(),
        });
    }
    context
        .source_sets
        .sort_by(|left, right| left.id.cmp(&right.id));
}

fn append_live_types(live: &JavaLiveDefinitionSurface, types: &mut Vec<TypeDeclaration>) {
    let local_symbols = types
        .iter()
        .map(TypeDeclaration::symbol)
        .cloned()
        .collect::<BTreeSet<_>>();
    types.extend(
        live.types
            .iter()
            .filter(|definition| !local_symbols.contains(&definition.symbol))
            .cloned()
            .map(|definition| TypeDeclaration {
                declaration: IndexedDeclaration::Report(Box::new(definition)),
            }),
    );
    types.sort();
    types.dedup();
}

fn append_live_members(
    live: &JavaLiveDefinitionSurface,
    fields: &mut Vec<FieldDeclaration>,
    methods: &mut Vec<MethodDeclaration>,
) {
    let local_fields = fields
        .iter()
        .map(FieldDeclaration::symbol)
        .cloned()
        .collect::<BTreeSet<_>>();
    fields.extend(
        live.fields
            .iter()
            .filter(|field| !local_fields.contains(&field.definition.symbol))
            .cloned()
            .map(|field| FieldDeclaration {
                declaration: IndexedDeclaration::Report(Box::new(field.definition)),
                value_type: field.value_type,
            }),
    );
    let local_methods = methods
        .iter()
        .map(MethodDeclaration::symbol)
        .cloned()
        .collect::<BTreeSet<_>>();
    methods.extend(
        live.methods
            .iter()
            .filter(|method| !local_methods.contains(&method.definition.symbol))
            .cloned()
            .map(|method| MethodDeclaration {
                declaration: IndexedDeclaration::Report(Box::new(method.definition)),
                parameter_types: method.parameter_types,
                return_type: method.return_type,
            }),
    );
    fields.sort();
    fields.dedup();
    methods.sort();
    methods.dedup();
}

fn append_dependency_types(
    dependencies: &DependencyJavaSymbolIndexBody,
    types: &mut Vec<TypeDeclaration>,
) {
    let local_symbols = types
        .iter()
        .map(TypeDeclaration::symbol)
        .cloned()
        .collect::<BTreeSet<_>>();
    for definition in &dependencies.definitions {
        match definition.symbol.kind {
            JavaSymbolKind::Class
            | JavaSymbolKind::Interface
            | JavaSymbolKind::Enum
            | JavaSymbolKind::Record
            | JavaSymbolKind::Annotation
                if !local_symbols.contains(&definition.symbol) =>
            {
                types.push(TypeDeclaration {
                    declaration: IndexedDeclaration::Report(Box::new(definition.clone())),
                });
            }
            _ => {}
        }
    }
    types.sort();
    types.dedup();
}

fn append_resolution_types(
    dependencies: &[JavaDependencyResolutionDefinition],
    types: &mut Vec<TypeDeclaration>,
) {
    let local_symbols = types
        .iter()
        .map(TypeDeclaration::symbol)
        .cloned()
        .collect::<BTreeSet<_>>();
    types.extend(
        dependencies
            .iter()
            .filter(|definition| {
                is_type_kind(definition.symbol.kind) && !local_symbols.contains(&definition.symbol)
            })
            .cloned()
            .map(|definition| TypeDeclaration {
                declaration: IndexedDeclaration::Resolution(definition),
            }),
    );
    types.sort();
    types.dedup();
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

fn append_dependency_members(
    dependencies: &DependencyJavaSymbolIndexBody,
    fields: &mut Vec<FieldDeclaration>,
    methods: &mut Vec<MethodDeclaration>,
) {
    let local_fields = fields
        .iter()
        .map(FieldDeclaration::symbol)
        .cloned()
        .collect::<BTreeSet<_>>();
    let local_methods = methods
        .iter()
        .map(MethodDeclaration::symbol)
        .cloned()
        .collect::<BTreeSet<_>>();
    for definition in &dependencies.definitions {
        match definition.symbol.kind {
            JavaSymbolKind::Field if !local_fields.contains(&definition.symbol) => {
                fields.push(FieldDeclaration {
                    declaration: IndexedDeclaration::Report(Box::new(definition.clone())),
                    value_type: None,
                });
            }
            JavaSymbolKind::Method | JavaSymbolKind::Constructor
                if !local_methods.contains(&definition.symbol) =>
            {
                let (parameter_types, return_type) = definition
                    .symbol
                    .descriptor
                    .as_deref()
                    .and_then(decode_method_descriptor)
                    .unwrap_or_default();
                methods.push(MethodDeclaration {
                    declaration: IndexedDeclaration::Report(Box::new(definition.clone())),
                    parameter_types,
                    return_type,
                });
            }
            _ => {}
        }
    }
    fields.sort();
    fields.dedup();
    methods.sort();
    methods.dedup();
}

fn append_resolution_members(
    dependencies: &[JavaDependencyResolutionDefinition],
    fields: &mut Vec<FieldDeclaration>,
    methods: &mut Vec<MethodDeclaration>,
) {
    let local_fields = fields
        .iter()
        .map(FieldDeclaration::symbol)
        .cloned()
        .collect::<BTreeSet<_>>();
    let local_methods = methods
        .iter()
        .map(MethodDeclaration::symbol)
        .cloned()
        .collect::<BTreeSet<_>>();
    for definition in dependencies {
        match definition.symbol.kind {
            JavaSymbolKind::Field if !local_fields.contains(&definition.symbol) => {
                fields.push(FieldDeclaration {
                    declaration: IndexedDeclaration::Resolution(definition.clone()),
                    value_type: None,
                });
            }
            JavaSymbolKind::Method | JavaSymbolKind::Constructor
                if !local_methods.contains(&definition.symbol) =>
            {
                let (parameter_types, return_type) = definition
                    .symbol
                    .descriptor
                    .as_deref()
                    .and_then(decode_method_descriptor)
                    .unwrap_or_default();
                methods.push(MethodDeclaration {
                    declaration: IndexedDeclaration::Resolution(definition.clone()),
                    parameter_types,
                    return_type,
                });
            }
            _ => {}
        }
    }
    fields.sort();
    fields.dedup();
    methods.sort();
    methods.dedup();
}

fn decode_method_descriptor(descriptor: &str) -> Option<(Vec<String>, Option<String>)> {
    let bytes = descriptor.as_bytes();
    if bytes.first() != Some(&b'(') {
        return None;
    }
    let mut cursor = 1;
    let mut parameters = Vec::new();
    while bytes.get(cursor) != Some(&b')') {
        parameters.push(decode_descriptor_type(bytes, &mut cursor, false)?);
    }
    cursor += 1;
    let return_type = decode_descriptor_type(bytes, &mut cursor, true)?;
    (cursor == bytes.len()).then_some((parameters, (return_type != "void").then_some(return_type)))
}

fn decode_descriptor_type(bytes: &[u8], cursor: &mut usize, allow_void: bool) -> Option<String> {
    let kind = *bytes.get(*cursor)?;
    *cursor += 1;
    let primitive = match kind {
        b'B' => Some("byte"),
        b'C' => Some("char"),
        b'D' => Some("double"),
        b'F' => Some("float"),
        b'I' => Some("int"),
        b'J' => Some("long"),
        b'S' => Some("short"),
        b'Z' => Some("boolean"),
        b'V' if allow_void => Some("void"),
        _ => None,
    };
    if let Some(primitive) = primitive {
        return Some(primitive.to_owned());
    }
    match kind {
        b'L' => {
            let relative_end = bytes
                .get(*cursor..)?
                .iter()
                .position(|byte| *byte == b';')?;
            let end = *cursor + relative_end;
            let name = std::str::from_utf8(bytes.get(*cursor..end)?).ok()?;
            *cursor = end + 1;
            Some(name.replace('/', "."))
        }
        b'[' => decode_descriptor_type(bytes, cursor, false).map(|inner| format!("{inner}[]")),
        _ => None,
    }
}

struct JavaIndexModel {
    files: Vec<JavaSyntaxFile>,
    resolution: Arc<JavaDefinitionResolutionSurface>,
}

fn member_lookup<'a>(
    members: impl IntoIterator<Item = &'a JavaSymbolIdentityOutput>,
) -> BTreeMap<(String, String), Vec<usize>> {
    let mut lookup = BTreeMap::<(String, String), Vec<usize>>::new();
    for (index, symbol) in members.into_iter().enumerate() {
        lookup
            .entry((symbol.owner.clone(), symbol.name.clone()))
            .or_default()
            .push(index);
    }
    lookup
}

#[derive(Clone, Debug)]
struct TypeLookup {
    by_name: BTreeMap<String, Vec<usize>>,
    visibility: BTreeMap<String, BTreeSet<String>>,
    types: Vec<LookupTypeDeclaration>,
}

#[derive(Clone, Debug)]
struct LookupTypeDeclaration {
    qualified_name: String,
    source_set: String,
}

impl TypeLookup {
    fn new(types: &[TypeDeclaration], visibility: BTreeSet<(String, String)>) -> Self {
        let mut by_name = BTreeMap::<String, Vec<usize>>::new();
        for (index, declaration) in types.iter().enumerate() {
            let qualified = &declaration.symbol().qualified_name;
            by_name.entry(qualified.clone()).or_default().push(index);
            if qualified.contains('$') {
                by_name
                    .entry(qualified.replace('$', "."))
                    .or_default()
                    .push(index);
            }
        }
        for indices in by_name.values_mut() {
            indices.sort_unstable();
            indices.dedup();
        }
        let mut visibility_by_source = BTreeMap::<String, BTreeSet<String>>::new();
        for (from, to) in visibility {
            visibility_by_source.entry(from).or_default().insert(to);
        }
        Self {
            by_name,
            visibility: visibility_by_source,
            types: types
                .iter()
                .map(|declaration| LookupTypeDeclaration {
                    qualified_name: declaration.symbol().qualified_name.clone(),
                    source_set: declaration.source_set().to_owned(),
                })
                .collect(),
        }
    }

    fn is_visible(&self, from: &str, to: &str) -> bool {
        from == to
            || self
                .visibility
                .get(from)
                .is_some_and(|targets| targets.contains(to))
    }

    fn visible_indices(&self, qualified_name: &str, from: &str) -> Vec<usize> {
        self.by_name
            .get(qualified_name)
            .into_iter()
            .flatten()
            .copied()
            .filter(|index| self.is_visible(from, &self.types[*index].source_set))
            .collect()
    }

    fn all_indices(&self, qualified_name: &str) -> Vec<usize> {
        self.by_name
            .get(qualified_name)
            .cloned()
            .unwrap_or_default()
    }
}

#[derive(Clone, Debug)]
struct RawFieldDeclaration {
    owner: String,
    name_node_range: std::ops::Range<usize>,
    declaration_range: std::ops::Range<usize>,
    raw_type: String,
    file_index: usize,
}

#[derive(Clone, Debug)]
struct RawMethodDeclaration {
    owner: String,
    name: String,
    name_node_range: std::ops::Range<usize>,
    declaration_range: std::ops::Range<usize>,
    raw_parameter_types: Vec<String>,
    raw_return_type: String,
    kind: JavaSymbolKind,
    file_index: usize,
}

#[derive(Clone, Debug)]
struct ResolvedType {
    qualified_name: String,
    source_indices: Vec<usize>,
}

#[derive(Clone, Debug)]
enum TypeResolutionFailure {
    Unresolved(String),
    Ambiguous {
        name: String,
        source_indices: Vec<usize>,
    },
    Inaccessible(String),
}

fn outcome_for_match_count(count: usize) -> SymbolCommandOutcome {
    match count {
        0 => SymbolCommandOutcome::NoMatch,
        1 => SymbolCommandOutcome::Success,
        _ => SymbolCommandOutcome::Ambiguous,
    }
}

fn collect_type_declarations(files: &[JavaSyntaxFile]) -> Vec<TypeDeclaration> {
    let mut declarations = Vec::new();
    for file in files {
        collect_type_declarations_from_node(file, file.tree.root_node(), None, &mut declarations);
    }
    declarations.sort();
    declarations
}

fn collect_type_declarations_from_node(
    file: &JavaSyntaxFile,
    node: Node<'_>,
    enclosing_owner: Option<&str>,
    declarations: &mut Vec<TypeDeclaration>,
) {
    if is_nonsemantic_literal_or_comment(node.kind()) {
        return;
    }
    if is_type_declaration(node.kind()) {
        let Some(name_node) = declaration_name_node(node) else {
            return;
        };
        let Some(name) = file.text(name_node) else {
            return;
        };
        let qualified_name = enclosing_owner.map_or_else(
            || qualify_name(&file.package_name, name),
            |owner| format!("{owner}${name}"),
        );
        let owner = enclosing_owner.map_or_else(|| file.package_name.clone(), ToOwned::to_owned);
        declarations.push(TypeDeclaration {
            declaration: IndexedDeclaration::Report(Box::new(JavaSymbolDefinitionOutput {
                symbol: JavaSymbolIdentityOutput {
                    kind: java_type_kind(node.kind()),
                    owner,
                    name: name.to_owned(),
                    descriptor: None,
                    qualified_name: qualified_name.clone(),
                },
                identifier_span: file.span(name_node),
                declaration_span: file.span(node),
                confidence: ResolutionConfidence::Resolved,
            })),
        });
        for child in named_children(node) {
            collect_type_declarations_from_node(file, child, Some(&qualified_name), declarations);
        }
        return;
    }
    for child in named_children(node) {
        collect_type_declarations_from_node(file, child, enclosing_owner, declarations);
    }
}

fn java_type_kind(kind: &str) -> JavaSymbolKind {
    match kind {
        "interface_declaration" => JavaSymbolKind::Interface,
        "enum_declaration" => JavaSymbolKind::Enum,
        "record_declaration" => JavaSymbolKind::Record,
        "annotation_type_declaration" => JavaSymbolKind::Annotation,
        _ => JavaSymbolKind::Class,
    }
}

#[expect(
    clippy::too_many_lines,
    reason = "field and callable declarations share one ordered resolution pass"
)]
fn collect_member_declarations(
    files: &[JavaSyntaxFile],
    type_lookup: &TypeLookup,
) -> (
    Vec<FieldDeclaration>,
    Vec<MethodDeclaration>,
    Vec<JavaAnalysisDiagnosticOutput>,
) {
    let mut raw_fields = Vec::new();
    let mut raw_methods = Vec::new();
    for (file_index, file) in files.iter().enumerate() {
        collect_raw_members(
            file,
            file_index,
            file.tree.root_node(),
            None,
            &mut raw_fields,
            &mut raw_methods,
        );
    }

    raw_fields.sort_by(|left, right| {
        (
            &left.owner,
            left.name_node_range.start,
            left.name_node_range.end,
            left.file_index,
        )
            .cmp(&(
                &right.owner,
                right.name_node_range.start,
                right.name_node_range.end,
                right.file_index,
            ))
    });
    raw_methods.sort_by(|left, right| {
        (
            &left.owner,
            &left.name,
            left.name_node_range.start,
            left.name_node_range.end,
            left.file_index,
        )
            .cmp(&(
                &right.owner,
                &right.name,
                right.name_node_range.start,
                right.name_node_range.end,
                right.file_index,
            ))
    });

    let mut diagnostics = Vec::new();
    let mut fields = Vec::new();
    for raw in raw_fields {
        let file = &files[raw.file_index];
        let value_type = match resolve_java_type(&raw.raw_type, file, &raw.owner, type_lookup) {
            Ok(resolved) => Some(resolved.qualified_name),
            Err(failure) => {
                diagnostics.push(type_resolution_diagnostic(
                    failure,
                    file.span_for_range(raw.name_node_range.clone()),
                    "field type",
                ));
                None
            }
        };
        let name = file
            .source
            .get(raw.name_node_range.clone())
            .unwrap_or("<unknown>")
            .to_owned();
        let confidence = if value_type.is_some() {
            ResolutionConfidence::Resolved
        } else {
            ResolutionConfidence::PartiallyResolved
        };
        fields.push(FieldDeclaration {
            declaration: IndexedDeclaration::Report(Box::new(JavaSymbolDefinitionOutput {
                symbol: JavaSymbolIdentityOutput {
                    kind: JavaSymbolKind::Field,
                    owner: raw.owner.clone(),
                    name: name.clone(),
                    descriptor: None,
                    qualified_name: format!("{}.{name}", raw.owner),
                },
                identifier_span: file.span_for_range(raw.name_node_range),
                declaration_span: file.span_for_range(raw.declaration_range),
                confidence,
            })),
            value_type,
        });
    }

    let mut methods = Vec::new();
    for raw in raw_methods {
        let file = &files[raw.file_index];
        let mut parameter_types = Vec::new();
        let mut parameter_descriptors = Vec::new();
        let mut complete = true;
        for raw_parameter in &raw.raw_parameter_types {
            match resolve_java_type_with_descriptor(
                raw_parameter,
                file,
                &raw.owner,
                type_lookup,
                false,
            ) {
                Ok((resolved, descriptor)) => {
                    parameter_types.push(resolved.qualified_name);
                    parameter_descriptors.push(descriptor);
                }
                Err(failure) => {
                    complete = false;
                    diagnostics.push(type_resolution_diagnostic(
                        failure,
                        file.span_for_range(raw.name_node_range.clone()),
                        "method parameter type",
                    ));
                }
            }
        }
        let return_resolution = resolve_java_type_with_descriptor(
            &raw.raw_return_type,
            file,
            &raw.owner,
            type_lookup,
            true,
        );
        let (return_type, return_descriptor) = match return_resolution {
            Ok((resolved, descriptor)) => (Some(resolved.qualified_name), Some(descriptor)),
            Err(failure) => {
                complete = false;
                diagnostics.push(type_resolution_diagnostic(
                    failure,
                    file.span_for_range(raw.name_node_range.clone()),
                    "method return type",
                ));
                (None, None)
            }
        };
        let descriptor = (complete && raw.raw_parameter_types.len() == parameter_descriptors.len())
            .then(|| {
                format!(
                    "({}){}",
                    parameter_descriptors.join(""),
                    return_descriptor.as_deref().unwrap_or("V")
                )
            });
        let qualified_name = descriptor.as_ref().map_or_else(
            || format!("{}.{}", raw.owner, raw.name),
            |descriptor| format!("{}.{}{descriptor}", raw.owner, raw.name),
        );
        methods.push(MethodDeclaration {
            declaration: IndexedDeclaration::Report(Box::new(JavaSymbolDefinitionOutput {
                symbol: JavaSymbolIdentityOutput {
                    kind: raw.kind,
                    owner: raw.owner,
                    name: raw.name,
                    descriptor,
                    qualified_name,
                },
                identifier_span: file.span_for_range(raw.name_node_range),
                declaration_span: file.span_for_range(raw.declaration_range),
                confidence: if complete {
                    ResolutionConfidence::Resolved
                } else {
                    ResolutionConfidence::Unresolved
                },
            })),
            parameter_types,
            return_type,
        });
    }

    fields.sort();
    methods.sort();
    (fields, methods, diagnostics)
}

fn collect_raw_members(
    file: &JavaSyntaxFile,
    file_index: usize,
    node: Node<'_>,
    enclosing_owner: Option<&str>,
    fields: &mut Vec<RawFieldDeclaration>,
    methods: &mut Vec<RawMethodDeclaration>,
) {
    if is_nonsemantic_literal_or_comment(node.kind()) {
        return;
    }
    if is_type_declaration(node.kind()) {
        let Some(name_node) = declaration_name_node(node) else {
            return;
        };
        let Some(name) = file.text(name_node) else {
            return;
        };
        let owner = enclosing_owner.map_or_else(
            || qualify_name(&file.package_name, name),
            |parent| format!("{parent}${name}"),
        );
        if let Some(body) = node.child_by_field_name("body") {
            for member in named_children(body) {
                match member.kind() {
                    "field_declaration" | "constant_declaration" => {
                        collect_raw_field(file, file_index, &owner, member, fields);
                    }
                    "method_declaration" | "constructor_declaration" => {
                        collect_raw_method(file, file_index, &owner, member, methods);
                    }
                    "enum_constant" => {
                        if let Some(enum_name) = declaration_name_node(member) {
                            fields.push(RawFieldDeclaration {
                                owner: owner.clone(),
                                name_node_range: enum_name.byte_range(),
                                declaration_range: member.byte_range(),
                                raw_type: owner.clone(),
                                file_index,
                            });
                        }
                    }
                    _ => {}
                }
                collect_raw_members(file, file_index, member, Some(&owner), fields, methods);
            }
        }
        return;
    }
    for child in named_children(node) {
        collect_raw_members(file, file_index, child, enclosing_owner, fields, methods);
    }
}

fn collect_raw_field(
    file: &JavaSyntaxFile,
    file_index: usize,
    owner: &str,
    node: Node<'_>,
    fields: &mut Vec<RawFieldDeclaration>,
) {
    let Some(type_node) = node.child_by_field_name("type") else {
        return;
    };
    let Some(raw_type) = file.text(type_node) else {
        return;
    };
    for declarator in named_children(node)
        .into_iter()
        .filter(|child| child.kind() == "variable_declarator")
    {
        if let Some(name_node) = declarator.child_by_field_name("name") {
            fields.push(RawFieldDeclaration {
                owner: owner.to_owned(),
                name_node_range: name_node.byte_range(),
                declaration_range: node.byte_range(),
                raw_type: raw_type.to_owned(),
                file_index,
            });
        }
    }
}

fn collect_raw_method(
    file: &JavaSyntaxFile,
    file_index: usize,
    owner: &str,
    node: Node<'_>,
    methods: &mut Vec<RawMethodDeclaration>,
) {
    let Some(name_node) = declaration_name_node(node) else {
        return;
    };
    let name = if node.kind() == "constructor_declaration" {
        "<init>".to_owned()
    } else {
        file.text(name_node).unwrap_or("<unknown>").to_owned()
    };
    let raw_return_type = if node.kind() == "constructor_declaration" {
        "void".to_owned()
    } else {
        node.child_by_field_name("type")
            .and_then(|type_node| file.text(type_node))
            .unwrap_or("<unresolved>")
            .to_owned()
    };
    let raw_parameter_types = node
        .child_by_field_name("parameters")
        .map(named_children)
        .unwrap_or_default()
        .into_iter()
        .filter_map(|parameter| raw_parameter_type(file, parameter))
        .collect();
    methods.push(RawMethodDeclaration {
        owner: owner.to_owned(),
        name,
        name_node_range: name_node.byte_range(),
        declaration_range: node.byte_range(),
        raw_parameter_types,
        raw_return_type,
        kind: if node.kind() == "constructor_declaration" {
            JavaSymbolKind::Constructor
        } else {
            JavaSymbolKind::Method
        },
        file_index,
    });
}

fn resolve_java_type(
    raw_type: &str,
    file: &JavaSyntaxFile,
    owner: &str,
    lookup: &TypeLookup,
) -> Result<ResolvedType, TypeResolutionFailure> {
    resolve_java_type_with_descriptor(raw_type, file, owner, lookup, false)
        .map(|(resolved, _)| resolved)
}

fn resolve_java_type_with_descriptor(
    raw_type: &str,
    file: &JavaSyntaxFile,
    owner: &str,
    lookup: &TypeLookup,
    allow_void: bool,
) -> Result<(ResolvedType, String), TypeResolutionFailure> {
    let (base, dimensions) = erase_java_type(raw_type);
    let primitive_descriptor = match base.as_str() {
        "boolean" => Some("Z"),
        "byte" => Some("B"),
        "char" => Some("C"),
        "short" => Some("S"),
        "int" => Some("I"),
        "long" => Some("J"),
        "float" => Some("F"),
        "double" => Some("D"),
        "void" if allow_void && dimensions == 0 => Some("V"),
        _ => None,
    };
    if let Some(descriptor) = primitive_descriptor {
        return Ok((
            ResolvedType {
                qualified_name: format!("{base}{}", "[]".repeat(dimensions)),
                source_indices: Vec::new(),
            },
            format!("{}{descriptor}", "[".repeat(dimensions)),
        ));
    }
    if base == "void" || base == "var" || base.is_empty() || base == "<unresolved>" {
        return Err(TypeResolutionFailure::Unresolved(raw_type.to_owned()));
    }

    let mut resolved = resolve_reference_type_name(&base, file, owner, lookup)?;
    let descriptor = format!(
        "{}L{};",
        "[".repeat(dimensions),
        resolved.qualified_name.replace('.', "/")
    );
    resolved.qualified_name.push_str(&"[]".repeat(dimensions));
    Ok((resolved, descriptor))
}

fn resolve_reference_type_name(
    raw_name: &str,
    file: &JavaSyntaxFile,
    owner: &str,
    lookup: &TypeLookup,
) -> Result<ResolvedType, TypeResolutionFailure> {
    let direct_candidates = direct_import_candidates(file, raw_name);
    let imported_nested_candidates = if let Some((head, tail)) = raw_name.split_once('.') {
        direct_import_candidates(file, head)
            .into_iter()
            .map(|import| format!("{import}.{tail}"))
            .collect::<Vec<_>>()
    } else {
        Vec::new()
    };
    let explicit_candidates = if direct_candidates.is_empty() {
        imported_nested_candidates
    } else {
        direct_candidates
    };
    if !explicit_candidates.is_empty() {
        return resolve_candidate_set(raw_name, &explicit_candidates, file, lookup);
    }

    if raw_name.contains('.') && raw_name.chars().next().is_some_and(char::is_lowercase) {
        return resolve_qualified_candidate(raw_name, file, lookup);
    }

    let mut enclosing = Some(owner);
    while let Some(current) = enclosing {
        let candidate = format!("{current}${raw_name}");
        match resolve_candidate_set(raw_name, &[candidate], file, lookup) {
            Err(TypeResolutionFailure::Unresolved(_)) => {}
            result => return result,
        }
        enclosing = current.rsplit_once('$').map(|(parent, _)| parent);
    }

    let package_candidate = qualify_name(&file.package_name, raw_name);
    match resolve_candidate_set(raw_name, &[package_candidate], file, lookup) {
        Err(TypeResolutionFailure::Unresolved(_)) => {}
        result => return result,
    }

    // `java.lang` is an implicit type-import-on-demand. It therefore shares
    // one precedence tier with explicit wildcard package imports, below
    // lexically enclosing and current-package declarations.
    let mut candidates = vec![format!("java.lang.{raw_name}")];
    for package in &file.imports.wildcard_packages {
        candidates.push(format!("{package}.{raw_name}"));
    }
    candidates.sort();
    candidates.dedup();

    resolve_candidate_set(raw_name, &candidates, file, lookup)
}

fn resolve_qualified_candidate(
    candidate: &str,
    file: &JavaSyntaxFile,
    lookup: &TypeLookup,
) -> Result<ResolvedType, TypeResolutionFailure> {
    resolve_candidate_set(candidate, &[candidate.to_owned()], file, lookup)
}

fn resolve_candidate_set(
    display_name: &str,
    candidates: &[String],
    file: &JavaSyntaxFile,
    lookup: &TypeLookup,
) -> Result<ResolvedType, TypeResolutionFailure> {
    let mut visible = Vec::new();
    let mut inaccessible = false;
    for candidate in candidates {
        let all = lookup.all_indices(candidate);
        let available = lookup.visible_indices(candidate, &file.source_set);
        inaccessible |= !all.is_empty() && available.is_empty();
        visible.extend(available);
    }
    visible.sort_unstable();
    visible.dedup();
    match visible.as_slice() {
        [index] => Ok(ResolvedType {
            qualified_name: lookup.types[*index].qualified_name.clone(),
            source_indices: visible,
        }),
        [] if inaccessible => Err(TypeResolutionFailure::Inaccessible(display_name.to_owned())),
        [] => Err(TypeResolutionFailure::Unresolved(display_name.to_owned())),
        _ => Err(TypeResolutionFailure::Ambiguous {
            name: display_name.to_owned(),
            source_indices: visible,
        }),
    }
}

fn direct_import_candidates(file: &JavaSyntaxFile, simple_name: &str) -> Vec<String> {
    let mut qualified_names =
        file.imports
            .direct_types
            .get(simple_name)
            .map_or_else(Vec::new, |imports| {
                imports
                    .iter()
                    .map(|import| import.qualified_name.clone())
                    .collect::<Vec<_>>()
            });
    qualified_names.sort();
    qualified_names.dedup();
    qualified_names
}

fn erase_java_type(raw_type: &str) -> (String, usize) {
    let mut value = raw_type.trim().replace("...", "[]");
    while value.starts_with('@') {
        let split = value.find(char::is_whitespace).unwrap_or(value.len());
        value = value[split..].trim_start().to_owned();
    }
    if let Some(bound) = value.strip_prefix("? extends ") {
        value = bound.to_owned();
    } else if value.starts_with('?') {
        "java.lang.Object".clone_into(&mut value);
    }
    let mut erased = String::new();
    let mut generic_depth = 0usize;
    for character in value.chars() {
        match character {
            '<' => generic_depth += 1,
            '>' => generic_depth = generic_depth.saturating_sub(1),
            _ if generic_depth == 0 => erased.push(character),
            _ => {}
        }
    }
    let mut dimensions = 0;
    let mut base = erased.trim().to_owned();
    while let Some(component) = base.strip_suffix("[]") {
        dimensions += 1;
        base = component.trim().to_owned();
    }
    (base, dimensions)
}

fn type_resolution_diagnostic(
    failure: TypeResolutionFailure,
    span: JavaSourceSpanOutput,
    role: &str,
) -> JavaAnalysisDiagnosticOutput {
    let (code, message) = match failure {
        TypeResolutionFailure::Unresolved(name) => (
            "java.unresolved-type",
            format!("Could not resolve {role} `{name}` without guessing"),
        ),
        TypeResolutionFailure::Ambiguous { name, .. } => (
            "java.ambiguous-type",
            format!("{role} `{name}` resolves to more than one visible source declaration"),
        ),
        TypeResolutionFailure::Inaccessible(name) => (
            "java.inaccessible-source-set-reference",
            format!("{role} `{name}` exists only in a source set that is not visible here"),
        ),
    };
    JavaAnalysisDiagnosticOutput {
        code: code.to_owned(),
        severity: DiagnosticSeverity::Warning,
        message,
        span: Some(span),
    }
}

fn qualify_name(package: &str, simple_name: &str) -> String {
    if package.is_empty() {
        simple_name.to_owned()
    } else {
        format!("{package}.{simple_name}")
    }
}

fn declaration_usages(model: &JavaIndexModel) -> Vec<JavaSymbolUsageOutput> {
    model
        .resolution
        .types
        .iter()
        .filter_map(TypeDeclaration::output)
        .chain(
            model
                .resolution
                .fields
                .iter()
                .filter_map(FieldDeclaration::output),
        )
        .chain(
            model
                .resolution
                .methods
                .iter()
                .filter_map(MethodDeclaration::output),
        )
        .map(|definition| JavaSymbolUsageOutput {
            target: definition.symbol.clone(),
            kind: JavaUsageKind::Declaration,
            span: definition.identifier_span.clone(),
            confidence: definition.confidence,
        })
        .collect()
}

fn declaration_usages_for_files(model: &JavaIndexModel) -> Vec<JavaSymbolUsageOutput> {
    model
        .resolution
        .types
        .iter()
        .filter_map(TypeDeclaration::output)
        .chain(
            model
                .resolution
                .fields
                .iter()
                .filter_map(FieldDeclaration::output),
        )
        .chain(
            model
                .resolution
                .methods
                .iter()
                .filter_map(MethodDeclaration::output),
        )
        .filter(|definition| {
            model.files.iter().any(|file| {
                definition.identifier_span.path == file.report_path
                    && definition.identifier_span.source_set == file.source_set
                    && definition.identifier_span.source_hash == file.source_hash
            })
        })
        .map(|definition| JavaSymbolUsageOutput {
            target: definition.symbol.clone(),
            kind: JavaUsageKind::Declaration,
            span: definition.identifier_span.clone(),
            confidence: definition.confidence,
        })
        .collect()
}

type CollectedFileUsages = (
    Vec<JavaSymbolUsageOutput>,
    Vec<JavaAnalysisDiagnosticOutput>,
    Vec<JavaSymbolDefinitionOutput>,
    u64,
);

fn collect_file_usages(
    model: &JavaIndexModel,
    file_index: usize,
) -> (
    Vec<JavaSymbolUsageOutput>,
    Vec<JavaAnalysisDiagnosticOutput>,
    Vec<JavaSymbolDefinitionOutput>,
) {
    let (usages, diagnostics, definitions, _) =
        collect_file_usages_with_limits(model, file_index, None, None, None, None);
    (usages, diagnostics, definitions)
}

fn collect_selected_file_usages(
    model: &JavaIndexModel,
    file_index: usize,
    selected_targets: &BTreeSet<JavaSymbolIdentityOutput>,
    usage_limit: usize,
    diagnostic_limit: usize,
    cancellation_token: &CancellationToken,
) -> eyre::Result<CollectedFileUsages> {
    let collected = collect_file_usages_with_limits(
        model,
        file_index,
        Some(selected_targets),
        Some(usage_limit),
        Some(diagnostic_limit),
        Some(cancellation_token),
    );
    cancellation_token.bail_if_cancelled()?;
    Ok(collected)
}

fn collect_file_usages_with_limits<'a>(
    model: &'a JavaIndexModel,
    file_index: usize,
    selected_targets: Option<&'a BTreeSet<JavaSymbolIdentityOutput>>,
    usage_limit: Option<usize>,
    diagnostic_limit: Option<usize>,
    cancellation_token: Option<&'a CancellationToken>,
) -> CollectedFileUsages {
    let file = &model.files[file_index];
    let mut collector = UsageCollector {
        model,
        file_index,
        owners: Vec::new(),
        callable_scopes: Vec::new(),
        scopes: Vec::new(),
        usages: BTreeSet::new(),
        diagnostics: BTreeSet::new(),
        local_definitions: Vec::new(),
        selected_targets,
        usage_limit,
        diagnostic_limit,
        discarded_usage_candidates: 0,
        cancellation_token,
        visited_nodes: 0,
        cancelled: false,
    };
    collector.collect_import_usages();
    collector.visit(file.tree.root_node());
    (
        collector.usages.into_iter().collect(),
        collector.diagnostics.into_iter().collect(),
        collector.local_definitions,
        collector.discarded_usage_candidates,
    )
}

struct UsageCollector<'a> {
    model: &'a JavaIndexModel,
    file_index: usize,
    owners: Vec<String>,
    callable_scopes: Vec<String>,
    scopes: Vec<BTreeMap<String, LocalBinding>>,
    usages: BTreeSet<JavaSymbolUsageOutput>,
    diagnostics: BTreeSet<JavaAnalysisDiagnosticOutput>,
    local_definitions: Vec<JavaSymbolDefinitionOutput>,
    selected_targets: Option<&'a BTreeSet<JavaSymbolIdentityOutput>>,
    usage_limit: Option<usize>,
    diagnostic_limit: Option<usize>,
    discarded_usage_candidates: u64,
    cancellation_token: Option<&'a CancellationToken>,
    visited_nodes: usize,
    cancelled: bool,
}

#[derive(Clone, Debug)]
struct LocalBinding {
    value_type: Option<String>,
    definition: JavaSymbolDefinitionOutput,
}

#[derive(Clone, Debug)]
enum MemberResolutionFailure {
    Unresolved,
    Ambiguous(Vec<JavaSymbolIdentityOutput>),
    Inaccessible,
}

impl UsageCollector<'_> {
    fn file(&self) -> &JavaSyntaxFile {
        &self.model.files[self.file_index]
    }

    fn record_usage(&mut self, usage: JavaSymbolUsageOutput) {
        if self
            .selected_targets
            .is_some_and(|targets| !targets.contains(&usage.target))
        {
            return;
        }
        if self.usages.contains(&usage) {
            return;
        }
        if self
            .usage_limit
            .is_none_or(|limit| self.usages.len() < limit)
        {
            self.usages.insert(usage);
            return;
        }
        let replace_largest = self.usages.last().is_some_and(|largest| usage < *largest);
        if replace_largest {
            self.usages.pop_last();
            self.usages.insert(usage);
        }
        self.discarded_usage_candidates = self.discarded_usage_candidates.saturating_add(1);
    }

    fn record_diagnostic(&mut self, diagnostic: JavaAnalysisDiagnosticOutput) {
        if self.diagnostics.contains(&diagnostic) {
            return;
        }
        if self
            .diagnostic_limit
            .is_none_or(|limit| self.diagnostics.len() < limit)
        {
            self.diagnostics.insert(diagnostic);
            return;
        }
        let replace_largest = self
            .diagnostics
            .last()
            .is_some_and(|largest| diagnostic < *largest);
        if replace_largest {
            self.diagnostics.pop_last();
            self.diagnostics.insert(diagnostic);
        }
    }

    fn collect_import_usages(&mut self) {
        let direct_imports = self
            .file()
            .imports
            .direct_types
            .values()
            .flatten()
            .cloned()
            .collect::<Vec<_>>();
        for import in direct_imports {
            match resolve_qualified_candidate(
                &import.qualified_name,
                self.file(),
                &self.model.resolution.type_lookup,
            ) {
                Ok(resolved) => {
                    self.record_type_indices(
                        &resolved.source_indices,
                        JavaUsageKind::Import,
                        &import.span,
                        ResolutionConfidence::Resolved,
                    );
                }
                Err(failure) => {
                    self.record_type_ambiguity(&failure, JavaUsageKind::Import, &import.span);
                    self.record_diagnostic(type_resolution_diagnostic(
                        failure,
                        import.span,
                        "imported type",
                    ));
                }
            }
        }

        let static_imports = self.file().imports.static_members.clone();
        for import in static_imports {
            if let Ok(owner) = resolve_qualified_candidate(
                &import.owner,
                self.file(),
                &self.model.resolution.type_lookup,
            ) {
                self.record_type_indices(
                    &owner.source_indices,
                    JavaUsageKind::Import,
                    &import.owner_span,
                    ResolutionConfidence::Resolved,
                );
            }
            let fields = self
                .visible_fields(&import.owner, &import.member)
                .into_iter()
                .map(|field| field.symbol().clone())
                .collect::<Vec<_>>();
            for target in fields {
                self.record_usage(JavaSymbolUsageOutput {
                    target,
                    kind: JavaUsageKind::Import,
                    span: import.member_span.clone(),
                    confidence: ResolutionConfidence::Resolved,
                });
            }
            let methods = self
                .visible_methods(&import.owner, &import.member)
                .into_iter()
                .map(|method| method.symbol().clone())
                .collect::<Vec<_>>();
            let confidence = if methods.len() == 1 {
                ResolutionConfidence::Resolved
            } else {
                ResolutionConfidence::PartiallyResolved
            };
            for target in methods {
                self.record_usage(JavaSymbolUsageOutput {
                    target,
                    kind: JavaUsageKind::Import,
                    span: import.member_span.clone(),
                    confidence,
                });
            }
        }
    }

    fn visit(&mut self, node: Node<'_>) {
        if self.cancelled {
            return;
        }
        self.visited_nodes = self.visited_nodes.saturating_add(1);
        if self.visited_nodes.is_multiple_of(256)
            && self
                .cancellation_token
                .is_some_and(CancellationToken::is_cancelled)
        {
            self.cancelled = true;
            return;
        }
        if is_nonsemantic_literal_or_comment(node.kind()) {
            return;
        }
        match node.kind() {
            "package_declaration" | "import_declaration" => {}
            kind if is_type_declaration(kind) => self.visit_type_declaration(node),
            "method_declaration" | "constructor_declaration" => self.visit_callable(node),
            "block" | "constructor_body" => self.visit_scope(node),
            "local_variable_declaration" => self.visit_local_variable(node),
            "enhanced_for_statement" => self.visit_enhanced_for(node),
            "catch_clause" => self.visit_catch_clause(node),
            "marker_annotation" | "annotation" => self.visit_annotation(node),
            "object_creation_expression" => self.visit_object_creation(node),
            "field_access" => self.visit_field_access(node),
            "method_invocation" => self.visit_method_invocation(node),
            "method_reference" => self.visit_method_reference(node),
            "scoped_identifier" => self.visit_qualified_expression_name(node),
            "type_identifier" | "scoped_type_identifier" => {
                self.visit_type_reference(node);
            }
            "identifier" => self.visit_unqualified_identifier(node),
            _ => self.visit_children(node),
        }
    }

    fn visit_type_declaration(&mut self, node: Node<'_>) {
        let Some(name_node) = declaration_name_node(node) else {
            self.visit_children(node);
            return;
        };
        let Some(name) = self.file().text(name_node) else {
            self.visit_children(node);
            return;
        };
        let owner = self.owners.last().map_or_else(
            || qualify_name(&self.file().package_name, name),
            |parent| format!("{parent}${name}"),
        );
        self.owners.push(owner);
        for child in named_children(node) {
            if child.byte_range() != name_node.byte_range() {
                self.visit(child);
            }
        }
        let _ = self.owners.pop();
    }

    fn visit_callable(&mut self, node: Node<'_>) {
        let callable_name = node
            .child_by_field_name("name")
            .and_then(|name| self.file().text(name))
            .unwrap_or("<anonymous>");
        let owner = self
            .owners
            .last()
            .cloned()
            .unwrap_or_else(|| self.file().report_path.clone());
        self.callable_scopes
            .push(format!("{owner}#{callable_name}@{}", node.start_byte()));
        self.scopes.push(BTreeMap::new());
        if let Some(parameters) = node.child_by_field_name("parameters") {
            for parameter in named_children(parameters) {
                self.register_typed_binding(parameter, JavaSymbolKind::Parameter);
            }
        }
        self.visit_children(node);
        let _ = self.scopes.pop();
        let _ = self.callable_scopes.pop();
    }

    fn visit_scope(&mut self, node: Node<'_>) {
        self.scopes.push(BTreeMap::new());
        self.visit_children(node);
        let _ = self.scopes.pop();
    }

    fn visit_local_variable(&mut self, node: Node<'_>) {
        if let Some(type_node) = node.child_by_field_name("type") {
            self.visit(type_node);
        }
        let raw_type = node
            .child_by_field_name("type")
            .and_then(|type_node| self.file().text(type_node))
            .and_then(|raw_type| self.resolve_type(raw_type).ok())
            .map(|resolved| resolved.qualified_name);
        for declarator in named_children(node)
            .into_iter()
            .filter(|child| child.kind() == "variable_declarator")
        {
            if let Some(value) = declarator.child_by_field_name("value") {
                self.visit(value);
            }
            let Some(name_node) = declarator.child_by_field_name("name") else {
                continue;
            };
            let Some(name) = self.file().text(name_node).map(ToOwned::to_owned) else {
                continue;
            };
            let value_type = raw_type.clone().or_else(|| {
                declarator
                    .child_by_field_name("value")
                    .and_then(|value| self.expression_type(value))
            });
            self.register_binding(
                name,
                name_node,
                declarator,
                value_type,
                JavaSymbolKind::LocalVariable,
            );
        }
    }

    fn visit_enhanced_for(&mut self, node: Node<'_>) {
        self.scopes.push(BTreeMap::new());
        self.register_typed_binding(node, JavaSymbolKind::LocalVariable);
        self.visit_children(node);
        let _ = self.scopes.pop();
    }

    fn visit_catch_clause(&mut self, node: Node<'_>) {
        self.scopes.push(BTreeMap::new());
        if let Some(parameter) = node.child_by_field_name("parameter") {
            self.register_typed_binding(parameter, JavaSymbolKind::LocalVariable);
        }
        self.visit_children(node);
        let _ = self.scopes.pop();
    }

    fn register_typed_binding(&mut self, node: Node<'_>, kind: JavaSymbolKind) {
        let Some(type_node) = node.child_by_field_name("type") else {
            return;
        };
        self.visit(type_node);
        let value_type = self
            .file()
            .text(type_node)
            .and_then(|raw_type| self.resolve_type(raw_type).ok())
            .map(|resolved| resolved.qualified_name);
        let Some(name_node) = node.child_by_field_name("name") else {
            return;
        };
        let Some(name) = self.file().text(name_node).map(ToOwned::to_owned) else {
            return;
        };
        self.register_binding(name, name_node, node, value_type, kind);
    }

    fn register_binding(
        &mut self,
        name: String,
        name_node: Node<'_>,
        declaration_node: Node<'_>,
        value_type: Option<String>,
        kind: JavaSymbolKind,
    ) {
        let identifier_span = self.file().span(name_node);
        let lexical_owner = self
            .callable_scopes
            .last()
            .or_else(|| self.owners.last())
            .cloned()
            .unwrap_or_else(|| self.file().report_path.clone());
        let qualified_name = format!(
            "{}#{}@{}",
            self.file().report_path,
            name,
            identifier_span.start_byte
        );
        let definition = JavaSymbolDefinitionOutput {
            symbol: JavaSymbolIdentityOutput {
                kind,
                owner: lexical_owner,
                name: name.clone(),
                descriptor: Some(format!("@{}", identifier_span.start_byte)),
                qualified_name,
            },
            identifier_span: identifier_span.clone(),
            declaration_span: self.file().span(declaration_node),
            confidence: ResolutionConfidence::Resolved,
        };
        self.record_usage(JavaSymbolUsageOutput {
            target: definition.symbol.clone(),
            kind: JavaUsageKind::Declaration,
            span: identifier_span,
            confidence: ResolutionConfidence::Resolved,
        });
        self.local_definitions.push(definition.clone());
        if let Some(scope) = self.scopes.last_mut() {
            scope.insert(
                name,
                LocalBinding {
                    value_type,
                    definition,
                },
            );
        }
    }

    fn visit_annotation(&mut self, node: Node<'_>) {
        let Some(name_node) = node
            .child_by_field_name("name")
            .or_else(|| named_children(node).into_iter().next())
        else {
            self.visit_children(node);
            return;
        };
        let Some(raw_type) = self.file().text(name_node) else {
            self.visit_children(node);
            return;
        };
        let span = self.file().span(name_node);
        match self.resolve_type(raw_type.trim_start_matches('@')) {
            Ok(resolved) => self.record_type_indices(
                &resolved.source_indices,
                JavaUsageKind::TypeReference,
                &span,
                ResolutionConfidence::Resolved,
            ),
            Err(failure) => {
                self.record_type_ambiguity(&failure, JavaUsageKind::TypeReference, &span);
                self.record_diagnostic(type_resolution_diagnostic(
                    failure,
                    span,
                    "annotation type",
                ));
            }
        }
        for child in named_children(node) {
            if child.byte_range() != name_node.byte_range() {
                self.visit(child);
            }
        }
    }

    fn visit_type_reference(&mut self, node: Node<'_>) {
        if node.kind() == "type_identifier"
            && node
                .parent()
                .is_some_and(|parent| parent.kind() == "scoped_type_identifier")
        {
            return;
        }
        let Some(raw_type) = self.file().text(node) else {
            return;
        };
        let span = self.file().span(node);
        match self.resolve_type(raw_type) {
            Ok(resolved) => self.record_type_indices(
                &resolved.source_indices,
                JavaUsageKind::TypeReference,
                &span,
                ResolutionConfidence::Resolved,
            ),
            Err(failure) => {
                self.record_type_ambiguity(&failure, JavaUsageKind::TypeReference, &span);
                self.record_diagnostic(type_resolution_diagnostic(failure, span, "type reference"));
            }
        }
        self.visit_children(node);
    }

    fn visit_field_access(&mut self, node: Node<'_>) {
        if let Some(resolved) = self.resolve_qualified_type_expression(node) {
            let span = self.file().span(node);
            self.record_type_indices(
                &resolved.source_indices,
                JavaUsageKind::TypeReference,
                &span,
                ResolutionConfidence::Resolved,
            );
            return;
        }
        let Some(field_node) = node.child_by_field_name("field") else {
            self.visit_children(node);
            return;
        };
        let Some(name) = self.file().text(field_node).map(ToOwned::to_owned) else {
            self.visit_children(node);
            return;
        };
        let owner = node.child_by_field_name("object").and_then(|object| {
            if object.kind() == "this" {
                self.owners.last().cloned()
            } else {
                self.expression_type(object)
            }
        });
        if let Some(owner) = owner {
            self.record_field_reference(&owner, &name, field_node, false);
        }
        for child in named_children(node) {
            if child.byte_range() != field_node.byte_range() {
                self.visit(child);
            }
        }
    }

    fn visit_qualified_expression_name(&mut self, node: Node<'_>) {
        let Some(resolved) = self.resolve_qualified_type_expression(node) else {
            self.visit_children(node);
            return;
        };
        let span = self.file().span(node);
        self.record_type_indices(
            &resolved.source_indices,
            JavaUsageKind::TypeReference,
            &span,
            ResolutionConfidence::Resolved,
        );
    }

    fn visit_object_creation(&mut self, node: Node<'_>) {
        let Some(type_node) = node.child_by_field_name("type") else {
            self.visit_children(node);
            return;
        };
        let Some(raw_type) = self.file().text(type_node).map(ToOwned::to_owned) else {
            self.visit_children(node);
            return;
        };
        let span = self.file().span(type_node);
        match self.resolve_type(&raw_type) {
            Ok(resolved) => {
                let owner = resolved.qualified_name;
                let arguments = node
                    .child_by_field_name("arguments")
                    .map(named_children)
                    .unwrap_or_default();
                let argument_types = arguments
                    .iter()
                    .map(|argument| self.expression_type(*argument))
                    .collect::<Vec<_>>();
                let all_arguments_resolved = argument_types.iter().all(Option::is_some);
                let declared_constructors = self.visible_methods(&owner, "<init>");
                let mut candidates = declared_constructors
                    .iter()
                    .copied()
                    .filter(|method| method.parameter_types.len() == argument_types.len())
                    .collect::<Vec<_>>();
                if all_arguments_resolved {
                    candidates.retain(|method| {
                        method.parameter_types.iter().zip(&argument_types).all(
                            |(parameter, argument)| {
                                argument
                                    .as_deref()
                                    .is_some_and(|argument| java_types_match(parameter, argument))
                            },
                        )
                    });
                }
                candidates.sort_by(|left, right| left.declaration.cmp(&right.declaration));
                candidates.dedup_by(|left, right| left.declaration == right.declaration);
                match candidates.as_slice() {
                    [constructor] => self.record_usage(JavaSymbolUsageOutput {
                        target: constructor.symbol().clone(),
                        kind: JavaUsageKind::Invocation,
                        span: span.clone(),
                        confidence: if all_arguments_resolved {
                            ResolutionConfidence::Resolved
                        } else {
                            ResolutionConfidence::PartiallyResolved
                        },
                    }),
                    [] if declared_constructors.is_empty() => self.record_type_indices(
                        &resolved.source_indices,
                        JavaUsageKind::TypeReference,
                        &span,
                        ResolutionConfidence::Resolved,
                    ),
                    [] => self.push_member_diagnostic(
                        &MemberResolutionFailure::Unresolved,
                        "<init>",
                        span.clone(),
                    ),
                    _ => {
                        let failure = MemberResolutionFailure::Ambiguous(
                            candidates
                                .iter()
                                .map(|method| method.symbol().clone())
                                .collect(),
                        );
                        self.record_member_ambiguity(&failure, JavaUsageKind::Invocation, &span);
                        self.push_member_diagnostic(&failure, "<init>", span.clone());
                    }
                }
            }
            Err(failure) => {
                self.record_type_ambiguity(&failure, JavaUsageKind::TypeReference, &span);
                self.record_diagnostic(type_resolution_diagnostic(
                    failure,
                    span.clone(),
                    "constructed type",
                ));
            }
        }
        for child in named_children(node) {
            if child.byte_range() != type_node.byte_range() {
                self.visit(child);
            }
        }
    }

    fn visit_unqualified_identifier(&mut self, node: Node<'_>) {
        if Self::is_nonreference_identifier(node) {
            return;
        }
        let Some(name) = self.file().text(node).map(ToOwned::to_owned) else {
            return;
        };
        if let Some(binding) = self.lookup_local(&name).cloned() {
            self.record_usage(JavaSymbolUsageOutput {
                target: binding.definition.symbol,
                kind: JavaUsageKind::LocalReference,
                span: self.file().span(node),
                confidence: ResolutionConfidence::Resolved,
            });
            return;
        }
        if let Some(owner) = self.owners.last().cloned()
            && self.visible_fields(&owner, &name).len() == 1
        {
            self.record_field_reference(&owner, &name, node, false);
            return;
        }
        let static_owners = self
            .file()
            .imports
            .static_members
            .iter()
            .filter(|import| import.member == name)
            .map(|import| import.owner.clone())
            .chain(self.file().imports.static_wildcard_owners.iter().cloned())
            .collect::<BTreeSet<_>>();
        let candidates = static_owners
            .iter()
            .flat_map(|owner| self.visible_fields(owner, &name))
            .collect::<Vec<_>>();
        match candidates.as_slice() {
            [field] => {
                self.record_usage(JavaSymbolUsageOutput {
                    target: field.symbol().clone(),
                    kind: JavaUsageKind::FieldReference,
                    span: self.file().span(node),
                    confidence: ResolutionConfidence::Resolved,
                });
                return;
            }
            [] => {}
            _ => {
                let span = self.file().span(node);
                let failure = MemberResolutionFailure::Ambiguous(
                    candidates
                        .iter()
                        .map(|field| field.symbol().clone())
                        .collect(),
                );
                self.record_member_ambiguity(&failure, JavaUsageKind::FieldReference, &span);
                self.push_member_diagnostic(&failure, &name, span);
                return;
            }
        }

        // Arborium represents `TypeName.member()` and `TypeName.FIELD` with an
        // ordinary identifier as the receiver. Resolve that receiver as a type
        // only after local/field lookup has failed, so wildcard package imports
        // retain exact jump-to-definition spans without guessing at variables.
        if Self::is_receiver_identifier(node) {
            let span = self.file().span(node);
            match self.resolve_type(&name) {
                Ok(resolved) => self.record_type_indices(
                    &resolved.source_indices,
                    JavaUsageKind::TypeReference,
                    &span,
                    ResolutionConfidence::Resolved,
                ),
                Err(
                    failure @ (TypeResolutionFailure::Ambiguous { .. }
                    | TypeResolutionFailure::Inaccessible { .. }),
                ) => {
                    self.record_type_ambiguity(&failure, JavaUsageKind::TypeReference, &span);
                    self.record_diagnostic(type_resolution_diagnostic(
                        failure,
                        span,
                        "receiver type",
                    ));
                }
                Err(TypeResolutionFailure::Unresolved(_)) => {}
            }
        }
    }

    fn visit_method_invocation(&mut self, node: Node<'_>) {
        let Some(name_node) = node.child_by_field_name("name") else {
            self.visit_children(node);
            return;
        };
        let name = self
            .file()
            .text(name_node)
            .unwrap_or("<unknown>")
            .to_owned();
        let span = self.file().span(name_node);
        match self.resolve_invocation(node) {
            Ok(Some((method, confidence))) => self.record_usage(JavaSymbolUsageOutput {
                target: method.symbol().clone(),
                kind: JavaUsageKind::Invocation,
                span: span.clone(),
                confidence,
            }),
            Ok(None) => {}
            Err(failure) => {
                self.record_member_ambiguity(&failure, JavaUsageKind::Invocation, &span);
                self.push_member_diagnostic(&failure, &name, span);
            }
        }
        for child in named_children(node) {
            if child.byte_range() != name_node.byte_range() {
                self.visit(child);
            }
        }
    }

    fn visit_method_reference(&mut self, node: Node<'_>) {
        // Arborium's Java grammar deliberately exposes method-reference parts as
        // ordered children rather than named fields: receiver, optional type
        // arguments, then an identifier (or the unnamed `new` token).
        let children = named_children(node);
        let Some(object) = children.first().copied() else {
            self.visit_children(node);
            return;
        };
        let named_method = children.iter().rev().copied().find(|child| {
            child.kind() == "identifier" && child.byte_range() != object.byte_range()
        });
        let (name, span, name_range) = if let Some(name_node) = named_method {
            let Some(name) = self.file().text(name_node).map(ToOwned::to_owned) else {
                return;
            };
            (
                name,
                self.file().span(name_node),
                Some(name_node.byte_range()),
            )
        } else {
            let Some(text) = self.file().text(node) else {
                return;
            };
            let Some(relative_start) = text.rfind("new") else {
                self.visit_children(node);
                return;
            };
            let start = node.start_byte() + relative_start;
            (
                "<init>".to_owned(),
                self.file().span_for_range(start..start + 3),
                None,
            )
        };
        let Some(owner) = self.expression_type(object) else {
            self.visit_children(node);
            return;
        };
        let candidates = self.visible_methods(&owner, &name);
        match candidates.as_slice() {
            [method] => self.record_usage(JavaSymbolUsageOutput {
                target: method.symbol().clone(),
                kind: JavaUsageKind::MethodReference,
                span: span.clone(),
                confidence: ResolutionConfidence::Resolved,
            }),
            [] if name == "<init>" && self.has_source_type(&owner) => {
                let indices = self
                    .model
                    .resolution
                    .type_lookup
                    .visible_indices(&owner, &self.file().source_set);
                self.record_type_indices(
                    &indices,
                    JavaUsageKind::MethodReference,
                    &span,
                    ResolutionConfidence::Resolved,
                );
            }
            [] if self.has_source_type(&owner) => self.push_member_diagnostic(
                &MemberResolutionFailure::Unresolved,
                &name,
                span.clone(),
            ),
            [] => {}
            _ => {
                let failure = MemberResolutionFailure::Ambiguous(
                    candidates
                        .iter()
                        .map(|method| method.symbol().clone())
                        .collect(),
                );
                self.record_member_ambiguity(&failure, JavaUsageKind::MethodReference, &span);
                self.push_member_diagnostic(&failure, &name, span);
            }
        }
        for child in children {
            if name_range
                .as_ref()
                .is_none_or(|range| child.byte_range() != *range)
            {
                self.visit(child);
            }
        }
    }

    fn visit_children(&mut self, node: Node<'_>) {
        for child in named_children(node) {
            if self.cancelled {
                break;
            }
            self.visit(child);
        }
    }

    fn resolve_type(&self, raw_type: &str) -> Result<ResolvedType, TypeResolutionFailure> {
        resolve_java_type(
            raw_type,
            self.file(),
            self.owners.last().map_or("", String::as_str),
            &self.model.resolution.type_lookup,
        )
    }

    /// Resolve a syntactically expression-shaped qualified name as a type only
    /// when Java's value namespace does not claim its left-most segment. This
    /// is the distinction needed for receivers such as
    /// `ca.teamdman.Type.staticMethod()` without turning `local.field` into a
    /// guessed type reference when a package happens to share `local`'s name.
    fn resolve_qualified_type_expression(&self, node: Node<'_>) -> Option<ResolvedType> {
        if !matches!(node.kind(), "field_access" | "scoped_identifier") {
            return None;
        }
        let raw_type = self.file().text(node)?;
        let root = raw_type.split('.').next()?.trim();
        if root.is_empty()
            || self.lookup_local(root).is_some()
            || self
                .owners
                .last()
                .is_some_and(|owner| !self.visible_fields(owner, root).is_empty())
        {
            return None;
        }
        self.resolve_type(raw_type).ok()
    }

    fn record_type_indices(
        &mut self,
        indices: &[usize],
        kind: JavaUsageKind,
        span: &JavaSourceSpanOutput,
        confidence: ResolutionConfidence,
    ) {
        for index in indices {
            self.record_usage(JavaSymbolUsageOutput {
                target: self.model.resolution.types[*index].symbol().clone(),
                kind,
                span: span.clone(),
                confidence,
            });
        }
    }

    fn record_type_ambiguity(
        &mut self,
        failure: &TypeResolutionFailure,
        kind: JavaUsageKind,
        span: &JavaSourceSpanOutput,
    ) {
        if let TypeResolutionFailure::Ambiguous { source_indices, .. } = failure {
            self.record_type_indices(
                source_indices,
                kind,
                span,
                ResolutionConfidence::PartiallyResolved,
            );
        }
    }

    fn record_member_ambiguity(
        &mut self,
        failure: &MemberResolutionFailure,
        kind: JavaUsageKind,
        span: &JavaSourceSpanOutput,
    ) {
        if let MemberResolutionFailure::Ambiguous(symbols) = failure {
            self.usages
                .extend(symbols.iter().cloned().map(|target| JavaSymbolUsageOutput {
                    target,
                    kind,
                    span: span.clone(),
                    confidence: ResolutionConfidence::PartiallyResolved,
                }));
        }
    }

    fn record_field_reference(
        &mut self,
        owner: &str,
        name: &str,
        location: Node<'_>,
        partially_resolved: bool,
    ) {
        let candidates = self.visible_fields(owner, name);
        match candidates.as_slice() {
            [field] => self.record_usage(JavaSymbolUsageOutput {
                target: field.symbol().clone(),
                kind: JavaUsageKind::FieldReference,
                span: self.file().span(location),
                confidence: if partially_resolved {
                    ResolutionConfidence::PartiallyResolved
                } else {
                    ResolutionConfidence::Resolved
                },
            }),
            [] if self.has_source_type(owner) => self.push_member_diagnostic(
                &MemberResolutionFailure::Unresolved,
                name,
                self.file().span(location),
            ),
            [] => {}
            _ => {
                let span = self.file().span(location);
                let failure = MemberResolutionFailure::Ambiguous(
                    candidates
                        .iter()
                        .map(|field| field.symbol().clone())
                        .collect(),
                );
                self.record_member_ambiguity(&failure, JavaUsageKind::FieldReference, &span);
                self.push_member_diagnostic(&failure, name, span);
            }
        }
    }

    fn visible_fields(&self, owner: &str, name: &str) -> Vec<&FieldDeclaration> {
        self.model
            .resolution
            .field_lookup
            .get(&(owner.to_owned(), name.to_owned()))
            .into_iter()
            .flatten()
            .filter_map(|index| self.model.resolution.fields.get(*index))
            .filter(|field| {
                self.model
                    .resolution
                    .type_lookup
                    .is_visible(&self.file().source_set, field.source_set())
            })
            .collect()
    }

    fn visible_methods(&self, owner: &str, name: &str) -> Vec<&MethodDeclaration> {
        self.model
            .resolution
            .method_lookup
            .get(&(owner.to_owned(), name.to_owned()))
            .into_iter()
            .flatten()
            .filter_map(|index| self.model.resolution.methods.get(*index))
            .filter(|method| {
                self.model
                    .resolution
                    .type_lookup
                    .is_visible(&self.file().source_set, method.source_set())
            })
            .collect()
    }

    fn has_source_type(&self, owner: &str) -> bool {
        !self
            .model
            .resolution
            .type_lookup
            .visible_indices(owner, &self.file().source_set)
            .is_empty()
    }

    fn is_nonreference_identifier(node: Node<'_>) -> bool {
        let Some(parent) = node.parent() else {
            return true;
        };
        if matches!(
            parent.kind(),
            "package_declaration"
                | "import_declaration"
                | "labeled_statement"
                | "break_statement"
                | "continue_statement"
        ) {
            return true;
        }
        ["name", "field"]
            .into_iter()
            .filter_map(|field| parent.child_by_field_name(field))
            .any(|candidate| candidate.byte_range() == node.byte_range())
            || matches!(
                parent.kind(),
                "type_identifier" | "scoped_type_identifier" | "scoped_identifier"
            )
    }

    fn is_receiver_identifier(node: Node<'_>) -> bool {
        let Some(parent) = node.parent() else {
            return false;
        };
        parent
            .child_by_field_name("object")
            .is_some_and(|candidate| candidate.byte_range() == node.byte_range())
            || (parent.kind() == "method_reference"
                && named_children(parent)
                    .first()
                    .is_some_and(|candidate| candidate.byte_range() == node.byte_range()))
    }

    fn lookup_local(&self, name: &str) -> Option<&LocalBinding> {
        self.scopes.iter().rev().find_map(|scope| scope.get(name))
    }

    fn push_member_diagnostic(
        &mut self,
        failure: &MemberResolutionFailure,
        member: &str,
        span: JavaSourceSpanOutput,
    ) {
        let (code, message) = match failure {
            MemberResolutionFailure::Unresolved => (
                "java.unresolved-member",
                format!("Could not resolve member `{member}` without guessing"),
            ),
            MemberResolutionFailure::Ambiguous(_) => (
                "java.ambiguous-member",
                format!("Member `{member}` resolves to more than one visible declaration"),
            ),
            MemberResolutionFailure::Inaccessible => (
                "java.inaccessible-source-set-reference",
                format!("Member `{member}` exists only in a source set that is not visible here"),
            ),
        };
        self.record_diagnostic(JavaAnalysisDiagnosticOutput {
            code: code.to_owned(),
            severity: DiagnosticSeverity::Warning,
            message,
            span: Some(span),
        });
    }

    fn resolve_invocation(
        &self,
        node: Node<'_>,
    ) -> Result<Option<(&MethodDeclaration, ResolutionConfidence)>, MemberResolutionFailure> {
        let Some(name_node) = node.child_by_field_name("name") else {
            return Ok(None);
        };
        let Some(name) = self.file().text(name_node) else {
            return Ok(None);
        };
        let owner = node.child_by_field_name("object").map_or_else(
            || self.owners.last().cloned(),
            |object| {
                if object.kind() == "this" {
                    self.owners.last().cloned()
                } else {
                    self.expression_type(object)
                }
            },
        );
        let mut owners = owner.into_iter().collect::<BTreeSet<_>>();
        if node.child_by_field_name("object").is_none() {
            owners.extend(
                self.file()
                    .imports
                    .static_members
                    .iter()
                    .filter(|import| import.member == name)
                    .map(|import| import.owner.clone()),
            );
            owners.extend(self.file().imports.static_wildcard_owners.iter().cloned());
        }
        if owners.is_empty() {
            return Ok(None);
        }

        let arguments = node
            .child_by_field_name("arguments")
            .map(named_children)
            .unwrap_or_default();
        let argument_types = arguments
            .iter()
            .map(|argument| self.expression_type(*argument))
            .collect::<Vec<_>>();
        let all_arguments_resolved = argument_types.iter().all(Option::is_some);
        let mut candidates = owners
            .iter()
            .flat_map(|owner| self.visible_methods(owner, name))
            .filter(|method| method.parameter_types.len() == argument_types.len())
            .collect::<Vec<_>>();
        if all_arguments_resolved {
            candidates.retain(|method| {
                method
                    .parameter_types
                    .iter()
                    .zip(&argument_types)
                    .all(|(parameter, argument)| {
                        argument
                            .as_deref()
                            .is_some_and(|argument| java_types_match(parameter, argument))
                    })
            });
        }
        candidates.sort_by(|left, right| left.declaration.cmp(&right.declaration));
        candidates.dedup_by(|left, right| left.declaration == right.declaration);
        match candidates.as_slice() {
            [method] => Ok(Some((
                *method,
                if all_arguments_resolved {
                    ResolutionConfidence::Resolved
                } else {
                    ResolutionConfidence::PartiallyResolved
                },
            ))),
            [] => {
                let any_source_owner = owners.iter().any(|owner| self.has_source_type(owner));
                let any_hidden_member = self.has_hidden_method(&owners, name);
                if any_hidden_member {
                    Err(MemberResolutionFailure::Inaccessible)
                } else if any_source_owner {
                    Err(MemberResolutionFailure::Unresolved)
                } else {
                    Ok(None)
                }
            }
            _ => Err(MemberResolutionFailure::Ambiguous(
                candidates
                    .iter()
                    .map(|method| method.symbol().clone())
                    .collect(),
            )),
        }
    }

    fn has_hidden_method(&self, owners: &BTreeSet<String>, name: &str) -> bool {
        owners.iter().any(|owner| {
            self.model
                .resolution
                .method_lookup
                .get(&(owner.clone(), name.to_owned()))
                .into_iter()
                .flatten()
                .filter_map(|index| self.model.resolution.methods.get(*index))
                .any(|method| {
                    !self
                        .model
                        .resolution
                        .type_lookup
                        .is_visible(&self.file().source_set, method.source_set())
                })
        })
    }

    #[expect(
        clippy::too_many_lines,
        reason = "the Java grammar's expression variants are clearer as one exhaustive dispatcher"
    )]
    fn expression_type(&self, node: Node<'_>) -> Option<String> {
        match node.kind() {
            "identifier" => {
                let name = self.file().text(node)?;
                self.lookup_local(name)
                    .and_then(|binding| binding.value_type.clone())
                    .or_else(|| {
                        self.owners.last().and_then(|owner| {
                            let fields = self.visible_fields(owner, name);
                            (fields.len() == 1)
                                .then(|| fields[0].value_type.clone())
                                .flatten()
                        })
                    })
                    .or_else(|| {
                        self.resolve_type(name)
                            .ok()
                            .map(|resolved| resolved.qualified_name)
                    })
            }
            "this" => self.owners.last().cloned(),
            "scoped_identifier" => self
                .resolve_qualified_type_expression(node)
                .map(|resolved| resolved.qualified_name),
            "type_identifier" | "scoped_type_identifier" => self
                .file()
                .text(node)
                .and_then(|raw_type| self.resolve_type(raw_type).ok())
                .map(|resolved| resolved.qualified_name),
            "object_creation_expression" => node
                .child_by_field_name("type")
                .and_then(|type_node| self.file().text(type_node))
                .and_then(|raw_type| self.resolve_type(raw_type).ok())
                .map(|resolved| resolved.qualified_name),
            "array_creation_expression" => {
                let raw_type = node
                    .child_by_field_name("type")
                    .and_then(|type_node| self.file().text(type_node))?;
                let dimensions = named_children(node)
                    .into_iter()
                    .filter(|child| matches!(child.kind(), "dimensions" | "dimensions_expr"))
                    .count()
                    .max(1);
                self.resolve_type(&format!("{raw_type}{}", "[]".repeat(dimensions)))
                    .ok()
                    .map(|resolved| resolved.qualified_name)
            }
            "cast_expression" => node
                .child_by_field_name("type")
                .and_then(|type_node| self.file().text(type_node))
                .and_then(|raw_type| self.resolve_type(raw_type).ok())
                .map(|resolved| resolved.qualified_name),
            "parenthesized_expression" => {
                first_named_child(node).and_then(|expression| self.expression_type(expression))
            }
            "field_access" => {
                if let Some(resolved) = self.resolve_qualified_type_expression(node) {
                    return Some(resolved.qualified_name);
                }
                let field_name = node
                    .child_by_field_name("field")
                    .and_then(|field| self.file().text(field))?;
                let owner = node.child_by_field_name("object").and_then(|object| {
                    if object.kind() == "this" {
                        self.owners.last().cloned()
                    } else {
                        self.expression_type(object)
                    }
                })?;
                let fields = self.visible_fields(&owner, field_name);
                (fields.len() == 1)
                    .then(|| fields[0].value_type.clone())
                    .flatten()
            }
            "method_invocation" => self
                .resolve_invocation(node)
                .ok()
                .flatten()
                .and_then(|(method, _)| method.return_type.clone()),
            "array_access" => node
                .child_by_field_name("array")
                .and_then(|array| self.expression_type(array))
                .and_then(|array_type| array_type.strip_suffix("[]").map(ToOwned::to_owned)),
            "decimal_integer_literal"
            | "hex_integer_literal"
            | "binary_integer_literal"
            | "octal_integer_literal" => {
                let text = self.file().text(node).unwrap_or_default();
                Some(
                    if text.ends_with('l') || text.ends_with('L') {
                        "long"
                    } else {
                        "int"
                    }
                    .to_owned(),
                )
            }
            "decimal_floating_point_literal" | "hex_floating_point_literal" => {
                let text = self.file().text(node).unwrap_or_default();
                Some(
                    if text.ends_with('f') || text.ends_with('F') {
                        "float"
                    } else {
                        "double"
                    }
                    .to_owned(),
                )
            }
            "true" | "false" => Some("boolean".to_owned()),
            "character_literal" => Some("char".to_owned()),
            "string_literal" | "text_block" => Some("java.lang.String".to_owned()),
            _ => None,
        }
    }
}

fn java_types_match(parameter: &str, argument: &str) -> bool {
    parameter == argument
        || matches!(
            (parameter, argument),
            ("long", "int") | ("float", "int" | "long") | ("double", "int" | "long" | "float")
        )
}

fn index_fingerprint(
    evidence: &[IndexSourceEvidence],
    definitions: &[JavaSymbolDefinitionOutput],
    usages: &[JavaSymbolUsageOutput],
    diagnostics: &[JavaAnalysisDiagnosticOutput],
) -> String {
    let mut hasher = blake3::Hasher::new();
    hasher.update(b"sfm-java-symbol-index/1\0");
    for file in evidence {
        hash_string(&mut hasher, &file.report_path);
        hash_string(&mut hasher, &file.source_set);
        hash_string(&mut hasher, &file.source_hash);
    }
    for definition in definitions {
        hash_identity(&mut hasher, &definition.symbol);
        hash_span(&mut hasher, &definition.identifier_span);
        hash_span(&mut hasher, &definition.declaration_span);
        hash_string(&mut hasher, &format!("{:?}", definition.confidence));
    }
    for usage in usages {
        hash_identity(&mut hasher, &usage.target);
        hash_string(&mut hasher, &format!("{:?}", usage.kind));
        hash_span(&mut hasher, &usage.span);
        hash_string(&mut hasher, &format!("{:?}", usage.confidence));
    }
    for diagnostic in diagnostics {
        hash_string(&mut hasher, &diagnostic.code);
        hash_string(&mut hasher, &format!("{:?}", diagnostic.severity));
        hash_string(&mut hasher, &diagnostic.message);
        if let Some(span) = &diagnostic.span {
            hash_span(&mut hasher, span);
        }
    }
    format!("blake3:{}", hasher.finalize().to_hex())
}

fn hash_identity(hasher: &mut blake3::Hasher, identity: &JavaSymbolIdentityOutput) {
    hash_string(hasher, &format!("{:?}", identity.kind));
    hash_string(hasher, &identity.owner);
    hash_string(hasher, &identity.name);
    hash_string(hasher, identity.descriptor.as_deref().unwrap_or_default());
    hash_string(hasher, &identity.qualified_name);
}

fn hash_span(hasher: &mut blake3::Hasher, span: &JavaSourceSpanOutput) {
    hash_string(hasher, &span.path);
    hash_string(hasher, &span.source_set);
    hash_string(hasher, &span.source_hash);
    hasher.update(&span.start_byte.to_le_bytes());
    hasher.update(&span.end_byte.to_le_bytes());
    hasher.update(&span.start_line.to_le_bytes());
    hasher.update(&span.start_column.to_le_bytes());
    hasher.update(&span.end_line.to_le_bytes());
    hasher.update(&span.end_column.to_le_bytes());
}

fn hash_string(hasher: &mut blake3::Hasher, value: &str) {
    hasher.update(&(value.len() as u64).to_le_bytes());
    hasher.update(value.as_bytes());
}

fn relevant_diagnostics(
    selector: &JavaSymbolSelector,
    _definitions: &[JavaSymbolDefinitionOutput],
    _usages: &[JavaSymbolUsageOutput],
    diagnostics: &[JavaAnalysisDiagnosticOutput],
) -> Vec<JavaAnalysisDiagnosticOutput> {
    let (owner, member) = match selector {
        JavaSymbolSelector::Type { owner } => (owner.as_str(), None),
        JavaSymbolSelector::Field { owner, name }
        | JavaSymbolSelector::Method { owner, name, .. } => (owner.as_str(), Some(name.as_str())),
    };
    let simple_owner = owner.rsplit(['.', '$']).next().unwrap_or(owner);
    let owner_marker = format!("`{owner}`");
    let simple_owner_marker = format!("`{simple_owner}`");
    let member_marker = member.map(|member| format!("`{member}`"));
    let mut selected = diagnostics
        .iter()
        .filter(|diagnostic| {
            (diagnostic.code == "java.parse-gap" && diagnostic.span.is_some())
                || diagnostic.message.contains(&owner_marker)
                || diagnostic.message.contains(&simple_owner_marker)
                || member_marker
                    .as_ref()
                    .is_some_and(|member| diagnostic.message.contains(member))
        })
        .cloned()
        .collect::<Vec<_>>();
    selected.sort();
    selected.dedup();
    if diagnostics.len() > selected.len() {
        selected.push(JavaAnalysisDiagnosticOutput {
            code: "java.unrelated-workspace-diagnostics-suppressed".to_owned(),
            severity: DiagnosticSeverity::Info,
            message: format!(
                "Additional diagnostics unrelated to selector `{}` were suppressed",
                selector.canonical()
            ),
            span: None,
        });
    }
    selected
}

fn relevant_list_diagnostics(
    pattern: &JavaSymbolGlob,
    definitions: &[JavaSymbolDefinitionOutput],
    diagnostics: &[JavaAnalysisDiagnosticOutput],
) -> Vec<JavaAnalysisDiagnosticOutput> {
    let selected_paths = definitions
        .iter()
        .flat_map(|definition| {
            [
                definition.identifier_span.path.as_str(),
                definition.declaration_span.path.as_str(),
            ]
        })
        .collect::<BTreeSet<_>>();
    let mut selected = diagnostics
        .iter()
        .filter(|diagnostic| {
            diagnostic
                .span
                .as_ref()
                .is_some_and(|span| selected_paths.contains(span.path.as_str()))
        })
        .cloned()
        .collect::<Vec<_>>();
    selected.sort();
    selected.dedup();
    let suppressed = diagnostics.len().saturating_sub(selected.len());
    if suppressed > 0 {
        selected.push(JavaAnalysisDiagnosticOutput {
            code: "java.unrelated-list-diagnostics-suppressed".to_owned(),
            severity: DiagnosticSeverity::Info,
            message: format!(
                "Suppressed {suppressed} diagnostics outside files matched by symbol glob `{}`",
                pattern.pattern()
            ),
            span: None,
        });
    }
    selected
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::java_analysis::DefinitionDocumentInput;
    use crate::java_analysis::DefinitionTextPositionInput;
    use crate::java_analysis::DefinitionWorkspaceIdentityInput;
    use crate::java_analysis::JavaClasspathMode;
    use crate::java_analysis::JavaSourceRootKind;
    use crate::java_analysis::JavaSourceRootOutput;
    use crate::java_analysis::JavaSourceSetOutput;

    #[test]
    fn usage_scan_retains_a_deterministic_bounded_set_under_adversarial_volume() {
        let mut forward = UsageAtPositionScan::new();
        let mut reverse = UsageAtPositionScan::new();
        let candidate_count = USAGE_AT_POSITION_MAX_RESULTS * 8;
        for index in 0..candidate_count {
            forward.record_usage(raw_usage(index));
        }
        for index in (0..candidate_count).rev() {
            reverse.record_usage(raw_usage(index));
        }

        assert_eq!(forward.usages.len(), USAGE_AT_POSITION_MAX_RESULTS);
        assert_eq!(reverse.usages.len(), USAGE_AT_POSITION_MAX_RESULTS);
        assert_eq!(forward.usages, reverse.usages);
        assert_eq!(
            forward.discarded_candidate_count,
            u64::try_from(candidate_count - USAGE_AT_POSITION_MAX_RESULTS).expect("fixture count")
        );
        assert_eq!(
            reverse.discarded_candidate_count,
            u64::try_from(candidate_count - USAGE_AT_POSITION_MAX_RESULTS).expect("fixture count")
        );
    }

    #[test]
    fn usage_target_cache_evicts_lru_entries_with_explicit_memory_bounds() {
        let mut cache = UsageTargetCache::default();
        let mut targets = Vec::new();
        for index in 0..=USAGE_TARGET_CACHE_MAX_ENTRIES {
            let mut usage = raw_usage(index);
            usage.target.name = format!("Target{index:02}");
            usage.target.qualified_name = format!("example.Target{index:02}");
            let target = usage.target.clone();
            let mut scan = UsageAtPositionScan::new();
            scan.record_usage(usage);
            cache.insert(target.clone(), Arc::new(scan));
            targets.push(target);
        }

        assert_eq!(cache.entries.len(), USAGE_TARGET_CACHE_MAX_ENTRIES);
        assert!(cache.retained_bytes <= USAGE_TARGET_CACHE_MAX_RETAINED_BYTES);
        assert_eq!(cache.evictions, 1);
        assert!(cache.get(&targets[0]).is_none());
        assert!(cache.get(targets.last().expect("last target")).is_some());
    }

    fn raw_usage(index: usize) -> JavaSymbolUsageOutput {
        let byte = u64::try_from(index).expect("fixture index");
        JavaSymbolUsageOutput {
            target: JavaSymbolIdentityOutput {
                kind: JavaSymbolKind::Class,
                owner: "example".to_owned(),
                name: "Target".to_owned(),
                descriptor: None,
                qualified_name: "example.Target".to_owned(),
            },
            kind: JavaUsageKind::TypeReference,
            span: JavaSourceSpanOutput {
                path: format!("source/example/Use{index:08}.java"),
                source_set: "scenario".to_owned(),
                source_hash: format!("blake3:{index:064x}"),
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

    fn context(source_sets: &[(&str, &[&str])]) -> JavaAnalysisContextOutput {
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
            source_sets: source_sets
                .iter()
                .map(|(id, visible)| JavaSourceSetOutput {
                    id: (*id).to_owned(),
                    visible_source_sets: visible.iter().map(|value| (*value).to_owned()).collect(),
                })
                .collect(),
            source_exclusions: Vec::new(),
            classpath_mode: JavaClasspathMode::Isolated,
            classpath_fingerprint: "blake3:isolated".to_owned(),
            parser_fingerprint: String::new(),
            index_fingerprint: String::new(),
        }
    }

    fn parsed(path: &str, source_set: &str, source: &str) -> JavaSyntaxFile {
        JavaSyntaxFile::parse_text(path, source_set, source.to_owned())
            .expect("fixture Java source should parse")
    }

    fn index(mut files: Vec<JavaSyntaxFile>) -> JavaSymbolIndex {
        let referenced_platform_types = ["String", "Object", "StringBuilder"]
            .into_iter()
            .filter(|name| files.iter().any(|file| file.source.contains(name)))
            .collect::<Vec<_>>();
        let mut context = context(&[("scenario", &["scenario"])]);
        let mut visibility = BTreeSet::from([("scenario".to_owned(), "scenario".to_owned())]);
        if !referenced_platform_types.is_empty() {
            context.source_sets[0]
                .visible_source_sets
                .push("jdk:fixture".to_owned());
            context.source_sets.push(JavaSourceSetOutput {
                id: "jdk:fixture".to_owned(),
                visible_source_sets: vec!["jdk:fixture".to_owned()],
            });
            visibility.insert(("scenario".to_owned(), "jdk:fixture".to_owned()));
            visibility.insert(("jdk:fixture".to_owned(), "jdk:fixture".to_owned()));
            files.extend(referenced_platform_types.into_iter().map(|name| {
                parsed(
                    &format!("jdk/java.base/java/lang/{name}.java"),
                    "jdk:fixture",
                    &format!("package java.lang; public class {name} {{}}"),
                )
            }));
        }
        JavaSymbolIndex::build_from_parsed(context, files, visibility)
    }

    fn selector(terms: &[&str]) -> JavaSymbolSelector {
        JavaSymbolSelector::parse_terms(
            &terms
                .iter()
                .map(|term| (*term).to_owned())
                .collect::<Vec<_>>(),
        )
        .expect("selector should parse")
    }

    fn position_workspace(sources: &[(&str, &str)]) -> (tempfile::TempDir, JavaSourceWorkspace) {
        let directory = tempfile::tempdir().expect("temporary definition workspace");
        let root = directory.path().join("source");
        let mut files = Vec::new();
        for (relative, source) in sources {
            let absolute_path = root.join(relative);
            std::fs::create_dir_all(absolute_path.parent().expect("source parent"))
                .expect("source directory");
            std::fs::write(&absolute_path, source).expect("Java fixture");
            files.push(JavaSourceFile {
                absolute_path,
                root_id: "scenario".to_owned(),
                root_relative_path: (*relative).to_owned(),
                report_path: format!("source/{relative}"),
                source_set: "scenario".to_owned(),
                source_override: None,
            });
        }
        files.sort_by(|left, right| left.report_path.cmp(&right.report_path));
        (
            directory,
            JavaSourceWorkspace {
                context: context(&[("scenario", &["scenario"])]),
                root_authorities: vec![super::super::JavaSourceRootAuthority {
                    root_id: "scenario".to_owned(),
                    source_set: "scenario".to_owned(),
                    canonical_absolute_path: root,
                    report_root_path: "source".to_owned(),
                }],
                files,
                diagnostics: Vec::new(),
                classpath_entries: Vec::new(),
                jdk_sources: JdkSourceDomainState::Disabled,
            },
        )
    }

    fn position_request(
        workspace: &JavaSourceWorkspace,
        relative: &str,
        text: &str,
        line: u64,
        column: u64,
    ) -> DefinitionAtPositionRequest {
        let file = workspace
            .files
            .iter()
            .find(|file| file.root_id == "scenario" && file.root_relative_path == relative)
            .expect("fixture request file");
        position_request_for_file(workspace, file, text, line, column)
    }

    fn position_request_for_file(
        workspace: &JavaSourceWorkspace,
        file: &JavaSourceFile,
        text: &str,
        line: u64,
        column: u64,
    ) -> DefinitionAtPositionRequest {
        let workspace_fingerprint = definition_workspace_fingerprint(&workspace.context, None)
            .expect("fixture workspace fingerprint");
        DefinitionAtPositionRequest::new(
            17,
            2,
            DefinitionWorkspaceIdentityInput {
                branch: workspace.context.branch.clone(),
                classpath_mode: workspace.context.classpath_mode,
                source_roots: workspace.context.source_roots.clone(),
                classpath_fingerprint: workspace.context.classpath_fingerprint.clone(),
                dependency_index_identity: None,
                workspace_fingerprint,
                workspace_generation: 5,
            },
            DefinitionDocumentInput {
                address: format!("workspace://{}/{}", file.root_id, file.root_relative_path),
                root_id: file.root_id.clone(),
                root_relative_path: file.root_relative_path.clone(),
                report_path: file.report_path.clone(),
                source_set: file.source_set.clone(),
                text: text.to_owned(),
                content_hash: blake3_content_hash(text),
                disk_content_hash: None,
            },
            DefinitionTextPositionInput::from_line_column(text, line, column)
                .expect("fixture position"),
        )
    }

    fn dependency_type(qualified_name: &str) -> DependencyJavaSymbolIndexBody {
        let (owner, name) = qualified_name
            .rsplit_once('.')
            .expect("dependency type has a package");
        let span = JavaSourceSpanOutput {
            path: format!(
                "dependency/minecraft/main/pipeline/{}.java",
                qualified_name.replace('.', "/")
            ),
            source_set: "dependency:minecraft:main".to_owned(),
            source_hash: "blake3:dependency-source".to_owned(),
            start_byte: 0,
            end_byte: 1,
            start_line: 1,
            start_column: 1,
            end_line: 1,
            end_column: 2,
        };
        DependencyJavaSymbolIndexBody::new(
            vec![JavaSymbolDefinitionOutput {
                symbol: JavaSymbolIdentityOutput {
                    kind: JavaSymbolKind::Class,
                    owner: owner.to_owned(),
                    name: name.to_owned(),
                    descriptor: None,
                    qualified_name: qualified_name.to_owned(),
                },
                identifier_span: span.clone(),
                declaration_span: span,
                confidence: ResolutionConfidence::Resolved,
            }],
            Vec::new(),
            Vec::new(),
        )
    }

    #[test]
    fn dependency_source_sets_inherit_jdk_visibility_added_before_them() {
        let mut context = context(&[
            ("main", &["main", "jdk:fixture"]),
            ("jdk:fixture", &["main", "jdk:fixture"]),
        ]);
        context.source_roots.push(JavaSourceRootOutput {
            id: "jdk-fixture".to_owned(),
            source_set: "jdk:fixture".to_owned(),
            path: "jdk".to_owned(),
            kind: JavaSourceRootKind::Jdk,
            exists: true,
        });

        add_dependency_source_sets(&mut context, &dependency_type("example.Dependency"));

        let dependency = context
            .source_sets
            .iter()
            .find(|source_set| source_set.id == "dependency:minecraft:main")
            .expect("dependency source set should be declared");
        assert_eq!(
            dependency.visible_source_sets,
            ["dependency:minecraft:main", "jdk:fixture"]
        );
    }

    #[test]
    fn varargs_constructor_keeps_its_array_descriptor_and_resolves_every_type_glyph() {
        let widget = concat!(
            "package p;\n",
            "public class Widget {\n",
            "    public Widget(String key) {}\n",
            "    public Widget(String key, Object... args) {}\n",
            "}\n",
        );
        let use_source = concat!(
            "package q;\n",
            "import p.Widget;\n",
            "class Use {\n",
            "    Widget create(String key, Object[] args) {\n",
            "        return new Widget(key, args);\n",
            "    }\n",
            "}\n",
        );
        let symbol_index = index(vec![
            parsed("source/p/Widget.java", "scenario", widget),
            parsed("source/q/Use.java", "scenario", use_source),
        ]);
        let descriptors = symbol_index
            .definitions
            .iter()
            .filter(|definition| {
                definition.symbol.owner == "p.Widget"
                    && definition.symbol.kind == JavaSymbolKind::Constructor
            })
            .map(|definition| definition.symbol.descriptor.as_deref())
            .collect::<Vec<_>>();
        assert_eq!(
            descriptors,
            [
                Some("(Ljava/lang/String;)V"),
                Some("(Ljava/lang/String;[Ljava/lang/Object;)V"),
            ]
        );

        let (_directory, workspace) = position_workspace(&[
            (
                "java/lang/Object.java",
                "package java.lang; public class Object {}\n",
            ),
            (
                "java/lang/String.java",
                "package java.lang; public class String {}\n",
            ),
            ("p/Widget.java", widget),
            ("q/Use.java", use_source),
        ]);
        for column in 20..=25 {
            let request = position_request(&workspace, "q/Use.java", use_source, 5, column);
            let result = analyze_definition_at_position(&workspace, &request, None)
                .expect("varargs constructor position analysis");
            assert_eq!(
                result.outcome,
                DefinitionAtPositionOutcome::Success,
                "column {column}: {:?}",
                result.diagnostics
            );
            assert_eq!(result.definitions.len(), 1, "column {column}");
            assert_eq!(
                result.definitions[0].symbol.descriptor.as_deref(),
                Some("(Ljava/lang/String;[Ljava/lang/Object;)V"),
                "column {column}"
            );
        }
    }

    #[test]
    fn fully_qualified_static_receiver_resolves_owner_and_member_regions() {
        let compat = concat!(
            "package p;\n",
            "public class Compat {\n",
            "    public static boolean ready() { return true; }\n",
            "}\n",
        );
        let use_source = concat!(
            "package q;\n",
            "class Use {\n",
            "    boolean call() {\n",
            "        return p.Compat.ready();\n",
            "    }\n",
            "}\n",
        );
        let (_directory, workspace) =
            position_workspace(&[("p/Compat.java", compat), ("q/Use.java", use_source)]);

        for column in 16..=23 {
            let request = position_request(&workspace, "q/Use.java", use_source, 4, column);
            let result = analyze_definition_at_position(&workspace, &request, None)
                .expect("qualified owner position analysis");
            assert_eq!(result.outcome, DefinitionAtPositionOutcome::Success);
            assert_eq!(result.definitions[0].symbol.qualified_name, "p.Compat");
        }
        for column in 25..=29 {
            let request = position_request(&workspace, "q/Use.java", use_source, 4, column);
            let result = analyze_definition_at_position(&workspace, &request, None)
                .expect("qualified member position analysis");
            assert_eq!(result.outcome, DefinitionAtPositionOutcome::Success);
            assert_eq!(
                result.definitions[0].symbol.qualified_name,
                "p.Compat.ready()Z"
            );
        }
    }

    #[test]
    fn value_root_wins_over_same_spelled_qualified_type_receiver() {
        let compat = concat!(
            "package p;\n",
            "public class Compat {\n",
            "    public static boolean ready() { return true; }\n",
            "}\n",
        );
        let use_source = concat!(
            "package q;\n",
            "class Holder { p.Compat Compat; }\n",
            "class Use {\n",
            "    Holder p;\n",
            "    boolean call() {\n",
            "        return p.Compat.ready();\n",
            "    }\n",
            "}\n",
        );
        let (_directory, workspace) =
            position_workspace(&[("p/Compat.java", compat), ("q/Use.java", use_source)]);
        let request = position_request(&workspace, "q/Use.java", use_source, 6, 18);
        let result = analyze_definition_at_position(&workspace, &request, None)
            .expect("value-root collision analysis");

        assert_eq!(result.outcome, DefinitionAtPositionOutcome::Success);
        assert_eq!(result.definitions.len(), 1);
        assert_eq!(result.definitions[0].symbol.kind, JavaSymbolKind::Field);
        assert_eq!(result.definitions[0].symbol.owner, "q.Holder");
        assert_eq!(result.definitions[0].symbol.name, "Compat");
    }

    #[test]
    fn definition_at_position_uses_current_unicode_crlf_snapshot_and_import_resolution() {
        let (_directory, workspace) = position_workspace(&[
            ("p/Café.java", "package p; public class Café {}\r\n"),
            ("q/Use.java", "package q; class Use {}\r\n"),
        ]);
        let current = concat!(
            "package q;\r\n",
            "import p.Café;\r\n",
            "class Use {\r\n",
            "    Café field;\r\n",
            "}\r\n",
        );
        let request = position_request(&workspace, "q/Use.java", current, 4, 6);
        let result =
            analyze_definition_at_position(&workspace, &request, None).expect("position analysis");

        assert_eq!(result.outcome, DefinitionAtPositionOutcome::Success);
        assert_eq!(result.symbols[0].qualified_name, "p.Café");
        assert_eq!(
            result.definitions[0].identifier_span.report_path,
            "source/p/Café.java"
        );
        assert_eq!(result.position.byte_offset, 47);
    }

    #[test]
    fn definition_at_position_resolves_fields_and_methods_from_usage_collector() {
        let use_source = concat!(
            "package q;\n",
            "import p.A;\n",
            "class Use {\n",
            "    void use(A target) {\n",
            "        int copy = target.value;\n",
            "        target.run(1);\n",
            "    }\n",
            "}\n",
        );
        let (_directory, workspace) = position_workspace(&[
            (
                "p/A.java",
                "package p; public class A { public int value; public void run(int input) {} }\n",
            ),
            ("q/Use.java", use_source),
        ]);

        let field = analyze_definition_at_position(
            &workspace,
            &position_request(&workspace, "q/Use.java", use_source, 5, 28),
            None,
        )
        .expect("field definition");
        assert_eq!(field.outcome, DefinitionAtPositionOutcome::Success);
        assert_eq!(field.symbols[0].canonical_selector(), "p.A value");

        let method = analyze_definition_at_position(
            &workspace,
            &position_request(&workspace, "q/Use.java", use_source, 6, 17),
            None,
        )
        .expect("method definition");
        assert_eq!(method.outcome, DefinitionAtPositionOutcome::Success);
        assert_eq!(method.symbols[0].canonical_selector(), "p.A run(I)V");
    }

    #[test]
    fn definition_at_position_resolves_parameters_and_block_local_variables() {
        let source = concat!(
            "package q;\n",
            "class Use {\n",
            "    void run(String input) {\n",
            "        String fromParameter = input;\n",
            "        {\n",
            "            String value = fromParameter;\n",
            "            String first = value;\n",
            "        }\n",
            "        {\n",
            "            String value = fromParameter;\n",
            "            String second = value;\n",
            "        }\n",
            "    }\n",
            "}\n",
        );
        let (_directory, workspace) = position_workspace(&[("q/Use.java", source)]);

        let parameter = analyze_definition_at_position(
            &workspace,
            &position_request(&workspace, "q/Use.java", source, 4, 32),
            None,
        )
        .expect("parameter definition");
        assert_eq!(parameter.outcome, DefinitionAtPositionOutcome::Success);
        assert_eq!(parameter.symbols[0].kind, JavaSymbolKind::Parameter);
        assert_eq!(parameter.definitions[0].identifier_span.start_line, 3);

        let first_block = analyze_definition_at_position(
            &workspace,
            &position_request(&workspace, "q/Use.java", source, 7, 28),
            None,
        )
        .expect("first block local definition");
        assert_eq!(first_block.outcome, DefinitionAtPositionOutcome::Success);
        assert_eq!(first_block.symbols[0].kind, JavaSymbolKind::LocalVariable);
        assert_eq!(first_block.definitions[0].identifier_span.start_line, 6);

        let second_block = analyze_definition_at_position(
            &workspace,
            &position_request(&workspace, "q/Use.java", source, 11, 29),
            None,
        )
        .expect("second block local definition");
        assert_eq!(second_block.outcome, DefinitionAtPositionOutcome::Success);
        assert_eq!(second_block.symbols[0].kind, JavaSymbolKind::LocalVariable);
        assert_eq!(second_block.definitions[0].identifier_span.start_line, 10);
        assert_ne!(first_block.symbols, second_block.symbols);
    }

    #[test]
    fn definition_at_position_uses_exact_annotation_and_import_subject_spans() {
        let source = concat!(
            "package q;\n",
            "import p.A;\n",
            "import p.Marker;\n",
            "import static p.Tools.VALUE;\n",
            "import static p.Tools.run;\n",
            "@Marker\n",
            "class Use { A value = new A(); int number = VALUE; void call() { run(); } }\n",
        );
        let (_directory, workspace) = position_workspace(&[
            ("p/A.java", "package p; public class A {}\n"),
            ("p/Marker.java", "package p; public @interface Marker {}\n"),
            (
                "p/Tools.java",
                "package p; public class Tools { public static int VALUE; public static void run() {} }\n",
            ),
            ("q/Use.java", source),
        ]);
        let column = |line_number: usize, needle: &str| {
            let line = source.lines().nth(line_number - 1).expect("fixture line");
            u64::try_from(line.find(needle).expect("fixture needle") + 1).expect("column fits u64")
        };

        let imported_type = analyze_definition_at_position(
            &workspace,
            &position_request(&workspace, "q/Use.java", source, 2, column(2, "A")),
            None,
        )
        .expect("direct import definition");
        assert_eq!(imported_type.outcome, DefinitionAtPositionOutcome::Success);
        assert_eq!(imported_type.symbols[0].canonical_selector(), "p.A");

        let static_owner = analyze_definition_at_position(
            &workspace,
            &position_request(&workspace, "q/Use.java", source, 4, column(4, "Tools")),
            None,
        )
        .expect("static import owner definition");
        assert_eq!(static_owner.outcome, DefinitionAtPositionOutcome::Success);
        assert_eq!(static_owner.symbols[0].canonical_selector(), "p.Tools");

        let static_field = analyze_definition_at_position(
            &workspace,
            &position_request(&workspace, "q/Use.java", source, 4, column(4, "VALUE")),
            None,
        )
        .expect("static imported field definition");
        assert_eq!(static_field.outcome, DefinitionAtPositionOutcome::Success);
        assert_eq!(
            static_field.symbols[0].canonical_selector(),
            "p.Tools VALUE"
        );

        let static_method = analyze_definition_at_position(
            &workspace,
            &position_request(&workspace, "q/Use.java", source, 5, column(5, "run")),
            None,
        )
        .expect("static imported method definition");
        assert_eq!(static_method.outcome, DefinitionAtPositionOutcome::Success);
        assert_eq!(
            static_method.symbols[0].canonical_selector(),
            "p.Tools run()V"
        );

        let annotation = analyze_definition_at_position(
            &workspace,
            &position_request(&workspace, "q/Use.java", source, 6, column(6, "Marker")),
            None,
        )
        .expect("annotation definition");
        assert_eq!(annotation.outcome, DefinitionAtPositionOutcome::Success);
        assert_eq!(annotation.symbols[0].canonical_selector(), "p.Marker");
    }

    #[test]
    fn definition_at_position_resolves_wildcard_imported_type_receiver() {
        let use_source = concat!(
            "package q;\n",
            "import p.*;\n",
            "class Use { void call() { Target.run(); } }\n",
        );
        let (_directory, workspace) = position_workspace(&[
            (
                "p/Target.java",
                "package p; public class Target { public static void run() {} }\n",
            ),
            ("q/Use.java", use_source),
        ]);
        let line = use_source.lines().nth(2).expect("usage line");
        let column =
            u64::try_from(line.find("Target").expect("receiver") + 2).expect("receiver column");

        let result = analyze_definition_at_position(
            &workspace,
            &position_request(&workspace, "q/Use.java", use_source, 3, column),
            None,
        )
        .expect("wildcard receiver definition");

        assert_eq!(result.outcome, DefinitionAtPositionOutcome::Success);
        assert_eq!(result.symbols[0].canonical_selector(), "p.Target");
        assert_eq!(
            result.definitions[0].identifier_span.report_path,
            "source/p/Target.java"
        );
    }

    #[test]
    fn definition_at_position_does_not_guess_bare_tokens_in_strings() {
        let source = concat!(
            "package q;\n",
            "class Use {\n",
            "    String text = \"A.value\";\n",
            "}\n",
        );
        let (_directory, workspace) = position_workspace(&[("q/Use.java", source)]);
        let result = analyze_definition_at_position(
            &workspace,
            &position_request(&workspace, "q/Use.java", source, 3, 21),
            None,
        )
        .expect("no-symbol analysis");

        assert_eq!(result.outcome, DefinitionAtPositionOutcome::NoSymbol);
        assert!(result.symbols.is_empty());
        assert!(result.definitions.is_empty());
    }

    #[test]
    fn definition_at_position_resolves_nested_types_and_matches_exact_definition() {
        let use_source = concat!(
            "package q;\n",
            "import p.Outer;\n",
            "class Use { Outer.Inner value; }\n",
        );
        let (_directory, workspace) = position_workspace(&[
            (
                "p/Outer.java",
                "package p; public class Outer { public static class Inner {} }\n",
            ),
            ("q/Use.java", use_source),
        ]);
        let result = analyze_definition_at_position(
            &workspace,
            &position_request(&workspace, "q/Use.java", use_source, 3, 21),
            None,
        )
        .expect("nested definition");
        let exact = JavaSymbolIndex::build(&workspace)
            .expect("exact index")
            .definition(&selector(&["p.Outer$Inner"]));

        assert_eq!(result.outcome, DefinitionAtPositionOutcome::Success);
        assert_eq!(result.symbols, vec![exact.definitions[0].symbol.clone()]);
        assert_eq!(
            result.definitions[0].identifier_span.report_path,
            exact.definitions[0].identifier_span.path
        );
    }

    #[test]
    fn definition_at_position_treats_comments_and_whitespace_as_no_symbol() {
        let source = concat!(
            "package q;\n",
            "class Use {\n",
            "    // A value is only commentary\n",
            "    String value;\n",
            "}\n",
        );
        let (_directory, workspace) = position_workspace(&[("q/Use.java", source)]);
        for (line, column) in [(3, 8), (4, 11)] {
            let result = analyze_definition_at_position(
                &workspace,
                &position_request(&workspace, "q/Use.java", source, line, column),
                None,
            )
            .expect("no-symbol location");
            assert_eq!(result.outcome, DefinitionAtPositionOutcome::NoSymbol);
            assert!(result.symbols.is_empty());
        }
    }

    #[test]
    fn definition_at_position_returns_typed_invalid_position() {
        let source = "package p; class A {}\n";
        let (_directory, workspace) = position_workspace(&[("p/A.java", source)]);
        let mut request = position_request(&workspace, "p/A.java", source, 1, 18);
        request.position.byte_offset += 1;

        let result = analyze_definition_at_position(&workspace, &request, None)
            .expect("typed invalid request");
        assert_eq!(result.outcome, DefinitionAtPositionOutcome::InvalidRequest);
        assert!(result.diagnostics.iter().any(|diagnostic| {
            diagnostic.code == "java.definition-position-invalid-request"
                && diagnostic.message.contains("byte")
        }));
    }

    #[test]
    fn definition_at_position_obeys_directed_source_set_visibility() {
        let directory = tempfile::tempdir().expect("source-set definition workspace");
        let main_root = directory.path().join("source-main");
        let test_root = directory.path().join("source-test");
        std::fs::create_dir_all(main_root.join("main")).expect("main source directory");
        std::fs::create_dir_all(test_root.join("hidden")).expect("test source directory");
        let main_source = "package main; import hidden.Hidden; class Consumer { Hidden value; }\n";
        let hidden_source = "package hidden; public class Hidden {}\n";
        std::fs::write(main_root.join("main/Consumer.java"), main_source).expect("main source");
        std::fs::write(test_root.join("hidden/Hidden.java"), hidden_source).expect("test source");
        let mut analysis_context = context(&[("main", &["main"]), ("test", &["test", "main"])]);
        analysis_context.source_roots = vec![
            JavaSourceRootOutput {
                id: "main-root".to_owned(),
                source_set: "main".to_owned(),
                path: "source-main".to_owned(),
                kind: JavaSourceRootKind::Custom,
                exists: true,
            },
            JavaSourceRootOutput {
                id: "test-root".to_owned(),
                source_set: "test".to_owned(),
                path: "source-test".to_owned(),
                kind: JavaSourceRootKind::Custom,
                exists: true,
            },
        ];
        let workspace = JavaSourceWorkspace {
            context: analysis_context,
            root_authorities: Vec::new(),
            files: vec![
                JavaSourceFile {
                    absolute_path: main_root.join("main/Consumer.java"),
                    root_id: "main-root".to_owned(),
                    root_relative_path: "main/Consumer.java".to_owned(),
                    report_path: "source-main/main/Consumer.java".to_owned(),
                    source_set: "main".to_owned(),
                    source_override: None,
                },
                JavaSourceFile {
                    absolute_path: test_root.join("hidden/Hidden.java"),
                    root_id: "test-root".to_owned(),
                    root_relative_path: "hidden/Hidden.java".to_owned(),
                    report_path: "source-test/hidden/Hidden.java".to_owned(),
                    source_set: "test".to_owned(),
                    source_override: None,
                },
            ],
            diagnostics: Vec::new(),
            classpath_entries: Vec::new(),
            jdk_sources: JdkSourceDomainState::Disabled,
        };
        let request =
            position_request_for_file(&workspace, &workspace.files[0], main_source, 1, 55);
        let result = analyze_definition_at_position(&workspace, &request, None)
            .expect("inaccessible source-set query");

        assert_eq!(result.outcome, DefinitionAtPositionOutcome::NoSymbol);
        assert!(
            result
                .diagnostics
                .iter()
                .any(|diagnostic| { diagnostic.code == "java.inaccessible-source-set-reference" })
        );
    }

    #[test]
    fn definition_at_position_rejects_non_workspace_document_identity() {
        let source = "package p; class A {}\n";
        let (_directory, workspace) = position_workspace(&[("p/A.java", source)]);
        let mut request = position_request(&workspace, "p/A.java", source, 1, 18);
        request.document.root_id = "other-root".to_owned();
        let result = analyze_definition_at_position(&workspace, &request, None)
            .expect("typed invalid request");

        assert_eq!(result.outcome, DefinitionAtPositionOutcome::InvalidRequest);
        assert!(
            result.diagnostics.iter().any(|diagnostic| {
                diagnostic.code == "java.definition-position-invalid-request"
            })
        );
    }

    #[test]
    fn cached_dependency_types_resolve_live_source_imports_without_reparsing_dependencies() {
        let dependency = dependency_type("net.minecraft.client.gui.components.MultiLineEditBox");
        let index = JavaSymbolIndex::build_from_parsed_mode(
            context(&[("main", &["main"])]),
            vec![parsed(
                "source/main/Editor.java",
                "main",
                r"
                    package example;
                    import net.minecraft.client.gui.components.MultiLineEditBox;
                    class Editor { MultiLineEditBox value; }
                ",
            )],
            BTreeSet::from([
                ("main".to_owned(), "main".to_owned()),
                ("main".to_owned(), "dependency:minecraft:main".to_owned()),
            ]),
            true,
            Some(&dependency),
            None,
            None,
        );

        let report = index.usages(&selector(&[
            "net.minecraft.client.gui.components.MultiLineEditBox",
        ]));
        assert_eq!(report.outcome, SymbolCommandOutcome::Success);
        assert!(report.usages.iter().any(|usage| {
            usage.kind == JavaUsageKind::Import && usage.span.path.ends_with("Editor.java")
        }));
        assert!(report.usages.iter().any(|usage| {
            usage.kind == JavaUsageKind::TypeReference && usage.span.path.ends_with("Editor.java")
        }));
    }

    #[test]
    fn resolution_only_dependencies_resolve_usages_without_becoming_report_definitions() {
        let qualified_name = "net.minecraft.client.gui.components.MultiLineEditBox";
        let resolution = vec![JavaDependencyResolutionDefinition {
            symbol: JavaSymbolIdentityOutput {
                kind: JavaSymbolKind::Class,
                owner: "net.minecraft.client.gui.components".to_owned(),
                name: "MultiLineEditBox".to_owned(),
                descriptor: None,
                qualified_name: qualified_name.to_owned(),
            },
            source_set: "dependency:minecraft:main".to_owned(),
        }];
        let index = JavaSymbolIndex::build_from_parsed_mode(
            context(&[("main", &["main"])]),
            vec![parsed(
                "source/main/Editor.java",
                "main",
                r"
                    package example;
                    import net.minecraft.client.gui.components.MultiLineEditBox;
                    class Editor { MultiLineEditBox value; }
                ",
            )],
            BTreeSet::from([
                ("main".to_owned(), "main".to_owned()),
                ("main".to_owned(), "dependency:minecraft:main".to_owned()),
            ]),
            true,
            None,
            Some(&resolution),
            None,
        );

        let body = index.dependency_body(&[]);
        assert!(
            body.definitions
                .iter()
                .all(|definition| definition.symbol.qualified_name != qualified_name)
        );
        assert!(body.usages.iter().any(|usage| {
            usage.target == resolution[0].symbol
                && usage.kind == JavaUsageKind::Import
                && usage.span.path.ends_with("Editor.java")
        }));
        assert!(body.usages.iter().any(|usage| {
            usage.target == resolution[0].symbol
                && usage.kind == JavaUsageKind::TypeReference
                && usage.span.path.ends_with("Editor.java")
        }));
    }

    #[test]
    fn live_declaration_takes_precedence_over_same_dependency_identity() {
        let dependency = dependency_type("example.Shared");
        let index = JavaSymbolIndex::build_from_parsed_mode(
            context(&[("main", &["main"])]),
            vec![parsed(
                "source/main/example/Shared.java",
                "main",
                "package example; public class Shared {}",
            )],
            BTreeSet::from([
                ("main".to_owned(), "main".to_owned()),
                ("main".to_owned(), "dependency:minecraft:main".to_owned()),
            ]),
            true,
            Some(&dependency),
            None,
            None,
        );

        let report = index.definition(&selector(&["example.Shared"]));
        assert_eq!(report.outcome, SymbolCommandOutcome::Success);
        assert_eq!(report.definitions.len(), 1);
        assert_eq!(
            report.definitions[0].identifier_span.path,
            "source/main/example/Shared.java"
        );
    }

    #[test]
    fn unqualified_selectors_resolve_unique_types_and_member_owners() {
        let index = index(vec![parsed(
            "source/example/DiskItem.java",
            "scenario",
            "package example; public class DiskItem { int color; void open() {} }",
        )]);

        let type_report = index.definition(&selector(&["DiskItem"]));
        assert_eq!(type_report.outcome, SymbolCommandOutcome::Success);
        assert_eq!(
            type_report.definitions[0].symbol.qualified_name,
            "example.DiskItem"
        );

        let field_report = index.definition(&selector(&["DiskItem", "color"]));
        assert_eq!(field_report.outcome, SymbolCommandOutcome::Success);
        assert_eq!(field_report.definitions[0].symbol.owner, "example.DiskItem");

        let method_report = index.definition(&selector(&["DiskItem", "open()V"]));
        assert_eq!(method_report.outcome, SymbolCommandOutcome::Success);
        assert_eq!(
            method_report.definitions[0].symbol.owner,
            "example.DiskItem"
        );
    }

    #[test]
    fn unqualified_selectors_report_ambiguity_but_exact_owners_take_precedence() {
        let index = index(vec![
            parsed(
                "source/example/DiskItem.java",
                "scenario",
                "package example; public class DiskItem {}",
            ),
            parsed(
                "source/other/DiskItem.java",
                "scenario",
                "package other; public class DiskItem {}",
            ),
            parsed(
                "source/prefix/example/DiskItem.java",
                "scenario",
                "package prefix.example; public class DiskItem {}",
            ),
        ]);

        let short_report = index.definition(&selector(&["DiskItem"]));
        assert_eq!(short_report.outcome, SymbolCommandOutcome::Ambiguous);
        assert_eq!(short_report.definitions.len(), 3);

        let exact_report = index.definition(&selector(&["example.DiskItem"]));
        assert_eq!(exact_report.outcome, SymbolCommandOutcome::Success);
        assert_eq!(exact_report.definitions.len(), 1);
        assert_eq!(
            exact_report.definitions[0].symbol.qualified_name,
            "example.DiskItem"
        );
    }

    #[test]
    fn symbol_list_orders_by_canonical_selector_and_preserves_overloads() {
        let index = index(vec![
            parsed(
                "source/example/Z.java",
                "scenario",
                "package example; class Z { void run(String value) {} void run() {} }",
            ),
            parsed(
                "source/example/A.java",
                "scenario",
                "package example; class A { int field; }",
            ),
        ]);
        let report = index.list(&JavaSymbolGlob::default());
        let selectors = report
            .definitions
            .iter()
            .map(|definition| definition.symbol.canonical_selector())
            .collect::<Vec<_>>();
        let mut expected = selectors.clone();
        expected.sort();
        assert_eq!(selectors, expected);
        assert!(selectors.contains(&"example.A field".to_owned()));
        assert!(selectors.contains(&"example.Z run()V".to_owned()));
        assert!(selectors.contains(&"example.Z run(Ljava/lang/String;)V".to_owned()));
    }

    #[test]
    fn cached_method_descriptors_restore_parameter_and_return_types() {
        assert_eq!(
            decode_method_descriptor("(ILjava/lang/String;[I)Ljava/lang/Object;"),
            Some((
                vec![
                    "int".to_owned(),
                    "java.lang.String".to_owned(),
                    "int[]".to_owned(),
                ],
                Some("java.lang.Object".to_owned()),
            ))
        );
        assert_eq!(decode_method_descriptor("()V"), Some((Vec::new(), None)));
    }

    #[test]
    fn java_symbol_index_resolves_imported_types_fields_overloads_and_method_references() {
        let index = index(vec![
            parsed(
                "source/p/A.java",
                "scenario",
                r"
                    package p;
                    public class A {
                        public int value;
                        public void run(int value) {}
                        public void run(String value) {}
                        public void ping() {}
                    }
                ",
            ),
            parsed(
                "source/q/B.java",
                "scenario",
                r#"
                    package q;
                    import p.A;
                    class B {
                        A field;
                        void use(A target) {
                            int copy = target.value;
                            target.run(1);
                            Runnable callback = target::ping;
                            String text = "target.run(1) and target.value";
                            // target.run(1);
                        }
                    }
                "#,
            ),
        ]);

        let type_usages = index.usages(&selector(&["p.A"]));
        assert_eq!(type_usages.outcome, SymbolCommandOutcome::Success);
        assert!(type_usages.usages.iter().any(|usage| {
            usage.kind == JavaUsageKind::Import && usage.span.path.ends_with("q/B.java")
        }));
        assert!(type_usages.usages.iter().any(|usage| {
            usage.kind == JavaUsageKind::TypeReference && usage.span.path.ends_with("q/B.java")
        }));

        let field_usages = index.usages(&selector(&["p.A", "value"]));
        assert!(field_usages.usages.iter().any(|usage| {
            usage.kind == JavaUsageKind::FieldReference && usage.span.path.ends_with("q/B.java")
        }));

        let int_run = index.usages(&selector(&["p.A", "run(I)V"]));
        assert!(
            int_run
                .usages
                .iter()
                .any(|usage| usage.kind == JavaUsageKind::Invocation)
        );
        let string_run = index.usages(&selector(&["p.A", "run(Ljava/lang/String;)V"]));
        assert!(
            !string_run
                .usages
                .iter()
                .any(|usage| usage.kind == JavaUsageKind::Invocation)
        );

        let ping = index.usages(&selector(&["p.A", "ping()V"]));
        assert!(
            ping.usages
                .iter()
                .any(|usage| usage.kind == JavaUsageKind::MethodReference)
        );
    }

    #[test]
    fn java_symbol_index_excludes_same_spelled_unrelated_symbols_comments_and_strings() {
        let index = index(vec![
            parsed(
                "source/p/A.java",
                "scenario",
                "package p; public class A { public int value; }",
            ),
            parsed(
                "source/r/A.java",
                "scenario",
                "package r; public class A { public int value; }",
            ),
            parsed(
                "source/q/Use.java",
                "scenario",
                r#"
                    package q;
                    import p.A;
                    class Use {
                        void use(A target) {
                            int copy = target.value;
                            String text = "r.A value";
                            // r.A.value
                        }
                    }
                "#,
            ),
        ]);

        let p_field = index.usages(&selector(&["p.A", "value"]));
        let r_field = index.usages(&selector(&["r.A", "value"]));
        assert_eq!(
            p_field
                .usages
                .iter()
                .filter(|usage| usage.kind == JavaUsageKind::FieldReference)
                .count(),
            1
        );
        assert_eq!(
            r_field
                .usages
                .iter()
                .filter(|usage| usage.kind == JavaUsageKind::FieldReference)
                .count(),
            0
        );
    }

    #[test]
    fn java_symbol_index_uses_dollar_qualified_nested_type_identity() {
        let index = index(vec![parsed(
            "source/p/Outer.java",
            "scenario",
            "package p; class Outer { static class Inner { void run() {} } Inner value; }",
        )]);
        let definition = index.definition(&selector(&["p.Outer$Inner"]));
        assert_eq!(definition.outcome, SymbolCommandOutcome::Success);
        assert_eq!(definition.definitions[0].symbol.owner, "p.Outer");
        assert_eq!(definition.definitions[0].symbol.name, "Inner");
        assert_eq!(
            index
                .definition(&selector(&["p.Outer$Inner", "run()V"]))
                .outcome,
            SymbolCommandOutcome::Success
        );
    }

    #[test]
    fn java_symbol_index_reports_inaccessible_source_set_edges() {
        let index = JavaSymbolIndex::build_from_parsed(
            context(&[("main", &["main"]), ("test", &["test", "main"])]),
            vec![
                parsed(
                    "source/main/Consumer.java",
                    "main",
                    "package main; import hidden.Hidden; class Consumer { Hidden value; }",
                ),
                parsed(
                    "source/test/Hidden.java",
                    "test",
                    "package hidden; public class Hidden {}",
                ),
            ],
            BTreeSet::from([
                ("main".to_owned(), "main".to_owned()),
                ("test".to_owned(), "test".to_owned()),
                ("test".to_owned(), "main".to_owned()),
            ]),
        );

        let usages = index.usages(&selector(&["hidden.Hidden"]));
        assert!(!usages.usages.iter().any(|usage| {
            usage.kind == JavaUsageKind::TypeReference
                && usage.span.path.ends_with("main/Consumer.java")
        }));
        assert!(usages.diagnostics.iter().any(|diagnostic| {
            diagnostic.code == "java.inaccessible-source-set-reference"
                && diagnostic
                    .span
                    .as_ref()
                    .is_some_and(|span| span.path.ends_with("main/Consumer.java"))
        }));
    }

    #[test]
    fn java_symbol_index_outputs_are_deterministic_for_reordered_inputs() {
        let first = index(vec![
            parsed("source/p/A.java", "scenario", "package p; class A {}"),
            parsed(
                "source/p/B.java",
                "scenario",
                "package p; class B { A value; }",
            ),
        ]);
        let second = index(vec![
            parsed(
                "source/p/B.java",
                "scenario",
                "package p; class B { A value; }",
            ),
            parsed("source/p/A.java", "scenario", "package p; class A {}"),
        ]);
        let selector = selector(&["p.A"]);
        assert_eq!(
            first.context.index_fingerprint,
            second.context.index_fingerprint
        );
        assert_eq!(first.definition(&selector), second.definition(&selector));
        assert_eq!(first.usages(&selector), second.usages(&selector));
    }

    #[test]
    fn java_symbol_index_reports_only_selector_relevant_workspace_diagnostics() {
        let index = index(vec![
            parsed("source/p/A.java", "scenario", "package p; class A {}"),
            parsed(
                "source/q/Unrelated.java",
                "scenario",
                "package q; class Unrelated { Missing value; }",
            ),
        ]);

        let a = index.definition(&selector(&["p.A"]));
        assert!(!a.diagnostics.iter().any(|diagnostic| {
            diagnostic.code == "java.unresolved-type" && diagnostic.message.contains("Missing")
        }));
        assert!(a.diagnostics.iter().any(|diagnostic| {
            diagnostic.code == "java.unrelated-workspace-diagnostics-suppressed"
        }));

        let missing = index.definition(&selector(&["q.Missing"]));
        assert!(missing.diagnostics.iter().any(|diagnostic| {
            diagnostic.code == "java.unresolved-type" && diagnostic.message.contains("Missing")
        }));
    }

    #[test]
    fn symbol_list_reports_only_diagnostics_from_matched_files() {
        let index = index(vec![
            parsed(
                "source/p/Match.java",
                "scenario",
                "package p; class Match { MissingOne value; }",
            ),
            parsed(
                "source/q/Unrelated.java",
                "scenario",
                "package q; class Unrelated { MissingTwo value; }",
            ),
        ]);

        let report = index.list(&JavaSymbolGlob::new(Some("p.Match*".to_owned())));
        assert!(
            report
                .diagnostics
                .iter()
                .any(|diagnostic| diagnostic.message.contains("MissingOne"))
        );
        assert!(
            report
                .diagnostics
                .iter()
                .all(|diagnostic| !diagnostic.message.contains("MissingTwo"))
        );
        assert!(
            report.diagnostics.iter().any(|diagnostic| {
                diagnostic.code == "java.unrelated-list-diagnostics-suppressed"
            })
        );
    }

    #[test]
    fn java_symbol_index_preserves_all_located_parse_gaps_for_usage_queries() {
        let index = index(vec![
            parsed("source/p/A.java", "scenario", "package p; class A {}"),
            parsed(
                "source/q/Broken.java",
                "scenario",
                "package q; class Broken { void run( {",
            ),
        ]);

        let report = index.usages(&selector(&["p.A"]));
        assert!(report.diagnostics.iter().any(|diagnostic| {
            diagnostic.code == "java.parse-gap"
                && diagnostic
                    .span
                    .as_ref()
                    .is_some_and(|span| span.path.ends_with("q/Broken.java"))
        }));
    }

    #[test]
    fn java_symbol_index_reports_conflicting_direct_imports_as_ambiguous() {
        let index = index(vec![
            parsed(
                "source/p/A.java",
                "scenario",
                "package p; public class A {}",
            ),
            parsed(
                "source/q/A.java",
                "scenario",
                "package q; public class A {}",
            ),
            parsed(
                "source/r/Consumer.java",
                "scenario",
                "package r; import p.A; import q.A; class Consumer { A value; }",
            ),
        ]);

        assert!(index.diagnostics.iter().any(|diagnostic| {
            diagnostic.code == "java.ambiguous-type"
                && diagnostic.message.contains("field type `A`")
        }));
        for owner in ["p.A", "q.A"] {
            let report = index.usages(&selector(&[owner]));
            assert!(report.usages.iter().any(|usage| {
                usage.kind == JavaUsageKind::TypeReference
                    && usage.span.path.ends_with("r/Consumer.java")
                    && usage.confidence == ResolutionConfidence::PartiallyResolved
            }));
        }
    }

    #[test]
    fn java_symbol_index_applies_current_package_before_on_demand_imports() {
        let index = index(vec![
            parsed(
                "source/p/String.java",
                "scenario",
                "package p; public class String {}",
            ),
            parsed(
                "source/p/Use.java",
                "scenario",
                "package p; class Use { String value; }",
            ),
        ]);

        let report = index.definition(&selector(&["p.Use", "value"]));
        assert_eq!(report.outcome, SymbolCommandOutcome::Success);
        assert_eq!(
            report.definitions[0].confidence,
            ResolutionConfidence::Resolved
        );
        assert!(index.diagnostics.iter().all(|diagnostic| {
            diagnostic.code != "java.ambiguous-type"
                || !diagnostic.message.contains("field type `String`")
        }));
    }

    #[test]
    fn java_symbol_index_treats_java_lang_and_wildcards_as_one_on_demand_tier() {
        let index = index(vec![
            parsed(
                "source/p/String.java",
                "scenario",
                "package p; public class String {}",
            ),
            parsed(
                "source/q/Use.java",
                "scenario",
                "package q; import p.*; class Use { String value; }",
            ),
        ]);

        assert!(index.diagnostics.iter().any(|diagnostic| {
            diagnostic.code == "java.ambiguous-type"
                && diagnostic.message.contains("field type `String`")
        }));
    }

    #[test]
    fn java_symbol_index_distinguishes_scalar_and_array_invocation_overloads() {
        let index = index(vec![parsed(
            "source/p/A.java",
            "scenario",
            r"
                package p;
                class A {
                    void run(String value) {}
                    void run(String[] values) {}
                    void use(String[] values) {
                        run(values);
                        run(new String[1]);
                    }
                }
            ",
        )]);

        let scalar = index.usages(&selector(&["p.A", "run(Ljava/lang/String;)V"]));
        let array = index.usages(&selector(&["p.A", "run([Ljava/lang/String;)V"]));
        assert_eq!(scalar.outcome, SymbolCommandOutcome::Success);
        assert_eq!(array.outcome, SymbolCommandOutcome::Success);
        assert_eq!(
            scalar
                .usages
                .iter()
                .filter(|usage| usage.kind == JavaUsageKind::Invocation)
                .count(),
            0
        );
        assert_eq!(
            array
                .usages
                .iter()
                .filter(|usage| usage.kind == JavaUsageKind::Invocation)
                .count(),
            2
        );
    }

    #[test]
    fn java_symbol_index_does_not_guess_external_types_in_any_classpath_mode() {
        let source = || {
            parsed(
                "source/example/Consumer.java",
                "scenario",
                "package example; import java.fake.Missing; class Consumer { Missing value; }",
            )
        };
        let visibility = || BTreeSet::from([("scenario".to_owned(), "scenario".to_owned())]);
        for mode in [JavaClasspathMode::Isolated, JavaClasspathMode::Branch] {
            let mut mode_context = context(&[("scenario", &["scenario"])]);
            mode_context.classpath_mode = mode;
            let index =
                JavaSymbolIndex::build_from_parsed(mode_context, vec![source()], visibility());
            let report = index.definition(&selector(&["java.fake.Missing"]));
            assert_eq!(report.outcome, SymbolCommandOutcome::NoMatch);
            assert!(report.diagnostics.iter().any(|diagnostic| {
                diagnostic.code == "java.unresolved-type"
                    && diagnostic.message.contains("java.fake.Missing")
            }));
        }
    }

    #[test]
    fn sha256_navigation_witness_is_only_emitted_for_the_same_blake3_source() {
        let source = "package example; class Target {}\n";
        let blake3 = blake3_content_hash(source);

        assert_eq!(
            sha256_witness_for_blake3(&blake3, source),
            Some(sha256_content_hash(source))
        );
        assert_eq!(
            sha256_witness_for_blake3(&blake3, "package example; class Changed {}\n"),
            None,
            "a witness computed from different bytes would make a stale range look authoritative"
        );
    }
}
