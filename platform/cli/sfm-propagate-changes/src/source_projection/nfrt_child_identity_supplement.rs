//! Authorized exact child-tool identity supplement for six released NFRT recipes.
//!
//! This module performs no filesystem,
//! process, network, cache, acquisition or lock-writing work. The original
//! schema-2 rows remain separate and unchanged. Construction proves target,
//! recipe and original-parent pin bindings; it does NOT seal the external Java
//! downloader or authorize native execution. Callers must recheck owned source
//! inputs and verify parent and child bytes before any later external use.
//!
//! Pins are a separate reviewed core-owned input, not replacements for the
//! original lockfiles. Identity approval does not enable native execution.

use super::development_nfrt_dependencies::DevelopmentNfrtDependencies;
use super::prepared_dependency_inputs::PreparedDependencyInputs;
use super::prepared_dependency_inputs::PreparedDependencyReceipt;
use super::provenance::sha256;
use eyre::Result;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeSet;
use std::io::Cursor;
use std::io::Read;
use std::sync::Arc;

const MANIFEST_PATH: &str =
    "platform/minecraft/core-liquid-template/build/supplements/nfrt-child-identities.json";
const SOURCE_LIBRARIES_PATH: &str =
    "platform/minecraft/core-liquid-template/build/supplements/nfrt-source-library-identities.json";
#[cfg(test)]
const MANIFEST: &str = include_str!(
    "../../../../../platform/minecraft/core-liquid-template/build/supplements/nfrt-child-identities.json"
);
const MANIFEST_SHA256: &str =
    "sha256:be8fbfd87a20f6ba018056f651f3f3d2bec054a0c19424c89127ae2c75311b6f";
#[cfg(test)]
const SOURCE_LIBRARIES: &str = include_str!(
    "../../../../../platform/minecraft/core-liquid-template/build/supplements/nfrt-source-library-identities.json"
);
const SOURCE_LIBRARIES_SHA256: &str =
    "sha256:986a253497f049fb22ca354f58aabe269651b90d3d734a99f5d46a3830c1ebf3";
#[cfg(test)]
const OFFICIAL_PROOF: &str =
    include_str!("../../../../../docs/tasks/sfm-core-nfrt-child-official-identity-proof.json");
const OFFICIAL_PROOF_SHA256: &str =
    "sha256:48c66c4ef25a2906c8fcbf60562b6ae5ab88a86c0e3f8f930df65efc4ea8870c";
const INVENTORY_SHA256: &str = "8a309479fadc1f1580a98e9269263689efb4294fb98055d64ae8751c34e33e1d";
const PARENT_PROOF_SHA256: &str =
    "45ddda335ea4624b9a873ddf1ceb53f7265f2d8bbb48bb2bd97d0e903fdab785";
const MAX_MANIFEST_BYTES: usize = 128 * 1024;
const MAX_PARENT_BYTES: usize = 128 * 1024 * 1024;
const MAX_PARENT_ENTRY_BYTES: u64 = 1024 * 1024;
const MAX_CHILD_BYTES: usize = 64 * 1024 * 1024;
const TARGETS: [&str; 6] = ["1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2"];

#[derive(Clone, Debug, Facet)]
#[facet(deny_unknown_fields)]
struct SupplementDocument {
    schema: String,
    status: String,
    authorization: String,
    inventory_sha256: String,
    parent_proof_sha256: String,
    official_proof_path: String,
    targets: Vec<TargetRecipe>,
    artifacts: Vec<ChildIdentity>,
    immutable_original_lock: bool,
    native_execution_enabled: bool,
}

#[derive(Clone, Debug, Facet)]
#[facet(deny_unknown_fields)]
struct SourceLibraryDocument {
    schema: String,
    authorization: String,
    original_tool_supplement_sha256: String,
    artifacts: Vec<SourceLibraryIdentity>,
}

#[derive(Clone, Debug, Facet)]
#[facet(deny_unknown_fields)]
struct SourceLibraryIdentity {
    coordinate: String,
    bytes: usize,
    sha256: String,
    official_url: String,
    official_checksum_url: String,
    checksum_algorithm: String,
    checksum_value: String,
    checksum_body_sha256: String,
    targets: Vec<String>,
}

#[derive(Clone, Debug, Facet)]
#[facet(deny_unknown_fields)]
struct TargetRecipe {
    target_id: String,
    minecraft_version: String,
    recipe_id: String,
    source_lock_sha256: String,
    source_lock_bytes: usize,
    compiler_release: u16,
    minimum_tool_jvm: u16,
    parents: Vec<ParentIdentity>,
    source_tool_coordinates: Vec<String>,
    function_classpaths: Vec<FunctionClasspath>,
}

#[derive(Clone, Debug, Facet)]
#[facet(deny_unknown_fields)]
struct ParentIdentity {
    coordinate: String,
    original_content_hash: String,
    bytes: usize,
    sha256: String,
    entry: String,
    entry_sha256: String,
}

#[derive(Clone, Debug, Facet)]
#[facet(deny_unknown_fields)]
struct FunctionClasspath {
    node: String,
    coordinates: Vec<String>,
}

#[derive(Clone, Debug, Facet)]
#[facet(deny_unknown_fields)]
struct ChildIdentity {
    coordinate: String,
    bytes: usize,
    sha256: String,
    official_url: String,
    official_checksum_url: String,
    targets: Vec<String>,
    request_witnesses: Vec<RequestWitness>,
}

#[derive(Clone, Debug, Facet)]
#[facet(deny_unknown_fields)]
struct RequestWitness {
    target_id: String,
    parent_coordinate: String,
    parent_original_content_hash: String,
    parent_sha256: String,
    entry: String,
    entry_sha256: String,
    property: Option<String>,
    json_paths: Vec<String>,
    selected_source_nodes: String,
}

#[derive(Debug, Facet)]
struct OfficialProof {
    schema: String,
    artifacts: Vec<VerifiedArtifactProof>,
}

#[derive(Debug, Facet)]
struct VerifiedArtifactProof {
    coordinate: String,
    targets: Vec<String>,
    observed_cache: CacheObservation,
    official_artifact_url: String,
    official_checksum: OfficialChecksum,
}

#[derive(Debug, Facet)]
struct CacheObservation {
    bytes: usize,
    sha256: String,
}

#[derive(Debug, Facet)]
struct OfficialChecksum {
    coordinate: String,
    url: String,
    http_status: u16,
    checksum_sha256: String,
    checksum_body_sha256: String,
    matches: bool,
    redirect_location: String,
    observed_at: String,
}

