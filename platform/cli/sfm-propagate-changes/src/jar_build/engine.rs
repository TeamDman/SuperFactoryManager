use super::BuildMode;
use super::BuildOptions;
use super::CompareOptions;
use super::RunKind;
use super::json_path::JsonOptionalPath;
use super::json_path::JsonPath;
use crate::worktree::get_sorted_worktrees;
use chrono::Local;
use eyre::Context;
use facet::Facet;
use reqwest::StatusCode;
use reqwest::blocking::Client;
use sha1::Digest;
use sha1::Sha1;
use std::cmp::Ordering;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fmt::Write as _;
use std::fs;
use std::fs::File;
use std::io::BufRead;
use std::io::BufReader;
use std::io::Cursor;
use std::io::Read;
use std::io::Write;
use std::path::Path;
use std::path::PathBuf;
use std::process::Command;
use std::process::ExitStatus;
use std::process::Stdio;
use std::thread;
use std::time::Duration;
use std::time::Instant;
use zip::CompressionMethod;
use zip::ZipArchive;
use zip::ZipWriter;
use zip::write::SimpleFileOptions;

const VERSION_MANIFEST_URL: &str =
    "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
const NEOFORM_RUNTIME_COORDINATE: &str = "net.neoforged:neoform-runtime:2.0.19:all";

pub(crate) fn invoke_build(options: &BuildOptions) -> eyre::Result<()> {
    let _span = tracing::info_span!(
        "sfm_jar_build_command",
        mc = %options.mc,
        mode = ?options.mode,
        refresh = options.refresh,
        explain_rebuild = options.explain_rebuild,
        dry_run = options.dry_run,
        allow_local_artifact_cache = options.allow_local_artifact_cache,
    )
    .entered();
    let plan = create_plan(options)?;
    write_plan_outputs(&plan, options.plan_json.as_deref())?;
    print_plan_summary(&plan);

    match options.mode {
        BuildMode::Plan => write_artifact_lockfile(&plan),
        BuildMode::Build if options.dry_run => {
            println!("Jar build dry-run: resolved plan and lockfile; skipped build execution.");
            tracing::info!("jar_build_dry_run_skip_execution");
            write_artifact_lockfile(&plan)
        }
        BuildMode::Build => {
            execute_build(&plan, options.explain_rebuild, BuildTarget::Jar)?;
            write_artifact_lockfile(&plan)
        }
    }
}

pub(crate) fn invoke_run(options: &BuildOptions, kind: RunKind) -> eyre::Result<()> {
    let _span = tracing::info_span!(
        "sfm_run_command",
        mc = %options.mc,
        kind = kind.command_name(),
        refresh = options.refresh,
        explain_rebuild = options.explain_rebuild,
        dry_run = options.dry_run,
        allow_local_artifact_cache = options.allow_local_artifact_cache,
    )
    .entered();
    let plan = create_plan(options)?;
    write_plan_outputs(&plan, options.plan_json.as_deref())?;
    print_plan_summary(&plan);
    execute_build(&plan, options.explain_rebuild, BuildTarget::Run)?;
    write_artifact_lockfile(&plan)?;
    execute_run(&plan, kind, options.dry_run)
}

pub(crate) fn invoke_compare(options: &CompareOptions) -> eyre::Result<()> {
    let _span = tracing::info_span!(
        "sfm_jar_compare_command",
        mc = %options.mc,
        strict_manifest = options.strict_manifest,
    )
    .entered();
    let paths = resolve_compare_paths(options)?;
    let report = compare_jars(&paths.gradle_jar, &paths.rust_jar, options.strict_manifest)?;

    print_compare_report(&report);
    write_compare_report(&report, options.report_json.as_deref())?;

    if report.matches {
        Ok(())
    } else {
        eyre::bail!("Jar comparison found normalized differences.")
    }
}

#[derive(Debug)]
struct ComparePaths {
    gradle_jar: PathBuf,
    rust_jar: PathBuf,
}

#[derive(Debug, Facet)]
struct BuildPlan {
    schema_version: u32,
    mode: String,
    minecraft_version: String,
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
    state_dir: PathBuf,
    #[facet(proxy = JsonPath)]
    maven_cache_dir: PathBuf,
    #[facet(proxy = JsonPath)]
    lockfile_path: PathBuf,
    #[facet(skip_serializing)]
    lockfile: Option<ArtifactLockfile>,
    java: JavaPlan,
    java_release: u32,
    refresh: bool,
    allow_local_artifact_cache: bool,
    properties: BTreeMap<String, String>,
    repositories: Vec<Repository>,
    loader_toolchain: LoaderToolchainPlan,
    artifacts: Vec<ArtifactPlan>,
    minecraft: MinecraftPlan,
    forge_userdev: Option<ForgeUserdevPlan>,
    mcp_config: Option<McpConfigPlan>,
    dependencies: Vec<DependencyPlan>,
    graph: Vec<GraphNode>,
    warnings: Vec<String>,
}

#[derive(Clone, Debug, Facet)]
struct Repository {
    name: String,
    url: String,
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
    id: String,
    coordinate: Option<String>,
    repository: Option<String>,
    url: Option<String>,
    #[facet(proxy = JsonPath)]
    cache_path: PathBuf,
    sha1: Option<String>,
    downloaded: bool,
    required_for: String,
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
    sha1: String,
}

#[derive(Clone, Debug, Facet)]
struct ArtifactLockfile {
    schema_version: u32,
    minecraft_version: String,
    #[facet(proxy = JsonPath)]
    maven_cache_dir: PathBuf,
    allow_local_artifact_cache: bool,
    repositories: Vec<Repository>,
    dependencies: Vec<DependencyLockEntry>,
    artifacts: Vec<ArtifactLockEntry>,
}

#[derive(Clone, Debug, Facet)]
struct DependencyLockEntry {
    configuration: String,
    notation: String,
    resolved_notation: String,
    source: DependencySource,
    dynamic_version: bool,
    #[facet(proxy = JsonPath)]
    cache_path: PathBuf,
}

#[derive(Clone, Debug, Facet)]
struct ArtifactLockEntry {
    coordinate: Option<String>,
    source: ArtifactSource,
    repository: Option<String>,
    url: Option<String>,
    #[facet(proxy = JsonPath)]
    cache_path: PathBuf,
    #[facet(proxy = JsonOptionalPath)]
    original_path: Option<PathBuf>,
    sha1: String,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
enum ArtifactSource {
    RemoteMaven,
    RemoteHttp,
    LocalM2Cache,
    LocalGradleModuleCache,
    ExistingSfmCacheUnknown,
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
enum DependencySource {
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
    gradle_sha1: String,
    rust_sha1: String,
}

#[derive(Debug, Facet)]
struct ManifestCompare {
    compared: bool,
    changed: bool,
    ignored_implementation_timestamp: bool,
    gradle_sha1: Option<String>,
    rust_sha1: Option<String>,
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
    entries: BTreeMap<String, String>,
    manifest_sha1: Option<String>,
    total_entries: usize,
}

#[derive(Debug, Eq, Facet, PartialEq)]
#[repr(u8)]
enum NodeStatus {
    Ready,
    Planned,
}

#[derive(Debug)]
struct Resolver {
    client: Client,
    cache_dir: PathBuf,
    repositories: Vec<Repository>,
    refresh: bool,
    allow_local_artifact_cache: bool,
    lockfile: Option<ArtifactLockfile>,
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
    hash: String,
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

impl Resolver {
    fn new(
        cache_dir: PathBuf,
        repositories: Vec<Repository>,
        refresh: bool,
        allow_local_artifact_cache: bool,
        lockfile: Option<ArtifactLockfile>,
    ) -> eyre::Result<Self> {
        let _span = tracing::debug_span!(
            "create_maven_resolver",
            cache_dir = %cache_dir.display(),
            repository_count = repositories.len(),
            refresh,
            allow_local_artifact_cache,
            has_lockfile = lockfile.is_some(),
        )
        .entered();
        let client = Client::builder()
            .user_agent("sfm-propagate-changes/no-gradle-toolchain")
            .build()
            .wrap_err("Failed to create HTTP client")?;

        Ok(Self {
            client,
            cache_dir,
            repositories,
            refresh,
            allow_local_artifact_cache,
            lockfile,
        })
    }

    fn resolve_artifact(
        &self,
        id: &str,
        coordinate: &MavenCoordinate,
        required_for: &str,
    ) -> eyre::Result<ArtifactPlan> {
        let _span = tracing::debug_span!(
            "resolve_artifact",
            id,
            coordinate = %coordinate,
            required_for,
        )
        .entered();
        let coordinate = self.resolve_dynamic_coordinate(coordinate)?;
        let cache_path = self.cache_path_for(&coordinate);

        if cache_path.is_file() && !self.refresh {
            let artifact = Self::cached_artifact_plan(id, &coordinate, cache_path, required_for)?;
            self.verify_locked_artifact(&coordinate, &artifact)?;
            tracing::debug!(
                coordinate = %coordinate,
                cache_path = %artifact.cache_path.display(),
                sha1 = artifact.sha1.as_deref(),
                "artifact cache hit"
            );
            return Ok(artifact);
        }

        let mut attempted = Vec::new();
        for repo in self.candidate_repositories(&coordinate) {
            let url = Self::artifact_url(repo, &coordinate);
            attempted.push(url.clone());
            tracing::debug!(
                coordinate = %coordinate,
                repository = repo.name.as_str(),
                url = url.as_str(),
                "checking artifact remote"
            );
            let download_result = if coordinate.group == "curse.maven" {
                download_to_path_overwrite(&self.client, &url, &cache_path, self.refresh)
            } else {
                if !remote_exists(&self.client, &url)? {
                    continue;
                }
                download_to_path_overwrite(&self.client, &url, &cache_path, self.refresh)
            };

            if let Err(error) = download_result {
                attempted.push(format!("{url} ({error:#})"));
                continue;
            }

            let artifact =
                Self::remote_artifact_plan(id, &coordinate, repo, url, cache_path, required_for)?;
            self.verify_locked_artifact(&coordinate, &artifact)?;
            tracing::info!(
                coordinate = %coordinate,
                repository = artifact.repository.as_deref(),
                cache_path = %artifact.cache_path.display(),
                sha1 = artifact.sha1.as_deref(),
                "artifact downloaded"
            );
            return Ok(artifact);
        }

        if self.allow_local_artifact_cache
            && let Some(local_artifact) = find_local_cached_artifact(&coordinate)
        {
            let artifact = Self::local_artifact_plan(
                id,
                &coordinate,
                local_artifact,
                cache_path,
                required_for,
            )?;
            self.verify_locked_artifact(&coordinate, &artifact)?;
            tracing::info!(
                coordinate = %coordinate,
                cache_path = %artifact.cache_path.display(),
                source = ?artifact.provenance.source,
                sha1 = artifact.sha1.as_deref(),
                "artifact copied from local cache fallback"
            );
            return Ok(artifact);
        }

        eyre::bail!(
            "Could not resolve artifact {}. Tried:\n{}\nLocal .m2/Gradle cache fallback is disabled; pass --allow-local-artifact-cache to allow bootstrapping from local Maven-created caches.",
            coordinate,
            attempted.join("\n")
        );
    }

    fn verify_locked_artifact(
        &self,
        coordinate: &MavenCoordinate,
        artifact: &ArtifactPlan,
    ) -> eyre::Result<()> {
        let Some(lockfile) = &self.lockfile else {
            return Ok(());
        };
        let coordinate_text = coordinate.to_string();
        let Some(locked) = lockfile
            .artifacts
            .iter()
            .find(|entry| entry.coordinate.as_deref() == Some(coordinate_text.as_str()))
        else {
            eyre::bail!(
                "Artifact {} is not present in {}. Run jar build --mc {} --refresh to update the lockfile intentionally.",
                coordinate_text,
                "sfm-toolchain.lock.json",
                lockfile.minecraft_version
            );
        };
        let actual_sha1 = artifact.sha1.as_deref().ok_or_else(|| {
            eyre::eyre!(
                "Resolved artifact {} did not report a SHA-1 for lock verification",
                coordinate_text
            )
        })?;
        if actual_sha1 != locked.sha1 {
            eyre::bail!(
                "Artifact {} resolved with SHA-1 {}, but sfm-toolchain.lock.json requires {}",
                coordinate_text,
                actual_sha1,
                locked.sha1
            );
        }
        Ok(())
    }

    fn cached_artifact_plan(
        id: &str,
        coordinate: &MavenCoordinate,
        cache_path: PathBuf,
        required_for: &str,
    ) -> eyre::Result<ArtifactPlan> {
        let sha1 = file_sha1(&cache_path)?;
        let provenance = read_artifact_provenance(&cache_path)?.unwrap_or_else(|| {
            artifact_provenance(
                ArtifactSource::ExistingSfmCacheUnknown,
                Some(coordinate.to_string()),
                None,
                None,
                None,
                sha1.clone(),
            )
        });
        Ok(ArtifactPlan {
            id: id.to_string(),
            coordinate: Some(coordinate.to_string()),
            repository: provenance.repository.clone(),
            url: provenance.url.clone(),
            sha1: Some(sha1),
            cache_path,
            downloaded: false,
            required_for: required_for.to_string(),
            provenance,
        })
    }

    fn remote_artifact_plan(
        id: &str,
        coordinate: &MavenCoordinate,
        repo: &Repository,
        url: String,
        cache_path: PathBuf,
        required_for: &str,
    ) -> eyre::Result<ArtifactPlan> {
        let sha1 = file_sha1(&cache_path)?;
        let provenance = artifact_provenance(
            ArtifactSource::RemoteMaven,
            Some(coordinate.to_string()),
            Some(repo.name.clone()),
            Some(url.clone()),
            None,
            sha1.clone(),
        );
        write_artifact_provenance(&cache_path, &provenance)?;
        Ok(ArtifactPlan {
            id: id.to_string(),
            coordinate: Some(coordinate.to_string()),
            repository: Some(repo.name.clone()),
            url: Some(url),
            sha1: Some(sha1),
            cache_path,
            downloaded: true,
            required_for: required_for.to_string(),
            provenance,
        })
    }

    fn local_artifact_plan(
        id: &str,
        coordinate: &MavenCoordinate,
        local_artifact: LocalCachedArtifact,
        cache_path: PathBuf,
        required_for: &str,
    ) -> eyre::Result<ArtifactPlan> {
        if let Some(parent) = cache_path.parent() {
            fs::create_dir_all(parent)?;
        }
        fs::copy(&local_artifact.path, &cache_path).wrap_err_with(|| {
            format!(
                "Failed to copy local cached artifact {} to {}",
                local_artifact.path.display(),
                cache_path.display()
            )
        })?;
        let sha1 = file_sha1(&cache_path)?;
        let provenance = artifact_provenance(
            local_artifact.source,
            Some(coordinate.to_string()),
            Some("local-artifact-cache".to_string()),
            None,
            Some(local_artifact.path.clone()),
            sha1.clone(),
        );
        write_artifact_provenance(&cache_path, &provenance)?;
        Ok(ArtifactPlan {
            id: id.to_string(),
            coordinate: Some(coordinate.to_string()),
            repository: Some("local-artifact-cache".to_string()),
            url: Some(local_artifact.path.display().to_string()),
            sha1: Some(sha1),
            cache_path,
            downloaded: false,
            required_for: required_for.to_string(),
            provenance,
        })
    }

    fn resolve_dependency(
        &self,
        configuration: &str,
        coordinate: &MavenCoordinate,
    ) -> eyre::Result<DependencyPlan> {
        let dynamic_version = coordinate.version.ends_with('+');
        let resolved = self.resolve_dynamic_coordinate(coordinate)?;
        let source = if resolved.group == "curse.maven" {
            DependencySource::CurseMaven
        } else {
            DependencySource::Maven
        };
        let cache_path = self.cache_path_for(&resolved);
        let url = self
            .candidate_repositories(&resolved)
            .first()
            .map(|repo| Self::artifact_url(repo, &resolved));

        Ok(DependencyPlan {
            configuration: configuration.to_string(),
            notation: coordinate.to_string(),
            resolved_notation: resolved.to_string(),
            source,
            cache_path,
            url,
            dynamic_version,
        })
    }

    fn resolve_dynamic_coordinate(
        &self,
        coordinate: &MavenCoordinate,
    ) -> eyre::Result<MavenCoordinate> {
        if !coordinate.version.ends_with('+') {
            return Ok(coordinate.clone());
        }

        if let Some(lockfile) = &self.lockfile {
            let notation = coordinate.to_string();
            let Some(locked) = lockfile
                .dependencies
                .iter()
                .find(|dependency| dependency.notation == notation)
            else {
                eyre::bail!(
                    "Dynamic dependency {} is not present in sfm-toolchain.lock.json. Run jar build --mc {} --refresh to update the lockfile intentionally.",
                    notation,
                    lockfile.minecraft_version
                );
            };
            let resolved = MavenCoordinate::parse(&locked.resolved_notation)?;
            if resolved.version.ends_with('+') {
                eyre::bail!(
                    "sfm-toolchain.lock.json resolved {} to dynamic version {}; refresh the lockfile.",
                    notation,
                    locked.resolved_notation
                );
            }
            return Ok(resolved);
        }

        let prefix = coordinate.version.trim_end_matches('+');
        let mut candidates = Vec::new();

        for repo in self.candidate_repositories(coordinate) {
            let metadata_url = Self::maven_metadata_url(repo, coordinate);
            let Ok(metadata) = download_text_optional(&self.client, &metadata_url) else {
                continue;
            };

            candidates.extend(
                parse_maven_versions(&metadata)
                    .into_iter()
                    .filter(|version| version.starts_with(prefix)),
            );
        }

        candidates.sort_by(|left, right| compare_version_text(left, right));
        candidates.dedup();

        let Some(version) = candidates.pop() else {
            eyre::bail!(
                "No Maven metadata version matched {} for {}:{}",
                coordinate.version,
                coordinate.group,
                coordinate.artifact
            );
        };

        Ok(MavenCoordinate {
            version,
            ..coordinate.clone()
        })
    }

    fn candidate_repositories(&self, coordinate: &MavenCoordinate) -> Vec<&Repository> {
        let preferred_names: &[&str] = if coordinate.group == "curse.maven" {
            &["CurseMaven"]
        } else if coordinate.group == "com.teamcofh" {
            &["Thermal"]
        } else if coordinate.group == "mezz.jei" {
            &["BlameJared", "JEI"]
        } else if coordinate.group == "org.parchmentmc.data" {
            &["Parchment"]
        } else if coordinate.group == "org.spongepowered" {
            &["Sponge", "Maven Central"]
        } else if coordinate.group == "net.minecraftforge" || coordinate.group == "de.oceanlabs.mcp"
        {
            &["Forge"]
        } else if coordinate.group == "net.neoforged" {
            &["NeoForged"]
        } else if coordinate.group.starts_with("org.")
            || coordinate.group.starts_with("com.github.")
            || coordinate.group.starts_with("junit")
        {
            &["Maven Central"]
        } else {
            &[]
        };

        let mut selected = Vec::new();
        for name in preferred_names {
            if let Some(repo) = self.repositories.iter().find(|repo| repo.name == *name) {
                selected.push(repo);
            }
        }

        if selected.is_empty() {
            selected.extend(self.repositories.iter());
        }

        selected
    }

    fn cache_path_for(&self, coordinate: &MavenCoordinate) -> PathBuf {
        self.cache_dir
            .join(coordinate.group.replace('.', "/"))
            .join(&coordinate.artifact)
            .join(&coordinate.version)
            .join(coordinate.file_name())
    }

    fn artifact_url(repo: &Repository, coordinate: &MavenCoordinate) -> String {
        format!(
            "{}/{}/{}/{}/{}",
            repo.url.trim_end_matches('/'),
            coordinate.group.replace('.', "/"),
            coordinate.artifact,
            coordinate.version,
            coordinate.file_name()
        )
    }

    fn maven_metadata_url(repo: &Repository, coordinate: &MavenCoordinate) -> String {
        format!(
            "{}/{}/{}/maven-metadata.xml",
            repo.url.trim_end_matches('/'),
            coordinate.group.replace('.', "/"),
            coordinate.artifact
        )
    }

    fn resolve_pom_runtime_dependencies(
        &self,
        coordinate: &MavenCoordinate,
    ) -> Vec<MavenCoordinate> {
        if coordinate.group == "curse.maven"
            || coordinate.classifier.is_some()
            || coordinate.extension != "jar"
        {
            return Vec::new();
        }

        let pom_coordinate = coordinate.with_extension("pom");
        for repo in self.candidate_repositories(coordinate) {
            let url = Self::artifact_url(repo, &pom_coordinate);
            let Ok(pom) = download_text_optional(&self.client, &url) else {
                continue;
            };
            return parse_maven_pom_runtime_dependencies(&pom, coordinate);
        }

        Vec::new()
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

#[derive(Debug)]
struct LocalCachedArtifact {
    path: PathBuf,
    source: ArtifactSource,
}

fn find_local_cached_artifact(coordinate: &MavenCoordinate) -> Option<LocalCachedArtifact> {
    let relative = PathBuf::from(coordinate.group.replace('.', "/"))
        .join(&coordinate.artifact)
        .join(&coordinate.version)
        .join(coordinate.file_name());
    if let Some(home) = std::env::var_os("USERPROFILE").or_else(|| std::env::var_os("HOME")) {
        let home = PathBuf::from(home);
        let m2 = home.join(".m2").join("repository").join(&relative);
        if m2.is_file() {
            return Some(LocalCachedArtifact {
                path: m2,
                source: ArtifactSource::LocalM2Cache,
            });
        }

        let gradle_module = home
            .join(".gradle")
            .join("caches")
            .join("modules-2")
            .join("files-2.1")
            .join(&coordinate.group)
            .join(&coordinate.artifact)
            .join(&coordinate.version);
        if let Ok(hash_dirs) = fs::read_dir(gradle_module) {
            for hash_dir in hash_dirs.flatten() {
                let candidate = hash_dir.path().join(coordinate.file_name());
                if candidate.is_file() {
                    return Some(LocalCachedArtifact {
                        path: candidate,
                        source: ArtifactSource::LocalGradleModuleCache,
                    });
                }
            }
        }
    }

    None
}

#[expect(
    clippy::too_many_lines,
    reason = "The planner is a single orchestration pass over project inputs."
)]
fn create_plan(options: &BuildOptions) -> eyre::Result<BuildPlan> {
    let _span = tracing::info_span!(
        "create_build_plan",
        mc = %options.mc,
        refresh = options.refresh,
        allow_local_artifact_cache = options.allow_local_artifact_cache,
    )
    .entered();
    let worktree_path = find_worktree_path(&options.mc)?;
    let minecraft_dir = worktree_path.join("platform").join("minecraft");
    let properties_path = minecraft_dir.join("gradle.properties");
    let properties = read_properties(&properties_path)?;

    let minecraft_version = required_property(&properties, "minecraft_version")?;
    let mut warnings = Vec::new();
    if minecraft_version != options.mc {
        warnings.push(format!(
            "--mc {} selected {}, but gradle.properties says minecraft_version={minecraft_version}; using minecraft_version for artifact coordinates.",
            options.mc,
            worktree_path.display()
        ));
    }

    let loader_version = required_property(&properties, "neo_version")?;
    let (mapping_channel, mapping_version) =
        resolve_mapping_settings(&properties, minecraft_version);
    let mod_name = required_property(&properties, "mod_name")?;
    let mod_version = required_property(&properties, "mod_version")?;

    let gradle_output_jar =
        gradle_output_jar_path(&minecraft_dir, mod_name, minecraft_version, mod_version);
    let rust_output_jar =
        rust_output_jar_path(&minecraft_dir, mod_name, minecraft_version, mod_version);
    let cache_dir = minecraft_dir.join("build").join("sfm-toolchain");
    let state_dir = cache_dir.join("state");
    let maven_cache_dir = cache_dir.join("maven");
    let lockfile_path = minecraft_dir.join("sfm-toolchain.lock.json");
    fs::create_dir_all(&state_dir)?;
    fs::create_dir_all(&maven_cache_dir)?;
    let lockfile = if options.refresh {
        None
    } else {
        read_optional_artifact_lockfile(&lockfile_path, minecraft_version)?
    };

    let repositories = repositories();
    let resolver = Resolver::new(
        maven_cache_dir.clone(),
        repositories.clone(),
        options.refresh,
        options.allow_local_artifact_cache,
        lockfile.clone(),
    )?;

    let dependency_script = minecraft_dir
        .join("gradle")
        .join("dependencies")
        .join(minecraft_version)
        .join("dependencies.gradle");
    let dependencies = parse_dependency_script(&dependency_script, &properties)?;
    let loader_toolchain =
        resolve_loader_toolchain(&dependencies, minecraft_version, loader_version)?;
    let java_release = read_java_toolchain_release(&minecraft_dir, minecraft_version)?;
    let required_java = required_java_runtime_major(&loader_toolchain, java_release);
    let java = resolve_java(options.java_home.as_deref(), required_java)?;

    let forge_userdev_coordinate = MavenCoordinate::parse(&loader_toolchain.userdev_coordinate)?;
    let forge_userdev_artifact = resolver.resolve_artifact(
        "forge-userdev",
        &forge_userdev_coordinate,
        "Loader userdev configuration and patches",
    )?;
    let forge_userdev = read_forge_userdev(&forge_userdev_artifact)?;

    let mcp_config = if loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
        None
    } else if let Some(mcp) = &forge_userdev.mcp {
        let mcp_coordinate = MavenCoordinate::parse(mcp)?;
        let mcp_artifact = resolver.resolve_artifact(
            "mcp-config",
            &mcp_coordinate,
            "MCPConfig clean-slate Minecraft pipeline",
        )?;
        Some(read_mcp_config(&mcp_artifact)?)
    } else {
        None
    };

    let mut artifacts = Vec::new();
    artifacts.push(forge_userdev.artifact.clone());
    if let Some(mcp_config) = &mcp_config {
        artifacts.push(mcp_config.artifact.clone());
    }

    for (id, coordinate, required_for) in core_coordinates(
        &loader_toolchain,
        &mapping_channel,
        &mapping_version,
        &forge_userdev,
        mcp_config.as_ref(),
        &dependencies,
    )? {
        let artifact = resolver.resolve_artifact(&id, &coordinate, &required_for)?;
        artifacts.push(artifact);
    }

    let minecraft = resolve_minecraft_plan(&cache_dir, &resolver.client, minecraft_version)?;
    artifacts.push(minecraft.version_manifest.clone());
    artifacts.push(minecraft.version_json.clone());

    let dependency_plans = dependencies
        .iter()
        .filter(|dependency| should_plan_project_dependency(&loader_toolchain, dependency))
        .map(|dependency| {
            resolver.resolve_dependency(&dependency.configuration, &dependency.coordinate)
        })
        .collect::<eyre::Result<Vec<_>>>()?;
    let dependency_plans = if loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
        add_transitive_runtime_dependency_plans(&resolver, dependency_plans)?
    } else {
        dependency_plans
    };

    let graph = build_graph(
        minecraft_version,
        &rust_output_jar,
        &dependency_plans,
        &loader_toolchain,
    );

    Ok(BuildPlan {
        schema_version: 1,
        mode: match options.mode {
            BuildMode::Plan => "plan".to_string(),
            BuildMode::Build => "build".to_string(),
        },
        minecraft_version: minecraft_version.to_string(),
        worktree_path,
        minecraft_dir,
        gradle_output_jar,
        rust_output_jar,
        cache_dir,
        state_dir,
        maven_cache_dir,
        lockfile_path,
        lockfile,
        java,
        java_release,
        refresh: options.refresh,
        allow_local_artifact_cache: options.allow_local_artifact_cache,
        properties,
        repositories,
        loader_toolchain,
        artifacts,
        minecraft,
        forge_userdev: Some(forge_userdev),
        mcp_config,
        dependencies: dependency_plans,
        graph,
        warnings,
    })
}

fn core_coordinates(
    loader_toolchain: &LoaderToolchainPlan,
    mapping_channel: &str,
    mapping_version: &str,
    userdev: &ForgeUserdevPlan,
    mcp_config: Option<&McpConfigPlan>,
    dependencies: &[ParsedDependency],
) -> eyre::Result<Vec<(String, MavenCoordinate, String)>> {
    let mut coordinates = Vec::new();

    if let Some(sources) = userdev
        .sources
        .as_deref()
        .or(loader_toolchain.sources_coordinate.as_deref())
    {
        let artifact_id = if loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
            "neoforge-sources"
        } else {
            "forge-sources"
        };
        coordinates.push((
            artifact_id.to_string(),
            MavenCoordinate::parse(sources)?,
            "Loader source patch application".to_string(),
        ));
    }

    if let Some(universal) = userdev
        .universal
        .as_deref()
        .or(loader_toolchain.universal_coordinate.as_deref())
    {
        let artifact_id = if loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
            "neoforge-universal"
        } else {
            "forge-universal"
        };
        coordinates.push((
            artifact_id.to_string(),
            MavenCoordinate::parse(universal)?,
            "Loader userdev resource merge".to_string(),
        ));
    }

    let neoform_coordinate = userdev.neo_form.as_ref().or_else(|| {
        (loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev)
            .then_some(userdev.mcp.as_ref())
            .flatten()
    });
    if let Some(neo_form) = neoform_coordinate {
        coordinates.push((
            "neoform-config".to_string(),
            MavenCoordinate::parse(neo_form)?,
            "NeoForm clean-slate Minecraft pipeline".to_string(),
        ));
    }

    if mapping_channel == "parchment" {
        coordinates.push((
            "parchment-data".to_string(),
            parchment_coordinate(mapping_version)?,
            "Parchment names layered over official mappings".to_string(),
        ));
    }

    if loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
        coordinates.push((
            "tool-neoform-runtime".to_string(),
            MavenCoordinate::parse(NEOFORM_RUNTIME_COORDINATE)?,
            "NeoForm Runtime userdev execution".to_string(),
        ));
        add_userdev_test_library_coordinates(&mut coordinates, userdev)?;
        add_project_tool_coordinates(&mut coordinates, dependencies)?;
        return Ok(coordinates);
    }

    if let Some(binpatcher) = &userdev.binpatcher {
        coordinates.push((
            "forge-binarypatcher".to_string(),
            MavenCoordinate::parse(binpatcher)?,
            "Forge binary patch application".to_string(),
        ));
    }

    add_mcp_tool_coordinates(&mut coordinates, mcp_config)?;

    add_project_tool_coordinates(&mut coordinates, dependencies)?;

    Ok(coordinates)
}

fn add_userdev_test_library_coordinates(
    coordinates: &mut Vec<(String, MavenCoordinate, String)>,
    userdev: &ForgeUserdevPlan,
) -> eyre::Result<()> {
    for (index, coordinate) in userdev.test_libraries.iter().enumerate() {
        coordinates.push((
            format!("forge-userdev-test-library-{index}"),
            MavenCoordinate::parse(coordinate)?,
            "Forge userdev game-test runtime classpath".to_string(),
        ));
    }
    Ok(())
}

fn should_plan_project_dependency(
    loader_toolchain: &LoaderToolchainPlan,
    dependency: &ParsedDependency,
) -> bool {
    if loader_toolchain.kind != LoaderToolchainKind::NeoGradleUserdev {
        return dependency.fg_deobf;
    }

    if dependency.coordinate.to_string() == loader_toolchain.base_coordinate {
        return false;
    }

    matches!(
        dependency.configuration.as_str(),
        "implementation"
            | "compileOnly"
            | "runtimeOnly"
            | "jarJar"
            | "gametestImplementation"
            | "gametestCompileOnly"
            | "gametestRuntimeOnly"
    )
}

fn add_transitive_runtime_dependency_plans(
    resolver: &Resolver,
    mut dependencies: Vec<DependencyPlan>,
) -> eyre::Result<Vec<DependencyPlan>> {
    let mut seen = dependencies
        .iter()
        .map(|dependency| dependency.resolved_notation.clone())
        .collect::<BTreeSet<_>>();
    let mut queue = dependencies
        .iter()
        .filter(|dependency| is_runtime_transitive_root(&dependency.configuration))
        .filter_map(|dependency| MavenCoordinate::parse(&dependency.resolved_notation).ok())
        .collect::<Vec<_>>();

    while let Some(root) = queue.pop() {
        for coordinate in resolver.resolve_pom_runtime_dependencies(&root) {
            let key = coordinate.to_string();
            if !seen.insert(key) {
                continue;
            }
            let dependency = resolver.resolve_dependency("transitiveRuntime", &coordinate)?;
            queue.push(MavenCoordinate::parse(&dependency.resolved_notation)?);
            dependencies.push(dependency);
        }
    }

    Ok(dependencies)
}

fn is_runtime_transitive_root(configuration: &str) -> bool {
    matches!(
        configuration,
        "implementation" | "runtimeOnly" | "gametestImplementation" | "gametestRuntimeOnly"
    )
}

fn add_project_tool_coordinates(
    coordinates: &mut Vec<(String, MavenCoordinate, String)>,
    dependencies: &[ParsedDependency],
) -> eyre::Result<()> {
    for dependency in dependencies {
        if dependency.configuration == "annotationProcessor" {
            coordinates.push((
                "mixin-annotation-processor".to_string(),
                dependency.coordinate.clone(),
                "Mixin refmap generation".to_string(),
            ));
        } else if dependency.configuration == "antlr" {
            for (index, coordinate) in antlr_classpath_coordinates(&dependency.coordinate.version)?
                .into_iter()
                .enumerate()
            {
                let artifact_id = if index == 0 {
                    "antlr-tool".to_string()
                } else {
                    format!("antlr-tool-dependency-{index}")
                };
                coordinates.push((
                    artifact_id,
                    MavenCoordinate::parse(&coordinate)?,
                    "ANTLR grammar generation".to_string(),
                ));
            }
        }
    }
    Ok(())
}

fn add_mcp_tool_coordinates(
    coordinates: &mut Vec<(String, MavenCoordinate, String)>,
    mcp_config: Option<&McpConfigPlan>,
) -> eyre::Result<()> {
    for (id, function_name, fallback_coordinate, required_for) in [
        (
            "tool-installer-tools-1-2",
            "mergeMappings",
            "net.minecraftforge:installertools:1.2.0:fatjar",
            "MCPConfig MERGE_MAPPING function",
        ),
        (
            "tool-installer-tools-1-3",
            "bundleExtractJar",
            "net.minecraftforge:installertools:1.3.0:fatjar",
            "MCPConfig server bundle extraction",
        ),
        (
            "tool-forgeflower",
            "decompile",
            "net.minecraftforge:forgeflower:1.5.605.9",
            "MCPConfig decompile function",
        ),
        (
            "tool-mergetool-1-1-5",
            "merge",
            "net.minecraftforge:mergetool:1.1.5:fatjar",
            "MCPConfig client/server merge function",
        ),
        (
            "tool-fart",
            "rename",
            "net.minecraftforge:ForgeAutoRenamingTool:0.1.22:all",
            "MCPConfig rename and jar remapping",
        ),
        (
            "tool-diffpatch",
            "patch",
            "net.minecraftforge:DiffPatch:2.0.12:all",
            "MCPConfig and Forge source patch application",
        ),
        (
            "tool-access-transformers",
            "accessTransformers",
            "net.minecraftforge:accesstransformers:8.0.4:fatjar",
            "Forge access transformer application",
        ),
        (
            "tool-specialsource",
            "reobfuscate",
            "net.md-5:SpecialSource:1.11.0:shaded",
            "Forge-style jar reobfuscation",
        ),
    ] {
        coordinates.push((
            id.to_string(),
            MavenCoordinate::parse(&mcp_function_coordinate(
                mcp_config,
                function_name,
                fallback_coordinate,
            ))?,
            required_for.to_string(),
        ));
    }

    Ok(())
}

