//! Trusted-local reuse of completed Forge dependency transformations.
//! Project namespaces, source checks and runtime/JAR consumers remain unchanged.
//! Entries are immutable once recorded. Incomplete/damaged entries are retained;
//! a later successful production gets another bounded entry, never an overwrite.
#[path = "held_forge_at_transport.rs"]
mod at_transport;
#[path = "held_forge_preparation_outputs.rs"]
mod held_outputs;
#[path = "forge_preparation_inputs.rs"]
mod inputs;
#[cfg(test)]
#[path = "dependency_remap_cache_tests.rs"]
mod remap_tests;
#[cfg(test)]
#[path = "forge_preparation_cache_tests.rs"]
mod tests;

use super::super::nfrt_preparation_phase::NfrtPreparationPhase;
use super::ArtifactLock;
use super::ExecutionContext;
use super::LoaderToolchainKind;
use super::project_compilation_cache;
use crate::source_projection::candidate_lock::checked_directory;
use crate::source_projection::core_input_leases::DirectoryLeases;
use crate::source_projection::core_input_leases::MAX_DIRECTORY_LEASES;
use at_transport::HeldForgeAtTransport;
use held_outputs::HeldForgePreparationOutputs;
pub(super) use inputs::DependencyRemapArtifactInputs;
pub(super) use inputs::ForgePreparationInputs;
use std::fs;
use std::path::Path;
use std::path::PathBuf;

const MAX_ENTRIES: usize = 32;

pub(super) fn eligible(context: &ExecutionContext<'_>) -> bool {
    context.plan.identity.development_target().is_some()
        && context.plan.loader_toolchain.kind == LoaderToolchainKind::ForgeGradleForge
        && context.plan.minecraft_version.as_str() == "1.19.2"
}

pub(super) fn execute(
    context: &ExecutionContext<'_>,
    kind: &str,
    legacy: impl FnOnce() -> eyre::Result<()>,
    fresh: impl FnOnce(Option<&Path>) -> eyre::Result<()>,
) -> eyre::Result<()> {
    let inputs = NfrtPreparationPhase::run("forge_preparation.input_admission", || {
        ForgePreparationInputs::prepare(context, kind)
    })?;
    let Some(inputs) = inputs else {
        tracing::info!(
            kind,
            reason = "input_capability",
            "forge_preparation_cache_declined"
        );
        return legacy();
    };
    let Some((base, _base_ancestry)) = store_base(context, kind)? else {
        tracing::info!(
            kind,
            reason = "store_ancestry",
            "forge_preparation_cache_declined"
        );
        return legacy();
    };
    let fingerprint = inputs.fingerprint();
    let key = fingerprint.trim_start_matches("sha256:");
    eyre::ensure!(
        key.len() == 64 && key.bytes().all(|b| b.is_ascii_hexdigit()),
        "Invalid preparation key"
    );
    let _lock = ArtifactLock::acquire_with_cancellation(
        base.join(format!("{key}.lock")),
        format!("{kind} dependency preparation {key}"),
        context.cancellation_token.clone(),
    )?;
    inputs.recheck(context)?;
    let Some((key_root, _key_ancestry)) = make_held_directory(&base.join(key))? else {
        tracing::info!(
            kind,
            reason = "key_ancestry",
            "forge_preparation_cache_declined"
        );
        return legacy();
    };
    let outputs = output_paths(context, kind)?;
    let reused = !context.plan.refresh
        && NfrtPreparationPhase::run("forge_preparation.reuse", || {
            restore(
                &key_root,
                fingerprint,
                &context.plan.cache_dir,
                &outputs,
                || context.bail_if_cancelled(),
            )
        })?;
    inputs.recheck(context)?;
    if reused {
        tracing::info!(
            kind,
            key,
            outcome = "reused",
            "forge_dependency_preparation"
        );
        return write_cached_node(context, kind, &outputs);
    }
    // Never turn an old existence-only project hit into a new validated entry.
    // This route deliberately invokes the original producer with that hit disabled.
    let at = if let Some(bytes) = inputs.at_bytes() {
        let Some(at) = HeldForgeAtTransport::capture(bytes)? else {
            tracing::info!(
                kind,
                reason = "at_transport",
                "forge_preparation_cache_declined"
            );
            return legacy();
        };
        Some(at)
    } else {
        None
    };
    inputs.recheck(context)?;
    NfrtPreparationPhase::run("forge_preparation.fresh", || {
        fresh(at.as_ref().map(HeldForgeAtTransport::path))
    })?;
    inputs.recheck(context)?;
    let stored = NfrtPreparationPhase::run("forge_preparation.publish", || {
        publish(&key_root, fingerprint, &outputs, || {
            context.bail_if_cancelled()
        })
    })?;
    inputs.recheck(context)?;
    tracing::info!(
        kind,
        key,
        outcome = "fresh",
        stored,
        "forge_dependency_preparation"
    );
    Ok(())
}

