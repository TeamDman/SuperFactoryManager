use super::dependency_context::load_inventory;
use crate::cancellation::CancellationToken;
use crate::cli::jar::BranchSelector;
use crate::curseforge::CurseforgeApiSecret;
use crate::curseforge::CurseforgeHttpClient;
use crate::curseforge::CurseforgeModLoader;
use crate::curseforge::CurseforgeProjectFileId;
use crate::curseforge::CurseforgeProjectId;
use crate::curseforge::CurseforgeProjectMetadata;
use crate::curseforge::file_matches_version_and_loader;
use crate::dependency_inventory::DependencyInventory;
use crate::jar_build::hash::ContentHash;
use crate::jar_build::hash::ContentHashAlgorithm;
use crate::paths::CacheHome;
pub(super) use crate::payload_fetcher::PayloadFetcher as ArtifactFetcher;
pub(super) use crate::payload_fetcher::http_fetcher;
pub(super) use crate::payload_fetcher::write_payload_atomically as write_cache_file_atomically;
use crate::terminal_output::stdout_line;
use crate::toolchain_lockfile_schema::version::v3::ArtifactOwnerV3;
use crate::toolchain_lockfile_schema::version::v3::ArtifactProvenanceV3;
use crate::toolchain_lockfile_schema::version::v3::ArtifactPurposeV3;
use crate::toolchain_lockfile_schema::version::v3::ArtifactTreatmentV3;
use crate::toolchain_lockfile_schema::version::v3::ArtifactV3;
use crate::toolchain_lockfile_schema::version::v3::BundlePolicyV3;
use crate::toolchain_lockfile_schema::version::v3::ComponentAcquisitionV3;
use crate::toolchain_lockfile_schema::version::v3::ComponentDeclarationV3;
use crate::toolchain_lockfile_schema::version::v3::ComponentDerivedChecksV3;
use crate::toolchain_lockfile_schema::version::v3::CurseForgeAcquisitionV3;
use crate::toolchain_lockfile_schema::version::v3::DataRunPolicyV3;
use crate::toolchain_lockfile_schema::version::v3::DependencyComponentV3;
use crate::toolchain_lockfile_schema::version::v3::DependencyKindV3;
use crate::toolchain_lockfile_schema::version::v3::DependencyRoleV3;
use crate::toolchain_lockfile_schema::version::v3::DependencyScopeV3;
use crate::toolchain_lockfile_schema::version::v3::DependencyV3;
use crate::toolchain_lockfile_schema::version::v3::MavenAcquisitionV3;
use crate::toolchain_lockfile_write::write_lockfile_atomically;
use facet::Facet;
use figue as args;
use std::collections::BTreeSet;
use std::path::PathBuf;

#[derive(Facet, Debug)]
pub struct DependencyAddArgs {
    /// Stable logical dependency ID.
    #[facet(args::positional)]
    pub id: String,
    /// Branch selector to update. Must match exactly one worktree.
    #[facet(args::named)]
    pub branch: BranchSelector,
    /// Exact Maven coordinate (`group:artifact:version[:classifier][@extension]`).
    #[facet(default, args::named)]
    pub maven: Option<String>,
    /// Exact `CurseForge` project ID. Requires `--curseforge-file`.
    #[facet(default, args::named)]
    pub curseforge_project: Option<u64>,
    /// Exact `CurseForge` file ID. Requires `--curseforge-project`.
    #[facet(default, args::named)]
    pub curseforge_file: Option<u64>,
    /// Semantic dependency kind. Defaults to `mod`.
    #[facet(default, args::named)]
    pub(crate) kind: Option<DependencyKindV3>,
    /// Semantic dependency role. Defaults to `integration`.
    #[facet(default, args::named)]
    pub(crate) role: Option<DependencyRoleV3>,
    /// `CurseForge` Core API key used only to validate exact project/file metadata.
    #[facet(default, args::named)]
    pub curseforge_api_key: Option<String>,
    /// Legacy `CurseForge` API token fallback; prefer `--curseforge-api-key`.
    #[facet(default, args::named)]
    pub curseforge_token: Option<String>,
    /// 1Password secret reference used when no `CurseForge` API key is configured.
    #[facet(default, args::named)]
    pub curseforge_op_secret: Option<String>,
    /// Semantic scope. Repeat for every required scope.
    #[facet(args::named)]
    pub(crate) scope: Vec<DependencyScopeV3>,
    /// Configured repository ID. When omitted, each configured repository is tried.
    #[facet(default, args::named)]
    pub repository: Option<String>,
    /// Artifact treatment. Mods default to loader-managed-mod.
    #[facet(default, args::named)]
    pub(crate) artifact_treatment: Option<ArtifactTreatmentV3>,
    /// Loader-compatible Maven range used for Jar-in-Jar sharing.
    #[facet(default, args::named)]
    pub(crate) bundle_accepted_version_range: Option<String>,
    /// Exact version recorded in Jar-in-Jar metadata. Defaults to the Maven coordinate version.
    #[facet(default, args::named)]
    pub(crate) bundle_artifact_version: Option<String>,
    /// Whether nested classes use Minecraft obfuscated names. Plain Java libraries must omit this flag.
    #[facet(default, args::named)]
    pub(crate) bundle_is_obfuscated: bool,
    /// Human-readable display name.
    #[facet(default, args::named)]
    pub display_name: Option<String>,
    /// Upstream project URL.
    #[facet(default, args::named)]
    pub project_url: Option<String>,
    /// Maintainer notes.
    #[facet(default, args::named)]
    pub notes: Option<String>,
}

