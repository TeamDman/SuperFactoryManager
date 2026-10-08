//! Pure strict-request foundation for the exact released schema-2 inputs.
//!
//! The opt-in `Resolver` constructor consumes this boundary. The native planner
//! and `NeoForm` external process have not adopted it yet.
//! This module performs no I/O, acquisition, cache creation or lock writing.
//! It retains original ordered rows and provenance, uses secondary indexes only
//! for lookup, and never invents a schema-4 profile or a POM parent-edge graph.
//! Named-project identity/rechecks and external-tool refusal remain integration
//! gates; success here is input preparation, not build/run acceptance.

use super::provenance::sha256;
use super::released_native_inputs::ReleasedNativeInputs;
use super::released_native_inputs::ReleasedPreparedDependency;
use crate::jar_build::ArtifactSource;
use crate::jar_build::DependencyLockEntry;
use crate::jar_build::hash::ContentHash;
use crate::toolchain_lockfile_schema::version::v2::ArtifactLockEntryV2;
use crate::toolchain_lockfile_schema::version::v2::ArtifactLockfileV2;
use eyre::Result;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;

const MAX_REQUEST_BYTES: usize = 4096;
const MAX_ARTIFACT_WITNESS_BYTES: usize = 512 * 1024 * 1024;

/// Evidence for this pure input boundary, not native execution capability.
#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub struct PreparedDependencyReceipt {
    pub schema: String,
    pub target_id: String,
    pub minecraft_version: String,
    pub recipe_id: String,
    pub source_lock_sha256: String,
    pub source_lock_bytes: usize,
    pub ordered_dependency_roles_sha256: String,
    pub source_exclusions_sha256: String,
    pub request_catalog_identity: String,
    pub original_dependency_rows: usize,
    pub supplemental_dependency_rows: usize,
    pub artifact_pins: usize,
    pub eager_coordinate_keys: usize,
    pub exact_transport_url_keys: usize,
    pub dynamic_alias_keys: usize,
    pub dynamic_alias_original_rows: usize,
    pub frozen_runtime_rows: usize,
    pub unexpanded_pipeline_roles: usize,
    pub immutable_source_lock: bool,
    pub live_pom_discovery: bool,
    pub compilation: String,
}

/// Multiple original configurations may share one exact dynamic resolution.
/// Original indexes remain ordered; merging this lookup does not merge rows.
#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub struct FrozenDynamicAlias {
    pub requested_coordinate: String,
    pub resolved_coordinate: String,
    pub original_artifact_index: usize,
    pub original_dependency_indexes: Vec<usize>,
}

/// One eligible row in the exact prepared runtime-root sequence.
///
/// Parent edges were not recorded in schema 2. This identifies the complete
/// selected input set for its recorded global closure, not a per-root graph.
#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub struct FrozenRuntimeRoot {
    pub prepared_dependency_index: usize,
    pub original_dependency_index: Option<usize>,
    pub configuration: String,
    pub resolved_coordinate: String,
}

/// A catalog-bound artifact request. Fields are private to prevent callers
/// from substituting a hash, URL, source path or artifact index.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct PreparedArtifactRequest {
    catalog_identity: String,
    artifact_index: usize,
    resolved_coordinate: Option<String>,
    transport_url: Option<String>,
    original_content_hash: ContentHash,
}

impl PreparedArtifactRequest {
    #[must_use]
    pub fn original_artifact_index(&self) -> usize {
        self.artifact_index
    }

    #[must_use]
    pub fn resolved_coordinate(&self) -> Option<&str> {
        self.resolved_coordinate.as_deref()
    }

    #[must_use]
    pub fn exact_transport_url(&self) -> Option<&str> {
        self.transport_url.as_deref()
    }

    #[must_use]
    pub fn original_content_hash(&self) -> String {
        self.original_content_hash.to_string()
    }
}

/// Immutable lossless source data with an eager strict request catalog.
///
/// Construction requires `ReleasedNativeInputs`, whose promotion has already
/// verified every weak artifact against its original exact content hash.
/// Secondary maps do not sort, deduplicate or rewrite original source rows.
#[derive(Clone, Debug)]
pub struct PreparedDependencyInputs {
    released: ReleasedNativeInputs,
    catalog: StrictRequestCatalog,
    runtime_roots: Vec<FrozenRuntimeRoot>,
    runtime_indexes: Vec<usize>,
    receipt: PreparedDependencyReceipt,
}