fn mcp_function_coordinate(
    mcp_config: Option<&McpConfigPlan>,
    function_name: &str,
    fallback_coordinate: &str,
) -> String {
    mcp_config
        .and_then(|config| config.function_coordinates.get(function_name))
        .cloned()
        .unwrap_or_else(|| fallback_coordinate.to_string())
}

fn parchment_coordinate(mapping_version: &str) -> eyre::Result<MavenCoordinate> {
    let parts = mapping_version.split('-').collect::<Vec<_>>();
    let (mc_version, date) = match parts.as_slice() {
        [date, mc_version] if looks_like_parchment_date(date) => (*mc_version, *date),
        [mc_version, date] if looks_like_parchment_date(date) => (*mc_version, *date),
        [mc_version, date, _target_version] if looks_like_parchment_date(date) => {
            (*mc_version, *date)
        }
        _ => {
            eyre::bail!("Unsupported parchment mapping_version: {mapping_version}");
        }
    };
    MavenCoordinate::parse(&format!(
        "org.parchmentmc.data:parchment-{mc_version}:{date}@zip"
    ))
}

fn looks_like_parchment_date(value: &str) -> bool {
    let mut parts = value.split('.');
    let Some(year) = parts.next() else {
        return false;
    };
    year.len() == 4
        && year.chars().all(|character| character.is_ascii_digit())
        && parts.all(|part| {
            !part.is_empty() && part.chars().all(|character| character.is_ascii_digit())
        })
}

fn resolve_mapping_settings(
    properties: &BTreeMap<String, String>,
    minecraft_version: &str,
) -> (String, String) {
    if let (Some(channel), Some(version)) = (
        properties.get("mapping_channel"),
        properties.get("mapping_version"),
    ) {
        return (channel.clone(), version.clone());
    }

    if let (Some(parchment_minecraft), Some(parchment_version)) = (
        properties.get("neogradle.subsystems.parchment.minecraftVersion"),
        properties.get("neogradle.subsystems.parchment.mappingsVersion"),
    ) {
        return (
            "parchment".to_string(),
            format!("{parchment_version}-{parchment_minecraft}"),
        );
    }

    ("official".to_string(), minecraft_version.to_string())
}

fn resolve_loader_toolchain(
    dependencies: &[ParsedDependency],
    minecraft_version: &str,
    loader_version: &str,
) -> eyre::Result<LoaderToolchainPlan> {
    if let Some(dependency) = dependencies
        .iter()
        .find(|dependency| dependency.configuration == "minecraft")
    {
        let coordinate = dependency.coordinate.clone();
        let kind = if coordinate.group == "net.minecraftforge" && coordinate.artifact == "forge" {
            LoaderToolchainKind::ForgeGradleForge
        } else if coordinate.group == "net.neoforged" && coordinate.artifact == "forge" {
            LoaderToolchainKind::ForgeGradleNeoForgeGroup
        } else {
            eyre::bail!(
                "Unsupported ForgeGradle minecraft dependency: {}",
                coordinate
            );
        };

        return Ok(loader_toolchain_plan(kind, &coordinate));
    }

    if let Some(dependency) = dependencies.iter().find(|dependency| {
        dependency.coordinate.group == "net.neoforged"
            && dependency.coordinate.artifact == "neoforge"
    }) {
        return Ok(loader_toolchain_plan(
            LoaderToolchainKind::NeoGradleUserdev,
            &dependency.coordinate,
        ));
    }

    let fallback = MavenCoordinate::parse(&format!(
        "net.minecraftforge:forge:{minecraft_version}-{loader_version}"
    ))?;
    Ok(loader_toolchain_plan(
        LoaderToolchainKind::ForgeGradleForge,
        &fallback,
    ))
}

fn loader_toolchain_plan(
    kind: LoaderToolchainKind,
    base_coordinate: &MavenCoordinate,
) -> LoaderToolchainPlan {
    let userdev_coordinate = base_coordinate.with_classifier("userdev");
    let sources_coordinate = base_coordinate.with_classifier("sources");
    let universal_coordinate = base_coordinate.with_classifier("universal");

    LoaderToolchainPlan {
        kind,
        base_coordinate: base_coordinate.to_string(),
        userdev_coordinate: userdev_coordinate.to_string(),
        sources_coordinate: Some(sources_coordinate.to_string()),
        universal_coordinate: Some(universal_coordinate.to_string()),
    }
}

fn resolve_minecraft_plan(
    cache_dir: &Path,
    client: &Client,
    minecraft_version: &str,
) -> eyre::Result<MinecraftPlan> {
    let minecraft_cache = cache_dir.join("minecraft");
    fs::create_dir_all(&minecraft_cache)?;
    let manifest_path = minecraft_cache.join("version_manifest_v2.json");
    download_to_path(client, VERSION_MANIFEST_URL, &manifest_path)?;
    let manifest: MojangVersionManifest = read_json_file(&manifest_path)?;
    let version_url = manifest
        .versions
        .iter()
        .find_map(|version| (version.id == minecraft_version).then_some(version.url.as_str()))
        .ok_or_else(|| {
            eyre::eyre!("Minecraft version {minecraft_version} not found in Mojang manifest")
        })?;

    let version_json_path = minecraft_cache.join(format!("{minecraft_version}.json"));
    download_to_path(client, version_url, &version_json_path)?;
    let version_json: MinecraftVersionJson = read_json_file(&version_json_path)?;
    let libraries_count = version_json.libraries.len();

    Ok(MinecraftPlan {
        version_manifest: plain_artifact(
            "minecraft-version-manifest",
            VERSION_MANIFEST_URL,
            manifest_path,
            "Minecraft version discovery",
        )?,
        version_json: plain_artifact(
            "minecraft-version-json",
            version_url,
            version_json_path,
            "Minecraft libraries and downloads",
        )?,
        client_jar_url: version_json.downloads.client.url,
        server_jar_url: version_json.downloads.server.url,
        client_mappings_url: version_json
            .downloads
            .client_mappings
            .map(|download| download.url),
        server_mappings_url: version_json
            .downloads
            .server_mappings
            .map(|download| download.url),
        libraries_count,
    })
}

fn read_forge_userdev(artifact: &ArtifactPlan) -> eyre::Result<ForgeUserdevPlan> {
    let config: ForgeUserdevConfig = read_zip_json_entry(&artifact.cache_path, "config.json")?;
    let binpatcher = config
        .binpatcher
        .as_ref()
        .and_then(|binpatcher| binpatcher.version.clone());
    let module_count = config.modules.len();
    let library_count = config.libraries.len();
    let test_libraries = config.test_libraries;
    let run_configs = config.runs.keys().cloned().collect();

    Ok(ForgeUserdevPlan {
        artifact: ArtifactPlan {
            id: artifact.id.clone(),
            coordinate: artifact.coordinate.clone(),
            repository: artifact.repository.clone(),
            url: artifact.url.clone(),
            cache_path: artifact.cache_path.clone(),
            sha1: artifact.sha1.clone(),
            downloaded: artifact.downloaded,
            required_for: artifact.required_for.clone(),
            provenance: artifact.provenance.clone(),
        },
        spec: config.spec,
        mcp: config.mcp,
        neo_form: config.neo_form,
        sources: config.sources,
        universal: config.universal,
        binpatcher,
        patches: config.patches,
        patches_original_prefix: config.patches_original_prefix,
        patches_modified_prefix: config.patches_modified_prefix,
        access_transformers: config.ats.map_or_else(Vec::new, StringList::into_vec),
        side_strippers: config.sass.map_or_else(Vec::new, StringList::into_vec),
        module_count,
        library_count,
        test_libraries,
        run_configs,
    })
}

fn read_mcp_config(artifact: &ArtifactPlan) -> eyre::Result<McpConfigPlan> {
    let config: McpConfigJson = read_zip_json_entry(&artifact.cache_path, "config.json")?;
    let joined_steps = config
        .steps
        .joined
        .iter()
        .filter_map(|step| step.name.as_ref().or(step.step_type.as_ref()).cloned())
        .collect();
    let mut data_keys = Vec::new();
    if config.data.inject.is_some() {
        data_keys.push("inject".to_string());
    }
    if config.data.mappings.is_some() {
        data_keys.push("mappings".to_string());
    }
    if config
        .data
        .patches
        .as_ref()
        .is_some_and(McpPatchData::has_any_patch_root)
    {
        data_keys.push("patches".to_string());
    }
    let function_count = config
        .functions
        .values()
        .filter(|function| function.has_declared_config())
        .count();
    let function_coordinates = config
        .functions
        .iter()
        .filter_map(|(name, function)| {
            function
                .version
                .as_ref()
                .map(|version| (name.clone(), version.clone()))
        })
        .collect();

    Ok(McpConfigPlan {
        artifact: ArtifactPlan {
            id: artifact.id.clone(),
            coordinate: artifact.coordinate.clone(),
            repository: artifact.repository.clone(),
            url: artifact.url.clone(),
            cache_path: artifact.cache_path.clone(),
            sha1: artifact.sha1.clone(),
            downloaded: artifact.downloaded,
            required_for: artifact.required_for.clone(),
            provenance: artifact.provenance.clone(),
        },
        joined_steps,
        function_coordinates,
        function_count,
        data_keys,
        library_count: config.libraries.values().map(Vec::len).sum(),
    })
}

#[derive(Clone, Debug)]
struct ParsedDependency {
    configuration: String,
    coordinate: MavenCoordinate,
    fg_deobf: bool,
}

fn parse_dependency_script(
    path: &Path,
    properties: &BTreeMap<String, String>,
) -> eyre::Result<Vec<ParsedDependency>> {
    let content = fs::read_to_string(path)
        .wrap_err_with(|| format!("Failed to read dependency script: {}", path.display()))?;
    let mut dependencies = Vec::new();

    for raw_line in content.lines() {
        let line = raw_line
            .split("//")
            .next()
            .unwrap_or_default()
            .trim()
            .trim_end_matches(';')
            .trim();
        if line.is_empty() || line == "dependencies {" || line == "}" {
            continue;
        }

        if line.starts_with("jarJar(") {
            let Some(notation) = extract_quoted(line) else {
                continue;
            };
            let notation = interpolate_properties(&notation, properties);
            dependencies.push(ParsedDependency {
                configuration: "jarJar".to_string(),
                coordinate: MavenCoordinate::parse(&notation)?,
                fg_deobf: false,
            });
            continue;
        }

        let Some((configuration, rest)) = line.split_once(char::is_whitespace) else {
            continue;
        };
        if !is_dependency_configuration(configuration) {
            continue;
        }
        let rest = rest.trim();
        let fg_deobf = rest.contains("fg.deobf");
        let Some(notation) = extract_quoted(rest) else {
            continue;
        };
        let notation = interpolate_properties(&notation, properties);
        dependencies.push(ParsedDependency {
            configuration: configuration.to_string(),
            coordinate: MavenCoordinate::parse(&notation)?,
            fg_deobf,
        });
    }

    Ok(dependencies)
}

fn parse_maven_pom_runtime_dependencies(
    pom: &str,
    parent: &MavenCoordinate,
) -> Vec<MavenCoordinate> {
    let properties = parse_maven_pom_properties(pom, parent);
    let search_start = pom
        .find("</dependencyManagement>")
        .map_or(0, |index| index + "</dependencyManagement>".len());
    let Some(dependencies_xml) = extract_xml_section(&pom[search_start..], "dependencies") else {
        return Vec::new();
    };

    let mut coordinates = Vec::new();
    for block in dependencies_xml.split("<dependency").skip(1) {
        let Some(start) = block.find('>') else {
            continue;
        };
        let Some(end) = block[start + 1..].find("</dependency>") else {
            continue;
        };
        let dependency_xml = &block[start + 1..start + 1 + end];
        let scope = extract_xml_tag_text(dependency_xml, "scope").unwrap_or_default();
        if !scope.is_empty() && scope != "compile" && scope != "runtime" {
            continue;
        }
        if extract_xml_tag_text(dependency_xml, "optional")
            .is_some_and(|optional| optional.eq_ignore_ascii_case("true"))
        {
            continue;
        }
        let dependency_type =
            extract_xml_tag_text(dependency_xml, "type").unwrap_or_else(|| "jar".to_string());
        if dependency_type != "jar" {
            continue;
        }

        let Some(group) = extract_resolved_pom_tag(dependency_xml, "groupId", &properties) else {
            continue;
        };
        let Some(artifact) = extract_resolved_pom_tag(dependency_xml, "artifactId", &properties)
        else {
            continue;
        };
        let Some(version) = extract_resolved_pom_tag(dependency_xml, "version", &properties) else {
            continue;
        };
        if version.contains('[') || version.contains('(') {
            continue;
        }
        let classifier = extract_resolved_pom_tag(dependency_xml, "classifier", &properties);
        coordinates.push(MavenCoordinate {
            group,
            artifact,
            version,
            classifier,
            extension: "jar".to_string(),
        });
    }

    coordinates
}

fn parse_maven_pom_properties(pom: &str, parent: &MavenCoordinate) -> BTreeMap<String, String> {
    let mut properties = BTreeMap::from([
        ("project.groupId".to_string(), parent.group.clone()),
        ("pom.groupId".to_string(), parent.group.clone()),
        ("project.artifactId".to_string(), parent.artifact.clone()),
        ("pom.artifactId".to_string(), parent.artifact.clone()),
        ("project.version".to_string(), parent.version.clone()),
        ("pom.version".to_string(), parent.version.clone()),
        ("version".to_string(), parent.version.clone()),
    ]);
    if let Some(properties_xml) = extract_xml_section(pom, "properties") {
        for block in properties_xml.split('<').skip(1) {
            let Some((tag, rest)) = block.split_once('>') else {
                continue;
            };
            if tag.starts_with('/') || tag.contains(char::is_whitespace) {
                continue;
            }
            let end_tag = format!("</{tag}>");
            let Some(end) = rest.find(&end_tag) else {
                continue;
            };
            properties.insert(tag.to_string(), strip_xml_cdata(rest[..end].trim()));
        }
    }
    properties
}

fn extract_resolved_pom_tag(
    input: &str,
    tag: &str,
    properties: &BTreeMap<String, String>,
) -> Option<String> {
    let value = extract_xml_tag_text(input, tag)?;
    let mut resolved = value;
    for _ in 0..8 {
        let Some(start) = resolved.find("${") else {
            return Some(resolved);
        };
        let property_start = start + "${".len();
        let end = resolved[property_start..].find('}')? + property_start;
        let property_name = &resolved[property_start..end];
        let replacement = properties.get(property_name)?;
        resolved.replace_range(start..=end, replacement);
    }
    None
}

fn extract_xml_section(input: &str, tag: &str) -> Option<String> {
    let start_tag = format!("<{tag}>");
    let end_tag = format!("</{tag}>");
    let start = input.find(&start_tag)? + start_tag.len();
    let end = input[start..].find(&end_tag)? + start;
    Some(input[start..end].to_string())
}

fn extract_xml_tag_text(input: &str, tag: &str) -> Option<String> {
    let start_tag = format!("<{tag}>");
    let end_tag = format!("</{tag}>");
    let start = input.find(&start_tag)? + start_tag.len();
    let end = input[start..].find(&end_tag)? + start;
    Some(strip_xml_cdata(input[start..end].trim()))
}

fn strip_xml_cdata(input: &str) -> String {
    input
        .strip_prefix("<![CDATA[")
        .and_then(|value| value.strip_suffix("]]>"))
        .unwrap_or(input)
        .to_string()
}

fn is_dependency_configuration(configuration: &str) -> bool {
    matches!(
        configuration,
        "minecraft"
            | "annotationProcessor"
            | "antlr"
            | "implementation"
            | "compileOnly"
            | "runtimeOnly"
            | "gametestImplementation"
            | "gametestCompileOnly"
            | "gametestRuntimeOnly"
            | "testImplementation"
            | "testCompileOnly"
            | "testRuntimeOnly"
            | "testAnnotationProcessor"
    )
}

fn extract_quoted(input: &str) -> Option<String> {
    let mut chars = input.char_indices();
    let (start_index, quote) =
        chars.find(|(_, character)| *character == '"' || *character == '\'')?;
    let value_start = start_index + quote.len_utf8();
    let end_offset = input[value_start..].find(quote)?;
    Some(input[value_start..value_start + end_offset].to_string())
}

fn interpolate_properties(input: &str, properties: &BTreeMap<String, String>) -> String {
    let mut output = input.to_string();
    let mut aliases = properties.clone();
    if let Some(minecraft_version) = properties.get("minecraft_version") {
        aliases.insert("mc_version".to_string(), minecraft_version.clone());
    }

    for (key, value) in aliases {
        output = output.replace(&format!("${{{key}}}"), &value);
    }

    output
}

fn build_graph(
    minecraft_version: &str,
    rust_output_jar: &Path,
    dependencies: &[DependencyPlan],
    loader_toolchain: &LoaderToolchainPlan,
) -> Vec<GraphNode> {
    let mut graph = vec![
        graph_ready(
            "resolve-project-config",
            vec!["gradle.properties", "versioned Gradle fragments"],
            vec!["build/sfm-toolchain/state/last-plan.json"],
        ),
        graph_ready(
            "resolve-maven-and-minecraft-inputs",
            vec![
                "Maven repositories",
                VERSION_MANIFEST_URL,
                "dependencies.gradle",
            ],
            vec!["build/sfm-toolchain/maven", "build/sfm-toolchain/minecraft"],
        ),
    ];

    if loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
        graph.push(graph_planned(
            "execute-neoform-userdev",
            "NeoForm userdev inputs are detected; execution support still needs implementation",
            vec![&loader_toolchain.userdev_coordinate],
            vec!["build/sfm-toolchain/neoform"],
        ));
    } else {
        graph.push(graph_planned(
            "execute-mcp-config-joined",
            "MCPConfig joined runtime inputs will be fingerprinted before execution",
            vec![
                &format!("Minecraft {minecraft_version} client/server jars"),
                "MCPConfig config.json",
            ],
            vec!["build/sfm-toolchain/mcp/joined"],
        ));
        graph.push(graph_planned(
            "execute-forge-userdev",
            "Forge userdev inputs will be fingerprinted before execution",
            vec![
                &loader_toolchain.userdev_coordinate,
                "MCPConfig joined outputs",
            ],
            vec!["build/sfm-toolchain/forge"],
        ));
    }

    graph.extend([
        graph_planned(
            "deobfuscate-mod-dependencies",
            "fg.deobf dependency jars will be remapped into SFM-owned cache",
            vec![&format!(
                "{} active fg.deobf dependencies",
                dependencies.len()
            )],
            vec!["build/sfm-toolchain/dependencies"],
        ),
        graph_planned(
            "compile-project",
            "Project sources, resources, classpath, and processors will be fingerprinted before javac",
            vec![
                "src/main/java",
                "src/main/antlr",
                "src/main/resources",
                "mapped Forge/Minecraft jar",
            ],
            vec!["build/sfm-toolchain/project/classes"],
        ),
        graph_planned(
            "package-and-reobfuscate-jar",
            "Development jar and reobfuscation mappings will be fingerprinted before packaging",
            vec!["compiled classes", "expanded resources", "MCP mappings"],
            vec![&rust_output_jar.display().to_string()],
        ),
    ]);

    graph
}

fn graph_ready(id: &str, inputs: Vec<&str>, outputs: Vec<&str>) -> GraphNode {
    GraphNode {
        id: id.to_string(),
        kind: "planning".to_string(),
        status: NodeStatus::Ready,
        inputs: inputs.into_iter().map(str::to_string).collect(),
        outputs: outputs.into_iter().map(str::to_string).collect(),
        rebuild_reason: "Planner inputs were read during this invocation".to_string(),
    }
}

fn graph_planned(id: &str, reason: &str, inputs: Vec<&str>, outputs: Vec<&str>) -> GraphNode {
    GraphNode {
        id: id.to_string(),
        kind: "execution".to_string(),
        status: NodeStatus::Planned,
        inputs: inputs.into_iter().map(str::to_string).collect(),
        outputs: outputs.into_iter().map(str::to_string).collect(),
        rebuild_reason: reason.to_string(),
    }
}

fn ensure_forge_gradle_execution_supported(plan: &BuildPlan) -> eyre::Result<()> {
    if plan.forge_userdev.is_none() || plan.mcp_config.is_none() {
        eyre::bail!(
            "Minecraft {} did not resolve the ForgeGradle userdev plus MCPConfig inputs required by the current executor.",
            plan.minecraft_version
        );
    }

    Ok(())
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum BuildTarget {
    Jar,
    Run,
}

#[expect(
    clippy::too_many_lines,
    reason = "build orchestration keeps the node order and timing output visible."
)]
fn execute_build(plan: &BuildPlan, explain_rebuild: bool, target: BuildTarget) -> eyre::Result<()> {
    let _span = tracing::info_span!(
        "execute_rust_owned_build",
        mc = %plan.minecraft_version,
        loader = ?plan.loader_toolchain.kind,
        graph_nodes = plan.graph.len(),
        explain_rebuild,
        target = ?target,
    )
    .entered();
    let context = ExecutionContext::new(plan)?;
    let total_started = Instant::now();

    if explain_rebuild {
        for node in &plan.graph {
            println!("{}: {}", node.id, node.rebuild_reason);
        }
    }

    println!("Build node resolve-project-config: recording plan state");
    context.write_node_state(
        "resolve-project-config",
        &["gradle.properties", "versioned Gradle fragments"],
        &[plan.state_dir.join("last-plan.json")],
        "complete",
    )?;
    println!("Build node resolve-maven-and-minecraft-inputs: recording resolved artifacts");
    context.write_node_state(
        "resolve-maven-and-minecraft-inputs",
        &[
            "Maven repositories",
            VERSION_MANIFEST_URL,
            "dependencies.gradle",
        ],
        &[
            plan.maven_cache_dir.clone(),
            plan.cache_dir.join("minecraft"),
        ],
        "complete",
    )?;

    if plan.loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
        let started = Instant::now();
        println!("Build node execute-neoform-userdev: start");
        execute_neoform_userdev(&context)?;
        println!(
            "Build node execute-neoform-userdev: done in {} ms",
            started.elapsed().as_millis()
        );
    } else {
        ensure_forge_gradle_execution_supported(plan)?;
        let started = Instant::now();
        println!("Build node execute-mcp-config-joined: start");
        execute_mcp_config_joined(&context)?;
        println!(
            "Build node execute-mcp-config-joined: done in {} ms",
            started.elapsed().as_millis()
        );

        let started = Instant::now();
        println!("Build node execute-forge-userdev: start");
        execute_forge_userdev(&context)?;
        println!(
            "Build node execute-forge-userdev: done in {} ms",
            started.elapsed().as_millis()
        );
    }
    let started = Instant::now();
    println!("Build node deobfuscate-mod-dependencies: start");
    execute_dependency_deobf(&context)?;
    println!(
        "Build node deobfuscate-mod-dependencies: done in {} ms",
        started.elapsed().as_millis()
    );
    let started = Instant::now();
    println!("Build node compile-project: start");
    execute_project_compile(&context)?;
    println!(
        "Build node compile-project: done in {} ms",
        started.elapsed().as_millis()
    );
    if target == BuildTarget::Jar {
        let started = Instant::now();
        println!("Build node package-and-reobfuscate-jar: start");
        execute_package_and_reobfuscate(&context)?;
        println!(
            "Build node package-and-reobfuscate-jar: done in {} ms",
            started.elapsed().as_millis()
        );

        if !plan.rust_output_jar.is_file() {
            eyre::bail!(
                "Build finished without producing Rust output jar: {}",
                plan.rust_output_jar.display()
            );
        }

        println!(
            "Rust jar build completed in {} ms",
            total_started.elapsed().as_millis()
        );
    } else {
        println!(
            "Rust run build outputs prepared in {} ms",
            total_started.elapsed().as_millis()
        );
    }

    Ok(())
}

impl RunKind {
    const fn userdev_name(self) -> &'static str {
        match self {
            Self::Client | Self::ClientSmoke | Self::ClientPuppet => "client",
            Self::Server => "server",
            Self::Data => "data",
            Self::GameTestServer => "gameTestServer",
        }
    }

    const fn command_name(self) -> &'static str {
        match self {
            Self::Client => "runClient",
            Self::ClientSmoke => "runClientSmoke",
            Self::ClientPuppet => "runClientPuppet",
            Self::Server => "runServer",
            Self::Data => "runData",
            Self::GameTestServer => "runGameTestServer",
        }
    }

    const fn working_dir_name(self) -> &'static str {
        match self {
            Self::Client => "run",
            Self::ClientSmoke => "runClientSmoke",
            Self::ClientPuppet => "runClientPuppet",
            Self::Server => "runServer",
            Self::Data => "runData",
            Self::GameTestServer => "runGameTest",
        }
    }

    const fn optional_source_set(self) -> &'static str {
        match self {
            Self::Client
            | Self::ClientSmoke
            | Self::ClientPuppet
            | Self::Server
            | Self::GameTestServer => "gametest",
            Self::Data => "datagen",
        }
    }

    const fn enables_game_tests(self) -> bool {
        matches!(
            self,
            Self::Client
                | Self::ClientSmoke
                | Self::ClientPuppet
                | Self::Server
                | Self::GameTestServer
        )
    }

    const fn automation_mode(self) -> Option<&'static str> {
        match self {
            Self::ClientSmoke => Some("smoke"),
            Self::ClientPuppet => Some("puppet"),
            _ => None,
        }
    }

    const fn launch_timeout(self) -> Option<Duration> {
        match self {
            Self::ClientSmoke => Some(Duration::from_mins(2)),
            Self::ClientPuppet => Some(Duration::from_mins(15)),
            _ => None,
        }
    }
}

#[derive(Debug, Clone, Default, Facet)]
#[facet(rename_all = "camelCase")]
struct ForgeRunConfig {
    #[facet(default)]
    main: String,
    #[facet(default)]
    args: Vec<String>,
    #[facet(default)]
    jvm_args: Vec<String>,
    #[facet(default)]
    env: BTreeMap<String, String>,
    #[facet(default)]
    props: BTreeMap<String, String>,
}

#[derive(Debug)]
struct MinecraftAssets {
    root: PathBuf,
    index_id: String,
}

#[derive(Debug)]
struct RunClasspath {
    legacy: Vec<PathBuf>,
    userdev_mods: Vec<PathBuf>,
}

#[derive(Debug)]
struct LaunchOutput {
    status: ExitStatus,
    combined: String,
    timed_out: bool,
}

#[expect(
    clippy::too_many_lines,
    reason = "Run launch orchestration intentionally mirrors Forge userdev config shape."
)]
fn execute_run(plan: &BuildPlan, kind: RunKind, dry_run: bool) -> eyre::Result<()> {
    let _span = tracing::info_span!(
        "execute_run_setup",
        mc = %plan.minecraft_version,
        kind = kind.command_name(),
        loader = ?plan.loader_toolchain.kind,
        dry_run,
    )
    .entered();
    let context = ExecutionContext::new(plan)?;
    let run_config = read_forge_run_config(&context, kind)?;
    if run_config.main.is_empty() {
        eyre::bail!(
            "Forge userdev run config {} did not declare a main class",
            kind.userdev_name()
        );
    }
    let launch_main = run_config.main.clone();

    let run_state_dir = plan.cache_dir.join("run").join(kind.command_name());
    fs::create_dir_all(&run_state_dir)?;
    let working_dir = plan.minecraft_dir.join(kind.working_dir_name());
    fs::create_dir_all(&working_dir)?;
    if matches!(kind, RunKind::GameTestServer) {
        clean_gametest_server_world(&plan.minecraft_dir, &working_dir)?;
    }
    if matches!(kind, RunKind::ClientPuppet) {
        clean_client_puppet_world(&plan.minecraft_dir, &working_dir)?;
    }
    let automation_options_path =
        prepare_client_automation_options(&plan.minecraft_dir, &working_dir, kind)?;

    let resolver = Resolver::new(
        plan.maven_cache_dir.clone(),
        plan.repositories.clone(),
        plan.refresh,
        plan.allow_local_artifact_cache,
        plan.lockfile.clone(),
    )?;
    let modules = resolve_forge_userdev_modules(&context, &resolver)?;
    let launch_classpath = resolve_run_classpath(&context, &resolver, kind)?;
    let minecraft_classpath_file = run_state_dir.join("minecraftClasspath.txt");
    write_classpath_file(&minecraft_classpath_file, &launch_classpath.legacy)?;

    let assets = if run_config_requires_assets(&run_config) {
        Some(prepare_minecraft_assets(&context)?)
    } else {
        None
    };

    let source_roots = run_source_roots(&context, kind)?;
    let mcp_mappings = run_mcp_mappings(plan);
    let module_path = join_classpath(&modules);
    let minecraft_classpath_file_text = minecraft_classpath_file.display().to_string();
    let assets_root = assets
        .as_ref()
        .map_or_else(String::new, |assets| assets.root.display().to_string());
    let asset_index = assets
        .as_ref()
        .map_or_else(String::new, |assets| assets.index_id.clone());
    let replacements = [
        ("{modules}", module_path.as_str()),
        ("{source_roots}", source_roots.as_str()),
        ("{mcp_mappings}", mcp_mappings.as_str()),
        (
            "{minecraft_classpath_file}",
            minecraft_classpath_file_text.as_str(),
        ),
        ("{asset_index}", asset_index.as_str()),
        ("{assets_root}", assets_root.as_str()),
    ];

    let mut properties = run_config.props.clone();
    properties.insert(
        "forge.logging.markers".to_string(),
        "REGISTRIES".to_string(),
    );
    properties.insert(
        "forge.logging.console.level".to_string(),
        "info".to_string(),
    );
    properties.insert("mixin.env.remapRefMap".to_string(), "true".to_string());
    if plan.loader_toolchain.kind != LoaderToolchainKind::NeoGradleUserdev {
        let refmap_remapping_file = ensure_run_refmap_remapping_file(&context)?;
        properties.insert(
            "mixin.env.refMapRemappingFile".to_string(),
            refmap_remapping_file.display().to_string(),
        );
        properties.insert(
            "net.minecraftforge.gradle.GradleStart.srg.srg-mcp".to_string(),
            refmap_remapping_file.display().to_string(),
        );
    }
    if kind.enables_game_tests() {
        let game_test_property = game_test_namespace_property(&context)?;
        properties.insert(
            game_test_property,
            required_property(&plan.properties, "mod_id")?.to_string(),
        );
    }
    if matches!(kind, RunKind::GameTestServer) {
        let log4j_config = write_gametest_log4j_config(&run_state_dir)?;
        properties.insert(
            "sfm.gametest.maxProgramRunMillis".to_string(),
            "150".to_string(),
        );
        properties.insert(
            "log4j2.configurationFile".to_string(),
            log4j_config.display().to_string(),
        );
    }
    if let Some(automation_mode) = kind.automation_mode() {
        properties.insert(
            "sfm.clientRun.mode".to_string(),
            automation_mode.to_string(),
        );
        properties.insert(
            "sfm.clientRun.keepOpenSeconds".to_string(),
            "10".to_string(),
        );
    }

    let mut jvm_args = properties
        .into_iter()
        .map(|(key, value)| format!("-D{key}={}", replace_placeholders(&value, &replacements)))
        .collect::<Vec<_>>();
    jvm_args.extend(
        run_config
            .jvm_args
            .iter()
            .map(|arg| replace_placeholders(arg, &replacements)),
    );
    jvm_args.extend([
        "-XX:+IgnoreUnrecognizedVMOptions".to_string(),
        "-XX:+AllowRedefinitionToAddDeleteMethods".to_string(),
    ]);

    let mut program_args = run_config
        .args
        .iter()
        .map(|arg| replace_placeholders(arg, &replacements))
        .collect::<Vec<_>>();
    program_args.extend(kind_extra_program_args(plan, kind)?);
    program_args.extend(["--mixin.config".to_string(), "sfm.mixins.json".to_string()]);

    let mut env = BTreeMap::new();
    for (key, value) in &run_config.env {
        env.insert(key.clone(), replace_placeholders(value, &replacements));
    }
    env.insert("MOD_CLASSES".to_string(), source_roots);
    env.insert("MCP_MAPPINGS".to_string(), mcp_mappings);

    let mut java_classpath_inputs = launch_classpath
        .legacy
        .iter()
        .cloned()
        .chain(launch_classpath.userdev_mods.iter().cloned())
        .chain(modules.iter().cloned())
        .collect::<Vec<_>>();
    if launch_main.starts_with("net.neoforged.fml.startup.") {
        java_classpath_inputs.extend(run_source_root_paths(&context, kind)?);
    }
    let java_classpath = dedup_paths_preserve_order(java_classpath_inputs);
    let mut java_args = Vec::new();
    java_args.extend(jvm_args);
    java_args.extend(["-cp".to_string(), join_classpath(&java_classpath)]);
    java_args.push(launch_main);
    java_args.extend(program_args);

    let argfile = run_state_dir.join("launch.java.args");
    fs::write(
        &argfile,
        java_args
            .into_iter()
            .map(escape_argfile_arg)
            .collect::<Vec<_>>()
            .join("\n"),
    )
    .wrap_err_with(|| format!("Failed to write {}", argfile.display()))?;

    println!(
        "Launching {} from {}",
        kind.command_name(),
        working_dir.display()
    );
    println!("Launch args: {}", argfile.display());
    println!(
        "MOD_CLASSES={}",
        env.get("MOD_CLASSES").map_or("", String::as_str)
    );
    println!(
        "Userdev mod jars on launch classpath: {}",
        launch_classpath.userdev_mods.len()
    );
    tracing::info!(
        kind = kind.command_name(),
        argfile = %argfile.display(),
        legacy_classpath_entries = launch_classpath.legacy.len(),
        userdev_mods = launch_classpath.userdev_mods.len(),
        "run_setup_complete"
    );

    let launch_log = run_state_dir.join("console.log");
    if dry_run {
        let mut run_outputs = vec![argfile.clone(), minecraft_classpath_file.clone()];
        if let Some(path) = automation_options_path {
            run_outputs.push(path);
        }
        context.write_node_state(
            &format!("run-{}", kind.userdev_name()),
            &["Forge userdev run config", "Rust-owned build outputs"],
            &run_outputs,
            "dry-run",
        )?;
        println!(
            "Dry run prepared {} launch setup and skipped Minecraft JVM launch.",
            kind.command_name()
        );
        tracing::info!(
            kind = kind.command_name(),
            argfile = %argfile.display(),
            "run_dry_run_skip_launch"
        );
        return Ok(());
    }
    let echo_launch_output = !matches!(kind, RunKind::GameTestServer);
    let max_launch_attempts = if matches!(kind, RunKind::GameTestServer) {
        3
    } else {
        1
    };
    let mut launch_output = None;
    for attempt in 1..=max_launch_attempts {
        if matches!(kind, RunKind::GameTestServer) {
            clean_gametest_server_world(&plan.minecraft_dir, &working_dir)?;
            println!("Game-test server attempt {attempt}/{max_launch_attempts}");
        }
        if matches!(kind, RunKind::ClientPuppet) {
            clean_client_puppet_world(&plan.minecraft_dir, &working_dir)?;
        }
        let attempt_output = run_launch_command(
            plan,
            &argfile,
            &working_dir,
            &env,
            &launch_log,
            echo_launch_output,
            kind.launch_timeout(),
        )
        .wrap_err_with(|| {
            format!(
                "Failed to launch {} using {}",
                kind.command_name(),
                plan.java.executable.display()
            )
        })?;
        if attempt_output.status.success() || attempt == max_launch_attempts {
            launch_output = Some(attempt_output);
            break;
        }
        println!(
            "{} attempt {attempt}/{max_launch_attempts} exited with {}; retrying. See {}",
            kind.command_name(),
            attempt_output.status,
            launch_log.display()
        );
    }
    let launch_output = launch_output.ok_or_else(|| {
        eyre::eyre!(
            "Failed to launch {} using {}",
            kind.command_name(),
            plan.java.executable.display()
        )
    })?;

    let mut run_outputs = vec![
        argfile.clone(),
        minecraft_classpath_file.clone(),
        launch_log.clone(),
    ];
    if let Some(path) = automation_options_path {
        run_outputs.push(path);
    }
    context.write_node_state(
        &format!("run-{}", kind.userdev_name()),
        &["Forge userdev run config", "Rust-owned build outputs"],
        &run_outputs,
        if launch_output.status.success() {
            "complete"
        } else {
            "failed"
        },
    )?;

    if launch_output.timed_out {
        eyre::bail!(
            "{} timed out after {} seconds. See {}",
            kind.command_name(),
            kind.launch_timeout().map_or(0, |timeout| timeout.as_secs()),
            launch_log.display()
        );
    }
    if !launch_output.status.success() {
        eyre::bail!(
            "{} exited with {}. See {}",
            kind.command_name(),
            launch_output.status,
            launch_log.display()
        );
    }
    if matches!(kind, RunKind::GameTestServer) {
        let Some(pass_count) = extract_required_gametest_pass_count(&launch_output.combined) else {
            let running_count = extract_running_gametest_count(&launch_output.combined)
                .map_or_else(|| "unknown".to_string(), |count| count.to_string());
            eyre::bail!(
                "{} exited successfully but did not report a required game-test pass count (running count: {}). See {}",
                kind.command_name(),
                running_count,
                launch_log.display()
            );
        };
        if pass_count == 0 {
            eyre::bail!(
                "{} reported 0 required game tests passed. See {}",
                kind.command_name(),
                launch_log.display()
            );
        }
        println!("Validated {pass_count} required game tests passed.");
    }
    if matches!(kind, RunKind::ClientSmoke) {
        if !launch_output.combined.contains("SFM_CLIENT_SMOKE_READY") {
            eyre::bail!(
                "{} exited successfully but did not report title-screen readiness. See {}",
                kind.command_name(),
                launch_log.display()
            );
        }
        println!("Validated client reached the title screen.");
    }
    if matches!(kind, RunKind::ClientPuppet) {
        let Some(pass_count) = extract_client_puppet_pass_count(&launch_output.combined) else {
            eyre::bail!(
                "{} exited successfully but did not report a client puppet pass count. See {}",
                kind.command_name(),
                launch_log.display()
            );
        };
        if pass_count == 0 {
            eyre::bail!(
                "{} reported 0 required game tests passed. See {}",
                kind.command_name(),
                launch_log.display()
            );
        }
        println!("Validated client puppet completed {pass_count} required game tests.");
    }
    Ok(())
}

