//! Read-only frozen schema-2 recipes over checked catalog-owned projects.
//!
//! This is dependency preparation, not native planning or execution. It never
//! synchronizes a projection, creates a cache, resolves a JDK, acquires an
//! artifact, discovers a POM, or rewrites a source lock. The catalog handle
//! proves the selected source membership; the frozen recipe proves dependency
//! roles. Both identities remain independent of any legacy branch name.

use super::catalog_owned_project::CatalogOwnedProject;
use super::catalog_owned_project::CatalogOwnedProjectReceipt;
use super::core_catalog::read_bounded_catalog_input;
use super::prepared_dependency_inputs::PreparedDependencyInputs;
use super::prepared_dependency_inputs::PreparedDependencyReceipt;
use super::provenance::sha256;
use super::released_native_inputs::BUILD_CONFIGURATION_PATH;
use super::released_native_inputs::ReleasedNativeReceipt;
use super::released_native_inputs::ReleasedNativeRequest;
use super::released_native_inputs::review_released_native_inputs_from_configuration;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::sync::Arc;

#[cfg(test)]
const REVIEW: &str =
    include_str!("../../../../../docs/tasks/sfm-core-released-native-adapter-review.json");
#[cfg(test)]
const REVIEW_SHA256: &str =
    "sha256:8b927a2b978715a6fc6ae1d0fd828a8a46b729a41a43a54897ca5e6cac313e21";
const MAX_REVIEW_BYTES: usize = 512 * 1024;
const LOCK_OUTPUT: &str = "sfm-toolchain.lock.json";
const ROLE_INPUT_COUNT: usize = 13;

/// Portable evidence for a prepared recipe, not a compiler or runtime permit.
#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub struct FrozenRecipeProjectReceipt {
    pub schema: String,
    pub scope: String,
    pub ownership: CatalogOwnedProjectReceipt,
    pub released_inputs: ReleasedNativeReceipt,
    pub prepared_inputs: PreparedDependencyReceipt,
    pub source_lock_binding: FrozenRecipeInputBinding,
    pub role_bindings: BTreeMap<String, FrozenRecipeInputBinding>,
    pub compiler_release: u16,
    pub tool_jvm_minimum: u16,
    pub jdk_identity: Option<String>,
    pub immutable_source_lock: bool,
    pub native_compilation: String,
    pub native_execution: String,
    pub external_execution_gates: Vec<String>,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub struct FrozenRecipeInputBinding {
    pub authored_input: String,
    pub output: String,
    pub authored_sha256: String,
    pub authored_bytes: usize,
    pub declared_template: bool,
    /// Exact role bytes supplied to the frozen recipe, after approved rendering.
    pub raw_sha256: String,
    pub bytes: usize,
}

/// A checked project and one immutable strict dependency handle.
///
/// The private fields prevent construction from arbitrary output paths or a
/// fabricated profile. Recheck immediately before a later external effect;
/// this snapshot does not hold a filesystem lock across execution.
#[derive(Debug)]
pub struct FrozenRecipeProject {
    project: CatalogOwnedProject,
    dependencies: Arc<PreparedDependencyInputs>,
    receipt: FrozenRecipeProjectReceipt,
}

/// Exact named source-set policy and its optional authenticated fingerprint input.
pub(crate) struct FrozenRecipeSourceExclusionPolicy {
    pub(crate) patterns: Vec<String>,
    pub(crate) output: Option<String>,
}

impl FrozenRecipeProjectReceipt {
    /// Hash exact context, ownership, recipe, lock and request-catalog evidence.
    /// Actual JDK identity must be included separately by a later build plan.
    ///
    /// # Errors
    /// Returns an error if the typed receipt cannot be serialized.
    pub fn preparation_identity(&self) -> Result<String> {
        Ok(sha256(facet_json::to_string(self)?.as_bytes()))
    }

    /// Propose a project-relative preparation cache name without touching it.
    /// This is not a build cache identity until actual JDK/tool inputs are bound.
    ///
    /// # Errors
    /// Returns an error if the typed receipt cannot be serialized.
    pub fn preparation_cache_relative_path(&self) -> Result<String> {
        let digest = self.preparation_identity()?;
        let digest = digest
            .strip_prefix("sha256:")
            .ok_or_else(|| eyre::eyre!("preparation identity did not use SHA-256"))?;
        Ok(format!("build/sfm-toolchain/native-project/{digest}"))
    }

    /// Serialize portable preparation evidence, with explicit non-build scope.
    ///
    /// # Errors
    /// Returns an error if the typed receipt cannot be serialized.
    pub fn to_json(&self) -> Result<String> {
        Ok(facet_json::to_string_pretty(self)?)
    }
}

impl FrozenRecipeProject {
    #[must_use]
    pub fn project(&self) -> &CatalogOwnedProject {
        &self.project
    }

    #[must_use]
    pub fn dependencies(&self) -> &Arc<PreparedDependencyInputs> {
        &self.dependencies
    }

    #[must_use]
    pub fn receipt(&self) -> &FrozenRecipeProjectReceipt {
        &self.receipt
    }

    pub(crate) fn source_exclusion_policy(
        &self,
        source_set: &str,
    ) -> Result<FrozenRecipeSourceExclusionPolicy> {
        ensure!(
            matches!(source_set, "main" | "test" | "gametest" | "datagen"),
            "unknown frozen recipe source set: {source_set}"
        );
        self.recheck(false)?;
        let relative = format!(
            "gradle/source-excludes/{}/{source_set}-java.txt",
            self.receipt.ownership.minecraft_version,
        );
        // All authenticated released recipes apply exclusions only to main,
        // test and gametest. Datagen's empty policy is explicit, not a fallback
        // to arbitrary physical files or a missing required recipe input.
        if source_set == "datagen" {
            ensure!(
                !self
                    .dependencies
                    .source_exclusions()
                    .contains_key(&relative)
                    && !self.project.receipt().files.contains_key(&relative),
                "frozen datagen recipe unexpectedly declares an exclusion input"
            );
            return Ok(FrozenRecipeSourceExclusionPolicy {
                patterns: Vec::new(),
                output: None,
            });
        }
        let patterns = self
            .dependencies
            .source_exclusions()
            .get(&relative)
            .cloned()
            .ok_or_else(|| {
                eyre::eyre!("exact frozen source-exclusion input is absent: {relative}")
            })?;
        let input = self.project.selected_input(&relative)?;
        let expected = self
            .receipt
            .role_bindings
            .get(input.source_path())
            .ok_or_else(|| {
                eyre::eyre!("source-exclusion output is not an authenticated recipe role")
            })?;
        ensure!(
            binding(
                &input,
                frozen_role_bytes(&input, &self.receipt.ownership.minecraft_version)?
            ) == *expected,
            "source-exclusion output differs from its authenticated recipe binding"
        );
        Ok(FrozenRecipeSourceExclusionPolicy {
            patterns,
            output: Some(relative),
        })
    }

