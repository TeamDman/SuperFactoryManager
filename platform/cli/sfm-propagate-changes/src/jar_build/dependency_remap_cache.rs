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

#[tracing::instrument(level = "info", name = "execute_shared_dependency_remap", skip_all)]
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
    let consumer_outputs = selected_consumer_outputs(&outputs);
    let cancellation = || context.bail_if_cancelled();
    let reused = if context.plan.refresh {
        None
    } else {
        NfrtPreparationPhase::run("dependency_remap.lookup", || {
            forge_preparation_cache::restore_entry(
                &key_root,
                fingerprint,
                &context.plan.cache_dir,
                &consumer_outputs,
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
        &consumer_outputs,
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
    consumer_outputs: &[(&str, PathBuf)],
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
            consumer_outputs,
            &cancellation,
            "dependency_remap.materialize",
        )?
    } else {
        let consumer_produced = selected_consumer_outputs(produced);
        forge_preparation_cache::materialize_outputs(
            &context.plan.cache_dir,
            &consumer_produced,
            consumer_outputs,
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

fn selected_consumer_outputs<'a>(outputs: &[(&'a str, PathBuf)]) -> Vec<(&'a str, PathBuf)> {
    // The SpecialSource JAR is an input to the member-constant rewrite in the
    // fresh attempt. Later compile/package consumers read only remapped.jar.
    // A completed store still records and verifies both outputs before a hit.
    outputs
        .iter()
        .filter(|(name, _)| *name == "remapped.jar")
        .cloned()
        .collect()
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
        tracing::info!(%coordinate, key, outcome, stored_outputs = 2, materialized_outputs = 1,
            store_key_root = %key_root.display(), entry = %entry.display(),
            inputs_receipt = %entry.join("inputs.sha256").display(),
            outputs_manifest = %entry.join("inputs.outputs.json").display(),
            specialsource_project_path = %producer.specialsource_output.display(),
            remapped_output = %producer.remapped.display(), materialization,
            "dependency_remap_cache");
    } else if let Some(entry) = entry {
        tracing::info!(%coordinate, key, outcome, stored, stored_outputs = 2, materialized_outputs = 1,
            store_key_root = %key_root.display(), entry = %entry.display(),
            inputs_receipt = %entry.join("inputs.sha256").display(),
            outputs_manifest = %entry.join("inputs.outputs.json").display(),
            specialsource_project_path = %producer.specialsource_output.display(),
            remapped_output = %producer.remapped.display(), materialization,
            "dependency_remap_cache");
    } else {
        tracing::info!(%coordinate, key, outcome, stored, stored_outputs = 2, materialized_outputs = 1,
            store_key_root = %key_root.display(),
            specialsource_project_path = %producer.specialsource_output.display(),
            remapped_output = %producer.remapped.display(), materialization,
            "dependency_remap_cache");
    }
}

#[cfg(test)]
mod selective_tests {
    use super::*;
    use eyre::Result;
    use std::fs;

    fn fixture_dir(path: &Path) -> Result<PathBuf> {
        let (path, _lease) = forge_preparation_cache::make_held_directory(path)?
            .ok_or_else(|| eyre::eyre!("fixture directory unavailable"))?;
        Ok(path)
    }

    #[test]
    fn verified_pair_materializes_only_the_consumed_remap() -> Result<()> {
        let root = tempfile::tempdir()?;
        let producer = fixture_dir(&root.path().join("producer"))?;
        let produced = vec![
            ("specialsource.jar", producer.join("specialsource.jar")),
            ("remapped.jar", producer.join("remapped.jar")),
        ];
        fs::write(&produced[0].1, b"intermediate-only")?;
        fs::write(&produced[1].1, b"compiled-consumer")?;
        let key = fixture_dir(&root.path().join("key"))?;
        let entry = forge_preparation_cache::publish_entry(&key, "pair-fixture", &produced, || Ok(()))?
            .ok_or_else(|| eyre::eyre!("fixture pair publication unavailable"))?;
        let project = fixture_dir(&root.path().join("project"))?;
        let destinations = vec![
            ("specialsource.jar", project.join("specialsource.jar")),
            ("remapped.jar", project.join("remapped.jar")),
        ];
        let consumed = selected_consumer_outputs(&destinations);
        assert_eq!(consumed.len(), 1);
        assert_eq!(consumed[0].0, "remapped.jar");
        assert_eq!(
            forge_preparation_cache::restore_completed(
                &entry,
                "pair-fixture",
                &project,
                &consumed,
                &|| Ok(()),
                "dependency_remap.fixture_selective",
            )?,
            Some(false)
        );
        assert!(!destinations[0].1.exists());
        assert_eq!(fs::read(&destinations[1].1)?, b"compiled-consumer");

        // A partial selection must still reject corruption in the omitted
        // stored intermediate, before trusting the remapped output.
        fs::write(entry.join("payload/specialsource.jar"), b"corrupt")?;
        let second = fixture_dir(&root.path().join("second"))?;
        let second_outputs = vec![
            ("specialsource.jar", second.join("specialsource.jar")),
            ("remapped.jar", second.join("remapped.jar")),
        ];
        assert!(forge_preparation_cache::restore_completed(
            &entry,
            "pair-fixture",
            &second,
            &selected_consumer_outputs(&second_outputs),
            &|| Ok(()),
            "dependency_remap.fixture_selective",
        )?
        .is_none());
        assert!(second_outputs.iter().all(|(_, path)| !path.exists()));
        Ok(())
    }

    #[test]
    fn declined_publication_still_materializes_the_consumed_remap() -> Result<()> {
        let root = tempfile::tempdir()?;
        let producer = fixture_dir(&root.path().join("producer"))?;
        let project = fixture_dir(&root.path().join("project"))?;
        let produced = vec![
            ("specialsource.jar", producer.join("specialsource.jar")),
            ("remapped.jar", producer.join("remapped.jar")),
        ];
        fs::write(&produced[0].1, b"intermediate-only")?;
        fs::write(&produced[1].1, b"compiled-consumer")?;
        let destinations = vec![
            ("specialsource.jar", project.join("specialsource.jar")),
            ("remapped.jar", project.join("remapped.jar")),
        ];
        assert_eq!(
            forge_preparation_cache::materialize_outputs(
                &project,
                &selected_consumer_outputs(&produced),
                &selected_consumer_outputs(&destinations),
                &|| Ok(()),
                "dependency_remap.fixture_selective",
            )?,
            Some(false)
        );
        assert!(!destinations[0].1.exists());
        assert_eq!(fs::read(&destinations[1].1)?, b"compiled-consumer");
        Ok(())
    }
}