fn run_mcp_mappings(plan: &BuildPlan) -> String {
    if let (Some(channel), Some(version)) = (
        plan.properties.get("mapping_channel"),
        plan.properties.get("mapping_version"),
    ) {
        return format!("{channel}_{version}");
    }
    format!("official_{}", plan.minecraft_version)
}

fn game_test_namespace_property(context: &ExecutionContext<'_>) -> eyre::Result<String> {
    let run_config = context
        .plan
        .minecraft_dir
        .join("gradle")
        .join("run-configurations")
        .join(&context.plan.minecraft_version)
        .join("run-configurations.gradle");
    if run_config.is_file() {
        let text = fs::read_to_string(&run_config)
            .wrap_err_with(|| format!("Failed to read {}", run_config.display()))?;
        if text.contains("neoforge.enabledGameTestNamespaces") {
            return Ok("neoforge.enabledGameTestNamespaces".to_string());
        }
    }
    Ok("forge.enabledGameTestNamespaces".to_string())
}

fn write_gametest_log4j_config(run_state_dir: &Path) -> eyre::Result<PathBuf> {
    let path = run_state_dir.join("log4j2-gametest.properties");
    let content = r"status = warn
name = SFMGameTest

appenders = console
appender.console.type = Console
appender.console.name = STDOUT
appender.console.target = SYSTEM_OUT
appender.console.layout.type = PatternLayout
appender.console.layout.pattern = [%d{HH:mm:ss}] [%t/%level] [%logger]: %msg%n%throwable

loggers = sfm
logger.sfm.name = sfm
logger.sfm.level = error
logger.sfm.additivity = false
logger.sfm.appenderRefs = stdout
logger.sfm.appenderRef.stdout.ref = STDOUT

rootLogger.level = info
rootLogger.appenderRefs = stdout
rootLogger.appenderRef.stdout.ref = STDOUT
";
    fs::write(&path, content).wrap_err_with(|| format!("Failed to write {}", path.display()))?;
    Ok(path)
}

fn clean_gametest_server_world(minecraft_dir: &Path, working_dir: &Path) -> eyre::Result<()> {
    if working_dir.file_name().and_then(|name| name.to_str()) != Some("runGameTest")
        || !working_dir.starts_with(minecraft_dir)
    {
        eyre::bail!(
            "Refusing to clean unexpected game-test working directory: {}",
            working_dir.display()
        );
    }

    let world_dir = working_dir.join("gametestserver");
    if world_dir.exists() {
        fs::remove_dir_all(&world_dir)
            .wrap_err_with(|| format!("Failed to remove {}", world_dir.display()))?;
    }
    Ok(())
}

fn clean_client_puppet_world(minecraft_dir: &Path, working_dir: &Path) -> eyre::Result<()> {
    if working_dir.file_name().and_then(|name| name.to_str()) != Some("runClientPuppet")
        || !working_dir.starts_with(minecraft_dir)
    {
        eyre::bail!(
            "Refusing to clean unexpected client puppet working directory: {}",
            working_dir.display()
        );
    }

    let world_dir = working_dir.join("saves").join("sfm_client_puppet");
    if world_dir.exists() {
        fs::remove_dir_all(&world_dir)
            .wrap_err_with(|| format!("Failed to remove {}", world_dir.display()))?;
    }
    Ok(())
}

fn prepare_client_automation_options(
    minecraft_dir: &Path,
    working_dir: &Path,
    kind: RunKind,
) -> eyre::Result<Option<PathBuf>> {
    if !matches!(kind, RunKind::ClientSmoke | RunKind::ClientPuppet) {
        return Ok(None);
    }
    if !working_dir.starts_with(minecraft_dir) {
        eyre::bail!(
            "Refusing to prepare client automation options outside minecraft dir: {}",
            working_dir.display()
        );
    }

    let options_path = working_dir.join("options.txt");
    let existing = match fs::read_to_string(&options_path) {
        Ok(content) => content,
        Err(err) if err.kind() == std::io::ErrorKind::NotFound => String::new(),
        Err(err) => {
            return Err(err).wrap_err_with(|| format!("Failed to read {}", options_path.display()));
        }
    };
    let updated = set_minecraft_option(&existing, "onboardAccessibility", "true");
    fs::write(&options_path, updated)
        .wrap_err_with(|| format!("Failed to write {}", options_path.display()))?;
    Ok(Some(options_path))
}

fn set_minecraft_option(content: &str, key: &str, value: &str) -> String {
    let prefix = format!("{key}:");
    let mut found = false;
    let mut lines = content
        .lines()
        .map(|line| {
            if line.starts_with(&prefix) {
                found = true;
                format!("{prefix}{value}")
            } else {
                line.to_string()
            }
        })
        .collect::<Vec<_>>();
    if !found {
        lines.push(format!("{prefix}{value}"));
    }
    let mut output = lines.join("\n");
    output.push('\n');
    output
}

fn run_launch_command(
    plan: &BuildPlan,
    argfile: &Path,
    working_dir: &Path,
    env: &BTreeMap<String, String>,
    log_path: &Path,
    echo_output: bool,
    timeout: Option<Duration>,
) -> eyre::Result<LaunchOutput> {
    let _span = tracing::info_span!(
        "launch_minecraft_jvm",
        mc = %plan.minecraft_version,
        java = %plan.java.executable.display(),
        argfile = %argfile.display(),
        working_dir = %working_dir.display(),
        env_vars = env.len(),
        echo_output,
        timeout_seconds = timeout.map_or(0, |timeout| timeout.as_secs()),
    )
    .entered();
    if let Some(parent) = log_path.parent() {
        fs::create_dir_all(parent)?;
    }
    let mut child = Command::new(&plan.java.executable)
        .arg(format!("@{}", argfile.display()))
        .current_dir(working_dir)
        .envs(env)
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()
        .wrap_err_with(|| format!("Failed to spawn {}", plan.java.executable.display()))?;
    tracing::info!("minecraft_jvm_spawned");

    let stdout = child
        .stdout
        .take()
        .ok_or_else(|| eyre::eyre!("Failed to capture launch stdout"))?;
    let stderr = child
        .stderr
        .take()
        .ok_or_else(|| eyre::eyre!("Failed to capture launch stderr"))?;
    let stdout_thread = thread::spawn(move || read_launch_stream(stdout, false, echo_output));
    let stderr_thread = thread::spawn(move || read_launch_stream(stderr, true, echo_output));
    let started = Instant::now();
    let mut timed_out = false;
    let status = loop {
        if let Some(status) = child.try_wait().wrap_err("Failed to poll launched JVM")? {
            break status;
        }
        if let Some(timeout) = timeout
            && started.elapsed() >= timeout
        {
            timed_out = true;
            child
                .kill()
                .wrap_err("Failed to kill timed-out launched JVM")?;
            break child
                .wait()
                .wrap_err("Failed to wait for timed-out launched JVM")?;
        }
        thread::sleep(Duration::from_millis(250));
    };
    let stdout_text = join_launch_stream(stdout_thread, "stdout")?;
    let stderr_text = join_launch_stream(stderr_thread, "stderr")?;
    let combined = format!("{stdout_text}{stderr_text}");
    let mut log = String::new();
    writeln!(log, "status={status}")?;
    writeln!(log, "timed_out={timed_out}")?;
    writeln!(log, "argfile={}", argfile.display())?;
    writeln!(log, "working_dir={}", working_dir.display())?;
    writeln!(log)?;
    log.push_str(&combined);
    fs::write(log_path, log).wrap_err_with(|| format!("Failed to write {}", log_path.display()))?;
    Ok(LaunchOutput {
        status,
        combined,
        timed_out,
    })
}

fn read_launch_stream<R>(stream: R, stderr: bool, echo_output: bool) -> std::io::Result<String>
where
    R: Read,
{
    let mut captured = String::new();
    for line in BufReader::new(stream).lines() {
        let line = line?;
        if echo_output {
            if stderr {
                eprintln!("{line}");
            } else {
                println!("{line}");
            }
        }
        captured.push_str(&line);
        captured.push('\n');
    }
    Ok(captured)
}

fn join_launch_stream(
    handle: thread::JoinHandle<std::io::Result<String>>,
    name: &str,
) -> eyre::Result<String> {
    handle
        .join()
        .map_err(|_panic| eyre::eyre!("Launch {name} reader thread panicked"))?
        .wrap_err_with(|| format!("Failed to read launch {name}"))
}

fn extract_required_gametest_pass_count(output: &str) -> Option<usize> {
    extract_number_between(output, "All ", " required tests passed :)")
}

fn extract_running_gametest_count(output: &str) -> Option<usize> {
    extract_number_between(output, "Running all ", " tests")
}

fn extract_client_puppet_pass_count(output: &str) -> Option<usize> {
    extract_number_between(
        output,
        "SFM_CLIENT_PUPPET_TESTS_PASSED required=",
        " total=",
    )
}

fn extract_number_between(output: &str, prefix: &str, suffix: &str) -> Option<usize> {
    for (start, _) in output.match_indices(prefix) {
        let after_prefix = &output[start + prefix.len()..];
        let Some(end) = after_prefix.find(suffix) else {
            continue;
        };
        let number = after_prefix[..end].trim();
        if !number.is_empty() && number.chars().all(|character| character.is_ascii_digit()) {
            return number.parse().ok();
        }
    }
    None
}

fn read_forge_run_config(
    context: &ExecutionContext<'_>,
    kind: RunKind,
) -> eyre::Result<ForgeRunConfig> {
    let config: ForgeUserdevConfig = read_zip_json_entry(
        &context.artifact("forge-userdev")?.cache_path,
        "config.json",
    )?;
    config
        .runs
        .get(kind.userdev_name())
        .cloned()
        .ok_or_else(|| {
            eyre::eyre!(
                "Forge userdev config does not define run config {}",
                kind.userdev_name()
            )
        })
}

fn run_config_requires_assets(config: &ForgeRunConfig) -> bool {
    config
        .args
        .iter()
        .chain(config.jvm_args.iter())
        .chain(config.env.values())
        .chain(config.props.values())
        .any(|value| value.contains("{asset_index}") || value.contains("{assets_root}"))
}

fn replace_placeholders(input: &str, replacements: &[(&str, &str)]) -> String {
    replacements
        .iter()
        .fold(input.to_string(), |output, (needle, replacement)| {
            output.replace(needle, replacement)
        })
}

fn resolve_forge_userdev_modules(
    context: &ExecutionContext<'_>,
    resolver: &Resolver,
) -> eyre::Result<Vec<PathBuf>> {
    let config: ForgeUserdevConfig = read_zip_json_entry(
        &context.artifact("forge-userdev")?.cache_path,
        "config.json",
    )?;
    let coordinates = config.modules;

    coordinates
        .iter()
        .enumerate()
        .map(|(index, coordinate)| {
            let coordinate = MavenCoordinate::parse(coordinate)?;
            resolver
                .resolve_artifact(
                    &format!("forge-userdev-module-{index}"),
                    &coordinate,
                    "Forge userdev module path",
                )
                .map(|artifact| artifact.cache_path)
        })
        .collect()
}

fn resolve_run_classpath(
    context: &ExecutionContext<'_>,
    resolver: &Resolver,
    kind: RunKind,
) -> eyre::Result<RunClasspath> {
    let _span = tracing::info_span!(
        "resolve_run_classpath",
        mc = %context.plan.minecraft_version,
        kind = kind.command_name(),
    )
    .entered();
    if context.plan.loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
        return resolve_neogradle_run_classpath(context, resolver, kind);
    }

    let mut legacy = Vec::new();
    legacy.push(ensure_run_forge_dev_jar(context)?);
    legacy.push(ensure_client_extra_jar(context)?);
    legacy.push(ensure_runtime_mcp_csv_mappings(context)?);
    legacy.extend(collect_jars(
        &context.plan.cache_dir.join("minecraft").join("libraries"),
    )?);
    legacy.extend(resolve_forge_userdev_libraries(context, resolver)?);
    legacy.extend(resolve_run_plain_dependencies(context, resolver, kind)?);

    let userdev_mods = resolve_run_deobf_dependencies(context, resolver, kind)?;
    let legacy = dedup_paths_preserve_order(legacy);
    let userdev_mods = dedup_paths_preserve_order(userdev_mods);
    tracing::info!(
        legacy_entries = legacy.len(),
        userdev_mods = userdev_mods.len(),
        "run_classpath resolved"
    );
    Ok(RunClasspath {
        legacy,
        userdev_mods,
    })
}

fn resolve_neogradle_run_classpath(
    context: &ExecutionContext<'_>,
    resolver: &Resolver,
    kind: RunKind,
) -> eyre::Result<RunClasspath> {
    let mut legacy = Vec::new();
    legacy.extend(collect_jars(
        &context.plan.cache_dir.join("minecraft").join("libraries"),
    )?);
    legacy.extend(resolve_forge_userdev_libraries(context, resolver)?);
    legacy.push(ensure_client_extra_jar(context)?);
    legacy.extend(ensure_run_neoforge_dev_jars(context, kind)?);

    let mut userdev_mods = Vec::new();
    if matches!(kind, RunKind::GameTestServer) {
        userdev_mods.extend(resolve_forge_userdev_test_libraries(context, resolver)?);
    }
    userdev_mods.extend(resolve_neogradle_run_dependencies(context, kind)?);

    let legacy = dedup_paths_preserve_order(legacy);
    let userdev_mods = dedup_paths_preserve_order(userdev_mods);
    tracing::info!(
        legacy_entries = legacy.len(),
        userdev_mods = userdev_mods.len(),
        "neogradle_run_classpath resolved"
    );
    Ok(RunClasspath {
        legacy,
        userdev_mods,
    })
}

fn ensure_run_neoforge_dev_jars(
    context: &ExecutionContext<'_>,
    kind: RunKind,
) -> eyre::Result<Vec<PathBuf>> {
    let input = loader_dev_compile_jar(context);
    if !input.is_file() {
        eyre::bail!(
            "{} requires the Rust-owned NeoForm dev jar first: {}",
            kind.command_name(),
            input.display()
        );
    }
    context.assert_allowed_input(&input)?;
    let neoforge_universal = context.artifact("neoforge-universal")?;
    context.assert_allowed_input(&neoforge_universal.cache_path)?;
    let neoforge_version = required_property(&context.plan.properties, "neo_version")?;
    if neoforge_requires_split_runtime(&neoforge_universal.cache_path)? {
        let minecraft_output = context
            .plan
            .cache_dir
            .join("run")
            .join(format!("minecraft-{neoforge_version}.jar"));
        let minecraft_input_state = format!(
            "{}\n{}\nsplit-minecraft-v3\n",
            file_sha1(&input)?,
            file_sha1(&neoforge_universal.cache_path)?
        );
        let minecraft_input_state_path = minecraft_output.with_extension("inputs.sha1");
        let current_minecraft_input_state =
            fs::read_to_string(&minecraft_input_state_path).unwrap_or_default();
        if !minecraft_output.is_file()
            || context.plan.refresh
            || current_minecraft_input_state != minecraft_input_state
        {
            write_run_neoforge_minecraft_dev_jar(
                &input,
                &neoforge_universal.cache_path,
                &minecraft_output,
            )?;
            fs::write(&minecraft_input_state_path, minecraft_input_state).wrap_err_with(|| {
                format!(
                    "Failed to write NeoForge Minecraft run jar input state {}",
                    minecraft_input_state_path.display()
                )
            })?;
        }
        return Ok(vec![
            minecraft_output,
            neoforge_universal.cache_path.clone(),
        ]);
    }

    let output = context
        .plan
        .cache_dir
        .join("run")
        .join(format!("neoforge-{neoforge_version}.jar"));
    let input_state = format!(
        "{}\n{}\n",
        file_sha1(&input)?,
        file_sha1(&neoforge_universal.cache_path)?
    );
    let input_state_path = output.with_extension("inputs.sha1");
    let current_input_state = fs::read_to_string(&input_state_path).unwrap_or_default();
    if !output.is_file() || context.plan.refresh || current_input_state != input_state {
        write_run_neoforge_dev_jar(&input, &neoforge_universal.cache_path, &output)?;
        fs::write(&input_state_path, input_state).wrap_err_with(|| {
            format!(
                "Failed to write NeoForge run jar input state {}",
                input_state_path.display()
            )
        })?;
    }
    Ok(vec![output])
}

fn ensure_run_forge_dev_jar(context: &ExecutionContext<'_>) -> eyre::Result<PathBuf> {
    let forge_version = required_property(&context.plan.properties, "neo_version")?;
    let input = context
        .plan
        .cache_dir
        .join("forge")
        .join(&context.plan.minecraft_version)
        .join("classes")
        .join("dev-compile.jar");
    if !input.is_file() {
        eyre::bail!(
            "Forge userdev launch requires the Rust-owned mapped dev compile jar first: {}",
            input.display()
        );
    }
    context.assert_allowed_input(&input)?;
    let forge_universal = context.artifact("forge-universal")?;
    context.assert_allowed_input(&forge_universal.cache_path)?;

    let output = context.plan.cache_dir.join("run").join(format!(
        "forge-{}-{}-dev-compile.jar",
        context.plan.minecraft_version, forge_version
    ));
    write_run_forge_dev_jar(&input, &forge_universal.cache_path, &output)?;
    Ok(output)
}

fn ensure_client_extra_jar(context: &ExecutionContext<'_>) -> eyre::Result<PathBuf> {
    let minecraft_root = context.plan.cache_dir.join("minecraft");
    let client_jar = minecraft_root.join("client.jar");
    if !client_jar.is_file() {
        let client = Client::builder()
            .user_agent("sfm-propagate-changes/no-gradle-toolchain")
            .build()
            .wrap_err("Failed to create HTTP client")?;
        download_to_path(&client, &context.plan.minecraft.client_jar_url, &client_jar)?;
    }
    context.assert_allowed_input(&client_jar)?;

    let output = minecraft_root.join("client-extra.jar");
    if output.is_file() && !context.plan.refresh {
        return Ok(output);
    }

    write_client_extra_jar(&client_jar, &output)?;
    Ok(output)
}

fn ensure_runtime_mcp_csv_mappings(context: &ExecutionContext<'_>) -> eyre::Result<PathBuf> {
    let input = context
        .plan
        .cache_dir
        .join("forge")
        .join(&context.plan.minecraft_version)
        .join("mappings")
        .join("srg_to_official.tsrg");
    if !input.is_file() {
        eyre::bail!(
            "Forge userdev launch requires SRG-to-named mappings first: {}",
            input.display()
        );
    }
    context.assert_allowed_input(&input)?;

    let output = context.plan.cache_dir.join("run").join("mcp-mappings");
    write_runtime_mcp_csv_mappings(&input, &output)?;
    Ok(output)
}

fn ensure_run_refmap_remapping_file(context: &ExecutionContext<'_>) -> eyre::Result<PathBuf> {
    let input = context
        .plan
        .cache_dir
        .join("forge")
        .join(&context.plan.minecraft_version)
        .join("mappings")
        .join("srg_to_official.tsrg");
    if !input.is_file() {
        eyre::bail!(
            "Forge userdev launch requires SRG-to-named mappings first: {}",
            input.display()
        );
    }
    context.assert_allowed_input(&input)?;

    let output = context
        .plan
        .cache_dir
        .join("project")
        .join("run-refmap-remap.srg");
    write_srg_to_named_mapping_file(&input, &output)?;
    Ok(output)
}

fn resolve_run_plain_dependencies(
    context: &ExecutionContext<'_>,
    resolver: &Resolver,
    kind: RunKind,
) -> eyre::Result<Vec<PathBuf>> {
    let dependency_script = context
        .plan
        .minecraft_dir
        .join("gradle")
        .join("dependencies")
        .join(&context.plan.minecraft_version)
        .join("dependencies.gradle");
    let dependencies = parse_dependency_script(&dependency_script, &context.plan.properties)?;
    let configurations = run_dependency_configurations(kind);
    dependencies
        .iter()
        .filter(|dependency| {
            !dependency.fg_deobf
                && configurations.contains(&dependency.configuration.as_str())
                && !is_api_classifier(&dependency.coordinate)
        })
        .enumerate()
        .map(|(index, dependency)| {
            resolver
                .resolve_artifact(
                    &format!("run-plain-dependency-{index}"),
                    &dependency.coordinate,
                    "Forge userdev run classpath",
                )
                .map(|artifact| artifact.cache_path)
        })
        .collect()
}

fn is_api_classifier(coordinate: &MavenCoordinate) -> bool {
    coordinate.classifier.as_deref() == Some("api")
}

fn resolve_run_deobf_dependencies(
    context: &ExecutionContext<'_>,
    resolver: &Resolver,
    kind: RunKind,
) -> eyre::Result<Vec<PathBuf>> {
    let dependency_output = context.plan.cache_dir.join("dependencies");
    let mapping_path = context
        .plan
        .cache_dir
        .join("forge")
        .join(&context.plan.minecraft_version)
        .join("mappings")
        .join("srg_to_official.tsrg");
    if !mapping_path.is_file() {
        eyre::bail!(
            "{} requires generated dependency mappings first: {}",
            kind.command_name(),
            mapping_path.display()
        );
    }
    let mapping_hash = file_sha1(&mapping_path)?;
    let configurations = run_dependency_configurations(kind);
    let mut output = Vec::new();

    for dependency in context.plan.dependencies.iter().filter(|dependency| {
        configurations.contains(&dependency.configuration.as_str())
            && MavenCoordinate::parse(&dependency.resolved_notation)
                .is_ok_and(|coordinate| !is_api_classifier(&coordinate))
    }) {
        let coordinate = MavenCoordinate::parse(&dependency.resolved_notation)?;
        let artifact = resolver.resolve_artifact(
            &format!("run-deobf-dependency-{}", output.len()),
            &coordinate,
            &format!("{} runtime dependency", dependency.configuration),
        )?;
        context.assert_allowed_input(&artifact.cache_path)?;
        let remapped = remapped_dependency_output_path(
            &dependency_output,
            &artifact.cache_path,
            &mapping_hash,
            &coordinate,
        )?;
        if !remapped.is_file() {
            eyre::bail!(
                "{} requires remapped dependency jar {}. Run jar build first.",
                kind.command_name(),
                remapped.display()
            );
        }
        context.assert_allowed_input(&remapped)?;
        if should_include_run_dependency(&remapped, kind) {
            output.push(remapped);
        }
    }

    Ok(output)
}

fn resolve_neogradle_run_dependencies(
    context: &ExecutionContext<'_>,
    kind: RunKind,
) -> eyre::Result<Vec<PathBuf>> {
    let dependency_output = context.plan.cache_dir.join("dependencies");
    let configurations = run_dependency_configurations(kind);
    let mut output = Vec::new();

    for dependency in context.plan.dependencies.iter().filter(|dependency| {
        configurations.contains(&dependency.configuration.as_str())
            && MavenCoordinate::parse(&dependency.resolved_notation)
                .is_ok_and(|coordinate| !is_api_classifier(&coordinate))
    }) {
        let coordinate = MavenCoordinate::parse(&dependency.resolved_notation)?;
        let copied = copied_neogradle_dependency_output_path(
            &dependency_output,
            &dependency.configuration,
            &coordinate,
        );
        if !copied.is_file() {
            eyre::bail!(
                "{} requires copied NeoGradle dependency jar {}. Run jar build first.",
                kind.command_name(),
                copied.display()
            );
        }
        context.assert_allowed_input(&copied)?;
        output.push(copied);
    }

    Ok(output)
}

fn run_dependency_configurations(kind: RunKind) -> &'static [&'static str] {
    match kind {
        RunKind::Data => &["implementation", "runtimeOnly", "transitiveRuntime"],
        RunKind::Client
        | RunKind::ClientSmoke
        | RunKind::ClientPuppet
        | RunKind::Server
        | RunKind::GameTestServer => &[
            "implementation",
            "jarJar",
            "runtimeOnly",
            "gametestImplementation",
            "gametestRuntimeOnly",
            "transitiveRuntime",
        ],
    }
}

fn should_include_run_dependency(path: &Path, kind: RunKind) -> bool {
    if !matches!(kind, RunKind::Data) {
        return true;
    }
    let file_name = path
        .file_name()
        .and_then(std::ffi::OsStr::to_str)
        .map_or("", |name| name);
    !file_name.contains("mouse-tweaks-60089")
}

fn write_classpath_file(path: &Path, classpath: &[PathBuf]) -> eyre::Result<()> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)?;
    }
    let lines = classpath
        .iter()
        .map(|path| path.display().to_string())
        .collect::<Vec<_>>();
    fs::write(path, format!("{}\n", lines.join("\n")))
        .wrap_err_with(|| format!("Failed to write {}", path.display()))
}

fn run_source_roots(context: &ExecutionContext<'_>, kind: RunKind) -> eyre::Result<String> {
    let mod_id = required_property(&context.plan.properties, "mod_id")?;
    let existing_roots = run_source_root_paths(context, kind)?;
    let separator = if cfg!(windows) { ";" } else { ":" };
    Ok(existing_roots
        .into_iter()
        .map(|path| format!("{mod_id}%%{}", path.display()))
        .collect::<Vec<_>>()
        .join(separator))
}

fn run_source_root_paths(
    context: &ExecutionContext<'_>,
    kind: RunKind,
) -> eyre::Result<Vec<PathBuf>> {
    let project_root = context.plan.cache_dir.join("project");
    let mut roots = vec![
        project_root.join("staged-resources"),
        project_root.join("classes"),
    ];
    let source_set = kind.optional_source_set();
    roots.push(project_root.join(source_set).join("resources"));
    roots.push(project_root.join(source_set).join("classes"));

    let existing_roots = roots
        .into_iter()
        .filter(|path| path.exists())
        .collect::<Vec<_>>();
    if existing_roots.is_empty() {
        eyre::bail!("No Rust-owned project class/resource roots are available for launch");
    }

    Ok(existing_roots)
}

fn kind_extra_program_args(plan: &BuildPlan, kind: RunKind) -> eyre::Result<Vec<String>> {
    if !matches!(kind, RunKind::Data) {
        return Ok(Vec::new());
    }
    Ok(vec![
        "--mod".to_string(),
        required_property(&plan.properties, "mod_id")?.to_string(),
        "--all".to_string(),
        "--output".to_string(),
        plan.minecraft_dir
            .join("src")
            .join("generated")
            .join("resources")
            .display()
            .to_string(),
        "--existing".to_string(),
        plan.minecraft_dir
            .join("src")
            .join("main")
            .join("resources")
            .display()
            .to_string(),
    ])
}

fn prepare_minecraft_assets(context: &ExecutionContext<'_>) -> eyre::Result<MinecraftAssets> {
    let _span = tracing::info_span!(
        "prepare_minecraft_assets",
        mc = %context.plan.minecraft_version,
    )
    .entered();
    let client = Client::builder()
        .user_agent("sfm-propagate-changes/no-gradle-toolchain")
        .build()
        .wrap_err("Failed to create HTTP client")?;
    let version_json_path = context
        .plan
        .cache_dir
        .join("minecraft")
        .join(format!("{}.json", context.plan.minecraft_version));
    let version_json: MinecraftVersionJson = read_json_file(&version_json_path)?;
    let asset_index = version_json
        .asset_index
        .ok_or_else(|| eyre::eyre!("Minecraft version JSON missing assetIndex"))?;
    let MinecraftAssetIndex {
        id: index_id,
        url: index_url,
    } = asset_index;
    let assets_root = context.plan.cache_dir.join("assets");
    let index_path = assets_root.join("indexes").join(format!("{index_id}.json"));
    download_to_path(&client, &index_url, &index_path)?;

    let index_json: MinecraftAssetIndexJson = read_json_file(&index_path)?;
    let objects = index_json.objects;
    let mut downloaded = 0usize;
    let mut checked = 0usize;
    for object in objects.values() {
        let hash = object.hash.as_str();
        let prefix = hash
            .get(..2)
            .ok_or_else(|| eyre::eyre!("Minecraft asset hash is too short: {hash}"))?;
        let object_path = assets_root.join("objects").join(prefix).join(hash);
        if object_path.is_file() && file_sha1(&object_path)? == hash {
            checked += 1;
            continue;
        }
        let object_url = format!("https://resources.download.minecraft.net/{prefix}/{hash}");
        download_to_path_overwrite(&client, &object_url, &object_path, true)?;
        let actual_hash = file_sha1(&object_path)?;
        if actual_hash != hash {
            eyre::bail!(
                "Downloaded asset {} with SHA-1 {}, expected {}",
                object_path.display(),
                actual_hash,
                hash
            );
        }
        downloaded += 1;
        checked += 1;
        if downloaded.is_multiple_of(100) {
            println!(
                "Downloaded {downloaded} missing Minecraft assets ({checked}/{})",
                objects.len()
            );
        }
    }
    if downloaded > 0 {
        println!(
            "Downloaded {downloaded} Minecraft assets into {}",
            assets_root.display()
        );
    }
    tracing::info!(
        asset_index = index_id.as_str(),
        checked,
        downloaded,
        total = objects.len(),
        assets_root = %assets_root.display(),
        "minecraft_assets_prepared"
    );

    Ok(MinecraftAssets {
        root: assets_root,
        index_id,
    })
}

#[derive(Debug)]
struct ExecutionContext<'a> {
    plan: &'a BuildPlan,
    forbidden_input_roots: Vec<PathBuf>,
}

#[derive(Debug, Facet)]
struct NodeState {
    schema_version: u32,
    id: String,
    status: String,
    started_at_unix_ms: u128,
    duration_ms: u128,
    #[facet(proxy = JsonPath)]
    java_executable: PathBuf,
    java_version: String,
    inputs: Vec<String>,
    outputs: Vec<NodeOutputState>,
}

#[derive(Debug, Facet)]
struct NodeOutputState {
    #[facet(proxy = JsonPath)]
    path: PathBuf,
    exists: bool,
    sha1: Option<String>,
}

impl<'a> ExecutionContext<'a> {
    fn new(plan: &'a BuildPlan) -> eyre::Result<Self> {
        let forbidden_input_roots = [
            plan.minecraft_dir.join("build").join("fg_cache"),
            plan.minecraft_dir.join("build").join("classpath"),
            plan.minecraft_dir.join("build").join("classes"),
            plan.minecraft_dir.join("build").join("resources"),
            plan.minecraft_dir.join("build").join("tmp").join("jar"),
        ]
        .into_iter()
        .map(|path| canonicalize_lenient(&path))
        .collect::<eyre::Result<Vec<_>>>()?;

        Ok(Self {
            plan,
            forbidden_input_roots,
        })
    }

    fn write_node_state(
        &self,
        id: &str,
        inputs: &[&str],
        outputs: &[PathBuf],
        status: &str,
    ) -> eyre::Result<()> {
        let started = Instant::now();
        let state = NodeState {
            schema_version: 1,
            id: id.to_string(),
            status: status.to_string(),
            started_at_unix_ms: std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .map_or(0, |duration| duration.as_millis()),
            duration_ms: started.elapsed().as_millis(),
            java_executable: self.plan.java.executable.clone(),
            java_version: self.plan.java.version_output.clone(),
            inputs: inputs.iter().map(|input| (*input).to_string()).collect(),
            outputs: outputs
                .iter()
                .map(|path| {
                    let sha1 = path.is_file().then(|| file_sha1(path)).transpose()?;
                    Ok(NodeOutputState {
                        path: path.clone(),
                        exists: path.exists(),
                        sha1,
                    })
                })
                .collect::<eyre::Result<Vec<_>>>()?,
        };

        fs::create_dir_all(&self.plan.state_dir)?;
        let state_path = self.plan.state_dir.join(format!("{id}.json"));
        fs::write(&state_path, facet_json::to_string_pretty(&state)?)
            .wrap_err_with(|| format!("Failed to write {}", state_path.display()))?;
        Ok(())
    }

