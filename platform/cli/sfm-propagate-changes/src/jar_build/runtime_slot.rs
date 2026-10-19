//! Persistent per-projection game directories with independently leased owners.
//! Sharing is explicit; native Minecraft world/session locks remain untouched.

use super::build_snapshot_receipt::ensure_owned_directory;
use super::build_snapshot_receipt::read_bounded;
use super::runtime_owner_identity::RuntimeOwnerIdentity;
use super::runtime_slot_owner::RuntimeSlotOwner;
use crate::source_projection::candidate_lock::checked_directory;
use crate::source_projection::candidate_lock::is_reparse;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use std::collections::BTreeSet;
use std::fs;
use std::fs::File;
use std::fs::OpenOptions;
use std::io::Write as _;
use std::path::Path;
use std::path::PathBuf;
use std::time::Duration;
use std::time::Instant;

const MAX_DEFAULT_SLOTS: u32 = 4096;
const MAX_OWNER_ENTRIES: usize = 8192;
// Allow compaction of pre-repair dead history beyond the retained-owner bound.
// Every inspection remains bounded; ordinary reuse now removes proved-dead pairs.
const MAX_OWNER_HISTORY_ENTRIES: usize = MAX_OWNER_ENTRIES * 8;
const MAX_OWNER_BYTES: u64 = 64 * 1024;
const ALLOCATION_WAIT: Duration = Duration::from_secs(5);
const OWNERS_DIRECTORY: &str = ".sfm-owners";

#[derive(Debug)]
pub(crate) struct RuntimeSlot {
    pub number: u32,
    pub data_dir: PathBuf,
    pub owner_id: String,
    minecraft_dir: PathBuf,
    launcher: RuntimeOwnerIdentity,
    lease: File,
}

impl RuntimeSlot {
    /// Atomically select a persistent directory and add one unique owner lease.
    ///
    /// # Errors
    /// Rejects unsafe paths, invalid sharing, occupied explicit slots, unverifiable
    /// owner records, failed process inspection, and allocation lock timeouts.
    pub(crate) fn claim(minecraft_dir: &Path, requested: Option<u32>, share: bool) -> Result<Self> {
        ensure!(
            requested != Some(0),
            "runtime slot must be a positive number"
        );
        ensure!(
            !share || requested.is_some(),
            "--share-runtime requires an explicit --runtime-slot N"
        );
        // The shared snapshot helpers use dunce's canonical Windows spelling.
        let minecraft_dir = dunce::canonicalize(checked_directory(minecraft_dir)?)?;
        let slots = minecraft_dir.join("build/sfm-runtime/slots");
        ensure_owned_directory(&minecraft_dir, &slots)?;
        let _allocation = allocation_lock(&minecraft_dir)?;
        let launcher = RuntimeOwnerIdentity::current()?;
        let range = requested.map_or(1..=MAX_DEFAULT_SLOTS, |number| number..=number);
        for number in range {
            let data_dir = slots.join(number.to_string());
            ensure_owned_directory(&minecraft_dir, &data_dir)?;
            let owners = data_dir.join(OWNERS_DIRECTORY);
            ensure_owned_directory(&minecraft_dir, &owners)?;
            let busy = has_live_owners(&minecraft_dir, &data_dir, number)?;
            if busy && !share {
                ensure!(
                    requested.is_none(),
                    "runtime slot {number} is occupied; select another slot or explicitly use \
                     --runtime-slot {number} --share-runtime (options/log/config contention remains)"
                );
                continue;
            }
            if share {
                tracing::warn!(
                    runtime_slot = number,
                    game_dir = %data_dir.display(),
                    "Explicitly sharing runtime data: options, logs and config can contend; \
                     Minecraft session/world locks remain authoritative and are not bypassed"
                );
            }
            return Self::create_owner(minecraft_dir, data_dir, number, launcher);
        }
        eyre::bail!(
            "no free runtime slot among 1..={MAX_DEFAULT_SLOTS}; select an explicit positive --runtime-slot N"
        )
    }

