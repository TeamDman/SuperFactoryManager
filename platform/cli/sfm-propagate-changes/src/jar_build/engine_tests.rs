use super::ArtifactLockEntry;
use super::ArtifactLockfile;
use super::ArtifactPlan;
use super::ArtifactProvenance;
use super::ArtifactSource;
use super::BuildMode;
use super::BuildOptions;
use super::BuildPlan;
use super::ChangedEntry;
use super::DependencyLockEntry;
use super::DependencyPlan;
use super::DependencySource;
use super::ForgeUserdevConfig;
use super::GraphNode;
use super::JarCompareReport;
use super::JavaPlan;
use super::LoaderToolchainKind;
use super::LoaderToolchainPlan;
use super::ManifestCompare;
use super::MavenCoordinate;
use super::McpConfigJson;
use super::McpConfigPlan;
use super::MinecraftPlan;
use super::MinecraftVersionJson;
use super::MojangVersionManifest;
use super::NodeStatus;
use super::ParchmentData;
use super::Repository;
use super::compare_version_text;
use super::copy_file_to_path_checked;
use super::execute_targets_parallel;
use super::extract_quoted;
use super::file_sha1;
use super::interpolate_properties;
use super::is_excluded_source;
use super::normalize_manifest_bytes;
use super::parchment_coordinate;
use super::parse_maven_versions;
use super::portable_cache_path;
use super::prepare_existing_artifact_for_reuse;
use super::resolve_loader_toolchain;
use super::rust_output_jar_path;
use super::set_minecraft_option;
use super::sha1_bytes;
use super::should_keep_split_minecraft_runtime_entry;
use super::write_unique_temp_file;
use crate::branch_targets::BranchName;
use crate::branch_targets::BranchQuery;
use crate::branch_targets::MinecraftVersion;
use crate::branch_targets::WorktreePath;
use crate::branch_targets::WorktreeTarget;
use crate::jar_build::ErrorAction;
use crate::jar_build::Parallelism;
use std::cmp::Ordering;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fs;
use std::path::Path;
use std::path::PathBuf;
use std::sync::atomic::AtomicUsize;
use std::sync::atomic::Ordering as AtomicOrdering;
use std::thread;
use std::time::Duration;

static TEST_DIR_COUNTER: AtomicUsize = AtomicUsize::new(0);

#[test]
fn parses_classifier_coordinate() {
    let coordinate = MavenCoordinate::parse("net.minecraftforge:forge:1.19.2-43.4.0:userdev")
        .expect("coordinate should parse");
    assert_eq!(
        coordinate.to_string(),
        "net.minecraftforge:forge:1.19.2-43.4.0:userdev"
    );
    assert_eq!(coordinate.file_name(), "forge-1.19.2-43.4.0-userdev.jar");
}

#[test]
fn parses_zip_coordinate() {
    let coordinate =
        MavenCoordinate::parse("de.oceanlabs.mcp:mcp_config:1.19.2-20220805.130853@zip")
            .expect("coordinate should parse");
    assert_eq!(
        coordinate.file_name(),
        "mcp_config-1.19.2-20220805.130853.zip"
    );
}

#[test]
fn parses_parchment_date_first_and_mc_first_versions() {
    assert_eq!(
        parchment_coordinate("2022.11.27-1.19.2")
            .expect("date-first parchment coordinate should parse")
            .to_string(),
        "org.parchmentmc.data:parchment-1.19.2:2022.11.27@zip"
    );
    assert_eq!(
        parchment_coordinate("1.19.3-2023.03.12-1.19.4")
            .expect("mc-first parchment coordinate should parse")
            .to_string(),
        "org.parchmentmc.data:parchment-1.19.3:2023.03.12@zip"
    );
}

#[test]
fn extracts_single_or_double_quoted_notation() {
    assert_eq!(
        extract_quoted("fg.deobf('mezz.jei:jei-1.19.2-forge:11.6.0.1018')"),
        Some("mezz.jei:jei-1.19.2-forge:11.6.0.1018".to_string())
    );
    assert_eq!(
        extract_quoted("antlr \"org.antlr:antlr4:4.9.1\""),
        Some("org.antlr:antlr4:4.9.1".to_string())
    );
}

