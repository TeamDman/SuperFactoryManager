//! Strict, pure named NFRT input export. No writer or execution entry point.
//!
//! This registered module consumes the real checked project, immutable release
//! or captured-development requests and approved child supplement. Supplied bytes are checked
//! before immutable in-memory copies/serialization. It does not attest that a
//! caller read them from a safe OS handle, create invocation directories, launch
//! a JVM, alter caches or enable native compilation. The later host writer must
//! recheck source ownership and safely create fresh private snapshots.

use super::authenticated_minecraft_inputs::AuthenticatedMinecraftChild;
use super::authenticated_minecraft_inputs::MinecraftCompileDownload;
use super::nfrt_requests::NfrtArtifactRequest;
use super::nfrt_requests::NfrtRequestCatalog;
use crate::jdk::ResolvedJava;
use crate::source_projection::frozen_recipe_project::FrozenRecipeProject;
use crate::source_projection::nfrt_child_identity_supplement::NfrtChildIdentitySupplement;
use crate::source_projection::nfrt_child_identity_supplement::NfrtChildRequest;
use crate::source_projection::nfrt_project_owner::NfrtProjectOwner;
use crate::source_projection::prepared_dependency_inputs::FrozenRuntimeRoot;
use crate::source_projection::provenance::sha256;
use crate::source_projection::released_native_inputs::ReleasedPreparedDependency;
use eyre::Result;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::io::Cursor;
use std::io::Read;
use std::sync::Arc;

const INVENTORY: &str =
    include_str!("../../../../../docs/tasks/sfm-core-named-nfrt-compile-review.json");
const INVENTORY_SHA256: &str =
    "sha256:8a309479fadc1f1580a98e9269263689efb4294fb98055d64ae8751c34e33e1d";
const NFRT: &str = "net.neoforged:neoform-runtime:2.0.19:all";
const MAX_CONTRACT_BYTES: usize = 4 * 1024 * 1024;
const MAX_TOTAL_INPUT_BYTES: usize = 1024 * 1024 * 1024;
const MAX_INPUTS: usize = 8192;

#[derive(Debug, Facet)]
struct ReviewedInventory {
    targets: Vec<ReviewedTarget>,
}

#[derive(Debug, Facet)]
struct ReviewedTarget {
    target: String,
    minecraft_version: String,
    recipe_id: String,
    source_lock: ReviewedLock,
    compiler_release: u16,
    minimum_tool_jvm: u16,
    parents: Vec<ReviewedParent>,
    requested_functions_in_original_property_order: BTreeMap<String, ReviewedFunction>,
    joined_steps_in_original_order: Vec<facet_value::Value>,
    selected_source_tool_coordinates_in_inventory_order: Vec<String>,
}

#[derive(Debug, Facet)]
struct ReviewedLock {
    sha256: String,
}

#[derive(Debug, Facet)]
struct ReviewedParent {
    coordinate: String,
}

#[derive(Debug, Facet)]
struct UserdevSourceLibraries {
    libraries: Vec<String>,
}

#[derive(Debug, Facet)]
struct NeoformSourceLibraries {
    libraries: BTreeMap<String, Vec<String>>,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
struct ReviewedFunction {
    #[facet(default)]
    version: Option<String>,
    #[facet(default)]
    classpath: Option<Vec<String>>,
    args: Vec<String>,
    jvmargs: Vec<String>,
    #[facet(default)]
    main_class: Option<String>,
    #[facet(default)]
    repo: Option<String>,
    #[facet(default)]
    java_version: Option<u16>,
}

/// Exact selected JVM transfer metadata, explicitly not a runtime attestation.
#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub struct NfrtToolSdkTransfer {
    pub major: u32,
    pub selection: String,
    pub original_archive_url: Option<String>,
    pub original_archive_sha512: Option<String>,
    pub supplied_executable_bytes: usize,
    pub supplied_executable_sha256: String,
    pub actual_sdk_rechecked: bool,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub struct NfrtFunctionExport {
    pub node: String,
    pub classpath: Vec<String>,
    pub args: Vec<String>,
    pub jvm_args: Vec<String>,
    pub main_class: Option<String>,
    pub original_repository: Option<String>,
    pub original_java_version: Option<u16>,
}

/// A bounded invocation-relative snapshot, not a machine cache path.
#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub struct NfrtInputSnapshotExport {
    pub request_key: String,
    pub coordinate: Option<String>,
    pub exact_url: Option<String>,
    pub origin: String,
    pub original_identity: String,
    pub snapshot_relative_path: String,
    pub bytes: usize,
    pub full_sha256: String,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[expect(
    clippy::struct_excessive_bools,
    reason = "Serialized independent authority evidence, not interchangeable lifecycle states."
)]
pub struct NamedNfrtInvocationInput {
    pub schema: String,
    pub invocation_id: String,
    pub projection_key: String,
    pub environment: String,
    pub target_id: String,
    pub minecraft_version: String,
    pub recipe_id: String,
    pub preparation_identity: String,
    pub source_lock_sha256: String,
    pub original_request_catalog_identity: String,
    pub supplement_identity: String,
    pub supplement_sha256: String,
    pub compiler_release: u16,
    pub minimum_tool_jvm: u16,
    pub tool_sdk: NfrtToolSdkTransfer,
    pub functions: Vec<NfrtFunctionExport>,
    pub joined_steps_json: Vec<String>,
    pub ordered_dependency_rows: Vec<ReleasedPreparedDependency>,
    pub ordered_runtime_roots: Vec<FrozenRuntimeRoot>,
    pub ordered_transitive_runtime_rows: Vec<ReleasedPreparedDependency>,
    pub inputs: Vec<NfrtInputSnapshotExport>,
    pub required_clean_environment_names: Vec<String>,
    pub fresh_work_root_required: bool,
    pub intermediate_cache_restore: bool,
    pub intermediate_cache_persistence: bool,
    pub launcher_probing: bool,
    pub os_filesystem_attested: bool,
    pub native_execution_enabled: bool,
    pub unresolved_execution_gates: Vec<String>,
}

/// Private prepared authority; the portable DTO alone cannot create this.
#[derive(Clone, Debug)]
pub struct NfrtExportPlan {
    input: NamedNfrtInvocationInput,
    original: NfrtRequestCatalog,
    supplement: NfrtChildIdentitySupplement,
    originals: Vec<(String, NfrtArtifactRequest)>,
    version_json_url: String,
    children: Vec<NfrtChildRequest>,
    parent_coordinates: Vec<String>,
}

/// Immutable in-memory snapshots; a future writer still needs OS ownership.
#[derive(Clone, Debug)]
pub struct PreparedNfrtInvocationInput {
    receipt: NamedNfrtInvocationInput,
    snapshots: Vec<Arc<[u8]>>,
    serialized: String,
    source_pin_origin: &'static str,
}

impl PreparedNfrtInvocationInput {
    /// Derived from the retained request catalog, never from a portable DTO.
    pub(crate) fn source_pin_origin(&self) -> &'static str {
        self.source_pin_origin
    }

    #[must_use]
    pub fn receipt(&self) -> &NamedNfrtInvocationInput {
        &self.receipt
    }
    #[must_use]
    pub fn serialized(&self) -> &str {
        &self.serialized
    }
    #[must_use]
    pub fn snapshots(&self) -> &[Arc<[u8]>] {
        &self.snapshots
    }
    #[must_use]
    pub fn contract_identity(&self) -> String {
        sha256(self.serialized.as_bytes())
    }

