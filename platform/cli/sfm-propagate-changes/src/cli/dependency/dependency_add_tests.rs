use super::*;
use crate::branch_targets::BranchName;
use crate::branch_targets::WorktreePath;
use crate::branch_targets::WorktreeTarget;
use crate::curseforge::CurseforgeMod;
use crate::curseforge::CurseforgeProjectFileItem;
use crate::curseforge::CurseforgeSortableGameVersion;
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

struct FixtureCurseforgeMetadata {
    project: CurseforgeMod,
    file: CurseforgeProjectFileItem,
}

impl CurseforgeProjectMetadata for FixtureCurseforgeMetadata {
    fn fetch_project(&self, _project_id: CurseforgeProjectId) -> eyre::Result<CurseforgeMod> {
        Ok(self.project.clone())
    }

    fn fetch_project_file(
        &self,
        _project_id: CurseforgeProjectId,
        _file_id: CurseforgeProjectFileId,
    ) -> eyre::Result<CurseforgeProjectFileItem> {
        Ok(self.file.clone())
    }
}

#[test]
fn add_resolves_into_injected_cache_and_writes_complete_v3_declaration() {
    let directory = tempfile::tempdir().expect("temp directory");
    let cache_home = CacheHome(directory.path().join("isolated-cache"));
    let mut lockfile = read_current(include_str!(
        "../../../../../minecraft/sfm-toolchain.lock.json"
    ))
    .expect("v3 fixture");
    lockfile
        .dependencies
        .retain(|dependency| dependency.id != "cc-tweaked");
    lockfile.artifacts.retain(|artifact| {
        artifact
            .owner
            .as_ref()
            .is_none_or(|owner| owner.dependency_id != "cc-tweaked")
    });
    let input = lockfile.to_canonical_json().expect("fixture JSON");
    let lockfile_path = directory.path().join("sfm-toolchain.lock.json");
    std::fs::write(&lockfile_path, &input).expect("fixture lockfile");
    let inventory = DependencyInventory {
        target: WorktreeTarget {
            branch: BranchName::from("1.19.2"),
            worktree_path: WorktreePath::from(directory.path().to_path_buf()),
            core: true,
            mc_version: None,
        },
        lockfile_path: lockfile_path.clone(),
        cache_home: cache_home.clone(),
        original_input: input,
        lockfile,
    };
    let coordinate = "org.squiddev:cc-tweaked-1.19.2:1.101.3";
    let expected_url = concat!(
        "https://squiddev.cc/maven/org/squiddev/cc-tweaked-1.19.2/1.101.3/",
        "cc-tweaked-1.19.2-1.101.3.jar"
    );
    let bytes = b"isolated CC:Tweaked artifact".to_vec();
    let fetcher = FixtureFetcher {
        expected_url: expected_url.to_owned(),
        bytes: bytes.clone(),
    };

    let report = add_dependency(
        inventory,
        &args(coordinate),
        &CancellationToken::new(),
        &fetcher,
    )
    .expect("dependency add");

    let expected_hash = ContentHash::from_bytes(&bytes, ContentHashAlgorithm::Blake3);
    assert_eq!(report.hash, expected_hash);
    let written =
        read_current(&std::fs::read_to_string(lockfile_path).expect("updated lockfile read"))
            .expect("updated v3 lockfile");
    let dependency = written
        .dependencies
        .iter()
        .find(|dependency| dependency.id == "cc-tweaked")
        .expect("CC:Tweaked declaration");
    assert_eq!(dependency.kind, DependencyKindV3::Mod);
    assert_eq!(dependency.role, DependencyRoleV3::Integration);
    assert_eq!(dependency.components.len(), 1);
    let component = &dependency.components[0];
    assert_eq!(component.id, "main");
    assert_eq!(
        component.declaration.scopes,
        vec![
            DependencyScopeV3::Compile,
            DependencyScopeV3::Runtime,
            DependencyScopeV3::GametestCompile,
            DependencyScopeV3::GametestRuntime,
        ]
    );
    assert_eq!(
        component.declaration.artifact_treatment,
        ArtifactTreatmentV3::LoaderManagedMod
    );
    assert_eq!(
        component.declaration.data_run_policy,
        DataRunPolicyV3::Exclude
    );
    let artifact = written
        .artifacts
        .iter()
        .find(|artifact| artifact.id == component.derived_checks.artifact_id)
        .expect("owned artifact");
    assert_eq!(artifact.repository_id.as_deref(), Some("squiddev"));
    assert_eq!(artifact.url.as_deref(), Some(expected_url));
    assert_eq!(artifact.hash, expected_hash);
    let local_path = cache_home.join("minecraft-toolchain").join(
        component
            .derived_checks
            .cache_path
            .strip_prefix("$sfm-cache")
            .expect("portable path"),
    );
    assert_eq!(std::fs::read(local_path).expect("cached bytes"), bytes);
}

