//! Launch ownership is separate from the mutable compiler-cache lock.
use super::build_snapshot_receipt::BuildSnapshotReceipt;
use super::build_snapshot_receipt::ensure_owned_directory;
use super::runtime_session_receipt::RuntimeSessionReceipt;
use super::runtime_session_receipt::safe_component;
use super::runtime_slot::RuntimeSlot;
#[cfg(windows)]
use crate::source_projection::core_input_leases::DirectoryLeases;
use eyre::ensure;
#[cfg(windows)]
use std::collections::BTreeMap;
#[cfg(windows)]
use std::collections::BTreeSet;
use std::fs;
use std::io::Write as _;
use std::path::Path;
#[cfg(windows)]
use std::path::PathBuf;
use std::time::Instant;

#[cfg(windows)]
type OwnedInputFiles = BTreeMap<PathBuf, (u64, blake3::Hash)>;

pub(crate) struct RuntimeSession {
    pub receipt: RuntimeSessionReceipt,
    pub slot: RuntimeSlot,
}

impl RuntimeSession {
    /// Close admission before any prospective successful-run input retirement.
    /// If the receipt cannot be updated, keep the private inputs intact.
    #[cfg(windows)]
    pub(crate) fn close_for_retirement(&mut self) -> eyre::Result<()> {
        self.receipt.active = false;
        self.receipt.write()
    }

    pub fn prepare(
        snapshot: &BuildSnapshotReceipt,
        run_kind: &str,
        roots: &[&str],
        jdwp_port: Option<u16>,
        requested_slot: Option<u32>,
        share_runtime: bool,
    ) -> eyre::Result<Self> {
        ensure!(safe_component(run_kind), "invalid runtime run kind");
        // materialize_roots checks the managed snapshot receipt and selected
        // copy bytes before validating the new private session inventory.
        let slot_started = Instant::now();
        let slot = RuntimeSlot::claim(&snapshot.minecraft_dir, requested_slot, share_runtime)?;
        tracing::info!(phase = "warm_session_slot_claim", elapsed_ms = slot_started.elapsed().as_millis(), "warm_run_stage");
        let parent = slot.data_dir.join("launches");
        ensure_owned_directory(&slot.data_dir, &parent)?;
        let directory = tempfile::Builder::new()
            .prefix("session-")
            .tempdir_in(&parent)?;
        let session_dir = directory.path().to_path_buf();
        let receipt = RuntimeSessionReceipt {
            schema: "sfm:runtime-session@1".to_owned(),
            session_id: session_dir
                .file_name()
                .and_then(|name| name.to_str())
                .ok_or_else(|| eyre::eyre!("runtime session path is not Unicode"))?
                .to_owned(),
            minecraft_dir: dunce::canonicalize(&snapshot.minecraft_dir)?,
            session_dir: session_dir.clone(),
            runtime_slot: slot.number,
            owner_id: slot.owner_id.clone(),
            owner_lease: slot.lease_path(),
            snapshot_receipt: snapshot.receipt_path(),
            java_major: snapshot.java_major,
            sdk_identity: snapshot.sdk_identity.clone(),
            active_roots: roots.iter().map(|root| (*root).to_owned()).collect(),
            run_kind: run_kind.to_owned(),
            jdwp_port,
            active: true,
        };
        fs::create_dir(receipt.state_dir())?;
        fs::create_dir(receipt.mod_root())?;
        let materialize_started = Instant::now();
        snapshot.materialize_roots(&receipt.mod_root(), roots)?;
        tracing::info!(phase = "warm_session_materialize", elapsed_ms = materialize_started.elapsed().as_millis(), "warm_run_stage");
        receipt.write()?;
        let latest = snapshot.cache_dir.join("run").join(run_kind);
        ensure_owned_directory(&snapshot.cache_dir, &latest)?;
        write_locator(&latest.join("latest-session.json"), &receipt)?;
        if let Some(port) = jdwp_port {
            let runtime = snapshot.minecraft_dir.join("build/sfm-runtime");
            write_locator(
                &runtime.join(format!("latest-hotswap-{port}.json")),
                &receipt,
            )?;
        }
        let _retained = directory.keep();
        tracing::info!(session = %receipt.session_id, receipt = %receipt.receipt_path().display(),
            snapshot = %snapshot.receipt_path().display(), "Prepared owned runtime session");
        Ok(Self { receipt, slot })
    }
}

