use super::artifact::copy_cached;
use super::artifact::missing;
use super::artifact::portable_cache_path;
use super::artifact::put_bytes;
use super::container_build_request::ContainerBuildRequest;
use super::context_manifest::ContextManifest;
use super::linux_toolchain_lock::LinuxToolchainLock;
use super::minecraft_asset_index::MinecraftAssetIndex;
use super::minecraft_version::MinecraftVersion;
use crate::cancellation::CancellationToken;
use crate::jar_build::hash::ContentHash;
use crate::jar_build::hash::ContentHashAlgorithm;
use crate::source_projection::candidate_lock::checked_directory;
use crate::source_projection::catalog_owned_project::collect_catalog_project;
use crate::source_projection::core_catalog::read_bounded_input;
use crate::source_projection::provenance::sha256;
use crate::toolchain_lockfile_schema::ToolchainLockfileDocument;
use crate::toolchain_lockfile_schema::parse_document;
use crate::toolchain_lockfile_schema::read_current;
use eyre::ensure;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fs;
use std::path::Path;
use std::time::Instant;

/// Preparation is read-only until a fresh sibling staging directory is created.
#[expect(
    clippy::too_many_lines,
    reason = "Sequential authenticated offline snapshot transaction with no resolver or acquisition side effects"
)]
#[tracing::instrument(level = "info", skip_all, fields(projection = %request.projection, prepare_only = request.prepare_only))]
pub(crate) fn execute(
    request: &ContainerBuildRequest,
    cancellation: &CancellationToken,
) -> eyre::Result<ContextManifest> {
    request.validate()?;
    super::disk::check(&request.context, 8 * 1024 * 1024 * 1024)?;
    cancellation.bail_if_cancelled()?;
    let started = Instant::now();
    let project =
        collect_catalog_project(&request.repo_root, &request.repo_root, &request.projection)?
            .check_current()?;
    let raw_lock = project
        .selected_input("sfm-toolchain.lock.json")?
        .require_exact_copy()?
        .to_vec();
    if request.toolchain_lock.is_some() || !request.prepare_only {
        ensure!(
            request.projection == "sfm-dev/mc-1.19.2"
                && sha256(&raw_lock)
                    == "sha256:198c141c2e45ed29a90935e84565e10f861aa0895233bb9b4a10cb8d2c109d41",
            "no validated config seed for this projection/source lock; regenerate and review pinned defaults"
        );
    }
    let text = std::str::from_utf8(&raw_lock)?;
    // Existing canonical parsing/profile projection; never migrate/write or discover a POM.
    let document = parse_document(text)?;
    let mut report = ContextManifest {
        schema: "sfm:container_context@1".into(),
        projection: request.projection.clone(),
        platform: request.platform.clone(),
        ownership_identity: project.ownership_identity()?,
        source_lock_sha256: sha256(&raw_lock),
        toolchain_lock_sha256: None,
        base_image: None,
        context_identity: None,
        test_maven_metadata_identity: None,
        files: BTreeMap::new(),
        total_bytes: 0,
        missing: Vec::new(),
        context_prepared: false,
        image_built: false,
        image_identity: None,
        gameplay_validated: false,
        dependency_layer: None,
    };
    let test_metadata = if matches!(
        &document,
        ToolchainLockfileDocument::V3(_) | ToolchainLockfileDocument::V4(_)
    ) && (request.toolchain_lock.is_some() || !request.prepare_only)
    {
        match super::test_metadata::collect(
            text,
            &project.receipt().minecraft_version,
            &request.cache_root,
            cancellation,
        ) {
            Ok(snapshot) => {
                for (coordinate, reason) in &snapshot.missing {
                    missing(
                        &mut report,
                        format!("test-maven-metadata:{coordinate}"),
                        reason.clone(),
                    );
                }
                report.test_maven_metadata_identity = Some(snapshot.identity.clone());
                Some(snapshot)
            }
            Err(error) => {
                missing(&mut report, "test-maven-metadata", format!("{error:#}"));
                None
            }
        }
    } else {
        None
    };
    if !report.missing.is_empty() {
        return Ok(report);
    }
    if request.toolchain_lock.is_none() && !request.prepare_only {
        let contract = super::bootstrap::build(
            request,
            &report,
            cancellation,
            request.timeout.saturating_sub(started.elapsed()),
        )?;
        let mut bounded = request.clone();
        bounded.toolchain_lock = Some(contract);
        bounded.timeout = request.timeout.saturating_sub(started.elapsed());
        return execute(&bounded, cancellation);
    }
    let preparation_timer = super::phase_timer::PhaseTimer::start("context_preparation", 0);
    let Some(contract_path) = &request.toolchain_lock else {
        missing(
            &mut report,
            "linux-toolchain-contract",
            "No reviewed Linux toolchain lock supplied; require a local base digest, exact Linux CLI bytes/source identity, and Linux SDK/graphics/Rust prerequisites.",
        );
        let pins = crate::toolchain_lockfile_schema::read_jdk_pins(text)?;
        if !pins.as_ref().is_some_and(|pins| {
            pins.iter().any(|pin| {
                pin.major == u32::from(project.receipt().java_major)
                    && pin
                        .artifacts
                        .iter()
                        .any(|artifact| artifact.platform == "linux-x64")
            })
        }) {
            missing(
                &mut report,
                format!("linux-x64-jdk-{}", project.receipt().java_major),
                "The selected frozen lock has no Linux SDK artifact pin; Windows SDK archives are not exported. An explicitly reviewed Linux toolchain contract must bind the runtime to this exact source lock.",
            );
        }
        return Ok(report);
    };
    let contract_root = checked_directory(
        contract_path
            .parent()
            .ok_or_else(|| eyre::eyre!("toolchain lock has no parent"))?,
    )?;
    let contract_name = contract_path
        .file_name()
        .and_then(|name| name.to_str())
        .ok_or_else(|| eyre::eyre!("invalid toolchain lock filename"))?;
    let contract_bytes = read_bounded_input(&contract_root, contract_name, 1024 * 1024)?;
    let contract: LinuxToolchainLock = facet_json::from_slice(&contract_bytes)?;
    validate_contract(
        &contract,
        &report.source_lock_sha256,
        project.receipt().java_major,
    )?;
    ensure!(
        contract.cli_source_identity == super::snapshot::cli_source_identity(project.repo_root())?,
        "Linux CLI source identity does not match this checkout; rebuild it from these exact sources separately"
    );
    report.toolchain_lock_sha256 = Some(sha256(&contract_bytes));
    report.base_image = Some(contract.base_image.clone());
    let cli_bytes = match read_bounded_input(&contract_root, &contract.cli_path, 256 * 1024 * 1024)
    {
        Ok(bytes) => bytes,
        Err(error) => {
            missing(&mut report, "linux-cli", format!("{error:#}"));
            return Ok(report);
        }
    };
    if sha256(&cli_bytes) != contract.cli_sha256 || !linux_elf(&cli_bytes) {
        missing(
            &mut report,
            "linux-cli",
            "CLI bytes fail the approved SHA-256 or Linux amd64 ELF header check",
        );
        return Ok(report);
    }
    if request.reuse_context {
        drop(preparation_timer);
        return super::existing_context::execute(
            request,
            &project,
            &report,
            &contract,
            &cli_bytes,
            cancellation,
            request.timeout.saturating_sub(started.elapsed()),
        );
    }
    let parent = checked_directory(
        request
            .context
            .parent()
            .ok_or_else(|| eyre::eyre!("context has no parent"))?,
    )?;
    ensure!(
        !request.context.exists() && fs::symlink_metadata(&request.context).is_err(),
        "context output already exists; choose a new directory"
    );
    ensure!(
        !request.context.starts_with(project.repo_root())
            && !request.context.starts_with(&request.cache_root),
        "context must be outside source checkout and canonical cache"
    );
    let stage = tempfile::Builder::new()
        .prefix(".sfm-container-context-")
        .tempdir_in(&parent)?;
    let cache = match checked_directory(&request.cache_root.join("minecraft-toolchain")) {
        Ok(root) => root,
        Err(error) => {
            missing(
                &mut report,
                "minecraft-toolchain-cache",
                format!("{error:#}"),
            );
            return Ok(report);
        }
    };
    if let Some(snapshot) = &test_metadata {
        super::test_metadata::stage(snapshot, stage.path(), &mut report, request.max_bytes)?;
    }
    let parent_lock = report.source_lock_sha256.clone();
    let mut version_path = None;
    match document {
        ToolchainLockfileDocument::V1(_) => missing(
            &mut report,
            "schema-1-source-lock",
            "Container export requires an existing schema-2 frozen recipe or declared schema-4 native profile; no migration was attempted",
        ),
        ToolchainLockfileDocument::V2 { lockfile, .. } => {
            for artifact in lockfile.artifacts {
                use crate::jar_build::ArtifactSource;
                let portable = matches!(
                    artifact.source,
                    ArtifactSource::RemoteHttp | ArtifactSource::RemoteMaven
                ) || matches!(artifact.source, ArtifactSource::SourceBuild)
                    && artifact
                        .source_git
                        .as_ref()
                        .is_some_and(|git| !git.dirty && git.remote_url.is_some());
                let source_ready = export_source_producer(
                    &cache,
                    stage.path(),
                    &artifact.cache_path,
                    artifact.coordinate.as_deref(),
                    artifact.hash,
                    artifact.source_git.as_ref(),
                    artifact.source_build.as_ref(),
                    &parent_lock,
                    &mut report,
                    request,
                )?;
                add_locked(
                    &cache,
                    stage.path(),
                    &artifact.cache_path,
                    artifact.hash,
                    portable && artifact.weak.is_none() && source_ready,
                    &parent_lock,
                    &mut report,
                    request,
                    cancellation,
                    &mut version_path,
                )?;
            }
        }
        ToolchainLockfileDocument::V3(_) | ToolchainLockfileDocument::V4(_) => {
            // The canonical API selects the same rust-toolchain profile as native preparation.
            let effective = read_current(text)?;
            for artifact in effective.artifacts {
                use crate::toolchain_lockfile_schema::version::v3::ArtifactProvenanceV3;
                let portable =
                    matches!(
                        artifact.provenance,
                        ArtifactProvenanceV3::RemoteHttp | ArtifactProvenanceV3::RemoteMaven
                    ) || matches!(artifact.provenance, ArtifactProvenanceV3::SourceBuild)
                        && artifact
                            .source_git
                            .as_ref()
                            .is_some_and(|git| !git.dirty && git.remote_url.is_some());
                let source_ready = export_source_producer(
                    &cache,
                    stage.path(),
                    &artifact.cache_path,
                    artifact.coordinate.as_deref(),
                    artifact.hash,
                    artifact.source_git.as_ref(),
                    artifact.source_build.as_ref(),
                    &parent_lock,
                    &mut report,
                    request,
                )?;
                add_locked(
                    &cache,
                    stage.path(),
                    &artifact.cache_path,
                    artifact.hash,
                    portable && artifact.weak.is_none() && source_ready,
                    &parent_lock,
                    &mut report,
                    request,
                    cancellation,
                    &mut version_path,
                )?;
            }
        }
    }
    if let Some(path) = version_path {
        export_minecraft(
            &cache,
            stage.path(),
            &path,
            &project.receipt().minecraft_version,
            &mut report,
            request,
            cancellation,
        )?;
    } else {
        missing(
            &mut report,
            "minecraft-version-json",
            "No verified version JSON is bound to the selected source lock",
        );
    }
    if !report.missing.is_empty() {
        return Ok(report);
    }
    super::snapshot::export(&project, stage.path(), &mut report, request.max_bytes)?;
    if request.profile == super::ContainerBuildProfile::SupportAgent {
        super::snapshot::export_catalog(
            &request.repo_root,
            stage.path(),
            &mut report,
            request.max_bytes,
        )?;
        super::support_agent_sdks::export(request, stage.path(), &mut report)?;
        export_supported_catalog_cache(request, stage.path(), &mut report, cancellation)?;
        if !report.missing.is_empty() {
            return Ok(report);
        }
    }
    let contract_parent = report
        .toolchain_lock_sha256
        .clone()
        .expect("contract was validated");
    put_bytes(
        stage.path(),
        "tools/sfm-propagate-changes",
        &cli_bytes,
        &contract_parent,
        &mut report,
        request.max_bytes,
    )?;
    drop(preparation_timer);
    let dependency_reference = super::dependency_cache::prepare(
        request,
        &mut report,
        stage.path(),
        cancellation,
        request.timeout.saturating_sub(started.elapsed()),
    )?;
    let publication_timer =
        super::phase_timer::PhaseTimer::start("context_publication", report.total_bytes);
    let dockerfile = if let Some(reference) = dependency_reference.as_deref() {
        super::backend::dockerfile_with_dependencies(&contract, request, Some(reference))?
    } else {
        super::backend::dockerfile(&contract, request)?
    };
    put_bytes(
        stage.path(),
        "Dockerfile",
        dockerfile.as_bytes(),
        &contract_parent,
        &mut report,
        request.max_bytes,
    )?;
    put_bytes(
        stage.path(),
        "entrypoint.sh",
        super::backend::ENTRYPOINT.as_bytes(),
        &contract_parent,
        &mut report,
        request.max_bytes,
    )?;
    for (name, bytes) in [
        (
            "junit_job.py",
            include_bytes!("native_api/session/junit_job.py").as_slice(),
        ),
        (
            "runtime_pack.py",
            include_bytes!("runtime_pack.py").as_slice(),
        ),
        (
            "config_seed.py",
            include_bytes!("config_seed.py").as_slice(),
        ),
        (
            "config_seed_data.py",
            include_bytes!("config_seed_data.py").as_slice(),
        ),
        (
            "support_agent.py",
            include_bytes!("support_agent.py").as_slice(),
        ),
        (
            "runtime_launch.py",
            include_bytes!("runtime_launch.py").as_slice(),
        ),
    ] {
        put_bytes(
            stage.path(),
            name,
            bytes,
            &contract_parent,
            &mut report,
            request.max_bytes,
        )?;
    }
    if let Some(snapshot) = &test_metadata {
        super::test_metadata::recheck(
            text,
            &project.receipt().minecraft_version,
            &request.cache_root,
            &snapshot.identity,
            cancellation,
        )?;
    }
    // No host path or contract filename is serialized into the portable manifest.
    project.recheck()?;
    ensure!(
        started.elapsed() < request.timeout,
        "context preparation exceeded timeout"
    );
    cancellation.bail_if_cancelled()?;
    report.context_prepared = true;
    report.context_identity = Some(sha256(facet_json::to_string(&report)?.as_bytes()));
    fs::write(
        stage.path().join("context-manifest.json"),
        facet_json::to_string_pretty(&report)?,
    )?;
    // Reserve a fresh directory without replacing even an empty existing output.
    fs::create_dir(&request.context)?;
    for name in [
        "snapshot",
        "sdks",
        "cache",
        "tools",
        "Dockerfile",
        "entrypoint.sh",
        "runtime_pack.py",
        "runtime_launch.py",
        "support_agent.py",
        "junit_job.py",
        "config_seed.py",
        "config_seed_data.py",
    ] {
        checked_directory(&request.context)?;
        if stage.path().join(name).exists() {
            fs::rename(stage.path().join(name), request.context.join(name))?;
        }
    }
    // A partial publication has no final manifest and is never admitted by the backend.
    fs::rename(
        stage.path().join("context-manifest.json"),
        request.context.join("context-manifest.json"),
    )?;
    // Backend validation is authoritative; avoid a duplicate full pass here.
    drop(publication_timer);
    if !request.prepare_only {
        report.image_identity = super::backend::build(
            request,
            &report,
            cancellation,
            request.timeout.saturating_sub(started.elapsed()),
        )?;
        report.image_built = true;
    }
    Ok(report)
}

