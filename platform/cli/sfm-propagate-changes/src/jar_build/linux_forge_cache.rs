//! Linux MCP, Forge and per-artifact remap authority. Legacy project hits are
//! deliberately outside this namespace and cannot become authenticated hits.
#[path = "forge_preparation_inputs.rs"]
mod inputs;
#[cfg(test)]
#[path = "linux_forge_performance_tests.rs"]
mod performance_tests;
#[path = "linux_forge_store.rs"]
mod store;
#[cfg(test)]
#[path = "linux_forge_engine_tests.rs"]
mod tests;
#[cfg(test)]
#[path = "linux_forge_verification_tests.rs"]
mod verification_tests;
use super::{
    ArtifactId, ArtifactPlan, ArtifactPurpose, BuildPlan, BuildProjectIdentity, ContentHash,
    ContentHashAlgorithm, DependencyDeobfProducer, ExecutionContext, MavenCoordinate,
    NamedSourceOrigin, OwnedDependencyRemapOutput, execute_forge_userdev_uncached,
    execute_mcp_config_joined_uncached, named_forge_compile_recipe_for_project,
    parse_unique_srg_member_mappings, requires_forge_dependency_deobf,
    rewrite_srg_member_constants_in_jar,
};
use crate::cancellation::CancellationToken;
use std::collections::BTreeSet;
use std::fs;
use std::io::Read as _;
use std::path::{Path, PathBuf};
use std::sync::Arc;

use inputs::{DependencyRemapArtifactInputs, ForgePreparationInputs};
use store::Store;

const MCP_ROLES: &[&str] = &["patched_sources.jar", "merged_mappings.tsrg", "renamed.jar"];
const FORGE_ROLES: &[&str] = &[
    "official_sources.jar",
    "deobfuscated_sources.jar",
    "srg_to_official.tsrg",
    "official_to_srg.tsrg",
    "dev_compile.jar",
];
const REMAP_ROLES: &[&str] = &["specialsource.jar", "remapped.jar"];

#[derive(Default)]
pub(super) struct State {
    preparations: Vec<(String, Store, ForgePreparationInputs)>,
    remap_common: Option<Arc<ForgePreparationInputs>>,
    remaps: Vec<(Store, DependencyRemapArtifactInputs)>,
}
impl std::fmt::Debug for State {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("LinuxForgeState")
            .field("preparations", &self.preparations.len())
            .field("remaps", &self.remaps.len())
            .field("remap_common", &self.remap_common.is_some())
            .finish()
    }
}
impl State {
    pub(super) fn holds(&self, path: &Path) -> eyre::Result<bool> {
        for (_, store, _) in &self.preparations {
            for role in MCP_ROLES.iter().chain(FORGE_ROLES.iter()) {
                if store.path(role).is_ok_and(|p| p == path) {
                    return Ok(true);
                }
            }
        }
        for (store, _) in &self.remaps {
            for role in REMAP_ROLES {
                if store.path(role)? == path {
                    return Ok(true);
                }
            }
        }
        Ok(false)
    }
    pub(super) fn remap_paths(&self) -> eyre::Result<BTreeSet<PathBuf>> {
        self.remaps
            .iter()
            .map(|(store, _)| store.path("remapped.jar"))
            .collect()
    }
    pub(super) fn source_origin(&self) -> eyre::Result<NamedSourceOrigin> {
        let (_, cache, _) = self
            .preparations
            .iter()
            .find(|(k, _, _)| k == "forge")
            .ok_or_else(|| eyre::eyre!("Forge sources lost held producer"))?;
        Ok(if cache.fresh {
            NamedSourceOrigin::FreshOwnedExecution
        } else {
            NamedSourceOrigin::HistoricalLocalCache
        })
    }
    pub(super) fn source_tree(&self, cancel: &CancellationToken) -> eyre::Result<PathBuf> {
        self.preparations
            .iter()
            .find(|(k, _, _)| k == "forge")
            .ok_or_else(|| eyre::eyre!("Forge source archive not held"))?
            .1
            .materialize_sources(cancel)
    }
    pub(super) fn ready_for_compiler(&self) -> bool {
        ["mcp", "forge"]
            .iter()
            .all(|kind| self.preparations.iter().any(|(k, _, _)| k == kind))
    }
    pub(super) fn recheck(&self, context: &ExecutionContext<'_>) -> eyre::Result<()> {
        context.bail_if_cancelled()?;
        context.plan.recheck_named_inputs()?;
        for (_, cache, inputs) in &self.preparations {
            inputs.recheck_payload(context)?;
            cache.recheck(&context.cancellation_token)?;
        }
        if let Some(common) = &self.remap_common {
            common.recheck_payload(context)?;
        }
        for (cache, artifact) in &self.remaps {
            artifact.recheck(context)?;
            cache.recheck(&context.cancellation_token)?;
        }
        context
            .held_forge_sdk_image()?
            .recheck(&context.cancellation_token)?;
        context.bail_if_cancelled()?;
        context.plan.recheck_named_inputs()?;
        context.bail_if_cancelled()
    }
    pub(super) fn path(&self, kind: &str, role: &str) -> eyre::Result<Option<PathBuf>> {
        self.preparations
            .iter()
            .find(|(k, _, _)| k == kind)
            .map(|(_, cache, _)| cache.path(role))
            .transpose()
    }
}

