// Included in the engine namespace. Exact NeoForm compile admission and
// acquisition; no inherited launcher outputs or arbitrary byte-batch inputs.

struct NamedNeoformCompileRecipe {
    target: &'static str,
    minecraft: &'static str,
    loader: &'static str,
    lock: &'static str,
    compiler_and_tool: (u32, u32),
}

// These are the six original recipes reviewed by the retained launch contract
// and child supplement. Admission does not establish successful native builds.
const NAMED_NEOFORM_RECIPES: [NamedNeoformCompileRecipe; 6] = [
    NamedNeoformCompileRecipe {
        target: "1.20.2",
        minecraft: "1.20.2",
        loader: "net.neoforged:neoforge:20.2.86",
        lock: "sha256:df4fd41c7bde7b3c622e68e92c0fa3c6f7eee8895be847ff57f11a3a5503bdd9",
        compiler_and_tool: (17, 21),
    },
    NamedNeoformCompileRecipe {
        target: "1.20.3",
        minecraft: "1.20.3",
        loader: "net.neoforged:neoforge:20.3.8-beta",
        lock: "sha256:fd21086bbb41c797325c5fbacfe38c7861b0179f8901fd799526a2b01d21186d",
        compiler_and_tool: (17, 21),
    },
    NamedNeoformCompileRecipe {
        target: "1.20.4",
        minecraft: "1.20.4",
        loader: "net.neoforged:neoforge:20.4.231",
        lock: "sha256:565fbfedcb830c76cb1ba9791cc70e16f4eec9cea9229ffc707b58cc93487406",
        compiler_and_tool: (17, 21),
    },
    NamedNeoformCompileRecipe {
        target: "1.21.0",
        minecraft: "1.21",
        loader: "net.neoforged:neoforge:21.0.143",
        lock: "sha256:2b6930878897d7cf1db94badbd98ba58f0fe21cca61e3656c298421c3cea32c4",
        compiler_and_tool: (21, 21),
    },
    NamedNeoformCompileRecipe {
        target: "1.21.1",
        minecraft: "1.21.1",
        loader: "net.neoforged:neoforge:21.1.206",
        lock: "sha256:2e06a563528734c0b3cb8396d8f151e2f69e782ef19721568906157380b2a876",
        compiler_and_tool: (21, 21),
    },
    NamedNeoformCompileRecipe {
        target: "26.1.2",
        minecraft: "26.1.2",
        loader: "net.neoforged:neoforge:26.1.2.72",
        lock: "sha256:2f4fbd00de200a7ba089d3dc5c19994de1ffb00b7b1a9ae56afc045a9de7f37f",
        compiler_and_tool: (25, 25),
    },
];

/// Pure CLI preflight. Full project admission still checks the raw lock,
/// loader, compiler and tool identities after source ownership is established.
pub(crate) fn preflight_named_neoform_recipe(
    target: &str,
    minecraft: &str,
    recipe: &str,
) -> eyre::Result<bool> {
    let Some(expected) = NAMED_NEOFORM_RECIPES
        .iter()
        .find(|row| row.target == target)
    else {
        return Ok(false);
    };
    eyre::ensure!(
        cfg!(any(windows, target_os = "linux"))
            && minecraft == expected.minecraft
            && recipe == format!("sfm:released-native-inputs/4.34.0/{target}@1"),
        "named NeoForm preflight requires its exact reviewed Windows/Linux recipe"
    );
    Ok(true)
}

fn require_named_neoform_recipe_identity(
    target: &str,
    minecraft: &str,
    recipe: &str,
    lock: &str,
    compiler_and_tool: (u32, u32),
) -> eyre::Result<&'static NamedNeoformCompileRecipe> {
    let expected = NAMED_NEOFORM_RECIPES
        .iter()
        .find(|row| row.target == target)
        .ok_or_else(|| eyre::eyre!("named NeoForm requires an exact reviewed recipe"))?;
    eyre::ensure!(
        cfg!(any(windows, target_os = "linux"))
            && minecraft == expected.minecraft
            && recipe == format!("sfm:released-native-inputs/4.34.0/{target}@1")
            && lock == expected.lock
            && compiler_and_tool == expected.compiler_and_tool,
        "named NeoForm lost its original target/recipe/raw-lock/compiler/tool identity"
    );
    Ok(expected)
}

