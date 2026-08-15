use super::DefinitionAtPositionRequest;
use super::DefinitionAtPositionResult;
use super::DependencyJavaSymbolIndexBody;
use super::DependencySymbolIndexQueryOutput;
use super::JavaDefinitionLinker;
use super::JavaDependencyResolutionDefinition;
use super::JavaFileFactDetail;
use super::JavaFileFacts;
use super::JavaFileFactsInput;
use super::JavaLiveDefinitionSurface;
use super::JavaSourceFile;
use super::JavaSourceWorkspace;
use super::JavaSymbolIndex;
use super::blake3_content_hash;
use super::content_hash_with_expected_algorithm;
use super::extract_java_file_facts_from_text_with_detail;
use super::normalize_definition_at_position_context;
use super::validate_definition_request_workspace;
use crate::cancellation::CancellationToken;
use facet::Facet;
use std::collections::BTreeMap;
use std::fmt;
use std::sync::Mutex;
use std::time::Instant;

pub const DEFAULT_DEFINITION_ENGINE_MAX_FACT_ENTRIES: usize = 4_096;
pub const DEFAULT_DEFINITION_ENGINE_MAX_FACT_BYTES: usize = 256 * 1024 * 1024;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct DefinitionAtPositionEngineLimits {
    pub max_fact_entries: usize,
    pub max_fact_bytes: usize,
}

impl Default for DefinitionAtPositionEngineLimits {
    fn default() -> Self {
        Self {
            max_fact_entries: DEFAULT_DEFINITION_ENGINE_MAX_FACT_ENTRIES,
            max_fact_bytes: DEFAULT_DEFINITION_ENGINE_MAX_FACT_BYTES,
        }
    }
}

#[derive(Debug, Eq, PartialEq)]
pub enum DefinitionAtPositionEngineConfigurationError {
    ZeroMaximumFactEntries,
    ZeroMaximumFactBytes,
}

impl fmt::Display for DefinitionAtPositionEngineConfigurationError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::ZeroMaximumFactEntries => {
                f.write_str("definition engine maximum fact entries must be positive")
            }
            Self::ZeroMaximumFactBytes => {
                f.write_str("definition engine maximum fact bytes must be positive")
            }
        }
    }
}

impl std::error::Error for DefinitionAtPositionEngineConfigurationError {}

/// Content-free retained-state evidence suitable for logs and provider diagnostics.
#[derive(Facet, Clone, Debug, Eq, PartialEq)]
pub struct DefinitionAtPositionEngineCacheSnapshot {
    pub workspace_generation: Option<u64>,
    pub entries: u64,
    pub retained_bytes: u64,
    pub hits: u64,
    pub misses: u64,
    pub evictions: u64,
}

/// Per-request stage timing and cache deltas. Paths and source text are deliberately absent.
#[derive(Facet, Clone, Debug, Eq, PartialEq)]
pub struct DefinitionAtPositionEngineTelemetry {
    pub source_snapshot_micros: u64,
    pub fact_parse_micros: u64,
    pub link_micros: u64,
    pub lookup_micros: u64,
    pub total_micros: u64,
    pub fact_cache_hits: u64,
    pub fact_cache_misses: u64,
    pub reparsed_files: u64,
    pub source_files: u64,
    pub cache: DefinitionAtPositionEngineCacheSnapshot,
}

pub struct DefinitionAtPositionEngineOutput {
    pub result: DefinitionAtPositionResult,
    pub telemetry: DefinitionAtPositionEngineTelemetry,
}

struct DefinitionFactSnapshot {
    source_files: usize,
    facts: Vec<JavaFileFacts>,
    before_cache: DefinitionAtPositionEngineCacheSnapshot,
    source_snapshot_micros: u64,
    fact_parse_micros: u64,
    reparsed_files: u64,
}

#[derive(Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
struct FactCacheKey {
    workspace_generation: u64,
    sequence: u64,
    root_id: String,
    root_relative_path: String,
    report_path: String,
    source_set: String,
    content_hash: String,
}

#[derive(Clone)]
struct CachedFact {
    facts: JavaFileFacts,
    retained_bytes: usize,
    last_access: u64,
}

#[derive(Default)]
struct FactCache {
    workspace_generation: Option<u64>,
    entries: BTreeMap<FactCacheKey, CachedFact>,
    retained_bytes: usize,
    access_clock: u64,
    hits: u64,
    misses: u64,
    evictions: u64,
}

impl FactCache {
    fn prepare_generation(&mut self, generation: u64) -> eyre::Result<()> {
        if self
            .workspace_generation
            .is_some_and(|current| generation < current)
        {
            eyre::bail!(
                "definition request workspace generation {generation} is older than engine generation {}",
                self.workspace_generation.expect("checked generation")
            );
        }
        if self.workspace_generation != Some(generation) {
            self.entries.clear();
            self.retained_bytes = 0;
            self.workspace_generation = Some(generation);
        }
        Ok(())
    }

