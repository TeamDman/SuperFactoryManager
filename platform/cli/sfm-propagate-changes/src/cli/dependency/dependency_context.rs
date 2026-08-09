use crate::cli::jar::BranchSelector;
use crate::dependency_inventory::DependencyInventory;
use crate::paths::CacheHome;

pub(super) fn load_inventory(
    branch: BranchSelector,
    cache_home: &CacheHome,
) -> eyre::Result<DependencyInventory> {
    let query = branch.into_query()?;
    DependencyInventory::load(&query, cache_home.clone())
}
