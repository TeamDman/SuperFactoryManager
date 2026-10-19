//! Invocation-local actual-input authority for ordinary development Forge preparation.
//!
//! This key excludes unrelated SFM source bodies and the broad projection receipt.
//! It records local actual-byte evidence, not released or historical reconstruction.

#[path = "forge_preparation_capture.rs"]
mod capture;
#[path = "held_forge_preparation_input.rs"]
mod held_input;
#[path = "forge_preparation_input_request.rs"]
mod input_request;
#[path = "dependency_remap_artifact_inputs.rs"]
mod remap_artifact;
#[cfg(test)]
#[path = "dependency_remap_common_inputs_tests.rs"]
mod remap_tests;

pub(in crate::jar_build::engine) use remap_artifact::DependencyRemapArtifactInputs;
#[cfg(test)]
#[path = "forge_preparation_inputs_tests.rs"]
mod tests;

use super::super::ArtifactId;
use super::super::ArtifactPlan;
use super::super::BuildProjectIdentity;
use super::super::ContentHash;
use super::super::ContentHashAlgorithm;
use super::super::ExecutionContext;
use super::super::LoaderToolchainKind;
use super::super::SPECIALSOURCE_REVIEWED_COORDINATE;
use super::super::SPECIALSOURCE_REVIEWED_HASH;
use super::super::SPECIALSOURCE_REVIEWED_MAIN;
use super::super::authenticated_minecraft_inputs;
use super::super::download_compile_child;
use super::super::read_main_class;
use super::super::resolve_current_minecraft_libraries;
use super::super::resolved_artifact_hash;
use super::super::verify_named_sdk_identity;
use crate::jar_build::local_dependency_image::LocalDependencyImage;
use crate::source_projection::catalog_owned_project::CatalogOwnedProject;
use crate::source_projection::core_input_leases::DirectoryLeases;
use crate::source_projection::core_input_leases::MAX_DIRECTORY_LEASES;
use crate::source_projection::provenance::sha256;
use capture::ForgePreparationCapture;
use eyre::Result;
use eyre::ensure;
use held_input::HeldForgePreparationInput;
use input_request::ForgePreparationInputRequest;
use reqwest::blocking::Client;
use sha2::Digest as _;
use sha2::Sha256;
use std::collections::BTreeSet;
use std::path::Path;

const MAX_INPUT_FILES: usize = 4096;
const MAX_INPUT_BYTES: u64 = 2 * 1024 * 1024 * 1024;
const MAX_SMALL_BYTES: u64 = 16 * 1024 * 1024;
const AT_OUTPUT: &str = "src/main/resources/META-INF/accesstransformer.cfg";

pub(in crate::jar_build::engine) struct ForgePreparationInputs {
    fingerprint: String,
    kind: String,
    sdk_identity: String,
    sdk_image: Option<LocalDependencyImage>,
    files: Vec<HeldForgePreparationInput>,
    directories: DirectoryLeases,
    // Mutually exclusive bounded role bytes: Forge AT or the remap mapping.
    at: Option<Vec<u8>>,
}

impl ForgePreparationInputs {
    pub(super) fn prepare(context: &ExecutionContext<'_>, kind: &str) -> Result<Option<Self>> {
        ensure!(
            matches!(kind, "mcp" | "forge"),
            "Unknown Forge preparation kind"
        );
        let Some((mut owner, mut hash)) = Self::prepare_common(context, kind)? else {
            return Ok(None);
        };
        let Some(parent) = owner.capture_version(context)? else {
            return Ok(None);
        };
        if !owner.capture_producer_inputs(context, kind, &parent)? {
            return Ok(None);
        }
        if kind == "forge" {
            let BuildProjectIdentity::Development { project, .. } = &context.plan.identity else {
                eyre::bail!("Forge preparation lost its checked development owner");
            };
            if !owner.capture_at(context, project, &mut hash)? {
                return Ok(None);
            }
        }
        Ok(Some(owner.finish(context, hash)?))
    }

