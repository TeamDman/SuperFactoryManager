//! Launch ownership is separate from the mutable compiler-cache lock.
use super::build_snapshot_receipt::BuildSnapshotReceipt;
use super::build_snapshot_receipt::ensure_owned_directory;
use super::runtime_session_receipt::RuntimeSessionReceipt;
use super::runtime_session_receipt::safe_component;
use super::runtime_slot::RuntimeSlot;
use eyre::ensure;
use std::fs;
use std::io::Write as _;
use std::path::Path;

pub(crate) struct RuntimeSession {
    pub receipt: RuntimeSessionReceipt,
    pub slot: RuntimeSlot,
}

impl RuntimeSession {
    pub fn prepare(
        snapshot: &BuildSnapshotReceipt,
        run_kind: &str,
        roots: &[&str],
        jdwp_port: Option<u16>,
        requested_slot: Option<u32>,
        share_runtime: bool,
    ) -> eyre::Result<Self> {
        ensure!(safe_component(run_kind), "invalid runtime run kind");
        snapshot.recheck()?;
        let slot = RuntimeSlot::claim(&snapshot.minecraft_dir, requested_slot, share_runtime)?;
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
        snapshot.materialize_roots(&receipt.mod_root(), roots)?;
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

fn write_locator(path: &Path, receipt: &RuntimeSessionReceipt) -> eyre::Result<()> {
    let parent = path
        .parent()
        .ok_or_else(|| eyre::eyre!("session locator has no parent"))?;
    let mut temporary = tempfile::NamedTempFile::new_in(parent)?;
    temporary.write_all(facet_json::to_string_pretty(receipt)?.as_bytes())?;
    temporary.persist(path).map_err(|error| error.error)?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::artifact_lock::ArtifactLock;
    use crate::jdk::ResolvedJava;

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
        let live =
            RuntimeSession::prepare(&first, "runClient", &["classes"], Some(5005), None, false)?;
        fs::write(live.slot.data_dir.join("options.txt"), b"retained options")?;
        fs::create_dir(live.slot.data_dir.join("world"))?;
        fs::write(
            live.slot.data_dir.join("world/session.lock"),
            b"native world lock marker",
        )?;
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
        let receipt_path = live.receipt.receipt_path();
        drop(live);
        let ended = RuntimeSessionReceipt::load(&receipt_path, &minecraft)?;
        assert!(!ended.active);
        assert!(ended.check_active(&minecraft, 5005).is_err());
        assert!(ended.session_dir.is_dir());
        Ok(())
    }
}
