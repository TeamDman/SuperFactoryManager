//! Identity of one owned live launch; never inferred from a JDWP port alone.
use super::build_snapshot_receipt::ensure_owned_directory;
use super::build_snapshot_receipt::read_bounded;
use super::json_path::JsonPath;
use crate::source_projection::candidate_lock::checked_directory;
use crate::source_projection::candidate_lock::is_reparse;
use eyre::Context;
use eyre::ensure;
use facet::Facet;
use std::fs;
use std::path::Path;
use std::path::PathBuf;

#[derive(Clone, Debug, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub(crate) struct RuntimeSessionReceipt {
    pub schema: String,
    pub session_id: String,
    #[facet(proxy = JsonPath)]
    pub minecraft_dir: PathBuf,
    #[facet(proxy = JsonPath)]
    pub session_dir: PathBuf,
    pub runtime_slot: u32,
    pub owner_id: String,
    #[facet(proxy = JsonPath)]
    pub owner_lease: PathBuf,
    #[facet(proxy = JsonPath)]
    pub snapshot_receipt: PathBuf,
    pub java_major: u32,
    pub sdk_identity: String,
    pub active_roots: Vec<String>,
    pub run_kind: String,
    pub jdwp_port: Option<u16>,
    pub active: bool,
}

impl RuntimeSessionReceipt {
    pub fn receipt_path(&self) -> PathBuf {
        self.session_dir.join("session.json")
    }

    pub fn state_dir(&self) -> PathBuf {
        self.session_dir.join("run")
    }

    pub fn mod_root(&self) -> PathBuf {
        self.session_dir.join("mod")
    }

    pub fn lease_path(&self) -> PathBuf {
        self.owner_lease.clone()
    }

    pub fn load(path: &Path, expected_minecraft_dir: &Path) -> eyre::Result<Self> {
        let receipt: Self = facet_json::from_slice(&read_bounded(path, 64 * 1024)?)?;
        receipt.check_paths(expected_minecraft_dir)?;
        // A latest-session file is only a locator. Its canonical session receipt
        // carries the current lifecycle state, including a completed launch.
        let current: Self =
            facet_json::from_slice(&read_bounded(&receipt.receipt_path(), 64 * 1024)?)?;
        current.check_paths(expected_minecraft_dir)?;
        ensure!(
            current.session_id == receipt.session_id,
            "runtime session locator changed identity"
        );
        Ok(current)
    }

    pub fn check_active(&self, expected_minecraft_dir: &Path, port: u16) -> eyre::Result<()> {
        self.check_paths(expected_minecraft_dir)?;
        ensure!(
            self.active && self.jdwp_port == Some(port),
            "selected runtime session is inactive or uses a different JDWP port; restart/select the correct client"
        );
        let current: Self =
            facet_json::from_slice(&read_bounded(&self.receipt_path(), 64 * 1024)?)?;
        ensure!(
            *self == current,
            "runtime session changed; select its current receipt"
        );
        ensure!(
            existing_owner_lease_is_locked(&self.lease_path())?,
            "runtime session has ended; restart the client before applying a build"
        );
        Ok(())
    }

    fn check_paths(&self, expected_minecraft_dir: &Path) -> eyre::Result<()> {
        ensure!(
            self.schema == "sfm:runtime-session@1",
            "unsupported runtime session receipt"
        );
        ensure!(
            safe_component(&self.session_id) && self.session_id.starts_with("session-"),
            "invalid runtime session identity"
        );
        ensure!(
            safe_component(&self.run_kind),
            "invalid runtime session kind"
        );
        ensure!(
            self.active_roots.iter().all(|root| matches!(
                root.as_str(),
                "classes"
                    | "staged-resources"
                    | "gametest/classes"
                    | "gametest/resources"
                    | "datagen/classes"
                    | "datagen/resources"
            )),
            "runtime session has unsupported class/resource roots"
        );
        let expected = dunce::canonicalize(expected_minecraft_dir)?;
        ensure!(
            dunce::canonicalize(&self.minecraft_dir)? == expected,
            "runtime session belongs to another projection"
        );
        ensure!(
            self.runtime_slot > 0 && safe_component(&self.owner_id),
            "invalid runtime slot owner"
        );
        let slot = expected
            .join("build/sfm-runtime/slots")
            .join(self.runtime_slot.to_string());
        let root = slot.join("launches");
        ensure!(
            root.is_dir()
                && self.session_dir.is_dir()
                && self.state_dir().is_dir()
                && self.mod_root().is_dir(),
            "runtime session directories are missing"
        );
        ensure_owned_directory(&expected, &root)?;
        ensure_owned_directory(&root, &self.session_dir)?;
        ensure!(
            self.session_dir == root.join(&self.session_id),
            "runtime session path differs from its identity"
        );
        ensure!(
            self.owner_lease
                == slot
                    .join(".sfm-owners")
                    .join(format!("{}.lock", self.owner_id)),
            "runtime owner lease differs from selected slot"
        );
        ensure_owned_directory(&self.session_dir, &self.state_dir())?;
        ensure_owned_directory(&self.session_dir, &self.mod_root())?;
        Ok(())
    }

