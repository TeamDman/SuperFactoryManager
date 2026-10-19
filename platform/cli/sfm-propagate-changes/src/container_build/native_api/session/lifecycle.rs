use super::API;
use super::CancellationToken;
use super::Duration;
use super::GAME;
use super::MEMORY;
use super::OWNER;
use super::Path;
use super::SessionReceipt;
use super::SessionTarget;
use super::empty;
use super::file_archive;
use super::json;
use super::operations;
use super::pause;
use super::png;
use super::policy;
use super::receipt;
use super::session_cpu;
use super::session_create;
use super::session_memory;
use super::session_namespace;
use super::session_pids;
use super::session_resources;
use super::url;
use super::write_new;
use crate::container_build::SessionStartRequest;
use crate::container_build::SessionStopRequest;

#[expect(
    clippy::too_many_lines,
    reason = "Fixed admission, create and readiness sequence is kept together for audit"
)]
pub(super) async fn start(
    request: &SessionStartRequest,
    engine: &super::super::api_info::ApiInfo,
    cancellation: &CancellationToken,
) -> eyre::Result<SessionReceipt> {
    let command = request.command()?;
    let target = &request.target;
    let path = url(&["images", &request.image, "json"])?;
    let image: super::super::api_image::ApiImage = facet_json::from_slice(
        &super::super::transport::small(&target.engine_endpoint, "GET", path.path()).await?,
    )?;
    eyre::ensure!(
        image.os == "linux"
            && image.architecture == "amd64"
            && image.id.len() == 64
            && image.id.bytes().all(|b| b.is_ascii_hexdigit()),
        "session requires an existing local Linux/amd64 image"
    );
    eyre::ensure!(
        image.config.entrypoint.as_deref()
            == Some(&["/bin/sh".to_owned(), "/opt/sfm/entrypoint.sh".to_owned()])
            && image.config.command.as_deref()
                == Some(&[request.projection.clone(), "sfm:move_1_stack_direct".into()])
            && image.config.user.as_deref() == Some("10001:10001"),
        "image lacks reviewed prebuilt puppet entrypoint/projection/user"
    );
    let context = image
        .config
        .labels
        .as_ref()
        .and_then(|m| m.get("org.sfm.container.context"))
        .ok_or_else(|| eyre::eyre!("image context binding missing"))?;
    eyre::ensure!(
        context.starts_with("sha256:") && context.len() == 71,
        "invalid image context binding"
    );
    super::memory_probe::check(&target.engine_endpoint, &image, engine, cancellation).await?;
    let labels = std::collections::BTreeMap::from([
        ("org.sfm.session.owner".into(), OWNER.into()),
        ("org.sfm.session.image".into(), image.id.clone()),
        (
            "org.sfm.session.projection".into(),
            request.projection.clone(),
        ),
        (
            "org.sfm.session.command".into(),
            facet_json::to_string(&command)?,
        ),
        ("org.sfm.container.context".into(), context.clone()),
    ]);
    let namespace = |mode: &str| session_namespace::SessionNamespace {
        nsmode: mode.into(),
        value: None,
    };
    let create = session_create::SessionCreate {
        name: target.session.clone(),
        image: image.id.clone(),
        entrypoint: None,
        command,
        user: "10001:10001".into(),
        labels,
        cap_drop: vec!["ALL".into()],
        no_new_privileges: true,
        seccomp_policy: "default".into(),
        privileged: false,
        http_proxy: false,
        env_host: false,
        userns: session_namespace::SessionNamespace {
            nsmode: "auto".into(),
            value: Some("size=65536".into()),
        },
        annotations: std::collections::BTreeMap::from([(
            "io.podman.annotations.userns".into(),
            "auto:size=65536".into(),
        )]),
        idmappings: super::session_id_mapping::SessionIdMapping {
            host_uid: false,
            host_gid: false,
            auto: true,
            options: super::session_auto_userns::SessionAutoUserns { size: 65536 },
        },
        netns: namespace("none"),
        ipcns: namespace("private"),
        pidns: namespace("private"),
        utsns: namespace("private"),
        cgroupns: namespace("private"),
        resource_limits: session_resources::SessionResources {
            memory: session_memory::SessionMemory {
                limit: MEMORY,
                swap: MEMORY,
            },
            cpu: session_cpu::SessionCpu {
                period: 100_000,
                quota: 200_000,
            },
            pids: session_pids::SessionPids { limit: 512 },
        },
        shm_size: 64 * 1024 * 1024,
    };
    let created: super::super::api_created::ApiCreated = facet_json::from_slice(
        &json(
            &target.engine_endpoint,
            "POST",
            &format!("{API}/containers/create"),
            &create,
        )
        .await?,
    )?;
    let bound = policy::bind(target, &created.id)?;
    let result = wait_started(request, &bound, cancellation).await;
    if result.is_err() {
        // Only the immutable ID just returned by our successful create is stopped.
        // Failed/partial startup evidence remains in the stopped worker for export.
        let path = url(&["containers", &created.id, "stop"])?;
        if let Err(error) = empty(
            &target.engine_endpoint,
            "POST",
            &format!("{}?t=20", path.path()),
        )
        .await
        {
            tracing::warn!(worker=%created.id,error=%error,"owned startup cleanup unconfirmed; inspect worker before retry");
        }
    }
    result
}
async fn wait_started(
    request: &SessionStartRequest,
    target: &SessionTarget,
    cancellation: &CancellationToken,
) -> eyre::Result<SessionReceipt> {
    // Podman materializes the automatic user namespace during OCI initialization.
    // Admit only the exact mapped, never-started worker for init; require the full
    // private namespace policy again before executing its game entrypoint.
    policy::created(target).await?;
    let path = url(&["containers", &target.session, "init"])?;
    empty(&target.engine_endpoint, "POST", path.path()).await?;
    policy::owned(target).await?;
    let path = url(&["containers", &target.session, "start"])?;
    empty(&target.engine_endpoint, "POST", path.path()).await?;
    let deadline = std::time::Instant::now() + Duration::from_secs(request.startup_seconds + 15);
    loop {
        let inspect = policy::owned(target).await?;
        let ready = operations::ready(target).await?;
        eyre::ensure!(
            inspect.state.running && !inspect.state.oom_killed,
            "owned client exited before managed readiness: exit{}, OOM{}; worker retained for logs",
            inspect.state.exit_code,
            inspect.state.oom_killed
        );
        if ready.is_some() {
            return Ok(receipt(&request.target, &inspect, ready));
        }
        eyre::ensure!(
            std::time::Instant::now() < deadline,
            "owned client did not become ready within startup budget"
        );
        pause(cancellation).await?;
    }
}
pub(super) async fn stop(
    request: &SessionStopRequest,
    mut result: SessionReceipt,
    cancellation: &CancellationToken,
) -> eyre::Result<SessionReceipt> {
    eyre::ensure!(
        !request.remove || request.output.is_some(),
        "removal requires --output so evidence is exported first"
    );
    if let Some(output) = &request.output {
        eyre::ensure!(
            !output.exists() && output.parent().is_some_and(Path::is_dir),
            "stop output must be a new directory with an existing parent"
        );
    }
    let bound = policy::bind(&request.target, &result.container_id)?;
    let target = &bound;
    if result.running {
        if result.ready.is_some() {
            match operations::perform(target, "stop", None, None, cancellation).await {
                Ok(response) => result.response = Some(response),
                Err(error) => {
                    tracing::warn!(error=%error,"owned puppet graceful stop failed; bounded engine stop follows");
                }
            }
        }
        let deadline = std::time::Instant::now() + Duration::from_secs(45);
        while result.response.is_some()
            && policy::owned(target).await?.state.running
            && std::time::Instant::now() < deadline
        {
            pause(cancellation).await?;
        }
        if policy::owned(target).await?.state.running {
            let path = url(&["containers", &target.session, "stop"])?;
            empty(
                &target.engine_endpoint,
                "POST",
                &format!("{}?t=20", path.path()),
            )
            .await?;
        }
    }
    let inspect = policy::owned(target).await?;
    eyre::ensure!(
        !inspect.state.running,
        "owned worker stop is unconfirmed; no removal attempted"
    );
    result.running = false;
    result.exit_code = inspect.state.exit_code;
    result.oom_killed = inspect.state.oom_killed;
    if let Some(output) = &request.output {
        export(target, output, &mut result).await?;
    }
    if request.remove {
        let path = url(&["containers", &target.session])?;
        empty(
            &target.engine_endpoint,
            "DELETE",
            &format!("{}?force=false", path.path()),
        )
        .await?;
        result.removed = true;
    }
    Ok(result)
}
async fn export(
    target: &SessionTarget,
    output: &Path,
    result: &mut SessionReceipt,
) -> eyre::Result<()> {
    std::fs::create_dir(output)?;
    let mut files = vec![
        (
            "/work/output/console.log".to_owned(),
            "console.log".to_owned(),
            64 * 1024 * 1024,
        ),
        (
            "/work/output/launch-receipt.json".to_owned(),
            "launch-receipt.json".to_owned(),
            2 * 1024 * 1024,
        ),
        (
            "/work/output/config-seed-receipt.json".to_owned(),
            "config-seed-receipt.json".to_owned(),
            2 * 1024 * 1024,
        ),
        (
            format!("{GAME}/managed-session/ready.json"),
            "managed-ready.json".into(),
            2 * 1024 * 1024,
        ),
    ];
    for orbit in 0..8 {
        let name = format!("managed_superflat_session__orbit-{orbit:02}.png");
        files.push((format!("{GAME}/screenshots/{name}"), name, 16 * 1024 * 1024));
    }
    for sequence in 1..=128 {
        let name = format!("managed_superflat_session__session-{sequence:06}.png");
        files.push((format!("{GAME}/screenshots/{name}"), name, 16 * 1024 * 1024));
        let name = format!("{sequence:06}.response.json");
        files.push((
            format!("{GAME}/managed-session/{name}"),
            name,
            2 * 1024 * 1024,
        ));
    }
    let mut total = 0;
    for (remote, name, budget) in files {
        if let Some(bytes) =
            file_archive::fetch(&target.engine_endpoint, &target.session, &remote, budget).await?
        {
            total += bytes.len();
            eyre::ensure!(
                total <= 64 * 1024 * 1024,
                "session artifact export exceeds64MiB; stopped worker retained"
            );
            if Path::new(&name)
                .extension()
                .is_some_and(|ext| ext.eq_ignore_ascii_case("png"))
            {
                png::verify(&bytes)?;
            }
            let path = output.join(name);
            write_new(&path, &bytes)?;
            result.artifacts.insert(
                path.display().to_string(),
                crate::source_projection::provenance::sha256(&bytes),
            );
        }
    }
    eyre::ensure!(
        result.artifacts.keys().any(|p| p.ends_with("console.log")),
        "console evidence missing; stopped worker retained"
    );
    write_new(
        &output.join("session-receipt.json"),
        &facet_json::to_vec(result)?,
    )?;
    Ok(())
}
