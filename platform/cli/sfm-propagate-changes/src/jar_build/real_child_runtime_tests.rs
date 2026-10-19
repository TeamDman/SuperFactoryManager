//! A real, bounded Rust child owns a slot while a synthetic build is prepared.
//! This fixture never starts Java, Minecraft, a GUI, or a live apply operation.

use super::RuntimeSlot;
use super::runtime_fixture_child::RuntimeFixtureChild;
use crate::artifact_lock::ArtifactLock;
use crate::jar_build::build_snapshot_receipt::BuildSnapshotReceipt;
use crate::jdk::ResolvedJava;
use eyre::Result;
use eyre::WrapErr as _;
use eyre::ensure;
use std::fs;
use std::io::Read as _;
use std::path::Path;
use std::process::Child;
use std::process::Command;
use std::process::Stdio;
use std::time::Duration;
use std::time::Instant;

const CHILD_ENV: &str = "SFM_PRIVATE_RUNTIME_CHILD_FIXTURE";
const SELECTOR: &str = "jar_build::runtime_slot::real_child_runtime_tests::real_child_keeps_slot_busy_during_candidate_and_then_releases_for_reuse";

#[test]
fn real_child_keeps_slot_busy_during_candidate_and_then_releases_for_reuse() -> Result<()> {
    if let Some(directory) = std::env::var_os(CHILD_ENV) {
        return child_wait(Path::new(&directory));
    }
    let temporary = tempfile::tempdir()?;
    let root = dunce::canonicalize(temporary.path())?;
    let cache = root.join("build/sfm-toolchain/native-project/fixture");
    fs::create_dir_all(cache.join("project/classes/example"))?;
    fs::write(
        cache.join("project/classes/example/Main.class"),
        b"old class",
    )?;
    let producer_path = cache.join(".locks/build-cache.lock");
    let original_producer =
        ArtifactLock::try_acquire(&producer_path, "original fixture candidate")?;
    assert!(original_producer.is_some());
    let original = publish_fixture(&root, &cache)?;
    drop(original_producer);
    let slot = RuntimeSlot::claim(&root, None, false)?;
    assert_eq!(slot.number, 1);
    fs::create_dir(slot.data_dir.join("saves"))?;
    fs::write(slot.data_dir.join("saves/world-marker"), b"retained world")?;
    fs::write(slot.data_dir.join("options.txt"), b"retained options")?;
    let live = slot.data_dir.join("launches/fixture/mod");
    original.materialize_roots(&live, &["classes"])?;
    let signals = root.join("fixture-signals");
    fs::create_dir(&signals)?;
    slot.record_spawn_pending()?;
    let mut owned_child = spawn_child(&signals)?;
    let child = owned_child.child_mut();
    let exercise = (|| -> Result<()> {
        wait_ready(child, &signals)?;
        slot.record_child(child.id())?;
        drop(slot);
        assert!(RuntimeSlot::claim(&root, Some(1), false).is_err());
        let second = RuntimeSlot::claim(&root, None, false)?;
        assert_eq!(second.number, 2);
        assert!(child.try_wait()?.is_none());
        let producer = ArtifactLock::try_acquire(&producer_path, "live-child fixture candidate")?;
        assert!(
            producer.is_some(),
            "the live child must not hold the producer lock"
        );
        fs::write(
            cache.join("project/classes/example/Main.class"),
            b"new class",
        )?;
        let candidate = publish_fixture(&root, &cache)?;
        let latest =
            BuildSnapshotReceipt::load(&cache.join("state/latest-successful-build.json"), &root)?;
        assert_eq!(latest.snapshot_dir, candidate.snapshot_dir);
        assert_ne!(candidate.snapshot_dir, original.snapshot_dir);
        assert_eq!(
            fs::read(
                candidate
                    .snapshot_dir
                    .join("project/classes/example/Main.class")
            )?,
            b"new class"
        );
        assert_eq!(fs::read(live.join("example/Main.class"))?, b"old class");
        original.recheck()?;
        assert!(child.try_wait()?.is_none());
        assert!(RuntimeSlot::claim(&root, Some(1), false).is_err());
        drop(producer);
        Ok(())
    })();
    // Always stop and reap the private child, including a failed fixture branch.
    let stopped = stop_child(child, &signals);
    exercise.wrap_err_with(|| child_diagnostics(&signals))?;
    stopped.wrap_err_with(|| child_diagnostics(&signals))?;
    let reused = RuntimeSlot::claim(&root, None, false)?;
    assert_eq!(reused.number, 1);
    assert_eq!(
        fs::read(reused.data_dir.join("options.txt"))?,
        b"retained options"
    );
    assert_eq!(
        fs::read(reused.data_dir.join("saves/world-marker"))?,
        b"retained world"
    );
    assert_eq!(fs::read(live.join("example/Main.class"))?, b"old class");
    Ok(())
}

