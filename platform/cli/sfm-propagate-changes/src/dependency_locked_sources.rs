use crate::cancellation::CancellationToken;
use crate::dependency_inventory::AcquisitionStatus;
use crate::dependency_inventory::DependencyInventory;
use crate::dependency_inventory::SourceStatus;
use crate::dependency_sources::SearchableSourceRoot;
use crate::dependency_sources::SourceComponentPreflight;
use crate::dependency_sources::SourcePreflight;
use crate::dependency_sources::SourceProviderPreflight;
use crate::dependency_sources::UnavailableSource;
use crate::payload_fetcher::PayloadFetcher;
use crate::source_decompile::acquire_locked_decompiled_sources;
use crate::source_decompile::derive_locked_decompile_provider;
use crate::source_provider::SourceProviderKind;
use crate::source_provider::SourceProviderView;
use crate::toolchain_lockfile_schema::version::v3::ArtifactPurposeV3;
use crate::toolchain_lockfile_schema::version::v3::ComponentAcquisitionV3;
use crate::toolchain_lockfile_schema::version::v3::DecompileSourceProviderV3;
use crate::toolchain_lockfile_schema::version::v3::ToolchainComponentKindV3;

const FORGE_LOADER_ID: &str = "forge";
const JAVA_FML_LANGUAGE_ARTIFACT: &str = "javafmllanguage";
const LOCKED_DECOMPILER_DEPENDENCY: &str = "vineflower";
const LOCKED_DECOMPILER_PROVIDER_ID: &str = "locked-vineflower";

/// One source attachment derived from an otherwise component-less artifact in
/// the effective lock. The provider is an in-memory projection only; the
/// checked-in lock remains authoritative and unchanged.
#[derive(Clone, Debug)]
pub(crate) struct LockedArtifactSource {
    pub(crate) dependency_id: String,
    pub(crate) component_id: String,
    pub(crate) artifact_id: String,
    pub(crate) coordinate: String,
    pub(crate) repository_id: Option<String>,
    pub(crate) provider: DecompileSourceProviderV3,
}

impl LockedArtifactSource {
    #[must_use]
    pub(crate) fn target(&self) -> String {
        format!("{}/{}", self.dependency_id, self.component_id)
    }
}

/// Select the exact Forge language artifact already present in the effective
/// lock and derive its source provider from the already-pinned Vineflower
/// component.
pub(crate) fn derive_locked_loader_artifact_sources(
    inventory: &DependencyInventory,
) -> eyre::Result<Vec<LockedArtifactSource>> {
    derive_locked_loader_artifact_sources_with(inventory, |binary, decompiler| {
        derive_locked_decompile_provider(
            inventory,
            binary,
            decompiler,
            LOCKED_DECOMPILER_PROVIDER_ID,
            Vec::new(),
        )
    })
}

fn derive_locked_loader_artifact_sources_with(
    inventory: &DependencyInventory,
    derive_provider: impl FnOnce(
        &crate::toolchain_lockfile_schema::version::v3::ArtifactV3,
        &crate::toolchain_lockfile_schema::version::v3::DependencyComponentV3,
    ) -> eyre::Result<DecompileSourceProviderV3>,
) -> eyre::Result<Vec<LockedArtifactSource>> {
    let loader_id = &inventory.lockfile.platform.loader_dependency;
    if loader_id != FORGE_LOADER_ID {
        return Ok(Vec::new());
    }
    let loader = inventory.dependency(loader_id)?;
    let loader_components = loader
        .components
        .iter()
        .filter_map(|component| match &component.declaration.acquisition {
            ComponentAcquisitionV3::Toolchain(acquisition)
                if acquisition.kind == ToolchainComponentKindV3::Loader =>
            {
                Some((component, acquisition.requested_version.as_str()))
            }
            _ => None,
        })
        .collect::<Vec<_>>();
    let [(loader_component, loader_version)] = loader_components.as_slice() else {
        eyre::bail!(
            "Forge locked-source derivation requires exactly one loader toolchain component; found {}",
            loader_components.len()
        );
    };
    let loader_coordinate = loader_component
        .derived_checks
        .resolved_coordinate
        .as_deref()
        .ok_or_else(|| eyre::eyre!("Forge loader component has no resolved coordinate"))?;
    let loader_group = loader_coordinate
        .split(':')
        .next()
        .filter(|group| !group.is_empty())
        .ok_or_else(|| eyre::eyre!("Forge loader coordinate is malformed: {loader_coordinate}"))?;
    let coordinate = format!("{loader_group}:{JAVA_FML_LANGUAGE_ARTIFACT}:{loader_version}");
    let matching = inventory
        .lockfile
        .artifacts
        .iter()
        .filter(|artifact| artifact.coordinate.as_deref() == Some(coordinate.as_str()))
        .collect::<Vec<_>>();
    let [binary] = matching.as_slice() else {
        eyre::bail!(
            "Expected exactly one locked Forge artifact `{coordinate}`; found {}",
            matching.len()
        );
    };
    if !binary.purposes.contains(&ArtifactPurposeV3::Toolchain) {
        eyre::bail!("Locked Forge artifact `{coordinate}` is not authenticated for toolchain use");
    }
    if loader
        .components
        .iter()
        .any(|component| component.derived_checks.artifact_id == binary.id)
    {
        return Ok(Vec::new());
    }

    let decompiler_dependency = inventory.dependency(LOCKED_DECOMPILER_DEPENDENCY)?;
    let [decompiler_component] = decompiler_dependency.components.as_slice() else {
        eyre::bail!(
            "Locked decompiler dependency `{LOCKED_DECOMPILER_DEPENDENCY}` must contain exactly one component"
        );
    };
    let provider = derive_provider(binary, decompiler_component)?;
    Ok(vec![LockedArtifactSource {
        dependency_id: loader_id.clone(),
        component_id: JAVA_FML_LANGUAGE_ARTIFACT.to_owned(),
        artifact_id: binary.id.clone(),
        coordinate,
        repository_id: binary.repository_id.clone(),
        provider,
    }])
}