impl DependencyAddArgs {
    /// # Errors
    ///
    /// Returns an error when resolution, validation, cache writing, or lockfile writing fails.
    pub fn invoke(
        self,
        cancellation_token: &CancellationToken,
        cache_home: &CacheHome,
    ) -> eyre::Result<()> {
        cancellation_token.bail_if_cancelled()?;
        let inventory = load_inventory(self.branch.clone(), cache_home)?;
        let report = match selected_add_source(&self)? {
            DependencyAddSource::Maven => {
                add_dependency(inventory, &self, cancellation_token, &http_fetcher()?)?
            }
            DependencyAddSource::Curseforge { project, file } => {
                let (key, _credential_source) = CurseforgeApiSecret::resolve_core(
                    self.curseforge_api_key.clone(),
                    self.curseforge_token.clone(),
                    self.curseforge_op_secret.clone(),
                )?;
                let client = CurseforgeHttpClient::new_core_api(&key)?;
                add_curseforge_dependency(
                    inventory,
                    &self,
                    CurseforgeProjectId(project),
                    CurseforgeProjectFileId(file),
                    client.as_ref(),
                    cancellation_token,
                    &http_fetcher()?,
                )?
            }
        };
        stdout_line(format!(
            "Added {}/main: {} from {} ({})",
            report.dependency_id, report.coordinate, report.repository_id, report.hash
        ))?;
        Ok(())
    }
}

pub(super) struct DependencyAddReport {
    pub(super) dependency_id: String,
    pub(super) component_id: String,
    pub(super) coordinate: String,
    pub(super) repository_id: String,
    pub(super) hash: ContentHash,
}

struct ResolvedMavenArtifact {
    repository_id: String,
    url: String,
    bytes: Vec<u8>,
}