    fn replace_generation(&mut self, generation: u64) -> eyre::Result<bool> {
        if self
            .workspace_generation
            .is_some_and(|current| generation < current)
        {
            eyre::bail!(
                "replacement workspace generation {generation} is older than engine generation {}",
                self.workspace_generation.expect("checked generation")
            );
        }
        if self.workspace_generation == Some(generation) {
            return Ok(false);
        }
        self.entries.clear();
        self.retained_bytes = 0;
        self.workspace_generation = Some(generation);
        Ok(true)
    }

    fn get(&mut self, key: &FactCacheKey) -> Option<JavaFileFacts> {
        self.access_clock = self.access_clock.saturating_add(1);
        let Some(entry) = self.entries.get_mut(key) else {
            self.misses = self.misses.saturating_add(1);
            return None;
        };
        entry.last_access = self.access_clock;
        self.hits = self.hits.saturating_add(1);
        Some(entry.facts.clone())
    }

    fn insert(
        &mut self,
        key: FactCacheKey,
        facts: JavaFileFacts,
        retained_bytes: usize,
        limits: DefinitionAtPositionEngineLimits,
    ) {
        if self.workspace_generation != Some(key.workspace_generation)
            || retained_bytes > limits.max_fact_bytes
        {
            return;
        }
        self.access_clock = self.access_clock.saturating_add(1);
        if let Some(existing) = self.entries.remove(&key) {
            self.retained_bytes = self.retained_bytes.saturating_sub(existing.retained_bytes);
        }
        self.retained_bytes = self.retained_bytes.saturating_add(retained_bytes);
        self.entries.insert(
            key,
            CachedFact {
                facts,
                retained_bytes,
                last_access: self.access_clock,
            },
        );
        while self.entries.len() > limits.max_fact_entries
            || self.retained_bytes > limits.max_fact_bytes
        {
            let Some(oldest) = self
                .entries
                .iter()
                .min_by_key(|(key, entry)| (entry.last_access, *key))
                .map(|(key, _)| key.clone())
            else {
                break;
            };
            if let Some(removed) = self.entries.remove(&oldest) {
                self.retained_bytes = self.retained_bytes.saturating_sub(removed.retained_bytes);
                self.evictions = self.evictions.saturating_add(1);
            }
        }
    }

    fn snapshot(&self) -> DefinitionAtPositionEngineCacheSnapshot {
        DefinitionAtPositionEngineCacheSnapshot {
            workspace_generation: self.workspace_generation,
            entries: self.entries.len().try_into().unwrap_or(u64::MAX),
            retained_bytes: self.retained_bytes.try_into().unwrap_or(u64::MAX),
            hits: self.hits,
            misses: self.misses,
            evictions: self.evictions,
        }
    }
}

/// Thread-safe warm definition engine retaining immutable per-content Java facts.
///
/// Every request still supplies and validates its complete current document.
/// Unchanged files reuse owned facts, changed content receives a distinct key,
/// and a workspace-generation replacement drops every retained key atomically.
pub struct DefinitionAtPositionEngine {
    workspace: JavaSourceWorkspace,
    dependencies: Option<DependencyJavaSymbolIndexBody>,
    dependency_index: Option<DependencySymbolIndexQueryOutput>,
    external_resolution: Vec<JavaDependencyResolutionDefinition>,
    limits: DefinitionAtPositionEngineLimits,
    cache: Mutex<FactCache>,
}

impl DefinitionAtPositionEngine {
    /// Construct one reusable engine over a resolved workspace and immutable dependency body.
    ///
    /// # Errors
    ///
    /// Returns an error when a cache bound is zero.
    pub fn new(
        workspace: JavaSourceWorkspace,
        dependencies: Option<DependencyJavaSymbolIndexBody>,
        dependency_index: Option<DependencySymbolIndexQueryOutput>,
        limits: DefinitionAtPositionEngineLimits,
    ) -> Result<Self, DefinitionAtPositionEngineConfigurationError> {
        if limits.max_fact_entries == 0 {
            return Err(DefinitionAtPositionEngineConfigurationError::ZeroMaximumFactEntries);
        }
        if limits.max_fact_bytes == 0 {
            return Err(DefinitionAtPositionEngineConfigurationError::ZeroMaximumFactBytes);
        }
        let mut external_resolution = dependencies
            .iter()
            .flat_map(|body| &body.definitions)
            .map(|definition| JavaDependencyResolutionDefinition {
                symbol: definition.symbol.clone(),
                source_set: definition.identifier_span.source_set.clone(),
            })
            .collect::<Vec<_>>();
        external_resolution.sort();
        external_resolution.dedup();
        Ok(Self {
            workspace,
            dependencies,
            dependency_index,
            external_resolution,
            limits,
            cache: Mutex::new(FactCache::default()),
        })
    }

