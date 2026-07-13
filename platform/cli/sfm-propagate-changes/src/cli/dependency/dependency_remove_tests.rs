use super::*;
use crate::branch_targets::BranchName;
use crate::branch_targets::WorktreePath;
use crate::branch_targets::WorktreeTarget;
use crate::toolchain_lockfile_schema::read_current;
use std::path::PathBuf;

#[test]
fn removing_ae2_api_preserves_its_main_component_and_artifact() {
    let (inventory, lockfile_path) = fixture();
    let dependency = inventory
        .lockfile
        .dependencies
        .iter()
        .find(|dependency| dependency.id == "applied-energistics-2")
        .expect("AE2 fixture");
    let api_artifact = dependency.components[0].derived_checks.artifact_id.clone();
    let main_artifact = dependency.components[1].derived_checks.artifact_id.clone();

    remove_target(inventory, "applied-energistics-2/api").expect("remove API");

    let written = read(&lockfile_path);
    let dependency = written
        .dependencies
        .iter()
        .find(|dependency| dependency.id == "applied-energistics-2")
        .expect("AE2 remains");
    assert_eq!(dependency.components.len(), 1);
    assert_eq!(dependency.components[0].id, "main");
    assert!(
        written
            .artifacts
            .iter()
            .any(|artifact| artifact.id == main_artifact)
    );
    assert!(
        !written
            .artifacts
            .iter()
            .any(|artifact| artifact.id == api_artifact)
    );
}

#[test]
fn removal_refuses_platform_and_last_component_targets() {
    let (inventory, _) = fixture();
    let error = remove_target(inventory, "minecraft").expect_err("platform removal");
    assert!(error.to_string().contains("platform dependency"));

    let (inventory, _) = fixture();
    let error = remove_target(inventory, "cc-tweaked/main").expect_err("last component");
    assert!(error.to_string().contains("last component"));
}

fn fixture() -> (DependencyInventory, PathBuf) {
    let directory = tempfile::tempdir().expect("temp directory").keep();
    let input = include_str!("../../../../../minecraft/sfm-toolchain.lock.json");
    let lockfile_path = directory.join("sfm-toolchain.lock.json");
    std::fs::write(&lockfile_path, input).expect("fixture lockfile");
    (
        DependencyInventory {
            target: WorktreeTarget {
                branch: BranchName::from("1.19.2"),
                worktree_path: WorktreePath::from(directory.clone()),
                core: true,
                mc_version: None,
            },
            lockfile_path: lockfile_path.clone(),
            cache_home: CacheHome(directory.join("cache")),
            original_input: input.to_owned(),
            lockfile: read_current(input).expect("v3 fixture"),
        },
        lockfile_path,
    )
}

fn read(path: &PathBuf) -> crate::toolchain_lockfile_schema::version::v3::ArtifactLockfileV3 {
    read_current(&std::fs::read_to_string(path).expect("lockfile read")).expect("v3 result")
}
