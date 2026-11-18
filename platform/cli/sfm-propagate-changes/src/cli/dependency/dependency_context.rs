use crate::cli::jar::BranchSelector;
use crate::dependency_inventory::DependencyInventory;
use crate::paths::AppHome;
use crate::paths::CacheHome;

pub(super) fn load_inventory(
    branch: BranchSelector,
    cache_home: &CacheHome,
    app_home: &AppHome,
) -> eyre::Result<DependencyInventory> {
    let query = branch.into_query(app_home)?;
    DependencyInventory::load(&query, cache_home.clone(), app_home)
}
