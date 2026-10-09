//! Pure, lossless schema-2 released-input preparation.
//!
//! Read-only foundation only: no native execution, project generation, cache
//! creation, acquisition, JDK resolution or lock writer. Frozen recipes bind
//! released Gradle roles to existing pins, not invented schema-4 profiles.
//! The 541 unexpanded pipeline consumer roles remain pending integration.
//! Weak metadata never establishes exact artifact identity here.

use super::provenance::sha256;
use crate::jar_build::ArtifactLockfile;
use crate::jar_build::ArtifactSource;
use crate::jar_build::hash::ContentHash;
use crate::toolchain_lockfile_schema::ToolchainLockfileDocument;
use crate::toolchain_lockfile_schema::parse_document;
use crate::toolchain_lockfile_schema::version::v2::ArtifactLockfileV2;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;

#[cfg(test)]
const REVIEW: &str =
    include_str!("../../../../../docs/tasks/sfm-core-released-native-adapter-review.json");
#[cfg(test)]
const REVIEW_SHA256: &str =
    "sha256:8b927a2b978715a6fc6ae1d0fd828a8a46b729a41a43a54897ca5e6cac313e21";
const MAX_REVIEW_BYTES: usize = 512 * 1024;
const MAX_LOCK_BYTES: usize = 1024 * 1024;
const MAX_ROLE_INPUT_BYTES: usize = 512 * 1024;
const MAX_ROLE_INPUT_TOTAL_BYTES: usize = 4 * 1024 * 1024;
pub(crate) const BUILD_CONFIGURATION_PATH: &str =
    "platform/minecraft/build-configuration/released-native.json";
const MAX_EXACT_WITNESS_BYTES: usize = 128 * 1024 * 1024;

/// Logical context, not a branch, path or dependency profile.
#[derive(Clone, Debug)]
pub struct ReleasedNativeRequest<'a> {
    pub target_id: &'a str,
    pub minecraft_version: &'a str,
    pub java_major: u16,
    pub recipe_id: &'a str,
    pub refresh: bool,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub struct ReleasedBundlePolicy {
    pub accepted_version_range: String,
    pub artifact_version: String,
    pub is_obfuscated: bool,
}

/// Original row identity remains independent from planner roles.
#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub struct ReleasedPreparedDependency {
    pub original_dependency_index: Option<usize>,
    pub configuration: String,
    pub requested_coordinate: String,
    pub resolved_coordinate: String,
    pub artifact_index: usize,
    pub artifact_hash: String,
    pub artifact_treatment: String,
    pub data_run_policy: String,
    pub bundle: Option<ReleasedBundlePolicy>,
}

/// Diagnostic bound to the original pin and source-build provenance.
#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub struct ReleasedExactByteRequirement {
    pub artifact_index: usize,
    pub coordinate: String,
    pub original_content_hash: String,
    pub source_commit: String,
    pub source_build_sha256: String,
}

/// Portable input evidence; compilation is deliberately not claimed.
#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub struct ReleasedNativeReceipt {
    pub schema: String,
    pub recipe_id: String,
    pub recipe_review_sha256: String,
    pub source_lock_sha256: String,
    pub target_id: String,
    pub minecraft_version: String,
    pub java_major: u16,
    pub loader_kind: String,
    pub loader_coordinate: String,
    pub immutable_source_lock: bool,
    pub strict_external_hashes: bool,
    pub dependency_rows: usize,
    pub artifact_pins: usize,
    pub supplemental_rows: usize,
    pub unexpanded_pipeline_roles: usize,
    pub role_input_sha256: BTreeMap<String, String>,
    pub direct_version_json_artifact_index: usize,
    pub direct_version_json_url: String,
    pub direct_version_json_hash: String,
    pub exact_bytes_verified: BTreeMap<usize, String>,
    pub compilation: String,
}

/// A review may still have unresolved exact-byte requirements.
///
/// Raw bytes and parsed structures remain unchanged. Promotion through
/// `require_exact_bytes` is input preparation, not native execution capability.
#[derive(Clone, Debug)]
pub struct ReleasedNativeInputReview {
    raw_lock_bytes: Vec<u8>,
    original_lock: ArtifactLockfileV2,
    legacy_lock: ArtifactLockfile,
    dependencies: Vec<ReleasedPreparedDependency>,
    source_excludes: BTreeMap<String, Vec<String>>,
    exact_byte_requirements: Vec<ReleasedExactByteRequirement>,
    receipt: ReleasedNativeReceipt,
}

/// Historical weak identities were checked against original exact pins.
///
/// Integration must enforce immutable/strict policy and recheck named inputs.
#[derive(Clone, Debug)]
pub struct ReleasedNativeInputs(ReleasedNativeInputReview);

impl ReleasedNativeInputReview {
    #[must_use]
    pub fn exact_byte_requirements(&self) -> &[ReleasedExactByteRequirement] {
        &self.exact_byte_requirements
    }

    #[must_use]
    pub fn receipt(&self) -> &ReleasedNativeReceipt {
        &self.receipt
    }

    /// Check provided bytes against the original frozen content hash.
    ///
    /// No local checksum, weak metadata or replacement pin can override it.
    ///
    /// # Errors
    /// Rejects unavailable, extra or mismatching byte witnesses. No acquisition
    /// or filesystem route is provided.
    pub fn require_exact_bytes(
        mut self,
        witnesses: &BTreeMap<usize, Vec<u8>>,
    ) -> Result<ReleasedNativeInputs> {
        let required = self
            .exact_byte_requirements
            .iter()
            .map(|requirement| requirement.artifact_index)
            .collect::<BTreeSet<_>>();
        ensure!(
            witnesses.keys().all(|index| required.contains(index)),
            "exact-byte evidence includes an artifact outside the frozen weak-identity requirements"
        );
        for requirement in &self.exact_byte_requirements {
            let bytes = witnesses.get(&requirement.artifact_index).ok_or_else(|| {
                eyre::eyre!(
                    "released native identity unresolved: {} at artifact {} requires exact bytes matching {}; weak metadata is not identity proof (locked source commit {})",
                    requirement.coordinate, requirement.artifact_index,
                    requirement.original_content_hash, requirement.source_commit
                )
            })?;
            ensure!(
                !bytes.is_empty() && bytes.len() <= MAX_EXACT_WITNESS_BYTES,
                "exact-byte witness for {} is empty or exceeds its bounded limit",
                requirement.coordinate
            );
            let locked = &self.original_lock.artifacts[requirement.artifact_index];
            let actual = ContentHash::from_bytes(bytes, locked.hash.algorithm);
            ensure!(
                actual == locked.hash,
                "released native exact-byte mismatch for {}: actual {}, original frozen pin {}; weak metadata or a new local checksum cannot substitute",
                requirement.coordinate,
                actual,
                locked.hash
            );
            self.receipt
                .exact_bytes_verified
                .insert(requirement.artifact_index, actual.to_string());
        }
        Ok(ReleasedNativeInputs(self))
    }
}

