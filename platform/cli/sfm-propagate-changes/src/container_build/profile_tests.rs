use super::*;
#[test]
fn internal_and_public_image_cache_profiles_do_not_collide() {
    let request = tests::request();
    let mut report = tests::report();
    assert_eq!(
        native_api::image_cache::profile(&request, &report),
        "tooling"
    );
    report.projection = "portable-dependency-cache".into();
    assert_eq!(
        native_api::image_cache::profile(&request, &report),
        "dependencies"
    );
    report.toolchain_lock_sha256 = Some("pin".into());
    for profile in [
        ContainerBuildProfile::Runtime,
        ContainerBuildProfile::Tests,
        ContainerBuildProfile::Developer,
        ContainerBuildProfile::SupportAgent,
    ] {
        let mut request = request.clone();
        request.profile = profile;
        assert_eq!(
            native_api::image_cache::profile(&request, &report),
            profile.stage()
        );
    }
}
#[test]
fn junit_summary_reuses_the_canonical_protocol_and_rejects_duplicate_or_missing() {
    use base64::Engine as _;
    let fields = ["5", "0", "0", "0", "0", "0", "3", "1"]
        .map(|s| base64::engine::general_purpose::STANDARD.encode(s))
        .join("\t");
    let log = format!("SFM_JUNIT\tsummary\t{fields}\n");
    let summary = crate::jar_build::junit_summary_from_log(&log).unwrap();
    assert_eq!(summary.tests_found, 5);
    assert_eq!(summary.tests_started, 0);
    assert_eq!(summary.containers_failed, 1);
    assert!(crate::jar_build::junit_summary_from_log(&format!("{log}{log}")).is_err());
    assert!(crate::jar_build::junit_summary_from_log("no summary").is_err());
}

#[test]
fn config_seed_changes_invalidate_cli_source_identity() -> eyre::Result<()> {
    let root = tempfile::tempdir()?;
    let source = root.path().join("platform/cli/sfm-propagate-changes/src");
    std::fs::create_dir_all(&source)?;
    std::fs::write(source.join("config_seed_data.py"), b"DATA = 'original'")?;
    let before = snapshot::cli_source_identity(root.path())?;
    std::fs::write(source.join("config_seed_data.py"), b"DATA = 'changed'")?;
    assert_ne!(before, snapshot::cli_source_identity(root.path())?);
    Ok(())
}

#[test]
fn support_catalog_exports_unselected_authored_inputs_only() -> eyre::Result<()> {
    let root = tempfile::tempdir()?;
    let stage = tempfile::tempdir()?;
    let core = root
        .path()
        .join("platform/minecraft/core-liquid-template/assets/features/other");
    std::fs::create_dir_all(&core)?;
    std::fs::write(core.join("unused.txt"), b"derivative-only source")?;
    std::fs::create_dir_all(root.path().join("private-cache"))?;
    std::fs::write(root.path().join("private-cache/token"), b"excluded")?;
    let mut report = super::tests::report();
    snapshot::export_catalog(root.path(), stage.path(), &mut report, 1024)?;
    assert_eq!(report.files.len(), 1);
    assert_eq!(
        std::fs::read(stage.path().join(
            "snapshot/platform/minecraft/core-liquid-template/assets/features/other/unused.txt"
        ))?,
        b"derivative-only source"
    );
    assert!(!stage.path().join("private-cache").exists());
    Ok(())
}