    fn prepare_common(
        context: &ExecutionContext<'_>,
        kind: &str,
    ) -> Result<Option<(Self, Sha256)>> {
        let BuildProjectIdentity::Development { project, target } = &context.plan.identity else {
            return Ok(None);
        };
        if context.plan.minecraft_version.as_str() != "1.19.2"
            || context.plan.loader_toolchain.kind != LoaderToolchainKind::ForgeGradleForge
            || target.receipt.loader != "forge"
            || !clean_environment()
        {
            return Ok(None);
        }
        let Some(sdk_identity) = &context.plan.java.execution_identity else {
            return Ok(None);
        };
        context.bail_if_cancelled()?;
        context
            .plan
            .require_strict("ordinary Forge preparation authority")?;
        context.plan.recheck_named_inputs()?;
        verify_named_sdk_identity(&context.plan.java)?;
        let mut owner = Self {
            fingerprint: String::new(),
            kind: kind.to_owned(),
            sdk_identity: sdk_identity.clone(),
            sdk_image: None,
            files: Vec::new(),
            directories: DirectoryLeases::default(),
            at: None,
        };
        if !owner.capture_sdk(context)? {
            return Ok(None);
        }
        let mut hash = Sha256::new();
        recipe_basis(
            &mut hash,
            kind,
            context.plan.minecraft_version.as_str(),
            &context.plan.loader_toolchain.base_coordinate,
            context.plan.java_release,
            context
                .plan
                .properties
                .get("mapping_channel")
                .map(String::as_str),
            context
                .plan
                .properties
                .get("mapping_version")
                .map(String::as_str),
        );
        if !owner.capture_lock(context, project, &target.receipt.lockfile_sha256, &mut hash)? {
            return Ok(None);
        }
        Ok(Some((owner, hash)))
    }

    pub(in crate::jar_build::engine) fn prepare_remap(
        context: &ExecutionContext<'_>,
        mapping_path: &Path,
    ) -> Result<Option<Self>> {
        let BuildProjectIdentity::Development { project, .. } = &context.plan.identity else {
            return Ok(None);
        };
        // Strict owners can install a read pool and steal a second producer
        // while a key lock is held. Keep their original route and all checks.
        if !project.uses_invocation_source_checks() {
            tracing::info!(
                reason = "independent_source_checks",
                "dependency_remap_cache_declined"
            );
            return Ok(None);
        }
        let expected = context
            .plan
            .cache_dir
            .join("forge/1.19.2/mappings/srg_to_official.tsrg");
        if !crate::jar_build::build_snapshot_receipt::same_path_spelling(mapping_path, &expected) {
            return Ok(None);
        }
        let Some((mut owner, hash)) = Self::prepare_common(context, "remap")? else {
            return Ok(None);
        };
        let tool = context.artifact(ArtifactId::from("tool-specialsource"))?;
        let reviewed =
            ContentHash::parse(SPECIALSOURCE_REVIEWED_HASH).map_err(|error| eyre::eyre!(error))?;
        if tool.coordinate.as_deref() != Some(SPECIALSOURCE_REVIEWED_COORDINATE)
            || resolved_artifact_hash(tool)? != reviewed
        {
            tracing::info!(
                reason = "unreviewed_specialsource",
                "dependency_remap_cache_declined"
            );
            return Ok(None);
        }
        let mut request = artifact_request(context, tool)?;
        request.expected.push(reviewed);
        if owner.capture_one(context, &request)?.is_none() {
            return Ok(None);
        }
        if !reviewed_remap_identity(
            tool.coordinate.as_deref(),
            &resolved_artifact_hash(tool)?,
            &read_main_class(&tool.cache_path)?,
        ) {
            return Ok(None);
        }
        let request =
            ForgePreparationInputRequest::new("dependency-remap:mapping", mapping_path.to_owned())
                .retained(MAX_SMALL_BYTES);
        let Some(raw) = owner.capture_one(context, &request)? else {
            return Ok(None);
        };
        owner.at = Some(raw);
        Ok(Some(owner.finish(context, hash)?))
    }

