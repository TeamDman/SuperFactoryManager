use crate::artifact_lock::ArtifactLock;
use crate::cancellation::CancellationToken;
use crate::dependency_inventory::AcquisitionStatus;
use crate::dependency_inventory::DependencyInventory;
use crate::jar_build::hash::ContentHash;
use crate::jar_build::hash::ContentHashAlgorithm;
use crate::payload_fetcher::PayloadFetcher;
use crate::payload_fetcher::write_payload_atomically;
use crate::source_archive::extract_zip_atomically;
use crate::source_cache::SourceCacheLayout;
use crate::toolchain_lockfile_schema::version::v3::ComponentAcquisitionV3;
use crate::toolchain_lockfile_schema::version::v3::DependencyComponentV3;
use crate::toolchain_lockfile_schema::version::v3::MavenSourceDeclarationV3;
use crate::toolchain_lockfile_schema::version::v3::MavenSourceDerivedChecksV3;
use crate::toolchain_lockfile_schema::version::v3::MavenSourceProviderV3;
use std::path::Path;

pub(crate) fn configure_maven_sources(
    inventory: &DependencyInventory,
    component: &DependencyComponentV3,
    coordinate_override: Option<&str>,
    roots: Vec<String>,
    cancellation_token: &CancellationToken,
    fetcher: &dyn PayloadFetcher,
) -> eyre::Result<Option<MavenSourceProviderV3>> {
    let ComponentAcquisitionV3::Maven(acquisition) = &component.declaration.acquisition else {
        eyre::bail!("Maven sources require a Maven dependency component");
    };
    let binary_coordinate = component
        .derived_checks
        .resolved_coordinate
        .as_deref()
        .unwrap_or(&acquisition.requested_coordinate);
    let coordinate = coordinate_override.map_or_else(
        || derive_sources_coordinate(binary_coordinate),
        |coordinate| parse_coordinate(coordinate).map(|parsed| parsed.canonical()),
    )?;
    let repository = inventory
        .lockfile
        .repositories
        .iter()
        .find(|repository| repository.id == acquisition.repository_id)
        .ok_or_else(|| eyre::eyre!("Unknown repository '{}'", acquisition.repository_id))?;
    let url = coordinate_url(&repository.url, &coordinate)?;
    let Some(bytes) = fetcher.fetch(&url, cancellation_token)? else {
        return Ok(None);
    };
    cancellation_token.bail_if_cancelled()?;
    let hash = ContentHash::from_bytes(&bytes, ContentHashAlgorithm::Blake3);
    let paths = SourceCacheLayout::maven(&coordinate, hash);
    let archive = inventory.local_path(&paths.archive);
    let tree = inventory.local_path(&paths.tree);
    let _lock = acquire_source_lock(&tree)?;
    write_payload_atomically(&archive, &bytes)?;
    extract_zip_atomically(&archive, &tree)?;
    validate_roots(&tree, &roots)?;

    Ok(Some(MavenSourceProviderV3 {
        id: "maven-sources".to_owned(),
        declaration: MavenSourceDeclarationV3 {
            requested_coordinate: coordinate.clone(),
            repository_id: repository.id.clone(),
            roots,
        },
        derived_checks: MavenSourceDerivedChecksV3 {
            resolved_coordinate: coordinate,
            url,
            hash,
            archive_cache_path: paths.archive,
            tree_cache_path: paths.tree,
        },
    }))
}