pub(super) fn eligible(plan: &BuildPlan) -> bool {
    matches!(plan.identity, BuildProjectIdentity::Catalog { .. })
        && plan
            .named_project()
            .is_some_and(|p| named_forge_compile_recipe_for_project(p).is_ok())
        && plan.derived_cache_root.is_some()
}

pub(super) fn prepare(
    context: &ExecutionContext<'_>,
    kind: &str,
    existing_only: bool,
) -> eyre::Result<()> {
    eyre::ensure!(
        eligible(context.plan) && super::super::local_dependency_cache::environment_allows_reuse(),
        "Linux Forge cache requires reviewed released inputs and an unmodified Java environment"
    );
    eyre::ensure!(
        !context.plan.refresh && !context.plan.rebuild_shared_mcp,
        "immutable Linux Forge generations do not accept refresh or forced rebuild overrides"
    );
    {
        let state = context
            .linux_forge
            .lock()
            .map_err(|_poisoned| eyre::eyre!("Linux Forge state poisoned"))?;
        if state.preparations.iter().any(|(k, _, _)| k == kind) {
            return state.recheck(context);
        }
    }
    let inputs = ForgePreparationInputs::prepare(context, kind)?.ok_or_else(|| {
        eyre::eyre!("Linux preparation input authority unavailable; no legacy fallback")
    })?;
    let roles = if kind == "mcp" {
        MCP_ROLES
    } else {
        FORGE_ROLES
    };
    let mut cache = Store::begin(
        context
            .plan
            .derived_cache_root
            .as_deref()
            .ok_or_else(|| eyre::eyre!("missing managed root"))?,
        kind,
        inputs.fingerprint(),
        roles,
        &context.cancellation_token,
    )?;
    let reused = cache.lookup(&context.cancellation_token)?;
    if !reused {
        eyre::ensure!(
            !existing_only && !context.plan.refresh && !context.plan.rebuild_shared_mcp,
            "verified Linux preparation absent; fresh compile required (refresh never replaces entries)"
        );
        cache.produce(
            &context.cancellation_token,
            |attempt| produce_preparation(context, kind, attempt),
            || {
                inputs.recheck(context)?;
                context.recheck_compiler_image()
            },
        )?;
    }
    inputs.recheck(context)?;
    cache.recheck(&context.cancellation_token)?;
    cache.release_producer();
    tracing::info!(
        kind,
        key = inputs.fingerprint(),
        dependency_origin = if reused {
            "historical_local_cache"
        } else {
            "fresh_owned_execution"
        },
        "linux_forge_dependency_preparation"
    );
    context
        .linux_forge
        .lock()
        .map_err(|_poisoned| eyre::eyre!("Linux Forge state poisoned"))?
        .preparations
        .push((kind.to_owned(), cache, inputs));
    Ok(())
}