    /// # Errors
    /// Always refuses: input export is not an OS/JVM launch capability.
    #[cfg_attr(
        not(test),
        expect(
            dead_code,
            reason = "Explicit refusal surface retained for input-transfer contract audits."
        )
    )]
    pub fn require_native_execution(&self) -> Result<()> {
        eyre::bail!(
            "named NFRT input export cannot enable native execution: {}",
            self.receipt.unresolved_execution_gates.join(", ")
        )
    }
}

impl NfrtExportPlan {
    /// Prepare a genuine development transfer, not a relabelled release recipe.
    #[expect(
        clippy::too_many_lines,
        reason = "Bind every development source, profile, tool and dependency role before publishing a transfer."
    )]
    pub(crate) fn prepare_development(
        project: &crate::source_projection::nfrt_project_owner::DevelopmentNfrtSourceOwner<'_>,
        dependencies: &Arc<
            crate::source_projection::development_nfrt_dependencies::DevelopmentNfrtDependencies,
        >,
        supplement: &NfrtChildIdentitySupplement,
        selected_java: &ResolvedJava,
        selected_executable_bytes: &[u8],
        invocation_id: &str,
        refresh: bool,
    ) -> Result<Self> {
        ensure!(
            !refresh,
            "development NFRT exporter refuses refresh before effects"
        );
        validate_invocation_id(invocation_id)?;
        dependencies.recheck_source(project)?;
        ensure!(
            sha256(INVENTORY.as_bytes()) == INVENTORY_SHA256,
            "frozen NFRT function inventory changed"
        );
        let source = dependencies.receipt();
        let child = supplement.receipt();
        let target = reviewed_target(&source.target_id)?;
        ensure!(
            child.source_lock_sha256 == source.source_lock_sha256
                && child.original_request_catalog_identity == source.request_catalog_identity
                && child.target_id == source.target_id
                && child.minecraft_version == source.minecraft_version
                && child.recipe_id
                    == format!("sfm:development-native-inputs/{}@1", source.target_id)
                && child.compiler_release == project.target().receipt.java_major
                && child.compiler_release == target.compiler_release
                && child.minimum_tool_jvm == target.minimum_tool_jvm
                && child.original_lock_immutable,
            "development NFRT exporter lost exact source/profile/supplement binding"
        );
        let functions = function_exports(&target, supplement)?;
        let tool_sdk = sdk_transfer(
            selected_java,
            selected_executable_bytes,
            child.minimum_tool_jvm,
        )?;
        let mut children = supplement.source_requests(false)?;
        ensure!(
            children
                .iter()
                .map(|request| request.coordinate().to_owned())
                .collect::<Vec<_>>()
                == target.selected_source_tool_coordinates_in_inventory_order,
            "development source tools changed order"
        );
        children.extend(supplement.source_library_requests(false)?);
        let roles =
            super::engine::development_nfrt_dependency_rows(dependencies.effective_profile())?;
        let roots = roles
            .iter()
            .enumerate()
            .filter(|(_, row)| {
                matches!(
                    row.configuration.as_str(),
                    "implementation"
                        | "runtimeOnly"
                        | "gametestImplementation"
                        | "gametestRuntimeOnly"
                )
            })
            .map(|(index, row)| FrozenRuntimeRoot {
                prepared_dependency_index: index,
                original_dependency_index: None,
                configuration: row.configuration.clone(),
                resolved_coordinate: row.resolved_coordinate.clone(),
            })
            .collect();
        let runtime = roles
            .iter()
            .filter(|row| row.configuration == "transitiveRuntime")
            .cloned()
            .collect();
        let parent_coordinates = std::iter::once(NFRT.to_owned())
            .chain(
                target
                    .parents
                    .iter()
                    .map(|parent| parent.coordinate.clone()),
            )
            .collect::<Vec<_>>();
        let userdev = target
            .parents
            .iter()
            .find(|parent| parent.coordinate.ends_with(":userdev"))
            .ok_or_else(|| eyre::eyre!("development target has no exact userdev parent"))?;
        let loader = userdev
            .coordinate
            .strip_suffix(":userdev")
            .expect("selected userdev suffix");
        let archives = [format!("{loader}:sources"), format!("{loader}:universal")];
        let version_request = dependencies.version_json_request()?;
        let version_json_url = dependencies
            .artifact_pin(&version_request)?
            .url
            .clone()
            .expect("selected version URL");
        let original = NfrtRequestCatalog::Development(Arc::clone(dependencies));
        let mut originals = Vec::new();
        let mut seen = BTreeSet::new();
        for coordinate in parent_coordinates
            .iter()
            .chain(archives.iter())
            .chain(roles.iter().map(|row| &row.resolved_coordinate))
        {
            if seen.insert(coordinate.clone()) {
                originals.push((
                    format!("original/{coordinate}"),
                    original.coordinate(coordinate, false)?,
                ));
            }
        }
        originals.push((
            "version_json".to_owned(),
            original.exact_url(&version_json_url, false)?,
        ));
        let input = NamedNfrtInvocationInput {
            schema: "sfm:named_nfrt_invocation_input@1".to_owned(),
            invocation_id: invocation_id.to_owned(),
            projection_key: project.project().receipt().projection_key.clone(),
            environment: "dev".to_owned(),
            target_id: source.target_id.clone(),
            minecraft_version: source.minecraft_version.clone(),
            recipe_id: child.recipe_id.clone(),
            preparation_identity: project.preparation_identity()?,
            source_lock_sha256: source.source_lock_sha256.clone(),
            original_request_catalog_identity: source.request_catalog_identity.clone(),
            supplement_identity: child.supplement_identity.clone(),
            supplement_sha256: child.supplement_sha256.clone(),
            compiler_release: child.compiler_release,
            minimum_tool_jvm: child.minimum_tool_jvm,
            tool_sdk,
            functions,
            joined_steps_json: target
                .joined_steps_in_original_order
                .iter()
                .map(facet_json::to_string)
                .collect::<std::result::Result<Vec<_>, _>>()?,
            ordered_dependency_rows: roles,
            ordered_runtime_roots: roots,
            ordered_transitive_runtime_rows: runtime,
            inputs: Vec::new(),
            required_clean_environment_names: [
                "JAVA_TOOL_OPTIONS",
                "JDK_JAVA_OPTIONS",
                "JDK_JAVAC_OPTIONS",
                "CLASSPATH",
            ]
            .map(str::to_owned)
            .to_vec(),
            fresh_work_root_required: true,
            intermediate_cache_restore: false,
            intermediate_cache_persistence: false,
            launcher_probing: false,
            os_filesystem_attested: false,
            native_execution_enabled: false,
            unresolved_execution_gates: [
                "checked_development_source_and_sdk",
                "owned_immutable_input_snapshots",
                "fresh_retained_host_and_reviewed_child_network",
            ]
            .map(str::to_owned)
            .to_vec(),
        };
        dependencies.recheck_source(project)?;
        Ok(Self {
            input,
            original,
            supplement: supplement.clone(),
            originals,
            children,
            parent_coordinates,
            version_json_url,
        })
    }

    /// Prepare only exact catalog/recipe/supplement inputs. No filesystem work.
    ///
    /// # Errors
    /// Refuses refresh, cross-recipe input, changed frozen function classpaths,
    /// unsupported compiler/tool combinations or unbounded JVM declarations.
    #[expect(
        clippy::too_many_lines,
        reason = "Keep frozen recipe, SDK and invocation preparation checks in one admission boundary."
    )]
    pub(crate) fn prepare(
        project: &FrozenRecipeProject,
        supplement: &NfrtChildIdentitySupplement,
        selected_java: &ResolvedJava,
        selected_executable_bytes: &[u8],
        invocation_id: &str,
        refresh: bool,
    ) -> Result<Self> {
        ensure!(
            !refresh,
            "NFRT exporter refuses refresh before any caller effects"
        );
        validate_invocation_id(invocation_id)?;
        ensure!(
            sha256(INVENTORY.as_bytes()) == INVENTORY_SHA256,
            "frozen NFRT function inventory changed"
        );
        let owner = project.receipt();
        let original = project.dependencies();
        let child = supplement.receipt();
        ensure!(
            owner.prepared_inputs.request_catalog_identity
                == child.original_request_catalog_identity
                && owner.prepared_inputs.target_id == child.target_id
                && owner.prepared_inputs.minecraft_version == child.minecraft_version
                && owner.prepared_inputs.recipe_id == child.recipe_id
                && owner.prepared_inputs.source_lock_sha256 == child.source_lock_sha256
                && owner.compiler_release == child.compiler_release
                && owner.tool_jvm_minimum == child.minimum_tool_jvm
                && owner.immutable_source_lock
                && child.original_lock_immutable,
            "NFRT exporter lost the exact project/original/supplement binding"
        );
        let target = reviewed_target(&child.target_id)?;
        ensure!(
            target.minecraft_version == child.minecraft_version
                && target.recipe_id == child.recipe_id
                && format!("sha256:{}", target.source_lock.sha256) == child.source_lock_sha256
                && target.compiler_release == child.compiler_release
                && target.minimum_tool_jvm == child.minimum_tool_jvm,
            "NFRT exporter target differs from frozen reviewed source recipe"
        );
        let functions = function_exports(&target, supplement)?;
        let tool_sdk = sdk_transfer(
            selected_java,
            selected_executable_bytes,
            child.minimum_tool_jvm,
        )?;
        let mut children = supplement.source_requests(false)?;
        ensure!(
            children
                .iter()
                .map(|value| value.coordinate().to_owned())
                .collect::<Vec<_>>()
                == target.selected_source_tool_coordinates_in_inventory_order,
            "NFRT exporter reordered source consumer tools"
        );
        children.extend(supplement.source_library_requests(false)?);
        let parent_coordinates = std::iter::once(NFRT.to_owned())
            .chain(target.parents.iter().map(|value| value.coordinate.clone()))
            .collect::<Vec<_>>();
        let mut originals = Vec::new();
        let mut seen = BTreeSet::new();
        // Userdev transforms consume these original pinned archives even when
        // they are neither dependency rows nor recipe-parent proof inputs.
        let userdev_archives = ["sources", "universal"]
            .map(|classifier| format!("{}:{classifier}", owner.released_inputs.loader_coordinate));
        for coordinate in parent_coordinates
            .iter()
            .chain(userdev_archives.iter())
            .map(String::as_str)
            .chain(
                original
                    .dependencies()
                    .iter()
                    .map(|row| row.resolved_coordinate.as_str()),
            )
        {
            if seen.insert(coordinate.to_owned()) {
                originals.push((
                    format!("original/{coordinate}"),
                    NfrtArtifactRequest::Released(original.coordinate(coordinate, false)?),
                ));
            }
        }
        let version_request =
            original.exact_url(&owner.released_inputs.direct_version_json_url, false)?;
        originals.push((
            "version_json".to_owned(),
            NfrtArtifactRequest::Released(version_request),
        ));
        let roots = original.runtime_roots().to_vec();
        let runtime = original
            .frozen_runtime_rows_for(&roots, false)?
            .into_iter()
            .cloned()
            .collect();
        let joined_steps_json = target
            .joined_steps_in_original_order
            .iter()
            .map(facet_json::to_string)
            .collect::<std::result::Result<Vec<_>, _>>()?;
        let input = NamedNfrtInvocationInput {
            schema: "sfm:named_nfrt_invocation_input@1".to_owned(),
            invocation_id: invocation_id.to_owned(),
            projection_key: owner.ownership.projection_key.clone(),
            environment: owner.ownership.environment.as_str().to_owned(),
            target_id: child.target_id.clone(),
            minecraft_version: child.minecraft_version.clone(),
            recipe_id: child.recipe_id.clone(),
            preparation_identity: owner.preparation_identity()?,
            source_lock_sha256: child.source_lock_sha256.clone(),
            original_request_catalog_identity: child.original_request_catalog_identity.clone(),
            supplement_identity: child.supplement_identity.clone(),
            supplement_sha256: child.supplement_sha256.clone(),
            compiler_release: child.compiler_release,
            minimum_tool_jvm: child.minimum_tool_jvm,
            tool_sdk,
            functions,
            joined_steps_json,
            ordered_dependency_rows: original.dependencies().to_vec(),
            ordered_runtime_roots: roots,
            ordered_transitive_runtime_rows: runtime,
            inputs: Vec::new(),
            required_clean_environment_names: [
                "JAVA_TOOL_OPTIONS",
                "JDK_JAVA_OPTIONS",
                "JDK_JAVAC_OPTIONS",
                "CLASSPATH",
            ]
            .map(str::to_owned)
            .to_vec(),
            fresh_work_root_required: true,
            intermediate_cache_restore: false,
            intermediate_cache_persistence: false,
            launcher_probing: false,
            os_filesystem_attested: false,
            native_execution_enabled: false,
            unresolved_execution_gates: [
                "source_ownership_and_raw_13_role_recheck_before_effects",
                "fresh_create_new_invocation_and_reparse_safe_immutable_file_snapshots",
                "selected_sdk_actual_path_archive_and_executable_recheck",
                "authenticated_library_name_to_java_contract_binding",
                "fresh_producer_graph_object_and_completed_output_receipts",
                "reviewed_child_network_boundary",
                "named_java_contract_parser_and_engine_wiring",
            ]
            .map(str::to_owned)
            .to_vec(),
        };
        Ok(Self {
            input,
            original: NfrtRequestCatalog::Released(Arc::clone(original)),
            supplement: supplement.clone(),
            originals,
            children,
            parent_coordinates,
            version_json_url: owner.released_inputs.direct_version_json_url.clone(),
        })
    }

    #[must_use]
    pub fn declaration(&self) -> &NamedNfrtInvocationInput {
        &self.input
    }

    pub(crate) fn version_json_url(&self) -> &str {
        &self.version_json_url
    }

    fn check_project_identity(&self, project: &dyn NfrtProjectOwner) -> Result<()> {
        ensure!(
            project.preparation_identity()? == self.input.preparation_identity,
            "NeoForm request plan belongs to another project"
        );
        Ok(())
    }

    /// Acquire only requests already selected by the frozen original recipe,
    /// approved supplement and authenticated Minecraft parent. The callbacks
    /// own bounded transport/cache I/O and must recheck the project immediately
    /// before a cache-miss write/download. This method checks the complete
    /// project at batch boundaries rather than rescanning it per cache read,
    /// and authenticates each result
    /// before using it to select any further request. No host is launched here.
    pub(in crate::jar_build) fn acquire_inputs(
        &self,
        project: &dyn NfrtProjectOwner,
        mut original: impl FnMut(&NfrtArtifactRequest) -> Result<Vec<u8>>,
        mut child: impl FnMut(&NfrtChildRequest) -> Result<Vec<u8>>,
        mut minecraft: impl FnMut(&AuthenticatedMinecraftChild) -> Result<Vec<u8>>,
    ) -> Result<PreparedNfrtInvocationInput> {
        self.original.recheck_source(project)?;
        ensure!(
            project.preparation_identity()? == self.input.preparation_identity,
            "NFRT acquisition plan belongs to another named project context"
        );
        let mut supplied = BTreeMap::new();
        let mut minecraft_children: BTreeMap<String, Vec<u8>> = BTreeMap::new();
        let mut total = 0usize;
        for (key, request) in &self.originals {
            let bytes = original(request)?;
            self.original
                .verify_artifact_bytes(request, &bytes, false)?;
            admit_acquired_input(&mut supplied, key.clone(), bytes, &mut total)?;
        }
        // These coordinates come from verified parent bytes and remain exact
        // original lock requests, never permissive POM/library discovery.
        for (key, request) in self.source_library_requests(project, &supplied)? {
            if supplied.contains_key(&key) {
                continue;
            }
            let bytes = original(&request)?;
            self.original
                .verify_artifact_bytes(&request, &bytes, false)?;
            admit_acquired_input(&mut supplied, key, bytes, &mut total)?;
        }
        for request in &self.children {
            let bytes = child(request)?;
            self.supplement.verify_child_bytes(request, &bytes, false)?;
            admit_acquired_input(
                &mut supplied,
                format!("child/{}", request.coordinate()),
                bytes,
                &mut total,
            )?;
        }
        let authenticated = self.original.authenticate_version_json(
            &self.input.minecraft_version,
            &self.version_json_url,
            &supplied["version_json"],
        )?;
        let mut requests = Vec::new();
        for role in [
            MinecraftCompileDownload::ClientJar,
            MinecraftCompileDownload::ServerJar,
            MinecraftCompileDownload::ClientMappings,
            MinecraftCompileDownload::ServerMappings,
        ] {
            if let Ok(request) = authenticated.download(role) {
                requests.push(request);
            }
        }
        requests.extend(authenticated.libraries());
        for request in requests {
            if let Some(bytes) = minecraft_children.get(request.url()) {
                // Shared URL does not permit differing metadata or identity.
                authenticated.verify_child_bytes(request, bytes)?;
                continue;
            }
            let bytes = minecraft(request)?;
            authenticated.verify_child_bytes(request, &bytes)?;
            admit_acquired_input(
                &mut minecraft_children,
                request.url().to_owned(),
                bytes,
                &mut total,
            )?;
        }
        self.original.recheck_source(project)?;
        self.export_supplied_bytes(project, &supplied, &minecraft_children, false)
    }

    /// Select source-tool libraries from the authenticated original userdev
    /// parent. This does not discover dependencies or authorize acquisition.
    pub(crate) fn source_library_requests(
        &self,
        project: &dyn NfrtProjectOwner,
        supplied: &BTreeMap<String, Vec<u8>>,
    ) -> Result<Vec<(String, NfrtArtifactRequest)>> {
        self.check_project_identity(project)?;
        let parents = self
            .originals
            .iter()
            .filter(|(_, request)| {
                request
                    .resolved_coordinate()
                    .is_some_and(|value| value.ends_with(":userdev"))
            })
            .collect::<Vec<_>>();
        ensure!(
            parents.len() == 1,
            "source library selection requires one exact userdev parent"
        );
        let (key, request) = parents[0];
        let raw = supplied
            .get(key)
            .ok_or_else(|| eyre::eyre!("missing original userdev parent"))?;
        ensure!(
            raw.len() <= 512 * 1024 * 1024,
            "oversized original userdev parent"
        );
        self.original.verify_artifact_bytes(request, raw, false)?;
        let coordinate = request.resolved_coordinate().expect("selected coordinate");
        self.supplement
            .verify_parent_bytes(coordinate, raw, false)?;
        let bytes = bounded_source_config(raw)?;
        let config: UserdevSourceLibraries = facet_json::from_str(std::str::from_utf8(&bytes)?)?;
        let mut library_coordinates = config.libraries;
        let recipe_parents = self
            .originals
            .iter()
            .filter(|(_, request)| {
                request.resolved_coordinate().is_some_and(|value| {
                    value.starts_with("net.neoforged:neoform:") && value.ends_with("@zip")
                })
            })
            .collect::<Vec<_>>();
        ensure!(
            recipe_parents.len() == 1,
            "expected one original NeoForm source parent"
        );
        let (recipe_key, recipe_request) = recipe_parents[0];
        let recipe_raw = supplied
            .get(recipe_key)
            .ok_or_else(|| eyre::eyre!("missing original NeoForm parent"))?;
        self.original
            .verify_artifact_bytes(recipe_request, recipe_raw, false)?;
        self.supplement.verify_parent_bytes(
            recipe_request
                .resolved_coordinate()
                .expect("selected recipe coordinate"),
            recipe_raw,
            false,
        )?;
        let recipe_bytes = bounded_source_config(recipe_raw)?;
        let recipe: NeoformSourceLibraries =
            facet_json::from_str(std::str::from_utf8(&recipe_bytes)?)?;
        let joined = recipe
            .libraries
            .get("joined")
            .ok_or_else(|| eyre::eyre!("original NeoForm recipe has no joined library list"))?;
        library_coordinates.extend(joined.iter().cloned());
        let minecraft = self.original.authenticate_version_json(
            &self.input.minecraft_version,
            &self.version_json_url,
            supplied
                .get("version_json")
                .ok_or_else(|| eyre::eyre!("missing original version JSON"))?,
        )?;
        let minecraft_library_paths = minecraft
            .libraries()
            .filter_map(|library| library.library_relative_path().map(str::to_owned))
            .collect();
        let approved_libraries =
            approved_recipe_library_coordinates(&self.supplement, &library_coordinates)?;
        select_source_library_requests(
            &self.original,
            library_coordinates,
            &minecraft_library_paths,
            &approved_libraries,
        )
    }

    /// Authenticate a complete exact byte batch and return bounded input JSON.
    ///
    /// All keys/counts are preflighted before copying or parsing payloads.
    /// Original `ContentHash`, approved child full SHA and authenticated parent
    /// SHA1 descendants remain separate authority classes. No acquisition or
    /// machine path can be inferred from this API.
    ///
    /// # Errors
    /// Refuses missing/extra/changed artifacts or parent/child metadata. Source
    /// locks are never rewritten; serialized success is not launch acceptance.
    #[expect(
        clippy::too_many_lines,
        reason = "Preflight every supplied input before publishing the invocation contract."
    )]
    pub fn export_supplied_bytes(
        &self,
        project: &dyn NfrtProjectOwner,
        supplied: &BTreeMap<String, Vec<u8>>,
        minecraft_children: &BTreeMap<String, Vec<u8>>,
        refresh: bool,
    ) -> Result<PreparedNfrtInvocationInput> {
        ensure!(
            !refresh,
            "NFRT exporter refuses refresh before any caller effects"
        );
        ensure!(
            project.preparation_identity()? == self.input.preparation_identity,
            "NFRT export plan belongs to another named project context"
        );
        let mut originals = self.originals.clone();
        for (key, request) in self.source_library_requests(project, supplied)? {
            if !originals.iter().any(|(existing, _)| existing == &key) {
                originals.push((key, request));
            }
        }
        let expected = originals
            .iter()
            .map(|(key, _)| key.clone())
            .chain(
                self.children
                    .iter()
                    .map(|value| format!("child/{}", value.coordinate())),
            )
            .collect::<BTreeSet<_>>();
        ensure!(
            supplied.len() == expected.len()
                && supplied.keys().all(|key| expected.contains(key))
                && supplied.len() + minecraft_children.len() <= MAX_INPUTS,
            "NFRT input batch has missing, extra or oversized request set"
        );
        let total = supplied
            .values()
            .chain(minecraft_children.values())
            .try_fold(0usize, |sum, raw| {
                sum.checked_add(raw.len())
                    .ok_or_else(|| eyre::eyre!("NFRT input batch overflow"))
            })?;
        ensure!(
            total <= MAX_TOTAL_INPUT_BYTES,
            "NFRT input byte batch exceeds bounded total"
        );
        self.original.recheck_source(project)?;
        for (key, request) in &originals {
            self.original
                .verify_artifact_bytes(request, &supplied[key], false)?;
        }
        for coordinate in &self.parent_coordinates {
            self.supplement.verify_parent_bytes(
                coordinate,
                &supplied[&format!("original/{coordinate}")],
                false,
            )?;
        }
        for request in &self.children {
            self.supplement.verify_child_bytes(
                request,
                &supplied[&format!("child/{}", request.coordinate())],
                false,
            )?;
        }
        let authenticated = self.original.authenticate_version_json(
            &self.input.minecraft_version,
            &self.version_json_url,
            &supplied["version_json"],
        )?;
        let mut selected_minecraft = Vec::new();
        for role in [
            MinecraftCompileDownload::ClientJar,
            MinecraftCompileDownload::ServerJar,
            MinecraftCompileDownload::ClientMappings,
            MinecraftCompileDownload::ServerMappings,
        ] {
            if let Ok(request) = authenticated.download(role) {
                selected_minecraft.push(request);
            }
        }
        selected_minecraft.extend(authenticated.libraries());
        let expected_children = selected_minecraft
            .iter()
            .map(|value| value.url())
            .collect::<BTreeSet<_>>();
        ensure!(
            minecraft_children.len() == expected_children.len()
                && minecraft_children
                    .keys()
                    .all(|key| expected_children.contains(key.as_str())),
            "Minecraft child batch differs from the exact authenticated version JSON"
        );
        for request in &selected_minecraft {
            authenticated.verify_child_bytes(request, &minecraft_children[request.url()])?;
        }
        let mut receipt = self.input.clone();
        let mut snapshots = Vec::new();
        for (key, request) in &originals {
            add_snapshot(
                &mut receipt,
                &mut snapshots,
                key,
                request.resolved_coordinate().map(str::to_owned),
                request.exact_transport_url().map(str::to_owned),
                request.origin(),
                request.original_content_hash(),
                &supplied[key],
            )?;
        }
        for request in &self.children {
            let key = format!("child/{}", request.coordinate());
            add_snapshot(
                &mut receipt,
                &mut snapshots,
                &key,
                Some(request.coordinate().to_owned()),
                Some(request.exact_url().to_owned()),
                "approved_nfrt_child",
                format!("sha256:{}", request.sha256()),
                &supplied[&key],
            )?;
        }
        for request in selected_minecraft {
            let key = format!(
                "minecraft/{}",
                request.library_relative_path().unwrap_or(request.url())
            );
            add_snapshot(
                &mut receipt,
                &mut snapshots,
                &key,
                None,
                Some(request.url().to_owned()),
                "authenticated_minecraft_child",
                request.expected_hash().to_string(),
                &minecraft_children[request.url()],
            )?;
        }
        let serialized = facet_json::to_string(&receipt)?;
        ensure!(
            serialized.len() <= MAX_CONTRACT_BYTES,
            "NFRT serialized input contract exceeds bounded bytes"
        );
        Ok(PreparedNfrtInvocationInput {
            receipt,
            snapshots,
            serialized,
            source_pin_origin: self.original.source_pin_origin(),
        })
    }
}