pub(crate) fn acquire_locked_maven_sources(
    inventory: &DependencyInventory,
    provider: &MavenSourceProviderV3,
    cancellation_token: &CancellationToken,
    fetcher: &dyn PayloadFetcher,
) -> eyre::Result<()> {
    let archive = inventory.local_path(&provider.derived_checks.archive_cache_path);
    let tree = inventory.local_path(&provider.derived_checks.tree_cache_path);
    let _lock = acquire_source_lock(&tree)?;
    if inventory.locked_file_status(
        &provider.derived_checks.archive_cache_path,
        provider.derived_checks.hash,
    ) == AcquisitionStatus::Acquired
        && tree.is_dir()
    {
        validate_roots(&tree, &provider.declaration.roots)?;
        return Ok(());
    }

    let bytes = fetcher
        .fetch(&provider.derived_checks.url, cancellation_token)?
        .ok_or_else(|| {
            eyre::eyre!(
                "Locked Maven source payload is no longer available: {}",
                provider.derived_checks.url
            )
        })?;
    cancellation_token.bail_if_cancelled()?;
    let actual = ContentHash::from_bytes(&bytes, provider.derived_checks.hash.algorithm);
    if actual != provider.derived_checks.hash {
        eyre::bail!(
            "Locked Maven source payload hash mismatch for {}: expected {}, got {}",
            provider.derived_checks.url,
            provider.derived_checks.hash,
            actual
        );
    }
    write_payload_atomically(&archive, &bytes)?;
    extract_zip_atomically(&archive, &tree)?;
    validate_roots(&tree, &provider.declaration.roots)?;
    Ok(())
}

fn validate_roots(tree: &Path, roots: &[String]) -> eyre::Result<()> {
    for root in roots {
        let path = Path::new(root);
        if path.is_absolute()
            || path
                .components()
                .any(|component| !matches!(component, std::path::Component::Normal(_)))
        {
            eyre::bail!("Source root must be a relative normalized path: {root}");
        }
        let resolved = tree.join(path);
        if !resolved.exists() {
            eyre::bail!(
                "Configured source root does not exist in the acquired payload: {}",
                resolved.display()
            );
        }
    }
    Ok(())
}

fn acquire_source_lock(tree: &Path) -> eyre::Result<ArtifactLock> {
    let file_name = tree
        .file_name()
        .ok_or_else(|| eyre::eyre!("Source tree has no file name: {}", tree.display()))?;
    ArtifactLock::acquire(
        tree.with_file_name(format!("{}.lock", file_name.to_string_lossy())),
        tree.display().to_string(),
    )
}

fn derive_sources_coordinate(binary_coordinate: &str) -> eyre::Result<String> {
    let mut parsed = parse_coordinate(binary_coordinate)?;
    parsed.classifier = Some(match parsed.classifier {
        Some(classifier) => format!("{classifier}-sources"),
        None => "sources".to_owned(),
    });
    "jar".clone_into(&mut parsed.extension);
    Ok(parsed.canonical())
}

fn coordinate_url(repository_url: &str, coordinate: &str) -> eyre::Result<String> {
    let parsed = parse_coordinate(coordinate)?;
    let mut file_name = format!("{}-{}", parsed.artifact, parsed.version);
    if let Some(classifier) = &parsed.classifier {
        file_name.push('-');
        file_name.push_str(classifier);
    }
    file_name.push('.');
    file_name.push_str(&parsed.extension);
    Ok(format!(
        "{}/{}/{}/{}/{}",
        repository_url.trim_end_matches('/'),
        parsed.group.replace('.', "/"),
        parsed.artifact,
        parsed.version,
        file_name
    ))
}

struct MavenCoordinate {
    group: String,
    artifact: String,
    version: String,
    classifier: Option<String>,
    extension: String,
}

impl MavenCoordinate {
    fn canonical(&self) -> String {
        let mut coordinate = format!("{}:{}:{}", self.group, self.artifact, self.version);
        if let Some(classifier) = &self.classifier {
            coordinate.push(':');
            coordinate.push_str(classifier);
        }
        if self.extension != "jar" {
            coordinate.push('@');
            coordinate.push_str(&self.extension);
        }
        coordinate
    }
}