#[test]
fn set_minecraft_option_replaces_or_appends_option() {
    let existing = "version:3700\nonboardAccessibility:false\nnarrator:0\n";
    assert_eq!(
        set_minecraft_option(existing, "onboardAccessibility", "true"),
        "version:3700\nonboardAccessibility:true\nnarrator:0\n"
    );
    assert_eq!(
        set_minecraft_option("version:3700\n", "onboardAccessibility", "true"),
        "version:3700\nonboardAccessibility:true\n"
    );
}

#[test]
fn split_minecraft_runtime_keeps_duplicate_vanilla_resources() {
    let neoforge_entries = BTreeSet::from([
        "assets/minecraft/atlases/blocks.json".to_string(),
        "assets/minecraft/atlases/items.json".to_string(),
        "net/minecraft/client/Minecraft.class".to_string(),
        "net/neoforged/neoforge/NeoForge.class".to_string(),
        "META-INF/services/example.Service".to_string(),
    ]);

    assert!(should_keep_split_minecraft_runtime_entry(
        "assets/minecraft/atlases/blocks.json",
        &neoforge_entries
    ));
    assert!(should_keep_split_minecraft_runtime_entry(
        "assets/minecraft/atlases/items.json",
        &neoforge_entries
    ));
    assert!(!should_keep_split_minecraft_runtime_entry(
        "net/minecraft/client/Minecraft.class",
        &neoforge_entries
    ));
    assert!(!should_keep_split_minecraft_runtime_entry(
        "net/neoforged/neoforge/NeoForge.class",
        &neoforge_entries
    ));
    assert!(!should_keep_split_minecraft_runtime_entry(
        "META-INF/services/example.Service",
        &neoforge_entries
    ));
}

#[test]
fn source_excludes_match_files_and_directories() {
    let excludes = vec![
        "ca/teamdman/sfm/common/compat/SFMMekanismCompat.java".to_string(),
        "ca/teamdman/sfm/common/program/linting/compat/mekanism".to_string(),
        "ca/teamdman/sfm/generated/**".to_string(),
    ];

    assert!(is_excluded_source(
        "ca/teamdman/sfm/common/compat/SFMMekanismCompat.java",
        &excludes
    ));
    assert!(is_excluded_source(
        "ca/teamdman/sfm/common/program/linting/compat/mekanism/MekanismSidednessProgramLinter.java",
        &excludes
    ));
    assert!(is_excluded_source(
        "ca/teamdman/sfm/generated/Generated.java",
        &excludes
    ));
    assert!(!is_excluded_source(
        "ca/teamdman/sfm/common/program/linting/compat/other/Other.java",
        &excludes
    ));
}

#[test]
fn detects_loader_toolchain_from_versioned_dependencies() {
    let forge = vec![super::ParsedDependency {
        configuration: "minecraft".to_string(),
        coordinate: MavenCoordinate::parse("net.minecraftforge:forge:1.19.2-43.4.0")
            .expect("coordinate should parse"),
        fg_deobf: false,
    }];
    let forge_plan = resolve_loader_toolchain(&forge, "1.19.2", "43.4.0")
        .expect("forge toolchain should resolve");
    assert_eq!(forge_plan.kind, LoaderToolchainKind::ForgeGradleForge);
    assert_eq!(
        forge_plan.userdev_coordinate,
        "net.minecraftforge:forge:1.19.2-43.4.0:userdev"
    );

    let transitional_neoforge = vec![super::ParsedDependency {
        configuration: "minecraft".to_string(),
        coordinate: MavenCoordinate::parse("net.neoforged:forge:1.20.1-47.1.65")
            .expect("coordinate should parse"),
        fg_deobf: false,
    }];
    let transitional_plan = resolve_loader_toolchain(&transitional_neoforge, "1.20.1", "47.1.65")
        .expect("transitional neoforge toolchain should resolve");
    assert_eq!(
        transitional_plan.kind,
        LoaderToolchainKind::ForgeGradleNeoForgeGroup
    );
    assert_eq!(
        transitional_plan.userdev_coordinate,
        "net.neoforged:forge:1.20.1-47.1.65:userdev"
    );

    let neogradle = vec![super::ParsedDependency {
        configuration: "implementation".to_string(),
        coordinate: MavenCoordinate::parse("net.neoforged:neoforge:20.2.86")
            .expect("coordinate should parse"),
        fg_deobf: false,
    }];
    let neogradle_plan = resolve_loader_toolchain(&neogradle, "1.20.2", "20.2.86")
        .expect("neogradle toolchain should resolve");
    assert_eq!(neogradle_plan.kind, LoaderToolchainKind::NeoGradleUserdev);
    assert_eq!(
        neogradle_plan.userdev_coordinate,
        "net.neoforged:neoforge:20.2.86:userdev"
    );
}

