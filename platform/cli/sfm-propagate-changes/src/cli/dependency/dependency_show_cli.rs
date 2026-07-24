use super::dependency_context::load_inventory;
use crate::cancellation::CancellationToken;
use crate::cli::jar::BranchSelector;
use crate::dependency_inventory::DependencyInventory;
use crate::dependency_inventory::kind_label;
use crate::dependency_inventory::resolved_version;
use crate::dependency_inventory::role_label;
use crate::dependency_inventory::scope_label;
use crate::paths::CacheHome;
use crate::source_provider::SourceProviderView;
use crate::terminal_output::stdout_line;
use crate::toolchain_lockfile_schema::version::v3::ArtifactTreatmentV3;
use crate::toolchain_lockfile_schema::version::v3::ComponentAcquisitionV3;
use crate::toolchain_lockfile_schema::version::v3::DataRunPolicyV3;
use crate::toolchain_lockfile_schema::version::v3::DependencyComponentV3;
use crate::toolchain_lockfile_schema::version::v3::DependencyV3;
use crate::toolchain_lockfile_schema::version::v3::SourceProviderV3;
use facet::Facet;
use figue as args;
use std::path::Path;

#[derive(Facet, Debug)]
pub struct DependencyShowArgs {
    /// Stable logical dependency ID.
    #[facet(args::positional)]
    pub id: String,

    /// Branch selector to inspect. Must match exactly one worktree.
    #[facet(args::named)]
    pub branch: BranchSelector,
}

impl DependencyShowArgs {
    /// # Errors
    ///
    /// Returns an error when branch resolution, lockfile loading, lookup, or output fails.
    pub fn invoke(
        self,
        _cancellation_token: CancellationToken,
        cache_home: &CacheHome,
    ) -> eyre::Result<()> {
        let inventory = load_inventory(self.branch, cache_home)?;
        let dependency = inventory.dependency(&self.id)?;
        for line in format_dependency(&inventory, dependency).lines() {
            stdout_line(line)?;
        }
        Ok(())
    }
}

fn format_dependency(inventory: &DependencyInventory, dependency: &DependencyV3) -> String {
    let mut output = format!(
        "Dependency: {}\nLockfile: {}\nKind: {}\nRole: {}\nDisplay name: {}\nProject URL: {}\nNotes: {}\n",
        dependency.id,
        inventory.lockfile_path.display(),
        kind_label(dependency.kind),
        role_label(dependency.role),
        dependency.display_name.as_deref().unwrap_or("not declared"),
        dependency.project_url.as_deref().unwrap_or("not declared"),
        dependency.notes.as_deref().unwrap_or("not declared")
    );
    for component in &dependency.components {
        output.push_str(&format_component(inventory, component));
    }
    output
}

fn format_component(inventory: &DependencyInventory, component: &DependencyComponentV3) -> String {
    let artifact = inventory.artifact(component);
    let repository = artifact.repository_id.as_deref().and_then(|id| {
        inventory
            .lockfile
            .repositories
            .iter()
            .find(|repository| repository.id == id)
    });
    let scopes = component
        .declaration
        .scopes
        .iter()
        .copied()
        .map(scope_label)
        .collect::<Vec<_>>()
        .join(", ");
    let local_binary = inventory.local_path(&component.derived_checks.cache_path);
    let binary_status = inventory.binary_status(component).label();
    let bundle = component.declaration.bundle.as_ref().map_or_else(
        || "not bundled".to_string(),
        |bundle| {
            format!(
                "range={}, artifact_version={}, is_obfuscated={}",
                bundle.accepted_version_range, bundle.artifact_version, bundle.is_obfuscated
            )
        },
    );
    let mut output = format!(
        "\nComponent: {}\n  Acquisition: {}\n  Scopes: {}\n  Bundle policy: {}\n  Artifact treatment: {}\n  Data runs: {}\n  Resolved coordinate: {}\n  Resolved version: {}\n  Repository: {}\n  Repository URL: {}\n  Artifact URL: {}\n  Hash: {}\n  Locked cache path: {}\n  Binary JAR: {}: {}\n  Transformed JAR: not tracked by schema v3\n  Sources status: {}\n",
        component.id,
        acquisition_label(&component.declaration.acquisition),
        scopes,
        bundle,
        treatment_label(component.declaration.artifact_treatment),
        data_policy_label(component.declaration.data_run_policy),
        component
            .derived_checks
            .resolved_coordinate
            .as_deref()
            .unwrap_or("not applicable"),
        resolved_version(component),
        artifact
            .repository_id
            .as_deref()
            .unwrap_or("not applicable"),
        repository.map_or("not applicable", |repository| repository.url.as_str()),
        artifact.url.as_deref().unwrap_or("not applicable"),
        artifact.hash,
        component.derived_checks.cache_path.display(),
        binary_status,
        local_binary.display(),
        inventory.source_status(component).label()
    );
    if component.source_providers.is_empty() {
        output.push_str("  Source strategies: none declared\n");
    } else {
        output.push_str("  Source strategies:\n");
        for provider in inventory.source_providers(component) {
            output.push_str(&format_provider(inventory, provider));
        }
    }
    output
}