struct LockedComponentEvidence {
    hash: ContentHash,
    cache_path: PathBuf,
    artifact_id: String,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
enum DependencyAddSource {
    Maven,
    Curseforge { project: u64, file: u64 },
}

fn selected_add_source(args: &DependencyAddArgs) -> eyre::Result<DependencyAddSource> {
    match (
        args.maven.as_deref(),
        args.curseforge_project,
        args.curseforge_file,
    ) {
        (Some(_), None, None) => Ok(DependencyAddSource::Maven),
        (None, Some(project), Some(file)) => Ok(DependencyAddSource::Curseforge { project, file }),
        (None, None, None) => eyre::bail!(
            "Specify either --maven or both --curseforge-project and --curseforge-file."
        ),
        _ => eyre::bail!(
            "Use exactly one source: --maven or both --curseforge-project and --curseforge-file."
        ),
    }
}

fn add_dependency(
    mut inventory: DependencyInventory,
    args: &DependencyAddArgs,
    cancellation_token: &CancellationToken,
    fetcher: &dyn ArtifactFetcher,
) -> eyre::Result<DependencyAddReport> {
    validate_dependency_id(&args.id)?;
    if inventory
        .lockfile
        .dependencies
        .iter()
        .any(|dependency| dependency.id == args.id)
    {
        eyre::bail!("Dependency '{}' already exists.", args.id);
    }
    inventory.lockfile.dependencies.push(DependencyV3 {
        id: args.id.clone(),
        kind: args.kind.unwrap_or(DependencyKindV3::Mod),
        role: args.role.unwrap_or(DependencyRoleV3::Integration),
        display_name: args.display_name.clone(),
        project_url: args.project_url.clone(),
        notes: args.notes.clone(),
        components: Vec::new(),
    });
    add_component(inventory, args, "main", cancellation_token, fetcher)
}

pub(super) fn add_component(
    mut inventory: DependencyInventory,
    args: &DependencyAddArgs,
    component_id: &str,
    cancellation_token: &CancellationToken,
    fetcher: &dyn ArtifactFetcher,
) -> eyre::Result<DependencyAddReport> {
    validate_component_id(component_id)?;
    let dependency = inventory
        .lockfile
        .dependencies
        .iter()
        .find(|dependency| dependency.id == args.id)
        .ok_or_else(|| eyre::eyre!("Unknown dependency '{}'.", args.id))?;
    if dependency
        .components
        .iter()
        .any(|component| component.id == component_id)
    {
        eyre::bail!("Component '{}/{}' already exists.", args.id, component_id);
    }
    if args.scope.is_empty() {
        eyre::bail!("At least one --scope is required.");
    }
    let coordinate = MavenCoordinate::parse(maven_coordinate(args)?)?;
    coordinate.require_exact()?;
    validate_bundle_options(args, &coordinate)?;
    let (resolved, evidence, append_artifact) =
        resolve_component_artifact(&inventory, args, &coordinate, cancellation_token, fetcher)?;
    let hash = evidence.hash;
    append_lock_entries(
        &mut inventory,
        args,
        component_id,
        &coordinate,
        &resolved,
        evidence,
        append_artifact,
    );
    let output = inventory.lockfile.to_canonical_json()?;
    write_lockfile_atomically(
        &inventory.lockfile_path,
        &inventory.original_input,
        output.as_bytes(),
    )?;
    Ok(DependencyAddReport {
        dependency_id: args.id.clone(),
        component_id: component_id.to_owned(),
        coordinate: coordinate.canonical,
        repository_id: resolved.repository_id,
        hash,
    })
}

fn resolve_component_artifact(
    inventory: &DependencyInventory,
    args: &DependencyAddArgs,
    coordinate: &MavenCoordinate,
    cancellation_token: &CancellationToken,
    fetcher: &dyn ArtifactFetcher,
) -> eyre::Result<(ResolvedMavenArtifact, LockedComponentEvidence, bool)> {
    let matching_artifact = inventory
        .lockfile
        .artifacts
        .iter()
        .find(|artifact| artifact.coordinate.as_deref() == Some(coordinate.canonical.as_str()));
    if let Some(artifact) = matching_artifact {
        if artifact.owner.is_some() {
            eyre::bail!(
                "Artifact '{}' is already locked by another component.",
                coordinate.canonical
            );
        }
        let repository_id = artifact.repository_id.clone().ok_or_else(|| {
            eyre::eyre!(
                "Locked artifact '{}' has no repository ID.",
                coordinate.canonical
            )
        })?;
        if args
            .repository
            .as_deref()
            .is_some_and(|selected| selected != repository_id.as_str())
        {
            eyre::bail!(
                "Locked artifact '{}' belongs to repository '{}', not the requested '{}'.",
                coordinate.canonical,
                repository_id,
                args.repository.as_deref().unwrap_or_default()
            );
        }
        let url = artifact.url.clone().ok_or_else(|| {
            eyre::eyre!(
                "Locked artifact '{}' has no download URL.",
                coordinate.canonical
            )
        })?;
        return Ok((
            ResolvedMavenArtifact {
                repository_id,
                url,
                bytes: Vec::new(),
            },
            LockedComponentEvidence {
                hash: artifact.hash,
                cache_path: artifact.cache_path.clone(),
                artifact_id: artifact.id.clone(),
            },
            false,
        ));
    }

    let resolved = resolve_artifact(
        inventory,
        coordinate,
        args.repository.as_deref(),
        cancellation_token,
        fetcher,
    )?;
    let hash = ContentHash::from_bytes(&resolved.bytes, ContentHashAlgorithm::Blake3);
    let portable_cache_path = coordinate.portable_cache_path();
    let cache_path = inventory.local_path(&portable_cache_path);
    write_cache_file_atomically(&cache_path, &resolved.bytes)?;
    let artifact_id = format!(
        "{}-{}",
        portable_id(&coordinate.canonical),
        hash.short_hex(8)
    );
    if inventory
        .lockfile
        .artifacts
        .iter()
        .any(|artifact| artifact.id == artifact_id)
    {
        eyre::bail!("Generated artifact ID '{artifact_id}' already exists.");
    }
    Ok((
        resolved,
        LockedComponentEvidence {
            hash,
            cache_path: portable_cache_path,
            artifact_id,
        },
        true,
    ))
}

fn maven_coordinate(args: &DependencyAddArgs) -> eyre::Result<&str> {
    if selected_add_source(args)? != DependencyAddSource::Maven {
        eyre::bail!("This command path requires an exact --maven coordinate.");
    }
    args.maven
        .as_deref()
        .ok_or_else(|| eyre::eyre!("Missing required --maven coordinate."))
}

fn add_curseforge_dependency(
    mut inventory: DependencyInventory,
    args: &DependencyAddArgs,
    project_id: CurseforgeProjectId,
    file_id: CurseforgeProjectFileId,
    metadata: &impl CurseforgeProjectMetadata,
    cancellation_token: &CancellationToken,
    fetcher: &dyn ArtifactFetcher,
) -> eyre::Result<DependencyAddReport> {
    validate_new_curseforge_dependency(&inventory, args)?;
    cancellation_token.bail_if_cancelled()?;
    let project = metadata.fetch_project(project_id)?;
    if project.id != project_id {
        eyre::bail!(
            "CurseForge metadata returned project {} instead of requested project {project_id}.",
            project.id
        );
    }
    let slug = project
        .slug
        .as_deref()
        .ok_or_else(|| eyre::eyre!("CurseForge project {project_id} did not include a slug."))?;
    validate_curseforge_slug(slug)?;
    let file = metadata.fetch_project_file(project_id, file_id)?;
    let (minecraft_version, loader) = curseforge_target_context(&inventory)?;
    validate_curseforge_file(&file, project_id, file_id, &minecraft_version, loader)?;

    let coordinate = MavenCoordinate::parse(&format!("curse.maven:{slug}-{project_id}:{file_id}"))?;
    if inventory
        .lockfile
        .artifacts
        .iter()
        .any(|artifact| artifact.coordinate.as_deref() == Some(coordinate.canonical.as_str()))
    {
        eyre::bail!("Artifact '{}' is already locked.", coordinate.canonical);
    }
    let resolved = resolve_artifact(
        &inventory,
        &coordinate,
        Some("cursemaven"),
        cancellation_token,
        fetcher,
    )?;
    let hash = ContentHash::from_bytes(&resolved.bytes, ContentHashAlgorithm::Blake3);
    let portable_cache_path = coordinate.portable_cache_path();
    write_cache_file_atomically(&inventory.local_path(&portable_cache_path), &resolved.bytes)?;
    let artifact_id = format!(
        "{}-{}",
        portable_id(&coordinate.canonical),
        hash.short_hex(8)
    );
    if inventory
        .lockfile
        .artifacts
        .iter()
        .any(|artifact| artifact.id == artifact_id)
    {
        eyre::bail!("Generated artifact ID '{artifact_id}' already exists.");
    }
    append_curseforge_lock_entries(
        &mut inventory,
        args,
        CurseforgeLockEntryInputs {
            project_id,
            file_id,
            slug,
            project_name: &project.name,
            coordinate: &coordinate,
            resolved: &resolved,
            evidence: LockedComponentEvidence {
                hash,
                cache_path: portable_cache_path,
                artifact_id,
            },
        },
    );
    let output = inventory.lockfile.to_canonical_json()?;
    write_lockfile_atomically(
        &inventory.lockfile_path,
        &inventory.original_input,
        output.as_bytes(),
    )?;
    Ok(DependencyAddReport {
        dependency_id: args.id.clone(),
        component_id: "main".to_owned(),
        coordinate: coordinate.canonical,
        repository_id: resolved.repository_id,
        hash,
    })
}

fn validate_new_curseforge_dependency(
    inventory: &DependencyInventory,
    args: &DependencyAddArgs,
) -> eyre::Result<()> {
    validate_dependency_id(&args.id)?;
    if inventory
        .lockfile
        .dependencies
        .iter()
        .any(|dependency| dependency.id == args.id)
    {
        eyre::bail!("Dependency '{}' already exists.", args.id);
    }
    if args.scope.is_empty() {
        eyre::bail!("At least one --scope is required.");
    }
    if args.scope.contains(&DependencyScopeV3::Bundle)
        || args.bundle_accepted_version_range.is_some()
        || args.bundle_artifact_version.is_some()
        || args.bundle_is_obfuscated
    {
        eyre::bail!("bundle policy is supported only for exact Maven dependencies");
    }
    if args
        .repository
        .as_deref()
        .is_some_and(|id| id != "cursemaven")
    {
        eyre::bail!("CurseForge dependencies use the configured 'cursemaven' repository.");
    }
    Ok(())
}

fn append_lock_entries(
    inventory: &mut DependencyInventory,
    args: &DependencyAddArgs,
    component_id: &str,
    coordinate: &MavenCoordinate,
    resolved: &ResolvedMavenArtifact,
    evidence: LockedComponentEvidence,
    append_artifact: bool,
) {
    let scopes: Vec<_> = args
        .scope
        .iter()
        .copied()
        .collect::<BTreeSet<_>>()
        .into_iter()
        .collect();
    let purposes = purposes_for_scopes(&scopes);
    let component = DependencyComponentV3 {
        id: component_id.to_owned(),
        declaration: ComponentDeclarationV3 {
            acquisition: ComponentAcquisitionV3::Maven(MavenAcquisitionV3 {
                requested_coordinate: coordinate.canonical.clone(),
                repository_id: resolved.repository_id.clone(),
            }),
            scopes,
            bundle: bundle_policy(args, coordinate),
            artifact_treatment: args
                .artifact_treatment
                .unwrap_or(ArtifactTreatmentV3::LoaderManagedMod),
            data_run_policy: DataRunPolicyV3::Exclude,
        },
        derived_checks: ComponentDerivedChecksV3 {
            artifact_id: evidence.artifact_id.clone(),
            resolved_coordinate: Some(coordinate.canonical.clone()),
            expected_hash: evidence.hash,
            cache_path: evidence.cache_path.clone(),
        },
        source_providers: Vec::new(),
    };
    inventory
        .lockfile
        .dependencies
        .iter_mut()
        .find(|dependency| dependency.id == args.id)
        .expect("component mutation validates dependency")
        .components
        .push(component);
    if !append_artifact {
        let artifact = inventory
            .lockfile
            .artifacts
            .iter_mut()
            .find(|artifact| artifact.id == evidence.artifact_id)
            .expect("existing artifact evidence references a locked artifact");
        artifact.owner = Some(ArtifactOwnerV3 {
            dependency_id: args.id.clone(),
            component_id: component_id.to_owned(),
        });
        artifact.purposes = purposes;
        return;
    }
    inventory.lockfile.artifacts.push(ArtifactV3 {
        id: evidence.artifact_id,
        owner: Some(ArtifactOwnerV3 {
            dependency_id: args.id.clone(),
            component_id: component_id.to_owned(),
        }),
        purposes,
        coordinate: Some(coordinate.canonical.clone()),
        repository_id: Some(resolved.repository_id.clone()),
        url: Some(resolved.url.clone()),
        hash: evidence.hash,
        cache_path: evidence.cache_path,
        provenance: ArtifactProvenanceV3::RemoteMaven,
        source_git: None,
        source_build: None,
        weak: None,
    });
}

fn validate_bundle_options(
    args: &DependencyAddArgs,
    coordinate: &MavenCoordinate,
) -> eyre::Result<()> {
    let has_bundle_scope = args.scope.contains(&DependencyScopeV3::Bundle);
    let has_bundle_options = args.bundle_accepted_version_range.is_some()
        || args.bundle_artifact_version.is_some()
        || args.bundle_is_obfuscated;
    if !has_bundle_scope && has_bundle_options {
        eyre::bail!("bundle policy options require --scope bundle");
    }
    if !has_bundle_scope {
        return Ok(());
    }
    if args.bundle_accepted_version_range.is_none() {
        eyre::bail!("--scope bundle requires --bundle-accepted-version-range");
    }
    if args.bundle_is_obfuscated {
        eyre::bail!("plain Java bundle dependencies require is_obfuscated=false");
    }
    if args
        .bundle_artifact_version
        .as_deref()
        .is_some_and(|version| version != coordinate.version)
    {
        eyre::bail!(
            "--bundle-artifact-version must match the exact Maven coordinate version `{}`",
            coordinate.version
        );
    }
    Ok(())
}

fn bundle_policy(args: &DependencyAddArgs, coordinate: &MavenCoordinate) -> Option<BundlePolicyV3> {
    args.scope
        .contains(&DependencyScopeV3::Bundle)
        .then(|| BundlePolicyV3 {
            accepted_version_range: args
                .bundle_accepted_version_range
                .clone()
                .expect("bundle options validated before lock mutation"),
            artifact_version: args
                .bundle_artifact_version
                .clone()
                .unwrap_or_else(|| coordinate.version.clone()),
            is_obfuscated: args.bundle_is_obfuscated,
        })
}

struct CurseforgeLockEntryInputs<'a> {
    project_id: CurseforgeProjectId,
    file_id: CurseforgeProjectFileId,
    slug: &'a str,
    project_name: &'a str,
    coordinate: &'a MavenCoordinate,
    resolved: &'a ResolvedMavenArtifact,
    evidence: LockedComponentEvidence,
}