    fn assert_allowed_input(&self, path: &Path) -> eyre::Result<()> {
        let canonical = canonicalize_lenient(path)?;
        for forbidden in &self.forbidden_input_roots {
            if canonical.starts_with(forbidden) {
                eyre::bail!(
                    "Clean-slate jar build attempted to read forbidden Gradle output path: {}",
                    path.display()
                );
            }
        }
        Ok(())
    }

    fn artifact(&self, id: &str) -> eyre::Result<&ArtifactPlan> {
        self.plan
            .artifacts
            .iter()
            .find(|artifact| artifact.id == id)
            .ok_or_else(|| eyre::eyre!("Resolved plan did not include artifact id {id}"))
    }

    fn maybe_artifact(&self, id: &str) -> Option<&ArtifactPlan> {
        self.plan
            .artifacts
            .iter()
            .find(|artifact| artifact.id == id)
    }

    fn run_java_tool(
        &self,
        tool_id: &str,
        jvm_args: &[&str],
        args: &[String],
        work_dir: &Path,
    ) -> eyre::Result<()> {
        self.run_java_tool_with_classpath(tool_id, jvm_args, &[], args, work_dir)
    }

    fn run_java_tool_with_classpath(
        &self,
        tool_id: &str,
        jvm_args: &[&str],
        extra_classpath: &[PathBuf],
        args: &[String],
        work_dir: &Path,
    ) -> eyre::Result<()> {
        let _span = tracing::info_span!(
            "run_java_tool",
            tool_id,
            work_dir = %work_dir.display(),
            jvm_args = jvm_args.len(),
            extra_classpath_entries = extra_classpath.len(),
            args = args.len(),
        )
        .entered();
        let tool = self.artifact(tool_id)?;
        self.assert_allowed_input(&tool.cache_path)?;
        for path in extra_classpath {
            self.assert_allowed_input(path)?;
        }
        fs::create_dir_all(work_dir)?;
        let main_class = read_main_class(&tool.cache_path)?;
        let classpath = dedup_paths_preserve_order(
            std::iter::once(tool.cache_path.clone())
                .chain(extra_classpath.iter().cloned())
                .collect(),
        );
        let classpath_arg = join_classpath(&classpath);
        let java_argfile = work_dir.join(format!("{tool_id}.java.args"));
        let mut java_args = jvm_args
            .iter()
            .map(|arg| (*arg).to_string())
            .collect::<Vec<_>>();
        java_args.extend(["-cp".to_string(), classpath_arg.clone(), main_class.clone()]);
        java_args.extend(args.iter().cloned());
        fs::write(
            &java_argfile,
            java_args
                .into_iter()
                .map(escape_argfile_arg)
                .collect::<Vec<_>>()
                .join("\n"),
        )
        .wrap_err_with(|| format!("Failed to write {}", java_argfile.display()))?;
        let started = Instant::now();
        let log_path = work_dir.join("console.log");
        println!(
            "Java tool {tool_id}: start main={main_class} argfile={} log={}",
            java_argfile.display(),
            log_path.display()
        );
        let output = Command::new(&self.plan.java.executable)
            .arg(format!("@{}", java_argfile.display()))
            .current_dir(work_dir)
            .output()
            .wrap_err_with(|| {
                format!(
                    "Failed to run Java tool {tool_id} using {}",
                    self.plan.java.executable.display()
                )
            })?;

        let mut log = Vec::new();
        let duration_ms = started.elapsed().as_millis();
        writeln!(
            log,
            "tool={tool_id}\nmain={main_class}\nstatus={}\nduration_ms={}\nclasspath={}\nargs={:?}\n",
            output.status, duration_ms, classpath_arg, args
        )?;
        log.extend_from_slice(b"\n--- stdout ---\n");
        log.extend_from_slice(&output.stdout);
        log.extend_from_slice(b"\n--- stderr ---\n");
        log.extend_from_slice(&output.stderr);
        fs::write(&log_path, log)
            .wrap_err_with(|| format!("Failed to write {}", log_path.display()))?;

        if !output.status.success() {
            tracing::warn!(
                tool_id,
                status = %output.status,
                duration_ms,
                log_path = %log_path.display(),
                "java_tool_failed"
            );
            eyre::bail!(
                "Java tool {tool_id} failed with {}. See {}",
                output.status,
                log_path.display()
            );
        }
        tracing::info!(
            tool_id,
            duration_ms,
            log_path = %log_path.display(),
            "java_tool_completed"
        );
        println!("Java tool {tool_id}: done in {duration_ms} ms");
        Ok(())
    }
}

#[expect(
    clippy::too_many_lines,
    reason = "clean-slate MCP executor is being split incrementally"
)]
fn execute_mcp_config_joined(context: &ExecutionContext<'_>) -> eyre::Result<()> {
    let _span = tracing::info_span!(
        "execute_mcp_config_joined",
        mc = %context.plan.minecraft_version,
    )
    .entered();
    let client = Client::builder()
        .user_agent("sfm-propagate-changes/no-gradle-toolchain")
        .build()
        .wrap_err("Failed to create HTTP client")?;
    let mcp_root = context
        .plan
        .cache_dir
        .join("mcp")
        .join(&context.plan.minecraft_version)
        .join("joined");
    fs::create_dir_all(&mcp_root)?;
    let output = mcp_root.join("patch").join("joined-patched-sources.jar");
    if output.is_file() && !context.plan.refresh {
        tracing::info!(
            output = %output.display(),
            "mcp_config_joined cache hit"
        );
        println!(
            "Build node execute-mcp-config-joined: reusing {}",
            output.display()
        );
        context.write_node_state(
            "execute-mcp-config-joined",
            &["Minecraft client/server jars", "MCPConfig config.json"],
            &[output],
            "cached",
        )?;
        return Ok(());
    }
    tracing::info!(
        output = %output.display(),
        refresh = context.plan.refresh,
        "mcp_config_joined cache miss"
    );

    let minecraft_root = context.plan.cache_dir.join("minecraft");
    let client_jar = minecraft_root.join("client.jar");
    let server_bundle = minecraft_root.join("server-bundle.jar");
    let client_mappings = minecraft_root.join("client.txt");

    download_to_path(&client, &context.plan.minecraft.client_jar_url, &client_jar)?;
    download_to_path(
        &client,
        &context.plan.minecraft.server_jar_url,
        &server_bundle,
    )?;
    download_to_path(
        &client,
        required_minecraft_mapping_url(
            context,
            context.plan.minecraft.client_mappings_url.as_deref(),
            "client",
        )?,
        &client_mappings,
    )?;

    context.assert_allowed_input(&client_jar)?;
    context.assert_allowed_input(&server_bundle)?;
    context.assert_allowed_input(&client_mappings)?;

    let mcp_config = context.artifact("mcp-config")?;
    context.assert_allowed_input(&mcp_config.cache_path)?;
    let data_dir = mcp_root.join("data");
    fs::create_dir_all(&data_dir)?;
    let joined_tsrg = data_dir.join("joined.tsrg");
    extract_zip_entry_to_path(&mcp_config.cache_path, "config/joined.tsrg", &joined_tsrg)?;

    let extract_server = mcp_root.join("extractServer").join("output.jar");
    context.run_java_tool(
        "tool-installer-tools-1-3",
        &[],
        &[
            "--task".to_string(),
            "bundler_extract".to_string(),
            "--input".to_string(),
            server_bundle.display().to_string(),
            "--output".to_string(),
            extract_server.display().to_string(),
            "--jar-only".to_string(),
        ],
        &mcp_root.join("extractServer"),
    )?;

    let merged_mappings = mcp_root.join("mergeMappings").join("output.tsrg");
    context.run_java_tool(
        "tool-installer-tools-1-2",
        &[],
        &[
            "--task".to_string(),
            "MERGE_MAPPING".to_string(),
            "--left".to_string(),
            joined_tsrg.display().to_string(),
            "--right".to_string(),
            client_mappings.display().to_string(),
            "--right-names".to_string(),
            "right,left".to_string(),
            "--classes".to_string(),
            "--output".to_string(),
            merged_mappings.display().to_string(),
        ],
        &mcp_root.join("mergeMappings"),
    )?;

    let mapped_classes = read_tsrg_original_classes(&joined_tsrg)?;
    let stripped_client = mcp_root.join("stripClient").join("output.jar");
    copy_filtered_jar(&client_jar, &stripped_client, &mapped_classes)?;
    let stripped_server = mcp_root.join("stripServer").join("output.jar");
    copy_filtered_jar(&extract_server, &stripped_server, &mapped_classes)?;

    let merged_jar = mcp_root.join("merge").join("output.jar");
    context.run_java_tool(
        "tool-mergetool-1-1-5",
        &[],
        &[
            "--client".to_string(),
            stripped_client.display().to_string(),
            "--server".to_string(),
            stripped_server.display().to_string(),
            "--ann".to_string(),
            context.plan.minecraft_version.clone(),
            "--output".to_string(),
            merged_jar.display().to_string(),
            "--inject".to_string(),
            "false".to_string(),
        ],
        &mcp_root.join("merge"),
    )?;

    let libraries_file = mcp_root.join("listLibraries").join("libraries.txt");
    write_minecraft_libraries_cfg(context, &client, &libraries_file)?;

    let renamed_jar = mcp_root.join("rename").join("output.jar");
    context.run_java_tool(
        "tool-fart",
        &[],
        &[
            "--input".to_string(),
            merged_jar.display().to_string(),
            "--output".to_string(),
            renamed_jar.display().to_string(),
            "--map".to_string(),
            merged_mappings.display().to_string(),
            "--cfg".to_string(),
            libraries_file.display().to_string(),
            "--ann-fix".to_string(),
            "--ids-fix".to_string(),
            "--src-fix".to_string(),
            "--record-fix".to_string(),
        ],
        &mcp_root.join("rename"),
    )?;

    let decompiled_jar = mcp_root.join("decompile").join("output.jar");
    context.run_java_tool(
        "tool-forgeflower",
        &["-Xmx4G"],
        &[
            "-din=1".to_string(),
            "-rbr=1".to_string(),
            "-dgs=1".to_string(),
            "-asc=1".to_string(),
            "-rsy=1".to_string(),
            "-iec=1".to_string(),
            "-jvn=1".to_string(),
            "-isl=0".to_string(),
            "-iib=1".to_string(),
            "-bsm=1".to_string(),
            "-dcl=1".to_string(),
            "-log=TRACE".to_string(),
            "-cfg".to_string(),
            libraries_file.display().to_string(),
            renamed_jar.display().to_string(),
            decompiled_jar.display().to_string(),
        ],
        &mcp_root.join("decompile"),
    )?;

    let injected_jar = mcp_root.join("inject").join("output.jar");
    inject_mcp_sources(&mcp_config.cache_path, &decompiled_jar, &injected_jar)?;

    apply_mcp_joined_patches(
        context,
        &mcp_config.cache_path,
        &injected_jar,
        &output,
        &mcp_root,
    )?;

    context.write_node_state(
        "execute-mcp-config-joined",
        &["Minecraft client/server jars", "MCPConfig config.json"],
        &[output],
        "complete",
    )?;
    Ok(())
}

#[expect(
    clippy::too_many_lines,
    reason = "clean-slate Forge userdev executor is being split incrementally"
)]
fn execute_forge_userdev(context: &ExecutionContext<'_>) -> eyre::Result<()> {
    let _span = tracing::info_span!(
        "execute_forge_userdev",
        mc = %context.plan.minecraft_version,
    )
    .entered();
    let client = Client::builder()
        .user_agent("sfm-propagate-changes/no-gradle-toolchain")
        .build()
        .wrap_err("Failed to create HTTP client")?;
    let forge_root = context
        .plan
        .cache_dir
        .join("forge")
        .join(&context.plan.minecraft_version);
    fs::create_dir_all(&forge_root)?;
    let mappings_root = forge_root.join("mappings");
    let srg_to_official = mappings_root.join("srg_to_official.tsrg");
    let official_to_srg = mappings_root.join("official_to_srg.tsrg");
    let output = forge_root.join("classes").join("dev-compile.jar");
    if output.is_file()
        && srg_to_official.is_file()
        && official_to_srg.is_file()
        && !context.plan.refresh
    {
        tracing::info!(
            output = %output.display(),
            "forge_userdev cache hit"
        );
        println!(
            "Build node execute-forge-userdev: reusing {}",
            output.display()
        );
        context.write_node_state(
            "execute-forge-userdev",
            &["Forge userdev", "MCPConfig joined outputs"],
            &[output],
            "cached",
        )?;
        return Ok(());
    }
    tracing::info!(
        output = %output.display(),
        refresh = context.plan.refresh,
        "forge_userdev cache miss"
    );

    let mcp_root = context
        .plan
        .cache_dir
        .join("mcp")
        .join(&context.plan.minecraft_version)
        .join("joined");
    let mcp_sources = mcp_root.join("patch").join("joined-patched-sources.jar");
    if !mcp_sources.is_file() {
        eyre::bail!(
            "Forge userdev requires MCP joined sources first: {}",
            mcp_sources.display()
        );
    }

    let userdev = context.artifact("forge-userdev")?;
    let forge_sources = context.artifact("forge-sources")?;
    let forge_universal = context.artifact("forge-universal")?;
    context.assert_allowed_input(&userdev.cache_path)?;
    context.assert_allowed_input(&forge_sources.cache_path)?;
    context.assert_allowed_input(&forge_universal.cache_path)?;

    let patched_minecraft_sources = forge_root.join("sourcePatches").join("minecraft-srg.jar");
    context.run_java_tool(
        "tool-diffpatch",
        &[],
        &[
            "--patch".to_string(),
            "--mode".to_string(),
            "OFFSET".to_string(),
            "--archive".to_string(),
            "ZIP".to_string(),
            "--archive-rejects".to_string(),
            "ZIP".to_string(),
            "--prefix".to_string(),
            "patches/".to_string(),
            "--output".to_string(),
            patched_minecraft_sources.display().to_string(),
            "--reject".to_string(),
            forge_root
                .join("sourcePatches")
                .join("rejects.zip")
                .display()
                .to_string(),
            mcp_sources.display().to_string(),
            userdev.cache_path.display().to_string(),
        ],
        &forge_root.join("sourcePatches"),
    )?;

    let combined_srg_sources = forge_root.join("sources").join("combined-srg.jar");
    merge_zip_archives(
        &[
            patched_minecraft_sources.clone(),
            forge_sources.cache_path.clone(),
        ],
        &combined_srg_sources,
    )?;

    let minecraft_root = context.plan.cache_dir.join("minecraft");
    let client_mappings = minecraft_root.join("client.txt");
    let server_mappings = minecraft_root.join("server.txt");
    download_to_path(
        &client,
        required_minecraft_mapping_url(
            context,
            context.plan.minecraft.client_mappings_url.as_deref(),
            "client",
        )?,
        &client_mappings,
    )?;
    download_to_path(
        &client,
        required_minecraft_mapping_url(
            context,
            context.plan.minecraft.server_mappings_url.as_deref(),
            "server",
        )?,
        &server_mappings,
    )?;
    fs::create_dir_all(&mappings_root)?;
    let obf_to_official = mappings_root.join("obf_to_official.tsrg");
    let parchment_parameters = context
        .artifact("parchment-data")
        .ok()
        .map(|artifact| read_parchment_parameters(&artifact.cache_path))
        .transpose()?;
    generate_mojang_tsrg_mappings(
        &mcp_root.join("mergeMappings").join("output.tsrg"),
        &[client_mappings, server_mappings],
        parchment_parameters.as_ref(),
        &obf_to_official,
        &srg_to_official,
        &official_to_srg,
    )?;

    let official_sources = forge_root.join("sources").join("combined-official.jar");
    context.run_java_tool(
        "tool-fart",
        &[],
        &[
            "--input".to_string(),
            combined_srg_sources.display().to_string(),
            "--output".to_string(),
            official_sources.display().to_string(),
            "--map".to_string(),
            srg_to_official.display().to_string(),
            "--src-fix".to_string(),
            "--record-fix".to_string(),
        ],
        &forge_root.join("remapSources"),
    )?;

    let binpatches = forge_root.join("binpatch").join("joined.lzma");
    extract_zip_entry_to_path(&userdev.cache_path, "joined.lzma", &binpatches)?;
    let binpatched_minecraft = forge_root.join("classes").join("minecraft-binpatched.jar");
    context.run_java_tool(
        "forge-binarypatcher",
        &[],
        &[
            "--clean".to_string(),
            mcp_root
                .join("rename")
                .join("output.jar")
                .display()
                .to_string(),
            "--output".to_string(),
            binpatched_minecraft.display().to_string(),
            "--apply".to_string(),
            binpatches.display().to_string(),
        ],
        &forge_root.join("binpatch"),
    )?;
    if !binpatched_minecraft.is_file() {
        eyre::bail!(
            "BinaryPatcher completed without producing {}",
            binpatched_minecraft.display()
        );
    }

    let merged_output = forge_root
        .join("classes")
        .join("dev-compile-srg-untransformed.jar");
    let clean_named_minecraft = mcp_root.join("rename").join("output.jar");
    merge_zip_archives(
        &[
            binpatched_minecraft,
            clean_named_minecraft,
            forge_universal.cache_path.clone(),
        ],
        &merged_output,
    )?;

    let access_transformed = forge_root.join("classes").join("dev-compile-srg-at.jar");
    let access_transformed_patched = forge_root.join("classes").join("dev-compile-srg.jar");
    let forge_at = forge_root
        .join("accessTransform")
        .join("forge-accesstransformer.cfg");
    extract_zip_entry_to_path(&userdev.cache_path, "ats/accesstransformer.cfg", &forge_at)?;
    let project_at = context
        .plan
        .minecraft_dir
        .join("src")
        .join("main")
        .join("resources")
        .join("META-INF")
        .join("accesstransformer.cfg");
    context.run_java_tool(
        "tool-access-transformers",
        &[],
        &[
            "--inJar".to_string(),
            merged_output.display().to_string(),
            "--outJar".to_string(),
            access_transformed.display().to_string(),
            "--atFile".to_string(),
            forge_at.display().to_string(),
            "--atFile".to_string(),
            project_at.display().to_string(),
        ],
        &forge_root.join("accessTransform"),
    )?;
    if !access_transformed.is_file() {
        eyre::bail!(
            "AccessTransformers completed without producing {}",
            access_transformed.display()
        );
    }
    patch_inner_class_access_in_jar(
        &access_transformed,
        &access_transformed_patched,
        "net/minecraft/client/gui/components/MultilineTextField",
        "net/minecraft/client/gui/components/MultilineTextField$StringView",
    )?;
    context.run_java_tool(
        "tool-fart",
        &[],
        &[
            "--input".to_string(),
            access_transformed_patched.display().to_string(),
            "--output".to_string(),
            output.display().to_string(),
            "--map".to_string(),
            srg_to_official.display().to_string(),
            "--ann-fix".to_string(),
            "--ids-fix".to_string(),
            "--record-fix".to_string(),
        ],
        &forge_root.join("remapDevCompile"),
    )?;
    if !output.is_file() {
        eyre::bail!(
            "FART completed without producing mapped Forge dev compile jar {}",
            output.display()
        );
    }
    context.write_node_state(
        "execute-forge-userdev",
        &["Forge userdev", "MCPConfig joined outputs"],
        &[output],
        "complete",
    )
}

#[expect(
    clippy::too_many_lines,
    reason = "NeoForm userdev orchestration keeps cache checks, arguments, and state writes visible."
)]
fn execute_neoform_userdev(context: &ExecutionContext<'_>) -> eyre::Result<()> {
    let _span = tracing::info_span!(
        "execute_neoform_userdev",
        mc = %context.plan.minecraft_version,
    )
    .entered();
    let neoform_root = context
        .plan
        .cache_dir
        .join("neoform")
        .join(&context.plan.minecraft_version);
    let output_root = neoform_root.join("classes");
    let nfrt_home = neoform_root.join("nfrt-home");
    let nfrt_work = neoform_root.join("nfrt-work");
    let artifact_manifest = neoform_root.join("artifact-manifest.properties");
    let problem_report = neoform_root.join("problems.json");
    let game_jar = neoform_dev_compile_jar(context);
    let game_sources = output_root.join("gameSourcesWithNeoForge.jar");

    if context.plan.refresh {
        tracing::info!("neoform_userdev refresh requested");
        reset_cache_directory(&context.plan.cache_dir, &neoform_root)?;
    }
    fs::create_dir_all(&output_root)?;
    fs::create_dir_all(&nfrt_home)?;
    fs::create_dir_all(&nfrt_work)?;
    write_neoform_artifact_manifest(context, &artifact_manifest)?;

    if game_jar.is_file() && !context.plan.refresh {
        tracing::info!(
            output = %game_jar.display(),
            "neoform_userdev cache hit"
        );
        context.write_node_state(
            "execute-neoform-userdev",
            &["NeoForge userdev", "NeoForm Runtime"],
            &[game_jar],
            "complete",
        )?;
        return Ok(());
    }
    tracing::info!(
        output = %game_jar.display(),
        refresh = context.plan.refresh,
        "neoform_userdev cache miss"
    );

    let mut args = vec![
        "--home-dir".to_string(),
        nfrt_home.display().to_string(),
        "--work-dir".to_string(),
        nfrt_work.display().to_string(),
        "--artifact-manifest".to_string(),
        artifact_manifest.display().to_string(),
        "--warn-on-artifact-manifest-miss".to_string(),
        "--no-color".to_string(),
        "--no-emojis".to_string(),
    ];
    for repository in &context.plan.repositories {
        args.push(format!("--add-repository={}", repository.url));
    }
    args.extend([
        "run".to_string(),
        "--dist".to_string(),
        "joined".to_string(),
        "--neoforge".to_string(),
        context.plan.loader_toolchain.userdev_coordinate.clone(),
        "--write-result".to_string(),
        format!("gameJarWithNeoForge:{}", game_jar.display()),
        "--write-result".to_string(),
        format!("gameSourcesWithNeoForge:{}", game_sources.display()),
        "--problems-report".to_string(),
        problem_report.display().to_string(),
    ]);

    if let Some(java_home) = &context.plan.java.home {
        args.extend(["--java-home".to_string(), java_home.display().to_string()]);
    }

    if let Some(parchment) = context.maybe_artifact("parchment-data")
        && let Some(coordinate) = &parchment.coordinate
    {
        args.extend([
            "--parchment-data".to_string(),
            coordinate.clone(),
            "--parchment-conflict-prefix".to_string(),
            "p_".to_string(),
        ]);
    }

    let project_at = context
        .plan
        .minecraft_dir
        .join("src")
        .join("main")
        .join("resources")
        .join("META-INF")
        .join("accesstransformer.cfg");
    if project_at.is_file() {
        context.assert_allowed_input(&project_at)?;
        args.extend([
            "--access-transformer".to_string(),
            project_at.display().to_string(),
        ]);
    }

    context.run_java_tool("tool-neoform-runtime", &[], &args, &neoform_root)?;
    if !game_jar.is_file() {
        eyre::bail!(
            "NeoForm Runtime completed without producing {}",
            game_jar.display()
        );
    }

    context.write_node_state(
        "execute-neoform-userdev",
        &["NeoForge userdev", "NeoForm Runtime"],
        &[game_jar, game_sources],
        "complete",
    )
}

fn neoform_dev_compile_jar(context: &ExecutionContext<'_>) -> PathBuf {
    context
        .plan
        .cache_dir
        .join("neoform")
        .join(&context.plan.minecraft_version)
        .join("classes")
        .join("gameJarWithNeoForge.jar")
}

fn required_minecraft_mapping_url<'a>(
    context: &ExecutionContext<'_>,
    url: Option<&'a str>,
    side: &str,
) -> eyre::Result<&'a str> {
    url.ok_or_else(|| {
        eyre::eyre!(
            "Minecraft {} version metadata does not include {side} mappings; this is only supported by the NeoGradle/NeoForm executor.",
            context.plan.minecraft_version
        )
    })
}

fn loader_dev_compile_jar(context: &ExecutionContext<'_>) -> PathBuf {
    if context.plan.loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
        neoform_dev_compile_jar(context)
    } else {
        context
            .plan
            .cache_dir
            .join("forge")
            .join(&context.plan.minecraft_version)
            .join("classes")
            .join("dev-compile.jar")
    }
}

fn write_neoform_artifact_manifest(
    context: &ExecutionContext<'_>,
    output: &Path,
) -> eyre::Result<()> {
    if let Some(parent) = output.parent() {
        fs::create_dir_all(parent)?;
    }

    let mut lines = Vec::new();
    for artifact in &context.plan.artifacts {
        let Some(coordinate) = &artifact.coordinate else {
            continue;
        };
        context.assert_allowed_input(&artifact.cache_path)?;
        lines.push(format!(
            "{}={}",
            java_properties_escape(coordinate),
            java_properties_escape(&artifact.cache_path.display().to_string())
        ));
    }
    lines.sort();
    lines.dedup();
    fs::write(output, lines.join("\n"))
        .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
    Ok(())
}

fn java_properties_escape(input: &str) -> String {
    let mut output = String::new();
    for character in input.chars() {
        match character {
            '\\' => output.push_str("\\\\"),
            ':' => output.push_str("\\:"),
            '=' => output.push_str("\\="),
            ' ' => output.push_str("\\ "),
            _ => output.push(character),
        }
    }
    output
}

#[expect(
    clippy::too_many_lines,
    reason = "dependency deobf orchestration keeps per-dependency cache and remap behavior visible."
)]
fn execute_dependency_deobf(context: &ExecutionContext<'_>) -> eyre::Result<()> {
    let _span = tracing::info_span!(
        "execute_dependency_deobf",
        mc = %context.plan.minecraft_version,
        dependencies = context.plan.dependencies.len(),
    )
    .entered();
    let output = context.plan.cache_dir.join("dependencies");
    if context.plan.refresh {
        reset_cache_directory(&context.plan.cache_dir, &output)?;
    } else {
        fs::create_dir_all(&output)?;
        remove_stale_dependency_outputs(&output)?;
    }
    let resolver = Resolver::new(
        context.plan.maven_cache_dir.clone(),
        context.plan.repositories.clone(),
        context.plan.refresh,
        context.plan.allow_local_artifact_cache,
        context.plan.lockfile.clone(),
    )?;
    if context.plan.loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
        copy_neogradle_dependency_jars(context, &resolver, &output)?;
        return Ok(());
    }

    let mapping_path = context
        .plan
        .cache_dir
        .join("forge")
        .join(&context.plan.minecraft_version)
        .join("mappings")
        .join("srg_to_official.tsrg");
    if !mapping_path.is_file() {
        eyre::bail!(
            "Dependency deobf requires generated mapping first: {}",
            mapping_path.display()
        );
    }
    let mapping_hash = file_sha1(&mapping_path)?;
    let member_mappings = read_unique_srg_member_mappings(&mapping_path)?;
    let mut outputs = Vec::new();

    for dependency in &context.plan.dependencies {
        let coordinate = MavenCoordinate::parse(&dependency.resolved_notation)?;
        #[cfg(feature = "tracing_detailed")]
        let _dependency_span = tracing::debug_span!(
            "prepare_dependency",
            configuration = dependency.configuration.as_str(),
            coordinate = %coordinate,
            source = ?dependency.source,
        )
        .entered();
        let artifact = resolver.resolve_artifact(
            &format!("dependency-{}", outputs.len()),
            &coordinate,
            &format!("{} dependency", dependency.configuration),
        )?;
        context.assert_allowed_input(&artifact.cache_path)?;
        let remapped = remapped_dependency_output_path(
            &output,
            &artifact.cache_path,
            &mapping_hash,
            &coordinate,
        )?;
        let specialsource_output = specialsource_dependency_output_path(
            &output,
            &artifact.cache_path,
            &mapping_hash,
            &coordinate,
        )?;
        if remapped.is_file() {
            tracing::debug!(
                coordinate = %coordinate,
                output = %remapped.display(),
                "dependency_deobf cache hit"
            );
        } else {
            tracing::info!(
                coordinate = %coordinate,
                output = %remapped.display(),
                "dependency_deobf cache miss"
            );
            if !specialsource_output.is_file() {
                if let Some(parent) = specialsource_output.parent() {
                    fs::create_dir_all(parent)?;
                }
                context.run_java_tool_with_classpath(
                    "tool-specialsource",
                    &[],
                    &[],
                    &[
                        "--in-jar".to_string(),
                        artifact.cache_path.display().to_string(),
                        "--out-jar".to_string(),
                        specialsource_output.display().to_string(),
                        "--srg-in".to_string(),
                        mapping_path.display().to_string(),
                        "--live".to_string(),
                    ],
                    &output
                        .join("remap-work")
                        .join(safe_path_segment(&coordinate.file_name())),
                )?;
            }
            rewrite_srg_member_constants_in_jar(
                &specialsource_output,
                &remapped,
                &member_mappings,
            )?;
        }
        if !remapped.is_file() {
            eyre::bail!(
                "SpecialSource completed without producing remapped dependency jar {}",
                remapped.display()
            );
        }
        outputs.push(remapped);
    }

    context.write_node_state(
        "deobfuscate-mod-dependencies",
        &["active fg.deobf dependency jars"],
        &outputs,
        "complete",
    )?;
    Ok(())
}

fn copy_neogradle_dependency_jars(
    context: &ExecutionContext<'_>,
    resolver: &Resolver,
    output: &Path,
) -> eyre::Result<()> {
    let mut outputs = Vec::new();
    for dependency in &context.plan.dependencies {
        let coordinate = MavenCoordinate::parse(&dependency.resolved_notation)?;
        let artifact = resolver.resolve_artifact(
            &format!("dependency-{}", outputs.len()),
            &coordinate,
            &format!("{} dependency", dependency.configuration),
        )?;
        context.assert_allowed_input(&artifact.cache_path)?;
        let copied =
            copied_neogradle_dependency_output_path(output, &dependency.configuration, &coordinate);
        fs::copy(&artifact.cache_path, &copied).wrap_err_with(|| {
            format!(
                "Failed to copy {} to {}",
                artifact.cache_path.display(),
                copied.display()
            )
        })?;
        outputs.push(copied);
    }
    context.write_node_state(
        "deobfuscate-mod-dependencies",
        &["active NeoGradle dependency jars"],
        &outputs,
        "complete",
    )?;
    Ok(())
}

fn copied_neogradle_dependency_output_path(
    output_dir: &Path,
    configuration: &str,
    coordinate: &MavenCoordinate,
) -> PathBuf {
    output_dir.join(format!(
        "{}-{}",
        safe_path_segment(configuration),
        coordinate.file_name()
    ))
}

fn remapped_dependency_output_path(
    output_dir: &Path,
    input_jar: &Path,
    mapping_hash: &str,
    coordinate: &MavenCoordinate,
) -> eyre::Result<PathBuf> {
    let input_hash = file_sha1(input_jar)?;
    Ok(output_dir.join(format!(
        "{}-{}-named-mixin-{}",
        input_hash.chars().take(12).collect::<String>(),
        mapping_hash.chars().take(12).collect::<String>(),
        coordinate.file_name()
    )))
}

fn specialsource_dependency_output_path(
    output_dir: &Path,
    input_jar: &Path,
    mapping_hash: &str,
    coordinate: &MavenCoordinate,
) -> eyre::Result<PathBuf> {
    let input_hash = file_sha1(input_jar)?;
    Ok(output_dir.join("specialsource").join(format!(
        "{}-{}-specialsource-{}",
        input_hash.chars().take(12).collect::<String>(),
        mapping_hash.chars().take(12).collect::<String>(),
        coordinate.file_name()
    )))
}

fn read_unique_srg_member_mappings(mapping_path: &Path) -> eyre::Result<BTreeMap<String, String>> {
    let content = fs::read_to_string(mapping_path)
        .wrap_err_with(|| format!("Failed to read {}", mapping_path.display()))?;
    let mut candidates: BTreeMap<String, Option<String>> = BTreeMap::new();

    for line in content.lines() {
        if !line.starts_with('\t') && !line.starts_with(' ') {
            continue;
        }
        if line.starts_with("\t\t") || line.starts_with("  ") {
            continue;
        }
        let parts = line.split_whitespace().collect::<Vec<_>>();
        match parts.as_slice() {
            [srg, named] if is_srg_member_name(srg) => {
                insert_unique_member_mapping(&mut candidates, srg, named);
            }
            [srg, _descriptor, named] if is_srg_member_name(srg) => {
                insert_unique_member_mapping(&mut candidates, srg, named);
            }
            _ => {}
        }
    }

    Ok(candidates
        .into_iter()
        .filter_map(|(srg, named)| named.map(|named| (srg, named)))
        .collect())
}

fn insert_unique_member_mapping(
    candidates: &mut BTreeMap<String, Option<String>>,
    srg: &str,
    named: &str,
) {
    match candidates.get_mut(srg) {
        Some(existing) if existing.as_deref() == Some(named) => {}
        Some(existing) => *existing = None,
        None => {
            candidates.insert(srg.to_string(), Some(named.to_string()));
        }
    }
}

fn is_srg_member_name(name: &str) -> bool {
    let Some(rest) = name.strip_prefix("f_").or_else(|| name.strip_prefix("m_")) else {
        return false;
    };
    let Some(number) = rest.strip_suffix('_') else {
        return false;
    };
    !number.is_empty() && number.chars().all(|character| character.is_ascii_digit())
}

fn rewrite_srg_member_constants_in_jar(
    input: &Path,
    output: &Path,
    member_mappings: &BTreeMap<String, String>,
) -> eyre::Result<()> {
    if let Some(parent) = output.parent() {
        fs::create_dir_all(parent)?;
    }
    let input_bytes =
        fs::read(input).wrap_err_with(|| format!("Failed to read {}", input.display()))?;
    let mut archive = ZipArchive::new(Cursor::new(input_bytes))
        .wrap_err_with(|| format!("Failed to open {}", input.display()))?;
    let output_file =
        File::create(output).wrap_err_with(|| format!("Failed to create {}", output.display()))?;
    let mut writer = ZipWriter::new(output_file);
    let options = SimpleFileOptions::default().compression_method(CompressionMethod::Deflated);
    let mut replacements = 0usize;

    for index in 0..archive.len() {
        let mut entry = archive
            .by_index(index)
            .wrap_err_with(|| format!("Failed to read {} entry #{index}", input.display()))?;
        let name = entry.name().replace('\\', "/");
        if name.ends_with('/') || is_signature_file(&name) {
            continue;
        }
        let mut bytes = Vec::new();
        entry
            .read_to_end(&mut bytes)
            .wrap_err_with(|| format!("Failed to read entry {name} from {}", input.display()))?;
        if zip_entry_has_extension(&name, "class") {
            let (patched, patched_count) =
                rewrite_class_srg_member_constants(&bytes, member_mappings)
                    .wrap_err_with(|| format!("Failed to patch class entry {name}"))?;
            bytes = patched;
            replacements += patched_count;
        }
        writer
            .start_file(name, options)
            .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
        writer
            .write_all(&bytes)
            .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
    }

    writer
        .finish()
        .wrap_err_with(|| format!("Failed to finish {}", output.display()))?;
    if replacements > 0 {
        println!(
            "Patched {replacements} SRG member constants in {}",
            output.display()
        );
    }
    Ok(())
}

