use super::ContainerBuildRequest;
use super::ContainerEngine;
use super::context_manifest::ContextManifest;
use super::linux_toolchain_lock::LinuxToolchainLock;
use crate::cancellation::CancellationToken;
use crate::source_projection::candidate_lock::checked_file;
use eyre::ensure;
use sha2::Digest as _;
use sha2::Sha256;
use std::ffi::OsString;
use std::fs;
use std::io::Read as _;
use std::path::Path;
use std::process::Command;
use std::process::Stdio;
use std::time::Duration;
use std::time::Instant;

// Worlds, descriptors and outputs are created only in disposable container storage.
// /work must be writable for UID 10001; no host or volume mount is required.
pub(super) const ENTRYPOINT: &str = r#"#!/bin/sh
set -eu
exec /usr/bin/python3 /opt/sfm/runtime_launch.py "$@"
"#;

pub(super) fn dockerfile(
    contract: &LinuxToolchainLock,
    request: &ContainerBuildRequest,
) -> eyre::Result<String> {
    dockerfile_with_dependencies(contract, request, None)
}

pub(super) fn dockerfile_with_dependencies(
    contract: &LinuxToolchainLock,
    request: &ContainerBuildRequest,
    dependencies: Option<&str>,
) -> eyre::Result<String> {
    request.validate()?;
    let dependency_stage = dependencies.map_or_else(String::new, |image| {
        format!("FROM {image} AS dependency-cache\n")
    });
    let cache_copy = dependencies.map_or_else(
        || "COPY --chown=10001:10001 cache/ /work/.sfm-cache/".to_owned(),
        |_| "COPY --from=dependency-cache --chown=10001:10001 /cache/ /work/.sfm-cache/".to_owned(),
    );
    Ok(format!(
        r#"{dependency_stage}FROM {} AS game-prep
SHELL ["/usr/bin/setpriv", "--no-new-privs", "/bin/sh", "-c"]
USER root
{cache_copy}
COPY --chown=10001:10001 snapshot/ /work/
COPY --chown=10001:10001 runtime_pack.py runtime_launch.py entrypoint.sh junit_job.py /opt/sfm/
USER 10001:10001
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 SFM_PROPAGATE_CHANGES_HOME=/work/.sfm-config SFM_PROPAGATE_CHANGES_CACHE=/work/.sfm-cache
WORKDIR /work
RUN printf '/platform/minecraft/projections/sfm-dev/\n' > .gitignore && git init -q -b container-snapshot && git add .gitignore platform && git -c user.name=SFM -c user.email=container@example.invalid commit -q -m snapshot
RUN mkdir -p platform/cli/sfm/target/debug && cp /opt/sfm/cargo/bin/sfm platform/cli/sfm/target/debug/sfm
RUN set -eu; test $(df -Pk / | tail -1 | awk '{{print $4}}') -gt 8388608; status=0; /opt/sfm/cargo/bin/sfm-propagate-changes puppet run game_test_orbit_capture --dry-run --repo-root /work --projection '{}' --game-test sfm:move_1_stack_direct --java-home "$JAVA_HOME" --require-portable-artifacts || status=$?; if test "$status" != 0; then find platform/minecraft/projections/sfm-dev -name 'javac-*.log' -type f -exec tail -n 100 {{}} \;; fi; exit "$status"
RUN mkdir -p test-receipts; status=0; /opt/sfm/cargo/bin/sfm-propagate-changes run compile --repo-root /work --projection '{}' --java-home "$JAVA_HOME" --require-portable-artifacts > test-receipts/junit-compile.log 2>&1 || status=$?; printf '{{"source_set":"JUnit","compiled":%s,"exit_code":%s}}\n' "$([ "$status" = 0 ] && echo true || echo false)" "$status" > test-receipts/junit-compile.json; tail -n 20 test-receipts/junit-compile.log; if test "$status" != 0; then find platform/minecraft/projections/sfm-dev -name 'javac-test.log' -type f -exec cp {{}} test-receipts/javac-test.log \; -exec tail -n 100 {{}} \;; fi; exit "$status"
FROM game-prep AS runtime-pack
RUN python3 /opt/sfm/runtime_pack.py
FROM runtime-pack AS config-seed
COPY config_seed.py config_seed_data.py /opt/sfm/
RUN python3 /opt/sfm/config_seed.py --manifest /work/.runtime-game/runtime-manifest.json --destination /work/config-stage
FROM scratch AS runtime
COPY --from=runtime-pack --chown=0:0 /work/.runtime-root/ /
COPY --from=runtime-pack --chown=10001:10001 /work/.runtime-game/ /work/
COPY --from=config-seed --chown=10001:10001 /work/config-stage/ /work/
COPY --from=config-seed /opt/sfm/config_seed.py /opt/sfm/config_seed_data.py /opt/sfm/
COPY --from=game-prep /opt/sfm/entrypoint.sh /opt/sfm/entrypoint.sh
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 HOME=/work/.home PATH=/usr/bin:/bin:/usr/lib/jvm/java-17-openjdk-amd64/bin:/opt/sfm/cargo/bin
USER 10001:10001
WORKDIR /work
SHELL ["/usr/bin/setpriv", "--no-new-privs", "/bin/sh", "-c"]
RUN LIBGL_ALWAYS_SOFTWARE=true GALLIUM_DRIVER=llvmpipe LP_NUM_THREADS=2 xvfb-run -a -e /work/graphics-preflight.errors glxinfo -B > /work/graphics-preflight.log && grep -q llvmpipe /work/graphics-preflight.log
ENTRYPOINT ["/bin/sh", "/opt/sfm/entrypoint.sh"]
CMD ["{}", "sfm:move_1_stack_direct"]
FROM {} AS developer
SHELL ["/usr/bin/setpriv", "--no-new-privs", "/bin/sh", "-c"]
USER root
COPY --from=game-prep --chown=10001:10001 /work/platform/ /work/platform/
COPY --from=game-prep --chown=10001:10001 /work/.sfm-cache/ /work/.sfm-cache/
COPY --from=game-prep --chown=10001:10001 /work/.git/ /work/.git/
COPY --from=game-prep --chown=10001:10001 /work/.gitignore /work/.gitignore
COPY --from=game-prep --chown=10001:10001 /work/examples/ /work/examples/
COPY --from=game-prep --chown=10001:10001 /work/docs/ /work/docs/
COPY --from=game-prep --chown=10001:10001 /work/test-receipts/ /work/test-receipts/
COPY --from=game-prep --chown=10001:10001 /work/container-inputs.json /work/container-inputs.json
COPY --from=config-seed --chown=10001:10001 /work/config-stage/ /work/
COPY --from=config-seed /opt/sfm/config_seed.py /opt/sfm/config_seed_data.py /opt/sfm/
COPY --from=game-prep /opt/sfm/runtime_launch.py /opt/sfm/entrypoint.sh /opt/sfm/junit_job.py /opt/sfm/
USER 10001:10001
RUN mkdir -p /work/.home /work/.sfm-config
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 SFM_PROPAGATE_CHANGES_HOME=/work/.sfm-config SFM_PROPAGATE_CHANGES_CACHE=/work/.sfm-cache HOME=/work/.home PATH=/usr/local/cargo/bin:/usr/bin:/bin:/usr/lib/jvm/java-17-openjdk-amd64/bin:/opt/sfm/cargo/bin
USER 10001:10001
WORKDIR /work
ENTRYPOINT ["/bin/sh", "/opt/sfm/entrypoint.sh"]
CMD ["{}", "sfm:move_1_stack_direct"]
FROM localhost/sfm-graphics@sha256:9f7f1c4b8cbb7452c3fa5bb30822ee112f6ea446e2f391e7e45ee20b13217b84 AS agent-sdks
SHELL ["/usr/bin/setpriv", "--no-new-privs", "/bin/sh", "-c"]
USER root
COPY sdks/21.tar.gz sdks/25.tar.gz /sdk-archives/
RUN mkdir -p /opt/sfm/jdks/21 /opt/sfm/jdks/25 && tar -xzf /sdk-archives/21.tar.gz --strip-components=1 -C /opt/sfm/jdks/21 && tar -xzf /sdk-archives/25.tar.gz --strip-components=1 -C /opt/sfm/jdks/25 && /opt/sfm/jdks/21/bin/java -version && /opt/sfm/jdks/25/bin/java -version
FROM developer AS support-agent
USER root
COPY --from=agent-sdks /opt/sfm/jdks/ /opt/sfm/jdks/
COPY --chown=0:0 snapshot/platform/minecraft/core-liquid-template/ /opt/sfm/catalog/platform/minecraft/core-liquid-template/
COPY --chown=0:0 snapshot/platform/minecraft/projections.json /opt/sfm/catalog/platform/minecraft/projections.json
COPY --chown=0:0 snapshot/platform/minecraft/build-configuration/ /opt/sfm/catalog/platform/minecraft/build-configuration/
COPY --chown=0:0 snapshot/support-readiness.json /opt/sfm/catalog/support-readiness.json
COPY --chown=0:0 support_agent.py /opt/sfm/support_agent.py
COPY --from=game-prep --chown=0:0 /opt/sfm/cargo/bin/sfm /opt/sfm/cargo/bin/sfm
RUN chmod 0755 /opt/sfm/cargo/bin/sfm && python3 -c 'import hashlib,json,pathlib; root=pathlib.Path("/work/platform/cli/sfm"); sources=[p for p in root.rglob("*") if p.is_file() and (p.suffix in (".rs",".py",".java") or p.name in ("Cargo.toml","Cargo.lock","build.rs")) and "target" not in p.parts]; identity="sha256:"+hashlib.sha256(json.dumps({{str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(sources)}},sort_keys=True).encode()).hexdigest(); binary=pathlib.Path("/opt/sfm/cargo/bin/sfm"); pathlib.Path("/opt/sfm/control-cli-pin.json").write_text(json.dumps({{"schema":"sfm:container_control_cli@1","executable":str(binary),"sha256":"sha256:"+hashlib.sha256(binary.read_bytes()).hexdigest(),"source_identity":identity}}))'
ENV SFM_CONTAINER_CONTROL_CLI_MANIFEST=/opt/sfm/control-cli-pin.json
USER 10001:10001
RUN mkdir -p /work/agent-sessions
ENTRYPOINT ["/usr/bin/setpriv", "--no-new-privs", "/usr/bin/python3", "/opt/sfm/support_agent.py"]
CMD ["list"]
FROM developer AS tests
ENTRYPOINT ["/usr/bin/setpriv", "--no-new-privs", "/usr/bin/timeout", "900s", "/usr/bin/python3", "/opt/sfm/junit_job.py"]
CMD ["{}"]
"#,
        contract.base_image,
        request.projection,
        request.projection,
        request.projection,
        contract.base_image,
        request.projection,
        request.projection
    ))
}