fn format_provider(inventory: &DependencyInventory, provider: SourceProviderView<'_>) -> String {
    let roots = provider
        .searchable_roots()
        .iter()
        .map(|root| root.display().to_string())
        .collect::<Vec<_>>()
        .join(", ");
    let mut output = format!(
        "    {} ({})\n      priority: {}\n      status: {}\n      searchable roots: {}\n",
        provider.id(),
        provider.kind().label(),
        provider.priority(),
        provider.status().label(),
        roots
    );
    let details = match provider.definition() {
        SourceProviderV3::MavenSources(provider) => {
            let archive = inventory.local_path(&provider.derived_checks.archive_cache_path);
            let archive_status = inventory
                .locked_file_status(
                    &provider.derived_checks.archive_cache_path,
                    provider.derived_checks.hash,
                )
                .label();
            format!(
                "      requested: {}\n      resolved: {}\n      archive: {}: {}\n      tree: {}\n",
                provider.declaration.requested_coordinate,
                provider.derived_checks.resolved_coordinate,
                archive_status,
                archive.display(),
                path_status(
                    &inventory.local_path(&provider.derived_checks.tree_cache_path),
                    true
                )
            )
        }
        SourceProviderV3::Git(provider) => format!(
            "      remote: {}\n      requested revision: {}\n      commit: {}\n      repository cache: {}\n      tree: {}\n",
            provider.declaration.remote_url,
            provider.declaration.requested_revision,
            provider.derived_checks.commit,
            path_status(
                &inventory.local_path(&provider.derived_checks.repository_cache_path),
                true
            ),
            path_status(
                &inventory.local_path(&provider.derived_checks.tree_cache_path),
                true
            )
        ),
        SourceProviderV3::Decompile(provider) => format!(
            "      binary artifact: {}\n      decompiler artifact: {}\n      fingerprint: {}\n      tree: {}\n",
            provider.derived_checks.binary_artifact_id,
            provider.derived_checks.decompiler_artifact_id,
            provider.derived_checks.fingerprint,
            path_status(
                &inventory.local_path(&provider.derived_checks.tree_cache_path),
                true
            )
        ),
        SourceProviderV3::PlatformPipeline(provider) => format!(
            "      fingerprint: {}\n      tree: {}\n",
            provider.derived_checks.fingerprint,
            path_status(
                &inventory.local_path(&provider.derived_checks.tree_cache_path),
                true
            )
        ),
    };
    output.push_str(&details);
    output
}

fn path_status(path: &Path, directory: bool) -> String {
    let exists = if directory {
        path.is_dir()
    } else {
        path.is_file()
    };
    format!(
        "{}: {}",
        if exists { "acquired" } else { "missing" },
        path.display()
    )
}

fn acquisition_label(acquisition: &ComponentAcquisitionV3) -> String {
    match acquisition {
        ComponentAcquisitionV3::Maven(acquisition) => {
            format!("maven {}", acquisition.requested_coordinate)
        }
        ComponentAcquisitionV3::CurseForge(acquisition) => format!(
            "curseforge {} project={} file={}",
            acquisition.slug, acquisition.project_id, acquisition.file_id
        ),
        ComponentAcquisitionV3::Http(acquisition) => format!("http {}", acquisition.url),
        ComponentAcquisitionV3::Toolchain(acquisition) => {
            format!("toolchain {}", acquisition.requested_version)
        }
    }
}

const fn treatment_label(treatment: ArtifactTreatmentV3) -> &'static str {
    match treatment {
        ArtifactTreatmentV3::LoaderManagedMod => "loader-managed-mod",
        ArtifactTreatmentV3::Plain => "plain",
    }
}

const fn data_policy_label(policy: DataRunPolicyV3) -> &'static str {
    match policy {
        DataRunPolicyV3::Exclude => "exclude",
        DataRunPolicyV3::Include => "include",
    }
}

#[cfg(test)]
mod tests {
    use super::format_dependency;
    use crate::branch_targets::BranchName;
    use crate::branch_targets::WorktreePath;
    use crate::branch_targets::WorktreeTarget;
    use crate::dependency_inventory::DependencyInventory;
    use crate::paths::CacheHome;
    use crate::toolchain_lockfile_schema::read_current;
    use std::path::PathBuf;

    #[test]
    fn show_reports_missing_paths_without_claiming_acquisition() {
        let inventory = fixture();
        let dependency = inventory.dependency("cc-tweaked").expect("CC:Tweaked");
        let output = format_dependency(&inventory, dependency);

        assert!(output.contains("Dependency: cc-tweaked"));
        assert!(output.contains("Artifact treatment: loader-managed-mod"));
        assert!(output.contains("Data runs: include"));
        assert!(output.contains("Hash: blake3:"));
        assert!(output.contains("Binary JAR: missing:"));
        assert!(output.contains("Transformed JAR: not tracked by schema v3"));
        assert!(output.contains("maven-sources (maven-sources)"));
        assert!(output.contains("status: missing"));
    }

    #[test]
    fn show_preserves_multicomponent_identity() {
        let inventory = fixture();
        let dependency = inventory.dependency("mekanism").expect("Mekanism");
        let output = format_dependency(&inventory, dependency);

        assert!(output.contains("Component: api"));
        assert!(output.contains("Component: main"));
        assert!(output.contains("Artifact treatment: plain"));
        assert!(output.contains("Artifact treatment: loader-managed-mod"));
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