    fn create_owner(
        minecraft_dir: PathBuf,
        data_dir: PathBuf,
        number: u32,
        launcher: RuntimeOwnerIdentity,
    ) -> Result<Self> {
        let owner_id = format!("owner-{}-{:032x}", launcher.pid, rand::random::<u128>());
        let lease_path = data_dir
            .join(OWNERS_DIRECTORY)
            .join(format!("{owner_id}.lock"));
        let lease = open_lock(&minecraft_dir, &lease_path, true)?;
        ensure!(
            try_lock(&lease)?,
            "new runtime owner lease is unexpectedly occupied"
        );
        let owner = RuntimeSlotOwner {
            schema: RuntimeSlotOwner::SCHEMA.to_owned(),
            owner_id: owner_id.clone(),
            number,
            launcher: launcher.clone(),
            child: None,
            active: true,
            spawn_pending: false,
        };
        write_owner(&minecraft_dir, &data_dir, &owner)?;
        Ok(Self {
            number,
            data_dir,
            owner_id,
            minecraft_dir,
            launcher,
            lease,
        })
    }

    /// Persist uncertainty before a child can start and survive this launcher.
    ///
    /// # Errors
    /// Rejects changed ownership, an unresolved spawn, or a prior live child.
    pub(crate) fn record_spawn_pending(&self) -> Result<()> {
        let _allocation = allocation_lock(&self.minecraft_dir)?;
        let mut owner = read_owner(&self.data_dir, &self.owner_id, self.number)?;
        ensure!(
            owner.active && owner.launcher == self.launcher && !owner.spawn_pending,
            "runtime slot cannot begin spawn with changed or unresolved ownership"
        );
        if let Some(prior) = &owner.child {
            ensure!(
                RuntimeOwnerIdentity::capture(prior.pid)?.as_ref() != Some(prior),
                "runtime slot still owns a live child before the next spawn"
            );
        }
        owner.child = None;
        owner.spawn_pending = true;
        write_owner(&self.minecraft_dir, &self.data_dir, &owner)
    }

    /// Clear uncertainty only when spawn failed without creating any child.
    ///
    /// # Errors
    /// Rejects changed ownership, a registered child, or no pending spawn.
    pub(crate) fn clear_spawn_pending(&self) -> Result<()> {
        let _allocation = allocation_lock(&self.minecraft_dir)?;
        let mut owner = read_owner(&self.data_dir, &self.owner_id, self.number)?;
        ensure!(
            owner.active
                && owner.launcher == self.launcher
                && owner.spawn_pending
                && owner.child.is_none(),
            "runtime slot cannot clear unresolved spawn with changed ownership"
        );
        owner.spawn_pending = false;
        write_owner(&self.minecraft_dir, &self.data_dir, &owner)
    }

    /// Record the exact child process immediately after spawning it.
    ///
    /// # Errors
    /// Rejects a missing/unverifiable child, changed owner metadata, or replacement
    /// of an already recorded child with a different process identity.
    pub(crate) fn record_child(&self, pid: u32) -> Result<()> {
        let child = RuntimeOwnerIdentity::capture(pid)?
            .ok_or_else(|| eyre::eyre!("runtime child PID {pid} is no longer live"))?;
        let _allocation = allocation_lock(&self.minecraft_dir)?;
        ensure_owned_directory(&self.minecraft_dir, &self.data_dir)?;
        let mut owner = read_owner(&self.data_dir, &self.owner_id, self.number)?;
        ensure!(
            owner.active && owner.launcher == self.launcher && owner.spawn_pending,
            "runtime slot owner identity changed before child registration"
        );
        if let Some(prior) = &owner.child {
            ensure!(
                prior == &child
                    || RuntimeOwnerIdentity::capture(prior.pid)?.as_ref() != Some(prior),
                "runtime slot owner still records a different live child process"
            );
        }
        owner.child = Some(child);
        owner.spawn_pending = false;
        write_owner(&self.minecraft_dir, &self.data_dir, &owner)
    }

    #[must_use]
    pub(crate) fn lease_path(&self) -> PathBuf {
        self.data_dir
            .join(OWNERS_DIRECTORY)
            .join(format!("{}.lock", self.owner_id))
    }

    fn mark_inactive(&self) -> Result<()> {
        let _allocation = allocation_lock(&self.minecraft_dir)?;
        ensure_owned_directory(&self.minecraft_dir, &self.data_dir)?;
        let mut owner = read_owner(&self.data_dir, &self.owner_id, self.number)?;
        ensure!(
            owner.launcher == self.launcher,
            "runtime slot owner identity changed during release"
        );
        owner.active = false;
        write_owner(&self.minecraft_dir, &self.data_dir, &owner)
    }
}