fn validate_named_neoform_compile(
    project: &crate::source_projection::frozen_recipe_project::FrozenRecipeProject,
) -> eyre::Result<()> {
    let receipt = project.receipt();
    let released = &receipt.released_inputs;
    let expected = require_named_neoform_recipe_identity(
        &receipt.ownership.target_id,
        &receipt.ownership.minecraft_version,
        &released.recipe_id,
        &receipt.prepared_inputs.source_lock_sha256,
        (
            u32::from(receipt.compiler_release),
            u32::from(receipt.tool_jvm_minimum),
        ),
    )?;
    eyre::ensure!(
        released.target_id == expected.target
            && released.minecraft_version == expected.minecraft
            && released.loader_kind == "neogradle_userdev"
            && released.loader_coordinate == expected.loader,
        "named NeoForm Compile requires its exact reviewed Windows/Linux loader recipe"
    );
    crate::source_projection::nfrt_child_identity_supplement::NfrtChildIdentitySupplement::from_prepared_at(
        project.project().repo_root(), Arc::clone(project.dependencies()), false,
    )?;
    Ok(())
}

#[cfg(any(windows, target_os = "linux"))]
struct DevelopmentNeoformSource<'a> {
    owner: crate::source_projection::nfrt_project_owner::DevelopmentNfrtSourceOwner<'a>,
    dependencies:
        Arc<crate::source_projection::development_nfrt_dependencies::DevelopmentNfrtDependencies>,
    supplement:
        crate::source_projection::nfrt_child_identity_supplement::NfrtChildIdentitySupplement,
}

#[cfg(any(windows, target_os = "linux"))]
fn development_neoform_source<'a>(
    project: &'a crate::source_projection::catalog_owned_project::CatalogOwnedProject,
    profile: &str,
) -> eyre::Result<DevelopmentNeoformSource<'a>> {
    let owner =
        crate::source_projection::nfrt_project_owner::DevelopmentNfrtSourceOwner::from_checked(
            project, profile,
        )?;
    let dependencies = Arc::new(crate::source_projection::development_nfrt_dependencies::DevelopmentNfrtDependencies::from_checked(&owner, false)?);
    let supplement = crate::source_projection::nfrt_child_identity_supplement::NfrtChildIdentitySupplement::from_development_at(project.repo_root(), Arc::clone(&dependencies), false)?;
    Ok(DevelopmentNeoformSource {
        owner,
        dependencies,
        supplement,
    })
}

#[cfg(any(windows, target_os = "linux"))]
fn resolve_development_minecraft_plan(
    project: &crate::source_projection::catalog_owned_project::CatalogOwnedProject,
    profile: &str,
    minecraft_cache: &Path,
    client: &Client,
    cancellation: &CancellationToken,
) -> eyre::Result<MinecraftPlan> {
    use crate::source_projection::nfrt_project_owner::NfrtProjectOwner;
    use authenticated_minecraft_inputs::MinecraftCompileDownload as Role;
    let source = development_neoform_source(project, profile)?;
    let request = source.dependencies.version_json_request()?;
    let pin = source.dependencies.artifact_pin(&request)?;
    let url = pin.url.as_deref().expect("exact version JSON URL");
    let minecraft = &source.dependencies.receipt().minecraft_version;
    let path = minecraft_cache
        .join("versions")
        .join(minecraft)
        .join("version.json");
    let raw = acquire_named_frozen_download(
        cancellation,
        client,
        url,
        &path,
        &pin.hash,
        16 * 1024 * 1024,
        None,
        || source.owner.recheck_source(),
    )?;
    let catalog =
        super::nfrt_requests::NfrtRequestCatalog::Development(Arc::clone(&source.dependencies));
    let inputs = catalog.authenticate_version_json(minecraft, url, &raw)?;
    let (client_mappings_url, server_mappings_url) = inputs.compile_mapping_urls()?;
    source.owner.recheck_source()?;
    Ok(MinecraftPlan {
        version_manifest: None,
        version_json: plain_artifact(
            ArtifactId::from("minecraft-version-json"),
            url,
            path,
            ArtifactPurpose::from("exact captured-development Minecraft metadata"),
        )?,
        client_jar_url: inputs.download(Role::ClientJar)?.url().to_owned(),
        server_jar_url: inputs.download(Role::ServerJar)?.url().to_owned(),
        client_mappings_url,
        server_mappings_url,
        libraries_count: inputs.libraries().count(),
        authenticated_inputs: Some(inputs),
    })
}