/// Input identity evidence only. No execution capability follows from it.
#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub struct NfrtChildSupplementReceipt {
    pub schema: String,
    pub target_id: String,
    pub minecraft_version: String,
    pub recipe_id: String,
    pub source_lock_sha256: String,
    pub original_request_catalog_identity: String,
    pub supplement_sha256: String,
    pub source_library_supplement_sha256: String,
    pub official_proof_sha256: String,
    pub inventory_sha256: String,
    pub parent_proof_sha256: String,
    pub supplement_identity: String,
    pub source_tool_coordinates: Vec<String>,
    pub compiler_release: u16,
    pub minimum_tool_jvm: u16,
    pub original_lock_immutable: bool,
    pub parent_bytes_verification: String,
    pub native_execution_enabled: bool,
}

/// Target-bound exact request; callers cannot replace coordinates or pins.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct NfrtChildRequest {
    supplement_identity: String,
    artifact_index: usize,
    coordinate: String,
    exact_url: String,
    sha256: String,
    bytes: usize,
}

impl NfrtChildRequest {
    #[must_use]
    pub fn coordinate(&self) -> &str {
        &self.coordinate
    }

    #[must_use]
    pub fn exact_url(&self) -> &str {
        &self.exact_url
    }

    #[must_use]
    pub fn sha256(&self) -> &str {
        &self.sha256
    }

    #[must_use]
    pub fn bytes(&self) -> usize {
        self.bytes
    }
}

/// Evidence that caller-supplied parent bytes and its exact entry were checked.
/// It is deliberately not a subprocess permission or a reusable cache path.
#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub struct NfrtParentByteReceipt {
    pub supplement_identity: String,
    pub coordinate: String,
    pub original_content_hash: String,
    pub bytes: usize,
    pub sha256: String,
    pub entry: String,
    pub entry_sha256: String,
}

/// Private immutable catalog bound to an already strict original input set.
#[derive(Clone, Debug)]
pub struct NfrtChildIdentitySupplement {
    original: SupplementOriginal,
    document: SupplementDocument,
    source_libraries: SourceLibraryDocument,
    target_index: usize,
    receipt: NfrtChildSupplementReceipt,
}

#[derive(Clone, Debug)]
enum SupplementOriginal {
    Released(Arc<PreparedDependencyInputs>),
    Development(Arc<DevelopmentNfrtDependencies>),
}

impl SupplementOriginal {
    fn verify_parent(&self, coordinate: &str, bytes: &[u8]) -> Result<()> {
        match self {
            Self::Released(inputs) => {
                inputs.verify_artifact_bytes(&inputs.coordinate(coordinate, false)?, bytes, false)
            }
            Self::Development(inputs) => {
                inputs.verify_artifact_bytes(&inputs.coordinate(coordinate, false)?, bytes, false)
            }
        }
    }
}

impl NfrtChildIdentitySupplement {
    /// Bind the separately authorized pins without altering original rows.
    ///
    /// # Errors
    /// Refuses refresh, other recipes, changed raw locks, parent pin mismatch,
    /// malformed/ambiguous supplement data or a changed official proof before
    /// any caller can perform I/O. Weak original identities must already have
    /// passed the original input preparation gate; this cannot bypass it.
    pub(crate) fn from_prepared_at(
        root: &std::path::Path,
        original: Arc<PreparedDependencyInputs>,
        refresh: bool,
    ) -> Result<Self> {
        refuse_refresh(refresh)?;
        let (document, libraries) = read_configuration(root)?;
        Self::from_prepared_documents(original, document, libraries)
    }

    #[cfg(test)]
    pub fn from_prepared(original: Arc<PreparedDependencyInputs>, refresh: bool) -> Result<Self> {
        refuse_refresh(refresh)?;
        let document = reviewed_document()?;
        let source_libraries = reviewed_source_library_document()?;
        Self::from_prepared_documents(original, document, source_libraries)
    }

    fn from_prepared_documents(
        original: Arc<PreparedDependencyInputs>,
        document: SupplementDocument,
        source_libraries: SourceLibraryDocument,
    ) -> Result<Self> {
        let target_index = bound_target(&document, original.receipt())?;
        original.verify_source_lock_bytes(original.raw_source_lock_bytes(), false)?;
        for parent in &document.targets[target_index].parents {
            let request = original.coordinate(&parent.coordinate, false)?;
            ensure!(
                request.original_content_hash() == parent.original_content_hash,
                "supplement parent differs from original frozen pin: {}",
                parent.coordinate
            );
        }
        let receipt = make_receipt(&document, target_index, original.receipt())?;
        Ok(Self {
            original: SupplementOriginal::Released(original),
            document,
            source_libraries,
            target_index,
            receipt,
        })
    }

    /// Bind identical reviewed tool parents to an actual development catalog.
    /// Its raw lock, profile and request identity remain development-owned.
    pub(crate) fn from_development_at(
        root: &std::path::Path,
        original: Arc<DevelopmentNfrtDependencies>,
        refresh: bool,
    ) -> Result<Self> {
        refuse_refresh(refresh)?;
        let (document, libraries) = read_configuration(root)?;
        Self::from_development_documents(original, document, libraries)
    }

    #[cfg(test)]
    pub(crate) fn from_development(
        original: Arc<DevelopmentNfrtDependencies>,
        refresh: bool,
    ) -> Result<Self> {
        refuse_refresh(refresh)?;
        let document = reviewed_document()?;
        let source_libraries = reviewed_source_library_document()?;
        Self::from_development_documents(original, document, source_libraries)
    }

    fn from_development_documents(
        original: Arc<DevelopmentNfrtDependencies>,
        document: SupplementDocument,
        source_libraries: SourceLibraryDocument,
    ) -> Result<Self> {
        let source = original.receipt();
        let target_index = document
            .targets
            .iter()
            .position(|target| target.target_id == source.target_id)
            .ok_or_else(|| {
                eyre::eyre!("development target has no authorized NeoForm tool parents")
            })?;
        let target = &document.targets[target_index];
        ensure!(
            source.schema == "sfm:development_nfrt_dependency_inputs@1"
                && source.minecraft_version == target.minecraft_version
                && source.dependency_profile
                    == super::native_project_target::NATIVE_DEPENDENCY_PROFILE,
            "development NeoForm supplement lost its actual target/profile identity"
        );
        for parent in &target.parents {
            let request = original.coordinate(&parent.coordinate, false)?;
            ensure!(
                original.artifact_pin(&request)?.hash.to_string() == parent.original_content_hash,
                "development NeoForm parent differs from the reviewed exact tool pin: {}",
                parent.coordinate
            );
        }
        let recipe = format!("sfm:development-native-inputs/{}@1", source.target_id);
        let receipt = make_receipt_for_identity(
            &document,
            target_index,
            &recipe,
            &source.source_lock_sha256,
            &source.request_catalog_identity,
        )?;
        Ok(Self {
            original: SupplementOriginal::Development(original),
            document,
            source_libraries,
            target_index,
            receipt,
        })
    }

