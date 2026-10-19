use super::API;
use super::CancellationToken;
use super::Path;
use super::SessionTarget;
use super::sandbox_role::SandboxRole;
use super::session_inspection_phase::SessionInspectionPhase;
const OWNER: &str = "sfm-live-memory-probe-v1";
const SCRIPT: &str = "import pathlib,json,os,signal; signal.alarm(10); raw=pathlib.Path('/proc/meminfo').read_bytes(); assert len(raw)<=32768; fields=dict(line.split(':',1) for line in raw.decode().splitlines() if ':' in line); parse=lambda name:int(fields[name].split()[0])*1024 if fields[name].split()[1:]==['kB'] else -1; total=parse('MemTotal'); available=parse('MemAvailable'); assert 0<=available<=total; status=dict(line.split(':',1) for line in pathlib.Path('/proc/self/status').read_text().splitlines() if ':' in line); result={'schema':'sfm:memory_probe@1','total':total,'available':available,'uid':os.geteuid(),'no_new_privileges':int(status['NoNewPrivs']),'effective_caps':status['CapEff'].strip(),'seccomp':int(status['Seccomp'])}; pathlib.Path('/tmp/sfm-memory.json').write_text(json.dumps(result))";
fn entrypoint() -> Vec<String> {
    ["/usr/bin/setpriv", "--no-new-privs", "/usr/bin/python3"]
        .map(str::to_owned)
        .to_vec()
}
fn command() -> Vec<String> {
    vec!["-c".into(), SCRIPT.into()]
}
pub(super) async fn check(
    endpoint: &Path,
    image: &super::super::api_image::ApiImage,
    info: &super::super::api_info::ApiInfo,
    cancellation: &CancellationToken,
) -> eyre::Result<()> {
    if info.host.mem_free >= super::MEMORY + 2 * 1024 * 1024 * 1024 {
        return Ok(());
    }
    eyre::ensure!(
        info.host.mem_free >= 384 * 1024 * 1024,
        "insufficient free memory for bounded capacity probe"
    );
    let directory = tempfile::Builder::new().prefix("sfm-memory-").tempdir()?;
    let name = directory
        .path()
        .file_name()
        .and_then(|s| s.to_str())
        .ok_or_else(|| eyre::eyre!("probe name"))?
        .to_owned();
    let spec = create(&name, &image.id);
    let created: super::super::api_created::ApiCreated = facet_json::from_slice(
        &super::json(endpoint, "POST", &format!("{API}/containers/create"), &spec).await?,
    )?;
    let target = super::policy::bind(
        &SessionTarget {
            engine_endpoint: endpoint.to_owned(),
            session: name,
        },
        &created.id,
    )?;
    let result = run(&target, &image.id, info.host.mem_total, cancellation).await;
    let cleanup = cleanup(&target, &image.id).await;
    cleanup?;
    result
}
fn create(name: &str, image: &str) -> super::session_create::SessionCreate {
    let namespace = |mode: &str| super::session_namespace::SessionNamespace {
        nsmode: mode.into(),
        value: None,
    };
    let role = SandboxRole::MemoryProbe;
    super::session_create::SessionCreate {
        name: name.into(),
        image: image.into(),
        entrypoint: Some(entrypoint()),
        command: command(),
        user: "10001:10001".into(),
        labels: std::collections::BTreeMap::from([
            ("org.sfm.memory.owner".into(), OWNER.into()),
            ("org.sfm.memory.image".into(), image.into()),
        ]),
        cap_drop: vec!["ALL".into()],
        no_new_privileges: true,
        seccomp_policy: "default".into(),
        privileged: false,
        http_proxy: false,
        env_host: false,
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
        userns: super::session_namespace::SessionNamespace {
            nsmode: "auto".into(),
            value: Some("size=65536".into()),
        },
        netns: namespace("none"),
        ipcns: namespace("private"),
        pidns: namespace("private"),
        utsns: namespace("private"),
        cgroupns: namespace("private"),
        resource_limits: super::session_resources::SessionResources {
            memory: super::session_memory::SessionMemory {
                limit: role.memory(),
                swap: role.memory(),
            },
            cpu: super::session_cpu::SessionCpu {
                period: 100_000,
                quota: role.cpu_quota(),
            },
            pids: super::session_pids::SessionPids { limit: role.pids() },
        },
        shm_size: 16 * 1024 * 1024,
    }
}
async fn inspected(
    target: &SessionTarget,
    image: &str,
) -> eyre::Result<super::session_inspect::SessionInspect> {
    let path = super::url(&["containers", &target.session, "json"])?;
    let value: super::session_inspect::SessionInspect = facet_json::from_slice(
        &super::super::transport::small(&target.engine_endpoint, "GET", path.path()).await?,
    )?;
    validate(&value, &target.session, image)?;
    Ok(value)
}
fn validate(
    value: &super::session_inspect::SessionInspect,
    id: &str,
    image: &str,
) -> eyre::Result<()> {
    eyre::ensure!(
        value.id == id
            && value.image == image
            && value
                .config
                .labels
                .get("org.sfm.memory.owner")
                .is_some_and(|s| s == OWNER)
            && value
                .config
                .labels
                .get("org.sfm.memory.image")
                .is_some_and(|s| s == image)
            && value.config.entrypoint == entrypoint()
            && value.config.command == command()
            && value.config.user == "10001:10001",
        "memory probe immutable ownership/image/command/user mismatch; no action taken"
    );
    let phase = if value.state.status == "created" {
        eyre::ensure!(
            !value.state.running && !value.state.oom_killed,
            "probe created state mismatch"
        );
        SessionInspectionPhase::Created
    } else {
        eyre::ensure!(
            ["initialized", "running", "exited"].contains(&value.state.status.as_str()),
            "unexpected probe lifecycle state"
        );
        SessionInspectionPhase::Runtime
    };
    super::policy::validate_sandbox(value, phase, SandboxRole::MemoryProbe)
}
async fn run(
    target: &SessionTarget,
    image: &str,
    total: u64,
    cancellation: &CancellationToken,
) -> eyre::Result<()> {
    let before = inspected(target, image).await?;
    eyre::ensure!(
        before.state.status == "created",
        "probe must never have started"
    );
    let path = super::url(&["containers", &target.session, "init"])?;
    super::empty(&target.engine_endpoint, "POST", path.path()).await?;
    let initialized = inspected(target, image).await?;
    eyre::ensure!(
        initialized.state.status == "initialized"
            && !initialized.state.running
            && initialized.host.mappings.as_ref().map(|m| (&m.uid, &m.gid))
                == before.host.mappings.as_ref().map(|m| (&m.uid, &m.gid)),
        "probe namespace initialization changed mappings/state"
    );
    let path = super::url(&["containers", &target.session, "start"])?;
    super::empty(&target.engine_endpoint, "POST", path.path()).await?;
    let deadline = std::time::Instant::now() + super::Duration::from_secs(15);
    loop {
        let value = inspected(target, image).await?;
        if !value.state.running {
            eyre::ensure!(
                value.state.exit_code == 0 && !value.state.oom_killed,
                "live capacity probe failed; capacity not admitted"
            );
            break;
        }
        eyre::ensure!(std::time::Instant::now() < deadline, "memory probe timeout");
        super::pause(cancellation).await?;
    }
    let bytes = super::file_archive::fetch(
        &target.engine_endpoint,
        &target.session,
        "/tmp/sfm-memory.json",
        4096,
    )
    .await?
    .ok_or_else(|| eyre::eyre!("live memory probe output missing"))?;
    let capacity: super::memory_capacity::MemoryCapacity = facet_json::from_slice(&bytes)?;
    capacity.check(total)?;
    tracing::info!(mem_available=capacity.available,mem_total=capacity.total,probe_id=%target.session,"Live Linux capacity admitted with 8GiB cap and 2GiB reserve");
    Ok(())
}
async fn cleanup(target: &SessionTarget, image: &str) -> eyre::Result<()> {
    let value = inspected(target, image).await?;
    if value.state.running {
        let mut path = super::url(&["containers", &target.session, "stop"])?;
        path.query_pairs_mut().append_pair("timeout", "5");
        super::empty(
            &target.engine_endpoint,
            "POST",
            &format!("{}?{}", path.path(), path.query().unwrap_or_default()),
        )
        .await?;
    }
    eyre::ensure!(
        !inspected(target, image).await?.state.running,
        "owned memory probe stop unconfirmed"
    );
    let mut path = super::url(&["containers", &target.session])?;
    path.query_pairs_mut()
        .append_pair("force", "false")
        .append_pair("v", "false");
    super::empty(
        &target.engine_endpoint,
        "DELETE",
        &format!("{}?{}", path.path(), path.query().unwrap_or_default()),
    )
    .await
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn capacity_probe_refuses_foreign_workers_and_weaker_sandboxes() -> eyre::Result<()> {
        let mut value: super::super::session_inspect::SessionInspect =
            facet_json::from_str(include_str!("fixtures/admitted-worker.json"))?;
        let id = value.id.clone();
        let image = value.image.clone();
        value.config.labels = std::collections::BTreeMap::from([
            ("org.sfm.memory.owner".into(), OWNER.into()),
            ("org.sfm.memory.image".into(), image.clone()),
        ]);
        value.config.entrypoint = entrypoint();
        value.config.command = command();
        value.host.memory = SandboxRole::MemoryProbe.memory();
        value.host.swap = value.host.memory;
        value.host.cpu_quota = SandboxRole::MemoryProbe.cpu_quota();
        value.host.pids = SandboxRole::MemoryProbe.pids();
        assert!(validate(&value, &id, &image).is_ok());
        assert!(validate(&value, "foreign-id", &image).is_err());
        assert!(validate(&value, &id, "foreign-image").is_err());
        value.config.env.push("CARGO_HOME=/workspace/.cargo".into());
        assert!(validate(&value, &id, &image).is_ok());
        value.config.env.push("CARGO_HOME=/root".into());
        assert!(validate(&value, &id, &image).is_err());
        value.config.env.pop();
        value.host.network = "host".into();
        assert!(validate(&value, &id, &image).is_err());
        value.host.network = "none".into();
        value.host.memory = super::super::MEMORY;
        assert!(validate(&value, &id, &image).is_err());
        value.host.memory = SandboxRole::MemoryProbe.memory();
        value.config.command.push("injected".into());
        assert!(validate(&value, &id, &image).is_err());
        value.config.command.pop();
        value.state.status = "created".into();
        value.state.running = false;
        value.host.userns = String::new();
        assert!(validate(&value, &id, &image).is_ok());
        value.state.running = true;
        assert!(validate(&value, &id, &image).is_err());
        Ok(())
    }
}