    /// Recheck exact selected inputs and raw immutable lock before effects.
    ///
    /// # Errors
    /// Refuses refresh before filesystem checks, then rejects changed catalog,
    /// authored/generated membership, provenance, role bindings or lock bytes.
    /// It performs no synchronization, acquisition or cache mutation.
    #[tracing::instrument(name = "frozen_recipe.recheck", skip_all)]
    pub fn recheck(&self, refresh: bool) -> Result<()> {
        refuse_refresh(refresh)?;
        self.project.recheck()?;
        let configuration =
            read_bounded_catalog_input(self.project.repo_root(), BUILD_CONFIGURATION_PATH)?;
        ensure!(
            sha256(&configuration) == self.receipt.released_inputs.recipe_review_sha256,
            "released-native build configuration changed after preparation"
        );
        ensure!(
            self.project.receipt() == &self.receipt.ownership,
            "frozen recipe lost its checked catalog ownership receipt"
        );
        let lock = self.project.selected_input(LOCK_OUTPUT)?;
        let raw = lock.require_exact_copy()?;
        self.dependencies.verify_source_lock_bytes(raw, false)?;
        ensure!(
            binding(&lock, raw) == self.receipt.source_lock_binding,
            "frozen recipe source-lock binding changed"
        );
        for (path, expected) in &self.receipt.role_bindings {
            let input = self.project.authored_input(path)?;
            ensure!(
                binding(
                    &input,
                    frozen_role_bytes(&input, &self.receipt.ownership.minecraft_version)?
                ) == *expected,
                "frozen recipe selected role binding changed: {path}"
            );
        }
        Ok(())
    }
}

/// Prepare an explicitly named frozen recipe from verified selected inputs.
///
/// There is no default recipe, schema migration, schema-4 profile fabrication,
/// arbitrary-path read or cache lookup for weak evidence. The caller supplies
/// exact artifact bytes when required; existing preparation checks those bytes
/// against the original pin, not a replacement checksum or weak metadata.
///
/// # Errors
/// Refuses refresh, unknown/mismatched recipes, stale catalog ownership,
/// unselected or rendered role inputs, changed raw lock bytes, missing weak
/// exact-byte evidence and ambiguous dependency requests. Success does not
/// authorize native compilation, source rebuilding, launching or lock writes.
pub fn prepare_frozen_recipe_project(
    project: CatalogOwnedProject,
    recipe_id: &str,
    refresh: bool,
    exact_byte_witnesses: &BTreeMap<usize, Vec<u8>>,
) -> Result<FrozenRecipeProject> {
    prepare_frozen_recipe_project_with_witness_loader(project, recipe_id, refresh, |_| {
        Ok(exact_byte_witnesses.clone())
    })
}

/// Load witnesses only after checked ownership, original lock and recipe roles
/// have established the exact requirements. Returned bytes still pass the
/// original frozen hash checks; this callback grants no replacement-pin policy.
#[tracing::instrument(name = "frozen_recipe.prepare", skip_all, fields(recipe_id))]
pub(crate) fn prepare_frozen_recipe_project_with_witness_loader(
    project: CatalogOwnedProject,
    recipe_id: &str,
    refresh: bool,
    load: impl FnOnce(
        &[super::released_native_inputs::ReleasedExactByteRequirement],
    ) -> Result<BTreeMap<usize, Vec<u8>>>,
) -> Result<FrozenRecipeProject> {
    refuse_refresh(refresh)?;
    let configuration_bytes =
        read_bounded_catalog_input(project.repo_root(), BUILD_CONFIGURATION_PATH)?;
    let configuration = std::str::from_utf8(&configuration_bytes)?;
    let recipe = recipe_index_from_configuration(configuration)?
        .targets
        .into_iter()
        .find(|recipe| recipe.recipe_id == recipe_id)
        .ok_or_else(|| eyre::eyre!("unknown explicit frozen native recipe: {recipe_id}"))?;
    let owner = project.receipt();
    ensure!(
        owner.target_id == recipe.target
            && owner.minecraft_version == recipe.minecraft_version
            && owner.java_major == recipe.platform.java_major,
        "explicit frozen recipe does not match the checked target, actual Minecraft version or compiler release"
    );
    let (loader, tool_jvm_minimum) = compiler_and_tool_boundary(&recipe)?;
    ensure!(
        owner.loader == loader,
        "explicit frozen recipe loader does not match checked build metadata"
    );
    project.recheck()?;

    // Resolve the lock by its real selected output and the same reviewed
    // source_path. Never accept a standalone raw file with a matching hash.
    let lock = project.selected_input(LOCK_OUTPUT)?;
    ensure!(
        lock.source_path() == recipe.source_lock.path,
        "frozen source lock is not the selected reviewed authored input"
    );
    let raw_lock = lock.require_exact_copy()?;
    ensure!(
        sha256(raw_lock) == format!("sha256:{}", recipe.source_lock.sha256),
        "frozen selected source-lock bytes differ from the reviewed recipe"
    );
    let source_lock_binding = binding(&lock, raw_lock);
    let mut role_bytes = BTreeMap::new();
    let mut role_bindings = BTreeMap::new();
    for witness in &recipe.role_input_hashes {
        let input = project.authored_input(&witness.path)?;
        let bytes = frozen_role_bytes(&input, &recipe.minecraft_version)?;
        ensure!(
            sha256(bytes) == format!("sha256:{}", witness.sha256),
            "frozen selected role bytes differ from the reviewed recipe: {}",
            witness.path
        );
        role_bindings.insert(witness.path.clone(), binding(&input, bytes));
        role_bytes.insert(witness.path.clone(), bytes.to_vec());
    }
    let reviewed = review_released_native_inputs_from_configuration(
        configuration,
        &ReleasedNativeRequest {
            target_id: &recipe.target,
            minecraft_version: &recipe.minecraft_version,
            java_major: recipe.platform.java_major,
            recipe_id: &recipe.recipe_id,
            refresh: false,
        },
        raw_lock,
        &role_bytes,
    )?;
    let witnesses = load(reviewed.exact_byte_requirements())?;
    let released = reviewed.require_exact_bytes(&witnesses)?;
    let released_receipt = released.receipt().clone();
    let dependencies = Arc::new(PreparedDependencyInputs::from_released(released, false)?);
    let receipt = FrozenRecipeProjectReceipt {
        schema: "sfm:frozen_recipe_project@1".to_owned(),
        scope: "read_only_checked_recipe_preparation_not_native_build".to_owned(),
        ownership: project.receipt().clone(),
        released_inputs: released_receipt,
        prepared_inputs: dependencies.receipt().clone(),
        source_lock_binding,
        role_bindings,
        compiler_release: recipe.platform.java_major,
        tool_jvm_minimum,
        jdk_identity: None,
        immutable_source_lock: true,
        native_compilation: "not_performed".to_owned(),
        native_execution: "not_performed".to_owned(),
        external_execution_gates: vec![
            "actual_jdk_identity_and_compiler_tool_boundary".to_owned(),
            "pinned_direct_version_json_children_and_pipeline_consumers".to_owned(),
            "sealed_external_neoform_runtime_manifest".to_owned(),
            "source_build_cache_misses_require_verified_recipe_closure".to_owned(),
            "immutable_engine_writer_and_selected_source_collector_integration".to_owned(),
        ],
    };
    let prepared = FrozenRecipeProject {
        project,
        dependencies,
        receipt,
    };
    prepared.recheck(false)?;
    Ok(prepared)
}

