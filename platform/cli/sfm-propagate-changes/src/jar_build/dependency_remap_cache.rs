//! Windows development 1.19.2 two-output dependency producer reuse.
//! Receipts detect accidental changes under a trusted-local authority. They do
//! not authenticate a store against a writer replacing both receipt and bytes.
#[path = "dependency_remap_attempt.rs"]
mod attempt;

use super::ArtifactLock;
use super::DependencyDeobfProducer;
use super::ExecutionContext;
use super::forge_preparation_cache;
use super::forge_preparation_cache::DependencyRemapArtifactInputs;
use super::rewrite_srg_member_constants_in_jar;
use crate::jar_build::nfrt_preparation_phase::NfrtPreparationPhase;
use attempt::DependencyRemapAttempt;
use std::collections::BTreeMap;
use std::path::Path;
use std::path::PathBuf;

pub(super) fn execute(
    context: &ExecutionContext<'_>,
    shared_key: &str,
    producer: &DependencyDeobfProducer,
    mapping: &Path,
    members: &BTreeMap<String, String>,
    shared_check: &impl Fn() -> eyre::Result<()>,
    legacy: impl FnOnce() -> eyre::Result<()>,
) -> eyre::Result<()> {
    let coordinate = producer
        .artifact
        .coordinate
        .as_deref()
        .ok_or_else(|| eyre::eyre!("Resolved dependency remap has no coordinate"))?;
    let inputs = NfrtPreparationPhase::run("dependency_remap.input_admission", || {
        DependencyRemapArtifactInputs::capture(context, &producer.artifact, coordinate, shared_key)
    })?;
    let Some(inputs) = inputs else {
        return decline(coordinate, "input_capability", legacy);
    };
    let fingerprint = inputs.fingerprint();
    let key = fingerprint
        .strip_prefix("sha256:")
        .filter(|key| key.len() == 64 && key.bytes().all(|b| b.is_ascii_hexdigit()))
        .ok_or_else(|| eyre::eyre!("Invalid dependency remap key"))?;
    let base = forge_preparation_cache::preparation_root(
        &context.plan.minecraft_dir,
        context.plan.derived_cache_root.as_deref(),
    )
    .join("dependency-remap-v1");
    let Some((_verified_base, _base_ancestry)) =
        forge_preparation_cache::make_held_directory(&base)?
    else {
        return decline(coordinate, "store_ancestry", legacy);
    };
    let _lock = ArtifactLock::acquire_with_cancellation(
        base.join(format!("{key}.lock")),
        format!("dependency remap {key}"),
        context.cancellation_token.clone(),
    )?;
    let Some((key_root, _key_ancestry)) =
        forge_preparation_cache::make_held_directory(&base.join(key))?
    else {
        return decline(coordinate, "key_ancestry", legacy);
    };
    let check = || {
        shared_check()?;
        inputs.recheck(context)
    };
    check()?;
    let outputs = output_paths(producer);
    let cancellation = || context.bail_if_cancelled();
    let reused = if context.plan.refresh {
        None
    } else {
        NfrtPreparationPhase::run("dependency_remap.lookup", || {
            forge_preparation_cache::restore_entry(
                &key_root,
                fingerprint,
                &context.plan.cache_dir,
                &outputs,
                cancellation,
                "dependency_remap.materialize",
            )
        })?
    };
    check()?;
    if let Some((entry, current)) = reused {
        observe(
            producer,
            key,
            &key_root,
            Some(&entry),
            "reused",
            true,
            current,
        );
        return Ok(());
    }
    let Some(attempt) = DependencyRemapAttempt::create(&base, key)? else {
        return decline(coordinate, "workspace_ancestry", legacy);
    };
    check()?;
    NfrtPreparationPhase::run("dependency_remap.fresh", || {
        produce(context, producer, mapping, members, key, &attempt.outputs)
    })?;
    let (entry, current) = finish(
        context,
        &key_root,
        fingerprint,
        &attempt.outputs,
        &outputs,
        &check,
    )?;
    observe(
        producer,
        key,
        &key_root,
        entry.as_deref(),
        "fresh",
        entry.is_some(),
        current,
    );
    Ok(())
}

