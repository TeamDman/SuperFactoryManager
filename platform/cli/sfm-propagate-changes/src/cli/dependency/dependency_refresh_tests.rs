use super::*;
use crate::branch_targets::BranchName;
use crate::branch_targets::WorktreePath;
use crate::branch_targets::WorktreeTarget;
use crate::toolchain_lockfile_schema::read_current;

struct FixtureFetcher {
    expected_url: String,
    bytes: Vec<u8>,
}

impl ArtifactFetcher for FixtureFetcher {
    fn fetch(
        &self,
        url: &str,
        _cancellation_token: &CancellationToken,
    ) -> eyre::Result<Option<Vec<u8>>> {
        Ok((url == self.expected_url).then(|| self.bytes.clone()))
    }
}

#[test]
fn targeted_refresh_updates_only_cc_tweaked_in_injected_cache() {
    let directory = tempfile::tempdir().expect("temp directory");
    let cache_home = CacheHome(directory.path().join("isolated-cache"));
    let input = include_str!("../../../../../minecraft/sfm-toolchain.lock.json");
    let lockfile = read_current(input).expect("v3 fixture");
    let cc = lockfile
        .dependencies
        .iter()
        .find(|dependency| dependency.id == "cc-tweaked")
        .expect("CC fixture");
    let cc_artifact_id = cc.components[0].derived_checks.artifact_id.clone();
    let artifact = lockfile
        .artifacts
        .iter()
        .find(|artifact| artifact.id == cc_artifact_id)
        .expect("CC artifact");
    let expected_url = artifact.url.clone().expect("remote URL");
    let old_hash = artifact.hash;
    let unrelated_hash = lockfile
        .artifacts
        .iter()
        .find(|artifact| artifact.id != cc_artifact_id)
        .map(|artifact| (artifact.id.clone(), artifact.hash))
        .expect("unrelated artifact");
    let lockfile_path = directory.path().join("sfm-toolchain.lock.json");
    std::fs::write(&lockfile_path, input).expect("fixture lockfile");
    let inventory = DependencyInventory {
        target: WorktreeTarget {
            branch: BranchName::from("1.19.2"),
            worktree_path: WorktreePath::from(directory.path().to_path_buf()),
            core: true,
            mc_version: None,
        },
        lockfile_path: lockfile_path.clone(),
        cache_home,
        original_input: input.to_owned(),
        lockfile,
    };
    let bytes = b"refreshed CC:Tweaked bytes".to_vec();

    let reports = refresh_dependencies(
        inventory,
        Some("cc-tweaked"),
        &CancellationToken::new(),
        &FixtureFetcher {
            expected_url,
            bytes: bytes.clone(),
        },
    )
    .expect("targeted refresh");

    assert_eq!(reports.len(), 1);
    assert_eq!(reports[0].old_hash, old_hash);
    let expected_hash = ContentHash::from_bytes(&bytes, ContentHashAlgorithm::Blake3);
    assert_eq!(reports[0].new_hash, expected_hash);
    let written =
        read_current(&std::fs::read_to_string(lockfile_path).expect("updated lockfile read"))
            .expect("updated v3 lockfile");
    assert_eq!(
        written
            .artifacts
            .iter()
            .find(|artifact| artifact.id == cc_artifact_id)
            .expect("updated CC artifact")
            .hash,
        expected_hash
    );
    assert_eq!(
        written
            .artifacts
            .iter()
            .find(|artifact| artifact.id == unrelated_hash.0)
            .expect("unrelated artifact")
            .hash,
        unrelated_hash.1
    );
}

#[test]
fn platform_only_target_has_no_refreshable_remote_artifacts() {
    let (mut inventory, _) = fixture();
    let artifact_id = inventory
        .dependency("minecraft")
        .expect("Minecraft fixture")
        .components[0]
        .derived_checks
        .artifact_id
        .clone();
    let artifact = inventory
        .lockfile
        .artifacts
        .iter_mut()
        .find(|artifact| artifact.id == artifact_id)
        .expect("Minecraft artifact");
    artifact.provenance = ArtifactProvenanceV3::ToolchainGenerated;
    artifact.url = None;
    let error = refresh_dependencies(
        inventory,
        Some("minecraft"),
        &CancellationToken::new(),
        &FixtureFetcher {
            expected_url: String::new(),
            bytes: Vec::new(),
        },
    )
    .expect_err("toolchain refresh should fail");
    assert!(
        error
            .to_string()
            .contains("no refreshable remote artifacts")
    );
}

fn fixture() -> (DependencyInventory, tempfile::TempDir) {
    let directory = tempfile::tempdir().expect("temp directory");
    let input = include_str!("../../../../../minecraft/sfm-toolchain.lock.json");
    let lockfile_path = directory.path().join("sfm-toolchain.lock.json");
    std::fs::write(&lockfile_path, input).expect("fixture lockfile");
    (
        DependencyInventory {
            target: WorktreeTarget {
                branch: BranchName::from("1.19.2"),
                worktree_path: WorktreePath::from(directory.path().to_path_buf()),
                core: true,
                mc_version: None,
            },
            lockfile_path,
            cache_home: CacheHome(directory.path().join("cache")),
            original_input: input.to_owned(),
            lockfile: read_current(input).expect("v3 fixture"),
        },
        directory,
    )
}