fn binding(
    input: &super::catalog_owned_project::CatalogOwnedInput<'_>,
    bytes: &[u8],
) -> FrozenRecipeInputBinding {
    FrozenRecipeInputBinding {
        authored_input: input.source_path().to_owned(),
        output: input.output_path().to_owned(),
        authored_sha256: sha256(input.source_bytes()),
        authored_bytes: input.source_bytes().len(),
        declared_template: input.declared_template(),
        raw_sha256: sha256(bytes),
        bytes: bytes.len(),
    }
}

// Only the three reviewed exclusion roles may be templates. Their projected
// bytes must still match the original recipe pin at preparation, and the
// receipt binds source bytes, output bytes and mode for every later recheck.
fn frozen_role_bytes<'a>(
    input: &'a super::catalog_owned_project::CatalogOwnedInput<'_>,
    minecraft_version: &str,
) -> Result<&'a [u8]> {
    if !input.declared_template() {
        return input.require_exact_copy();
    }
    let allowed = ["main", "test", "gametest"].into_iter().any(|source_set| {
        let output = format!("gradle/source-excludes/{minecraft_version}/{source_set}-java.txt");
        input.output_path() == output
            && ["standard", "shared"].into_iter().any(|layout| {
                input.source_path()
                    == format!("{}/build/{layout}/{output}", super::core_inputs::CORE_ROOT)
            })
    });
    ensure!(
        allowed,
        "selected role input declares template rendering outside exact frozen source-exclusion roles: {}",
        input.output_path()
    );
    Ok(input.output_bytes())
}

// A deliberately small view of the same checksum-bound recipe document used
// by ReleasedNativeInputs. It supplies exact selected source names only; all
// artifact/row/Gradle-line semantics remain validated by that existing module.
#[derive(Clone, Debug, Facet)]
struct RecipeIndex {
    schema: String,
    targets: Vec<FrozenRecipeIndexRow>,
}

#[derive(Clone, Debug, Facet)]
struct FrozenRecipeIndexRow {
    target: String,
    minecraft_version: String,
    recipe_id: String,
    source_lock: SourceLock,
    role_input_hashes: Vec<RoleInput>,
    platform: Platform,
}

#[derive(Clone, Debug, Facet)]
struct SourceLock {
    path: String,
    sha256: String,
}

#[derive(Clone, Debug, Facet)]
struct RoleInput {
    path: String,
    sha256: String,
}

#[derive(Clone, Debug, Facet)]
struct Platform {
    kind: String,
    java_major: u16,
}

#[tracing::instrument(name = "frozen_recipe.parse_index", skip_all)]
fn recipe_index_from_configuration(configuration: &str) -> Result<RecipeIndex> {
    ensure!(
        configuration.len() <= MAX_REVIEW_BYTES,
        "recipe configuration exceeds its bounded limit"
    );
    let index: RecipeIndex =
        facet_json::from_str(configuration).wrap_err("cannot parse recipe configuration")?;
    ensure!(
        index.schema == "sfm:released_native_build_configuration@1" && index.targets.len() == 10,
        "frozen recipe index has an unexpected schema or target count"
    );
    let mut ids = BTreeSet::new();
    let mut targets = BTreeSet::new();
    for recipe in &index.targets {
        ensure!(
            ids.insert(&recipe.recipe_id)
                && targets.insert(&recipe.target)
                && recipe.role_input_hashes.len() == ROLE_INPUT_COUNT
                && recipe
                    .role_input_hashes
                    .iter()
                    .map(|role| &role.path)
                    .collect::<BTreeSet<_>>()
                    .len()
                    == ROLE_INPUT_COUNT,
            "frozen recipe index has duplicate recipes, targets or role inputs"
        );
    }
    Ok(index)
}

#[cfg(test)]
fn recipe_index() -> Result<RecipeIndex> {
    ensure!(
        sha256(REVIEW.as_bytes()) == REVIEW_SHA256,
        "historical fixture changed"
    );
    recipe_index_from_configuration(&REVIEW.replace(
        "sfm:core_released_native_adapter_review@1",
        "sfm:released_native_build_configuration@1",
    ))
}

#[cfg(test)]
fn selected_recipe(recipe_id: &str) -> Result<FrozenRecipeIndexRow> {
    recipe_index()?
        .targets
        .into_iter()
        .find(|recipe| recipe.recipe_id == recipe_id)
        .ok_or_else(|| eyre::eyre!("unknown explicit frozen native recipe: {recipe_id}"))
}

