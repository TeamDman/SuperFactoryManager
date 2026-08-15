use super::DefinitionAtPositionRequest;
use super::DefinitionAtPositionResult;
use super::DependencyJavaSymbolIndexBody;
use super::DependencySymbolIndexQueryOutput;
use super::JavaDefinitionLinker;
use super::JavaDefinitionResolutionSurface;
use super::JavaDependencyResolutionDefinition;
use super::JavaFileFactDetail;
use super::JavaFileFacts;
use super::JavaFileFactsInput;
use super::JavaLiveDefinitionSurface;
use super::JavaSourceFile;
use super::JavaSourceWorkspace;
use super::blake3_content_hash;
use super::content_hash_with_expected_algorithm;
use super::extract_java_file_facts_from_text_with_detail;
use super::normalize_definition_at_position_context;
use super::sha256_content_hash;
use super::validate_definition_request_workspace;
use crate::cancellation::CancellationToken;
use facet::Facet;
use rayon::prelude::*;
use std::collections::BTreeMap;
use std::fmt;
use std::path::PathBuf;
use std::sync::Arc;
use std::sync::Mutex;
use std::time::Instant;

pub const DEFAULT_DEFINITION_ENGINE_MAX_FACT_ENTRIES: usize = 4_096;
pub const DEFAULT_DEFINITION_ENGINE_MAX_FACT_BYTES: usize = 256 * 1024 * 1024;
const DEFAULT_DEFINITION_ENGINE_MAX_RESOLUTION_SURFACES: usize = 2;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct DefinitionAtPositionEngineLimits {
    pub max_fact_entries: usize,
    pub max_fact_bytes: usize,
}

/// One acquired dependency-source root that may turn an indexed portable span
/// into an exact, hash-witnessed source location. These roots are resolved once
/// by the CLI's lockfile/source-provider machinery and are not analysis inputs.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct DefinitionDependencySourceRoot {
    pub root_id: String,
    pub report_prefix: String,
    pub source_set: String,
    pub canonical_absolute_path: PathBuf,
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
    pub resolution_cache_hits: u64,
    pub resolution_cache_misses: u64,
    pub resolution_cache_entries: u64,
    pub resolution_declarations: u64,
    pub reparsed_files: u64,
    pub source_files: u64,
    pub cache: DefinitionAtPositionEngineCacheSnapshot,
}

pub struct DefinitionAtPositionEngineOutput {
    pub result: DefinitionAtPositionResult,
    pub telemetry: DefinitionAtPositionEngineTelemetry,
}

struct DefinitionSourceSnapshot<'workspace> {
    files: Vec<&'workspace JavaSourceFile>,
    sources: Vec<String>,
    keys: Vec<FactCacheKey>,
    source_snapshot_micros: u64,
}

struct DefinitionFactSnapshot {
    facts: Vec<JavaFileFacts>,
    fact_parse_micros: u64,
    reparsed_files: u64,
}

#[derive(Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
struct ResolutionSurfaceCacheKey {
    workspace_generation: u64,
    fact_fingerprint: String,
}

struct CachedResolutionSurface {
    surface: Arc<JavaDefinitionResolutionSurface>,
    last_access: u64,
}

#[derive(Default)]
struct ResolutionSurfaceCache {
    entries: BTreeMap<ResolutionSurfaceCacheKey, CachedResolutionSurface>,
    access_clock: u64,
    hits: u64,
    misses: u64,
}

impl ResolutionSurfaceCache {
    fn get(
        &mut self,
        key: &ResolutionSurfaceCacheKey,
    ) -> Option<Arc<JavaDefinitionResolutionSurface>> {
        self.access_clock = self.access_clock.saturating_add(1);
        let Some(entry) = self.entries.get_mut(key) else {
            self.misses = self.misses.saturating_add(1);
            return None;
        };
        entry.last_access = self.access_clock;
        self.hits = self.hits.saturating_add(1);
        Some(Arc::clone(&entry.surface))
    }

    fn insert(
        &mut self,
        key: ResolutionSurfaceCacheKey,
        surface: Arc<JavaDefinitionResolutionSurface>,
    ) {
        self.access_clock = self.access_clock.saturating_add(1);
        self.entries.insert(
            key,
            CachedResolutionSurface {
                surface,
                last_access: self.access_clock,
            },
        );
        while self.entries.len() > DEFAULT_DEFINITION_ENGINE_MAX_RESOLUTION_SURFACES {
            let Some(oldest) = self
                .entries
                .iter()
                .min_by_key(|(key, entry)| (entry.last_access, *key))
                .map(|(key, _)| key.clone())
            else {
                break;
            };
            self.entries.remove(&oldest);
        }
    }