fn parse_coordinate(input: &str) -> eyre::Result<MavenCoordinate> {
    let (coordinate, extension) = input
        .split_once('@')
        .map_or((input, "jar"), |(coordinate, extension)| {
            (coordinate, extension)
        });
    let parts = coordinate.split(':').collect::<Vec<_>>();
    if !(parts.len() == 3 || parts.len() == 4)
        || parts.iter().any(|part| part.is_empty())
        || extension.is_empty()
        || input.contains(['+', '[', ']', '(', ')'])
    {
        eyre::bail!(
            "Expected exact Maven coordinate group:artifact:version[:classifier][@extension], got '{input}'"
        );
    }
    Ok(MavenCoordinate {
        group: parts[0].to_owned(),
        artifact: parts[1].to_owned(),
        version: parts[2].to_owned(),
        classifier: parts.get(3).map(|classifier| (*classifier).to_owned()),
        extension: extension.to_owned(),
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::branch_targets::BranchName;
    use crate::branch_targets::WorktreePath;
    use crate::branch_targets::WorktreeTarget;
    use crate::paths::CacheHome;
    use crate::toolchain_lockfile_schema::read_current;
    use std::io::Cursor;
    use std::io::Write;
    use std::path::PathBuf;
    use zip::ZipWriter;
    use zip::write::SimpleFileOptions;

    struct FixtureFetcher(Vec<u8>);

    struct NeverFetcher;

    impl PayloadFetcher for FixtureFetcher {
        fn fetch(
            &self,
            _url: &str,
            _cancellation_token: &CancellationToken,
        ) -> eyre::Result<Option<Vec<u8>>> {
            Ok(Some(self.0.clone()))
        }
    }

    impl PayloadFetcher for NeverFetcher {
        fn fetch(
            &self,
            _url: &str,
            _cancellation_token: &CancellationToken,
        ) -> eyre::Result<Option<Vec<u8>>> {
            panic!("validated cached sources must not perform HTTP")
        }
    }

    #[test]
    fn derives_and_materializes_cc_tweaked_sources_outside_artifact_inventory() {
        let cache = tempfile::tempdir().expect("temporary cache");
        let inventory = fixture(CacheHome(cache.path().to_path_buf()));
        let component = &inventory.dependency("cc-tweaked").unwrap().components[0];
        let artifact_count = inventory.lockfile.artifacts.len();
        let bytes = source_zip();

        let provider = configure_maven_sources(
            &inventory,
            component,
            None,
            vec!["dan200/computercraft/api".to_owned()],
            &CancellationToken::new(),
            &FixtureFetcher(bytes),
        )
        .expect("source resolution")
        .expect("published sources");

        assert_eq!(
            provider.declaration.requested_coordinate,
            "org.squiddev:cc-tweaked-1.19.2:1.101.3:sources"
        );
        assert!(
            inventory
                .local_path(&provider.derived_checks.tree_cache_path)
                .join("dan200/computercraft/api/IPeripheral.java")
                .is_file()
        );
        acquire_locked_maven_sources(
            &inventory,
            &provider,
            &CancellationToken::new(),
            &NeverFetcher,
        )
        .expect("second acquisition should reuse cache");
        assert_eq!(inventory.lockfile.artifacts.len(), artifact_count);
        assert!(
            provider
                .derived_checks
                .archive_cache_path
                .starts_with("$sfm-cache/sources/maven")
        );
    }

    #[test]
    fn configuration_rejects_missing_declared_roots() {
        let cache = tempfile::tempdir().expect("temporary cache");
        let inventory = fixture(CacheHome(cache.path().to_path_buf()));
        let component = &inventory.dependency("cc-tweaked").unwrap().components[0];

        let error = configure_maven_sources(
            &inventory,
            component,
            None,
            vec!["not/published".to_owned()],
            &CancellationToken::new(),
            &FixtureFetcher(source_zip()),
        )
        .expect_err("missing root");

        assert!(error.to_string().contains("does not exist"));
    }

    fn source_zip() -> Vec<u8> {
        let mut writer = ZipWriter::new(Cursor::new(Vec::new()));
        writer
            .start_file(
                "dan200/computercraft/api/IPeripheral.java",
                SimpleFileOptions::default(),
            )
            .unwrap();
        writer.write_all(b"interface IPeripheral {}").unwrap();
        writer.finish().unwrap().into_inner()
    }

    fn fixture(cache_home: CacheHome) -> DependencyInventory {
        let input = include_str!("../../../minecraft/sfm-toolchain.lock.json");
        DependencyInventory {
            target: WorktreeTarget {
                branch: BranchName::from("1.19.2"),
                worktree_path: WorktreePath::from(PathBuf::from("fixture")),
                core: true,
                mc_version: None,
            },
            lockfile_path: PathBuf::from("fixture/sfm-toolchain.lock.json"),
            cache_home,
            original_input: input.to_owned(),
            lockfile: read_current(input).expect("v3 fixture"),
        }
    }
}