fn store_base(
    context: &ExecutionContext<'_>,
    kind: &str,
) -> eyre::Result<Option<(PathBuf, DirectoryLeases)>> {
    eyre::ensure!(
        matches!(kind, "mcp" | "forge"),
        "Unsupported preparation kind"
    );
    let root = preparation_root(
        &context.plan.minecraft_dir,
        context.plan.derived_cache_root.as_deref(),
    );
    context.plan.recheck_named_inputs()?;
    context.plan.recheck_named_sdk()?;
    make_held_directory(&root.join("forge-preparation-v1").join(kind))
}

fn output_paths(
    context: &ExecutionContext<'_>,
    kind: &str,
) -> eyre::Result<Vec<(&'static str, PathBuf)>> {
    let mc = context.plan.minecraft_version.as_str();
    let root = &context.plan.cache_dir;
    Ok(match kind {
        "mcp" => {
            let root = root.join("mcp").join(mc).join("joined");
            vec![
                (
                    "patched_sources.jar",
                    root.join("patch/joined-patched-sources.jar"),
                ),
                (
                    "merged_mappings.tsrg",
                    root.join("mergeMappings/output.tsrg"),
                ),
                ("renamed.jar", root.join("rename/output.jar")),
            ]
        }
        "forge" => {
            let root = root.join("forge").join(mc);
            vec![
                (
                    "official_sources.jar",
                    root.join("sources/combined-official.jar"),
                ),
                (
                    "deobfuscated_sources.jar",
                    root.join("sources/combined-deobfuscated.jar"),
                ),
                (
                    "srg_to_official.tsrg",
                    root.join("mappings/srg_to_official.tsrg"),
                ),
                (
                    "official_to_srg.tsrg",
                    root.join("mappings/official_to_srg.tsrg"),
                ),
                ("dev_compile.jar", root.join("classes/dev-compile.jar")),
            ]
        }
        _ => eyre::bail!("Unsupported preparation kind"),
    })
}

fn write_cached_node(
    context: &ExecutionContext<'_>,
    kind: &str,
    outputs: &[(&str, PathBuf)],
) -> eyre::Result<()> {
    let id = if kind == "mcp" {
        "execute-mcp-config-joined"
    } else {
        "execute-forge-userdev"
    };
    context.write_node_state(
        id,
        &["verified local dependency production"],
        &outputs
            .iter()
            .map(|(_, path)| path.clone())
            .collect::<Vec<_>>(),
        "cached",
    )
}

fn entry_directories(key_root: &Path) -> eyre::Result<Option<Vec<PathBuf>>> {
    checked_directory(key_root)?;
    let mut entries = Vec::new();
    for item in fs::read_dir(key_root)? {
        let item = item?;
        let name = item.file_name();
        let Some(name) = name.to_str() else {
            return Ok(None);
        };
        if !name.starts_with("entry-")
            || name.len() > 96
            || name.len() <= 6
            || !name[6..]
                .bytes()
                .all(|b| b.is_ascii_alphanumeric() || b == b'-')
        {
            return Ok(None);
        }
        let path = item.path();
        checked_directory(&path)?;
        entries.push(path);
        if entries.len() > MAX_ENTRIES {
            return Ok(None);
        }
    }
    entries.sort();
    Ok(Some(entries))
}