#[test]
fn interpolates_gradle_style_properties() {
    let mut properties = BTreeMap::new();
    properties.insert("minecraft_version".to_string(), "1.19.2".to_string());
    properties.insert("neo_version".to_string(), "43.4.0".to_string());
    assert_eq!(
        interpolate_properties(
            "net.minecraftforge:forge:${minecraft_version}-${neo_version}",
            &properties
        ),
        "net.minecraftforge:forge:1.19.2-43.4.0"
    );
}

#[test]
fn parses_maven_metadata_versions() {
    let versions = parse_maven_versions(
        "<metadata><versioning><versions><version>1.0.0</version><version>1.0.1</version></versions></versioning></metadata>",
    );
    assert_eq!(versions, vec!["1.0.0", "1.0.1"]);
}

#[test]
fn compares_numeric_version_segments() {
    assert_eq!(compare_version_text("1.2.10", "1.2.9"), Ordering::Greater);
}

#[test]
fn rust_output_jar_uses_rust_suffix() {
    let path = rust_output_jar_path(
        Path::new("platform/minecraft"),
        "Super Factory Manager (SFM)",
        "1.19.2",
        "4.33.0",
    );
    assert_eq!(
        path,
        Path::new(
            "platform/minecraft/build/libs/Super Factory Manager (SFM)-MC1.19.2-4.33.0-rust.jar"
        )
    );
}

#[test]
fn manifest_normalization_ignores_timestamp_by_default() {
    let left = b"Manifest-Version: 1.0\r\nImplementation-Timestamp: 2026-01-01T00:00:00-0400\r\nMixinConfigs: sfm.mixins.json\r\n";
    let right = b"Manifest-Version: 1.0\r\nImplementation-Timestamp: 2026-02-02T00:00:00-0400\r\nMixinConfigs: sfm.mixins.json\r\n";
    assert_eq!(
        normalize_manifest_bytes(left, false),
        normalize_manifest_bytes(right, false)
    );
    assert_ne!(
        normalize_manifest_bytes(left, true),
        normalize_manifest_bytes(right, true)
    );
}

#[test]
fn facet_json_roundtrips_artifact_lockfile_and_provenance() {
    let provenance = minimal_provenance();
    let provenance_json =
        facet_json::to_string_pretty(&provenance).expect("provenance should serialize");
    assert!(provenance_json.contains("remote-maven"));
    let parsed_provenance: ArtifactProvenance =
        facet_json::from_str(&provenance_json).expect("provenance should parse");
    assert_eq!(parsed_provenance.sha1, "abc123");

    let lockfile = ArtifactLockfile {
        schema_version: 1,
        minecraft_version: "1.19.2".to_string(),
        maven_cache_dir: PathBuf::from("build/sfm-toolchain/maven"),
        allow_local_artifact_cache: false,
        repositories: vec![Repository {
            name: "Forge".to_string(),
            url: "https://maven.minecraftforge.net".to_string(),
        }],
        dependencies: vec![DependencyLockEntry {
            configuration: "implementation".to_string(),
            notation: "curse.maven:example-1:2.0".to_string(),
            resolved_notation: "curse.maven:example-1:2.0".to_string(),
            source: DependencySource::CurseMaven,
            dynamic_version: false,
            cache_path: PathBuf::from("artifact.jar"),
        }],
        artifacts: vec![ArtifactLockEntry {
            coordinate: Some("g:a:1".to_string()),
            source: ArtifactSource::RemoteMaven,
            repository: Some("Forge".to_string()),
            url: Some("https://example.test/a.jar".to_string()),
            cache_path: PathBuf::from("a.jar"),
            original_path: None,
            sha1: "abc123".to_string(),
        }],
    };
    let json = facet_json::to_string_pretty(&lockfile).expect("lockfile should serialize");
    assert!(json.contains("remote-maven"));
    let parsed: ArtifactLockfile = facet_json::from_str(&json).expect("lockfile should parse");
    assert_eq!(parsed.artifacts[0].source, ArtifactSource::RemoteMaven);
}