    #[must_use]
    pub fn receipt(&self) -> &NfrtChildSupplementReceipt {
        &self.receipt
    }

    /// Retrieve only an exact child authorized for this recipe.
    ///
    /// # Errors
    /// Rejects refresh and unknown, changed, dynamic or cross-target requests.
    pub fn coordinate(&self, coordinate: &str, refresh: bool) -> Result<NfrtChildRequest> {
        refuse_refresh(refresh)?;
        let target = &self.document.targets[self.target_index];
        if let Some((index, library)) =
            self.source_libraries
                .artifacts
                .iter()
                .enumerate()
                .find(|(_, library)| {
                    library.coordinate == coordinate && library.targets.contains(&target.target_id)
                })
        {
            return Ok(NfrtChildRequest {
                supplement_identity: self.receipt.supplement_identity.clone(),
                artifact_index: self.document.artifacts.len() + index,
                coordinate: library.coordinate.clone(),
                exact_url: library.official_url.clone(),
                sha256: library.sha256.clone(),
                bytes: library.bytes,
            });
        }
        ensure!(
            target
                .source_tool_coordinates
                .iter()
                .any(|candidate| candidate == coordinate),
            "NFRT child is not authorized for exact recipe {}: {coordinate}",
            target.recipe_id
        );
        let index = self
            .document
            .artifacts
            .iter()
            .position(|child| child.coordinate == coordinate)
            .ok_or_else(|| eyre::eyre!("authorized child has no exact identity"))?;
        Ok(self.request_at(index))
    }

    /// Retrieve only the exact official artifact URL, without discovery.
    ///
    /// # Errors
    /// Rejects refresh, sidecars, URL normalization, other versions and targets.
    pub fn exact_url(&self, url: &str, refresh: bool) -> Result<NfrtChildRequest> {
        refuse_refresh(refresh)?;
        let child = self
            .document
            .artifacts
            .iter()
            .find(|child| child.official_url == url)
            .ok_or_else(|| eyre::eyre!("NFRT child URL is not an authorized exact artifact"))?;
        self.coordinate(&child.coordinate, false)
    }

    /// Preserve the reviewed source-consumer order; do not sort/deduplicate.
    ///
    /// # Errors
    /// Rejects refresh or a broken target binding.
    pub fn source_requests(&self, refresh: bool) -> Result<Vec<NfrtChildRequest>> {
        refuse_refresh(refresh)?;
        self.document.targets[self.target_index]
            .source_tool_coordinates
            .iter()
            .map(|coordinate| self.coordinate(coordinate, false))
            .collect()
    }

    /// Separately approved source libraries, not executable function tools.
    ///
    /// # Errors
    /// Refuses refresh or any identity outside this exact target's scope.
    pub fn source_library_requests(&self, refresh: bool) -> Result<Vec<NfrtChildRequest>> {
        refuse_refresh(refresh)?;
        let target = &self.document.targets[self.target_index].target_id;
        self.source_libraries
            .artifacts
            .iter()
            .filter(|library| library.targets.contains(target))
            .map(|library| self.coordinate(&library.coordinate, false))
            .collect()
    }

    /// Exact function classpath, including ordered 26.1.2 decompiler plugins.
    ///
    /// # Errors
    /// Rejects unknown functions and caller-provided changes before any effects.
    pub fn function_requests(
        &self,
        node: &str,
        expected_coordinates: &[String],
        refresh: bool,
    ) -> Result<Vec<NfrtChildRequest>> {
        refuse_refresh(refresh)?;
        let function = self.document.targets[self.target_index]
            .function_classpaths
            .iter()
            .find(|function| function.node == node)
            .ok_or_else(|| eyre::eyre!("unreviewed NFRT source function: {node}"))?;
        ensure!(
            function.coordinates == expected_coordinates,
            "NFRT function classpath changed from the authenticated parent request"
        );
        function
            .coordinates
            .iter()
            .map(|coordinate| self.coordinate(coordinate, false))
            .collect()
    }

    /// Check supplied cache/download bytes, without opening or repairing paths.
    ///
    /// # Errors
    /// Rejects a foreign/forged request, refresh, wrong length or full SHA-256.
    pub fn verify_child_bytes(
        &self,
        request: &NfrtChildRequest,
        bytes: &[u8],
        refresh: bool,
    ) -> Result<()> {
        refuse_refresh(refresh)?;
        let expected = self.coordinate(&request.coordinate, false)?;
        ensure!(
            request == &expected,
            "NFRT child handle belongs to another supplement or was changed"
        );
        verify_exact_bytes(bytes, request.bytes, &request.sha256, MAX_CHILD_BYTES)
    }

    /// Verify an original parent and one bounded exact ZIP/JAR entry.
    ///
    /// The original `ContentHash`, full observed SHA-256, entry hash and recorded
    /// length are all checked. The entry hash binds the reviewed property/JSON
    /// request witnesses; no mutable/new parent recipe is interpreted here.
    ///
    /// # Errors
    /// Rejects unknown parents/refresh before processing supplied bytes; then
    /// rejects wrong hashes, duplicates, oversized or mismatching entries.
    pub fn verify_parent_bytes(
        &self,
        coordinate: &str,
        bytes: &[u8],
        refresh: bool,
    ) -> Result<NfrtParentByteReceipt> {
        refuse_refresh(refresh)?;
        let parent = self.document.targets[self.target_index]
            .parents
            .iter()
            .find(|parent| parent.coordinate == coordinate)
            .ok_or_else(|| eyre::eyre!("NFRT supplement has no selected original parent"))?;
        verify_exact_bytes(bytes, parent.bytes, &parent.sha256, MAX_PARENT_BYTES)?;
        self.original.verify_parent(coordinate, bytes)?;
        let entry = bounded_unique_entry(bytes, &parent.entry)?;
        ensure!(
            sha256(&entry) == format!("sha256:{}", parent.entry_sha256),
            "NFRT parent entry differs from authenticated request witness"
        );
        Ok(NfrtParentByteReceipt {
            supplement_identity: self.receipt.supplement_identity.clone(),
            coordinate: parent.coordinate.clone(),
            original_content_hash: parent.original_content_hash.clone(),
            bytes: bytes.len(),
            sha256: sha256(bytes),
            entry: parent.entry.clone(),
            entry_sha256: sha256(&entry),
        })
    }

    fn request_at(&self, index: usize) -> NfrtChildRequest {
        let child = &self.document.artifacts[index];
        NfrtChildRequest {
            supplement_identity: self.receipt.supplement_identity.clone(),
            artifact_index: index,
            coordinate: child.coordinate.clone(),
            exact_url: child.official_url.clone(),
            sha256: child.sha256.clone(),
            bytes: child.bytes,
        }
    }
}

