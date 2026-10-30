//! Bounded, read-only reachability report. This is not a garbage collector.

use crate::branch_targets::WorktreeTarget;
use crate::dependency_inventory::DependencyInventory;
use crate::paths::CacheHome;
use crate::source_cache::SourceCacheLayout;
use crate::source_projection::candidate_lock::is_reparse;
use crate::source_projection::core_catalog::CoreCatalog;
use crate::terminal_output::stdout_line;
use crate::toolchain_lockfile_schema::ToolchainLockfileDocument;
use crate::toolchain_lockfile_schema::parse_document;
use crate::toolchain_lockfile_schema::version::v3::SourceProviderV3;
use eyre::Context as _;
use eyre::ensure;
use facet::Facet;
use figue as args;
use std::collections::BTreeSet;
use std::fs;
use std::path::Component;
use std::path::{Path, PathBuf};
use std::process::Command;

const MAX_MAVEN_ENTRIES: usize = 100_000;
const ROOT_MARKER: &str = ".sfm-native-cache-root.json";

#[derive(Facet, Debug)]
pub struct CacheReportArgs {
    /// Exact catalogued projection root in this checkout.
    #[facet(args::named)]
    pub project_root: PathBuf,
    /// Existing owned cache root; defaults to the selected checkout's sfm-toolchain-data.
    #[facet(default, args::named)]
    pub cache_root: Option<PathBuf>,
}

#[derive(Default)]
struct ExpectedPaths {
    direct: BTreeSet<PathBuf>,
    derived: BTreeSet<PathBuf>,
}

impl ExpectedPaths {
    fn extend(&mut self, other: Self) {
        self.direct.extend(other.direct);
        self.derived.extend(other.derived);
    }
}

