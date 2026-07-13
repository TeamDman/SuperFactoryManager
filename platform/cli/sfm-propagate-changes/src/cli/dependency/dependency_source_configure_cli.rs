use super::dependency_context::load_inventory;
use crate::cancellation::CancellationToken;
use crate::cli::jar::BranchSelector;
use crate::paths::CacheHome;
use crate::payload_fetcher::http_fetcher;
use crate::source_decompile::configure_decompile_sources;
use crate::source_git::configure_git_sources;
use crate::source_git::validate_requested_git_revision;
use crate::source_maven::configure_maven_sources;
use crate::terminal_output::stdout_line;
use crate::toolchain_lockfile_schema::version::v3::ComponentAcquisitionV3;
use crate::toolchain_lockfile_schema::version::v3::DependencyKindV3;
use crate::toolchain_lockfile_schema::version::v3::PlatformPipelineSourceDeclarationV3;
use crate::toolchain_lockfile_schema::version::v3::PlatformPipelineSourceDerivedChecksV3;
use crate::toolchain_lockfile_schema::version::v3::PlatformPipelineSourceProviderV3;
use crate::toolchain_lockfile_schema::version::v3::SourceProviderV3;
use crate::toolchain_lockfile_schema::version::v3::ToolchainComponentKindV3;
use crate::toolchain_lockfile_write::write_lockfile_atomically;
use facet::Facet;
use figue as args;
use std::path::PathBuf;

#[derive(Facet, Debug)]
pub struct DependencySourceConfigureArgs {
    /// Dependency or dependency/component to configure.
    #[facet(args::positional)]
    pub target: String,
    /// Branch selector to update. Must match exactly one worktree.
    #[facet(args::named)]
    pub branch: BranchSelector,
    /// Configure the conventional Maven `sources` classifier.
    #[facet(default = false, args::named)]
    pub maven_sources: bool,
    /// Exact alternate Maven source coordinate for unusual publications.
    #[facet(default, args::named)]
    pub maven_coordinate: Option<String>,
    /// Configure deterministic Vineflower fallback sources from locked binaries.
    #[facet(default = false, args::named)]
    pub decompile: bool,
    /// Configure the deterministic Minecraft/loader source pipeline.
    #[facet(default = false, args::named)]
    pub platform_pipeline: bool,
    /// Dependency or dependency/component containing the locked decompiler (defaults to vineflower).
    #[facet(default, args::named)]
    pub decompiler: Option<String>,
    /// HTTPS Git remote to configure as a locked source provider.
    #[facet(default, args::named)]
    pub git_url: Option<String>,
    /// Exact 40-character Git commit or explicit `refs/tags/...` reference required by `--git-url`.
    #[facet(default, args::named)]
    pub git_revision: Option<String>,
    /// Search root relative to the extracted source tree. Repeat as needed.
    #[facet(default, args::named)]
    pub root: Vec<String>,
    /// Move the configured provider to the front of its component's explicit provider order.
    #[facet(default = false, args::named)]
    pub prefer: bool,
}

impl DependencySourceConfigureArgs {
    pub(crate) fn invoke(
        self,
        cancellation_token: &CancellationToken,
        cache_home: &CacheHome,
    ) -> eyre::Result<()> {
        let target = self.target.clone();
        let configuration = SourceConfiguration::from_args(self)?;
        let mut inventory = load_inventory(configuration.branch(), cache_home)?;
        let selected = select_configured_component(&inventory, &target)?;
        let prefer = configuration.prefer();
        let provider =
            configure_source_provider(&inventory, selected, configuration, cancellation_token)?;
        replace_source_provider(&mut inventory, selected, provider.clone(), prefer);
        let output = inventory.lockfile.to_canonical_json()?;
        write_lockfile_atomically(
            &inventory.lockfile_path,
            &inventory.original_input,
            output.as_bytes(),
        )?;
        stdout_line(configured_provider_message(&target, &provider))?;
        Ok(())
    }
}