fn read_configuration(
    root: &std::path::Path,
) -> Result<(SupplementDocument, SourceLibraryDocument)> {
    use super::core_catalog::read_bounded_catalog_input;
    let manifest = read_bounded_catalog_input(root, MANIFEST_PATH)?;
    let libraries = read_bounded_catalog_input(root, SOURCE_LIBRARIES_PATH)?;
    ensure!(
        manifest.len() <= MAX_MANIFEST_BYTES && sha256(&manifest) == MANIFEST_SHA256,
        "authorized NFRT child pins changed"
    );
    let document: SupplementDocument = facet_json::from_str(std::str::from_utf8(&manifest)?)?;
    // Exact authorized pin bytes already bind the historical publisher evidence.
    // Runtime verifies downloaded bytes against these pins, not a review document.
    validate_pinned_document(&document, None)?;
    Ok((
        document,
        source_library_document(std::str::from_utf8(&libraries)?)?,
    ))
}

#[cfg(test)]
fn reviewed_document() -> Result<SupplementDocument> {
    reviewed_document_bytes(MANIFEST, OFFICIAL_PROOF)
}

#[cfg(test)]
fn reviewed_source_library_document() -> Result<SourceLibraryDocument> {
    source_library_document(SOURCE_LIBRARIES)
}

fn source_library_document(input: &str) -> Result<SourceLibraryDocument> {
    ensure!(
        input.len() <= MAX_MANIFEST_BYTES && sha256(input.as_bytes()) == SOURCE_LIBRARIES_SHA256,
        "authorized source-library supplement changed"
    );
    let document = facet_json::from_str::<SourceLibraryDocument>(input)?;
    validate_source_library_document(&document)?;
    Ok(document)
}

fn validate_source_library_document(document: &SourceLibraryDocument) -> Result<()> {
    ensure!(
        document.schema == "sfm:nfrt_source_library_identity_supplement@1"
            && document.authorization == "exact_four_original_recipe_libraries_20261006"
            && format!("sha256:{}", document.original_tool_supplement_sha256) == MANIFEST_SHA256
            && document.artifacts.len() == 4,
        "source-library authorization scope changed"
    );
    let expected: [(&str, &[&str]); 4] = [
        ("net.neoforged:mergetool:2.0.2:api", &["1.20.3", "1.20.4"]),
        ("net.neoforged:mergetool:2.0.3:api", &["1.21.1"]),
        (
            "org.jetbrains:annotations:24.1.0",
            &["1.20.3", "1.20.4", "1.21.0", "1.21.1"],
        ),
        ("org.jetbrains:annotations:26.0.2-1", &["26.1.2"]),
    ];
    for (library, (coordinate, targets)) in document.artifacts.iter().zip(expected) {
        let parts = coordinate.split(':').collect::<Vec<_>>();
        let (base, classifier, algorithm) = if parts.len() == 4 {
            ("https://maven.neoforged.net/releases", "-api", "sha256")
        } else {
            ("https://repo.maven.apache.org/maven2", "", "sha1")
        };
        let url = format!(
            "{}/{}/{}/{}/{}-{}{classifier}.jar",
            base,
            parts[0].replace('.', "/"),
            parts[1],
            parts[2],
            parts[1],
            parts[2]
        );
        ensure!(
            library.coordinate == coordinate
                && library
                    .targets
                    .iter()
                    .map(String::as_str)
                    .eq(targets.iter().copied())
                && library.bytes > 0
                && library.bytes <= 1024 * 1024
                && valid_sha256(&library.sha256)
                && library.official_url == url
                && library.official_checksum_url == format!("{url}.{algorithm}")
                && library.checksum_algorithm == algorithm
                && valid_sha256(&library.checksum_body_sha256),
            "source-library pin/provenance or target boundary changed"
        );
        ensure!(
            if algorithm == "sha256" {
                library.checksum_value == library.sha256
            } else {
                library.checksum_value.len() == 40
                    && library
                        .checksum_value
                        .bytes()
                        .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte))
            },
            "official source-library checksum is malformed"
        );
    }
    Ok(())
}

#[cfg(test)]
fn reviewed_document_bytes(manifest: &str, official_proof: &str) -> Result<SupplementDocument> {
    ensure!(
        manifest.len() <= MAX_MANIFEST_BYTES
            && sha256(manifest.as_bytes()) == MANIFEST_SHA256
            && official_proof.len() <= MAX_MANIFEST_BYTES
            && sha256(official_proof.as_bytes()) == OFFICIAL_PROOF_SHA256,
        "authorized NFRT child identity evidence changed"
    );
    let document = facet_json::from_str::<SupplementDocument>(manifest)?;
    let proof = facet_json::from_str::<OfficialProof>(official_proof)?;
    validate_document(&document, &proof)?;
    Ok(document)
}

