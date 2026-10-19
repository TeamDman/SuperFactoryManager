//! Embedded-adapter launch and invocation-held compiler consumer.
//! No fixture classpath, arbitrary adapter path or inherited output is accepted.
//! The source/recipe acquisition and public CLI route are separate callers.

use super::nfrt_child_process::OwnedChild;
use super::nfrt_host_protocol::NfrtHostOperation;
use super::nfrt_host_protocol::NfrtHostProtocol;
use super::nfrt_host_protocol::read_frame;
use super::nfrt_host_session::NfrtHostSession;
use super::nfrt_launch_contract::PreparedNfrtInvocationInput;
use super::nfrt_preparation_phase::NfrtPreparationPhase;
use crate::source_projection::nfrt_input_store::NfrtInputStore;
use crate::source_projection::nfrt_project_owner::NfrtProjectOwner;
use crate::source_projection::nfrt_tool_sdk::NfrtToolSdk;
use eyre::Result;
use eyre::ensure;
use std::fs::OpenOptions;
use std::io::BufReader;
use std::io::Cursor;
use std::path::Path;
use std::process::Command;
use std::process::Stdio;
use std::sync::mpsc;
use std::time::Duration;
use std::time::Instant;

const HOST_SOURCES: &[(&str, &[u8])] = &[
    (
        "SFMHostTiming.java",
        include_bytes!("nfrt_host/SFMHostTiming.java"),
    ),
    (
        "SFMClosedDownloadManager.java",
        include_bytes!("nfrt_host/SFMClosedDownloadManager.java"),
    ),
    (
        "SFMFreshProducerBindings.java",
        include_bytes!("nfrt_host/SFMFreshProducerBindings.java"),
    ),
    (
        "SFMHostChannel.java",
        include_bytes!("nfrt_host/SFMHostChannel.java"),
    ),
    (
        "SFMHostExternalToolAction.java",
        include_bytes!("nfrt_host/SFMHostExternalToolAction.java"),
    ),
    (
        "SFMHostGraph.java",
        include_bytes!("nfrt_host/SFMHostGraph.java"),
    ),
    (
        "SFMHostProducerSealer.java",
        include_bytes!("nfrt_host/SFMHostProducerSealer.java"),
    ),
    (
        "SFMHostProjectInputs.java",
        include_bytes!("nfrt_host/SFMHostProjectInputs.java"),
    ),
    (
        "SFMHostRecompileAction.java",
        include_bytes!("nfrt_host/SFMHostRecompileAction.java"),
    ),
    (
        "SFMHostTransformPreparation.java",
        include_bytes!("nfrt_host/SFMHostTransformPreparation.java"),
    ),
    (
        "SFMHostTransformToolAction.java",
        include_bytes!("nfrt_host/SFMHostTransformToolAction.java"),
    ),
    (
        "SFMNamedEngineFactory.java",
        include_bytes!("nfrt_host/SFMNamedEngineFactory.java"),
    ),
    (
        "SFMNamedHostPreparationMain.java",
        include_bytes!("nfrt_host/SFMNamedHostPreparationMain.java"),
    ),
    (
        "SFMNamedNeoFormLauncher.java",
        include_bytes!("nfrt_host/SFMNamedNeoFormLauncher.java"),
    ),
    (
        "SFMRustContractConverter.java",
        include_bytes!("nfrt_host/SFMRustContractConverter.java"),
    ),
];