#[test]
fn add_adopts_matching_unowned_migrated_artifact_without_fetching() {
    let directory = tempfile::tempdir().expect("temp directory");
    let cache_home = CacheHome(directory.path().join("isolated-cache"));
    let mut lockfile = read_current(include_str!(
        "../../../../../minecraft/sfm-toolchain.lock.json"
    ))
    .expect("v3 fixture");
    let coordinate = "com.github.javaparser:javaparser-symbol-solver-core:3.26.4";
    let expected_artifact = lockfile
        .artifacts
        .iter()
        .find(|artifact| artifact.coordinate.as_deref() == Some(coordinate))
        .expect("unowned JavaParser artifact");
    let expected_artifact_id = expected_artifact.id.clone();
    let expected_hash = expected_artifact.hash;
    assert!(expected_artifact.owner.is_none());
    lockfile
        .dependencies
        .retain(|dependency| dependency.id != "javaparser");
    let input = lockfile.to_canonical_json().expect("fixture JSON");
    let lockfile_path = directory.path().join("sfm-toolchain.lock.json");
    std::fs::write(&lockfile_path, &input).expect("fixture lockfile");
    let inventory = DependencyInventory {
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
    let mut dependency_args = args(coordinate);
    dependency_args.id = "javaparser".to_owned();
    dependency_args.kind = Some(DependencyKindV3::Library);
    dependency_args.role = Some(DependencyRoleV3::Test);
    dependency_args.scope = vec![
        DependencyScopeV3::TestCompile,
        DependencyScopeV3::TestRuntime,
    ];
    dependency_args.repository = Some("maven-central".to_owned());
    dependency_args.artifact_treatment = Some(ArtifactTreatmentV3::Plain);

    let report = add_dependency(
        inventory,
        &dependency_args,
        &CancellationToken::new(),
        &FixtureFetcher {
            expected_url: "must-not-fetch".to_owned(),
            bytes: Vec::new(),
        },
    )
    .expect("unowned artifact should be adopted");

    assert_eq!(report.hash, expected_hash);
    let written =
        read_current(&std::fs::read_to_string(lockfile_path).expect("updated lockfile read"))
            .expect("updated v3 lockfile");
    let dependency = written
        .dependencies
        .iter()
        .find(|dependency| dependency.id == "javaparser")
        .expect("JavaParser declaration");
    assert_eq!(dependency.kind, DependencyKindV3::Library);
    assert_eq!(dependency.role, DependencyRoleV3::Test);
    assert_eq!(
        dependency.components[0].derived_checks.artifact_id,
        expected_artifact_id
    );
    assert_eq!(
        written
            .artifacts
            .iter()
            .find(|artifact| artifact.coordinate.as_deref() == Some(coordinate))
            .and_then(|artifact| artifact.owner.as_ref())
            .map(|owner| (owner.dependency_id.as_str(), owner.component_id.as_str())),
        Some(("javaparser", "main"))
    );
}

#[test]
fn add_rejects_dynamic_and_malformed_coordinates() {
    let coordinate = MavenCoordinate::parse("example:mod:1.+").expect("coordinate shape");
    assert!(coordinate.require_exact().is_err());
    assert!(MavenCoordinate::parse("example:mod").is_err());
}

#[test]
fn bundle_cli_options_require_scope_and_capture_explicit_policy() {
    let coordinate = MavenCoordinate::parse("org.facet:vox-java:0.1.3").expect("coordinate");
    let mut dependency_args = args(&coordinate.canonical);
    dependency_args.kind = Some(DependencyKindV3::Library);
    dependency_args.artifact_treatment = Some(ArtifactTreatmentV3::Plain);
    dependency_args.scope = vec![
        DependencyScopeV3::Compile,
        DependencyScopeV3::Runtime,
        DependencyScopeV3::Bundle,
    ];
    let error =
        validate_bundle_options(&dependency_args, &coordinate).expect_err("range is required");
    assert!(error.to_string().contains("bundle-accepted-version-range"));

    dependency_args.bundle_accepted_version_range = Some("[0.1.0,0.2.0)".to_string());
    validate_bundle_options(&dependency_args, &coordinate).expect("bundle options");
    let policy = bundle_policy(&dependency_args, &coordinate).expect("bundle policy");
    assert_eq!(policy.accepted_version_range, "[0.1.0,0.2.0)");
    assert_eq!(policy.artifact_version, "0.1.3");
    assert!(!policy.is_obfuscated);

    dependency_args
        .scope
        .retain(|scope| *scope != DependencyScopeV3::Bundle);
    let error =
        validate_bundle_options(&dependency_args, &coordinate).expect_err("policy without scope");
    assert!(error.to_string().contains("require --scope bundle"));
}

#[test]
fn add_curseforge_locks_validated_exact_project_and_file() {
    let directory = tempfile::tempdir().expect("temp directory");
    let cache_home = CacheHome(directory.path().join("isolated-cache"));
    let mut lockfile = read_current(include_str!(
        "../../../../../minecraft/sfm-toolchain.lock.json"
    ))
    .expect("v3 fixture");
    lockfile
        .dependencies
        .retain(|dependency| dependency.id != "mekanism");
    lockfile.artifacts.retain(|artifact| {
        artifact
            .owner
            .as_ref()
            .is_none_or(|owner| owner.dependency_id != "mekanism")
    });
    let input = lockfile.to_canonical_json().expect("fixture JSON");
    let lockfile_path = directory.path().join("sfm-toolchain.lock.json");
    std::fs::write(&lockfile_path, &input).expect("fixture lockfile");
    let inventory = DependencyInventory {
        target: WorktreeTarget {
            branch: BranchName::from("1.19.2"),
            worktree_path: WorktreePath::from(directory.path().to_path_buf()),
            core: true,
            mc_version: None,
        },
        lockfile_path: lockfile_path.clone(),
        cache_home: cache_home.clone(),
        original_input: input,
        lockfile,
    };
    let project_id = CurseforgeProjectId(268_560);
    let file_id = CurseforgeProjectFileId(4_644_795);
    let metadata = FixtureCurseforgeMetadata {
        project: CurseforgeMod {
            id: project_id,
            name: "Mekanism".to_owned(),
            slug: Some("mekanism".to_owned()),
            summary: None,
            download_count: 0,
            date_modified: None,
            date_released: None,
            game_popularity_rank: None,
        },
        file: mekanism_file(),
    };
    let expected_url = concat!(
        "https://www.cursemaven.com/curse/maven/mekanism-268560/4644795/",
        "mekanism-268560-4644795.jar"
    );
    let bytes = b"isolated Mekanism artifact".to_vec();
    let fetcher = FixtureFetcher {
        expected_url: expected_url.to_owned(),
        bytes: bytes.clone(),
    };

    let report = add_curseforge_dependency(
        inventory,
        &curseforge_args(),
        project_id,
        file_id,
        &metadata,
        &CancellationToken::new(),
        &fetcher,
    )
    .expect("CurseForge dependency add");

    assert_eq!(report.coordinate, "curse.maven:mekanism-268560:4644795");
    assert_eq!(report.repository_id, "cursemaven");
    assert_curseforge_lock_written(&lockfile_path, &cache_home, expected_url, &bytes);
}

fn assert_curseforge_lock_written(
    lockfile_path: &std::path::Path,
    cache_home: &CacheHome,
    expected_url: &str,
    bytes: &[u8],
) {
    let written =
        read_current(&std::fs::read_to_string(lockfile_path).expect("updated lockfile read"))
            .expect("updated v3 lockfile");
    let dependency = written
        .dependencies
        .iter()
        .find(|dependency| dependency.id == "mekanism")
        .expect("Mekanism declaration");
    let component = dependency.components.first().expect("main component");
    assert!(matches!(
        &component.declaration.acquisition,
        ComponentAcquisitionV3::CurseForge(CurseForgeAcquisitionV3 {
            project_id: 268_560,
            file_id: 4_644_795,
            slug,
            repository_id,
        }) if slug == "mekanism" && repository_id == "cursemaven"
    ));
    assert_eq!(
        component.derived_checks.resolved_coordinate.as_deref(),
        Some("curse.maven:mekanism-268560:4644795")
    );
    let artifact = written
        .artifacts
        .iter()
        .find(|artifact| artifact.id == component.derived_checks.artifact_id)
        .expect("owned artifact");
    assert_eq!(artifact.url.as_deref(), Some(expected_url));
    assert_eq!(artifact.repository_id.as_deref(), Some("cursemaven"));
    let local_path = cache_home.join("minecraft-toolchain").join(
        component
            .derived_checks
            .cache_path
            .strip_prefix("$sfm-cache")
            .expect("portable path"),
    );
    assert_eq!(std::fs::read(local_path).expect("cached bytes"), bytes);
}

#[test]
fn dependency_add_requires_one_complete_source_declaration() {
    let mut no_source = args("example:mod:1.0.0");
    no_source.maven = None;
    let _ = selected_add_source(&no_source).expect_err("source declaration should be required");

    let mut both_sources = args("example:mod:1.0.0");
    both_sources.curseforge_project = Some(268_560);
    both_sources.curseforge_file = Some(4_644_795);
    let _ = selected_add_source(&both_sources)
        .expect_err("only one source declaration should be allowed");

    let mut incomplete_curseforge = no_source;
    incomplete_curseforge.curseforge_project = Some(268_560);
    let _ = selected_add_source(&incomplete_curseforge)
        .expect_err("both exact CurseForge IDs should be required");
}

#[test]
fn exact_curseforge_file_validation_rejects_wrong_owner_version_and_loader() {
    let project_id = CurseforgeProjectId(268_560);
    let file_id = CurseforgeProjectFileId(4_644_795);
    let mut file = mekanism_file();
    file.mod_id = Some(CurseforgeProjectId(1));
    let error = validate_curseforge_file(
        &file,
        project_id,
        file_id,
        "1.19.2",
        CurseforgeModLoader::Forge,
    )
    .expect_err("wrong project must be rejected");
    assert!(error.to_string().contains("does not belong"));

    let mut file = mekanism_file();
    file.game_versions = vec!["1.19.1".to_owned()];
    let error = validate_curseforge_file(
        &file,
        project_id,
        file_id,
        "1.19.2",
        CurseforgeModLoader::Forge,
    )
    .expect_err("wrong Minecraft version must be rejected");
    assert!(error.to_string().contains("does not support Minecraft"));

    let mut file = mekanism_file();
    file.sortable_game_versions[0].mod_loader = Some(CurseforgeModLoader::Neoforge.api_value());
    let error = validate_curseforge_file(
        &file,
        project_id,
        file_id,
        "1.19.2",
        CurseforgeModLoader::Forge,
    )
    .expect_err("wrong loader must be rejected");
    assert!(error.to_string().contains("does not support loader"));
}

fn args(coordinate: &str) -> DependencyAddArgs {
    DependencyAddArgs {
        id: "cc-tweaked".to_owned(),
        branch: BranchSelector::from("1.19.2".to_owned()),
        maven: Some(coordinate.to_owned()),
        curseforge_project: None,
        curseforge_file: None,
        kind: None,
        role: None,
        curseforge_api_key: None,
        curseforge_token: None,
        curseforge_op_secret: None,
        scope: vec![
            DependencyScopeV3::Runtime,
            DependencyScopeV3::Compile,
            DependencyScopeV3::GametestRuntime,
            DependencyScopeV3::GametestCompile,
        ],
        repository: Some("squiddev".to_owned()),
        artifact_treatment: None,
        bundle_accepted_version_range: None,
        bundle_artifact_version: None,
        bundle_is_obfuscated: false,
        display_name: Some("CC:Tweaked".to_owned()),
        project_url: Some("https://tweaked.cc".to_owned()),
        notes: None,
    }
}

fn curseforge_args() -> DependencyAddArgs {
    DependencyAddArgs {
        id: "mekanism".to_owned(),
        branch: BranchSelector::from("1.19.2".to_owned()),
        maven: None,
        curseforge_project: Some(268_560),
        curseforge_file: Some(4_644_795),
        kind: None,
        role: None,
        curseforge_api_key: None,
        curseforge_token: None,
        curseforge_op_secret: None,
        scope: vec![DependencyScopeV3::Compile, DependencyScopeV3::Runtime],
        repository: Some("cursemaven".to_owned()),
        artifact_treatment: Some(ArtifactTreatmentV3::LoaderManagedMod),
        bundle_accepted_version_range: None,
        bundle_artifact_version: None,
        bundle_is_obfuscated: false,
        display_name: None,
        project_url: None,
        notes: None,
    }
}

fn mekanism_file() -> CurseforgeProjectFileItem {
    CurseforgeProjectFileItem {
        id: CurseforgeProjectFileId(4_644_795),
        mod_id: Some(CurseforgeProjectId(268_560)),
        file_name: Some("Mekanism-1.19.2-10.3.9.13.jar".to_owned()),
        display_name: None,
        release_type: Some(1),
        file_status: Some(4),
        game_versions: vec!["1.19.2".to_owned(), "Forge".to_owned()],
        sortable_game_versions: vec![CurseforgeSortableGameVersion {
            game_version: Some("1.19.2".to_owned()),
            mod_loader: Some(CurseforgeModLoader::Forge.api_value()),
        }],
        download_url: None,
        download_count: 0,
        file_date: None,
        hashes: Vec::new(),
    }
}