    fn finish(mut self, context: &ExecutionContext<'_>, hash: Sha256) -> Result<Self> {
        self.recheck(context)?;
        self.fingerprint = finish_fingerprint(
            hash,
            &self.kind,
            &self.sdk_identity,
            self.sdk_image
                .as_ref()
                .ok_or_else(|| eyre::eyre!("Missing SDK image"))?
                .identity(),
            &self.files,
        );
        Ok(self)
    }

    pub(in crate::jar_build::engine) fn fingerprint(&self) -> &str {
        &self.fingerprint
    }

    pub(super) fn at_bytes(&self) -> Option<&[u8]> {
        if self.kind == "forge" {
            self.at.as_deref()
        } else {
            None
        }
    }

    /// Exact held mapping bytes from `prepare_remap`, never a physical reread.
    /// # Panics
    /// Panics if called on an owner made for MCP or Forge instead of remapping.
    pub(in crate::jar_build::engine) fn mapping_bytes(&self) -> &[u8] {
        assert_eq!(self.kind, "remap", "mapping bytes require a remap owner");
        self.at
            .as_deref()
            .expect("admitted remap owner retains mapping bytes")
    }

    pub(in crate::jar_build::engine) fn recheck(
        &self,
        context: &ExecutionContext<'_>,
    ) -> Result<()> {
        recheck_captured_inputs(
            context,
            &self.sdk_identity,
            self.sdk_image.as_ref(),
            &self.files,
        )
    }