#[cfg(not(any(windows, target_os = "linux")))]
fn resolve_development_minecraft_plan(
    _: &crate::source_projection::catalog_owned_project::CatalogOwnedProject,
    _: &str,
    _: &Path,
    _: &Client,
    _: &CancellationToken,
) -> eyre::Result<MinecraftPlan> {
    eyre::bail!("development NeoForm requires the Windows held-input adapter")
}

#[cfg(any(windows, target_os = "linux"))]
enum NeoformCompileSource<'a> {
    Released(&'a crate::source_projection::frozen_recipe_project::FrozenRecipeProject),
    Admitted {
        frozen: &'a crate::source_projection::frozen_recipe_project::FrozenRecipeProject,
        owner: crate::source_projection::retained_nfrt_project_owner::RetainedNfrtProjectOwner<'a>,
    },
    Development(Box<DevelopmentNeoformSource<'a>>),
}

#[cfg(any(windows, target_os = "linux"))]
impl<'a> NeoformCompileSource<'a> {
    fn from_plan(plan: &'a BuildPlan) -> eyre::Result<Self> {
        match &plan.identity {
            BuildProjectIdentity::Catalog { frozen } => {
                validate_named_neoform_compile(frozen)?;
                Ok(Self::Released(frozen))
            }
            BuildProjectIdentity::CatalogAdmitted { inputs } => {
                validate_named_neoform_compile(inputs.project())?;
                Ok(Self::Admitted {
                    frozen: inputs.project(),
                    owner: crate::source_projection::retained_nfrt_project_owner::RetainedNfrtProjectOwner::from_admitted(inputs.project())?,
                })
            }
            BuildProjectIdentity::Development { project, target } => {
                Ok(Self::Development(Box::new(development_neoform_source(
                    project,
                    &target.receipt.dependency_profile,
                )?)))
            }
            BuildProjectIdentity::Worktree { .. } => {
                eyre::bail!("retained NeoForm host requires a checked catalog owner")
            }
        }
    }

    fn owner(&self) -> &dyn crate::source_projection::nfrt_project_owner::NfrtProjectOwner {
        match self {
            Self::Released(project) => *project,
            Self::Admitted { owner, .. } => owner,
            Self::Development(source) => &source.owner,
        }
    }

    fn catalog(&self) -> super::nfrt_requests::NfrtRequestCatalog {
        match self {
            Self::Released(project)
            | Self::Admitted {
                frozen: project, ..
            } => super::nfrt_requests::NfrtRequestCatalog::Released(Arc::clone(
                project.dependencies(),
            )),
            Self::Development(source) => super::nfrt_requests::NfrtRequestCatalog::Development(
                Arc::clone(&source.dependencies),
            ),
        }
    }

    fn export(
        &self,
        selected: &crate::jdk::ResolvedJava,
        executable: &[u8],
        invocation: &str,
    ) -> eyre::Result<super::nfrt_launch_contract::NfrtExportPlan> {
        use super::nfrt_launch_contract::NfrtExportPlan;
        use crate::source_projection::nfrt_child_identity_supplement::NfrtChildIdentitySupplement;
        match self {
            Self::Released(project)
            | Self::Admitted {
                frozen: project, ..
            } => {
                let supplement = NfrtChildIdentitySupplement::from_prepared_at(
                    project.project().repo_root(),
                    Arc::clone(project.dependencies()),
                    false,
                )?;
                NfrtExportPlan::prepare(
                    project,
                    &supplement,
                    selected,
                    executable,
                    invocation,
                    false,
                )
            }
            Self::Development(source) => NfrtExportPlan::prepare_development(
                &source.owner,
                &source.dependencies,
                &source.supplement,
                selected,
                executable,
                invocation,
                false,
            ),
        }
    }
}

// Only receipt-bound producer inputs depart from the historical Maven path.
// Verification precedes the read and no missing producer may use remote fallback.
#[cfg(any(windows, target_os = "linux"))]
fn named_neoform_coordinate_cache_path(
    resolver: &Resolver,
    maven_cache: &Path,
    coordinate: &MavenCoordinate,
    remote: bool,
) -> eyre::Result<(PathBuf, bool)> {
    if let Some(path) = resolver.verified_producer_cache_path(coordinate)? {
        Ok((path, false))
    } else {
        Ok((maven_cache_path_for(maven_cache, coordinate), remote))
    }
}

#[cfg(all(test, any(windows, target_os = "linux")))]
#[path = "named_neoform_producer_tests.rs"]
mod named_neoform_producer_tests;

#[cfg(any(windows, target_os = "linux"))]
#[expect(
    clippy::too_many_lines,
    reason = "Keep the three frozen acquisition authorities and their cache-miss checks together."
)]
fn acquire_named_neoform_inputs(
    context: &ExecutionContext<'_>,
    source: &NeoformCompileSource<'_>,
) -> eyre::Result<super::nfrt_launch_contract::PreparedNfrtInvocationInput> {
    let project = source.owner();
    let catalog = source.catalog();
    context.plan.recheck_named_inputs()?;
    context.plan.recheck_named_sdk()?;
    let java = &context.plan.java;
    let selected = crate::jdk::ResolvedJava {
        executable: fs::canonicalize(&java.executable)?,
        home: java.home.as_ref().map(fs::canonicalize).transpose()?,
        version_output: java.version_output.clone(),
        major_version: java.major_version,
        selection: java.selection.clone(),
        pin_url: java.pin_url.clone(),
        pin_sha512: java.pin_sha512.clone(),
    };
    let nonce = SystemTime::now().duration_since(UNIX_EPOCH)?.as_nanos();
    let invocation = format!("native-{}-{nonce}", std::process::id());
    let executable = read_named_authenticated_file(&selected.executable, 16 * 1024 * 1024)?;
    let export = source.export(&selected, &executable, &invocation)?;
    tracing::info!(target = %export.declaration().target_id,
        invocation = %export.declaration().invocation_id, "Acquiring exact named NeoForm inputs");
    let resolver = context.plan.resolver(&context.cancellation_token)?;
    let acquired = export.acquire_inputs(
        project,
        |request| {
            context.bail_if_cancelled()?;
            let (hash, remote) = catalog.transport_pin(request)?;
            let (path, remote) = if let Some(coordinate) = request.resolved_coordinate() {
                named_neoform_coordinate_cache_path(
                    &resolver,
                    &context.plan.maven_cache_dir,
                    &MavenCoordinate::parse(coordinate)?,
                    remote,
                )?
            } else {
                eyre::ensure!(
                    request.exact_transport_url() == Some(export.version_json_url()),
                    "NFRT original URL is not its exact version metadata"
                );
                (
                    context.plan.minecraft.version_json.cache_path.clone(),
                    remote,
                )
            };
            context.assert_allowed_input(&path)?;
            acquire_named_frozen_download(
                &context.cancellation_token,
                &resolver.client,
                request.exact_transport_url().unwrap_or(""),
                &path,
                &hash,
                512 * 1024 * 1024,
                None,
                || {
                    context.plan.before_named_acquisition()?;
                    eyre::ensure!(
                        remote && request.exact_transport_url().is_some(),
                        "NFRT original cache miss has no frozen remote acquisition recipe"
                    );
                    Ok(())
                },
            )
        },
        |request| {
            context.bail_if_cancelled()?;
            let path = maven_cache_path_for(
                &context.plan.maven_cache_dir,
                &MavenCoordinate::parse(request.coordinate())?,
            );
            context.assert_allowed_input(&path)?;
            let size = u64::try_from(request.bytes())?;
            acquire_named_frozen_download_verified(
                &context.cancellation_token,
                &resolver.client,
                request.exact_url(),
                &path,
                size,
                |bytes| verify_named_supplement_bytes(bytes, request.sha256(), size),
                || context.plan.before_named_acquisition(),
            )
        },
        |request| {
            context.bail_if_cancelled()?;
            let path = if let Some(relative) = request.library_relative_path() {
                context.plan.minecraft_libraries_dir.join(relative)
            } else {
                let minecraft = &context.plan.minecraft;
                let name = if request.url() == minecraft.client_jar_url {
                    "client.jar"
                } else if request.url() == minecraft.server_jar_url {
                    "server.jar"
                } else if Some(request.url()) == minecraft.client_mappings_url.as_deref() {
                    "client.txt"
                } else if Some(request.url()) == minecraft.server_mappings_url.as_deref() {
                    "server.txt"
                } else {
                    eyre::bail!("NFRT Minecraft request has no exact compile role")
                };
                context.plan.minecraft_version_cache_dir.join(name)
            };
            context.assert_allowed_input(&path)?;
            acquire_named_frozen_download(
                &context.cancellation_token,
                &resolver.client,
                request.url(),
                &path,
                request.expected_hash(),
                request.expected_bytes(),
                Some(request.expected_bytes()),
                || context.plan.before_named_acquisition(),
            )
        },
    )?;
    context.plan.recheck_named_sdk()?;
    Ok(acquired)
}

