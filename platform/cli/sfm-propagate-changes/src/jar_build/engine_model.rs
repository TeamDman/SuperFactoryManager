#[derive(Debug, Facet)]
struct BuildPlan {
    schema_version: u32,
    mode: String,
    #[facet(proxy = JsonBranchName)]
    branch_name: BranchName,
    #[facet(proxy = JsonMinecraftVersion)]
    minecraft_version: MinecraftVersion,
    #[facet(proxy = JsonPath)]
    worktree_path: PathBuf,
    #[facet(proxy = JsonPath)]
    minecraft_dir: PathBuf,
    #[facet(proxy = JsonPath)]
    gradle_output_jar: PathBuf,
    #[facet(proxy = JsonPath)]
    rust_output_jar: PathBuf,
    #[facet(proxy = JsonPath)]
    cache_dir: PathBuf,
    #[facet(proxy = JsonPath)]
    common_cache_dir: PathBuf,
    #[facet(proxy = JsonPath)]
    state_dir: PathBuf,
    #[facet(proxy = JsonPath)]
    maven_cache_dir: PathBuf,
    #[facet(proxy = JsonPath)]
    minecraft_cache_dir: PathBuf,
    #[facet(proxy = JsonPath)]
    minecraft_version_cache_dir: PathBuf,
    #[facet(proxy = JsonPath)]
    minecraft_assets_dir: PathBuf,
    #[facet(proxy = JsonPath)]
    minecraft_libraries_dir: PathBuf,
    #[facet(proxy = JsonPath)]
    lockfile_path: PathBuf,
    #[facet(skip_serializing)]
    lockfile: Option<ArtifactLockfile>,
    java: JavaPlan,
    java_release: u32,
    refresh: bool,
    allow_local_artifact_cache: bool,
    #[facet(skip_serializing)]
    artifact_sources: Vec<PathBuf>,
    properties: BTreeMap<String, String>,
    repositories: Vec<Repository>,
    loader_toolchain: LoaderToolchainPlan,
    artifacts: Vec<ArtifactPlan>,
    minecraft: MinecraftPlan,
    forge_userdev: Option<ForgeUserdevPlan>,
    mcp_config: Option<McpConfigPlan>,
    dependencies: Vec<DependencyPlan>,
    graph: Vec<GraphNode>,
    artifact_portability: ArtifactPortabilityAudit,
    warnings: Vec<String>,
}

