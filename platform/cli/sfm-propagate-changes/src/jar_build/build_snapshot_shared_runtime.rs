//! Invocation-held direct readers for a completed Forge runtime snapshot.
//!
//! Publication owns the content check. A live client holds every selected
//! file against write/delete and the selected directory ancestry against
//! replacement. Unselected roots stay outside the client classpath.
use super::BuildSnapshotReceipt;
use super::canonical_directory;
use super::source_capture::SnapshotSourceCapture;
use crate::cancellation::CancellationToken;
use eyre::Result;
use eyre::ensure;
use std::collections::BTreeSet;
use std::path::PathBuf;

pub(crate) struct SharedRuntimeSnapshot {
    roots: Vec<PathBuf>,
    _capture: SnapshotSourceCapture,
}

impl SharedRuntimeSnapshot {
    pub(crate) fn acquire(
        snapshot: &BuildSnapshotReceipt,
        selected: &[&str],
        cancellation: &CancellationToken,
    ) -> Result<Option<Self>> {
        // The named Forge mod route can accept distinct class and resource
        // roots, including the normal client's GameTest source set. Other run
        // shapes retain their private merged session copy.
        if selected != ["staged-resources", "classes"]
            && selected
                != [
                    "staged-resources",
                    "classes",
                    "gametest/resources",
                    "gametest/classes",
                ]
        {
            return Ok(None);
        }
        snapshot.managed_recheck()?;
        let mut names = BTreeSet::new();
        let mut roots = Vec::new();
        for root in selected {
            let prefix = format!("project/{root}/");
            for path in snapshot.files.keys() {
                if let Some(relative) = path.strip_prefix(&prefix) {
                    // The old merged directory chose the later root for a
                    // collision. A multi-root classpath may choose another.
                    if !names.insert(relative.to_lowercase()) {
                        return Ok(None);
                    }
                }
            }
            let path = canonical_directory(&snapshot.snapshot_dir.join("project").join(root))?;
            if path.to_string_lossy().contains(';') {
                return Ok(None);
            }
            roots.push(path);
        }
        let Some(mut capture) =
            SnapshotSourceCapture::prepare(&snapshot.snapshot_dir.join("project"), cancellation)?
        else {
            return Ok(None);
        };
        if !capture.hold_selected_without_hash(snapshot, selected, cancellation)? {
            return Ok(None);
        }
        Ok(Some(Self {
            roots,
            _capture: capture,
        }))
    }

    pub(crate) fn roots(&self) -> &[PathBuf] {
        &self.roots
    }