impl Drop for RuntimeSlot {
    fn drop(&mut self) {
        if let Err(error) = self.mark_inactive() {
            tracing::warn!(owner_id = %self.owner_id, error = %error, "Cannot mark runtime owner inactive");
        }
        if let Err(error) = self.lease.unlock() {
            tracing::warn!(owner_id = %self.owner_id, error = %error, "Cannot release runtime owner lease");
        }
        // The persistent slot, options, worlds and native session.lock remain intact.
    }
}

fn allocation_lock(minecraft_dir: &Path) -> Result<File> {
    let root = minecraft_dir.join("build/sfm-runtime");
    ensure_owned_directory(minecraft_dir, &root)?;
    let path = root.join(".allocation.lock");
    let file = open_lock(minecraft_dir, &path, false)?;
    let started = Instant::now();
    while !try_lock(&file)? {
        ensure!(
            started.elapsed() < ALLOCATION_WAIT,
            "runtime slot allocation lock timed out: {}",
            path.display()
        );
        std::thread::sleep(Duration::from_millis(10));
    }
    Ok(file)
}

fn try_lock(file: &File) -> Result<bool> {
    match file.try_lock() {
        Ok(()) => Ok(true),
        Err(fs::TryLockError::WouldBlock) => Ok(false),
        Err(fs::TryLockError::Error(error)) => {
            Err(error).wrap_err("cannot lock runtime owner file")
        }
    }
}

fn open_lock(owner: &Path, path: &Path, create_new: bool) -> Result<File> {
    ensure_owned_directory(
        owner,
        path.parent()
            .ok_or_else(|| eyre::eyre!("lock has no parent"))?,
    )?;
    if let Ok(metadata) = fs::symlink_metadata(path) {
        ensure!(
            metadata.is_file() && !is_reparse(&metadata),
            "runtime lock is a reparse point or non-file"
        );
    }
    let mut options = OpenOptions::new();
    options
        .read(true)
        .write(true)
        .create(!create_new)
        .create_new(create_new)
        .truncate(false);
    #[cfg(windows)]
    {
        use std::os::windows::fs::OpenOptionsExt as _;
        options.custom_flags(0x0020_0000) // FILE_FLAG_OPEN_REPARSE_POINT
    };
    #[cfg(target_os = "linux")]
    {
        use std::os::unix::fs::OpenOptionsExt as _;
        options.custom_flags(0o400_000).mode(0o600) // O_NOFOLLOW
    };
    let file = options
        .open(path)
        .wrap_err_with(|| format!("cannot open runtime lock {}", path.display()))?;
    let metadata = file.metadata()?;
    ensure!(
        metadata.is_file() && !is_reparse(&metadata),
        "opened runtime lock is a reparse point or non-file"
    );
    Ok(file)
}

// All callers hold the projection allocation lock. Only proved-dead pairs are
// removed; pending, unverifiable and currently leased owners remain authoritative.
fn has_live_owners(minecraft_dir: &Path, data_dir: &Path, number: u32) -> Result<bool> {
    compact_owner_history(minecraft_dir, data_dir, number, MAX_OWNER_ENTRIES)
}

fn compact_owner_history(
    minecraft_dir: &Path,
    data_dir: &Path,
    number: u32,
    retained_limit: usize,
) -> Result<bool> {
    let directory = data_dir.join(OWNERS_DIRECTORY);
    let owners = collect_owner_ids(&directory)?;
    let mut busy = false;
    let mut retained = 0;
    for owner_id in owners {
        let lock_path = directory.join(format!("{owner_id}.lock"));
        let lease = open_lock(minecraft_dir, &lock_path, false)?;
        let metadata_path = directory.join(format!("{owner_id}.json"));
        let locked = try_lock(&lease)?;
        if !locked || !metadata_path.try_exists()? {
            // A free lease without committed metadata may have an unrecorded
            // surviving child. Never infer that such a slot is reusable.
            busy = true;
            retained += 2;
            continue;
        }
        if read_owner(data_dir, &owner_id, number)?.has_live_process()? {
            busy = true;
            retained += 2;
            continue;
        }
        drop(lease);
        // Delete the free lock first: interruption then leaves a committed dead
        // metadata record that the next allocation can safely recheck. Deleting
        // metadata first would leave an unverifiable orphaned lock forever.
        fs::remove_file(&lock_path)?;
        fs::remove_file(&metadata_path)?;
    }
    ensure!(
        retained <= retained_limit,
        "runtime slot live/unresolved owner records exceed inspection bound"
    );
    Ok(busy)
}