impl PreparedDependencyInputs {
    /// Prepare an exact schema-2 request boundary, before any effects.
    ///
    /// # Errors
    /// Rejects refresh first, unavailable/ambiguous identities, incomplete
    /// original-row bindings, unverified weak pins and changed source evidence.
    /// No source-lock writer or acquisition route is exposed.
    #[tracing::instrument(name = "prepared_dependencies.from_released", skip_all)]
    pub fn from_released(released: ReleasedNativeInputs, refresh: bool) -> Result<Self> {
        refuse_refresh(refresh)?;
        let source_receipt = released.receipt();
        let raw = released.raw_source_lock_bytes();
        let lock = released.original_lock();
        ensure!(
            lock.schema_version == 2
                && !lock.allow_local_artifact_cache
                && source_receipt.immutable_source_lock
                && source_receipt.strict_external_hashes
                && source_receipt.source_lock_sha256 == sha256(raw)
                && lock.minecraft_version == source_receipt.minecraft_version,
            "prepared released input lost immutable schema-2 source identity"
        );
        verify_weak_promotions(lock, &source_receipt.exact_bytes_verified)?;
        let catalog = StrictRequestCatalog::from_ordered_lock(lock)?;
        check_prepared_row_bindings(lock, released.dependencies(), &catalog)?;
        let runtime_roots = runtime_root_sequence(released.dependencies());
        let runtime_indexes = released
            .dependencies()
            .iter()
            .enumerate()
            .filter(|(_, row)| row.configuration == "transitiveRuntime")
            .map(|(index, _)| index)
            .collect::<Vec<_>>();
        let roles_sha256 = sha256(facet_json::to_string(released.dependencies())?.as_bytes());
        let exclusions_sha256 =
            sha256(facet_json::to_string(released.source_excludes())?.as_bytes());
        let catalog_identity = framed_identity(&[
            b"sfm:released_strict_request_catalog@1",
            source_receipt.source_lock_sha256.as_bytes(),
            source_receipt.recipe_review_sha256.as_bytes(),
            source_receipt.recipe_id.as_bytes(),
            roles_sha256.as_bytes(),
            exclusions_sha256.as_bytes(),
        ]);
        let receipt = PreparedDependencyReceipt {
            schema: "sfm:prepared_dependency_inputs@1".to_owned(),
            target_id: source_receipt.target_id.clone(),
            minecraft_version: source_receipt.minecraft_version.clone(),
            recipe_id: source_receipt.recipe_id.clone(),
            source_lock_sha256: source_receipt.source_lock_sha256.clone(),
            source_lock_bytes: raw.len(),
            ordered_dependency_roles_sha256: roles_sha256,
            source_exclusions_sha256: exclusions_sha256,
            request_catalog_identity: catalog_identity,
            original_dependency_rows: lock.dependencies.len(),
            supplemental_dependency_rows: released
                .dependencies()
                .iter()
                .filter(|row| row.original_dependency_index.is_none())
                .count(),
            artifact_pins: lock.artifacts.len(),
            eager_coordinate_keys: catalog.coordinates.len(),
            exact_transport_url_keys: catalog.transport_urls.len(),
            dynamic_alias_keys: catalog.dynamic_aliases.len(),
            dynamic_alias_original_rows: catalog
                .dynamic_aliases
                .values()
                .map(|alias| alias.original_dependency_indexes.len())
                .sum(),
            frozen_runtime_rows: runtime_indexes.len(),
            unexpanded_pipeline_roles: source_receipt.unexpanded_pipeline_roles,
            immutable_source_lock: true,
            live_pom_discovery: false,
            compilation: "not_performed".to_owned(),
        };
        Ok(Self {
            released,
            catalog,
            runtime_roots,
            runtime_indexes,
            receipt,
        })
    }

    #[must_use]
    pub fn receipt(&self) -> &PreparedDependencyReceipt {
        &self.receipt
    }

    #[must_use]
    pub fn raw_source_lock_bytes(&self) -> &[u8] {
        self.released.raw_source_lock_bytes()
    }

    /// Immutable original row/provenance view, never a writer input.
    #[must_use]
    pub(crate) fn original_lock(&self) -> &ArtifactLockfileV2 {
        self.released.original_lock()
    }

    #[must_use]
    pub fn dependencies(&self) -> &[ReleasedPreparedDependency] {
        self.released.dependencies()
    }

    #[must_use]
    pub fn source_exclusions(&self) -> &BTreeMap<String, Vec<String>> {
        self.released.source_excludes()
    }

    /// Verify exact original bytes at an integration gate, without writing.
    ///
    /// # Errors
    /// Rejects refresh or changed bytes, including semantically equivalent JSON
    /// serialization. This is not a lock held across later I/O or execution.
    pub fn verify_source_lock_bytes(&self, current: &[u8], refresh: bool) -> Result<()> {
        refuse_refresh(refresh)?;
        ensure!(
            current == self.raw_source_lock_bytes()
                && sha256(current) == self.receipt.source_lock_sha256,
            "prepared immutable source-lock bytes changed; recheck the exact named inputs before effects"
        );
        Ok(())
    }

    /// Look up only an exact pinned coordinate or recorded dynamic alias.
    ///
    /// # Errors
    /// Rejects refresh, malformed/unknown requests or unrecorded dynamic forms
    /// before callers can perform cache, provenance or network work.
    pub fn coordinate(&self, requested: &str, refresh: bool) -> Result<PreparedArtifactRequest> {
        refuse_refresh(refresh)?;
        self.request(self.catalog.coordinate_index(requested)?)
    }

