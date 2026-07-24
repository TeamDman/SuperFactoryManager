use super::*;
use crate::branch_targets::BranchName;
use crate::branch_targets::WorktreePath;
use crate::branch_targets::WorktreeTarget;
use crate::cli::dependency::dependency_add_cli::ArtifactFetcher;
use crate::jar_build::hash::ContentHash;
use crate::jar_build::hash::ContentHashAlgorithm;
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
fn adding_mekanism_api_preserves_main_component() {
    let directory = tempfile::tempdir().expect("temp directory");
    let cache_home = CacheHome(directory.path().join("isolated-cache"));
    let mut lockfile = read_current(include_str!(
        "../../../../../minecraft/sfm-toolchain.lock.json"
    ))
    .expect("v3 fixture");
    let mekanism = lockfile
        .dependencies
        .iter_mut()
        .find(|dependency| dependency.id == "mekanism")
        .expect("Mekanism fixture");
    let removed = mekanism
        .components
        .iter()
        .find(|component| component.id == "api")
        .map(|component| component.derived_checks.artifact_id.clone());
    mekanism
        .components
        .retain(|component| component.id != "api");
    if let Some(artifact_id) = removed {
        lockfile
            .artifacts
            .retain(|artifact| artifact.id != artifact_id);
    }
    let input = lockfile.to_canonical_json().expect("fixture JSON");
    let lockfile_path = directory.path().join("sfm-toolchain.lock.json");
    std::fs::write(&lockfile_path, &input).expect("fixture lockfile");
    let inventory = crate::dependency_inventory::DependencyInventory {
        target: WorktreeTarget {
            branch: BranchName::from("1.19.2"),
            worktree_path: WorktreePath::from(directory.path().to_path_buf()),
            core: true,
            mc_version: None,
        },
        lockfile_path: lockfile_path.clone(),
        cache_home,
        original_input: input,
        lockfile,
    };
    let coordinate = "mekanism:Mekanism:1.19.2-10.3.9.13:api";
    let expected_url = concat!(
        "https://modmaven.dev/mekanism/Mekanism/1.19.2-10.3.9.13/",
        "Mekanism-1.19.2-10.3.9.13-api.jar"
    );
    let bytes = b"controlled Mekanism API artifact".to_vec();
    let args = DependencyAddArgs {
        id: "mekanism".to_owned(),
        branch: BranchSelector::from("1.19.2".to_owned()),
        maven: Some(coordinate.to_owned()),
        curseforge_project: None,
        curseforge_file: None,
        kind: None,
        role: None,
        curseforge_api_key: None,
        curseforge_token: None,
        curseforge_op_secret: None,
        scope: vec![DependencyScopeV3::Compile],
        repository: Some("modmaven".to_owned()),
        artifact_treatment: Some(ArtifactTreatmentV3::Plain),
        bundle_accepted_version_range: None,
        bundle_artifact_version: None,
        bundle_is_obfuscated: false,
        display_name: None,
        project_url: None,
        notes: None,
    };

    let report = add_component(
        inventory,
        &args,
        "api",
        &CancellationToken::new(),
        &FixtureFetcher {
            expected_url: expected_url.to_owned(),
            bytes: bytes.clone(),
        },
    )
    .expect("component add");

    assert_eq!(report.component_id, "api");
    assert_eq!(
        report.hash,
        ContentHash::from_bytes(&bytes, ContentHashAlgorithm::Blake3)
    );
    assert_mekanism_api_component(&lockfile_path);
}

fn assert_mekanism_api_component(lockfile_path: &std::path::Path) {
    let written =
        read_current(&std::fs::read_to_string(lockfile_path).expect("updated lockfile read"))
            .expect("updated v3 lockfile");
    let mekanism = written
        .dependencies
        .iter()
        .find(|dependency| dependency.id == "mekanism")
        .expect("Mekanism remains");
    assert_eq!(mekanism.components.len(), 2);
    assert!(
        mekanism
            .components
            .iter()
            .any(|component| component.id == "main")
    );
    let api = mekanism
        .components
        .iter()
        .find(|component| component.id == "api")
        .expect("API component");
    assert_eq!(
        api.declaration.artifact_treatment,
        ArtifactTreatmentV3::Plain
    );
    assert_eq!(api.declaration.scopes, [DependencyScopeV3::Compile]);
}