fn append_curseforge_lock_entries(
    inventory: &mut DependencyInventory,
    args: &DependencyAddArgs,
    inputs: CurseforgeLockEntryInputs<'_>,
) {
    let scopes: Vec<_> = args
        .scope
        .iter()
        .copied()
        .collect::<BTreeSet<_>>()
        .into_iter()
        .collect();
    let purposes = purposes_for_scopes(&scopes);
    let component = DependencyComponentV3 {
        id: "main".to_owned(),
        declaration: ComponentDeclarationV3 {
            acquisition: ComponentAcquisitionV3::CurseForge(CurseForgeAcquisitionV3 {
                project_id: *inputs.project_id,
                file_id: *inputs.file_id,
                slug: inputs.slug.to_owned(),
                repository_id: inputs.resolved.repository_id.clone(),
            }),
            scopes,
            bundle: None,
            artifact_treatment: args
                .artifact_treatment
                .unwrap_or(ArtifactTreatmentV3::LoaderManagedMod),
            data_run_policy: DataRunPolicyV3::Exclude,
        },
        derived_checks: ComponentDerivedChecksV3 {
            artifact_id: inputs.evidence.artifact_id.clone(),
            resolved_coordinate: Some(inputs.coordinate.canonical.clone()),
            expected_hash: inputs.evidence.hash,
            cache_path: inputs.evidence.cache_path.clone(),
        },
        source_providers: Vec::new(),
    };
    inventory.lockfile.dependencies.push(DependencyV3 {
        id: args.id.clone(),
        kind: args.kind.unwrap_or(DependencyKindV3::Mod),
        role: args.role.unwrap_or(DependencyRoleV3::Integration),
        display_name: args
            .display_name
            .clone()
            .or_else(|| Some(inputs.project_name.to_owned())),
        project_url: args.project_url.clone().or_else(|| {
            Some(format!(
                "https://www.curseforge.com/minecraft/mc-mods/{}",
                inputs.slug
            ))
        }),
        notes: args.notes.clone(),
        components: vec![component],
    });
    inventory.lockfile.artifacts.push(ArtifactV3 {
        id: inputs.evidence.artifact_id,
        owner: Some(ArtifactOwnerV3 {
            dependency_id: args.id.clone(),
            component_id: "main".to_owned(),
        }),
        purposes,
        coordinate: Some(inputs.coordinate.canonical.clone()),
        repository_id: Some(inputs.resolved.repository_id.clone()),
        url: Some(inputs.resolved.url.clone()),
        hash: inputs.evidence.hash,
        cache_path: inputs.evidence.cache_path,
        provenance: ArtifactProvenanceV3::RemoteMaven,
        source_git: None,
        source_build: None,
        weak: None,
    });
}