    fn clear(&mut self) {
        self.entries.clear();
    }
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
    fn prepare_generation(&mut self, generation: u64) -> eyre::Result<bool> {
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
            return Ok(true);
        }
        Ok(false)
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
    dependency_source_roots: Vec<DefinitionDependencySourceRoot>,
    limits: DefinitionAtPositionEngineLimits,
    cache: Mutex<FactCache>,
    resolution_cache: Mutex<ResolutionSurfaceCache>,
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
        Self::new_with_dependency_source_roots(
            workspace,
            dependencies,
            dependency_index,
            Vec::new(),
            limits,
        )
    }

    /// Construct a reusable engine that can also witness already-acquired
    /// dependency sources without adding them to the live parse surface.
    ///
    /// # Errors
    ///
    /// Returns an error when a cache bound is zero.
    pub fn new_with_dependency_source_roots(
        workspace: JavaSourceWorkspace,
        dependencies: Option<DependencyJavaSymbolIndexBody>,
        dependency_index: Option<DependencySymbolIndexQueryOutput>,
        mut dependency_source_roots: Vec<DefinitionDependencySourceRoot>,
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
        dependency_source_roots.sort_by(|left, right| {
            (
                &left.report_prefix,
                &left.source_set,
                &left.root_id,
                &left.canonical_absolute_path,
            )
                .cmp(&(
                    &right.report_prefix,
                    &right.source_set,
                    &right.root_id,
                    &right.canonical_absolute_path,
                ))
        });
        dependency_source_roots.dedup();
        Ok(Self {
            workspace,
            dependencies,
            dependency_index,
            external_resolution,
            dependency_source_roots,
            limits,
            cache: Mutex::new(FactCache::default()),
            resolution_cache: Mutex::new(ResolutionSurfaceCache::default()),
        })
    }

    /// Atomically replace the accepted workspace generation and clear retained facts.
    ///
    /// # Errors
    ///
    /// Returns an error when `generation` moves backwards.
    pub fn replace_workspace_generation(&self, generation: u64) -> eyre::Result<bool> {
        let replaced = self
            .cache
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner)
            .replace_generation(generation)?;
        if replaced {
            self.resolution_cache
                .lock()
                .unwrap_or_else(std::sync::PoisonError::into_inner)
                .clear();
        }
        Ok(replaced)
    }

    #[must_use]
    pub fn cache_snapshot(&self) -> DefinitionAtPositionEngineCacheSnapshot {
        self.cache
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner)
            .snapshot()
    }

    fn prepare_workspace_generation(&self, generation: u64) -> eyre::Result<()> {
        let replaced = self
            .cache
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner)
            .prepare_generation(generation)?;
        if replaced {
            self.resolution_cache
                .lock()
                .unwrap_or_else(std::sync::PoisonError::into_inner)
                .clear();
        }
        Ok(())
    }

    fn resolution_cache_counts(&self) -> (u64, u64) {
        let cache = self
            .resolution_cache
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        (cache.hits, cache.misses)
    }

    fn resolution_surface_cache_key(
        workspace_generation: u64,
        keys: &[FactCacheKey],
    ) -> ResolutionSurfaceCacheKey {
        let mut hasher = blake3::Hasher::new();
        hasher.update(&workspace_generation.to_le_bytes());
        for key in keys {
            hasher.update(&key.sequence.to_le_bytes());
            for field in [
                key.root_id.as_str(),
                key.root_relative_path.as_str(),
                key.report_path.as_str(),
                key.source_set.as_str(),
                key.content_hash.as_str(),
            ] {
                let length = u64::try_from(field.len()).unwrap_or(u64::MAX);
                hasher.update(&length.to_le_bytes());
                hasher.update(field.as_bytes());
            }
        }
        ResolutionSurfaceCacheKey {
            workspace_generation,
            fact_fingerprint: format!("blake3:{}", hasher.finalize().to_hex()),
        }
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
        self.prepare_workspace_generation(request.workspace.workspace_generation)?;
        let before_cache = self.cache_snapshot();
        let (before_resolution_hits, before_resolution_misses) = self.resolution_cache_counts();
        let source_snapshot = self.collect_source_snapshot(request, cancellation_token)?;
        let surface_key = Self::resolution_surface_cache_key(
            request.workspace.workspace_generation,
            &source_snapshot.keys,
        );
        let cached_surface = self
            .resolution_cache
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner)
            .get(&surface_key);
        let (surface, source_snapshot_micros, fact_parse_micros, link_micros, reparsed_files) =
            if let Some(surface) = cached_surface {
                (surface, source_snapshot.source_snapshot_micros, 0, 0, 0)
            } else {
                let source_snapshot_micros = source_snapshot.source_snapshot_micros;
                let DefinitionFactSnapshot {
                    facts,
                    fact_parse_micros,
                    reparsed_files,
                } = self.collect_facts(source_snapshot, cancellation_token)?;
                let source_files = facts.len();
                let (live, link_micros) =
                    self.link_facts(facts, source_files, cancellation_token)?;
                cancellation_token.bail_if_cancelled()?;
                let surface = JavaDefinitionResolutionSurface::build(
                    &self.workspace,
                    &live,
                    self.dependencies.as_ref(),
                );
                self.resolution_cache
                    .lock()
                    .unwrap_or_else(std::sync::PoisonError::into_inner)
                    .insert(surface_key, Arc::clone(&surface));
                (
                    surface,
                    source_snapshot_micros,
                    fact_parse_micros,
                    link_micros,
                    reparsed_files,
                )
            };
        let (result, lookup_micros) = self.lookup(request, &surface, cancellation_token)?;
        let after_cache = self.cache_snapshot();
        let (after_resolution_hits, after_resolution_misses) = self.resolution_cache_counts();
        let resolution_cache_entries = self
            .resolution_cache
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner)
            .entries
            .len()
            .try_into()
            .unwrap_or(u64::MAX);
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
                resolution_cache_hits: after_resolution_hits.saturating_sub(before_resolution_hits),
                resolution_cache_misses: after_resolution_misses
                    .saturating_sub(before_resolution_misses),
                resolution_cache_entries,
                resolution_declarations: surface.declaration_count().try_into().unwrap_or(u64::MAX),
                reparsed_files,
                source_files: self.workspace.files.len().try_into().unwrap_or(u64::MAX),
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

    fn collect_source_snapshot<'workspace>(
        &'workspace self,
        request: &DefinitionAtPositionRequest,
        cancellation_token: &CancellationToken,
    ) -> eyre::Result<DefinitionSourceSnapshot<'workspace>> {
        let snapshot_started = Instant::now();
        let mut files = self.workspace.files.iter().collect::<Vec<_>>();
        files.sort_by(|left, right| {
            (&left.report_path, &left.source_set, &left.absolute_path).cmp(&(
                &right.report_path,
                &right.source_set,
                &right.absolute_path,
            ))
        });
        let snapshots = files
            .par_iter()
            .enumerate()
            .map(|(sequence, file)| {
                cancellation_token.bail_if_cancelled()?;
                let source = Self::source_for_request(file, request)?;
                let key = Self::fact_cache_key(request, file, sequence, &source);
                Ok((source, key))
            })
            .collect::<eyre::Result<Vec<_>>>()?;
        let (sources, keys) = snapshots.into_iter().unzip();
        Ok(DefinitionSourceSnapshot {
            files,
            sources,
            keys,
            source_snapshot_micros: duration_micros(snapshot_started.elapsed()),
        })
    }

    fn collect_facts(
        &self,
        snapshot: DefinitionSourceSnapshot<'_>,
        cancellation_token: &CancellationToken,
    ) -> eyre::Result<DefinitionFactSnapshot> {
        let DefinitionSourceSnapshot {
            files,
            sources,
            keys,
            ..
        } = snapshot;
        let mut facts = Vec::with_capacity(files.len());
        let mut fact_parse_micros = 0_u64;
        let mut reparsed_files = 0_u64;
        for ((file, source), key) in files.into_iter().zip(sources).zip(keys) {
            cancellation_token.bail_if_cancelled()?;
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
            facts,
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
        surface: &Arc<JavaDefinitionResolutionSurface>,
        cancellation_token: &CancellationToken,
    ) -> eyre::Result<(DefinitionAtPositionResult, u64)> {
        cancellation_token.bail_if_cancelled()?;
        let started = Instant::now();
        let mut result = surface.definition_at_position(&self.workspace, request)?;
        normalize_definition_at_position_context(
            self.workspace.context.clone(),
            self.dependencies.as_ref(),
            &mut result,
        );
        if let Some(dependency_index) = &self.dependency_index {
            result = result.with_dependency_index(dependency_index.clone());
        }
        self.enrich_dependency_source_spans(&mut result);
        cancellation_token.bail_if_cancelled()?;
        Ok((result, duration_micros(started.elapsed())))
    }

    fn enrich_dependency_source_spans(&self, result: &mut DefinitionAtPositionResult) {
        for definition in &mut result.definitions {
            self.enrich_dependency_source_span(&mut definition.identifier_span);
            self.enrich_dependency_source_span(&mut definition.declaration_span);
        }
    }

    fn enrich_dependency_source_span(&self, span: &mut super::DefinitionSourceSpanOutput) {
        if span.resolver_id != "dependency-index" {
            return;
        }
        let mut matches = Vec::new();
        for root in &self.dependency_source_roots {
            if root.source_set != span.source_set {
                continue;
            }
            let prefix = root.report_prefix.trim_end_matches('/');
            let Some(relative) = span
                .report_path
                .strip_prefix(prefix)
                .and_then(|tail| tail.strip_prefix('/'))
            else {
                continue;
            };
            if relative.is_empty() || relative.split('/').any(str::is_empty) {
                continue;
            }
            let mut native = root.canonical_absolute_path.clone();
            for segment in relative.split('/') {
                native.push(segment);
            }
            let Ok(source) = std::fs::read_to_string(&native) else {
                continue;
            };
            if blake3_content_hash(&source) != span.source_hash {
                continue;
            }
            let Ok(start) = usize::try_from(span.start_byte) else {
                continue;
            };
            let Ok(end) = usize::try_from(span.end_byte) else {
                continue;
            };
            if end > source.len()
                || start > end
                || !source.is_char_boundary(start)
                || !source.is_char_boundary(end)
            {
                continue;
            }
            matches.push((root, relative.to_owned(), source));
        }
        let [(root, relative, source)] = matches.as_slice() else {
            return;
        };
        "dependency-source".clone_into(&mut span.resolver_id);
        span.root_id.clone_from(&root.root_id);
        span.root_relative_path.clone_from(relative);
        span.address = contributed_address("dependency-source", &root.root_id, relative);
        span.source_sha256 = Some(sha256_content_hash(source));
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
            resolution_cache_hits: 0,
            resolution_cache_misses: 0,
            resolution_cache_entries: self
                .resolution_cache
                .lock()
                .unwrap_or_else(std::sync::PoisonError::into_inner)
                .entries
                .len()
                .try_into()
                .unwrap_or(u64::MAX),
            resolution_declarations: 0,
            reparsed_files: 0,
            source_files: self.workspace.files.len().try_into().unwrap_or(u64::MAX),
            cache: self.cache_snapshot(),
        }
    }
}