    /// Look up an exact recorded transport URL; no normalization or discovery.
    ///
    /// # Errors
    /// Rejects refresh, unknown URLs and provenance-only source repository URLs.
    pub fn exact_url(&self, url: &str, refresh: bool) -> Result<PreparedArtifactRequest> {
        refuse_refresh(refresh)?;
        self.request(self.catalog.transport_url_index(url)?)
    }

    /// Keep every original alias row visible even when resolutions coincide.
    #[must_use]
    pub fn dynamic_aliases(&self) -> Vec<&FrozenDynamicAlias> {
        self.catalog.dynamic_aliases.values().collect()
    }

    #[must_use]
    pub fn runtime_roots(&self) -> &[FrozenRuntimeRoot] {
        &self.runtime_roots
    }

    /// Return the recorded ordered global closure for the exact selected roots.
    ///
    /// No schema-2 parent edges exist. Integrate this at the planner's global
    /// transitive-expansion hook, not as fabricated per-root POM results. An
    /// empty result means the exact frozen input set records no additional rows.
    ///
    /// # Errors
    /// Rejects refresh and any removed, added, reordered or changed root row.
    pub fn frozen_runtime_rows_for(
        &self,
        roots: &[FrozenRuntimeRoot],
        refresh: bool,
    ) -> Result<Vec<&ReleasedPreparedDependency>> {
        refuse_refresh(refresh)?;
        ensure!(
            roots == self.runtime_roots.as_slice(),
            "runtime root sequence differs from the frozen prepared set; live POM discovery and inferred parent edges are unavailable"
        );
        Ok(self
            .runtime_indexes
            .iter()
            .map(|index| &self.dependencies()[*index])
            .collect())
    }

    /// Validate acquisition bytes against the original exact frozen pin.
    ///
    /// # Errors
    /// Rejects refresh, cross-catalog/forged requests, empty/oversized bytes and
    /// mismatches. Weak mod metadata is never a byte-identity substitute.
    pub fn verify_artifact_bytes(
        &self,
        request: &PreparedArtifactRequest,
        bytes: &[u8],
        refresh: bool,
    ) -> Result<()> {
        refuse_refresh(refresh)?;
        let pin = self.artifact_pin(request)?;
        ensure!(
            !bytes.is_empty() && bytes.len() <= MAX_ARTIFACT_WITNESS_BYTES,
            "prepared artifact byte evidence is empty or exceeds its bounded limit"
        );
        let actual = ContentHash::from_bytes(bytes, pin.hash.algorithm);
        ensure!(
            actual == pin.hash,
            "prepared artifact {} exact-byte mismatch: actual {}, original frozen pin {}; weak metadata cannot override it",
            request.artifact_index,
            actual,
            pin.hash
        );
        Ok(())
    }

    /// Read the exact original provenance, including source-relative/build data.
    ///
    /// # Errors
    /// Rejects a request from another source/context or an altered identity.
    pub(crate) fn artifact_pin(
        &self,
        request: &PreparedArtifactRequest,
    ) -> Result<&ArtifactLockEntryV2> {
        ensure!(
            request.catalog_identity == self.receipt.request_catalog_identity,
            "artifact request belongs to another prepared source/context"
        );
        let pin = self
            .original_lock()
            .artifacts
            .get(request.artifact_index)
            .ok_or_else(|| eyre::eyre!("prepared artifact index is unavailable"))?;
        ensure!(
            request.resolved_coordinate == pin.coordinate
                && request.transport_url.as_deref() == transport_url(pin)
                && request.original_content_hash == pin.hash,
            "artifact request differs from the original frozen pin"
        );
        Ok(pin)
    }

    fn request(&self, index: usize) -> Result<PreparedArtifactRequest> {
        let pin = self
            .original_lock()
            .artifacts
            .get(index)
            .ok_or_else(|| eyre::eyre!("prepared artifact index is unavailable"))?;
        Ok(PreparedArtifactRequest {
            catalog_identity: self.receipt.request_catalog_identity.clone(),
            artifact_index: index,
            resolved_coordinate: pin.coordinate.clone(),
            transport_url: transport_url(pin).map(str::to_owned),
            original_content_hash: pin.hash,
        })
    }
}

/// Eager secondary indexes only. Original records remain in their source order.
#[derive(Clone, Debug)]
struct StrictRequestCatalog {
    coordinates: BTreeMap<String, usize>,
    transport_urls: BTreeMap<String, usize>,
    provenance_urls: BTreeSet<String>,
    dynamic_aliases: BTreeMap<String, FrozenDynamicAlias>,
}

impl StrictRequestCatalog {
    fn from_ordered_lock(lock: &ArtifactLockfileV2) -> Result<Self> {
        let mut catalog = Self {
            coordinates: BTreeMap::new(),
            transport_urls: BTreeMap::new(),
            provenance_urls: BTreeSet::new(),
            dynamic_aliases: BTreeMap::new(),
        };
        for (index, pin) in lock.artifacts.iter().enumerate() {
            catalog.index_artifact(pin, index)?;
        }
        for (index, row) in lock.dependencies.iter().enumerate() {
            catalog.index_dependency(lock, row, index)?;
        }
        Ok(catalog)
    }