fn curseforge_target_context(
    inventory: &DependencyInventory,
) -> eyre::Result<(String, CurseforgeModLoader)> {
    let minecraft = inventory
        .dependency(&inventory.lockfile.platform.minecraft_dependency)?
        .components
        .iter()
        .find(|component| {
            matches!(
                &component.declaration.acquisition,
                ComponentAcquisitionV3::Toolchain(_)
            )
        })
        .ok_or_else(|| eyre::eyre!("Platform Minecraft dependency has no toolchain component."))?;
    let ComponentAcquisitionV3::Toolchain(acquisition) = &minecraft.declaration.acquisition else {
        unreachable!("selected component is a toolchain component")
    };
    let loader = match inventory.lockfile.platform.loader_dependency.as_str() {
        "forge" => CurseforgeModLoader::Forge,
        "neoforge" => CurseforgeModLoader::Neoforge,
        loader => eyre::bail!("Unsupported CurseForge loader '{loader}'."),
    };
    Ok((acquisition.requested_version.clone(), loader))
}

fn validate_curseforge_file(
    file: &crate::curseforge::CurseforgeProjectFileItem,
    project_id: CurseforgeProjectId,
    file_id: CurseforgeProjectFileId,
    minecraft_version: &str,
    loader: CurseforgeModLoader,
) -> eyre::Result<()> {
    if file.id != file_id {
        eyre::bail!(
            "CurseForge metadata returned file {} instead of requested file {file_id}.",
            file.id
        );
    }
    if file.mod_id != Some(project_id) {
        eyre::bail!("CurseForge file {file_id} does not belong to project {project_id}.");
    }
    if !file
        .game_versions
        .iter()
        .any(|version| version == minecraft_version)
    {
        eyre::bail!("CurseForge file {file_id} does not support Minecraft {minecraft_version}.");
    }
    if !file_matches_version_and_loader(file, minecraft_version, loader) {
        eyre::bail!(
            "CurseForge file {file_id} does not support loader {}.",
            loader.label()
        );
    }
    Ok(())
}

