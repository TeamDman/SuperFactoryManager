use super::ArtifactAuditIssueKind;
use super::ArtifactAuditSeverity;
use super::ArtifactId;
use super::ArtifactLockEntry;
use super::ArtifactLockfile;
use super::ArtifactPlan;
use super::ArtifactPortabilityAudit;
use super::ArtifactProvenance;
use super::ArtifactPurpose;
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
use super::Resolver;
use super::RunKind;
use super::RunOptions;
use super::SourceBuildProvenance;
use super::SourceBuildSystem;
use super::TargetJarCompareReport;
use super::apply_client_puppet_keep_open_property;
use super::apply_game_test_filter_property;
use super::artifact_lock_path;
use super::artifact_portability_audit;
use super::audit_artifact_lockfile;
use super::build_artifact_lockfile;
use super::compare_version_text;
use super::copy_file_to_path_checked;
use super::diagnostic_counts_from_log_text;
use super::download_to_path_overwrite_with_expected_hash;
use super::enforce_portable_artifacts;
use super::execute_targets_parallel;
use super::execute_targets_parallel_with_cancellation;
use super::extract_client_puppet_failure;
use super::extract_client_puppet_pass_count;
use super::extract_failed_gametest_names;
use super::extract_quoted;
use super::extract_sfm_game_test_names;
use super::interpolate_properties;
use super::is_excluded_source;
use super::minecraft_library_jars_from_version_json;
use super::normalize_manifest_bytes;
use super::parchment_coordinate;
use super::parse_maven_versions;
use super::partition_game_test_candidates;
use super::portable_cache_path;
use super::prepare_client_automation_options;
use super::prepare_existing_artifact_for_reuse;
use super::replace_artifact_file;
use super::resolve_loader_toolchain;
use super::run_dependency_configurations;
use super::run_max_launch_attempts;
use super::rust_output_jar_path;
use super::set_minecraft_option;
use super::should_keep_split_minecraft_runtime_entry;
use super::source_build_checkout_key;
use super::source_git_provenance;
use super::write_compare_reports;
use super::write_unique_temp_file;
use crate::artifact_lock::ArtifactLock;
use crate::branch_targets::BranchName;
use crate::branch_targets::BranchQuery;
use crate::branch_targets::MinecraftVersion;
use crate::branch_targets::WorktreePath;
use crate::branch_targets::WorktreeTarget;
use crate::cancellation::CancellationToken;
use crate::jar_build::ClientPuppetKeepOpen;
use crate::jar_build::ErrorAction;
use crate::jar_build::Parallelism;
use crate::jar_build::hash::ContentHash;
use crate::jar_build::hash::ContentHashAlgorithm;
use reqwest::blocking::Client;
use std::cmp::Ordering;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fs;
use std::io::Read;
use std::io::Write;
use std::net::TcpListener;
use std::path::Path;
use std::path::PathBuf;
use std::process::Command;
use std::sync::Arc;
use std::sync::atomic::AtomicUsize;
use std::sync::atomic::Ordering as AtomicOrdering;
use std::thread;
use std::time::Duration;
use std::time::Instant;

fn legacy_sha1_hash(input: &str) -> ContentHash {
    ContentHash::parse_hex(input, ContentHashAlgorithm::Sha1).expect("test SHA-1 should parse")
}

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
fn extracts_client_puppet_pass_and_failure_markers() {
    assert_eq!(
        extract_client_puppet_pass_count(
            "[Render thread/INFO]: SFM_CLIENT_PUPPET_TESTS_PASSED required=206 total=206"
        ),
        Some(206)
    );

    let failure = extract_client_puppet_failure(
        "[Render thread/INFO]: SFM_CLIENT_PUPPET_TESTS_FAILED required_failed=7 optional_failed=1 required=190 total=191",
    )
    .expect("failure marker should parse");

    assert_eq!(failure.required_failed, 7);
    assert_eq!(failure.optional_failed, 1);
    assert_eq!(failure.required_count, 190);
    assert_eq!(failure.total_count, 191);
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
    let existing = "version:3700\nonboardAccessibility:true\nnarrator:0\n";
    assert_eq!(
        set_minecraft_option(existing, "onboardAccessibility", "false"),
        "version:3700\nonboardAccessibility:false\nnarrator:0\n"
    );
    assert_eq!(
        set_minecraft_option("version:3700\n", "onboardAccessibility", "false"),
        "version:3700\nonboardAccessibility:false\n"
    );
    assert_eq!(
        set_minecraft_option("pauseOnLostFocus:true\n", "pauseOnLostFocus", "false"),
        "pauseOnLostFocus:false\n"
    );
}

#[test]
fn client_automation_options_disable_onboarding_and_focus_pause() {
    let test_dir = tempfile::Builder::new()
        .prefix("sfm-client-options-")
        .tempdir()
        .expect("test temp dir should be created");
    let minecraft_dir = test_dir.path().join("minecraft");
    let puppet_dir = minecraft_dir.join("runClientPuppet");
    fs::create_dir_all(&puppet_dir).expect("create puppet run dir");
    let options_path = puppet_dir.join("options.txt");
    fs::write(
        &options_path,
        "version:3955\nonboardAccessibility:true\npauseOnLostFocus:true\n",
    )
    .expect("write existing options");

    let prepared =
        prepare_client_automation_options(&minecraft_dir, &puppet_dir, RunKind::ClientPuppet)
            .expect("prepare client puppet options");

    assert_eq!(prepared, Some(options_path.clone()));
    let updated = fs::read_to_string(&options_path).expect("read updated options");
    assert!(updated.contains("onboardAccessibility:false\n"));
    assert!(updated.contains("narrator:0\n"));
    assert!(updated.contains("pauseOnLostFocus:false\n"));
    assert!(updated.contains("tutorialStep:none\n"));

    let client_dir = minecraft_dir.join("runClient");
    fs::create_dir_all(&client_dir).expect("create client run dir");
    let untouched = prepare_client_automation_options(&minecraft_dir, &client_dir, RunKind::Client)
        .expect("skip regular client options");
    assert_eq!(untouched, None);
    assert!(!client_dir.join("options.txt").exists());
}

#[test]
fn game_test_run_filter_sets_selection_property_for_game_test_runners() {
    let run_options = RunOptions {
        game_test_filter: Some(" wither_aggro_* ".to_string()),
        game_test_bisect: None,
        ..RunOptions::default()
    };
    let mut server_properties = BTreeMap::new();
    apply_game_test_filter_property(
        &mut server_properties,
        RunKind::GameTestServer,
        &run_options,
    );
    assert_eq!(
        server_properties
            .get("sfm.gametestSelection")
            .map(String::as_str),
        Some("wither_aggro_*")
    );

    let mut puppet_properties = BTreeMap::new();
    apply_game_test_filter_property(&mut puppet_properties, RunKind::ClientPuppet, &run_options);
    assert_eq!(
        puppet_properties
            .get("sfm.gametestSelection")
            .map(String::as_str),
        Some("wither_aggro_*")
    );

    let mut client_properties = BTreeMap::new();
    apply_game_test_filter_property(&mut client_properties, RunKind::Client, &run_options);
    assert!(!client_properties.contains_key("sfm.gametestSelection"));

    let mut blank_properties = BTreeMap::new();
    apply_game_test_filter_property(
        &mut blank_properties,
        RunKind::GameTestServer,
        &RunOptions {
            game_test_filter: Some("  ".to_string()),
            game_test_bisect: None,
            ..RunOptions::default()
        },
    );
    assert!(!blank_properties.contains_key("sfm.gametestSelection"));
}