fn remap(
    context: &ExecutionContext<'_>,
    common: &ForgePreparationInputs,
    artifact: &ArtifactPlan,
    coordinate: &MavenCoordinate,
    existing_only: bool,
) -> eyre::Result<PathBuf> {
    let mapping = context.forge_mapping_path("srg_to_official.tsrg");
    let input = DependencyRemapArtifactInputs::capture(
        context,
        artifact,
        &coordinate.to_string(),
        common.fingerprint(),
    )?
    .ok_or_else(|| eyre::eyre!("Linux remap artifact authority unavailable"))?;
    let mut cache = Store::begin(
        context
            .plan
            .derived_cache_root
            .as_deref()
            .ok_or_else(|| eyre::eyre!("missing managed root"))?,
        "remap",
        input.fingerprint(),
        REMAP_ROLES,
        &context.cancellation_token,
    )?;
    let reused = cache.lookup(&context.cancellation_token)?;
    if !reused {
        eyre::ensure!(
            !existing_only && !context.plan.refresh,
            "verified Linux remap absent; compile required"
        );
        let raw = common.mapping_bytes();
        let mapping_hash = ContentHash::from_bytes(raw, ContentHashAlgorithm::Blake3);
        let members = parse_unique_srg_member_mappings(std::str::from_utf8(raw)?);
        cache.produce(
            &context.cancellation_token,
            |attempt| {
                let intermediate = attempt.path.join("specialsource.jar");
                let output = attempt.path.join("remapped.jar");
                context.run_dependency_specialsource_selected(
                    artifact,
                    &intermediate,
                    &mapping,
                    mapping_hash,
                    coordinate,
                    &attempt.path.join("diagnostics"),
                    Some(&OwnedDependencyRemapOutput { directory: attempt }),
                )?;
                verify_remap_jar(&intermediate, &context.cancellation_token)?;
                rewrite_srg_member_constants_in_jar(&intermediate, &output, &members)?;
                verify_remap_jar(&output, &context.cancellation_token)?;
                Ok(vec![intermediate, output])
            },
            || {
                common.recheck(context)?;
                input.recheck(context)
            },
        )?;
    }
    // Common source/SDK/tool/mapping authority brackets the entire lookup
    // batch. Fresh producer effects additionally check it around each child
    // and publication; earlier remap outputs are not inputs to this producer.
    input.recheck(context)?;
    cache.recheck(&context.cancellation_token)?;
    let path = cache.path("remapped.jar")?;
    verify_remap_jar(
        &cache.path("specialsource.jar")?,
        &context.cancellation_token,
    )?;
    verify_remap_jar(&path, &context.cancellation_token)?;
    cache.recheck(&context.cancellation_token)?;
    cache.release_producer();
    tracing::info!(coordinate = %coordinate, key = input.fingerprint(), dependency_origin = if reused { "historical_local_cache" } else { "fresh_owned_execution" }, "linux_forge_dependency_remap");
    context
        .linux_forge
        .lock()
        .map_err(|_poisoned| eyre::eyre!("Linux Forge state poisoned"))?
        .remaps
        .push((cache, input));
    Ok(path)
}

/// Paths are returned only after the complete current source/SDK/input/output
/// closure has been rechecked. No cached success survives across effect calls.
pub(super) fn remaps(
    context: &ExecutionContext<'_>,
    rows: &[(&ArtifactPlan, &MavenCoordinate)],
    existing_only: bool,
) -> eyre::Result<Vec<PathBuf>> {
    context.recheck_compiler_image()?;
    if rows.is_empty() {
        return Ok(Vec::new());
    }
    let common = remap_common(context)?;
    let paths = rows
        .iter()
        .map(|(artifact, coordinate)| remap(context, &common, artifact, coordinate, existing_only))
        .collect::<eyre::Result<Vec<_>>>()?;
    context.recheck_compiler_image()?;
    Ok(paths)
}

fn remap_common(context: &ExecutionContext<'_>) -> eyre::Result<Arc<ForgePreparationInputs>> {
    if let Some(common) = context
        .linux_forge
        .lock()
        .map_err(|_poisoned| eyre::eyre!("Linux Forge state poisoned"))?
        .remap_common
        .as_ref()
    {
        return Ok(Arc::clone(common));
    }
    let mapping = context.forge_mapping_path("srg_to_official.tsrg");
    let common = Arc::new(
        ForgePreparationInputs::prepare_remap(context, &mapping)?
            .ok_or_else(|| eyre::eyre!("Linux remap common authority unavailable"))?,
    );
    bind_remap_common(context, common)
}

fn bind_remap_common(
    context: &ExecutionContext<'_>,
    common: Arc<ForgePreparationInputs>,
) -> eyre::Result<Arc<ForgePreparationInputs>> {
    context
        .linux_forge
        .lock()
        .map_err(|_poisoned| eyre::eyre!("Linux Forge state poisoned"))?
        .remap_common = Some(Arc::clone(&common));
    // Capturing a derived mapping must not bless bytes changed since the
    // initial Forge store check. Seal both retained owners before any remap
    // attempt or child can be created, then hold them through all effects.
    context.recheck_compiler_image()?;
    Ok(common)
}

/// A fresh remap consumes this common authority, not all previously produced
/// remaps. Keep source, full SDK/trust, exact mapping/tool and lock checks here.
pub(super) fn recheck_remap_inputs(context: &ExecutionContext<'_>) -> eyre::Result<()> {
    context.bail_if_cancelled()?;
    let state = context
        .linux_forge
        .lock()
        .map_err(|_poisoned| eyre::eyre!("Linux Forge state poisoned"))?;
    state
        .preparations
        .iter()
        .find(|(kind, _, _)| kind == "forge")
        .ok_or_else(|| eyre::eyre!("Linux remap mapping production not held"))?
        .1
        .recheck_role("srg_to_official.tsrg", &context.cancellation_token)?;
    let common = state
        .remap_common
        .as_ref()
        .map(Arc::clone)
        .ok_or_else(|| eyre::eyre!("Linux remap common authority not held"))?;
    drop(state);
    common.recheck(context)
}

