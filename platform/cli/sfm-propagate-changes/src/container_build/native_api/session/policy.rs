use super::API;
use super::MEMORY;
use super::OWNER;
use super::Path;
use super::SessionTarget;
use super::session_inspect::SessionInspect;
use super::url;
pub(super) async fn engine(
    endpoint: &Path,
    starting: bool,
) -> eyre::Result<super::super::api_info::ApiInfo> {
    let version: super::super::api_version::ApiVersion = facet_json::from_slice(
        &super::super::transport::small(endpoint, "GET", "/version").await?,
    )?;
    eyre::ensure!(
        version.version == "6.0.2"
            && version.os == "linux"
            && version.arch == "amd64"
            && version.components.iter().any(|p| p.name == "Podman Engine"),
        "session API requires reviewed Podman6.0.2 linux/amd64"
    );
    let info: super::super::api_info::ApiInfo = facet_json::from_slice(
        &super::super::transport::small(endpoint, "GET", &format!("{API}/info")).await?,
    )?;
    eyre::ensure!(
        info.host.security.rootless && info.host.security.seccomp_enabled,
        "session requires existing rootless/seccomp-enabled engine"
    );
    if starting {
        info.store.check()?;
        eyre::ensure!(
            info.host.mem_total >= MEMORY + 2 * 1024 * 1024 * 1024,
            "insufficient Linux headroom for capped session"
        );
        #[cfg(windows)]
        eyre::ensure!(
            super::super::memory_status::available()? >= MEMORY + 4 * 1024 * 1024 * 1024,
            "insufficient Windows session memory headroom"
        );
    }
    Ok(info)
}
pub(super) fn bind(target: &SessionTarget, id: &str) -> eyre::Result<SessionTarget> {
    eyre::ensure!(
        id.len() == 64 && id.bytes().all(|b| b.is_ascii_hexdigit()),
        "invalid immutable container ID"
    );
    Ok(SessionTarget {
        engine_endpoint: target.engine_endpoint.clone(),
        session: id.to_owned(),
    })
}
pub(super) async fn owned(target: &SessionTarget) -> eyre::Result<SessionInspect> {
    owned_at_phase(
        target,
        super::session_inspection_phase::SessionInspectionPhase::Runtime,
    )
    .await
}
pub(super) async fn created(target: &SessionTarget) -> eyre::Result<SessionInspect> {
    owned_at_phase(
        target,
        super::session_inspection_phase::SessionInspectionPhase::Created,
    )
    .await
}
async fn owned_at_phase(
    target: &SessionTarget,
    phase: super::session_inspection_phase::SessionInspectionPhase,
) -> eyre::Result<SessionInspect> {
    let path = url(&["containers", &target.session, "json"])?;
    let inspect: SessionInspect = facet_json::from_slice(
        &super::super::transport::small(&target.engine_endpoint, "GET", path.path()).await?,
    )?;
    match phase {
        super::session_inspection_phase::SessionInspectionPhase::Runtime => validate(&inspect)?,
        super::session_inspection_phase::SessionInspectionPhase::Created => {
            validate_created(&inspect)?;
        }
    }
    bind(target, &inspect.id)?;
    if target.session.len() == 64 && target.session.bytes().all(|b| b.is_ascii_hexdigit()) {
        eyre::ensure!(
            inspect.id == target.session,
            "engine returned a different immutable container ID"
        );
    }
    let path = url(&["images", &inspect.image, "json"])?;
    let image: super::super::api_image::ApiImage = facet_json::from_slice(
        &super::super::transport::small(&target.engine_endpoint, "GET", path.path()).await?,
    )?;
    eyre::ensure!(
        image.id == inspect.image
            && image.os == "linux"
            && image.architecture == "amd64"
            && image
                .config
                .labels
                .as_ref()
                .and_then(|m| m.get("org.sfm.container.context"))
                == inspect.config.labels.get("org.sfm.container.context"),
        "owned session image/context provenance mismatch"
    );
    Ok(inspect)
}
pub(super) fn validate(inspect: &SessionInspect) -> eyre::Result<()> {
    validate_at_phase(
        inspect,
        super::session_inspection_phase::SessionInspectionPhase::Runtime,
    )
}
pub(super) fn validate_created(inspect: &SessionInspect) -> eyre::Result<()> {
    eyre::ensure!(
        inspect.state.status == "created" && !inspect.state.running && !inspect.state.oom_killed,
        "initialization requires an exact never-started created worker"
    );
    validate_at_phase(
        inspect,
        super::session_inspection_phase::SessionInspectionPhase::Created,
    )
}
fn validate_at_phase(
    inspect: &SessionInspect,
    phase: super::session_inspection_phase::SessionInspectionPhase,
) -> eyre::Result<()> {
    let config = &inspect.config;
    eyre::ensure!(
        config
            .labels
            .get("org.sfm.session.owner")
            .is_some_and(|s| s == OWNER)
            && config.labels.get("org.sfm.session.image") == Some(&inspect.image)
            && config
                .labels
                .get("org.sfm.session.projection")
                .is_some_and(|p| p == "sfm-dev/mc-1.19.2"),
        "worker ownership/projection label mismatch; no action taken"
    );
    let command: Vec<String> = facet_json::from_str(
        config
            .labels
            .get("org.sfm.session.command")
            .ok_or_else(|| eyre::eyre!("session command binding missing"))?,
    )?;
    eyre::ensure!(
        config.command == command
            && config.entrypoint == ["/bin/sh", "/opt/sfm/entrypoint.sh"]
            && config.user == "10001:10001",
        "owned session command/user differs from admitted contract"
    );
    eyre::ensure!(
        command.len() == 8
            && command[0] == "sfm-dev/mc-1.19.2"
            && command[2] == "--java-max-heap-mib"
            && command[4] == "--session-seconds"
            && command[6] == "--startup-seconds",
        "session command shape mismatch"
    );
    crate::container_build::session_request::bare_test(&command[1])?;
    eyre::ensure!(
        (512..=8192).contains(&command[3].parse::<u64>()?)
            && (1..=28800).contains(&command[5].parse::<u64>()?)
            && (30..=900).contains(&command[7].parse::<u64>()?),
        "session command budgets changed"
    );
    validate_sandbox(inspect, phase, super::sandbox_role::SandboxRole::Session)
}
pub(super) fn validate_sandbox(
    inspect: &SessionInspect,
    phase: super::session_inspection_phase::SessionInspectionPhase,
    role: super::sandbox_role::SandboxRole,
) -> eyre::Result<()> {
    let config = &inspect.config;
    let host = &inspect.host;
    let keys = ["HOME", "PATH", "JAVA_HOME", "HOSTNAME", "container"];
    let sdk = [
        "RUSTUP_HOME=/usr/local/rustup",
        "RUST_VERSION=1.96.0",
        "LANG=C.UTF-8",
        "LC_ALL=C.UTF-8",
        "CARGO_HOME=/workspace/.cargo",
        "LIBGL_ALWAYS_SOFTWARE=true",
        "GALLIUM_DRIVER=llvmpipe",
        "LP_NUM_THREADS=2",
        "ALSOFT_DRIVERS=null",
        "CARGO_BUILD_JOBS=2",
        "SFM_PROPAGATE_CHANGES_HOME=/work/.sfm-config",
        "SFM_PROPAGATE_CHANGES_CACHE=/work/.sfm-cache",
    ];
    eyre::ensure!(
        config.env.iter().all(|e| e
            .split_once('=')
            .is_some_and(|(key, _)| keys.contains(&key))
            || sdk.contains(&e.as_str())),
        "unexpected session environment; no action taken"
    );
    eyre::ensure!(
        inspect.mounts.is_empty()
            && host.binds.is_empty()
            && host.ports.is_empty()
            && !host.privileged
            && host.cap_add.is_empty()
            && inspect.effective_caps.as_ref().is_none_or(Vec::is_empty)
            && inspect.bounding_caps.as_ref().is_none_or(Vec::is_empty),
        "session mounts/ports/privilege/capability policy mismatch"
    );
    let defaults = [
        "CAP_CHOWN",
        "CAP_DAC_OVERRIDE",
        "CAP_FOWNER",
        "CAP_FSETID",
        "CAP_KILL",
        "CAP_NET_BIND_SERVICE",
        "CAP_SETFCAP",
        "CAP_SETGID",
        "CAP_SETPCAP",
        "CAP_SETUID",
        "CAP_SYS_CHROOT",
    ];
    eyre::ensure!(
        defaults
            .iter()
            .all(|cap| host.cap_drop.iter().any(|drop| drop == cap))
            && host.security.iter().any(|s| s == "no-new-privileges")
            && !host.security.iter().any(|s| s.contains("unconfined")),
        "session capabilities/NNP/seccomp policy mismatch"
    );
    eyre::ensure!(
        host.network == "none"
            && host.ipc == "private"
            && host.pid == "private"
            && host.uts == "private"
            && host.cgroup == "private"
            && host.userns == phase.required_userns()
            && host
                .annotations
                .as_ref()
                .and_then(|a| a.get("io.podman.annotations.userns"))
                .is_some_and(|s| s == "auto:size=65536"),
        "session namespace policy mismatch"
    );
    eyre::ensure!(
        host.mappings.as_ref().is_some_and(|m| {
            [&m.uid, &m.gid].iter().all(|maps| {
                maps.len() == 1
                    && maps[0].split(':').next() == Some("0")
                    && maps[0]
                        .split(':')
                        .nth(1)
                        .and_then(|s| s.parse::<u64>().ok())
                        .is_some_and(|id| id > 0)
                    && maps[0].split(':').nth(2) == Some("65536")
                    && maps[0].split(':').count() == 3
            })
        }),
        "session automatic UID/GID mapping missing or changed"
    );
    eyre::ensure!(
        host.memory == role.memory()
            && host.swap == role.memory()
            && host.cpu_period == 100_000
            && host.cpu_quota == role.cpu_quota()
            && host.pids == role.pids(),
        "session resource policy mismatch"
    );
    Ok(())
}