fn collect_owner_ids(directory: &Path) -> Result<BTreeSet<String>> {
    let mut owners = BTreeSet::new();
    for (index, entry) in fs::read_dir(directory)?.enumerate() {
        ensure!(
            index < MAX_OWNER_HISTORY_ENTRIES,
            "runtime slot owner history exceeds bounded compaction limit"
        );
        let entry = entry?;
        let metadata = fs::symlink_metadata(entry.path())?;
        ensure!(
            metadata.is_file() && !is_reparse(&metadata),
            "runtime owner entry is a reparse point or non-file"
        );
        let name = entry.file_name();
        let name = name
            .to_str()
            .ok_or_else(|| eyre::eyre!("runtime owner filename is not UTF-8"))?;
        // Under allocation ownership, these can only be interrupted, uncommitted
        // atomic metadata writes. They have never represented a runtime owner.
        if name.starts_with(".tmp") {
            fs::remove_file(entry.path())?;
            continue;
        }
        let owner_id = name
            .strip_suffix(".lock")
            .or_else(|| name.strip_suffix(".json"))
            .ok_or_else(|| eyre::eyre!("unexpected runtime owner entry: {name}"))?;
        ensure!(valid_owner_id(owner_id), "invalid runtime owner token");
        owners.insert(owner_id.to_owned());
    }
    Ok(owners)
}

fn valid_owner_id(owner_id: &str) -> bool {
    owner_id.starts_with("owner-")
        && owner_id.len() <= 96
        && owner_id
            .bytes()
            .all(|byte| byte.is_ascii_lowercase() || byte.is_ascii_digit() || byte == b'-')
}

fn read_owner(data_dir: &Path, owner_id: &str, number: u32) -> Result<RuntimeSlotOwner> {
    ensure!(
        valid_owner_id(owner_id) && number > 0,
        "invalid runtime slot owner path"
    );
    let path = data_dir
        .join(OWNERS_DIRECTORY)
        .join(format!("{owner_id}.json"));
    let mut owner: RuntimeSlotOwner =
        facet_json::from_slice(&read_bounded(&path, MAX_OWNER_BYTES)?)?;
    if owner.schema == RuntimeSlotOwner::LEGACY_SCHEMA {
        // The old format did not persist the spawn crash boundary. A stale
        // active launcher with no registered child is therefore unverifiable.
        owner.spawn_pending |= owner.active && owner.child.is_none();
        RuntimeSlotOwner::SCHEMA.clone_into(&mut owner.schema);
    }
    ensure!(
        owner.schema == RuntimeSlotOwner::SCHEMA
            && owner.owner_id == owner_id
            && owner.number == number,
        "runtime slot owner metadata does not match its path"
    );
    ensure!(
        owner.launcher.pid > 0
            && !owner.launcher.creation_identity.is_empty()
            && owner.launcher.executable_path.is_absolute()
            && owner.child.as_ref().is_none_or(|child| child.pid > 0
                && !child.creation_identity.is_empty()
                && child.executable_path.is_absolute()),
        "runtime slot owner process identity is incomplete"
    );
    Ok(owner)
}