fn compiler_and_tool_boundary(recipe: &FrozenRecipeIndexRow) -> Result<(&'static str, u16)> {
    match recipe.platform.kind.as_str() {
        "forge_gradle_forge" => Ok(("forge", recipe.platform.java_major)),
        "forge_gradle_neoforge_group" => Ok(("neoforge", recipe.platform.java_major)),
        // The existing native NeoGradle pipeline requires at least Java 21;
        // this does not change the exact released javac --release setting.
        "neogradle_userdev" => Ok(("neoforge", recipe.platform.java_major.max(21))),
        other => eyre::bail!("unreviewed frozen native loader/tool boundary: {other}"),
    }
}

fn refuse_refresh(refresh: bool) -> Result<()> {
    ensure!(
        !refresh,
        "frozen recipe project refuses dependency refresh before ownership checks or external effects"
    );
    Ok(())
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
    use crate::source_projection::projection_catalog::SUPPORTED_TARGETS;
    use std::fs;
    use std::path::Path;
    use walkdir::WalkDir;

    fn repository() -> Result<&'static Path> {
        Path::new(env!("CARGO_MANIFEST_DIR"))
            .ancestors()
            .nth(3)
            .ok_or_else(|| eyre::eyre!("CLI crate must be below the repository"))
    }

    // Reuse the shared isolated 20-context ownership fixture, then replace
    // only the selected target's lock and thirteen roles with frozen bytes.
    // Output names come from the real core selectors, not recipe path guesses.
    #[tracing::instrument(name = "frozen_recipe.fixture", skip_all)]
    fn fixture_for(recipe: &FrozenRecipeIndexRow) -> Result<Fixture> {
        let repo = repository()?;
        // Test setup reads the workspace catalog but mutates only the isolated
        // fixture. Parse the shared immutable inputs once, not per scenario.
        static INPUTS: std::sync::OnceLock<(CoreCatalog, CoreProjectInputs)> =
            std::sync::OnceLock::new();
        let (catalog, metadata) = INPUTS.get_or_init(|| {
            let load = || -> Result<_> {
                let catalog = CoreCatalog::load(repo, repo)?;
                let metadata = CoreProjectInputs::from_json(
                    std::str::from_utf8(&read_bounded(
                        &checked_file(repo, CORE_METADATA_PATH)?,
                        8 * 1024 * 1024,
                    )?)?,
                    &catalog.registered_features,
                )?;
                Ok((catalog, metadata))
            };
            load().expect("load immutable frozen recipe fixture inputs")
        });
        let context = catalog.context(&format!("sfm-4.34.0/mc-{}", recipe.target))?;
        let selection = select_core_inputs(metadata, &context, &BTreeSet::new())?;
        let wanted = std::iter::once(recipe.source_lock.path.clone())
            .chain(
                recipe
                    .role_input_hashes
                    .iter()
                    .map(|input| input.path.clone()),
            )
            .collect();
        let projected =
            super::super::core_release_project_role_fixtures::release_project_role_outputs_selected(
                repo,
                &context,
                &selection,
                &wanted,
            )?;
        let mut fixture = Fixture::new_for_target(&recipe.target);
        let configuration_path = fixture.repository().join(BUILD_CONFIGURATION_PATH);
        fs::create_dir_all(
            configuration_path
                .parent()
                .expect("configuration has a parent"),
        )?;
        fs::copy(repo.join(BUILD_CONFIGURATION_PATH), &configuration_path)?;
        let mut roles = Vec::new();
        for source_path in std::iter::once(recipe.source_lock.path.as_str()).chain(
            recipe
                .role_input_hashes
                .iter()
                .map(|witness| witness.path.as_str()),
        ) {
            let core_relative = source_path
                .strip_prefix(&format!("{CORE_ROOT}/"))
                .ok_or_else(|| eyre::eyre!("frozen fixture input must remain core-owned"))?;
            let selected = selection
                .inputs
                .iter()
                .filter(|(_, input)| input.input == core_relative)
                .collect::<Vec<_>>();
            ensure!(
                selected.len() == 1,
                "frozen fixture role must have one real selected binding: {source_path}"
            );
            let bytes = &projected[source_path];
            roles.push((
                selected[0].0.as_str(),
                core_relative,
                bytes.as_slice(),
                false,
            ));
        }
        fixture.set_project_files_for(&recipe.target, roles)?;
        Ok(fixture)
    }

    fn slot(recipe: &FrozenRecipeIndexRow) -> Result<usize> {
        SUPPORTED_TARGETS
            .iter()
            .position(|(target, _)| *target == recipe.target)
            .ok_or_else(|| eyre::eyre!("recipe has an unsupported fixture target"))
    }

    fn primary_recipe() -> Result<FrozenRecipeIndexRow> {
        selected_recipe("sfm:released-native-inputs/4.34.0/1.19.2@1")
    }

    fn prepare(
        fixture: &Fixture,
        key: &str,
        recipe: &FrozenRecipeIndexRow,
    ) -> Result<FrozenRecipeProject> {
        prepare_frozen_recipe_project(
            fixture.collect(key)?.check_current()?,
            &recipe.recipe_id,
            false,
            &BTreeMap::new(),
        )
    }

    fn snapshot(root: &Path) -> Result<BTreeMap<String, String>> {
        WalkDir::new(root)
            .sort_by_file_name()
            .into_iter()
            .filter_map(|entry| match entry {
                Ok(entry) if entry.file_type().is_file() => Some(Ok(entry)),
                Ok(_) => None,
                Err(error) => Some(Err(error)),
            })
            .map(|entry| {
                let entry = entry?;
                let relative = entry
                    .path()
                    .strip_prefix(root)?
                    .to_str()
                    .ok_or_else(|| eyre::eyre!("fixture path must be UTF-8"))?
                    .replace('\\', "/");
                Ok((relative, sha256(&fs::read(entry.path())?)))
            })
            .collect()
    }

    #[test]
    #[ignore = "manual tracing capture; the normal suite covers preparation and rechecks"]
    fn profile_frozen_recipe_preparation() -> Result<()> {
        crate::logging::init_logging(
            &crate::logging::LoggingConfig::new(
                tracing::level_filters::LevelFilter::INFO,
                std::env::var_os("SFM_TEST_LOG_FILE").map(std::path::PathBuf::from),
            ),
            &crate::cancellation::CancellationToken::new(),
        )?;
        let recipe = primary_recipe()?;
        let fixture = fixture_for(&recipe)?;
        let key = Fixture::key(slot(&recipe)?, "release");
        fixture.publish(&key);
        prepare(&fixture, &key, &recipe)?.recheck(false)
    }

    #[test]
    fn twenty_owned_contexts_bind_exact_frozen_inputs_without_writes() -> Result<()> {
        use rayon::prelude::*;
        let results = recipe_index()?
            .targets
            .into_par_iter()
            .map(|recipe| -> Result<_> {
                let mut prepared_contexts = 0;
                let mut weak_refusals = 0;
                let mut identities = BTreeSet::new();
                let fixture = fixture_for(&recipe)?;
                for environment in ["release", "dev"] {
                    let key = Fixture::key(slot(&recipe)?, environment);
                    let root = fixture.publish(&key);
                    let before = snapshot(&root)?;
                    let result = prepare(&fixture, &key, &recipe);
                    if recipe.target == "26.1.2" {
                        let error = result.unwrap_err().to_string();
                        assert!(
                            error.contains("released native identity unresolved"),
                            "{error}"
                        );
                        assert!(
                            error.contains("blake3:fa5d96536fb3195b1a113d1d9e30695927d76378"),
                            "{error}"
                        );
                        weak_refusals += 1;
                    } else {
                        let prepared = result?;
                        let receipt = prepared.receipt();
                        assert_eq!(receipt.ownership.projection_key, key);
                        assert_eq!(receipt.ownership.target_id, recipe.target);
                        assert_eq!(
                            receipt.ownership.minecraft_version,
                            recipe.minecraft_version
                        );
                        assert_eq!(receipt.role_bindings.len(), ROLE_INPUT_COUNT);
                        assert_eq!(receipt.compiler_release, recipe.platform.java_major);
                        assert_eq!(receipt.jdk_identity, None);
                        assert_eq!(receipt.native_compilation, "not_performed");
                        assert_eq!(receipt.native_execution, "not_performed");
                        assert!(receipt.immutable_source_lock);
                        assert!(!receipt.prepared_inputs.live_pom_discovery);
                        if recipe.target == "1.19.4" {
                            assert_eq!(
                                receipt.released_inputs.loader_coordinate,
                                "net.minecraftforge:forge:1.19.4-45.0.9"
                            );
                            assert!(
                                prepared
                                    .dependencies()
                                    .coordinate("net.minecraftforge:forge:1.19.4-45.0.42", false)
                                    .is_err()
                            );
                        }
                        assert_eq!(
                            prepared.dependencies().raw_source_lock_bytes(),
                            fs::read(root.join(LOCK_OUTPUT))?
                        );
                        assert!(identities.insert(receipt.preparation_identity()?));
                        assert!(
                            !root
                                .join(receipt.preparation_cache_relative_path()?)
                                .exists()
                        );
                        assert!(!root.join("build").exists());
                        let portable = receipt.to_json()?;
                        assert!(portable.contains("sfm:frozen_recipe_project@1"));
                        assert!(
                            !portable
                                .contains(&fixture.repository().to_string_lossy().into_owned())
                        );
                        prepared.recheck(false)?;
                        prepared_contexts += 1;
                    }
                    assert_eq!(snapshot(&root)?, before);
                    assert!(!root.join("build").exists());
                }
                Ok((prepared_contexts, weak_refusals, identities))
            })
            .collect::<Result<Vec<_>>>()?;
        let mut prepared_contexts = 0;
        let mut weak_refusals = 0;
        let mut identities = BTreeSet::new();
        for (prepared, weak, target_identities) in results {
            prepared_contexts += prepared;
            weak_refusals += weak;
            for identity in target_identities {
                assert!(identities.insert(identity));
            }
        }
        assert_eq!((prepared_contexts, weak_refusals), (18, 2));
        assert_eq!(identities.len(), 18);
        Ok(())
    }

    #[test]
    fn named_datagen_exclusion_policy_matches_actual_frozen_absence() -> Result<()> {
        let recipe = primary_recipe()?;
        let fixture = fixture_for(&recipe)?;
        let key = Fixture::key(slot(&recipe)?, "release");
        let root = fixture.publish(&key);
        let prepared = prepare(&fixture, &key, &recipe)?;
        let before = snapshot(&root)?;
        assert!(
            !root
                .join("gradle/source-excludes/1.19.2/datagen-java.txt")
                .exists()
        );
        let policy = prepared.source_exclusion_policy("datagen")?;
        assert!(policy.patterns.is_empty());
        assert_eq!(policy.output, None);
        assert_eq!(snapshot(&root)?, before);
        Ok(())
    }

    #[test]
    fn named_datagen_exclusion_policy_ignores_unowned_physical_file() -> Result<()> {
        let recipe = primary_recipe()?;
        let fixture = fixture_for(&recipe)?;
        let key = Fixture::key(slot(&recipe)?, "release");
        let root = fixture.publish(&key);
        let prepared = prepare(&fixture, &key, &recipe)?;
        let unowned = root.join("gradle/source-excludes/1.19.2/datagen-java.txt");
        fs::write(
            &unowned,
            b"\xff{% untrusted patterns must never be read %}\n",
        )?;
        let before = snapshot(&root)?;
        let policy = prepared.source_exclusion_policy("datagen")?;
        assert!(policy.patterns.is_empty());
        assert_eq!(
            policy.output, None,
            "unowned bytes cannot enter the fingerprint"
        );
        assert_eq!(snapshot(&root)?, before);
        Ok(())
    }

    #[test]
    fn named_exclusion_policies_require_exact_owned_files_and_known_source_sets() -> Result<()> {
        let recipe = primary_recipe()?;
        let fixture = fixture_for(&recipe)?;
        let key = Fixture::key(slot(&recipe)?, "release");
        let root = fixture.publish(&key);
        let prepared = prepare(&fixture, &key, &recipe)?;
        for source_set in ["main", "test", "gametest"] {
            let relative = format!("gradle/source-excludes/1.19.2/{source_set}-java.txt");
            let policy = prepared.source_exclusion_policy(source_set)?;
            assert_eq!(policy.output.as_deref(), Some(relative.as_str()));
            assert_eq!(
                policy.patterns,
                prepared.dependencies().source_exclusions()[&relative]
            );
            let path = root.join(&relative);
            let original = fs::read(&path)?;
            fs::write(&path, b"unreviewed changed exclusion\n")?;
            assert!(prepared.source_exclusion_policy(source_set).is_err());
            fs::write(&path, original)?;
            let held = path.with_extension("temporarily-held");
            fs::rename(&path, &held)?;
            assert!(prepared.source_exclusion_policy(source_set).is_err());
            fs::rename(&held, &path)?;
        }
        for unknown in ["unknown", "generated", "../main", ""] {
            assert!(prepared.source_exclusion_policy(unknown).is_err());
        }
        Ok(())
    }

    #[test]
    fn named_exclusion_policies_refuse_relocated_authenticated_outputs() -> Result<()> {
        for source_set in ["main", "test", "gametest"] {
            let recipe = primary_recipe()?;
            let fixture = fixture_for(&recipe)?;
            let key = Fixture::key(slot(&recipe)?, "release");
            let metadata_path = fixture.repository().join(CORE_METADATA_PATH);
            let mut metadata = CoreProjectInputs::from_json(
                &fs::read_to_string(&metadata_path)?,
                &BTreeSet::from(["review_toggle".to_owned()]),
            )?;
            let canonical = format!("gradle/source-excludes/1.19.2/{source_set}-java.txt");
            let variants = metadata.project_files.get_mut(&canonical).unwrap();
            let mut relocated = variants
                .iter()
                .find(|variant| variant.when.targets.contains(&recipe.target))
                .unwrap()
                .clone();
            for variant in variants.iter_mut() {
                variant
                    .when
                    .targets
                    .retain(|target| target != &recipe.target);
            }
            variants.retain(|variant| !variant.when.targets.is_empty());
            if variants.is_empty() {
                metadata.project_files.remove(&canonical);
            }
            relocated.when.targets = vec![recipe.target.clone()];
            let relocated_output = format!("gradle/relocated-{source_set}-exclusions.txt");
            metadata
                .project_files
                .insert(relocated_output.clone(), vec![relocated]);
            fs::write(&metadata_path, facet_json::to_string_pretty(&metadata)?)?;
            let root = fixture.publish(&key);
            let prepared = prepare(&fixture, &key, &recipe)?;
            assert!(!root.join(&canonical).exists());
            assert!(root.join(&relocated_output).is_file());
            let before = snapshot(&root)?;
            assert!(
                prepared.source_exclusion_policy(source_set).is_err(),
                "a recipe map key cannot authorize an unowned fingerprint path",
            );
            assert_eq!(snapshot(&root)?, before);
        }
        Ok(())
    }

    #[test]
    fn actual_minecraft_alias_and_compiler_tool_jvm_boundaries_are_explicit() -> Result<()> {
        let expected = BTreeMap::from([
            ("1.19.2", (17, 17)),
            ("1.19.4", (17, 17)),
            ("1.20", (17, 17)),
            ("1.20.1", (17, 17)),
            ("1.20.2", (17, 21)),
            ("1.20.3", (17, 21)),
            ("1.20.4", (17, 21)),
            ("1.21.0", (21, 21)),
            ("1.21.1", (21, 21)),
            ("26.1.2", (25, 25)),
        ]);
        for recipe in recipe_index()?.targets {
            assert_eq!(
                (
                    recipe.platform.java_major,
                    compiler_and_tool_boundary(&recipe)?.1
                ),
                expected[recipe.target.as_str()]
            );
            if recipe.target == "1.21.0" {
                assert_eq!(recipe.minecraft_version, "1.21");
                let fixture = fixture_for(&recipe)?;
                let key = Fixture::key(slot(&recipe)?, "release");
                fixture.publish(&key);
                let prepared = prepare(&fixture, &key, &recipe)?;
                let outputs = prepared
                    .receipt()
                    .role_bindings
                    .values()
                    .map(|binding| binding.output.as_str())
                    .collect::<BTreeSet<_>>();
                assert!(outputs.contains("gradle/dependencies/1.21/dependencies.gradle"));
                assert!(outputs.contains("gradle/java-toolchain/1.21/java-toolchain.gradle"));
                assert!(outputs.contains("gradle.properties"));
            }
        }
        Ok(())
    }

    #[test]
    fn rendered_exclusion_binds_both_identities_and_refuses_changed_output_or_source() -> Result<()>
    {
        let recipe = primary_recipe()?;
        let mut fixture = fixture_for(&recipe)?;
        let key = Fixture::key(slot(&recipe)?, "release");
        let output = format!(
            "gradle/source-excludes/{}/main-java.txt",
            recipe.minecraft_version
        );
        let collected = fixture.collect(&key)?;
        let artifact = &collected.artifacts()[&output];
        let source = artifact
            .source_path
            .strip_prefix(&format!("{CORE_ROOT}/"))
            .ok_or_else(|| eyre::eyre!("exclusion fixture is not core-owned"))?
            .to_owned();
        let raw = std::str::from_utf8(&artifact.output_bytes)?.to_owned();
        // Both branches deliberately produce exactly the original pinned body.
        // This tests rendered ownership, not an altered recipe or permissive pin.
        let template =
            format!("{{% if features.review_toggle %}}\n{raw}{{% else %}}\n{raw}{{% endif %}}\n");
        fixture.set_project_file_for(
            &recipe.target,
            &output,
            &source,
            template.as_bytes(),
            true,
        )?;
        let root = fixture.publish(&key);
        assert_eq!(fs::read(root.join(&output))?, raw.as_bytes());
        let prepared = prepare(&fixture, &key, &recipe)?;
        let binding = &prepared.receipt().role_bindings[&format!("{CORE_ROOT}/{source}")];
        assert!(binding.declared_template);
        assert_eq!(binding.raw_sha256, sha256(raw.as_bytes()));
        assert_eq!(binding.authored_sha256, sha256(template.as_bytes()));
        assert_ne!(binding.authored_sha256, binding.raw_sha256);
        prepared.recheck(false)?;

        // Authored changes invalidate the snapshot even when the release branch
        // is unchanged. The selected output remains its original exact bytes.
        let changed_inactive = template.replacen(&raw, &format!("# inactive edit\n{raw}"), 1);
        fs::write(
            fixture.repository().join(CORE_ROOT).join(&source),
            changed_inactive.as_bytes(),
        )?;
        assert!(prepared.recheck(false).is_err());
        assert_eq!(fs::read(root.join(&output))?, raw.as_bytes());

        let mut fixture = fixture_for(&recipe)?;
        let changed_output = format!("{template}# changed output\n");
        fixture.set_project_file_for(
            &recipe.target,
            &output,
            &source,
            changed_output.as_bytes(),
            true,
        )?;
        fixture.publish(&key);
        let error = prepare(&fixture, &key, &recipe).unwrap_err().to_string();
        assert!(
            error.contains("frozen selected role bytes differ"),
            "{error}"
        );
        Ok(())
    }
    #[test]
    fn refresh_unknown_recipe_and_target_mismatch_refuse_before_recheck() -> Result<()> {
        let recipe = primary_recipe()?;
        let fixture = fixture_for(&recipe)?;
        let key = Fixture::key(slot(&recipe)?, "release");
        let root = fixture.publish(&key);
        for (recipe_id, refresh, expected) in [
            (
                recipe.recipe_id.as_str(),
                true,
                "refuses dependency refresh",
            ),
            (
                "unknown/recipe",
                false,
                "unknown explicit frozen native recipe",
            ),
            (
                "sfm:released-native-inputs/4.34.0/1.19.4@1",
                false,
                "does not match the checked target",
            ),
        ] {
            let checked = fixture.collect(&key)?.check_current()?;
            let source = root.join("src/main/java/Shared.java");
            let saved = fs::read(&source)?;
            fs::write(&source, b"changed after checked ownership\n")?;
            let result =
                prepare_frozen_recipe_project(checked, recipe_id, refresh, &BTreeMap::new());
            let error = result.unwrap_err().to_string();
            assert!(error.contains(expected), "{error}");
            assert_eq!(fs::read(&source)?, b"changed after checked ownership\n");
            fs::write(&source, saved)?;
            assert!(!root.join("build").exists());
        }
        Ok(())
    }

    #[test]
    fn matching_raw_role_hash_is_not_a_selected_source_binding() -> Result<()> {
        let recipe = primary_recipe()?;
        let mut fixture = fixture_for(&recipe)?;
        let key = Fixture::key(slot(&recipe)?, "release");
        let witness = &recipe.role_input_hashes[0];
        let initial = fixture.collect(&key)?;
        let matches = initial
            .artifacts()
            .iter()
            .filter(|(_, artifact)| artifact.source_path == witness.path)
            .collect::<Vec<_>>();
        assert_eq!(matches.len(), 1);
        let output = matches[0].0.clone();
        let bytes = matches[0].1.source_bytes.clone();
        fixture.set_project_file_for(
            &recipe.target,
            &output,
            "build/unreviewed/matching-role.gradle",
            &bytes,
            false,
        )?;
        fixture.publish(&key);
        let error = prepare(&fixture, &key, &recipe).unwrap_err().to_string();
        assert!(
            error.contains("reviewed authored role is not selected"),
            "{error}"
        );
        assert_eq!(
            sha256(&fs::read(fixture.repository().join(&witness.path))?),
            format!("sha256:{}", witness.sha256)
        );
        Ok(())
    }

    #[test]
    fn selected_lock_requires_original_authored_path_not_only_equal_bytes() -> Result<()> {
        let recipe = primary_recipe()?;
        let mut fixture = fixture_for(&recipe)?;
        let key = Fixture::key(slot(&recipe)?, "release");
        let raw = fs::read(fixture.repository().join(&recipe.source_lock.path))?;
        fixture.set_project_file_for(
            &recipe.target,
            LOCK_OUTPUT,
            "build/unreviewed/equal-lock.json",
            &raw,
            false,
        )?;
        fixture.publish(&key);
        let error = prepare(&fixture, &key, &recipe).unwrap_err().to_string();
        assert!(
            error.contains("not the selected reviewed authored input"),
            "{error}"
        );
        Ok(())
    }

    #[test]
    fn rendered_roles_and_duplicate_selected_role_outputs_are_refused() -> Result<()> {
        let recipe = primary_recipe()?;
        let key = Fixture::key(slot(&recipe)?, "release");
        for rendered in [true, false] {
            let mut fixture = fixture_for(&recipe)?;
            let initial = fixture.collect(&key)?;
            let witness = &recipe.role_input_hashes[0];
            let (output, artifact) = initial
                .artifacts()
                .iter()
                .find(|(_, artifact)| artifact.source_path == witness.path)
                .ok_or_else(|| eyre::eyre!("fixture role is absent"))?;
            let output = output.clone();
            let core_relative = artifact
                .source_path
                .strip_prefix(&format!("{CORE_ROOT}/"))
                .ok_or_else(|| eyre::eyre!("fixture role must be core-owned"))?
                .to_owned();
            let mut bytes = artifact.source_bytes.clone();
            if rendered {
                bytes.extend_from_slice(
                    b"\n{% if features.review_toggle %}\n// optional fixture line\n{% endif %}\n",
                );
            }
            fixture.set_project_file_for(
                &recipe.target,
                if rendered {
                    &output
                } else {
                    "gradle/duplicate-role.gradle"
                },
                &core_relative,
                &bytes,
                rendered,
            )?;
            fixture.publish(&key);
            let error = prepare(&fixture, &key, &recipe).unwrap_err().to_string();
            let expected = if rendered {
                "selected role input declares template rendering"
            } else {
                "reviewed authored role has ambiguous selected outputs"
            };
            assert!(error.contains(expected), "{error}");
        }
        Ok(())
    }

    #[test]
    fn template_enabled_lock_is_refused_even_when_rendered_bytes_are_identical() -> Result<()> {
        let recipe = primary_recipe()?;
        let mut fixture = fixture_for(&recipe)?;
        let key = Fixture::key(slot(&recipe)?, "release");
        let initial = fixture.collect(&key)?;
        let artifact = &initial.artifacts()[LOCK_OUTPUT];
        let source = artifact
            .source_path
            .strip_prefix(&format!("{CORE_ROOT}/"))
            .ok_or_else(|| eyre::eyre!("fixture lock must be core-owned"))?
            .to_owned();
        let raw = artifact.source_bytes.clone();
        fixture.set_project_file_for(&recipe.target, LOCK_OUTPUT, &source, &raw, true)?;
        let root = fixture.publish(&key);
        assert_eq!(fs::read(root.join(LOCK_OUTPUT))?, raw);
        let error = prepare(&fixture, &key, &recipe).unwrap_err().to_string();
        assert!(
            error.contains("selected role input declares template rendering"),
            "{error}"
        );
        Ok(())
    }

    #[test]
    fn checked_but_changed_frozen_role_and_lock_bytes_are_refused() -> Result<()> {
        let recipe = primary_recipe()?;
        let key = Fixture::key(slot(&recipe)?, "release");
        for changed_lock in [false, true] {
            let mut fixture = fixture_for(&recipe)?;
            let initial = fixture.collect(&key)?;
            let source = if changed_lock {
                recipe.source_lock.path.as_str()
            } else {
                recipe.role_input_hashes[0].path.as_str()
            };
            let (output, artifact) = initial
                .artifacts()
                .iter()
                .find(|(_, artifact)| artifact.source_path == source)
                .ok_or_else(|| eyre::eyre!("fixture role is absent"))?;
            let output = output.clone();
            let core_relative = source
                .strip_prefix(&format!("{CORE_ROOT}/"))
                .ok_or_else(|| eyre::eyre!("fixture role must be core-owned"))?;
            let mut bytes = artifact.source_bytes.clone();
            // Valid JSON whitespace changes raw source identity without
            // changing parsed schema semantics. Gradle permits whitespace too.
            bytes.extend_from_slice(b" \n");
            fixture.set_project_file_for(&recipe.target, &output, core_relative, &bytes, false)?;
            fixture.publish(&key);
            let error = prepare(&fixture, &key, &recipe).unwrap_err().to_string();
            let expected = if changed_lock {
                "source-lock bytes differ"
            } else {
                "selected role bytes differ"
            };
            assert!(error.contains(expected), "{error}");
        }
        Ok(())
    }

    #[test]
    fn preparation_and_recheck_refuse_unowned_or_changed_sources_without_repair() -> Result<()> {
        let recipe = primary_recipe()?;
        let fixture = fixture_for(&recipe)?;
        let key = Fixture::key(slot(&recipe)?, "dev");
        let root = fixture.publish(&key);
        let checked = fixture.collect(&key)?.check_current()?;
        let extra = root.join("src/main/java/Unowned.java");
        fs::write(&extra, b"class Unowned {}\n")?;
        let error =
            prepare_frozen_recipe_project(checked, &recipe.recipe_id, false, &BTreeMap::new())
                .unwrap_err()
                .to_string();
        assert!(error.contains("unowned source inputs"), "{error}");
        assert_eq!(fs::read(extra)?, b"class Unowned {}\n");

        let fixture = fixture_for(&recipe)?;
        let root = fixture.publish(&key);
        let prepared = prepare(&fixture, &key, &recipe)?;
        let source = root.join("src/main/java/Shared.java");
        fs::write(&source, b"changed after prepared receipt\n")?;
        assert!(prepared.recheck(false).is_err());
        assert!(
            prepared
                .recheck(true)
                .unwrap_err()
                .to_string()
                .contains("refuses dependency refresh")
        );
        assert_eq!(fs::read(source)?, b"changed after prepared receipt\n");
        assert!(!root.join("build").exists());
        Ok(())
    }

    #[test]
    fn source_lock_and_request_handles_stay_immutable_and_context_isolated() -> Result<()> {
        let recipe = primary_recipe()?;
        let fixture = fixture_for(&recipe)?;
        let mut receipts = Vec::new();
        for environment in ["release", "dev"] {
            let key = Fixture::key(slot(&recipe)?, environment);
            fixture.publish(&key);
            let prepared = prepare(&fixture, &key, &recipe)?;
            let shared = Arc::clone(prepared.dependencies());
            assert!(Arc::ptr_eq(&shared, prepared.dependencies()));
            shared.verify_source_lock_bytes(shared.raw_source_lock_bytes(), false)?;
            assert!(shared.verify_source_lock_bytes(b"{}", false).is_err());
            assert!(shared.coordinate("unreviewed:dependency:1", false).is_err());
            assert!(
                shared
                    .coordinate("net.minecraftforge:forge:1.19.2-43.4.0", true)
                    .is_err()
            );
            let mut changed = prepared.receipt().clone();
            changed.tool_jvm_minimum += 1;
            assert_ne!(
                prepared.receipt().preparation_identity()?,
                changed.preparation_identity()?
            );
            receipts.push(prepared.receipt().clone());
        }
        assert_eq!(receipts[0].prepared_inputs, receipts[1].prepared_inputs);
        assert_ne!(
            receipts[0].ownership.context_identity,
            receipts[1].ownership.context_identity
        );
        assert_ne!(
            receipts[0].preparation_identity()?,
            receipts[1].preparation_identity()?
        );
        Ok(())
    }

    #[test]
    fn unrelated_exact_byte_witnesses_do_not_promote_a_recipe() -> Result<()> {
        let recipe = primary_recipe()?;
        let fixture = fixture_for(&recipe)?;
        let key = Fixture::key(slot(&recipe)?, "release");
        fixture.publish(&key);
        let error = prepare_frozen_recipe_project(
            fixture.collect(&key)?.check_current()?,
            &recipe.recipe_id,
            false,
            &BTreeMap::from([(0, b"unrelated artifact bytes".to_vec())]),
        )
        .unwrap_err()
        .to_string();
        assert!(
            error.contains("outside the frozen weak-identity requirements"),
            "{error}"
        );
        Ok(())
    }

    #[test]
    fn witness_loader_runs_only_after_frozen_recipe_admission() -> Result<()> {
        let recipe = primary_recipe()?;
        let fixture = fixture_for(&recipe)?;
        let key = Fixture::key(slot(&recipe)?, "release");
        fixture.publish(&key);
        let make = || fixture.collect(&key)?.check_current();
        for (identity, refresh) in [("unknown", false), (recipe.recipe_id.as_str(), true)] {
            let called = std::cell::Cell::new(false);
            assert!(
                prepare_frozen_recipe_project_with_witness_loader(
                    make()?,
                    identity,
                    refresh,
                    |_| {
                        called.set(true);
                        Ok(BTreeMap::new())
                    }
                )
                .is_err()
            );
            assert!(!called.get());
        }
        let prepared = prepare_frozen_recipe_project_with_witness_loader(
            make()?,
            &recipe.recipe_id,
            false,
            |requirements| {
                assert!(requirements.is_empty());
                Ok(BTreeMap::new())
            },
        )?;
        prepared.recheck(false)?;
        assert!(
            prepare_frozen_recipe_project_with_witness_loader(
                make()?,
                &recipe.recipe_id,
                false,
                |_| { Ok(BTreeMap::from([(18, b"unrelated".to_vec())])) }
            )
            .is_err()
        );
        Ok(())
    }
}
