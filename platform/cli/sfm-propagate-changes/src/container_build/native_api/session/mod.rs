//! Fixed sandbox lifecycle and typed file requests for one disposable client/world.
mod build_capacity;
pub(super) use build_capacity::check as check_build_capacity;
mod controller_receipt;
mod exec_create;
mod exec_inspect;
mod exec_start;
mod file_archive;
pub(crate) mod junit_job;
mod lifecycle;
mod memory_capacity;
mod memory_probe;
mod operations;
mod png;
mod policy;
mod sandbox_create;
mod sandbox_role;
mod session_auto_userns;
mod session_config;
mod session_cpu;
mod session_create;
mod session_host;
mod session_id_mapping;
mod session_inspect;
mod session_inspect_mapping;
mod session_inspection_phase;
mod session_memory;
mod session_mount;
mod session_namespace;
mod session_operation;
mod session_pids;
mod session_ready;
mod session_resources;
mod session_response;
mod session_state;
use crate::cancellation::CancellationToken;
use crate::container_build::SessionReceipt;
use crate::container_build::SessionRequest;
use crate::container_build::SessionTarget;
pub use session_ready::SessionReady;
pub use session_response::SessionResponse;
use std::path::Path;
use std::time::Duration;
const API: &str = "/v6.0.2/libpod";
const GAME: &str = "/work/platform/minecraft/projections/sfm-dev/mc-1.19.2/runGameTestPreview";
const OWNER: &str = "sfm-local-session-v1";
const MEMORY: u64 = 8 * 1024 * 1024 * 1024;

