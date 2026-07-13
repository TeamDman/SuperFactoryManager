use super::dependency_context::load_inventory;
use crate::cancellation::CancellationToken;
use crate::cli::jar::BranchSelector;
use crate::dependency_inventory::DependencyInventory;
use crate::paths::CacheHome;
use crate::terminal_output::stdout_line;
use crate::toolchain_lockfile_schema::version::v3::DependencyRoleV3;
use crate::toolchain_lockfile_schema::version::v3::SourceProviderV3;
use crate::toolchain_lockfile_write::write_lockfile_atomically;
use facet::Facet;
use figue as args;
use std::collections::BTreeSet;

#[derive(Facet, Debug)]
pub struct DependencyRemoveArgs {
    /// Logical dependency or dependency/component ID to remove.
    #[facet(args::positional)]
    pub target: String,
    /// Branch selector to update. Must match exactly one worktree.
    #[facet(args::named)]
    pub branch: BranchSelector,
}

impl DependencyRemoveArgs {
    /// # Errors
    ///
    /// Returns an error when the target is invalid, protected, referenced, or cannot be written.
    pub fn invoke(
        self,
        cancellation_token: &CancellationToken,
        cache_home: &CacheHome,
    ) -> eyre::Result<()> {
        cancellation_token.bail_if_cancelled()?;
        let inventory = load_inventory(self.branch, cache_home)?;
        let removed = remove_target(inventory, &self.target)?;
        stdout_line(format!("Removed {removed}."))?;
        Ok(())
    }
}

fn remove_target(mut inventory: DependencyInventory, target: &str) -> eyre::Result<String> {
    let (dependency_id, component_id) = split_target(target)?;
    let dependency_index = inventory
        .lockfile
        .dependencies
        .iter()
        .position(|dependency| dependency.id == dependency_id)
        .ok_or_else(|| eyre::eyre!("Unknown dependency '{dependency_id}'."))?;
    let dependency = &inventory.lockfile.dependencies[dependency_index];
    if dependency.role == DependencyRoleV3::Platform {
        eyre::bail!(
            "Required platform dependency '{}' cannot be removed through dependency remove.",
            dependency.id
        );
    }

    let removed_components = if let Some(component_id) = component_id {
        if dependency.components.len() == 1 {
            eyre::bail!(
                "Cannot remove the last component from '{}'; remove the whole dependency instead.",
                dependency.id
            );
        }
        let component_index = dependency
            .components
            .iter()
            .position(|component| component.id == component_id)
            .ok_or_else(|| eyre::eyre!("Unknown component '{dependency_id}/{component_id}'."))?;
        let component = inventory.lockfile.dependencies[dependency_index]
            .components
            .remove(component_index);
        vec![component]
    } else {
        inventory
            .lockfile
            .dependencies
            .remove(dependency_index)
            .components
    };
    let removed_artifact_ids: BTreeSet<_> = removed_components
        .iter()
        .map(|component| component.derived_checks.artifact_id.clone())
        .collect();
    let remaining_references = referenced_artifacts(&inventory);
    inventory.lockfile.artifacts.retain_mut(|artifact| {
        if !removed_artifact_ids.contains(&artifact.id) {
            return true;
        }
        if remaining_references.contains(&artifact.id) {
            if artifact
                .owner
                .as_ref()
                .is_some_and(|owner| owner.dependency_id == dependency_id)
            {
                artifact.owner = None;
            }
            true
        } else {
            false
        }
    });
    let output = inventory.lockfile.to_canonical_json()?;
    write_lockfile_atomically(
        &inventory.lockfile_path,
        &inventory.original_input,
        output.as_bytes(),
    )?;
    Ok(target.to_owned())
}

fn referenced_artifacts(inventory: &DependencyInventory) -> BTreeSet<String> {
    let mut referenced = BTreeSet::new();
    for dependency in &inventory.lockfile.dependencies {
        for component in &dependency.components {
            referenced.insert(component.derived_checks.artifact_id.clone());
            for provider in &component.source_providers {
                if let SourceProviderV3::Decompile(provider) = provider {
                    referenced.insert(provider.derived_checks.binary_artifact_id.clone());
                    referenced.insert(provider.derived_checks.decompiler_artifact_id.clone());
                }
            }
        }
    }
    referenced
}

fn split_target(target: &str) -> eyre::Result<(&str, Option<&str>)> {
    let mut parts = target.split('/');
    let dependency = parts.next().unwrap_or_default();
    let component = parts.next();
    if dependency.is_empty() || parts.next().is_some() || component.is_some_and(str::is_empty) {
        eyre::bail!("Expected dependency or dependency/component, got '{target}'.");
    }
    Ok((dependency, component))
}

#[cfg(test)]
#[path = "dependency_remove_tests.rs"]
mod tests;