fn rewrite_class_srg_member_constants(
    bytes: &[u8],
    member_mappings: &BTreeMap<String, String>,
) -> eyre::Result<(Vec<u8>, usize)> {
    if bytes.len() < 10 || bytes.get(..4) != Some(&[0xCA, 0xFE, 0xBA, 0xBE]) {
        return Ok((bytes.to_vec(), 0));
    }

    let constant_pool_count = read_u16(bytes, 8)? as usize;
    let mut output = Vec::with_capacity(bytes.len());
    output.extend_from_slice(&bytes[..10]);
    let mut cursor = 10usize;
    let mut index = 1usize;
    let mut replacements = 0usize;

    while index < constant_pool_count {
        let tag = *bytes
            .get(cursor)
            .ok_or_else(|| eyre::eyre!("Class constant pool is truncated"))?;
        output.push(tag);
        cursor += 1;
        match tag {
            1 => {
                let length = read_u16(bytes, cursor)? as usize;
                let content_start = cursor + 2;
                let content_end = content_start
                    .checked_add(length)
                    .ok_or_else(|| eyre::eyre!("Utf8 constant length overflow"))?;
                if content_end > bytes.len() {
                    eyre::bail!("Utf8 constant extends past end of class file");
                }
                let content = &bytes[content_start..content_end];
                if let Ok(text) = std::str::from_utf8(content)
                    && let Some(replacement) = member_mappings.get(text)
                {
                    let replacement_bytes = replacement.as_bytes();
                    let replacement_len = u16::try_from(replacement_bytes.len())
                        .wrap_err("Replacement member name is too long for classfile UTF8")?;
                    output.extend_from_slice(&replacement_len.to_be_bytes());
                    output.extend_from_slice(replacement_bytes);
                    replacements += 1;
                } else {
                    output.extend_from_slice(&bytes[cursor..content_end]);
                }
                cursor = content_end;
            }
            3 | 4 | 9 | 10 | 11 | 12 | 17 | 18 => {
                copy_constant_pool_payload(bytes, &mut output, &mut cursor, 4)?;
            }
            5 | 6 => {
                copy_constant_pool_payload(bytes, &mut output, &mut cursor, 8)?;
                index += 1;
            }
            7 | 8 | 16 | 19 | 20 => {
                copy_constant_pool_payload(bytes, &mut output, &mut cursor, 2)?;
            }
            15 => {
                copy_constant_pool_payload(bytes, &mut output, &mut cursor, 3)?;
            }
            _ => eyre::bail!("Unsupported class constant pool tag {tag}"),
        }
        index += 1;
    }

    output.extend_from_slice(
        bytes
            .get(cursor..)
            .ok_or_else(|| eyre::eyre!("Class constant pool extends past end of file"))?,
    );
    Ok((output, replacements))
}

fn copy_constant_pool_payload(
    input: &[u8],
    output: &mut Vec<u8>,
    cursor: &mut usize,
    length: usize,
) -> eyre::Result<()> {
    let end = cursor
        .checked_add(length)
        .ok_or_else(|| eyre::eyre!("Constant pool payload length overflow"))?;
    let payload = input
        .get(*cursor..end)
        .ok_or_else(|| eyre::eyre!("Class constant pool extends past end of file"))?;
    output.extend_from_slice(payload);
    *cursor = end;
    Ok(())
}

fn remove_stale_dependency_outputs(output: &Path) -> eyre::Result<()> {
    for entry in
        fs::read_dir(output).wrap_err_with(|| format!("Failed to read {}", output.display()))?
    {
        let entry = entry.wrap_err_with(|| format!("Failed to read {}", output.display()))?;
        let path = entry.path();
        if !path.is_file() || !zip_entry_has_extension(&path.to_string_lossy(), "jar") {
            continue;
        }
        let file_name = path
            .file_name()
            .and_then(std::ffi::OsStr::to_str)
            .map_or("", |name| name);
        if !file_name.contains("-named-mixin-") {
            fs::remove_file(&path).wrap_err_with(|| {
                format!(
                    "Failed to remove stale dependency output {}",
                    path.display()
                )
            })?;
        }
    }
    Ok(())
}

#[expect(
    clippy::too_many_lines,
    reason = "project compile orchestration keeps generated sources, resources, and javac inputs visible."
)]
fn execute_project_compile(context: &ExecutionContext<'_>) -> eyre::Result<()> {
    let _span = tracing::info_span!(
        "execute_project_compile",
        mc = %context.plan.minecraft_version,
    )
    .entered();
    let project_root = context.plan.cache_dir.join("project");
    let generated_sources = project_root
        .join("generated-src")
        .join("antlr")
        .join("main")
        .join("ca")
        .join("teamdman")
        .join("langs");
    let classes_dir = project_root.join("classes");
    let resources_dir = project_root.join("resources");
    let staged_resources_dir = project_root.join("staged-resources");
    let gametest_classes_dir = project_root.join("gametest").join("classes");
    let gametest_resources_dir = project_root.join("gametest").join("resources");
    tracing::info!(
        classes_dir = %classes_dir.display(),
        resources_dir = %resources_dir.display(),
        gametest_classes_dir = %gametest_classes_dir.display(),
        "project_compile_outputs_will_be_recreated"
    );
    fs::create_dir_all(&generated_sources)?;

    let resolver = Resolver::new(
        context.plan.maven_cache_dir.clone(),
        context.plan.repositories.clone(),
        context.plan.refresh,
        context.plan.allow_local_artifact_cache,
        context.plan.lockfile.clone(),
    )?;
    write_minecraft_libraries_cfg(
        context,
        &resolver.client,
        &project_root.join("minecraft-libraries.cfg"),
    )?;
    let antlr_classpath = resolve_antlr_classpath(context, &resolver)?;
    run_antlr(context, &antlr_classpath, &generated_sources)?;

    let classpath = resolve_project_compile_classpath(context, &resolver, &antlr_classpath)?;

    let sources = collect_project_java_sources(context, &generated_sources)?;
    let argfile = project_root.join("javac-main.args");
    write_javac_argfile(context, &argfile, &classpath, &sources, &classes_dir)?;

    let started = Instant::now();
    println!(
        "javac main: start sources={} argfile={}",
        sources.len(),
        argfile.display()
    );
    let mut main_fingerprint_paths = classpath.clone();
    main_fingerprint_paths.extend(sources.iter().cloned());
    main_fingerprint_paths.push(argfile.clone());
    let main_fingerprint = input_fingerprint(
        context,
        "javac-main",
        &main_fingerprint_paths,
        &[
            context.plan.java.version_output.clone(),
            context.plan.java_release.to_string(),
            format!("{:?}", context.plan.loader_toolchain.kind),
        ],
    )?;
    let main_state_path = project_root.join("javac-main.inputs.sha1");
    let main_refmap = resources_dir.join("sfm.refmap.json");
    let main_cache_hit = cache_state_matches(
        context,
        &main_state_path,
        &main_fingerprint,
        &[&classes_dir],
    )? && (context.plan.loader_toolchain.kind
        == LoaderToolchainKind::NeoGradleUserdev
        || main_refmap.is_file());
    if main_cache_hit {
        println!(
            "javac main: reused cached outputs in {} ms",
            started.elapsed().as_millis()
        );
    } else {
        reset_cache_directory(&context.plan.cache_dir, &classes_dir)?;
        reset_cache_directory(&context.plan.cache_dir, &resources_dir)?;
        let output = Command::new(javac_executable(&context.plan.java))
            .arg(format!("@{}", argfile.display()))
            .output()
            .wrap_err("Failed to run javac")?;
        let log_path = project_root.join("javac-main.log");
        let mut log = Vec::new();
        log.extend_from_slice(b"--- stdout ---\n");
        log.extend_from_slice(&output.stdout);
        log.extend_from_slice(b"\n--- stderr ---\n");
        log.extend_from_slice(&output.stderr);
        fs::write(&log_path, log)
            .wrap_err_with(|| format!("Failed to write {}", log_path.display()))?;
        if !output.status.success() {
            eyre::bail!(
                "javac failed with {}. See {}",
                output.status,
                log_path.display()
            );
        }
        write_cache_state(&main_state_path, &main_fingerprint)?;
        println!("javac main: done in {} ms", started.elapsed().as_millis());
    }

    compile_optional_java_source_set(
        context,
        "gametest",
        &classpath,
        &classes_dir,
        &gametest_classes_dir,
        &main_fingerprint,
    )?;
    stage_optional_resource_source_set(
        context,
        "gametest",
        &gametest_resources_dir,
        &["README.md"],
    )?;
    if context.plan.loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
        patch_neogradle_anonymous_constructor_debug_names(&classes_dir)?;
    } else {
        ensure_run_refmap_remapping_file(context)?;
    }
    stage_project_resources(context, &staged_resources_dir, &resources_dir)?;

    context.write_node_state(
        "compile-project",
        &[
            "src/main/java",
            "src/main/antlr",
            "src/gametest/java",
            "mapped Forge/Minecraft jar",
        ],
        &[
            classes_dir,
            resources_dir,
            staged_resources_dir,
            gametest_classes_dir,
            gametest_resources_dir,
            project_root.join("run-refmap-remap.srg"),
        ],
        "complete",
    )?;
    Ok(())
}

fn patch_neogradle_anonymous_constructor_debug_names(classes_dir: &Path) -> eyre::Result<()> {
    let debug_name_patches: &[(&str, &[(&str, &str)])] = &[
        (
            "ca/teamdman/sfm/client/text_styling/ProgramSyntaxHighlightingHelper$1.class",
            &[("arg0", "tokenSource")],
        ),
        (
            "ca/teamdman/sfm/common/containermenu/ManagerContainerMenu$1.class",
            &[
                ("arg0", "container"),
                ("arg1", "slot"),
                ("arg2", "x"),
                ("arg3", "y"),
            ],
        ),
        (
            "ca/teamdman/sfm/common/resourcetype/FluidResourceType$1.class",
            &[("arg0", "size"), ("arg1", "capacity")],
        ),
        (
            "ca/teamdman/sfm/common/resourcetype/ForgeEnergyResourceType$1.class",
            &[("arg0", "capacity")],
        ),
        (
            "ca/teamdman/sfm/common/resourcetype/ItemResourceType$1.class",
            &[("arg0", "size")],
        ),
    ];

    let mut total_replacements = 0usize;
    for (class_name, replacements) in debug_name_patches {
        let class_path = zip_name_to_path(classes_dir, class_name);
        if !class_path.is_file() {
            continue;
        }
        let mapping = replacements
            .iter()
            .map(|(from, to)| ((*from).to_string(), (*to).to_string()))
            .collect::<BTreeMap<_, _>>();
        let bytes = fs::read(&class_path)
            .wrap_err_with(|| format!("Failed to read {}", class_path.display()))?;
        let (patched_bytes, replacement_count) =
            rewrite_class_srg_member_constants(&bytes, &mapping).wrap_err_with(|| {
                format!("Failed to patch debug names in {}", class_path.display())
            })?;
        if replacement_count == 0 {
            continue;
        }
        fs::write(&class_path, patched_bytes)
            .wrap_err_with(|| format!("Failed to write {}", class_path.display()))?;
        total_replacements += replacement_count;
    }

    if total_replacements > 0 {
        println!("Patched {total_replacements} NeoGradle anonymous constructor debug names");
    }
    Ok(())
}

fn compile_optional_java_source_set(
    context: &ExecutionContext<'_>,
    source_set: &str,
    base_classpath: &[PathBuf],
    main_classes_dir: &Path,
    classes_dir: &Path,
    upstream_fingerprint: &str,
) -> eyre::Result<()> {
    let project_root = context.plan.cache_dir.join("project");
    let source_root = context
        .plan
        .minecraft_dir
        .join("src")
        .join(source_set)
        .join("java");
    if !source_root.exists() {
        reset_cache_directory(&context.plan.cache_dir, classes_dir)?;
        return Ok(());
    }

    let sources = collect_source_set_java_sources(context, source_set)?;
    if sources.is_empty() {
        reset_cache_directory(&context.plan.cache_dir, classes_dir)?;
        return Ok(());
    }

    let classpath = dedup_paths_preserve_order(
        std::iter::once(main_classes_dir.to_path_buf())
            .chain(base_classpath.iter().cloned())
            .collect(),
    );
    let argfile = project_root.join(format!("javac-{source_set}.args"));
    write_javac_no_ap_argfile(
        &argfile,
        &classpath,
        &sources,
        classes_dir,
        context.plan.java_release,
        context.plan.java.major_version,
    )?;

    let started = Instant::now();
    println!(
        "javac {source_set}: start sources={} argfile={}",
        sources.len(),
        argfile.display()
    );
    let mut fingerprint_paths = sources.clone();
    fingerprint_paths.push(argfile.clone());
    let fingerprint = input_fingerprint(
        context,
        &format!("javac-{source_set}"),
        &fingerprint_paths,
        &[
            context.plan.java.version_output.clone(),
            context.plan.java_release.to_string(),
            source_set.to_string(),
            upstream_fingerprint.to_string(),
        ],
    )?;
    let state_path = project_root.join(format!("javac-{source_set}.inputs.sha1"));
    if cache_state_matches(context, &state_path, &fingerprint, &[classes_dir])? {
        println!(
            "javac {source_set}: reused cached outputs in {} ms",
            started.elapsed().as_millis()
        );
        return Ok(());
    }

    reset_cache_directory(&context.plan.cache_dir, classes_dir)?;
    let output = Command::new(javac_executable(&context.plan.java))
        .arg(format!("@{}", argfile.display()))
        .output()
        .wrap_err_with(|| format!("Failed to run javac for {source_set}"))?;
    let log_path = project_root.join(format!("javac-{source_set}.log"));
    let mut log = Vec::new();
    log.extend_from_slice(b"--- stdout ---\n");
    log.extend_from_slice(&output.stdout);
    log.extend_from_slice(b"\n--- stderr ---\n");
    log.extend_from_slice(&output.stderr);
    fs::write(&log_path, log)
        .wrap_err_with(|| format!("Failed to write {}", log_path.display()))?;
    if !output.status.success() {
        eyre::bail!(
            "javac {source_set} failed with {}. See {}",
            output.status,
            log_path.display()
        );
    }
    write_cache_state(&state_path, &fingerprint)?;
    println!(
        "javac {source_set}: done in {} ms",
        started.elapsed().as_millis()
    );
    Ok(())
}

fn stage_optional_resource_source_set(
    context: &ExecutionContext<'_>,
    source_set: &str,
    output: &Path,
    excludes: &[&str],
) -> eyre::Result<()> {
    reset_cache_directory(&context.plan.cache_dir, output)?;
    let root = context
        .plan
        .minecraft_dir
        .join("src")
        .join(source_set)
        .join("resources");
    if !root.exists() {
        return Ok(());
    }
    context.assert_allowed_input(&root)?;

    for path in collect_files_under(&root)? {
        context.assert_allowed_input(&path)?;
        let name = relative_zip_name(&root, &path)?;
        if excludes.iter().any(|exclude| *exclude == name) {
            continue;
        }
        let output_path = zip_name_to_path(output, &name);
        if let Some(parent) = output_path.parent() {
            fs::create_dir_all(parent)?;
        }
        fs::copy(&path, &output_path).wrap_err_with(|| {
            format!(
                "Failed to copy {} to {}",
                path.display(),
                output_path.display()
            )
        })?;
    }
    Ok(())
}

#[expect(
    clippy::too_many_lines,
    reason = "packaging executor keeps orchestration visible while toolchain is incomplete"
)]
fn execute_package_and_reobfuscate(context: &ExecutionContext<'_>) -> eyre::Result<()> {
    let _span = tracing::info_span!(
        "execute_package_and_reobfuscate",
        mc = %context.plan.minecraft_version,
        output = %context.plan.rust_output_jar.display(),
    )
    .entered();
    if context.plan.rust_output_jar == context.plan.gradle_output_jar {
        eyre::bail!(
            "Refusing to write Rust jar over Gradle jar: {}",
            context.plan.rust_output_jar.display()
        );
    }

    let project_root = context.plan.cache_dir.join("project");
    let classes_dir = project_root.join("classes");
    let javac_resources_dir = project_root.join("resources");
    let staged_resources_dir = project_root.join("staged-resources");
    let development_jar = project_root.join("dev.jar");
    let mixin_reobf_mapping = project_root.join("compileJava-mappings.tsrg");
    let reobf_mapping = context
        .plan
        .cache_dir
        .join("forge")
        .join(&context.plan.minecraft_version)
        .join("mappings")
        .join("official_to_srg.tsrg");
    tracing::info!(
        development_jar = %development_jar.display(),
        staged_resources_dir = %staged_resources_dir.display(),
        reobf_mapping = %reobf_mapping.display(),
        "package_inputs_resolved"
    );

    if !classes_dir.is_dir() {
        eyre::bail!(
            "Project classes directory is missing: {}",
            classes_dir.display()
        );
    }
    if context.plan.loader_toolchain.kind != LoaderToolchainKind::NeoGradleUserdev
        && !javac_resources_dir.join("sfm.refmap.json").is_file()
    {
        eyre::bail!(
            "Mixin annotation processor did not produce {}",
            javac_resources_dir.join("sfm.refmap.json").display()
        );
    }
    stage_project_resources(context, &staged_resources_dir, &javac_resources_dir)?;
    let mut package_fingerprint_paths = vec![classes_dir.clone(), staged_resources_dir.clone()];
    if context.plan.loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
        package_fingerprint_paths.extend(
            context
                .plan
                .dependencies
                .iter()
                .filter(|dependency| dependency.configuration == "jarJar")
                .map(|dependency| dependency.cache_path.clone()),
        );
    } else {
        package_fingerprint_paths.push(mixin_reobf_mapping.clone());
        package_fingerprint_paths.push(reobf_mapping.clone());
    }
    let package_fingerprint = input_fingerprint(
        context,
        "package-and-reobfuscate-jar",
        &package_fingerprint_paths,
        &package_fingerprint_extras(context),
    )?;
    let package_state_path = project_root.join("package-and-reobfuscate.inputs.sha1");
    let started = Instant::now();
    println!(
        "package-and-reobfuscate-jar: start output={}",
        context.plan.rust_output_jar.display()
    );
    if cache_state_matches_outputs(
        context,
        &package_state_path,
        &package_fingerprint,
        &[],
        &[&context.plan.rust_output_jar],
    )? {
        println!(
            "package-and-reobfuscate-jar: reused cached Rust jar in {} ms",
            started.elapsed().as_millis()
        );
        context.write_node_state(
            "package-and-reobfuscate-jar",
            &[
                "compiled classes",
                "expanded resources",
                "reobfuscation mappings",
            ],
            std::slice::from_ref(&context.plan.rust_output_jar),
            "cached",
        )?;
        return Ok(());
    }

    write_project_development_jar(
        context,
        &classes_dir,
        &staged_resources_dir,
        &development_jar,
    )?;

    if let Some(parent) = context.plan.rust_output_jar.parent() {
        fs::create_dir_all(parent)?;
    }
    if context.plan.rust_output_jar.exists() {
        fs::remove_file(&context.plan.rust_output_jar).wrap_err_with(|| {
            format!(
                "Failed to remove previous Rust jar {}",
                context.plan.rust_output_jar.display()
            )
        })?;
    }
    if context.plan.loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
        fs::copy(&development_jar, &context.plan.rust_output_jar).wrap_err_with(|| {
            format!(
                "Failed to copy {} to {}",
                development_jar.display(),
                context.plan.rust_output_jar.display()
            )
        })?;
        context.write_node_state(
            "package-and-reobfuscate-jar",
            &["compiled classes", "expanded resources"],
            &[
                staged_resources_dir,
                development_jar,
                context.plan.rust_output_jar.clone(),
            ],
            "complete",
        )?;
        write_cache_state(&package_state_path, &package_fingerprint)?;
        println!(
            "package-and-reobfuscate-jar: done in {} ms",
            started.elapsed().as_millis()
        );
        return Ok(());
    }

    if !reobf_mapping.is_file() {
        eyre::bail!(
            "Reobfuscation mapping is missing: {}",
            reobf_mapping.display()
        );
    }
    if !mixin_reobf_mapping.is_file() {
        eyre::bail!(
            "Mixin reobfuscation mapping is missing: {}",
            mixin_reobf_mapping.display()
        );
    }

    let resolver = Resolver::new(
        context.plan.maven_cache_dir.clone(),
        context.plan.repositories.clone(),
        context.plan.refresh,
        context.plan.allow_local_artifact_cache,
        context.plan.lockfile.clone(),
    )?;
    let antlr_classpath = resolve_antlr_classpath(context, &resolver)?;
    let reobf_classpath = resolve_project_compile_classpath(context, &resolver, &antlr_classpath)?;
    context.run_java_tool_with_classpath(
        "tool-specialsource",
        &[],
        &reobf_classpath,
        &[
            "--in-jar".to_string(),
            development_jar.display().to_string(),
            "--out-jar".to_string(),
            context.plan.rust_output_jar.display().to_string(),
            "--srg-in".to_string(),
            reobf_mapping.display().to_string(),
            "--srg-in".to_string(),
            mixin_reobf_mapping.display().to_string(),
            "--live".to_string(),
        ],
        &project_root.join("reobf"),
    )?;
    if !context.plan.rust_output_jar.is_file() {
        eyre::bail!(
            "SpecialSource completed without producing Rust jar {}",
            context.plan.rust_output_jar.display()
        );
    }

    write_cache_state(&package_state_path, &package_fingerprint)?;
    println!(
        "package-and-reobfuscate-jar: done in {} ms",
        started.elapsed().as_millis()
    );
    context.write_node_state(
        "package-and-reobfuscate-jar",
        &[
            "compiled classes",
            "expanded resources",
            "reobfuscation mappings",
        ],
        &[
            staged_resources_dir,
            development_jar,
            context.plan.rust_output_jar.clone(),
        ],
        "complete",
    )
}

fn package_fingerprint_extras(context: &ExecutionContext<'_>) -> Vec<String> {
    let mut extras = vec![
        "package-and-reobfuscate-v1".to_string(),
        format!("{:?}", context.plan.loader_toolchain.kind),
        context
            .plan
            .worktree_path
            .file_name()
            .and_then(|name| name.to_str())
            .unwrap_or("sfm")
            .to_string(),
    ];
    extras.extend(
        context
            .plan
            .properties
            .iter()
            .map(|(key, value)| format!("property:{key}={value}")),
    );
    extras.extend(context.plan.artifacts.iter().map(|artifact| {
        format!(
            "artifact:{}:{:?}:{:?}:{:?}",
            artifact.id, artifact.coordinate, artifact.url, artifact.sha1
        )
    }));
    extras.extend(context.plan.dependencies.iter().map(|dependency| {
        format!(
            "dependency:{}:{}:{}",
            dependency.configuration, dependency.notation, dependency.resolved_notation
        )
    }));
    extras
}

fn stage_project_resources(
    context: &ExecutionContext<'_>,
    staging_dir: &Path,
    javac_resources_dir: &Path,
) -> eyre::Result<()> {
    reset_cache_directory(&context.plan.cache_dir, staging_dir)?;
    let mut written = BTreeSet::new();
    for root in [
        context
            .plan
            .minecraft_dir
            .join("src")
            .join("main")
            .join("resources"),
        context
            .plan
            .minecraft_dir
            .join("src")
            .join("generated")
            .join("resources"),
        javac_resources_dir.to_path_buf(),
    ] {
        stage_resource_root(context, &root, staging_dir, &mut written)?;
    }
    Ok(())
}

fn stage_resource_root(
    context: &ExecutionContext<'_>,
    root: &Path,
    staging_dir: &Path,
    written: &mut BTreeSet<String>,
) -> eyre::Result<()> {
    if !root.exists() {
        return Ok(());
    }
    context.assert_allowed_input(root)?;

    for path in collect_files_under(root)? {
        if path
            .components()
            .any(|component| component.as_os_str() == ".cache")
        {
            continue;
        }
        context.assert_allowed_input(&path)?;
        let name = relative_zip_name(root, &path)?;
        if !written.insert(name.clone()) {
            continue;
        }

        let bytes = if matches!(
            name.as_str(),
            "META-INF/mods.toml" | "META-INF/neoforge.mods.toml" | "pack.mcmeta"
        ) {
            let template_text = fs::read_to_string(&path)
                .wrap_err_with(|| format!("Failed to read resource {}", path.display()))?;
            expand_gradle_resource_template(&template_text, &context.plan.properties)?.into_bytes()
        } else {
            fs::read(&path).wrap_err_with(|| format!("Failed to read {}", path.display()))?
        };
        let output = zip_name_to_path(staging_dir, &name);
        if let Some(parent) = output.parent() {
            fs::create_dir_all(parent)?;
        }
        fs::write(&output, bytes)
            .wrap_err_with(|| format!("Failed to write staged resource {}", output.display()))?;
    }

    Ok(())
}

fn write_project_development_jar(
    context: &ExecutionContext<'_>,
    classes_dir: &Path,
    resources_dir: &Path,
    output: &Path,
) -> eyre::Result<()> {
    context.assert_allowed_input(classes_dir)?;
    context.assert_allowed_input(resources_dir)?;
    let mut entries = BTreeMap::new();
    add_directory_to_jar_entries(context, &mut entries, classes_dir)?;
    add_directory_to_jar_entries(context, &mut entries, resources_dir)?;
    add_neogradle_jarjar_entries(context, &mut entries)?;

    if let Some(parent) = output.parent() {
        fs::create_dir_all(parent)?;
    }
    let output_file =
        File::create(output).wrap_err_with(|| format!("Failed to create {}", output.display()))?;
    let mut writer = ZipWriter::new(output_file);
    let options = SimpleFileOptions::default().compression_method(CompressionMethod::Deflated);

    writer
        .start_file("META-INF/MANIFEST.MF", options)
        .wrap_err_with(|| format!("Failed to write manifest to {}", output.display()))?;
    writer
        .write_all(build_project_manifest(context).as_bytes())
        .wrap_err_with(|| format!("Failed to write manifest to {}", output.display()))?;

    for (name, bytes) in entries {
        if name.eq_ignore_ascii_case("META-INF/MANIFEST.MF") {
            continue;
        }
        writer
            .start_file(name, options)
            .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
        writer
            .write_all(&bytes)
            .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
    }

    writer
        .finish()
        .wrap_err_with(|| format!("Failed to finish {}", output.display()))?;
    Ok(())
}

fn add_neogradle_jarjar_entries(
    context: &ExecutionContext<'_>,
    entries: &mut BTreeMap<String, Vec<u8>>,
) -> eyre::Result<()> {
    if context.plan.loader_toolchain.kind != LoaderToolchainKind::NeoGradleUserdev {
        return Ok(());
    }

    let mut metadata_entries = Vec::new();
    for dependency in context
        .plan
        .dependencies
        .iter()
        .filter(|dependency| dependency.configuration == "jarJar")
    {
        let coordinate = MavenCoordinate::parse(&dependency.resolved_notation)?;
        context.assert_allowed_input(&dependency.cache_path)?;
        let path = format!("META-INF/jarjar/{}", coordinate.file_name());
        let bytes = fs::read(&dependency.cache_path)
            .wrap_err_with(|| format!("Failed to read {}", dependency.cache_path.display()))?;
        entries.insert(path.clone(), bytes);
        metadata_entries.push(JarJarMetadataEntry {
            identifier: JarJarIdentifier {
                group: coordinate.group,
                artifact: coordinate.artifact,
            },
            version: JarJarVersion {
                range: format!("[{}]", coordinate.version),
                artifact_version: coordinate.version,
            },
            path,
            is_obfuscated: false,
        });
    }

    if metadata_entries.is_empty() {
        return Ok(());
    }

    let metadata = JarJarMetadata {
        jars: metadata_entries,
    };
    let mut metadata_json = facet_json::to_string_pretty(&metadata)?.replace('\n', "\r\n");
    metadata_json.push_str("\r\n");
    entries.insert(
        "META-INF/jarjar/metadata.json".to_string(),
        metadata_json.into_bytes(),
    );
    Ok(())
}

fn add_directory_to_jar_entries(
    context: &ExecutionContext<'_>,
    entries: &mut BTreeMap<String, Vec<u8>>,
    root: &Path,
) -> eyre::Result<()> {
    if !root.exists() {
        return Ok(());
    }
    context.assert_allowed_input(root)?;
    for path in collect_files_under(root)? {
        context.assert_allowed_input(&path)?;
        let name = relative_zip_name(root, &path)?;
        if !should_package_project_entry(&name) {
            continue;
        }
        if entries.contains_key(&name) {
            continue;
        }
        let bytes =
            fs::read(&path).wrap_err_with(|| format!("Failed to read {}", path.display()))?;
        entries.insert(name, bytes);
    }
    Ok(())
}

fn should_package_project_entry(name: &str) -> bool {
    !name.eq_ignore_ascii_case(
        "META-INF/org/apache/logging/log4j/core/config/plugins/Log4j2Plugins.dat",
    )
}

fn build_project_manifest(context: &ExecutionContext<'_>) -> String {
    let properties = &context.plan.properties;
    let mod_id = properties.get("mod_id").map_or("sfm", String::as_str);
    let mod_authors = properties.get("mod_authors").map_or("", String::as_str);
    let project_name = context
        .plan
        .worktree_path
        .file_name()
        .and_then(|name| name.to_str())
        .map_or_else(|| "sfm".to_string(), |version| format!("sfm-{version}"));
    let mod_version = properties.get("mod_version").map_or("", String::as_str);
    let timestamp = Local::now().format("%Y-%m-%dT%H:%M:%S%z").to_string();
    let mut manifest = String::new();
    let attributes = [
        ("Manifest-Version", "1.0"),
        ("Specification-Title", mod_id),
        ("Specification-Vendor", mod_authors),
        ("Specification-Version", "1"),
        ("Implementation-Title", project_name.as_str()),
        ("Implementation-Version", mod_version),
        ("Implementation-Vendor", mod_authors),
        ("Implementation-Timestamp", timestamp.as_str()),
    ];
    for (key, value) in attributes {
        append_manifest_attribute(&mut manifest, key, value);
    }
    if context.plan.loader_toolchain.kind != LoaderToolchainKind::NeoGradleUserdev {
        append_manifest_attribute(&mut manifest, "MixinConfigs", "sfm.mixins.json");
    }
    manifest.push_str("\r\n");
    manifest
}

fn append_manifest_attribute(manifest: &mut String, key: &str, value: &str) {
    manifest.push_str(key);
    manifest.push_str(": ");
    manifest.push_str(value);
    manifest.push_str("\r\n");
}

fn expand_gradle_resource_template(
    content: &str,
    properties: &BTreeMap<String, String>,
) -> eyre::Result<String> {
    let mut output = String::new();
    let mut remaining = content;
    while let Some(start) = remaining.find("${") {
        output.push_str(&remaining[..start]);
        let after_start = &remaining[start + 2..];
        let Some(end) = after_start.find('}') else {
            eyre::bail!("Unclosed resource expansion placeholder in {content:?}");
        };
        let key = after_start[..end].trim();
        let value = properties
            .get(key)
            .ok_or_else(|| eyre::eyre!("Missing resource expansion property: {key}"))?;
        output.push_str(&decode_gradle_property_value(value));
        remaining = &after_start[end + 1..];
    }
    output.push_str(remaining);
    Ok(output)
}

fn decode_gradle_property_value(value: &str) -> String {
    let mut output = String::new();
    let mut chars = value.chars();
    while let Some(character) = chars.next() {
        if character != '\\' {
            output.push(character);
            continue;
        }
        match chars.next() {
            Some('n') => output.push('\n'),
            Some('r') => output.push('\r'),
            Some('t') => output.push('\t'),
            Some('\\') | None => output.push('\\'),
            Some(other) => {
                output.push('\\');
                output.push(other);
            }
        }
    }
    output
}

fn reset_cache_directory(cache_dir: &Path, path: &Path) -> eyre::Result<()> {
    let canonical_cache = canonicalize_lenient(cache_dir)?;
    let canonical_path = canonicalize_lenient(path)?;
    if !canonical_path.starts_with(&canonical_cache) {
        eyre::bail!("Refusing to reset non-cache directory {}", path.display());
    }
    if path.exists() {
        fs::remove_dir_all(path)
            .wrap_err_with(|| format!("Failed to remove {}", path.display()))?;
    }
    fs::create_dir_all(path).wrap_err_with(|| format!("Failed to create {}", path.display()))?;
    Ok(())
}

fn cache_state_matches(
    context: &ExecutionContext<'_>,
    state_path: &Path,
    expected_state: &str,
    required_output_dirs: &[&Path],
) -> eyre::Result<bool> {
    cache_state_matches_outputs(
        context,
        state_path,
        expected_state,
        required_output_dirs,
        &[],
    )
}

fn cache_state_matches_outputs(
    context: &ExecutionContext<'_>,
    state_path: &Path,
    expected_state: &str,
    required_output_dirs: &[&Path],
    required_output_files: &[&Path],
) -> eyre::Result<bool> {
    if context.plan.refresh {
        return Ok(false);
    }
    if fs::read_to_string(state_path).unwrap_or_default() != expected_state {
        return Ok(false);
    }
    for output_dir in required_output_dirs {
        if !directory_has_files(output_dir)? {
            return Ok(false);
        }
    }
    for output_file in required_output_files {
        if !output_file.is_file() {
            return Ok(false);
        }
    }
    Ok(true)
}

fn directory_has_files(path: &Path) -> eyre::Result<bool> {
    Ok(path.is_dir() && !collect_files_under(path)?.is_empty())
}

fn input_fingerprint(
    context: &ExecutionContext<'_>,
    label: &str,
    paths: &[PathBuf],
    extras: &[String],
) -> eyre::Result<String> {
    let mut hasher = Sha1::new();
    hasher.update(b"sfm-input-fingerprint-v1\n");
    hasher.update(label.as_bytes());
    hasher.update(b"\n");
    for extra in extras {
        hasher.update(b"extra:");
        hasher.update(extra.as_bytes());
        hasher.update(b"\n");
    }
    for path in paths {
        hash_path_input(context, &mut hasher, path)?;
    }
    Ok(format!("{:x}", hasher.finalize()))
}

fn hash_path_input(
    context: &ExecutionContext<'_>,
    hasher: &mut Sha1,
    path: &Path,
) -> eyre::Result<()> {
    context.assert_allowed_input(path)?;
    let normalized = path.to_string_lossy().replace('\\', "/");
    hasher.update(b"path:");
    hasher.update(normalized.as_bytes());
    hasher.update(b"\n");

    if path.is_file() {
        hasher.update(b"file:");
        hasher.update(file_sha1(path)?.as_bytes());
        hasher.update(b"\n");
        return Ok(());
    }

    if path.is_dir() {
        hasher.update(b"dir\n");
        for file in collect_files_under(path)? {
            context.assert_allowed_input(&file)?;
            let relative = relative_zip_name(path, &file)?;
            hasher.update(b"entry:");
            hasher.update(relative.as_bytes());
            hasher.update(b":");
            hasher.update(file_sha1(&file)?.as_bytes());
            hasher.update(b"\n");
        }
        return Ok(());
    }

    hasher.update(b"missing\n");
    Ok(())
}