pub(crate) fn execute(
    request: &SessionRequest,
    cancellation: &CancellationToken,
) -> eyre::Result<SessionReceipt> {
    request.target().validate()?;
    if let SessionRequest::Start(r) = request {
        r.command()?;
    }
    if let SessionRequest::RunTest(r) = request {
        crate::container_build::session_request::bare_test(&r.game_test)?;
    }
    if let SessionRequest::Capture(r) = request {
        eyre::ensure!(
            r.angle.is_finite() && r.angle.abs() <= std::f64::consts::TAU && !r.output.exists(),
            "invalid angle or existing capture output"
        );
    }
    if let SessionRequest::Stop(r) = request {
        eyre::ensure!(
            !r.remove || r.output.is_some(),
            "removal requires --output before worker changes"
        );
        if let Some(output) = &r.output {
            eyre::ensure!(
                !output.exists() && output.parent().is_some_and(Path::is_dir),
                "stop output must be a new directory with an existing parent"
            );
        }
    }
    let timeout = match request {
        SessionRequest::Start(r) => r.startup_seconds + 90,
        SessionRequest::Stop(_) => 240,
        _ => 150,
    };
    let runtime = tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()?;
    runtime.block_on(async {
        tokio::time::timeout(
            Duration::from_secs(timeout),
            execute_async(request, cancellation),
        )
        .await
        .map_err(|error| {
            eyre::eyre!("session operation timed out ({error}); worker retained, inspect status before retry")
        })?
    })
}
async fn execute_async(
    request: &SessionRequest,
    cancellation: &CancellationToken,
) -> eyre::Result<SessionReceipt> {
    let target = request.target();
    cancellation.bail_if_cancelled()?;
    let engine = policy::engine(
        &target.engine_endpoint,
        matches!(request, SessionRequest::Start(_)),
    )
    .await?;
    if let SessionRequest::Start(start) = request {
        return lifecycle::start(start, &engine, cancellation).await;
    }
    let inspected = policy::owned(target).await?;
    let bound = policy::bind(target, &inspected.id)?;
    let ready = operations::ready(&bound).await?;
    let mut receipt = receipt(target, &inspected, ready);
    let target = &bound;
    match request {
        SessionRequest::Start(_) => unreachable!(),
        SessionRequest::Status(_) => {}
        SessionRequest::Reset(_) => {
            receipt.response =
                Some(operations::perform(target, "reset", None, None, cancellation).await?);
        }
        SessionRequest::RunTest(r) => {
            let test = crate::container_build::session_request::bare_test(&r.game_test)?;
            receipt.response = Some(
                operations::perform(
                    target,
                    "run_game_test",
                    Some(test.to_owned()),
                    None,
                    cancellation,
                )
                .await?,
            );
        }
        SessionRequest::Capture(r) => {
            eyre::ensure!(
                r.angle.is_finite() && r.angle.abs() <= std::f64::consts::TAU,
                "camera angle must be finite and within one orbit"
            );
            eyre::ensure!(
                !r.output.exists(),
                "capture output already exists; choose a new file"
            );
            let response =
                operations::perform(target, "capture", None, Some(r.angle), cancellation).await?;
            let name = format!(
                "managed_superflat_session__session-{:06}.png",
                response.sequence
            );
            let bytes = file_archive::fetch(
                &target.engine_endpoint,
                &target.session,
                &format!("{GAME}/screenshots/{name}"),
                16 * 1024 * 1024,
            )
            .await?
            .ok_or_else(|| eyre::eyre!("capture response lacks named screenshot: {name}"))?;
            png::verify(&bytes)?;
            write_new(&r.output, &bytes)?;
            receipt.artifacts.insert(
                r.output.display().to_string(),
                crate::source_projection::provenance::sha256(&bytes),
            );
            receipt.response = Some(response);
        }
        SessionRequest::Stop(stop) => {
            return lifecycle::stop(stop, receipt, cancellation).await;
        }
    }
    Ok(receipt)
}
fn receipt(
    target: &SessionTarget,
    inspect: &session_inspect::SessionInspect,
    ready: Option<SessionReady>,
) -> SessionReceipt {
    SessionReceipt {
        schema: "sfm:container_session@1".into(),
        session: target.session.clone(),
        container_id: inspect.id.clone(),
        image: inspect.image.clone(),
        running: inspect.state.running,
        exit_code: inspect.state.exit_code,
        oom_killed: inspect.state.oom_killed,
        ready,
        response: None,
        artifacts: std::collections::BTreeMap::new(),
        removed: false,
    }
}
fn url(parts: &[&str]) -> eyre::Result<reqwest::Url> {
    let mut url = reqwest::Url::parse("http://localhost/")?;
    url.path_segments_mut()
        .map_err(|()| eyre::eyre!("local API URI"))?
        .extend(["v6.0.2", "libpod"])
        .extend(parts);
    Ok(url)
}
async fn json<T: for<'a> facet::Facet<'a>>(
    endpoint: &Path,
    method: &str,
    path: &str,
    value: &T,
) -> eyre::Result<Vec<u8>> {
    use http_body_util::BodyExt as _;
    let raw = facet_json::to_vec(value)?;
    let mut response = super::transport::request(
        endpoint,
        method,
        path,
        super::transport::bytes_body(raw.clone()),
        raw.len() as u64,
    )
    .await?;
    eyre::ensure!(
        response.response.status().is_success(),
        "session API HTTP {}",
        response.response.status()
    );
    let mut bytes = Vec::new();
    while let Some(frame) = response.response.body_mut().frame().await {
        if let Ok(data) = frame?.into_data() {
            eyre::ensure!(
                bytes.len() + data.len() <= 2 * 1024 * 1024,
                "session API response byte budget"
            );
            bytes.extend_from_slice(&data);
        }
    }
    Ok(bytes)
}
async fn empty(endpoint: &Path, method: &str, path: &str) -> eyre::Result<()> {
    super::transport::small(endpoint, method, path).await?;
    Ok(())
}
async fn pause(cancellation: &CancellationToken) -> eyre::Result<()> {
    cancellation.bail_if_cancelled()?;
    tokio::time::sleep(Duration::from_millis(500)).await;
    cancellation.bail_if_cancelled()
}
fn write_new(path: &Path, bytes: &[u8]) -> eyre::Result<()> {
    use std::io::Write as _;
    let parent = path
        .parent()
        .ok_or_else(|| eyre::eyre!("artifact output needs a parent directory"))?;
    eyre::ensure!(parent.is_dir(), "artifact output parent does not exist");
    let mut current = std::path::PathBuf::new();
    for component in parent.components() {
        current.push(component);
        eyre::ensure!(
            !std::fs::symlink_metadata(&current)?
                .file_type()
                .is_symlink(),
            "artifact output symlink parent refused"
        );
    }
    let mut file = std::fs::OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(path)?;
    file.write_all(bytes)?;
    file.sync_all()?;
    Ok(())
}

#[cfg(test)]
mod tests;