fn validate_curseforge_slug(slug: &str) -> eyre::Result<()> {
    if slug.is_empty()
        || !slug
            .bytes()
            .all(|byte| byte.is_ascii_lowercase() || byte.is_ascii_digit() || byte == b'-')
    {
        eyre::bail!(
            "CurseForge project slug '{slug}' is not portable for a CurseMaven coordinate."
        );
    }
    Ok(())
}

fn resolve_artifact(
    inventory: &DependencyInventory,
    coordinate: &MavenCoordinate,
    requested_repository: Option<&str>,
    cancellation_token: &CancellationToken,
    fetcher: &dyn ArtifactFetcher,
) -> eyre::Result<ResolvedMavenArtifact> {
    let candidates = repository_candidates(inventory, requested_repository)?;
    let mut attempted = Vec::new();
    for (repository_id, repository_url) in candidates {
        cancellation_token.bail_if_cancelled()?;
        let url = coordinate.url(repository_url);
        attempted.push(url.clone());
        if let Some(bytes) = fetcher.fetch(&url, cancellation_token)? {
            return Ok(ResolvedMavenArtifact {
                repository_id: repository_id.to_owned(),
                url,
                bytes,
            });
        }
    }
    eyre::bail!(
        "Could not resolve '{}'. Tried:\n{}",
        coordinate.canonical,
        attempted.join("\n")
    )
}

