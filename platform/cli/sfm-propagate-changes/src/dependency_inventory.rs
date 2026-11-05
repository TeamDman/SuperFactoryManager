use crate::branch_targets::BranchQuery;
use crate::branch_targets::WorktreeTarget;
use crate::branch_targets::select_single_worktree_target;
use crate::paths::CacheHome;
use crate::source_provider::SourceProviderView;
use crate::toolchain_lockfile_schema::ToolchainLockfileDocument;
use crate::toolchain_lockfile_schema::parse_document;
#[cfg(test)]
use crate::toolchain_lockfile_schema::read_current;
use crate::toolchain_lockfile_schema::version::v3::ArtifactLockfileV3;
use crate::toolchain_lockfile_schema::version::v3::ArtifactV3;
use crate::toolchain_lockfile_schema::version::v3::ComponentAcquisitionV3;
use crate::toolchain_lockfile_schema::version::v3::DependencyComponentV3;
use crate::toolchain_lockfile_schema::version::v3::DependencyKindV3;
use crate::toolchain_lockfile_schema::version::v3::DependencyRoleV3;
use crate::toolchain_lockfile_schema::version::v3::DependencyScopeV3;
use crate::toolchain_lockfile_schema::version::v3::DependencyV3;
use eyre::Context;
use std::path::Path;
use std::path::PathBuf;

pub(crate) struct DependencyInventory {
    pub(crate) target: WorktreeTarget,
    pub(crate) lockfile_path: PathBuf,
    pub(crate) cache_home: CacheHome,
    pub(crate) original_input: String,
    pub(crate) lockfile: ArtifactLockfileV3,
}

impl DependencyInventory {
    pub fn load(query: &BranchQuery, cache_home: CacheHome) -> eyre::Result<Self> {
        let target = select_single_worktree_target(query)?;
        Self::load_target(target, cache_home)
    }

    pub(crate) fn load_target(target: WorktreeTarget, cache_home: CacheHome) -> eyre::Result<Self> {
        let lockfile_path = target
            .worktree_path
            .join("platform")
            .join("minecraft")
            .join("sfm-toolchain.lock.json");
        let input = std::fs::read_to_string(&lockfile_path)
            .wrap_err_with(|| format!("Failed to read {}", lockfile_path.display()))?;
        let lockfile = read_editable(&input)
            .wrap_err_with(|| format!("Failed to load {}", lockfile_path.display()))?;
        Ok(Self {
            target,
            lockfile_path,
            cache_home,
            original_input: input,
            lockfile,
        })
    }

    /// Select the lock emitted for an exact catalogued projection. Historical
    /// worktree source commands continue to use `load_target` above.
    pub(crate) fn load_project(project_root: &Path, cache_home: CacheHome) -> eyre::Result<Self> {
        let (catalog, key) =
            crate::source_projection::core_catalog::CoreCatalog::for_project(project_root)?;
        let projection = catalog.context(&key)?;
        let lockfile_path = catalog
            .repo_root
            .join(catalog.catalog.project_dir(&key)?)
            .join("sfm-toolchain.lock.json");
        let input = std::fs::read_to_string(&lockfile_path)
            .wrap_err_with(|| format!("Failed to read {}", lockfile_path.display()))?;
        let mut lockfile = read_editable(&input)
            .wrap_err_with(|| format!("Failed to load {}", lockfile_path.display()))?;
        crate::source_binding::apply_for_project(&catalog.repo_root, &key, &input, &mut lockfile)?;
        let target = WorktreeTarget {
            branch: crate::branch_targets::BranchName::new(format!("project:{key}")),
            worktree_path: crate::branch_targets::WorktreePath::new(catalog.repo_root),
            core: false,
            mc_version: Some(crate::branch_targets::MinecraftVersion::parse(
                &projection.minecraft_version,
            )?),
        };
        Ok(Self {
            target,
            lockfile_path,
            cache_home,
            original_input: input,
            lockfile,
        })
    }

    pub fn dependencies(&self) -> impl Iterator<Item = &DependencyV3> {
        self.lockfile.dependencies.iter()
    }

    /// Persist edits without replacing schema-4 feature/profile authority with a runtime projection.
    pub(crate) fn to_canonical_json(&self) -> eyre::Result<String> {
        let mut effective = self.lockfile.clone();
        if let Some(key) = self.target.branch.as_str().strip_prefix("project:") {
            let base = read_editable(&self.original_input)?;
            crate::source_binding::restore_base_providers(
                &self.target.worktree_path,
                key,
                &self.original_input,
                &base,
                &mut effective,
            )?;
        }
        match parse_document(&self.original_input)? {
            ToolchainLockfileDocument::V4(mut document) => {
                document.platform = effective.platform.clone();
                document.policy = effective.policy.clone();
                document.repositories.clone_from(&effective.repositories);
                document.dependencies.clone_from(&effective.dependencies);
                document.artifacts.clone_from(&effective.artifacts);
                document.to_canonical_json()
            }
            ToolchainLockfileDocument::V3(_) => effective.to_canonical_json(),
            _ => eyre::bail!("Dependency editing requires schema version 3 or 4"),
        }
    }