/// Resolve the complete bounded closure before acquisition. Report all missing
/// identities together, without substituting another version or classifier.
fn approved_recipe_library_coordinates(
    supplement: &NfrtChildIdentitySupplement,
    library_coordinates: &[String],
) -> Result<BTreeSet<String>> {
    let approved = supplement
        .source_library_requests(false)?
        .iter()
        .map(|request| request.coordinate().to_owned())
        .collect::<BTreeSet<_>>();
    let declared = library_coordinates
        .iter()
        .map(|actual| actual.strip_suffix("@jar").unwrap_or(actual))
        .collect::<BTreeSet<_>>();
    ensure!(
        approved
            .iter()
            .all(|coordinate| declared.contains(coordinate.as_str())),
        "approved source library is absent from authenticated original recipe"
    );
    Ok(approved)
}

fn select_source_library_requests(
    original: &NfrtRequestCatalog,
    library_coordinates: Vec<String>,
    minecraft_library_paths: &BTreeSet<String>,
    approved_libraries: &BTreeSet<String>,
) -> Result<Vec<(String, NfrtArtifactRequest)>> {
    ensure!(
        library_coordinates.len() <= 4096,
        "oversized userdev source library list"
    );
    let mut seen = BTreeSet::new();
    let mut resolved_keys = BTreeSet::new();
    let mut requests = Vec::new();
    let mut missing = Vec::new();
    for coordinate in library_coordinates {
        ensure!(
            !coordinate.is_empty() && coordinate.len() <= 4096,
            "invalid source library"
        );
        // Only the explicitly equivalent default JAR suffix is normalized.
        let coordinate = coordinate.strip_suffix("@jar").unwrap_or(&coordinate);
        if !seen.insert(coordinate.to_owned()) {
            continue;
        }
        if approved_libraries.contains(coordinate) {
            continue;
        }
        let Ok(request) = original.coordinate(coordinate, false) else {
            let parts = coordinate.split(':').collect::<Vec<_>>();
            if parts.len() == 3 {
                let relative = format!(
                    "{}/{}/{}/{}-{}.jar",
                    parts[0].replace('.', "/"),
                    parts[1],
                    parts[2],
                    parts[1],
                    parts[2]
                );
                if minecraft_library_paths.contains(&relative) {
                    continue;
                }
            }
            missing.push(coordinate.to_owned());
            continue;
        };
        let resolved = request
            .resolved_coordinate()
            .ok_or_else(|| eyre::eyre!("source library has no resolved coordinate"))?;
        let key = format!("original/{resolved}");
        ensure!(
            resolved_keys.insert(key.clone()),
            "duplicate resolved userdev source library"
        );
        requests.push((key, request));
    }
    ensure!(
        missing.is_empty(),
        "{} source libraries are outside the frozen eager request catalog (first 16: {}); no dynamic discovery or unpinned fallback is permitted",
        missing.len(),
        missing
            .iter()
            .take(16)
            .map(String::as_str)
            .collect::<Vec<_>>()
            .join(", ")
    );
    Ok(requests)
}

