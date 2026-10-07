//! Preserve release and captured-development request authority through export.
//!
//! Neither variant converts source lock schemas or permits dynamic discovery.

use super::authenticated_minecraft_inputs::AuthenticatedMinecraftInputs;
use super::authenticated_minecraft_inputs::authenticate_pinned_version_json;
use crate::source_projection::development_nfrt_dependencies::DevelopmentNfrtArtifactRequest;
use crate::source_projection::development_nfrt_dependencies::DevelopmentNfrtDependencies;
use crate::source_projection::nfrt_project_owner::NfrtProjectOwner;
use crate::source_projection::prepared_dependency_inputs::PreparedArtifactRequest;
use crate::source_projection::prepared_dependency_inputs::PreparedDependencyInputs;
use crate::toolchain_lockfile_schema::version::v3::ArtifactV3;
use eyre::Result;
use eyre::ensure;
use std::sync::Arc;

#[derive(Clone, Debug)]
pub(crate) enum NfrtRequestCatalog {
    Released(Arc<PreparedDependencyInputs>),
    Development(Arc<DevelopmentNfrtDependencies>),
}

#[derive(Clone, Debug)]
pub(crate) enum NfrtArtifactRequest {
    Released(PreparedArtifactRequest),
    Development {
        request: DevelopmentNfrtArtifactRequest,
        pin: Box<ArtifactV3>,
        index: usize,
    },
}

impl NfrtArtifactRequest {
    pub(crate) fn resolved_coordinate(&self) -> Option<&str> {
        match self {
            Self::Released(request) => request.resolved_coordinate(),
            Self::Development { pin, .. } => pin.coordinate.as_deref(),
        }
    }

    pub(crate) fn exact_transport_url(&self) -> Option<&str> {
        match self {
            Self::Released(request) => request.exact_transport_url(),
            Self::Development { pin, .. } => match pin.provenance {
                crate::toolchain_lockfile_schema::version::v3::ArtifactProvenanceV3::RemoteMaven
                | crate::toolchain_lockfile_schema::version::v3::ArtifactProvenanceV3::RemoteHttp => pin.url.as_deref(),
                _ => None,
            },
        }
    }

    pub(crate) fn original_content_hash(&self) -> String {
        match self {
            Self::Released(request) => request.original_content_hash(),
            Self::Development { pin, .. } => pin.hash.to_string(),
        }
    }

    fn artifact_index(&self) -> usize {
        match self {
            Self::Released(request) => request.original_artifact_index(),
            Self::Development { index, .. } => *index,
        }
    }

    pub(crate) fn origin(&self) -> &'static str {
        match self {
            Self::Released(_) => "original_schema2_pin",
            Self::Development { .. } => "captured_schema4_pin",
        }
    }
}