pub(super) fn dependencies(
    context: &ExecutionContext<'_>,
    existing_only: bool,
) -> eyre::Result<()> {
    let resolver = context.plan.resolver(&context.cancellation_token)?;
    let mut rows = Vec::new();
    let mapping = context.forge_mapping_path("srg_to_official.tsrg");
    let mapping_hash = ContentHash::from_path(&mapping, ContentHashAlgorithm::Blake3)?;
    for (index, dependency) in context.plan.dependencies.iter().enumerate() {
        if !requires_forge_dependency_deobf(dependency) {
            continue;
        }
        let coordinate = MavenCoordinate::parse(&dependency.resolved_notation)?;
        let artifact = resolver.resolve_artifact(
            ArtifactId::from(format!("dependency-{index}")),
            &coordinate,
            ArtifactPurpose::from(format!("{} dependency", dependency.configuration)),
        )?;
        context.assert_allowed_input(&artifact.cache_path)?;
        rows.push(DependencyDeobfProducer::new(
            index,
            coordinate,
            artifact,
            &context.plan.cache_dir.join("dependencies"),
            mapping_hash,
        )?);
    }
    let producers = DependencyDeobfProducer::select(rows)?;
    let rows = producers
        .iter()
        .map(|p| (&p.artifact, &p.coordinate))
        .collect::<Vec<_>>();
    let paths = remaps(context, &rows, existing_only)?;
    *context
        .selected_dependency_remaps
        .lock()
        .map_err(|_poisoned| eyre::eyre!("selected remaps poisoned"))? = Some(
        DependencyDeobfProducer::output_bindings_for(&producers, &paths),
    );
    context.recheck_compiler_image()
}

pub(super) fn reopen_for_consumer(context: &ExecutionContext<'_>) -> eyre::Result<()> {
    if eligible(context.plan) {
        prepare(context, "mcp", true)?;
        prepare(context, "forge", true)?;
    }
    Ok(())
}

fn produce_preparation(
    context: &ExecutionContext<'_>,
    kind: &str,
    attempt: &crate::jar_build::local_dependency_cache::linux_io::Directory,
) -> eyre::Result<Vec<PathBuf>> {
    if kind == "mcp" {
        execute_mcp_config_joined_uncached(context, false, Some(&attempt.path))?;
        let base = attempt
            .path
            .join("mcp")
            .join(context.plan.minecraft_version.as_str())
            .join("joined");
        Ok([
            "patch/joined-patched-sources.jar",
            "mergeMappings/output.tsrg",
            "rename/output.jar",
        ]
        .map(|p| base.join(p))
        .to_vec())
    } else {
        // The exact selected project AT remains held/rechecked by inputs;
        // the existing producer consumes that original checked path.
        execute_forge_userdev_uncached(context, false, None, Some(&attempt.path))?;
        let base = attempt
            .path
            .join("forge")
            .join(context.plan.minecraft_version.as_str());
        Ok([
            "sources/combined-official.jar",
            "sources/combined-deobfuscated.jar",
            "mappings/srg_to_official.tsrg",
            "mappings/official_to_srg.tsrg",
            "classes/dev-compile.jar",
        ]
        .map(|p| base.join(p))
        .to_vec())
    }
}

fn verify_remap_jar(path: &Path, cancel: &CancellationToken) -> eyre::Result<()> {
    cancel.bail_if_cancelled()?;
    let metadata = fs::symlink_metadata(path)?;
    eyre::ensure!(
        metadata.file_type().is_file() && metadata.len() <= 512 * 1024 * 1024,
        "remap output is not a bounded regular JAR"
    );
    let mut archive = zip::ZipArchive::new(fs::File::open(path)?)?;
    eyre::ensure!(
        !archive.is_empty() && archive.len() <= crate::source_archive::MAX_ENTRIES,
        "remap output has invalid entry count"
    );
    let mut total = 0u64;
    let mut buffer = vec![0; 64 * 1024];
    for index in 0..archive.len() {
        cancel.bail_if_cancelled()?;
        let mut entry = archive.by_index(index)?;
        eyre::ensure!(
            entry.size() <= crate::source_archive::MAX_ENTRY_BYTES,
            "remap entry exceeds bound"
        );
        total = total
            .checked_add(entry.size())
            .ok_or_else(|| eyre::eyre!("remap archive size overflow"))?;
        eyre::ensure!(
            total <= crate::source_archive::MAX_TOTAL_BYTES,
            "remap archive exceeds expansion bound"
        );
        let mut read = 0u64;
        loop {
            cancel.bail_if_cancelled()?;
            let count = entry.read(&mut buffer)?;
            if count == 0 {
                break;
            }
            read += u64::try_from(count)?;
            eyre::ensure!(read <= entry.size(), "remap entry grew while checking CRC");
        }
        eyre::ensure!(read == entry.size(), "remap entry is incomplete");
    }
    Ok(())
}
