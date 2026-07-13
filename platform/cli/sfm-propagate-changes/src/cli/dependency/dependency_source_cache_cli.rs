use super::dependency_context::load_inventory;
use crate::branch_targets::WorktreeTarget;
use crate::branch_targets::discover_worktree_targets;
use crate::cancellation::CancellationToken;
use crate::cli::jar::BranchSelector;
use crate::dependency_inventory::DependencyInventory;
use crate::paths::CacheHome;
use crate::terminal_output::stdout_line;
use crate::toolchain_lockfile_schema::version::v3::SourceProviderV3;
use facet::Facet;
use figue as args;
use std::collections::BTreeSet;
use std::fs;
use std::path::Path;
use std::path::PathBuf;

#[derive(Facet, Debug)]
pub struct DependencySourceCacheArgs {
    #[facet(args::subcommand)]
    pub command: DependencySourceCacheCommand,
}

#[derive(Facet, Debug)]
#[repr(u8)]
pub enum DependencySourceCacheCommand {
    /// Report managed Git source-cache references and unreferenced entries without deleting anything.
    Audit(DependencySourceCacheAuditArgs),
    /// Remove unreferenced managed Git cache entries after verifying every readable v3 lockfile.
    Cleanup(DependencySourceCacheCleanupArgs),
}

#[derive(Facet, Debug)]
pub struct DependencySourceCacheAuditArgs {
    /// Branch selector whose configured providers are shown. Must match exactly one worktree.
    #[facet(args::named)]
    pub branch: BranchSelector,
}

#[derive(Facet, Debug)]
pub struct DependencySourceCacheCleanupArgs {
    /// Branch selector used to verify the current dependency workspace. Must match exactly one worktree.
    #[facet(args::named)]
    pub branch: BranchSelector,
    /// Actually remove the audited entries. Without this flag cleanup only reports what is safe to remove.
    #[facet(default = false, args::named)]
    pub confirm: bool,
}

impl DependencySourceCacheArgs {
    pub(crate) fn invoke(
        self,
        _cancellation_token: &CancellationToken,
        cache_home: &CacheHome,
    ) -> eyre::Result<()> {
        match self.command {
            DependencySourceCacheCommand::Audit(args) => args.invoke(cache_home),
            DependencySourceCacheCommand::Cleanup(args) => args.invoke(cache_home),
        }
    }
}

impl DependencySourceCacheAuditArgs {
    fn invoke(self, cache_home: &CacheHome) -> eyre::Result<()> {
        let inventory = load_inventory(self.branch, cache_home)?;
        let references = discovered_git_cache_references(cache_home)?;
        render_git_cache_audit(&audit_git_cache(&inventory, &references))
    }
}

impl DependencySourceCacheCleanupArgs {
    fn invoke(self, cache_home: &CacheHome) -> eyre::Result<()> {
        let inventory = load_inventory(self.branch, cache_home)?;
        let references = discovered_git_cache_references(cache_home)?;
        let audit = audit_git_cache(&inventory, &references);
        render_git_cache_audit(&audit)?;
        ensure_cleanup_is_safe(&references)?;
        if !self.confirm {
            stdout_line(
                "No cache entries were removed. Re-run with --confirm to remove the audited unreferenced entries.",
            )?;
            return Ok(());
        }
        let cleanup = remove_unreferenced_git_cache_entries(&inventory, &references.references)?;
        stdout_line(format!(
            "Removed {} unreferenced repositories and {} unreferenced trees.",
            cleanup.repositories, cleanup.trees
        ))?;
        Ok(())
    }
}

#[derive(Default)]
struct GitCacheReferences {
    repositories: BTreeSet<PathBuf>,
    trees: BTreeSet<PathBuf>,
}

impl GitCacheReferences {
    fn extend(&mut self, other: Self) {
        self.repositories.extend(other.repositories);
        self.trees.extend(other.trees);
    }
}

#[derive(Default)]
struct DiscoveredGitCacheReferences {
    references: GitCacheReferences,
    unverified_lockfiles: Vec<UnverifiedLockfile>,
}

#[derive(Clone, Debug, Eq, PartialEq)]
struct UnverifiedLockfile {
    branch: String,
    path: PathBuf,
    reason: String,
}

