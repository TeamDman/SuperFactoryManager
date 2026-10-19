use super::ContainerBuildRequest;
use super::ContainerTransport;
use super::context_manifest::ContextManifest;
use super::dependency_layer::DependencyLayer;
use crate::cancellation::CancellationToken;
use crate::source_projection::provenance::sha256;
use std::collections::BTreeMap;
use std::path::Path;
use std::time::Duration;
pub(super) fn prepare(
    request: &ContainerBuildRequest,
    report: &mut ContextManifest,
    stage: &Path,
    cancellation: &CancellationToken,
    timeout: Duration,
) -> eyre::Result<Option<String>> {
    if request.prepare_only || request.transport != ContainerTransport::NativeApi {
        return Ok(None);
    }
    let _timer = super::phase_timer::PhaseTimer::start("dependency_layer", report.total_bytes);
    let mut dependency = report.clone();
    dependency.ownership_identity = "sfm-portable-dependency-layer-v1".into();
    dependency.projection = "portable-dependency-cache".into();
    dependency.toolchain_lock_sha256 = None;
    dependency.base_image = None;
    dependency.files = report
        .files
        .iter()
        .filter(|(path, _)| path.starts_with("cache/"))
        .map(|(path, row)| (path.clone(), row.clone()))
        .collect::<BTreeMap<_, _>>();
    dependency.total_bytes = dependency.files.values().map(|row| row.bytes).sum();
    dependency.context_prepared = false;
    dependency.context_identity = None;
    let parent = request
        .context
        .parent()
        .ok_or_else(|| eyre::eyre!("dependency context parent missing"))?;
    let directory = tempfile::Builder::new()
        .prefix(".sfm-dependency-context-")
        .tempdir_in(parent)?;
    std::fs::rename(stage.join("cache"), directory.path().join("cache"))?;
    super::artifact::put_bytes(
        directory.path(),
        "Dockerfile",
        b"FROM scratch\nCOPY --chown=10001:10001 cache/ /cache/\n",
        "sfm-portable-dependency-layer-v1",
        &mut dependency,
        request.max_bytes,
    )?;
    dependency.context_prepared = true;
    dependency.context_identity = Some(sha256(facet_json::to_string(&dependency)?.as_bytes()));
    std::fs::write(
        directory.path().join("context-manifest.json"),
        facet_json::to_string_pretty(&dependency)?,
    )?;
    let mut bounded = request.clone();
    bounded.context = directory.path().to_path_buf();
    bounded.tag = "sfm-dependencies:local".into();
    // Cache inputs have just been hash-checked by copy_cached and content_store.
    // A cache miss additionally hashes them into the uploaded allowlisted archive.
    let image = super::native_api::build(&bounded, &dependency, cancellation, timeout)?;
    let identity = dependency
        .context_identity
        .clone()
        .expect("dependency identity");
    let reference = format!("localhost/sfm-dependencies@{}", image.manifest_digest);
    let progress = directory.path().join("build-progress.log");
    if progress.exists() {
        std::fs::copy(
            progress,
            parent.join(format!("dependency-{}-progress.log", &identity[7..23])),
        )?;
    }
    let cache_files = report
        .files
        .iter()
        .filter(|(path, _)| path.starts_with("cache/"))
        .map(|(path, row)| (path.clone(), row.clone()))
        .collect::<BTreeMap<_, _>>();
    let bytes = cache_files.values().map(|row| row.bytes).sum();
    report.files.retain(|path, _| !path.starts_with("cache/"));
    report.total_bytes -= bytes;
    report.dependency_layer = Some(DependencyLayer {
        context_identity: identity,
        image,
        files: cache_files,
        total_bytes: bytes,
    });
    Ok(Some(reference))
}