fn verify_named_supplement_bytes(
    bytes: &[u8],
    expected_sha256: &str,
    expected_size: u64,
) -> eyre::Result<()> {
    eyre::ensure!(
        expected_sha256.len() == 64
            && expected_sha256
                .bytes()
                .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte)),
        "named supplement requires a complete lowercase SHA-256 pin"
    );
    eyre::ensure!(
        u64::try_from(bytes.len())? == expected_size,
        "named supplement size mismatch; no repair performed"
    );
    eyre::ensure!(
        crate::source_projection::provenance::sha256(bytes) == format!("sha256:{expected_sha256}"),
        "named supplement SHA-256 mismatch; no repair performed"
    );
    Ok(())
}

#[cfg(any(windows, target_os = "linux"))]
fn execute_named_neoform_compile(
    context: &ExecutionContext<'_>,
    target: BuildTarget,
    started: Instant,
) -> eyre::Result<()> {
    with_named_neoform_context(context, |consumer| {
        execute_project_build(consumer, target, started)
    })
}

#[cfg(any(windows, target_os = "linux"))]
#[expect(
    clippy::too_many_lines,
    reason = "Keep current source authority, historical/fresh origin and held SDK/output lifetimes visible across both consumer paths."
)]
fn with_named_neoform_context<T>(
    context: &ExecutionContext<'_>,
    consume: impl FnOnce(&ExecutionContext<'_>) -> eyre::Result<T>,
) -> eyre::Result<T> {
    let source = NeoformCompileSource::from_plan(context.plan)?;
    let project = source.owner();
    let prepared = acquire_named_neoform_inputs(context, &source)?;
    let java = &context.plan.java;
    let selected = crate::jdk::ResolvedJava {
        executable: fs::canonicalize(&java.executable)?,
        home: java.home.as_ref().map(fs::canonicalize).transpose()?,
        version_output: java.version_output.clone(),
        major_version: java.major_version,
        selection: java.selection.clone(),
        pin_url: java.pin_url.clone(),
        pin_sha512: java.pin_sha512.clone(),
    };
    let mut sdk =
        crate::source_projection::nfrt_tool_sdk::NfrtToolSdk::capture(&selected, &prepared)?;
    let profile_key = if context.plan.derived_cache_root.is_some() {
        None
    } else {
        match super::derived_toolchain_cache::key(project, &prepared, &mut sdk) {
            Ok(key) => key,
            Err(error) => {
                eprintln!(
                    "Derived toolchain key unavailable: {error:#}; using fresh owned execution"
                );
                None
            }
        }
    };
    if let Some(profile) = profile_key
        .as_deref()
        .filter(|_| !context.plan.refresh)
        .and_then(super::derived_toolchain_cache::lookup)
    {
        context
            .plan
            .require_strict("unverified derived NeoForm profile")?;
        project.recheck_source()?;
        sdk.recheck()?;
        let mut consumer = ExecutionContext::new(context.plan, context.cancellation_token.clone())?;
        consumer.held_neoform_compile_jar = Some(&profile.classes);
        consumer.immutable_neoform_profile = true;
        let result = consume(&consumer);
        project.recheck_source()?;
        sdk.recheck()?;
        return result;
    }
    // A separate trusted-local historical dependency cache never enables NFRT
    // inherited restoration or turns historical output into fresh execution.
    let mut cache = None;
    let mut execution_image = None;
    if cfg!(windows) {
        match super::local_dependency_image::LocalDependencyImage::capture(
            &sdk,
            &context.cancellation_token,
        ) {
            Ok(image) => execution_image = Some(image),
            Err(error) => {
                context.bail_if_cancelled()?;
                tracing::warn!(%error, "Selected SDK image unavailable; using fresh owned execution without local dependency reuse");
            }
        }
    }
    if let Some(image) = &execution_image
        && super::local_dependency_cache::environment_allows_reuse()
    {
        let attempt = (|| -> eyre::Result<_> {
            const AT: &str = "src/main/resources/META-INF/accesstransformer.cfg";
            let selected_at = if project.project().receipt().files.contains_key(AT) {
                Some(project.project().selected_input(AT)?)
            } else {
                None
            };
            let key = super::local_dependency_key::LocalDependencyKey::from_prepared(
                &prepared,
                sdk.identity(),
                image.identity(),
                &super::local_dependency_cache::implementation_identity(),
                selected_at.as_ref().map(crate::source_projection::catalog_owned_project::CatalogOwnedInput::output_bytes),
            )?;
            let cache = super::local_dependency_cache::LocalDependencyCache::begin(
                &context
                    .plan
                    .derived_cache_root
                    .as_ref()
                    .unwrap_or(&context.plan.common_cache_dir)
                    .join("local-dependencies-v1"),
                key,
                &context.cancellation_token,
            )?;
            Ok(cache)
        })();
        match attempt {
            Ok(prepared_cache) => cache = Some(prepared_cache),
            Err(error) => {
                context.bail_if_cancelled()?;
                tracing::warn!(%error, "Local dependency cache unavailable; preserving it and using fresh owned execution");
            }
        }
    }
    if !context.plan.refresh
        && let Some(cache) = &mut cache
        && let Some(classes) = cache.lookup(&prepared, &context.cancellation_token)?
    {
        project.recheck_source()?;
        sdk.recheck()?;
        if let Some(image) = &execution_image {
            image.recheck(&context.cancellation_token)?;
        }
        cache.release_producer();
        let mut consumer = ExecutionContext::new(context.plan, context.cancellation_token.clone())?;
        consumer.held_neoform_compile_jar = Some(&classes);
        // Dependency origin does not authorize existence-only project hits.
        // The separate project-output manifest policy verifies those bytes.
        consumer.verify_compilation_outputs = true;
        consumer.compiler_image_identity = execution_image
            .as_ref()
            .map(|image| image.identity().to_owned());
        consumer.compiler_image = execution_image.as_ref();
        let result = consume(&consumer);
        project.recheck_source()?;
        sdk.recheck()?;
        if let Some(image) = &execution_image {
            image.recheck(&context.cancellation_token)?;
        }
        return result;
    }
    context
        .plan
        .require_strict("NeoForm dependency producer cache miss")?;
    let result = super::nfrt_owned_host::with_owned_compiler_inputs(
        project,
        &prepared,
        &mut sdk,
        &context.cancellation_token,
        context.plan.derived_cache_root.as_deref(),
        |classes, sources, receipts| {
            if let Some(image) = &execution_image {
                image.recheck(&context.cancellation_token)?;
            }
            if let Some(cache) = &mut cache {
                if let Err(error) = cache.publish(
                    &prepared,
                    classes,
                    sources,
                    receipts,
                    &context.cancellation_token,
                ) {
                    context.bail_if_cancelled()?;
                    tracing::warn!(%error, "Local dependency publication failed; retained evidence and using the successful fresh inputs");
                }
                cache.release_producer();
            }
            context.bail_if_cancelled()?;
            let normal = dunce::simplified(classes);
            eyre::ensure!(
                fs::canonicalize(normal)? == fs::canonicalize(classes)?,
                "NeoForm compiler classpath spelling changed its held file"
            );
            let mut consumer =
                ExecutionContext::new(context.plan, context.cancellation_token.clone())?;
            consumer.held_neoform_compile_jar = Some(normal);
            consumer.verify_compilation_outputs = true;
            consumer.compiler_image_identity = execution_image
                .as_ref()
                .map(|image| image.identity().to_owned());
            consumer.compiler_image = execution_image.as_ref();
            consume(&consumer)
        },
    );
    if let Some(image) = &execution_image {
        image.recheck(&context.cancellation_token)?;
    }
    result
}

#[cfg(not(any(windows, target_os = "linux")))]
fn with_named_neoform_context<T>(
    _: &ExecutionContext<'_>,
    _: impl FnOnce(&ExecutionContext<'_>) -> eyre::Result<T>,
) -> eyre::Result<T> {
    eyre::bail!("named NeoForm launch requires the Windows held-input adapter")
}

#[cfg(not(any(windows, target_os = "linux")))]
fn execute_named_neoform_compile(
    _: &ExecutionContext<'_>,
    _: BuildTarget,
    _: Instant,
) -> eyre::Result<()> {
    eyre::bail!("named NeoForm compile requires the Windows held-input adapter")
}

#[cfg(all(test, windows))]
mod named_neoform_compile_tests {
    use super::*;

    #[test]
    fn complete_supplement_sha256_is_verified_without_legacy_hash_truncation() -> eyre::Result<()> {
        const ABC_SHA256: &str = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        verify_named_supplement_bytes(b"abc", ABC_SHA256, 3)?;
        assert!(verify_named_supplement_bytes(b"abd", ABC_SHA256, 3).is_err());
        assert!(verify_named_supplement_bytes(b"abc", ABC_SHA256, 4).is_err());
        assert!(verify_named_supplement_bytes(b"abc", &ABC_SHA256[..40], 3).is_err());
        assert!(verify_named_supplement_bytes(b"abc", &ABC_SHA256.to_uppercase(), 3).is_err());
        let mut changed_tail = ABC_SHA256.to_owned();
        changed_tail.replace_range(63..64, "0");
        assert!(verify_named_supplement_bytes(b"abc", &changed_tail, 3).is_err());
        Ok(())
    }

    #[test]
    fn supplement_cache_hit_verifies_full_pin_without_network_or_repair() -> eyre::Result<()> {
        const SHA256: &str = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        let directory = tempfile::tempdir()?;
        let path = directory.path().join("supplement.jar");
        fs::write(&path, b"abc")?;
        let client = Client::builder().build()?;
        let token = CancellationToken::new();
        let acquire = |digest| {
            acquire_named_frozen_download_verified(
                &token,
                &client,
                "https://never-contact.invalid/tool.jar",
                &path,
                3,
                |bytes| verify_named_supplement_bytes(bytes, digest, 3),
                || eyre::bail!("fixture forbids missing-file acquisition effects"),
            )
        };
        assert_eq!(acquire(SHA256)?, b"abc");
        let mut changed_tail = SHA256.to_owned();
        changed_tail.replace_range(63..64, "0");
        assert!(acquire(&changed_tail).is_err());
        assert_eq!(fs::read(&path)?, b"abc");
        Ok(())
    }

    #[test]
    fn exact_original_1202_contexts_admit_compile_but_not_forge_packaging() -> eyre::Result<()> {
        for environment in ["release", "dev"] {
            let (_fixture, project) =
                super::super::nfrt_launch_contract::tests::project_fixture("1.20.2", environment)?;
            validate_first_named_compile(&project)?;
            validate_named_neoform_compile(&project)?;
            assert!(named_forge_compile_recipe_for_project(&project).is_err());
            assert!(validate_named_jar_project(&project).is_err());
            validate_named_package_project(&project)?;
        }
        Ok(())
    }

    #[test]
    fn producer_receipt_2612_admission_preserves_all_identity_guards() -> eyre::Result<()> {
        let target = "26.1.2";
        let recipe = "sfm:released-native-inputs/4.34.0/26.1.2@1";
        let lock = "sha256:2f4fbd00de200a7ba089d3dc5c19994de1ffb00b7b1a9ae56afc045a9de7f37f";
        let admitted =
            require_named_neoform_recipe_identity(target, target, recipe, lock, (25, 25))?;
        assert_eq!(admitted.loader, "net.neoforged:neoforge:26.1.2.72");
        for (selected_target, minecraft, selected_recipe, selected_lock, tools) in [
            ("unknown", target, recipe, lock, (25, 25)),
            (target, "1.21.1", recipe, lock, (25, 25)),
            (
                target,
                target,
                "sfm:released-native-inputs/4.34.0/26.1.2@2",
                lock,
                (25, 25),
            ),
            (
                target,
                target,
                recipe,
                "sha256:475d2e71d7d640d6520a62e109e647308f5fff309bc8d57b02b45ce0c1aac0de",
                (25, 25),
            ),
            (target, target, recipe, "sha256:wrong", (25, 25)),
            (target, target, recipe, lock, (21, 25)),
            (target, target, recipe, lock, (25, 21)),
        ] {
            assert!(
                require_named_neoform_recipe_identity(
                    selected_target,
                    minecraft,
                    selected_recipe,
                    selected_lock,
                    tools
                )
                .is_err()
            );
        }
        Ok(())
    }

    #[test]
    fn reviewed_neoform_recipes_admit_only_their_exact_compile_and_package_inputs()
    -> eyre::Result<()> {
        for row in &NAMED_NEOFORM_RECIPES {
            // The 26.1.2 fixture requires a separately source-built pinned
            // Mekanism artifact. Test its pure admission tuple here, without
            // replacing that missing identity or claiming project ownership.
            if row.target != "26.1.2" {
                let (_fixture, project) =
                    super::super::nfrt_launch_contract::tests::project_fixture(
                        row.target, "release",
                    )?;
                validate_first_named_compile(&project)?;
                validate_named_neoform_compile(&project)?;
                validate_named_package_project(&project)?;
                assert!(validate_named_jar_project(&project).is_err());
            }
            let recipe = format!("sfm:released-native-inputs/4.34.0/{}@1", row.target);
            require_named_package_recipe_identity(
                row.target,
                row.minecraft,
                &recipe,
                row.lock,
                row.compiler_and_tool,
            )?;
            for (minecraft, recipe, lock, tools) in [
                ("unknown", recipe.as_str(), row.lock, row.compiler_and_tool),
                (row.minecraft, "unknown", row.lock, row.compiler_and_tool),
                (
                    row.minecraft,
                    recipe.as_str(),
                    "sha256:wrong",
                    row.compiler_and_tool,
                ),
                (row.minecraft, recipe.as_str(), row.lock, (17, 17)),
            ] {
                assert!(
                    require_named_package_recipe_identity(
                        row.target, minecraft, recipe, lock, tools
                    )
                    .is_err()
                );
            }
        }
        assert!(
            require_named_package_recipe_identity(
                "unknown",
                "unknown",
                "unknown",
                "unknown",
                (17, 21)
            )
            .is_err()
        );
        Ok(())
    }

    #[test]
    fn named_neoform_package_copy_is_exact_and_refuses_existing_output() -> eyre::Result<()> {
        let directory = tempfile::tempdir()?;
        let input = directory.path().join("dev.jar");
        let output = directory.path().join("mod.jar");
        fs::write(&input, b"bounded package fixture")?;
        copy_named_neoform_package(&input, &output)?;
        assert_eq!(fs::read(&input)?, fs::read(&output)?);
        assert!(copy_named_neoform_package(&input, &output).is_err());
        assert_eq!(fs::read(&output)?, b"bounded package fixture");
        Ok(())
    }

    #[test]
    fn exact_neoform_release_package_uses_published_manifest_title() -> eyre::Result<()> {
        for (target, title) in [
            ("1.20.2", "sfm-1.20.2"),
            ("1.20.3", "sfm-1.20.3"),
            ("1.20.4", "sfm-1.20.4"),
            ("1.21.0", "sfm-1.21.0"),
            ("1.21.1", "sfm-1.21.1"),
        ] {
            let (_fixture, project) =
                super::super::nfrt_launch_contract::tests::project_fixture(target, "release")?;
            let properties = BTreeMap::from([
                (
                    "minecraft_version".to_owned(),
                    project.receipt().ownership.minecraft_version.clone(),
                ),
                ("mod_version".to_owned(), "4.34.0".to_owned()),
            ]);
            let policy =
                catalog_release_packaging_policy_for_receipt(project.receipt(), &properties)?
                    .ok_or_else(|| eyre::eyre!("release package policy missing"))?;
            assert_eq!(policy.implementation_title, title);
            assert!(!stage_synthesized_antlr_grammar_resources(Some(policy)));
        }
        // Only the published title is proven here. Preparing the 26.1.2
        // project still requires its separately authenticated byte witness.
        assert_eq!(
            catalog_release_implementation_title("26.1.2")?,
            "sfm-26.1.2"
        );
        assert!(catalog_release_implementation_title("1.21").is_err());
        assert!(catalog_release_implementation_title("unknown").is_err());
        Ok(())
    }
}
