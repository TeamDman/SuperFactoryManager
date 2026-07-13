use super::dependency_add_cli::ArtifactFetcher;
use super::dependency_add_cli::http_fetcher;
use super::dependency_add_cli::write_cache_file_atomically;
use super::dependency_context::load_inventory;
use crate::cancellation::CancellationToken;
use crate::cli::jar::BranchSelector;
use crate::curseforge::CurseforgeApiSecret;
use crate::curseforge::CurseforgeHttpClient;
use crate::curseforge::CurseforgeProjectFileId;
use crate::curseforge::CurseforgeProjectId;
use crate::curseforge::CurseforgeProjectMetadata;
use crate::dependency_inventory::DependencyInventory;
use crate::jar_build::hash::ContentHash;
use crate::jar_build::hash::ContentHashAlgorithm;
use crate::paths::CacheHome;
use crate::terminal_output::stdout_line;
use crate::toolchain_lockfile_schema::version::v3::ArtifactProvenanceV3;
use crate::toolchain_lockfile_schema::version::v3::ComponentAcquisitionV3;
use crate::toolchain_lockfile_write::write_lockfile_atomically;
use facet::Facet;
use figue as args;
use std::collections::BTreeMap;

#[derive(Facet, Debug)]
pub struct DependencyRefreshArgs {
    /// Optional dependency or dependency/component target. Omit to refresh all remote artifacts.
    #[facet(default, args::positional)]
    pub target: Option<String>,
    /// Branch selector to update. Must match exactly one worktree.
    #[facet(args::named)]
    pub branch: BranchSelector,
}

impl DependencyRefreshArgs {
    /// # Errors
    ///
    /// Returns an error when selection, acquisition, validation, or atomic writing fails.
    pub fn invoke(
        self,
        cancellation_token: &CancellationToken,
        cache_home: &CacheHome,
    ) -> eyre::Result<()> {
        cancellation_token.bail_if_cancelled()?;
        let inventory = load_inventory(self.branch, cache_home)?;
        let reports = refresh_dependencies(
            inventory,
            self.target.as_deref(),
            cancellation_token,
            &http_fetcher()?,
        )?;
        for report in &reports {
            stdout_line(format!(
                "Refreshed {}: {} -> {}",
                report.artifact_id, report.old_hash, report.new_hash
            ))?;
        }
        stdout_line(format!("Refreshed {} artifact(s).", reports.len()))?;
        Ok(())
    }
}

#[derive(Debug)]
struct RefreshReport {
    artifact_id: String,
    old_hash: ContentHash,
    new_hash: ContentHash,
}

struct RefreshCandidate {
    artifact_id: String,
    url: String,
    cache_path: std::path::PathBuf,
    curseforge_file: Option<(CurseforgeProjectId, CurseforgeProjectFileId)>,
}

fn refresh_dependencies(
    mut inventory: DependencyInventory,
    target: Option<&str>,
    cancellation_token: &CancellationToken,
    fetcher: &dyn ArtifactFetcher,
) -> eyre::Result<Vec<RefreshReport>> {
    let candidates = refresh_candidates(&inventory, target)?;
    if candidates.is_empty() {
        eyre::bail!("The selected target has no refreshable remote artifacts.");
    }
    let mut reports = Vec::new();
    for candidate in candidates.values() {
        cancellation_token.bail_if_cancelled()?;
        let (bytes, resolved_url, direct_curseforge_download) =
            match fetcher.fetch(&candidate.url, cancellation_token)? {
                Some(bytes) => (bytes, candidate.url.clone(), false),
                None => fetch_curseforge_fallback(candidate, cancellation_token, fetcher)?,
            };
        let hash = ContentHash::from_bytes(&bytes, ContentHashAlgorithm::Blake3);
        let local_path = inventory.local_path(&candidate.cache_path);
        write_cache_file_atomically(&local_path, &bytes)?;
        let artifact = inventory
            .lockfile
            .artifacts
            .iter_mut()
            .find(|artifact| artifact.id == candidate.artifact_id)
            .expect("refresh candidates come from artifact inventory");
        let old_hash = artifact.hash;
        artifact.hash = hash;
        artifact.weak = None;
        if direct_curseforge_download {
            artifact.url = Some(resolved_url);
            artifact.provenance = ArtifactProvenanceV3::RemoteHttp;
        }
        for dependency in &mut inventory.lockfile.dependencies {
            for component in &mut dependency.components {
                if component.derived_checks.artifact_id == candidate.artifact_id {
                    component.derived_checks.expected_hash = hash;
                }
            }
        }
        reports.push(RefreshReport {
            artifact_id: candidate.artifact_id.clone(),
            old_hash,
            new_hash: hash,
        });
    }
    let output = inventory.lockfile.to_canonical_json()?;
    write_lockfile_atomically(
        &inventory.lockfile_path,
        &inventory.original_input,
        output.as_bytes(),
    )?;
    Ok(reports)
}