impl ReleasedNativeInputs {
    #[must_use]
    pub(crate) fn original_lock(&self) -> &ArtifactLockfileV2 {
        &self.0.original_lock
    }

    #[must_use]
    pub fn receipt(&self) -> &ReleasedNativeReceipt {
        &self.0.receipt
    }

    #[must_use]
    pub fn raw_source_lock_bytes(&self) -> &[u8] {
        &self.0.raw_lock_bytes
    }

    #[must_use]
    pub fn dependencies(&self) -> &[ReleasedPreparedDependency] {
        &self.0.dependencies
    }

    #[must_use]
    pub fn source_excludes(&self) -> &BTreeMap<String, Vec<String>> {
        &self.0.source_excludes
    }

    /// Field snapshot for review, never a raw source-lock replacement.
    ///
    /// # Errors
    /// Returns a serialization error. No mutable lock or writer is exposed.
    pub fn original_lock_snapshot(&self) -> Result<String> {
        Ok(facet_json::to_string(&self.0.original_lock)?)
    }

    /// Lossless existing-engine field view, still schema 2.
    ///
    /// # Errors
    /// Returns a serialization error. No mutable lock or writer is exposed.
    pub fn legacy_lock_snapshot(&self) -> Result<String> {
        Ok(facet_json::to_string(&self.0.legacy_lock)?)
    }
}

#[derive(Clone, Debug, Facet)]
struct ReviewDocument {
    schema: String,
    targets: Vec<ReviewedRecipe>,
}

#[derive(Clone, Debug, Facet)]
struct ReviewedRecipe {
    target: String,
    minecraft_version: String,
    recipe_id: String,
    source_lock: SourceLockWitness,
    role_input_hashes: Vec<RoleInputWitness>,
    platform: PlatformWitness,
    artifact_pin_witnesses: Vec<ArtifactWitness>,
    recorded_dependency_roles: Vec<RecordedRole>,
    released_gradle_supplements: Vec<SupplementalRole>,
    codegen: CodegenWitness,
    native_compile_support_pins: Vec<CoordinateWitness>,
    pinned_minecraft_metadata: Vec<MetadataWitness>,
    source_excludes: Vec<ExclusionWitness>,
}

// The pinned Facet release implements tuples up to arity four. Retain the
// reviewed JSON's exact five-column array without changing its frozen bytes.
type ArtifactWitness = [facet_value::Value; 5];

fn witness_field<T: for<'facet> Facet<'facet>>(
    witness: &ArtifactWitness,
    index: usize,
) -> Result<T> {
    Ok(facet_json::from_str(&facet_json::to_string(
        &witness[index],
    )?)?)
}

#[derive(Clone, Debug, Facet)]
struct SourceLockWitness {
    path: String,
    sha256: String,
    bytes: usize,
    schema_version: u32,
    minecraft_version: String,
    dependency_count: usize,
    artifact_count: usize,
    allow_local_artifact_cache: bool,
    migration_hints_present: bool,
}

#[derive(Clone, Debug, Facet)]
struct RoleInputWitness {
    path: String,
    sha256: String,
}

#[derive(Clone, Debug, Facet)]
struct PlatformWitness {
    kind: String,
    base_coordinate: String,
    gradle_configuration: String,
    component_artifacts: Vec<CoordinateWitness>,
    java_major: u16,
    jdk_pin: Option<String>,
    mapping_properties: BTreeMap<String, String>,
}

#[derive(Clone, Debug, Facet)]
struct CoordinateWitness {
    artifact_index: usize,
    coordinate: String,
    hash: String,
}

#[derive(Clone, Debug, Facet)]
struct RecordedRole {
    index: usize,
    configuration: String,
    notation: String,
    resolved_notation: String,
    source: String,
    dynamic_version: bool,
    artifact_index: usize,
    artifact_hash: String,
    gradle_line: Option<usize>,
    gradle_wrap: String,
    artifact_treatment: String,
    data_run_policy: String,
    bundle: Option<ReleasedBundlePolicy>,
}

#[derive(Clone, Debug, Facet)]
struct SupplementalRole {
    configuration: String,
    coordinate: String,
    artifact_index: usize,
    artifact_hash: String,
    gradle_line: usize,
    artifact_treatment: String,
    data_run_policy: String,
}

#[derive(Clone, Debug, Facet)]
struct CodegenWitness {
    version: String,
    configuration: String,
    classpath_pins: Vec<CoordinateWitness>,
}

#[derive(Clone, Debug, Facet)]
struct MetadataWitness {
    artifact_index: usize,
    url: String,
    hash: String,
    required_for_selected_version: bool,
}

#[derive(Clone, Debug, Facet)]
struct ExclusionWitness {
    output: String,
    source_path: String,
    sha256: String,
    patterns: Vec<String>,
}

/// Review the exact released input set before any effects.
///
/// All ten frozen sets can be reviewed. The 26.1.2 weak identity remains an
/// explicit requirement, not prepared input or native capability.
///
/// # Errors
/// Rejects unknown recipes, refresh, context/hash/pin mismatches, incomplete
/// role evidence, ambiguous bindings and unavailable source provenance.
#[tracing::instrument(name = "released_inputs.review", skip_all)]
pub(crate) fn review_released_native_inputs_from_configuration(
    configuration: &str,
    request: &ReleasedNativeRequest<'_>,
    raw_lock: &[u8],
    role_input_bytes: &BTreeMap<String, Vec<u8>>,
) -> Result<ReleasedNativeInputReview> {
    ensure!(
        !request.refresh,
        "released native input is immutable; --refresh is not supported"
    );
    let document = parse_configuration(configuration)?;
    let recipe = document
        .targets
        .iter()
        .find(|recipe| recipe.target == request.target_id)
        .ok_or_else(|| {
            eyre::eyre!(
                "no reviewed released schema-2 recipe for {}",
                request.target_id
            )
        })?;
    ensure!(
        request.recipe_id == recipe.recipe_id
            && request.minecraft_version == recipe.minecraft_version
            && request.java_major == recipe.platform.java_major,
        "released schema-2 recipe/context mismatch for {}; use exact recipe, MC and Java major",
        request.target_id
    );
    let role_hashes = check_role_inputs(recipe, role_input_bytes)?;
    let original_lock = parse_bound_lock(recipe, raw_lock)?;
    check_artifact_witnesses(recipe, &original_lock)?;
    check_platform(recipe, &original_lock, role_input_bytes)?;
    let dependencies = prepare_dependency_roles(recipe, &original_lock, role_input_bytes)?;
    let source_excludes = check_source_excludes(recipe, role_input_bytes)?;
    let metadata = check_version_metadata(recipe, &original_lock)?;
    let requirements = exact_byte_requirements(&original_lock)?;
    let legacy_lock = original_lock.clone().into_latest();
    let unexpanded =
        recipe.artifact_pin_witnesses.iter().try_fold(
            0usize,
            |count, witness| -> Result<usize> {
                let roles: Vec<String> = witness_field(witness, 4)?;
                Ok(count
                    + usize::from(roles.iter().any(|role| {
                        role == "preserved_pin_pipeline_consumer_role_not_yet_expanded"
                    })))
            },
        )?;
    let receipt = ReleasedNativeReceipt {
        schema: "sfm:released_native_input_review@1".to_owned(),
        recipe_id: recipe.recipe_id.clone(),
        recipe_review_sha256: sha256(configuration.as_bytes()),
        source_lock_sha256: sha256(raw_lock),
        target_id: recipe.target.clone(),
        minecraft_version: recipe.minecraft_version.clone(),
        java_major: recipe.platform.java_major,
        loader_kind: recipe.platform.kind.clone(),
        loader_coordinate: recipe.platform.base_coordinate.clone(),
        immutable_source_lock: true,
        strict_external_hashes: true,
        dependency_rows: original_lock.dependencies.len(),
        artifact_pins: original_lock.artifacts.len(),
        supplemental_rows: recipe.released_gradle_supplements.len(),
        unexpanded_pipeline_roles: unexpanded,
        role_input_sha256: role_hashes,
        direct_version_json_artifact_index: metadata.artifact_index,
        direct_version_json_url: metadata.url.clone(),
        direct_version_json_hash: metadata.hash.clone(),
        exact_bytes_verified: BTreeMap::new(),
        compilation: "not_performed".to_owned(),
    };
    Ok(ReleasedNativeInputReview {
        raw_lock_bytes: raw_lock.to_vec(),
        original_lock,
        legacy_lock,
        dependencies,
        source_excludes,
        exact_byte_requirements: requirements,
        receipt,
    })
}