    fn index_artifact(&mut self, pin: &ArtifactLockEntryV2, index: usize) -> Result<()> {
        ensure!(
            matches!(
                pin.source,
                ArtifactSource::RemoteMaven
                    | ArtifactSource::RemoteHttp
                    | ArtifactSource::SourceBuild
            ),
            "artifact {index} has no reviewed strict acquisition source"
        );
        if let Some(coordinate) = &pin.coordinate {
            check_coordinate(coordinate, false)?;
            ensure!(
                self.coordinates.insert(coordinate.clone(), index).is_none(),
                "ambiguous pinned coordinate: {coordinate}"
            );
        }
        if let Some(url) = transport_url(pin) {
            check_url(url)?;
            ensure!(
                self.transport_urls.insert(url.to_owned(), index).is_none(),
                "ambiguous pinned transport URL: {url}"
            );
        } else {
            ensure!(
                pin.source == ArtifactSource::SourceBuild && pin.coordinate.is_some(),
                "artifact {index} has no exact transport URL or source-build coordinate"
            );
            if let Some(url) = &pin.url {
                check_url(url)?;
                self.provenance_urls.insert(url.clone());
            }
        }
        Ok(())
    }

    fn index_dependency(
        &mut self,
        lock: &ArtifactLockfileV2,
        row: &DependencyLockEntry,
        index: usize,
    ) -> Result<()> {
        check_coordinate(&row.notation, row.dynamic_version)?;
        check_coordinate(&row.resolved_notation, false)?;
        let pin_index = self
            .coordinates
            .get(&row.resolved_notation)
            .copied()
            .ok_or_else(|| eyre::eyre!("dependency row {index} has no exact pinned resolution"))?;
        ensure!(
            lock.artifacts[pin_index].cache_path == row.cache_path,
            "dependency row {index} cache binding differs from its original artifact"
        );
        if row.dynamic_version {
            ensure!(
                row.notation.contains('+') && row.notation != row.resolved_notation,
                "dependency row {index} has an unreviewed dynamic form"
            );
            match self.dynamic_aliases.get_mut(&row.notation) {
                Some(alias) => {
                    ensure!(
                        alias.resolved_coordinate == row.resolved_notation
                            && alias.original_artifact_index == pin_index,
                        "ambiguous dynamic alias: {}",
                        row.notation
                    );
                    alias.original_dependency_indexes.push(index);
                }
                None => {
                    self.dynamic_aliases.insert(
                        row.notation.clone(),
                        FrozenDynamicAlias {
                            requested_coordinate: row.notation.clone(),
                            resolved_coordinate: row.resolved_notation.clone(),
                            original_artifact_index: pin_index,
                            original_dependency_indexes: vec![index],
                        },
                    );
                }
            }
        } else {
            ensure!(
                row.notation == row.resolved_notation,
                "dependency row {index} has an unrecorded non-dynamic alias"
            );
        }
        Ok(())
    }

    fn coordinate_index(&self, requested: &str) -> Result<usize> {
        check_coordinate(requested, true)?;
        if let Some(alias) = self.dynamic_aliases.get(requested) {
            return Ok(alias.original_artifact_index);
        }
        self.coordinates.get(requested).copied().ok_or_else(|| {
            eyre::eyre!(
                "coordinate is outside the frozen eager request catalog: {requested}; no dynamic discovery or unpinned fallback is permitted"
            )
        })
    }

    fn transport_url_index(&self, url: &str) -> Result<usize> {
        check_url(url)?;
        ensure!(
            !self.provenance_urls.contains(url),
            "source repository URL is provenance, not an artifact transport request: {url}"
        );
        self.transport_urls.get(url).copied().ok_or_else(|| {
            eyre::eyre!(
                "URL is outside the exact frozen transport catalog: {url}; no URL normalization or metadata rediscovery is permitted"
            )
        })
    }
}

fn refuse_refresh(refresh: bool) -> Result<()> {
    ensure!(
        !refresh,
        "prepared released dependencies are immutable; refresh is refused before any effects"
    );
    Ok(())
}

fn transport_url(pin: &ArtifactLockEntryV2) -> Option<&str> {
    if matches!(
        pin.source,
        ArtifactSource::RemoteMaven | ArtifactSource::RemoteHttp
    ) {
        pin.url.as_deref()
    } else {
        None
    }
}

fn verify_weak_promotions(
    lock: &ArtifactLockfileV2,
    verified: &BTreeMap<usize, String>,
) -> Result<()> {
    let weak = lock
        .artifacts
        .iter()
        .enumerate()
        .filter(|(_, pin)| pin.weak.is_some())
        .map(|(index, _)| index)
        .collect::<BTreeSet<_>>();
    ensure!(
        verified.keys().copied().collect::<BTreeSet<_>>() == weak,
        "weak exact-byte promotion set differs from the original frozen requirements"
    );
    for index in weak {
        ensure!(
            verified[&index] == lock.artifacts[index].hash.to_string(),
            "weak artifact {index} has no original-pin exact-byte promotion"
        );
    }
    Ok(())
}

