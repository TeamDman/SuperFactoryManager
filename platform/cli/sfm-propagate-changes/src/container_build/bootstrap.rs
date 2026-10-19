use super::ContainerBuildRequest;
use super::context_file::ContextFile;
use super::context_manifest::ContextManifest;
use super::linux_toolchain_lock::LinuxToolchainLock;
use crate::cancellation::CancellationToken;
use crate::source_projection::provenance::sha256;
use std::collections::BTreeMap;
use std::io::Read as _;
use std::path::PathBuf;
use std::process::Command;
use std::process::Stdio;
use std::time::Duration;
use std::time::Instant;

fn run(
    mut command: Command,
    token: &CancellationToken,
    timeout: Duration,
) -> eyre::Result<Vec<u8>> {
    let mut child = command
        .stdin(Stdio::null())
        .stdout(Stdio::piped())
        .stderr(Stdio::inherit())
        .spawn()?;
    let pipe = child
        .stdout
        .take()
        .ok_or_else(|| eyre::eyre!("bootstrap output missing"))?;
    let reader = std::thread::spawn(move || {
        let mut bytes = Vec::new();
        pipe.take(32 * 1024 * 1024 + 1)
            .read_to_end(&mut bytes)
            .map(|_| bytes)
    });
    let _timer = super::phase_timer::PhaseTimer::start("bootstrap_subprocess", 0);
    let start = Instant::now();
    let status = loop {
        if token.is_cancelled() || start.elapsed() >= timeout {
            let _ = child.kill();
            let _ = child.wait();
            break Err(eyre::eyre!(
                "bootstrap helper exceeded time budget or was cancelled"
            ));
        }
        if let Some(status) = child.try_wait()? {
            break Ok(status);
        }
        std::thread::sleep(Duration::from_millis(100));
    };
    let bytes = reader
        .join()
        .map_err(|_panic| eyre::eyre!("bootstrap output reader failed"))??;
    eyre::ensure!(
        status?.success(),
        "bootstrap helper failed; cached pinned inputs are incomplete or invalid"
    );
    eyre::ensure!(
        bytes.len() <= 32 * 1024 * 1024,
        "bootstrap helper output budget exceeded"
    );
    Ok(bytes)
}