#[expect(
    clippy::too_many_arguments,
    reason = "Exact frozen lock row exported through one checked cache boundary"
)]
fn add_locked(
    cache: &Path,
    stage: &Path,
    path: &Path,
    hash: ContentHash,
    portable: bool,
    parent: &str,
    report: &mut ContextManifest,
    request: &ContainerBuildRequest,
    cancellation: &CancellationToken,
    version_path: &mut Option<String>,
) -> eyre::Result<()> {
    let relative = match portable_cache_path(path) {
        Ok(relative) => relative,
        Err(error) => {
            missing(
                report,
                hash.to_string(),
                format!("locked cache path refused: {error:#}"),
            );
            return Ok(());
        }
    };
    if !portable {
        missing(
            report,
            relative,
            "Locked artifact has weak, local-only, generated or unverified source provenance",
        );
        return Ok(());
    }
    let destination = format!("cache/minecraft-toolchain/{relative}");
    if copy_cached(
        cache,
        &relative,
        stage,
        &destination,
        hash,
        None,
        parent,
        report,
        request.max_bytes,
        cancellation,
    )? && relative.starts_with("minecraft/versions/")
        && relative.ends_with("/version.json")
    {
        *version_path = Some(destination);
    }
    Ok(())
}

#[expect(
    clippy::too_many_lines,
    reason = "Bounded authenticated metadata-descendant export with no network or resolution"
)]
fn export_minecraft(
    cache: &Path,
    stage: &Path,
    version_path: &str,
    version: &str,
    report: &mut ContextManifest,
    request: &ContainerBuildRequest,
    cancellation: &CancellationToken,
) -> eyre::Result<()> {
    let parent = report.files[version_path].sha256.clone();
    let metadata: MinecraftVersion = facet_json::from_slice(&fs::read(stage.join(version_path))?)?;
    ensure!(
        metadata.id == version,
        "authenticated Minecraft version identity mismatch"
    );
    let index = metadata.asset_index;
    let id = index
        .id
        .ok_or_else(|| eyre::eyre!("asset index has no exact id"))?;
    crate::source_projection::promotion::validate_relative_path(&id)?;
    ensure!(
        !id.contains('/'),
        "asset index identity must be one component"
    );
    let relative = format!("minecraft/assets/indexes/{id}.json");
    let destination = format!("cache/minecraft-toolchain/{relative}");
    if copy_cached(
        cache,
        &relative,
        stage,
        &destination,
        sha1(&index.sha1)?,
        Some(index.size),
        &parent,
        report,
        request.max_bytes,
        cancellation,
    )? {
        let index_parent = report.files[&destination].sha256.clone();
        let bytes = read_bounded_input(stage, &destination, 16 * 1024 * 1024)?;
        let assets: MinecraftAssetIndex = facet_json::from_slice(&bytes)?;
        ensure!(
            assets.objects.len() <= 100_000,
            "asset index entry-count budget exceeded"
        );
        let mut unique = BTreeMap::new();
        for asset in assets.objects.into_values() {
            sha1(&asset.hash)?;
            if let Some(size) = unique.insert(asset.hash.clone(), asset.size) {
                ensure!(
                    size == asset.size,
                    "conflicting content-addressed asset sizes"
                );
            }
        }
        for (hash, size) in unique {
            let relative = format!("minecraft/assets/objects/{}/{}", &hash[..2], hash);
            copy_cached(
                cache,
                &relative,
                stage,
                &format!("cache/minecraft-toolchain/{relative}"),
                sha1(&hash)?,
                Some(size),
                &index_parent,
                report,
                request.max_bytes,
                cancellation,
            )?;
        }
    }
    for (role, filename) in [
        ("client", "client.jar"),
        ("server", "server-bundle.jar"),
        ("client_mappings", "client.txt"),
        ("server_mappings", "server.txt"),
    ] {
        if version == "26.1.2" && role.ends_with("_mappings") {
            ensure!(
                !metadata.downloads.contains_key(role),
                "26.1.2 metadata unexpectedly declares obfuscated mappings"
            );
            continue;
        }
        let download = metadata
            .downloads
            .get(role)
            .ok_or_else(|| eyre::eyre!("authenticated version JSON lacks {role}"))?;
        let filename = if role == "server"
            && matches!(
                version,
                "1.20.2" | "1.20.3" | "1.20.4" | "1.21" | "1.21.1" | "26.1.2"
            ) {
            "server.jar"
        } else {
            filename
        };
        let relative = format!("minecraft/versions/{version}/{filename}");
        copy_cached(
            cache,
            &relative,
            stage,
            &format!("cache/minecraft-toolchain/{relative}"),
            sha1(&download.sha1)?,
            Some(download.size),
            &parent,
            report,
            request.max_bytes,
            cancellation,
        )?;
    }
    ensure!(
        metadata.libraries.len() <= 4096,
        "Minecraft library-count budget exceeded"
    );
    // Captured NFRT compilation needs the complete authenticated Java API set.
    // Runtime native selection remains restricted to Linux.
    let nfrt_compile = matches!(
        version,
        "1.20.2" | "1.20.3" | "1.20.4" | "1.21" | "1.21.1" | "26.1.2"
    );
    let mut natives = BTreeSet::new();
    for library in metadata.libraries {
        let runtime_allowed = library.linux_allowed()?;
        if !runtime_allowed && !nfrt_compile {
            continue;
        }
        let Some(artifact) = library.downloads.and_then(|downloads| downloads.artifact) else {
            missing(
                report,
                library.name,
                "Unsupported native-classifier layout; extend the canonical authenticated metadata boundary before export",
            );
            continue;
        };
        let path = artifact
            .path
            .ok_or_else(|| eyre::eyre!("authenticated library has no relative path"))?;
        crate::source_projection::promotion::validate_relative_path(&path)?;
        ensure!(
            Path::new(&path)
                .extension()
                .is_some_and(|extension| extension.eq_ignore_ascii_case("jar"))
                && (nfrt_compile
                    || (!path.contains("natives-windows") && !path.contains("natives-macos"))),
            "nonportable library path"
        );
        if runtime_allowed && library.name.ends_with(":natives-linux") {
            natives.insert(library.name);
        }
        let relative = format!("minecraft/libraries/{path}");
        copy_cached(
            cache,
            &relative,
            stage,
            &format!("cache/minecraft-toolchain/{relative}"),
            sha1(&artifact.sha1)?,
            Some(artifact.size),
            &parent,
            report,
            request.max_bytes,
            cancellation,
        )?;
    }
    ensure!(
        !natives.is_empty(),
        "authenticated version metadata supplied no Linux native classifiers"
    );
    Ok(())
}

