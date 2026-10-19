use super::ArtifactLockEntry;
use super::ArtifactLockfile;
use super::CancellationToken;
use super::MavenCoordinate;
use super::Resolver;
use super::artifact_provenance_path;
use super::maven_cache_path_for;
use super::named_neoform_coordinate_cache_path;
use crate::jar_build::source_build_receipt::RECEIPT_KEY;
use crate::jar_build::source_build_receipt::tests::resolver_fixture;
use std::fs;
use std::path::Path;

fn resolver(root: &Path, entry: ArtifactLockEntry, refresh: bool) -> eyre::Result<Resolver> {
    Resolver::new(
        root.join("maven"),
        Vec::new(),
        refresh,
        false,
        Vec::new(),
        Some(ArtifactLockfile {
            schema_version: crate::toolchain_lockfile_schema::ENGINE_SCHEMA_VERSION,
            minecraft_version: "26.1.2".into(),
            maven_cache_dir: "$sfm-cache/maven".into(),
            allow_local_artifact_cache: false,
            repositories: Vec::new(),
            dependencies: Vec::new(),
            artifacts: vec![entry],
        }),
        None,
        CancellationToken::new(),
    )
}

#[test]
fn neoform_selects_verified_producer_and_preserves_conflicting_maven_bytes() -> eyre::Result<()> {
    let (root, output, entry) = resolver_fixture();
    let coordinate = MavenCoordinate::parse(entry.coordinate.as_deref().unwrap())?;
    let maven = root.path().join("maven");
    let old = maven_cache_path_for(&maven, &coordinate);
    fs::create_dir_all(old.parent().unwrap())?;
    fs::write(&old, b"historical Maven output")?;
    let resolved = resolver(root.path(), entry, false)?;
    // Even an incoming remote flag cannot make a producer output downloadable.
    let (selected, remote) =
        named_neoform_coordinate_cache_path(&resolved, &maven, &coordinate, true)?;
    assert_eq!(selected, output);
    assert!(!remote);
    assert_eq!(fs::read(selected)?, b"fresh producer output");
    assert_eq!(fs::read(old)?, b"historical Maven output");
    assert!(!artifact_provenance_path(&output)?.exists());
    Ok(())
}

#[test]
fn neoform_nonproducer_keeps_ordinary_path_and_transport_policy() -> eyre::Result<()> {
    let (root, _, entry) = resolver_fixture();
    let resolved = resolver(root.path(), entry, false)?;
    let coordinate = MavenCoordinate::parse("fixture:ordinary:1")?;
    let maven = root.path().join("maven");
    for remote in [false, true] {
        assert_eq!(
            named_neoform_coordinate_cache_path(&resolved, &maven, &coordinate, remote)?,
            (maven_cache_path_for(&maven, &coordinate), remote)
        );
    }
    assert!(!maven.exists());
    Ok(())
}

#[test]
fn neoform_bad_or_missing_producer_never_uses_good_ordinary_fallback() -> eyre::Result<()> {
    for case in 0..7 {
        let (root, output, mut entry) = resolver_fixture();
        let coordinate = MavenCoordinate::parse(entry.coordinate.as_deref().unwrap())?;
        let maven = root.path().join("maven");
        let old = maven_cache_path_for(&maven, &coordinate);
        fs::create_dir_all(old.parent().unwrap())?;
        fs::write(&old, b"fresh producer output")?;
        let receipt_hash = entry.source_build.as_ref().unwrap().environment[RECEIPT_KEY].clone();
        let receipt = root
            .path()
            .join(format!("producer-receipts/{receipt_hash}.json"));
        match case {
            0 => fs::remove_file(&output)?,
            1 => fs::write(&output, b"wrong producer bytes")?,
            2 => fs::remove_file(&receipt)?,
            3 => fs::write(&receipt, b"{}")?,
            4 => entry.source_git.as_mut().unwrap().commit = "c".repeat(40),
            5 => entry.source_git.as_mut().unwrap().dirty = true,
            _ => {}
        }
        let resolved = resolver(root.path(), entry, case == 6)?;
        assert!(
            named_neoform_coordinate_cache_path(&resolved, &maven, &coordinate, true).is_err(),
            "invalid producer case {case} admitted ordinary-path fallback"
        );
        assert_eq!(fs::read(&old)?, b"fresh producer output");
        assert!(!artifact_provenance_path(&output)?.exists());
        assert!(!root.path().join("source-builds-gix").exists());
    }
    Ok(())
}
