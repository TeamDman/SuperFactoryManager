use super::artifact::copy_cached;
use super::artifact::portable_cache_path;
use super::context_file::ContextFile;
use super::*;
use crate::cancellation::CancellationToken;
use crate::jar_build::hash::ContentHash;
use crate::jar_build::hash::ContentHashAlgorithm;
use crate::source_projection::provenance::sha256;
use std::collections::BTreeMap;
use std::fs;
use std::path::Path;
use std::path::PathBuf;
use std::time::Duration;

pub(super) fn request() -> ContainerBuildRequest {
    ContainerBuildRequest {
        projection: "sfm-dev/mc-1.19.2".into(),
        profile: ContainerBuildProfile::Runtime,
        repo_root: PathBuf::from("repo"),
        context: PathBuf::from("context"),
        toolchain_lock: None,
        cache_root: PathBuf::from("cache"),
        platform: "linux/amd64".into(),
        engine: ContainerEngine::Podman,
        transport: ContainerTransport::Command,
        engine_endpoint: None,
        tag: "sfm-puppet:local".into(),
        prepare_only: true,
        reuse_context: false,
        offline: true,
        max_bytes: 1024,
        timeout: Duration::from_secs(1),
    }
}
pub(super) fn report() -> ContextManifest {
    ContextManifest {
        schema: "sfm:container_context@1".into(),
        projection: "sfm-dev/mc-1.19.2".into(),
        platform: "linux/amd64".into(),
        ownership_identity: "owner".into(),
        source_lock_sha256: "lock".into(),
        toolchain_lock_sha256: None,
        base_image: None,
        context_identity: None,
        test_maven_metadata_identity: None,
        files: BTreeMap::new(),
        total_bytes: 0,
        missing: Vec::new(),
        context_prepared: false,
        image_built: false,
        image_identity: None,
        gameplay_validated: false,
        dependency_layer: None,
    }
}
#[test]
fn offline_platform_tag_and_budget_are_checked_before_effects() {
    let mut request = request();
    assert!(request.validate().is_ok());
    request.offline = false;
    assert!(request.validate().is_ok());
    request.offline = true;
    request.platform = "windows/amd64".into();
    assert!(request.validate().is_err());
    request.platform = "linux/amd64".into();
    request.tag = "--privileged".into();
    assert!(request.validate().is_err());
}
#[test]
fn cache_allowlist_rejects_host_paths_credentials_and_windows_binaries() {
    for path in [
        "C:/Users/user/.minecraft/accounts.json",
        "$sfm-cache/../secrets",
        "$sfm-cache/home/token",
        "$sfm-cache/maven/java.exe",
        "$sfm-cache/maven/native.dll",
        "$sfm-cache/maven/native.DLL",
        "$sfm-cache/maven/Home/token",
    ] {
        assert!(portable_cache_path(Path::new(path)).is_err(), "{path}");
    }
    assert_eq!(
        portable_cache_path(Path::new("$sfm-cache\\maven\\org\\example.jar")).unwrap(),
        "maven/org/example.jar"
    );
}
#[test]
fn corrupt_and_missing_cache_rows_accumulate_without_publication() {
    let cache = tempfile::tempdir().unwrap();
    let stage = tempfile::tempdir().unwrap();
    fs::write(cache.path().join("artifact.jar"), b"bad").unwrap();
    let expected = ContentHash::from_bytes(b"good", ContentHashAlgorithm::Sha1);
    let mut report = report();
    let token = CancellationToken::new();
    for input in ["artifact.jar", "missing.jar"] {
        assert!(
            !copy_cached(
                cache.path(),
                input,
                stage.path(),
                input,
                expected,
                None,
                "frozen-lock",
                &mut report,
                1024,
                &token
            )
            .unwrap()
        );
    }
    assert_eq!(report.missing.len(), 2);
    assert!(report.files.is_empty());
    assert!(!report.context_prepared);
}
#[test]
fn exported_bytes_bind_both_original_pin_and_sha256() {
    let cache = tempfile::tempdir().unwrap();
    let stage = tempfile::tempdir().unwrap();
    fs::write(cache.path().join("artifact.jar"), b"good").unwrap();
    let expected = ContentHash::from_bytes(b"good", ContentHashAlgorithm::Blake3);
    let mut report = report();
    assert!(
        copy_cached(
            cache.path(),
            "artifact.jar",
            stage.path(),
            "cache/artifact.jar",
            expected,
            Some(4),
            "lock",
            &mut report,
            1024,
            &CancellationToken::new()
        )
        .unwrap()
    );
    assert_eq!(report.files["cache/artifact.jar"].sha256, sha256(b"good"));
    assert_eq!(
        report.files["cache/artifact.jar"].expected_hash,
        Some(expected.to_string())
    );
}
#[test]
fn one_cached_blob_records_two_authenticated_hash_parents() {
    let cache = tempfile::tempdir().unwrap();
    let stage = tempfile::tempdir().unwrap();
    fs::write(cache.path().join("artifact.jar"), b"good").unwrap();
    let mut report = report();
    for (algorithm, parent) in [
        (ContentHashAlgorithm::Blake3, "frozen-lock"),
        (ContentHashAlgorithm::Sha1, "version-json"),
    ] {
        assert!(
            copy_cached(
                cache.path(),
                "artifact.jar",
                stage.path(),
                "cache/artifact.jar",
                ContentHash::from_bytes(b"good", algorithm),
                Some(4),
                parent,
                &mut report,
                1024,
                &CancellationToken::new()
            )
            .unwrap()
        );
    }
    assert_eq!(report.files.len(), 1);
    assert_eq!(report.total_bytes, 4);
    assert_eq!(
        report.files["cache/artifact.jar"]
            .authenticated_parents
            .len(),
        2
    );
}
#[test]
fn backend_has_no_network_pull_mount_push_or_runtime_invocation() {
    let mut request = request();
    request.prepare_only = false;
    for engine in [ContainerEngine::Podman, ContainerEngine::Docker] {
        request.engine = engine;
        let args = backend::build_arguments(&request)
            .iter()
            .map(|arg| arg.to_string_lossy().into_owned())
            .collect::<Vec<_>>();
        assert!(args.contains(&"--network=none".into()));
        assert!(args.contains(&if engine == ContainerEngine::Podman {
            "--pull=never".into()
        } else {
            "--pull=false".into()
        }));
        assert!(!args.iter().any(|arg| {
            ["--mount", "--volume", "--privileged", "push", "run"].contains(&arg.as_str())
        }));
    }
}
#[test]
fn backend_refuses_incomplete_or_tampered_context_and_unlisted_files() {
    let stage = tempfile::tempdir().unwrap();
    let mut report = report();
    assert!(backend::verify_context(stage.path(), &report).is_err());
    report.context_prepared = true;
    report.context_identity = Some("identity".into());
    report.files.insert(
        "Dockerfile".into(),
        ContextFile {
            path: "Dockerfile".into(),
            bytes: 4,
            sha256: sha256(b"test"),
            parent_identity: "contract".into(),
            expected_hash: None,
            authenticated_parents: BTreeMap::new(),
        },
    );
    fs::write(stage.path().join("Dockerfile"), b"test").unwrap();
    fs::write(
        stage.path().join("context-manifest.json"),
        facet_json::to_string_pretty(&report).unwrap(),
    )
    .unwrap();
    assert!(backend::verify_context(stage.path(), &report).is_ok());
    fs::write(stage.path().join("accounts.json"), b"secret").unwrap();
    assert!(backend::verify_context(stage.path(), &report).is_err());
    fs::remove_file(stage.path().join("accounts.json")).unwrap();
    fs::write(stage.path().join("Dockerfile"), b"evil").unwrap();
    assert!(backend::verify_context(stage.path(), &report).is_err());
}
#[test]
fn linux_elf_rejects_windows_exe_and_wrong_architecture() {
    assert!(!planner::linux_elf(b"MZ Windows executable"));
    let mut elf = [0_u8; 64];
    elf[..7].copy_from_slice(&[0x7f, b'E', b'L', b'F', 2, 1, 1]);
    elf[18] = 62;
    assert!(planner::linux_elf(&elf));
    elf[18] = 183;
    assert!(!planner::linux_elf(&elf));
}
#[test]
fn missing_contract_is_a_typed_plan_error_and_creates_no_context() {
    use crate::source_projection::catalog_owned_project::tests::Fixture;
    let fixture = Fixture::new_for_target("1.19.2");
    let key = Fixture::key(0, "release");
    fixture.publish(&key);
    let output = tempfile::tempdir().unwrap();
    let mut request = request();
    request.projection = key;
    request.repo_root = fixture.repository().to_path_buf();
    request.context = output.path().join("must-not-exist");
    let result = execute(&request, &CancellationToken::new()).unwrap();
    assert!(!result.context_prepared && !result.image_built && !result.gameplay_validated);
    assert!(
        result
            .missing
            .iter()
            .any(|input| input.identity == "linux-toolchain-contract")
    );
    assert!(!request.context.exists());
}