fn duration_micros(duration: std::time::Duration) -> u64 {
    duration.as_micros().try_into().unwrap_or(u64::MAX)
}

fn contributed_address(scheme: &str, authority: &str, relative: &str) -> String {
    let path = relative
        .split('/')
        .map(percent_encode_component)
        .collect::<Vec<_>>()
        .join("/");
    format!(
        "{}://{}/{}",
        scheme,
        percent_encode_component(authority),
        path
    )
}

fn percent_encode_component(value: &str) -> String {
    const HEX: &[u8; 16] = b"0123456789ABCDEF";
    let mut encoded = String::with_capacity(value.len());
    for byte in value.as_bytes() {
        if byte.is_ascii_alphanumeric() || matches!(byte, b'-' | b'.' | b'_' | b'~') {
            encoded.push(char::from(*byte));
        } else {
            encoded.push('%');
            encoded.push(char::from(HEX[usize::from(byte >> 4)]));
            encoded.push(char::from(HEX[usize::from(byte & 0x0F)]));
        }
    }
    encoded
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
        assert_eq!(warm.telemetry.fact_cache_hits, 0);
        assert_eq!(warm.telemetry.resolution_cache_hits, 1);
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
        assert_eq!(warm_field.telemetry.fact_cache_hits, 0);
        assert_eq!(warm_field.telemetry.resolution_cache_hits, 1);
        assert_eq!(warm_method.telemetry.reparsed_files, 0);
        assert_eq!(warm_method.telemetry.fact_cache_hits, 0);
        assert_eq!(warm_method.telemetry.resolution_cache_hits, 1);
    }

    #[test]
    fn ready_dependency_definition_is_addressable_and_complete() {
        let source = concat!(
            "package q;\n",
            "import net.minecraft.core.BlockPos;\n",
            "class Use { BlockPos value; }\n",
        );
        let (directory, workspace) = workspace_from_sources(&[("q/Use.java", source)]);
        let identity = "dependency-ready";
        let mut request = request_at(&workspace, "q/Use.java", source, 8, "BlockPos value");
        use_dependency_identity(&workspace, &mut request, identity);
        let dependency_source = "B";
        let dependency_root = directory.path().join("dependency-source");
        let dependency_relative = "net/minecraft/core/BlockPos.java";
        let dependency_path = dependency_root.join(dependency_relative);
        std::fs::create_dir_all(dependency_path.parent().expect("dependency parent"))
            .expect("dependency parent");
        std::fs::write(&dependency_path, dependency_source).expect("dependency source");
        let mut dependencies = dependency_type("net.minecraft.core.BlockPos");
        for definition in &mut dependencies.definitions {
            definition.identifier_span.source_hash = blake3_content_hash(dependency_source);
            definition.declaration_span.source_hash = blake3_content_hash(dependency_source);
        }
        let engine = DefinitionAtPositionEngine::new_with_dependency_source_roots(
            workspace,
            Some(dependencies),
            Some(dependency_index(
                DependencySymbolIndexProbeStatus::Ready,
                SymbolQueryCompleteness::Complete,
                identity,
            )),
            vec![DefinitionDependencySourceRoot {
                root_id: "dependency-source-0".to_owned(),
                report_prefix: "dependency/minecraft/main/pipeline".to_owned(),
                source_set: "dependency:minecraft:main".to_owned(),
                canonical_absolute_path: dependency_root,
            }],
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
            "dependency-source"
        );
        assert_eq!(
            result.definitions[0].identifier_span.root_id,
            "dependency-source-0"
        );
        assert_eq!(
            result.definitions[0].identifier_span.root_relative_path,
            dependency_relative
        );
        assert_eq!(
            result.definitions[0].identifier_span.address,
            "dependency-source://dependency-source-0/net/minecraft/core/BlockPos.java"
        );
        assert_eq!(
            result.definitions[0].identifier_span.source_sha256,
            Some(sha256_content_hash(dependency_source))
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

    #[test]
    fn resolution_surface_cache_is_content_keyed_and_bounded() {
        let (_directory, workspace) = workspace_from_sources(&[
            ("p/A.java", "package p; public class A {}\n"),
            ("q/Use.java", "package q; class Use {}\n"),
        ]);
        let overlays = [
            "package q; import p.A; class Use { A value0; }\n",
            "package q; import p.A; class Use { A value1; }\n",
            "package q; import p.A; class Use { A value2; }\n",
        ];
        let requests = overlays
            .iter()
            .enumerate()
            .map(|(index, text)| {
                request_at(
                    &workspace,
                    "q/Use.java",
                    text,
                    12,
                    &format!("A value{index}"),
                )
            })
            .collect::<Vec<_>>();
        let engine = DefinitionAtPositionEngine::new(
            workspace,
            None,
            None,
            DefinitionAtPositionEngineLimits::default(),
        )
        .expect("engine");

        for request in &requests {
            let output = engine
                .analyze_with_telemetry(request, &CancellationToken::new())
                .expect("overlay analysis");
            assert_eq!(output.result.outcome, DefinitionAtPositionOutcome::Success);
            assert!(
                output.telemetry.resolution_cache_entries
                    <= DEFAULT_DEFINITION_ENGINE_MAX_RESOLUTION_SURFACES as u64
            );
        }
        let evicted = engine
            .analyze_with_telemetry(&requests[0], &CancellationToken::new())
            .expect("evicted overlay analysis");

        assert_eq!(evicted.telemetry.resolution_cache_hits, 0);
        assert_eq!(evicted.telemetry.resolution_cache_misses, 1);
        assert_eq!(
            evicted.telemetry.resolution_cache_entries,
            DEFAULT_DEFINITION_ENGINE_MAX_RESOLUTION_SURFACES as u64
        );
    }
}