#[derive(Clone, Debug, Facet)]
pub(crate) struct Repository {
    pub(crate) name: String,
    pub(crate) url: String,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
enum LoaderToolchainKind {
    ForgeGradleForge,
    ForgeGradleNeoForgeGroup,
    NeoGradleUserdev,
}

#[derive(Clone, Debug, Facet)]
struct LoaderToolchainPlan {
    kind: LoaderToolchainKind,
    base_coordinate: String,
    userdev_coordinate: String,
    sources_coordinate: Option<String>,
    universal_coordinate: Option<String>,
}

#[derive(Clone, Debug, Eq, PartialEq)]
struct MavenCoordinate {
    group: String,
    artifact: String,
    version: String,
    classifier: Option<String>,
    extension: String,
}

#[derive(Clone, Debug, Facet)]
struct ArtifactPlan {
    id: ArtifactId,
    coordinate: Option<String>,
    repository: Option<String>,
    url: Option<String>,
    #[facet(proxy = JsonPath)]
    cache_path: PathBuf,
    sha1: Option<ContentHash>,
    downloaded: bool,
    required_for: ArtifactPurpose,
    provenance: ArtifactProvenance,
}

#[derive(Clone, Debug, Facet)]
struct ArtifactProvenance {
    schema_version: u32,
    source: ArtifactSource,
    coordinate: Option<String>,
    repository: Option<String>,
    url: Option<String>,
    #[facet(proxy = JsonOptionalPath)]
    original_path: Option<PathBuf>,
    #[facet(default)]
    #[facet(proxy = JsonOptionalPath)]
    source_relative_path: Option<PathBuf>,
    #[facet(default)]
    source_git: Option<SourceGitProvenance>,
    #[facet(default)]
    source_build: Option<SourceBuildProvenance>,
    #[facet(alias = "sha1")]
    hash: ContentHash,
}

#[derive(Clone, Debug, Facet)]
pub(crate) struct ArtifactLockfile {
    pub(crate) schema_version: u32,
    pub(crate) minecraft_version: String,
    #[facet(proxy = JsonPath)]
    pub(crate) maven_cache_dir: PathBuf,
    pub(crate) allow_local_artifact_cache: bool,
    pub(crate) repositories: Vec<Repository>,
    pub(crate) dependencies: Vec<DependencyLockEntry>,
    pub(crate) artifacts: Vec<ArtifactLockEntry>,
}

#[derive(Clone, Debug, Facet)]
pub(crate) struct DependencyLockEntry {
    pub(crate) configuration: String,
    pub(crate) notation: String,
    pub(crate) resolved_notation: String,
    pub(crate) source: DependencySource,
    pub(crate) dynamic_version: bool,
    #[facet(proxy = JsonPath)]
    pub(crate) cache_path: PathBuf,
}

#[derive(Clone, Debug, Facet)]
pub(crate) struct ArtifactLockEntry {
    pub(crate) coordinate: Option<String>,
    pub(crate) source: ArtifactSource,
    pub(crate) repository: Option<String>,
    pub(crate) url: Option<String>,
    #[facet(proxy = JsonPath)]
    pub(crate) cache_path: PathBuf,
    #[facet(proxy = JsonOptionalPath)]
    pub(crate) original_path: Option<PathBuf>,
    #[facet(default)]
    #[facet(proxy = JsonOptionalPath)]
    pub(crate) source_relative_path: Option<PathBuf>,
    #[facet(default)]
    pub(crate) source_git: Option<SourceGitProvenance>,
    #[facet(default)]
    pub(crate) source_build: Option<SourceBuildProvenance>,
    #[facet(alias = "sha1")]
    pub(crate) hash: ContentHash,
    #[facet(default)]
    pub(crate) weak: Option<WeakArtifactValidation>,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct WeakArtifactValidation {
    #[facet(proxy = JsonPath)]
    pub(crate) metadata_path: PathBuf,
    pub(crate) mod_id: String,
    pub(crate) version: String,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct SourceGitProvenance {
    #[facet(proxy = JsonPath)]
    pub(crate) root: PathBuf,
    pub(crate) commit: String,
    pub(crate) branch: String,
    pub(crate) dirty: bool,
    #[facet(default)]
    pub(crate) remote_url: Option<String>,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct SourceBuildProvenance {
    pub(crate) build_system: SourceBuildSystem,
    pub(crate) tasks: Vec<String>,
    pub(crate) environment: BTreeMap<String, String>,
    #[facet(proxy = JsonPath)]
    pub(crate) output_path: PathBuf,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub(crate) enum SourceBuildSystem {
    GradleWrapper,
}

#[derive(Clone, Debug, Default, Facet)]
struct ArtifactPortabilityAudit {
    fresh_slate_portable: bool,
    total_artifacts: usize,
    portable_artifacts: usize,
    non_portable_artifacts: usize,
    explicit_source_artifacts: usize,
    local_cache_artifacts: usize,
    unknown_cache_artifacts: usize,
    issues: Vec<ArtifactPortabilityIssue>,
}

#[derive(Clone, Debug, Facet)]
struct ArtifactPortabilityIssue {
    coordinate: Option<String>,
    source: ArtifactSource,
    #[facet(proxy = JsonPath)]
    cache_path: PathBuf,
    #[facet(proxy = JsonOptionalPath)]
    original_path: Option<PathBuf>,
    #[facet(default)]
    #[facet(proxy = JsonOptionalPath)]
    source_relative_path: Option<PathBuf>,
    #[facet(default)]
    source_git: Option<SourceGitProvenance>,
    #[facet(default)]
    source_build: Option<SourceBuildProvenance>,
    reason: String,
    remediation: String,
}

#[derive(Clone, Debug)]
struct ArtifactPortabilityInput {
    coordinate: Option<String>,
    source: ArtifactSource,
    cache_path: PathBuf,
    original_path: Option<PathBuf>,
    source_relative_path: Option<PathBuf>,
    source_git: Option<SourceGitProvenance>,
    source_build: Option<SourceBuildProvenance>,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub(crate) enum ArtifactSource {
    RemoteMaven,
    RemoteHttp,
    #[facet(rename = "explicit-artifact-source")]
    ExplicitSource,
    #[facet(rename = "source-build")]
    SourceBuild,
    LocalM2Cache,
    LocalGradleModuleCache,
    ExistingSfmCacheUnknown,
}

impl ArtifactSource {
    pub(super) fn is_fresh_slate_portable(&self) -> bool {
        matches!(
            self,
            Self::RemoteMaven | Self::RemoteHttp | Self::SourceBuild
        )
    }

    fn is_local_cache(&self) -> bool {
        matches!(self, Self::LocalM2Cache | Self::LocalGradleModuleCache)
    }

    fn can_be_materialized_from_source(&self) -> bool {
        matches!(self, Self::ExplicitSource | Self::SourceBuild)
    }

    pub(super) fn label(&self) -> &'static str {
        match self {
            Self::RemoteMaven => "remote-maven",
            Self::RemoteHttp => "remote-http",
            Self::ExplicitSource => "explicit-artifact-source",
            Self::SourceBuild => "source-build",
            Self::LocalM2Cache => "local-m2-cache",
            Self::LocalGradleModuleCache => "local-gradle-module-cache",
            Self::ExistingSfmCacheUnknown => "existing-sfm-cache-unknown",
        }
    }
}

#[derive(Debug, Facet)]
struct MinecraftPlan {
    version_manifest: ArtifactPlan,
    version_json: ArtifactPlan,
    client_jar_url: String,
    server_jar_url: String,
    client_mappings_url: Option<String>,
    server_mappings_url: Option<String>,
    libraries_count: usize,
}

#[derive(Debug, Facet)]
struct ForgeUserdevPlan {
    artifact: ArtifactPlan,
    spec: Option<i64>,
    mcp: Option<String>,
    neo_form: Option<String>,
    sources: Option<String>,
    universal: Option<String>,
    binpatcher: Option<String>,
    patches: Option<String>,
    patches_original_prefix: Option<String>,
    patches_modified_prefix: Option<String>,
    access_transformers: Vec<String>,
    side_strippers: Vec<String>,
    modules: Vec<String>,
    libraries: Vec<String>,
    module_count: usize,
    library_count: usize,
    test_libraries: Vec<String>,
    run_configs: Vec<String>,
}

#[derive(Debug, Facet)]
struct McpConfigPlan {
    artifact: ArtifactPlan,
    joined_steps: Vec<String>,
    function_coordinates: BTreeMap<String, String>,
    function_count: usize,
    data_keys: Vec<String>,
    library_count: usize,
}

#[derive(Clone, Debug, Facet)]
struct DependencyPlan {
    configuration: String,
    notation: String,
    resolved_notation: String,
    source: DependencySource,
    #[facet(proxy = JsonPath)]
    cache_path: PathBuf,
    url: Option<String>,
    dynamic_version: bool,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[repr(u8)]
pub(crate) enum DependencySource {
    CurseMaven,
    Maven,
}

#[derive(Debug, Facet)]
struct GraphNode {
    id: String,
    kind: String,
    status: NodeStatus,
    inputs: Vec<String>,
    outputs: Vec<String>,
    rebuild_reason: String,
}

#[derive(Debug, Facet)]
struct JavaPlan {
    // todo(2026-06-17) this can probably be removed in favour of jdk.rs#ResolvedJava
    #[facet(proxy = JsonPath)]
    executable: PathBuf,
    #[facet(proxy = JsonOptionalPath)]
    home: Option<PathBuf>,
    version_output: String,
    major_version: u32,
}

#[derive(Debug, Facet)]
struct JarCompareReport {
    #[facet(proxy = JsonPath)]
    gradle_jar: PathBuf,
    #[facet(proxy = JsonPath)]
    rust_jar: PathBuf,
    strict_manifest: bool,
    matches: bool,
    total_gradle_entries: usize,
    total_rust_entries: usize,
    compared_entries: usize,
    missing_entries: Vec<String>,
    extra_entries: Vec<String>,
    changed_entries: Vec<ChangedEntry>,
    manifest: ManifestCompare,
}

#[derive(Debug, Facet)]
struct ChangedEntry {
    path: String,
    #[facet(alias = "gradle_sha1")]
    gradle_hash: ContentHash,
    #[facet(alias = "rust_sha1")]
    rust_hash: ContentHash,
}

#[derive(Debug, Facet)]
struct ManifestCompare {
    compared: bool,
    changed: bool,
    ignored_implementation_timestamp: bool,
    gradle_sha1: Option<ContentHash>,
    rust_sha1: Option<ContentHash>,
}

#[derive(Debug, Facet)]
struct JarJarMetadata {
    jars: Vec<JarJarMetadataEntry>,
}

#[derive(Debug, Facet)]
struct JarJarMetadataEntry {
    identifier: JarJarIdentifier,
    version: JarJarVersion,
    path: String,
    #[facet(rename = "isObfuscated")]
    is_obfuscated: bool,
}

#[derive(Debug, Facet)]
struct JarJarIdentifier {
    group: String,
    artifact: String,
}

#[derive(Debug, Facet)]
struct JarJarVersion {
    range: String,
    #[facet(rename = "artifactVersion")]
    artifact_version: String,
}

#[derive(Debug)]
struct NormalizedJar {
    entries: BTreeMap<String, ContentHash>,
    manifest_sha1: Option<ContentHash>,
    total_entries: usize,
}

#[derive(Debug, Eq, Facet, PartialEq)]
#[repr(u8)]
enum NodeStatus {
    Ready,
    Planned,
}

#[derive(Debug, Facet)]
struct MojangVersionManifest {
    #[facet(default)]
    versions: Vec<MojangManifestVersion>,
}

#[derive(Debug, Facet)]
struct MojangManifestVersion {
    id: String,
    url: String,
}

#[derive(Debug, Facet)]
struct MinecraftVersionJson {
    downloads: MinecraftDownloads,
    #[facet(default)]
    libraries: Vec<MinecraftLibrary>,
    #[facet(rename = "assetIndex", default)]
    asset_index: Option<MinecraftAssetIndex>,
}

#[derive(Debug, Facet)]
struct MinecraftDownloads {
    client: MinecraftDownload,
    server: MinecraftDownload,
    #[facet(default)]
    client_mappings: Option<MinecraftDownload>,
    #[facet(default)]
    server_mappings: Option<MinecraftDownload>,
}

#[derive(Debug, Facet)]
struct MinecraftDownload {
    url: String,
}

#[derive(Debug, Facet)]
struct MinecraftAssetIndex {
    id: String,
    url: String,
}

#[derive(Debug, Facet)]
struct MinecraftAssetIndexJson {
    #[facet(default)]
    objects: BTreeMap<String, MinecraftAssetObject>,
}

#[derive(Debug, Facet)]
struct MinecraftAssetObject {
    hash: ContentHash,
}

#[derive(Debug, Facet)]
struct MinecraftLibrary {
    #[facet(default)]
    downloads: Option<MinecraftLibraryDownloads>,
}

#[derive(Debug, Facet)]
struct MinecraftLibraryDownloads {
    #[facet(default)]
    artifact: Option<MinecraftLibraryArtifact>,
}

#[derive(Debug, Facet)]
struct MinecraftLibraryArtifact {
    url: String,
    path: String,
    #[facet(default)]
    sha1: Option<ContentHash>,
}

#[derive(Debug, Default, Facet)]
struct ForgeUserdevConfig {
    #[facet(default)]
    spec: Option<i64>,
    #[facet(default)]
    mcp: Option<String>,
    #[facet(rename = "neoForm", default)]
    neo_form: Option<String>,
    #[facet(default)]
    sources: Option<String>,
    #[facet(default)]
    universal: Option<String>,
    #[facet(default)]
    binpatcher: Option<ForgeBinpatcherConfig>,
    #[facet(default)]
    patches: Option<String>,
    #[facet(rename = "patchesOriginalPrefix", default)]
    patches_original_prefix: Option<String>,
    #[facet(rename = "patchesModifiedPrefix", default)]
    patches_modified_prefix: Option<String>,
    #[facet(default)]
    ats: Option<StringList>,
    #[facet(default)]
    sass: Option<StringList>,
    #[facet(default)]
    modules: Vec<String>,
    #[facet(default)]
    libraries: Vec<String>,
    #[facet(rename = "testLibraries", default)]
    test_libraries: Vec<String>,
    #[facet(default)]
    runs: BTreeMap<String, ForgeRunConfig>,
}

#[derive(Debug, Default, Facet)]
struct ForgeBinpatcherConfig {
    #[facet(default)]
    version: Option<String>,
}

#[derive(Debug, Facet)]
#[facet(untagged)]
#[repr(u8)]
enum StringList {
    One(String),
    Many(Vec<String>),
}

impl StringList {
    fn into_vec(self) -> Vec<String> {
        match self {
            Self::One(value) => vec![value],
            Self::Many(values) => values,
        }
    }
}

#[derive(Debug, Default, Facet)]
struct McpConfigJson {
    #[facet(default)]
    data: McpData,
    #[facet(default)]
    steps: McpSteps,
    #[facet(default)]
    functions: BTreeMap<String, McpFunction>,
    #[facet(default)]
    libraries: BTreeMap<String, Vec<String>>,
}

#[derive(Debug, Default, Facet)]
struct McpData {
    #[facet(default)]
    mappings: Option<String>,
    #[facet(default)]
    inject: Option<String>,
    #[facet(default)]
    patches: Option<McpPatchData>,
}

#[derive(Debug, Default, Facet)]
struct McpPatchData {
    #[facet(default)]
    client: Option<String>,
    #[facet(default)]
    joined: Option<String>,
    #[facet(default)]
    server: Option<String>,
}

#[derive(Debug, Default, Facet)]
struct McpSteps {
    #[facet(default)]
    joined: Vec<McpStep>,
}

#[derive(Debug, Default, Facet)]
struct McpStep {
    #[facet(default)]
    name: Option<String>,
    #[facet(rename = "type", default)]
    step_type: Option<String>,
}

#[derive(Debug, Default, Facet)]
struct McpFunction {
    #[facet(default)]
    version: Option<String>,
    #[facet(default)]
    args: Vec<String>,
    #[facet(default)]
    jvmargs: Vec<String>,
    #[facet(default)]
    repo: Option<String>,
}

impl McpPatchData {
    fn has_any_patch_root(&self) -> bool {
        self.client.is_some() || self.joined.is_some() || self.server.is_some()
    }
}

impl McpFunction {
    fn has_declared_config(&self) -> bool {
        self.version
            .as_deref()
            .is_some_and(|version| !version.is_empty())
            || !self.args.is_empty()
            || !self.jvmargs.is_empty()
            || self.repo.as_deref().is_some_and(|repo| !repo.is_empty())
    }
}

impl MavenCoordinate {
    fn parse(input: &str) -> eyre::Result<Self> {
        let (notation, extension) = input
            .split_once('@')
            .map_or((input, "jar"), |(left, right)| (left, right));
        let parts: Vec<&str> = notation.split(':').collect();
        match parts.as_slice() {
            [group, artifact, version] => Ok(Self {
                group: (*group).to_string(),
                artifact: (*artifact).to_string(),
                version: (*version).to_string(),
                classifier: None,
                extension: extension.to_string(),
            }),
            [group, artifact, version, classifier] => Ok(Self {
                group: (*group).to_string(),
                artifact: (*artifact).to_string(),
                version: (*version).to_string(),
                classifier: Some((*classifier).to_string()),
                extension: extension.to_string(),
            }),
            _ => eyre::bail!("Invalid Maven coordinate: {input}"),
        }
    }

    fn file_name(&self) -> String {
        let classifier = self
            .classifier
            .as_ref()
            .map_or_else(String::new, |classifier| format!("-{classifier}"));
        format!(
            "{}-{}{}.{}",
            self.artifact, self.version, classifier, self.extension
        )
    }

    fn with_classifier(&self, classifier: &str) -> Self {
        Self {
            group: self.group.clone(),
            artifact: self.artifact.clone(),
            version: self.version.clone(),
            classifier: Some(classifier.to_string()),
            extension: self.extension.clone(),
        }
    }

    fn with_extension(&self, extension: &str) -> Self {
        Self {
            group: self.group.clone(),
            artifact: self.artifact.clone(),
            version: self.version.clone(),
            classifier: self.classifier.clone(),
            extension: extension.to_string(),
        }
    }
}

impl std::fmt::Display for MavenCoordinate {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}:{}:{}", self.group, self.artifact, self.version)?;
        if let Some(classifier) = &self.classifier {
            write!(f, ":{classifier}")?;
        }
        if self.extension != "jar" {
            write!(f, "@{}", self.extension)?;
        }
        Ok(())
    }
}