impl CacheReportArgs {
    /// Report a checkout's expected resources and bounded candidates, without mutation.
    pub fn invoke(self) -> eyre::Result<()> {
        let (catalog, selected_key) = CoreCatalog::for_project(&self.project_root)?;
        let logical = self
            .cache_root
            .unwrap_or_else(|| catalog.repo_root.join("sfm-toolchain-data"));
        let physical = checked_existing_root(&logical)?;
        let cache = CacheHome(physical.clone());
        let selected = DependencyInventory::load_project(&self.project_root, cache.clone())?;
        let selected_paths = expected_paths(&selected)?;
        let mut known_owners = ExpectedPaths::default();
        let mut unknown_owners = Vec::new();
        let mut known_owner_count = 0;
        let mut prior_schema_direct_count = 0;
        for key in catalog.catalog.0.keys() {
            if key == &selected_key {
                continue;
            }
            let project = catalog.repo_root.join(catalog.catalog.project_dir(key)?);
            let lock = project.join("sfm-toolchain.lock.json");
            if !lock.is_file() {
                unknown_owners.push(format!("catalog {key}: lock unavailable"));
                continue;
            }
            if let Some(paths) = prior_schema_direct_paths(&lock, &cache)? {
                known_owners.direct.extend(paths);
                prior_schema_direct_count += 1;
                unknown_owners.push(format!(
                    "catalog {key}: prior schema 2 direct paths counted; derived resources and consumers unknown"
                ));
                continue;
            }
            match DependencyInventory::load_project(&project, cache.clone()) {
                Ok(inventory) => {
                    known_owners.extend(expected_paths(&inventory)?);
                    known_owner_count += 1;
                }
                Err(error) => unknown_owners.push(format!(
                    "catalog {key}: {}",
                    unavailable_lock_reason(&lock, &error)
                )),
            }
        }
        // Other worktrees may intentionally point at this same physical root.
        // A missing/offline worktree cannot be taken as proof of non-ownership.
        for worktree in crate::worktree::get_worktrees(&catalog.repo_root)? {
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
                unknown_owners.push(format!(
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
                    known_owners.extend(expected_paths(&inventory)?);
                    known_owner_count += 1;
                }
                Err(error) => unknown_owners.push(format!("worktree {}: {error}", target.branch)),
            }
        }
        let producer_paths =
            crate::source_projection::nfrt_child_identity_supplement::reviewed_producer_jar_paths(
                &catalog.repo_root,
                &physical.join("minecraft-toolchain/maven"),
            )?;
        known_owners.direct.extend(producer_paths.iter().cloned());
        let revision = checkout_revision(&catalog.repo_root)?;
        let schema = match parse_document(&selected.original_input)? {
            ToolchainLockfileDocument::V3(_) => 3,
            ToolchainLockfileDocument::V4(_) => 4,
            _ => eyre::bail!("selected lock must use supported schema 3 or 4"),
        };
        stdout_line("Read-only SFM cache reachability report; candidates are NOT global orphans")?;
        stdout_line(format!(
            "  checkout: {} @ {revision}",
            catalog.repo_root.display()
        ))?;
        stdout_line(format!(
            "  selected profile: {selected_key}; lock schema: {schema}"
        ))?;
        stdout_line(format!(
            "  selected lock SHA-256: {}",
            crate::source_projection::provenance::sha256(selected.original_input.as_bytes())
        ))?;
        stdout_line(format!("  logical cache alias: {}", logical.display()))?;
        stdout_line(format!("  physical cache target: {}", physical.display()))?;
        stdout_line(format!(
            "  logical alias is a link/reparse point: {}",
            is_reparse(&fs::symlink_metadata(&logical)?)
        ))?;
        stdout_line(format!("  other readable owner locks: {known_owner_count}"))?;
        stdout_line(format!(
            "  prior-schema locks with exact direct paths only: {prior_schema_direct_count}"
        ))?;
        stdout_line(format!(
            "  reviewed NeoForm producer recipe JAR paths: {}",
            producer_paths.len()
        ))?;
        for owner in &unknown_owners {
            stdout_line(format!("  unknown owner: {owner}"))?;
        }
        stdout_line(format!(
            "  exact selected direct artifacts: {}",
            selected_paths.direct.len()
        ))?;
        for path in &selected_paths.direct {
            stdout_line(format!("    direct: {}", path.display()))?;
        }
        stdout_line(format!(
            "  conservative selected source outputs: {}",
            selected_paths.derived.len()
        ))?;
        for path in &selected_paths.derived {
            stdout_line(format!("    derived: {}", path.display()))?;
        }
        // Maven JAR leaves are the one direct resource class whose physical
        // layout is stable and enumerable without walking extracted source trees.
        let observed = existing_maven_jars(&physical.join("minecraft-toolchain/maven"))?;
        let (other_owner, unclassified) =
            classify_observed(&observed, &selected_paths.direct, &known_owners.direct);
        stdout_line(format!("  observed Maven JARs: {}", observed.len()))?;
        stdout_line(format!(
            "  selected-checkout missing; known other owner: {}",
            other_owner.len()
        ))?;
        stdout_line(format!(
            "  unclassified Maven JARs (may be producer tools, not cleanup candidates): {}",
            unclassified.len()
        ))?;
        for path in unclassified {
            stdout_line(format!("    unclassified: {}", path.display()))?;
        }
        let git_root = physical.join("minecraft-toolchain/sources/git");
        let mut git_entries = child_directories(&git_root.join("repositories"))?;
        for remote in child_directories(&git_root.join("trees"))? {
            git_entries.extend(child_directories(&remote)?);
        }
        let (git_other, git_unclassified) =
            classify_observed(&git_entries, &selected_paths.derived, &known_owners.derived);
        stdout_line(format!(
            "  observed Git repositories and trees: {}",
            git_entries.len()
        ))?;
        stdout_line(format!(
            "  Git entries held by known other locks: {}",
            git_other.len()
        ))?;
        stdout_line(format!(
            "  unclassified Git entries (not deletion candidates): {}",
            git_unclassified.len()
        ))?;
        for path in git_unclassified {
            stdout_line(format!("    unclassified Git: {}", path.display()))?;
        }
        stdout_line(
            "  unknown classes: non-JAR Maven metadata, extracted/decompiled/platform source trees, derived preparation, Minecraft assets, symbol indexes, prior-schema derived resources, receipts and snapshots",
        )?;
        stdout_line(
            "  retirement blocked: producer recipes, other/offline owners and active readers/publishers are not fully known; report never deletes or follows descendants of a cache link for cleanup",
        )?;
        Ok(())
    }
}

