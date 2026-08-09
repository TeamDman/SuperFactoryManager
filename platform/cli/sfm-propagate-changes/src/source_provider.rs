use crate::dependency_inventory::AcquisitionStatus;
use crate::dependency_inventory::DependencyInventory;
use crate::dependency_inventory::SourceStatus;
use crate::source_decompile::completed_tree_matches;
use crate::toolchain_lockfile_schema::version::v3::SourceProviderV3;
use std::path::Path;
use std::path::PathBuf;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) enum SourceProviderKind {
    MavenSources,
    Git,
    Decompile,
    PlatformPipeline,
}

impl SourceProviderKind {
    #[must_use]
    pub const fn label(self) -> &'static str {
        match self {
            Self::MavenSources => "maven-sources",
            Self::Git => "git",
            Self::Decompile => "decompile",
            Self::PlatformPipeline => "platform-pipeline",
        }
    }
}

#[derive(Clone, Copy)]
pub(crate) struct SourceProviderView<'a> {
    inventory: &'a DependencyInventory,
    provider: &'a SourceProviderV3,
    priority: usize,
}

impl<'a> SourceProviderView<'a> {
    #[must_use]
    pub const fn new(
        inventory: &'a DependencyInventory,
        provider: &'a SourceProviderV3,
        priority: usize,
    ) -> Self {
        Self {
            inventory,
            provider,
            priority,
        }
    }

