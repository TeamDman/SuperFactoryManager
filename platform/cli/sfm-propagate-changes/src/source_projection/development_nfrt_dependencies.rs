//! Exact schema-4 requests for captured-development `NeoForm` preparation.
//!
//! Retains the raw source lock and effective profile without constructing a
//! schema-2 recipe. Construction and lookup have no filesystem, acquisition,
//! cache, metadata-discovery or process effects. The source owner must remain
//! alive and be freshly checked by later acquisition/execution gates.

use super::nfrt_project_owner::DevelopmentNfrtSourceOwner;
use super::nfrt_project_owner::NfrtProjectOwner;
use super::provenance::sha256;
use crate::jar_build::hash::ContentHash;
use crate::toolchain_lockfile_schema::ToolchainLockfileDocument;
use crate::toolchain_lockfile_schema::parse_document;
use crate::toolchain_lockfile_schema::version::v3::ArtifactLockfileV3;
use crate::toolchain_lockfile_schema::version::v3::ArtifactProvenanceV3;
use crate::toolchain_lockfile_schema::version::v3::ArtifactV3;
use eyre::Result;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeMap;

const MAX_ARTIFACT_BYTES: usize = 512 * 1024 * 1024;

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct DevelopmentNfrtDependencyReceipt {
    pub schema: String,
    pub target_id: String,
    pub minecraft_version: String,
    pub dependency_profile: String,
    pub source_preparation_identity: String,
    pub source_lock_sha256: String,
    pub source_lock_bytes: usize,
    pub effective_profile_sha256: String,
    pub request_catalog_identity: String,
    pub artifact_rows: usize,
    pub coordinate_keys: usize,
    pub transport_url_keys: usize,
}

/// An opaque catalog-bound request, not a caller-supplied acquisition recipe.
#[derive(Clone, Debug, Eq, PartialEq)]
pub(crate) struct DevelopmentNfrtArtifactRequest {
    catalog_identity: String,
    artifact_index: usize,
}

impl DevelopmentNfrtArtifactRequest {
    pub(crate) fn artifact_index(&self) -> usize {
        self.artifact_index
    }
}

#[derive(Clone, Debug)]
pub(crate) struct DevelopmentNfrtDependencies {
    raw_lock: Vec<u8>,
    effective: ArtifactLockfileV3,
    coordinates: BTreeMap<String, usize>,
    transport_urls: BTreeMap<String, usize>,
    receipt: DevelopmentNfrtDependencyReceipt,
}

impl DevelopmentNfrtDependencies {
    pub(crate) fn from_checked(
        owner: &DevelopmentNfrtSourceOwner<'_>,
        refresh: bool,
    ) -> Result<Self> {
        ensure!(
            !refresh,
            "development NeoForm requests refuse refresh before effects"
        );
        owner.recheck_source()?;
        let selected = owner.project().selected_input("sfm-toolchain.lock.json")?;
        let raw_lock = selected.output_bytes().to_vec();
        let ToolchainLockfileDocument::V4(lock) = parse_document(std::str::from_utf8(&raw_lock)?)?
        else {
            eyre::bail!("development NeoForm requests require their actual schema-4 lock");
        };
        let target = &owner.target().receipt;
        ensure!(
            !lock.policy.allow_local_artifact_cache && sha256(&raw_lock) == target.lockfile_sha256,
            "development NeoForm requests lost their exact immutable source lock"
        );
        let effective = lock.effective_lockfile(&target.dependency_profile)?;
        let effective_profile_sha256 = sha256(facet_json::to_string(&effective)?.as_bytes());
        ensure!(
            effective_profile_sha256 == target.effective_dependencies_sha256,
            "development NeoForm request profile differs from its checked source owner"
        );
        let mut coordinates = BTreeMap::new();
        let mut transport_urls = BTreeMap::new();
        for (index, pin) in effective.artifacts.iter().enumerate() {
            // Derived pipeline outputs are not external acquisition authority.
            if pin.provenance == ArtifactProvenanceV3::ToolchainGenerated {
                continue;
            }
            if let Some(coordinate) = &pin.coordinate {
                ensure!(
                    coordinates.insert(coordinate.clone(), index).is_none(),
                    "ambiguous captured-development artifact coordinate: {coordinate}"
                );
            }
            if matches!(
                pin.provenance,
                ArtifactProvenanceV3::RemoteMaven | ArtifactProvenanceV3::RemoteHttp
            ) {
                let url = pin
                    .url
                    .as_deref()
                    .ok_or_else(|| eyre::eyre!("development remote artifact has no exact URL"))?;
                ensure!(
                    transport_urls.insert(url.to_owned(), index).is_none(),
                    "ambiguous captured-development transport URL: {url}"
                );
            }
        }
        let source_preparation_identity = owner.preparation_identity()?;
        let request_catalog_identity = sha256(
            facet_json::to_string(&[
                "sfm:development_nfrt_request_catalog@1",
                source_preparation_identity.as_str(),
                target.lockfile_sha256.as_str(),
                target.dependency_profile.as_str(),
                effective_profile_sha256.as_str(),
            ])?
            .as_bytes(),
        );
        let receipt = DevelopmentNfrtDependencyReceipt {
            schema: "sfm:development_nfrt_dependency_inputs@1".to_owned(),
            target_id: target.target_id.clone(),
            minecraft_version: target.minecraft_version.clone(),
            dependency_profile: target.dependency_profile.clone(),
            source_preparation_identity,
            source_lock_sha256: target.lockfile_sha256.clone(),
            source_lock_bytes: raw_lock.len(),
            effective_profile_sha256,
            request_catalog_identity,
            artifact_rows: effective.artifacts.len(),
            coordinate_keys: coordinates.len(),
            transport_url_keys: transport_urls.len(),
        };
        owner.recheck_source()?;
        Ok(Self {
            raw_lock,
            effective,
            coordinates,
            transport_urls,
            receipt,
        })
    }