pub(super) fn restore(
    key_root: &Path,
    fingerprint: &str,
    project: &Path,
    outputs: &[(&str, PathBuf)],
    check: impl Fn() -> eyre::Result<()>,
) -> eyre::Result<bool> {
    Ok(restore_entry(
        key_root,
        fingerprint,
        project,
        outputs,
        check,
        "forge_preparation.materialize",
    )?
    .is_some())
}

pub(super) fn restore_entry(
    key_root: &Path,
    fingerprint: &str,
    project: &Path,
    outputs: &[(&str, PathBuf)],
    check: impl Fn() -> eyre::Result<()>,
    phase: &'static str,
) -> eyre::Result<Option<(PathBuf, bool)>> {
    check()?;
    let Some(entries) = entry_directories(key_root)? else {
        return Ok(None);
    };
    for entry in entries {
        if let Some(current) =
            restore_completed(&entry, fingerprint, project, outputs, &check, phase)?
        {
            return Ok(Some((entry, current)));
        }
    }
    Ok(None)
}

// A fresh producer materializes its exact completed entry, rather than selecting
// a prior same-key history entry. Both payload leaves are admitted before verify.
pub(super) fn restore_completed(
    entry: &Path,
    fingerprint: &str,
    project: &Path,
    outputs: &[(&str, PathBuf)],
    check: &impl Fn() -> eyre::Result<()>,
    phase: &'static str,
) -> eyre::Result<Option<bool>> {
    check()?;
    let payload = entry.join("payload");
    let held = match HeldForgePreparationOutputs::open(
        &payload,
        outputs.iter().map(|(name, _)| *name),
        check,
    ) {
        Ok(Some(held)) => held,
        Ok(None) => return Ok(None),
        Err(error) => {
            check()?;
            tracing::debug!(%error, "Dependency preparation export admission miss");
            return Ok(None);
        }
    };
    if !project_compilation_cache::matches(
        entry,
        &entry.join("inputs.sha256"),
        fingerprint,
        &[("payload", &payload)],
        check,
    )? {
        return Ok(None);
    }
    NfrtPreparationPhase::run(phase, || copy_or_compare(&held, project, outputs, check))
}

pub(super) fn publish(
    key_root: &Path,
    fingerprint: &str,
    outputs: &[(&str, PathBuf)],
    check: impl Fn() -> eyre::Result<()>,
) -> eyre::Result<bool> {
    Ok(publish_entry(key_root, fingerprint, outputs, check)?.is_some())
}

pub(super) fn publish_entry(
    key_root: &Path,
    fingerprint: &str,
    outputs: &[(&str, PathBuf)],
    check: impl Fn() -> eyre::Result<()>,
) -> eyre::Result<Option<PathBuf>> {
    check()?;
    // Retained history is bounded; no old evidence is removed to make room.
    let Some(entries) = entry_directories(key_root)? else {
        return Ok(None);
    };
    if entries.len() >= MAX_ENTRIES {
        return Ok(None);
    }
    let Some(held) = HeldForgePreparationOutputs::open_paths(outputs, &check)? else {
        return Ok(None);
    };
    check()?;
    let entry = tempfile::Builder::new()
        .prefix("entry-")
        .tempdir_in(key_root)?
        .keep();
    let Some((entry, _entry_ancestry)) = make_held_directory(&entry)? else {
        return Ok(None);
    };
    let payload = make_directory(&entry.join("payload"))?;
    let destinations = outputs
        .iter()
        .map(|(name, _)| (*name, payload.join(name)))
        .collect::<Vec<_>>();
    if !held.copy_to(&entry, &destinations, &check)? {
        return Ok(None);
    }
    let copied = HeldForgePreparationOutputs::open_paths(&destinations, &check)?
        .ok_or_else(|| eyre::eyre!("Cannot hold recorded dependency outputs"))?;
    eyre::ensure!(
        held.content_matches(&copied, &check)?,
        "Recorded dependency bytes differ"
    );
    project_compilation_cache::record(
        &entry,
        &entry.join("inputs.sha256"),
        fingerprint,
        &[("payload", &payload)],
        &check,
    )?;
    check()?;
    Ok(Some(entry))
}

fn make_directory(path: &Path) -> eyre::Result<PathBuf> {
    make_held_directory(path)?
        .map(|(path, _guard)| path)
        .ok_or_else(|| eyre::eyre!("Preparation directory lease unavailable"))
}