/// Prepare only after original weak identities have exact-byte evidence.
///
/// # Errors
/// Returns review errors or a precise unresolved/mismatching byte requirement.
/// Witnesses match original content hashes, never a new local checksum.
#[cfg(test)]
pub fn prepare_released_native_inputs(
    request: &ReleasedNativeRequest<'_>,
    raw_lock: &[u8],
    role_input_bytes: &BTreeMap<String, Vec<u8>>,
    exact_artifact_bytes: &BTreeMap<usize, Vec<u8>>,
) -> Result<ReleasedNativeInputs> {
    review_released_native_inputs(request, raw_lock, role_input_bytes)?
        .require_exact_bytes(exact_artifact_bytes)
}

#[tracing::instrument(name = "released_inputs.parse_configuration", skip_all)]
fn parse_configuration(configuration: &str) -> Result<ReviewDocument> {
    ensure!(
        configuration.len() <= MAX_REVIEW_BYTES,
        "released-native build configuration exceeds its bounded limit"
    );
    let document: ReviewDocument = facet_json::from_str(configuration)
        .wrap_err("invalid released-native build configuration")?;
    ensure!(
        document.schema == "sfm:released_native_build_configuration@1"
            && document.targets.len() == 10,
        "unsupported released-native build configuration"
    );
    let targets = document
        .targets
        .iter()
        .map(|recipe| recipe.target.as_str())
        .collect::<BTreeSet<_>>();
    ensure!(
        targets.len() == 10,
        "duplicate compiled released recipe target"
    );
    Ok(document)
}

// Temporary historical-test adapter; production receives checkout-owned inputs.
#[cfg(test)]
fn historical_configuration() -> String {
    REVIEW.replace(
        "sfm:core_released_native_adapter_review@1",
        "sfm:released_native_build_configuration@1",
    )
}

#[cfg(test)]
fn frozen_review() -> Result<ReviewDocument> {
    ensure!(
        sha256(REVIEW.as_bytes()) == REVIEW_SHA256,
        "historical fixture changed"
    );
    parse_configuration(&historical_configuration())
}

#[cfg(test)]
pub fn review_released_native_inputs(
    request: &ReleasedNativeRequest<'_>,
    raw_lock: &[u8],
    role_input_bytes: &BTreeMap<String, Vec<u8>>,
) -> Result<ReleasedNativeInputReview> {
    review_released_native_inputs_from_configuration(
        &historical_configuration(),
        request,
        raw_lock,
        role_input_bytes,
    )
}

fn check_role_inputs(
    recipe: &ReviewedRecipe,
    inputs: &BTreeMap<String, Vec<u8>>,
) -> Result<BTreeMap<String, String>> {
    let paths = recipe
        .role_input_hashes
        .iter()
        .map(|witness| witness.path.as_str())
        .collect::<BTreeSet<_>>();
    ensure!(
        paths.len() == recipe.role_input_hashes.len()
            && inputs.len() == paths.len()
            && inputs.keys().all(|key| paths.contains(key.as_str())),
        "released role-input set is missing, duplicate or contains an unreviewed path"
    );
    let mut hashes = BTreeMap::new();
    let mut total = 0usize;
    for witness in &recipe.role_input_hashes {
        check_relative_path(&witness.path)?;
        let bytes = &inputs[&witness.path];
        total = total
            .checked_add(bytes.len())
            .ok_or_else(|| eyre::eyre!("role input overflow"))?;
        ensure!(
            bytes.len() <= MAX_ROLE_INPUT_BYTES && total <= MAX_ROLE_INPUT_TOTAL_BYTES,
            "released role inputs exceed bounded byte limits"
        );
        let actual = sha256(bytes);
        ensure!(
            actual == format!("sha256:{}", witness.sha256),
            "released role-input hash mismatch: {} (expected {}, actual {})",
            witness.path,
            witness.sha256,
            actual
        );
        std::str::from_utf8(bytes).wrap_err("released role input is not UTF-8")?;
        hashes.insert(witness.path.clone(), actual);
    }
    Ok(hashes)
}

#[tracing::instrument(name = "released_inputs.parse_lock", skip_all)]
fn parse_bound_lock(recipe: &ReviewedRecipe, bytes: &[u8]) -> Result<ArtifactLockfileV2> {
    check_relative_path(&recipe.source_lock.path)?;
    ensure!(
        bytes.len() <= MAX_LOCK_BYTES
            && bytes.len() == recipe.source_lock.bytes
            && sha256(bytes) == format!("sha256:{}", recipe.source_lock.sha256),
        "released source-lock raw hash/length mismatch for {}; schema-4 substitution or normalization is unsupported",
        recipe.target
    );
    let ToolchainLockfileDocument::V2 { lockfile: lock, .. } =
        parse_document(std::str::from_utf8(bytes).wrap_err("released lock is not UTF-8")?)?
    else {
        eyre::bail!("released adapter requires the exact reviewed schema-2 document");
    };
    ensure!(
        lock.schema_version == 2
            && recipe.source_lock.schema_version == 2
            && lock.minecraft_version == recipe.minecraft_version
            && lock.minecraft_version == recipe.source_lock.minecraft_version
            && lock.dependencies.len() == recipe.source_lock.dependency_count
            && lock.artifacts.len() == recipe.source_lock.artifact_count
            && !lock.allow_local_artifact_cache
            && !recipe.source_lock.allow_local_artifact_cache
            && lock.migration_hints.is_none()
            && !recipe.source_lock.migration_hints_present,
        "released source-lock declarations differ from the frozen recipe"
    );
    Ok(lock)
}