fn sha1(text: &str) -> eyre::Result<ContentHash> {
    ensure!(
        text.len() == 40
            && text
                .bytes()
                .all(|byte| byte.is_ascii_hexdigit() && !byte.is_ascii_uppercase()),
        "invalid authenticated SHA-1"
    );
    ContentHash::parse_hex(text, ContentHashAlgorithm::Sha1).map_err(|error| eyre::eyre!(error))
}

pub(super) fn validate_contract(
    contract: &LinuxToolchainLock,
    source_lock: &str,
    java_major: u16,
) -> eyre::Result<()> {
    ensure!(
        contract.schema == "sfm:linux_toolchain@1" && contract.platform == "linux/amd64",
        "unsupported Linux toolchain contract"
    );
    ensure!(
        contract.source_lock_sha256 == source_lock && contract.java_major == java_major,
        "Linux toolchain contract is not bound to this frozen source lock and Java major"
    );
    let (repository, digest) = contract
        .base_image
        .split_once("@sha256:")
        .ok_or_else(|| eyre::eyre!("base image requires an immutable SHA-256 digest"))?;
    ensure!(
        !repository.is_empty()
            && !repository.starts_with('-')
            && repository
                .bytes()
                .all(|byte| byte.is_ascii_alphanumeric() || b"._-/:".contains(&byte)),
        "invalid base image repository"
    );
    ensure!(
        digest.len() == 64
            && digest
                .bytes()
                .all(|byte| byte.is_ascii_hexdigit() && !byte.is_ascii_uppercase()),
        "invalid base image digest"
    );
    ensure!(
        !contract.java_identity.is_empty()
            && contract.java_identity.len() <= 160
            && contract
                .java_identity
                .bytes()
                .all(|byte| byte.is_ascii_alphanumeric() || b"._-+ ()".contains(&byte)),
        "unsafe Java identity"
    );
    crate::source_projection::promotion::validate_relative_path(&contract.cli_path)?;
    Ok(())
}

