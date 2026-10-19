use super::artifact::put_bytes;
use super::context_manifest::ContextManifest;
use crate::cancellation::CancellationToken;
use crate::jar_build::test_maven_metadata_snapshot::TestMavenMetadataSnapshot;
use crate::source_projection::candidate_lock::checked_directory;
use crate::source_projection::provenance::sha256;
use std::path::Path;
pub(super) const DERIVED_CATALOG: &str = "cache/minecraft-toolchain/maven/maven-metadata-pins.json";
pub(super) fn collect(
    text: &str,
    minecraft_version: &str,
    cache_root: &Path,
    token: &CancellationToken,
) -> eyre::Result<TestMavenMetadataSnapshot> {
    let lock = crate::toolchain_lockfile_schema::read_current(text)?;
    let maven = checked_directory(&cache_root.join("minecraft-toolchain/maven"))?;
    crate::jar_build::snapshot_test_maven_metadata(&lock, minecraft_version, &maven, token)
}
pub(super) fn stage(
    snapshot: &TestMavenMetadataSnapshot,
    output: &Path,
    report: &mut ContextManifest,
    max_bytes: u64,
) -> eyre::Result<()> {
    eyre::ensure!(
        snapshot.missing.is_empty(),
        "incomplete test metadata snapshot cannot be staged"
    );
    let parent = report.source_lock_sha256.clone();
    if let Some(catalog) = &snapshot.catalog {
        put_bytes(output, DERIVED_CATALOG, catalog, &parent, report, max_bytes)?;
        bind(
            report,
            DERIVED_CATALOG,
            &snapshot.identity,
            &snapshot.identity,
        )?;
    }
    for (path, raw) in &snapshot.files {
        eyre::ensure!(
            Path::new(path).extension() == Some(std::ffi::OsStr::new("pom")),
            "non-POM metadata export refused"
        );
        let destination = format!("cache/minecraft-toolchain/maven/{path}");
        put_bytes(output, &destination, raw, &parent, report, max_bytes)?;
        bind(report, &destination, &snapshot.identity, &sha256(raw))?;
    }
    Ok(())
}
fn bind(
    report: &mut ContextManifest,
    path: &str,
    catalog: &str,
    expected: &str,
) -> eyre::Result<()> {
    let row = report
        .files
        .get_mut(path)
        .ok_or_else(|| eyre::eyre!("metadata staging row missing: {path}"))?;
    row.expected_hash = Some(expected.into());
    row.authenticated_parents
        .insert("test-maven-metadata".into(), catalog.into());
    Ok(())
}
pub(super) fn recheck(
    text: &str,
    minecraft_version: &str,
    cache_root: &Path,
    expected: &str,
    token: &CancellationToken,
) -> eyre::Result<()> {
    let current = collect(text, minecraft_version, cache_root, token)?;
    eyre::ensure!(
        current.missing.is_empty() && current.identity == expected,
        "consumed test metadata changed during context preparation; prepare a fresh context"
    );
    Ok(())
}