impl Drop for RuntimeSession {
    fn drop(&mut self) {
        self.receipt.active = false;
        if let Err(error) = self.receipt.write() {
            tracing::warn!(session = %self.receipt.session_id, %error, "failed to close runtime session receipt");
        }
        // Retain unique launch evidence and runtime files. Cleanup is explicit;
        // never remove files still held by a live JVM or another CLI process.
    }
}

/// Exact private launch bytes captured before an eligible JVM starts. Directory
/// leases keep the session tree at the same paths through the final comparison
/// and file unlink. Empty directories and all receipts/logs remain; a failed or
/// changed run retains its inputs for diagnosis. This never visits old sessions.
#[cfg(windows)]
pub(crate) struct OwnedSessionInputs {
    session_dir: PathBuf,
    files: OwnedInputFiles,
    directories: BTreeSet<PathBuf>,
    leases: DirectoryLeases,
}

#[cfg(windows)]
impl OwnedSessionInputs {
    pub(crate) fn capture(session_dir: &Path) -> eyre::Result<Self> {
        let mut owned = Self {
            session_dir: dunce::canonicalize(session_dir)?,
            files: BTreeMap::new(),
            directories: BTreeSet::new(),
            leases: DirectoryLeases::default(),
        };
        let (files, directories) = owned.inventory()?;
        owned.files = files;
        owned.directories = directories;
        Ok(owned)
    }

    /// Called only after the owned JVM has exited successfully and any required
    /// run-specific result has been validated. An inventory change retains all payloads.
    pub(crate) fn retire(mut self) -> eyre::Result<(usize, u64)> {
        let (current_files, current_directories) = self.inventory()?;
        ensure!(
            current_files == self.files && current_directories == self.directories,
            "owned session inputs changed during launch; retaining private copies"
        );
        let planned_files = self.files.len();
        let planned_bytes = self.files.values().map(|(size, _)| *size).sum::<u64>();
        self.write_retirement_marker("retiring", planned_files, planned_bytes)?;
        let mut removed = 0_usize;
        let mut bytes = 0_u64;
        for (path, (size, _)) in &self.files {
            let metadata = fs::symlink_metadata(path)?;
            ensure!(
                metadata.is_file()
                    && !crate::source_projection::candidate_lock::is_reparse(&metadata),
                "owned GameTest input became a link or non-file"
            );
            fs::remove_file(path)?;
            removed += 1;
            bytes += size;
        }
        self.write_retirement_marker("complete", removed, bytes)?;
        Ok((removed, bytes))
    }

    fn write_retirement_marker(&self, state: &str, files: usize, bytes: u64) -> eyre::Result<()> {
        let run = self.session_dir.join("run");
        super::build_snapshot_receipt::ensure_owned_directory(&self.session_dir, &run)?;
        let canonical = fs::canonicalize(&run)?;
        ensure!(
            super::build_snapshot_receipt::same_path_spelling(&run, &canonical),
            "runtime retirement marker directory spelling changed"
        );
        let mut temporary = tempfile::NamedTempFile::new_in(&canonical)?;
        writeln!(
            temporary,
            "{{\"schema\":\"sfm:retired-runtime-inputs@1\",\"state\":\"{state}\",\"replayable\":false,\"files\":{files},\"bytes\":{bytes}}}"
        )?;
        temporary
            .persist(canonical.join("immutable-input-retirement.json"))
            .map_err(|error| error.error)?;
        Ok(())
    }