    pub(crate) fn receipt(&self) -> &DevelopmentNfrtDependencyReceipt {
        &self.receipt
    }

    pub(crate) fn raw_source_lock_bytes(&self) -> &[u8] {
        &self.raw_lock
    }

    pub(crate) fn effective_profile(&self) -> &ArtifactLockfileV3 {
        &self.effective
    }

    pub(crate) fn version_json_request(&self) -> Result<DevelopmentNfrtArtifactRequest> {
        let suffix = format!("/{}.json", self.receipt.minecraft_version);
        let matches = self
            .effective
            .artifacts
            .iter()
            .filter(|pin| {
                pin.coordinate.is_none()
                    && pin.owner.as_ref().is_some_and(|owner| {
                        owner.dependency_id == self.effective.platform.minecraft_dependency
                    })
                    && pin.provenance == ArtifactProvenanceV3::RemoteHttp
                    && pin.url.as_deref().is_some_and(|url| url.ends_with(&suffix))
            })
            .collect::<Vec<_>>();
        ensure!(
            matches.len() == 1,
            "development profile requires one exact Minecraft version JSON pin"
        );
        self.exact_url(
            matches[0].url.as_deref().expect("selected exact URL"),
            false,
        )
    }

    pub(crate) fn coordinate(
        &self,
        coordinate: &str,
        refresh: bool,
    ) -> Result<DevelopmentNfrtArtifactRequest> {
        ensure!(
            !refresh,
            "development NeoForm requests refuse refresh before effects"
        );
        let index = self.coordinates.get(coordinate).copied().ok_or_else(|| {
            eyre::eyre!(
                "coordinate is outside the captured-development request catalog: {coordinate}"
            )
        })?;
        Ok(self.request(index))
    }

    pub(crate) fn exact_url(
        &self,
        url: &str,
        refresh: bool,
    ) -> Result<DevelopmentNfrtArtifactRequest> {
        ensure!(
            !refresh,
            "development NeoForm requests refuse refresh before effects"
        );
        let index = self.transport_urls.get(url).copied().ok_or_else(|| {
            eyre::eyre!("URL is outside the captured-development transport catalog: {url}")
        })?;
        Ok(self.request(index))
    }

    pub(crate) fn artifact_pin(
        &self,
        request: &DevelopmentNfrtArtifactRequest,
    ) -> Result<&ArtifactV3> {
        ensure!(
            request.catalog_identity == self.receipt.request_catalog_identity,
            "development NeoForm artifact request belongs to another source/profile catalog"
        );
        let pin = self
            .effective
            .artifacts
            .get(request.artifact_index)
            .ok_or_else(|| eyre::eyre!("development NeoForm artifact index is unavailable"))?;
        ensure!(
            pin.provenance != ArtifactProvenanceV3::ToolchainGenerated,
            "derived pipeline output is not an external artifact request"
        );
        Ok(pin)
    }

    pub(crate) fn verify_artifact_bytes(
        &self,
        request: &DevelopmentNfrtArtifactRequest,
        bytes: &[u8],
        refresh: bool,
    ) -> Result<()> {
        ensure!(
            !refresh,
            "development NeoForm requests refuse refresh before effects"
        );
        let pin = self.artifact_pin(request)?;
        ensure!(
            !bytes.is_empty() && bytes.len() <= MAX_ARTIFACT_BYTES,
            "development NeoForm artifact bytes are empty or exceed their limit"
        );
        ensure!(
            ContentHash::from_bytes(bytes, pin.hash.algorithm) == pin.hash,
            "development NeoForm artifact exact-byte identity mismatch; metadata cannot override its pin"
        );
        Ok(())
    }

    pub(crate) fn recheck_source(&self, owner: &DevelopmentNfrtSourceOwner<'_>) -> Result<()> {
        owner.recheck_source()?;
        ensure!(
            owner.preparation_identity()? == self.receipt.source_preparation_identity
                && owner
                    .project()
                    .selected_input("sfm-toolchain.lock.json")?
                    .output_bytes()
                    == self.raw_lock,
            "development NeoForm dependency catalog lost its source/profile owner"
        );
        Ok(())
    }

    fn request(&self, artifact_index: usize) -> DevelopmentNfrtArtifactRequest {
        DevelopmentNfrtArtifactRequest {
            catalog_identity: self.receipt.request_catalog_identity.clone(),
            artifact_index,
        }
    }
}