#[derive(Debug)]
enum SourceConfiguration {
    Maven {
        branch: BranchSelector,
        coordinate: Option<String>,
        roots: Vec<String>,
        prefer: bool,
    },
    Decompile {
        branch: BranchSelector,
        decompiler: Option<String>,
        roots: Vec<String>,
        prefer: bool,
    },
    PlatformPipeline {
        branch: BranchSelector,
        roots: Vec<String>,
        prefer: bool,
    },
    Git {
        branch: BranchSelector,
        remote: String,
        revision: String,
        roots: Vec<String>,
        prefer: bool,
    },
}

impl SourceConfiguration {
    fn from_args(args: DependencySourceConfigureArgs) -> eyre::Result<Self> {
        let DependencySourceConfigureArgs {
            branch,
            maven_sources,
            maven_coordinate,
            decompile,
            platform_pipeline,
            decompiler,
            git_url,
            git_revision,
            root,
            prefer,
            ..
        } = args;
        if git_revision.is_some() && git_url.is_none() {
            eyre::bail!("--git-revision requires --git-url.");
        }
        if decompiler.is_some() && !decompile {
            eyre::bail!("--decompiler requires --decompile.");
        }
        if usize::from(maven_sources)
            + usize::from(maven_coordinate.is_some())
            + usize::from(decompile)
            + usize::from(platform_pipeline)
            + usize::from(git_url.is_some())
            != 1
        {
            eyre::bail!(
                "Specify exactly one of --maven-sources, --maven-coordinate, --decompile, --platform-pipeline, or --git-url."
            );
        }
        if platform_pipeline {
            return Ok(Self::PlatformPipeline {
                branch,
                roots: root,
                prefer,
            });
        }
        if decompile {
            return Ok(Self::Decompile {
                branch,
                decompiler,
                roots: root,
                prefer,
            });
        }
        if let Some(remote) = git_url {
            let revision = git_revision.ok_or_else(|| {
                eyre::eyre!(
                    "--git-url requires --git-revision with an exact commit or explicit refs/tags/... reference."
                )
            })?;
            validate_requested_git_revision(&revision)?;
            return Ok(Self::Git {
                branch,
                remote,
                revision,
                roots: root,
                prefer,
            });
        }
        Ok(Self::Maven {
            branch,
            coordinate: maven_coordinate,
            roots: root,
            prefer,
        })
    }

    fn branch(&self) -> BranchSelector {
        match self {
            Self::Maven { branch, .. }
            | Self::Decompile { branch, .. }
            | Self::PlatformPipeline { branch, .. }
            | Self::Git { branch, .. } => branch.clone(),
        }
    }

    const fn prefer(&self) -> bool {
        match self {
            Self::Maven { prefer, .. }
            | Self::Decompile { prefer, .. }
            | Self::PlatformPipeline { prefer, .. }
            | Self::Git { prefer, .. } => *prefer,
        }
    }
}

#[derive(Clone, Copy)]
struct ConfiguredComponent {
    dependency_index: usize,
    component_index: usize,
}

fn select_configured_component(
    inventory: &crate::dependency_inventory::DependencyInventory,
    target: &str,
) -> eyre::Result<ConfiguredComponent> {
    let (dependency_id, component_id) = split_target(target)?;
    let dependency_index = inventory
        .lockfile
        .dependencies
        .iter()
        .position(|dependency| dependency.id == dependency_id)
        .ok_or_else(|| eyre::eyre!("Unknown dependency '{dependency_id}'."))?;
    let component_index = select_component_index(
        &inventory.lockfile.dependencies[dependency_index].components,
        dependency_id,
        component_id,
    )?;
    Ok(ConfiguredComponent {
        dependency_index,
        component_index,
    })
}