fn checked_existing_root(logical: &Path) -> eyre::Result<PathBuf> {
    let physical = dunce::canonicalize(logical).wrap_err_with(|| {
        format!(
            "cache root is missing or a broken link: {}",
            logical.display()
        )
    })?;
    ensure!(physical.is_dir(), "cache root is not a directory");
    let metadata = fs::symlink_metadata(&physical)?;
    ensure!(
        !is_reparse(&metadata),
        "physical cache root is a reparse point"
    );
    let raw = fs::read_to_string(physical.join(ROOT_MARKER))
        .wrap_err("cache root has no readable native ownership marker")?;
    let marker: (String, String) = facet_json::from_str(&raw)?;
    ensure!(
        marker.0 == "sfm:native_cache_root@1" && marker.1 == physical.to_string_lossy(),
        "cache link/target does not match the native ownership marker"
    );
    Ok(physical)
}

fn checkout_revision(root: &Path) -> eyre::Result<String> {
    let output = Command::new("git")
        .args([
            "-C",
            &root.to_string_lossy(),
            "rev-parse",
            "--verify",
            "HEAD",
        ])
        .output()?;
    ensure!(
        output.status.success(),
        "cannot read selected checkout HEAD"
    );
    Ok(String::from_utf8(output.stdout)?.trim().to_owned())
}

fn unavailable_lock_reason(path: &Path, error: &eyre::Report) -> String {
    if let Ok(input) = fs::read_to_string(path) {
        match parse_document(&input) {
            Ok(ToolchainLockfileDocument::V1(_)) => {
                return "prior schema 1; reachability unknown".into();
            }
            Ok(ToolchainLockfileDocument::V2 { .. }) => {
                return "prior schema 2; reachability unknown".into();
            }
            _ => {}
        }
    }
    error
        .chain()
        .map(ToString::to_string)
        .collect::<Vec<_>>()
        .join(": ")
}

/// Schema-2 locks retain exact portable artifact paths, but not the current
/// source-provider/derived-resource contract. Count only direct cache paths.
fn prior_schema_direct_paths(
    lock: &Path,
    cache: &CacheHome,
) -> eyre::Result<Option<BTreeSet<PathBuf>>> {
    let input = fs::read_to_string(lock)?;
    let ToolchainLockfileDocument::V2 { lockfile, .. } = parse_document(&input)? else {
        return Ok(None);
    };
    let mut paths = BTreeSet::new();
    for artifact in lockfile.artifacts {
        let relative = artifact
            .cache_path
            .strip_prefix("$sfm-cache")
            .wrap_err_with(|| {
                format!(
                    "prior-schema artifact is not cache-relative: {}",
                    artifact.cache_path.display()
                )
            })?;
        ensure!(
            relative
                .components()
                .all(|component| matches!(component, Component::Normal(_))),
            "prior-schema artifact has an unsafe cache path: {}",
            artifact.cache_path.display()
        );
        paths.insert(cache.join("minecraft-toolchain").join(relative));
    }
    Ok(Some(paths))
}

fn expected_paths(inventory: &DependencyInventory) -> eyre::Result<ExpectedPaths> {
    let mut paths = ExpectedPaths::default();
    for artifact in &inventory.lockfile.artifacts {
        paths
            .direct
            .insert(inventory.local_path(&artifact.cache_path));
        if let Some(source_git) = &artifact.source_git {
            if let Some(remote) = source_git.remote_url.as_deref() {
                let remote = crate::source_git::canonical_https_remote(remote)?;
                paths.derived.insert(
                    inventory.local_path(
                        &SourceCacheLayout::git(&remote, &source_git.commit).repository,
                    ),
                );
            }
        }
    }
    for dependency in &inventory.lockfile.dependencies {
        for component in &dependency.components {
            for provider in &component.source_providers {
                match provider {
                    SourceProviderV3::MavenSources(provider) => {
                        paths.direct.insert(
                            inventory.local_path(&provider.derived_checks.archive_cache_path),
                        );
                        paths
                            .derived
                            .insert(inventory.local_path(&provider.derived_checks.tree_cache_path));
                    }
                    SourceProviderV3::Git(provider) => {
                        paths.derived.insert(
                            inventory.local_path(&provider.derived_checks.repository_cache_path),
                        );
                        paths
                            .derived
                            .insert(inventory.local_path(&provider.derived_checks.tree_cache_path));
                    }
                    SourceProviderV3::Decompile(provider) => {
                        paths
                            .derived
                            .insert(inventory.local_path(&provider.derived_checks.tree_cache_path));
                    }
                    SourceProviderV3::PlatformPipeline(provider) => {
                        paths
                            .derived
                            .insert(inventory.local_path(&provider.derived_checks.tree_cache_path));
                    }
                }
            }
        }
    }
    Ok(paths)
}