#[test]
fn snapshot_uses_checked_catalog_receipt_without_obsolete_owner_manifest() {
    use crate::source_projection::catalog_owned_project::tests::Fixture;
    let fixture = Fixture::new_for_target("1.19.2");
    let key = Fixture::key(0, "release");
    let project_root = fixture.publish(&key);
    assert!(
        !project_root
            .join(".sfm-source-projection-manifest.json")
            .exists()
    );
    let cli = fixture.repository().join("platform/cli/sfm");
    fs::create_dir_all(cli.join("src")).unwrap();
    fs::write(
        cli.join("Cargo.toml"),
        b"[package]\nname='fixture'\nversion='0.1.0'\n",
    )
    .unwrap();
    fs::write(cli.join("Cargo.lock"), b"version = 4\n").unwrap();
    fs::write(cli.join("src/main.rs"), b"fn main() {}\n").unwrap();
    for relative in super::public_test_fixtures::PATHS {
        let path = fixture.repository().join(relative);
        fs::create_dir_all(path.parent().unwrap()).unwrap();
        fs::write(path, b"public fixture\n").unwrap();
    }
    let project = fixture.collect(&key).unwrap().check_current().unwrap();
    let stage = tempfile::tempdir().unwrap();
    let mut report = report();
    super::snapshot::export(&project, stage.path(), &mut report, 16 * 1024 * 1024).unwrap();
    assert!(
        report
            .files
            .contains_key("snapshot/platform/minecraft/projections.json")
    );
    assert!(
        report
            .files
            .contains_key("snapshot/platform/cli/sfm/src/main.rs")
    );
    assert!(
        !report
            .files
            .keys()
            .any(|path| path.contains(".sfm-source-projection-manifest"))
    );
    assert!(report.files.contains_key("snapshot/container-inputs.json"));
    for relative in super::public_test_fixtures::PATHS {
        assert!(report.files.contains_key(&format!("snapshot/{relative}")));
    }
}