fn configure_source_provider(
    inventory: &crate::dependency_inventory::DependencyInventory,
    selected: ConfiguredComponent,
    configuration: SourceConfiguration,
    cancellation_token: &CancellationToken,
) -> eyre::Result<SourceProviderV3> {
    let component = &inventory.lockfile.dependencies[selected.dependency_index].components
        [selected.component_index];
    match configuration {
        SourceConfiguration::Maven {
            coordinate, roots, ..
        } => configure_maven_sources(
            inventory,
            component,
            coordinate.as_deref(),
            roots,
            cancellation_token,
            &http_fetcher()?,
        )?
        .map(SourceProviderV3::MavenSources)
        .ok_or_else(|| {
            eyre::eyre!(
                "No Maven source payload was published for '{}'. Configure Git or decompile sources instead.",
                component.id
            )
        }),
        SourceConfiguration::Git {
            remote,
            revision,
            roots,
            ..
        } => configure_git_sources(inventory, &remote, &revision, roots, cancellation_token)
            .map(SourceProviderV3::Git),
        SourceConfiguration::Decompile {
            decompiler, roots, ..
        } => {
            let decompiler_target = decompiler.as_deref().unwrap_or("vineflower");
            let decompiler = select_configured_component(inventory, decompiler_target)?;
            let decompiler_component = &inventory.lockfile.dependencies[decompiler.dependency_index]
                .components[decompiler.component_index];
            configure_decompile_sources(inventory, component, decompiler_component, roots)
                .map(SourceProviderV3::Decompile)
        }
        SourceConfiguration::PlatformPipeline { roots, .. } => {
            configure_platform_pipeline_sources(inventory, selected, roots)
        }
    }
}

fn configure_platform_pipeline_sources(
    inventory: &crate::dependency_inventory::DependencyInventory,
    selected: ConfiguredComponent,
    roots: Vec<String>,
) -> eyre::Result<SourceProviderV3> {
    let selected_dependency = &inventory.lockfile.dependencies[selected.dependency_index];
    let kind = match selected_dependency.kind {
        DependencyKindV3::Minecraft => ToolchainComponentKindV3::Minecraft,
        DependencyKindV3::Loader => ToolchainComponentKindV3::Loader,
        _ => eyre::bail!(
            "Platform pipelines may only be configured for the Minecraft or loader dependency, not '{}'.",
            selected_dependency.id
        ),
    };
    let minecraft_dependency = inventory
        .lockfile
        .dependencies
        .iter()
        .find(|dependency| dependency.id == inventory.lockfile.platform.minecraft_dependency)
        .ok_or_else(|| eyre::eyre!("The configured Minecraft platform dependency is missing."))?;
    let loader_dependency = inventory
        .lockfile
        .dependencies
        .iter()
        .find(|dependency| dependency.id == inventory.lockfile.platform.loader_dependency)
        .ok_or_else(|| eyre::eyre!("The configured loader platform dependency is missing."))?;
    let minecraft_version =
        platform_requested_version(minecraft_dependency, ToolchainComponentKindV3::Minecraft)?;
    let loader_version =
        platform_requested_version(loader_dependency, ToolchainComponentKindV3::Loader)?;
    let (fingerprint, tree_cache_path) = if loader_dependency.id == "neoforge" {
        (
            format!("neogradle-{loader_version}"),
            PathBuf::from(format!(
                "build/sfm-toolchain/neoform/{minecraft_version}/classes/gameSourcesWithNeoForge.filetree"
            )),
        )
    } else {
        (
            format!("forgegradle-{loader_version}"),
            PathBuf::from(format!(
                "build/sfm-toolchain/forge/{minecraft_version}/sources/combined-deobfuscated.filetree"
            )),
        )
    };
    let id = match kind {
        ToolchainComponentKindV3::Minecraft => "minecraft-pipeline",
        ToolchainComponentKindV3::Loader => "loader-pipeline",
    };
    Ok(SourceProviderV3::PlatformPipeline(
        PlatformPipelineSourceProviderV3 {
            id: id.to_owned(),
            declaration: PlatformPipelineSourceDeclarationV3 { kind, roots },
            derived_checks: PlatformPipelineSourceDerivedChecksV3 {
                fingerprint,
                tree_cache_path,
            },
        },
    ))
}

fn platform_requested_version(
    dependency: &crate::toolchain_lockfile_schema::version::v3::DependencyV3,
    expected_kind: ToolchainComponentKindV3,
) -> eyre::Result<&str> {
    dependency
        .components
        .iter()
        .find_map(|component| match &component.declaration.acquisition {
            ComponentAcquisitionV3::Toolchain(acquisition) if acquisition.kind == expected_kind => {
                Some(acquisition.requested_version.as_str())
            }
            _ => None,
        })
        .ok_or_else(|| {
            eyre::eyre!(
                "Platform dependency '{}' has no {} toolchain component.",
                dependency.id,
                match expected_kind {
                    ToolchainComponentKindV3::Minecraft => "Minecraft",
                    ToolchainComponentKindV3::Loader => "loader",
                }
            )
        })
}