#[expect(
    clippy::too_many_lines,
    reason = "One fail-closed pass verifies the fixed fifteen-artifact authorization and ordered parent proofs."
)]
fn validate_pinned_document(
    document: &SupplementDocument,
    proof: Option<&OfficialProof>,
) -> Result<()> {
    ensure!(
        document.schema == "sfm:nfrt_child_identity_supplement@1"
            && document.status == "authorized_identity_only_native_disabled"
            && document.authorization == "exact_fifteen_reviewed_child_identities"
            && document.inventory_sha256 == INVENTORY_SHA256
            && document.parent_proof_sha256 == PARENT_PROOF_SHA256
            && document.official_proof_path
                == "docs/tasks/sfm-core-nfrt-child-official-identity-proof.json"
            && document.immutable_original_lock
            && !document.native_execution_enabled
            && document.artifacts.len() == 15
            && document
                .targets
                .iter()
                .map(|target| target.target_id.as_str())
                .eq(TARGETS),
        "NFRT child supplement widened its separately reviewed scope"
    );
    if let Some(proof) = proof {
        ensure!(
            proof.schema == "sfm:nfrt_child_official_identity_proof@1"
                && proof.artifacts.len() == 15,
            "official NFRT child identity proof has wrong scope"
        );
    }
    let coordinates = document
        .artifacts
        .iter()
        .map(|child| child.coordinate.as_str())
        .collect::<BTreeSet<_>>();
    ensure!(coordinates.len() == 15, "ambiguous NFRT child coordinate");
    let urls = document
        .artifacts
        .iter()
        .map(|child| child.official_url.as_str())
        .collect::<BTreeSet<_>>();
    ensure!(urls.len() == 15, "ambiguous NFRT child URL");
    if let Some(proof) = proof {
        let proof_coordinates = proof
            .artifacts
            .iter()
            .map(|row| row.coordinate.as_str())
            .collect::<BTreeSet<_>>();
        ensure!(
            proof_coordinates == coordinates,
            "official proof omitted or introduced a child"
        );
    }
    let mut occurrence_count = 0;
    for target in &document.targets {
        let upstream = if target.target_id == "1.21.0" {
            "1.21"
        } else {
            &target.target_id
        };
        ensure!(
            target.minecraft_version == upstream
                && target.recipe_id
                    == format!("sfm:released-native-inputs/4.34.0/{}@1", target.target_id)
                && valid_sha256(&target.source_lock_sha256)
                && target.source_lock_bytes > 0
                && target.parents.len() == 3
                && target.source_tool_coordinates.len()
                    == if target.target_id == "26.1.2" { 5 } else { 6 }
                && target.compiler_release
                    == if target.target_id == "26.1.2" {
                        25
                    } else if target.target_id.starts_with("1.21.") {
                        21
                    } else {
                        17
                    }
                && target.minimum_tool_jvm == if target.target_id == "26.1.2" { 25 } else { 21 },
            "NFRT recipe context/compiler/tool-JVM split changed"
        );
        ensure!(
            target
                .source_tool_coordinates
                .iter()
                .collect::<BTreeSet<_>>()
                .len()
                == target.source_tool_coordinates.len()
                && target
                    .parents
                    .iter()
                    .map(|parent| parent.coordinate.as_str())
                    .collect::<BTreeSet<_>>()
                    .len()
                    == 3,
            "duplicate source tool or parent"
        );
        for parent in &target.parents {
            ensure!(
                parent.bytes > 0
                    && parent.bytes <= MAX_PARENT_BYTES
                    && valid_sha256(&parent.sha256)
                    && valid_sha256(&parent.entry_sha256)
                    && matches!(parent.entry.as_str(), "config.json" | "tools.properties"),
                "malformed bounded original parent witness"
            );
        }
        ensure!(
            target
                .function_classpaths
                .iter()
                .map(|function| function.node.as_str())
                .collect::<BTreeSet<_>>()
                .len()
                == target.function_classpaths.len(),
            "ambiguous NFRT function classpath"
        );
        for function in &target.function_classpaths {
            ensure!(
                !function.node.is_empty()
                    && !function.coordinates.is_empty()
                    && function
                        .coordinates
                        .iter()
                        .all(|coordinate| { target.source_tool_coordinates.contains(coordinate) }),
                "unreviewed function child in source route"
            );
        }
        occurrence_count += target.source_tool_coordinates.len();
    }
    ensure!(occurrence_count == 35, "NFRT target request scope changed");
    for child in &document.artifacts {
        ensure!(
            valid_sha256(&child.sha256)
                && child.bytes > 0
                && child.bytes <= MAX_CHILD_BYTES
                && child.official_checksum_url == format!("{}.sha256", child.official_url)
                && child.targets
                    == document
                        .targets
                        .iter()
                        .filter(|target| target.source_tool_coordinates.contains(&child.coordinate))
                        .map(|target| target.target_id.clone())
                        .collect::<Vec<_>>()
                && child
                    .request_witnesses
                    .iter()
                    .map(|witness| witness.target_id.as_str())
                    .eq(child.targets.iter().map(String::as_str)),
            "child pin target membership or request witness changed"
        );
        for witness in &child.request_witnesses {
            let parent = document
                .targets
                .iter()
                .find(|target| target.target_id == witness.target_id)
                .and_then(|target| {
                    target
                        .parents
                        .iter()
                        .find(|parent| parent.coordinate == witness.parent_coordinate)
                })
                .ok_or_else(|| eyre::eyre!("child request lacks an original selected parent"))?;
            ensure!(
                parent.original_content_hash == witness.parent_original_content_hash
                    && parent.sha256 == witness.parent_sha256
                    && parent.entry == witness.entry
                    && parent.entry_sha256 == witness.entry_sha256
                    && witness.property.is_some() == witness.json_paths.is_empty()
                    && !witness.selected_source_nodes.is_empty(),
                "child request is detached from authenticated parent entry"
            );
        }
        if let Some(proof) = proof {
            let official = proof
                .artifacts
                .iter()
                .find(|row| row.coordinate == child.coordinate)
                .ok_or_else(|| eyre::eyre!("official child identity missing"))?;
            ensure!(
                official.targets == child.targets
                    && official.observed_cache.bytes == child.bytes
                    && official.observed_cache.sha256 == child.sha256
                    && official.official_artifact_url == child.official_url
                    && official.official_checksum.coordinate == child.coordinate
                    && official.official_checksum.url == child.official_checksum_url
                    && official.official_checksum.http_status == 200
                    && official.official_checksum.checksum_sha256 == child.sha256
                    && valid_sha256(&official.official_checksum.checksum_body_sha256)
                    && official.official_checksum.matches
                    && official.official_checksum.redirect_location.is_empty()
                    && !official.official_checksum.observed_at.is_empty(),
                "child SHA-256 does not match exact official publisher proof"
            );
        }
    }
    Ok(())
}

#[cfg(test)]
fn validate_document(document: &SupplementDocument, proof: &OfficialProof) -> Result<()> {
    validate_pinned_document(document, Some(proof))
}

fn bound_target(
    document: &SupplementDocument,
    original: &PreparedDependencyReceipt,
) -> Result<usize> {
    let index = document
        .targets
        .iter()
        .position(|target| target.target_id == original.target_id)
        .ok_or_else(|| eyre::eyre!("original target has no authorized NFRT child supplement"))?;
    let target = &document.targets[index];
    ensure!(
        original.minecraft_version == target.minecraft_version
            && original.recipe_id == target.recipe_id
            && original.source_lock_sha256 == format!("sha256:{}", target.source_lock_sha256)
            && original.source_lock_bytes == target.source_lock_bytes
            && original.immutable_source_lock
            && !original.live_pom_discovery,
        "NFRT supplement does not match the exact original recipe/raw-lock identity"
    );
    Ok(index)
}

fn make_receipt(
    document: &SupplementDocument,
    index: usize,
    original: &PreparedDependencyReceipt,
) -> Result<NfrtChildSupplementReceipt> {
    make_receipt_for_identity(
        document,
        index,
        &original.recipe_id,
        &original.source_lock_sha256,
        &original.request_catalog_identity,
    )
}