fn admit_acquired_input(
    inputs: &mut BTreeMap<String, Vec<u8>>,
    key: String,
    bytes: Vec<u8>,
    total: &mut usize,
) -> Result<()> {
    ensure!(
        inputs.len() < MAX_INPUTS,
        "NFRT acquired request set exceeds bound"
    );
    let next = total
        .checked_add(bytes.len())
        .ok_or_else(|| eyre::eyre!("NFRT acquired byte total overflow"))?;
    ensure!(
        next <= MAX_TOTAL_INPUT_BYTES,
        "NFRT acquired input bytes exceed bound"
    );
    ensure!(
        !inputs.contains_key(&key),
        "NFRT acquisition repeated a request key"
    );
    inputs.insert(key, bytes);
    *total = next;
    Ok(())
}

pub(crate) fn bounded_source_config(raw: &[u8]) -> Result<Vec<u8>> {
    ensure!(
        raw.len() <= 512 * 1024 * 1024,
        "unbounded source parent ZIP"
    );
    let mut archive = zip::ZipArchive::new(Cursor::new(raw))?;
    ensure!(archive.len() <= 65536, "unbounded source parent directory");
    let mut matches = 0;
    for index in 0..archive.len() {
        if archive.by_index(index)?.name() == "config.json" {
            matches += 1;
        }
    }
    ensure!(matches == 1, "source config missing or duplicated");
    let entry = archive.by_name("config.json")?;
    ensure!(
        !entry.is_dir() && entry.size() <= 4 * 1024 * 1024,
        "unbounded source config"
    );
    let mut bytes = Vec::new();
    entry.take(4 * 1024 * 1024 + 1).read_to_end(&mut bytes)?;
    ensure!(
        bytes.len() <= 4 * 1024 * 1024,
        "source config exceeds bound"
    );
    Ok(bytes)
}