fn check_artifact_witnesses(recipe: &ReviewedRecipe, lock: &ArtifactLockfileV2) -> Result<()> {
    ensure!(
        lock.artifacts.len() == recipe.artifact_pin_witnesses.len(),
        "released artifact inventory is incomplete"
    );
    for (expected_index, witness) in recipe.artifact_pin_witnesses.iter().enumerate() {
        ensure!(
            witness_field::<usize>(witness, 0)? == expected_index,
            "artifact witness indexes differ from original order"
        );
        let artifact = &lock.artifacts[expected_index];
        ensure!(
            artifact.coordinate == witness_field::<Option<String>>(witness, 1)?
                && artifact.hash.to_string() == witness_field::<String>(witness, 2)?
                && facet_json::to_string(&artifact.source)?
                    == facet_json::to_string(&witness_field::<String>(witness, 3)?)?,
            "released artifact identity differs at row {expected_index}"
        );
        ensure!(
            matches!(
                artifact.source,
                ArtifactSource::RemoteMaven
                    | ArtifactSource::RemoteHttp
                    | ArtifactSource::SourceBuild
            ) && artifact.original_path.is_none(),
            "released artifact {expected_index} has unavailable local/unknown provenance"
        );
        check_cache_placeholder(&artifact.cache_path.to_string_lossy())?;
        if artifact.source == ArtifactSource::SourceBuild {
            let git = artifact.source_git.as_ref().ok_or_else(|| {
                eyre::eyre!("source-built artifact {expected_index} has no locked Git provenance")
            })?;
            let build = artifact.source_build.as_ref().ok_or_else(|| {
                eyre::eyre!("source-built artifact {expected_index} has no locked build recipe")
            })?;
            ensure!(
                !git.dirty
                    && git.commit.len() == 40
                    && git.commit.bytes().all(|byte| byte.is_ascii_hexdigit())
                    && git
                        .remote_url
                        .as_deref()
                        .is_some_and(|url| url.starts_with("https://"))
                    && !build.tasks.is_empty(),
                "insufficient frozen source provenance at row {expected_index}"
            );
            check_cache_placeholder(&git.root.to_string_lossy())?;
            check_relative_path(&build.output_path.to_string_lossy().replace('\\', "/"))?;
            let relative = artifact.source_relative_path.as_ref().ok_or_else(|| {
                eyre::eyre!("source-relative path is missing at artifact {expected_index}")
            })?;
            check_relative_path(&relative.to_string_lossy().replace('\\', "/"))?;
        }
    }
    Ok(())
}

fn check_platform(
    recipe: &ReviewedRecipe,
    lock: &ArtifactLockfileV2,
    inputs: &BTreeMap<String, Vec<u8>>,
) -> Result<()> {
    ensure!(
        recipe.platform.jdk_pin.is_none()
            && recipe.platform.component_artifacts.len() == 3
            && matches!(
                recipe.platform.kind.as_str(),
                "forge_gradle_forge" | "forge_gradle_neoforge_group" | "neogradle_userdev"
            ),
        "released platform needs a genuine schema-2 recipe, not an invented SDK/profile"
    );
    let path = format!(
        "platform/minecraft/core-liquid-template/build/versions/{}/gradle.properties",
        recipe.target
    );
    let properties = properties_map(&inputs[&path])?;
    ensure!(
        properties.get("minecraft_version") == Some(&recipe.minecraft_version),
        "released MC property differs from recipe"
    );
    let version = properties
        .get("neo_version")
        .ok_or_else(|| eyre::eyre!("missing released loader version"))?;
    let prefix = match recipe.platform.kind.as_str() {
        "forge_gradle_forge" => "net.minecraftforge:forge",
        "forge_gradle_neoforge_group" => "net.neoforged:forge",
        _ => "net.neoforged:neoforge",
    };
    let expected = if recipe.platform.kind == "neogradle_userdev" {
        format!("{prefix}:{version}")
    } else {
        format!("{prefix}:{}-{version}", recipe.minecraft_version)
    };
    ensure!(
        recipe.platform.base_coordinate == expected,
        "loader differs from exact released properties"
    );
    let mut classifiers = BTreeSet::new();
    for pin in &recipe.platform.component_artifacts {
        check_coordinate_pin(lock, pin)?;
        classifiers.insert(
            pin.coordinate
                .strip_prefix(&format!("{expected}:"))
                .ok_or_else(|| eyre::eyre!("loader component has another platform version"))?,
        );
    }
    ensure!(
        classifiers == BTreeSet::from(["sources", "universal", "userdev"]),
        "released loader requires exact three component pins"
    );
    for (key, value) in &recipe.platform.mapping_properties {
        ensure!(
            properties.get(key) == Some(value),
            "released mapping property mismatch: {key}"
        );
    }
    let text = dependency_script(recipe, inputs)?;
    ensure!(
        text.lines().any(|line| !line.trim_start().starts_with("//")
            && line
                .trim_start()
                .starts_with(&format!("{} ", recipe.platform.gradle_configuration))
            && line.contains(prefix)),
        "exact released Gradle loader declaration missing"
    );
    ensure!(
        recipe.codegen.configuration == "antlr"
            && matches!(recipe.codegen.version.as_str(), "4.9.1" | "4.13.1"),
        "unsupported released ANTLR role"
    );
    for pin in recipe
        .codegen
        .classpath_pins
        .iter()
        .chain(&recipe.native_compile_support_pins)
    {
        check_coordinate_pin(lock, pin)?;
    }
    Ok(())
}

fn prepare_dependency_roles(
    recipe: &ReviewedRecipe,
    lock: &ArtifactLockfileV2,
    inputs: &BTreeMap<String, Vec<u8>>,
) -> Result<Vec<ReleasedPreparedDependency>> {
    ensure!(
        recipe.recorded_dependency_roles.len() == lock.dependencies.len(),
        "released dependency role coverage is incomplete"
    );
    let text = dependency_script(recipe, inputs)?;
    let mut prepared = Vec::new();
    for (index, role) in recipe.recorded_dependency_roles.iter().enumerate() {
        check_recorded_binding(lock, role, index)?;
        if role.configuration == "transitiveRuntime" {
            ensure!(
                role.gradle_line.is_none() && role.gradle_wrap == "recorded_transitive_runtime",
                "transitiveRuntime must not be rediscovered from live POMs"
            );
        } else {
            let line = role
                .gradle_line
                .ok_or_else(|| eyre::eyre!("recorded role has no Gradle line"))?;
            check_gradle_line(text, line, &role.configuration, &role.notation)?;
            let actual_wrap = if text
                .lines()
                .nth(line - 1)
                .is_some_and(|text| text.contains("fg.deobf("))
            {
                "fg_deobf"
            } else {
                "plain"
            };
            ensure!(
                actual_wrap == role.gradle_wrap,
                "released fg.deobf role mismatch"
            );
        }
        prepared.push(prepared_role(
            Some(index),
            &role.configuration,
            &role.notation,
            &role.resolved_notation,
            role.artifact_index,
            &role.artifact_hash,
            &role.artifact_treatment,
            &role.data_run_policy,
            role.bundle.clone(),
        )?);
    }
    for role in &recipe.released_gradle_supplements {
        check_gradle_line(
            text,
            role.gradle_line,
            &role.configuration,
            &role.coordinate,
        )?;
        check_coordinate_pin(
            lock,
            &CoordinateWitness {
                artifact_index: role.artifact_index,
                coordinate: role.coordinate.clone(),
                hash: role.artifact_hash.clone(),
            },
        )?;
        prepared.push(prepared_role(
            None,
            &role.configuration,
            &role.coordinate,
            &role.coordinate,
            role.artifact_index,
            &role.artifact_hash,
            &role.artifact_treatment,
            &role.data_run_policy,
            None,
        )?);
    }
    Ok(prepared)
}