/// Opt-in, zero-download smoke build against the already-running local rootless engine.
#[test]
#[ignore = "requires an explicitly selected local Podman pipe and cached reviewed graphics image"]
fn native_api_bounded_offline_isolation_probe() {
    let endpoint = std::env::var_os("SFM_CONTAINER_ENGINE_PROBE")
        .expect("explicit local pipe/socket required");
    let stage = tempfile::tempdir().unwrap();
    let recipe = br#"FROM localhost/sfm-graphics@sha256:9f7f1c4b8cbb7452c3fa5bb30822ee112f6ea446e2f391e7e45ee20b13217b84
SHELL ["/usr/bin/setpriv", "--no-new-privs", "/bin/sh", "-c"]
USER 10001:10001
RUN set -eu; test "$(id -u)" = 10001; test "$(awk '/^CapEff:/ {print $2}' /proc/self/status)" = 0000000000000000; test "$(awk '/^Seccomp:/ {print $2}' /proc/self/status)" = 2; test "$(awk '/^NoNewPrivs:/ {print $2}' /proc/self/status)" = 1; test "$(cat /sys/fs/cgroup/memory.max)" = 6442450944; test "$(cat /sys/fs/cgroup/cpu.max)" = "200000 100000"; test "$(ls /sys/class/net)" = lo; id; cat /sys/fs/cgroup/memory.max /sys/fs/cgroup/cpu.max; awk '/^(CapEff|NoNewPrivs|Seccomp):/' /proc/self/status; readlink /proc/self/ns/net /proc/self/ns/pid; echo SFM_OFFLINE_ISOLATION_OK
"#;
    let mut report = report();
    report.context_prepared = true;
    report.context_identity = Some(sha256(recipe));
    report.total_bytes = recipe.len() as u64;
    report.files.insert(
        "Dockerfile".into(),
        ContextFile {
            path: "Dockerfile".into(),
            bytes: recipe.len() as u64,
            sha256: sha256(recipe),
            parent_identity: "bounded-isolation-probe-v1".into(),
            expected_hash: None,
            authenticated_parents: BTreeMap::new(),
        },
    );
    fs::write(stage.path().join("Dockerfile"), recipe).unwrap();
    fs::write(
        stage.path().join("context-manifest.json"),
        facet_json::to_string_pretty(&report).unwrap(),
    )
    .unwrap();
    let mut request = request();
    request.context = stage.path().into();
    request.engine_endpoint = Some(endpoint.into());
    request.transport = ContainerTransport::NativeApi;
    request.tag = format!("sfm-native-isolation-probe:{:016x}", rand::random::<u64>());
    request.timeout = Duration::from_secs(90);
    let identity = backend::build(
        &request,
        &report,
        &CancellationToken::new(),
        request.timeout,
    )
    .unwrap()
    .unwrap();
    assert_eq!(identity.os, "linux");
    assert_eq!(identity.architecture, "amd64");
    assert_eq!(
        identity.manifest_media_type,
        "application/vnd.oci.image.manifest.v1+json"
    );
    eprintln!(
        "SFM_PROBE_IMAGE {} {}",
        request.tag,
        facet_json::to_string(&identity).unwrap()
    );
}