#[expect(
    clippy::too_many_arguments,
    reason = "Explicit snapshot identity fields stay separate from the mutable receipt and byte store."
)]
fn add_snapshot(
    receipt: &mut NamedNfrtInvocationInput,
    snapshots: &mut Vec<Arc<[u8]>>,
    key: &str,
    coordinate: Option<String>,
    exact_url: Option<String>,
    origin: &str,
    original_identity: String,
    raw: &[u8],
) -> Result<()> {
    ensure!(!raw.is_empty(), "NFRT transfer snapshot is empty");
    let digest = sha256(raw);
    let copied: Arc<[u8]> = Arc::from(raw);
    ensure!(sha256(&copied) == digest, "immutable input copy changed");
    let snapshot_relative_path = original_snapshot_path(snapshots.len(), coordinate.as_deref());
    receipt.inputs.push(NfrtInputSnapshotExport {
        request_key: key.to_owned(),
        coordinate,
        exact_url,
        origin: origin.to_owned(),
        original_identity,
        snapshot_relative_path,
        bytes: copied.len(),
        full_sha256: digest,
    });
    snapshots.push(copied);
    Ok(())
}

/// `PatchActionFactory` passes the parent filename to `DiffPatch`, which infers
/// archive format from its suffix. Vineflower also loads its embedded plugins
/// only when its own code-source filename ends in `.jar`.
pub(crate) fn original_snapshot_path(index: usize, coordinate: Option<&str>) -> String {
    let extension = match coordinate {
        Some(value) if value.starts_with("net.neoforged:neoform:") && value.ends_with("@zip") => {
            "zip"
        }
        Some(value) if value.ends_with(":userdev") => "jar",
        Some(value)
            if value.starts_with("org.vineflower:vineflower:")
                && (!value.contains('@') || value.ends_with("@jar")) =>
        {
            "jar"
        }
        _ => "bin",
    };
    format!("inputs/artifacts/{index:04}.{extension}")
}