fn check_recorded_binding(
    lock: &ArtifactLockfileV2,
    role: &RecordedRole,
    index: usize,
) -> Result<()> {
    let dependency = lock
        .dependencies
        .get(index)
        .ok_or_else(|| eyre::eyre!("dependency row absent"))?;
    ensure!(
        role.index == index
            && role.configuration == dependency.configuration
            && role.notation == dependency.notation
            && role.resolved_notation == dependency.resolved_notation
            && role.dynamic_version == dependency.dynamic_version
            && facet_json::to_string(&dependency.source)? == facet_json::to_string(&role.source)?,
        "recorded released dependency differs at row {index}"
    );
    let coordinate_matches = lock
        .artifacts
        .iter()
        .enumerate()
        .filter(|(_, artifact)| {
            artifact.coordinate.as_deref() == Some(dependency.resolved_notation.as_str())
        })
        .map(|(index, _)| index)
        .collect::<Vec<_>>();
    let cache_matches = lock
        .artifacts
        .iter()
        .enumerate()
        .filter(|(_, artifact)| artifact.cache_path == dependency.cache_path)
        .map(|(index, _)| index)
        .collect::<Vec<_>>();
    ensure!(
        coordinate_matches == [role.artifact_index] && cache_matches == [role.artifact_index],
        "released dependency row {index} has unavailable or ambiguous coordinate/cache binding"
    );
    ensure!(
        lock.artifacts[role.artifact_index].hash.to_string() == role.artifact_hash
            && !dependency.resolved_notation.contains(['+', '[', ']']),
        "released dependency row {index} has no deterministic exact resolution"
    );
    Ok(())
}

#[expect(
    clippy::too_many_arguments,
    reason = "Original row identity and independent reviewed policies remain explicit."
)]
fn prepared_role(
    original_dependency_index: Option<usize>,
    configuration: &str,
    requested: &str,
    resolved: &str,
    artifact_index: usize,
    artifact_hash: &str,
    treatment: &str,
    data: &str,
    bundle: Option<ReleasedBundlePolicy>,
) -> Result<ReleasedPreparedDependency> {
    ensure!(
        matches!(
            configuration,
            "annotationProcessor"
                | "implementation"
                | "compileOnly"
                | "runtimeOnly"
                | "jarJar"
                | "gametestImplementation"
                | "transitiveRuntime"
                | "antlr"
                | "testImplementation"
                | "testRuntimeOnly"
                | "testAnnotationProcessor"
        ),
        "unsupported released configuration: {configuration}"
    );
    ensure!(
        matches!(treatment, "plain" | "loader_managed_mod"),
        "unsupported artifact treatment: {treatment}"
    );
    ensure!(
        matches!(data, "include" | "exclude"),
        "unsupported released Data policy: {data}"
    );
    ensure!(
        (configuration == "jarJar") == bundle.is_some(),
        "bundle policy must belong to exact jarJar row"
    );
    Ok(ReleasedPreparedDependency {
        original_dependency_index,
        configuration: configuration.to_owned(),
        requested_coordinate: requested.to_owned(),
        resolved_coordinate: resolved.to_owned(),
        artifact_index,
        artifact_hash: artifact_hash.to_owned(),
        artifact_treatment: treatment.to_owned(),
        data_run_policy: data.to_owned(),
        bundle,
    })
}

fn check_coordinate_pin(lock: &ArtifactLockfileV2, witness: &CoordinateWitness) -> Result<()> {
    let matches = lock
        .artifacts
        .iter()
        .enumerate()
        .filter(|(_, artifact)| artifact.coordinate.as_deref() == Some(witness.coordinate.as_str()))
        .collect::<Vec<_>>();
    ensure!(
        matches.len() == 1
            && matches[0].0 == witness.artifact_index
            && matches[0].1.hash.to_string() == witness.hash,
        "released coordinate pin is unavailable or ambiguous: {}",
        witness.coordinate
    );
    Ok(())
}

fn check_source_excludes(
    recipe: &ReviewedRecipe,
    inputs: &BTreeMap<String, Vec<u8>>,
) -> Result<BTreeMap<String, Vec<String>>> {
    let mut excludes = BTreeMap::new();
    for witness in &recipe.source_excludes {
        let bytes = inputs
            .get(&witness.source_path)
            .ok_or_else(|| eyre::eyre!("expected source-exclusion file missing"))?;
        ensure!(
            sha256(bytes) == format!("sha256:{}", witness.sha256),
            "source-exclusion hash mismatch"
        );
        let patterns = std::str::from_utf8(bytes)?
            .lines()
            .map(str::trim)
            .filter(|line| !line.is_empty() && !line.starts_with('#'))
            .map(|line| line.replace('\\', "/"))
            .collect::<Vec<_>>();
        ensure!(
            patterns == witness.patterns,
            "released source-exclusion semantics changed"
        );
        ensure!(
            excludes.insert(witness.output.clone(), patterns).is_none(),
            "duplicate exclusion output"
        );
    }
    ensure!(
        excludes.len() == 3,
        "exclusions must cover main/test/gametest"
    );
    Ok(excludes)
}

fn check_version_metadata<'a>(
    recipe: &'a ReviewedRecipe,
    lock: &ArtifactLockfileV2,
) -> Result<&'a MetadataWitness> {
    let selected = recipe
        .pinned_minecraft_metadata
        .iter()
        .filter(|witness| witness.required_for_selected_version)
        .collect::<Vec<_>>();
    ensure!(
        selected.len() == 1,
        "released direct version metadata is unavailable or ambiguous"
    );
    let witness = selected[0];
    let artifact = lock
        .artifacts
        .get(witness.artifact_index)
        .ok_or_else(|| eyre::eyre!("missing version artifact"))?;
    ensure!(
        artifact.source == ArtifactSource::RemoteHttp
            && artifact.coordinate.is_none()
            && artifact.url.as_deref() == Some(witness.url.as_str())
            && artifact.hash.to_string() == witness.hash
            && witness
                .url
                .starts_with("https://piston-meta.mojang.com/v1/packages/")
            && witness
                .url
                .ends_with(&format!("/{}.json", recipe.minecraft_version)),
        "released direct version JSON differs from frozen source lock"
    );
    Ok(witness)
}

