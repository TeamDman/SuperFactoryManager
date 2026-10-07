//! Pure planning/refusal for the staged named-only NFRT 2.0.19 adapter.
//!
//! Unregistered foundation only. This module does not write a launch contract,
//! copy/cache/acquire artifacts, recheck files, spawn Java or enable any native
//! target. It consumes already checked private project/dependency handles and
//! records still-open execution gates. A proposal is not a build cache key.
//! The ignored Java include must be separately promoted before registration.

use super::frozen_recipe_project::FrozenRecipeProject;
use super::nfrt_child_identity_supplement::NfrtChildIdentitySupplement;
use super::prepared_dependency_inputs::FrozenRuntimeRoot;
use super::provenance::sha256;
use super::released_native_inputs::ReleasedPreparedDependency;
use eyre::Result;
use eyre::ensure;
use facet::Facet;

const ADAPTER_SOURCE: &str =
    include_str!("../../../../../target/core-nfrt-launcher-stage-v1/SFMNamedNeoFormLauncher.java");
const ADAPTER_SHA256: &str =
    "sha256:3a3868fe950d8a0be3c3cde04a95b299190468a407a64dd6f3d6c1cd571a5c38";
const NFRT_COORDINATE: &str = "net.neoforged:neoform-runtime:2.0.19:all";
const NFRT_ORIGINAL_PIN: &str = "blake3:008acc741f349d2b57d6c393f3cb248974143a1a";
const NFRT_SHA256: &str = "sha256:6db13ff7efaa70cf076f1f5d6a4f116885a3a5fa4e03960da88b9d92b25089bd";
const MAX_ADAPTER_BYTES: usize = 128 * 1024;

/// Portable source/shape proposal, explicitly not a launch/cache receipt.
#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub struct NamedNfrtAdapterProposalReceipt {
    pub schema: String,
    pub projection_key: String,
    pub environment: String,
    pub target_id: String,
    pub minecraft_version: String,
    pub recipe_id: String,
    pub checked_project_preparation_identity: String,
    pub original_source_lock_sha256: String,
    pub original_request_catalog_identity: String,
    pub child_supplement_identity: String,
    pub child_supplement_sha256: String,
    pub adapter_source_sha256: String,
    pub nfrt_coordinate: String,
    pub nfrt_original_content_hash: String,
    pub nfrt_observed_sha256: String,
    pub userdev_coordinate: String,
    pub userdev_original_content_hash: String,
    pub compiler_release: u16,
    pub minimum_tool_jvm: u16,
    pub frozen_runtime_roots: Vec<FrozenRuntimeRoot>,
    pub frozen_transitive_runtime_rows: Vec<ReleasedPreparedDependency>,
    pub source_child_coordinates: Vec<String>,
    pub native_execution_enabled: bool,
    pub legacy_executor_unchanged: bool,
    pub intermediate_cache_reuse: bool,
    pub intermediate_cache_persistence: bool,
    pub launcher_probing: bool,
    pub live_pom_discovery: bool,
    pub external_network_sandbox: bool,
    pub blocked_by: Vec<String>,
}

/// Private proposal, not an external-execution permit.
#[derive(Clone, Debug)]
pub struct NamedNfrtAdapterProposal {
    receipt: NamedNfrtAdapterProposalReceipt,
}

impl NamedNfrtAdapterProposal {
    #[must_use]
    pub fn receipt(&self) -> &NamedNfrtAdapterProposalReceipt {
        &self.receipt
    }

    /// Context-sensitive planning identity; actual SDK/snapshot outputs missing.
    ///
    /// # Errors
    /// Returns a typed serialization error. It performs no filesystem work.
    pub fn proposal_identity(&self) -> Result<String> {
        Ok(sha256(facet_json::to_string(&self.receipt)?.as_bytes()))
    }

    /// Check only the declared JVM major, not its artifact/source provenance.
    ///
    /// # Errors
    /// Rejects a too-old declaration. Success cannot waive the actual SDK gate.
    pub fn validate_declared_tool_major(&self, major: u16) -> Result<()> {
        ensure!(
            major >= self.receipt.minimum_tool_jvm,
            "named NFRT requires tool JVM >={} independently of compiler --release {}",
            self.receipt.minimum_tool_jvm,
            self.receipt.compiler_release
        );
        Ok(())
    }