    /// Atomically replace the accepted workspace generation and clear retained facts.
    ///
    /// # Errors
    ///
    /// Returns an error when `generation` moves backwards.
    pub fn replace_workspace_generation(&self, generation: u64) -> eyre::Result<bool> {
        self.cache
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner)
            .replace_generation(generation)
    }

    #[must_use]
    pub fn cache_snapshot(&self) -> DefinitionAtPositionEngineCacheSnapshot {
        self.cache
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner)
            .snapshot()
    }

    /// Analyze one immutable request while returning privacy-safe stage evidence.
    ///
    /// # Errors
    ///
    /// Returns an error for source I/O, parsing/linking failure, cancellation,
    /// or a stale engine generation. Request-contract failures remain typed results.
    pub fn analyze_with_telemetry(
        &self,
        request: &DefinitionAtPositionRequest,
        cancellation_token: &CancellationToken,
    ) -> eyre::Result<DefinitionAtPositionEngineOutput> {
        let started = Instant::now();
        if let Some(result) = self.request_rejection(request)? {
            return Ok(DefinitionAtPositionEngineOutput {
                result,
                telemetry: self.empty_telemetry(started),
            });
        }
        cancellation_token.bail_if_cancelled()?;
        let DefinitionFactSnapshot {
            source_files,
            facts,
            before_cache,
            source_snapshot_micros,
            fact_parse_micros,
            reparsed_files,
        } = self.collect_facts(request, cancellation_token)?;
        let (live, link_micros) = self.link_facts(facts, source_files, cancellation_token)?;
        let (result, lookup_micros) = self.lookup(request, &live, cancellation_token)?;
        let after_cache = self.cache_snapshot();
        Ok(DefinitionAtPositionEngineOutput {
            result,
            telemetry: DefinitionAtPositionEngineTelemetry {
                source_snapshot_micros,
                fact_parse_micros,
                link_micros,
                lookup_micros,
                total_micros: duration_micros(started.elapsed()),
                fact_cache_hits: after_cache.hits.saturating_sub(before_cache.hits),
                fact_cache_misses: after_cache.misses.saturating_sub(before_cache.misses),
                reparsed_files,
                source_files: source_files.try_into().unwrap_or(u64::MAX),
                cache: after_cache,
            },
        })
    }

    fn request_rejection(
        &self,
        request: &DefinitionAtPositionRequest,
    ) -> eyre::Result<Option<DefinitionAtPositionResult>> {
        if let Err(error) = validate_definition_request_workspace(&self.workspace, request) {
            return Ok(Some(DefinitionAtPositionResult::invalid_request(
                request,
                self.workspace.context.clone(),
                format!("{error:#}"),
            )));
        }
        let dependency_identity = self
            .dependency_index
            .as_ref()
            .map(|index| index.expected_identity.as_str());
        if request.workspace.dependency_index_identity.as_deref() != dependency_identity {
            return Ok(Some(DefinitionAtPositionResult::invalid_request(
                request,
                self.workspace.context.clone(),
                "definition request dependency-index identity does not match the engine",
            )));
        }
        let Some(expected_disk_hash) = request.document.disk_content_hash.as_deref() else {
            return Ok(None);
        };
        let target = self.target_file(request)?;
        let Ok(disk_source) = std::fs::read_to_string(&target.absolute_path) else {
            return Ok(Some(DefinitionAtPositionResult::stale_document(
                request,
                self.workspace.context.clone(),
                "Could not verify the captured document against its current disk contents",
            )));
        };
        let current_disk_hash =
            content_hash_with_expected_algorithm(&disk_source, expected_disk_hash)?;
        Ok((current_disk_hash != expected_disk_hash).then(|| {
            DefinitionAtPositionResult::stale_document(
                request,
                self.workspace.context.clone(),
                "The document changed on disk after the editor snapshot was captured",
            )
        }))
    }

    fn collect_facts(
        &self,
        request: &DefinitionAtPositionRequest,
        cancellation_token: &CancellationToken,
    ) -> eyre::Result<DefinitionFactSnapshot> {
        let before_cache = {
            let mut cache = self
                .cache
                .lock()
                .unwrap_or_else(std::sync::PoisonError::into_inner);
            cache.prepare_generation(request.workspace.workspace_generation)?;
            cache.snapshot()
        };
        let snapshot_started = Instant::now();
        let mut files = self.workspace.files.iter().collect::<Vec<_>>();
        files.sort_by(|left, right| {
            (&left.report_path, &left.source_set, &left.absolute_path).cmp(&(
                &right.report_path,
                &right.source_set,
                &right.absolute_path,
            ))
        });
        let mut facts = Vec::with_capacity(files.len());
        let mut fact_parse_micros = 0_u64;
        let mut reparsed_files = 0_u64;
        for (sequence, file) in files.iter().enumerate() {
            cancellation_token.bail_if_cancelled()?;
            let source = Self::source_for_request(file, request)?;
            let key = Self::fact_cache_key(request, file, sequence, &source);
            if let Some(cached) = self
                .cache
                .lock()
                .unwrap_or_else(std::sync::PoisonError::into_inner)
                .get(&key)
            {
                facts.push(cached);
                continue;
            }
            let parse_started = Instant::now();
            let source_len = source.len();
            let parsed = self.parse_fact(file, source, key.sequence)?;
            fact_parse_micros =
                fact_parse_micros.saturating_add(duration_micros(parse_started.elapsed()));
            reparsed_files = reparsed_files.saturating_add(1);
            let retained_bytes = source_len
                .saturating_add(facet_json::to_string(&parsed).map_or(0, |encoded| encoded.len()));
            self.cache
                .lock()
                .unwrap_or_else(std::sync::PoisonError::into_inner)
                .insert(key, parsed.clone(), retained_bytes, self.limits);
            facts.push(parsed);
        }
        Ok(DefinitionFactSnapshot {
            source_files: files.len(),
            facts,
            before_cache,
            source_snapshot_micros: duration_micros(snapshot_started.elapsed())
                .saturating_sub(fact_parse_micros),
            fact_parse_micros,
            reparsed_files,
        })
    }

    fn fact_cache_key(
        request: &DefinitionAtPositionRequest,
        file: &JavaSourceFile,
        sequence: usize,
        source: &str,
    ) -> FactCacheKey {
        FactCacheKey {
            workspace_generation: request.workspace.workspace_generation,
            sequence: sequence.try_into().unwrap_or(u64::MAX),
            root_id: file.root_id.clone(),
            root_relative_path: file.root_relative_path.clone(),
            report_path: file.report_path.clone(),
            source_set: file.source_set.clone(),
            content_hash: blake3_content_hash(source),
        }
    }

    fn parse_fact(
        &self,
        file: &JavaSourceFile,
        source: String,
        sequence: u64,
    ) -> eyre::Result<JavaFileFacts> {
        let visible_source_sets = self.visible_source_sets(&file.source_set);
        extract_java_file_facts_from_text_with_detail(
            JavaFileFactsInput {
                sequence,
                report_path: &file.report_path,
                source_set: &file.source_set,
                visible_source_sets: &visible_source_sets,
            },
            source,
            None,
            JavaFileFactDetail::Declarations,
        )
    }

    fn link_facts(
        &self,
        facts: Vec<JavaFileFacts>,
        source_files: usize,
        cancellation_token: &CancellationToken,
    ) -> eyre::Result<(JavaLiveDefinitionSurface, u64)> {
        cancellation_token.bail_if_cancelled()?;
        let started = Instant::now();
        let mut linker =
            JavaDefinitionLinker::new(self.workspace.context.clone(), &self.external_resolution);
        for file_facts in facts {
            cancellation_token.bail_if_cancelled()?;
            linker.ingest(file_facts)?;
        }
        let live = linker.seal_surface(source_files)?;
        Ok((live, duration_micros(started.elapsed())))
    }

    fn lookup(
        &self,
        request: &DefinitionAtPositionRequest,
        live: &JavaLiveDefinitionSurface,
        cancellation_token: &CancellationToken,
    ) -> eyre::Result<(DefinitionAtPositionResult, u64)> {
        cancellation_token.bail_if_cancelled()?;
        let started = Instant::now();
        let mut target = self.target_file(request)?.clone();
        target.source_override = Some(request.document.text.clone());
        let target_workspace = JavaSourceWorkspace {
            context: self.workspace.context.clone(),
            root_authorities: self.workspace.root_authorities.clone(),
            files: vec![target],
            diagnostics: self.workspace.diagnostics.clone(),
            classpath_entries: self.workspace.classpath_entries.clone(),
        };
        let index = JavaSymbolIndex::build_with_live_definitions(
            &target_workspace,
            live,
            self.dependencies.as_ref(),
            true,
        )?;
        let mut result = index.definition_at_position(request, &self.workspace);
        normalize_definition_at_position_context(
            self.workspace.context.clone(),
            self.dependencies.as_ref(),
            &mut result,
        );
        if let Some(dependency_index) = &self.dependency_index {
            result = result.with_dependency_index(dependency_index.clone());
        }
        cancellation_token.bail_if_cancelled()?;
        Ok((result, duration_micros(started.elapsed())))
    }

    /// Analyze one request without exposing transport-independent telemetry.
    ///
    /// # Errors
    ///
    /// Returns the same errors as [`Self::analyze_with_telemetry`].
    pub fn analyze(
        &self,
        request: &DefinitionAtPositionRequest,
        cancellation_token: &CancellationToken,
    ) -> eyre::Result<DefinitionAtPositionResult> {
        self.analyze_with_telemetry(request, cancellation_token)
            .map(|output| output.result)
    }

    fn visible_source_sets(&self, source_set: &str) -> Vec<String> {
        let mut visible = self
            .workspace
            .context
            .source_sets
            .iter()
            .find(|candidate| candidate.id == source_set)
            .map_or_else(Vec::new, |candidate| candidate.visible_source_sets.clone());
        visible.push(source_set.to_owned());
        visible.sort();
        visible.dedup();
        visible
    }

    fn source_for_request(
        file: &JavaSourceFile,
        request: &DefinitionAtPositionRequest,
    ) -> eyre::Result<String> {
        if file.root_id == request.document.root_id
            && file.root_relative_path == request.document.root_relative_path
        {
            return Ok(request.document.text.clone());
        }
        if let Some(source) = &file.source_override {
            return Ok(source.clone());
        }
        std::fs::read_to_string(&file.absolute_path).map_err(|error| {
            eyre::eyre!(
                "failed to read Java source `{}` for definition analysis: {error}",
                file.report_path
            )
        })
    }

    fn target_file<'a>(
        &'a self,
        request: &DefinitionAtPositionRequest,
    ) -> eyre::Result<&'a JavaSourceFile> {
        self.workspace
            .files
            .iter()
            .find(|file| {
                file.root_id == request.document.root_id
                    && file.root_relative_path == request.document.root_relative_path
            })
            .ok_or_else(|| {
                eyre::eyre!(
                    "validated definition document `{}`:`{}` is absent from the engine workspace",
                    request.document.root_id,
                    request.document.root_relative_path
                )
            })
    }

    fn empty_telemetry(&self, started: Instant) -> DefinitionAtPositionEngineTelemetry {
        DefinitionAtPositionEngineTelemetry {
            source_snapshot_micros: 0,
            fact_parse_micros: 0,
            link_micros: 0,
            lookup_micros: 0,
            total_micros: duration_micros(started.elapsed()),
            fact_cache_hits: 0,
            fact_cache_misses: 0,
            reparsed_files: 0,
            source_files: self.workspace.files.len().try_into().unwrap_or(u64::MAX),
            cache: self.cache_snapshot(),
        }
    }
}