fn existing_maven_jars(root: &Path) -> eyre::Result<BTreeSet<PathBuf>> {
    let mut paths = BTreeSet::new();
    if !root.is_dir() {
        return Ok(paths);
    }
    for (count, entry) in walkdir::WalkDir::new(root)
        .follow_links(false)
        .into_iter()
        .enumerate()
    {
        let entry = entry?;
        ensure!(
            count < MAX_MAVEN_ENTRIES,
            "Maven report entry limit reached"
        );
        let metadata = fs::symlink_metadata(entry.path())?;
        ensure!(
            !is_reparse(&metadata),
            "Maven report encountered a link/reparse entry: {}",
            entry.path().display()
        );
        if entry.file_type().is_file()
            && entry
                .path()
                .extension()
                .is_some_and(|ext| ext.eq_ignore_ascii_case("jar"))
        {
            paths.insert(entry.path().to_path_buf());
        }
    }
    Ok(paths)
}

fn child_directories(root: &Path) -> eyre::Result<BTreeSet<PathBuf>> {
    let mut paths = BTreeSet::new();
    if !root.is_dir() {
        return Ok(paths);
    }
    for entry in fs::read_dir(root)? {
        let entry = entry?;
        let metadata = fs::symlink_metadata(entry.path())?;
        ensure!(
            !is_reparse(&metadata),
            "Git report encountered a link/reparse entry: {}",
            entry.path().display()
        );
        if metadata.is_dir() {
            paths.insert(entry.path());
        }
    }
    Ok(paths)
}

fn classify_observed(
    observed: &BTreeSet<PathBuf>,
    selected: &BTreeSet<PathBuf>,
    other: &BTreeSet<PathBuf>,
) -> (BTreeSet<PathBuf>, BTreeSet<PathBuf>) {
    let not_selected = observed
        .difference(selected)
        .cloned()
        .collect::<BTreeSet<_>>();
    let other_owner = not_selected.intersection(other).cloned().collect();
    let unclassified = not_selected.difference(other).cloned().collect();
    (other_owner, unclassified)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn other_or_offline_owner_never_makes_a_global_orphan() {
        let a = PathBuf::from("cache/a.jar");
        let b = PathBuf::from("cache/b.jar");
        let observed = BTreeSet::from([a.clone(), b.clone()]);
        let selected = BTreeSet::from([a]);
        let (other, unclassified) =
            classify_observed(&observed, &selected, &BTreeSet::from([b.clone()]));
        assert_eq!(other, BTreeSet::from([b]));
        assert!(unclassified.is_empty());
    }

    #[test]
    fn wrong_marker_and_missing_root_fail_closed_without_creating_data() {
        let root = tempfile::tempdir().unwrap();
        assert!(checked_existing_root(&root.path().join("missing")).is_err());
        fs::write(
            root.path().join(ROOT_MARKER),
            r#"["sfm:native_cache_root@1","wrong-target"]"#,
        )
        .unwrap();
        assert!(checked_existing_root(root.path()).is_err());
    }

    #[test]
    fn historical_lock_contributes_only_exact_direct_cache_paths() {
        let root = Path::new(env!("CARGO_MANIFEST_DIR"));
        let lock =
            root.join("../../minecraft/projections/sfm-4.34.0/mc-1.19.2/sfm-toolchain.lock.json");
        let cache = CacheHome(PathBuf::from("cache-root"));
        let paths = prior_schema_direct_paths(&lock, &cache)
            .unwrap()
            .expect("historical release uses schema 2");
        assert!(!paths.is_empty());
        assert!(
            paths
                .iter()
                .all(|path| path.starts_with(cache.join("minecraft-toolchain")))
        );
        assert!(
            paths
                .iter()
                .any(|path| path.ends_with("mixin-0.8.5-processor.jar"))
        );
    }
}