fn discovered_git_cache_references(
    cache_home: &CacheHome,
) -> eyre::Result<DiscoveredGitCacheReferences> {
    let mut result = DiscoveredGitCacheReferences::default();
    for target in discover_worktree_targets()? {
        if !lockfile_path(&target).is_file() {
            continue;
        }
        match DependencyInventory::load_target(target.clone(), cache_home.clone()) {
            Ok(inventory) => result.references.extend(git_cache_references(&inventory)),
            Err(error) => result.unverified_lockfiles.push(UnverifiedLockfile {
                branch: target.branch.to_string(),
                path: lockfile_path(&target),
                reason: error_chain(&error),
            }),
        }
    }
    Ok(result)
}

fn error_chain(error: &eyre::Report) -> String {
    error
        .chain()
        .map(ToString::to_string)
        .collect::<Vec<_>>()
        .join(": ")
}

fn lockfile_path(target: &WorktreeTarget) -> PathBuf {
    target
        .worktree_path
        .join("platform")
        .join("minecraft")
        .join("sfm-toolchain.lock.json")
}

fn git_cache_references(inventory: &DependencyInventory) -> GitCacheReferences {
    let mut references = GitCacheReferences::default();
    for dependency in &inventory.lockfile.dependencies {
        for component in &dependency.components {
            for provider in &component.source_providers {
                let SourceProviderV3::Git(provider) = provider else {
                    continue;
                };
                references
                    .repositories
                    .insert(inventory.local_path(&provider.derived_checks.repository_cache_path));
                references
                    .trees
                    .insert(inventory.local_path(&provider.derived_checks.tree_cache_path));
            }
        }
    }
    references
}

#[derive(Debug, Eq, PartialEq)]
struct GitCacheAudit {
    providers: Vec<GitProviderAudit>,
    referenced_repositories: usize,
    materialized_trees: usize,
    unreferenced_repositories: usize,
    unreferenced_trees: usize,
    unverified_lockfiles: Vec<UnverifiedLockfile>,
}

#[derive(Debug, Eq, PartialEq)]
struct GitProviderAudit {
    dependency: String,
    component: String,
    commit: String,
    repository_present: bool,
    tree_present: bool,
}

fn audit_git_cache(
    inventory: &DependencyInventory,
    references: &DiscoveredGitCacheReferences,
) -> GitCacheAudit {
    let mut providers = Vec::new();
    for dependency in &inventory.lockfile.dependencies {
        for component in &dependency.components {
            for provider in &component.source_providers {
                let SourceProviderV3::Git(provider) = provider else {
                    continue;
                };
                let repository =
                    inventory.local_path(&provider.derived_checks.repository_cache_path);
                let tree = inventory.local_path(&provider.derived_checks.tree_cache_path);
                providers.push(GitProviderAudit {
                    dependency: dependency.id.clone(),
                    component: component.id.clone(),
                    commit: provider.derived_checks.commit.clone(),
                    repository_present: repository.is_dir(),
                    tree_present: tree.is_dir(),
                });
            }
        }
    }
    providers.sort_by(|left, right| {
        (&left.dependency, &left.component, &left.commit).cmp(&(
            &right.dependency,
            &right.component,
            &right.commit,
        ))
    });
    let git_root = inventory.local_path(Path::new("$sfm-cache/sources/git"));
    let repositories = child_directories(&git_root.join("repositories"));
    let trees = git_tree_directories(&git_root.join("trees"));
    GitCacheAudit {
        referenced_repositories: references.references.repositories.len(),
        materialized_trees: references
            .references
            .trees
            .iter()
            .filter(|tree| tree.is_dir())
            .count(),
        unreferenced_repositories: repositories
            .iter()
            .filter(|path| !references.references.repositories.contains(*path))
            .count(),
        unreferenced_trees: trees
            .iter()
            .filter(|path| !references.references.trees.contains(*path))
            .count(),
        providers,
        unverified_lockfiles: references.unverified_lockfiles.clone(),
    }
}