#[cfg(test)]
mod original_snapshot_path_tests {
    use super::original_snapshot_path;
    #[test]
    fn preserves_original_patch_parent_and_vineflower_plugin_archive_suffixes() {
        for (coordinate, extension) in [
            (
                Some("net.neoforged:neoform:1.20.2-20230921.100330@zip"),
                "zip",
            ),
            (Some("net.neoforged:neoforge:20.2.93:userdev"), "jar"),
            (Some("net.neoforged:neoforge:20.2.93:sources"), "bin"),
            (Some("org.vineflower:vineflower:1.10.1"), "jar"),
            (Some("org.vineflower:vineflower:1.10.1@jar"), "jar"),
            (Some("org.vineflower:vineflower:1.10.1@zip"), "bin"),
            (Some("org.vineflower:other:1.10.1"), "bin"),
            (Some("other:archive:1@zip"), "bin"),
            (None, "bin"),
        ] {
            assert_eq!(
                original_snapshot_path(7, coordinate),
                format!("inputs/artifacts/0007.{extension}")
            );
        }
    }
}

fn sdk_transfer(java: &ResolvedJava, raw: &[u8], minimum: u16) -> Result<NfrtToolSdkTransfer> {
    ensure!(
        java.major_version >= u32::from(minimum)
            && !raw.is_empty()
            && raw.len() <= 64 * 1024 * 1024,
        "selected tool JVM is too old or executable bytes are unavailable/oversized"
    );
    ensure!(
        !java.selection.is_empty()
            && java.selection.len() <= 512
            && java.pin_url.is_some() == java.pin_sha512.is_some(),
        "selected tool SDK identity is incomplete"
    );
    if let Some(pin) = &java.pin_sha512 {
        ensure!(
            pin.len() == 128
                && pin
                    .bytes()
                    .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte)),
            "selected SDK archive identity is not full lowercase SHA512"
        );
    }
    Ok(NfrtToolSdkTransfer {
        major: java.major_version,
        // ResolvedJava.selection may contain an incidental machine path. Keep
        // only a portable category; exact executable/archive hashes carry the
        // transfer identity independently of that diagnostic text.
        selection: if java.pin_sha512.is_some() {
            "selected_pinned_archive"
        } else if java.home.is_some() {
            "selected_local_sdk"
        } else {
            "selected_java_executable"
        }
        .to_owned(),
        original_archive_url: java.pin_url.clone(),
        original_archive_sha512: java.pin_sha512.clone(),
        supplied_executable_bytes: raw.len(),
        supplied_executable_sha256: sha256(raw),
        actual_sdk_rechecked: false,
    })
}

fn reviewed_target(target: &str) -> Result<ReviewedTarget> {
    let document: ReviewedInventory = facet_json::from_str(INVENTORY)?;
    document
        .targets
        .into_iter()
        .find(|row| row.target == target)
        .ok_or_else(|| eyre::eyre!("unreviewed named NFRT target"))
}

fn function_exports(
    target: &ReviewedTarget,
    supplement: &NfrtChildIdentitySupplement,
) -> Result<Vec<NfrtFunctionExport>> {
    let names: &[&str] = if target.target == "26.1.2" {
        &["preProcessJar", "decompile"]
    } else {
        &[
            "decompile",
            "merge",
            "rename",
            "mergeMappings",
            "bundleExtractJar",
        ]
    };
    ensure!(
        target.requested_functions_in_original_property_order.len() == names.len(),
        "frozen function inventory count changed"
    );
    names
        .iter()
        .map(|name| {
            let function = &target.requested_functions_in_original_property_order[*name];
            let classpath = match (&function.version, &function.classpath) {
                (Some(version), None) => vec![version.clone()],
                (None, Some(classpath)) => classpath.clone(),
                _ => eyre::bail!("ambiguous frozen function classpath"),
            };
            supplement.function_requests(name, &classpath, false)?;
            ensure!(
                function.args.len() <= 512
                    && function.jvmargs.len() <= 128
                    && function
                        .args
                        .iter()
                        .chain(&function.jvmargs)
                        .all(|arg| arg.len() <= 16 * 1024 && !arg.contains('\0')),
                "frozen tool argument arrays exceed bounded contract"
            );
            Ok(NfrtFunctionExport {
                node: (*name).to_owned(),
                classpath,
                args: function.args.clone(),
                jvm_args: function.jvmargs.clone(),
                main_class: function.main_class.clone(),
                original_repository: function.repo.clone(),
                original_java_version: function.java_version,
            })
        })
        .collect()
}

fn validate_invocation_id(value: &str) -> Result<()> {
    ensure!(
        !value.is_empty()
            && value.len() <= 128
            && value
                .bytes()
                .all(|byte| byte.is_ascii_alphanumeric() || matches!(byte, b'-' | b'_')),
        "unsafe or missing fresh invocation identity"
    );
    Ok(())
}

#[cfg(test)]
pub(super) mod tests {
    use super::*;
    use crate::source_projection::candidate_lock::checked_file;
    use crate::source_projection::catalog_owned_project::tests::Fixture;
    use crate::source_projection::core_catalog::CoreCatalog;
    use crate::source_projection::core_inputs::CORE_METADATA_PATH;
    use crate::source_projection::core_inputs::CORE_ROOT;
    use crate::source_projection::core_inputs::CoreProjectInputs;
    use crate::source_projection::core_inputs::select_core_inputs;
    use crate::source_projection::projection_catalog::SUPPORTED_TARGETS;
    use std::path::Path;