#[test]
fn prepare_existing_artifact_quarantines_wrong_sha1() {
    let test_dir = TestDir::new("prepare-existing-artifact");
    let artifact = test_dir.path.join("artifact.jar");
    fs::write(&artifact, b"bad").expect("artifact should be written");

    let expected_sha1 = sha1_bytes(b"good");
    prepare_existing_artifact_for_reuse(&artifact, Some(&expected_sha1))
        .expect("corrupt artifact should be quarantined");

    assert!(!artifact.exists());
    let bad_entries = matching_siblings(&artifact, "bad");
    assert_eq!(bad_entries.len(), 1);
    assert_eq!(
        fs::read(&bad_entries[0]).expect("bad artifact should remain readable"),
        b"bad"
    );
}

#[test]
fn copy_file_to_path_checked_skips_existing_valid_artifact() {
    let test_dir = TestDir::new("copy-file-skips-existing-valid");
    let source = test_dir.path.join("source.jar");
    let destination = test_dir.path.join("artifact.jar");
    fs::write(&source, b"bad").expect("source should be written");
    fs::write(&destination, b"good").expect("destination should be written");

    let expected_sha1 = sha1_bytes(b"good");
    copy_file_to_path_checked(&source, &destination, Some(&expected_sha1))
        .expect("valid destination should skip copying the bad source");

    assert_eq!(
        fs::read(&destination).expect("destination should remain readable"),
        b"good"
    );
    assert_eq!(
        file_sha1(&destination).expect("destination should hash"),
        expected_sha1
    );
    assert!(matching_siblings(&destination, "tmp").is_empty());
}

#[test]
fn copy_file_to_path_checked_rejects_temp_sha1_failure() {
    let test_dir = TestDir::new("copy-file-temp-sha1-failure");
    let source = test_dir.path.join("source.jar");
    let destination = test_dir.path.join("artifact.jar");
    fs::write(&source, b"bad").expect("source should be written");

    let expected_sha1 = sha1_bytes(b"good");
    let error = copy_file_to_path_checked(&source, &destination, Some(&expected_sha1))
        .expect_err("bad source should fail expected SHA-1 validation");

    assert!(
        error.to_string().contains("Copied local artifact"),
        "{error:?}"
    );
    assert!(!destination.exists());
    assert!(matching_siblings(&destination, "tmp").is_empty());
}

#[test]
fn copy_file_to_path_checked_replaces_bad_final_artifact_and_cleans_bad_file() {
    let test_dir = TestDir::new("copy-file-replaces-bad-final");
    let source = test_dir.path.join("source.jar");
    let destination = test_dir.path.join("artifact.jar");
    fs::write(&source, b"good").expect("source should be written");
    fs::write(&destination, b"bad").expect("destination should be written");

    let expected_sha1 = sha1_bytes(b"good");
    copy_file_to_path_checked(&source, &destination, Some(&expected_sha1))
        .expect("good source should replace corrupt artifact");

    assert_eq!(
        fs::read(&destination).expect("destination should remain readable"),
        b"good"
    );
    assert!(matching_siblings(&destination, "bad").is_empty());
}

#[test]
fn write_unique_temp_file_uses_artifact_sibling() {
    let test_dir = TestDir::new("write-unique-temp-file");
    let artifact = test_dir.path.join("artifact.jar");

    let first = write_unique_temp_file(&artifact, b"first").expect("first temp should be written");
    let second =
        write_unique_temp_file(&artifact, b"second").expect("second temp should be written");

    assert_ne!(first, second);
    assert_eq!(first.parent(), artifact.parent());
    assert_eq!(second.parent(), artifact.parent());
    assert_eq!(fs::read(first).expect("first temp should read"), b"first");
    assert_eq!(
        fs::read(second).expect("second temp should read"),
        b"second"
    );
}

#[test]
fn parallel_targets_return_plans_in_input_order() {
    let options = test_build_options(Parallelism::Parallel { limit: 2 });
    let targets = vec![
        test_worktree_target("1.19.2", "D:/tmp/1.19.2"),
        test_worktree_target("1.20.1", "D:/tmp/1.20.1"),
    ];

    let summary = execute_targets_parallel(
        &options,
        targets,
        "test_parallel_targets",
        2,
        |_options, target| {
            if target.branch.as_ref() == "1.19.2" {
                thread::sleep(Duration::from_millis(25));
            }
            let mut plan = minimal_plan_for_paths();
            plan.branch_name = target.branch.clone();
            Ok(plan)
        },
    )
    .expect("parallel execution should succeed");

    let branches = summary
        .plans
        .iter()
        .map(|plan| plan.branch_name.as_ref())
        .collect::<Vec<_>>();
    assert_eq!(branches, vec!["1.19.2", "1.20.1"]);
}