fn duration_micros(duration: std::time::Duration) -> u64 {
    duration.as_micros().try_into().unwrap_or(u64::MAX)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::java_analysis::DefinitionAtPositionOutcome;
    use crate::java_analysis::DefinitionDocumentInput;
    use crate::java_analysis::DefinitionRecoveryActionKind;
    use crate::java_analysis::DefinitionTextPositionInput;
    use crate::java_analysis::DefinitionWorkspaceIdentityInput;
    use crate::java_analysis::DependencySymbolIndexProbeStatus;
    use crate::java_analysis::JavaAnalysisContextOutput;
    use crate::java_analysis::JavaClasspathMode;
    use crate::java_analysis::JavaSourceRootKind;
    use crate::java_analysis::JavaSourceRootOutput;
    use crate::java_analysis::JavaSourceSetOutput;
    use crate::java_analysis::JavaSourceSpanOutput;
    use crate::java_analysis::JavaSymbolDefinitionOutput;
    use crate::java_analysis::JavaSymbolIdentityOutput;
    use crate::java_analysis::JavaSymbolKind;
    use crate::java_analysis::ResolutionConfidence;
    use crate::java_analysis::SymbolQueryCompleteness;
    use crate::java_analysis::definition_workspace_fingerprint;

    fn workspace() -> (tempfile::TempDir, JavaSourceWorkspace) {
        workspace_from_sources(&[
            ("p/A.java", "package p; public class A {}\n"),
            (
                "q/Use.java",
                "package q; import p.A; class Use { A value; }\n",
            ),
        ])
    }

    fn workspace_from_sources(
        sources: &[(&str, &str)],
    ) -> (tempfile::TempDir, JavaSourceWorkspace) {
        let directory = tempfile::tempdir().expect("definition engine fixture");
        let root = directory.path().join("source");
        for (relative, source) in sources {
            let path = root.join(relative);
            std::fs::create_dir_all(path.parent().expect("Java source parent"))
                .expect("source directory");
            std::fs::write(path, source).expect("Java source");
        }
        let context = JavaAnalysisContextOutput {
            branch: "1.19.2".to_owned(),
            minecraft_version: "1.19.2".to_owned(),
            java_release: "17".to_owned(),
            jdk: "fixture".to_owned(),
            source_roots: vec![JavaSourceRootOutput {
                id: "fixture".to_owned(),
                source_set: "main".to_owned(),
                path: "source".to_owned(),
                kind: JavaSourceRootKind::Custom,
                exists: true,
            }],
            source_sets: vec![JavaSourceSetOutput {
                id: "main".to_owned(),
                visible_source_sets: vec!["main".to_owned()],
            }],
            source_exclusions: Vec::new(),
            classpath_mode: JavaClasspathMode::Isolated,
            classpath_fingerprint: "isolated".to_owned(),
            parser_fingerprint: String::new(),
            index_fingerprint: String::new(),
        };
        let files = sources
            .iter()
            .map(|(relative, _)| JavaSourceFile {
                absolute_path: root.join(relative),
                root_id: "fixture".to_owned(),
                root_relative_path: (*relative).to_owned(),
                report_path: format!("source/{relative}"),
                source_set: "main".to_owned(),
                source_override: None,
            })
            .collect();
        (
            directory,
            JavaSourceWorkspace {
                context,
                root_authorities: Vec::new(),
                files,
                diagnostics: Vec::new(),
                classpath_entries: Vec::new(),
            },
        )
    }

    fn request(
        workspace: &JavaSourceWorkspace,
        text: &str,
        generation: u64,
    ) -> DefinitionAtPositionRequest {
        request_at_position(workspace, "q/Use.java", text, generation, 1, 36)
    }

    fn request_at(
        workspace: &JavaSourceWorkspace,
        relative: &str,
        text: &str,
        generation: u64,
        needle: &str,
    ) -> DefinitionAtPositionRequest {
        let byte_offset = text.find(needle).expect("request needle");
        let prefix = &text[..byte_offset];
        let line = prefix.bytes().filter(|byte| *byte == b'\n').count() + 1;
        let line_prefix = prefix.rsplit_once('\n').map_or(prefix, |(_, tail)| tail);
        let column = line_prefix.chars().count() + 1;
        request_at_position(
            workspace,
            relative,
            text,
            generation,
            line.try_into().expect("fixture line"),
            column.try_into().expect("fixture column"),
        )
    }

    fn request_at_position(
        workspace: &JavaSourceWorkspace,
        relative: &str,
        text: &str,
        generation: u64,
        line: u64,
        column: u64,
    ) -> DefinitionAtPositionRequest {
        let workspace_fingerprint = definition_workspace_fingerprint(&workspace.context, None)
            .expect("fixture workspace fingerprint");
        let file = workspace
            .files
            .iter()
            .find(|file| file.root_relative_path == relative)
            .expect("request file");
        DefinitionAtPositionRequest::new(
            1,
            1,
            DefinitionWorkspaceIdentityInput {
                branch: workspace.context.branch.clone(),
                classpath_mode: workspace.context.classpath_mode,
                source_roots: workspace.context.source_roots.clone(),
                classpath_fingerprint: workspace.context.classpath_fingerprint.clone(),
                dependency_index_identity: None,
                workspace_fingerprint,
                workspace_generation: generation,
            },
            DefinitionDocumentInput {
                address: format!("workspace://fixture/{relative}"),
                root_id: file.root_id.clone(),
                root_relative_path: file.root_relative_path.clone(),
                report_path: file.report_path.clone(),
                source_set: file.source_set.clone(),
                text: text.to_owned(),
                content_hash: blake3_content_hash(text),
                disk_content_hash: None,
            },
            DefinitionTextPositionInput::from_line_column(text, line, column)
                .expect("fixture cursor"),
        )
    }

    fn use_dependency_identity(
        workspace: &JavaSourceWorkspace,
        request: &mut DefinitionAtPositionRequest,
        identity: &str,
    ) {
        request.workspace.dependency_index_identity = Some(identity.to_owned());
        request.workspace.workspace_fingerprint =
            definition_workspace_fingerprint(&workspace.context, Some(identity))
                .expect("dependency workspace fingerprint");
    }

    fn dependency_index(
        status: DependencySymbolIndexProbeStatus,
        completeness: SymbolQueryCompleteness,
        identity: &str,
    ) -> DependencySymbolIndexQueryOutput {
        DependencySymbolIndexQueryOutput {
            status,
            completeness,
            expected_identity: identity.to_owned(),
            portable_path: format!("symbol-index/v3/{identity}"),
            path: format!("cache/symbol-index/v3/{identity}"),
            reason: format!("fixture status: {status:?}"),
            refresh_command: "sfm-propagate-changes symbol index refresh --branch 1.19.2"
                .to_owned(),
            acquisition_commands: vec![
                "sfm-propagate-changes dependency source acquire minecraft --branch 1.19.2"
                    .to_owned(),
            ],
        }
    }

    fn dependency_type(qualified_name: &str) -> DependencyJavaSymbolIndexBody {
        let (owner, name) = qualified_name
            .rsplit_once('.')
            .expect("dependency type package");
        let path = format!(
            "dependency/minecraft/main/pipeline/{}.java",
            qualified_name.replace('.', "/")
        );
        let span = JavaSourceSpanOutput {
            path,
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
    fn warm_engine_reuses_unchanged_file_facts_and_matches_direct_result() {
        let (_directory, workspace) = workspace();
        let text =
            std::fs::read_to_string(&workspace.files[1].absolute_path).expect("usage source");
        let request = request(&workspace, &text, 3);
        let direct = super::super::analyze_definition_at_position(&workspace, &request, None)
            .expect("direct result");
        let engine = DefinitionAtPositionEngine::new(
            workspace,
            None,
            None,
            DefinitionAtPositionEngineLimits::default(),
        )
        .expect("engine");
        let cold = engine
            .analyze_with_telemetry(&request, &CancellationToken::new())
            .expect("cold result");
        let warm = engine
            .analyze_with_telemetry(&request, &CancellationToken::new())
            .expect("warm result");

        assert_eq!(cold.result, direct);
        assert_eq!(warm.result, direct);
        assert_eq!(cold.telemetry.reparsed_files, 2);
        assert_eq!(warm.telemetry.reparsed_files, 0);
        assert_eq!(warm.telemetry.fact_cache_hits, 2);
    }

    #[test]
    fn current_overlay_resolves_without_mutating_the_disk_snapshot() {
        let disk_source = "package q; class Use {}\n";
        let overlay_source = "package q; import p.A; class Use { A overlayValue; }\n";
        let (_directory, workspace) = workspace_from_sources(&[
            ("p/A.java", "package p; public class A {}\n"),
            ("q/Use.java", disk_source),
        ]);
        let target = workspace.files[1].absolute_path.clone();
        let disk_before = std::fs::read(&target).expect("disk snapshot before analysis");
        let mut request = request_at(
            &workspace,
            "q/Use.java",
            overlay_source,
            2,
            "A overlayValue",
        );
        request.document.disk_content_hash = Some(blake3_content_hash(disk_source));
        assert_ne!(
            request.document.content_hash,
            request
                .document
                .disk_content_hash
                .clone()
                .expect("disk hash")
        );
        let engine = DefinitionAtPositionEngine::new(
            workspace,
            None,
            None,
            DefinitionAtPositionEngineLimits::default(),
        )
        .expect("engine");

        let output = engine
            .analyze_with_telemetry(&request, &CancellationToken::new())
            .expect("overlay analysis");

        assert_eq!(output.result.outcome, DefinitionAtPositionOutcome::Success);
        assert_eq!(output.result.symbols[0].canonical_selector(), "p.A");
        assert_eq!(
            output.result.document.content_hash,
            blake3_content_hash(overlay_source)
        );
        assert_eq!(
            output.result.document.disk_content_hash.as_deref(),
            Some(blake3_content_hash(disk_source).as_str())
        );
        assert_eq!(
            std::fs::read(&target).expect("disk snapshot after analysis"),
            disk_before
        );
    }

    #[test]
    fn stale_disk_hash_is_a_typed_rejection_with_retry_recovery() {
        let (_directory, workspace) = workspace();
        let target = workspace.files[1].absolute_path.clone();
        let captured = std::fs::read_to_string(&target).expect("captured disk source");
        let mut request = request_at(&workspace, "q/Use.java", &captured, 3, "A value");
        request.document.disk_content_hash = Some(blake3_content_hash(&captured));
        std::fs::write(&target, "package q; class Use { int changed; }\n")
            .expect("change disk after capture");
        let engine = DefinitionAtPositionEngine::new(
            workspace,
            None,
            None,
            DefinitionAtPositionEngineLimits::default(),
        )
        .expect("engine");

        let output = engine
            .analyze_with_telemetry(&request, &CancellationToken::new())
            .expect("typed stale result");

        assert_eq!(
            output.result.outcome,
            DefinitionAtPositionOutcome::StaleDocument
        );
        assert_eq!(output.result.status(), 4);
        assert!(output.result.symbols.is_empty());
        assert!(output.result.definitions.is_empty());
        assert_eq!(output.result.recovery_actions.len(), 1);
        assert_eq!(
            output.result.recovery_actions[0].kind,
            DefinitionRecoveryActionKind::Retry
        );
        assert_eq!(output.telemetry.reparsed_files, 0);
        assert_eq!(output.telemetry.cache.entries, 0);
    }

    #[test]
    fn warm_engine_matches_direct_resolution_for_chained_fields_and_methods() {
        let use_source = concat!(
            "package q;\n",
            "import p.Root;\n",
            "class Use {\n",
            "    void use(Root root) {\n",
            "        int field = root.middle.leaf.value;\n",
            "        int method = root.middle().leaf().finish();\n",
            "    }\n",
            "}\n",
        );
        let (_directory, workspace) = workspace_from_sources(&[
            (
                "p/Leaf.java",
                "package p; public class Leaf { public int value; public int finish() { return 1; } }\n",
            ),
            (
                "p/Middle.java",
                "package p; public class Middle { public Leaf leaf; public Leaf leaf() { return leaf; } }\n",
            ),
            (
                "p/Root.java",
                "package p; public class Root { public Middle middle; public Middle middle() { return middle; } }\n",
            ),
            ("q/Use.java", use_source),
        ]);
        let field_request = request_at(&workspace, "q/Use.java", use_source, 7, "value;");
        let method_request = request_at(&workspace, "q/Use.java", use_source, 7, "finish();");
        let direct_field =
            super::super::analyze_definition_at_position(&workspace, &field_request, None)
                .expect("direct chained field");
        let direct_method =
            super::super::analyze_definition_at_position(&workspace, &method_request, None)
                .expect("direct chained method");
        let engine = DefinitionAtPositionEngine::new(
            workspace,
            None,
            None,
            DefinitionAtPositionEngineLimits::default(),
        )
        .expect("engine");
        let cold_field = engine
            .analyze_with_telemetry(&field_request, &CancellationToken::new())
            .expect("cold chained field");
        let warm_field = engine
            .analyze_with_telemetry(&field_request, &CancellationToken::new())
            .expect("warm chained field");
        let warm_method = engine
            .analyze_with_telemetry(&method_request, &CancellationToken::new())
            .expect("warm chained method");

        assert_eq!(cold_field.result, direct_field);
        assert_eq!(warm_field.result, direct_field);
        assert_eq!(warm_method.result, direct_method);
        assert_eq!(
            warm_field.result.symbols[0].canonical_selector(),
            "p.Leaf value"
        );
        assert_eq!(
            warm_method.result.symbols[0].canonical_selector(),
            "p.Leaf finish()I"
        );
        assert_eq!(warm_field.telemetry.reparsed_files, 0);
        assert_eq!(warm_field.telemetry.fact_cache_hits, 4);
        assert_eq!(warm_method.telemetry.reparsed_files, 0);
        assert_eq!(warm_method.telemetry.fact_cache_hits, 4);
    }

    #[test]
    fn ready_dependency_definition_is_addressable_and_complete() {
        let source = concat!(
            "package q;\n",
            "import net.minecraft.core.BlockPos;\n",
            "class Use { BlockPos value; }\n",
        );
        let (_directory, workspace) = workspace_from_sources(&[("q/Use.java", source)]);
        let identity = "dependency-ready";
        let mut request = request_at(&workspace, "q/Use.java", source, 8, "BlockPos value");
        use_dependency_identity(&workspace, &mut request, identity);
        let engine = DefinitionAtPositionEngine::new(
            workspace,
            Some(dependency_type("net.minecraft.core.BlockPos")),
            Some(dependency_index(
                DependencySymbolIndexProbeStatus::Ready,
                SymbolQueryCompleteness::Complete,
                identity,
            )),
            DefinitionAtPositionEngineLimits::default(),
        )
        .expect("engine");

        let result = engine
            .analyze(&request, &CancellationToken::new())
            .expect("dependency definition");

        assert_eq!(result.outcome, DefinitionAtPositionOutcome::Success);
        assert_eq!(result.status(), 0);
        assert_eq!(
            result.symbols[0].canonical_selector(),
            "net.minecraft.core.BlockPos"
        );
        assert_eq!(
            result.definitions[0].identifier_span.resolver_id,
            "dependency-index"
        );
        assert_eq!(
            result.definitions[0].identifier_span.root_id,
            "dependency-index"
        );
        assert_eq!(result.completeness, SymbolQueryCompleteness::Complete);
        assert!(result.recovery_actions.is_empty());
        assert_eq!(
            result.dependency_index.as_ref().map(|index| index.status),
            Some(DependencySymbolIndexProbeStatus::Ready)
        );
    }

    #[test]
    fn missing_and_stale_dependency_indexes_preserve_local_results_and_recovery() {
        for status in [
            DependencySymbolIndexProbeStatus::Missing,
            DependencySymbolIndexProbeStatus::Stale,
        ] {
            let (_directory, workspace) = workspace();
            let text =
                std::fs::read_to_string(&workspace.files[1].absolute_path).expect("usage source");
            let identity = format!("dependency-{status:?}");
            let mut request = request_at(&workspace, "q/Use.java", &text, 9, "A value");
            use_dependency_identity(&workspace, &mut request, &identity);
            let engine = DefinitionAtPositionEngine::new(
                workspace,
                None,
                Some(dependency_index(
                    status,
                    SymbolQueryCompleteness::Incomplete,
                    &identity,
                )),
                DefinitionAtPositionEngineLimits::default(),
            )
            .expect("engine");

            let result = engine
                .analyze(&request, &CancellationToken::new())
                .expect("incomplete dependency result");

            assert_eq!(result.outcome, DefinitionAtPositionOutcome::Success);
            assert_eq!(result.symbols[0].canonical_selector(), "p.A");
            assert_eq!(result.completeness, SymbolQueryCompleteness::Incomplete);
            assert_eq!(result.status(), 5);
            assert_eq!(
                result.dependency_index.as_ref().map(|index| index.status),
                Some(status)
            );
            assert!(result.recovery_actions.iter().any(|action| {
                action.kind == DefinitionRecoveryActionKind::RefreshDependencyIndex
            }));
            assert!(result.recovery_actions.iter().any(|action| {
                action.kind == DefinitionRecoveryActionKind::AcquireDependencySources
            }));
        }
    }

    #[test]
    fn changed_file_invalidates_only_its_fact_and_generation_clears_all() {
        let (_directory, workspace) = workspace();
        let text =
            std::fs::read_to_string(&workspace.files[1].absolute_path).expect("usage source");
        let first = request(&workspace, &text, 4);
        let engine = DefinitionAtPositionEngine::new(
            workspace.clone(),
            None,
            None,
            DefinitionAtPositionEngineLimits::default(),
        )
        .expect("engine");
        engine
            .analyze(&first, &CancellationToken::new())
            .expect("prime cache");
        std::fs::write(
            &workspace.files[0].absolute_path,
            "package p; public class A { int changed; }\n",
        )
        .expect("change one source");
        let changed = engine
            .analyze_with_telemetry(&first, &CancellationToken::new())
            .expect("changed analysis");
        assert_eq!(changed.telemetry.reparsed_files, 1);
        assert_eq!(changed.telemetry.fact_cache_hits, 1);

        assert!(
            engine
                .replace_workspace_generation(5)
                .expect("new generation")
        );
        let next = request(&workspace, &text, 5);
        let replaced = engine
            .analyze_with_telemetry(&next, &CancellationToken::new())
            .expect("replacement analysis");
        assert_eq!(replaced.telemetry.reparsed_files, 2);
    }

    #[test]
    fn cache_limits_evict_without_losing_correctness() {
        let (_directory, workspace) = workspace();
        let text =
            std::fs::read_to_string(&workspace.files[1].absolute_path).expect("usage source");
        let request = request(&workspace, &text, 1);
        let engine = DefinitionAtPositionEngine::new(
            workspace,
            None,
            None,
            DefinitionAtPositionEngineLimits {
                max_fact_entries: 1,
                max_fact_bytes: 1024 * 1024,
            },
        )
        .expect("engine");
        let result = engine
            .analyze_with_telemetry(&request, &CancellationToken::new())
            .expect("bounded analysis");
        assert_eq!(result.result.outcome, DefinitionAtPositionOutcome::Success);
        assert_eq!(result.telemetry.cache.entries, 1);
        assert!(result.telemetry.cache.evictions >= 1);
    }
}