    pub fn write(&self) -> eyre::Result<()> {
        fs::write(self.receipt_path(), facet_json::to_string_pretty(self)?)
            .wrap_err("failed to write owned runtime session receipt")
    }
}

fn existing_owner_lease_is_locked(path: &Path) -> eyre::Result<bool> {
    let parent = path
        .parent()
        .ok_or_else(|| eyre::eyre!("runtime owner lease has no parent"))?;
    checked_directory(parent)?;
    let metadata = fs::symlink_metadata(path)
        .wrap_err("runtime owner lease is missing; restart the client before applying a build")?;
    ensure!(
        metadata.is_file() && !is_reparse(&metadata),
        "runtime owner lease is a reparse point or non-file"
    );
    let file = open_existing_owner_lease(path)?;
    let metadata = file.metadata()?;
    ensure!(
        metadata.is_file() && !is_reparse(&metadata),
        "opened runtime owner lease is a reparse point or non-file"
    );
    match file.try_lock() {
        Ok(()) => {
            file.unlock()
                .wrap_err("cannot release runtime owner liveness probe")?;
            Ok(false)
        }
        Err(fs::TryLockError::WouldBlock) => Ok(true),
        Err(fs::TryLockError::Error(error)) => {
            Err(error).wrap_err("cannot inspect runtime owner lease lock")
        }
    }
}

fn open_existing_owner_lease(path: &Path) -> eyre::Result<fs::File> {
    // A liveness check must never recreate a compacted owner's lock. The open
    // also refuses creation if the selected file disappears after inspection.
    let mut options = fs::OpenOptions::new();
    options.read(true).write(true).create(false).truncate(false);
    #[cfg(windows)]
    {
        use std::os::windows::fs::OpenOptionsExt as _;
        options.custom_flags(0x0020_0000) // FILE_FLAG_OPEN_REPARSE_POINT
    };
    #[cfg(target_os = "linux")]
    {
        use std::os::unix::fs::OpenOptionsExt as _;
        options.custom_flags(0o400_000) // O_NOFOLLOW
    };
    options
        .open(path)
        .wrap_err("cannot open existing runtime owner lease")
}

pub(super) fn safe_component(value: &str) -> bool {
    !value.is_empty()
        && value.len() <= 128
        && value
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || matches!(byte, b'-' | b'_'))
}

#[cfg(test)]
mod tests {
    use super::RuntimeSessionReceipt;
    use super::existing_owner_lease_is_locked;
    use super::open_existing_owner_lease;
    use super::safe_component;
    use crate::jar_build::build_snapshot_receipt::BuildSnapshotReceipt;
    use crate::jar_build::runtime_session::RuntimeSession;
    use crate::jar_build::runtime_slot::RuntimeSlot;
    use crate::jdk::ResolvedJava;
    use std::fs;
    use std::path::Path;

    #[test]
    fn session_components_cannot_escape_owned_roots() {
        for value in [
            "",
            "..",
            "../client",
            "C:\\client",
            "client/other",
            "client:other",
        ] {
            assert!(!safe_component(value));
        }
        assert!(safe_component("session-a1_runClient"));
    }