#[test]
#[expect(
    clippy::too_many_lines,
    reason = "BuildPlan JSON fixture is intentionally explicit"
)]
fn facet_json_serializes_plan_without_embedded_lockfile() {
    let artifact = minimal_artifact();
    let plan = BuildPlan {
        schema_version: 1,
        mode: "plan".to_string(),
        branch_name: BranchName::from("1.19.2"),
        minecraft_version: MinecraftVersion::parse("1.19.2").expect("version should parse"),
        worktree_path: PathBuf::from("D:/Repos/Minecraft/SFM/repos2/1.19.2"),
        minecraft_dir: PathBuf::from("platform/minecraft"),
        gradle_output_jar: PathBuf::from("build/libs/sfm.jar"),
        rust_output_jar: PathBuf::from("build/libs/sfm-rust.jar"),
        cache_dir: PathBuf::from("build/sfm-toolchain"),
        common_cache_dir: PathBuf::from("sfm-cache/minecraft-toolchain"),
        state_dir: PathBuf::from("build/sfm-toolchain/state"),
        maven_cache_dir: PathBuf::from("sfm-cache/minecraft-toolchain/maven"),
        minecraft_cache_dir: PathBuf::from("sfm-cache/minecraft-toolchain/minecraft"),
        minecraft_version_cache_dir: PathBuf::from(
            "sfm-cache/minecraft-toolchain/minecraft/versions/1.19.2",
        ),
        minecraft_assets_dir: PathBuf::from("sfm-cache/minecraft-toolchain/minecraft/assets"),
        minecraft_libraries_dir: PathBuf::from("sfm-cache/minecraft-toolchain/minecraft/libraries"),
        lockfile_path: PathBuf::from("sfm-toolchain.lock.json"),
        lockfile: Some(ArtifactLockfile {
            schema_version: 1,
            minecraft_version: "1.19.2".to_string(),
            maven_cache_dir: PathBuf::from("build/sfm-toolchain/maven"),
            allow_local_artifact_cache: false,
            repositories: Vec::new(),
            dependencies: Vec::new(),
            artifacts: Vec::new(),
        }),
        java: JavaPlan {
            executable: PathBuf::from("java"),
            home: None,
            version_output: "openjdk version \"17\"".to_string(),
            major_version: 17,
        },
        java_release: 17,
        refresh: false,
        allow_local_artifact_cache: false,
        properties: BTreeMap::new(),
        repositories: Vec::new(),
        loader_toolchain: LoaderToolchainPlan {
            kind: LoaderToolchainKind::ForgeGradleForge,
            base_coordinate: "net.minecraftforge:forge:1.19.2-43.4.0".to_string(),
            userdev_coordinate: "net.minecraftforge:forge:1.19.2-43.4.0:userdev".to_string(),
            sources_coordinate: Some("net.minecraftforge:forge:1.19.2-43.4.0:sources".to_string()),
            universal_coordinate: Some(
                "net.minecraftforge:forge:1.19.2-43.4.0:universal".to_string(),
            ),
        },
        artifacts: vec![artifact.clone()],
        minecraft: MinecraftPlan {
            version_manifest: artifact.clone(),
            version_json: artifact.clone(),
            client_jar_url: "https://example.test/client.jar".to_string(),
            server_jar_url: "https://example.test/server.jar".to_string(),
            client_mappings_url: Some("https://example.test/client.txt".to_string()),
            server_mappings_url: Some("https://example.test/server.txt".to_string()),
            libraries_count: 0,
        },
        forge_userdev: Some(super::ForgeUserdevPlan {
            artifact: artifact.clone(),
            spec: Some(1),
            mcp: Some("de.oceanlabs.mcp:mcp_config:1@zip".to_string()),
            neo_form: None,
            sources: None,
            universal: None,
            binpatcher: None,
            patches: None,
            patches_original_prefix: None,
            patches_modified_prefix: None,
            access_transformers: Vec::new(),
            side_strippers: Vec::new(),
            module_count: 0,
            library_count: 0,
            test_libraries: Vec::new(),
            run_configs: Vec::new(),
        }),
        mcp_config: Some(McpConfigPlan {
            artifact,
            joined_steps: vec!["downloadManifest".to_string()],
            function_coordinates: BTreeMap::new(),
            function_count: 0,
            data_keys: Vec::new(),
            library_count: 0,
        }),
        dependencies: vec![DependencyPlan {
            configuration: "implementation".to_string(),
            notation: "g:a:1".to_string(),
            resolved_notation: "g:a:1".to_string(),
            source: DependencySource::Maven,
            cache_path: PathBuf::from("a.jar"),
            url: None,
            dynamic_version: false,
        }],
        graph: vec![GraphNode {
            id: "resolve-project-config".to_string(),
            kind: "planning".to_string(),
            status: NodeStatus::Ready,
            inputs: Vec::new(),
            outputs: Vec::new(),
            rebuild_reason: "test".to_string(),
        }],
        warnings: Vec::new(),
    };

    let json = facet_json::to_string_pretty(&plan).expect("plan should serialize");
    assert!(json.contains("rust_output_jar"));
    assert!(json.contains("common_cache_dir"));
    assert!(!json.contains("\"lockfile\""));
}