    fn inventory(&mut self) -> eyre::Result<(OwnedInputFiles, BTreeSet<PathBuf>)> {
        let mut files = BTreeMap::new();
        let mut directories = BTreeSet::new();
        let mut total = 0_u64;
        for name in ["mod", "launch-inputs"] {
            let root = self.session_dir.join(name);
            if name == "launch-inputs" && !root.exists() {
                continue;
            }
            super::build_snapshot_receipt::ensure_owned_directory(&self.session_dir, &root)?;
            for entry in walkdir::WalkDir::new(&root)
                .follow_links(false)
                .max_depth(130)
            {
                let entry = entry?;
                let path = entry.path();
                super::build_snapshot_receipt::checked_descendant(&self.session_dir, path)?;
                let metadata = fs::symlink_metadata(path)?;
                ensure!(
                    !crate::source_projection::candidate_lock::is_reparse(&metadata),
                    "owned GameTest input contains a link or reparse point"
                );
                if metadata.is_dir() {
                    self.leases.prepare(path, "reserved-leaf")?;
                    directories.insert(path.to_path_buf());
                    ensure!(
                        directories.len() <= 100_000,
                        "too many owned GameTest directories"
                    );
                } else {
                    ensure!(
                        metadata.is_file(),
                        "owned GameTest input is not a regular file"
                    );
                    let mut options = fs::OpenOptions::new();
                    options.read(true);
                    #[cfg(windows)]
                    {
                        use std::os::windows::fs::OpenOptionsExt as _;
                        options.share_mode(1).custom_flags(0x0020_0000)
                    };
                    #[cfg(target_os = "linux")]
                    {
                        use std::os::unix::fs::OpenOptionsExt as _;
                        options.custom_flags(0o400_000)
                    };
                    let mut file = options.open(path)?;
                    let identity = crate::file_identity::file_identity(&file)?;
                    let mut hasher = blake3::Hasher::new();
                    let size = std::io::copy(&mut file, &mut hasher)?;
                    ensure!(
                        size == metadata.len()
                            && file.metadata()?.len() == size
                            && crate::file_identity::file_identity(&file)? == identity,
                        "owned GameTest input changed while reading"
                    );
                    total = total
                        .checked_add(size)
                        .ok_or_else(|| eyre::eyre!("owned GameTest size overflow"))?;
                    ensure!(
                        total <= 8 * 1024 * 1024 * 1024,
                        "owned GameTest input budget exceeded"
                    );
                    files.insert(path.to_path_buf(), (size, hasher.finalize()));
                    ensure!(files.len() <= 100_000, "too many owned GameTest files");
                }
            }
        }
        Ok((files, directories))
    }
}

