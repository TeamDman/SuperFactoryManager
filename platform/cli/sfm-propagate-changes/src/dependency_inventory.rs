use crate::branch_targets::BranchQuery;
use crate::branch_targets::WorktreeTarget;
use crate::branch_targets::select_single_worktree_target;
use crate::paths::CacheHome;
use crate::source_provider::SourceProviderView;
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
        let lockfile = read_current(&input)
            .wrap_err_with(|| format!("Failed to load {}", lockfile_path.display()))?;
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
        if let Ok(relative) = portable.strip_prefix(Path::new("$sfm-cache")) {
            return self.cache_home.join("minecraft-toolchain").join(relative);
        }
        if portable.is_absolute() {
            return portable.to_path_buf();
        }
        self.target
            .worktree_path
            .join("platform")
            .join("minecraft")
            .join(portable)
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
        if statuses.contains(&SourceStatus::Stale) {
            SourceStatus::Stale
        } else if statuses
            .iter()
            .all(|status| *status == SourceStatus::Acquired)
        {
            SourceStatus::Acquired
        } else if statuses
            .iter()
            .all(|status| *status == SourceStatus::Missing)
        {
            SourceStatus::Missing
        } else {
            SourceStatus::Partial
        }
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