impl NfrtRequestCatalog {
    pub(crate) fn source_pin_origin(&self) -> &'static str {
        match self {
            Self::Released(_) => "original_schema2_pin",
            Self::Development(_) => "captured_schema4_pin",
        }
    }

    /// Retain exact pin authority when the engine selects a cache/transport path.
    pub(crate) fn transport_pin(
        &self,
        request: &NfrtArtifactRequest,
    ) -> Result<(super::hash::ContentHash, bool)> {
        match (self, request) {
            (Self::Released(catalog), NfrtArtifactRequest::Released(request)) => {
                let pin = catalog.artifact_pin(request)?;
                Ok((
                    pin.hash,
                    matches!(
                        pin.source,
                        super::ArtifactSource::RemoteMaven | super::ArtifactSource::RemoteHttp
                    ),
                ))
            }
            (
                Self::Development(catalog),
                NfrtArtifactRequest::Development {
                    request,
                    pin,
                    index,
                },
            ) => {
                ensure!(
                    catalog.artifact_pin(request)? == pin.as_ref()
                        && request.artifact_index() == *index,
                    "development transport metadata lost its catalog binding"
                );
                Ok((pin.hash, matches!(pin.provenance,
                    crate::toolchain_lockfile_schema::version::v3::ArtifactProvenanceV3::RemoteMaven
                    | crate::toolchain_lockfile_schema::version::v3::ArtifactProvenanceV3::RemoteHttp)))
            }
            _ => eyre::bail!("NeoForm transport request belongs to another source authority"),
        }
    }

    pub(crate) fn coordinate(
        &self,
        coordinate: &str,
        refresh: bool,
    ) -> Result<NfrtArtifactRequest> {
        match self {
            Self::Released(catalog) => Ok(NfrtArtifactRequest::Released(
                catalog.coordinate(coordinate, refresh)?,
            )),
            Self::Development(catalog) => {
                Self::development_request(catalog, catalog.coordinate(coordinate, refresh)?)
            }
        }
    }

    pub(crate) fn exact_url(&self, url: &str, refresh: bool) -> Result<NfrtArtifactRequest> {
        match self {
            Self::Released(catalog) => Ok(NfrtArtifactRequest::Released(
                catalog.exact_url(url, refresh)?,
            )),
            Self::Development(catalog) => {
                Self::development_request(catalog, catalog.exact_url(url, refresh)?)
            }
        }
    }

    fn development_request(
        catalog: &DevelopmentNfrtDependencies,
        request: DevelopmentNfrtArtifactRequest,
    ) -> Result<NfrtArtifactRequest> {
        let pin = catalog.artifact_pin(&request)?.clone();
        let index = request.artifact_index();
        Ok(NfrtArtifactRequest::Development {
            request,
            pin: Box::new(pin),
            index,
        })
    }

    pub(crate) fn verify_artifact_bytes(
        &self,
        request: &NfrtArtifactRequest,
        bytes: &[u8],
        refresh: bool,
    ) -> Result<()> {
        match (self, request) {
            (Self::Released(catalog), NfrtArtifactRequest::Released(request)) => {
                catalog.verify_artifact_bytes(request, bytes, refresh)
            }
            (
                Self::Development(catalog),
                NfrtArtifactRequest::Development {
                    request,
                    pin,
                    index,
                },
            ) => {
                ensure!(
                    catalog.artifact_pin(request)? == pin.as_ref()
                        && request.artifact_index() == *index,
                    "development request metadata lost its catalog binding"
                );
                catalog.verify_artifact_bytes(request, bytes, refresh)
            }
            _ => eyre::bail!("NeoForm request belongs to a different source-lock authority"),
        }
    }

    pub(crate) fn recheck_source(&self, project: &dyn NfrtProjectOwner) -> Result<()> {
        project.recheck_source()?;
        let raw = match self {
            Self::Released(catalog) => catalog.raw_source_lock_bytes(),
            Self::Development(catalog) => {
                ensure!(
                    project.preparation_identity()?
                        == catalog.receipt().source_preparation_identity,
                    "development request catalog belongs to another source owner"
                );
                catalog.raw_source_lock_bytes()
            }
        };
        ensure!(
            project
                .project()
                .selected_input("sfm-toolchain.lock.json")?
                .output_bytes()
                == raw,
            "NeoForm request source lock changed"
        );
        Ok(())
    }

    pub(crate) fn authenticate_version_json(
        &self,
        minecraft: &str,
        exact_url: &str,
        bytes: &[u8],
    ) -> Result<AuthenticatedMinecraftInputs> {
        let request = self.exact_url(exact_url, false)?;
        ensure!(
            request.resolved_coordinate().is_none()
                && request.exact_transport_url() == Some(exact_url),
            "Minecraft version parent must be an exact non-Maven transport pin"
        );
        self.verify_artifact_bytes(&request, bytes, false)?;
        authenticate_pinned_version_json(
            minecraft,
            request.artifact_index(),
            exact_url,
            &request.original_content_hash(),
            bytes,
        )
    }
}