    #[test]
    fn compacted_stale_session_check_preserves_slot_reuse_without_recreating_owner_records()
    -> eyre::Result<()> {
        let fixture = tempfile::tempdir()?;
        let minecraft = dunce::canonicalize(fixture.path())?;
        let session = prepare_fixture_session(&minecraft)?;
        let stale = session.receipt.clone();
        let lease = stale.lease_path();
        let metadata = lease.with_extension("json");
        drop(session);
        // Simulate retained active evidence after a crashed launcher and dead
        // registered child have already had their owner pair compacted.
        stale.write()?;
        fs::remove_file(&lease)?;
        fs::remove_file(&metadata)?;
        let canonical_before = fs::read(stale.receipt_path())?;
        let selected = RuntimeSessionReceipt::load(&stale.receipt_path(), &minecraft)?;
        assert!(selected.active);
        assert!(selected.check_active(&minecraft, 5005).is_err());
        assert!(!lease.exists());
        assert!(!metadata.exists());
        assert_eq!(fs::read(stale.receipt_path())?, canonical_before);
        let reused = RuntimeSlot::claim(&minecraft, Some(stale.runtime_slot), false)?;
        assert_eq!(reused.number, stale.runtime_slot);
        assert!(!lease.exists());
        assert!(!metadata.exists());
        Ok(())
    }

    #[test]
    fn existing_live_and_free_owner_leases_are_probed_without_changing_evidence() -> eyre::Result<()>
    {
        let fixture = tempfile::tempdir()?;
        let minecraft = dunce::canonicalize(fixture.path())?;
        let session = prepare_fixture_session(&minecraft)?;
        let stale = session.receipt.clone();
        let lease = stale.lease_path();
        let metadata = lease.with_extension("json");
        let live_owner = fs::read(&metadata)?;
        session.receipt.check_active(&minecraft, 5005)?;
        assert!(existing_owner_lease_is_locked(&lease)?);
        assert_eq!(fs::read(&metadata)?, live_owner);
        drop(session);
        stale.write()?;
        let free_owner = fs::read(&metadata)?;
        fs::write(&lease, b"retained free lease evidence")?;
        let lease_before = fs::read(&lease)?;
        assert!(!existing_owner_lease_is_locked(&lease)?);
        let error = stale.check_active(&minecraft, 5005).unwrap_err();
        assert!(error.to_string().contains("runtime session has ended"));
        assert_eq!(fs::read(&lease)?, lease_before);
        assert_eq!(fs::read(&metadata)?, free_owner);
        let available = fs::OpenOptions::new().read(true).write(true).open(&lease)?;
        assert!(
            available.try_lock().is_ok(),
            "Liveness probe retained its lock"
        );
        available.unlock()?;
        Ok(())
    }

    #[test]
    fn owner_lease_disappearing_after_observation_is_not_recreated() -> eyre::Result<()> {
        let fixture = tempfile::tempdir()?;
        let lease = fixture.path().join("selected-owner.lock");
        fs::write(&lease, b"retained lease evidence")?;
        assert!(fs::symlink_metadata(&lease)?.is_file());
        fs::remove_file(&lease)?;
        assert!(open_existing_owner_lease(&lease).is_err());
        assert!(!lease.exists());
        assert!(existing_owner_lease_is_locked(&lease).is_err());
        assert!(!lease.exists());
        assert!(fs::read_dir(fixture.path())?.next().is_none());
        Ok(())
    }

    fn prepare_fixture_session(minecraft: &Path) -> eyre::Result<RuntimeSession> {
        let cache = minecraft.join("build/sfm-toolchain/native-project/fixture");
        let project = cache.join("project");
        fs::create_dir_all(project.join("classes"))?;
        fs::write(
            project.join("classes/Main.class"),
            b"not-executed class fixture",
        )?;
        let java = ResolvedJava {
            executable: minecraft.join("jdk/bin/java"),
            home: Some(minecraft.join("jdk")),
            version_output: "not-executed fixture JDK 17".to_owned(),
            major_version: 17,
            selection: "fixture".to_owned(),
            pin_url: None,
            pin_sha512: None,
        };
        let snapshot = BuildSnapshotReceipt::publish(
            minecraft,
            &cache,
            "1.19.2",
            &java,
            &project,
            "fixture-sdk",
        )?;
        RuntimeSession::prepare(
            &snapshot,
            "runClient",
            &["classes"],
            Some(5005),
            Some(1),
            false,
        )
    }
}