pub(super) fn make_held_directory(path: &Path) -> eyre::Result<Option<(PathBuf, DirectoryLeases)>> {
    eyre::ensure!(
        path.is_absolute()
            && !path.components().any(|c| matches!(
                c,
                std::path::Component::CurDir | std::path::Component::ParentDir
            )),
        "Unsafe preparation directory"
    );
    if path.ancestors().count() > MAX_DIRECTORY_LEASES {
        return Ok(None);
    }
    let mut missing = Vec::new();
    let mut existing = path;
    while !existing.try_exists()? {
        missing.push(existing.to_path_buf());
        existing = existing
            .parent()
            .ok_or_else(|| eyre::eyre!("No existing preparation ancestor"))?;
    }
    checked_directory(existing)?;
    let mut ancestry = DirectoryLeases::default();
    if ancestry
        .prepare_if_available(existing, "preparation-reserved-leaf")?
        .is_none()
    {
        return Ok(None);
    }
    for directory in missing.into_iter().rev() {
        match fs::create_dir(&directory) {
            Ok(()) => {}
            Err(error) if error.kind() == std::io::ErrorKind::AlreadyExists => {}
            Err(error) => return Err(error.into()),
        }
        if ancestry
            .prepare_if_available(&directory, "preparation-reserved-leaf")?
            .is_none()
        {
            return Ok(None);
        }
    }
    Ok(Some((checked_directory(path)?, ancestry)))
}

pub(super) fn preparation_root(minecraft: &Path, derived: Option<&Path>) -> PathBuf {
    derived.map_or_else(|| minecraft.join("build/sfm-toolchain"), Path::to_path_buf)
}

// Fresh workspace transport uses the same held-byte comparison as a store hit.
pub(super) fn materialize_outputs(
    project: &Path,
    produced: &[(&str, PathBuf)],
    destinations: &[(&str, PathBuf)],
    check: &impl Fn() -> eyre::Result<()>,
    phase: &'static str,
) -> eyre::Result<Option<bool>> {
    check()?;
    let Some(held) = HeldForgePreparationOutputs::open_paths(produced, check)? else {
        return Ok(None);
    };
    NfrtPreparationPhase::run(phase, || {
        copy_or_compare(&held, project, destinations, check)
    })
}

fn copy_or_compare(
    held: &HeldForgePreparationOutputs,
    project: &Path,
    destinations: &[(&str, PathBuf)],
    check: &impl Fn() -> eyre::Result<()>,
) -> eyre::Result<Option<bool>> {
    check()?;
    // Even an equal pair must be inside the selected project namespace.
    let project = checked_directory(project)?;
    for (_, path) in destinations {
        let relative = dunce::simplified(path).strip_prefix(dunce::simplified(&project))?;
        eyre::ensure!(
            !relative.as_os_str().is_empty()
                && relative
                    .components()
                    .all(|part| matches!(part, std::path::Component::Normal(_))),
            "Unsafe materialization destination"
        );
    }
    if outputs_current(held, destinations, check)? {
        return Ok(Some(true));
    }
    // outputs_current's readers are gone before the first possible writer.
    if !held.copy_to(&project, destinations, check)? {
        return Ok(None);
    }
    let copied = HeldForgePreparationOutputs::open_paths(destinations, check)?
        .ok_or_else(|| eyre::eyre!("Cannot hold materialized dependency outputs"))?;
    eyre::ensure!(
        held.content_matches(&copied, check)?,
        "Materialized dependency bytes differ"
    );
    check()?;
    Ok(Some(false))
}

fn outputs_current(
    held: &HeldForgePreparationOutputs,
    destinations: &[(&str, PathBuf)],
    check: &impl Fn() -> eyre::Result<()>,
) -> eyre::Result<bool> {
    let other = match HeldForgePreparationOutputs::open_paths(destinations, check) {
        Ok(Some(other)) => other,
        Ok(None) => return Ok(false),
        Err(error)
            if error
                .downcast_ref::<std::io::Error>()
                .is_some_and(|error| error.kind() == std::io::ErrorKind::NotFound) =>
        {
            return Ok(false);
        }
        Err(error) => return Err(error),
    };
    held.content_matches(&other, check)
}