fn render_git_cache_audit(audit: &GitCacheAudit) -> eyre::Result<()> {
    stdout_line("Git source cache audit:")?;
    for provider in &audit.providers {
        stdout_line(format!(
            "  {}/{}: commit={} repository={} tree={}",
            provider.dependency,
            provider.component,
            provider.commit,
            status_label(provider.repository_present),
            status_label(provider.tree_present),
        ))?;
    }
    stdout_line(format!(
        "  globally referenced repositories: {}",
        audit.referenced_repositories
    ))?;
    stdout_line(format!(
        "  materialized trees: {}",
        audit.materialized_trees
    ))?;
    stdout_line(format!(
        "  unreferenced repositories: {}",
        audit.unreferenced_repositories
    ))?;
    stdout_line(format!(
        "  unreferenced trees: {}",
        audit.unreferenced_trees
    ))?;
    if !audit.unverified_lockfiles.is_empty() {
        stdout_line("  cleanup blocked by unverified lockfiles:")?;
        for lockfile in &audit.unverified_lockfiles {
            stdout_line(format!(
                "    {} ({}): {}",
                lockfile.branch,
                lockfile.path.display(),
                lockfile.reason
            ))?;
        }
    }
    Ok(())
}

fn ensure_cleanup_is_safe(references: &DiscoveredGitCacheReferences) -> eyre::Result<()> {
    if references.unverified_lockfiles.is_empty() {
        return Ok(());
    }
    let lockfiles = references
        .unverified_lockfiles
        .iter()
        .map(|lockfile| format!("{} ({})", lockfile.branch, lockfile.path.display()))
        .collect::<Vec<_>>()
        .join(", ");
    eyre::bail!(
        "Refusing cache cleanup until every discovered lockfile is readable as schema v3: {lockfiles}."
    );
}

#[derive(Debug, Eq, PartialEq)]
struct GitCacheCleanup {
    repositories: usize,
    trees: usize,
}

fn remove_unreferenced_git_cache_entries(
    inventory: &DependencyInventory,
    references: &GitCacheReferences,
) -> eyre::Result<GitCacheCleanup> {
    let git_root = inventory.local_path(Path::new("$sfm-cache/sources/git"));
    let repositories_root = git_root.join("repositories");
    let trees_root = git_root.join("trees");
    let repositories =
        remove_unreferenced_directories(&repositories_root, &references.repositories)?;
    let trees = remove_unreferenced_tree_directories(&trees_root, &references.trees)?;
    Ok(GitCacheCleanup {
        repositories,
        trees,
    })
}

fn remove_unreferenced_directories(
    root: &Path,
    references: &BTreeSet<PathBuf>,
) -> eyre::Result<usize> {
    let mut removed = 0;
    for path in child_directories(root) {
        if references.contains(&path) {
            continue;
        }
        if path.parent() != Some(root) {
            eyre::bail!("Refusing to remove cache entry outside {}", root.display());
        }
        fs::remove_dir_all(&path)?;
        removed += 1;
    }
    Ok(removed)
}

fn remove_unreferenced_tree_directories(
    trees_root: &Path,
    references: &BTreeSet<PathBuf>,
) -> eyre::Result<usize> {
    let mut removed = 0;
    for path in git_tree_directories(trees_root) {
        if references.contains(&path) {
            continue;
        }
        if path.parent().and_then(Path::parent) != Some(trees_root) {
            eyre::bail!(
                "Refusing to remove cache tree outside {}",
                trees_root.display()
            );
        }
        fs::remove_dir_all(&path)?;
        removed += 1;
    }
    Ok(removed)
}

fn git_tree_directories(root: &Path) -> Vec<PathBuf> {
    child_directories(root)
        .into_iter()
        .flat_map(|remote| child_directories(&remote))
        .collect()
}

fn child_directories(path: &Path) -> Vec<PathBuf> {
    fs::read_dir(path)
        .into_iter()
        .flatten()
        .flatten()
        .filter_map(|entry| {
            entry
                .file_type()
                .ok()
                .filter(|kind| kind.is_dir() && !kind.is_symlink())
                .map(|_| entry.path())
        })
        .collect()
}

