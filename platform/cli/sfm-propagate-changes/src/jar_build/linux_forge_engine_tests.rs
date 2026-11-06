use super::*;
use std::io::Write as _;

#[test]
fn linux_forge_remap_checks_every_entry_crc_and_cancellation() -> eyre::Result<()> {
    let fixture = tempfile::tempdir()?;
    let path = fixture.path().join("remapped.jar");
    let mut zip = zip::ZipWriter::new(fs::File::create(&path)?);
    zip.start_file(
        "asset.bin",
        zip::write::SimpleFileOptions::default().compression_method(zip::CompressionMethod::Stored),
    )?;
    zip.write_all(b"fixture non-class resource payload")?;
    zip.finish()?;
    let cancel = CancellationToken::new();
    verify_remap_jar(&path, &cancel)?;
    let mut bytes = fs::read(&path)?;
    let offset = bytes
        .windows(b"fixture non-class resource payload".len())
        .position(|s| s == b"fixture non-class resource payload")
        .unwrap();
    bytes[offset] ^= 1;
    fs::write(&path, bytes)?;
    assert!(verify_remap_jar(&path, &cancel).is_err());
    cancel.request_cancel("cancel before archive read");
    let cancelled = verify_remap_jar(&path, &cancel).unwrap_err();
    assert_eq!(cancelled.to_string(), "cancel before archive read");
    Ok(())
}

#[test]
fn linux_forge_admission_requires_checked_catalog_managed_root_and_exact_owned_paths()
-> eyre::Result<()> {
    let (fixture, slot, recipe, _) =
        super::super::named_forge_compile_cohort_tests::fixture("1.19.2")?;
    let project = super::super::named_forge_compile_cohort_tests::prepared(
        &fixture, slot, "release", &recipe,
    )?;
    let mut plan = super::super::tests::minimal_plan_for_paths();
    plan.minecraft_dir = project.project().project_root().to_path_buf();
    plan.identity = BuildProjectIdentity::Catalog {
        frozen: std::sync::Arc::new(project),
    };
    assert!(!eligible(&plan));
    let temporary = tempfile::tempdir()?;
    let cancel = CancellationToken::new();
    let root_owner = super::super::native_cache_root::NativeCacheRoot::acquire_for_source(
        &temporary.path().join("native"),
        fixture.repository(),
        &plan.minecraft_dir,
        &cancel,
    )?;
    plan.derived_cache_root = Some(root_owner.path().to_path_buf());
    assert!(eligible(&plan));
    let context = super::super::all_hit_compile_tests::context(&plan);
    let directory =
        crate::jar_build::local_dependency_cache::linux_io::Directory::open(temporary.path())?;
    let owned = OwnedDependencyRemapOutput {
        directory: &directory,
    };
    for (output, diagnostic, mapping) in [
        (
            temporary.path().join("foreign.jar"),
            temporary.path().join("diagnostics"),
            context.forge_mapping_path("srg_to_official.tsrg"),
        ),
        (
            temporary.path().join("specialsource.jar"),
            temporary.path().join("foreign"),
            context.forge_mapping_path("srg_to_official.tsrg"),
        ),
        (
            temporary.path().join("specialsource.jar"),
            temporary.path().join("diagnostics"),
            temporary.path().join("foreign.tsrg"),
        ),
    ] {
        let rejected = owned
            .require(&context, &output, &diagnostic, &mapping)
            .unwrap_err();
        assert_eq!(
            rejected.to_string(),
            "owned remap output/mapping role differs"
        );
    }
    Ok(())
}
