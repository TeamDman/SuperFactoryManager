use crate::jar_build::SourceBuildProvenance;
use crate::jar_build::SourceGitProvenance;
use crate::jar_build::hash::ContentHash;
use crate::jar_build::json_path::JsonPath;
use facet::Facet;
use std::collections::BTreeSet;
use std::path::Path;
use std::path::PathBuf;

pub(crate) const SCHEMA_VERSION: u32 = 3;

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct ArtifactLockfileV3 {
    pub(crate) schema_version: u32,
    pub(crate) platform: PlatformV3,
    pub(crate) policy: LockfilePolicyV3,
    pub(crate) repositories: Vec<RepositoryV3>,
    pub(crate) dependencies: Vec<DependencyV3>,
    pub(crate) artifacts: Vec<ArtifactV3>,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct PlatformV3 {
    pub(crate) minecraft_dependency: String,
    pub(crate) loader_dependency: String,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct LockfilePolicyV3 {
    pub(crate) allow_local_artifact_cache: bool,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct RepositoryV3 {
    pub(crate) id: String,
    pub(crate) url: String,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct DependencyV3 {
    pub(crate) id: String,
    pub(crate) kind: DependencyKindV3,
    pub(crate) role: DependencyRoleV3,
    #[facet(default)]
    pub(crate) display_name: Option<String>,
    #[facet(default)]
    pub(crate) project_url: Option<String>,
    #[facet(default)]
    pub(crate) notes: Option<String>,
    pub(crate) components: Vec<DependencyComponentV3>,
}

#[derive(Clone, Copy, Debug, Eq, Facet, Hash, Ord, PartialEq, PartialOrd)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub(crate) enum DependencyKindV3 {
    Minecraft,
    Loader,
    Mod,
    Library,
    Tool,
}

#[derive(Clone, Copy, Debug, Eq, Facet, Hash, Ord, PartialEq, PartialOrd)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub(crate) enum DependencyRoleV3 {
    Platform,
    Integration,
    Build,
    Test,
    Library,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct DependencyComponentV3 {
    pub(crate) id: String,
    pub(crate) declaration: ComponentDeclarationV3,
    pub(crate) derived_checks: ComponentDerivedChecksV3,
    pub(crate) source_providers: Vec<SourceProviderV3>,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct ComponentDeclarationV3 {
    pub(crate) acquisition: ComponentAcquisitionV3,
    pub(crate) scopes: Vec<DependencyScopeV3>,
    pub(crate) artifact_treatment: ArtifactTreatmentV3,
    pub(crate) data_run_policy: DataRunPolicyV3,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub(crate) enum ComponentAcquisitionV3 {
    Maven(MavenAcquisitionV3),
    CurseForge(CurseForgeAcquisitionV3),
    Http(HttpAcquisitionV3),
    Toolchain(ToolchainAcquisitionV3),
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct MavenAcquisitionV3 {
    pub(crate) requested_coordinate: String,
    pub(crate) repository_id: String,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct CurseForgeAcquisitionV3 {
    pub(crate) project_id: u64,
    pub(crate) file_id: u64,
    pub(crate) slug: String,
    pub(crate) repository_id: String,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct HttpAcquisitionV3 {
    pub(crate) url: String,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct ToolchainAcquisitionV3 {
    pub(crate) kind: ToolchainComponentKindV3,
    pub(crate) requested_version: String,
}

#[derive(Clone, Copy, Debug, Eq, Facet, Hash, Ord, PartialEq, PartialOrd)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub(crate) enum ToolchainComponentKindV3 {
    Minecraft,
    Loader,
}

#[derive(Clone, Copy, Debug, Eq, Facet, Hash, Ord, PartialEq, PartialOrd)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub(crate) enum DependencyScopeV3 {
    AnnotationProcessor,
    Codegen,
    Compile,
    Runtime,
    GametestCompile,
    GametestRuntime,
    TestCompile,
    TestAnnotationProcessor,
    TestRuntime,
    Bundle,
}

#[derive(Clone, Copy, Debug, Eq, Facet, Hash, Ord, PartialEq, PartialOrd)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub(crate) enum ArtifactTreatmentV3 {
    LoaderManagedMod,
    Plain,
}

#[derive(Clone, Copy, Debug, Eq, Facet, Hash, Ord, PartialEq, PartialOrd)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub(crate) enum DataRunPolicyV3 {
    Exclude,
    Include,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct ComponentDerivedChecksV3 {
    pub(crate) artifact_id: String,
    #[facet(default)]
    pub(crate) resolved_coordinate: Option<String>,
    pub(crate) expected_hash: ContentHash,
    #[facet(proxy = JsonPath)]
    pub(crate) cache_path: PathBuf,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub(crate) enum SourceProviderV3 {
    MavenSources(MavenSourceProviderV3),
    Git(GitSourceProviderV3),
    Decompile(DecompileSourceProviderV3),
    PlatformPipeline(PlatformPipelineSourceProviderV3),
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct MavenSourceProviderV3 {
    pub(crate) id: String,
    pub(crate) declaration: MavenSourceDeclarationV3,
    pub(crate) derived_checks: MavenSourceDerivedChecksV3,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct MavenSourceDeclarationV3 {
    pub(crate) requested_coordinate: String,
    pub(crate) repository_id: String,
    pub(crate) roots: Vec<String>,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct MavenSourceDerivedChecksV3 {
    pub(crate) resolved_coordinate: String,
    pub(crate) url: String,
    pub(crate) hash: ContentHash,
    #[facet(proxy = JsonPath)]
    pub(crate) archive_cache_path: PathBuf,
    #[facet(proxy = JsonPath)]
    pub(crate) tree_cache_path: PathBuf,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct GitSourceProviderV3 {
    pub(crate) id: String,
    pub(crate) declaration: GitSourceDeclarationV3,
    pub(crate) derived_checks: GitSourceDerivedChecksV3,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct GitSourceDeclarationV3 {
    pub(crate) remote_url: String,
    pub(crate) requested_revision: String,
    pub(crate) roots: Vec<String>,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct GitSourceDerivedChecksV3 {
    pub(crate) commit: String,
    #[facet(proxy = JsonPath)]
    pub(crate) repository_cache_path: PathBuf,
    #[facet(proxy = JsonPath)]
    pub(crate) tree_cache_path: PathBuf,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct DecompileSourceProviderV3 {
    pub(crate) id: String,
    pub(crate) declaration: DecompileSourceDeclarationV3,
    pub(crate) derived_checks: DecompileSourceDerivedChecksV3,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct DecompileSourceDeclarationV3 {
    pub(crate) roots: Vec<String>,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct DecompileSourceDerivedChecksV3 {
    pub(crate) binary_artifact_id: String,
    pub(crate) decompiler_artifact_id: String,
    pub(crate) fingerprint: String,
    #[facet(proxy = JsonPath)]
    pub(crate) tree_cache_path: PathBuf,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct PlatformPipelineSourceProviderV3 {
    pub(crate) id: String,
    pub(crate) declaration: PlatformPipelineSourceDeclarationV3,
    pub(crate) derived_checks: PlatformPipelineSourceDerivedChecksV3,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct PlatformPipelineSourceDeclarationV3 {
    pub(crate) kind: ToolchainComponentKindV3,
    pub(crate) roots: Vec<String>,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct PlatformPipelineSourceDerivedChecksV3 {
    pub(crate) fingerprint: String,
    #[facet(proxy = JsonPath)]
    pub(crate) tree_cache_path: PathBuf,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct ArtifactV3 {
    pub(crate) id: String,
    #[facet(default)]
    pub(crate) owner: Option<ArtifactOwnerV3>,
    pub(crate) purposes: Vec<ArtifactPurposeV3>,
    #[facet(default)]
    pub(crate) coordinate: Option<String>,
    #[facet(default)]
    pub(crate) repository_id: Option<String>,
    #[facet(default)]
    pub(crate) url: Option<String>,
    pub(crate) hash: ContentHash,
    #[facet(proxy = JsonPath)]
    pub(crate) cache_path: PathBuf,
    pub(crate) provenance: ArtifactProvenanceV3,
    #[facet(default)]
    pub(crate) source_git: Option<SourceGitProvenance>,
    #[facet(default)]
    pub(crate) source_build: Option<SourceBuildProvenance>,
    #[facet(default)]
    pub(crate) weak: Option<WeakArtifactValidationV3>,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct ArtifactOwnerV3 {
    pub(crate) dependency_id: String,
    pub(crate) component_id: String,
}

#[derive(Clone, Copy, Debug, Eq, Facet, Hash, Ord, PartialEq, PartialOrd)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub(crate) enum ArtifactPurposeV3 {
    Build,
    Runtime,
    Gametest,
    Test,
    Toolchain,
    SourceTool,
}

#[derive(Clone, Copy, Debug, Eq, Facet, Hash, Ord, PartialEq, PartialOrd)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub(crate) enum ArtifactProvenanceV3 {
    RemoteMaven,
    RemoteHttp,
    ToolchainGenerated,
    SourceBuild,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct WeakArtifactValidationV3 {
    #[facet(proxy = JsonPath)]
    pub(crate) metadata_path: PathBuf,
    pub(crate) mod_id: String,
    pub(crate) version: String,
}

impl ArtifactLockfileV3 {
    pub(crate) fn validate(&self) -> eyre::Result<()> {
        if self.schema_version != SCHEMA_VERSION {
            eyre::bail!(
                "v3 lockfile declares schema_version {}, expected {SCHEMA_VERSION}",
                self.schema_version
            );
        }

        let repository_ids = collect_unique_ids(
            self.repositories
                .iter()
                .map(|repository| repository.id.as_str()),
            "repository",
        )?;
        for repository in &self.repositories {
            require_nonempty(&repository.url, "repository URL")?;
        }

        let artifact_ids = collect_unique_ids(
            self.artifacts.iter().map(|artifact| artifact.id.as_str()),
            "artifact",
        )?;
        for artifact in &self.artifacts {
            if artifact.purposes.is_empty() {
                eyre::bail!(
                    "artifact `{}` must declare at least one purpose",
                    artifact.id
                );
            }
            validate_portable_path(&artifact.cache_path, "artifact cache path")?;
            if let Some(repository_id) = &artifact.repository_id {
                require_reference(&repository_ids, repository_id, "repository")?;
            }
            if let Some(weak) = &artifact.weak {
                validate_portable_path(&weak.metadata_path, "weak metadata path")?;
                require_nonempty(&weak.mod_id, "weak validation mod id")?;
                require_nonempty(&weak.version, "weak validation version")?;
            }
            match artifact.provenance {
                ArtifactProvenanceV3::SourceBuild => {
                    let source_git = artifact.source_git.as_ref().ok_or_else(|| {
                        eyre::eyre!(
                            "source-build artifact `{}` is missing its locked Git provenance",
                            artifact.id
                        )
                    })?;
                    let source_build = artifact.source_build.as_ref().ok_or_else(|| {
                        eyre::eyre!(
                            "source-build artifact `{}` is missing its locked build recipe",
                            artifact.id
                        )
                    })?;
                    validate_portable_path(&source_git.root, "source-build Git root")?;
                    require_nonempty(&source_git.commit, "source-build Git commit")?;
                    require_nonempty(&source_git.branch, "source-build Git branch")?;
                    require_nonempty(
                        source_git.remote_url.as_deref().unwrap_or_default(),
                        "source-build Git remote URL",
                    )?;
                    if source_git.dirty {
                        eyre::bail!(
                            "source-build artifact `{}` must not depend on a dirty Git checkout",
                            artifact.id
                        );
                    }
                    if source_build.tasks.is_empty()
                        || source_build.tasks.iter().any(|task| task.trim().is_empty())
                    {
                        eyre::bail!(
                            "source-build artifact `{}` must declare non-empty build tasks",
                            artifact.id
                        );
                    }
                    validate_portable_path(&source_build.output_path, "source-build output path")?;
                }
                _ if artifact.source_git.is_some() || artifact.source_build.is_some() => {
                    eyre::bail!(
                        "non-source-build artifact `{}` must not carry source-build provenance",
                        artifact.id
                    );
                }
                _ => {}
            }
        }

        let dependency_ids = collect_unique_ids(
            self.dependencies
                .iter()
                .map(|dependency| dependency.id.as_str()),
            "dependency",
        )?;
        require_reference(
            &dependency_ids,
            &self.platform.minecraft_dependency,
            "Minecraft dependency",
        )?;
        require_reference(
            &dependency_ids,
            &self.platform.loader_dependency,
            "loader dependency",
        )?;

        for dependency in &self.dependencies {
            if dependency.components.is_empty() {
                eyre::bail!(
                    "dependency `{}` must declare at least one component",
                    dependency.id
                );
            }
            let component_ids = collect_unique_ids(
                dependency
                    .components
                    .iter()
                    .map(|component| component.id.as_str()),
                "component",
            )?;
            for component in &dependency.components {
                validate_component(component, &repository_ids, &artifact_ids, &self.artifacts)?;
            }
            for artifact in self.artifacts.iter().filter(|artifact| {
                artifact
                    .owner
                    .as_ref()
                    .is_some_and(|owner| owner.dependency_id == dependency.id)
            }) {
                let owner = artifact.owner.as_ref().expect("owner checked above");
                require_reference(&component_ids, &owner.component_id, "component")?;
            }
        }

        validate_platform_dependency(
            self,
            &self.platform.minecraft_dependency,
            DependencyKindV3::Minecraft,
        )?;
        validate_platform_dependency(
            self,
            &self.platform.loader_dependency,
            DependencyKindV3::Loader,
        )?;
        Ok(())
    }
}

fn validate_component(
    component: &DependencyComponentV3,
    repository_ids: &BTreeSet<&str>,
    artifact_ids: &BTreeSet<&str>,
    artifacts: &[ArtifactV3],
) -> eyre::Result<()> {
    if component.declaration.scopes.is_empty() {
        eyre::bail!(
            "component `{}` must declare at least one scope",
            component.id
        );
    }
    let unique_scopes = component
        .declaration
        .scopes
        .iter()
        .copied()
        .collect::<BTreeSet<_>>();
    if unique_scopes.len() != component.declaration.scopes.len() {
        eyre::bail!("component `{}` contains duplicate scopes", component.id);
    }

    match &component.declaration.acquisition {
        ComponentAcquisitionV3::Maven(acquisition) => {
            require_nonempty(&acquisition.requested_coordinate, "Maven coordinate")?;
            require_reference(repository_ids, &acquisition.repository_id, "repository")?;
        }
        ComponentAcquisitionV3::CurseForge(acquisition) => {
            require_nonempty(&acquisition.slug, "CurseForge slug")?;
            require_reference(repository_ids, &acquisition.repository_id, "repository")?;
        }
        ComponentAcquisitionV3::Http(acquisition) => {
            require_nonempty(&acquisition.url, "HTTP URL")?;
        }
        ComponentAcquisitionV3::Toolchain(acquisition) => {
            require_nonempty(&acquisition.requested_version, "toolchain version")?;
        }
    }

    require_reference(
        artifact_ids,
        &component.derived_checks.artifact_id,
        "artifact",
    )?;
    validate_portable_path(&component.derived_checks.cache_path, "component cache path")?;
    let artifact = artifacts
        .iter()
        .find(|artifact| artifact.id == component.derived_checks.artifact_id)
        .expect("artifact reference validated above");
    if artifact.hash != component.derived_checks.expected_hash
        || artifact.cache_path != component.derived_checks.cache_path
    {
        eyre::bail!(
            "component `{}` derived checks disagree with artifact `{}`",
            component.id,
            artifact.id
        );
    }

    let provider_ids = collect_unique_ids(
        component.source_providers.iter().map(SourceProviderV3::id),
        "source provider",
    )?;
    if provider_ids.len() != component.source_providers.len() {
        eyre::bail!(
            "component `{}` contains duplicate source providers",
            component.id
        );
    }
    for provider in &component.source_providers {
        provider.validate(repository_ids, artifact_ids)?;
    }
    Ok(())
}

impl SourceProviderV3 {
    fn id(&self) -> &str {
        match self {
            Self::MavenSources(provider) => &provider.id,
            Self::Git(provider) => &provider.id,
            Self::Decompile(provider) => &provider.id,
            Self::PlatformPipeline(provider) => &provider.id,
        }
    }

    fn validate(
        &self,
        repository_ids: &BTreeSet<&str>,
        artifact_ids: &BTreeSet<&str>,
    ) -> eyre::Result<()> {
        require_nonempty(self.id(), "source provider id")?;
        match self {
            Self::MavenSources(provider) => {
                require_reference(
                    repository_ids,
                    &provider.declaration.repository_id,
                    "repository",
                )?;
                require_nonempty(
                    &provider.declaration.requested_coordinate,
                    "Maven source coordinate",
                )?;
                require_nonempty(
                    &provider.derived_checks.resolved_coordinate,
                    "resolved Maven source coordinate",
                )?;
                validate_portable_path(
                    &provider.derived_checks.archive_cache_path,
                    "Maven source archive path",
                )?;
                validate_portable_path(
                    &provider.derived_checks.tree_cache_path,
                    "Maven source tree path",
                )?;
            }
            Self::Git(provider) => {
                require_nonempty(&provider.declaration.remote_url, "Git remote URL")?;
                require_nonempty(
                    &provider.declaration.requested_revision,
                    "Git requested revision",
                )?;
                require_nonempty(&provider.derived_checks.commit, "Git commit")?;
                validate_portable_path(
                    &provider.derived_checks.repository_cache_path,
                    "Git repository path",
                )?;
                validate_portable_path(&provider.derived_checks.tree_cache_path, "Git tree path")?;
            }
            Self::Decompile(provider) => {
                require_reference(
                    artifact_ids,
                    &provider.derived_checks.binary_artifact_id,
                    "binary artifact",
                )?;
                require_reference(
                    artifact_ids,
                    &provider.derived_checks.decompiler_artifact_id,
                    "decompiler artifact",
                )?;
                require_nonempty(
                    &provider.derived_checks.fingerprint,
                    "decompiler fingerprint",
                )?;
                validate_portable_path(
                    &provider.derived_checks.tree_cache_path,
                    "decompiled source tree path",
                )?;
            }
            Self::PlatformPipeline(provider) => {
                require_nonempty(
                    &provider.derived_checks.fingerprint,
                    "platform source fingerprint",
                )?;
                validate_portable_path(
                    &provider.derived_checks.tree_cache_path,
                    "platform source tree path",
                )?;
            }
        }
        Ok(())
    }
}

fn validate_platform_dependency(
    lockfile: &ArtifactLockfileV3,
    dependency_id: &str,
    expected_kind: DependencyKindV3,
) -> eyre::Result<()> {
    let dependency = lockfile
        .dependencies
        .iter()
        .find(|dependency| dependency.id == dependency_id)
        .expect("platform reference validated above");
    if dependency.kind != expected_kind || dependency.role != DependencyRoleV3::Platform {
        eyre::bail!(
            "platform dependency `{dependency_id}` must have kind {expected_kind:?} and role platform"
        );
    }
    Ok(())
}

fn collect_unique_ids<'a>(
    values: impl IntoIterator<Item = &'a str>,
    kind: &str,
) -> eyre::Result<BTreeSet<&'a str>> {
    let mut ids = BTreeSet::new();
    for value in values {
        require_nonempty(value, &format!("{kind} id"))?;
        if !ids.insert(value) {
            eyre::bail!("duplicate {kind} id `{value}`");
        }
    }
    Ok(ids)
}

fn require_reference(values: &BTreeSet<&str>, value: &str, kind: &str) -> eyre::Result<()> {
    if !values.contains(value) {
        eyre::bail!("unknown {kind} reference `{value}`");
    }
    Ok(())
}

fn require_nonempty(value: &str, label: &str) -> eyre::Result<()> {
    if value.trim().is_empty() {
        eyre::bail!("{label} must not be empty");
    }
    Ok(())
}

fn validate_portable_path(path: &Path, label: &str) -> eyre::Result<()> {
    if path.is_absolute() {
        eyre::bail!("{label} must be portable, got `{}`", path.display());
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::jar_build::hash::ContentHashAlgorithm;
    use crate::toolchain_lockfile_schema::api::ToolchainLockfileDocument;
    use crate::toolchain_lockfile_schema::api::parse_document;

    fn hash(seed: u8) -> ContentHash {
        ContentHash {
            value: [seed; 20],
            algorithm: ContentHashAlgorithm::Sha1,
        }
    }

    fn artifact(id: &str, seed: u8) -> ArtifactV3 {
        ArtifactV3 {
            id: id.to_string(),
            owner: None,
            purposes: vec![ArtifactPurposeV3::Toolchain],
            coordinate: Some(format!("test:{id}:1")),
            repository_id: Some("central".to_string()),
            url: Some(format!("https://repo.example/{id}.jar")),
            hash: hash(seed),
            cache_path: PathBuf::from(format!("$sfm-cache/maven/{id}.jar")),
            provenance: ArtifactProvenanceV3::RemoteMaven,
            source_git: None,
            source_build: None,
            weak: None,
        }
    }

    fn platform_dependency(
        id: &str,
        kind: DependencyKindV3,
        toolchain_kind: ToolchainComponentKindV3,
        artifact_id: &str,
        seed: u8,
    ) -> DependencyV3 {
        DependencyV3 {
            id: id.to_string(),
            kind,
            role: DependencyRoleV3::Platform,
            display_name: None,
            project_url: None,
            notes: None,
            components: vec![DependencyComponentV3 {
                id: "main".to_string(),
                declaration: ComponentDeclarationV3 {
                    acquisition: ComponentAcquisitionV3::Toolchain(ToolchainAcquisitionV3 {
                        kind: toolchain_kind,
                        requested_version: "1.19.2".to_string(),
                    }),
                    scopes: vec![DependencyScopeV3::Compile, DependencyScopeV3::Runtime],
                    artifact_treatment: ArtifactTreatmentV3::Plain,
                    data_run_policy: DataRunPolicyV3::Exclude,
                },
                derived_checks: ComponentDerivedChecksV3 {
                    artifact_id: artifact_id.to_string(),
                    resolved_coordinate: Some(format!("test:{artifact_id}:1")),
                    expected_hash: hash(seed),
                    cache_path: PathBuf::from(format!("$sfm-cache/maven/{artifact_id}.jar")),
                },
                source_providers: Vec::new(),
            }],
        }
    }

    fn fixture() -> ArtifactLockfileV3 {
        ArtifactLockfileV3 {
            schema_version: SCHEMA_VERSION,
            platform: PlatformV3 {
                minecraft_dependency: "minecraft".to_string(),
                loader_dependency: "loader".to_string(),
            },
            policy: LockfilePolicyV3 {
                allow_local_artifact_cache: false,
            },
            repositories: vec![RepositoryV3 {
                id: "central".to_string(),
                url: "https://repo.example/".to_string(),
            }],
            dependencies: vec![
                platform_dependency(
                    "minecraft",
                    DependencyKindV3::Minecraft,
                    ToolchainComponentKindV3::Minecraft,
                    "minecraft-main",
                    1,
                ),
                platform_dependency(
                    "loader",
                    DependencyKindV3::Loader,
                    ToolchainComponentKindV3::Loader,
                    "loader-main",
                    2,
                ),
            ],
            artifacts: vec![artifact("minecraft-main", 1), artifact("loader-main", 2)],
        }
    }

    #[test]
    fn v3_roundtrips_and_dispatches_as_latest_document() {
        let lockfile = fixture();
        lockfile.validate().expect("fixture should validate");
        let json = facet_json::to_string_pretty(&lockfile).expect("v3 should serialize");
        let parsed = parse_document(&json).expect("v3 should parse");
        let ToolchainLockfileDocument::V3(parsed) = parsed else {
            panic!("expected v3 document");
        };
        assert_eq!(parsed, lockfile);
    }

    #[test]
    fn v3_rejects_duplicate_dependency_ids() {
        let mut lockfile = fixture();
        lockfile.dependencies[1].id = "minecraft".to_string();
        let error = lockfile
            .validate()
            .expect_err("duplicate dependencies should fail");
        assert!(error.to_string().contains("duplicate dependency id"));
    }

    #[test]
    fn v3_rejects_component_checks_that_disagree_with_artifact() {
        let mut lockfile = fixture();
        lockfile.dependencies[0].components[0]
            .derived_checks
            .expected_hash = hash(9);
        let error = lockfile
            .validate()
            .expect_err("mismatched component checks should fail");
        assert!(error.to_string().contains("derived checks disagree"));
    }
}