fn write_cache_state(path: &Path, state: &str) -> eyre::Result<()> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)?;
    }
    fs::write(path, state).wrap_err_with(|| format!("Failed to write {}", path.display()))
}

fn collect_files_under(root: &Path) -> eyre::Result<Vec<PathBuf>> {
    let mut files = Vec::new();
    if !root.exists() {
        return Ok(files);
    }
    let mut entries = fs::read_dir(root)
        .wrap_err_with(|| format!("Failed to read {}", root.display()))?
        .collect::<Result<Vec<_>, _>>()
        .wrap_err_with(|| format!("Failed to read {}", root.display()))?;
    entries.sort_by_key(std::fs::DirEntry::path);
    for entry in entries {
        let path = entry.path();
        if path.is_dir() {
            files.extend(collect_files_under(&path)?);
        } else if path.is_file() {
            files.push(path);
        }
    }
    Ok(files)
}

fn zip_name_to_path(root: &Path, name: &str) -> PathBuf {
    name.split('/')
        .filter(|part| !part.is_empty())
        .fold(root.to_path_buf(), |path, part| path.join(part))
}

fn zip_entry_has_extension(name: &str, extension: &str) -> bool {
    Path::new(name)
        .extension()
        .is_some_and(|actual| actual.eq_ignore_ascii_case(extension))
}

fn resolve_coordinates_for_classpath(
    resolver: &Resolver,
    coordinates: &[&str],
    required_for: &str,
) -> eyre::Result<Vec<PathBuf>> {
    coordinates
        .iter()
        .enumerate()
        .map(|(index, coordinate)| {
            let coordinate = MavenCoordinate::parse(coordinate)?;
            resolver
                .resolve_artifact(&format!("classpath-{index}"), &coordinate, required_for)
                .map(|artifact| artifact.cache_path)
        })
        .collect()
}

fn resolve_antlr_classpath(
    context: &ExecutionContext<'_>,
    resolver: &Resolver,
) -> eyre::Result<Vec<PathBuf>> {
    let dependency_script = context
        .plan
        .minecraft_dir
        .join("gradle")
        .join("dependencies")
        .join(&context.plan.minecraft_version)
        .join("dependencies.gradle");
    let dependencies = parse_dependency_script(&dependency_script, &context.plan.properties)?;
    let antlr_version = dependencies
        .iter()
        .find(|dependency| dependency.configuration == "antlr")
        .map_or("4.9.1", |dependency| dependency.coordinate.version.as_str());
    let coordinates = antlr_classpath_coordinates(antlr_version)?;
    let coordinate_refs = coordinates.iter().map(String::as_str).collect::<Vec<_>>();
    resolve_coordinates_for_classpath(resolver, &coordinate_refs, "ANTLR grammar generation")
}

fn antlr_classpath_coordinates(version: &str) -> eyre::Result<Vec<String>> {
    match version {
        "4.9.1" => Ok(vec![
            "org.antlr:antlr4:4.9.1".to_string(),
            "org.antlr:antlr-runtime:3.5.2".to_string(),
            "org.antlr:antlr4-runtime:4.9.1".to_string(),
            "org.antlr:ST4:4.3".to_string(),
            "org.abego.treelayout:org.abego.treelayout.core:1.0.3".to_string(),
            "org.glassfish:javax.json:1.0.4".to_string(),
        ]),
        "4.13.1" => Ok(vec![
            "org.antlr:antlr4:4.13.1".to_string(),
            "org.antlr:antlr4-runtime:4.13.1".to_string(),
            "org.antlr:antlr-runtime:3.5.3".to_string(),
            "org.antlr:ST4:4.3.4".to_string(),
            "org.abego.treelayout:org.abego.treelayout.core:1.0.3".to_string(),
            "com.ibm.icu:icu4j:72.1".to_string(),
        ]),
        _ => eyre::bail!("Unsupported ANTLR tool version: {version}"),
    }
}

fn resolve_project_compile_classpath(
    context: &ExecutionContext<'_>,
    resolver: &Resolver,
    antlr_classpath: &[PathBuf],
) -> eyre::Result<Vec<PathBuf>> {
    let mut classpath = Vec::new();
    classpath.push(loader_dev_compile_jar(context));
    classpath.extend(collect_jars(
        &context.plan.cache_dir.join("minecraft").join("libraries"),
    )?);
    classpath.extend(resolve_forge_userdev_libraries(context, resolver)?);
    classpath.extend(resolve_compile_dependencies(context, resolver)?);
    classpath.extend(collect_jars(&context.plan.cache_dir.join("dependencies"))?);
    classpath.extend(resolve_coordinates_for_classpath(
        resolver,
        &[
            "org.jetbrains:annotations:24.0.1",
            "com.google.code.findbugs:jsr305:3.0.2",
        ],
        "Project compile annotations",
    )?);
    classpath.extend(antlr_classpath.iter().cloned());
    Ok(dedup_paths_preserve_order(classpath))
}

fn dedup_paths_preserve_order(paths: Vec<PathBuf>) -> Vec<PathBuf> {
    let mut seen = BTreeSet::new();
    let mut deduped = Vec::new();
    for path in paths {
        let key = path
            .to_string_lossy()
            .replace('\\', "/")
            .to_ascii_lowercase();
        if seen.insert(key) {
            deduped.push(path);
        }
    }
    deduped
}

fn safe_path_segment(value: &str) -> String {
    value
        .chars()
        .map(|ch| {
            if ch.is_ascii_alphanumeric() || matches!(ch, '.' | '-' | '_') {
                ch
            } else {
                '_'
            }
        })
        .collect()
}

fn run_antlr(
    context: &ExecutionContext<'_>,
    classpath: &[PathBuf],
    output_dir: &Path,
) -> eyre::Result<()> {
    let grammar_root = context
        .plan
        .minecraft_dir
        .join("src")
        .join("main")
        .join("antlr");
    let grammars = [
        grammar_root.join("sfml").join("SFML.g4"),
        grammar_root.join("toml").join("TomlLexer.g4"),
        grammar_root.join("toml").join("TomlParser.g4"),
    ];
    for grammar in &grammars {
        context.assert_allowed_input(grammar)?;
    }

    let mut fingerprint_paths = classpath.to_vec();
    fingerprint_paths.extend(grammars.iter().cloned());
    let fingerprint = input_fingerprint(
        context,
        "antlr-main",
        &fingerprint_paths,
        &[
            context.plan.java.version_output.clone(),
            "-visitor -Xexact-output-dir".to_string(),
        ],
    )?;
    let state_path = output_dir.with_extension("inputs.sha1");
    let started = Instant::now();
    println!(
        "ANTLR main: start grammars={} output={}",
        grammars.len(),
        output_dir.display()
    );
    if cache_state_matches(context, &state_path, &fingerprint, &[output_dir])? {
        println!(
            "ANTLR main: reused cached outputs in {} ms",
            started.elapsed().as_millis()
        );
        return Ok(());
    }

    reset_cache_directory(&context.plan.cache_dir, output_dir)?;
    let output = Command::new(&context.plan.java.executable)
        .arg("-cp")
        .arg(join_classpath(classpath))
        .arg("org.antlr.v4.Tool")
        .arg("-visitor")
        .arg("-Xexact-output-dir")
        .arg("-o")
        .arg(output_dir)
        .args(grammars)
        .output()
        .wrap_err("Failed to run ANTLR")?;
    let log_path = output_dir.parent().unwrap_or(output_dir).join("antlr.log");
    let mut log = Vec::new();
    log.extend_from_slice(b"--- stdout ---\n");
    log.extend_from_slice(&output.stdout);
    log.extend_from_slice(b"\n--- stderr ---\n");
    log.extend_from_slice(&output.stderr);
    fs::write(&log_path, log)
        .wrap_err_with(|| format!("Failed to write {}", log_path.display()))?;
    if !output.status.success() {
        eyre::bail!(
            "ANTLR failed with {}. See {}",
            output.status,
            log_path.display()
        );
    }
    write_cache_state(&state_path, &fingerprint)?;
    println!("ANTLR main: done in {} ms", started.elapsed().as_millis());
    Ok(())
}

fn resolve_forge_userdev_libraries(
    context: &ExecutionContext<'_>,
    resolver: &Resolver,
) -> eyre::Result<Vec<PathBuf>> {
    let config: ForgeUserdevConfig = read_zip_json_entry(
        &context.artifact("forge-userdev")?.cache_path,
        "config.json",
    )?;
    let mut coordinates = Vec::new();
    coordinates.extend(config.libraries);
    coordinates.extend(config.modules);
    coordinates.sort();
    coordinates.dedup();

    coordinates
        .iter()
        .enumerate()
        .map(|(index, coordinate)| {
            let coordinate = MavenCoordinate::parse(coordinate)?;
            resolver
                .resolve_artifact(
                    &format!("forge-userdev-library-{index}"),
                    &coordinate,
                    "Forge userdev compile classpath",
                )
                .map(|artifact| artifact.cache_path)
        })
        .collect()
}

fn resolve_forge_userdev_test_libraries(
    context: &ExecutionContext<'_>,
    resolver: &Resolver,
) -> eyre::Result<Vec<PathBuf>> {
    let config: ForgeUserdevConfig = read_zip_json_entry(
        &context.artifact("forge-userdev")?.cache_path,
        "config.json",
    )?;

    config
        .test_libraries
        .iter()
        .enumerate()
        .map(|(index, coordinate)| {
            let coordinate = MavenCoordinate::parse(coordinate)?;
            resolver
                .resolve_artifact(
                    &format!("forge-userdev-test-library-{index}"),
                    &coordinate,
                    "Forge userdev game-test runtime classpath",
                )
                .map(|artifact| artifact.cache_path)
        })
        .collect()
}

fn resolve_compile_dependencies(
    context: &ExecutionContext<'_>,
    resolver: &Resolver,
) -> eyre::Result<Vec<PathBuf>> {
    let dependency_script = context
        .plan
        .minecraft_dir
        .join("gradle")
        .join("dependencies")
        .join(&context.plan.minecraft_version)
        .join("dependencies.gradle");
    let dependencies = parse_dependency_script(&dependency_script, &context.plan.properties)?;
    dependencies
        .iter()
        .filter(|dependency| {
            !dependency.fg_deobf
                && matches!(
                    dependency.configuration.as_str(),
                    "implementation" | "compileOnly" | "annotationProcessor"
                )
                && (context.plan.loader_toolchain.kind != LoaderToolchainKind::NeoGradleUserdev
                    || dependency.configuration == "annotationProcessor")
        })
        .enumerate()
        .map(|(index, dependency)| {
            resolver
                .resolve_artifact(
                    &format!("compile-dependency-{index}"),
                    &dependency.coordinate,
                    "Project compile classpath",
                )
                .map(|artifact| artifact.cache_path)
        })
        .collect()
}

fn collect_project_java_sources(
    context: &ExecutionContext<'_>,
    generated_sources: &Path,
) -> eyre::Result<Vec<PathBuf>> {
    let mut sources = collect_source_set_java_sources(context, "main")?;
    sources.extend(collect_java_sources_under(generated_sources)?);
    sources.sort();
    Ok(sources)
}

fn collect_source_set_java_sources(
    context: &ExecutionContext<'_>,
    source_set: &str,
) -> eyre::Result<Vec<PathBuf>> {
    let source_root = context
        .plan
        .minecraft_dir
        .join("src")
        .join(source_set)
        .join("java");
    let excludes = read_source_excludes(context, source_set)?;
    let mut sources = collect_java_sources_under(&source_root)?
        .into_iter()
        .filter(|path| {
            let relative = relative_zip_name(&source_root, path).unwrap_or_default();
            !is_excluded_source(&relative, &excludes)
        })
        .collect::<Vec<_>>();
    sources.sort();
    Ok(sources)
}

fn read_source_excludes(
    context: &ExecutionContext<'_>,
    source_set: &str,
) -> eyre::Result<Vec<String>> {
    let path = context
        .plan
        .minecraft_dir
        .join("gradle")
        .join("source-excludes")
        .join(&context.plan.minecraft_version)
        .join(format!("{source_set}-java.txt"));
    let excludes_text =
        fs::read_to_string(&path).wrap_err_with(|| format!("Failed to read {}", path.display()))?;
    Ok(excludes_text
        .lines()
        .map(str::trim)
        .filter(|line| !line.is_empty() && !line.starts_with('#'))
        .map(|line| line.replace('\\', "/"))
        .collect())
}

fn is_excluded_source(relative: &str, excludes: &[String]) -> bool {
    excludes.iter().any(|exclude| {
        if let Some(prefix) = exclude.strip_suffix("/**") {
            relative.starts_with(prefix)
        } else if Path::new(exclude)
            .extension()
            .is_some_and(|extension| extension.eq_ignore_ascii_case("java"))
        {
            relative == exclude
        } else {
            relative == exclude || relative.starts_with(&format!("{exclude}/"))
        }
    })
}

fn collect_java_sources_under(root: &Path) -> eyre::Result<Vec<PathBuf>> {
    let mut sources = Vec::new();
    if !root.exists() {
        return Ok(sources);
    }
    for entry in
        fs::read_dir(root).wrap_err_with(|| format!("Failed to read {}", root.display()))?
    {
        let entry = entry.wrap_err_with(|| format!("Failed to read {}", root.display()))?;
        let path = entry.path();
        if path.is_dir() {
            sources.extend(collect_java_sources_under(&path)?);
        } else if path.extension().and_then(|extension| extension.to_str()) == Some("java") {
            sources.push(path);
        }
    }
    Ok(sources)
}

fn collect_jars(root: &Path) -> eyre::Result<Vec<PathBuf>> {
    let mut jars = Vec::new();
    if !root.exists() {
        return Ok(jars);
    }
    for entry in
        fs::read_dir(root).wrap_err_with(|| format!("Failed to read {}", root.display()))?
    {
        let entry = entry.wrap_err_with(|| format!("Failed to read {}", root.display()))?;
        let path = entry.path();
        if path.is_dir() {
            jars.extend(collect_jars(&path)?);
        } else if path.extension().and_then(|extension| extension.to_str()) == Some("jar") {
            jars.push(path);
        }
    }
    Ok(jars)
}

fn write_javac_argfile(
    context: &ExecutionContext<'_>,
    argfile: &Path,
    classpath: &[PathBuf],
    sources: &[PathBuf],
    classes_dir: &Path,
) -> eyre::Result<()> {
    let refmap = context
        .plan
        .cache_dir
        .join("project")
        .join("resources")
        .join("sfm.refmap.json");
    let out_tsrg = context
        .plan
        .cache_dir
        .join("project")
        .join("compileJava-mappings.tsrg");
    let reobf_tsrg = context
        .plan
        .cache_dir
        .join("forge")
        .join(&context.plan.minecraft_version)
        .join("mappings")
        .join("official_to_srg.tsrg");
    if let Some(parent) = refmap.parent() {
        fs::create_dir_all(parent)?;
    }

    let mut args = Vec::new();
    args.extend([
        "-encoding".to_string(),
        "UTF-8".to_string(),
        "-g".to_string(),
        "-Xmaxerrs".to_string(),
        "0".to_string(),
        "-d".to_string(),
        classes_dir.display().to_string(),
        "-classpath".to_string(),
        join_classpath(classpath),
        "-sourcepath".to_string(),
        String::new(),
        "-AoutRefMapFile=".to_string() + &refmap.display().to_string(),
    ]);
    append_javac_release_args(
        &mut args,
        context.plan.java_release,
        context.plan.java.major_version,
    );
    if context.plan.loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
        args.extend([
            "-AdefaultObfuscationEnv=named".to_string(),
            "-AdisableTargetValidator=true".to_string(),
        ]);
    } else {
        args.extend([
            "-AoutTsrgFile=".to_string() + &out_tsrg.display().to_string(),
            "-AreobfTsrgFile=".to_string() + &reobf_tsrg.display().to_string(),
            "-AmappingTypes=tsrg".to_string(),
            "-AdefaultObfuscationEnv=searge".to_string(),
        ]);
    }
    args.extend(sources.iter().map(|source| source.display().to_string()));

    if let Some(parent) = argfile.parent() {
        fs::create_dir_all(parent)?;
    }
    fs::write(
        argfile,
        args.into_iter()
            .map(escape_argfile_arg)
            .collect::<Vec<_>>()
            .join("\n"),
    )
    .wrap_err_with(|| format!("Failed to write {}", argfile.display()))?;
    Ok(())
}

fn write_javac_no_ap_argfile(
    argfile: &Path,
    classpath: &[PathBuf],
    sources: &[PathBuf],
    classes_dir: &Path,
    java_release: u32,
    java_major_version: u32,
) -> eyre::Result<()> {
    let mut args = Vec::new();
    args.extend([
        "-encoding".to_string(),
        "UTF-8".to_string(),
        "-g".to_string(),
        "-Xmaxerrs".to_string(),
        "0".to_string(),
        "-proc:none".to_string(),
        "-d".to_string(),
        classes_dir.display().to_string(),
        "-classpath".to_string(),
        join_classpath(classpath),
        "-sourcepath".to_string(),
        String::new(),
    ]);
    append_javac_release_args(&mut args, java_release, java_major_version);
    args.extend(sources.iter().map(|source| source.display().to_string()));

    if let Some(parent) = argfile.parent() {
        fs::create_dir_all(parent)?;
    }
    fs::write(
        argfile,
        args.into_iter()
            .map(escape_argfile_arg)
            .collect::<Vec<_>>()
            .join("\n"),
    )
    .wrap_err_with(|| format!("Failed to write {}", argfile.display()))?;
    Ok(())
}

fn append_javac_release_args(args: &mut Vec<String>, java_release: u32, java_major_version: u32) {
    if java_major_version != java_release {
        args.extend(["--release".to_string(), java_release.to_string()]);
    }
}

fn join_classpath(classpath: &[PathBuf]) -> String {
    let separator = if cfg!(windows) { ";" } else { ":" };
    classpath
        .iter()
        .map(|path| path.display().to_string())
        .collect::<Vec<_>>()
        .join(separator)
}

fn escape_argfile_arg(arg: String) -> String {
    if arg.is_empty() {
        "\"\"".to_string()
    } else if arg.contains(' ') || arg.contains('(') || arg.contains(')') {
        format!("\"{}\"", arg.replace('\\', "\\\\").replace('"', "\\\""))
    } else {
        arg
    }
}

fn read_main_class(path: &Path) -> eyre::Result<String> {
    let bytes = fs::read(path).wrap_err_with(|| format!("Failed to read {}", path.display()))?;
    let cursor = Cursor::new(bytes);
    let mut archive = ZipArchive::new(cursor)
        .wrap_err_with(|| format!("Failed to read zip archive {}", path.display()))?;
    let mut manifest = archive
        .by_name("META-INF/MANIFEST.MF")
        .wrap_err_with(|| format!("Tool jar {} has no manifest", path.display()))?;
    let mut content = String::new();
    manifest
        .read_to_string(&mut content)
        .wrap_err_with(|| format!("Failed to read manifest from {}", path.display()))?;
    manifest_attribute(&content, "Main-Class")
        .ok_or_else(|| eyre::eyre!("Tool jar {} has no Main-Class", path.display()))
}

fn manifest_attribute(content: &str, key: &str) -> Option<String> {
    let mut unfolded: Vec<String> = Vec::new();
    for line in content.lines() {
        if let Some(continuation) = line.strip_prefix(' ') {
            if let Some(last) = unfolded.last_mut() {
                last.push_str(continuation);
            }
        } else {
            unfolded.push(line.to_string());
        }
    }

    let prefix = format!("{key}:");
    unfolded
        .iter()
        .find_map(|line| line.strip_prefix(&prefix).map(str::trim))
        .map(str::to_string)
}

fn extract_zip_entry_to_path(zip_path: &Path, entry_name: &str, output: &Path) -> eyre::Result<()> {
    let bytes =
        fs::read(zip_path).wrap_err_with(|| format!("Failed to read {}", zip_path.display()))?;
    let cursor = Cursor::new(bytes);
    let mut archive = ZipArchive::new(cursor)
        .wrap_err_with(|| format!("Failed to read zip archive {}", zip_path.display()))?;
    let mut entry = archive
        .by_name(entry_name)
        .wrap_err_with(|| format!("Archive {} missing {entry_name}", zip_path.display()))?;
    if let Some(parent) = output.parent() {
        fs::create_dir_all(parent)?;
    }
    let mut file =
        File::create(output).wrap_err_with(|| format!("Failed to create {}", output.display()))?;
    std::io::copy(&mut entry, &mut file)
        .wrap_err_with(|| format!("Failed to extract {entry_name} to {}", output.display()))?;
    Ok(())
}

fn read_zip_entry(zip_path: &Path, entry_name: &str) -> eyre::Result<Vec<u8>> {
    let bytes =
        fs::read(zip_path).wrap_err_with(|| format!("Failed to read {}", zip_path.display()))?;
    let cursor = Cursor::new(bytes);
    let mut archive = ZipArchive::new(cursor)
        .wrap_err_with(|| format!("Failed to read zip archive {}", zip_path.display()))?;
    let mut entry = archive
        .by_name(entry_name)
        .wrap_err_with(|| format!("Archive {} missing {entry_name}", zip_path.display()))?;
    let mut output = Vec::new();
    entry
        .read_to_end(&mut output)
        .wrap_err_with(|| format!("Failed to read {entry_name} from {}", zip_path.display()))?;
    Ok(output)
}

fn read_tsrg_original_classes(path: &Path) -> eyre::Result<BTreeSet<String>> {
    let content =
        fs::read_to_string(path).wrap_err_with(|| format!("Failed to read {}", path.display()))?;
    let mut classes = BTreeSet::new();
    for line in content.lines() {
        if line.trim().is_empty()
            || line.starts_with('\t')
            || line.starts_with(' ')
            || line.starts_with('#')
            || line.starts_with("tsrg")
        {
            continue;
        }
        if let Some(class_name) = line.split_whitespace().next() {
            classes.insert(format!("{}.class", class_name.replace('.', "/")));
        }
    }
    Ok(classes)
}

fn copy_filtered_jar(
    input: &Path,
    output: &Path,
    allowed_entries: &BTreeSet<String>,
) -> eyre::Result<()> {
    if let Some(parent) = output.parent() {
        fs::create_dir_all(parent)?;
    }
    let bytes = fs::read(input).wrap_err_with(|| format!("Failed to read {}", input.display()))?;
    let cursor = Cursor::new(bytes);
    let mut archive = ZipArchive::new(cursor)
        .wrap_err_with(|| format!("Failed to open jar {}", input.display()))?;
    let file =
        File::create(output).wrap_err_with(|| format!("Failed to create {}", output.display()))?;
    let mut writer = ZipWriter::new(file);
    let options = SimpleFileOptions::default().compression_method(CompressionMethod::Deflated);

    for index in 0..archive.len() {
        let mut entry = archive
            .by_index(index)
            .wrap_err_with(|| format!("Failed to read jar entry #{index}"))?;
        let name = entry.name().replace('\\', "/");
        if !allowed_entries.contains(&name) {
            continue;
        }
        let mut bytes = Vec::new();
        entry
            .read_to_end(&mut bytes)
            .wrap_err_with(|| format!("Failed to read jar entry {name}"))?;
        writer
            .start_file(name, options)
            .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
        writer
            .write_all(&bytes)
            .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
    }

    writer
        .finish()
        .wrap_err_with(|| format!("Failed to finish {}", output.display()))?;
    Ok(())
}

fn write_minecraft_libraries_cfg(
    context: &ExecutionContext<'_>,
    client: &Client,
    output: &Path,
) -> eyre::Result<()> {
    let version_json_path = context
        .plan
        .cache_dir
        .join("minecraft")
        .join(format!("{}.json", context.plan.minecraft_version));
    let version_json: MinecraftVersionJson = read_json_file(&version_json_path)?;
    let libraries_root = context.plan.cache_dir.join("minecraft").join("libraries");
    let mut lines = Vec::new();

    for library in version_json.libraries {
        let Some(artifact) = library.downloads.and_then(|downloads| downloads.artifact) else {
            continue;
        };
        let library_path =
            libraries_root.join(artifact.path.replace('/', std::path::MAIN_SEPARATOR_STR));
        download_to_path(client, &artifact.url, &library_path)?;
        context.assert_allowed_input(&library_path)?;
        lines.push(format!(
            "-e={}",
            dunce::canonicalize(&library_path)?.display()
        ));
    }

    if let Some(parent) = output.parent() {
        fs::create_dir_all(parent)?;
    }
    lines.sort();
    fs::write(output, format!("{}\n", lines.join("\n")))
        .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
    Ok(())
}

fn inject_mcp_sources(mcp_zip: &Path, source_jar: &Path, output: &Path) -> eyre::Result<()> {
    if let Some(parent) = output.parent() {
        fs::create_dir_all(parent)?;
    }
    let source_bytes = fs::read(source_jar)
        .wrap_err_with(|| format!("Failed to read {}", source_jar.display()))?;
    let mut source_archive = ZipArchive::new(Cursor::new(source_bytes))
        .wrap_err_with(|| format!("Failed to open {}", source_jar.display()))?;
    let output_file =
        File::create(output).wrap_err_with(|| format!("Failed to create {}", output.display()))?;
    let mut writer = ZipWriter::new(output_file);
    let options = SimpleFileOptions::default().compression_method(CompressionMethod::Deflated);
    let mut written = BTreeSet::new();

    for index in 0..source_archive.len() {
        let mut entry = source_archive
            .by_index(index)
            .wrap_err_with(|| format!("Failed to read source entry #{index}"))?;
        let name = entry.name().replace('\\', "/");
        if name.ends_with('/') {
            continue;
        }
        let mut bytes = Vec::new();
        entry
            .read_to_end(&mut bytes)
            .wrap_err_with(|| format!("Failed to read source entry {name}"))?;
        writer.start_file(&name, options)?;
        writer.write_all(&bytes)?;
        written.insert(name);
    }

    let mcp_bytes =
        fs::read(mcp_zip).wrap_err_with(|| format!("Failed to read {}", mcp_zip.display()))?;
    let mut mcp_archive = ZipArchive::new(Cursor::new(mcp_bytes))
        .wrap_err_with(|| format!("Failed to open {}", mcp_zip.display()))?;
    for index in 0..mcp_archive.len() {
        let mut entry = mcp_archive
            .by_index(index)
            .wrap_err_with(|| format!("Failed to read MCP entry #{index}"))?;
        let name = entry.name().replace('\\', "/");
        let Some(output_name) = name.strip_prefix("config/inject/") else {
            continue;
        };
        if output_name.is_empty() || output_name.ends_with('/') || written.contains(output_name) {
            continue;
        }
        let mut bytes = Vec::new();
        entry
            .read_to_end(&mut bytes)
            .wrap_err_with(|| format!("Failed to read MCP inject entry {name}"))?;
        writer.start_file(output_name, options)?;
        writer.write_all(&bytes)?;
        written.insert(output_name.to_string());
    }

    writer
        .finish()
        .wrap_err_with(|| format!("Failed to finish {}", output.display()))?;
    Ok(())
}

fn apply_mcp_joined_patches(
    context: &ExecutionContext<'_>,
    mcp_zip: &Path,
    source_jar: &Path,
    output: &Path,
    mcp_root: &Path,
) -> eyre::Result<()> {
    let patch_root = mcp_root.join("patch");
    if let Some(parent) = output.parent() {
        fs::create_dir_all(parent)?;
    }
    let rejects = patch_root.join("rejects.zip");
    context.run_java_tool(
        "tool-diffpatch",
        &[],
        &[
            "--patch".to_string(),
            "--mode".to_string(),
            "OFFSET".to_string(),
            "--archive".to_string(),
            "ZIP".to_string(),
            "--archive-rejects".to_string(),
            "ZIP".to_string(),
            "--prefix".to_string(),
            "patches/joined/".to_string(),
            "--output".to_string(),
            output.display().to_string(),
            "--reject".to_string(),
            rejects.display().to_string(),
            source_jar.display().to_string(),
            mcp_zip.display().to_string(),
        ],
        &patch_root,
    )?;
    Ok(())
}

fn merge_zip_archives(inputs: &[PathBuf], output: &Path) -> eyre::Result<()> {
    if let Some(parent) = output.parent() {
        fs::create_dir_all(parent)?;
    }
    let output_file =
        File::create(output).wrap_err_with(|| format!("Failed to create {}", output.display()))?;
    let mut writer = ZipWriter::new(output_file);
    let options = SimpleFileOptions::default().compression_method(CompressionMethod::Deflated);
    let mut written = BTreeSet::new();

    for input in inputs {
        let bytes =
            fs::read(input).wrap_err_with(|| format!("Failed to read {}", input.display()))?;
        let mut archive = ZipArchive::new(Cursor::new(bytes))
            .wrap_err_with(|| format!("Failed to open {}", input.display()))?;
        for index in 0..archive.len() {
            let mut entry = archive
                .by_index(index)
                .wrap_err_with(|| format!("Failed to read {} entry #{index}", input.display()))?;
            let name = entry.name().replace('\\', "/");
            if name.ends_with('/')
                || name.eq_ignore_ascii_case("META-INF/MANIFEST.MF")
                || zip_entry_has_extension(&name, "SF")
                || zip_entry_has_extension(&name, "RSA")
                || !written.insert(name.clone())
            {
                continue;
            }
            let mut bytes = Vec::new();
            entry.read_to_end(&mut bytes).wrap_err_with(|| {
                format!("Failed to read entry {name} from {}", input.display())
            })?;
            writer
                .start_file(name, options)
                .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
            writer
                .write_all(&bytes)
                .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
        }
    }

    writer
        .finish()
        .wrap_err_with(|| format!("Failed to finish {}", output.display()))?;
    Ok(())
}

fn write_run_forge_dev_jar(
    input: &Path,
    forge_universal_jar: &Path,
    output: &Path,
) -> eyre::Result<()> {
    let manifest = forge_runtime_manifest(forge_universal_jar)?;
    write_run_loader_dev_jar(input, &manifest, output)?;
    println!("Generated Forge userdev runtime jar: {}", output.display());
    Ok(())
}

fn write_run_neoforge_dev_jar(
    input: &Path,
    neoforge_universal_jar: &Path,
    output: &Path,
) -> eyre::Result<()> {
    let manifest = neoforge_runtime_manifest(neoforge_universal_jar)?;
    write_run_loader_dev_jar(input, &manifest, output)?;
    println!(
        "Generated NeoForge userdev runtime jar: {}",
        output.display()
    );
    Ok(())
}

fn write_run_neoforge_minecraft_dev_jar(
    input: &Path,
    neoforge_universal_jar: &Path,
    output: &Path,
) -> eyre::Result<()> {
    if let Some(parent) = output.parent() {
        fs::create_dir_all(parent)?;
    }

    let manifest = minecraft_runtime_manifest(input)?;
    let neoforge_entries = zip_entry_names(neoforge_universal_jar)?;
    let bytes = fs::read(input).wrap_err_with(|| format!("Failed to read {}", input.display()))?;
    let mut archive = ZipArchive::new(Cursor::new(bytes)).wrap_err_with(|| {
        format!(
            "Failed to open NeoForge Minecraft runtime jar {}",
            input.display()
        )
    })?;
    let mut names = BTreeSet::new();
    for index in 0..archive.len() {
        let entry = archive.by_index(index).wrap_err_with(|| {
            format!("Failed to read NeoForge Minecraft runtime jar entry #{index}")
        })?;
        let name = entry.name().replace('\\', "/");
        if should_keep_split_minecraft_runtime_entry(&name, &neoforge_entries) {
            names.insert(name);
        }
    }

    let output_file =
        File::create(output).wrap_err_with(|| format!("Failed to create {}", output.display()))?;
    let mut writer = ZipWriter::new(output_file);
    let options = SimpleFileOptions::default().compression_method(CompressionMethod::Deflated);
    writer
        .start_file("META-INF/MANIFEST.MF", options)
        .wrap_err_with(|| format!("Failed to write manifest to {}", output.display()))?;
    writer
        .write_all(&manifest)
        .wrap_err_with(|| format!("Failed to write manifest to {}", output.display()))?;

    for name in names {
        let mut entry = archive.by_name(&name).wrap_err_with(|| {
            format!("Failed to read NeoForge Minecraft runtime jar entry {name}")
        })?;
        let mut bytes = Vec::new();
        entry.read_to_end(&mut bytes).wrap_err_with(|| {
            format!("Failed to read NeoForge Minecraft runtime jar entry {name}")
        })?;
        writer
            .start_file(name, options)
            .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
        writer
            .write_all(&bytes)
            .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
    }

    writer
        .finish()
        .wrap_err_with(|| format!("Failed to finish {}", output.display()))?;
    println!(
        "Generated NeoForge Minecraft runtime jar: {}",
        output.display()
    );
    Ok(())
}

fn should_keep_split_minecraft_runtime_entry(
    name: &str,
    neoforge_entries: &BTreeSet<String>,
) -> bool {
    if name.ends_with('/')
        || name.eq_ignore_ascii_case("META-INF/MANIFEST.MF")
        || is_signature_file(name)
        || is_neoforge_specific_runtime_entry(name)
    {
        return false;
    }

    if is_neoforge_mod_marker(name) {
        return true;
    }

    if neoforge_entries.contains(name) && zip_entry_has_extension(name, "class") {
        return false;
    }

    true
}

fn write_run_loader_dev_jar(input: &Path, manifest: &[u8], output: &Path) -> eyre::Result<()> {
    if let Some(parent) = output.parent() {
        fs::create_dir_all(parent)?;
    }

    let bytes = fs::read(input).wrap_err_with(|| format!("Failed to read {}", input.display()))?;
    let mut archive = ZipArchive::new(Cursor::new(bytes))
        .wrap_err_with(|| format!("Failed to open loader runtime jar {}", input.display()))?;
    let mut names = BTreeSet::new();
    for index in 0..archive.len() {
        let entry = archive
            .by_index(index)
            .wrap_err_with(|| format!("Failed to read loader runtime jar entry #{index}"))?;
        let name = entry.name().replace('\\', "/");
        if !name.ends_with('/')
            && !name.eq_ignore_ascii_case("META-INF/MANIFEST.MF")
            && !is_signature_file(&name)
        {
            names.insert(name);
        }
    }

    let output_file =
        File::create(output).wrap_err_with(|| format!("Failed to create {}", output.display()))?;
    let mut writer = ZipWriter::new(output_file);
    let options = SimpleFileOptions::default().compression_method(CompressionMethod::Deflated);
    writer
        .start_file("META-INF/MANIFEST.MF", options)
        .wrap_err_with(|| format!("Failed to write manifest to {}", output.display()))?;
    writer
        .write_all(manifest)
        .wrap_err_with(|| format!("Failed to write manifest to {}", output.display()))?;

    for name in names {
        let mut entry = archive
            .by_name(&name)
            .wrap_err_with(|| format!("Failed to read loader runtime jar entry {name}"))?;
        let mut bytes = Vec::new();
        entry
            .read_to_end(&mut bytes)
            .wrap_err_with(|| format!("Failed to read loader runtime jar entry {name}"))?;
        writer
            .start_file(name, options)
            .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
        writer
            .write_all(&bytes)
            .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
    }

    writer
        .finish()
        .wrap_err_with(|| format!("Failed to finish {}", output.display()))?;
    Ok(())
}