#[test]
fn client_puppet_keep_open_sets_seconds_property() {
    let mut default_properties = BTreeMap::new();
    apply_client_puppet_keep_open_property(
        &mut default_properties,
        RunKind::ClientPuppet,
        &RunOptions::default(),
    );
    assert_eq!(
        default_properties
            .get("sfm.clientRun.keepOpenSeconds")
            .map(String::as_str),
        Some("25")
    );

    let mut forever_properties = BTreeMap::new();
    apply_client_puppet_keep_open_property(
        &mut forever_properties,
        RunKind::ClientPuppet,
        &RunOptions {
            client_puppet_keep_open: ClientPuppetKeepOpen::Forever,
            ..RunOptions::default()
        },
    );
    assert_eq!(
        forever_properties
            .get("sfm.clientRun.keepOpenSeconds")
            .map(String::as_str),
        Some("-1")
    );

    let mut client_properties = BTreeMap::new();
    apply_client_puppet_keep_open_property(
        &mut client_properties,
        RunKind::Client,
        &RunOptions {
            client_puppet_keep_open: ClientPuppetKeepOpen::Countdown { seconds: 300 },
            ..RunOptions::default()
        },
    );
    assert!(!client_properties.contains_key("sfm.clientRun.keepOpenSeconds"));
}

#[test]
fn game_test_bisect_partitions_candidates_evenly() {
    let candidates = ["a", "b", "c", "d", "e"]
        .into_iter()
        .map(str::to_string)
        .collect::<Vec<_>>();

    assert_eq!(
        partition_game_test_candidates(&candidates, 2),
        vec![
            vec!["a".to_string(), "b".to_string(), "c".to_string()],
            vec!["d".to_string(), "e".to_string()]
        ]
    );
    assert_eq!(
        partition_game_test_candidates(&candidates, 4),
        vec![
            vec!["a".to_string(), "b".to_string()],
            vec!["c".to_string()],
            vec!["d".to_string()],
            vec!["e".to_string()]
        ]
    );
}