fn repository_candidates<'a>(
    inventory: &'a DependencyInventory,
    requested: Option<&str>,
) -> eyre::Result<Vec<(&'a str, &'a str)>> {
    if let Some(requested) = requested {
        let repository = inventory
            .lockfile
            .repositories
            .iter()
            .find(|repository| repository.id == requested)
            .ok_or_else(|| eyre::eyre!("Unknown repository '{requested}'."))?;
        return Ok(vec![(&repository.id, &repository.url)]);
    }
    Ok(inventory
        .lockfile
        .repositories
        .iter()
        .map(|repository| (repository.id.as_str(), repository.url.as_str()))
        .collect())
}

fn validate_dependency_id(id: &str) -> eyre::Result<()> {
    if id.is_empty()
        || !id
            .bytes()
            .all(|byte| byte.is_ascii_lowercase() || byte.is_ascii_digit() || byte == b'-')
    {
        eyre::bail!(
            "Dependency ID '{id}' must contain only lowercase ASCII letters, digits, and hyphens."
        );
    }
    Ok(())
}

fn validate_component_id(id: &str) -> eyre::Result<()> {
    if id.is_empty()
        || !id
            .bytes()
            .all(|byte| byte.is_ascii_lowercase() || byte.is_ascii_digit() || byte == b'-')
    {
        eyre::bail!(
            "Component ID '{id}' must contain only lowercase ASCII letters, digits, and hyphens."
        );
    }
    Ok(())
}

