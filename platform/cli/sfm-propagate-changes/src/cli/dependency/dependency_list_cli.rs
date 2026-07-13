use super::dependency_context::load_inventory;
use crate::cancellation::CancellationToken;
use crate::cli::jar::BranchSelector;
use crate::dependency_inventory::DependencyInventory;
use crate::dependency_inventory::kind_label;
use crate::dependency_inventory::resolved_version;
use crate::dependency_inventory::role_label;
use crate::dependency_inventory::scope_label;
use crate::paths::CacheHome;
use crate::terminal_output::stdout_line;
use crate::toolchain_lockfile_schema::version::v3::DependencyKindV3;
use crate::toolchain_lockfile_schema::version::v3::DependencyV3;
use facet::Facet;
use figue as args;

#[derive(Facet, Debug)]
pub struct DependencyListArgs {
    /// Branch selector to inspect. Must match exactly one worktree.
    #[facet(args::named)]
    pub branch: BranchSelector,
}

impl DependencyListArgs {
    /// # Errors
    ///
    /// Returns an error when branch resolution, lockfile loading, or output fails.
    pub fn invoke(
        self,
        _cancellation_token: CancellationToken,
        cache_home: &CacheHome,
    ) -> eyre::Result<()> {
        let inventory = load_inventory(self.branch, cache_home)?;
        for line in format_inventory(&inventory).lines() {
            stdout_line(line)?;
        }
        Ok(())
    }
}

fn format_inventory(inventory: &DependencyInventory) -> String {
    let mut dependencies: Vec<_> = inventory.dependencies().collect();
    dependencies.sort_by(|left, right| {
        kind_order(left.kind)
            .cmp(&kind_order(right.kind))
            .then_with(|| left.id.cmp(&right.id))
    });
    let mut output = format!(
        "Dependencies for {}\nLockfile: {}\nID | KIND | ROLE | VERSION | SCOPES | BINARY | SOURCES\n",
        inventory.target.branch,
        inventory.lockfile_path.display()
    );
    for dependency in dependencies {
        output.push_str(&format_dependency_row(inventory, dependency));
        output.push('\n');
    }
    output
}

fn format_dependency_row(inventory: &DependencyInventory, dependency: &DependencyV3) -> String {
    let versions = dependency
        .components
        .iter()
        .map(|component| {
            if dependency.components.len() == 1 {
                resolved_version(component)
            } else {
                format!("{}={}", component.id, resolved_version(component))
            }
        })
        .collect::<Vec<_>>()
        .join(",");
    let scopes = dependency
        .components
        .iter()
        .map(|component| {
            let labels = component
                .declaration
                .scopes
                .iter()
                .copied()
                .map(scope_label)
                .collect::<Vec<_>>()
                .join(",");
            if dependency.components.len() == 1 {
                labels
            } else {
                format!("{}=[{}]", component.id, labels)
            }
        })
        .collect::<Vec<_>>()
        .join(";");
    let binary_statuses = dependency
        .components
        .iter()
        .map(|component| inventory.binary_status(component))
        .collect::<Vec<_>>();
    let binary = if binary_statuses
        .iter()
        .all(|status| status.label() == "acquired")
    {
        "acquired"
    } else if binary_statuses
        .iter()
        .any(|status| status.label() == "stale")
    {
        "stale"
    } else {
        "missing"
    };
    let source_labels = dependency
        .components
        .iter()
        .map(|component| inventory.source_status(component).label())
        .collect::<Vec<_>>();
    let sources = if source_labels.iter().all(|label| *label == source_labels[0]) {
        source_labels[0].to_owned()
    } else {
        source_labels.join(",")
    };
    format!(
        "{} | {} | {} | {} | {} | {} | {}",
        dependency.id,
        kind_label(dependency.kind),
        role_label(dependency.role),
        versions,
        scopes,
        binary,
        sources
    )
}

const fn kind_order(kind: DependencyKindV3) -> u8 {
    match kind {
        DependencyKindV3::Minecraft => 0,
        DependencyKindV3::Loader => 1,
        DependencyKindV3::Mod => 2,
        DependencyKindV3::Library => 3,
        DependencyKindV3::Tool => 4,
    }
}

#[cfg(test)]
mod tests {
    use super::format_inventory;
    use crate::branch_targets::BranchName;
    use crate::branch_targets::WorktreePath;
    use crate::branch_targets::WorktreeTarget;
    use crate::dependency_inventory::DependencyInventory;
    use crate::paths::CacheHome;
    use crate::toolchain_lockfile_schema::read_current;
    use std::path::PathBuf;

    #[test]
    fn list_is_deterministic_and_contains_one_cc_tweaked_row() {
        let inventory = fixture();
        let first = format_inventory(&inventory);
        let second = format_inventory(&inventory);

        assert_eq!(first, second);
        assert!(first.starts_with("Dependencies for 1.19.2\nLockfile:"));
        assert_eq!(
            first
                .lines()
                .filter(|line| line.starts_with("cc-tweaked |"))
                .count(),
            1
        );
        assert!(first.contains("cc-tweaked | mod | integration | 1.101.3"));
        assert!(first.contains("gametest-compile"));
        assert!(
            first
                .lines()
                .nth(3)
                .is_some_and(|line| line.starts_with("minecraft |"))
        );
    }

    fn fixture() -> DependencyInventory {
        let input = include_str!("../../../../../minecraft/sfm-toolchain.lock.json");
        DependencyInventory {
            target: WorktreeTarget {
                branch: BranchName::from("1.19.2"),
                worktree_path: WorktreePath::from(PathBuf::from("fixture")),
                core: true,
                mc_version: None,
            },
            lockfile_path: PathBuf::from("fixture/sfm-toolchain.lock.json"),
            cache_home: CacheHome(PathBuf::from("fixture/empty-cache")),
            original_input: input.to_owned(),
            lockfile: read_current(input).expect("v3 fixture"),
        }
    }
}