/// Run the exact pinned recipe and consume both immutable results while the
/// store, producer handles and authenticated SDK stay alive. Not a cache API.
#[expect(
    clippy::too_many_lines,
    reason = "Keep owned JVM, pipe reader and retained-input cleanup visible across every exit path."
)]
pub(crate) fn with_owned_compiler_inputs<T>(
    project: &dyn NfrtProjectOwner,
    prepared: &PreparedNfrtInvocationInput,
    sdk: &mut NfrtToolSdk,
    cancellation: &crate::cancellation::CancellationToken,
    derived_cache_root: Option<&Path>,
    consume: impl FnOnce(
        &Path,
        &Path,
        &[&crate::source_projection::nfrt_producer_output_receipt::NfrtProducerOutputReceipt; 2],
    ) -> Result<T>,
) -> Result<T> {
    let _invocation = tracing::info_span!("nfrt_owned_host",
        invocation_id = %prepared.receipt().invocation_id,
        compiler_release = prepared.receipt().compiler_release)
    .entered();
    let mut preparation = NfrtPreparationPhase::start("owned_host.preparation");
    cancellation.bail_if_cancelled()?;
    NfrtPreparationPhase::run("owned_host.source_recheck.before_store", || {
        project.recheck_source()
    })?;
    NfrtPreparationPhase::run("owned_host.sdk_recheck.before_store", || sdk.recheck())?;
    let mut store = NfrtPreparationPhase::run("owned_host.store_create", || {
        if let Some(root) = derived_cache_root {
            NfrtInputStore::create_with_derived_root(project, prepared, Some(root))
        } else {
            NfrtInputStore::create(project, prepared)
        }
    })?;
    NfrtPreparationPhase::run("owned_host.publish_project_inputs", || {
        store.publish_project_inputs(project)
    })?;
    tracing::info!(
        phase = "owned_host.store_policy",
        inherited_cache_restore_enabled = false,
        fresh_owned_store = true,
        "nfrt_preparation_work"
    );
    let runtime =
        unique_runtime_snapshot(&prepared.receipt().inputs, prepared.source_pin_origin())?;
    let runtime = store.root().join(runtime);
    let mut host_sources = NfrtPreparationPhase::start("owned_host.publish_host_sources");
    host_sources.work(
        HOST_SOURCES.len() as u64,
        HOST_SOURCES
            .iter()
            .map(|(_, bytes)| bytes.len() as u64)
            .sum(),
    );
    let (sources, classes) = host_sources.finish(store.publish_host_sources(HOST_SOURCES))?;
    let mut javac_preparation = NfrtPreparationPhase::start("owned_host.javac_prepare");
    let mut javac = sdk.executable().to_owned();
    javac.set_file_name(if cfg!(windows) { "javac.exe" } else { "javac" });
    let mut command = Command::new(javac);
    command
        .args(["-encoding", "UTF-8", "-proc:none", "-cp"])
        .arg(&runtime)
        .arg("-d")
        .arg(&classes)
        .args(sources);
    clean_jvm_environment(&mut command, prepared);
    let log = OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(store.root().join("adapter/javac.log"))?;
    command
        .stdin(Stdio::null())
        .stdout(Stdio::from(log.try_clone()?))
        .stderr(Stdio::from(log));
    javac_preparation.success();
    drop(javac_preparation);
    let mut compiler = OwnedChild(NfrtPreparationPhase::run("owned_host.javac_spawn", || {
        command.spawn().map_err(Into::into)
    })?);
    tracing::info!(
        phase = "owned_host.javac",
        child_pid = compiler.0.id(),
        source_files = HOST_SOURCES.len(),
        "nfrt_preparation_process"
    );
    NfrtPreparationPhase::run("owned_host.javac_process_wall", || {
        wait_success(
            &mut compiler,
            Duration::from_mins(5),
            "embedded host javac",
            cancellation,
        )
    })?;
    NfrtPreparationPhase::run("owned_host.sdk_recheck.after_javac", || sdk.recheck())?;
    NfrtPreparationPhase::run("owned_host.source_recheck.after_javac", || {
        project.recheck_source()
    })?;

    let host_jar = NfrtPreparationPhase::run("owned_host.publish_host_jar", || {
        store.publish_host_jar(HOST_SOURCES)
    })?;
    let mut host_preparation = NfrtPreparationPhase::start("owned_host.host_command_prepare");
    let classpath = std::env::join_paths([
        verified_java_spelling(&host_jar)?,
        verified_java_spelling(&runtime)?,
    ])?;
    let mut command = Command::new(sdk.executable());
    command
        .args(["-Xmx4g", "-cp"])
        .arg(classpath)
        .arg("ca.teamdman.sfm.toolchain.nfrt.SFMNamedHostPreparationMain")
        .arg(store.root())
        .arg(store.contract_identity())
        .arg("minecraft-compile-proof");
    clean_jvm_environment(&mut command, prepared);
    let log = OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(store.root().join("adapter/host.log"))?;
    command
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::from(log));
    host_preparation.success();
    drop(host_preparation);
    let host_lifetime = NfrtPreparationPhase::start("owned_host.host_lifetime");
    let mut host = OwnedChild(NfrtPreparationPhase::run("owned_host.host_spawn", || {
        command.spawn().map_err(Into::into)
    })?);
    tracing::info!(
        phase = "owned_host.host_spawn",
        child_pid = host.0.id(),
        "nfrt_preparation_process"
    );
    eprintln!(
        "Owned NFRT host started pid={} root={}",
        host.0.id(),
        store.root().display()
    );
    let mut writer = host
        .0
        .stdin
        .take()
        .ok_or_else(|| eyre::eyre!("Missing host stdin"))?;
    let reader = host
        .0
        .stdout
        .take()
        .ok_or_else(|| eyre::eyre!("Missing host stdout"))?;
    let mut protocol = NfrtHostProtocol::new(store.contract_identity().to_owned())?;
    let session_creation = NfrtPreparationPhase::start("owned_host.session_new");
    let mut session =
        session_creation.finish(NfrtHostSession::new(project, prepared, &mut store))?;
    let result = std::thread::scope(|scope| -> Result<()> {
        // One queued bounded frame: no unbounded buffering or detached reader.
        let (sender, receiver) = mpsc::sync_channel(1);
        let reader_thread = scope.spawn(move || {
            let mut reader = BufReader::new(reader);
            loop {
                let frame = read_frame(&mut reader);
                let failed = frame.is_err();
                if sender.send(frame).is_err() || failed {
                    break;
                }
            }
        });
        let started = Instant::now();
        let result = (|| -> Result<()> {
            for request_id in 1_u64..=4096 {
                let request_span = tracing::info_span!(
                    "nfrt_host_request",
                    request_id,
                    operation = tracing::field::Empty,
                    host_pid = host.0.id()
                );
                let _request_span = request_span.enter();
                let mut protocol_wait = NfrtPreparationPhase::start("owned_host.protocol_wait");
                let frame = loop {
                    cancellation.bail_if_cancelled()?;
                    ensure!(
                        started.elapsed() < Duration::from_hours(1),
                        "Owned host exceeded its one-hour bound"
                    );
                    match receiver.recv_timeout(Duration::from_secs(10)) {
                        Ok(frame) => break frame?,
                        Err(mpsc::RecvTimeoutError::Timeout) => {
                            ensure!(
                                host.0.try_wait()?.is_none(),
                                "Owned host exited before completion"
                            );
                            eprintln!("Owned NFRT host active: {}s", started.elapsed().as_secs());
                        }
                        Err(mpsc::RecvTimeoutError::Disconnected) => {
                            eyre::bail!("Owned host pipe closed before completion")
                        }
                    }
                };
                protocol_wait.work(1, frame.len() as u64);
                protocol_wait.success();
                drop(protocol_wait);
                let request = NfrtPreparationPhase::run("owned_host.protocol_decode", || {
                    protocol.read_request(&mut Cursor::new(frame))
                })?;
                request_span.record("operation", tracing::field::debug(request.operation));
                tracing::info!(phase = "owned_host.protocol_request", request_id,
                    operation = ?request.operation, request_bytes = request.payload.len(),
                    "nfrt_preparation_work");
                let dispatch = NfrtPreparationPhase::start("owned_host.protocol_dispatch");
                let reply = dispatch.finish(if request.operation == NfrtHostOperation::Tool {
                    session.execute_tool(&request, sdk, cancellation)
                } else {
                    session.handle(&request)
                })?;
                NfrtPreparationPhase::run("owned_host.protocol_reply", || {
                    protocol.reply(&mut writer, &request, &reply)
                })?;
                if request.operation == NfrtHostOperation::Complete {
                    return Ok(());
                }
            }
            eyre::bail!("Owned host exceeded request bound")
        })();
        drop(writer);
        drop(receiver);
        finish_host(&mut host, result, cancellation, reader_thread)
    });
    host_lifetime.finish(result)?;
    NfrtPreparationPhase::run("owned_host.sdk_recheck.after_host", || sdk.recheck())?;
    cancellation.bail_if_cancelled()?;
    let export_key = if std::env::var_os("SFM_AGENT_DERIVED_EXPORT_ROOT").is_some() {
        NfrtPreparationPhase::run("owned_host.derived_export_key", || {
            super::derived_toolchain_cache::key(project, prepared, sdk)
        })?
    } else {
        None
    };
    let receipts = session.compiler_result_receipts()?;
    // Preparation ends before the downstream SFM compiler/test/live consumer.
    preparation.success();
    drop(preparation);
    let consumer = NfrtPreparationPhase::start("owned_host.compiler_input_consumer");
    let result = session.with_compiler_inputs(|classes, sources| {
        if let Some(key) = &export_key {
            super::derived_toolchain_cache::export(
                key,
                &prepared.contract_identity(),
                classes,
                sources,
                &receipts,
            )?;
        }
        consume(classes, sources, &receipts)
    });
    NfrtPreparationPhase::run("owned_host.sdk_recheck.after_consumer", || sdk.recheck())?;
    consumer.finish(result)
}