pub(super) fn linux_elf(bytes: &[u8]) -> bool {
    super::policy::linux_elf(bytes)
}

/// Export the verified union once, retaining per-projection cache gaps instead of
/// turning one unsupported/cold row into a false all-catalog runtime claim.
#[expect(
    clippy::too_many_lines,
    reason = "One bounded cache-only transaction records each catalog row and publishes a verified union"
)]
fn export_supported_catalog_cache(
    request: &ContainerBuildRequest,
    stage: &Path,
    report: &mut ContextManifest,
    cancellation: &CancellationToken,
) -> eyre::Result<()> {
    #[derive(facet::Facet)]
    struct ReadyRow {
        projection: String,
        source_lock_sha256: String,
        java_major: u16,
        puppet_features_present: bool,
        cache_ready: bool,
        runtime_validated: bool,
        missing: Vec<super::missing_artifact::MissingArtifact>,
    }
    let catalog = crate::source_projection::core_catalog::CoreCatalog::load(
        &request.repo_root,
        &request.repo_root,
    )?;
    let cache = checked_directory(&request.cache_root.join("minecraft-toolchain"))?;
    let mut rows = Vec::new();
    let mut locks = BTreeMap::<String, Vec<super::missing_artifact::MissingArtifact>>::new();
    for (key, entry) in &catalog.catalog.0 {
        cancellation.bail_if_cancelled()?;
        super::disk::check(stage, 8 * 1024 * 1024 * 1024)?;
        let project = collect_catalog_project(&request.repo_root, &request.repo_root, key)?;
        let raw = project.output_bytes("sfm-toolchain.lock.json")?.to_vec();
        let selected = &project.artifacts()["sfm-toolchain.lock.json"];
        ensure!(
            selected.source_bytes == raw && selected.source_path.contains("/build/lockfiles/"),
            "support cache lock must be an exact core lock copy"
        );
        let identity = sha256(&raw);
        let gaps = if let Some(previous) = locks.get(&identity) {
            previous.clone()
        } else {
            let text = std::str::from_utf8(&raw)?;
            let mut candidate = report.clone();
            candidate.missing.clear();
            let mut version_path = None;
            match parse_document(text)? {
                ToolchainLockfileDocument::V1(_) => missing(
                    &mut candidate,
                    "schema-1",
                    "No admitted container cache recipe",
                ),
                ToolchainLockfileDocument::V2 { lockfile, .. } => {
                    for artifact in lockfile.artifacts {
                        use crate::jar_build::ArtifactSource;
                        let portable = matches!(
                            artifact.source,
                            ArtifactSource::RemoteHttp | ArtifactSource::RemoteMaven
                        ) || matches!(artifact.source, ArtifactSource::SourceBuild)
                            && artifact
                                .source_git
                                .as_ref()
                                .is_some_and(|git| !git.dirty && git.remote_url.is_some());
                        let source_ready = export_source_producer(
                            &cache,
                            stage,
                            &artifact.cache_path,
                            artifact.coordinate.as_deref(),
                            artifact.hash,
                            artifact.source_git.as_ref(),
                            artifact.source_build.as_ref(),
                            &identity,
                            &mut candidate,
                            request,
                        )?;
                        add_locked(
                            &cache,
                            stage,
                            &artifact.cache_path,
                            artifact.hash,
                            portable && artifact.weak.is_none() && source_ready,
                            &identity,
                            &mut candidate,
                            request,
                            cancellation,
                            &mut version_path,
                        )?;
                    }
                }
                ToolchainLockfileDocument::V3(_) | ToolchainLockfileDocument::V4(_) => {
                    for artifact in read_current(text)?.artifacts {
                        use crate::toolchain_lockfile_schema::version::v3::ArtifactProvenanceV3;
                        let portable = matches!(
                            artifact.provenance,
                            ArtifactProvenanceV3::RemoteHttp | ArtifactProvenanceV3::RemoteMaven
                        ) || matches!(
                            artifact.provenance,
                            ArtifactProvenanceV3::SourceBuild
                        ) && artifact
                            .source_git
                            .as_ref()
                            .is_some_and(|git| !git.dirty && git.remote_url.is_some());
                        let source_ready = export_source_producer(
                            &cache,
                            stage,
                            &artifact.cache_path,
                            artifact.coordinate.as_deref(),
                            artifact.hash,
                            artifact.source_git.as_ref(),
                            artifact.source_build.as_ref(),
                            &identity,
                            &mut candidate,
                            request,
                        )?;
                        add_locked(
                            &cache,
                            stage,
                            &artifact.cache_path,
                            artifact.hash,
                            portable && artifact.weak.is_none() && source_ready,
                            &identity,
                            &mut candidate,
                            request,
                            cancellation,
                            &mut version_path,
                        )?;
                    }
                }
            }
            super::support_agent_nfrt::export(
                &request.repo_root,
                &cache,
                stage,
                key.rsplit("/mc-")
                    .next()
                    .ok_or_else(|| eyre::eyre!("catalog target missing"))?,
                &mut candidate,
                request.max_bytes,
                cancellation,
            )?;
            if let Some(version) = version_path {
                export_minecraft(
                    &cache,
                    stage,
                    &version,
                    &project.identity().minecraft_version,
                    &mut candidate,
                    request,
                    cancellation,
                )?;
            } else {
                missing(
                    &mut candidate,
                    "minecraft-version-json",
                    "No verified version metadata available",
                );
            }
            report.files = candidate.files;
            report.total_bytes = candidate.total_bytes;
            let gaps = candidate.missing;
            locks.insert(identity.clone(), gaps.clone());
            gaps
        };
        rows.push(ReadyRow {
            projection: key.clone(),
            source_lock_sha256: identity,
            java_major: project.build_target().java_major,
            puppet_features_present: entry
                .features
                .iter()
                .any(|feature| feature == "game_puppet_runtime"),
            cache_ready: gaps.is_empty(),
            runtime_validated: false,
            missing: gaps,
        });
    }
    let bytes = facet_json::to_vec(&rows)?;
    put_bytes(
        stage,
        "snapshot/support-readiness.json",
        &bytes,
        &catalog.catalog_sha256,
        report,
        request.max_bytes,
    )?;
    Ok(())
}