pub(crate) fn acquire_locked_artifact_sources(
    inventory: &DependencyInventory,
    sources: &[LockedArtifactSource],
    cancellation_token: &CancellationToken,
    fetcher: &dyn PayloadFetcher,
) -> eyre::Result<()> {
    for source in sources {
        cancellation_token.bail_if_cancelled()?;
        acquire_locked_decompiled_sources(
            inventory,
            &source.provider,
            cancellation_token,
            fetcher,
        )?;
    }
    Ok(())
}

pub(crate) fn append_locked_artifact_source_preflight(
    inventory: &DependencyInventory,
    sources: &[LockedArtifactSource],
    preflight: &mut SourcePreflight,
) {
    for source in sources {
        let status = locked_artifact_source_status(inventory, source);
        let provider_definition =
            crate::toolchain_lockfile_schema::version::v3::SourceProviderV3::Decompile(
                source.provider.clone(),
            );
        let view = SourceProviderView::new(inventory, &provider_definition, 0);
        let searchable_roots = view.searchable_roots();
        let provider = SourceProviderPreflight {
            id: source.provider.id.clone(),
            kind: SourceProviderKind::Decompile,
            priority: 0,
            status,
            searchable_roots: searchable_roots.clone(),
            unavailable_reason: locked_artifact_unavailable_reason(inventory, source),
        };
        let target = source.target();
        if status == SourceStatus::Acquired {
            preflight.roots.extend(
                searchable_roots
                    .into_iter()
                    .map(|root| SearchableSourceRoot {
                        identity: target.clone(),
                        provider_id: source.provider.id.clone(),
                        root,
                    }),
            );
        } else {
            preflight.missing.push(UnavailableSource {
                target: target.clone(),
                identity: format!("{target}/{}", source.provider.id),
                reason: provider
                    .unavailable_reason
                    .clone()
                    .unwrap_or_else(|| status.label().to_owned()),
                // `symbol index refresh` owns this synthetic acquisition. It
                // is not a lockfile component accepted by `dependency source acquire`.
                acquirable: false,
            });
        }
        preflight.components.push(SourceComponentPreflight {
            target,
            status,
            preferred_provider: Some(provider.clone()),
            providers: vec![provider],
        });
    }
}

fn locked_artifact_source_status(
    inventory: &DependencyInventory,
    source: &LockedArtifactSource,
) -> SourceStatus {
    let provider = crate::toolchain_lockfile_schema::version::v3::SourceProviderV3::Decompile(
        source.provider.clone(),
    );
    let view = SourceProviderView::new(inventory, &provider, 0);
    if view.status() == SourceStatus::Acquired {
        return SourceStatus::Acquired;
    }
    let binary = inventory
        .artifact_by_id(&source.artifact_id)
        .expect("derived source binary belongs to the validated lock");
    let decompiler = inventory
        .artifact_by_id(&source.provider.derived_checks.decompiler_artifact_id)
        .expect("derived source decompiler belongs to the validated lock");
    if [binary, decompiler].into_iter().any(|artifact| {
        inventory.locked_file_status(&artifact.cache_path, artifact.hash)
            == AcquisitionStatus::Stale
    }) {
        SourceStatus::Stale
    } else {
        SourceStatus::Missing
    }
}