fn portable_id(value: &str) -> String {
    let mut output = String::new();
    for character in value.chars() {
        if character.is_ascii_alphanumeric() {
            output.push(character.to_ascii_lowercase());
        } else if !output.ends_with('-') {
            output.push('-');
        }
    }
    output.trim_matches('-').to_owned()
}

fn purposes_for_scopes(scopes: &[DependencyScopeV3]) -> Vec<ArtifactPurposeV3> {
    let mut purposes = BTreeSet::new();
    for scope in scopes {
        purposes.insert(match scope {
            DependencyScopeV3::AnnotationProcessor
            | DependencyScopeV3::Codegen
            | DependencyScopeV3::Compile
            | DependencyScopeV3::Bundle => ArtifactPurposeV3::Build,
            DependencyScopeV3::Runtime => ArtifactPurposeV3::Runtime,
            DependencyScopeV3::GametestCompile | DependencyScopeV3::GametestRuntime => {
                ArtifactPurposeV3::Gametest
            }
            DependencyScopeV3::TestCompile
            | DependencyScopeV3::TestAnnotationProcessor
            | DependencyScopeV3::TestRuntime => ArtifactPurposeV3::Test,
        });
    }
    purposes.into_iter().collect()
}

struct MavenCoordinate {
    canonical: String,
    group: String,
    artifact: String,
    version: String,
    classifier: Option<String>,
    extension: String,
}

impl MavenCoordinate {
    fn parse(input: &str) -> eyre::Result<Self> {
        let (notation, extension) = input
            .split_once('@')
            .map_or((input, "jar"), |(notation, extension)| {
                (notation, extension)
            });
        let parts: Vec<_> = notation.split(':').collect();
        let (group, artifact, version, classifier) = match parts.as_slice() {
            [group, artifact, version] => (*group, *artifact, *version, None),
            [group, artifact, version, classifier] => {
                (*group, *artifact, *version, Some((*classifier).to_owned()))
            }
            _ => eyre::bail!("Invalid Maven coordinate '{input}'."),
        };
        if [group, artifact, version, extension]
            .iter()
            .any(|part| part.is_empty())
            || classifier.as_deref().is_some_and(str::is_empty)
        {
            eyre::bail!("Invalid Maven coordinate '{input}'.");
        }
        let mut canonical = format!("{group}:{artifact}:{version}");
        if let Some(classifier) = &classifier {
            canonical.push(':');
            canonical.push_str(classifier);
        }
        if extension != "jar" {
            canonical.push('@');
            canonical.push_str(extension);
        }
        Ok(Self {
            canonical,
            group: group.to_owned(),
            artifact: artifact.to_owned(),
            version: version.to_owned(),
            classifier,
            extension: extension.to_owned(),
        })
    }

    fn require_exact(&self) -> eyre::Result<()> {
        let normalized = self.version.to_ascii_lowercase();
        if self.version.contains(['+', '*', '[', ']', '(', ')', ','])
            || matches!(normalized.as_str(), "latest" | "release")
        {
            eyre::bail!(
                "Dynamic Maven version '{}' is not supported; provide an exact version.",
                self.version
            );
        }
        Ok(())
    }

    fn file_name(&self) -> String {
        let classifier = self
            .classifier
            .as_deref()
            .map_or_else(String::new, |classifier| format!("-{classifier}"));
        format!(
            "{}-{}{}.{}",
            self.artifact, self.version, classifier, self.extension
        )
    }

    fn url(&self, repository_url: &str) -> String {
        format!(
            "{}/{}/{}/{}/{}",
            repository_url.trim_end_matches('/'),
            self.group.replace('.', "/"),
            self.artifact,
            self.version,
            self.file_name()
        )
    }

    fn portable_cache_path(&self) -> PathBuf {
        PathBuf::from("$sfm-cache")
            .join("maven")
            .join(self.group.replace('.', "/"))
            .join(&self.artifact)
            .join(&self.version)
            .join(self.file_name())
    }
}

#[cfg(test)]
#[path = "dependency_add_tests.rs"]
mod tests;
