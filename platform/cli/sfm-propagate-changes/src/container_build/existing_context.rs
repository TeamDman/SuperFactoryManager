use super::ContainerBuildRequest;
use super::context_manifest::ContextManifest;
use super::linux_toolchain_lock::LinuxToolchainLock;
use crate::cancellation::CancellationToken;
use crate::source_projection::candidate_lock::checked_directory;
use crate::source_projection::candidate_lock::checked_file;
use crate::source_projection::catalog_owned_project::CatalogOwnedProject;
use crate::source_projection::core_catalog::read_bounded_input;
use crate::source_projection::provenance::sha256;
use sha2::Digest as _;
use std::io::Read as _;
use std::time::Duration;
pub(super) fn execute(
    request: &ContainerBuildRequest,
    project: &CatalogOwnedProject,
    current: &ContextManifest,
    contract: &LinuxToolchainLock,
    cli: &[u8],
    token: &CancellationToken,
    timeout: Duration,
) -> eyre::Result<ContextManifest> {
    let freshness_timer = super::phase_timer::PhaseTimer::start("existing_context_freshness", 0);
    eyre::ensure!(
        request.transport == super::ContainerTransport::NativeApi,
        "existing-context reuse requires verified native image identity"
    );
    let root = checked_directory(&request.context)?;
    let raw = read_bounded_input(&root, "context-manifest.json", 32 * 1024 * 1024)?;
    let mut stored: ContextManifest = facet_json::from_slice(&raw)?;
    eyre::ensure!(
        stored.context_prepared
            && !stored.image_built
            && stored.image_identity.is_none()
            && !stored.gameplay_validated
            && stored.missing.is_empty(),
        "existing context is not an immutable input manifest"
    );
    verify_binding(&mut stored)?;
    eyre::ensure!(
        stored.projection == current.projection
            && stored.platform == current.platform
            && stored.ownership_identity == current.ownership_identity
            && stored.source_lock_sha256 == current.source_lock_sha256
            && stored.toolchain_lock_sha256 == current.toolchain_lock_sha256
            && stored.test_maven_metadata_identity == current.test_maven_metadata_identity
            && stored.base_image.as_deref() == Some(&contract.base_image),
        "existing context is stale; prepare a fresh context directory"
    );
    eyre::ensure!(
        stored.total_bytes.saturating_add(
            stored
                .dependency_layer
                .as_ref()
                .map_or(0, |d| d.total_bytes)
        ) <= request.max_bytes,
        "existing input byte budget exceeded"
    );
    let inputs = super::snapshot::inputs(project)?;
    eyre::ensure!(
        inputs.len()
            == stored
                .files
                .keys()
                .filter(|p| p.starts_with("snapshot/"))
                .count(),
        "snapshot input set changed"
    );
    for (path, (bytes, parent)) in inputs {
        token.bail_if_cancelled()?;
        let row = stored
            .files
            .get(&path)
            .ok_or_else(|| eyre::eyre!("snapshot input added: {path}"))?;
        eyre::ensure!(
            row.sha256 == sha256(&bytes)
                && row.bytes == bytes.len() as u64
                && row.parent_identity == parent,
            "snapshot input changed: {path}; prepare a fresh context"
        );
    }
    let cli_row = stored
        .files
        .get("tools/sfm-propagate-changes")
        .ok_or_else(|| eyre::eyre!("existing Linux CLI missing"))?;
    eyre::ensure!(cli_row.sha256 == sha256(cli), "Linux CLI bytes changed");
    verify_cache(request, &mut stored, token)?;
    project.recheck()?;
    if !stored.missing.is_empty() {
        stored.context_prepared = false;
        return Ok(stored);
    }
    drop(freshness_timer);
    if request.prepare_only {
        super::backend::verify_context(&root, &stored)?;
    }
    if !request.prepare_only {
        stored.image_identity = super::backend::build(request, &stored, token, timeout)?;
        stored.image_built = true;
    }
    Ok(stored)
}

pub(super) fn verify_binding(stored: &mut ContextManifest) -> eyre::Result<()> {
    let identity = stored
        .context_identity
        .take()
        .ok_or_else(|| eyre::eyre!("existing context identity missing"))?;
    let result = sha256(facet_json::to_string(stored)?.as_bytes());
    stored.context_identity = Some(identity.clone());
    eyre::ensure!(
        result == identity,
        "existing context identity does not bind its complete input manifest"
    );
    Ok(())
}

fn verify_cache(
    request: &ContainerBuildRequest,
    stored: &mut ContextManifest,
    token: &CancellationToken,
) -> eyre::Result<()> {
    let cache = checked_directory(&request.cache_root.join("minecraft-toolchain"))?;
    let dependency = stored.dependency_layer.as_ref().ok_or_else(|| {
        eyre::eyre!("existing-context reuse requires the independent dependency layer")
    })?;
    let cache_timer =
        super::phase_timer::PhaseTimer::start("pinned_cache_freshness", dependency.total_bytes);
    let files = dependency.files.clone();
    for (path, row) in &files {
        token.bail_if_cancelled()?;
        let relative = path
            .strip_prefix("cache/minecraft-toolchain/")
            .ok_or_else(|| eyre::eyre!("unapproved dependency cache root"))?;
        if path == super::test_metadata::DERIVED_CATALOG
            && stored.test_maven_metadata_identity.is_some()
        {
            eyre::ensure!(
                stored.test_maven_metadata_identity.as_deref() == Some(row.sha256.as_str())
                    && row.expected_hash.as_deref() == Some(row.sha256.as_str()),
                "derived test metadata catalog binding missing"
            );
            // Its consumed, reduced bytes were recomputed by the canonical resolver;
            // the host catalog may additionally contain unconsumed public metadata.
            continue;
        }
        let result = (|| -> eyre::Result<()> {
            let mut input = std::fs::File::open(checked_file(&cache, relative)?)?;
            eyre::ensure!(
                input.metadata()?.len() == row.bytes,
                "cached artifact size changed"
            );
            let mut hash = sha2::Sha256::new();
            let mut buffer = vec![0_u8; 65536].into_boxed_slice();
            let mut count = 0_u64;
            loop {
                token.bail_if_cancelled()?;
                let n = input.read(&mut buffer)?;
                if n == 0 {
                    break;
                }
                count += n as u64;
                eyre::ensure!(count <= row.bytes, "cached artifact grew");
                hash.update(&buffer[..n]);
            }
            eyre::ensure!(
                count == row.bytes && format!("sha256:{:x}", hash.finalize()) == row.sha256,
                "cached artifact content changed"
            );
            Ok(())
        })();
        token.bail_if_cancelled()?;
        if let Err(error) = result {
            super::artifact::missing(stored, path, format!("{error:#}"));
        }
    }
    drop(cache_timer);
    Ok(())
}