fn exact_byte_requirements(lock: &ArtifactLockfileV2) -> Result<Vec<ReleasedExactByteRequirement>> {
    let mut requirements = Vec::new();
    for (index, artifact) in lock.artifacts.iter().enumerate() {
        if artifact.weak.is_none() {
            continue;
        }
        ensure!(
            artifact.source == ArtifactSource::SourceBuild,
            "weak identity at artifact {index} has no reviewed source-build route"
        );
        let git = artifact
            .source_git
            .as_ref()
            .ok_or_else(|| eyre::eyre!("weak artifact lacks locked source provenance"))?;
        let build = artifact
            .source_build
            .as_ref()
            .ok_or_else(|| eyre::eyre!("weak artifact lacks locked build recipe"))?;
        requirements.push(ReleasedExactByteRequirement {
            artifact_index: index,
            coordinate: artifact
                .coordinate
                .clone()
                .ok_or_else(|| eyre::eyre!("weak artifact lacks coordinate"))?,
            original_content_hash: artifact.hash.to_string(),
            source_commit: git.commit.clone(),
            source_build_sha256: sha256(facet_json::to_string(build)?.as_bytes()),
        });
    }
    Ok(requirements)
}

fn properties_map(bytes: &[u8]) -> Result<BTreeMap<String, String>> {
    let mut properties = BTreeMap::new();
    for line in std::str::from_utf8(bytes)?.lines().map(str::trim) {
        if line.is_empty() || line.starts_with('#') {
            continue;
        }
        if let Some((key, value)) = line.split_once('=') {
            ensure!(
                properties
                    .insert(key.trim().to_owned(), value.trim().to_owned())
                    .is_none(),
                "duplicate released Gradle property: {key}"
            );
        }
    }
    Ok(properties)
}

fn dependency_script<'a>(
    recipe: &ReviewedRecipe,
    inputs: &'a BTreeMap<String, Vec<u8>>,
) -> Result<&'a str> {
    let paths = recipe
        .role_input_hashes
        .iter()
        .filter(|witness| witness.path.ends_with("/dependencies.gradle"))
        .collect::<Vec<_>>();
    ensure!(
        paths.len() == 1,
        "dependency-script role is missing or ambiguous"
    );
    Ok(std::str::from_utf8(&inputs[&paths[0].path])?)
}

fn check_gradle_line(text: &str, line: usize, configuration: &str, coordinate: &str) -> Result<()> {
    ensure!(line > 0, "released Gradle line must be one-based");
    let text = text
        .lines()
        .nth(line - 1)
        .ok_or_else(|| eyre::eyre!("Gradle role line unavailable"))?
        .trim_start();
    let starts = if configuration == "jarJar" {
        text.starts_with("jarJar(implementation(")
    } else {
        text.starts_with(&format!("{configuration} "))
    };
    ensure!(
        !text.starts_with("//") && starts && text.contains(coordinate),
        "released Gradle role differs from exact active line: {configuration} {coordinate}"
    );
    Ok(())
}

fn check_relative_path(path: &str) -> Result<()> {
    ensure!(
        !path.is_empty()
            && !path.contains(['\\', ':', '\0'])
            && !path.starts_with('/')
            && path
                .split('/')
                .all(|part| !part.is_empty() && part != "." && part != ".."),
        "released recipe path is not bounded repository-relative input"
    );
    Ok(())
}

