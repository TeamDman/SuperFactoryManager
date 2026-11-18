use super::CatalogReleaseAction;
use crate::curseforge::CurseforgeGameVersion;
use crate::curseforge::CurseforgeGameVersionId;
use crate::source_projection::provenance::sha256;
use eyre::Result;
use eyre::ensure;
use std::collections::BTreeSet;

/// A supplied offline provider catalog and explicit create/amend intent.
/// The catalog is an observation, not a claim that remote state is current.
#[derive(Debug)]
pub struct CatalogReleaseProviderRequest {
    action: CatalogReleaseAction,
    game_versions: Vec<CurseforgeGameVersion>,
    catalog_sha256: String,
}

impl CatalogReleaseProviderRequest {
    /// # Errors
    /// Rejects oversized, malformed or ambiguous offline provider observations.
    pub fn from_bytes(action: CatalogReleaseAction, bytes: &[u8]) -> Result<Self> {
        ensure!(
            bytes.len() <= 8 * 1024 * 1024,
            "offline game-version catalog exceeds 8 MiB"
        );
        let game_versions: Vec<CurseforgeGameVersion> = facet_json::from_slice(bytes)?;
        ensure!(
            !game_versions.is_empty() && game_versions.len() <= 50_000,
            "offline game-version catalog has invalid row count"
        );
        let mut ids = BTreeSet::new();
        for row in &game_versions {
            ensure!(
                row.id.0 > 0 && ids.insert(row.id),
                "offline game-version catalog repeats or omits an ID"
            );
            ensure!(
                !row.name.is_empty()
                    && row.name.len() <= 256
                    && !row.name.chars().any(char::is_control),
                "invalid offline game-version name"
            );
        }
        Ok(Self {
            action,
            game_versions,
            catalog_sha256: sha256(bytes),
        })
    }

    pub(super) fn action(&self) -> &CatalogReleaseAction {
        &self.action
    }
    pub(super) fn catalog_sha256(&self) -> &str {
        &self.catalog_sha256
    }

    pub(super) fn resolve_names(&self, names: &[String]) -> Result<Vec<CurseforgeGameVersionId>> {
        CurseforgeGameVersionId::resolve_game_version_ids(
            &CurseforgeGameVersionId::build_index(&self.game_versions),
            names,
        )
    }
}