/// Bootstrap uses the canonical Cargo graph plus the narrow, isolated stdlib exporter.
#[expect(
    clippy::too_many_lines,
    reason = "Sequential bounded cache export, native build, copy and provenance transaction"
)]
pub(super) fn build(
    request: &ContainerBuildRequest,
    initial: &ContextManifest,
    token: &CancellationToken,
    timeout: Duration,
) -> eyre::Result<PathBuf> {
    eyre::ensure!(
        request.transport == super::ContainerTransport::NativeApi,
        "automatic Linux bootstrap requires reviewed native API transport"
    );
    let _timer = super::phase_timer::PhaseTimer::start("linux_tooling", 0);
    let start = Instant::now();
    let main_identity = super::snapshot::cli_source_identity(&request.repo_root)?;
    let control = super::snapshot::cli_inputs(&request.repo_root, "platform/cli/sfm")?;
    let control_inventory = control
        .iter()
        .map(|(p, b)| (p.clone(), sha256(b)))
        .collect::<BTreeMap<_, _>>();
    let control_identity = sha256(facet_json::to_string(&control_inventory)?.as_bytes());
    let identity =
        sha256(format!("stable-multistage-v2:{main_identity}:{control_identity}").as_bytes());
    let parent = request
        .context
        .parent()
        .ok_or_else(|| eyre::eyre!("context requires parent"))?;
    std::fs::create_dir_all(parent)?;
    let durable = parent.join(format!(".sfm-linux-tooling-{}", &identity[7..23]));
    let contract_path = durable.join("linux-toolchain.json");
    if contract_path.is_file() {
        let bytes = std::fs::read(&contract_path)?;
        let pin: LinuxToolchainLock = facet_json::from_slice(&bytes)?;
        eyre::ensure!(
            pin.source_lock_sha256 == initial.source_lock_sha256
                && pin.cli_source_identity == main_identity,
            "cached Linux tooling provenance mismatch"
        );
        let cli = crate::source_projection::core_catalog::read_bounded_input(
            &durable,
            &pin.cli_path,
            256 * 1024 * 1024,
        )?;
        eyre::ensure!(
            sha256(&cli) == pin.cli_sha256 && super::policy::linux_elf(&cli),
            "cached Linux CLI changed"
        );
        return Ok(contract_path);
    }
    eyre::ensure!(
        !durable.exists(),
        "incomplete prior bootstrap output exists; choose a fresh context parent"
    );
    let work = tempfile::tempdir_in(parent)?;
    std::fs::write(
        work.path().join("export.py"),
        include_bytes!("bootstrap.py"),
    )?;
    for (cli, label) in [("sfm-propagate-changes", "propagate"), ("sfm", "control")] {
        let mut cmd = Command::new("cargo");
        cmd.args([
            "metadata",
            "--locked",
            "--offline",
            "--format-version",
            "1",
            "--filter-platform",
            "x86_64-unknown-linux-gnu",
        ])
        .current_dir(request.repo_root.join("platform/cli").join(cli));
        let output = run(cmd, token, timeout.saturating_sub(start.elapsed()))?;
        std::fs::write(
            work.path().join(format!("cargo-linux-{label}.json")),
            output,
        )?;
    }
    let cargo_home = std::env::var_os("CARGO_HOME")
        .map(PathBuf::from)
        .or_else(|| directories_next::UserDirs::new().map(|dirs| dirs.home_dir().join(".cargo")))
        .ok_or_else(|| eyre::eyre!("CARGO_HOME required for pinned bootstrap inputs"))?;
    #[cfg(windows)]
    let mut python = {
        let mut cmd = Command::new("py");
        cmd.arg("-3");
        cmd
    };
    #[cfg(not(windows))]
    let mut python = Command::new("python3");
    let context = work.path().join("context");
    // Never derive a builder from a previous builder. Engine layers cache stable stages.
    let base = "localhost/sfm-graphics@sha256:9f7f1c4b8cbb7452c3fa5bb30822ee112f6ea446e2f391e7e45ee20b13217b84";
    python
        .arg("-I")
        .arg(work.path().join("export.py"))
        .arg("--context")
        .arg(&context)
        .arg("--repo-root")
        .arg(&request.repo_root)
        .arg("--base")
        .arg(base)
        .arg("--cargo-home")
        .arg(&cargo_home);
    run(python, token, timeout.saturating_sub(start.elapsed()))?;
    let receipt_bytes = std::fs::read(context.join("tooling-context.json"))?;
    let receipt: super::tooling_receipt::ToolingReceipt = facet_json::from_slice(&receipt_bytes)?;
    eyre::ensure!(
        receipt.context_prepared
            && !receipt.image_built
            && !receipt.gameplay_validated
            && receipt.cli_source_identity == main_identity,
        "bootstrap source identity/completion mismatch"
    );
    for (path, expected) in &receipt.source_files {
        let bytes = crate::source_projection::core_catalog::read_bounded_input(
            &request.repo_root,
            path,
            16 * 1024 * 1024,
        )?;
        eyre::ensure!(
            sha256(&bytes) == *expected,
            "bootstrap source file changed: {path}"
        );
    }
    let mut report = initial.clone();
    report.context_prepared = true;
    report.missing.clear();
    report.total_bytes = receipt.bytes;
    report.base_image = Some(receipt.base_image);
    report.context_identity = Some(sha256(&receipt_bytes));
    report.files = receipt
        .files
        .into_iter()
        .map(|(path, file)| {
            (
                path.clone(),
                ContextFile {
                    path,
                    bytes: file.bytes,
                    sha256: file.sha256,
                    parent_identity: file.parent,
                    expected_hash: None,
                    authenticated_parents: BTreeMap::new(),
                },
            )
        })
        .collect();
    report.files.insert(
        "tooling-context.json".into(),
        ContextFile {
            path: "tooling-context.json".into(),
            bytes: receipt_bytes.len() as u64,
            sha256: sha256(&receipt_bytes),
            parent_identity: identity.clone(),
            expected_hash: None,
            authenticated_parents: BTreeMap::new(),
        },
    );
    std::fs::write(
        context.join("context-manifest.json"),
        facet_json::to_string_pretty(&report)?,
    )?;
    let mut build = request.clone();
    build.context = context;
    build.offline = true;
    build.tag = format!("sfm-linux-tooling:{}", &identity[7..23]);
    tracing::info!(%identity,memory_limit=super::native_api::memory_limit(),cargo_jobs=1,"starting cache-only Linux tooling compilation");
    let image = match super::native_api::build(
        &build,
        &report,
        token,
        timeout.saturating_sub(start.elapsed()),
    ) {
        Ok(image) => image,
        Err(error) => {
            let log = build.context.join("build-progress.log");
            if log.is_file() {
                std::fs::copy(
                    log,
                    parent.join(format!("sfm-tooling-failure-{}.jsonl", &identity[7..23])),
                )?;
            }
            return Err(error);
        }
    };
    eyre::ensure!(
        super::snapshot::cli_source_identity(&request.repo_root)? == main_identity,
        "sources changed during Linux tooling build; output not admitted"
    );
    let current_control = super::snapshot::cli_inputs(&request.repo_root, "platform/cli/sfm")?
        .iter()
        .map(|(path, bytes)| (path.clone(), sha256(bytes)))
        .collect::<BTreeMap<_, _>>();
    eyre::ensure!(
        current_control == control_inventory,
        "control CLI sources changed during Linux tooling build; output not admitted"
    );
    let endpoint = build
        .engine_endpoint
        .as_ref()
        .ok_or_else(|| eyre::eyre!("local engine endpoint required"))?;
    let cli = super::native_api::copy_linux_cli(
        endpoint,
        &image.image_config_id,
        timeout.saturating_sub(start.elapsed()),
    )?;
    eyre::ensure!(
        super::policy::linux_elf(&cli),
        "bootstrap output is not Linux amd64 ELF"
    );
    let pin = LinuxToolchainLock {
        schema: "sfm:linux_toolchain@1".into(),
        platform: "linux/amd64".into(),
        source_lock_sha256: initial.source_lock_sha256.clone(),
        base_image: image
            .repository_digests
            .iter()
            .find(|digest| {
                digest.starts_with("localhost/") && digest.ends_with(&image.manifest_digest)
            })
            .ok_or_else(|| eyre::eyre!("built tooling image has no local digest reference"))?
            .clone(),
        java_major: 17,
        java_identity: "17.0.20.1+1-1-deb12u1-Debian".into(),
        cli_path: "sfm-propagate-changes".into(),
        cli_sha256: sha256(&cli),
        cli_source_identity: main_identity,
    };
    std::fs::create_dir(&durable)?;
    std::fs::write(durable.join(&pin.cli_path), cli)?;
    std::fs::write(
        durable.join("image-identity.json"),
        facet_json::to_string_pretty(&image)?,
    )?;
    std::fs::write(&contract_path, facet_json::to_string_pretty(&pin)?)?;
    tracing::info!(seconds=start.elapsed().as_secs(),digest=%image.manifest_digest,"Linux tooling bootstrap complete");
    Ok(contract_path)
}