fn make_receipt_for_identity(
    document: &SupplementDocument,
    index: usize,
    recipe_id: &str,
    source_lock_sha256: &str,
    request_catalog_identity: &str,
) -> Result<NfrtChildSupplementReceipt> {
    let target = &document.targets[index];
    let identity = sha256(
        facet_json::to_string(&[
            "sfm:nfrt_child_supplement_identity@1",
            request_catalog_identity,
            source_lock_sha256,
            recipe_id,
            MANIFEST_SHA256,
            SOURCE_LIBRARIES_SHA256,
            OFFICIAL_PROOF_SHA256,
            INVENTORY_SHA256,
            PARENT_PROOF_SHA256,
        ])?
        .as_bytes(),
    );
    Ok(NfrtChildSupplementReceipt {
        schema: "sfm:nfrt_child_supplement_receipt@1".to_owned(),
        target_id: target.target_id.clone(),
        minecraft_version: target.minecraft_version.clone(),
        recipe_id: recipe_id.to_owned(),
        source_lock_sha256: source_lock_sha256.to_owned(),
        original_request_catalog_identity: request_catalog_identity.to_owned(),
        supplement_sha256: MANIFEST_SHA256.to_owned(),
        source_library_supplement_sha256: SOURCE_LIBRARIES_SHA256.to_owned(),
        official_proof_sha256: OFFICIAL_PROOF_SHA256.to_owned(),
        inventory_sha256: INVENTORY_SHA256.to_owned(),
        parent_proof_sha256: PARENT_PROOF_SHA256.to_owned(),
        supplement_identity: identity,
        source_tool_coordinates: target.source_tool_coordinates.clone(),
        compiler_release: target.compiler_release,
        minimum_tool_jvm: target.minimum_tool_jvm,
        original_lock_immutable: true,
        parent_bytes_verification: "required_at_consumption_not_construction".to_owned(),
        native_execution_enabled: false,
    })
}

fn verify_exact_bytes(
    bytes: &[u8],
    expected_len: usize,
    expected_sha256: &str,
    bound: usize,
) -> Result<()> {
    ensure!(
        !bytes.is_empty()
            && bytes.len() <= bound
            && bytes.len() == expected_len
            && sha256(bytes) == format!("sha256:{expected_sha256}"),
        "exact authorized artifact bytes differ in length or full SHA-256"
    );
    Ok(())
}

fn bounded_unique_entry(bytes: &[u8], name: &str) -> Result<Vec<u8>> {
    let mut archive = zip::ZipArchive::new(Cursor::new(bytes))?;
    let matches = (0..archive.len())
        .map(|index| Ok(archive.by_index(index)?.name() == name))
        .collect::<Result<Vec<_>>>()?
        .into_iter()
        .filter(|matches| *matches)
        .count();
    ensure!(matches == 1, "parent entry is missing or duplicated");
    let entry = archive.by_name(name)?;
    ensure!(
        !entry.is_dir() && entry.size() <= MAX_PARENT_ENTRY_BYTES,
        "parent entry exceeds its bounded request witness"
    );
    let mut result = Vec::new();
    entry
        .take(MAX_PARENT_ENTRY_BYTES + 1)
        .read_to_end(&mut result)?;
    ensure!(
        result.len() as u64 <= MAX_PARENT_ENTRY_BYTES,
        "parent entry grew past its bounded request witness"
    );
    Ok(result)
}

fn valid_sha256(value: &str) -> bool {
    value.len() == 64
        && value
            .bytes()
            .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte))
}