#[test]
fn game_test_log_parsers_extract_selected_and_failed_names() {
    let unfiltered_output = "\
[12:00:00] [Server thread/INFO] [ca.teamdman.sfm/SFM]: Discovered SFM game test: move_1_stack
[12:00:00] [Server thread/INFO] [ca.teamdman.sfm/SFM]: Generated SFM game test: wither_aggro_does_not_break_tough_cable_facaded_as_bedrock_wall
[12:00:01] [Server thread/INFO] [minecraft/GameTestServer]: 1 required tests failed :(
[12:00:01] [Server thread/INFO] [minecraft/GameTestServer]: - wither_aggro_does_not_break_tough_cable_facaded_as_bedrock_wall
[12:00:01] [Server thread/INFO] [minecraft/GameTestServer]: ====================================
";

    assert_eq!(
        extract_sfm_game_test_names(unfiltered_output),
        vec![
            "move_1_stack".to_string(),
            "wither_aggro_does_not_break_tough_cable_facaded_as_bedrock_wall".to_string()
        ]
    );
    assert_eq!(
        extract_failed_gametest_names(unfiltered_output),
        vec!["wither_aggro_does_not_break_tough_cable_facaded_as_bedrock_wall".to_string()]
    );

    let filtered_output = "\
[12:00:00] [Server thread/INFO] [ca.teamdman.sfm/SFM]: Discovered SFM game test: move_1_stack
[12:00:00] [Server thread/INFO] [ca.teamdman.sfm/SFM]: Generated SFM game test: wither_aggro_does_not_break_tough_cable_facaded_as_bedrock_wall
[12:00:00] [Server thread/INFO] [ca.teamdman.sfm/SFM]: Selected SFM game test: sfm:wither_aggro_does_not_break_tough_cable_facaded_as_bedrock_wall
[12:00:01] [Server thread/INFO] [minecraft/GameTestServer]: 1 required tests failed :(
[12:00:01] [Server thread/INFO] [minecraft/GameTestServer]: - wither_aggro_does_not_break_tough_cable_facaded_as_bedrock_wall
[12:00:01] [Server thread/INFO] [minecraft/GameTestServer]: ====================================
";

    assert_eq!(
        extract_sfm_game_test_names(filtered_output),
        vec!["wither_aggro_does_not_break_tough_cable_facaded_as_bedrock_wall".to_string()]
    );
}

#[test]
fn graphical_client_runs_use_relaxed_program_timing() {
    assert_eq!(RunKind::Client.game_test_max_program_run_millis(), "1000");
    assert_eq!(
        RunKind::ClientSmoke.game_test_max_program_run_millis(),
        "1000"
    );
    assert_eq!(
        RunKind::ClientPuppet.game_test_max_program_run_millis(),
        "1000"
    );
    assert_eq!(
        RunKind::GameTestServer.game_test_max_program_run_millis(),
        "150"
    );
    assert_eq!(RunKind::Server.game_test_max_program_run_millis(), "150");
}

#[test]
fn game_test_server_runs_keep_retry_attempts_for_bisect_consistency() {
    assert_eq!(run_max_launch_attempts(RunKind::GameTestServer), 3);
    assert_eq!(run_max_launch_attempts(RunKind::ClientPuppet), 1);
    assert_eq!(run_max_launch_attempts(RunKind::Client), 1);
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
fn forge_project_dependency_planning_includes_plain_compile_inputs() {
    let forge_toolchain = LoaderToolchainPlan {
        kind: LoaderToolchainKind::ForgeGradleForge,
        base_coordinate: "net.minecraftforge:forge:1.19.2-43.4.0".to_string(),
        userdev_coordinate: "net.minecraftforge:forge:1.19.2-43.4.0:userdev".to_string(),
        sources_coordinate: Some("net.minecraftforge:forge:1.19.2-43.4.0:sources".to_string()),
        universal_coordinate: Some("net.minecraftforge:forge:1.19.2-43.4.0:universal".to_string()),
    };

    let mekanism_api = super::ParsedDependency {
        configuration: "implementation".to_string(),
        coordinate: MavenCoordinate::parse("mekanism:Mekanism:1.19.2-10.3.8.477:api")
            .expect("coordinate should parse"),
        fg_deobf: false,
    };
    assert!(super::should_plan_project_dependency(
        &forge_toolchain,
        &mekanism_api
    ));

    let minecraft_base = super::ParsedDependency {
        configuration: "minecraft".to_string(),
        coordinate: MavenCoordinate::parse("net.minecraftforge:forge:1.19.2-43.4.0")
            .expect("coordinate should parse"),
        fg_deobf: false,
    };
    assert!(!super::should_plan_project_dependency(
        &forge_toolchain,
        &minecraft_base
    ));

    let test_only = super::ParsedDependency {
        configuration: "testImplementation".to_string(),
        coordinate: MavenCoordinate::parse("org.junit.jupiter:junit-jupiter-api:5.10.0")
            .expect("coordinate should parse"),
        fg_deobf: false,
    };
    assert!(!super::should_plan_project_dependency(
        &forge_toolchain,
        &test_only
    ));
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
fn diagnostic_counts_prefer_compiler_summary_lines() {
    let counts = diagnostic_counts_from_log_text(
        "src/Main.java:1: error: cannot find symbol\n\
         src/Main.java:2: warning: [unchecked] unchecked conversion\n\
         3 errors\n\
         100 warnings\n",
    );

    assert_eq!(counts.errors, 3);
    assert_eq!(counts.warnings, 100);
}

#[test]
fn diagnostic_counts_fall_back_to_diagnostic_lines() {
    let counts = diagnostic_counts_from_log_text(
        "error: first failure\n\
         note: this is informational\n\
         warning: first warning\n\
         [ERROR] second failure\n",
    );

    assert_eq!(counts.errors, 2);
    assert_eq!(counts.warnings, 1);
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
    assert_eq!(parsed_provenance.hash, provenance.hash);

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
            source_relative_path: None,
            source_git: None,
            source_build: None,
            hash: provenance.hash,
        }],
    };
    let json = facet_json::to_string_pretty(&lockfile).expect("lockfile should serialize");
    assert!(json.contains("remote-maven"));
    let parsed: ArtifactLockfile = facet_json::from_str(&json).expect("lockfile should parse");
    assert_eq!(parsed.artifacts[0].source, ArtifactSource::RemoteMaven);
}

#[test]
fn migrated_common_cache_lockfile_does_not_duplicate_old_cache_entries() {
    let test_dir = TestDir::new("common-cache-lockfile-dedupe");
    let common_cache = test_dir.path.join("sfm-cache").join("minecraft-toolchain");
    let maven_cache = common_cache.join("maven");
    let artifact_path = maven_cache.join("g").join("a").join("1").join("a-1.jar");
    fs::create_dir_all(artifact_path.parent().expect("artifact should have parent"))
        .expect("artifact parent should be created");
    fs::write(&artifact_path, b"artifact").expect("artifact should be written");

    let legacy_sha1 = legacy_sha1_hash("1e5dcbb59b753cb1d46e234d8f6180285b8b86ad");
    let provenance = ArtifactProvenance {
        schema_version: 1,
        source: ArtifactSource::RemoteMaven,
        coordinate: Some("g:a:1".to_string()),
        repository: Some("Forge".to_string()),
        url: Some("https://example.test/a-1.jar".to_string()),
        original_path: None,
        source_relative_path: None,
        source_git: None,
        source_build: None,
        hash: legacy_sha1,
    };
    fs::write(
        artifact_path.with_file_name("a-1.jar.sfm-provenance.json"),
        facet_json::to_string_pretty(&provenance).expect("provenance should serialize"),
    )
    .expect("provenance should be written");

    let mut plan = minimal_plan_for_paths();
    plan.common_cache_dir = common_cache;
    plan.maven_cache_dir = maven_cache.clone();
    plan.artifacts = Vec::new();
    plan.dependencies = vec![DependencyPlan {
        configuration: "implementation".to_string(),
        notation: "g:a:1".to_string(),
        resolved_notation: "g:a:1".to_string(),
        source: DependencySource::Maven,
        cache_path: artifact_path,
        url: Some("https://example.test/a-1.jar".to_string()),
        dynamic_version: false,
    }];
    plan.lockfile = Some(ArtifactLockfile {
        schema_version: 1,
        minecraft_version: plan.minecraft_version.to_string(),
        maven_cache_dir: PathBuf::from("build/sfm-toolchain/maven"),
        allow_local_artifact_cache: false,
        repositories: Vec::new(),
        dependencies: Vec::new(),
        artifacts: vec![ArtifactLockEntry {
            coordinate: Some("g:a:1".to_string()),
            source: ArtifactSource::RemoteMaven,
            repository: Some("Forge".to_string()),
            url: Some("https://example.test/a-1.jar".to_string()),
            cache_path: PathBuf::from("build/sfm-toolchain/maven/g/a/1/a-1.jar"),
            original_path: None,
            source_relative_path: None,
            source_git: None,
            source_build: None,
            hash: legacy_sha1,
        }],
    });

    let lockfile = build_artifact_lockfile(&plan, &[]).expect("lockfile should build");
    let matching_entries = lockfile
        .artifacts
        .iter()
        .filter(|artifact| artifact.coordinate.as_deref() == Some("g:a:1"))
        .collect::<Vec<_>>();

    assert_eq!(matching_entries.len(), 1);
    assert_eq!(
        matching_entries[0].cache_path,
        PathBuf::from("$sfm-cache/maven/g/a/1/a-1.jar")
    );
}

#[test]
fn run_extra_cache_artifacts_are_written_to_lockfile() {
    let test_dir = TestDir::new("run-extra-cache-lockfile");
    let common_cache = test_dir.path.join("sfm-cache");
    let maven_cache = common_cache.join("maven");
    let artifact_path = maven_cache
        .join("com")
        .join("electronwill")
        .join("night-config")
        .join("core")
        .join("3.8.3")
        .join("core-3.8.3.jar");
    fs::create_dir_all(artifact_path.parent().expect("artifact should have parent"))
        .expect("artifact parent should be created");
    fs::write(&artifact_path, b"night-config-core").expect("artifact should be written");
    let hash = ContentHash::from_bytes(b"night-config-core", ContentHashAlgorithm::Blake3);
    let provenance = ArtifactProvenance {
        schema_version: 1,
        source: ArtifactSource::RemoteMaven,
        coordinate: Some("com.electronwill.night-config:core:3.8.3".to_string()),
        repository: Some("NeoForge".to_string()),
        url: Some(
            "https://maven.neoforged.net/releases/com/electronwill/night-config/core/3.8.3/core-3.8.3.jar"
                .to_string(),
        ),
        original_path: None,
        source_relative_path: None,
        source_git: None,
        source_build: None,
        hash,
    };
    fs::write(
        artifact_path.with_file_name("core-3.8.3.jar.sfm-provenance.json"),
        facet_json::to_string_pretty(&provenance).expect("provenance should serialize"),
    )
    .expect("provenance should be written");

    let unprovenanced_cache_file = common_cache.join("run").join("generated.jar");
    fs::create_dir_all(
        unprovenanced_cache_file
            .parent()
            .expect("cache file should have parent"),
    )
    .expect("cache file parent should be created");
    fs::write(&unprovenanced_cache_file, b"generated").expect("cache file should be written");

    let mut plan = minimal_plan_for_paths();
    plan.common_cache_dir = common_cache;
    plan.maven_cache_dir = maven_cache;
    plan.artifacts = Vec::new();
    plan.dependencies = Vec::new();

    let lockfile = build_artifact_lockfile(&plan, &[artifact_path, unprovenanced_cache_file])
        .expect("lockfile should build");

    assert_eq!(lockfile.artifacts.len(), 1);
    assert_eq!(
        lockfile.artifacts[0].coordinate.as_deref(),
        Some("com.electronwill.night-config:core:3.8.3")
    );
    assert_eq!(
        lockfile.artifacts[0].cache_path,
        PathBuf::from("$sfm-cache/maven/com/electronwill/night-config/core/3.8.3/core-3.8.3.jar")
    );
    assert_eq!(lockfile.artifacts[0].hash, hash);
}

#[test]
fn prepare_existing_artifact_quarantines_wrong_sha1() {
    let test_dir = TestDir::new("prepare-existing-artifact");
    let artifact = test_dir.path.join("artifact.jar");
    fs::write(&artifact, b"bad").expect("artifact should be written");

    let expected_hash = ContentHash::from_bytes(b"good", ContentHashAlgorithm::Blake3);
    prepare_existing_artifact_for_reuse(&artifact, Some(&expected_hash))
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

    let expected_hash = ContentHash::from_bytes(b"good", ContentHashAlgorithm::Blake3);
    copy_file_to_path_checked(&source, &destination, Some(&expected_hash))
        .expect("valid destination should skip copying the bad source");

    assert_eq!(
        fs::read(&destination).expect("destination should remain readable"),
        b"good"
    );
    assert_eq!(
        ContentHash::from_path(&destination, ContentHashAlgorithm::Blake3)
            .expect("destination should hash"),
        expected_hash
    );
    assert!(matching_siblings(&destination, "tmp").is_empty());
}

#[test]
fn copy_file_to_path_checked_waits_and_reuses_artifact_created_by_lock_holder() {
    let test_dir = TestDir::new("copy-file-waits-for-valid-final");
    let source = test_dir.path.join("source.jar");
    let destination = test_dir.path.join("artifact.jar");
    fs::write(&source, b"bad").expect("source should be written");
    let expected_hash = ContentHash::from_bytes(b"good", ContentHashAlgorithm::Blake3);
    let lock_path = artifact_lock_path(&destination).expect("artifact should have lock path");
    let lock =
        ArtifactLock::acquire(&lock_path, destination.display().to_string()).expect("first lock");

    let thread_source = source.clone();
    let thread_destination = destination.clone();
    let thread_expected_hash = expected_hash;
    let started = Instant::now();
    let waiter = thread::spawn(move || {
        copy_file_to_path_checked(
            &thread_source,
            &thread_destination,
            Some(&thread_expected_hash),
        )
    });

    thread::sleep(Duration::from_millis(50));
    fs::write(&destination, b"good").expect("lock holder should write final artifact");
    drop(lock);
    waiter
        .join()
        .expect("waiter thread should finish")
        .expect("waiter should reuse valid final artifact");

    assert!(started.elapsed() >= Duration::from_millis(40));
    assert_eq!(
        fs::read(&destination).expect("destination should remain readable"),
        b"good"
    );
    assert_eq!(
        ContentHash::from_path(&destination, ContentHashAlgorithm::Blake3)
            .expect("destination should hash"),
        expected_hash
    );
    assert!(matching_siblings(&destination, "tmp").is_empty());
}

#[test]
fn resolver_cache_hit_waits_for_writer_lock_before_reading() {
    let test_dir = TestDir::new("resolver-cache-hit-waits-for-writer");
    let coordinate =
        MavenCoordinate::parse("example.group:artifact:1.0.0").expect("coordinate should parse");
    let resolver = Resolver::new(
        test_dir.path.join("maven"),
        Vec::new(),
        false,
        false,
        Vec::new(),
        None,
        None,
        test_cancellation_token(),
    )
    .expect("resolver should build");
    let cache_path = resolver.cache_path_for(&coordinate);
    fs::create_dir_all(cache_path.parent().expect("cache path should have parent"))
        .expect("cache parent should be created");
    fs::write(&cache_path, b"cached").expect("cached artifact should be written");
    let lock_path = artifact_lock_path(&cache_path).expect("artifact should have lock path");
    let writer_lock =
        ArtifactLock::acquire(&lock_path, cache_path.display().to_string()).expect("writer lock");

    let started = Instant::now();
    let waiter = thread::spawn(move || {
        resolver.resolve_artifact(
            ArtifactId::from("cached-artifact"),
            &coordinate,
            ArtifactPurpose::from("resolver test"),
        )
    });

    thread::sleep(Duration::from_millis(50));
    drop(writer_lock);
    let artifact = waiter
        .join()
        .expect("resolver thread should finish")
        .expect("cached artifact should resolve");

    assert!(started.elapsed() >= Duration::from_millis(40));
    assert_eq!(artifact.cache_path, cache_path);
    assert_eq!(
        artifact.sha1,
        Some(ContentHash::from_bytes(
            b"cached",
            ContentHashAlgorithm::Blake3
        ))
    );
}

#[test]
fn resolver_imports_from_explicit_project_artifact_source() {
    let test_dir = TestDir::new("resolver-explicit-artifact-source");
    let source_root = test_dir.path.join("source-project");
    let libs_dir = source_root.join("build").join("libs");
    fs::create_dir_all(&libs_dir).expect("source build libs should be created");
    let coordinate = MavenCoordinate::parse("example.group:artifact:1.0.0:sources")
        .expect("classifier coordinate should parse");
    let source_artifact = libs_dir.join(coordinate.file_name());
    fs::write(&source_artifact, b"explicit source").expect("source artifact should be written");
    run_git(&source_root, ["init"]);
    run_git(&source_root, ["config", "user.name", "SFM Test"]);
    run_git(
        &source_root,
        ["config", "user.email", "sfm-test@example.test"],
    );
    run_git(
        &source_root,
        [
            "remote",
            "add",
            "origin",
            "https://example.test/sfm/source-project.git",
        ],
    );
    run_git(&source_root, ["add", "."]);
    run_git(&source_root, ["commit", "-m", "initial artifact"]);

    let resolver = Resolver::new(
        test_dir.path.join("maven-cache"),
        Vec::new(),
        false,
        false,
        vec![source_root],
        None,
        None,
        test_cancellation_token(),
    )
    .expect("resolver should build");
    let artifact = resolver
        .resolve_artifact(
            ArtifactId::from("explicit-source"),
            &coordinate,
            ArtifactPurpose::from("explicit source test"),
        )
        .expect("artifact should import from explicit source");

    assert_eq!(
        fs::read(&artifact.cache_path).expect("cache artifact should be readable"),
        b"explicit source"
    );
    assert_eq!(artifact.provenance.source, ArtifactSource::ExplicitSource);
    assert_eq!(
        artifact.provenance.original_path.as_deref(),
        Some(source_artifact.as_path())
    );
    let expected_source_relative_path =
        Path::new("build").join("libs").join(coordinate.file_name());
    assert_eq!(
        artifact.provenance.source_relative_path.as_deref(),
        Some(expected_source_relative_path.as_path())
    );
    assert_eq!(
        artifact
            .provenance
            .source_git
            .as_ref()
            .and_then(|source_git| source_git.remote_url.as_deref()),
        Some("https://example.test/sfm/source-project.git")
    );
    assert_eq!(
        artifact.repository.as_deref(),
        Some("explicit-artifact-source")
    );
}

#[test]
fn explicit_source_mekanism_artifacts_record_source_build_commands() {
    let test_dir = TestDir::new("mekanism-source-build-provenance");
    let source_root = test_dir.path.join("Mekanism");
    let libs_dir = source_root.join("build").join("libs");
    fs::create_dir_all(&libs_dir).expect("source build libs should be created");
    fs::write(source_root.join("gradlew.bat"), "@echo off\r\n")
        .expect("gradle wrapper marker should be written");

    let main_coordinate = MavenCoordinate::parse("mekanism:Mekanism:26.1.2-10.8.0.86")
        .expect("main coordinate should parse");
    let api_coordinate = MavenCoordinate::parse("mekanism:Mekanism:26.1.2-10.8.0.86:api")
        .expect("api coordinate should parse");
    fs::write(libs_dir.join(main_coordinate.file_name()), b"mekanism main")
        .expect("main artifact should be written");
    fs::write(libs_dir.join(api_coordinate.file_name()), b"mekanism api")
        .expect("api artifact should be written");

    run_git(&source_root, ["init"]);
    run_git(&source_root, ["config", "user.name", "SFM Test"]);
    run_git(
        &source_root,
        ["config", "user.email", "sfm-test@example.test"],
    );
    run_git(
        &source_root,
        [
            "remote",
            "add",
            "origin",
            "https://github.com/mekanism/Mekanism/",
        ],
    );
    run_git(&source_root, ["add", "."]);
    run_git(&source_root, ["commit", "-m", "initial mekanism artifacts"]);

    let resolver = Resolver::new(
        test_dir.path.join("maven-cache"),
        Vec::new(),
        false,
        false,
        vec![source_root],
        None,
        None,
        test_cancellation_token(),
    )
    .expect("resolver should build");
    let main_artifact = resolver
        .resolve_artifact(
            ArtifactId::from("mekanism-main"),
            &main_coordinate,
            ArtifactPurpose::from("mekanism main"),
        )
        .expect("main artifact should import from explicit source");
    let api_artifact = resolver
        .resolve_artifact(
            ArtifactId::from("mekanism-api"),
            &api_coordinate,
            ArtifactPurpose::from("mekanism api"),
        )
        .expect("api artifact should import from explicit source");

    assert_source_build(
        main_artifact
            .provenance
            .source_build
            .as_ref()
            .expect("main artifact should record source build"),
        "jar",
        "build/libs/Mekanism-26.1.2-10.8.0.86.jar",
    );
    assert_source_build(
        api_artifact
            .provenance
            .source_build
            .as_ref()
            .expect("api artifact should record source build"),
        "apiJar",
        "build/libs/Mekanism-26.1.2-10.8.0.86-api.jar",
    );
}

#[test]
fn resolver_materializes_locked_artifact_from_source_build() {
    let test_dir = TestDir::new("resolver-source-build-fallback");
    let source_root = test_dir.path.join("source-project");
    fs::create_dir_all(&source_root).expect("source root should be created");
    let coordinate =
        MavenCoordinate::parse("example.group:artifact:1.0.0").expect("coordinate should parse");
    let output_path = Path::new("build").join("libs").join("artifact-1.0.0.jar");
    write_fake_gradle_wrapper(&source_root, &output_path);
    run_git(&source_root, ["init"]);
    run_git(&source_root, ["config", "user.name", "SFM Test"]);
    run_git(
        &source_root,
        ["config", "user.email", "sfm-test@example.test"],
    );
    run_git(&source_root, ["add", "."]);
    run_git(&source_root, ["commit", "-m", "fake source build"]);
    let commit = git_stdout_test(&source_root, ["rev-parse", "HEAD"]);
    let remote_url = source_root.display().to_string();
    let source_build = SourceBuildProvenance {
        build_system: SourceBuildSystem::GradleWrapper,
        tasks: vec!["jar".to_string()],
        environment: BTreeMap::new(),
        output_path: output_path.clone(),
    };
    let lockfile = ArtifactLockfile {
        schema_version: 1,
        minecraft_version: "1.19.2".to_string(),
        maven_cache_dir: PathBuf::from("$sfm-cache").join("maven"),
        allow_local_artifact_cache: false,
        repositories: Vec::new(),
        dependencies: Vec::new(),
        artifacts: vec![ArtifactLockEntry {
            coordinate: Some(coordinate.to_string()),
            source: ArtifactSource::ExplicitSource,
            repository: Some("explicit-artifact-source".to_string()),
            url: None,
            cache_path: PathBuf::from("$sfm-cache")
                .join("maven/example/group/artifact/1.0.0/artifact-1.0.0.jar"),
            original_path: Some(source_root.join(&output_path)),
            source_relative_path: Some(output_path.clone()),
            source_git: Some(super::SourceGitProvenance {
                root: source_root.clone(),
                commit: commit.clone(),
                branch: "main".to_string(),
                dirty: false,
                remote_url: Some(remote_url.clone()),
            }),
            source_build: Some(source_build),
            hash: ContentHash::from_bytes(
                b"not-used-when-refreshing",
                ContentHashAlgorithm::Blake3,
            ),
        }],
    };

    let resolver = Resolver::new(
        test_dir.path.join("maven-cache"),
        Vec::new(),
        true,
        false,
        Vec::new(),
        None,
        Some(lockfile),
        test_cancellation_token(),
    )
    .expect("resolver should build");
    let artifact = resolver
        .resolve_artifact(
            ArtifactId::from("source-build"),
            &coordinate,
            ArtifactPurpose::from("source build test"),
        )
        .expect("artifact should materialize from source build");

    assert_eq!(artifact.provenance.source, ArtifactSource::SourceBuild);
    assert_eq!(
        String::from_utf8(fs::read(&artifact.cache_path).expect("artifact should read"))
            .expect("artifact should be utf8")
            .trim(),
        "source build artifact"
    );
    let checkout_key = source_build_checkout_key(&remote_url, &commit);
    assert_eq!(
        artifact
            .provenance
            .source_git
            .as_ref()
            .map(|source_git| source_git.root.clone()),
        Some(
            PathBuf::from("$sfm-cache")
                .join("source-builds")
                .join(checkout_key)
        )
    );
    assert_eq!(
        artifact.provenance.source_relative_path.as_deref(),
        Some(output_path.as_path())
    );
}

fn assert_source_build(source_build: &SourceBuildProvenance, task: &str, output_path: &str) {
    assert_eq!(source_build.build_system, SourceBuildSystem::GradleWrapper);
    assert_eq!(source_build.tasks, vec![task.to_string()]);
    assert_eq!(
        source_build.environment.get("BUILD_NUMBER"),
        Some(&"86".to_string())
    );
    assert_eq!(source_build.output_path, PathBuf::from(output_path));
}

#[test]
fn source_git_provenance_records_checkout_state() {
    let test_dir = TestDir::new("source-git-provenance");
    let repo = test_dir.path.join("source-project");
    fs::create_dir_all(&repo).expect("repo dir should be created");
    run_git(&repo, ["init"]);
    run_git(&repo, ["config", "user.name", "SFM Test"]);
    run_git(&repo, ["config", "user.email", "sfm-test@example.test"]);
    run_git(
        &repo,
        [
            "remote",
            "add",
            "origin",
            "https://example.test/sfm/source-project.git",
        ],
    );

    let artifact = repo.join("build").join("libs").join("artifact.jar");
    fs::create_dir_all(artifact.parent().expect("artifact should have parent"))
        .expect("artifact parent should be created");
    fs::write(&artifact, b"artifact").expect("artifact should be written");
    run_git(&repo, ["add", "."]);
    run_git(&repo, ["commit", "-m", "initial artifact"]);

    let clean = source_git_provenance(&artifact).expect("clean provenance should be available");
    assert_eq!(
        fs::canonicalize(&clean.root).expect("git root should canonicalize"),
        fs::canonicalize(&repo).expect("repo should canonicalize")
    );
    assert_eq!(clean.commit.len(), 40);
    assert!(!clean.branch.is_empty());
    assert!(!clean.dirty);
    assert_eq!(
        clean.remote_url.as_deref(),
        Some("https://example.test/sfm/source-project.git")
    );

    fs::write(repo.join("untracked.txt"), b"dirty").expect("dirty file should be written");
    let dirty = source_git_provenance(&artifact).expect("dirty provenance should be available");
    assert_eq!(dirty.commit, clean.commit);
    assert!(dirty.dirty);
}

#[test]
fn copy_file_to_path_checked_rejects_temp_sha1_failure() {
    let test_dir = TestDir::new("copy-file-temp-sha1-failure");
    let source = test_dir.path.join("source.jar");
    let destination = test_dir.path.join("artifact.jar");
    fs::write(&source, b"bad").expect("source should be written");

    let expected_hash = ContentHash::from_bytes(b"good", ContentHashAlgorithm::Blake3);
    let error = copy_file_to_path_checked(&source, &destination, Some(&expected_hash))
        .expect_err("bad source should fail expected content hash validation");

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

    let expected_hash = ContentHash::from_bytes(b"good", ContentHashAlgorithm::Blake3);
    copy_file_to_path_checked(&source, &destination, Some(&expected_hash))
        .expect("good source should replace corrupt artifact");

    assert_eq!(
        fs::read(&destination).expect("destination should remain readable"),
        b"good"
    );
    assert!(matching_siblings(&destination, "bad").is_empty());
}

#[test]
fn download_to_path_retries_after_temp_sha1_failure() {
    let test_dir = TestDir::new("download-retry-temp-sha1-failure");
    let destination = test_dir.path.join("artifact.jar");
    let expected_hash = ContentHash::from_bytes(b"good", ContentHashAlgorithm::Blake3);
    let (url, server) = serve_http_bodies(vec![b"bad".to_vec(), b"good".to_vec()]);
    let cancellation_token = test_cancellation_token();

    download_to_path_overwrite_with_expected_hash(
        &cancellation_token,
        &Client::new(),
        &url,
        &destination,
        false,
        &expected_hash,
    )
    .expect("download should retry and write good artifact");
    server.join().expect("test server should finish");

    assert_eq!(
        fs::read(&destination).expect("destination should remain readable"),
        b"good"
    );
    assert_eq!(
        ContentHash::from_path(&destination, ContentHashAlgorithm::Blake3)
            .expect("destination should hash"),
        expected_hash
    );
    assert!(matching_siblings(&destination, "tmp").is_empty());
}

#[test]
fn replace_artifact_file_replaces_existing_destination_under_lock() {
    let test_dir = TestDir::new("replace-existing-artifact");
    let destination = test_dir.path.join("artifact.jar");
    let temporary = test_dir.path.join("artifact.jar.tmp");
    fs::write(&destination, b"old").expect("destination should be written");
    fs::write(&temporary, b"new").expect("temporary should be written");
    let lock_path = artifact_lock_path(&destination).expect("artifact should have lock path");
    let _lock =
        ArtifactLock::acquire(&lock_path, destination.display().to_string()).expect("writer lock");

    replace_artifact_file(&temporary, &destination).expect("artifact should be replaced");

    assert_eq!(
        fs::read(&destination).expect("destination should be readable"),
        b"new"
    );
    assert!(!temporary.exists());
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
    let cancellation_token = test_cancellation_token();
    let targets = vec![
        test_worktree_target("1.19.2", "D:/tmp/1.19.2"),
        test_worktree_target("1.20.1", "D:/tmp/1.20.1"),
    ];

    let summary = execute_targets_parallel(
        &options,
        targets,
        "test_parallel_targets",
        2,
        &cancellation_token,
        |_options, target, _cancellation_token| {
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
fn parallel_targets_stop_starting_after_cancellation() {
    let options = test_build_options(Parallelism::Parallel { limit: 1 });
    let targets = vec![
        test_worktree_target("1.19.2", "D:/tmp/1.19.2"),
        test_worktree_target("1.20.1", "D:/tmp/1.20.1"),
    ];
    let started = Arc::new(AtomicUsize::new(0));
    let cancellation_token = CancellationToken::new();
    let execute_cancellation_token = cancellation_token.clone();
    let execute_started = Arc::clone(&started);

    let error = execute_targets_parallel_with_cancellation(
        &options,
        targets,
        "test_parallel_cancelled_targets",
        1,
        move |_options, target, _cancellation_token| {
            execute_started.fetch_add(1, AtomicOrdering::Relaxed);
            let mut plan = minimal_plan_for_paths();
            plan.branch_name = target.branch.clone();
            execute_cancellation_token.request_cancel("Operation cancelled by Ctrl+C");
            Ok(plan)
        },
        &cancellation_token,
    )
    .expect_err("parallel execution should report cancellation");

    assert_eq!(started.load(AtomicOrdering::Relaxed), 1);
    assert!(error.to_string().contains("Operation cancelled by Ctrl+C"));
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
        artifact_sources: Vec::new(),
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
            modules: Vec::new(),
            libraries: Vec::new(),
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
        artifact_portability: ArtifactPortabilityAudit::default(),
        warnings: Vec::new(),
    };

    let json = facet_json::to_string_pretty(&plan).expect("plan should serialize");
    assert!(json.contains("rust_output_jar"));
    assert!(json.contains("common_cache_dir"));
    assert!(json.contains("artifact_portability"));
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
fn artifact_portability_audit_reports_explicit_sources() {
    let source_path = PathBuf::from("G:/Programming/Repos/Mekanism/build/libs/Mekanism.jar");
    let mut plan = minimal_plan_for_paths();
    plan.artifacts[0].provenance.source = ArtifactSource::ExplicitSource;
    plan.artifacts[0].provenance.repository = Some("explicit-artifact-source".to_string());
    plan.artifacts[0].provenance.original_path = Some(source_path.clone());

    let audit = artifact_portability_audit(&plan).expect("audit should build");

    assert!(!audit.fresh_slate_portable);
    assert_eq!(audit.total_artifacts, 1);
    assert_eq!(audit.portable_artifacts, 0);
    assert_eq!(audit.non_portable_artifacts, 1);
    assert_eq!(audit.explicit_source_artifacts, 1);
    assert_eq!(audit.issues[0].source, ArtifactSource::ExplicitSource);
    assert_eq!(
        audit.issues[0].original_path.as_deref(),
        Some(source_path.as_path())
    );

    plan.artifact_portability = audit;
    let error = enforce_portable_artifacts(&plan)
        .expect_err("explicit source should fail portable enforcement");
    assert!(
        error
            .to_string()
            .contains("provide the same source with --artifact-source"),
        "{error:?}"
    );
}

#[test]
fn artifact_portability_audit_treats_source_build_as_portable() {
    let mut plan = minimal_plan_for_paths();
    plan.artifacts[0].provenance.source = ArtifactSource::SourceBuild;
    plan.artifacts[0].provenance.repository = Some("source-build".to_string());
    plan.artifacts[0].provenance.original_path = None;
    plan.artifacts[0].provenance.source_relative_path =
        Some(Path::new("build").join("libs").join("artifact-1.0.0.jar"));
    plan.artifacts[0].provenance.source_git = Some(super::SourceGitProvenance {
        root: PathBuf::from("$sfm-cache")
            .join("source-builds")
            .join("abc123"),
        commit: "abcdef".to_string(),
        branch: "main".to_string(),
        dirty: false,
        remote_url: Some("https://example.test/source.git".to_string()),
    });
    plan.artifacts[0].provenance.source_build = Some(SourceBuildProvenance {
        build_system: SourceBuildSystem::GradleWrapper,
        tasks: vec!["jar".to_string()],
        environment: BTreeMap::new(),
        output_path: Path::new("build").join("libs").join("artifact-1.0.0.jar"),
    });

    let audit = artifact_portability_audit(&plan).expect("audit should build");

    assert!(audit.fresh_slate_portable);
    assert_eq!(audit.total_artifacts, 1);
    assert_eq!(audit.portable_artifacts, 1);
    assert_eq!(audit.non_portable_artifacts, 0);
    assert!(audit.issues.is_empty());
}

#[test]
fn artifact_portability_audit_reads_dependency_provenance() {
    let test_dir = TestDir::new("artifact-portability-dependency-provenance");
    let artifact_path = test_dir.path.join("maven").join("local-only.jar");
    fs::create_dir_all(artifact_path.parent().expect("artifact should have parent"))
        .expect("artifact parent should be created");
    fs::write(&artifact_path, b"local only").expect("artifact should be written");
    let provenance = ArtifactProvenance {
        schema_version: 1,
        source: ArtifactSource::LocalGradleModuleCache,
        coordinate: Some("example:local-only:1.0.0".to_string()),
        repository: Some("local-gradle-module-cache".to_string()),
        url: None,
        original_path: Some(PathBuf::from(
            "C:/Users/Teamy/.gradle/caches/local-only.jar",
        )),
        source_relative_path: None,
        source_git: None,
        source_build: None,
        hash: ContentHash::from_bytes(b"local only", ContentHashAlgorithm::Blake3),
    };
    fs::write(
        artifact_path.with_file_name("local-only.jar.sfm-provenance.json"),
        facet_json::to_string_pretty(&provenance).expect("provenance should serialize"),
    )
    .expect("provenance should be written");

    let mut plan = minimal_plan_for_paths();
    plan.artifacts.clear();
    plan.dependencies = vec![DependencyPlan {
        configuration: "implementation".to_string(),
        notation: "example:local-only:1.0.0".to_string(),
        resolved_notation: "example:local-only:1.0.0".to_string(),
        source: DependencySource::Maven,
        cache_path: artifact_path,
        url: None,
        dynamic_version: false,
    }];

    let audit = artifact_portability_audit(&plan).expect("audit should build");

    assert!(!audit.fresh_slate_portable);
    assert_eq!(audit.total_artifacts, 1);
    assert_eq!(audit.local_cache_artifacts, 1);
    assert_eq!(
        audit.issues[0].source,
        ArtifactSource::LocalGradleModuleCache
    );
    assert!(
        audit.issues[0]
            .remediation
            .contains("configured remote repository")
    );
}

#[test]
fn artifact_audit_verifies_sfm_cache_lockfile_artifact() {
    let test_dir = TestDir::new("artifact-audit-sfm-cache");
    let minecraft_dir = test_dir
        .path
        .join("worktree")
        .join("platform")
        .join("minecraft");
    let common_cache = test_dir.path.join("sfm-cache").join("minecraft-toolchain");
    let cache_path = common_cache
        .join("maven")
        .join("g")
        .join("a")
        .join("1")
        .join("a-1.jar");
    fs::create_dir_all(cache_path.parent().expect("artifact should have parent"))
        .expect("artifact parent should be created");
    fs::write(&cache_path, b"remote artifact").expect("artifact should be written");
    let hash = ContentHash::from_path(&cache_path, ContentHashAlgorithm::Blake3)
        .expect("artifact should hash");
    let lockfile_path = minecraft_dir.join("sfm-toolchain.lock.json");
    fs::create_dir_all(&minecraft_dir).expect("minecraft dir should be created");
    write_test_artifact_lockfile(
        &lockfile_path,
        vec![ArtifactLockEntry {
            coordinate: Some("g:a:1".to_string()),
            source: ArtifactSource::RemoteMaven,
            repository: Some("Test".to_string()),
            url: Some("https://example.test/g/a/1/a-1.jar".to_string()),
            cache_path: PathBuf::from("$sfm-cache").join("maven/g/a/1/a-1.jar"),
            original_path: None,
            source_relative_path: None,
            source_git: None,
            source_build: None,
            hash: hash,
        }],
        Vec::new(),
    );

    let report = audit_artifact_lockfile(
        &lockfile_path,
        &minecraft_dir,
        &common_cache,
        "1.19.2",
        false,
    )
    .expect("artifact audit should run");

    assert!(report.passed);
    assert!(report.fresh_slate_portable);
    assert_eq!(report.total_artifacts, 1);
    assert_eq!(report.verified_artifacts, 1);
    assert_eq!(report.error_count, 0);
    assert_eq!(report.warning_count, 0);
}

#[test]
fn artifact_audit_warns_or_fails_for_explicit_sources() {
    let test_dir = TestDir::new("artifact-audit-explicit-source");
    let minecraft_dir = test_dir
        .path
        .join("worktree")
        .join("platform")
        .join("minecraft");
    let common_cache = test_dir.path.join("sfm-cache").join("minecraft-toolchain");
    let cache_path = common_cache
        .join("maven")
        .join("mekanism")
        .join("Mekanism")
        .join("26.1.2-10.8.0.86")
        .join("Mekanism-26.1.2-10.8.0.86.jar");
    let source_path = test_dir
        .path
        .join("Mekanism")
        .join("build")
        .join("libs")
        .join("Mekanism-26.1.2-10.8.0.86.jar");
    fs::create_dir_all(cache_path.parent().expect("artifact should have parent"))
        .expect("artifact parent should be created");
    fs::create_dir_all(source_path.parent().expect("source should have parent"))
        .expect("source parent should be created");
    fs::write(&cache_path, b"mekanism artifact").expect("artifact should be written");
    fs::write(&source_path, b"mekanism artifact").expect("source should be written");
    let hash = ContentHash::from_path(&cache_path, ContentHashAlgorithm::Blake3)
        .expect("artifact should hash");
    let lockfile_path = minecraft_dir.join("sfm-toolchain.lock.json");
    fs::create_dir_all(&minecraft_dir).expect("minecraft dir should be created");
    write_test_artifact_lockfile(
        &lockfile_path,
        vec![ArtifactLockEntry {
            coordinate: Some("mekanism:Mekanism:26.1.2-10.8.0.86".to_string()),
            source: ArtifactSource::ExplicitSource,
            repository: Some("explicit-artifact-source".to_string()),
            url: None,
            cache_path: PathBuf::from("$sfm-cache")
                .join("maven/mekanism/Mekanism/26.1.2-10.8.0.86/Mekanism-26.1.2-10.8.0.86.jar"),
            original_path: Some(source_path),
            source_relative_path: None,
            source_git: None,
            source_build: None,
            hash: hash,
        }],
        Vec::new(),
    );

    let normal_report = audit_artifact_lockfile(
        &lockfile_path,
        &minecraft_dir,
        &common_cache,
        "1.19.2",
        false,
    )
    .expect("normal artifact audit should run");
    assert!(normal_report.passed);
    assert!(!normal_report.fresh_slate_portable);
    assert_eq!(normal_report.verified_artifacts, 1);
    assert_eq!(normal_report.error_count, 0);
    assert_eq!(normal_report.non_portable_artifacts, 1);
    assert!(
        normal_report.issues.iter().any(|issue| {
            issue.kind == ArtifactAuditIssueKind::NonPortableProvenance
                && issue.severity == ArtifactAuditSeverity::Warning
        }),
        "{:?}",
        normal_report.issues
    );

    let strict_report = audit_artifact_lockfile(
        &lockfile_path,
        &minecraft_dir,
        &common_cache,
        "1.19.2",
        true,
    )
    .expect("strict artifact audit should run");
    assert!(!strict_report.passed);
    assert_eq!(strict_report.error_count, 1);
    assert!(
        strict_report.issues.iter().any(|issue| {
            issue.kind == ArtifactAuditIssueKind::NonPortableProvenance
                && issue.severity == ArtifactAuditSeverity::Error
        }),
        "{:?}",
        strict_report.issues
    );
}

#[test]
fn facet_json_serializes_compare_report() {
    let report = compare_report_fixture(false);
    let json = facet_json::to_string_pretty(&report).expect("report should serialize");
    assert!(json.contains("missing_entries"));
    assert!(json.contains("ignored_implementation_timestamp"));
}

#[test]
fn compare_report_json_preserves_single_shape_and_wraps_multi_target_reports() {
    let test_dir = TestDir::new("compare-report-json-shape");
    let single_path = test_dir.path.join("single.json");
    let multi_path = test_dir.path.join("multi.json");
    let first = TargetJarCompareReport {
        branch_name: BranchName::from("1.19.2"),
        worktree_path: PathBuf::from("D:/Repos/Minecraft/SFM/repos2/1.19.2"),
        report: compare_report_fixture(true),
    };
    let second = TargetJarCompareReport {
        branch_name: BranchName::from("1.20.1"),
        worktree_path: PathBuf::from("D:/Repos/Minecraft/SFM/repos2/1.20.1"),
        report: compare_report_fixture(false),
    };

    write_compare_reports(&[first], Some(&single_path)).expect("single report should write");
    let single_json = fs::read_to_string(&single_path).expect("single report should be readable");
    assert!(single_json.contains("\"gradle_jar\""));
    assert!(!single_json.contains("\"branch_name\""));

    write_compare_reports(&[second], Some(&multi_path)).expect("single report should rewrite");
    let single_again_json =
        fs::read_to_string(&multi_path).expect("rewritten single report should be readable");
    assert!(!single_again_json.contains("\"branch_name\""));

    let multi_reports = [
        TargetJarCompareReport {
            branch_name: BranchName::from("1.19.2"),
            worktree_path: PathBuf::from("D:/Repos/Minecraft/SFM/repos2/1.19.2"),
            report: compare_report_fixture(true),
        },
        TargetJarCompareReport {
            branch_name: BranchName::from("1.20.1"),
            worktree_path: PathBuf::from("D:/Repos/Minecraft/SFM/repos2/1.20.1"),
            report: compare_report_fixture(false),
        },
    ];
    write_compare_reports(&multi_reports, Some(&multi_path)).expect("multi report should write");
    let multi_json = fs::read_to_string(&multi_path).expect("multi report should be readable");
    assert!(multi_json.trim_start().starts_with('['));
    assert!(multi_json.contains("\"branch_name\""));
    assert!(multi_json.contains("\"1.20.1\""));
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
    let libraries_root = Path::new("D:/sfm-cache/minecraft-toolchain/minecraft/libraries");
    let libraries = minecraft_library_jars_from_version_json(libraries_root, &version_json);
    assert_eq!(libraries.len(), 1);
    assert_eq!(
        libraries[0].path,
        PathBuf::from("D:/sfm-cache/minecraft-toolchain/minecraft/libraries/g/a/1/a.jar")
    );
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

    let neoforge_runs: ForgeUserdevConfig = facet_json::from_str(
        r#"{
                "runs": {
                    "clientData": {
                        "main": "net.neoforged.fml.startup.DataClient",
                        "args": ["--assetIndex", "{asset_index}"]
                    }
                }
            }"#,
    )
    .expect("neoforge clientData run config should parse");
    assert_eq!(
        neoforge_runs.runs[RunKind::Data.userdev_names()[1]].main,
        "net.neoforged.fml.startup.DataClient"
    );
    assert_eq!(RunKind::Data.userdev_names(), &["data", "clientData"]);

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

#[test]
fn datagen_launch_uses_only_bundled_library_dependency_configurations() {
    assert_eq!(run_dependency_configurations(RunKind::Data), &["jarJar"]);
    assert!(
        !run_dependency_configurations(RunKind::Data)
            .iter()
            .any(|configuration| matches!(*configuration, "implementation" | "runtimeOnly"))
    );
    assert!(
        run_dependency_configurations(RunKind::Client)
            .iter()
            .any(|configuration| configuration == &"runtimeOnly")
    );
}

#[test]
fn minecraft_library_selection_uses_only_current_version_json() {
    let version_json: MinecraftVersionJson = facet_json::from_str(
        r#"{
            "downloads": {
                "client": {"url": "https://example.test/client.jar"},
                "server": {"url": "https://example.test/server.jar"}
            },
            "libraries": [
                {
                    "downloads": {
                        "artifact": {
                            "url": "https://example.test/guava-32.jar",
                            "path": "com/google/guava/guava/32.1.2-jre/guava-32.1.2-jre.jar",
                            "sha1": "1111111111111111111111111111111111111111"
                        }
                    }
                },
                {
                    "downloads": {
                        "artifact": {
                            "url": "https://example.test/authlib-7.jar",
                            "path": "com/mojang/authlib/7.0.63/authlib-7.0.63.jar",
                            "sha1": "2222222222222222222222222222222222222222"
                        }
                    }
                }
            ]
        }"#,
    )
    .expect("version json should parse");
    let libraries_root = Path::new("D:/sfm-cache/minecraft-toolchain/minecraft/libraries");
    let libraries = minecraft_library_jars_from_version_json(libraries_root, &version_json);
    let paths = libraries
        .iter()
        .map(|library| library.path.clone())
        .collect::<Vec<_>>();

    assert_eq!(
        paths,
        vec![
            PathBuf::from(
                "D:/sfm-cache/minecraft-toolchain/minecraft/libraries/com/google/guava/guava/32.1.2-jre/guava-32.1.2-jre.jar"
            ),
            PathBuf::from(
                "D:/sfm-cache/minecraft-toolchain/minecraft/libraries/com/mojang/authlib/7.0.63/authlib-7.0.63.jar"
            ),
        ]
    );
    assert!(
        paths
            .iter()
            .all(|path| { !path.to_string_lossy().contains("com/mojang/authlib/6.0.54") })
    );
    assert!(paths.iter().all(|path| {
        !path
            .to_string_lossy()
            .contains("com/google/guava/failureaccess/1.0.1")
    }));
    assert_eq!(
        libraries[0].sha1,
        Some(legacy_sha1_hash("1111111111111111111111111111111111111111"))
    );
}

fn minimal_artifact() -> ArtifactPlan {
    ArtifactPlan {
        id: ArtifactId::from("artifact"),
        coordinate: Some("g:a:1".to_string()),
        repository: Some("Forge".to_string()),
        url: Some("https://example.test/a.jar".to_string()),
        cache_path: PathBuf::from("a.jar"),
        sha1: Some(ContentHash::from_bytes(
            b"abc123",
            ContentHashAlgorithm::Blake3,
        )),
        downloaded: true,
        required_for: ArtifactPurpose::from("test"),
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
        source_relative_path: None,
        source_git: None,
        source_build: None,
        hash: ContentHash::from_bytes(b"abc123", ContentHashAlgorithm::Blake3),
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
        artifact_sources: Vec::new(),
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
        artifact_portability: ArtifactPortabilityAudit::default(),
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
        artifact_sources: Vec::new(),
        require_portable_artifacts: false,
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

fn compare_report_fixture(matches: bool) -> JarCompareReport {
    JarCompareReport {
        gradle_jar: PathBuf::from("gradle.jar"),
        rust_jar: PathBuf::from("rust.jar"),
        strict_manifest: false,
        matches,
        total_gradle_entries: 1,
        total_rust_entries: 1,
        compared_entries: 1,
        missing_entries: if matches {
            Vec::new()
        } else {
            vec!["a.class".to_string()]
        },
        extra_entries: Vec::new(),
        changed_entries: if matches {
            Vec::new()
        } else {
            vec![ChangedEntry {
                path: "b.class".to_string(),
                gradle_hash: ContentHash::from_bytes(b"1", ContentHashAlgorithm::Blake3),
                rust_hash: ContentHash::from_bytes(b"2", ContentHashAlgorithm::Blake3),
            }]
        },
        manifest: ManifestCompare {
            compared: true,
            changed: false,
            ignored_implementation_timestamp: true,
            gradle_sha1: Some(ContentHash::from_bytes(b"1", ContentHashAlgorithm::Blake3)),
            rust_sha1: Some(ContentHash::from_bytes(b"1", ContentHashAlgorithm::Blake3)),
        },
    }
}

fn write_test_artifact_lockfile(
    path: &Path,
    artifacts: Vec<ArtifactLockEntry>,
    dependencies: Vec<DependencyLockEntry>,
) {
    let lockfile = ArtifactLockfile {
        schema_version: 1,
        minecraft_version: "1.19.2".to_string(),
        maven_cache_dir: PathBuf::from("$sfm-cache").join("maven"),
        allow_local_artifact_cache: false,
        repositories: Vec::new(),
        dependencies,
        artifacts,
    };
    fs::write(
        path,
        facet_json::to_string_pretty(&lockfile).expect("lockfile should serialize"),
    )
    .expect("lockfile should be written");
}

fn serve_http_bodies(bodies: Vec<Vec<u8>>) -> (String, thread::JoinHandle<()>) {
    let listener = TcpListener::bind("127.0.0.1:0").expect("test server should bind");
    let url = format!(
        "http://{}/artifact.jar",
        listener
            .local_addr()
            .expect("test server should have local address")
    );
    let handle = thread::spawn(move || {
        for body in bodies {
            let (mut stream, _) = listener.accept().expect("test server should accept");
            let mut request_buffer = [0_u8; 1024];
            let _ = stream.read(&mut request_buffer);
            write!(
                stream,
                "HTTP/1.1 200 OK\r\nContent-Length: {}\r\nConnection: close\r\n\r\n",
                body.len()
            )
            .expect("test server should write headers");
            stream
                .write_all(&body)
                .expect("test server should write body");
        }
    });
    (url, handle)
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

fn test_cancellation_token() -> CancellationToken {
    CancellationToken::new()
}

fn run_git<const N: usize>(repo: &Path, args: [&str; N]) {
    let output = Command::new("git")
        .arg("-C")
        .arg(repo)
        .args(args)
        .output()
        .expect("git command should start");
    assert!(
        output.status.success(),
        "git command failed: {}\nstdout:\n{}\nstderr:\n{}",
        output.status,
        String::from_utf8_lossy(&output.stdout),
        String::from_utf8_lossy(&output.stderr)
    );
}

fn git_stdout_test<const N: usize>(repo: &Path, args: [&str; N]) -> String {
    let output = Command::new("git")
        .arg("-C")
        .arg(repo)
        .args(args)
        .output()
        .expect("git command should start");
    assert!(
        output.status.success(),
        "git command failed: {}\nstdout:\n{}\nstderr:\n{}",
        output.status,
        String::from_utf8_lossy(&output.stdout),
        String::from_utf8_lossy(&output.stderr)
    );
    String::from_utf8(output.stdout)
        .expect("git stdout should be utf8")
        .trim()
        .to_string()
}

fn write_fake_gradle_wrapper(source_root: &Path, output_path: &Path) {
    let output_parent = output_path
        .parent()
        .expect("fake output should have parent")
        .to_string_lossy()
        .replace('/', "\\");
    let output_path_windows = output_path.to_string_lossy().replace('/', "\\");
    fs::write(
        source_root.join("gradlew.bat"),
        format!(
            "@echo off\r\nmkdir \"{output_parent}\" 2>NUL\r\n> \"{output_path_windows}\" echo source build artifact\r\n"
        ),
    )
    .expect("fake Windows Gradle wrapper should be written");

    #[cfg(not(windows))]
    {
        use std::os::unix::fs::PermissionsExt;

        let output_parent = output_path
            .parent()
            .expect("fake output should have parent")
            .display()
            .to_string();
        let output_path_unix = output_path.display().to_string();
        let wrapper = source_root.join("gradlew");
        fs::write(
            &wrapper,
            format!(
                "#!/bin/sh\nmkdir -p \"{output_parent}\"\nprintf 'source build artifact\\n' > \"{output_path_unix}\"\n"
            ),
        )
        .expect("fake Unix Gradle wrapper should be written");
        let mut permissions = fs::metadata(&wrapper)
            .expect("fake Unix wrapper should stat")
            .permissions();
        permissions.set_mode(0o755);
        fs::set_permissions(&wrapper, permissions).expect("fake Unix wrapper should be executable");
    }
}

struct TestDir {
    path: PathBuf,
    _dir: tempfile::TempDir,
}

impl TestDir {
    fn new(name: &str) -> Self {
        let dir = tempfile::Builder::new()
            .prefix(&format!("sfm-jar-build-engine-tests-{name}-"))
            .tempdir()
            .expect("test dir should be created");
        let path = dir.path().to_path_buf();
        Self { path, _dir: dir }
    }
}