fn finish(
    context: &ExecutionContext<'_>,
    key_root: &Path,
    fingerprint: &str,
    produced: &[(&str, PathBuf)],
    outputs: &[(&str, PathBuf)],
    check: &impl Fn() -> eyre::Result<()>,
) -> eyre::Result<(Option<PathBuf>, bool)> {
    check()?;
    let cancellation = || context.bail_if_cancelled();
    let entry = NfrtPreparationPhase::run("dependency_remap.publish", || {
        forge_preparation_cache::publish_entry(key_root, fingerprint, produced, cancellation)
    })?;
    check()?;
    let current = if let Some(entry) = &entry {
        forge_preparation_cache::restore_completed(
            entry,
            fingerprint,
            &context.plan.cache_dir,
            outputs,
            &cancellation,
            "dependency_remap.materialize",
        )?
    } else {
        forge_preparation_cache::materialize_outputs(
            &context.plan.cache_dir,
            produced,
            outputs,
            &cancellation,
            "dependency_remap.materialize",
        )?
    }
    .ok_or_else(|| eyre::eyre!("Cannot materialize complete dependency remap outputs"))?;
    check()?;
    Ok((entry, current))
}

fn output_paths(producer: &DependencyDeobfProducer) -> Vec<(&'static str, PathBuf)> {
    vec![
        ("specialsource.jar", producer.specialsource_output.clone()),
        ("remapped.jar", producer.remapped.clone()),
    ]
}

fn decline(
    coordinate: &str,
    reason: &str,
    legacy: impl FnOnce() -> eyre::Result<()>,
) -> eyre::Result<()> {
    tracing::info!(
        coordinate,
        reason,
        outcome = "declined",
        "dependency_remap_cache"
    );
    legacy()
}

fn produce(
    context: &ExecutionContext<'_>,
    producer: &DependencyDeobfProducer,
    mapping: &Path,
    members: &BTreeMap<String, String>,
    key: &str,
    outputs: &[(&str, PathBuf)],
) -> eyre::Result<()> {
    context.bail_if_cancelled()?;
    let diagnostic = context
        .plan
        .cache_dir
        .join("dependencies/remap-work")
        .join(key);
    context.run_dependency_specialsource(
        &producer.artifact,
        &outputs[0].1,
        mapping,
        producer.mapping_hash(),
        &producer.coordinate,
        &diagnostic,
    )?;
    context.bail_if_cancelled()?;
    rewrite_srg_member_constants_in_jar(&outputs[0].1, &outputs[1].1, members)?;
    eyre::ensure!(
        outputs.iter().all(|(_, path)| path.is_file()),
        "Incomplete fresh dependency remap"
    );
    Ok(())
}

fn observe(
    producer: &DependencyDeobfProducer,
    key: &str,
    key_root: &Path,
    entry: Option<&Path>,
    outcome: &str,
    stored: bool,
    current: bool,
) {
    let materialization = if current { "already_current" } else { "copied" };
    let coordinate = &producer.coordinate;
    // These are already-validated metadata paths. No payload digest/log reread.
    if let Some(entry) = entry.filter(|_| outcome == "reused") {
        tracing::info!(%coordinate, key, outcome, outputs = 2,
            store_key_root = %key_root.display(), entry = %entry.display(),
            inputs_receipt = %entry.join("inputs.sha256").display(),
            outputs_manifest = %entry.join("inputs.outputs.json").display(),
            specialsource_output = %producer.specialsource_output.display(),
            remapped_output = %producer.remapped.display(), materialization,
            "dependency_remap_cache");
    } else if let Some(entry) = entry {
        tracing::info!(%coordinate, key, outcome, stored, outputs = 2,
            store_key_root = %key_root.display(), entry = %entry.display(),
            inputs_receipt = %entry.join("inputs.sha256").display(),
            outputs_manifest = %entry.join("inputs.outputs.json").display(),
            specialsource_output = %producer.specialsource_output.display(),
            remapped_output = %producer.remapped.display(), materialization,
            "dependency_remap_cache");
    } else {
        tracing::info!(%coordinate, key, outcome, stored, outputs = 2,
            store_key_root = %key_root.display(),
            specialsource_output = %producer.specialsource_output.display(),
            remapped_output = %producer.remapped.display(), materialization,
            "dependency_remap_cache");
    }
}