pub(super) fn build_arguments(request: &ContainerBuildRequest) -> Vec<OsString> {
    let mut arguments = super::policy::build_arguments(
        request.engine == ContainerEngine::Podman,
        &request.context,
        &request.tag,
    );
    if !request.offline {
        arguments[1] = if request.engine == ContainerEngine::Podman {
            "--pull=missing"
        } else {
            "--pull=true"
        }
        .into();
        arguments[2] = if request.engine == ContainerEngine::Podman {
            "--network=private"
        } else {
            "--network=default"
        }
        .into();
    }
    if request.engine == ContainerEngine::Podman {
        arguments.splice(
            3..3,
            [
                "--pid=private",
                "--ipc=private",
                "--uts=private",
                "--cap-drop=ALL",
                "--memory=6g",
                "--memory-swap=6g",
                "--cpu-period=100000",
                "--cpu-quota=200000",
                "--jobs=1",
                "--http-proxy=false",
                "--ulimit=nproc=512:512",
            ]
            .map(OsString::from),
        );
    }
    arguments.extend([
        OsString::from("--target"),
        OsString::from(request.profile.stage()),
    ]);
    arguments
}

pub(super) fn verify_context(root: &Path, report: &ContextManifest) -> eyre::Result<()> {
    let _timer = super::phase_timer::PhaseTimer::start("context_validation", report.total_bytes);
    ensure!(
        report.context_prepared && report.missing.is_empty() && report.context_identity.is_some(),
        "context is incomplete and cannot be built"
    );
    let raw = crate::source_projection::core_catalog::read_bounded_input(
        root,
        "context-manifest.json",
        32 * 1024 * 1024,
    )?;
    ensure!(
        raw.len() <= 32 * 1024 * 1024,
        "context manifest exceeds byte budget"
    );
    let expected = facet_json::to_string_pretty(report)?;
    ensure!(
        raw == expected.as_bytes(),
        "context manifest changed after preparation"
    );
    let mut observed = 0_usize;
    for entry in walkdir::WalkDir::new(root)
        .follow_links(false)
        .max_depth(32)
    {
        let entry = entry?;
        ensure!(!entry.file_type().is_symlink(), "context contains a link");
        if !entry.file_type().is_file() {
            continue;
        }
        let relative = entry
            .path()
            .strip_prefix(root)?
            .to_str()
            .ok_or_else(|| eyre::eyre!("non-Unicode context path"))?
            .replace('\\', "/");
        if relative == "context-manifest.json" {
            continue;
        }
        if relative == "build-progress.log" {
            ensure!(
                entry.metadata()?.len() <= 64 * 1024 * 1024,
                "diagnostic log exceeds byte budget"
            );
            continue;
        }
        let expected = report
            .files
            .get(&relative)
            .ok_or_else(|| eyre::eyre!("context contains unlisted file: {relative}"))?;
        let mut file = fs::File::open(checked_file(root, &relative)?)?;
        ensure!(
            file.metadata()?.len() == expected.bytes,
            "context file size changed: {relative}"
        );
        let mut hash = Sha256::new();
        let mut buffer = vec![0_u8; 65_536].into_boxed_slice();
        loop {
            let read = file.read(&mut buffer)?;
            if read == 0 {
                break;
            }
            hash.update(&buffer[..read]);
        }
        ensure!(
            format!("sha256:{:x}", hash.finalize()) == expected.sha256,
            "context file hash changed: {relative}"
        );
        observed += 1;
    }
    ensure!(
        observed == report.files.len(),
        "context is missing allowlisted files"
    );
    Ok(())
}