fn publish_fixture(root: &Path, cache: &Path) -> Result<BuildSnapshotReceipt> {
    let java = ResolvedJava {
        executable: root.join("sdk/bin/java"),
        home: Some(root.join("sdk")),
        version_output: "not-executed fixture JDK 17".to_owned(),
        major_version: 17,
        selection: "fixture".to_owned(),
        pin_url: None,
        pin_sha512: None,
    };
    BuildSnapshotReceipt::publish(
        root,
        cache,
        "1.19.2",
        &java,
        &cache.join("project"),
        "fixture-sdk",
    )
}

fn spawn_child(signals: &Path) -> Result<RuntimeFixtureChild> {
    let mut command = Command::new(std::env::current_exe()?);
    command
        .args(["--exact", SELECTOR, "--nocapture"])
        .env(CHILD_ENV, signals)
        .stdin(Stdio::null())
        .stdout(Stdio::from(fs::File::create(signals.join("child.stdout"))?))
        .stderr(Stdio::from(fs::File::create(signals.join("child.stderr"))?));
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt as _;
        command.creation_flags(0x0800_0000)
    };
    RuntimeFixtureChild::spawn(&mut command)
}

fn wait_ready(child: &mut Child, signals: &Path) -> Result<()> {
    let started = Instant::now();
    while !signals.join("ready").try_exists()? {
        ensure!(
            child.try_wait()?.is_none(),
            "private child exited before ready"
        );
        ensure!(
            started.elapsed() < Duration::from_secs(10),
            "private child readiness timed out"
        );
        std::thread::sleep(Duration::from_millis(10));
    }
    ensure!(
        fs::read_to_string(signals.join("ready"))? == child.id().to_string(),
        "private child readiness PID mismatch"
    );
    Ok(())
}

fn child_wait(signals: &Path) -> Result<()> {
    let pid = std::process::id().to_string();
    write_signal(signals, "ready", &pid)?;
    let started = Instant::now();
    while !signals.join("release").try_exists()? {
        ensure!(
            started.elapsed() < Duration::from_secs(60),
            "private child self-deadline exceeded"
        );
        std::thread::sleep(Duration::from_millis(10));
    }
    ensure!(
        fs::read_to_string(signals.join("release"))? == pid,
        "private child release PID mismatch"
    );
    write_signal(signals, "released", &pid)?;
    Ok(())
}

fn stop_child(child: &mut Child, signals: &Path) -> Result<()> {
    let release = write_signal(signals, "release", &child.id().to_string());
    let started = Instant::now();
    loop {
        if let Some(status) = child.try_wait()? {
            release?;
            ensure!(status.success(), "private child returned {status}");
            ensure!(
                fs::read_to_string(signals.join("released"))? == child.id().to_string(),
                "private child release acknowledgement PID mismatch"
            );
            return Ok(());
        }
        if release.is_err() || started.elapsed() >= Duration::from_secs(10) {
            child.kill()?;
            child.wait()?;
            release.wrap_err("private child release signal could not be written")?;
            eyre::bail!("private child required bounded cleanup termination");
        }
        std::thread::sleep(Duration::from_millis(10));
    }
}

fn child_diagnostics(signals: &Path) -> String {
    ["child.stdout", "child.stderr"]
        .map(|name| {
            let mut raw = Vec::new();
            let read = fs::File::open(signals.join(name))
                .and_then(|file| file.take(64 * 1024).read_to_end(&mut raw));
            format!("{name}: {read:?}\n{}", String::from_utf8_lossy(&raw))
        })
        .join("\n")
}

fn write_signal(signals: &Path, name: &str, pid: &str) -> std::io::Result<()> {
    let staging = signals.join(format!("{name}.tmp"));
    fs::write(&staging, pid.as_bytes())?;
    fs::rename(staging, signals.join(name))
}