    #[must_use]
    pub const fn definition(self) -> &'a SourceProviderV3 {
        self.provider
    }

    #[must_use]
    pub fn id(self) -> &'a str {
        match self.provider {
            SourceProviderV3::MavenSources(provider) => &provider.id,
            SourceProviderV3::Git(provider) => &provider.id,
            SourceProviderV3::Decompile(provider) => &provider.id,
            SourceProviderV3::PlatformPipeline(provider) => &provider.id,
        }
    }

    #[must_use]
    pub const fn kind(self) -> SourceProviderKind {
        match self.provider {
            SourceProviderV3::MavenSources(_) => SourceProviderKind::MavenSources,
            SourceProviderV3::Git(_) => SourceProviderKind::Git,
            SourceProviderV3::Decompile(_) => SourceProviderKind::Decompile,
            SourceProviderV3::PlatformPipeline(_) => SourceProviderKind::PlatformPipeline,
        }
    }

    #[must_use]
    pub const fn priority(self) -> usize {
        self.priority
    }

    #[must_use]
    pub fn tree_path(self) -> PathBuf {
        self.inventory.local_path(self.tree_cache_path())
    }

    #[must_use]
    pub fn searchable_roots(self) -> Vec<PathBuf> {
        let tree = self.tree_path();
        let roots = self.roots();
        if roots.is_empty() {
            vec![tree]
        } else {
            roots.iter().map(|root| tree.join(root)).collect()
        }
    }

    #[must_use]
    pub fn status(self) -> SourceStatus {
        let materialization = match self.provider {
            SourceProviderV3::MavenSources(provider) => {
                let archive = self.inventory.locked_file_status(
                    &provider.derived_checks.archive_cache_path,
                    provider.derived_checks.hash,
                );
                if archive == AcquisitionStatus::Stale {
                    SourceStatus::Stale
                } else if archive == AcquisitionStatus::Acquired && self.tree_path().is_dir() {
                    SourceStatus::Acquired
                } else {
                    SourceStatus::Missing
                }
            }
            SourceProviderV3::Git(provider) => {
                let repository = self
                    .inventory
                    .local_path(&provider.derived_checks.repository_cache_path);
                if repository.is_dir() && self.tree_path().is_dir() {
                    SourceStatus::Acquired
                } else {
                    SourceStatus::Missing
                }
            }
            SourceProviderV3::Decompile(provider) => {
                if completed_tree_matches(
                    &self.tree_path(),
                    &provider.derived_checks.fingerprint,
                    &provider.declaration.roots,
                ) {
                    SourceStatus::Acquired
                } else {
                    SourceStatus::Missing
                }
            }
            SourceProviderV3::PlatformPipeline(_) => {
                if self.tree_path().is_dir() {
                    SourceStatus::Acquired
                } else {
                    SourceStatus::Missing
                }
            }
        };
        if materialization == SourceStatus::Acquired
            && self.searchable_roots().iter().any(|root| !root.is_dir())
        {
            SourceStatus::Missing
        } else {
            materialization
        }
    }

    #[must_use]
    pub fn unavailable_reason(self) -> Option<&'static str> {
        if self.status() == SourceStatus::Acquired {
            return None;
        }
        match self.provider {
            SourceProviderV3::MavenSources(provider) => {
                let archive = self.inventory.locked_file_status(
                    &provider.derived_checks.archive_cache_path,
                    provider.derived_checks.hash,
                );
                match archive {
                    AcquisitionStatus::Stale => Some("locked source archive hash does not match"),
                    AcquisitionStatus::Missing => Some("locked source archive is not cached"),
                    AcquisitionStatus::Acquired if !self.tree_path().is_dir() => {
                        Some("source archive has not been extracted")
                    }
                    AcquisitionStatus::Acquired => {
                        Some("a configured Maven source root is missing")
                    }
                }
            }
            SourceProviderV3::Git(provider) => {
                let repository = self
                    .inventory
                    .local_path(&provider.derived_checks.repository_cache_path);
                match (repository.is_dir(), self.tree_path().is_dir()) {
                    (false, false) => Some("managed repository and materialized tree are missing"),
                    (false, true) => Some("managed repository is missing"),
                    (true, false) => Some("locked commit tree is not materialized"),
                    (true, true) => Some("a configured Git source root is missing"),
                }
            }
            SourceProviderV3::Decompile(_) => {
                Some("decompiled tree is missing or does not match its locked fingerprint")
            }
            SourceProviderV3::PlatformPipeline(_) => {
                Some("platform source pipeline output is not materialized")
            }
        }
    }

    fn roots(self) -> &'a [String] {
        match self.provider {
            SourceProviderV3::MavenSources(provider) => &provider.declaration.roots,
            SourceProviderV3::Git(provider) => &provider.declaration.roots,
            SourceProviderV3::Decompile(provider) => &provider.declaration.roots,
            SourceProviderV3::PlatformPipeline(provider) => &provider.declaration.roots,
        }
    }

    fn tree_cache_path(self) -> &'a Path {
        match self.provider {
            SourceProviderV3::MavenSources(provider) => &provider.derived_checks.tree_cache_path,
            SourceProviderV3::Git(provider) => &provider.derived_checks.tree_cache_path,
            SourceProviderV3::Decompile(provider) => &provider.derived_checks.tree_cache_path,
            SourceProviderV3::PlatformPipeline(provider) => {
                &provider.derived_checks.tree_cache_path
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::branch_targets::BranchName;
    use crate::branch_targets::WorktreePath;
    use crate::branch_targets::WorktreeTarget;
    use crate::paths::CacheHome;
    use crate::source_decompile::FINGERPRINT_FILE;
    use crate::toolchain_lockfile_schema::read_current;
    use crate::toolchain_lockfile_schema::version::v3::DecompileSourceDeclarationV3;
    use crate::toolchain_lockfile_schema::version::v3::DecompileSourceDerivedChecksV3;
    use crate::toolchain_lockfile_schema::version::v3::DecompileSourceProviderV3;
    use crate::toolchain_lockfile_schema::version::v3::GitSourceDeclarationV3;
    use crate::toolchain_lockfile_schema::version::v3::GitSourceDerivedChecksV3;
    use crate::toolchain_lockfile_schema::version::v3::GitSourceProviderV3;

    #[test]
    fn git_provider_contract_uses_injected_cache_and_declared_roots() {
        let cache = tempfile::tempdir().expect("temporary cache");
        let inventory = fixture(CacheHome(cache.path().to_path_buf()));
        let provider = SourceProviderV3::Git(GitSourceProviderV3 {
            id: "upstream".to_owned(),
            declaration: GitSourceDeclarationV3 {
                remote_url: "https://example.invalid/upstream.git".to_owned(),
                requested_revision: "v1".to_owned(),
                roots: vec!["src/main/java".to_owned(), "projects/api/src".to_owned()],
            },
            derived_checks: GitSourceDerivedChecksV3 {
                commit: "0123456789abcdef".to_owned(),
                repository_cache_path: PathBuf::from(
                    "$sfm-cache/sources/git/repositories/test.git",
                ),
                tree_cache_path: PathBuf::from("$sfm-cache/sources/git/trees/test/revision"),
            },
        });
        let view = SourceProviderView::new(&inventory, &provider, 2);

        assert_eq!(view.id(), "upstream");
        assert_eq!(view.kind(), SourceProviderKind::Git);
        assert_eq!(view.priority(), 2);
        assert_eq!(view.status(), SourceStatus::Missing);
        assert_eq!(
            view.unavailable_reason(),
            Some("managed repository and materialized tree are missing")
        );
        assert_eq!(
            view.searchable_roots(),
            vec![
                view.tree_path().join("src/main/java"),
                view.tree_path().join("projects/api/src")
            ]
        );

        let SourceProviderV3::Git(provider) = &provider else {
            unreachable!()
        };
        std::fs::create_dir_all(
            inventory.local_path(&provider.derived_checks.repository_cache_path),
        )
        .expect("repository cache");
        for root in view.searchable_roots() {
            std::fs::create_dir_all(root).expect("searchable root");
        }
        assert_eq!(view.status(), SourceStatus::Acquired);
    }

    #[test]
    fn decompile_provider_requires_a_matching_completion_fingerprint() {
        let cache = tempfile::tempdir().expect("temporary cache");
        let inventory = fixture(CacheHome(cache.path().to_path_buf()));
        let provider = SourceProviderV3::Decompile(DecompileSourceProviderV3 {
            id: "vineflower".to_owned(),
            declaration: DecompileSourceDeclarationV3 {
                roots: vec!["src".to_owned()],
            },
            derived_checks: DecompileSourceDerivedChecksV3 {
                binary_artifact_id: "binary".to_owned(),
                decompiler_artifact_id: "decompiler".to_owned(),
                fingerprint: "blake3:fixture".to_owned(),
                tree_cache_path: PathBuf::from("$sfm-cache/sources/decompiled/fixture/tree"),
            },
        });
        let view = SourceProviderView::new(&inventory, &provider, 0);
        let tree = view.tree_path();
        std::fs::create_dir_all(tree.join("src")).expect("source root");

        assert_eq!(view.status(), SourceStatus::Missing);
        std::fs::write(tree.join(FINGERPRINT_FILE), "blake3:fixture").expect("completion marker");
        assert_eq!(view.status(), SourceStatus::Acquired);
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