fn zip_entry_names(path: &Path) -> eyre::Result<BTreeSet<String>> {
    let bytes = fs::read(path).wrap_err_with(|| format!("Failed to read {}", path.display()))?;
    let mut archive = ZipArchive::new(Cursor::new(bytes))
        .wrap_err_with(|| format!("Failed to open jar {}", path.display()))?;
    let mut names = BTreeSet::new();
    for index in 0..archive.len() {
        let entry = archive
            .by_index(index)
            .wrap_err_with(|| format!("Failed to read {} entry #{index}", path.display()))?;
        let name = entry.name().replace('\\', "/");
        if !name.ends_with('/') {
            names.insert(name);
        }
    }
    Ok(names)
}

fn forge_runtime_manifest(forge_universal_jar: &Path) -> eyre::Result<Vec<u8>> {
    let manifest = read_zip_entry(forge_universal_jar, "META-INF/MANIFEST.MF")?;
    let manifest = String::from_utf8(manifest).wrap_err_with(|| {
        format!(
            "Forge universal manifest was not UTF-8: {}",
            forge_universal_jar.display()
        )
    })?;
    let normalized = manifest.replace("\r\n", "\n");
    let sections = normalized.split("\n\n").collect::<Vec<_>>();
    let main_section = sections
        .first()
        .copied()
        .ok_or_else(|| eyre::eyre!("Forge universal manifest was empty"))?;
    let required_sections = [
        "net/minecraftforge/fml/loading/",
        "net/minecraftforge/versions/forge/",
        "net/minecraftforge/versions/mcp/",
    ];
    let mut output_sections = vec![strip_manifest_digests(main_section)];

    for required in required_sections {
        let Some(section) = sections
            .iter()
            .copied()
            .find(|section| manifest_section_name(section) == Some(required))
        else {
            if manifest_attribute(&manifest, "FML-System-Mods").as_deref() == Some("forge") {
                let output_sections = sections
                    .iter()
                    .copied()
                    .map(strip_manifest_digests)
                    .filter(|section| !section.trim().is_empty())
                    .collect::<Vec<_>>();
                return Ok(format!("{}\r\n\r\n", output_sections.join("\r\n\r\n")).into_bytes());
            }
            eyre::bail!(
                "Forge universal manifest {} did not contain package section {required}",
                forge_universal_jar.display()
            );
        };
        output_sections.push(strip_manifest_digests(section));
    }

    Ok(format!("{}\r\n\r\n", output_sections.join("\r\n\r\n")).into_bytes())
}

fn minecraft_runtime_manifest(input: &Path) -> eyre::Result<Vec<u8>> {
    let manifest = match read_zip_entry(input, "META-INF/MANIFEST.MF") {
        Ok(manifest) => String::from_utf8(manifest).wrap_err_with(|| {
            format!(
                "Minecraft runtime manifest was not UTF-8: {}",
                input.display()
            )
        })?,
        Err(_) => "Manifest-Version: 1.0\n".to_string(),
    };
    let normalized = manifest.replace("\r\n", "\n");
    let sections = normalized
        .split("\n\n")
        .map(strip_manifest_digests)
        .map(|section| strip_manifest_attribute(&section, "FML-System-Mods"))
        .filter(|section| !section.trim().is_empty())
        .collect::<Vec<_>>();
    Ok(format!("{}\r\n\r\n", sections.join("\r\n\r\n")).into_bytes())
}

fn neoforge_runtime_manifest(neoforge_universal_jar: &Path) -> eyre::Result<Vec<u8>> {
    let manifest = read_zip_entry(neoforge_universal_jar, "META-INF/MANIFEST.MF")?;
    let manifest = String::from_utf8(manifest).wrap_err_with(|| {
        format!(
            "NeoForge universal manifest was not UTF-8: {}",
            neoforge_universal_jar.display()
        )
    })?;
    if manifest_attribute(&manifest, "FML-System-Mods").as_deref() != Some("neoforge") {
        eyre::bail!(
            "NeoForge universal manifest {} did not declare FML-System-Mods: neoforge",
            neoforge_universal_jar.display()
        );
    }
    let normalized = manifest.replace("\r\n", "\n");
    let output_sections = normalized
        .split("\n\n")
        .map(strip_manifest_digests)
        .filter(|section| !section.trim().is_empty())
        .collect::<Vec<_>>();

    Ok(format!("{}\r\n\r\n", output_sections.join("\r\n\r\n")).into_bytes())
}

fn neoforge_requires_split_runtime(neoforge_universal_jar: &Path) -> eyre::Result<bool> {
    let manifest = read_zip_entry(neoforge_universal_jar, "META-INF/MANIFEST.MF")?;
    let manifest = String::from_utf8(manifest).wrap_err_with(|| {
        format!(
            "NeoForge universal manifest was not UTF-8: {}",
            neoforge_universal_jar.display()
        )
    })?;
    Ok(
        manifest_attribute(&manifest, "FML-System-Mods").as_deref() == Some("neoforge")
            && !manifest.contains("\nName: net/neoforged/neoforge/versions/neoform/"),
    )
}

fn manifest_section_name(section: &str) -> Option<&str> {
    section.lines().next()?.strip_prefix("Name: ")
}

fn strip_manifest_digests(section: &str) -> String {
    let mut output = Vec::new();
    let mut dropping_attribute = false;
    for line in section.lines() {
        if line.starts_with(' ') {
            if !dropping_attribute {
                output.push(line);
            }
            continue;
        }
        dropping_attribute = line.contains("-Digest:");
        if !dropping_attribute {
            output.push(line);
        }
    }
    output.join("\r\n")
}

fn strip_manifest_attribute(section: &str, attribute: &str) -> String {
    let mut output = Vec::new();
    let mut dropping_attribute = false;
    let prefix = format!("{attribute}:");
    for line in section.lines() {
        if line.starts_with(' ') {
            if !dropping_attribute {
                output.push(line);
            }
            continue;
        }
        dropping_attribute = line.starts_with(&prefix);
        if !dropping_attribute {
            output.push(line);
        }
    }
    output.join("\r\n")
}

fn is_neoforge_specific_runtime_entry(name: &str) -> bool {
    name.starts_with("net/neoforged/neoforge/")
        || name.starts_with("META-INF/services/")
        || name.eq_ignore_ascii_case("META-INF/neoforged.mods.toml")
        || name.eq_ignore_ascii_case("META-INF/mods.toml")
        || name.starts_with("data/neoforge/")
        || name.starts_with("assets/neoforge/")
}

fn is_neoforge_mod_marker(name: &str) -> bool {
    name.eq_ignore_ascii_case("META-INF/neoforge.mods.toml")
}

fn write_client_extra_jar(client_jar: &Path, output: &Path) -> eyre::Result<()> {
    if let Some(parent) = output.parent() {
        fs::create_dir_all(parent)?;
    }

    let bytes = fs::read(client_jar)
        .wrap_err_with(|| format!("Failed to read {}", client_jar.display()))?;
    let mut archive = ZipArchive::new(Cursor::new(bytes))
        .wrap_err_with(|| format!("Failed to open client jar {}", client_jar.display()))?;
    let mut names = BTreeSet::new();
    for index in 0..archive.len() {
        let entry = archive
            .by_index(index)
            .wrap_err_with(|| format!("Failed to read client jar entry #{index}"))?;
        let name = entry.name().replace('\\', "/");
        if is_client_extra_entry(&name) {
            names.insert(name);
        }
    }

    let output_file =
        File::create(output).wrap_err_with(|| format!("Failed to create {}", output.display()))?;
    let mut writer = ZipWriter::new(output_file);
    let options = SimpleFileOptions::default().compression_method(CompressionMethod::Deflated);
    writer
        .start_file("META-INF/MANIFEST.MF", options)
        .wrap_err_with(|| format!("Failed to write manifest to {}", output.display()))?;
    writer
        .write_all(b"Manifest-Version: 1.0\r\nMinecraft-Dists: server client\r\n\r\n")
        .wrap_err_with(|| format!("Failed to write manifest to {}", output.display()))?;

    for name in names {
        let mut entry = archive
            .by_name(&name)
            .wrap_err_with(|| format!("Failed to read client jar entry {name}"))?;
        let mut bytes = Vec::new();
        entry
            .read_to_end(&mut bytes)
            .wrap_err_with(|| format!("Failed to read client jar entry {name}"))?;
        writer
            .start_file(name, options)
            .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
        writer
            .write_all(&bytes)
            .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
    }

    writer
        .finish()
        .wrap_err_with(|| format!("Failed to finish {}", output.display()))?;
    println!("Generated client-extra jar: {}", output.display());
    Ok(())
}

fn is_client_extra_entry(name: &str) -> bool {
    !name.ends_with('/')
        && !zip_entry_has_extension(name, "class")
        && !name.eq_ignore_ascii_case("META-INF/MANIFEST.MF")
        && !is_signature_file(name)
}

fn is_signature_file(name: &str) -> bool {
    name.starts_with("META-INF/")
        && (zip_entry_has_extension(name, "SF")
            || zip_entry_has_extension(name, "RSA")
            || zip_entry_has_extension(name, "EC")
            || zip_entry_has_extension(name, "DSA"))
}

fn patch_inner_class_access_in_jar(
    input: &Path,
    output: &Path,
    outer_class: &str,
    inner_class: &str,
) -> eyre::Result<()> {
    if let Some(parent) = output.parent() {
        fs::create_dir_all(parent)?;
    }
    let bytes = fs::read(input).wrap_err_with(|| format!("Failed to read {}", input.display()))?;
    let mut archive = ZipArchive::new(Cursor::new(bytes))
        .wrap_err_with(|| format!("Failed to open jar {}", input.display()))?;
    let output_file =
        File::create(output).wrap_err_with(|| format!("Failed to create {}", output.display()))?;
    let mut writer = ZipWriter::new(output_file);
    let options = SimpleFileOptions::default().compression_method(CompressionMethod::Deflated);

    for index in 0..archive.len() {
        let mut entry = archive
            .by_index(index)
            .wrap_err_with(|| format!("Failed to read {} entry #{index}", input.display()))?;
        let name = entry.name().replace('\\', "/");
        if name.ends_with('/') {
            continue;
        }
        let mut entry_bytes = Vec::new();
        entry
            .read_to_end(&mut entry_bytes)
            .wrap_err_with(|| format!("Failed to read entry {name} from {}", input.display()))?;
        if zip_entry_has_extension(&name, "class") {
            patch_inner_class_access_in_class_file(&mut entry_bytes, outer_class, inner_class)
                .wrap_err_with(|| format!("Failed to patch class access in {name}"))?;
        }
        writer
            .start_file(name, options)
            .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
        writer
            .write_all(&entry_bytes)
            .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
    }

    writer
        .finish()
        .wrap_err_with(|| format!("Failed to finish {}", output.display()))?;
    Ok(())
}

fn patch_inner_class_access_in_class_file(
    bytes: &mut [u8],
    outer_class: &str,
    inner_class: &str,
) -> eyre::Result<bool> {
    const ACC_PUBLIC: u16 = 0x0001;
    const ACC_PRIVATE: u16 = 0x0002;
    const ACC_PROTECTED: u16 = 0x0004;

    if bytes.len() < 10 || read_u32(bytes, 0)? != 0xCAFE_BABE {
        return Ok(false);
    }

    let parsed = parse_class_constant_pool(bytes)?;
    let mut changed = false;
    let access_flags_offset = parsed.after_constant_pool;
    let this_class = read_u16(bytes, access_flags_offset + 2)?;
    if parsed.class_name(this_class) == Some(inner_class) {
        let flags = read_u16(bytes, access_flags_offset)?;
        let patched = (flags | ACC_PUBLIC) & !ACC_PRIVATE & !ACC_PROTECTED;
        if patched != flags {
            write_u16(bytes, access_flags_offset, patched)?;
            changed = true;
        }
    }

    let mut cursor = parsed.after_constant_pool + 6;
    let interfaces_count = read_u16(bytes, cursor)? as usize;
    cursor += 2 + interfaces_count * 2;
    cursor = skip_class_members(bytes, cursor)?;
    cursor = skip_class_members(bytes, cursor)?;

    let attributes_count = read_u16(bytes, cursor)? as usize;
    cursor += 2;
    for _ in 0..attributes_count {
        let attribute_name_index = read_u16(bytes, cursor)?;
        let attribute_length = read_u32(bytes, cursor + 2)? as usize;
        let attribute_info = cursor + 6;
        let attribute_end = attribute_info
            .checked_add(attribute_length)
            .ok_or_else(|| eyre::eyre!("Class attribute length overflow"))?;
        if attribute_end > bytes.len() {
            eyre::bail!("Class attribute extends past end of file");
        }

        if parsed.utf8(attribute_name_index) == Some("InnerClasses") {
            let inner_classes_count = read_u16(bytes, attribute_info)? as usize;
            let mut inner_cursor = attribute_info + 2;
            for _ in 0..inner_classes_count {
                let inner_class_index = read_u16(bytes, inner_cursor)?;
                let outer_class_index = read_u16(bytes, inner_cursor + 2)?;
                let access_offset = inner_cursor + 6;
                let inner_name_matches = parsed.class_name(inner_class_index) == Some(inner_class);
                let outer_name_matches = outer_class_index == 0
                    || parsed.class_name(outer_class_index) == Some(outer_class);
                if inner_name_matches && outer_name_matches {
                    let flags = read_u16(bytes, access_offset)?;
                    let patched = (flags | ACC_PUBLIC) & !ACC_PRIVATE & !ACC_PROTECTED;
                    if patched != flags {
                        write_u16(bytes, access_offset, patched)?;
                        changed = true;
                    }
                }
                inner_cursor += 8;
            }
        }
        cursor = attribute_end;
    }

    Ok(changed)
}

#[derive(Debug)]
struct ParsedClassConstantPool {
    entries: Vec<ClassConstant>,
    after_constant_pool: usize,
}

impl ParsedClassConstantPool {
    fn utf8(&self, index: u16) -> Option<&str> {
        match self.entries.get(index as usize)? {
            ClassConstant::Utf8(value) => Some(value.as_str()),
            ClassConstant::Other | ClassConstant::Class { .. } => None,
        }
    }

    fn class_name(&self, index: u16) -> Option<&str> {
        let ClassConstant::Class { name_index } = self.entries.get(index as usize)? else {
            return None;
        };
        self.utf8(*name_index)
    }
}

#[derive(Debug)]
enum ClassConstant {
    Utf8(String),
    Class { name_index: u16 },
    Other,
}

fn parse_class_constant_pool(bytes: &[u8]) -> eyre::Result<ParsedClassConstantPool> {
    let constant_pool_count = read_u16(bytes, 8)? as usize;
    let mut entries = Vec::with_capacity(constant_pool_count);
    entries.push(ClassConstant::Other);
    let mut cursor = 10;
    let mut index = 1;

    while index < constant_pool_count {
        let tag = *bytes
            .get(cursor)
            .ok_or_else(|| eyre::eyre!("Class constant pool is truncated"))?;
        cursor += 1;
        match tag {
            1 => {
                let length = read_u16(bytes, cursor)? as usize;
                cursor += 2;
                let end = cursor
                    .checked_add(length)
                    .ok_or_else(|| eyre::eyre!("Utf8 constant length overflow"))?;
                if end > bytes.len() {
                    eyre::bail!("Utf8 constant extends past end of class file");
                }
                let value = String::from_utf8_lossy(&bytes[cursor..end]).into_owned();
                entries.push(ClassConstant::Utf8(value));
                cursor = end;
            }
            3 | 4 | 9 | 10 | 11 | 12 | 17 | 18 => {
                cursor += 4;
                entries.push(ClassConstant::Other);
            }
            5 | 6 => {
                cursor += 8;
                entries.push(ClassConstant::Other);
                entries.push(ClassConstant::Other);
                index += 1;
            }
            7 => {
                let name_index = read_u16(bytes, cursor)?;
                cursor += 2;
                entries.push(ClassConstant::Class { name_index });
            }
            8 | 16 | 19 | 20 => {
                cursor += 2;
                entries.push(ClassConstant::Other);
            }
            15 => {
                cursor += 3;
                entries.push(ClassConstant::Other);
            }
            _ => eyre::bail!("Unsupported class constant pool tag {tag}"),
        }
        if cursor > bytes.len() {
            eyre::bail!("Class constant pool extends past end of file");
        }
        index += 1;
    }

    Ok(ParsedClassConstantPool {
        entries,
        after_constant_pool: cursor,
    })
}

fn skip_class_members(bytes: &[u8], mut cursor: usize) -> eyre::Result<usize> {
    let member_count = read_u16(bytes, cursor)? as usize;
    cursor += 2;
    for _ in 0..member_count {
        cursor += 6;
        let attributes_count = read_u16(bytes, cursor)? as usize;
        cursor += 2;
        for _ in 0..attributes_count {
            let attribute_length = read_u32(bytes, cursor + 2)? as usize;
            cursor = cursor
                .checked_add(6)
                .and_then(|value| value.checked_add(attribute_length))
                .ok_or_else(|| eyre::eyre!("Member attribute length overflow"))?;
            if cursor > bytes.len() {
                eyre::bail!("Member attribute extends past end of class file");
            }
        }
    }
    Ok(cursor)
}

fn read_u16(bytes: &[u8], offset: usize) -> eyre::Result<u16> {
    let value = bytes
        .get(offset..offset + 2)
        .ok_or_else(|| eyre::eyre!("Class file ended before u16 at offset {offset}"))?;
    Ok(u16::from_be_bytes([value[0], value[1]]))
}

fn write_u16(bytes: &mut [u8], offset: usize, value: u16) -> eyre::Result<()> {
    let destination = bytes
        .get_mut(offset..offset + 2)
        .ok_or_else(|| eyre::eyre!("Class file ended before u16 at offset {offset}"))?;
    destination.copy_from_slice(&value.to_be_bytes());
    Ok(())
}

fn read_u32(bytes: &[u8], offset: usize) -> eyre::Result<u32> {
    let value = bytes
        .get(offset..offset + 4)
        .ok_or_else(|| eyre::eyre!("Class file ended before u32 at offset {offset}"))?;
    Ok(u32::from_be_bytes([value[0], value[1], value[2], value[3]]))
}

#[derive(Debug)]
struct MojangClassMapping {
    official_slash: String,
    obf: String,
    fields: BTreeMap<String, String>,
    methods: BTreeMap<(String, String), MojangMethodMapping>,
}

#[derive(Clone, Debug)]
struct MojangMethodMapping {
    official_name: String,
    official_descriptor: String,
}

#[derive(Debug)]
struct SrgClassMapping {
    fields: BTreeMap<String, String>,
    methods: BTreeMap<(String, String), SrgMethodMapping>,
}

#[derive(Debug)]
struct SrgMethodMapping {
    srg_name: String,
    parameters: BTreeMap<usize, String>,
    is_static: bool,
}

type ParchmentParameters = BTreeMap<String, BTreeMap<(String, String), BTreeMap<usize, String>>>;

#[derive(Debug, Facet)]
struct ParchmentData {
    #[facet(default)]
    classes: Vec<ParchmentClass>,
}

#[derive(Debug, Facet)]
struct ParchmentClass {
    name: String,
    #[facet(default)]
    methods: Vec<ParchmentMethod>,
}

#[derive(Debug, Facet)]
struct ParchmentMethod {
    name: String,
    descriptor: String,
    #[facet(default)]
    parameters: Vec<ParchmentParameter>,
}

#[derive(Debug, Facet)]
struct ParchmentParameter {
    index: usize,
    name: String,
}

fn generate_mojang_tsrg_mappings(
    merged_mcp_mappings: &Path,
    mojang_mapping_paths: &[PathBuf],
    parchment_parameters: Option<&ParchmentParameters>,
    obf_to_official_output: &Path,
    srg_to_official_output: &Path,
    official_to_srg_output: &Path,
) -> eyre::Result<()> {
    let mojang = read_mojang_mappings(mojang_mapping_paths)?;
    let srg = read_srg_member_mappings(merged_mcp_mappings)?;

    let mut obf_to_official = String::from("tsrg2 left right\n");
    let mut srg_to_official = String::from("tsrg2 left right\n");
    let mut official_to_srg = String::from("tsrg2 left right\n");

    for class in mojang.values() {
        writeln!(obf_to_official, "{} {}", class.obf, class.official_slash)?;
        writeln!(
            srg_to_official,
            "{} {}",
            class.official_slash, class.official_slash
        )?;
        writeln!(
            official_to_srg,
            "{} {}",
            class.official_slash, class.official_slash
        )?;

        let srg_class = srg.get(&class.official_slash);
        for (obf_name, official_name) in &class.fields {
            writeln!(obf_to_official, "\t{obf_name} {official_name}")?;
            if let Some(srg_name) = srg_class.and_then(|mapping| mapping.fields.get(obf_name)) {
                writeln!(srg_to_official, "\t{srg_name} {official_name}")?;
                writeln!(official_to_srg, "\t{official_name} {srg_name}")?;
            }
        }

        for ((obf_name, obf_descriptor), method) in &class.methods {
            writeln!(
                obf_to_official,
                "\t{} {} {}",
                obf_name, obf_descriptor, method.official_name
            )?;
            let candidate_descriptors =
                [obf_descriptor.clone(), method.official_descriptor.clone()];
            if let Some(srg_method) = srg_class.and_then(|mapping| {
                find_srg_method_mapping(
                    mapping,
                    obf_name,
                    &method.official_name,
                    &candidate_descriptors,
                )
            }) {
                writeln!(
                    srg_to_official,
                    "\t{} {} {}",
                    srg_method.srg_name, method.official_descriptor, method.official_name
                )?;
                write_parameter_mappings(
                    &mut srg_to_official,
                    &class.official_slash,
                    method,
                    srg_method,
                    parchment_parameters,
                    ParameterDirection::SrgToOfficial,
                )?;
                writeln!(
                    official_to_srg,
                    "\t{} {} {}",
                    method.official_name, method.official_descriptor, srg_method.srg_name
                )?;
                write_parameter_mappings(
                    &mut official_to_srg,
                    &class.official_slash,
                    method,
                    srg_method,
                    parchment_parameters,
                    ParameterDirection::OfficialToSrg,
                )?;
            }
        }
    }

    if let Some(parent) = obf_to_official_output.parent() {
        fs::create_dir_all(parent)?;
    }
    fs::write(obf_to_official_output, obf_to_official)
        .wrap_err_with(|| format!("Failed to write {}", obf_to_official_output.display()))?;
    fs::write(srg_to_official_output, srg_to_official)
        .wrap_err_with(|| format!("Failed to write {}", srg_to_official_output.display()))?;
    fs::write(official_to_srg_output, official_to_srg)
        .wrap_err_with(|| format!("Failed to write {}", official_to_srg_output.display()))?;
    Ok(())
}

fn write_runtime_mcp_csv_mappings(srg_to_named: &Path, output: &Path) -> eyre::Result<()> {
    let content = fs::read_to_string(srg_to_named)
        .wrap_err_with(|| format!("Failed to read {}", srg_to_named.display()))?;
    let mut fields = String::from("searge,name,desc\n");
    let mut methods = String::from("searge,name,desc\n");

    for line in content.lines() {
        if line.trim().is_empty() || line.starts_with("tsrg") {
            continue;
        }
        if !line.starts_with('\t') && !line.starts_with(' ') {
            continue;
        }
        if line.starts_with("\t\t") || line.starts_with("  ") {
            continue;
        }

        let parts = line.split_whitespace().collect::<Vec<_>>();
        match parts.as_slice() {
            [srg, named] => {
                if srg.starts_with("f_") && *srg != *named {
                    writeln!(fields, "{srg},{named},")?;
                }
            }
            [srg, _descriptor, named] if srg.starts_with("m_") && *srg != *named => {
                writeln!(methods, "{srg},{named},")?;
            }
            _ => {}
        }
    }

    fs::create_dir_all(output)?;
    let fields_path = output.join("fields.csv");
    let methods_path = output.join("methods.csv");
    fs::write(&fields_path, fields)
        .wrap_err_with(|| format!("Failed to write {}", fields_path.display()))?;
    fs::write(&methods_path, methods)
        .wrap_err_with(|| format!("Failed to write {}", methods_path.display()))?;
    println!(
        "Generated Forge runtime MCP CSV mappings: {}",
        output.display()
    );
    Ok(())
}

fn write_srg_to_named_mapping_file(srg_to_named: &Path, output: &Path) -> eyre::Result<()> {
    let content = fs::read_to_string(srg_to_named)
        .wrap_err_with(|| format!("Failed to read {}", srg_to_named.display()))?;
    let mut class_mappings = BTreeMap::new();

    for line in content.lines() {
        if line.trim().is_empty() || line.starts_with("tsrg") || line.starts_with('\t') {
            continue;
        }
        let parts = line.split_whitespace().collect::<Vec<_>>();
        if let [srg_class, named_class] = parts.as_slice() {
            class_mappings.insert((*srg_class).to_string(), (*named_class).to_string());
        }
    }

    let mut output_text = String::new();
    let mut current_class: Option<(String, String)> = None;
    for line in content.lines() {
        if line.trim().is_empty() || line.starts_with("tsrg") {
            continue;
        }
        if !line.starts_with('\t') && !line.starts_with(' ') {
            let parts = line.split_whitespace().collect::<Vec<_>>();
            if let [srg_class, named_class] = parts.as_slice() {
                writeln!(output_text, "CL: {srg_class} {named_class}")?;
                current_class = Some(((*srg_class).to_string(), (*named_class).to_string()));
            }
            continue;
        }
        if line.starts_with("\t\t") || line.starts_with("  ") {
            continue;
        }

        let Some((srg_class, named_class)) = current_class.as_ref() else {
            continue;
        };
        let parts = line.split_whitespace().collect::<Vec<_>>();
        match parts.as_slice() {
            [srg, named] => {
                writeln!(output_text, "FD: {srg_class}/{srg} {named_class}/{named}")?;
            }
            [srg, descriptor, named] => {
                let named_descriptor = remap_descriptor_classes(descriptor, &class_mappings);
                writeln!(
                    output_text,
                    "MD: {srg_class}/{srg} {descriptor} {named_class}/{named} {named_descriptor}"
                )?;
            }
            _ => {}
        }
    }

    if let Some(parent) = output.parent() {
        fs::create_dir_all(parent)?;
    }
    fs::write(output, output_text)
        .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
    println!("Generated Mixin refmap remap file: {}", output.display());
    Ok(())
}

fn remap_descriptor_classes(descriptor: &str, class_mappings: &BTreeMap<String, String>) -> String {
    let mut output = String::with_capacity(descriptor.len());
    let mut cursor = 0;
    while let Some(relative_start) = descriptor[cursor..].find('L') {
        let start = cursor + relative_start;
        output.push_str(&descriptor[cursor..=start]);
        let name_start = start + 1;
        let Some(relative_end) = descriptor[name_start..].find(';') else {
            cursor = name_start;
            break;
        };
        let end = name_start + relative_end;
        let class_name = &descriptor[name_start..end];
        output.push_str(
            class_mappings
                .get(class_name)
                .map_or(class_name, String::as_str),
        );
        output.push(';');
        cursor = end + 1;
    }
    output.push_str(&descriptor[cursor..]);
    output
}

#[derive(Clone, Copy)]
enum ParameterDirection {
    SrgToOfficial,
    OfficialToSrg,
}

fn write_parameter_mappings(
    output: &mut String,
    class_name: &str,
    method: &MojangMethodMapping,
    srg_method: &SrgMethodMapping,
    parchment_parameters: Option<&ParchmentParameters>,
    direction: ParameterDirection,
) -> eyre::Result<()> {
    if srg_method.parameters.is_empty() {
        return Ok(());
    }

    let parchment_method = parchment_parameters
        .and_then(|classes| classes.get(class_name))
        .and_then(|methods| {
            methods.get(&(
                method.official_name.clone(),
                method.official_descriptor.clone(),
            ))
        });

    for (index, srg_name) in &srg_method.parameters {
        let parchment_index = if srg_method.is_static {
            *index
        } else {
            index + 1
        };
        let official_name = parchment_method
            .and_then(|parameters| parameters.get(&parchment_index))
            .map_or_else(|| srg_name.clone(), |name| forgegradle_parameter_name(name));
        match direction {
            ParameterDirection::SrgToOfficial => {
                writeln!(output, "\t\t{index} {srg_name} {official_name}")?;
            }
            ParameterDirection::OfficialToSrg => {
                writeln!(output, "\t\t{index} {official_name} {srg_name}")?;
            }
        }
    }

    Ok(())
}

fn forgegradle_parameter_name(name: &str) -> String {
    let mut chars = name.chars();
    let Some(first) = chars.next() else {
        return String::new();
    };
    format!("p{}{}", first.to_uppercase(), chars.collect::<String>())
}

fn read_parchment_parameters(path: &Path) -> eyre::Result<ParchmentParameters> {
    let bytes = fs::read(path).wrap_err_with(|| format!("Failed to read {}", path.display()))?;
    let mut archive = ZipArchive::new(Cursor::new(bytes))
        .wrap_err_with(|| format!("Failed to open Parchment zip {}", path.display()))?;
    let mut entry = archive
        .by_name("parchment.json")
        .wrap_err_with(|| format!("Parchment zip {} has no parchment.json", path.display()))?;
    let mut content = String::new();
    entry
        .read_to_string(&mut content)
        .wrap_err_with(|| format!("Failed to read parchment.json from {}", path.display()))?;
    let data: ParchmentData = facet_json::from_str(&content)
        .wrap_err_with(|| format!("Failed to parse parchment.json from {}", path.display()))?;

    let mut classes = BTreeMap::new();
    for class in data.classes {
        let mut methods = BTreeMap::new();
        for method in class.methods {
            let parameters = method
                .parameters
                .into_iter()
                .map(|parameter| (parameter.index, parameter.name))
                .collect::<BTreeMap<_, _>>();
            if !parameters.is_empty() {
                methods.insert((method.name, method.descriptor), parameters);
            }
        }
        if !methods.is_empty() {
            classes.insert(class.name, methods);
        }
    }

    Ok(classes)
}

fn find_srg_method_mapping<'a>(
    mapping: &'a SrgClassMapping,
    obf_name: &str,
    official_name: &str,
    descriptors: &[String],
) -> Option<&'a SrgMethodMapping> {
    for source_name in [obf_name, official_name] {
        for descriptor in descriptors {
            if let Some(srg_method) = mapping
                .methods
                .get(&(source_name.to_owned(), descriptor.clone()))
            {
                return Some(srg_method);
            }
        }
    }

    for source_name in [obf_name, official_name] {
        let mut candidates = mapping
            .methods
            .iter()
            .filter(|((name, _), _)| name == source_name)
            .map(|(_, srg_method)| srg_method);
        let first = candidates.next();
        if first.is_some() && candidates.next().is_none() {
            return first;
        }
    }

    None
}

fn read_mojang_mappings(paths: &[PathBuf]) -> eyre::Result<BTreeMap<String, MojangClassMapping>> {
    let mut class_names = BTreeMap::new();
    for path in paths {
        let content = fs::read_to_string(path)
            .wrap_err_with(|| format!("Failed to read {}", path.display()))?;
        for line in content.lines() {
            if line.starts_with('#') || line.starts_with(' ') {
                continue;
            }
            if let Some((official, obf_with_colon)) = line.split_once(" -> ") {
                let obf = obf_with_colon.trim_end_matches(':');
                class_names.insert(official.replace('.', "/"), obf.to_string());
            }
        }
    }

    let mut classes = BTreeMap::new();
    for path in paths {
        let content = fs::read_to_string(path)
            .wrap_err_with(|| format!("Failed to read {}", path.display()))?;
        let mut current_official = None::<String>;
        let mut current_obf = None::<String>;
        for line in content.lines() {
            if line.starts_with('#') {
                continue;
            }
            if !line.starts_with(' ') {
                if let Some((official, obf_with_colon)) = line.split_once(" -> ") {
                    let official_slash = official.replace('.', "/");
                    let obf = obf_with_colon.trim_end_matches(':').to_string();
                    classes
                        .entry(official_slash.clone())
                        .or_insert_with(|| MojangClassMapping {
                            official_slash: official_slash.clone(),
                            obf: obf.clone(),
                            fields: BTreeMap::new(),
                            methods: BTreeMap::new(),
                        });
                    current_official = Some(official_slash);
                    current_obf = Some(obf);
                }
                continue;
            }

            let Some(class_name) = &current_official else {
                continue;
            };
            let Some(class_obf) = &current_obf else {
                continue;
            };
            let member = line.trim();
            let Some((left, obf_name)) = member.split_once(" -> ") else {
                continue;
            };
            let class = classes
                .get_mut(class_name)
                .ok_or_else(|| eyre::eyre!("Missing Mojang class mapping for {class_name}"))?;
            if left.contains('(') {
                if let Some((official_name, official_descriptor, obf_descriptor)) =
                    parse_mojang_method_signature(left, &class_names)?
                {
                    class.methods.insert(
                        (obf_name.to_string(), obf_descriptor),
                        MojangMethodMapping {
                            official_name,
                            official_descriptor,
                        },
                    );
                }
            } else if let Some(official_name) = left.split_whitespace().last() {
                class
                    .fields
                    .insert(obf_name.to_string(), official_name.to_string());
            }

            if class.obf != *class_obf {
                eyre::bail!("Conflicting Mojang class mapping for {class_name}");
            }
        }
    }

    Ok(classes)
}