    pub fn dependency(&self, id: &str) -> eyre::Result<&DependencyV3> {
        self.lockfile
            .dependencies
            .iter()
            .find(|dependency| dependency.id == id)
            .ok_or_else(|| eyre::eyre!("Unknown dependency '{id}'."))
    }

    #[must_use]
    pub fn artifact(&self, component: &DependencyComponentV3) -> &ArtifactV3 {
        self.artifact_by_id(&component.derived_checks.artifact_id)
            .expect("v3 validation guarantees component artifact references")
    }

    #[must_use]
    pub fn artifact_by_id(&self, id: &str) -> Option<&ArtifactV3> {
        self.lockfile
            .artifacts
            .iter()
            .find(|artifact| artifact.id == id)
    }

    #[must_use]
    pub fn local_path(&self, portable: &Path) -> PathBuf {
        if let Some(relative) = crate::paths::portable_cache_relative(portable)
            .expect("invalid portable cache path after lockfile validation")
        {
            return self.cache_home.join("minecraft-toolchain").join(relative);
        }
        if portable.is_absolute() {
            return portable.to_path_buf();
        }
        if self.target.branch.0.starts_with("project:") {
            self.lockfile_path
                .parent()
                .expect("loaded project lockfile has a parent")
                .join(portable)
        } else {
            self.target
                .worktree_path
                .join("platform")
                .join("minecraft")
                .join(portable)
        }
    }

    #[must_use]
    pub fn binary_status(&self, component: &DependencyComponentV3) -> AcquisitionStatus {
        self.locked_file_status(
            &component.derived_checks.cache_path,
            component.derived_checks.expected_hash,
        )
    }

    #[must_use]
    pub fn locked_file_status(
        &self,
        portable: &Path,
        expected: crate::jar_build::hash::ContentHash,
    ) -> AcquisitionStatus {
        AcquisitionStatus::from_locked_file(&self.local_path(portable), expected)
    }

    #[must_use]
    pub fn source_status(&self, component: &DependencyComponentV3) -> SourceStatus {
        if component.source_providers.is_empty() {
            return SourceStatus::NoneDeclared;
        }
        let statuses: Vec<_> = component
            .source_providers
            .iter()
            .enumerate()
            .map(|(priority, provider)| SourceProviderView::new(self, provider, priority).status())
            .collect();
        SourceStatus::from_provider_statuses(&statuses)
    }

    pub fn source_providers<'a>(
        &'a self,
        component: &'a DependencyComponentV3,
    ) -> impl Iterator<Item = SourceProviderView<'a>> {
        component
            .source_providers
            .iter()
            .enumerate()
            .map(|(priority, provider)| SourceProviderView::new(self, provider, priority))
    }
}

fn read_editable(input: &str) -> eyre::Result<ArtifactLockfileV3> {
    match parse_document(input)? {
        ToolchainLockfileDocument::V3(document) => Ok(document),
        ToolchainLockfileDocument::V4(document) => Ok(ArtifactLockfileV3 {
            schema_version: crate::toolchain_lockfile_schema::version::v3::SCHEMA_VERSION,
            platform: document.platform,
            policy: document.policy,
            repositories: document.repositories,
            dependencies: document.dependencies,
            artifacts: document.artifacts,
        }),
        _ => eyre::bail!(
            "Dependency editing requires schema version 3 or 4; run dependency migrate first"
        ),
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) enum AcquisitionStatus {
    Acquired,
    Missing,
    Stale,
}

impl AcquisitionStatus {
    fn from_locked_file(path: &Path, expected: crate::jar_build::hash::ContentHash) -> Self {
        if !path.is_file() {
            return Self::Missing;
        }
        match crate::jar_build::hash::ContentHash::from_path(path, expected.algorithm) {
            Ok(actual) if actual == expected => Self::Acquired,
            Ok(_) | Err(_) => Self::Stale,
        }
    }

    #[must_use]
    pub const fn label(self) -> &'static str {
        match self {
            Self::Acquired => "acquired",
            Self::Missing => "missing",
            Self::Stale => "stale",
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) enum SourceStatus {
    NoneDeclared,
    Acquired,
    Partial,
    Missing,
    Stale,
}

impl SourceStatus {
    #[must_use]
    pub(crate) fn from_provider_statuses(statuses: &[Self]) -> Self {
        if statuses.is_empty() {
            Self::NoneDeclared
        } else if statuses.contains(&Self::Stale) {
            Self::Stale
        } else if statuses.iter().all(|status| *status == Self::Acquired) {
            Self::Acquired
        } else if statuses.iter().all(|status| *status == Self::Missing) {
            Self::Missing
        } else {
            Self::Partial
        }
    }

    #[must_use]
    pub const fn label(self) -> &'static str {
        match self {
            Self::NoneDeclared => "none-declared",
            Self::Acquired => "acquired",
            Self::Partial => "partial",
            Self::Missing => "missing",
            Self::Stale => "stale",
        }
    }
}

#[must_use]
pub(crate) const fn kind_label(kind: DependencyKindV3) -> &'static str {
    match kind {
        DependencyKindV3::Minecraft => "minecraft",
        DependencyKindV3::Loader => "loader",
        DependencyKindV3::Mod => "mod",
        DependencyKindV3::Library => "library",
        DependencyKindV3::Tool => "tool",
    }
}