fn clean_jvm_environment(command: &mut Command, prepared: &PreparedNfrtInvocationInput) {
    for name in &prepared.receipt().required_clean_environment_names {
        command.env_remove(name);
    }
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt as _;
        command.creation_flags(0x0800_0000); // CREATE_NO_WINDOW.
    }
}

fn unique_runtime_snapshot<'a>(
    inputs: &'a [super::nfrt_launch_contract::NfrtInputSnapshotExport],
    source_pin_origin: &str,
) -> Result<&'a str> {
    let mut selected = inputs.iter().filter(|input| {
        input.coordinate.as_deref() == Some("net.neoforged:neoform-runtime:2.0.19:all")
    });
    let input = selected
        .next()
        .ok_or_else(|| eyre::eyre!("Missing pinned NFRT host runtime"))?;
    ensure!(
        selected.next().is_none()
            && matches!(
                source_pin_origin,
                "original_schema2_pin" | "captured_schema4_pin"
            )
            && input.origin == source_pin_origin
            && input.full_sha256
                == "sha256:6db13ff7efaa70cf076f1f5d6a4f116885a3a5fa4e03960da88b9d92b25089bd",
        "Ambiguous or changed original NFRT host runtime"
    );
    Ok(&input.snapshot_relative_path)
}

fn verified_java_spelling(path: &Path) -> Result<std::path::PathBuf> {
    let normal = dunce::simplified(path);
    ensure!(
        normal.is_absolute() && std::fs::canonicalize(normal)? == std::fs::canonicalize(path)?,
        "Java classpath spelling changed the held file identity"
    );
    Ok(normal.to_owned())
}