fn replace_source_provider(
    inventory: &mut crate::dependency_inventory::DependencyInventory,
    selected: ConfiguredComponent,
    provider: SourceProviderV3,
    prefer: bool,
) {
    let providers = &mut inventory.lockfile.dependencies[selected.dependency_index].components
        [selected.component_index]
        .source_providers;
    let existing_position = providers
        .iter()
        .position(|existing| same_provider_identity(existing, &provider));
    providers.retain(|existing| !same_provider_identity(existing, &provider));
    let position = if prefer {
        0
    } else {
        existing_position.unwrap_or(providers.len())
    };
    providers.insert(position, provider);
}

fn same_provider_identity(left: &SourceProviderV3, right: &SourceProviderV3) -> bool {
    match (left, right) {
        (SourceProviderV3::MavenSources(left), SourceProviderV3::MavenSources(right)) => {
            left.id == right.id
        }
        (SourceProviderV3::Git(left), SourceProviderV3::Git(right)) => left.id == right.id,
        (SourceProviderV3::Decompile(left), SourceProviderV3::Decompile(right)) => {
            left.id == right.id
        }
        (SourceProviderV3::PlatformPipeline(left), SourceProviderV3::PlatformPipeline(right)) => {
            left.id == right.id
        }
        _ => false,
    }
}

fn configured_provider_message(target: &str, provider: &SourceProviderV3) -> String {
    match provider {
        SourceProviderV3::MavenSources(provider) => format!(
            "Configured Maven sources for {target}: {}",
            provider.derived_checks.resolved_coordinate
        ),
        SourceProviderV3::Git(_) => format!("Configured locked Git sources for {target}."),
        SourceProviderV3::Decompile(_) => {
            format!("Configured Vineflower fallback sources for {target}.")
        }
        SourceProviderV3::PlatformPipeline(_) => {
            format!("Configured platform-pipeline sources for {target}.")
        }
    }
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

fn select_component_index(
    components: &[crate::toolchain_lockfile_schema::version::v3::DependencyComponentV3],
    dependency_id: &str,
    component_id: Option<&str>,
) -> eyre::Result<usize> {
    if let Some(component_id) = component_id {
        return components
            .iter()
            .position(|component| component.id == component_id)
            .ok_or_else(|| eyre::eyre!("Unknown component '{dependency_id}/{component_id}'."));
    }
    if components.len() != 1 {
        eyre::bail!(
            "Dependency '{dependency_id}' has multiple components; select dependency/component explicitly."
        );
    }
    Ok(0)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn git_configuration_accepts_explicit_tags_and_retains_preference() {
        let _error = SourceConfiguration::from_args(args(Some("abc")))
            .expect_err("ambiguous Git revision must be rejected");
        let configuration = SourceConfiguration::from_args(args(Some("refs/tags/v1.19.2-1.101.3")))
            .expect("explicit Git tag should be accepted");

        assert!(matches!(
            configuration,
            SourceConfiguration::Git { prefer: true, .. }
        ));
    }

    #[test]
    fn platform_pipeline_configuration_is_explicit() {
        let mut input = args(None);
        input.git_url = None;
        input.platform_pipeline = true;
        let configuration =
            SourceConfiguration::from_args(input).expect("platform pipeline should be accepted");

        assert!(matches!(
            configuration,
            SourceConfiguration::PlatformPipeline { prefer: true, .. }
        ));
    }

    fn args(git_revision: Option<&str>) -> DependencySourceConfigureArgs {
        DependencySourceConfigureArgs {
            target: "cc-tweaked".to_owned(),
            branch: BranchSelector::from("1.19.2".to_owned()),
            maven_sources: false,
            maven_coordinate: None,
            decompile: false,
            platform_pipeline: false,
            decompiler: None,
            git_url: Some("https://github.com/cc-tweaked/CC-Tweaked.git".to_owned()),
            git_revision: git_revision.map(ToOwned::to_owned),
            root: Vec::new(),
            prefer: true,
        }
    }
}