#[must_use]
pub(crate) const fn role_label(role: DependencyRoleV3) -> &'static str {
    match role {
        DependencyRoleV3::Platform => "platform",
        DependencyRoleV3::Integration => "integration",
        DependencyRoleV3::Build => "build",
        DependencyRoleV3::Test => "test",
        DependencyRoleV3::Library => "library",
    }
}

#[must_use]
pub(crate) const fn scope_label(scope: DependencyScopeV3) -> &'static str {
    match scope {
        DependencyScopeV3::AnnotationProcessor => "annotation-processor",
        DependencyScopeV3::Codegen => "codegen",
        DependencyScopeV3::Compile => "compile",
        DependencyScopeV3::Runtime => "runtime",
        DependencyScopeV3::GametestCompile => "gametest-compile",
        DependencyScopeV3::GametestRuntime => "gametest-runtime",
        DependencyScopeV3::TestCompile => "test-compile",
        DependencyScopeV3::TestAnnotationProcessor => "test-annotation-processor",
        DependencyScopeV3::TestRuntime => "test-runtime",
        DependencyScopeV3::Bundle => "bundle",
    }
}

#[must_use]
pub(crate) fn resolved_version(component: &DependencyComponentV3) -> String {
    match &component.declaration.acquisition {
        ComponentAcquisitionV3::CurseForge(acquisition) => {
            format!("file:{}", acquisition.file_id)
        }
        ComponentAcquisitionV3::Toolchain(acquisition) => acquisition.requested_version.clone(),
        ComponentAcquisitionV3::Maven(_) => component
            .derived_checks
            .resolved_coordinate
            .as_deref()
            .and_then(coordinate_version)
            .unwrap_or("unknown")
            .to_owned(),
        ComponentAcquisitionV3::Http(_) => "http".to_owned(),
        ComponentAcquisitionV3::SourceBuild(acquisition) => {
            format!("source-build:{}", acquisition.artifact_id)
        }
    }
}