    pub(crate) fn recheck_membership(
        &self,
        snapshot: &BuildSnapshotReceipt,
        cancellation: &CancellationToken,
    ) -> Result<()> {
        let Some(current) =
            SnapshotSourceCapture::prepare(&snapshot.snapshot_dir.join("project"), cancellation)?
        else {
            eyre::bail!("shared runtime snapshot ancestry is unavailable");
        };
        ensure!(
            current.matches_names(snapshot),
            "shared runtime snapshot gained or lost an entry during launch"
        );
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::jar_build::runtime_session::RuntimeSession;
    use crate::jdk::ResolvedJava;
    use std::fs;

    fn fixture(collision: bool) -> Result<(tempfile::TempDir, BuildSnapshotReceipt)> {
        let temporary = tempfile::tempdir()?;
        let minecraft = temporary.path().join("projection");
        let cache = minecraft.join("build/sfm-toolchain/fixture");
        let project = cache.join("project");
        fs::create_dir_all(project.join("classes"))?;
        fs::create_dir_all(project.join("staged-resources"))?;
        fs::create_dir_all(project.join("gametest/classes"))?;
        fs::create_dir_all(project.join("gametest/resources"))?;
        fs::write(project.join("classes/Main.class"), b"class")?;
        fs::write(project.join("staged-resources/mods.toml"), b"resource")?;
        fs::write(project.join("gametest/classes/GameTest.class"), b"test")?;
        if collision {
            fs::write(project.join("staged-resources/main.class"), b"overlap")?;
        }
        let java = ResolvedJava {
            executable: minecraft.join("sdk/bin/java.exe"),
            home: Some(minecraft.join("sdk")),
            version_output: "fixture SDK".to_owned(),
            major_version: 17,
            selection: "fixture".to_owned(),
            pin_url: None,
            pin_sha512: None,
        };
        let receipt = BuildSnapshotReceipt::publish(
            &minecraft,
            &cache,
            "1.19.2",
            &java,
            &project,
            "sdk-fixture",
        )?;
        Ok((temporary, receipt))
    }

    #[test]
    fn held_roots_prevent_selected_writes_and_detect_new_names() -> Result<()> {
        let (_temporary, snapshot) = fixture(false)?;
        let token = CancellationToken::new();
        let selected = ["staged-resources", "classes"];
        let held = SharedRuntimeSnapshot::acquire(&snapshot, &selected, &token)?.unwrap();
        assert_eq!(held.roots().len(), 2);
        let selected_file = held.roots()[1].join("Main.class");
        assert!(fs::write(&selected_file, b"changed").is_err());
        assert!(fs::remove_file(&selected_file).is_err());
        assert!(fs::rename(&held.roots()[1], snapshot.snapshot_dir.join("moved")).is_err());
        held.recheck_membership(&snapshot, &token)?;
        fs::write(held.roots()[1].join("Unexpected.class"), b"new")?;
        assert!(held.recheck_membership(&snapshot, &token).is_err());
        drop(held);
        fs::write(selected_file, b"released")?;
        assert!(
            snapshot.recheck().is_err(),
            "explicit audit detects the edit"
        );
        Ok(())
    }

    #[test]
    fn overlap_and_missing_inputs_fall_back_without_session_publication() -> Result<()> {
        let (_temporary, collision) = fixture(true)?;
        let token = CancellationToken::new();
        let selected = ["staged-resources", "classes"];
        assert!(SharedRuntimeSnapshot::acquire(&collision, &selected, &token)?.is_none());
        let (_temporary, missing) = fixture(false)?;
        fs::remove_file(missing.snapshot_dir.join("project/classes/Main.class"))?;
        assert!(SharedRuntimeSnapshot::acquire(&missing, &selected, &token)?.is_none());
        assert!(missing.recheck().is_err());
        Ok(())
    }

    #[test]
    fn shared_session_retains_private_state_without_copying_mod_payloads() -> Result<()> {
        let (_temporary, snapshot) = fixture(false)?;
        let token = CancellationToken::new();
        let selected = ["staged-resources", "classes"];
        let session = RuntimeSession::prepare_shared(
            &snapshot,
            "runClientSmoke",
            &selected,
            None,
            None,
            false,
            &token,
        )?
        .expect("distinct selected roots should use the direct route");
        assert_eq!(session.shared_roots().unwrap().len(), 2);
        assert_eq!(fs::read_dir(session.receipt.mod_root())?.count(), 0);
        assert!(session.receipt.state_dir().is_dir());
        crate::jar_build::runtime_session_receipt::RuntimeSessionReceipt::load(
            &session.receipt.receipt_path(),
            &snapshot.minecraft_dir,
        )?;
        session.recheck_shared_snapshot(&snapshot, &token)?;
        let selected_file = snapshot.snapshot_dir.join("project/classes/Main.class");
        assert!(fs::write(&selected_file, b"changed").is_err());
        drop(session);
        fs::write(selected_file, b"released")?;
        Ok(())
    }

    #[test]
    fn normal_client_holds_all_four_roots_without_a_private_mod_copy() -> Result<()> {
        let (_temporary, snapshot) = fixture(false)?;
        let token = CancellationToken::new();
        let selected = [
            "staged-resources",
            "classes",
            "gametest/resources",
            "gametest/classes",
        ];
        let session = RuntimeSession::prepare_shared(
            &snapshot,
            "runClientSmoke",
            &selected,
            None,
            None,
            false,
            &token,
        )?
        .expect("normal client roots have distinct names");
        assert_eq!(session.shared_roots().unwrap().len(), 4);
        assert_eq!(fs::read_dir(session.receipt.mod_root())?.count(), 0);
        assert!(
            fs::write(
                snapshot
                    .snapshot_dir
                    .join("project/gametest/classes/GameTest.class"),
                b"changed"
            )
            .is_err()
        );
        session.recheck_shared_snapshot(&snapshot, &token)?;
        Ok(())
    }
}