fn write_owner(minecraft_dir: &Path, data_dir: &Path, owner: &RuntimeSlotOwner) -> Result<()> {
    ensure!(
        valid_owner_id(&owner.owner_id) && owner.number > 0,
        "invalid runtime slot owner path"
    );
    let directory = data_dir.join(OWNERS_DIRECTORY);
    ensure_owned_directory(minecraft_dir, &directory)?;
    let path = directory.join(format!("{}.json", owner.owner_id));
    if let Ok(metadata) = fs::symlink_metadata(&path) {
        ensure!(
            metadata.is_file() && !is_reparse(&metadata),
            "runtime owner metadata is a reparse point or non-file"
        );
    }
    let bytes = facet_json::to_string_pretty(owner)?;
    ensure!(
        u64::try_from(bytes.len())? <= MAX_OWNER_BYTES,
        "runtime owner metadata exceeds inspection bound"
    );
    let mut staging = tempfile::NamedTempFile::new_in(&directory)?;
    staging.write_all(bytes.as_bytes())?;
    staging.as_file().sync_all()?;
    staging
        .persist(&path)
        .map_err(|error| eyre::eyre!("cannot publish runtime owner metadata: {error}"))?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::Arc;
    use std::sync::Barrier;

    #[test]
    fn default_slots_are_distinct_and_reuse_retains_world_and_options() -> Result<()> {
        let project = tempfile::tempdir()?;
        let first = RuntimeSlot::claim(project.path(), None, false)?;
        assert_eq!(first.number, 1);
        fs::create_dir(first.data_dir.join("saves"))?;
        fs::write(first.data_dir.join("saves/world-marker"), b"retained world")?;
        fs::write(first.data_dir.join("options.txt"), b"retained options")?;
        let second = RuntimeSlot::claim(project.path(), None, false)?;
        assert_eq!(second.number, 2);
        drop(first);
        let reused = RuntimeSlot::claim(project.path(), None, false)?;
        assert_eq!(reused.number, 1);
        assert_eq!(
            fs::read(reused.data_dir.join("saves/world-marker"))?,
            b"retained world"
        );
        assert_eq!(
            fs::read(reused.data_dir.join("options.txt"))?,
            b"retained options"
        );
        Ok(())
    }

    #[test]
    fn concurrent_claims_get_different_slots() -> Result<()> {
        let project = tempfile::tempdir()?;
        let barrier = Arc::new(Barrier::new(3));
        let handles = (0..2)
            .map(|_| {
                let root = project.path().to_owned();
                let barrier = Arc::clone(&barrier);
                std::thread::spawn(move || {
                    barrier.wait();
                    RuntimeSlot::claim(&root, None, false).unwrap()
                })
            })
            .collect::<Vec<_>>();
        barrier.wait();
        let slots = handles
            .into_iter()
            .map(|handle| handle.join().unwrap())
            .collect::<Vec<_>>();
        assert_eq!(
            slots
                .iter()
                .map(|slot| slot.number)
                .collect::<BTreeSet<_>>(),
            BTreeSet::from([1, 2])
        );
        Ok(())
    }

    #[test]
    fn explicit_sharing_tracks_each_owner_and_preserves_native_locks() -> Result<()> {
        let project = tempfile::tempdir()?;
        let first = RuntimeSlot::claim(project.path(), Some(1), false)?;
        fs::write(first.data_dir.join("options.txt"), b"options")?;
        fs::create_dir(first.data_dir.join("world"))?;
        fs::write(
            first.data_dir.join("world/session.lock"),
            b"native lock bytes",
        )?;
        assert!(RuntimeSlot::claim(project.path(), Some(1), false).is_err());
        assert!(RuntimeSlot::claim(project.path(), None, true).is_err());
        let shared = RuntimeSlot::claim(project.path(), Some(1), true)?;
        assert_eq!(first.data_dir, shared.data_dir);
        assert_ne!(first.owner_id, shared.owner_id);
        assert_ne!(first.lease_path(), shared.lease_path());
        drop(first);
        assert!(RuntimeSlot::claim(project.path(), Some(1), false).is_err());
        assert_eq!(RuntimeSlot::claim(project.path(), None, false)?.number, 2);
        let data_dir = shared.data_dir.clone();
        drop(shared);
        let reused = RuntimeSlot::claim(project.path(), None, false)?;
        assert_eq!(reused.number, 1);
        assert_eq!(reused.data_dir, data_dir);
        assert_eq!(fs::read(data_dir.join("options.txt"))?, b"options");
        assert_eq!(
            fs::read(data_dir.join("world/session.lock"))?,
            b"native lock bytes"
        );
        Ok(())
    }

    #[test]
    fn two_projection_roots_for_the_same_mc_have_independent_slot_one() -> Result<()> {
        let repository = tempfile::tempdir()?;
        let left = repository.path().join("left-1.19.2");
        let right = repository.path().join("right-1.19.2");
        fs::create_dir(&left)?;
        fs::create_dir(&right)?;
        let left = RuntimeSlot::claim(&left, None, false)?;
        let right = RuntimeSlot::claim(&right, None, false)?;
        assert_eq!((left.number, right.number), (1, 1));
        assert_ne!(left.data_dir, right.data_dir);
        assert_ne!(left.lease_path(), right.lease_path());
        Ok(())
    }

    #[test]
    fn matching_recorded_child_survives_launcher_lease_release() -> Result<()> {
        let project = tempfile::tempdir()?;
        let slot = RuntimeSlot::claim(project.path(), Some(1), false)?;
        slot.record_spawn_pending()?;
        slot.record_child(std::process::id())?;
        drop(slot);
        assert!(RuntimeSlot::claim(project.path(), Some(1), false).is_err());
        assert_eq!(RuntimeSlot::claim(project.path(), None, false)?.number, 2);
        Ok(())
    }

    #[test]
    fn unlocked_stale_identity_is_recoverable_even_if_pid_was_reused() -> Result<()> {
        let project = tempfile::tempdir()?;
        let slot = RuntimeSlot::claim(project.path(), Some(1), false)?;
        let data_dir = slot.data_dir.clone();
        let owner_id = slot.owner_id.clone();
        drop(slot);
        let mut stale = read_owner(&data_dir, &owner_id, 1)?;
        stale.active = true;
        stale.launcher.creation_identity.push_str("-old-process");
        write_owner(project.path(), &data_dir, &stale)?;
        assert_eq!(RuntimeSlot::claim(project.path(), None, false)?.number, 1);
        Ok(())
    }

    #[test]
    fn invalid_zero_and_implicit_shared_slots_do_not_create_runtime_state() -> Result<()> {
        let project = tempfile::tempdir()?;
        assert!(RuntimeSlot::claim(project.path(), Some(0), false).is_err());
        assert!(RuntimeSlot::claim(project.path(), None, true).is_err());
        assert!(!project.path().join("build").exists());
        Ok(())
    }

    #[cfg(target_os = "linux")]
    #[test]
    fn runtime_build_symlink_is_rejected_without_changing_its_target() -> Result<()> {
        let project = tempfile::tempdir()?;
        let outside = tempfile::tempdir()?;
        fs::write(outside.path().join("marker"), b"untouched")?;
        std::os::unix::fs::symlink(outside.path(), project.path().join("build"))?;
        assert!(RuntimeSlot::claim(project.path(), None, false).is_err());
        assert_eq!(fs::read(outside.path().join("marker"))?, b"untouched");
        assert!(!outside.path().join("sfm-runtime").exists());
        Ok(())
    }

    #[test]
    fn interrupted_pending_spawn_is_never_reclaimed_or_compacted() -> Result<()> {
        let project = tempfile::tempdir()?;
        let slot = RuntimeSlot::claim(project.path(), Some(1), false)?;
        slot.record_spawn_pending()?;
        let data = slot.data_dir.clone();
        let owner_id = slot.owner_id.clone();
        drop(slot);
        let mut owner = read_owner(&data, &owner_id, 1)?;
        owner.launcher.creation_identity.push_str("-exited");
        write_owner(project.path(), &data, &owner)?;
        assert!(owner.spawn_pending);
        assert!(RuntimeSlot::claim(project.path(), Some(1), false).is_err());
        assert_eq!(RuntimeSlot::claim(project.path(), None, false)?.number, 2);
        assert!(
            data.join(OWNERS_DIRECTORY)
                .join(format!("{owner_id}.json"))
                .exists()
        );
        Ok(())
    }

    #[test]
    fn known_spawn_failure_clears_pending_and_reuses_persistent_data() -> Result<()> {
        let project = tempfile::tempdir()?;
        let slot = RuntimeSlot::claim(project.path(), None, false)?;
        fs::write(slot.data_dir.join("options.txt"), b"keep")?;
        slot.record_spawn_pending()?;
        assert!(slot.record_spawn_pending().is_err());
        slot.clear_spawn_pending()?;
        assert!(slot.clear_spawn_pending().is_err());
        drop(slot);
        let reused = RuntimeSlot::claim(project.path(), None, false)?;
        assert_eq!(reused.number, 1);
        assert_eq!(fs::read(reused.data_dir.join("options.txt"))?, b"keep");
        Ok(())
    }

    #[test]
    fn child_registration_resolves_pending_but_child_still_owns_slot() -> Result<()> {
        let project = tempfile::tempdir()?;
        let slot = RuntimeSlot::claim(project.path(), None, false)?;
        assert!(slot.record_child(std::process::id()).is_err());
        slot.record_spawn_pending()?;
        slot.record_child(std::process::id())?;
        let owner = read_owner(&slot.data_dir, &slot.owner_id, slot.number)?;
        assert!(!owner.spawn_pending);
        assert!(owner.child.is_some());
        assert!(slot.clear_spawn_pending().is_err());
        assert!(slot.record_spawn_pending().is_err());
        drop(slot);
        assert_eq!(RuntimeSlot::claim(project.path(), None, false)?.number, 2);
        Ok(())
    }

    #[test]
    fn dead_history_above_old_scan_bound_is_compacted_without_touching_game_data() -> Result<()> {
        let project = tempfile::tempdir()?;
        let slot = RuntimeSlot::claim(project.path(), None, false)?;
        let data = slot.data_dir.clone();
        let mut dead = read_owner(&data, &slot.owner_id, 1)?;
        drop(slot);
        dead.active = false;
        dead.child = None;
        fs::create_dir(data.join("world"))?;
        fs::write(data.join("world/session.lock"), b"native lock")?;
        fs::write(data.join("options.txt"), b"retained options")?;
        fs::create_dir(data.join("launches"))?;
        fs::write(data.join("launches/evidence"), b"retained evidence")?;
        let owners = data.join(OWNERS_DIRECTORY);
        let retained_limit = 8;
        for index in 0..=retained_limit / 2 {
            dead.owner_id = format!("owner-1-dead{index}");
            fs::write(owners.join(format!("{}.lock", dead.owner_id)), b"")?;
            fs::write(
                owners.join(format!("{}.json", dead.owner_id)),
                facet_json::to_string_pretty(&dead)?,
            )?;
        }
        assert!(fs::read_dir(&owners)?.count() > retained_limit);
        // Exercise the production compactor beyond its retained-owner bound,
        // with a small fixture bound instead of thousands of file writes.
        let allocation = allocation_lock(project.path())?;
        assert!(!compact_owner_history(
            project.path(),
            &data,
            1,
            retained_limit
        )?);
        assert_eq!(fs::read_dir(&owners)?.count(), 0);
        drop(allocation);
        let reused = RuntimeSlot::claim(project.path(), None, false)?;
        assert_eq!(reused.number, 1);
        assert_eq!(fs::read_dir(&owners)?.count(), 2);
        assert_eq!(fs::read(data.join("world/session.lock"))?, b"native lock");
        assert_eq!(fs::read(data.join("options.txt"))?, b"retained options");
        assert_eq!(
            fs::read(data.join("launches/evidence"))?,
            b"retained evidence"
        );
        Ok(())
    }

    #[test]
    fn compaction_keeps_shared_live_and_pending_owners() -> Result<()> {
        let project = tempfile::tempdir()?;
        let first = RuntimeSlot::claim(project.path(), Some(1), false)?;
        let pending = RuntimeSlot::claim(project.path(), Some(1), true)?;
        pending.record_spawn_pending()?;
        let dead = RuntimeSlot::claim(project.path(), Some(1), true)?;
        let dead_id = dead.owner_id.clone();
        drop(dead);
        let next = RuntimeSlot::claim(project.path(), Some(1), true)?;
        let owners = next.data_dir.join(OWNERS_DIRECTORY);
        assert!(!owners.join(format!("{dead_id}.json")).exists());
        assert!(owners.join(format!("{}.json", first.owner_id)).exists());
        assert!(owners.join(format!("{}.json", pending.owner_id)).exists());
        assert_ne!(first.owner_id, next.owner_id);
        Ok(())
    }

    #[test]
    fn old_active_unregistered_owner_is_conservatively_pending() -> Result<()> {
        let project = tempfile::tempdir()?;
        let slot = RuntimeSlot::claim(project.path(), None, false)?;
        let data = slot.data_dir.clone();
        let owner_id = slot.owner_id.clone();
        drop(slot);
        let mut owner = read_owner(&data, &owner_id, 1)?;
        owner.schema = RuntimeSlotOwner::LEGACY_SCHEMA.to_owned();
        owner.active = true;
        owner.launcher.creation_identity.push_str("-old");
        let raw = format!(
            r#"{{"schema":"{}","owner_id":"{}","number":1,"launcher":{},"child":null,"active":true}}"#,
            RuntimeSlotOwner::LEGACY_SCHEMA,
            owner_id,
            facet_json::to_string(&owner.launcher)?
        );
        fs::write(
            data.join(OWNERS_DIRECTORY).join(format!("{owner_id}.json")),
            raw,
        )?;
        assert!(read_owner(&data, &owner_id, 1)?.spawn_pending);
        assert_eq!(RuntimeSlot::claim(project.path(), None, false)?.number, 2);
        Ok(())
    }

    #[test]
    fn free_orphaned_owner_lock_stays_busy_and_preserved() -> Result<()> {
        let project = tempfile::tempdir()?;
        let slot = RuntimeSlot::claim(project.path(), Some(1), false)?;
        let owners = slot.data_dir.join(OWNERS_DIRECTORY);
        let lock = slot.lease_path();
        let metadata = owners.join(format!("{}.json", slot.owner_id));
        drop(slot);
        fs::remove_file(&metadata)?;
        let before = fs::read(&lock)?;
        assert!(RuntimeSlot::claim(project.path(), Some(1), false).is_err());
        let next = RuntimeSlot::claim(project.path(), None, false)?;
        assert_eq!(next.number, 2);
        assert_eq!(fs::read(&lock)?, before);
        assert!(!metadata.exists());
        Ok(())
    }

    #[test]
    fn malformed_or_unverifiable_owner_is_rejected_without_compacting_its_files() -> Result<()> {
        let project = tempfile::tempdir()?;
        let slot = RuntimeSlot::claim(project.path(), Some(1), false)?;
        let data = slot.data_dir.clone();
        let owner_id = slot.owner_id.clone();
        let lock = slot.lease_path();
        drop(slot);
        let metadata = data.join(OWNERS_DIRECTORY).join(format!("{owner_id}.json"));
        let mut unverifiable = read_owner(&data, &owner_id, 1)?;
        unverifiable.launcher.creation_identity.clear();
        let invalid_inputs = [
            b"{incomplete metadata".to_vec(),
            facet_json::to_string_pretty(&unverifiable)?.into_bytes(),
        ];
        for invalid in invalid_inputs {
            fs::write(&metadata, &invalid)?;
            let lock_before = fs::read(&lock)?;
            assert!(RuntimeSlot::claim(project.path(), Some(1), false).is_err());
            assert_eq!(fs::read(&metadata)?, invalid);
            assert_eq!(fs::read(&lock)?, lock_before);
        }
        Ok(())
    }

    #[test]
    fn interrupted_compaction_dead_metadata_without_lock_is_rechecked_and_removed() -> Result<()> {
        let project = tempfile::tempdir()?;
        let slot = RuntimeSlot::claim(project.path(), Some(1), false)?;
        let data = slot.data_dir.clone();
        let lock = slot.lease_path();
        let metadata = data
            .join(OWNERS_DIRECTORY)
            .join(format!("{}.json", slot.owner_id));
        drop(slot);
        fs::write(data.join("options.txt"), b"retained options")?;
        fs::create_dir(data.join("world"))?;
        fs::write(data.join("world/session.lock"), b"native world lock")?;
        fs::remove_file(&lock)?;
        assert!(metadata.is_file());
        let reused = RuntimeSlot::claim(project.path(), None, false)?;
        assert_eq!(reused.number, 1);
        assert!(!metadata.exists());
        assert!(!lock.exists());
        assert_eq!(fs::read_dir(data.join(OWNERS_DIRECTORY))?.count(), 2);
        assert_eq!(fs::read(data.join("options.txt"))?, b"retained options");
        assert_eq!(
            fs::read(data.join("world/session.lock"))?,
            b"native world lock"
        );
        Ok(())
    }
}

#[cfg(test)]
#[path = "real_child_runtime_tests.rs"]
mod real_child_runtime_tests;

#[cfg(test)]
#[path = "runtime_fixture_child.rs"]
mod runtime_fixture_child;