fn check_prepared_row_bindings(
    lock: &ArtifactLockfileV2,
    rows: &[ReleasedPreparedDependency],
    catalog: &StrictRequestCatalog,
) -> Result<()> {
    let mut original_indexes = Vec::new();
    for row in rows {
        let index = catalog.coordinate_index(&row.resolved_coordinate)?;
        let pin = &lock.artifacts[index];
        ensure!(
            index == row.artifact_index
                && pin.hash.to_string() == row.artifact_hash
                && catalog.coordinate_index(&row.requested_coordinate)? == index,
            "prepared dependency role differs from its strict original binding"
        );
        if let Some(original_index) = row.original_dependency_index {
            let original = lock
                .dependencies
                .get(original_index)
                .ok_or_else(|| eyre::eyre!("prepared original dependency index is unavailable"))?;
            ensure!(
                original.configuration == row.configuration
                    && original.notation == row.requested_coordinate
                    && original.resolved_notation == row.resolved_coordinate,
                "prepared dependency row differs from its original ordered row"
            );
            original_indexes.push(original_index);
        } else {
            ensure!(
                row.configuration != "transitiveRuntime",
                "transitive runtime row cannot be an invented supplement"
            );
        }
    }
    ensure!(
        original_indexes == (0..lock.dependencies.len()).collect::<Vec<_>>(),
        "prepared dependency roles lost, reordered or duplicated original rows"
    );
    Ok(())
}

fn runtime_root_sequence(rows: &[ReleasedPreparedDependency]) -> Vec<FrozenRuntimeRoot> {
    rows.iter()
        .enumerate()
        .filter(|(_, row)| {
            matches!(
                row.configuration.as_str(),
                "implementation" | "runtimeOnly" | "gametestImplementation" | "gametestRuntimeOnly"
            )
        })
        .map(|(index, row)| FrozenRuntimeRoot {
            prepared_dependency_index: index,
            original_dependency_index: row.original_dependency_index,
            configuration: row.configuration.clone(),
            resolved_coordinate: row.resolved_coordinate.clone(),
        })
        .collect()
}

fn check_coordinate(coordinate: &str, dynamic_allowed: bool) -> Result<()> {
    check_request_text(coordinate)?;
    let (body, extension) = coordinate
        .split_once('@')
        .map_or((coordinate, None), |(body, extension)| {
            (body, Some(extension))
        });
    let parts = body.split(':').collect::<Vec<_>>();
    ensure!(
        matches!(parts.len(), 3 | 4)
            && parts.iter().all(|part| !part.is_empty())
            && extension.is_none_or(|extension| !extension.is_empty() && !extension.contains('@'))
            && !coordinate.contains(['/', '\\', '[', ']', '(', ')'])
            // '+' inside a pinned Maven version is ordinary text (for example
            // Fabric's "0.13.1+mixin.0.8.5"). Only a trailing version '+' is
            // the reviewed Gradle dynamic form, which requires an exact alias.
            && (dynamic_allowed || !parts[2].ends_with('+')),
        "malformed or non-exact prepared coordinate: {coordinate}"
    );
    Ok(())
}

fn check_url(url: &str) -> Result<()> {
    check_request_text(url)?;
    ensure!(
        url.starts_with("https://") && url.len() > "https://".len() && !url.contains('\\'),
        "prepared transport/provenance URL is not an exact HTTPS request"
    );
    Ok(())
}

fn check_request_text(value: &str) -> Result<()> {
    ensure!(
        !value.is_empty()
            && value.len() <= MAX_REQUEST_BYTES
            && !value
                .chars()
                .any(|character| character.is_control() || character.is_whitespace()),
        "prepared request is empty, oversized or contains control/whitespace characters"
    );
    Ok(())
}