fn refuse_refresh(refresh: bool) -> Result<()> {
    ensure!(
        !refresh,
        "NFRT child supplement refuses refresh before any caller effects"
    );
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::source_projection::candidate_lock::checked_file;
    use crate::source_projection::core_slice_test_support::read_bounded;
    use crate::source_projection::released_native_inputs::ReleasedNativeRequest;
    use crate::source_projection::released_native_inputs::prepare_released_native_inputs;
    use std::cell::Cell;
    use std::collections::BTreeMap;
    use std::path::Path;

    const RECIPE_REVIEW: &str =
        include_str!("../../../../../docs/tasks/sfm-core-released-native-adapter-review.json");
    const RECIPE_REVIEW_SHA256: &str =
        "sha256:8b927a2b978715a6fc6ae1d0fd828a8a46b729a41a43a54897ca5e6cac313e21";

    #[derive(Facet)]
    struct Recipes {
        targets: Vec<Recipe>,
    }

    #[derive(Facet)]
    struct Recipe {
        target: String,
        minecraft_version: String,
        recipe_id: String,
        platform: Platform,
        source_lock: InputWitness,
        role_input_hashes: Vec<InputWitness>,
    }

    #[derive(Facet)]
    struct Platform {
        java_major: u16,
    }

    #[derive(Facet)]
    struct InputWitness {
        path: String,
    }

    fn prepared(target: &str) -> Result<Arc<PreparedDependencyInputs>> {
        ensure!(
            sha256(RECIPE_REVIEW.as_bytes()) == RECIPE_REVIEW_SHA256,
            "original recipe fixture changed"
        );
        let recipe = facet_json::from_str::<Recipes>(RECIPE_REVIEW)?
            .targets
            .into_iter()
            .find(|recipe| recipe.target == target)
            .ok_or_else(|| eyre::eyre!("unknown fixture target"))?;
        let repository = Path::new(env!("CARGO_MANIFEST_DIR"))
            .ancestors()
            .nth(3)
            .ok_or_else(|| eyre::eyre!("CLI fixture repository missing"))?;
        let raw = read_bounded(
            &checked_file(repository, &recipe.source_lock.path)?,
            1024 * 1024,
        )?;
        let roles = super::super::core_release_project_role_fixtures::release_project_role_outputs(
            repository,
            &recipe.target,
            &recipe
                .role_input_hashes
                .iter()
                .map(|input| input.path.clone())
                .collect(),
        )?;
        let request = ReleasedNativeRequest {
            target_id: &recipe.target,
            minecraft_version: &recipe.minecraft_version,
            java_major: recipe.platform.java_major,
            recipe_id: &recipe.recipe_id,
            refresh: false,
        };
        Ok(Arc::new(PreparedDependencyInputs::from_released(
            prepare_released_native_inputs(&request, &raw, &roles, &BTreeMap::new())?,
            false,
        )?))
    }

    #[test]
    fn four_source_libraries_are_target_bound_and_do_not_widen_tool_functions() -> Result<()> {
        let document = reviewed_source_library_document()?;
        ensure!(
            document.artifacts.len() == 4,
            "source library count changed"
        );
        for (target, expected) in [
            ("1.20.2", Vec::<&str>::new()),
            (
                "1.20.3",
                vec![
                    "net.neoforged:mergetool:2.0.2:api",
                    "org.jetbrains:annotations:24.1.0",
                ],
            ),
            ("1.21.0", vec!["org.jetbrains:annotations:24.1.0"]),
            (
                "1.21.1",
                vec![
                    "net.neoforged:mergetool:2.0.3:api",
                    "org.jetbrains:annotations:24.1.0",
                ],
            ),
        ] {
            let original = prepared(target)?;
            let raw = original.raw_source_lock_bytes().to_vec();
            let supplement =
                NfrtChildIdentitySupplement::from_prepared(Arc::clone(&original), false)?;
            let requests = supplement.source_library_requests(false)?;
            ensure!(
                requests
                    .iter()
                    .map(NfrtChildRequest::coordinate)
                    .eq(expected),
                "source library target scope changed: {target}"
            );
            ensure!(
                supplement.source_requests(false)?.len() == 6
                    && original.raw_source_lock_bytes() == raw,
                "tool scope or original lock changed"
            );
            for request in &requests {
                ensure!(
                    supplement
                        .verify_child_bytes(request, b"wrong bytes", false)
                        .is_err(),
                    "wrong source library bytes accepted"
                );
                ensure!(
                    supplement
                        .function_requests("merge", &[request.coordinate().to_owned()], false)
                        .is_err(),
                    "API library promoted to executable function classpath"
                );
            }
            ensure!(
                supplement.source_library_requests(true).is_err(),
                "source library refresh accepted"
            );
            ensure!(
                supplement
                    .coordinate("org.jetbrains:annotations:26.0.2-1", false)
                    .is_err(),
                "cross-target source library accepted"
            );
            ensure!(
                supplement
                    .coordinate("net.neoforged:mergetool:2.+:api", false)
                    .is_err(),
                "dynamic API coordinate accepted"
            );
        }
        Ok(())
    }

    #[test]
    fn source_library_document_refuses_scope_url_and_checksum_mutations() -> Result<()> {
        let document = reviewed_source_library_document()?;
        let mut widened = document.clone();
        widened.artifacts[0].targets.push("1.20.2".to_owned());
        ensure!(
            validate_source_library_document(&widened).is_err(),
            "widened source library scope accepted"
        );
        let mut version = document.clone();
        version.artifacts[0].coordinate = "net.neoforged:mergetool:2.0.0:api".to_owned();
        ensure!(
            validate_source_library_document(&version).is_err(),
            "another API version accepted"
        );
        let mut transport = document.clone();
        transport.artifacts[0]
            .official_url
            .push_str("?fallback=true");
        ensure!(
            validate_source_library_document(&transport).is_err(),
            "nonexact artifact URL accepted"
        );
        let mut checksum = document.clone();
        checksum.artifacts[0].checksum_value.replace_range(..1, "0");
        ensure!(
            validate_source_library_document(&checksum).is_err(),
            "mismatching official checksum accepted"
        );
        let mut short = document.clone();
        short.artifacts[0].sha256.truncate(40);
        ensure!(
            validate_source_library_document(&short).is_err(),
            "truncated source library SHA-256 accepted"
        );
        Ok(())
    }

    #[test]
    fn official_fifteen_pin_six_recipe_matrix_preserves_exact_consumer_order() -> Result<()> {
        let document = reviewed_document()?;
        ensure!(
            document.targets.len() == 6 && document.artifacts.len() == 15,
            "authorized matrix changed"
        );
        let mut requests = 0;
        for target in &document.targets {
            requests += target.source_tool_coordinates.len();
            if target.target_id == "26.1.2" {
                ensure!(
                    target.minecraft_version == "26.1.2"
                        && target.compiler_release == 25
                        && target.minimum_tool_jvm == 25
                        && target.source_tool_coordinates.len() == 5,
                    "26.1.2 structural recipe changed"
                );
                let decompile = target
                    .function_classpaths
                    .iter()
                    .find(|function| function.node == "decompile")
                    .ok_or_else(|| eyre::eyre!("26 decompiler classpath absent"))?;
                ensure!(
                    decompile.coordinates
                        == [
                            "org.vineflower:vineflower:1.11.2",
                            "net.neoforged:vineflower-plugins:0.1.5",
                        ],
                    "26 decompiler/plugin classpath was reordered"
                );
                continue;
            }
            let original = prepared(&target.target_id)?;
            let raw = original.raw_source_lock_bytes().to_vec();
            let original_receipt = original.receipt().clone();
            let supplement =
                NfrtChildIdentitySupplement::from_prepared(Arc::clone(&original), false)?;
            ensure!(
                supplement
                    .source_requests(false)?
                    .iter()
                    .map(|request| request.coordinate().to_owned())
                    .collect::<Vec<_>>()
                    == target.source_tool_coordinates,
                "supplement source order changed"
            );
            for function in &target.function_classpaths {
                let function_requests =
                    supplement.function_requests(&function.node, &function.coordinates, false)?;
                ensure!(
                    function_requests
                        .iter()
                        .map(|request| request.coordinate().to_owned())
                        .collect::<Vec<_>>()
                        == function.coordinates,
                    "function classpath changed"
                );
            }
            ensure!(
                original.raw_source_lock_bytes() == raw
                    && original.receipt() == &original_receipt
                    && !supplement.receipt().native_execution_enabled
                    && supplement.receipt().original_lock_immutable
                    && supplement.receipt().parent_bytes_verification
                        == "required_at_consumption_not_construction"
                    && supplement.receipt().minimum_tool_jvm == 21
                    && supplement.receipt().compiler_release == target.compiler_release,
                "supplement altered original inputs or claimed execution/parent-byte proof"
            );
            if target.target_id == "1.21.0" {
                ensure!(
                    supplement.receipt().minecraft_version == "1.21",
                    "stable target alias was substituted for actual upstream MC"
                );
            }
        }
        ensure!(requests == 35, "authorized occurrence count changed");
        Ok(())
    }

    #[test]
    fn four_forge_recipes_and_unresolved_original_weak_bytes_remain_refused() -> Result<()> {
        let effects = Cell::new(0);
        for target in ["1.19.2", "1.19.4", "1.20", "1.20.1"] {
            if NfrtChildIdentitySupplement::from_prepared(prepared(target)?, false).is_ok() {
                effects.set(effects.get() + 1);
            }
        }
        ensure!(
            effects.get() == 0,
            "unreviewed target reached caller effects"
        );
        let error = prepared("26.1.2").unwrap_err().to_string();
        ensure!(
            error.contains("identity unresolved"),
            "child approval bypassed the original Mekanism exact-byte gate: {error}"
        );
        // All six supplement rows are structurally verified above. A real
        // PreparedDependencyInputs success for 26 requires original weak-pin
        // bytes from the caller; these tests deliberately do not open a cache.
        Ok(())
    }

    #[test]
    fn unknown_changed_cross_target_and_refresh_requests_fail_before_effects() -> Result<()> {
        let original = prepared("1.20.2")?;
        ensure!(
            NfrtChildIdentitySupplement::from_prepared(Arc::clone(&original), true).is_err(),
            "refresh accepted at construction"
        );
        let supplement = NfrtChildIdentitySupplement::from_prepared(original, false)?;
        let effects = Cell::new(0);
        for coordinate in [
            "net.neoforged:mergetool:2.0.2:fatjar",
            "net.neoforged:mergetool:2.0.0",
            "net.neoforged:mergetool:2.+:fatjar",
            "net.neoforged.installertools:installertools:4.0.12:fatjar",
            "net.minecraftforge:binarypatcher:1.1.1:fatjar",
            "example:unapproved:1",
        ] {
            if supplement.coordinate(coordinate, false).is_ok() {
                effects.set(effects.get() + 1);
            }
        }
        let known = supplement.coordinate("net.neoforged:mergetool:2.0.0:fatjar", false)?;
        for url in [
            "https://example.invalid/unapproved.jar".to_owned(),
            format!("{}.sha256", known.exact_url()),
            format!("{}?mirror=other", known.exact_url()),
        ] {
            if supplement.exact_url(&url, false).is_ok() {
                effects.set(effects.get() + 1);
            }
        }
        ensure!(effects.get() == 0, "unapproved caller effects");
        ensure!(
            supplement.coordinate(known.coordinate(), true).is_err()
                && supplement.exact_url(known.exact_url(), true).is_err()
                && supplement.source_requests(true).is_err()
                && supplement
                    .verify_child_bytes(&known, b"wrong", true)
                    .is_err()
                && supplement
                    .verify_parent_bytes("net.neoforged:neoform-runtime:2.0.19:all", b"wrong", true)
                    .is_err(),
            "refresh escaped an exact request boundary"
        );
        let classpath = vec!["org.vineflower:vineflower:1.9.3".to_owned()];
        ensure!(
            supplement
                .function_requests("decompile", &classpath, true)
                .is_err()
                && supplement
                    .function_requests(
                        "decompile",
                        &["org.vineflower:vineflower:1.10.1".to_owned()],
                        false
                    )
                    .is_err()
                && supplement
                    .function_requests("unapproved_function", &classpath, false)
                    .is_err(),
            "function/classpath changed"
        );
        Ok(())
    }

    #[test]
    fn changed_raw_receipt_and_foreign_child_handles_are_refused() -> Result<()> {
        let document = reviewed_document()?;
        let original = prepared("1.20.2")?;
        let mut changed = original.receipt().clone();
        changed.source_lock_sha256 = format!("sha256:{}", "0".repeat(64));
        ensure!(
            bound_target(&document, &changed).is_err(),
            "changed raw-lock identity accepted"
        );
        changed = original.receipt().clone();
        changed.recipe_id.push_str("-other");
        ensure!(
            bound_target(&document, &changed).is_err(),
            "changed recipe accepted"
        );
        changed = original.receipt().clone();
        changed.minecraft_version = "1.21".to_owned();
        ensure!(
            bound_target(&document, &changed).is_err(),
            "changed upstream MC accepted"
        );
        let supplement = NfrtChildIdentitySupplement::from_prepared(original, false)?;
        let known = supplement.coordinate("net.neoforged.jst:jst-cli-bundle:2.0.8", false)?;
        let other = NfrtChildIdentitySupplement::from_prepared(prepared("1.20.3")?, false)?;
        let error = other
            .verify_child_bytes(&known, b"wrong", false)
            .unwrap_err();
        ensure!(
            error.to_string().contains("another supplement"),
            "cross-target handle reached byte processing"
        );
        let mut forged = known.clone();
        forged.exact_url.push_str("?override=1");
        ensure!(
            supplement
                .verify_child_bytes(&forged, b"wrong", false)
                .unwrap_err()
                .to_string()
                .contains("another supplement"),
            "forged URL accepted"
        );
        ensure!(
            supplement
                .verify_child_bytes(&known, b"wrong", false)
                .unwrap_err()
                .to_string()
                .contains("full SHA-256"),
            "wrong cache bytes accepted"
        );
        Ok(())
    }

    #[test]
    fn pure_byte_verifier_checks_full_hash_length_and_bounded_unique_entry() -> Result<()> {
        let bytes = b"synthetic verifier fixture";
        let digest = sha256(bytes);
        let raw_digest = digest
            .strip_prefix("sha256:")
            .ok_or_else(|| eyre::eyre!("digest prefix missing"))?;
        verify_exact_bytes(bytes, bytes.len(), raw_digest, 1024)?;
        ensure!(
            verify_exact_bytes(b"changed verifier fixture", bytes.len(), raw_digest, 1024).is_err()
                && verify_exact_bytes(bytes, bytes.len() + 1, raw_digest, 1024).is_err()
                && verify_exact_bytes(bytes, bytes.len(), raw_digest, bytes.len() - 1).is_err(),
            "byte verifier accepted changed/truncated/oversized data"
        );
        let mut writer = zip::ZipWriter::new(Cursor::new(Vec::new()));
        writer.start_file("config.json", zip::write::SimpleFileOptions::default())?;
        std::io::Write::write_all(&mut writer, br#"{"fixture":"identity"}"#)?;
        let archive = writer.finish()?.into_inner();
        ensure!(
            bounded_unique_entry(&archive, "config.json")? == br#"{"fixture":"identity"}"#
                && bounded_unique_entry(&archive, "tools.properties").is_err()
                && bounded_unique_entry(b"not a ZIP", "config.json").is_err(),
            "bounded parent entry verifier accepted an unavailable entry"
        );
        Ok(())
    }

    #[test]
    fn official_and_original_parent_witness_conflicts_are_not_accepted() -> Result<()> {
        let proof = facet_json::from_str::<OfficialProof>(OFFICIAL_PROOF)?;
        let mut document = reviewed_document()?;
        document.artifacts[0].sha256 = "0".repeat(64);
        ensure!(
            validate_document(&document, &proof).is_err(),
            "child pin override accepted"
        );
        document = reviewed_document()?;
        document.artifacts[0].request_witnesses[0].parent_original_content_hash =
            format!("blake3:{}", "0".repeat(40));
        ensure!(
            validate_document(&document, &proof).is_err(),
            "child detached from original parent pin"
        );
        document = reviewed_document()?;
        document.targets[5].function_classpaths[1]
            .coordinates
            .reverse();
        // Hash-bound actual MANIFEST cannot be injected through public API.
        // The independently recorded decompile order is tested above; ensure
        // any alternate serialized manifest has a different reviewed digest.
        ensure!(
            reviewed_document_bytes(&facet_json::to_string(&document)?, OFFICIAL_PROOF).is_err(),
            "modified manifest bypassed the exact raw identity gate"
        );
        ensure!(
            reviewed_document_bytes(&format!("{MANIFEST}\n"), OFFICIAL_PROOF).is_err()
                && reviewed_document_bytes(MANIFEST, &format!("{OFFICIAL_PROOF}\n")).is_err(),
            "semantically equivalent evidence normalization was accepted"
        );
        document = reviewed_document()?;
        document.native_execution_enabled = true;
        ensure!(
            validate_document(&document, &proof).is_err(),
            "supplement accidentally enabled a native launcher"
        );
        Ok(())
    }
}