fn wait_success(
    child: &mut OwnedChild,
    limit: Duration,
    role: &str,
    cancellation: &crate::cancellation::CancellationToken,
) -> Result<()> {
    let started = Instant::now();
    loop {
        cancellation.bail_if_cancelled()?;
        if let Some(status) = child.0.try_wait()? {
            tracing::info!(phase = "owned_host.process_exit", process_role = role,
                child_pid = child.0.id(), exit_code = ?status.code(), child_success = status.success(),
                "nfrt_preparation_process_exit");
            ensure!(status.success(), "{role} failed: {status}");
            return Ok(());
        }
        ensure!(
            started.elapsed() < limit,
            "{role} exceeded its process bound"
        );
        std::thread::sleep(Duration::from_millis(100));
    }
}

// Caller closes protocol stdin and the frame receiver before entering this seam.
// The reader joins only after the exact owned child has terminated and been reaped.
fn finish_host(
    host: &mut OwnedChild,
    result: Result<()>,
    cancellation: &crate::cancellation::CancellationToken,
    reader_thread: std::thread::ScopedJoinHandle<'_, ()>,
) -> Result<()> {
    // Terminate/reap this exact owned child before joining its pipe reader.
    // Otherwise an error path could wait forever for Java to close stdout.
    let host_exit = NfrtPreparationPhase::start("owned_host.host_exit");
    let terminal = host_exit.finish(if result.is_ok() {
        wait_success(
            host,
            Duration::from_secs(30),
            "owned NFRT host exit",
            cancellation,
        )
    } else {
        let _ = host.0.kill();
        host.0
            .wait()
            .map(|status| {
                tracing::info!(phase = "owned_host.host_exit", child_pid = host.0.id(),
                exit_code = ?status.code(), child_success = status.success(),
                "nfrt_preparation_process_exit");
            })
            .map_err(Into::into)
    });
    if terminal.is_err() {
        let _ = host.0.kill();
        let _ = host.0.wait();
    }
    let joined = reader_thread
        .join()
        .map_err(|panic| eyre::eyre!("Owned host pipe reader panicked: {panic:?}"));
    result?;
    terminal?;
    joined?;
    Ok(())
}

// Test registration is compiled only after this outer cfg admits it.
#[cfg(test)]
include!("nfrt_host_test_registration.rs");