fn refresh_candidates(
    inventory: &DependencyInventory,
    target: Option<&str>,
) -> eyre::Result<BTreeMap<String, RefreshCandidate>> {
    let selected = selected_components(inventory, target)?;
    let mut candidates = BTreeMap::new();
    for component in selected {
        let artifact = inventory.artifact(component);
        if !matches!(
            artifact.provenance,
            ArtifactProvenanceV3::RemoteMaven | ArtifactProvenanceV3::RemoteHttp
        ) {
            continue;
        }
        let url = artifact
            .url
            .clone()
            .ok_or_else(|| eyre::eyre!("Remote artifact '{}' has no locked URL.", artifact.id))?;
        candidates
            .entry(artifact.id.clone())
            .or_insert_with(|| RefreshCandidate {
                artifact_id: artifact.id.clone(),
                url,
                cache_path: artifact.cache_path.clone(),
                curseforge_file: match &component.declaration.acquisition {
                    ComponentAcquisitionV3::CurseForge(acquisition) => Some((
                        CurseforgeProjectId(acquisition.project_id),
                        CurseforgeProjectFileId(acquisition.file_id),
                    )),
                    _ => None,
                },
            });
    }
    Ok(candidates)
}

fn fetch_curseforge_fallback(
    candidate: &RefreshCandidate,
    cancellation_token: &CancellationToken,
    fetcher: &dyn ArtifactFetcher,
) -> eyre::Result<(Vec<u8>, String, bool)> {
    let Some((project_id, file_id)) = candidate.curseforge_file else {
        eyre::bail!("Remote artifact not found: {}", candidate.url);
    };
    let (api_key, _) = CurseforgeApiSecret::resolve_core(None, None, None)?;
    let client = CurseforgeHttpClient::new_core_api(&api_key)?;
    let file = client.fetch_project_file(project_id, file_id)?;
    let url = file.download_url.ok_or_else(|| {
        eyre::eyre!(
            "CurseForge file {file_id} does not provide a direct download URL after its CurseMaven mirror was unavailable."
        )
    })?;
    let bytes = fetcher.fetch(&url, cancellation_token)?.ok_or_else(|| {
        eyre::eyre!("CurseForge direct download was not found for file {file_id}: {url}")
    })?;
    Ok((bytes, url, true))
}

fn selected_components<'a>(
    inventory: &'a DependencyInventory,
    target: Option<&str>,
) -> eyre::Result<Vec<&'a crate::toolchain_lockfile_schema::version::v3::DependencyComponentV3>> {
    let Some(target) = target else {
        return Ok(inventory
            .lockfile
            .dependencies
            .iter()
            .flat_map(|dependency| &dependency.components)
            .collect());
    };
    let (dependency_id, component_id) = split_target(target)?;
    let dependency = inventory.dependency(dependency_id)?;
    if let Some(component_id) = component_id {
        let component = dependency
            .components
            .iter()
            .find(|component| component.id == component_id)
            .ok_or_else(|| eyre::eyre!("Unknown component '{dependency_id}/{component_id}'."))?;
        return Ok(vec![component]);
    }
    Ok(dependency.components.iter().collect())
}

fn split_target(target: &str) -> eyre::Result<(&str, Option<&str>)> {
    let mut parts = target.split('/');
    let dependency = parts.next().unwrap_or_default();
    let component = parts.next();
    if dependency.is_empty() || parts.next().is_some() || component.is_some_and(str::is_empty) {
        eyre::bail!("Expected dependency or dependency/component, got '{target}'.");
    }
    Ok((dependency, component))
}

#[cfg(test)]
#[path = "dependency_refresh_tests.rs"]
mod tests;