    /// Refuse execution regardless of successful input/shape planning.
    ///
    /// # Errors
    /// Always returns the unresolved explicit integration boundary. No caller
    /// should interpret a proposal, source compile or child-pin approval as a
    /// native Java graph/process permission.
    pub fn require_native_execution(&self) -> Result<()> {
        eyre::bail!(
            "named NFRT execution is not enabled for {}: {}",
            self.receipt.projection_key,
            self.receipt.blocked_by.join(", ")
        )
    }
}

/// Derive a proposal from real checked named ownership, not a fake branch.
///
/// # Errors
/// Rejects refresh first, cross-project supplements, changed original parent
/// pins, wrong compiler/tool split or staged source bytes. It does not recheck
/// ownership or byte snapshots across I/O; those remain mandatory later gates.
pub fn propose_named_nfrt_adapter(
    project: &FrozenRecipeProject,
    supplement: &NfrtChildIdentitySupplement,
    refresh: bool,
) -> Result<NamedNfrtAdapterProposal> {
    ensure!(
        !refresh,
        "named NFRT adapter refuses refresh before any caller effects"
    );
    ensure!(
        ADAPTER_SOURCE.len() <= MAX_ADAPTER_BYTES
            && sha256(ADAPTER_SOURCE.as_bytes()) == ADAPTER_SHA256,
        "staged named NFRT adapter source changed"
    );
    let project_receipt = project.receipt();
    let child = supplement.receipt();
    let original = project.dependencies().receipt();
    ensure!(
        original.target_id == child.target_id
            && original.minecraft_version == child.minecraft_version
            && original.recipe_id == child.recipe_id
            && original.source_lock_sha256 == child.source_lock_sha256
            && original.request_catalog_identity == child.original_request_catalog_identity
            && project_receipt.compiler_release == child.compiler_release
            && project_receipt.tool_jvm_minimum == child.minimum_tool_jvm
            && project_receipt.immutable_source_lock
            && original.immutable_source_lock
            && !original.live_pom_discovery
            && !child.native_execution_enabled,
        "named NFRT adapter lost exact project/recipe/original/supplement binding"
    );
    let nfrt = project.dependencies().coordinate(NFRT_COORDINATE, false)?;
    ensure!(
        nfrt.original_content_hash() == NFRT_ORIGINAL_PIN,
        "named NFRT adapter parent is not exact frozen 2.0.19"
    );
    let userdev_coordinate = format!(
        "{}:userdev",
        project_receipt.released_inputs.loader_coordinate
    );
    let userdev = project
        .dependencies()
        .coordinate(&userdev_coordinate, false)?;
    let roots = project.dependencies().runtime_roots().to_vec();
    let runtime = project
        .dependencies()
        .frozen_runtime_rows_for(&roots, false)?
        .into_iter()
        .cloned()
        .collect::<Vec<_>>();
    let source_child_coordinates = supplement
        .source_requests(false)?
        .iter()
        .map(|request| request.coordinate().to_owned())
        .collect();
    let receipt = NamedNfrtAdapterProposalReceipt {
        schema: "sfm:named_nfrt_adapter_proposal@1".to_owned(),
        projection_key: project_receipt.ownership.projection_key.clone(),
        environment: project_receipt.ownership.environment.as_str().to_owned(),
        target_id: original.target_id.clone(),
        minecraft_version: original.minecraft_version.clone(),
        recipe_id: original.recipe_id.clone(),
        checked_project_preparation_identity: project_receipt.preparation_identity()?,
        original_source_lock_sha256: original.source_lock_sha256.clone(),
        original_request_catalog_identity: original.request_catalog_identity.clone(),
        child_supplement_identity: child.supplement_identity.clone(),
        child_supplement_sha256: child.supplement_sha256.clone(),
        adapter_source_sha256: ADAPTER_SHA256.to_owned(),
        nfrt_coordinate: NFRT_COORDINATE.to_owned(),
        nfrt_original_content_hash: nfrt.original_content_hash(),
        nfrt_observed_sha256: NFRT_SHA256.to_owned(),
        userdev_coordinate,
        userdev_original_content_hash: userdev.original_content_hash(),
        compiler_release: project_receipt.compiler_release,
        minimum_tool_jvm: project_receipt.tool_jvm_minimum,
        frozen_runtime_roots: roots,
        frozen_transitive_runtime_rows: runtime,
        source_child_coordinates,
        native_execution_enabled: false,
        legacy_executor_unchanged: true,
        intermediate_cache_reuse: false,
        intermediate_cache_persistence: false,
        launcher_probing: false,
        live_pom_discovery: false,
        external_network_sandbox: false,
        blocked_by: vec![
            "checked_ownership_and_raw_lock_recheck_before_effects".to_owned(),
            "original_parent_and_child_exact_bytes_at_consumption".to_owned(),
            "authenticated_minecraft_version_children_export".to_owned(),
            "rust_owned_immutable_invocation_snapshots_and_reparse_checks".to_owned(),
            "fresh_generated_classpath_producer_receipts".to_owned(),
            "child_process_network_containment".to_owned(),
            "actual_checksum_verified_tool_sdk_identity".to_owned(),
            "frozen_runtime_order_consumer_integration".to_owned(),
            "named_engine_adapter_contract_export_and_wiring".to_owned(),
        ],
    };
    Ok(NamedNfrtAdapterProposal { receipt })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::source_projection::candidate_lock::checked_file;
    use crate::source_projection::catalog_owned_project::tests::Fixture;
    use crate::source_projection::core_catalog::CoreCatalog;
    use crate::source_projection::core_inputs::CORE_METADATA_PATH;
    use crate::source_projection::core_inputs::CORE_ROOT;
    use crate::source_projection::core_inputs::CoreProjectInputs;
    use crate::source_projection::core_inputs::select_core_inputs;
    use crate::source_projection::core_slice_test_support::read_bounded;
    use crate::source_projection::frozen_recipe_project::prepare_frozen_recipe_project;
    use crate::source_projection::projection_catalog::SUPPORTED_TARGETS;
    use std::cell::Cell;
    use std::collections::BTreeMap;
    use std::collections::BTreeSet;
    use std::path::Path;
    use std::sync::Arc;

    const REVIEW: &str =
        include_str!("../../../../../docs/tasks/sfm-core-released-native-adapter-review.json");
    const REVIEW_SHA256: &str =
        "sha256:8b927a2b978715a6fc6ae1d0fd828a8a46b729a41a43a54897ca5e6cac313e21";

    #[derive(Facet)]
    struct Review {
        targets: Vec<Recipe>,
    }

    #[derive(Facet)]
    struct Recipe {
        target: String,
        recipe_id: String,
        source_lock: Input,
        role_input_hashes: Vec<Input>,
    }

    #[derive(Facet)]
    struct Input {
        path: String,
    }

    fn fixture(target: &str) -> Result<(Fixture, usize, String)> {
        ensure!(
            sha256(REVIEW.as_bytes()) == REVIEW_SHA256,
            "recipe fixture changed"
        );
        let recipe = facet_json::from_str::<Review>(REVIEW)?
            .targets
            .into_iter()
            .find(|recipe| recipe.target == target)
            .ok_or_else(|| eyre::eyre!("fixture target missing"))?;
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
        let context = catalog.context(&format!("sfm-4.34.0/mc-{target}"))?;
        let selection = select_core_inputs(&metadata, &context, &BTreeSet::new())?;
        let mut fixture = Fixture::new();
        for input in std::iter::once(&recipe.source_lock).chain(recipe.role_input_hashes.iter()) {
            let source = input
                .path
                .strip_prefix(&format!("{CORE_ROOT}/"))
                .ok_or_else(|| eyre::eyre!("fixture role is not core owned"))?;
            let selected = selection
                .inputs
                .iter()
                .filter(|(_, candidate)| candidate.input == source)
                .collect::<Vec<_>>();
            ensure!(
                selected.len() == 1 && !selected[0].1.template,
                "fixture role is not an exact selected copy"
            );
            fixture.set_project_file_for(
                target,
                selected[0].0,
                source,
                &read_bounded(&checked_file(repository, &input.path)?, 1024 * 1024)?,
                false,
            )?;
        }
        let slot = SUPPORTED_TARGETS
            .iter()
            .position(|(candidate, _)| *candidate == target)
            .ok_or_else(|| eyre::eyre!("fixture slot missing"))?;
        Ok((fixture, slot, recipe.recipe_id))
    }

    fn project(
        fixture: &Fixture,
        slot: usize,
        environment: &str,
        recipe_id: &str,
    ) -> Result<FrozenRecipeProject> {
        let key = Fixture::key(slot, environment);
        fixture.publish(&key);
        prepare_frozen_recipe_project(
            fixture.collect(&key)?.check_current()?,
            recipe_id,
            false,
            &BTreeMap::new(),
        )
    }

    #[test]
    fn five_real_recipe_ten_context_proposals_keep_compile_and_tool_jvm_separate() -> Result<()> {
        let mut identities = BTreeSet::new();
        for target in ["1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1"] {
            let (fixture, slot, recipe_id) = fixture(target)?;
            for environment in ["release", "dev"] {
                let project = project(&fixture, slot, environment, &recipe_id)?;
                let original_raw = project.dependencies().raw_source_lock_bytes().to_vec();
                let supplement = NfrtChildIdentitySupplement::from_prepared(
                    Arc::clone(project.dependencies()),
                    false,
                )?;
                let proposal = propose_named_nfrt_adapter(&project, &supplement, false)?;
                ensure!(
                    proposal.receipt().projection_key == Fixture::key(slot, environment)
                        && proposal.receipt().environment == environment
                        && proposal.receipt().minimum_tool_jvm == 21
                        && proposal.receipt().compiler_release
                            == if target.starts_with("1.21.") { 21 } else { 17 }
                        && !proposal.receipt().native_execution_enabled
                        && !proposal.receipt().external_network_sandbox
                        && !proposal.receipt().intermediate_cache_reuse
                        && !proposal.receipt().intermediate_cache_persistence
                        && !proposal.receipt().launcher_probing
                        && proposal.receipt().legacy_executor_unchanged,
                    "proposal claimed native support or changed the tool/compiler split"
                );
                ensure!(
                    project.dependencies().raw_source_lock_bytes() == original_raw,
                    "adapter proposal rewrote a released lock"
                );
                ensure!(
                    identities.insert(proposal.proposal_identity()?),
                    "different context reused proposal identity"
                );
                ensure!(
                    proposal.validate_declared_tool_major(17).is_err()
                        && proposal.validate_declared_tool_major(21).is_ok()
                        && proposal.require_native_execution().is_err(),
                    "JVM declaration waived native gates"
                );
            }
        }
        ensure!(identities.len() == 10, "context identity count changed");
        Ok(())
    }

    #[test]
    fn refresh_and_foreign_supplement_refuse_before_caller_effects() -> Result<()> {
        let (first, slot, recipe) = fixture("1.20.2")?;
        let project_first = project(&first, slot, "release", &recipe)?;
        let supplement_first = NfrtChildIdentitySupplement::from_prepared(
            Arc::clone(project_first.dependencies()),
            false,
        )?;
        let (second, slot, recipe) = fixture("1.20.3")?;
        let project_second = project(&second, slot, "dev", &recipe)?;
        let effects = Cell::new(0);
        for result in [
            propose_named_nfrt_adapter(&project_first, &supplement_first, true),
            propose_named_nfrt_adapter(&project_second, &supplement_first, false),
        ] {
            if result.is_ok() {
                effects.set(effects.get() + 1);
            }
        }
        ensure!(
            effects.get() == 0,
            "refused proposal reached caller effects"
        );
        Ok(())
    }

    #[test]
    fn adapter_source_has_all_pinned_request_hooks_and_no_graph_entry_point() -> Result<()> {
        ensure!(
            sha256(ADAPTER_SOURCE.as_bytes()) == ADAPTER_SHA256,
            "source golden changed"
        );
        for signature in [
            "public Artifact get(String location)",
            "public Artifact get(MavenCoordinate coordinate)",
            "public Artifact get(String location, URI repositoryBaseUrl)",
            "public Artifact get(MavenCoordinate coordinate, URI repositoryBaseUrl)",
            "public Artifact get(MinecraftLibrary library)",
            "public List<Path> resolveClasspath(Collection<ClasspathItem> items)",
            "public Artifact getVersionManifest(String minecraftVersion)",
            "public Artifact getLauncherManifest()",
            "public void loadArtifactManifest(Path ignored)",
            "public void setWarnOnArtifactManifestMiss(boolean ignored)",
            "public boolean restoreOutputsFromCache(",
            "public void saveOutputs(",
            "public List<Path> getInstallationRoots()",
            "public List<Path> getAssetRoots()",
            "public Path getAssetDirectoryForIndex(String ignored)",
        ] {
            ensure!(
                ADAPTER_SOURCE.contains(signature),
                "missing pinned hook: {signature}"
            );
        }
        ensure!(
            ADAPTER_SOURCE.contains("public Artifact downloadFromManifest(")
                && ADAPTER_SOURCE.contains("throw new UnsupportedOperationException(")
                && !ADAPTER_SOURCE.contains("new CommandLine(")
                && !ADAPTER_SOURCE.contains("super.get(")
                && !ADAPTER_SOURCE.contains("super.resolveClasspath(")
                && !ADAPTER_SOURCE.contains("downloadManager.download(")
                && ADAPTER_SOURCE.contains(
                    "Fresh NodeOutput classpath needs a separately verified producer receipt"
                ),
            "staged adapter delegated to an uncontrolled route"
        );
        Ok(())
    }
}