fn coordinate_version(coordinate: &str) -> Option<&str> {
    let mut parts = coordinate.split(':');
    parts.next()?;
    parts.next()?;
    parts.next()
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::branch_targets::BranchName;
    use crate::branch_targets::WorktreePath;
    use crate::jar_build::hash::ContentHash;
    use crate::jar_build::hash::ContentHashAlgorithm;
    use crate::toolchain_lockfile_schema::version::v4::JdkArtifactV4;
    use crate::toolchain_lockfile_schema::version::v4::JdkPinV4;

    #[test]
    fn editing_preserves_schema_four_profiles_inactive_components_and_jdk_pins() {
        let input = include_str!("../../../minecraft/sfm-toolchain.lock.json");
        let ToolchainLockfileDocument::V4(mut original) = parse_document(input).unwrap() else {
            panic!("expected schema-4 fixture");
        };
        original
            .profiles
            .iter_mut()
            .find(|p| p.id == "rust-toolchain")
            .unwrap()
            .features
            .clear();
        original.jdk_pins = Some(vec![JdkPinV4 {
            major: 17,
            vendor: "JetBrains".to_owned(),
            version: "17.0.14".to_owned(),
            build: "b1367.22".to_owned(),
            flavor: "jbrsdk".to_owned(),
            artifacts: vec![JdkArtifactV4 {
                platform: "windows-x64".to_owned(),
                url: "https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-17.0.14-windows-x64-b1367.22.zip".to_owned(),
                sha512: "a".repeat(128),
            }],
        }]);
        let input = original.to_canonical_json().unwrap();
        let ToolchainLockfileDocument::V4(original) = parse_document(&input).unwrap() else {
            panic!("canonical fixture must remain schema v4");
        };
        let mut inventory = fixture(CacheHome(PathBuf::from("unused-cache")));
        inventory.lockfile = read_editable(&input).unwrap();
        inventory.original_input = input;
        assert_eq!(inventory.lockfile.dependencies, original.dependencies);
        let vox = inventory
            .lockfile
            .dependencies
            .iter_mut()
            .find(|d| d.id == "vox-java")
            .unwrap();
        vox.notes = Some("edited without activating feature".to_owned());
        let output = inventory.to_canonical_json().unwrap();
        let ToolchainLockfileDocument::V4(updated) = parse_document(&output).unwrap() else {
            panic!("editing must not downgrade the schema");
        };
        assert_eq!(updated.features, original.features);
        assert_eq!(updated.profiles, original.profiles);
        assert_eq!(updated.jdk_pins, original.jdk_pins);
        assert_eq!(updated.artifacts, original.artifacts);
        assert_eq!(updated.dependencies.len(), original.dependencies.len());
        assert_eq!(
            updated
                .dependencies
                .iter()
                .find(|d| d.id == "vox-java")
                .unwrap()
                .notes
                .as_deref(),
            Some("edited without activating feature")
        );
    }

    #[test]
    fn injected_cache_reports_missing_stale_and_acquired_bytes() {
        let directory = tempfile::tempdir().expect("temporary cache");
        let mut inventory = fixture(CacheHome(directory.path().to_path_buf()));
        let dependency_index = inventory
            .lockfile
            .dependencies
            .iter()
            .position(|dependency| dependency.id == "cc-tweaked")
            .expect("CC:Tweaked fixture");
        let expected = ContentHash::from_bytes(b"expected", ContentHashAlgorithm::Blake3);
        inventory.lockfile.dependencies[dependency_index].components[0]
            .derived_checks
            .expected_hash = expected;
        let component = &inventory.lockfile.dependencies[dependency_index].components[0];
        let local = inventory.local_path(&component.derived_checks.cache_path);

        assert_eq!(
            inventory.binary_status(component),
            AcquisitionStatus::Missing
        );
        std::fs::create_dir_all(local.parent().expect("cache parent")).expect("cache parent");
        std::fs::write(&local, b"wrong").expect("stale fixture");
        assert_eq!(inventory.binary_status(component), AcquisitionStatus::Stale);
        std::fs::write(&local, b"expected").expect("acquired fixture");
        assert_eq!(
            inventory.binary_status(component),
            AcquisitionStatus::Acquired
        );
    }

    #[test]
    fn windows_spelled_cache_path_resolves_and_checks_bytes_on_every_host() {
        let directory = tempfile::tempdir().expect("temporary cache");
        let mut inventory = fixture(CacheHome(directory.path().to_path_buf()));
        let index = inventory
            .lockfile
            .dependencies
            .iter()
            .position(|dependency| dependency.id == "cc-tweaked")
            .expect("CC:Tweaked fixture");
        let component = &mut inventory.lockfile.dependencies[index].components[0];
        component.derived_checks.cache_path = PathBuf::from(r"$sfm-cache\maven/example\locked.jar");
        component.derived_checks.expected_hash =
            ContentHash::from_bytes(b"expected", ContentHashAlgorithm::Blake3);
        let path = directory
            .path()
            .join("minecraft-toolchain/maven/example/locked.jar");
        std::fs::create_dir_all(path.parent().unwrap()).unwrap();
        std::fs::write(&path, b"expected").unwrap();
        let component = &inventory.lockfile.dependencies[index].components[0];
        assert_eq!(
            inventory.local_path(&component.derived_checks.cache_path),
            path
        );
        assert_eq!(
            inventory.binary_status(component),
            AcquisitionStatus::Acquired
        );
        std::fs::write(&path, b"wrong").unwrap();
        assert_eq!(inventory.binary_status(component), AcquisitionStatus::Stale);
    }

    #[test]
    fn catalog_source_path_cannot_escape_the_portable_cache_root() {
        use crate::toolchain_lockfile_schema::version::v3::SourceProviderV3;

        let mut inventory = fixture(CacheHome(PathBuf::from("unused-cache")));
        let source = inventory
            .lockfile
            .dependencies
            .iter_mut()
            .flat_map(|dependency| &mut dependency.components)
            .flat_map(|component| &mut component.source_providers)
            .find_map(|provider| match provider {
                SourceProviderV3::PlatformPipeline(provider)
                    if provider.derived_checks.catalog_projection.is_some() =>
                {
                    Some(provider)
                }
                _ => None,
            })
            .expect("catalog source fixture");
        source.derived_checks.tree_cache_path = PathBuf::from(r"$sfm-cache\sources\..\outside");
        assert!(inventory.lockfile.validate().is_err());
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