fn locked_artifact_unavailable_reason(
    inventory: &DependencyInventory,
    source: &LockedArtifactSource,
) -> Option<String> {
    let binary = inventory.artifact_by_id(&source.artifact_id)?;
    match inventory.locked_file_status(&binary.cache_path, binary.hash) {
        AcquisitionStatus::Stale => {
            return Some("locked Forge binary artifact hash does not match".to_owned());
        }
        AcquisitionStatus::Missing => {
            return Some(
                "locked Forge binary artifact and derived source tree are missing".to_owned(),
            );
        }
        AcquisitionStatus::Acquired => {}
    }
    let decompiler =
        inventory.artifact_by_id(&source.provider.derived_checks.decompiler_artifact_id)?;
    match inventory.locked_file_status(&decompiler.cache_path, decompiler.hash) {
        AcquisitionStatus::Stale => {
            Some("locked Vineflower artifact hash does not match".to_owned())
        }
        AcquisitionStatus::Missing => {
            Some("locked Vineflower artifact and derived source tree are missing".to_owned())
        }
        AcquisitionStatus::Acquired => {
            Some("derived source tree is missing or has a different fingerprint".to_owned())
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::branch_targets::BranchName;
    use crate::branch_targets::MinecraftVersion;
    use crate::branch_targets::WorktreePath;
    use crate::branch_targets::WorktreeTarget;
    use crate::paths::CacheHome;
    use crate::source_decompile::FINGERPRINT_FILE;
    use crate::source_decompile::derive_locked_decompile_provider_with_runtime;
    use crate::toolchain_lockfile_schema::read_current;
    use std::fs;
    use std::path::PathBuf;

    #[test]
    fn derives_exact_forge_language_source_from_locked_artifacts_only() {
        let cache = tempfile::tempdir().expect("temporary cache");
        let inventory = fixture(CacheHome(cache.path().to_path_buf()));
        let original_lock = inventory.original_input.clone();

        let sources = derive_with_runtime(&inventory, "fixture Java 17");
        let [source] = sources.as_slice() else {
            panic!("expected one locked Forge source")
        };
        assert_eq!(source.target(), "forge/javafmllanguage");
        assert_eq!(
            source.coordinate,
            "net.minecraftforge:javafmllanguage:1.19.2-43.4.0"
        );
        assert_eq!(
            source.artifact_id,
            "net-minecraftforge-javafmllanguage-1-19-2-43-4-0-84a169e7"
        );
        assert_eq!(
            source.provider.derived_checks.decompiler_artifact_id,
            "org-vineflower-vineflower-1-12-0-865bc756"
        );
        assert!(
            source
                .provider
                .derived_checks
                .tree_cache_path
                .starts_with("$sfm-cache/sources/decompiled")
        );
        assert_eq!(inventory.original_input, original_lock);
    }

    #[test]
    fn preflight_is_bound_to_the_injected_cache_and_reports_missing_stale_and_ready() {
        let cache = tempfile::tempdir().expect("temporary cache");
        let inventory = fixture(CacheHome(cache.path().to_path_buf()));
        let sources = derive_with_runtime(&inventory, "fixture Java 17");
        let source = &sources[0];
        let binary = inventory.artifact_by_id(&source.artifact_id).unwrap();
        let binary_path = inventory.local_path(&binary.cache_path);

        let mut missing = SourcePreflight::default();
        append_locked_artifact_source_preflight(&inventory, &sources, &mut missing);
        assert_eq!(missing.components[0].status, SourceStatus::Missing);
        assert!(missing.roots.is_empty());
        assert!(missing.missing[0].reason.contains("binary artifact"));
        assert!(!missing.missing[0].acquirable);

        fs::create_dir_all(binary_path.parent().unwrap()).unwrap();
        fs::write(&binary_path, b"not the locked artifact").unwrap();
        let mut stale = SourcePreflight::default();
        append_locked_artifact_source_preflight(&inventory, &sources, &mut stale);
        assert_eq!(stale.components[0].status, SourceStatus::Stale);
        assert!(stale.missing[0].reason.contains("hash does not match"));

        let tree = inventory.local_path(&source.provider.derived_checks.tree_cache_path);
        fs::create_dir_all(&tree).unwrap();
        fs::write(
            tree.join(FINGERPRINT_FILE),
            &source.provider.derived_checks.fingerprint,
        )
        .unwrap();
        let mut ready = SourcePreflight::default();
        append_locked_artifact_source_preflight(&inventory, &sources, &mut ready);
        assert_eq!(ready.components[0].status, SourceStatus::Acquired);
        assert_eq!(ready.roots.len(), 1);
        assert!(ready.missing.is_empty());
        assert!(ready.roots[0].root.starts_with(cache.path()));
    }

    fn derive_with_runtime(
        inventory: &DependencyInventory,
        runtime_identity: &str,
    ) -> Vec<LockedArtifactSource> {
        derive_locked_loader_artifact_sources_with(inventory, |binary, decompiler| {
            Ok(derive_locked_decompile_provider_with_runtime(
                inventory,
                binary,
                decompiler,
                LOCKED_DECOMPILER_PROVIDER_ID,
                Vec::new(),
                runtime_identity,
            ))
        })
        .expect("locked Forge source derivation")
    }

    fn fixture(cache_home: CacheHome) -> DependencyInventory {
        let input = include_str!("../../../minecraft/sfm-toolchain.lock.json");
        DependencyInventory {
            target: WorktreeTarget {
                branch: BranchName::from("1.19.2"),
                worktree_path: WorktreePath::from(PathBuf::from("fixture")),
                core: true,
                mc_version: Some(MinecraftVersion::parse("1.19.2").unwrap()),
            },
            lockfile_path: PathBuf::from("fixture/sfm-toolchain.lock.json"),
            cache_home,
            original_input: input.to_owned(),
            lockfile: read_current(input).expect("effective lock fixture"),
        }
    }
}