#[expect(
    clippy::too_many_arguments,
    reason = "Digest-bound producer admission and context staging boundary"
)]
fn export_source_producer(
    cache: &Path,
    stage: &Path,
    artifact: &Path,
    coordinate: Option<&str>,
    hash: ContentHash,
    git: Option<&crate::jar_build::engine::SourceGitProvenance>,
    build: Option<&crate::jar_build::engine::SourceBuildProvenance>,
    parent: &str,
    report: &mut ContextManifest,
    request: &ContainerBuildRequest,
) -> eyre::Result<bool> {
    if !super::source_producer::required(coordinate, build) {
        return Ok(true);
    }
    let result = (|| {
        let relative = portable_cache_path(artifact)?;
        let artifact = crate::source_projection::candidate_lock::checked_file(cache, &relative)?;
        super::source_producer::verify(cache, &artifact, coordinate, hash, git, build)
    })();
    match result {
        Ok(Some((relative, raw))) => {
            put_bytes(
                stage,
                &format!("cache/minecraft-toolchain/{relative}"),
                &raw,
                parent,
                report,
                request.max_bytes,
            )?;
            Ok(true)
        }
        Ok(None) => Ok(true),
        Err(error) => {
            missing(
                report,
                coordinate.unwrap_or("source-built input"),
                format!("producer receipt refused: {error:#}"),
            );
            Ok(false)
        }
    }
}