#[test]
fn minecraft_java_edits_leave_rust_source_identity_unchanged() {
    let tmp = tempfile::tempdir().unwrap();
    let cli = tmp.path().join("platform/cli/sfm-propagate-changes/src");
    fs::create_dir_all(&cli).unwrap();
    fs::write(cli.join("main.rs"), "fn main() {}").unwrap();
    let before = snapshot::cli_source_identity(tmp.path()).unwrap();
    let java = tmp
        .path()
        .join("platform/minecraft/core-liquid-template/src/main/java");
    fs::create_dir_all(&java).unwrap();
    fs::write(java.join("Example.java"), "class Example {}").unwrap();
    assert_eq!(before, snapshot::cli_source_identity(tmp.path()).unwrap());
    fs::write(cli.join("main.rs"), "fn main() {println!(\"changed\");}").unwrap();
    assert_ne!(before, snapshot::cli_source_identity(tmp.path()).unwrap());
}
#[test]
fn disk_admission_refuses_impossible_capacity_without_cleanup() {
    let tmp = tempfile::tempdir().unwrap();
    fs::write(tmp.path().join("keep"), "preserve").unwrap();
    assert!(disk::check(tmp.path(), u64::MAX).is_err());
    assert_eq!(
        fs::read_to_string(tmp.path().join("keep")).unwrap(),
        "preserve"
    );
}

#[test]
fn content_reuse_hashes_objects_and_rejects_shared_link_tampering() {
    let temp = tempfile::tempdir().unwrap();
    let stage = temp.path().join("stage");
    fs::create_dir(&stage).unwrap();
    super::content_store::bytes(&stage, "first", b"verified input").unwrap();
    super::content_store::bytes(&stage, "second", b"verified input").unwrap();
    assert_eq!(fs::read(stage.join("second")).unwrap(), b"verified input");
    // A hardlink is an optimization, never an immutability claim.
    let store = temp
        .path()
        .join(".sfm-container-blobs")
        .join(&sha256(b"verified input")[7..]);
    fs::write(store, b"tampered input").unwrap();
    assert!(super::content_store::bytes(&stage, "third", b"verified input").is_err());
    assert!(!stage.join("third").exists());
}
#[test]
fn control_rust_identity_is_independent_from_java_and_main_rust() {
    let temp = tempfile::tempdir().unwrap();
    let main = temp.path().join("platform/cli/sfm-propagate-changes/src");
    let control = temp.path().join("platform/cli/sfm/src");
    fs::create_dir_all(&main).unwrap();
    fs::create_dir_all(&control).unwrap();
    fs::write(main.join("main.rs"), b"fn main() {}").unwrap();
    fs::write(control.join("main.rs"), b"fn main() {}").unwrap();
    let main_before = super::snapshot::cli_source_identity(temp.path()).unwrap();
    let control_before = super::snapshot::cli_inputs(temp.path(), "platform/cli/sfm").unwrap();
    fs::write(
        control.join("main.rs"),
        b"fn main() {println!(\"control\");}",
    )
    .unwrap();
    assert_eq!(
        main_before,
        super::snapshot::cli_source_identity(temp.path()).unwrap()
    );
    assert_ne!(
        control_before,
        super::snapshot::cli_inputs(temp.path(), "platform/cli/sfm").unwrap()
    );
}

#[test]
fn existing_context_binding_rejects_manifest_and_provenance_tampering() {
    let mut input = report();
    input.context_prepared = true;
    input.context_identity = Some(sha256(facet_json::to_string(&input).unwrap().as_bytes()));
    super::existing_context::verify_binding(&mut input).unwrap();
    let identity = input.context_identity.clone();
    input.source_lock_sha256 = "changed-lock".into();
    assert!(super::existing_context::verify_binding(&mut input).is_err());
    assert_eq!(input.context_identity, identity);
    input.source_lock_sha256 = "lock".into();
    input.files.insert(
        "snapshot/added.java".into(),
        ContextFile {
            path: "snapshot/added.java".into(),
            sha256: sha256(b"added"),
            bytes: 5,
            parent_identity: "owner".into(),
            expected_hash: None,
            authenticated_parents: BTreeMap::new(),
        },
    );
    assert!(super::existing_context::verify_binding(&mut input).is_err());
}
