// Pure canonical POM resolution: no artifact materialization or network method is called.
pub(crate) fn snapshot_test_maven_metadata(
    lock: &crate::toolchain_lockfile_schema::version::v3::ArtifactLockfileV3,
    minecraft_version: &str,
    maven_cache: &Path,
    token: &CancellationToken,
) -> eyre::Result<super::test_maven_metadata_snapshot::TestMavenMetadataSnapshot> {
    let frozen = project_v3_artifact_lockfile(lock, minecraft_version, maven_cache)?;
    let roots = project_v3_dependencies(lock)?
        .into_iter()
        .filter(|p| {
            !p.loader_managed()
                && matches!(
                    p.configuration.as_str(),
                    "testImplementation" | "testCompileOnly" | "testRuntimeOnly"
                )
        })
        .map(|p| p.coordinate)
        .collect::<Vec<_>>();
    let resolver = Resolver::new(
        maven_cache.into(),
        frozen.repositories.clone(),
        false,
        false,
        Vec::new(),
        Some(frozen.clone()),
        Some(frozen.clone()),
        token.clone(),
    )?;
    snapshot_test_metadata_roots(&resolver, &frozen, roots, token)
}
fn snapshot_test_metadata_roots(
    resolver: &Resolver,
    frozen: &ArtifactLockfile,
    roots: Vec<MavenCoordinate>,
    token: &CancellationToken,
) -> eyre::Result<super::test_maven_metadata_snapshot::TestMavenMetadataSnapshot> {
    let mut trace = maven_metadata_trace::MavenMetadataTrace::default();
    let mut seen = BTreeSet::new();
    let mut queue = VecDeque::from(roots);
    let mut missing = BTreeMap::new();
    while let Some(coordinate) = queue.pop_front() {
        token.bail_if_cancelled()?;
        if !seen.insert(coordinate.to_string()) {
            continue;
        }
        eyre::ensure!(
            seen.len() <= 128,
            "test metadata snapshot artifact graph budget exceeded"
        );
        let pinned = frozen
            .artifacts
            .iter()
            .filter(|p| p.coordinate.as_deref() == Some(coordinate.to_string().as_str()))
            .collect::<Vec<_>>();
        if pinned.is_empty()
            || pinned
                .iter()
                .any(|p| p.weak.is_some() || p.hash != pinned[0].hash)
        {
            missing.insert(coordinate.to_string(), format!("offline frozen test artifact pin absent, weak or ambiguous: {coordinate}; freeze this exact test artifact separately"));
            continue;
        }
        match resolver.resolve_pom_runtime_dependencies_export(&coordinate, &mut trace) {
            Ok(dependencies) => queue.extend(dependencies),
            Err(error) => {
                missing.insert(coordinate.to_string(), format!("{error:#}"));
            }
        }
    }
    let catalog = trace.catalog()?;
    let identity = crate::source_projection::provenance::sha256(
        catalog
            .as_deref()
            .unwrap_or(b"sfm:empty_test_maven_metadata@1"),
    );
    let files = trace
        .entries
        .into_values()
        .map(|p| (p.pin.path, p.raw))
        .collect();
    Ok(
        super::test_maven_metadata_snapshot::TestMavenMetadataSnapshot {
            identity,
            catalog,
            files,
            missing,
        },
    )
}
