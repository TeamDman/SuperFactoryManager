use super::DefinitionAtPositionOutcome;
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
use super::JavaInteractionFileOutput;
use super::JavaInteractionFileState;
use super::JavaInteractionMapRequest;
use super::JavaInteractionMapResult;
use super::JavaLiveDefinitionSurface;
use super::JavaSourceFile;
use super::JavaSourceWorkspace;
use super::JavaSymbolKind;
use super::JavaUsageResolutionSurface;
use super::JavaUsageResolutionTelemetry;
use super::JavaUsageSourceSnapshot;
#[cfg(test)]
use super::JdkSourceDomainState;
use super::UsageAtPositionRequest;
use super::UsageAtPositionResult;
use super::blake3_content_hash;
use super::build_java_interaction_map;
use super::content_hash_with_expected_algorithm;
use super::extract_java_file_facts_from_text_with_detail;
use super::jdk_resolution_definitions;
use super::normalize_definition_at_position_context;
use super::normalize_usage_at_position_context;
use super::sha256_content_hash;
use super::usage_at_position::USAGE_AT_POSITION_MAX_ENCODED_BYTES;
use super::usage_at_position::USAGE_AT_POSITION_MAX_RESULTS;
use super::validate_definition_document_projection;
use super::validate_definition_request_workspace_identity;
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
    pub resolution_types: u64,
    pub resolution_fields: u64,
    pub resolution_methods: u64,
    pub usage_index_build_micros: u64,
    pub usage_index_cache_hits: u64,
    pub usage_index_candidate_files: u64,
    pub usage_index_parsed_files: u64,
    pub usage_index_retained_rows: u64,
    pub usage_index_cache_entries: u64,
    pub usage_index_cache_retained_bytes: u64,
    pub usage_index_cache_evictions: u64,
    pub reparsed_files: u64,
    pub source_files: u64,
    pub cache: DefinitionAtPositionEngineCacheSnapshot,
}

pub struct DefinitionAtPositionEngineOutput {
    pub result: DefinitionAtPositionResult,
    pub telemetry: DefinitionAtPositionEngineTelemetry,
}

pub struct UsageAtPositionEngineOutput {
    pub result: UsageAtPositionResult,
    pub telemetry: DefinitionAtPositionEngineTelemetry,
}

pub struct JavaInteractionMapEngineOutput {
    pub result: JavaInteractionMapResult,
    pub telemetry: DefinitionAtPositionEngineTelemetry,
}

struct DefinitionSourceSnapshot {
    files: Vec<JavaSourceFile>,
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

type UsageResolutionSlot = Arc<Mutex<Option<Arc<JavaUsageResolutionSurface>>>>;
type ResolutionSurfaceHandle = (Arc<JavaDefinitionResolutionSurface>, UsageResolutionSlot);

struct AcquiredResolutionSurface {
    surface: Arc<JavaDefinitionResolutionSurface>,
    usage_slot: UsageResolutionSlot,
    fact_parse_micros: u64,
    link_micros: u64,
    reparsed_files: u64,
}

struct CachedResolutionSurface {
    surface: Arc<JavaDefinitionResolutionSurface>,
    usages: UsageResolutionSlot,
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
    fn get(&mut self, key: &ResolutionSurfaceCacheKey) -> Option<ResolutionSurfaceHandle> {
        self.access_clock = self.access_clock.saturating_add(1);
        let Some(entry) = self.entries.get_mut(key) else {
            self.misses = self.misses.saturating_add(1);
            return None;
        };
        entry.last_access = self.access_clock;
        self.hits = self.hits.saturating_add(1);
        Some((Arc::clone(&entry.surface), Arc::clone(&entry.usages)))
    }