const fn status_label(present: bool) -> &'static str {
    if present { "present" } else { "missing" }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::branch_targets::BranchName;
    use crate::branch_targets::WorktreePath;
    use crate::toolchain_lockfile_schema::read_current;
    use crate::toolchain_lockfile_schema::version::v3::GitSourceDeclarationV3;
    use crate::toolchain_lockfile_schema::version::v3::GitSourceDerivedChecksV3;
    use crate::toolchain_lockfile_schema::version::v3::GitSourceProviderV3;

    #[test]
    fn cleanup_removes_only_entries_not_referenced_by_any_verified_lockfile() {
        let cache = tempfile::tempdir().expect("temporary cache");
        let mut inventory = fixture(CacheHome(cache.path().to_path_buf()));
        let (repository, tree) = add_git_provider(&mut inventory);
        std::fs::create_dir_all(&repository).expect("referenced repository");
        std::fs::create_dir_all(&tree).expect("referenced tree");
        let git_root = inventory.local_path(Path::new("$sfm-cache/sources/git"));
        let stale_repository = git_root.join("repositories/stale.git");
        let stale_tree = git_root.join("trees/stale/commit");
        std::fs::create_dir_all(&stale_repository).expect("stale repository");
        std::fs::create_dir_all(&stale_tree).expect("stale tree");
        let references = DiscoveredGitCacheReferences {
            references: git_cache_references(&inventory),
            unverified_lockfiles: Vec::new(),
        };

        let audit = audit_git_cache(&inventory, &references);
        assert_eq!(audit.unreferenced_repositories, 1);
        assert_eq!(audit.unreferenced_trees, 1);

        let removed = remove_unreferenced_git_cache_entries(&inventory, &references.references)
            .expect("cache cleanup should succeed");
        assert_eq!(removed.repositories, 1);
        assert_eq!(removed.trees, 1);
        assert!(repository.is_dir());
        assert!(tree.is_dir());
        assert!(!stale_repository.exists());
        assert!(!stale_tree.exists());
    }

    #[test]
    fn cleanup_requires_every_discovered_lockfile_to_be_verified() {
        let references = DiscoveredGitCacheReferences {
            references: GitCacheReferences::default(),
            unverified_lockfiles: vec![UnverifiedLockfile {
                branch: "1.20.1".to_owned(),
                path: PathBuf::from("1.20.1/sfm-toolchain.lock.json"),
                reason: "schema version 2".to_owned(),
            }],
        };

        let error = ensure_cleanup_is_safe(&references)
            .expect_err("cleanup must reject an unverified lockfile");
        assert!(error.to_string().contains("1.20.1"));
    }

    fn add_git_provider(inventory: &mut DependencyInventory) -> (PathBuf, PathBuf) {
        let dependency = inventory
            .lockfile
            .dependencies
            .iter_mut()
            .find(|dependency| dependency.id == "cc-tweaked")
            .expect("CC:Tweaked fixture");
        let component = dependency
            .components
            .iter_mut()
            .find(|component| component.id == "main")
            .expect("CC:Tweaked main component");
        let provider = GitSourceProviderV3 {
            id: "git".to_owned(),
            declaration: GitSourceDeclarationV3 {
                remote_url: "https://example.invalid/cc-tweaked".to_owned(),
                requested_revision: "0123456789012345678901234567890123456789".to_owned(),
                roots: Vec::new(),
            },
            derived_checks: GitSourceDerivedChecksV3 {
                commit: "0123456789012345678901234567890123456789".to_owned(),
                repository_cache_path: PathBuf::from(
                    "$sfm-cache/sources/git/repositories/referenced.git",
                ),
                tree_cache_path: PathBuf::from("$sfm-cache/sources/git/trees/referenced/commit"),
            },
        };
        let repository_cache_path = provider.derived_checks.repository_cache_path.clone();
        let tree_cache_path = provider.derived_checks.tree_cache_path.clone();
        component
            .source_providers
            .push(SourceProviderV3::Git(provider));
        let repository = inventory.local_path(&repository_cache_path);
        let tree = inventory.local_path(&tree_cache_path);
        (repository, tree)
    }

    fn fixture(cache_home: CacheHome) -> DependencyInventory {
        let input = include_str!("../../../../../minecraft/sfm-toolchain.lock.json");
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