fn check_cache_placeholder(path: &str) -> Result<()> {
    let normalized = path.replace('\\', "/");
    let relative = normalized
        .strip_prefix("$sfm-cache/")
        .ok_or_else(|| eyre::eyre!("released artifact has unreviewed cache root"))?;
    check_relative_path(relative)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::source_projection::candidate_lock::checked_file;
    use crate::source_projection::core_slice_test_support::read_bounded;
    use std::path::Path;

    type Fixture = (Vec<u8>, BTreeMap<String, Vec<u8>>);

    fn fixture_repository() -> Result<&'static Path> {
        Path::new(env!("CARGO_MANIFEST_DIR"))
            .ancestors()
            .nth(3)
            .ok_or_else(|| eyre::eyre!("CLI crate must be below repository"))
    }

    fn fixture_lock_bytes(recipe: &ReviewedRecipe) -> Result<Vec<u8>> {
        read_bounded(
            &checked_file(fixture_repository()?, &recipe.source_lock.path)?,
            MAX_LOCK_BYTES as u64,
        )
    }

    fn fixture(recipe: &ReviewedRecipe) -> Result<Fixture> {
        let lock = fixture_lock_bytes(recipe)?;
        let inputs =
            super::super::core_release_project_role_fixtures::release_project_role_outputs(
                &recipe.target,
                &recipe
                    .role_input_hashes
                    .iter()
                    .map(|input| input.path.clone())
                    .collect(),
            )?;
        Ok((lock, inputs))
    }

    fn request(recipe: &ReviewedRecipe) -> ReleasedNativeRequest<'_> {
        ReleasedNativeRequest {
            target_id: &recipe.target,
            minecraft_version: &recipe.minecraft_version,
            java_major: recipe.platform.java_major,
            recipe_id: &recipe.recipe_id,
            refresh: false,
        }
    }

    #[test]
    fn ten_input_sets_are_lossless_with_weak_identity_unresolved() -> Result<()> {
        let document = frozen_review()?;
        let mut counts = (0, 0, 0, 0);
        for recipe in &document.targets {
            let (raw, inputs) = fixture(recipe)?;
            let review = review_released_native_inputs(&request(recipe), &raw, &inputs)?;
            counts.0 += review.receipt.dependency_rows;
            counts.1 += review.receipt.artifact_pins;
            counts.2 += review.receipt.supplemental_rows;
            counts.3 += review.receipt.unexpanded_pipeline_roles;
            ensure!(review.raw_lock_bytes == raw, "raw lock bytes changed");
            ensure!(
                facet_json::to_string(&review.original_lock.dependencies)?
                    == facet_json::to_string(&review.legacy_lock.dependencies)?
                    && facet_json::to_string(&review.original_lock.artifacts)?
                        == facet_json::to_string(&review.legacy_lock.artifacts)?
                    && facet_json::to_string(&review.original_lock.repositories)?
                        == facet_json::to_string(&review.legacy_lock.repositories)?,
                "schema2 field projection lost input evidence"
            );
            if recipe.target == "26.1.2" {
                ensure!(
                    review.exact_byte_requirements.len() == 1,
                    "missing exact-byte requirement"
                );
                let error = review
                    .require_exact_bytes(&BTreeMap::new())
                    .unwrap_err()
                    .to_string();
                ensure!(
                    error.contains("identity unresolved") && error.contains("10.8.0.86"),
                    "{error}"
                );
            } else {
                let prepared = review.require_exact_bytes(&BTreeMap::new())?;
                ensure!(
                    prepared.raw_source_lock_bytes() == raw
                        && prepared.receipt().compilation == "not_performed",
                    "false build claim"
                );
            }
        }
        ensure!(
            counts == (97, 838, 81, 541),
            "released inventory does not reconcile"
        );
        Ok(())
    }

    #[test]
    fn exact_loader_aliases_codegen_and_metadata_are_not_inferred() -> Result<()> {
        let expected = [
            ("1.19.2", "net.minecraftforge:forge:1.19.2-43.4.0", "4.9.1"),
            ("1.19.4", "net.minecraftforge:forge:1.19.4-45.0.9", "4.9.1"),
            ("1.20", "net.minecraftforge:forge:1.20-46.0.10", "4.9.1"),
            ("1.20.1", "net.neoforged:forge:1.20.1-47.1.65", "4.9.1"),
            ("1.20.2", "net.neoforged:neoforge:20.2.86", "4.13.1"),
            ("1.20.3", "net.neoforged:neoforge:20.3.8-beta", "4.13.1"),
            ("1.20.4", "net.neoforged:neoforge:20.4.231", "4.13.1"),
            ("1.21.0", "net.neoforged:neoforge:21.0.143", "4.13.1"),
            ("1.21.1", "net.neoforged:neoforge:21.1.206", "4.13.1"),
            ("26.1.2", "net.neoforged:neoforge:26.1.2.72", "4.13.1"),
        ];
        let document = frozen_review()?;
        for (target, loader, antlr) in expected {
            let recipe = document
                .targets
                .iter()
                .find(|recipe| recipe.target == target)
                .ok_or_else(|| eyre::eyre!("missing target"))?;
            let (raw, inputs) = fixture(recipe)?;
            let review = review_released_native_inputs(&request(recipe), &raw, &inputs)?;
            ensure!(
                review.receipt.loader_coordinate == loader && recipe.codegen.version == antlr,
                "inferred loader/ANTLR"
            );
            ensure!(
                review.receipt.direct_version_json_artifact_index == 1
                    && review.receipt.direct_version_json_url.ends_with(&format!(
                        "/{}.json",
                        if target == "1.21.0" { "1.21" } else { target }
                    )),
                "manifest rediscovery or wrong MC alias"
            );
        }
        Ok(())
    }

    #[test]
    fn frozen_dynamic_data_bundle_and_transitive_roles_survive() -> Result<()> {
        let document = frozen_review()?;
        for recipe in &document.targets {
            let (raw, inputs) = fixture(recipe)?;
            let review = review_released_native_inputs(&request(recipe), &raw, &inputs)?;
            let dynamic = review
                .original_lock
                .dependencies
                .iter()
                .filter(|row| row.dynamic_version)
                .collect::<Vec<_>>();
            ensure!(
                dynamic.len() == if recipe.target == "1.19.2" { 6 } else { 0 }
                    && dynamic
                        .iter()
                        .all(|row| !row.resolved_notation.contains('+')),
                "dynamic resolution drift"
            );
            let excluded = review
                .dependencies
                .iter()
                .filter(|row| row.data_run_policy == "exclude")
                .collect::<Vec<_>>();
            if recipe.target == "1.19.2" {
                ensure!(
                    excluded.len() == 1
                        && excluded[0].requested_coordinate
                            == "curse.maven:mouse-tweaks-60089:3871353",
                    "Data exclusion drift"
                );
            } else {
                ensure!(excluded.is_empty(), "unreviewed Data exclusion");
            }
            let transitive = review
                .dependencies
                .iter()
                .filter(|row| row.configuration == "transitiveRuntime")
                .count();
            ensure!(
                transitive == usize::from(matches!(recipe.target.as_str(), "1.21.1" | "26.1.2")),
                "GuideMe row lost"
            );
            if recipe.target == "26.1.2" {
                let row = review
                    .dependencies
                    .iter()
                    .find(|row| row.configuration == "jarJar")
                    .ok_or_else(|| eyre::eyre!("missing bundle"))?;
                ensure!(
                    row.bundle
                        .as_ref()
                        .is_some_and(|bundle| bundle.accepted_version_range == "[4.13.1]"
                            && bundle.artifact_version == "4.13.1"
                            && !bundle.is_obfuscated),
                    "bundle drift"
                );
                ensure!(
                    review.original_lock.artifacts[18]
                        .source_relative_path
                        .is_some()
                        && review.legacy_lock.artifacts[18].source_relative_path
                            == review.original_lock.artifacts[18].source_relative_path,
                    "source path lost"
                );
            }
        }
        Ok(())
    }

    #[test]
    fn unknown_refresh_context_and_changed_inputs_fail_closed() -> Result<()> {
        let document = frozen_review()?;
        let recipe = &document.targets[1];
        let (raw, inputs) = fixture(recipe)?;
        let mut wrong = request(recipe);
        wrong.refresh = true;
        ensure!(
            review_released_native_inputs(&wrong, &raw, &inputs)
                .unwrap_err()
                .to_string()
                .contains("immutable"),
            "refresh accepted"
        );
        wrong = request(recipe);
        wrong.target_id = "unknown";
        ensure!(
            review_released_native_inputs(&wrong, &raw, &inputs).is_err(),
            "unknown target accepted"
        );
        wrong = request(recipe);
        wrong.java_major = 21;
        ensure!(
            review_released_native_inputs(&wrong, &raw, &inputs).is_err(),
            "wrong Java accepted"
        );
        wrong = request(recipe);
        wrong.minecraft_version = "1.19.2";
        ensure!(
            review_released_native_inputs(&wrong, &raw, &inputs).is_err(),
            "wrong MC accepted"
        );
        wrong = request(recipe);
        wrong.recipe_id = "rust-toolchain";
        ensure!(
            review_released_native_inputs(&wrong, &raw, &inputs).is_err(),
            "fake profile accepted"
        );
        let mut changed = raw.clone();
        changed.push(b'\n');
        ensure!(
            review_released_native_inputs(&request(recipe), &changed, &inputs)
                .unwrap_err()
                .to_string()
                .contains("raw hash/length"),
            "normalization accepted"
        );
        let properties = recipe
            .role_input_hashes
            .iter()
            .find(|row| row.path.ends_with("/gradle.properties"))
            .ok_or_else(|| eyre::eyre!("missing properties"))?;
        let mut changed_inputs = inputs.clone();
        let text =
            std::str::from_utf8(&changed_inputs[&properties.path])?.replace("45.0.9", "45.0.42");
        changed_inputs.insert(properties.path.clone(), text.into_bytes());
        ensure!(
            review_released_native_inputs(&request(recipe), &raw, &changed_inputs)
                .unwrap_err()
                .to_string()
                .contains("role-input hash"),
            "Forge substitute accepted"
        );
        changed_inputs = inputs.clone();
        changed_inputs.remove(&properties.path);
        ensure!(
            review_released_native_inputs(&request(recipe), &raw, &changed_inputs).is_err(),
            "missing role accepted"
        );
        changed_inputs = inputs.clone();
        changed_inputs.insert("arbitrary/override.gradle".to_owned(), b"override".to_vec());
        ensure!(
            review_released_native_inputs(&request(recipe), &raw, &changed_inputs).is_err(),
            "extra role accepted"
        );
        Ok(())
    }

    #[test]
    fn absent_and_ambiguous_artifacts_have_precise_errors() -> Result<()> {
        let document = frozen_review()?;
        let recipe = &document.targets[0];
        let raw = fixture_lock_bytes(recipe)?;
        let role = &recipe.recorded_dependency_roles[0];
        let mut lock = parse_bound_lock(recipe, &raw)?;
        lock.artifacts
            .push(lock.artifacts[role.artifact_index].clone());
        ensure!(
            check_recorded_binding(&lock, role, 0)
                .unwrap_err()
                .to_string()
                .contains("ambiguous coordinate/cache"),
            "duplicate binding accepted"
        );
        lock = parse_bound_lock(recipe, &raw)?;
        lock.artifacts[role.artifact_index].coordinate = None;
        ensure!(
            check_recorded_binding(&lock, role, 0)
                .unwrap_err()
                .to_string()
                .contains("unavailable or ambiguous"),
            "missing binding accepted"
        );
        lock = parse_bound_lock(recipe, &raw)?;
        lock.dependencies[0].resolved_notation = "org.spongepowered:mixin:0.8.5:unknown".to_owned();
        ensure!(
            check_recorded_binding(&lock, role, 0).is_err(),
            "changed resolution accepted"
        );
        Ok(())
    }

    #[test]
    fn weak_metadata_and_local_checksums_cannot_bless_bytes() -> Result<()> {
        let document = frozen_review()?;
        let recipe = document
            .targets
            .iter()
            .find(|recipe| recipe.target == "26.1.2")
            .ok_or_else(|| eyre::eyre!("missing target"))?;
        let (raw, inputs) = fixture(recipe)?;
        let review = review_released_native_inputs(&request(recipe), &raw, &inputs)?;
        let requirement = &review.exact_byte_requirements()[0];
        ensure!(
            requirement.original_content_hash == "blake3:fa5d96536fb3195b1a113d1d9e30695927d76378"
                && requirement.source_commit == "f33ff1f438caa55d58ef1f0a08091997353afcb8",
            "weak requirement not frozen"
        );
        let evidence = BTreeMap::from([(
            18usize,
            b"modId=\"mekanism\"\nversion=\"10.8.0\"\n".to_vec(),
        )]);
        let error = review
            .require_exact_bytes(&evidence)
            .unwrap_err()
            .to_string();
        ensure!(
            error.contains("exact-byte mismatch") && error.contains("original frozen pin"),
            "{error}"
        );
        let extra = BTreeMap::from([(19usize, b"arbitrary API bytes".to_vec())]);
        ensure!(
            prepare_released_native_inputs(&request(recipe), &raw, &inputs, &extra)
                .unwrap_err()
                .to_string()
                .contains("outside"),
            "extra acceptance route"
        );
        Ok(())
    }

    #[test]
    #[ignore = "requires an explicitly supplied existing artifact; never acquires or rebuilds it"]
    fn exact_original_26_artifact_prepares_the_released_input_boundary() -> Result<()> {
        use crate::source_projection::prepared_dependency_inputs::PreparedDependencyInputs;

        let artifact_path = std::env::var_os("SFM_EXACT_RELEASED_MEKANISM_26_JAR")
            .ok_or_else(|| eyre::eyre!("supply SFM_EXACT_RELEASED_MEKANISM_26_JAR explicitly"))?;
        let artifact_bytes = read_bounded(Path::new(&artifact_path), 16 * 1024 * 1024)?;
        let document = frozen_review()?;
        let recipe = document
            .targets
            .iter()
            .find(|recipe| recipe.target == "26.1.2")
            .ok_or_else(|| eyre::eyre!("missing reviewed 26.1.2 recipe"))?;
        let (raw, inputs) = fixture(recipe)?;
        let review = review_released_native_inputs(&request(recipe), &raw, &inputs)?;
        ensure!(
            review.exact_byte_requirements().len() == 1,
            "weak evidence scope changed"
        );
        let artifact_index = review.exact_byte_requirements()[0].artifact_index;
        let prepared = PreparedDependencyInputs::from_released(
            review
                .require_exact_bytes(&BTreeMap::from([(artifact_index, artifact_bytes.clone())]))?,
            false,
        )?;
        ensure!(
            prepared.receipt().target_id == "26.1.2"
                && prepared.receipt().immutable_source_lock
                && !prepared.receipt().live_pom_discovery
                && prepared.receipt().compilation == "not_performed"
                && prepared.raw_source_lock_bytes() == raw,
            "pure exact-byte preparation altered lock or execution boundary"
        );
        let coordinate = prepared.original_lock().artifacts[artifact_index]
            .coordinate
            .as_deref()
            .ok_or_else(|| eyre::eyre!("source-built Mekanism coordinate disappeared"))?;
        let artifact_request = prepared.coordinate(coordinate, false)?;
        ensure!(
            artifact_request.original_artifact_index() == artifact_index,
            "exact request was rebound"
        );
        prepared.verify_artifact_bytes(&artifact_request, &artifact_bytes, false)?;
        let mut wrong = artifact_bytes;
        wrong[0] ^= 1;
        ensure!(
            prepared
                .verify_artifact_bytes(&artifact_request, &wrong, false)
                .is_err(),
            "original frozen hash did not reject changed bytes"
        );
        prepared.verify_source_lock_bytes(&raw, false)?;
        Ok(())
    }

    #[test]
    fn unsupported_roles_and_unsafe_paths_fail_closed() -> Result<()> {
        ensure!(
            prepared_role(
                None,
                "customRuntime",
                "a:b:1",
                "a:b:1",
                0,
                "hash",
                "plain",
                "include",
                None
            )
            .is_err(),
            "custom config"
        );
        ensure!(
            prepared_role(
                None,
                "implementation",
                "a:b:1",
                "a:b:1",
                0,
                "hash",
                "dynamic",
                "include",
                None
            )
            .is_err(),
            "custom treatment"
        );
        ensure!(
            prepared_role(
                None,
                "implementation",
                "a:b:1",
                "a:b:1",
                0,
                "hash",
                "plain",
                "maybe",
                None
            )
            .is_err(),
            "custom Data policy"
        );
        for path in ["../escape", "C:/escape", "/escape", "a//b", "a\\b", "a/./b"] {
            ensure!(
                check_relative_path(path).is_err(),
                "unsafe path accepted: {path}"
            );
        }
        ensure!(
            check_cache_placeholder("$sfm-cache/maven/a/b.jar").is_ok(),
            "cache placeholder rejected"
        );
        ensure!(
            check_cache_placeholder("arbitrary/cache.jar").is_err(),
            "arbitrary cache accepted"
        );
        Ok(())
    }
}