#[test]
fn portable_cache_path_uses_sfm_cache_prefix_for_common_cache() {
    let plan = minimal_plan_for_paths();
    let common_artifact = PathBuf::from("D:/sfm-cache/minecraft-toolchain/maven/g/a/1/a.jar");
    let local_artifact = PathBuf::from(
        "D:/Repos/Minecraft/SFM/repos2/1.19.2/platform/minecraft/build/sfm-toolchain/project/a.jar",
    );

    assert_eq!(
        portable_cache_path(&plan, &common_artifact),
        PathBuf::from("$sfm-cache/maven/g/a/1/a.jar")
    );
    assert_eq!(
        portable_cache_path(&plan, &local_artifact),
        PathBuf::from("build/sfm-toolchain/project/a.jar")
    );
}

#[test]
fn facet_json_serializes_compare_report() {
    let report = JarCompareReport {
        gradle_jar: PathBuf::from("gradle.jar"),
        rust_jar: PathBuf::from("rust.jar"),
        strict_manifest: false,
        matches: false,
        total_gradle_entries: 1,
        total_rust_entries: 1,
        compared_entries: 1,
        missing_entries: vec!["a.class".to_string()],
        extra_entries: Vec::new(),
        changed_entries: vec![ChangedEntry {
            path: "b.class".to_string(),
            gradle_sha1: "1".to_string(),
            rust_sha1: "2".to_string(),
        }],
        manifest: ManifestCompare {
            compared: true,
            changed: false,
            ignored_implementation_timestamp: true,
            gradle_sha1: Some("1".to_string()),
            rust_sha1: Some("1".to_string()),
        },
    };
    let json = facet_json::to_string_pretty(&report).expect("report should serialize");
    assert!(json.contains("missing_entries"));
    assert!(json.contains("ignored_implementation_timestamp"));
}

