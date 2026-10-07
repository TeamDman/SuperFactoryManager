//! Embedded-adapter launch and invocation-held compiler consumer.
//! No fixture classpath, arbitrary adapter path or inherited output is accepted.
//! The source/recipe acquisition and public CLI route are separate callers.

use super::nfrt_child_process::OwnedChild;
use super::nfrt_host_protocol::NfrtHostProtocol;
use super::nfrt_host_protocol::read_frame;
use super::nfrt_host_session::NfrtHostSession;
use super::nfrt_launch_contract::PreparedNfrtInvocationInput;
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
    consume: impl FnOnce(&Path, &Path) -> Result<T>,
) -> Result<T> {
    cancellation.bail_if_cancelled()?;
    project.recheck_source()?;
    sdk.recheck()?;
    let mut store = NfrtInputStore::create(project, prepared)?;
    store.publish_project_inputs(project)?;
    let runtime =
        unique_runtime_snapshot(&prepared.receipt().inputs, prepared.source_pin_origin())?;
    let runtime = store.root().join(runtime);
    let (sources, classes) = store.publish_host_sources(HOST_SOURCES)?;
    let mut javac = sdk.executable().to_owned();
    javac.set_file_name("javac.exe");
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
    let mut compiler = OwnedChild(command.spawn()?);
    wait_success(
        &mut compiler,
        Duration::from_mins(5),
        "embedded host javac",
        cancellation,
    )?;
    sdk.recheck()?;
    project.recheck_source()?;

    let host_jar = store.publish_host_jar(HOST_SOURCES)?;
    let mut classpath = verified_java_spelling(&host_jar)?.into_os_string();
    classpath.push(";");
    classpath.push(verified_java_spelling(&runtime)?);
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
    let mut host = OwnedChild(command.spawn()?);
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
    let mut session = NfrtHostSession::new(project, prepared, &mut store)?;
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
            for _ in 0..4096 {
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
                let request = protocol.read_request(&mut Cursor::new(frame))?;
                let reply = if request.operation == "tool" {
                    session.execute_tool(&request, sdk, cancellation)?
                } else {
                    session.handle(&request)?
                };
                protocol.reply(&mut writer, &request, &reply)?;
                if request.operation == "complete" {
                    return Ok(());
                }
            }
            eyre::bail!("Owned host exceeded request bound")
        })();
        drop(writer);
        drop(receiver);
        // Terminate/reap this exact owned child before joining its pipe reader.
        // Otherwise an error path could wait forever for Java to close stdout.
        let terminal = if result.is_ok() {
            wait_success(
                &mut host,
                Duration::from_secs(30),
                "owned NFRT host exit",
                cancellation,
            )
        } else {
            let _ = host.0.kill();
            host.0.wait().map(|_| ()).map_err(Into::into)
        };
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
    });
    result?;
    sdk.recheck()?;
    cancellation.bail_if_cancelled()?;
    let result = session.with_compiler_inputs(consume);
    sdk.recheck()?;
    result
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

#[cfg(test)]
mod runtime_source_tests {
    use super::*;
    use crate::jar_build::nfrt_launch_contract::NfrtInputSnapshotExport;

    fn actual_development_runtime() -> NfrtInputSnapshotExport {
        facet_json::from_str(include_str!(
            "../../tests/fixtures/neoform/development-runtime-snapshot.json"
        ))
        .unwrap()
    }

    #[test]
    fn actual_schema4_runtime_retains_exact_pin_and_source_origin() {
        let development = actual_development_runtime();
        assert_eq!(development.origin, "captured_schema4_pin");
        assert_eq!(development.bytes, 4_479_682);
        // This exact row from the failed native invocation was rejected by
        // the old unconditional schema-2 comparison despite the exact hash.
        assert_ne!(development.origin, "original_schema2_pin");
        assert_eq!(
            unique_runtime_snapshot(std::slice::from_ref(&development), "captured_schema4_pin")
                .unwrap(),
            "inputs/artifacts/0000.bin"
        );
        let mut released = development.clone();
        released.origin = "original_schema2_pin".to_owned();
        assert!(
            unique_runtime_snapshot(std::slice::from_ref(&released), "original_schema2_pin")
                .is_ok()
        );
        for (row, expected) in [
            (&development, "original_schema2_pin"),
            (&released, "captured_schema4_pin"),
            (&development, "unreviewed_source"),
        ] {
            assert!(unique_runtime_snapshot(std::slice::from_ref(row), expected).is_err());
        }
    }

    #[test]
    fn runtime_source_refuses_missing_duplicate_changed_pin_and_other_version() {
        let original = actual_development_runtime();
        assert!(unique_runtime_snapshot(&[], "captured_schema4_pin").is_err());
        assert!(
            unique_runtime_snapshot(
                &[original.clone(), original.clone()],
                "captured_schema4_pin"
            )
            .is_err()
        );
        let mut wrong_hash = original.clone();
        wrong_hash.full_sha256 = format!("sha256:{}", "0".repeat(64));
        let mut wrong_origin = original.clone();
        wrong_origin.origin = "approved_nfrt_child".to_owned();
        let mut wrong_version = original;
        wrong_version.coordinate = Some("net.neoforged:neoform-runtime:2.0.20:all".to_owned());
        for row in [wrong_hash, wrong_origin, wrong_version] {
            assert!(unique_runtime_snapshot(&[row], "captured_schema4_pin").is_err());
        }
    }
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