fn write_locator(path: &Path, receipt: &RuntimeSessionReceipt) -> eyre::Result<()> {
    let parent = path
        .parent()
        .ok_or_else(|| eyre::eyre!("session locator has no parent"))?;
    #[cfg(windows)]
    let (parent, path) = {
        let canonical = fs::canonicalize(parent)?;
        ensure!(
            super::build_snapshot_receipt::same_path_spelling(parent, &canonical),
            "session locator directory spelling changed"
        );
        let name = path
            .file_name()
            .ok_or_else(|| eyre::eyre!("session locator has no filename"))?;
        let target = canonical.join(name);
        (canonical, target)
    };
    #[cfg(not(windows))]
    let path = path.to_path_buf();
    let mut temporary = tempfile::NamedTempFile::new_in(parent)?;
    temporary.write_all(facet_json::to_string_pretty(receipt)?.as_bytes())?;
    temporary.persist(&path).map_err(|error| error.error)?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::artifact_lock::ArtifactLock;
    use crate::jdk::ResolvedJava;

    #[cfg(windows)]
    #[test]
    fn successful_gametest_retires_only_new_private_immutable_files() -> eyre::Result<()> {
        let temporary = tempfile::tempdir()?;
        let session = temporary.path().join("session-new");
        fs::create_dir(&session)?;
        fs::create_dir_all(session.join("mod/pkg"))?;
        fs::create_dir_all(session.join("launch-inputs/0"))?;
        fs::create_dir_all(session.join("run"))?;
        fs::write(session.join("mod/pkg/Main.class"), b"class")?;
        fs::write(session.join("launch-inputs/0/runtime.jar"), b"jar")?;
        fs::write(session.join("run/console.log"), b"passed")?;
        fs::write(temporary.path().join("options.txt"), b"user state")?;
        fs::create_dir(temporary.path().join("world"))?;
        fs::write(temporary.path().join("world/level.dat"), b"saved world")?;
        let retained_before = [
            fs::read(temporary.path().join("options.txt"))?,
            fs::read(temporary.path().join("world/level.dat"))?,
            fs::read(session.join("run/console.log"))?,
        ];
        let owned = OwnedSessionInputs::capture(&session)?;
        let (files, bytes) = owned.retire()?;
        assert_eq!((files, bytes), (2, 8));
        assert!(session.join("mod/pkg").is_dir());
        assert!(session.join("launch-inputs/0").is_dir());
        assert_eq!(
            fs::read(session.join("run/console.log"))?,
            retained_before[2]
        );
        assert!(
            fs::read_to_string(session.join("run/immutable-input-retirement.json"))?
                .contains("\"replayable\":false")
        );
        assert_eq!(
            fs::read(temporary.path().join("options.txt"))?,
            retained_before[0]
        );
        assert_eq!(
            fs::read(temporary.path().join("world/level.dat"))?,
            retained_before[1]
        );
        Ok(())
    }

    #[cfg(windows)]
    #[test]
    fn successful_client_retirement_leaves_live_and_failed_neighbors() -> eyre::Result<()> {
        let temporary = tempfile::tempdir()?;
        let completed = temporary.path().join("session-completed");
        let live = temporary.path().join("session-live");
        let failed = temporary.path().join("session-failed");
        for session in [&completed, &live, &failed] {
            fs::create_dir(session)?;
            fs::create_dir(session.join("mod"))?;
            fs::create_dir(session.join("launch-inputs"))?;
            fs::create_dir(session.join("run"))?;
            fs::write(session.join("mod/Main.class"), b"owned class")?;
            fs::write(session.join("launch-inputs/runtime.jar"), b"owned jar")?;
        }
        fs::write(completed.join("run/console.log"), b"normal exit")?;
        fs::write(live.join("run/console.log"), b"still running")?;
        fs::write(failed.join("run/console.log"), b"failure diagnostics")?;
        let completed_inputs = OwnedSessionInputs::capture(&completed)?;
        let live_inputs = OwnedSessionInputs::capture(&live)?;
        let failed_inputs = OwnedSessionInputs::capture(&failed)?;
        assert_eq!(completed_inputs.retire()?, (2, 20));
        assert!(!completed.join("mod/Main.class").exists());
        assert!(!completed.join("launch-inputs/runtime.jar").exists());
        assert_eq!(fs::read(completed.join("run/console.log"))?, b"normal exit");
        assert_eq!(fs::read(live.join("mod/Main.class"))?, b"owned class");
        assert_eq!(fs::read(live.join("launch-inputs/runtime.jar"))?, b"owned jar");
        assert_eq!(fs::read(live.join("run/console.log"))?, b"still running");
        assert_eq!(fs::read(failed.join("mod/Main.class"))?, b"owned class");
        assert_eq!(fs::read(failed.join("launch-inputs/runtime.jar"))?, b"owned jar");
        assert_eq!(fs::read(failed.join("run/console.log"))?, b"failure diagnostics");
        assert!(!live.join("run/immutable-input-retirement.json").exists());
        assert!(!failed.join("run/immutable-input-retirement.json").exists());
        drop(live_inputs);
        drop(failed_inputs);
        Ok(())
    }

    #[cfg(windows)]
    #[test]
    fn changed_or_failed_gametest_retains_private_inputs() -> eyre::Result<()> {
        let temporary = tempfile::tempdir()?;
        let session = temporary.path().join("session-new");
        fs::create_dir(&session)?;
        fs::create_dir(session.join("mod"))?;
        fs::create_dir(session.join("launch-inputs"))?;
        fs::create_dir(session.join("run"))?;
        fs::create_dir(temporary.path().join("world"))?;
        fs::write(temporary.path().join("options.txt"), b"user options")?;
        fs::write(temporary.path().join("world/level.dat"), b"saved world")?;
        fs::write(session.join("run/console.log"), b"failed run evidence")?;
        let file = session.join("launch-inputs/input.jar");
        fs::write(&file, b"original")?;
        let failed = OwnedSessionInputs::capture(&session)?;
        drop(failed); // A failed/cancelled launch never calls retire.
        assert_eq!(fs::read(&file)?, b"original");
        let changed = OwnedSessionInputs::capture(&session)?;
        fs::write(&file, b"changed")?;
        assert!(changed.retire().is_err());
        assert_eq!(fs::read(&file)?, b"changed");
        assert_eq!(
            fs::read(temporary.path().join("options.txt"))?,
            b"user options"
        );
        assert_eq!(
            fs::read(temporary.path().join("world/level.dat"))?,
            b"saved world"
        );
        assert_eq!(
            fs::read(session.join("run/console.log"))?,
            b"failed run evidence"
        );
        Ok(())
    }

    #[cfg(windows)]
    #[test]
    fn tracked_gametest_root_cannot_be_replaced_before_retirement() -> eyre::Result<()> {
        let temporary = tempfile::tempdir()?;
        let session = temporary.path().join("session-new");
        fs::create_dir(&session)?;
        fs::create_dir(session.join("mod"))?;
        fs::create_dir(session.join("launch-inputs"))?;
        let input = session.join("launch-inputs/input.jar");
        fs::write(&input, b"owned")?;
        let owned = OwnedSessionInputs::capture(&session)?;
        assert!(
            fs::rename(
                session.join("launch-inputs"),
                session.join("replacement-target")
            )
            .is_err(),
            "a live inventory must block namespace replacement"
        );
        fs::write(session.join("mod/unknown.class"), b"new input")?;
        assert!(owned.retire().is_err());
        assert_eq!(fs::read(input)?, b"owned");
        Ok(())
    }

    #[cfg(windows)]
    #[test]
    fn long_windows_retirement_marker_is_written_after_input_unlink() -> eyre::Result<()> {
        let temporary = tempfile::tempdir()?;
        let padding = 230usize
            .checked_sub(temporary.path().as_os_str().len() + 1)
            .expect("fixture root must fit below the Windows legacy limit");
        let parent = temporary.path().join("x".repeat(padding));
        fs::create_dir(&parent)?;
        let session = parent.join("session-new");
        fs::create_dir(&session)?;
        fs::create_dir(session.join("mod"))?;
        fs::create_dir(session.join("run"))?;
        let input = session.join("mod/one.class");
        fs::write(&input, b"class")?;
        let marker = session.join("run/immutable-input-retirement.json");
        assert!(marker.as_os_str().len() > 260);
        let owned = OwnedSessionInputs::capture(&session)?;
        assert_eq!(owned.retire()?, (1, 5));
        assert!(!input.exists());
        assert!(
            fs::read_to_string(
                fs::canonicalize(session.join("run"))?.join("immutable-input-retirement.json")
            )?
            .contains("\"state\":\"complete\"")
        );
        Ok(())
    }

    #[cfg(windows)]
    #[test]
    fn long_windows_latest_session_locator_is_replaced_atomically() -> eyre::Result<()> {
        let temporary = tempfile::tempdir()?;
        let padding = 245usize
            .checked_sub(temporary.path().as_os_str().len() + 1)
            .expect("fixture root must fit below the Windows legacy limit");
        let parent = temporary.path().join("x".repeat(padding));
        fs::create_dir(&parent)?;
        let path = parent.join("latest-session.json");
        assert!(path.as_os_str().len() > 260);
        let mut receipt = RuntimeSessionReceipt {
            schema: "sfm:runtime-session@1".to_owned(),
            session_id: "session-fixture".to_owned(),
            minecraft_dir: temporary.path().to_path_buf(),
            session_dir: temporary.path().join("session-fixture"),
            runtime_slot: 1,
            owner_id: "fixture".to_owned(),
            owner_lease: temporary.path().join("owner.lease"),
            snapshot_receipt: temporary.path().join("snapshot.json"),
            java_major: 21,
            sdk_identity: "fixture".to_owned(),
            active_roots: vec!["classes".to_owned()],
            run_kind: "runGameTestServer".to_owned(),
            jdwp_port: None,
            active: true,
        };
        write_locator(&path, &receipt)?;
        let readable = fs::canonicalize(&parent)?.join("latest-session.json");
        assert_eq!(
            fs::read(&readable)?,
            facet_json::to_string_pretty(&receipt)?.as_bytes()
        );
        receipt.active = false;
        write_locator(&path, &receipt)?;
        assert_eq!(
            fs::read(&readable)?,
            facet_json::to_string_pretty(&receipt)?.as_bytes()
        );
        Ok(())
    }

    #[test]
    fn corrupt_snapshot_never_publishes_a_runtime_session() -> eyre::Result<()> {
        let temporary = tempfile::tempdir()?;
        let minecraft = dunce::canonicalize(temporary.path())?;
        let cache = minecraft.join("build/sfm-toolchain/native-project/fixture");
        let project = cache.join("project");
        fs::create_dir_all(project.join("classes"))?;
        fs::write(project.join("classes/Main.class"), b"good class")?;
        let java = ResolvedJava {
            executable: minecraft.join("jdk/bin/java"),
            home: Some(minecraft.join("jdk")),
            version_output: "openjdk version 17 fixture".to_owned(),
            major_version: 17,
            selection: "fixture".to_owned(),
            pin_url: None,
            pin_sha512: None,
        };
        let snapshot = BuildSnapshotReceipt::publish(&minecraft, &cache, "1.19.2", &java, &project, "fixture-sdk")?;
        fs::write(snapshot.snapshot_dir.join("project/classes/Main.class"), b"corrupt")?;
        assert!(RuntimeSession::prepare(&snapshot, "runClientSmoke", &["classes"], None, None, false).is_err());
        assert!(!cache.join("run/runClientSmoke/latest-session.json").exists());
        Ok(())
    }

    #[test]
    fn live_session_keeps_its_code_and_game_data_while_another_candidate_is_built()
    -> eyre::Result<()> {
        let temporary = tempfile::tempdir()?;
        let minecraft = dunce::canonicalize(temporary.path())?;
        let cache = minecraft.join("build/sfm-toolchain/native-project/fixture");
        let project = cache.join("project");
        fs::create_dir_all(project.join("classes"))?;
        fs::write(
            project.join("classes/Main.class"),
            b"first successful class",
        )?;
        let java = ResolvedJava {
            executable: minecraft.join("jdk/bin/java"),
            home: Some(minecraft.join("jdk")),
            version_output: "openjdk version 17 fixture".to_owned(),
            major_version: 17,
            selection: "fixture".to_owned(),
            pin_url: None,
            pin_sha512: None,
        };
        let build_lock = cache.join(".locks/build-cache.lock");
        let producer = ArtifactLock::acquire(&build_lock, "fixture compile")?;
        let first = BuildSnapshotReceipt::publish(
            &minecraft,
            &cache,
            "1.19.2",
            &java,
            &project,
            "fixture-sdk",
        )?;
        let mut live = RuntimeSession::prepare(
            &first,
            "runGameTestServer",
            &["classes"],
            Some(5005),
            None,
            false,
        )?;
        fs::write(live.slot.data_dir.join("options.txt"), b"retained options")?;
        fs::create_dir(live.slot.data_dir.join("world"))?;
        fs::write(
            live.slot.data_dir.join("world/session.lock"),
            b"native world lock marker",
        )?;
        fs::create_dir_all(live.receipt.session_dir.join("run"))?;
        fs::write(
            live.receipt.session_dir.join("run/console.log"),
            b"test passed",
        )?;
        let retained_before = [
            fs::read(live.slot.data_dir.join("options.txt"))?,
            fs::read(live.slot.data_dir.join("world/session.lock"))?,
            fs::read(live.receipt.session_dir.join("run/console.log"))?,
        ];
        drop(producer);
        let _next_producer = ArtifactLock::try_acquire(&build_lock, "second fixture compile")?
            .ok_or_else(|| eyre::eyre!("live session incorrectly retains compiler lock"))?;
        fs::write(
            project.join("classes/Main.class"),
            b"second successful class",
        )?;
        let second = BuildSnapshotReceipt::publish(
            &minecraft,
            &cache,
            "1.19.2",
            &java,
            &project,
            "fixture-sdk",
        )?;
        assert_ne!(first.snapshot_dir, second.snapshot_dir);
        assert_eq!(
            fs::read(live.receipt.mod_root().join("Main.class"))?,
            b"first successful class"
        );
        assert_eq!(
            fs::read(live.slot.data_dir.join("options.txt"))?,
            b"retained options"
        );
        assert_eq!(
            fs::read(live.slot.data_dir.join("world/session.lock"))?,
            b"native world lock marker"
        );
        live.receipt.check_active(&minecraft, 5005)?;
        assert!(live.receipt.check_active(&minecraft, 5006).is_err());
        #[cfg(windows)]
        {
            let owned = OwnedSessionInputs::capture(&live.receipt.session_dir)?;
            live.close_for_retirement()?;
            assert!(live.receipt.check_active(&minecraft, 5005).is_err());
            assert_eq!(owned.retire()?, (1, b"first successful class".len() as u64));
            assert!(!live.receipt.mod_root().join("Main.class").exists());
        }
        assert_eq!(
            fs::read(live.slot.data_dir.join("options.txt"))?,
            retained_before[0]
        );
        assert_eq!(
            fs::read(live.slot.data_dir.join("world/session.lock"))?,
            retained_before[1]
        );
        assert_eq!(
            fs::read(live.receipt.session_dir.join("run/console.log"))?,
            retained_before[2]
        );
        let receipt_path = live.receipt.receipt_path();
        drop(live);
        let ended = RuntimeSessionReceipt::load(&receipt_path, &minecraft)?;
        assert!(!ended.active);
        assert!(ended.check_active(&minecraft, 5005).is_err());
        assert!(ended.session_dir.is_dir());
        Ok(())
    }

    /// Manual bounded JVM witness. The source and both runtime slots live only
    /// in this fixture; no projection source or saved Minecraft world is used.
    #[cfg(windows)]
    #[test]
    #[ignore = "requires SFM_LIVE_JAVA_HOME pointing at an installed JBR 17 SDK"]
    fn changed_class_bytes_are_observed_by_two_live_session_classpaths() -> eyre::Result<()> {
        let home = std::env::var_os("SFM_LIVE_JAVA_HOME")
            .ok_or_else(|| eyre::eyre!("set SFM_LIVE_JAVA_HOME for the owned JVM fixture"))?;
        let home = fs::canonicalize(PathBuf::from(home))?;
        let java_executable = home.join("bin/java.exe");
        let javac = home.join("bin/javac.exe");
        ensure!(java_executable.is_file() && javac.is_file(), "fixture SDK is incomplete");
        let temporary = tempfile::tempdir()?;
        let minecraft = dunce::canonicalize(temporary.path())?;
        let cache = minecraft.join("build/sfm-toolchain/native-project/fixture");
        let project = cache.join("project");
        let classes = project.join("classes");
        fs::create_dir_all(&classes)?;
        let source = minecraft.join("Main.java");
        let java = ResolvedJava {
            executable: java_executable.clone(),
            home: Some(home),
            version_output: "installed fixture JBR 17".to_owned(),
            major_version: 17,
            selection: "owned live fixture".to_owned(),
            pin_url: None,
            pin_sha512: None,
        };
        let compile = |marker: &str| -> eyre::Result<()> {
            fs::write(
                &source,
                format!(
                    "public class Main {{ public static void main(String[] args) {{ System.out.print(\"{marker}\"); }} }}"
                ),
            )?;
            let output = std::process::Command::new(&javac)
                .arg("-d")
                .arg(&classes)
                .arg(&source)
                .output()?;
            ensure!(output.status.success(), "owned javac failed: {}", String::from_utf8_lossy(&output.stderr));
            Ok(())
        };
        let observe = |session: &RuntimeSession| -> eyre::Result<String> {
            let output = std::process::Command::new(&java_executable)
                .arg("-cp")
                .arg(session.receipt.mod_root())
                .arg("Main")
                .output()?;
            ensure!(output.status.success(), "owned JVM failed: {}", String::from_utf8_lossy(&output.stderr));
            Ok(String::from_utf8(output.stdout)?)
        };
        let build_lock = cache.join(".locks/build-cache.lock");
        let producer = ArtifactLock::acquire(&build_lock, "first owned fixture compile")?;
        compile("first-class")?;
        let first_hash = blake3::hash(&fs::read(classes.join("Main.class"))?);
        let first = BuildSnapshotReceipt::publish(
            &minecraft, &cache, "1.19.2", &java, &project, "fixture-sdk",
        )?;
        let first_live = RuntimeSession::prepare(&first, "runGameTestServer", &["classes"], None, Some(1), false)?;
        fs::write(first_live.slot.data_dir.join("options.txt"), b"owned options")?;
        fs::create_dir(first_live.slot.data_dir.join("world"))?;
        fs::write(first_live.slot.data_dir.join("world/level.dat"), b"owned world")?;
        assert_eq!(observe(&first_live)?, "first-class");
        drop(producer);
        let _next_producer = ArtifactLock::try_acquire(&build_lock, "second owned fixture compile")?
            .ok_or_else(|| eyre::eyre!("first runtime session retained the compile lock"))?;
        compile("second-class")?;
        let second_hash = blake3::hash(&fs::read(classes.join("Main.class"))?);
        assert_ne!(first_hash, second_hash);
        let second = BuildSnapshotReceipt::publish(
            &minecraft, &cache, "1.19.2", &java, &project, "fixture-sdk",
        )?;
        assert_ne!(first.snapshot_dir, second.snapshot_dir);
        let second_live = RuntimeSession::prepare(&second, "runGameTestServer", &["classes"], None, Some(2), false)?;
        assert_eq!(observe(&first_live)?, "first-class");
        assert_eq!(observe(&second_live)?, "second-class");
        assert_eq!(fs::read(first_live.slot.data_dir.join("options.txt"))?, b"owned options");
        assert_eq!(fs::read(first_live.slot.data_dir.join("world/level.dat"))?, b"owned world");
        println!("live changed-class witness: first={first_hash} second={second_hash}; first session retained while second JVM observed its new class");
        Ok(())
    }
}