    const RECIPES: &str =
        include_str!("../../../../../docs/tasks/sfm-core-released-native-adapter-review.json");
    const RECIPES_SHA256: &str =
        "sha256:8b927a2b978715a6fc6ae1d0fd828a8a46b729a41a43a54897ca5e6cac313e21";

    #[derive(Facet)]
    struct FixtureRecipes {
        targets: Vec<FixtureRecipe>,
    }
    #[derive(Facet)]
    struct FixtureRecipe {
        target: String,
        recipe_id: String,
        source_lock: FixtureInput,
        role_input_hashes: Vec<FixtureInput>,
    }
    #[derive(Facet)]
    struct FixtureInput {
        path: String,
    }

    fn read_bounded(path: &Path, limit: u64) -> Result<Vec<u8>> {
        let mut raw = Vec::new();
        std::fs::File::open(path)?
            .take(
                limit
                    .checked_add(1)
                    .ok_or_else(|| eyre::eyre!("fixture byte limit overflow"))?,
            )
            .read_to_end(&mut raw)?;
        ensure!(
            u64::try_from(raw.len())? <= limit,
            "fixture exceeds bounded bytes"
        );
        Ok(raw)
    }

    pub(in crate::jar_build) fn project_fixture(
        target: &str,
        environment: &str,
    ) -> Result<(Fixture, FrozenRecipeProject)> {
        project_fixture_with_witness_loader(target, environment, |_| Ok(BTreeMap::new()))
    }

    pub(in crate::jar_build) fn project_fixture_with_witness_loader(
        target: &str,
        environment: &str,
        load: impl FnOnce(
            &[crate::source_projection::released_native_inputs::ReleasedExactByteRequirement],
        ) -> Result<BTreeMap<usize, Vec<u8>>>,
    ) -> Result<(Fixture, FrozenRecipeProject)> {
        ensure!(
            sha256(RECIPES.as_bytes()) == RECIPES_SHA256,
            "recipe fixture changed"
        );
        let recipe = facet_json::from_str::<FixtureRecipes>(RECIPES)?
            .targets
            .into_iter()
            .find(|row| row.target == target)
            .ok_or_else(|| eyre::eyre!("fixture recipe missing"))?;
        let repository = Path::new(env!("CARGO_MANIFEST_DIR"))
            .ancestors()
            .nth(3)
            .ok_or_else(|| eyre::eyre!("fixture repository missing"))?;
        let catalog = CoreCatalog::load(repository, repository)?;
        let metadata = CoreProjectInputs::from_json(
            std::str::from_utf8(&read_bounded(
                &checked_file(repository, CORE_METADATA_PATH)?,
                8 * 1024 * 1024,
            )?)?,
            &catalog.registered_features,
        )?;
        let selection = select_core_inputs(
            &metadata,
            &catalog.context(&format!("sfm-4.34.0/mc-{target}"))?,
            &BTreeSet::new(),
        )?;
        let mut fixture = Fixture::new();
        for input in std::iter::once(&recipe.source_lock).chain(recipe.role_input_hashes.iter()) {
            let source = input
                .path
                .strip_prefix(&format!("{CORE_ROOT}/"))
                .ok_or_else(|| eyre::eyre!("fixture input not core owned"))?;
            let selected = selection
                .inputs
                .iter()
                .filter(|(_, row)| row.input == source)
                .collect::<Vec<_>>();
            ensure!(
                selected.len() == 1,
                "fixture role must have one selected output: {}",
                input.path
            );
            fixture.set_project_file_for(
                target,
                selected[0].0,
                source,
                &read_bounded(&checked_file(repository, &input.path)?, 1024 * 1024)?,
                selected[0].1.template,
            )?;
        }
        let slot = SUPPORTED_TARGETS
            .iter()
            .position(|(id, _)| *id == target)
            .ok_or_else(|| eyre::eyre!("fixture target missing"))?;
        let key = Fixture::key(slot, environment);
        fixture.import_feature_context(&catalog, &format!("sfm-4.34.0/mc-{target}"), &key)?;
        fixture.publish(&key);
        let project = crate::source_projection::frozen_recipe_project::prepare_frozen_recipe_project_with_witness_loader(
            fixture.collect(&key)?.check_current()?,
            &recipe.recipe_id,
            false,
            load,
        )?;
        Ok((fixture, project))
    }

    fn selected_java(major: u32) -> ResolvedJava {
        ResolvedJava {
            executable: "synthetic-java-not-opened".into(),
            home: None,
            version_output: "fixture".to_owned(),
            major_version: major,
            selection: "fixture-no-runtime-attestation".to_owned(),
            pin_url: None,
            pin_sha512: None,
        }
    }

    #[test]
    fn rendered_exclusion_fixture_retains_mode_and_original_recipe_pin() -> Result<()> {
        let (_fixture, project) = project_fixture("1.20.2", "release")?;
        let binding = project
            .receipt()
            .role_bindings
            .values()
            .find(|binding| binding.output == "gradle/source-excludes/1.20.2/main-java.txt")
            .ok_or_else(|| eyre::eyre!("original main exclusion role missing"))?;
        ensure!(
            binding.declared_template && binding.authored_sha256 != binding.raw_sha256,
            "fixture must preserve the real template mode and distinct rendered bytes"
        );
        project.recheck(false)?;
        Ok(())
    }

    #[test]
    fn frozen_six_recipe_function_argument_and_compiler_matrix_is_exact() -> Result<()> {
        ensure!(
            sha256(INVENTORY.as_bytes()) == INVENTORY_SHA256,
            "inventory changed"
        );
        let mut total_functions = 0;
        for target in ["1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2"] {
            let row = reviewed_target(target)?;
            let expected_compiler = if target == "26.1.2" {
                25
            } else if target.starts_with("1.21.") {
                21
            } else {
                17
            };
            ensure!(
                row.compiler_release == expected_compiler
                    && row.minimum_tool_jvm == if target == "26.1.2" { 25 } else { 21 }
                    && row.minecraft_version == if target == "1.21.0" { "1.21" } else { target },
                "frozen target/compiler/tool identity changed"
            );
            total_functions += row.requested_functions_in_original_property_order.len();
            if target == "26.1.2" {
                let decompile = &row.requested_functions_in_original_property_order["decompile"];
                ensure!(
                    decompile.classpath.as_deref()
                        == Some(
                            &[
                                "org.vineflower:vineflower:1.11.2".to_owned(),
                                "net.neoforged:vineflower-plugins:0.1.5".to_owned()
                            ][..]
                        )
                        && decompile.args.contains(&"--indent-string=    ".to_owned())
                        && decompile.jvmargs == ["-Xmx4g"],
                    "26 plugin/argument order or whitespace changed"
                );
            }
        }
        ensure!(total_functions == 27, "function count changed");
        Ok(())
    }

    #[test]
    fn selected_sdk_byte_binding_does_not_claim_runtime_attestation() -> Result<()> {
        ensure!(
            sdk_transfer(&selected_java(17), b"java", 21).is_err(),
            "too-old tool JVM accepted"
        );
        let first = sdk_transfer(&selected_java(21), b"java-a", 21)?;
        let second = sdk_transfer(&selected_java(21), b"java-b", 21)?;
        ensure!(
            !first.actual_sdk_rechecked
                && first.supplied_executable_sha256 != second.supplied_executable_sha256,
            "SDK transfer lost exact supplied bytes or claimed actual SDK proof"
        );
        let mut partial = selected_java(21);
        partial.pin_url = Some("https://example.invalid/sdk.zip".to_owned());
        ensure!(
            sdk_transfer(&partial, b"java", 21).is_err(),
            "partial pin identity accepted"
        );
        Ok(())
    }