#[test]
fn facet_json_parses_upstream_config_shapes() {
    let manifest: MojangVersionManifest = facet_json::from_str(
        r#"{"versions":[{"id":"1.19.2","url":"https://example.test/1.19.2.json"}]}"#,
    )
    .expect("manifest should parse");
    assert_eq!(manifest.versions[0].id, "1.19.2");

    let version_json: MinecraftVersionJson = facet_json::from_str(
            r#"{
                "downloads": {
                    "client": {"url": "https://example.test/client.jar"},
                    "server": {"url": "https://example.test/server.jar"},
                    "client_mappings": {"url": "https://example.test/client.txt"},
                    "server_mappings": {"url": "https://example.test/server.txt"}
                },
                "assetIndex": {"id": "1.19", "url": "https://example.test/assets.json"},
                "libraries": [{"downloads": {"artifact": {"url": "https://example.test/lib.jar", "path": "g/a/1/a.jar"}}}]
            }"#,
        )
        .expect("version json should parse");
    assert_eq!(version_json.libraries.len(), 1);
    assert_eq!(version_json.asset_index.expect("asset index").id, "1.19");

    let forge: ForgeUserdevConfig = facet_json::from_str(
        r#"{
                "spec": 1,
                "mcp": "de.oceanlabs.mcp:mcp_config:1.19.2@zip",
                "binpatcher": {"version": "net.minecraftforge:binarypatcher:1"},
                "patchesOriginalPrefix": "a/",
                "patchesModifiedPrefix": "b/",
                "ats": ["ats/accesstransformer.cfg"],
                "sass": ["sas.cfg"],
                "modules": ["g:module:1"],
                "libraries": ["g:lib:1"],
                "runs": {
                    "client": {
                        "main": "cpw.mods.bootstraplauncher.BootstrapLauncher",
                        "args": ["--launchTarget", "forgeclientuserdev"],
                        "jvmArgs": ["-Dexample=true"],
                        "env": {"MOD_CLASSES": "{source_roots}"},
                        "props": {"mixin.env.remapRefMap": "true"}
                    }
                }
            }"#,
    )
    .expect("forge userdev config should parse");
    assert_eq!(forge.runs["client"].jvm_args, vec!["-Dexample=true"]);
    assert_eq!(
        forge.ats.expect("ats should parse").into_vec(),
        vec!["ats/accesstransformer.cfg".to_string()]
    );

    let neoforge: ForgeUserdevConfig = facet_json::from_str(
        r#"{
                "spec": 2,
                "mcp": "net.neoforged:neoform:1.20.2-20231019.002635@zip",
                "ats": "ats/",
                "sass": "sas.cfg",
                "sources": "net.neoforged:neoforge:20.2.86:sources",
                "universal": "net.neoforged:neoforge:20.2.86:universal"
            }"#,
    )
    .expect("neoforge userdev config should parse scalar lists");
    assert_eq!(
        neoforge.ats.expect("ats should parse").into_vec(),
        vec!["ats/".to_string()]
    );
    assert_eq!(
        neoforge.sass.expect("sass should parse").into_vec(),
        vec!["sas.cfg".to_string()]
    );

    let mcp: McpConfigJson = facet_json::from_str(
            r#"{
                "data": {"mappings": "config/joined.tsrg", "inject": "config/inject/", "patches": {"joined": "patches/joined/"}},
                "steps": {"joined": [{"type": "downloadManifest"}, {"name": "extractServer", "type": "bundleExtractJar"}]},
                "functions": {"rename": {"version": "net.minecraftforge:ForgeAutoRenamingTool:0.1.22:all", "args": ["--input", "{input}"], "jvmargs": []}},
                "libraries": {"joined": ["g:lib:1"]}
            }"#,
        )
        .expect("mcp config should parse");
    assert_eq!(mcp.steps.joined[1].name.as_deref(), Some("extractServer"));
    assert_eq!(mcp.functions.len(), 1);
    assert_eq!(
        mcp.functions["rename"].version.as_deref(),
        Some("net.minecraftforge:ForgeAutoRenamingTool:0.1.22:all")
    );

    let parchment: ParchmentData = facet_json::from_str(
            r#"{"classes":[{"name":"net/minecraft/Test","methods":[{"name":"run","descriptor":"()V","parameters":[{"index":1,"name":"level"}]}]}]}"#,
        )
        .expect("parchment should parse");
    assert_eq!(parchment.classes[0].methods[0].parameters[0].name, "level");
}

fn minimal_artifact() -> ArtifactPlan {
    ArtifactPlan {
        id: "artifact".to_string(),
        coordinate: Some("g:a:1".to_string()),
        repository: Some("Forge".to_string()),
        url: Some("https://example.test/a.jar".to_string()),
        cache_path: PathBuf::from("a.jar"),
        sha1: Some("abc123".to_string()),
        downloaded: true,
        required_for: "test".to_string(),
        provenance: minimal_provenance(),
    }
}

fn minimal_provenance() -> ArtifactProvenance {
    ArtifactProvenance {
        schema_version: 1,
        source: ArtifactSource::RemoteMaven,
        coordinate: Some("g:a:1".to_string()),
        repository: Some("Forge".to_string()),
        url: Some("https://example.test/a.jar".to_string()),
        original_path: None,
        sha1: "abc123".to_string(),
    }
}

