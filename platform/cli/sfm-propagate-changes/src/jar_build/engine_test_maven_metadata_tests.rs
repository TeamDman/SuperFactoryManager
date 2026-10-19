use super::*;
fn metadata_fixture() -> eyre::Result<(
    tempfile::TempDir,
    Resolver,
    ArtifactLockfile,
    Vec<MavenCoordinate>,
)> {
    let temp = tempfile::tempdir()?;
    let cache = temp.path().join("maven");
    fs::create_dir(&cache)?;
    let repository = "https://repo.maven.apache.org/maven2/";
    let poms = [
        (
            "parent",
            "1",
            r"<project><modelVersion>4.0.0</modelVersion><groupId>org.example</groupId><artifactId>parent</artifactId><version>1</version><properties><lib.version>2</lib.version></properties></project>",
        ),
        (
            "bom",
            "1",
            r"<project><modelVersion>4.0.0</modelVersion><parent><groupId>org.example</groupId><artifactId>parent</artifactId><version>1</version></parent><artifactId>bom</artifactId><version>1</version><dependencyManagement><dependencies><dependency><groupId>org.example</groupId><artifactId>lib</artifactId><version>${lib.version}</version></dependency></dependencies></dependencyManagement></project>",
        ),
        (
            "root",
            "1",
            r"<project><modelVersion>4.0.0</modelVersion><parent><groupId>org.example</groupId><artifactId>parent</artifactId><version>1</version></parent><artifactId>root</artifactId><version>1</version><dependencyManagement><dependencies><dependency><groupId>org.example</groupId><artifactId>bom</artifactId><version>1</version><type>pom</type><scope>import</scope></dependency></dependencies></dependencyManagement><dependencies><dependency><groupId>org.example</groupId><artifactId>lib</artifactId></dependency></dependencies></project>",
        ),
        (
            "lib",
            "2",
            r"<project><modelVersion>4.0.0</modelVersion><groupId>org.example</groupId><artifactId>lib</artifactId><version>2</version></project>",
        ),
        (
            "unused",
            "9",
            r"<project><modelVersion>4.0.0</modelVersion><groupId>org.example</groupId><artifactId>unused</artifactId><version>9</version></project>",
        ),
    ];
    let mut pins = Vec::new();
    for (artifact, version, raw) in poms {
        let coordinate = MavenCoordinate::parse(&format!("org.example:{artifact}:{version}@pom"))?;
        let path = maven_cache_path_for(&cache, &coordinate);
        fs::create_dir_all(
            path.parent()
                .ok_or_else(|| eyre::eyre!("fixture parent missing"))?,
        )?;
        fs::write(&path, raw)?;
        let relative = path
            .strip_prefix(&cache)?
            .to_string_lossy()
            .replace('\\', "/");
        pins.push(maven_metadata_pin::MavenMetadataPin {
            coordinate: coordinate.to_string(),
            url: format!("{repository}{relative}"),
            sha256: crate::source_projection::provenance::sha256(raw.as_bytes()),
            bytes: raw.len() as u64,
            path: relative,
            retrieved_utc: "2026-10-10T00:00:00Z".into(),
        });
    }
    let catalog = maven_metadata_catalog::MavenMetadataCatalog {
        schema: "sfm:maven_metadata_provision@1".into(),
        repository: repository.into(),
        artifacts: pins,
    };
    fs::write(
        cache.join("maven-metadata-pins.json"),
        facet_json::to_string(&catalog)?,
    )?;
    let coordinates = ["org.example:root:1", "org.example:lib:2"]
        .into_iter()
        .map(MavenCoordinate::parse)
        .collect::<eyre::Result<Vec<_>>>()?;
    let artifacts = coordinates
        .iter()
        .map(|c| ArtifactLockEntry {
            coordinate: Some(c.to_string()),
            source: ArtifactSource::RemoteMaven,
            repository: Some("central".into()),
            url: None,
            cache_path: maven_cache_path_for(&cache, c),
            original_path: None,
            source_relative_path: None,
            source_git: None,
            source_build: None,
            hash: ContentHash::from_bytes(
                b"synthetic frozen jar witness",
                ContentHashAlgorithm::Blake3,
            ),
            weak: None,
        })
        .collect();
    let frozen = ArtifactLockfile {
        schema_version: 2,
        minecraft_version: "fixture".into(),
        maven_cache_dir: cache.clone(),
        allow_local_artifact_cache: false,
        repositories: vec![Repository {
            name: "central".into(),
            url: repository.into(),
        }],
        dependencies: Vec::new(),
        artifacts,
    };
    let resolver = Resolver::new(
        cache,
        frozen.repositories.clone(),
        false,
        false,
        Vec::new(),
        Some(frozen.clone()),
        Some(frozen.clone()),
        CancellationToken::new(),
    )?;
    Ok((temp, resolver, frozen, vec![coordinates[0].clone()]))
}
#[test]
fn consumed_metadata_exports_parent_bom_and_transitive_but_omits_unused_rows() -> eyre::Result<()> {
    let (_temp, resolver, frozen, roots) = metadata_fixture()?;
    let snapshot =
        snapshot_test_metadata_roots(&resolver, &frozen, roots, &CancellationToken::new())?;
    assert!(snapshot.missing.is_empty(), "{:?}", snapshot.missing);
    assert_eq!(snapshot.files.len(), 4);
    assert!(snapshot.files.keys().all(|p| !p.contains("unused")));
    let catalog: maven_metadata_catalog::MavenMetadataCatalog = facet_json::from_slice(
        snapshot
            .catalog
            .as_deref()
            .ok_or_else(|| eyre::eyre!("catalog missing"))?,
    )?;
    assert_eq!(catalog.artifacts.len(), 4);
    assert!(
        catalog
            .artifacts
            .iter()
            .all(|p| !p.coordinate.contains("unused"))
    );
    Ok(())
}
#[test]
fn missing_parent_and_corrupt_consumed_metadata_are_precise_offline_failures() -> eyre::Result<()> {
    let (_temp, resolver, frozen, roots) = metadata_fixture()?;
    let parent = MavenCoordinate::parse("org.example:parent:1@pom")?;
    fs::remove_file(maven_cache_path_for(&frozen.maven_cache_dir, &parent))?;
    let snapshot =
        snapshot_test_metadata_roots(&resolver, &frozen, roots, &CancellationToken::new())?;
    let reason = snapshot
        .missing
        .values()
        .next()
        .ok_or_else(|| eyre::eyre!("missing failure"))?;
    assert!(reason.contains("parent"), "{reason}");
    let (_temp, resolver, frozen, roots) = metadata_fixture()?;
    let root = MavenCoordinate::parse("org.example:root:1@pom")?;
    fs::write(
        maven_cache_path_for(&frozen.maven_cache_dir, &root),
        b"corrupted",
    )?;
    let snapshot =
        snapshot_test_metadata_roots(&resolver, &frozen, roots, &CancellationToken::new())?;
    assert!(
        snapshot
            .missing
            .values()
            .any(|reason| reason.contains("hash/size mismatch"))
    );
    Ok(())
}
#[test]
fn unconsumed_metadata_changes_do_not_invalidate_the_reduced_catalog() -> eyre::Result<()> {
    let (_temp, resolver, frozen, roots) = metadata_fixture()?;
    let before =
        snapshot_test_metadata_roots(&resolver, &frozen, roots.clone(), &CancellationToken::new())?;
    let catalog_path = frozen.maven_cache_dir.join("maven-metadata-pins.json");
    let mut catalog: maven_metadata_catalog::MavenMetadataCatalog =
        facet_json::from_slice(&fs::read(&catalog_path)?)?;
    let row = catalog
        .artifacts
        .iter_mut()
        .find(|p| p.coordinate.contains("unused"))
        .ok_or_else(|| eyre::eyre!("unused pin missing"))?;
    row.retrieved_utc = "2026-10-11T00:00:00Z".into();
    fs::write(catalog_path, facet_json::to_string(&catalog)?)?;
    let after = snapshot_test_metadata_roots(&resolver, &frozen, roots, &CancellationToken::new())?;
    assert_eq!(before.identity, after.identity);
    assert_eq!(before.catalog, after.catalog);
    Ok(())
}
#[test]
fn changed_consumed_pin_invalidates_identity_and_missing_artifact_pin_is_reported()
-> eyre::Result<()> {
    let (_temp, resolver, mut frozen, roots) = metadata_fixture()?;
    let before =
        snapshot_test_metadata_roots(&resolver, &frozen, roots.clone(), &CancellationToken::new())?;
    let catalog_path = frozen.maven_cache_dir.join("maven-metadata-pins.json");
    let mut catalog: maven_metadata_catalog::MavenMetadataCatalog =
        facet_json::from_slice(&fs::read(&catalog_path)?)?;
    let row = catalog
        .artifacts
        .iter_mut()
        .find(|p| p.coordinate.contains(":root:"))
        .ok_or_else(|| eyre::eyre!("root pin missing"))?;
    row.retrieved_utc = "2026-10-11T00:00:00Z".into();
    fs::write(catalog_path, facet_json::to_string(&catalog)?)?;
    let after =
        snapshot_test_metadata_roots(&resolver, &frozen, roots.clone(), &CancellationToken::new())?;
    assert_ne!(before.identity, after.identity);
    frozen
        .artifacts
        .retain(|p| p.coordinate.as_deref() != Some("org.example:lib:2"));
    let after = snapshot_test_metadata_roots(&resolver, &frozen, roots, &CancellationToken::new())?;
    assert!(after.missing.contains_key("org.example:lib:2"));
    Ok(())
}

#[test]
fn invalid_provenance_timestamp_is_refused_before_export() -> eyre::Result<()> {
    let (_temp, resolver, frozen, roots) = metadata_fixture()?;
    let path = frozen.maven_cache_dir.join("maven-metadata-pins.json");
    let mut catalog: maven_metadata_catalog::MavenMetadataCatalog =
        facet_json::from_slice(&fs::read(&path)?)?;
    let row = catalog
        .artifacts
        .iter_mut()
        .find(|p| p.coordinate.contains(":root:"))
        .ok_or_else(|| eyre::eyre!("root pin missing"))?;
    row.retrieved_utc = "synthetic-invalid-date".into();
    fs::write(path, facet_json::to_string(&catalog)?)?;
    let snapshot =
        snapshot_test_metadata_roots(&resolver, &frozen, roots, &CancellationToken::new())?;
    assert!(
        snapshot
            .missing
            .values()
            .any(|reason| reason.contains("metadata retrieval timestamp"))
    );
    assert!(snapshot.files.keys().all(|path| !path.contains("root")));
    Ok(())
}