    #[test]
    fn source_library_closure_reports_all_exact_gaps_without_version_fallback() -> Result<()> {
        let (_fixture, project) = project_fixture("1.20.3", "release")?;
        let original = project.dependencies();
        let paths =
            BTreeSet::from(["ca/weblite/java-objc-bridge/1.1/java-objc-bridge-1.1.jar".to_owned()]);
        let known = "net.neoforged:mergetool:2.0.0:api";
        let supplement = NfrtChildIdentitySupplement::from_prepared(Arc::clone(original), false)?;
        let catalog = NfrtRequestCatalog::Released(Arc::clone(original));
        let original = &catalog;
        ensure!(
            approved_recipe_library_coordinates(
                &supplement,
                &["net.neoforged:mergetool:2.0.2:api".to_owned()]
            )
            .is_err(),
            "partial original recipe source-library evidence accepted"
        );
        ensure!(
            approved_recipe_library_coordinates(
                &supplement,
                &[
                    "net.neoforged:mergetool:2.0.2:api".to_owned(),
                    "org.jetbrains:annotations:24.1.0@jar".to_owned()
                ]
            )?
            .len()
                == 2,
            "exact original recipe library evidence rejected"
        );
        let no_supplement = BTreeSet::new();
        let selected = select_source_library_requests(
            original,
            vec![
                known.to_owned(),
                format!("{known}@jar"),
                "ca.weblite:java-objc-bridge:1.1".to_owned(),
            ],
            &paths,
            &no_supplement,
        )?;
        ensure!(
            selected.len() == 1 && selected[0].0 == format!("original/{known}"),
            "exact known library or duplicate normalization changed"
        );
        let error = select_source_library_requests(
            original,
            vec![
                known.to_owned(),
                "net.neoforged:mergetool:2.0.2:api".to_owned(),
                "org.jetbrains:annotations:24.1.0".to_owned(),
                "net.neoforged:mergetool:2.0.2:api@jar".to_owned(),
            ],
            &paths,
            &no_supplement,
        )
        .expect_err("missing exact identities must not resolve to locked older versions")
        .to_string();
        ensure!(
            error.contains("2 source libraries")
                && error.contains("net.neoforged:mergetool:2.0.2:api")
                && error.contains("org.jetbrains:annotations:24.1.0"),
            "complete closure gap diagnostic missing: {error}"
        );
        let approved = BTreeSet::from([
            "net.neoforged:mergetool:2.0.2:api".to_owned(),
            "org.jetbrains:annotations:24.1.0".to_owned(),
        ]);
        let selected = select_source_library_requests(
            original,
            vec![
                known.to_owned(),
                "net.neoforged:mergetool:2.0.2:api".to_owned(),
                "org.jetbrains:annotations:24.1.0@jar".to_owned(),
            ],
            &paths,
            &approved,
        )?;
        ensure!(
            selected.len() == 1 && selected[0].0 == format!("original/{known}"),
            "separate approved-library requests lost original lock identity"
        );
        ensure!(
            select_source_library_requests(
                original,
                vec!["net.neoforged:mergetool:2.0.3:api".to_owned()],
                &paths,
                &approved
            )
            .is_err(),
            "unapproved library version accepted"
        );
        ensure!(
            select_source_library_requests(
                original,
                vec!["ca.weblite:java-objc-bridge:1.2".to_owned()],
                &paths,
                &no_supplement
            )
            .is_err(),
            "manifest path accepted another version"
        );
        ensure!(
            select_source_library_requests(
                original,
                vec!["ca.weblite:java-objc-bridge:1.1:api".to_owned()],
                &paths,
                &no_supplement
            )
            .is_err(),
            "manifest path accepted another classifier"
        );
        ensure!(
            select_source_library_requests(
                original,
                vec![format!("{known}@zip")],
                &paths,
                &no_supplement
            )
            .is_err(),
            "non-JAR suffix silently normalized"
        );
        Ok(())
    }

    #[test]
    fn pure_snapshots_use_only_generated_relative_names_and_bound_full_hashes() -> Result<()> {
        for invalid in ["", "../outside", "with space", "a/b", "C:drive"] {
            ensure!(
                validate_invocation_id(invalid).is_err(),
                "unsafe invocation identity accepted"
            );
        }
        validate_invocation_id("fresh-test-123")?;
        let first: Arc<[u8]> = Arc::from(&b"original bytes"[..]);
        let digest = sha256(&first);
        ensure!(
            digest == sha256(b"original bytes"),
            "memory copy byte identity changed"
        );
        Ok(())
    }

    #[test]
    fn five_recipe_ten_context_preparations_keep_order_raw_locks_and_main_refusal() -> Result<()> {
        let mut contexts = BTreeSet::new();
        for target in ["1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1"] {
            for environment in ["release", "dev"] {
                let (_fixture, project) = project_fixture(target, environment)?;
                let original_raw = project.dependencies().raw_source_lock_bytes().to_vec();
                let supplement = NfrtChildIdentitySupplement::from_prepared(
                    Arc::clone(project.dependencies()),
                    false,
                )?;
                let plan = NfrtExportPlan::prepare(
                    &project,
                    &supplement,
                    &selected_java(21),
                    b"fixture-executable",
                    "fresh-fixture",
                    false,
                )?;
                let receipt = plan.declaration();
                ensure!(
                    receipt.environment == environment
                        && receipt.target_id == target
                        && receipt.ordered_dependency_rows == project.dependencies().dependencies()
                        && receipt.ordered_runtime_roots == project.dependencies().runtime_roots()
                        && receipt
                            .functions
                            .iter()
                            .map(|value| value.node.as_str())
                            .collect::<Vec<_>>()
                            == [
                                "decompile",
                                "merge",
                                "rename",
                                "mergeMappings",
                                "bundleExtractJar"
                            ]
                        && receipt.fresh_work_root_required
                        && !receipt.native_execution_enabled
                        && !receipt.os_filesystem_attested
                        && plan.parent_coordinates.len() == 3,
                    "export declaration altered context/order or execution boundary"
                );
                ensure!(
                    contexts.insert(receipt.preparation_identity.clone()),
                    "context identity reused"
                );
                for classifier in ["sources", "universal"] {
                    let coordinate = format!(
                        "{}:{classifier}",
                        project.receipt().released_inputs.loader_coordinate
                    );
                    let expected = project.dependencies().coordinate(&coordinate, false)?;
                    let selected = plan
                        .originals
                        .iter()
                        .filter(|(key, _)| key == &format!("original/{coordinate}"))
                        .collect::<Vec<_>>();
                    ensure!(
                        selected.len() == 1
                            && selected[0].1.original_content_hash()
                                == expected.original_content_hash(),
                        "userdev archive omitted, duplicated or detached from original pin"
                    );
                }
                ensure!(
                    plan.export_supplied_bytes(&project, &BTreeMap::new(), &BTreeMap::new(), false)
                        .is_err(),
                    "missing byte batch accepted"
                );
                ensure!(
                    NfrtExportPlan::prepare(
                        &project,
                        &supplement,
                        &selected_java(21),
                        b"fixture-executable",
                        "fresh",
                        true
                    )
                    .is_err(),
                    "refresh accepted"
                );
                ensure!(
                    project.dependencies().raw_source_lock_bytes() == original_raw,
                    "exporter mutated original lock"
                );
            }
        }
        ensure!(contexts.len() == 10, "context matrix changed");
        Ok(())
    }
}