pub(super) fn build(
    request: &ContainerBuildRequest,
    report: &ContextManifest,
    cancellation: &CancellationToken,
    timeout: Duration,
) -> eyre::Result<Option<super::image_identity::ImageIdentity>> {
    verify_context(&request.context, report)?;
    cancellation.bail_if_cancelled()?;
    if request.transport == super::ContainerTransport::NativeApi {
        let identity = super::native_api::build(request, report, cancellation, timeout)?;
        tracing::info!(
            ?identity,
            "native API image built; gameplay remains unvalidated"
        );
        return Ok(Some(identity));
    }
    ensure!(
        request.engine == ContainerEngine::Podman,
        "Docker build isolation is not yet verified; use the reviewed Podman backend"
    );
    ensure!(!timeout.is_zero(), "no container build time budget remains");
    tracing::info!(engine = request.engine.executable(), tag = %request.tag, "building verified context; no push or runtime acceptance implied");
    let mut child = Command::new(request.engine.executable())
        .args(build_arguments(request))
        .stdin(Stdio::null())
        .stdout(Stdio::piped())
        .stderr(Stdio::inherit())
        .spawn()?;
    let mut stdout = child
        .stdout
        .take()
        .ok_or_else(|| eyre::eyre!("engine output pipe missing"))?;
    // Stream engine output to stderr so typed CLI stdout remains a single report.
    let reader = std::thread::spawn(move || std::io::copy(&mut stdout, &mut std::io::stderr()));
    let started = Instant::now();
    let result = loop {
        if cancellation.is_cancelled() || started.elapsed() >= timeout {
            let _ = child.kill();
            let _ = child.wait();
            break Err(eyre::eyre!(
                "container engine build cancelled or exceeded its time budget; image completion is unconfirmed"
            ));
        }
        if let Some(status) = child.try_wait()? {
            break if status.success() {
                Ok(())
            } else {
                Err(eyre::eyre!("container engine build failed: {status}"))
            };
        }
        std::thread::sleep(Duration::from_millis(100));
    };
    reader
        .join()
        .map_err(|_panic| eyre::eyre!("engine output reader failed"))??;
    result.map(|()| None)
}