    fn insert(
        &mut self,
        key: ResolutionSurfaceCacheKey,
        surface: Arc<JavaDefinitionResolutionSurface>,
        usages: UsageResolutionSlot,
    ) {
        self.access_clock = self.access_clock.saturating_add(1);
        self.entries.insert(
            key,
            CachedResolutionSurface {
                surface,
                usages,
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
    /// Single-flight gate for cold linked-surface construction. Cache hits
    /// hold this only long enough to perform the lookup; cache misses retain it
    /// through fact collection/linking/publication so two concurrent requests
    /// cannot duplicate the largest retained analysis object.
    resolution_build: Mutex<()>,
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
            .filter(|definition| {
                matches!(
                    definition.symbol.kind,
                    JavaSymbolKind::Class
                        | JavaSymbolKind::Interface
                        | JavaSymbolKind::Enum
                        | JavaSymbolKind::Record
                        | JavaSymbolKind::Annotation
                )
            })
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
            resolution_build: Mutex::new(()),
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

    fn lock_resolution_build<'engine>(
        &'engine self,
        cancellation_token: &CancellationToken,
    ) -> eyre::Result<std::sync::MutexGuard<'engine, ()>> {
        loop {
            match self.resolution_build.try_lock() {
                Ok(guard) => return Ok(guard),
                Err(std::sync::TryLockError::WouldBlock) => {
                    cancellation_token.bail_if_cancelled()?;
                    std::thread::park_timeout(std::time::Duration::from_millis(2));
                }
                Err(std::sync::TryLockError::Poisoned(poisoned)) => {
                    return Ok(poisoned.into_inner());
                }
            }
        }
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

    fn acquire_resolution_surface(
        &self,
        workspace_generation: u64,
        source_snapshot: &DefinitionSourceSnapshot,
        cancellation_token: &CancellationToken,
    ) -> eyre::Result<AcquiredResolutionSurface> {
        let surface_key =
            Self::resolution_surface_cache_key(workspace_generation, &source_snapshot.keys);
        let _single_flight = self.lock_resolution_build(cancellation_token)?;
        if let Some((surface, usage_slot)) = self
            .resolution_cache
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner)
            .get(&surface_key)
        {
            return Ok(AcquiredResolutionSurface {
                surface,
                usage_slot,
                fact_parse_micros: 0,
                link_micros: 0,
                reparsed_files: 0,
            });
        }

        let DefinitionFactSnapshot {
            facts,
            fact_parse_micros,
            reparsed_files,
        } = self.collect_facts(source_snapshot, cancellation_token)?;
        let source_files = facts.len();
        let (live, link_micros) = self.link_facts(facts, source_files, cancellation_token)?;
        cancellation_token.bail_if_cancelled()?;
        let surface = JavaDefinitionResolutionSurface::build(
            &self.workspace,
            &live,
            self.dependencies.as_ref(),
        );
        // References are target-lazy. Definition requests retain only this
        // linked declaration surface; the first reference request initializes
        // the bounded target cache behind the shared slot.
        let usage_slot = Arc::new(Mutex::new(None));
        self.resolution_cache
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner)
            .insert(surface_key, Arc::clone(&surface), Arc::clone(&usage_slot));
        Ok(AcquiredResolutionSurface {
            surface,
            usage_slot,
            fact_parse_micros,
            link_micros,
            reparsed_files,
        })
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
        let source_snapshot_micros = source_snapshot.source_snapshot_micros;
        let AcquiredResolutionSurface {
            surface,
            fact_parse_micros,
            link_micros,
            reparsed_files,
            ..
        } = self.acquire_resolution_surface(
            request.workspace.workspace_generation,
            &source_snapshot,
            cancellation_token,
        )?;
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
        let (resolution_types, resolution_fields, resolution_methods) =
            surface.declaration_counts();
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
                resolution_types: resolution_types.try_into().unwrap_or(u64::MAX),
                resolution_fields: resolution_fields.try_into().unwrap_or(u64::MAX),
                resolution_methods: resolution_methods.try_into().unwrap_or(u64::MAX),
                usage_index_build_micros: 0,
                usage_index_cache_hits: 0,
                usage_index_candidate_files: 0,
                usage_index_parsed_files: 0,
                usage_index_retained_rows: 0,
                usage_index_cache_entries: 0,
                usage_index_cache_retained_bytes: 0,
                usage_index_cache_evictions: 0,
                reparsed_files,
                source_files: self.workspace.files.len().try_into().unwrap_or(u64::MAX),
                cache: after_cache,
            },
        })
    }

    /// Analyze one usage-at-position request while sharing the same immutable
    /// facts and linked resolution surface as definition requests.
    ///
    /// # Errors
    ///
    /// Returns an error when the request is invalid, the immutable source
    /// snapshot cannot be collected, analysis is cancelled, or linking fails.
    pub fn analyze_usages_with_telemetry(
        &self,
        request: &UsageAtPositionRequest,
        cancellation_token: &CancellationToken,
    ) -> eyre::Result<UsageAtPositionEngineOutput> {
        let started = Instant::now();
        if let Some(result) = self.usage_request_rejection(request)? {
            return Ok(UsageAtPositionEngineOutput {
                result,
                telemetry: self.empty_telemetry(started),
            });
        }
        let definition_request = request.as_definition_request();
        cancellation_token.bail_if_cancelled()?;
        self.prepare_workspace_generation(request.workspace.workspace_generation)?;
        let before_cache = self.cache_snapshot();
        let (before_resolution_hits, before_resolution_misses) = self.resolution_cache_counts();
        let source_snapshot =
            self.collect_source_snapshot(&definition_request, cancellation_token)?;
        let source_snapshot_micros = source_snapshot.source_snapshot_micros;
        let AcquiredResolutionSurface {
            surface,
            usage_slot,
            fact_parse_micros,
            link_micros,
            reparsed_files,
        } = self.acquire_resolution_surface(
            request.workspace.workspace_generation,
            &source_snapshot,
            cancellation_token,
        )?;
        let (usage_surface, usage_index_build_micros, usage_index_cache_hits) = {
            // Serialize only first-use construction for this linked surface.
            // Definition requests never acquire this lock, while concurrent
            // usage requests share one completed index instead of multiplying
            // the workspace parse and its retained allocator pressure.
            let mut slot = usage_slot
                .lock()
                .unwrap_or_else(std::sync::PoisonError::into_inner);
            if let Some(cached) = slot.as_ref() {
                (Arc::clone(cached), 0_u64, 0_u64)
            } else {
                let usage_index_started = Instant::now();
                let built = JavaUsageResolutionSurface::new()?;
                let build_micros = duration_micros(usage_index_started.elapsed());
                *slot = Some(Arc::clone(&built));
                (built, build_micros, 0_u64)
            }
        };
        let (result, lookup_micros, usage_resolution_telemetry) = self.lookup_usages(
            request,
            &surface,
            &usage_surface,
            &source_snapshot,
            cancellation_token,
        )?;
        let usage_index_build_micros =
            usage_index_build_micros.saturating_add(usage_resolution_telemetry.target_build_micros);
        let usage_index_cache_hits =
            usage_index_cache_hits.saturating_add(usage_resolution_telemetry.target_cache_hits);
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
        let (resolution_types, resolution_fields, resolution_methods) =
            surface.declaration_counts();
        Ok(UsageAtPositionEngineOutput {
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
                resolution_types: resolution_types.try_into().unwrap_or(u64::MAX),
                resolution_fields: resolution_fields.try_into().unwrap_or(u64::MAX),
                resolution_methods: resolution_methods.try_into().unwrap_or(u64::MAX),
                usage_index_build_micros,
                usage_index_cache_hits,
                usage_index_candidate_files: usage_resolution_telemetry.candidate_files,
                usage_index_parsed_files: usage_resolution_telemetry.parsed_files,
                usage_index_retained_rows: usage_resolution_telemetry.retained_rows,
                usage_index_cache_entries: usage_resolution_telemetry.cache_entries,
                usage_index_cache_retained_bytes: usage_resolution_telemetry.cache_retained_bytes,
                usage_index_cache_evictions: usage_resolution_telemetry.cache_evictions,
                reparsed_files,
                source_files: self.workspace.files.len().try_into().unwrap_or(u64::MAX),
                cache: after_cache,
            },
        })
    }

    /// Build one generation-tagged, paged semantic interaction map while
    /// sharing the same immutable facts and linked resolution surface as
    /// definition/reference queries.
    ///
    /// # Errors
    ///
    /// Returns an error for source I/O, cancellation, parsing/linking failure,
    /// or an encoded page that cannot satisfy the caller's declared bound.
    /// Contract failures remain typed `invalid-request` results.
    #[expect(
        clippy::too_many_lines,
        reason = "the interaction-map transaction keeps validation, cancellation, cache deltas, and telemetry in one auditable sequence"
    )]
    pub fn analyze_interaction_map_with_telemetry(
        &self,
        request: &JavaInteractionMapRequest,
        cancellation_token: &CancellationToken,
    ) -> eyre::Result<JavaInteractionMapEngineOutput> {
        let started = Instant::now();
        if let Err(error) = request.validate() {
            return Ok(JavaInteractionMapEngineOutput {
                result: JavaInteractionMapResult::invalid_request(request, format!("{error:#}")),
                telemetry: self.empty_telemetry(started),
            });
        }
        let definition_request = request.as_definition_request()?;
        if let Some(rejection) = self.request_rejection(&definition_request)? {
            let message = rejection
                .diagnostics
                .iter()
                .map(|diagnostic| diagnostic.message.as_str())
                .collect::<Vec<_>>()
                .join("; ");
            let result = match rejection.outcome {
                DefinitionAtPositionOutcome::StaleDocument => {
                    JavaInteractionMapResult::stale_document(request, message)
                }
                DefinitionAtPositionOutcome::Unavailable => {
                    JavaInteractionMapResult::unavailable(request, message)
                }
                _ => JavaInteractionMapResult::invalid_request(request, message),
            };
            return Ok(JavaInteractionMapEngineOutput {
                result,
                telemetry: self.empty_telemetry(started),
            });
        }
        cancellation_token.bail_if_cancelled()?;
        self.prepare_workspace_generation(request.workspace.workspace_generation)?;
        let before_cache = self.cache_snapshot();
        let (before_resolution_hits, before_resolution_misses) = self.resolution_cache_counts();
        let source_snapshot =
            self.collect_source_snapshot(&definition_request, cancellation_token)?;
        let source_snapshot_micros = source_snapshot.source_snapshot_micros;
        let AcquiredResolutionSurface {
            surface,
            fact_parse_micros,
            link_micros,
            reparsed_files,
            ..
        } = self.acquire_resolution_surface(
            request.workspace.workspace_generation,
            &source_snapshot,
            cancellation_token,
        )?;
        let (semantic_generation, semantic_fingerprint) =
            java_interaction_semantic_identity(request, &surface);
        let lookup_started = Instant::now();
        let result = if request
            .known_semantic_fingerprint
            .as_deref()
            .is_some_and(|known| known == semantic_fingerprint)
        {
            JavaInteractionMapResult::not_modified(
                request,
                semantic_generation,
                semantic_fingerprint,
            )
        } else {
            let target = self.resolve_request_document(&definition_request)?;
            let resolved = surface.resolve_interaction_document(&target, &definition_request)?;
            cancellation_token.bail_if_cancelled()?;
            let inventory = self.interaction_file_inventory(&source_snapshot, &surface);
            build_java_interaction_map(
                request,
                &resolved,
                &surface,
                &self.workspace,
                &self.dependency_source_roots,
                inventory,
                semantic_generation,
                semantic_fingerprint,
            )?
        };
        let lookup_micros = duration_micros(lookup_started.elapsed());
        cancellation_token.bail_if_cancelled()?;
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
        let (resolution_types, resolution_fields, resolution_methods) =
            surface.declaration_counts();
        Ok(JavaInteractionMapEngineOutput {
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
                resolution_types: resolution_types.try_into().unwrap_or(u64::MAX),
                resolution_fields: resolution_fields.try_into().unwrap_or(u64::MAX),
                resolution_methods: resolution_methods.try_into().unwrap_or(u64::MAX),
                usage_index_build_micros: 0,
                usage_index_cache_hits: 0,
                usage_index_candidate_files: 0,
                usage_index_parsed_files: 0,
                usage_index_retained_rows: 0,
                usage_index_cache_entries: 0,
                usage_index_cache_retained_bytes: 0,
                usage_index_cache_evictions: 0,
                reparsed_files,
                source_files: self.workspace.files.len().try_into().unwrap_or(u64::MAX),
                cache: after_cache,
            },
        })
    }

    #[expect(
        clippy::too_many_lines,
        reason = "the inventory exhaustively records workspace, JDK, dependency, and missing-root terminal states in one ordered ledger"
    )]
    fn interaction_file_inventory(
        &self,
        snapshot: &DefinitionSourceSnapshot,
        surface: &JavaDefinitionResolutionSurface,
    ) -> Vec<JavaInteractionFileOutput> {
        let mut files = BTreeMap::<(String, String, String), JavaInteractionFileOutput>::new();
        for ((file, source), key) in snapshot
            .files
            .iter()
            .zip(&snapshot.sources)
            .zip(&snapshot.keys)
        {
            if !self.workspace.files.iter().any(|candidate| {
                candidate.root_id == file.root_id
                    && candidate.root_relative_path == file.root_relative_path
            }) {
                continue;
            }
            let state = if key.content_hash == blake3_content_hash(source) {
                JavaInteractionFileState::Covered
            } else {
                JavaInteractionFileState::Stale
            };
            files.insert(
                (
                    file.report_path.clone(),
                    file.source_set.clone(),
                    key.content_hash.clone(),
                ),
                JavaInteractionFileOutput {
                    address: contributed_address(
                        "workspace",
                        &file.root_id,
                        &file.root_relative_path,
                    ),
                    resolver_id: "workspace".to_owned(),
                    root_id: file.root_id.clone(),
                    root_relative_path: file.root_relative_path.clone(),
                    report_path: file.report_path.clone(),
                    source_set: file.source_set.clone(),
                    content_hash: Some(key.content_hash.clone()),
                    state,
                    diagnostic: None,
                },
            );
        }

        for fact in surface.files() {
            let key = (
                fact.report_path.clone(),
                fact.source_set.clone(),
                fact.source_hash.clone(),
            );
            if files.contains_key(&key) {
                continue;
            }
            let jdk_identity = self
                .workspace
                .jdk_sources
                .report_identity(&fact.report_path, &fact.source_set);
            let dependency_identity =
                self.dependency_source_identity(&fact.report_path, &fact.source_set);
            let (address, resolver_id, root_id, root_relative_path, state, diagnostic) =
                if let Some(identity) = jdk_identity {
                    (
                        contributed_address(
                            "jdk-source",
                            &identity.root_id,
                            &identity.root_relative_path,
                        ),
                        "jdk-source".to_owned(),
                        identity.root_id,
                        identity.root_relative_path,
                        JavaInteractionFileState::Covered,
                        None,
                    )
                } else if let Some((root, relative)) = dependency_identity {
                    (
                        contributed_address("dependency-source", &root.root_id, &relative),
                        "dependency-source".to_owned(),
                        root.root_id.clone(),
                        relative,
                        JavaInteractionFileState::Covered,
                        None,
                    )
                } else {
                    (
                        contributed_address("dependency-index", "jdk", &fact.report_path),
                        "dependency-index".to_owned(),
                        "jdk".to_owned(),
                        fact.report_path.clone(),
                        JavaInteractionFileState::Partial,
                        Some(
                            "JDK semantic facts were indexed, but their exact source identity was not available"
                                .to_owned(),
                        ),
                    )
                };
            files.insert(
                key,
                JavaInteractionFileOutput {
                    address,
                    resolver_id,
                    root_id,
                    root_relative_path,
                    report_path: fact.report_path.clone(),
                    source_set: fact.source_set.clone(),
                    content_hash: Some(fact.source_hash.clone()),
                    state,
                    diagnostic,
                },
            );
        }

        if let Some(dependencies) = &self.dependencies {
            for span in dependencies
                .definitions
                .iter()
                .flat_map(|definition| [&definition.identifier_span, &definition.declaration_span])
                .chain(dependencies.usages.iter().map(|usage| &usage.span))
                .chain(
                    dependencies
                        .diagnostics
                        .iter()
                        .filter_map(|diagnostic| diagnostic.span.as_ref()),
                )
            {
                let key = (
                    span.path.clone(),
                    span.source_set.clone(),
                    span.source_hash.clone(),
                );
                files
                    .entry(key)
                    .or_insert_with(|| self.dependency_inventory_file(span));
            }
        }

        for root in self
            .workspace
            .context
            .source_roots
            .iter()
            .filter(|root| !root.exists)
        {
            files.insert(
                (root.path.clone(), root.source_set.clone(), String::new()),
                JavaInteractionFileOutput {
                    address: root.path.clone(),
                    resolver_id: "unavailable-source-root".to_owned(),
                    root_id: root.id.clone(),
                    root_relative_path: root.path.clone(),
                    report_path: root.path.clone(),
                    source_set: root.source_set.clone(),
                    content_hash: None,
                    state: JavaInteractionFileState::Missing,
                    diagnostic: Some("Selected Java source root is unavailable".to_owned()),
                },
            );
        }
        files.into_values().collect()
    }

    fn dependency_inventory_file(
        &self,
        span: &super::JavaSourceSpanOutput,
    ) -> JavaInteractionFileOutput {
        if let Some((root, relative)) =
            self.dependency_source_identity(&span.path, &span.source_set)
        {
            return JavaInteractionFileOutput {
                address: contributed_address("dependency-source", &root.root_id, &relative),
                resolver_id: "dependency-source".to_owned(),
                root_id: root.root_id.clone(),
                root_relative_path: relative,
                report_path: span.path.clone(),
                source_set: span.source_set.clone(),
                content_hash: Some(span.source_hash.clone()),
                state: JavaInteractionFileState::Covered,
                diagnostic: None,
            };
        }
        JavaInteractionFileOutput {
            address: contributed_address("dependency-index", "dependency-index", &span.path),
            resolver_id: "dependency-index".to_owned(),
            root_id: "dependency-index".to_owned(),
            root_relative_path: span.path.clone(),
            report_path: span.path.clone(),
            source_set: span.source_set.clone(),
            content_hash: Some(span.source_hash.clone()),
            state: JavaInteractionFileState::Covered,
            diagnostic: Some(
                "Indexed dependency semantics are available; exact source material is not acquired"
                    .to_owned(),
            ),
        }
    }

    fn dependency_source_identity(
        &self,
        report_path: &str,
        source_set: &str,
    ) -> Option<(&DefinitionDependencySourceRoot, String)> {
        self.dependency_source_roots.iter().find_map(|root| {
            if root.source_set != source_set {
                return None;
            }
            let relative = report_path
                .strip_prefix(root.report_prefix.trim_end_matches('/'))?
                .strip_prefix('/')?;
            (!relative.is_empty() && !relative.split('/').any(str::is_empty))
                .then(|| (root, relative.to_owned()))
        })
    }

    fn usage_request_rejection(
        &self,
        request: &UsageAtPositionRequest,
    ) -> eyre::Result<Option<UsageAtPositionResult>> {
        let definition_request = request.as_definition_request();
        if let Err(error) = request.validate() {
            return Ok(Some(UsageAtPositionResult::from_definition(
                DefinitionAtPositionResult::invalid_request(
                    &definition_request,
                    self.workspace.context.clone(),
                    format!("{error:#}"),
                ),
            )));
        }
        Ok(self
            .request_rejection(&definition_request)?
            .map(UsageAtPositionResult::from_definition))
    }

    fn request_rejection(
        &self,
        request: &DefinitionAtPositionRequest,
    ) -> eyre::Result<Option<DefinitionAtPositionResult>> {
        if let Err(error) = validate_definition_request_workspace_identity(&self.workspace, request)
        {
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
        let target = match self.resolve_request_document(request) {
            Ok(target) => target,
            Err(error) => {
                return Ok(Some(DefinitionAtPositionResult::invalid_request(
                    request,
                    self.workspace.context.clone(),
                    format!("{error:#}"),
                )));
            }
        };
        let Some(expected_disk_hash) = request.document.disk_content_hash.as_deref() else {
            return Ok(None);
        };
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

    fn collect_source_snapshot(
        &self,
        request: &DefinitionAtPositionRequest,
        cancellation_token: &CancellationToken,
    ) -> eyre::Result<DefinitionSourceSnapshot> {
        let snapshot_started = Instant::now();
        let mut files = self.workspace.files.clone();
        let target = self.resolve_request_document(request)?;
        if !files.iter().any(|file| {
            file.root_id == target.root_id && file.root_relative_path == target.root_relative_path
        }) {
            files.push(target);
        }
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
        snapshot: &DefinitionSourceSnapshot,
        cancellation_token: &CancellationToken,
    ) -> eyre::Result<DefinitionFactSnapshot> {
        let mut facts = Vec::with_capacity(snapshot.files.len());
        let mut fact_parse_micros = 0_u64;
        let mut reparsed_files = 0_u64;
        for ((file, source), key) in snapshot
            .files
            .iter()
            .zip(&snapshot.sources)
            .zip(&snapshot.keys)
        {
            cancellation_token.bail_if_cancelled()?;
            if let Some(cached) = self
                .cache
                .lock()
                .unwrap_or_else(std::sync::PoisonError::into_inner)
                .get(key)
            {
                facts.push(cached);
                continue;
            }
            let parse_started = Instant::now();
            let source_len = source.len();
            let parsed = self.parse_fact(file, source.clone(), key.sequence)?;
            fact_parse_micros =
                fact_parse_micros.saturating_add(duration_micros(parse_started.elapsed()));
            reparsed_files = reparsed_files.saturating_add(1);
            let retained_bytes = source_len
                .saturating_add(facet_json::to_string(&parsed).map_or(0, |encoded| encoded.len()));
            self.cache
                .lock()
                .unwrap_or_else(std::sync::PoisonError::into_inner)
                .insert(key.clone(), parsed.clone(), retained_bytes, self.limits);
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
        mut facts: Vec<JavaFileFacts>,
        source_files: usize,
        cancellation_token: &CancellationToken,
    ) -> eyre::Result<(JavaLiveDefinitionSurface, u64)> {
        cancellation_token.bail_if_cancelled()?;
        let started = Instant::now();
        let first_jdk_sequence = u64::try_from(facts.len()).unwrap_or(u64::MAX);
        let visible_source_sets = self
            .workspace
            .context
            .source_sets
            .iter()
            .map(|source_set| source_set.id.clone())
            .collect::<Vec<_>>();
        let mut jdk_facts = self.workspace.jdk_sources.facts_for_project(
            &facts,
            first_jdk_sequence,
            &visible_source_sets,
            cancellation_token,
        )?;
        let mut external_resolution = self.external_resolution.clone();
        external_resolution.extend(jdk_resolution_definitions(&jdk_facts));
        external_resolution.sort();
        external_resolution.dedup();
        facts.append(&mut jdk_facts);
        let expected_files = facts.len();
        let mut linker =
            JavaDefinitionLinker::new(self.workspace.context.clone(), &external_resolution);
        for file_facts in facts {
            cancellation_token.bail_if_cancelled()?;
            linker.ingest(file_facts)?;
        }
        let mut live = linker.seal_surface(expected_files)?;
        live.diagnostics
            .extend(self.workspace.diagnostics.iter().cloned());
        live.diagnostics.sort();
        live.diagnostics.dedup();
        debug_assert!(expected_files >= source_files);
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
        let target = self.resolve_request_document(request)?;
        let mut result = surface.definition_at_position(&self.workspace, &target, request)?;
        normalize_definition_at_position_context(
            self.workspace.context.clone(),
            self.dependencies.as_ref(),
            &mut result,
        );
        if let Some(dependency_index) = &self.dependency_index {
            result = result.with_dependency_index(dependency_index.clone());
        }
        self.enrich_dependency_source_spans(&mut result);
        self.enrich_jdk_source_spans(&mut result);
        cancellation_token.bail_if_cancelled()?;
        Ok((result, duration_micros(started.elapsed())))
    }

    fn lookup_usages(
        &self,
        request: &UsageAtPositionRequest,
        surface: &Arc<JavaDefinitionResolutionSurface>,
        usage_surface: &JavaUsageResolutionSurface,
        source_snapshot: &DefinitionSourceSnapshot,
        cancellation_token: &CancellationToken,
    ) -> eyre::Result<(UsageAtPositionResult, u64, JavaUsageResolutionTelemetry)> {
        cancellation_token.bail_if_cancelled()?;
        let started = Instant::now();
        let definition_request = request.as_definition_request();
        let target = self.resolve_request_document(&definition_request)?;
        let (mut result, usage_telemetry) = surface.usages_at_position(
            usage_surface,
            &self.workspace,
            &target,
            JavaUsageSourceSnapshot {
                files: &source_snapshot.files,
                sources: &source_snapshot.sources,
            },
            request,
            self.dependencies.as_ref(),
            cancellation_token,
        )?;
        normalize_usage_at_position_context(
            self.workspace.context.clone(),
            self.dependencies.as_ref(),
            &mut result,
        );
        if let Some(dependency_index) = &self.dependency_index {
            result = result.with_dependency_index(dependency_index.clone());
        }
        self.enrich_usage_dependency_source_spans(&mut result);
        self.enrich_usage_jdk_source_spans(&mut result);
        result.apply_result_limits(
            USAGE_AT_POSITION_MAX_RESULTS,
            USAGE_AT_POSITION_MAX_ENCODED_BYTES,
            usage_telemetry.discarded_candidates,
        )?;
        result.refresh_index_fingerprint();
        cancellation_token.bail_if_cancelled()?;
        Ok((result, duration_micros(started.elapsed()), usage_telemetry))
    }

    fn enrich_dependency_source_spans(&self, result: &mut DefinitionAtPositionResult) {
        for definition in &mut result.definitions {
            self.enrich_dependency_source_span(&mut definition.identifier_span);
            self.enrich_dependency_source_span(&mut definition.declaration_span);
        }
    }

    fn enrich_jdk_source_spans(&self, result: &mut DefinitionAtPositionResult) {
        for definition in &mut result.definitions {
            self.workspace
                .jdk_sources
                .enrich_span(&mut definition.identifier_span);
            self.workspace
                .jdk_sources
                .enrich_span(&mut definition.declaration_span);
        }
    }

    fn enrich_usage_dependency_source_spans(&self, result: &mut UsageAtPositionResult) {
        for definition in &mut result.definitions {
            self.enrich_dependency_source_span(&mut definition.identifier_span);
            self.enrich_dependency_source_span(&mut definition.declaration_span);
        }
        for usage in &mut result.usages {
            self.enrich_dependency_source_span(&mut usage.span);
        }
    }

    fn enrich_usage_jdk_source_spans(&self, result: &mut UsageAtPositionResult) {
        for definition in &mut result.definitions {
            self.workspace
                .jdk_sources
                .enrich_span(&mut definition.identifier_span);
            self.workspace
                .jdk_sources
                .enrich_span(&mut definition.declaration_span);
        }
        for usage in &mut result.usages {
            self.workspace.jdk_sources.enrich_span(&mut usage.span);
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

    /// Analyze one usage request without returning transport telemetry.
    ///
    /// # Errors
    ///
    /// Returns the same errors as [`Self::analyze_usages_with_telemetry`].
    pub fn analyze_usages(
        &self,
        request: &UsageAtPositionRequest,
        cancellation_token: &CancellationToken,
    ) -> eyre::Result<UsageAtPositionResult> {
        self.analyze_usages_with_telemetry(request, cancellation_token)
            .map(|output| output.result)
    }

    /// Analyze one interaction-map request without transport telemetry.
    ///
    /// # Errors
    ///
    /// Returns the same errors as [`Self::analyze_interaction_map_with_telemetry`].
    pub fn analyze_interaction_map(
        &self,
        request: &JavaInteractionMapRequest,
        cancellation_token: &CancellationToken,
    ) -> eyre::Result<JavaInteractionMapResult> {
        self.analyze_interaction_map_with_telemetry(request, cancellation_token)
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

    fn resolve_request_document(
        &self,
        request: &DefinitionAtPositionRequest,
    ) -> eyre::Result<JavaSourceFile> {
        let workspace_matches = self
            .workspace
            .files
            .iter()
            .filter(|file| {
                file.root_id == request.document.root_id
                    && file.root_relative_path == request.document.root_relative_path
            })
            .collect::<Vec<_>>();
        match workspace_matches.as_slice() {
            [file] => {
                validate_definition_document_projection(file, request, "workspace")?;
                return Ok((*file).clone());
            }
            [] => {}
            matches => eyre::bail!(
                "definition document `{}`:`{}` matched {} editable workspace files",
                request.document.root_id,
                request.document.root_relative_path,
                matches.len()
            ),
        }

        if let Some(file) = self.workspace.jdk_sources.addressed_source_file(
            &request.document.root_id,
            &request.document.root_relative_path,
        )? {
            validate_definition_document_projection(&file, request, "jdk-source")?;
            return Ok(file);
        }

        let dependency_roots = self
            .dependency_source_roots
            .iter()
            .filter(|root| root.root_id == request.document.root_id)
            .collect::<Vec<_>>();
        let root = match dependency_roots.as_slice() {
            [root] => *root,
            [] => eyre::bail!(
                "definition document `{}`:`{}` has no editable, managed-JDK, or acquired-dependency source authority",
                request.document.root_id,
                request.document.root_relative_path
            ),
            roots => eyre::bail!(
                "definition document root `{}` matched {} acquired dependency source authorities",
                request.document.root_id,
                roots.len()
            ),
        };
        if request.document.root_relative_path.is_empty()
            || request.document.root_relative_path.contains('\\')
            || request
                .document
                .root_relative_path
                .split('/')
                .any(|segment| segment.is_empty() || matches!(segment, "." | ".."))
        {
            eyre::bail!("addressed dependency-source path is not canonical");
        }
        let canonical_root =
            dunce::canonicalize(&root.canonical_absolute_path).map_err(|error| {
                eyre::eyre!(
                    "failed to resolve acquired dependency source authority `{}`: {error}",
                    root.root_id
                )
            })?;
        let candidate = canonical_root.join(&request.document.root_relative_path);
        let absolute_path = dunce::canonicalize(&candidate).map_err(|error| {
            eyre::eyre!(
                "failed to resolve addressed dependency source `{}`:`{}`: {error}",
                request.document.root_id,
                request.document.root_relative_path
            )
        })?;
        if !absolute_path.starts_with(&canonical_root) {
            eyre::bail!(
                "addressed dependency source `{}`:`{}` escapes its canonical root",
                request.document.root_id,
                request.document.root_relative_path
            );
        }
        if !absolute_path.is_file()
            || absolute_path
                .extension()
                .and_then(|extension| extension.to_str())
                != Some("java")
        {
            eyre::bail!(
                "addressed dependency source `{}`:`{}` is not a Java file",
                request.document.root_id,
                request.document.root_relative_path
            );
        }
        let file = JavaSourceFile {
            absolute_path,
            root_id: root.root_id.clone(),
            root_relative_path: request.document.root_relative_path.clone(),
            report_path: format!(
                "{}/{}",
                root.report_prefix.trim_end_matches('/'),
                request.document.root_relative_path
            ),
            source_set: root.source_set.clone(),
            source_override: None,
        };
        validate_definition_document_projection(&file, request, "dependency-source")?;
        Ok(file)
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
            resolution_types: 0,
            resolution_fields: 0,
            resolution_methods: 0,
            usage_index_build_micros: 0,
            usage_index_cache_hits: 0,
            usage_index_candidate_files: 0,
            usage_index_parsed_files: 0,
            usage_index_retained_rows: 0,
            usage_index_cache_entries: 0,
            usage_index_cache_retained_bytes: 0,
            usage_index_cache_evictions: 0,
            reparsed_files: 0,
            source_files: self.workspace.files.len().try_into().unwrap_or(u64::MAX),
            cache: self.cache_snapshot(),
        }
    }
}

fn java_interaction_semantic_identity(
    request: &JavaInteractionMapRequest,
    surface: &JavaDefinitionResolutionSurface,
) -> (u64, String) {
    let mut hasher = blake3::Hasher::new();
    for value in [
        super::JAVA_INTERACTION_MAP_SCHEMA,
        request.workspace.workspace_fingerprint.as_str(),
        request.document.content_hash.as_str(),
        surface.context().parser_fingerprint.as_str(),
        surface.context().index_fingerprint.as_str(),
    ] {
        hasher.update(&u64::try_from(value.len()).unwrap_or(u64::MAX).to_le_bytes());
        hasher.update(value.as_bytes());
    }
    hasher.update(
        &u64::try_from(surface.files().len())
            .unwrap_or(u64::MAX)
            .to_le_bytes(),
    );
    for file in surface.files() {
        for value in [
            file.report_path.as_str(),
            file.source_set.as_str(),
            file.source_hash.as_str(),
        ] {
            hasher.update(&u64::try_from(value.len()).unwrap_or(u64::MAX).to_le_bytes());
            hasher.update(value.as_bytes());
        }
    }
    let hash = hasher.finalize();
    let mut generation_bytes = [0_u8; 8];
    generation_bytes.copy_from_slice(&hash.as_bytes()[..8]);
    (
        java_wire_generation(generation_bytes),
        format!("blake3:{}", hash.to_hex()),
    )
}

/// Semantic generations cross the Rust/Java wire as non-negative Java longs.
/// The full BLAKE3 fingerprint remains the collision-resistant identity.
fn java_wire_generation(bytes: [u8; 8]) -> u64 {
    u64::from_le_bytes(bytes) & (i64::MAX as u64)
}

fn duration_micros(duration: std::time::Duration) -> u64 {
    duration.as_micros().try_into().unwrap_or(u64::MAX)
}

pub(crate) fn contributed_address(scheme: &str, authority: &str, relative: &str) -> String {
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

    #[test]
    fn java_wire_generations_never_set_the_signed_long_bit() {
        assert_eq!(java_wire_generation([0; 8]), 0);
        assert_eq!(java_wire_generation([u8::MAX; 8]), i64::MAX as u64);
        assert!(java_wire_generation([0, 0, 0, 0, 0, 0, 0, 0x80]) <= i64::MAX as u64);
    }
    use crate::java_analysis::DefinitionDocumentInput;
    use crate::java_analysis::DefinitionRecoveryActionKind;
    use crate::java_analysis::DefinitionTextPositionInput;
    use crate::java_analysis::DefinitionWorkspaceIdentityInput;
    use crate::java_analysis::DependencySymbolIndexProbeStatus;
    use crate::java_analysis::JavaAnalysisContextOutput;
    use crate::java_analysis::JavaClasspathMode;
    use crate::java_analysis::JavaInteractionMapOutcome;
    use crate::java_analysis::JavaSourceRootKind;
    use crate::java_analysis::JavaSourceRootOutput;
    use crate::java_analysis::JavaSourceSetOutput;
    use crate::java_analysis::JavaSourceSpanOutput;
    use crate::java_analysis::JavaSymbolDefinitionOutput;
    use crate::java_analysis::JavaSymbolIdentityOutput;
    use crate::java_analysis::ResolutionConfidence;
    use crate::java_analysis::SymbolQueryCompleteness;
    use crate::java_analysis::UsageAtPositionOutcome;
    use crate::java_analysis::definition_workspace_fingerprint;
    use std::collections::BTreeSet;

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
                jdk_sources: JdkSourceDomainState::Disabled,
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

    fn usage_request(definition: DefinitionAtPositionRequest) -> UsageAtPositionRequest {
        UsageAtPositionRequest::new(
            definition.request_id,
            definition.request_generation,
            definition.workspace,
            definition.document,
            definition.position,
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
            path: format!("C:/fixture/symbol-index/v3/{identity}"),
            reason: format!("fixture status: {status:?}"),
            refresh_command: "sfm-propagate-changes symbol index refresh --branch 1.19.2"
                .to_owned(),
            acquisition_commands: vec![
                "sfm-propagate-changes dependency source acquire minecraft --branch 1.19.2"
                    .to_owned(),
            ],
        }
    }

    #[test]
    fn implicit_java_lang_types_resolve_to_source_backed_jdk_definitions() {
        let source = concat!(
            "package q;\n",
            "class Use { String text; Object value; StringBuilder builder; }\n",
        );
        let (_workspace_directory, mut workspace) =
            workspace_from_sources(&[("q/Use.java", source)]);
        let jdk_directory = tempfile::tempdir().expect("synthetic JDK source tree");
        for (name, declaration) in [
            ("String", "public final class String {}"),
            ("Object", "public class Object {}"),
            ("StringBuilder", "public final class StringBuilder {}"),
        ] {
            let path = jdk_directory
                .path()
                .join("java.base")
                .join("java")
                .join("lang")
                .join(format!("{name}.java"));
            std::fs::create_dir_all(path.parent().expect("JDK package")).expect("JDK package");
            std::fs::write(&path, format!("package java.lang; {declaration}\n"))
                .expect("JDK source");
        }
        workspace.jdk_sources = JdkSourceDomainState::ready_from_tree("17", jdk_directory.path())
            .expect("JDK source domain");
        workspace
            .jdk_sources
            .apply_to_context(&mut workspace.context);
        let engine = DefinitionAtPositionEngine::new(
            workspace.clone(),
            None,
            None,
            DefinitionAtPositionEngineLimits::default(),
        )
        .expect("definition engine");

        for name in ["String", "Object", "StringBuilder"] {
            let result = engine
                .analyze(
                    &request_at(&workspace, "q/Use.java", source, 1, name),
                    &CancellationToken::new(),
                )
                .expect("JDK definition");

            assert_eq!(result.outcome, DefinitionAtPositionOutcome::Success);
            assert_eq!(
                result.symbols[0].canonical_selector(),
                format!("java.lang.{name}")
            );
            assert_eq!(
                result.definitions[0].identifier_span.resolver_id,
                "jdk-source"
            );
            assert!(
                result.definitions[0]
                    .identifier_span
                    .root_relative_path
                    .ends_with(&format!("java/lang/{name}.java"))
            );
            assert!(
                result.definitions[0]
                    .identifier_span
                    .source_sha256
                    .is_some()
            );
        }
    }

    #[test]
    fn interaction_map_accepts_only_exact_acquired_dependency_source_authority() {
        let (_workspace_directory, workspace) = workspace();
        let dependency_directory = tempfile::tempdir().expect("dependency source root");
        let relative = "dep/External.java";
        let source = "package dep; public class External { int value; }\n";
        let path = dependency_directory.path().join(relative);
        std::fs::create_dir_all(path.parent().expect("dependency package"))
            .expect("dependency package");
        std::fs::write(&path, source).expect("dependency source");
        let root = DefinitionDependencySourceRoot {
            root_id: "dependency-source-fixture".to_owned(),
            report_prefix: "dependency/fixture".to_owned(),
            source_set: "dependency:fixture".to_owned(),
            canonical_absolute_path: dunce::canonicalize(dependency_directory.path())
                .expect("canonical dependency source root"),
        };
        let workspace_fingerprint = definition_workspace_fingerprint(&workspace.context, None)
            .expect("fixture workspace fingerprint");
        let document = DefinitionDocumentInput {
            address: contributed_address("dependency-source", &root.root_id, relative),
            root_id: root.root_id.clone(),
            root_relative_path: relative.to_owned(),
            report_path: format!("{}/{relative}", root.report_prefix),
            source_set: root.source_set.clone(),
            text: source.to_owned(),
            content_hash: blake3_content_hash(source),
            disk_content_hash: Some(blake3_content_hash(source)),
        };
        let request = JavaInteractionMapRequest::new(
            71,
            3,
            DefinitionWorkspaceIdentityInput {
                branch: workspace.context.branch.clone(),
                classpath_mode: workspace.context.classpath_mode,
                source_roots: workspace.context.source_roots.clone(),
                classpath_fingerprint: workspace.context.classpath_fingerprint.clone(),
                dependency_index_identity: None,
                workspace_fingerprint,
                workspace_generation: 9,
            },
            document,
        );
        request
            .validate()
            .expect("portable dependency-source request");
        let engine = DefinitionAtPositionEngine::new_with_dependency_source_roots(
            workspace,
            None,
            None,
            vec![root],
            DefinitionAtPositionEngineLimits::default(),
        )
        .expect("dependency-source engine");

        let result = engine
            .analyze_interaction_map(&request, &CancellationToken::new())
            .expect("dependency-source interaction map");

        assert_eq!(result.outcome, JavaInteractionMapOutcome::Success);
        assert_eq!(result.document.root_id, "dependency-source-fixture");
        assert!(
            result
                .regions
                .iter()
                .any(|region| region.semantic_kind == "java-class-declaration")
        );

        let mut forged = request;
        forged.request_id += 1;
        forged.document.report_path = "dependency/other/dep/External.java".to_owned();
        let rejected = engine
            .analyze_interaction_map(&forged, &CancellationToken::new())
            .expect("forged request remains a typed result");
        assert_eq!(rejected.outcome, JavaInteractionMapOutcome::InvalidRequest);
        assert!(rejected.diagnostics.iter().any(|diagnostic| {
            diagnostic
                .message
                .contains("report path does not match the resolved source file")
        }));
    }

    #[test]
    fn usage_at_position_returns_persistent_addressable_project_references() {
        let use_source = "package q; import p.A; class Use { A first; A second; }\n";
        let other_source = "package r; import p.A; class Other { A value; }\n";
        let (_directory, workspace) = workspace_from_sources(&[
            ("p/A.java", "package p; public class A {}\n"),
            ("q/Use.java", use_source),
            ("r/Other.java", other_source),
        ]);
        let engine = DefinitionAtPositionEngine::new(
            workspace.clone(),
            None,
            None,
            DefinitionAtPositionEngineLimits::default(),
        )
        .expect("usage engine");
        let mut definition_request = request_at(&workspace, "q/Use.java", use_source, 1, "A first");
        definition_request.document.disk_content_hash =
            Some(definition_request.document.content_hash.clone());
        let usage_request = usage_request(definition_request.clone());

        let definition = engine
            .analyze_with_telemetry(&definition_request, &CancellationToken::new())
            .expect("definition result");
        assert_eq!(
            definition.result.outcome,
            DefinitionAtPositionOutcome::Success
        );
        let usages = engine
            .analyze_usages_with_telemetry(&usage_request, &CancellationToken::new())
            .expect("usage result");

        assert_eq!(usages.result.outcome, UsageAtPositionOutcome::Success);
        assert_eq!(usages.result.targets[0].canonical_selector(), "p.A");
        assert!(usages.telemetry.resolution_cache_hits >= 1);
        assert!(usages.telemetry.usage_index_build_micros > 0);
        assert_eq!(usages.telemetry.usage_index_cache_hits, 0);
        let paths = usages
            .result
            .usages
            .iter()
            .map(|usage| usage.span.report_path.as_str())
            .collect::<BTreeSet<_>>();
        assert!(paths.contains("source/p/A.java"));
        assert!(paths.contains("source/q/Use.java"));
        assert!(paths.contains("source/r/Other.java"));
        assert!(usages.result.usages.iter().all(|usage| {
            usage.span.resolver_id == "workspace"
                && usage.span.address.starts_with("workspace://")
                && usage.span.source_sha256.is_some()
        }));

        let repeated = engine
            .analyze_usages_with_telemetry(&usage_request, &CancellationToken::new())
            .expect("warm usage result");
        assert_eq!(repeated.result, usages.result);
        assert_eq!(repeated.telemetry.reparsed_files, 0);
        assert_eq!(repeated.telemetry.usage_index_build_micros, 0);
        assert_eq!(repeated.telemetry.usage_index_cache_hits, 1);
    }

    #[test]
    fn concurrent_cold_requests_single_flight_one_resolution_surface() {
        let mut owned_sources = vec![
            (
                "p/A.java".to_owned(),
                "package p; public class A {}\n".to_owned(),
            ),
            (
                "q/Use.java".to_owned(),
                "package q; import p.A; class Use { A value; }\n".to_owned(),
            ),
        ];
        // Keep the first build in flight long enough for both request threads
        // to reach the single-flight gate deterministically on ordinary CI
        // machines without adding a test-only production hook.
        for index in 0..128 {
            owned_sources.push((
                format!("bulk/C{index}.java"),
                format!("package bulk; class C{index} {{ int value{index}; }}\n"),
            ));
        }
        let borrowed_sources = owned_sources
            .iter()
            .map(|(path, source)| (path.as_str(), source.as_str()))
            .collect::<Vec<_>>();
        let (_directory, workspace) = workspace_from_sources(&borrowed_sources);
        let engine = DefinitionAtPositionEngine::new(
            workspace.clone(),
            None,
            None,
            DefinitionAtPositionEngineLimits::default(),
        )
        .expect("definition engine");
        let request = request_at(
            &workspace,
            "q/Use.java",
            "package q; import p.A; class Use { A value; }\n",
            1,
            "A value",
        );
        let ready = std::sync::Barrier::new(3);

        std::thread::scope(|scope| {
            let first = scope.spawn(|| {
                ready.wait();
                engine.analyze(&request, &CancellationToken::new())
            });
            let second = scope.spawn(|| {
                ready.wait();
                engine.analyze(&request, &CancellationToken::new())
            });
            ready.wait();
            assert_eq!(
                first
                    .join()
                    .expect("first request thread")
                    .expect("first request")
                    .outcome,
                DefinitionAtPositionOutcome::Success
            );
            assert_eq!(
                second
                    .join()
                    .expect("second request thread")
                    .expect("second request")
                    .outcome,
                DefinitionAtPositionOutcome::Success
            );
        });

        let resolution_cache = engine
            .resolution_cache
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        assert_eq!(resolution_cache.entries.len(), 1);
        assert_eq!(resolution_cache.misses, 1);
        assert_eq!(resolution_cache.hits, 1);
        drop(resolution_cache);
        let facts = engine.cache_snapshot();
        assert_eq!(facts.misses, u64::try_from(workspace.files.len()).unwrap());
    }

    #[test]
    fn usage_at_position_replaces_only_the_current_disk_file_with_an_unsaved_overlay() {
        let disk_source = "package q; import p.A; class Use { A diskFirst; A diskSecond; }\n";
        let overlay_source = "package q; import p.A; class Use { A overlayOnly; }\n";
        let other_source = "package r; import p.A; class Other { A retained; }\n";
        let (_directory, workspace) = workspace_from_sources(&[
            ("p/A.java", "package p; public class A {}\n"),
            ("q/Use.java", disk_source),
            ("r/Other.java", other_source),
        ]);
        let engine = DefinitionAtPositionEngine::new(
            workspace.clone(),
            None,
            None,
            DefinitionAtPositionEngineLimits::default(),
        )
        .expect("usage engine");
        let mut definition_request =
            request_at(&workspace, "q/Use.java", overlay_source, 1, "A overlayOnly");
        definition_request.document.disk_content_hash = Some(blake3_content_hash(disk_source));
        let usage_request = usage_request(definition_request);

        let usages = engine
            .analyze_usages_with_telemetry(&usage_request, &CancellationToken::new())
            .expect("overlay usage result");

        assert_eq!(usages.result.outcome, UsageAtPositionOutcome::Success);
        assert_eq!(usages.result.targets[0].canonical_selector(), "p.A");
        let overlay_hash = blake3_content_hash(overlay_source);
        let current_document_usages = usages
            .result
            .usages
            .iter()
            .filter(|usage| usage.span.report_path == "source/q/Use.java")
            .collect::<Vec<_>>();
        assert!(!current_document_usages.is_empty());
        assert!(
            current_document_usages
                .iter()
                .all(|usage| usage.span.source_hash == overlay_hash)
        );
        assert!(
            usages
                .result
                .usages
                .iter()
                .any(|usage| usage.span.report_path == "source/r/Other.java")
        );
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