fn framed_identity(parts: &[&[u8]]) -> String {
    let mut framed = Vec::new();
    for part in parts {
        framed.extend_from_slice(&(part.len() as u64).to_be_bytes());
        framed.extend_from_slice(part);
    }
    sha256(&framed)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::source_projection::candidate_lock::checked_file;
    use crate::source_projection::core_slice_test_support::read_bounded;
    use crate::source_projection::released_native_inputs::ReleasedNativeRequest;
    use crate::source_projection::released_native_inputs::prepare_released_native_inputs;
    use crate::source_projection::released_native_inputs::review_released_native_inputs;
    use crate::toolchain_lockfile_schema::ToolchainLockfileDocument;
    use crate::toolchain_lockfile_schema::parse_document;
    use std::cell::Cell;
    use std::path::Path;

    const FIXTURE_REVIEW: &str =
        include_str!("../../../../../docs/tasks/sfm-core-released-native-adapter-review.json");

    #[derive(Debug, Facet)]
    struct Fixtures {
        targets: Vec<Recipe>,
    }

    #[derive(Debug, Facet)]
    struct Recipe {
        target: String,
        minecraft_version: String,
        recipe_id: String,
        platform: Platform,
        source_lock: LockWitness,
        role_input_hashes: Vec<InputWitness>,
    }

    #[derive(Debug, Facet)]
    struct Platform {
        java_major: u16,
    }

    #[derive(Debug, Facet)]
    struct LockWitness {
        path: String,
        sha256: String,
        bytes: usize,
    }

    #[derive(Debug, Facet)]
    struct InputWitness {
        path: String,
    }

    type Fixture = (Vec<u8>, BTreeMap<String, Vec<u8>>);

    fn recipes() -> Result<Vec<Recipe>> {
        Ok(facet_json::from_str::<Fixtures>(FIXTURE_REVIEW)?.targets)
    }

    fn recipe(target: &str) -> Result<Recipe> {
        recipes()?
            .into_iter()
            .find(|recipe| recipe.target == target)
            .ok_or_else(|| eyre::eyre!("fixture target missing"))
    }

    fn fixture(recipe: &Recipe) -> Result<Fixture> {
        let repository = Path::new(env!("CARGO_MANIFEST_DIR"))
            .ancestors()
            .nth(3)
            .ok_or_else(|| eyre::eyre!("CLI crate must be below repository"))?;
        let raw = read_bounded(
            &checked_file(repository, &recipe.source_lock.path)?,
            1024 * 1024,
        )?;
        ensure!(
            raw.len() == recipe.source_lock.bytes
                && sha256(&raw) == format!("sha256:{}", recipe.source_lock.sha256),
            "fixture raw lock changed"
        );
        let inputs =
            super::super::core_release_project_role_fixtures::release_project_role_outputs(
                repository,
                &recipe.target,
                &recipe
                    .role_input_hashes
                    .iter()
                    .map(|input| input.path.clone())
                    .collect(),
            )?;
        Ok((raw, inputs))
    }

    fn request(recipe: &Recipe) -> ReleasedNativeRequest<'_> {
        ReleasedNativeRequest {
            target_id: &recipe.target,
            minecraft_version: &recipe.minecraft_version,
            java_major: recipe.platform.java_major,
            recipe_id: &recipe.recipe_id,
            refresh: false,
        }
    }

    fn prepared(target: &str) -> Result<PreparedDependencyInputs> {
        let recipe = recipe(target)?;
        let (raw, inputs) = fixture(&recipe)?;
        PreparedDependencyInputs::from_released(
            prepare_released_native_inputs(&request(&recipe), &raw, &inputs, &BTreeMap::new())?,
            false,
        )
    }

    fn lock(recipe: &Recipe) -> Result<ArtifactLockfileV2> {
        let (raw, _) = fixture(recipe)?;
        let ToolchainLockfileDocument::V2 { lockfile: lock, .. } =
            parse_document(std::str::from_utf8(&raw)?)?
        else {
            eyre::bail!("fixture is not schema 2");
        };
        Ok(lock)
    }

    #[test]
    fn eager_ten_target_catalogs_preserve_all_original_pin_indexes() -> Result<()> {
        let mut counts = (0, 0, 0, 0);
        for recipe in recipes()? {
            let lock = lock(&recipe)?;
            let original = facet_json::to_string(&lock)?;
            let catalog = StrictRequestCatalog::from_ordered_lock(&lock)?;
            counts.0 += lock.artifacts.len();
            counts.1 += catalog.coordinates.len();
            counts.2 += catalog.transport_urls.len();
            counts.3 += catalog
                .dynamic_aliases
                .values()
                .map(|alias| alias.original_dependency_indexes.len())
                .sum::<usize>();
            for (index, pin) in lock.artifacts.iter().enumerate() {
                if let Some(coordinate) = &pin.coordinate {
                    ensure!(
                        catalog.coordinate_index(coordinate)? == index,
                        "coordinate index changed"
                    );
                }
                if let Some(url) = transport_url(pin) {
                    ensure!(
                        catalog.transport_url_index(url)? == index,
                        "URL index changed"
                    );
                }
            }
            ensure!(
                facet_json::to_string(&lock)? == original,
                "catalog rewrote or sorted original rows"
            );
        }
        ensure!(
            counts == (838, 818, 836, 6),
            "strict fixture counts drifted: {counts:?}"
        );
        // This proves private catalog mechanics, not weak-byte promotion or
        // native preparation for 26.1.2. The exact-byte gate is tested below.
        Ok(())
    }

    #[test]
    fn nine_prepared_targets_retain_raw_bytes_roles_and_immutable_identity() -> Result<()> {
        let mut counts = (0, 0, 0);
        for recipe in recipes()? {
            if recipe.target == "26.1.2" {
                continue;
            }
            let inputs = prepared(&recipe.target)?;
            let (raw, _) = fixture(&recipe)?;
            let original = lock(&recipe)?;
            inputs.verify_source_lock_bytes(&raw, false)?;
            ensure!(
                inputs.raw_source_lock_bytes() == raw
                    && facet_json::to_string(inputs.original_lock())?
                        == facet_json::to_string(&original)?
                    && inputs.receipt().compilation == "not_performed"
                    && !inputs.receipt().live_pom_discovery
                    && inputs.receipt().immutable_source_lock,
                "prepared input changed source or claimed native execution"
            );
            counts.0 += inputs.receipt().original_dependency_rows;
            counts.1 += inputs.receipt().artifact_pins;
            counts.2 += inputs.receipt().supplemental_dependency_rows;
        }
        ensure!(
            counts == (89, 758, 72),
            "nine-target role counts drifted: {counts:?}"
        );
        Ok(())
    }

    #[test]
    fn dynamic_aliases_retain_six_rows_and_three_exact_resolutions() -> Result<()> {
        let inputs = prepared("1.19.2")?;
        ensure!(
            inputs.receipt().dynamic_alias_keys == 3
                && inputs.receipt().dynamic_alias_original_rows == 6,
            "dynamic rows were lost or re-resolved"
        );
        for alias in inputs.dynamic_aliases() {
            let request = inputs.coordinate(&alias.requested_coordinate, false)?;
            ensure!(
                request.resolved_coordinate() == Some(alias.resolved_coordinate.as_str())
                    && request.original_artifact_index() == alias.original_artifact_index
                    && alias.original_dependency_indexes.len() == 2,
                "dynamic alias escaped original resolution"
            );
        }
        ensure!(
            inputs
                .coordinate("com.teamcofh:cofh_core:1.19.2-10.2.+", false)
                .is_err(),
            "unrecorded dynamic alias accepted"
        );
        Ok(())
    }

    #[test]
    fn runtime_closure_is_frozen_global_rows_not_live_parent_edges() -> Result<()> {
        for target in ["1.19.2", "1.21.1"] {
            let inputs = prepared(target)?;
            let rows = inputs.frozen_runtime_rows_for(inputs.runtime_roots(), false)?;
            ensure!(
                rows.len() == usize::from(target == "1.21.1"),
                "transitive runtime rediscovery"
            );
            if target == "1.21.1" {
                ensure!(
                    rows[0].resolved_coordinate == "org.appliedenergistics:guideme:21.1.1"
                        && rows[0].original_dependency_index.is_some(),
                    "frozen GuideMe row changed"
                );
            }
            let mut changed = inputs.runtime_roots().to_vec();
            ensure!(!changed.is_empty(), "fixture must exercise root mismatch");
            changed[0].resolved_coordinate.push_str("-unrecorded");
            ensure!(
                inputs.frozen_runtime_rows_for(&changed, false).is_err(),
                "changed root graph was inferred"
            );
            changed = inputs.runtime_roots().to_vec();
            changed.pop();
            ensure!(
                inputs.frozen_runtime_rows_for(&changed, false).is_err(),
                "partial root set reused global closure"
            );
            if inputs.runtime_roots().len() > 1 {
                changed = inputs.runtime_roots().to_vec();
                changed.swap(0, 1);
                ensure!(
                    inputs.frozen_runtime_rows_for(&changed, false).is_err(),
                    "reordered original roots accepted"
                );
            }
        }
        Ok(())
    }

    #[test]
    fn refresh_and_unpinned_requests_fail_before_caller_effects() -> Result<()> {
        let inputs = prepared("1.19.4")?;
        let effects = Cell::new(0usize);
        let attempt = |result: Result<PreparedArtifactRequest>| {
            if result.is_ok() {
                effects.set(effects.get() + 1);
            }
            result.is_err()
        };
        ensure!(
            attempt(inputs.coordinate("example:unknown:1", false)),
            "unpinned coordinate"
        );
        ensure!(
            attempt(inputs.coordinate("net.minecraftforge:forge:1.19.4-45.0.42", false)),
            "loader substitute"
        );
        ensure!(
            attempt(inputs.exact_url("https://example.invalid/unpinned.jar", false)),
            "unpinned URL"
        );
        let known = inputs.coordinate("net.minecraftforge:forge:1.19.4-45.0.9:userdev", false)?;
        ensure!(
            attempt(inputs.coordinate(known.resolved_coordinate().unwrap_or("missing"), true)),
            "refresh coordinate"
        );
        ensure!(
            attempt(inputs.exact_url(known.exact_transport_url().unwrap_or("missing"), true)),
            "refresh URL"
        );
        ensure!(effects.get() == 0, "caller effects occurred after refusal");
        ensure!(
            inputs
                .verify_source_lock_bytes(inputs.raw_source_lock_bytes(), true)
                .is_err()
                && inputs
                    .frozen_runtime_rows_for(inputs.runtime_roots(), true)
                    .is_err()
                && inputs
                    .verify_artifact_bytes(&known, b"arbitrary", true)
                    .is_err(),
            "refresh escaped another boundary"
        );
        let recipe = recipe("1.19.4")?;
        let (raw, role_inputs) = fixture(&recipe)?;
        let released = prepare_released_native_inputs(
            &request(&recipe),
            &raw,
            &role_inputs,
            &BTreeMap::new(),
        )?;
        ensure!(
            PreparedDependencyInputs::from_released(released, true)
                .unwrap_err()
                .to_string()
                .contains("before any effects"),
            "refresh accepted at preparation"
        );
        Ok(())
    }

    #[test]
    fn eager_ambiguity_and_dynamic_binding_conflicts_are_refused() -> Result<()> {
        let recipe = recipe("1.19.2")?;
        let mut original = lock(&recipe)?;
        original.artifacts.push(original.artifacts[2].clone());
        ensure!(
            StrictRequestCatalog::from_ordered_lock(&original)
                .unwrap_err()
                .to_string()
                .contains("ambiguous pinned coordinate"),
            "duplicate coordinate accepted"
        );
        original = lock(&recipe)?;
        let mut duplicate_url = original.artifacts[2].clone();
        duplicate_url.coordinate = Some("example:other:1".to_owned());
        original.artifacts.push(duplicate_url);
        ensure!(
            StrictRequestCatalog::from_ordered_lock(&original)
                .unwrap_err()
                .to_string()
                .contains("ambiguous pinned transport URL"),
            "duplicate transport URL accepted"
        );
        original = lock(&recipe)?;
        let aliases = original
            .dependencies
            .iter()
            .enumerate()
            .filter(|(_, row)| row.dynamic_version)
            .map(|(index, _)| index)
            .collect::<Vec<_>>();
        let mut conflict = original.dependencies[aliases[2]].clone();
        conflict
            .notation
            .clone_from(&original.dependencies[aliases[0]].notation);
        original.dependencies.push(conflict);
        ensure!(
            StrictRequestCatalog::from_ordered_lock(&original)
                .unwrap_err()
                .to_string()
                .contains("ambiguous dynamic alias"),
            "conflicting dynamic resolution accepted"
        );
        Ok(())
    }

    #[test]
    fn weak_metadata_gate_and_source_build_provenance_remain_exact() -> Result<()> {
        let recipe = recipe("26.1.2")?;
        let (raw, inputs) = fixture(&recipe)?;
        let review = review_released_native_inputs(&request(&recipe), &raw, &inputs)?;
        ensure!(
            review
                .require_exact_bytes(&BTreeMap::new())
                .unwrap_err()
                .to_string()
                .contains("identity unresolved"),
            "weak input promoted without exact bytes"
        );
        let lock = lock(&recipe)?;
        let catalog = StrictRequestCatalog::from_ordered_lock(&lock)?;
        for index in [18usize, 19] {
            let pin = &lock.artifacts[index];
            ensure!(
                pin.source_relative_path.is_some()
                    && pin
                        .source_git
                        .as_ref()
                        .is_some_and(|git| git.commit == "f33ff1f438caa55d58ef1f0a08091997353afcb8")
                    && pin.source_build.is_some()
                    && catalog.coordinate_index(pin.coordinate.as_deref().unwrap_or("missing"))?
                        == index,
                "source-build provenance lost"
            );
            ensure!(
                catalog
                    .transport_url_index(pin.url.as_deref().unwrap_or("missing"))
                    .unwrap_err()
                    .to_string()
                    .contains("provenance, not an artifact"),
                "repository URL accepted as artifact bytes"
            );
        }
        ensure!(
            verify_weak_promotions(&lock, &BTreeMap::new()).is_err(),
            "weak gate bypass"
        );
        let wrong = BTreeMap::from([(
            18usize,
            "blake3:0000000000000000000000000000000000000000".to_owned(),
        )]);
        ensure!(
            verify_weak_promotions(&lock, &wrong).is_err(),
            "replacement hash accepted"
        );
        Ok(())
    }

    #[test]
    fn changed_raw_lock_cross_context_and_mismatching_bytes_are_refused() -> Result<()> {
        let inputs = prepared("1.19.4")?;
        let mut changed = inputs.raw_source_lock_bytes().to_vec();
        changed.push(b'\n');
        ensure!(
            inputs.verify_source_lock_bytes(&changed, false).is_err(),
            "semantically equivalent source-lock normalization accepted"
        );
        let known = inputs.coordinate("net.minecraftforge:forge:1.19.4-45.0.9:userdev", false)?;
        let error = inputs
            .verify_artifact_bytes(&known, b"not the locked artifact", false)
            .unwrap_err()
            .to_string();
        ensure!(
            error.contains("original frozen pin"),
            "wrong bytes accepted: {error}"
        );
        let other = prepared("1.19.2")?;
        ensure!(
            other.artifact_pin(&known).is_err(),
            "cross-context request accepted"
        );
        let mut forged = known;
        forged.transport_url = Some("https://example.invalid/override.jar".to_owned());
        ensure!(
            inputs.artifact_pin(&forged).is_err(),
            "forged request accepted"
        );
        ensure!(
            inputs.source_exclusions().len() == 3,
            "source exclusions lost"
        );
        Ok(())
    }
}