fn read_srg_member_mappings(path: &Path) -> eyre::Result<BTreeMap<String, SrgClassMapping>> {
    let content =
        fs::read_to_string(path).wrap_err_with(|| format!("Failed to read {}", path.display()))?;
    let mut classes = BTreeMap::new();
    let mut current_official = None::<String>;
    let mut current_method_key = None::<(String, String)>;

    for line in content.lines() {
        if line.trim().is_empty() || line.starts_with("tsrg") {
            continue;
        }
        if !line.starts_with('\t') && !line.starts_with(' ') {
            let parts = line.split_whitespace().collect::<Vec<_>>();
            if parts.len() >= 2 {
                let official = parts[1].to_string();
                current_official = Some(official.clone());
                current_method_key = None;
                classes
                    .entry(official.clone())
                    .or_insert_with(|| SrgClassMapping {
                        fields: BTreeMap::new(),
                        methods: BTreeMap::new(),
                    });
            }
            continue;
        }

        if line.starts_with("\t\t") || line.starts_with("  ") {
            let Some(class_name) = &current_official else {
                continue;
            };
            let Some(method_key) = &current_method_key else {
                continue;
            };
            let parts = line.split_whitespace().collect::<Vec<_>>();
            if parts.len() == 1 && parts[0] == "static" {
                if let Some(method) = classes
                    .get_mut(class_name)
                    .and_then(|class| class.methods.get_mut(method_key))
                {
                    method.is_static = true;
                }
                continue;
            }
            if parts.len() >= 3 {
                let index = parts[0].parse::<usize>().wrap_err_with(|| {
                    format!(
                        "Invalid TSRG parameter index {} in {}",
                        parts[0],
                        path.display()
                    )
                })?;
                let parameter_name = parts[parts.len() - 1].to_string();
                if let Some(method) = classes
                    .get_mut(class_name)
                    .and_then(|class| class.methods.get_mut(method_key))
                {
                    method.parameters.insert(index, parameter_name);
                }
            }
            continue;
        }
        let Some(class_name) = &current_official else {
            continue;
        };
        let parts = line.split_whitespace().collect::<Vec<_>>();
        let class = classes
            .get_mut(class_name)
            .ok_or_else(|| eyre::eyre!("Missing SRG class mapping for {class_name}"))?;
        if parts.len() >= 3 && parts[1].starts_with('(') {
            let method_key = (parts[0].to_string(), parts[1].to_string());
            class.methods.insert(
                method_key.clone(),
                SrgMethodMapping {
                    srg_name: parts[2].to_string(),
                    parameters: BTreeMap::new(),
                    is_static: false,
                },
            );
            current_method_key = Some(method_key);
        } else if parts.len() >= 2 {
            class
                .fields
                .insert(parts[0].to_string(), parts[1].to_string());
            current_method_key = None;
        }
    }

    Ok(classes)
}

fn parse_mojang_method_signature(
    raw: &str,
    class_names: &BTreeMap<String, String>,
) -> eyre::Result<Option<(String, String, String)>> {
    let signature = strip_mojang_line_numbers(raw);
    let Some(open_paren) = signature.find('(') else {
        return Ok(None);
    };
    let Some(close_paren) = signature.rfind(')') else {
        return Ok(None);
    };
    let before_args = signature[..open_paren].trim();
    let Some((return_type, method_name)) = before_args.rsplit_once(' ') else {
        return Ok(None);
    };
    let args = &signature[open_paren + 1..close_paren];
    let mut official_descriptor = String::from("(");
    let mut obf_descriptor = String::from("(");
    if !args.trim().is_empty() {
        for arg in args.split(',') {
            official_descriptor.push_str(&type_descriptor(arg.trim(), class_names, false)?);
            obf_descriptor.push_str(&type_descriptor(arg.trim(), class_names, true)?);
        }
    }
    official_descriptor.push(')');
    official_descriptor.push_str(&type_descriptor(return_type.trim(), class_names, false)?);
    obf_descriptor.push(')');
    obf_descriptor.push_str(&type_descriptor(return_type.trim(), class_names, true)?);
    Ok(Some((
        method_name.to_string(),
        official_descriptor,
        obf_descriptor,
    )))
}

fn strip_mojang_line_numbers(raw: &str) -> &str {
    let mut remaining = raw;
    for _ in 0..2 {
        let Some((left, right)) = remaining.split_once(':') else {
            return raw;
        };
        if left.chars().all(|character| character.is_ascii_digit()) {
            remaining = right;
        } else {
            return raw;
        }
    }
    remaining
}

fn type_descriptor(
    raw_type: &str,
    class_names: &BTreeMap<String, String>,
    obfuscate_classes: bool,
) -> eyre::Result<String> {
    let mut ty = raw_type.trim();
    let mut array_depth = 0usize;
    while let Some(stripped) = ty.strip_suffix("[]") {
        array_depth += 1;
        ty = stripped;
    }

    let base = match ty {
        "void" => "V".to_string(),
        "boolean" => "Z".to_string(),
        "byte" => "B".to_string(),
        "char" => "C".to_string(),
        "short" => "S".to_string(),
        "int" => "I".to_string(),
        "long" => "J".to_string(),
        "float" => "F".to_string(),
        "double" => "D".to_string(),
        _ => {
            let official_slash = ty.replace('.', "/");
            let mapped = if obfuscate_classes {
                class_names
                    .get(&official_slash)
                    .map_or(official_slash, Clone::clone)
            } else {
                official_slash
            };
            format!("L{mapped};")
        }
    };

    if array_depth == 0 {
        return Ok(base);
    }
    if base == "V" {
        eyre::bail!("Invalid array type: {raw_type}");
    }
    Ok(format!("{}{base}", "[".repeat(array_depth)))
}

fn resolve_compare_paths(options: &CompareOptions) -> eyre::Result<ComparePaths> {
    let worktree_path = find_worktree_path(&options.mc)?;
    let minecraft_dir = worktree_path.join("platform").join("minecraft");
    let properties = read_properties(&minecraft_dir.join("gradle.properties"))?;
    let minecraft_version = required_property(&properties, "minecraft_version")?;
    let mod_name = required_property(&properties, "mod_name")?;
    let mod_version = required_property(&properties, "mod_version")?;

    Ok(ComparePaths {
        gradle_jar: options.gradle_jar.clone().unwrap_or_else(|| {
            gradle_output_jar_path(&minecraft_dir, mod_name, minecraft_version, mod_version)
        }),
        rust_jar: options.rust_jar.clone().unwrap_or_else(|| {
            rust_output_jar_path(&minecraft_dir, mod_name, minecraft_version, mod_version)
        }),
    })
}

fn compare_jars(
    gradle_jar: &Path,
    rust_jar: &Path,
    strict_manifest: bool,
) -> eyre::Result<JarCompareReport> {
    let gradle = read_normalized_jar(gradle_jar, strict_manifest)?;
    let rust = read_normalized_jar(rust_jar, strict_manifest)?;

    let gradle_names: BTreeSet<String> = gradle.entries.keys().cloned().collect();
    let rust_names: BTreeSet<String> = rust.entries.keys().cloned().collect();

    let missing_entries: Vec<String> = gradle_names.difference(&rust_names).cloned().collect();
    let extra_entries: Vec<String> = rust_names.difference(&gradle_names).cloned().collect();
    let common_entries: Vec<String> = gradle_names.intersection(&rust_names).cloned().collect();

    let changed_entries = common_entries
        .iter()
        .filter_map(|path| {
            let gradle_sha1 = gradle.entries.get(path)?;
            let rust_sha1 = rust.entries.get(path)?;
            (gradle_sha1 != rust_sha1).then(|| ChangedEntry {
                path: path.clone(),
                gradle_sha1: gradle_sha1.clone(),
                rust_sha1: rust_sha1.clone(),
            })
        })
        .collect::<Vec<_>>();

    let manifest_compared = gradle.manifest_sha1.is_some() || rust.manifest_sha1.is_some();
    let manifest_changed = gradle.manifest_sha1 != rust.manifest_sha1;
    let manifest = ManifestCompare {
        compared: manifest_compared,
        changed: manifest_changed,
        ignored_implementation_timestamp: !strict_manifest,
        gradle_sha1: gradle.manifest_sha1,
        rust_sha1: rust.manifest_sha1,
    };

    let matches = missing_entries.is_empty()
        && extra_entries.is_empty()
        && changed_entries.is_empty()
        && !manifest.changed;

    Ok(JarCompareReport {
        gradle_jar: gradle_jar.to_path_buf(),
        rust_jar: rust_jar.to_path_buf(),
        strict_manifest,
        matches,
        total_gradle_entries: gradle.total_entries,
        total_rust_entries: rust.total_entries,
        compared_entries: common_entries.len() + usize::from(manifest_compared),
        missing_entries,
        extra_entries,
        changed_entries,
        manifest,
    })
}

fn read_normalized_jar(path: &Path, strict_manifest: bool) -> eyre::Result<NormalizedJar> {
    if !path.is_file() {
        eyre::bail!("Jar does not exist: {}", path.display());
    }

    let bytes = fs::read(path).wrap_err_with(|| format!("Failed to read {}", path.display()))?;
    let cursor = Cursor::new(bytes);
    let mut archive = ZipArchive::new(cursor)
        .wrap_err_with(|| format!("Failed to open jar {}", path.display()))?;
    let mut entries = BTreeMap::new();
    let mut manifest_sha1 = None;
    let mut total_entries = 0usize;

    for index in 0..archive.len() {
        let mut file = archive.by_index(index).wrap_err_with(|| {
            format!("Failed to read jar entry #{index} from {}", path.display())
        })?;
        let name = file.name().replace('\\', "/");
        if name.ends_with('/') {
            continue;
        }

        total_entries += 1;
        let mut entry_bytes = Vec::new();
        file.read_to_end(&mut entry_bytes)
            .wrap_err_with(|| format!("Failed to read jar entry {name} from {}", path.display()))?;

        if name.eq_ignore_ascii_case("META-INF/MANIFEST.MF") {
            let normalized = normalize_manifest_bytes(&entry_bytes, strict_manifest);
            manifest_sha1 = Some(sha1_bytes(normalized.as_bytes()));
        } else {
            entries.insert(name, sha1_bytes(&entry_bytes));
        }
    }

    Ok(NormalizedJar {
        entries,
        manifest_sha1,
        total_entries,
    })
}

fn normalize_manifest_bytes(bytes: &[u8], strict_manifest: bool) -> String {
    let text = String::from_utf8_lossy(bytes)
        .replace("\r\n", "\n")
        .replace('\r', "\n");
    if strict_manifest {
        return text;
    }

    let mut lines = Vec::new();
    let mut skipping_timestamp = false;
    for line in text.split('\n') {
        if skipping_timestamp && line.starts_with(' ') {
            continue;
        }
        skipping_timestamp = false;
        if line.starts_with("Implementation-Timestamp:") {
            skipping_timestamp = true;
            continue;
        }
        lines.push(line);
    }

    lines.join("\n")
}

fn print_compare_report(report: &JarCompareReport) {
    println!("Gradle jar: {}", report.gradle_jar.display());
    println!("Rust jar:   {}", report.rust_jar.display());
    println!("Compared entries: {}", report.compared_entries);
    println!("Gradle entries:   {}", report.total_gradle_entries);
    println!("Rust entries:     {}", report.total_rust_entries);
    println!("Missing entries:  {}", report.missing_entries.len());
    println!("Extra entries:    {}", report.extra_entries.len());
    println!("Changed entries:  {}", report.changed_entries.len());
    println!(
        "Manifest changed: {}",
        if report.manifest.changed { "yes" } else { "no" }
    );

    print_string_list("Missing", &report.missing_entries);
    print_string_list("Extra", &report.extra_entries);
    print_changed_entries(&report.changed_entries);

    if report.matches {
        println!("Jar comparison passed: normalized jars match.");
    } else {
        println!("Jar comparison failed: normalized jars differ.");
    }
}

fn print_string_list(label: &str, entries: &[String]) {
    if entries.is_empty() {
        return;
    }

    println!("{label} entry sample:");
    for entry in entries.iter().take(20) {
        println!(" - {entry}");
    }
    if entries.len() > 20 {
        println!(" - ... {} more", entries.len() - 20);
    }
}

fn print_changed_entries(entries: &[ChangedEntry]) {
    if entries.is_empty() {
        return;
    }

    println!("Changed entry sample:");
    for entry in entries.iter().take(20) {
        println!(
            " - {} (gradle {}, rust {})",
            entry.path, entry.gradle_sha1, entry.rust_sha1
        );
    }
    if entries.len() > 20 {
        println!(" - ... {} more", entries.len() - 20);
    }
}

fn write_compare_report(
    report: &JarCompareReport,
    requested_path: Option<&Path>,
) -> eyre::Result<()> {
    let Some(path) = requested_path else {
        return Ok(());
    };

    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)?;
    }
    fs::write(path, facet_json::to_string_pretty(report)?)
        .wrap_err_with(|| format!("Failed to write {}", path.display()))?;
    Ok(())
}

fn write_plan_outputs(plan: &BuildPlan, requested_path: Option<&Path>) -> eyre::Result<()> {
    fs::create_dir_all(&plan.state_dir)?;
    let plan_json = facet_json::to_string_pretty(plan)?;
    let last_plan_path = plan.state_dir.join("last-plan.json");
    fs::write(&last_plan_path, &plan_json)
        .wrap_err_with(|| format!("Failed to write {}", last_plan_path.display()))?;

    if let Some(path) = requested_path {
        if let Some(parent) = path.parent() {
            fs::create_dir_all(parent)?;
        }
        fs::write(path, &plan_json)
            .wrap_err_with(|| format!("Failed to write {}", path.display()))?;
    }

    Ok(())
}

fn write_artifact_lockfile(plan: &BuildPlan) -> eyre::Result<()> {
    let _span = tracing::info_span!(
        "write_artifact_lockfile",
        mc = %plan.minecraft_version,
        artifacts = plan.artifacts.len(),
        dependencies = plan.dependencies.len(),
    )
    .entered();
    let lockfile = build_artifact_lockfile(plan)?;
    if let Some(parent) = plan.lockfile_path.parent() {
        fs::create_dir_all(parent)?;
    }
    fs::write(
        &plan.lockfile_path,
        facet_json::to_string_pretty(&lockfile)?,
    )
    .wrap_err_with(|| format!("Failed to write {}", plan.lockfile_path.display()))?;
    println!(
        "Artifact lockfile: {} ({} artifacts)",
        plan.lockfile_path.display(),
        lockfile.artifacts.len()
    );
    Ok(())
}

fn build_artifact_lockfile(plan: &BuildPlan) -> eyre::Result<ArtifactLockfile> {
    let mut provenance_paths = Vec::new();
    collect_provenance_paths(&plan.maven_cache_dir, &mut provenance_paths)?;
    provenance_paths.sort();

    let mut artifacts = Vec::with_capacity(provenance_paths.len());
    for provenance_path in provenance_paths {
        let artifact_path = artifact_path_from_provenance_path(&provenance_path)?;
        if !artifact_path.is_file() {
            continue;
        }
        let provenance = read_required_artifact_provenance(&provenance_path)?;
        let actual_sha1 = file_sha1(&artifact_path)?;
        if actual_sha1 != provenance.sha1 {
            eyre::bail!(
                "Artifact provenance hash mismatch for {}: sidecar {}, actual {}",
                artifact_path.display(),
                provenance.sha1,
                actual_sha1
            );
        }
        artifacts.push(ArtifactLockEntry {
            coordinate: provenance.coordinate,
            source: provenance.source,
            repository: provenance.repository,
            url: provenance.url,
            cache_path: relative_path(&plan.minecraft_dir, &artifact_path),
            original_path: provenance.original_path,
            sha1: actual_sha1,
        });
    }

    if let Some(existing_lockfile) = &plan.lockfile {
        for locked in &existing_lockfile.artifacts {
            let already_present = artifacts.iter().any(|artifact| {
                artifact.coordinate == locked.coordinate && artifact.cache_path == locked.cache_path
            });
            if !already_present {
                artifacts.push(locked.clone());
            }
        }
    }

    artifacts.sort_by(|left, right| {
        (
            left.coordinate.as_deref(),
            left.repository.as_deref(),
            left.cache_path.as_path(),
        )
            .cmp(&(
                right.coordinate.as_deref(),
                right.repository.as_deref(),
                right.cache_path.as_path(),
            ))
    });

    Ok(ArtifactLockfile {
        schema_version: 1,
        minecraft_version: plan.minecraft_version.clone(),
        maven_cache_dir: relative_path(&plan.minecraft_dir, &plan.maven_cache_dir),
        allow_local_artifact_cache: plan.allow_local_artifact_cache,
        repositories: plan.repositories.clone(),
        dependencies: plan
            .dependencies
            .iter()
            .map(|dependency| DependencyLockEntry {
                configuration: dependency.configuration.clone(),
                notation: dependency.notation.clone(),
                resolved_notation: dependency.resolved_notation.clone(),
                source: dependency.source.clone(),
                dynamic_version: dependency.dynamic_version,
                cache_path: relative_path(&plan.minecraft_dir, &dependency.cache_path),
            })
            .collect(),
        artifacts,
    })
}

fn read_optional_artifact_lockfile(
    path: &Path,
    minecraft_version: &str,
) -> eyre::Result<Option<ArtifactLockfile>> {
    if !path.is_file() {
        return Ok(None);
    }
    let content =
        fs::read_to_string(path).wrap_err_with(|| format!("Failed to read {}", path.display()))?;
    let lockfile: ArtifactLockfile = facet_json::from_str(&content)
        .wrap_err_with(|| format!("Failed to parse {}", path.display()))?;
    if lockfile.minecraft_version != minecraft_version {
        eyre::bail!(
            "Lockfile {} is for Minecraft {}, but this plan is for {}",
            path.display(),
            lockfile.minecraft_version,
            minecraft_version
        );
    }
    Ok(Some(lockfile))
}

fn collect_provenance_paths(root: &Path, output: &mut Vec<PathBuf>) -> eyre::Result<()> {
    if !root.is_dir() {
        return Ok(());
    }
    for entry in
        fs::read_dir(root).wrap_err_with(|| format!("Failed to read {}", root.display()))?
    {
        let path = entry?.path();
        if path.is_dir() {
            collect_provenance_paths(&path, output)?;
        } else if path
            .file_name()
            .and_then(std::ffi::OsStr::to_str)
            .is_some_and(|name| name.ends_with(".sfm-provenance.json"))
        {
            output.push(path);
        }
    }
    Ok(())
}

fn artifact_path_from_provenance_path(path: &Path) -> eyre::Result<PathBuf> {
    let file_name = path
        .file_name()
        .and_then(std::ffi::OsStr::to_str)
        .ok_or_else(|| eyre::eyre!("Path has no filename: {}", path.display()))?;
    let artifact_name = file_name
        .strip_suffix(".sfm-provenance.json")
        .ok_or_else(|| eyre::eyre!("Not an artifact provenance path: {}", path.display()))?;
    Ok(path.with_file_name(artifact_name))
}

fn read_required_artifact_provenance(path: &Path) -> eyre::Result<ArtifactProvenance> {
    let content =
        fs::read_to_string(path).wrap_err_with(|| format!("Failed to read {}", path.display()))?;
    facet_json::from_str(&content).wrap_err_with(|| format!("Failed to parse {}", path.display()))
}

fn relative_path(base: &Path, path: &Path) -> PathBuf {
    path.strip_prefix(base)
        .map_or_else(|_| path.to_path_buf(), Path::to_path_buf)
}

fn print_plan_summary(plan: &BuildPlan) {
    println!("Clean-slate jar build plan resolved.");
    println!("Minecraft:    {}", plan.minecraft_version);
    println!("Worktree:     {}", plan.worktree_path.display());
    println!("Gradle jar:   {}", plan.gradle_output_jar.display());
    println!("Rust jar:     {}", plan.rust_output_jar.display());
    println!("Java:         {}", plan.java.executable.display());
    println!("Java release: {}", plan.java_release);
    println!(
        "Toolchain:    {:?} ({})",
        plan.loader_toolchain.kind, plan.loader_toolchain.userdev_coordinate
    );
    println!(
        "State:        {}",
        plan.state_dir.join("last-plan.json").display()
    );
    println!("Lockfile:     {}", plan.lockfile_path.display());
    println!("Artifacts:    {}", plan.artifacts.len());
    let dependency_label = if plan.loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
        "project deps"
    } else {
        "fg.deobf deps"
    };
    println!("{dependency_label}: {}", plan.dependencies.len());
    println!("Graph nodes:  {}", plan.graph.len());

    for warning in &plan.warnings {
        println!("Warning: {warning}");
    }
}

fn read_java_toolchain_release(minecraft_dir: &Path, minecraft_version: &str) -> eyre::Result<u32> {
    let path = minecraft_dir
        .join("gradle")
        .join("java-toolchain")
        .join(minecraft_version)
        .join("java-toolchain.gradle");
    let content =
        fs::read_to_string(&path).wrap_err_with(|| format!("Failed to read {}", path.display()))?;
    let start = content.find("JavaLanguageVersion.of(").ok_or_else(|| {
        eyre::eyre!(
            "Could not find JavaLanguageVersion.of(...) in {}",
            path.display()
        )
    })? + "JavaLanguageVersion.of(".len();
    let end = content[start..]
        .find(')')
        .ok_or_else(|| eyre::eyre!("Could not parse Java toolchain in {}", path.display()))?
        + start;
    content[start..end]
        .trim()
        .parse()
        .wrap_err_with(|| format!("Could not parse Java release from {}", path.display()))
}

fn required_java_runtime_major(loader_toolchain: &LoaderToolchainPlan, java_release: u32) -> u32 {
    if loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
        java_release.max(21)
    } else {
        java_release
    }
}

fn resolve_java(java_home: Option<&Path>, required_major: u32) -> eyre::Result<JavaPlan> {
    let home = java_home
        .map(Path::to_path_buf)
        .or_else(|| std::env::var_os("JAVA_HOME").map(PathBuf::from));
    let executable = home.as_ref().map_or_else(
        || PathBuf::from("java"),
        |home| {
            home.join("bin")
                .join(if cfg!(windows) { "java.exe" } else { "java" })
        },
    );

    let output = Command::new(&executable)
        .arg("-version")
        .output()
        .wrap_err_with(|| format!("Failed to run {} -version", executable.display()))?;
    if !output.status.success() {
        eyre::bail!(
            "{} -version exited with {}",
            executable.display(),
            output.status
        );
    }

    let version_output = String::from_utf8_lossy(&output.stderr).trim().to_string();
    let major_version = parse_java_major_version(&version_output)
        .ok_or_else(|| eyre::eyre!("Could not parse Java version from: {version_output}"))?;
    if major_version < required_major {
        eyre::bail!(
            "Java {} or newer is required for this clean-slate build, but {} reports Java {}",
            required_major,
            executable.display(),
            major_version
        );
    }

    Ok(JavaPlan {
        executable,
        home,
        version_output,
        major_version,
    })
}

fn parse_java_major_version(version_output: &str) -> Option<u32> {
    let quoted = version_output.split('"').nth(1)?;
    let first = quoted.split('.').next()?;
    if first == "1" {
        quoted.split('.').nth(1)?.parse().ok()
    } else {
        first.parse().ok()
    }
}

fn javac_executable(java: &JavaPlan) -> PathBuf {
    java.home.as_ref().map_or_else(
        || {
            if cfg!(windows) {
                PathBuf::from("javac.exe")
            } else {
                PathBuf::from("javac")
            }
        },
        |home| {
            home.join("bin")
                .join(if cfg!(windows) { "javac.exe" } else { "javac" })
        },
    )
}

fn canonicalize_lenient(path: &Path) -> eyre::Result<PathBuf> {
    if path.exists() {
        return dunce::canonicalize(path)
            .wrap_err_with(|| format!("Failed to canonicalize {}", path.display()));
    }

    let parent = path
        .parent()
        .ok_or_else(|| eyre::eyre!("Path has no parent: {}", path.display()))?;
    let canonical_parent = if parent.exists() {
        dunce::canonicalize(parent)
            .wrap_err_with(|| format!("Failed to canonicalize {}", parent.display()))?
    } else {
        canonicalize_lenient(parent)?
    };
    let file_name = path
        .file_name()
        .ok_or_else(|| eyre::eyre!("Path has no file name: {}", path.display()))?;
    Ok(canonical_parent.join(file_name))
}

fn relative_zip_name(root: &Path, path: &Path) -> eyre::Result<String> {
    let relative = path
        .strip_prefix(root)
        .wrap_err_with(|| format!("{} is not under {}", path.display(), root.display()))?;
    Ok(relative.to_string_lossy().replace('\\', "/"))
}

fn find_worktree_path(mc: &str) -> eyre::Result<PathBuf> {
    if let Ok(worktrees) = get_sorted_worktrees()
        && let Some(worktree) = worktrees.into_iter().find(|worktree| worktree.branch == mc)
    {
        return Ok(worktree.path);
    }

    let mut current = std::env::current_dir().wrap_err("Failed to get current directory")?;
    loop {
        let candidate = current.join("platform").join("minecraft");
        if candidate.join("gradle.properties").is_file() {
            let properties = read_properties(&candidate.join("gradle.properties"))?;
            if properties
                .get("minecraft_version")
                .is_some_and(|version| version == mc)
            {
                return Ok(current);
            }
        }

        if !current.pop() {
            break;
        }
    }

    eyre::bail!(
        "Could not find worktree for Minecraft {mc}. Configure repo root or run from that worktree."
    );
}

fn gradle_output_jar_path(
    minecraft_dir: &Path,
    mod_name: &str,
    minecraft_version: &str,
    mod_version: &str,
) -> PathBuf {
    minecraft_dir.join("build").join("libs").join(format!(
        "{mod_name}-MC{minecraft_version}-{mod_version}.jar"
    ))
}

fn rust_output_jar_path(
    minecraft_dir: &Path,
    mod_name: &str,
    minecraft_version: &str,
    mod_version: &str,
) -> PathBuf {
    minecraft_dir.join("build").join("libs").join(format!(
        "{mod_name}-MC{minecraft_version}-{mod_version}-rust.jar"
    ))
}

fn read_properties(path: &Path) -> eyre::Result<BTreeMap<String, String>> {
    let content = fs::read_to_string(path)
        .wrap_err_with(|| format!("Failed to read properties file: {}", path.display()))?;
    let mut properties = BTreeMap::new();

    for line in content.lines().map(str::trim) {
        if line.is_empty() || line.starts_with('#') {
            continue;
        }
        if let Some((key, value)) = line.split_once('=') {
            properties.insert(key.trim().to_string(), value.trim().to_string());
        }
    }

    Ok(properties)
}

fn required_property<'a>(
    properties: &'a BTreeMap<String, String>,
    key: &str,
) -> eyre::Result<&'a str> {
    properties
        .get(key)
        .map(String::as_str)
        .filter(|value| !value.is_empty())
        .ok_or_else(|| eyre::eyre!("Missing required gradle.properties key: {key}"))
}

fn repositories() -> Vec<Repository> {
    [
        ("Forge", "https://maven.minecraftforge.net"),
        ("NeoForged", "https://maven.neoforged.net/releases"),
        ("Maven Central", "https://repo1.maven.org/maven2"),
        ("Parchment", "https://maven.parchmentmc.org"),
        (
            "Sponge",
            "https://repo.spongepowered.org/repository/maven-public",
        ),
        ("BlameJared", "https://maven.blamejared.com"),
        ("JEI", "https://dvs1.progwml6.com/files/maven"),
        ("CurseMaven", "https://www.cursemaven.com"),
        ("ModMaven", "https://modmaven.dev"),
        ("Thermal", "https://maven.covers1624.net"),
    ]
    .into_iter()
    .map(|(name, url)| Repository {
        name: name.to_string(),
        url: url.to_string(),
    })
    .collect()
}

fn plain_artifact(
    id: &str,
    url: &str,
    cache_path: PathBuf,
    required_for: &str,
) -> eyre::Result<ArtifactPlan> {
    let sha1 = file_sha1(&cache_path)?;
    Ok(ArtifactPlan {
        id: id.to_string(),
        coordinate: None,
        repository: None,
        url: Some(url.to_string()),
        sha1: Some(sha1.clone()),
        cache_path,
        downloaded: true,
        required_for: required_for.to_string(),
        provenance: artifact_provenance(
            ArtifactSource::RemoteHttp,
            None,
            None,
            Some(url.to_string()),
            None,
            sha1,
        ),
    })
}

fn artifact_provenance(
    source: ArtifactSource,
    coordinate: Option<String>,
    repository: Option<String>,
    url: Option<String>,
    original_path: Option<PathBuf>,
    sha1: String,
) -> ArtifactProvenance {
    ArtifactProvenance {
        schema_version: 1,
        source,
        coordinate,
        repository,
        url,
        original_path,
        sha1,
    }
}

fn artifact_provenance_path(path: &Path) -> eyre::Result<PathBuf> {
    let file_name = path
        .file_name()
        .and_then(std::ffi::OsStr::to_str)
        .ok_or_else(|| eyre::eyre!("Path has no filename: {}", path.display()))?;
    Ok(path.with_file_name(format!("{file_name}.sfm-provenance.json")))
}

fn read_artifact_provenance(path: &Path) -> eyre::Result<Option<ArtifactProvenance>> {
    let provenance_path = artifact_provenance_path(path)?;
    if !provenance_path.is_file() {
        return Ok(None);
    }
    let content = fs::read_to_string(&provenance_path)
        .wrap_err_with(|| format!("Failed to read {}", provenance_path.display()))?;
    let provenance = facet_json::from_str(&content)
        .wrap_err_with(|| format!("Failed to parse {}", provenance_path.display()))?;
    Ok(Some(provenance))
}

fn write_artifact_provenance(path: &Path, provenance: &ArtifactProvenance) -> eyre::Result<()> {
    let provenance_path = artifact_provenance_path(path)?;
    if let Some(parent) = provenance_path.parent() {
        fs::create_dir_all(parent)?;
    }
    let content = facet_json::to_string_pretty(provenance)
        .wrap_err("Failed to encode artifact provenance")?;
    fs::write(&provenance_path, content)
        .wrap_err_with(|| format!("Failed to write {}", provenance_path.display()))
}

fn read_json_file<T>(path: &Path) -> eyre::Result<T>
where
    T: Facet<'static>,
{
    let content = fs::read_to_string(path)
        .wrap_err_with(|| format!("Failed to read JSON file: {}", path.display()))?;
    facet_json::from_str(&content).wrap_err_with(|| format!("Failed to parse {}", path.display()))
}

fn read_zip_json_entry<T>(path: &Path, entry_name: &str) -> eyre::Result<T>
where
    T: Facet<'static>,
{
    let bytes = fs::read(path).wrap_err_with(|| format!("Failed to read {}", path.display()))?;
    let cursor = Cursor::new(bytes);
    let mut archive = ZipArchive::new(cursor)
        .wrap_err_with(|| format!("Failed to read zip archive {}", path.display()))?;
    let mut entry = archive
        .by_name(entry_name)
        .wrap_err_with(|| format!("Archive {} missing {entry_name}", path.display()))?;
    let mut content = String::new();
    entry
        .read_to_string(&mut content)
        .wrap_err_with(|| format!("Failed to read {entry_name} from {}", path.display()))?;
    facet_json::from_str(&content)
        .wrap_err_with(|| format!("Failed to parse {entry_name} from {}", path.display()))
}

fn download_to_path(client: &Client, url: &str, path: &Path) -> eyre::Result<()> {
    download_to_path_overwrite(client, url, path, false)
}

fn download_to_path_overwrite(
    client: &Client,
    url: &str,
    path: &Path,
    overwrite: bool,
) -> eyre::Result<()> {
    #[cfg(feature = "tracing_detailed")]
    let _span = tracing::debug_span!(
        "download_to_path",
        url,
        path = %path.display(),
        overwrite,
    )
    .entered();
    if path.is_file() && !overwrite {
        tracing::debug!(
            path = %path.display(),
            url,
            "download cache hit"
        );
        return Ok(());
    }

    tracing::info!(
        path = %path.display(),
        url,
        overwrite,
        "download cache miss"
    );
    let parent = path
        .parent()
        .ok_or_else(|| eyre::eyre!("Path has no parent: {}", path.display()))?;
    fs::create_dir_all(parent)?;
    let response = client
        .get(url)
        .send()
        .wrap_err_with(|| format!("Failed to request {url}"))?;
    if !response.status().is_success() {
        eyre::bail!("Failed to download {url}: HTTP {}", response.status());
    }
    let bytes = response
        .bytes()
        .wrap_err_with(|| format!("Failed to read response body for {url}"))?;
    let file_name = path
        .file_name()
        .and_then(std::ffi::OsStr::to_str)
        .ok_or_else(|| eyre::eyre!("Path has no filename: {}", path.display()))?;
    let temporary_path = path.with_file_name(format!("{file_name}.download"));
    fs::write(&temporary_path, bytes)
        .wrap_err_with(|| format!("Failed to write {}", temporary_path.display()))?;
    if path.exists() {
        fs::remove_file(path).wrap_err_with(|| format!("Failed to replace {}", path.display()))?;
    }
    fs::rename(&temporary_path, path).wrap_err_with(|| {
        format!(
            "Failed to move downloaded file {} to {}",
            temporary_path.display(),
            path.display()
        )
    })?;
    Ok(())
}

fn download_text_optional(client: &Client, url: &str) -> eyre::Result<String> {
    let response = client
        .get(url)
        .send()
        .wrap_err_with(|| format!("Failed to request {url}"))?;
    if response.status() == StatusCode::NOT_FOUND {
        eyre::bail!("not found");
    }
    if !response.status().is_success() {
        eyre::bail!("HTTP {}", response.status());
    }
    response
        .text()
        .wrap_err_with(|| format!("Failed to read response body for {url}"))
}

fn remote_exists(client: &Client, url: &str) -> eyre::Result<bool> {
    #[cfg(feature = "tracing_detailed")]
    let _span = tracing::debug_span!("remote_exists", url).entered();
    let response = client
        .head(url)
        .send()
        .wrap_err_with(|| format!("Failed to request {url}"))?;
    if response.status() == StatusCode::METHOD_NOT_ALLOWED {
        return Ok(download_text_optional(client, url).is_ok());
    }
    Ok(response.status().is_success())
}

fn file_sha1(path: &Path) -> eyre::Result<String> {
    let bytes = fs::read(path).wrap_err_with(|| format!("Failed to hash {}", path.display()))?;
    Ok(sha1_bytes(&bytes))
}

fn sha1_bytes(bytes: &[u8]) -> String {
    let mut hasher = Sha1::new();
    hasher.update(bytes);
    format!("{:x}", hasher.finalize())
}

fn parse_maven_versions(metadata: &str) -> Vec<String> {
    let mut versions = Vec::new();
    let mut remaining = metadata;

    while let Some(start) = remaining.find("<version>") {
        let after_start = &remaining[start + "<version>".len()..];
        let Some(end) = after_start.find("</version>") else {
            break;
        };
        versions.push(after_start[..end].trim().to_string());
        remaining = &after_start[end + "</version>".len()..];
    }

    versions
}

fn compare_version_text(left: &str, right: &str) -> Ordering {
    let left_parts = split_version_parts(left);
    let right_parts = split_version_parts(right);
    left_parts.cmp(&right_parts)
}

fn split_version_parts(version: &str) -> Vec<VersionPart> {
    let mut parts = Vec::new();
    let mut current = String::new();
    let mut digit_mode = None;

    for character in version.chars() {
        let is_digit = character.is_ascii_digit();
        if digit_mode.is_some_and(|mode| mode != is_digit) && !current.is_empty() {
            parts.push(VersionPart::from_text(&current));
            current.clear();
        }
        digit_mode = Some(is_digit);
        if character == '.' || character == '-' || character == '_' || character == '+' {
            if !current.is_empty() {
                parts.push(VersionPart::from_text(&current));
                current.clear();
            }
            digit_mode = None;
        } else {
            current.push(character);
        }
    }

    if !current.is_empty() {
        parts.push(VersionPart::from_text(&current));
    }

    parts
}

#[derive(Clone, Debug, Eq, Ord, PartialEq, PartialOrd)]
enum VersionPart {
    Number(u64),
    Text(String),
}

impl VersionPart {
    fn from_text(text: &str) -> Self {
        text.parse::<u64>()
            .map_or_else(|_| Self::Text(text.to_string()), Self::Number)
    }
}

#[cfg(test)]
#[path = "engine_tests.rs"]
mod tests;
