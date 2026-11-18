use super::ExpectedPaths;
use super::expected_paths;
use super::prior_schema_direct_paths;
use super::unavailable_lock_reason;
use crate::branch_targets::WorktreeTarget;
use crate::dependency_inventory::DependencyInventory;
use crate::paths::CacheHome;
use crate::source_projection::core_catalog::CoreCatalog;
use crate::worktree::get_worktrees;
use std::path::Path;

/// Readable cache claims and the owners whose unavailable locks prevent retirement.
#[derive(Default)]
pub(super) struct CacheOwnerClaims {
    pub(super) paths: ExpectedPaths,
    pub(super) unknown: Vec<String>,
    pub(super) readable_lock_count: usize,
    pub(super) prior_schema_direct_count: usize,
}

impl CacheOwnerClaims {
    pub(super) fn collect(
        catalog: &CoreCatalog,
        selected_key: &str,
        physical: &Path,
        cache: &CacheHome,
    ) -> eyre::Result<Self> {
        let mut claims = Self::default();
        claims.collect_catalog(catalog, selected_key, cache)?;
        claims.collect_worktrees(catalog, physical, cache)?;
        Ok(claims)
    }

    fn collect_catalog(
        &mut self,
        catalog: &CoreCatalog,
        selected_key: &str,
        cache: &CacheHome,
    ) -> eyre::Result<()> {
        for key in catalog.catalog.0.keys() {
            if key.as_str() == selected_key {
                continue;
            }
            let project = catalog.repo_root.join(catalog.catalog.project_dir(key)?);
            let lock = project.join("sfm-toolchain.lock.json");
            if !lock.is_file() {
                self.unknown
                    .push(format!("catalog {key}: lock unavailable"));
                continue;
            }
            if let Some(paths) = prior_schema_direct_paths(&lock, cache)? {
                self.paths.direct.extend(paths);
                self.prior_schema_direct_count += 1;
                self.unknown.push(format!(
                "catalog {key}: prior schema 2 direct paths counted; derived resources and consumers unknown"
            ));
                continue;
            }
            match DependencyInventory::load_project(&project, cache.clone()) {
                Ok(inventory) => {
                    self.paths.extend(expected_paths(&inventory)?);
                    self.readable_lock_count += 1;
                }
                Err(error) => self.unknown.push(format!(
                    "catalog {key}: {}",
                    unavailable_lock_reason(&lock, &error)
                )),
            }
        }
        Ok(())
    }

    fn collect_worktrees(
        &mut self,
        catalog: &CoreCatalog,
        physical: &Path,
        cache: &CacheHome,
    ) -> eyre::Result<()> {
        // Other worktrees may intentionally point at this same physical root.
        // A missing/offline worktree cannot be taken as proof of non-ownership.
        for worktree in get_worktrees(&catalog.repo_root)? {
            let target = WorktreeTarget::from_worktree(worktree)?;
            if dunce::canonicalize(target.worktree_path.as_path())
                .ok()
                .as_deref()
                == Some(catalog.repo_root.as_path())
            {
                continue;
            }
            let other_alias = target.worktree_path.join("sfm-toolchain-data");
            let Ok(other_physical) = dunce::canonicalize(&other_alias) else {
                self.unknown.push(format!(
                    "worktree {}: cache root unavailable",
                    target.branch
                ));
                continue;
            };
            if other_physical != physical {
                continue;
            }
            match DependencyInventory::load_target(target.clone(), cache.clone()) {
                Ok(inventory) => {
                    self.paths.extend(expected_paths(&inventory)?);
                    self.readable_lock_count += 1;
                }
                Err(error) => self
                    .unknown
                    .push(format!("worktree {}: {error}", target.branch)),
            }
        }
        Ok(())
    }
}