    /// Share only Sync evidence, while the original directory-lease owner stays
    /// on the calling thread through the whole parallel remap node.
    pub(in crate::jar_build::engine) fn remap_recheck(
        &self,
    ) -> impl Fn(&ExecutionContext<'_>) -> Result<()> + Sync + '_ {
        let sdk_identity = self.sdk_identity.as_str();
        let image = self.sdk_image.as_ref();
        let files = self.files.as_slice();
        move |context| recheck_captured_inputs(context, sdk_identity, image, files)
    }

    fn capture_sdk(&mut self, context: &ExecutionContext<'_>) -> Result<bool> {
        let Some(home) = context.plan.java.home.as_deref() else {
            return Ok(false);
        };
        for (role, path) in [
            ("sdk:java", context.plan.java.executable.clone()),
            ("sdk:javac", home.join("bin/javac.exe")),
            ("sdk:release", home.join("release")),
            ("sdk:modules", home.join("lib/modules")),
        ] {
            let mut request = ForgePreparationInputRequest::new(role, path);
            request.limit = 1024 * 1024 * 1024;
            if self.capture_one(context, &request)?.is_none() {
                return Ok(false);
            }
        }
        let image = match LocalDependencyImage::capture_home(
            home,
            &self.sdk_identity,
            &context.cancellation_token,
        ) {
            Ok(image) => image,
            Err(error) => {
                context.bail_if_cancelled()?;
                tracing::debug!(%error, "Ordinary Forge SDK image admission declined");
                return Ok(false);
            }
        };
        self.sdk_image = Some(image);
        // Four held files plus the reused bin/lib/conf image now cover the
        // same selected SDK throughout production; bind the original plan once.
        verify_named_sdk_identity(&context.plan.java)?;
        Ok(true)
    }

    fn capture_lock(
        &mut self,
        context: &ExecutionContext<'_>,
        project: &CatalogOwnedProject,
        expected: &str,
        hash: &mut Sha256,
    ) -> Result<bool> {
        let selected = project.selected_input("sfm-toolchain.lock.json")?;
        let bytes = selected.require_exact_copy()?;
        let source = ForgePreparationInputRequest::new(
            "selected-lock:authored",
            project.repo_root().join(selected.source_path()),
        )
        .retained(MAX_SMALL_BYTES);
        let output = ForgePreparationInputRequest::new(
            "selected-lock:generated",
            project.project_root().join(selected.output_path()),
        )
        .retained(MAX_SMALL_BYTES);
        for request in [source, output] {
            let Some(current) = self.capture_one(context, &request)? else {
                return Ok(false);
            };
            validate_selected_lock(&current, bytes, expected)?;
        }
        append(hash, selected.source_path().as_bytes());
        append(hash, selected.output_path().as_bytes());
        append(hash, expected.as_bytes());
        Ok(true)
    }

    fn capture_version(
        &mut self,
        context: &ExecutionContext<'_>,
    ) -> Result<Option<authenticated_minecraft_inputs::AuthenticatedMinecraftInputs>> {
        let artifact = &context.plan.minecraft.version_json;
        let request = ForgePreparationInputRequest::new(
            "minecraft:version-json",
            artifact.cache_path.clone(),
        )
        .pinned(resolved_artifact_hash(artifact)?)
        .retained(MAX_SMALL_BYTES);
        let Some(raw) = self.capture_one(context, &request)? else {
            return Ok(None);
        };
        // Development's current planner observed this actual parent. Binding it
        // locally does not claim an original released direct-version pin.
        match authenticated_minecraft_inputs::authenticate_pinned_version_json(
            context.plan.minecraft_version.as_str(),
            0,
            artifact
                .url
                .as_deref()
                .ok_or_else(|| eyre::eyre!("Missing version JSON URL"))?,
            &resolved_artifact_hash(artifact)?.to_string(),
            &raw,
        ) {
            Ok(parent) => Ok(Some(parent)),
            Err(error) => {
                tracing::debug!(
                    %error,
                    reason = "unsupported_version_metadata",
                    "forge_preparation_cache_declined"
                );
                Ok(None)
            }
        }
    }

    fn capture_producer_inputs(
        &mut self,
        context: &ExecutionContext<'_>,
        kind: &str,
        parent: &authenticated_minecraft_inputs::AuthenticatedMinecraftInputs,
    ) -> Result<bool> {
        let client = Client::builder()
            .user_agent("sfm-propagate-changes/no-gradle-toolchain")
            .build()?;
        let mut requests = Self::download_requests(context, kind, parent, &client)?;
        let tools: &[&str] = if kind == "mcp" {
            &[
                "mcp-config",
                "tool-installer-tools-1-3",
                "tool-installer-tools-1-2",
                "tool-mergetool-1-1-5",
                "tool-fart",
                "tool-forgeflower",
                "tool-diffpatch",
            ]
        } else {
            &[
                "forge-userdev",
                "forge-sources",
                "forge-universal",
                "forge-binarypatcher",
                "tool-access-transformers",
                "tool-fart",
                "tool-diffpatch",
            ]
        };
        for id in tools {
            requests.push(artifact_request(
                context,
                context.artifact(ArtifactId::from(*id))?,
            )?);
        }
        if kind == "forge" {
            if let Some(parchment) = context.maybe_artifact(ArtifactId::from("parchment-data")) {
                requests.push(artifact_request(context, parchment)?);
            }
            let mcp = context
                .plan
                .cache_dir
                .join("mcp")
                .join(context.plan.minecraft_version.as_str())
                .join("joined");
            for relative in [
                "patch/joined-patched-sources.jar",
                "mergeMappings/output.tsrg",
                "rename/output.jar",
            ] {
                requests.push(ForgePreparationInputRequest::new(
                    format!("mcp-output:{relative}"),
                    mcp.join(relative),
                ));
            }
        }
        requests.sort_by(|left, right| left.role.cmp(&right.role));
        ensure!(
            requests.windows(2).all(|pair| pair[0].role != pair[1].role),
            "Ambiguous Forge preparation input role"
        );
        if !self.capacity_allows(&requests) {
            return Ok(false);
        }
        for request in requests {
            if self.capture_one(context, &request)?.is_none() {
                return Ok(false);
            }
        }
        Ok(true)
    }

    fn download_requests(
        context: &ExecutionContext<'_>,
        kind: &str,
        parent: &authenticated_minecraft_inputs::AuthenticatedMinecraftInputs,
        client: &Client,
    ) -> Result<Vec<ForgePreparationInputRequest>> {
        use authenticated_minecraft_inputs::MinecraftCompileDownload as Role;
        let roles: &[(Role, &str, &str)] = if kind == "mcp" {
            &[
                (Role::ClientJar, "client", "client.jar"),
                (Role::ServerJar, "server", "server-bundle.jar"),
                (Role::ClientMappings, "client-mappings", "client.txt"),
            ]
        } else {
            &[
                (Role::ClientMappings, "client-mappings", "client.txt"),
                (Role::ServerMappings, "server-mappings", "server.txt"),
            ]
        };
        let mut requests = Vec::new();
        for (role, label, filename) in roles {
            let selected = parent.download(*role)?;
            let path = context.plan.minecraft_version_cache_dir.join(filename);
            download_compile_child(context, client, *role, &path)?;
            requests.push(
                ForgePreparationInputRequest::new(format!("minecraft:{label}"), path)
                    .pinned(*selected.expected_hash())
                    .sized(selected.expected_bytes()),
            );
        }
        if kind == "mcp" {
            let actual = resolve_current_minecraft_libraries(context, client)?;
            let mut expected = Vec::new();
            for selected in parent.libraries() {
                if !selected
                    .library_allowed_for_target(std::env::consts::OS, std::env::consts::ARCH)?
                {
                    continue;
                }
                let relative = selected
                    .library_relative_path()
                    .ok_or_else(|| eyre::eyre!("Missing selected library path"))?;
                let path = context.plan.minecraft_libraries_dir.join(relative);
                expected.push(path.clone());
                requests.push(
                    ForgePreparationInputRequest::new(format!("library:{relative}"), path)
                        .pinned(*selected.expected_hash())
                        .sized(selected.expected_bytes()),
                );
            }
            ensure!(
                actual == expected,
                "Actual Minecraft library selection differs from captured parent"
            );
        }
        Ok(requests)
    }

    fn capture_at(
        &mut self,
        context: &ExecutionContext<'_>,
        project: &CatalogOwnedProject,
        hash: &mut Sha256,
    ) -> Result<bool> {
        let input = project.selected_input(AT_OUTPUT)?;
        let request = ForgePreparationInputRequest::new(
            "selected-project-at",
            project.project_root().join(AT_OUTPUT),
        )
        .retained(1024 * 1024);
        let Some(raw) = self.capture_one(context, &request)? else {
            return Ok(false);
        };
        ensure!(
            raw == input.output_bytes(),
            "Actual selected generated AT changed"
        );
        append(hash, input.source_path().as_bytes());
        append(hash, input.output_path().as_bytes());
        append(hash, &[u8::from(input.declared_template())]);
        self.at = Some(raw);
        Ok(true)
    }

    fn capacity_allows(&self, requests: &[ForgePreparationInputRequest]) -> bool {
        if self.files.len().saturating_add(requests.len()) > MAX_INPUT_FILES {
            return false;
        }
        let directories = self
            .files
            .iter()
            .map(|input| input.path.as_path())
            .chain(requests.iter().map(|input| input.path.as_path()))
            .filter_map(Path::parent)
            .flat_map(Path::ancestors)
            .collect::<BTreeSet<_>>();
        directories.len() <= MAX_DIRECTORY_LEASES
    }

    fn capture_one(
        &mut self,
        context: &ExecutionContext<'_>,
        request: &ForgePreparationInputRequest,
    ) -> Result<Option<Vec<u8>>> {
        context.assert_allowed_input(&request.path)?;
        if !self.capacity_allows(std::slice::from_ref(request)) {
            return Ok(None);
        }
        let Some(captured) = HeldForgePreparationInput::capture(
            request,
            &mut self.directories,
            &context.cancellation_token,
        )?
        else {
            return Ok(None);
        };
        let current = self.files.iter().map(|input| input.bytes).sum::<u64>();
        if current
            .checked_add(captured.input.bytes)
            .is_none_or(|bytes| bytes > MAX_INPUT_BYTES)
        {
            return Ok(None);
        }
        self.files.push(captured.input);
        // An empty vector is success for non-retained payloads; only small
        // authority inputs keep their actual bytes in this invocation owner.
        Ok(Some(captured.retained.unwrap_or_default()))
    }
}

fn reviewed_remap_identity(coordinate: Option<&str>, hash: &ContentHash, main: &str) -> bool {
    coordinate == Some(SPECIALSOURCE_REVIEWED_COORDINATE)
        && hash.to_string() == SPECIALSOURCE_REVIEWED_HASH
        && main == SPECIALSOURCE_REVIEWED_MAIN
}

fn recheck_captured_inputs(
    context: &ExecutionContext<'_>,
    sdk_identity: &str,
    image: Option<&LocalDependencyImage>,
    files: &[HeldForgePreparationInput],
) -> Result<()> {
    context.bail_if_cancelled()?;
    ensure!(
        clean_environment(),
        "Inherited Java environment changed during preparation"
    );
    ensure!(
        context.plan.java.execution_identity.as_deref() == Some(sdk_identity),
        "Selected SDK execution identity changed"
    );
    context.plan.recheck_named_inputs()?;
    for file in files {
        context.bail_if_cancelled()?;
        file.recheck()?;
    }
    image
        .ok_or_else(|| eyre::eyre!("Missing held SDK image"))?
        .recheck(&context.cancellation_token)?;
    Ok(())
}

fn finish_fingerprint(
    mut hash: Sha256,
    kind: &str,
    sdk_identity: &str,
    sdk_image_identity: &str,
    files: &[HeldForgePreparationInput],
) -> String {
    append(&mut hash, kind.as_bytes());
    append(&mut hash, sdk_identity.as_bytes());
    append(&mut hash, sdk_image_identity.as_bytes());
    let mut files = files.iter().collect::<Vec<_>>();
    files.sort_by(|left, right| left.role.cmp(&right.role));
    for input in files {
        append(&mut hash, input.role.as_bytes());
        append(&mut hash, &input.bytes.to_le_bytes());
        append(&mut hash, input.sha256.as_bytes());
    }
    format!("sha256:{:x}", hash.finalize())
}

fn validate_selected_lock(current: &[u8], selected: &[u8], expected: &str) -> Result<()> {
    ensure!(
        current == selected && sha256(current) == expected,
        "Current selected source lock differs from its checked original bytes"
    );
    Ok(())
}

fn clean_environment() -> bool {
    environment_is_clean(|name| {
        let value = std::env::var_os(name);
        if value.is_some() {
            tracing::info!(
                environment_name = name,
                reason = "inherited_jvm_control",
                "forge_preparation_cache_declined"
            );
        }
        value
    })
}

fn environment_is_clean(mut value: impl FnMut(&str) -> Option<std::ffi::OsString>) -> bool {
    [
        "JAVA_TOOL_OPTIONS",
        "JDK_JAVA_OPTIONS",
        "_JAVA_OPTIONS",
        "JDK_ALTERNATE_VM",
    ]
    .iter()
    .all(|name| value(name).is_none())
}

fn artifact_request(
    context: &ExecutionContext<'_>,
    artifact: &ArtifactPlan,
) -> Result<ForgePreparationInputRequest> {
    let mut request = ForgePreparationInputRequest::new(
        format!("artifact:{}", artifact.id),
        artifact.cache_path.clone(),
    )
    .pinned(resolved_artifact_hash(artifact)?);
    if let Some(coordinate) = &artifact.coordinate {
        let pin = context
            .plan
            .lockfile
            .as_ref()
            .and_then(|lock| {
                lock.artifacts
                    .iter()
                    .find(|pin| pin.coordinate.as_ref() == Some(coordinate))
            })
            .ok_or_else(|| eyre::eyre!("Preparation tool is absent from current lock"))?;
        if pin.weak.is_none() {
            request.expected.push(pin.hash);
        }
    }
    Ok(request)
}

fn recipe_basis(
    hash: &mut Sha256,
    kind: &str,
    minecraft_version: &str,
    loader_coordinate: &str,
    java_release: u32,
    mapping_channel: Option<&str>,
    mapping_version: Option<&str>,
) {
    append(hash, b"sfm:ordinary_forge_preparation_actual_inputs@1");
    append(hash, kind.as_bytes());
    append(hash, std::env::consts::OS.as_bytes());
    append(hash, std::env::consts::ARCH.as_bytes());
    append(hash, minecraft_version.as_bytes());
    append(hash, loader_coordinate.as_bytes());
    append(hash, &java_release.to_le_bytes());
    for (name, value) in [
        ("mapping_channel", mapping_channel),
        ("mapping_version", mapping_version),
    ] {
        append(hash, name.as_bytes());
        append(hash, &[u8::from(value.is_some())]);
        append(hash, value.unwrap_or_default().as_bytes());
    }
    if kind == "remap" {
        append_remap_recipe(hash);
    }
    for (name, source) in [
        (
            "producer",
            include_bytes!("forge_preparation_producers.rs").as_slice(),
        ),
        (
            "java-launch",
            include_bytes!("engine_execute.rs").as_slice(),
        ),
        ("process-launch", include_bytes!("engine_run.rs").as_slice()),
        (
            "cache-algorithm",
            include_bytes!("forge_preparation_cache.rs").as_slice(),
        ),
        (
            "input-algorithm",
            include_bytes!("forge_preparation_inputs.rs").as_slice(),
        ),
        (
            "held-input",
            include_bytes!("held_forge_preparation_input.rs").as_slice(),
        ),
        (
            "input-hashes",
            include_bytes!("forge_preparation_input_hashes.rs").as_slice(),
        ),
        (
            "input-request",
            include_bytes!("forge_preparation_input_request.rs").as_slice(),
        ),
        (
            "capture-model",
            include_bytes!("forge_preparation_capture.rs").as_slice(),
        ),
        (
            "source-zip-helpers",
            include_bytes!("engine_sources.rs").as_slice(),
        ),
        (
            "mapping-helpers",
            include_bytes!("engine_mappings_compare_artifacts.rs").as_slice(),
        ),
        (
            "identifier-remapper",
            include_bytes!("source_identifier_remapper.rs").as_slice(),
        ),
        (
            "library-platform",
            include_bytes!("library_platform.rs").as_slice(),
        ),
        (
            "sdk-image",
            include_bytes!("local_dependency_image.rs").as_slice(),
        ),
        ("content-hash", include_bytes!("hash.rs").as_slice()),
        ("cargo-lock", include_bytes!("../../Cargo.lock").as_slice()),
    ] {
        append(hash, name.as_bytes());
        append(hash, source);
    }
}

fn append_remap_recipe(hash: &mut Sha256) {
    for (name, source) in [
        (
            "remap-launch",
            include_bytes!("engine_specialsource_launch.rs").as_slice(),
        ),
        (
            "remap-cache",
            include_bytes!("dependency_remap_cache.rs").as_slice(),
        ),
        (
            "remap-workspace",
            include_bytes!("dependency_remap_attempt.rs").as_slice(),
        ),
        (
            "remap-artifact-input",
            include_bytes!("dependency_remap_artifact_inputs.rs").as_slice(),
        ),
    ] {
        append(hash, name.as_bytes());
        append(hash, source);
    }
}

fn append(hash: &mut Sha256, bytes: &[u8]) {
    hash.update((bytes.len() as u64).to_le_bytes());
    hash.update(bytes);
}