fn minimal_plan_for_paths() -> BuildPlan {
    let artifact = minimal_artifact();
    BuildPlan {
        schema_version: 1,
        mode: "plan".to_string(),
        branch_name: BranchName::from("1.19.2"),
        minecraft_version: MinecraftVersion::parse("1.19.2").expect("version should parse"),
        worktree_path: PathBuf::from("D:/Repos/Minecraft/SFM/repos2/1.19.2"),
        minecraft_dir: PathBuf::from("D:/Repos/Minecraft/SFM/repos2/1.19.2/platform/minecraft"),
        gradle_output_jar: PathBuf::from("build/libs/sfm.jar"),
        rust_output_jar: PathBuf::from("build/libs/sfm-rust.jar"),
        cache_dir: PathBuf::from(
            "D:/Repos/Minecraft/SFM/repos2/1.19.2/platform/minecraft/build/sfm-toolchain",
        ),
        common_cache_dir: PathBuf::from("D:/sfm-cache/minecraft-toolchain"),
        state_dir: PathBuf::from("build/sfm-toolchain/state"),
        maven_cache_dir: PathBuf::from("D:/sfm-cache/minecraft-toolchain/maven"),
        minecraft_cache_dir: PathBuf::from("D:/sfm-cache/minecraft-toolchain/minecraft"),
        minecraft_version_cache_dir: PathBuf::from(
            "D:/sfm-cache/minecraft-toolchain/minecraft/versions/1.19.2",
        ),
        minecraft_assets_dir: PathBuf::from("D:/sfm-cache/minecraft-toolchain/minecraft/assets"),
        minecraft_libraries_dir: PathBuf::from(
            "D:/sfm-cache/minecraft-toolchain/minecraft/libraries",
        ),
        lockfile_path: PathBuf::from("sfm-toolchain.lock.json"),
        lockfile: None,
        java: JavaPlan {
            executable: PathBuf::from("java"),
            home: None,
            version_output: "openjdk version \"17\"".to_string(),
            major_version: 17,
        },
        java_release: 17,
        refresh: false,
        allow_local_artifact_cache: false,
        properties: BTreeMap::new(),
        repositories: Vec::new(),
        loader_toolchain: LoaderToolchainPlan {
            kind: LoaderToolchainKind::ForgeGradleForge,
            base_coordinate: "net.minecraftforge:forge:1.19.2-43.4.0".to_string(),
            userdev_coordinate: "net.minecraftforge:forge:1.19.2-43.4.0:userdev".to_string(),
            sources_coordinate: None,
            universal_coordinate: None,
        },
        artifacts: vec![artifact.clone()],
        minecraft: MinecraftPlan {
            version_manifest: artifact.clone(),
            version_json: artifact,
            client_jar_url: "https://example.test/client.jar".to_string(),
            server_jar_url: "https://example.test/server.jar".to_string(),
            client_mappings_url: None,
            server_mappings_url: None,
            libraries_count: 0,
        },
        forge_userdev: None,
        mcp_config: None,
        dependencies: Vec::new(),
        graph: Vec::new(),
        warnings: Vec::new(),
    }
}

fn test_build_options(parallelism: Parallelism) -> BuildOptions {
    BuildOptions {
        branch: BranchQuery::default(),
        refresh: false,
        explain_rebuild: false,
        plan_json: None,
        java_home: None,
        dry_run: true,
        allow_local_artifact_cache: false,
        error_action: ErrorAction::Bail,
        parallelism,
        mode: BuildMode::Plan,
    }
}

fn test_worktree_target(branch: &str, path: &str) -> WorktreeTarget {
    WorktreeTarget {
        branch: BranchName::from(branch),
        worktree_path: WorktreePath::from(PathBuf::from(path)),
        core: true,
        mc_version: Some(MinecraftVersion::parse(branch).expect("test branch should be version")),
    }
}

fn matching_siblings(path: &Path, kind: &str) -> Vec<PathBuf> {
    let parent = path.parent().expect("path should have parent");
    let file_name = path
        .file_name()
        .and_then(std::ffi::OsStr::to_str)
        .expect("path should have filename");
    let prefix = format!("{file_name}.{kind}.");
    let mut paths = fs::read_dir(parent)
        .expect("parent should read")
        .map(|entry| entry.expect("entry should read").path())
        .filter(|entry_path| {
            entry_path
                .file_name()
                .and_then(std::ffi::OsStr::to_str)
                .is_some_and(|name| name.starts_with(&prefix))
        })
        .collect::<Vec<_>>();
    paths.sort();
    paths
}

struct TestDir {
    path: PathBuf,
}

impl TestDir {
    fn new(name: &str) -> Self {
        let id = TEST_DIR_COUNTER.fetch_add(1, AtomicOrdering::SeqCst);
        let path = std::env::temp_dir().join(format!(
            "sfm-jar-build-engine-tests-{name}-{}-{id}",
            std::process::id()
        ));
        if path.exists() {
            fs::remove_dir_all(&path).expect("stale test dir should be removable");
        }
        fs::create_dir_all(&path).expect("test dir should be created");
        Self { path }
    }
}

impl Drop for TestDir {
    fn drop(&mut self) {
        if self.path.exists() {
            fs::remove_dir_all(&self.path).expect("test dir should be removable");
        }
    }
}
