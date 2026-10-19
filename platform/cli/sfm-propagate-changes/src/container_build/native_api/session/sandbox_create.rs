use std::collections::BTreeMap;
pub(super) fn create(
    name: &str,
    image: &str,
    entrypoint: Vec<String>,
    command: Vec<String>,
    labels: BTreeMap<String, String>,
    role: super::sandbox_role::SandboxRole,
) -> super::session_create::SessionCreate {
    let ns = |mode: &str| super::session_namespace::SessionNamespace {
        nsmode: mode.into(),
        value: None,
    };
    super::session_create::SessionCreate {
        name: name.into(),
        image: image.into(),
        entrypoint: Some(entrypoint),
        command,
        user: "10001:10001".into(),
        labels,
        cap_drop: vec!["ALL".into()],
        no_new_privileges: true,
        seccomp_policy: "default".into(),
        privileged: false,
        http_proxy: false,
        env_host: false,
        annotations: BTreeMap::from([(
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
        netns: ns("none"),
        ipcns: ns("private"),
        pidns: ns("private"),
        utsns: ns("private"),
        cgroupns: ns("private"),
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
        shm_size: 64 * 1024 * 1024,
    }
}
