//! Test-only release project inputs from the actual selector and collector.
//! No Git fallback, generated-output reads, writes or dependency acquisition.

pub(super) fn release_project_role_outputs(
    repository: &std::path::Path,
    target: &str,
    wanted: &std::collections::BTreeSet<String>,
) -> eyre::Result<std::collections::BTreeMap<String, Vec<u8>>> {
    use super::candidate_lock::checked_file;
    use super::core_catalog::CoreCatalog;
    use super::core_inputs::CORE_METADATA_PATH;
    use super::core_inputs::CORE_ROOT;
    use super::core_inputs::CoreProjectInputs;
    use super::core_inputs::collect_core_artifacts;
    use super::core_inputs::select_core_inputs;
    use super::core_slice_test_support::read_bounded;
    use eyre::ensure;
    use std::collections::BTreeMap;
    use std::collections::BTreeSet;

    let catalog = CoreCatalog::load(repository, repository)?;
    let context = catalog.context(&format!("sfm-4.34.0/mc-{target}"))?;
    let metadata = CoreProjectInputs::from_json(
        std::str::from_utf8(&read_bounded(
            &checked_file(repository, CORE_METADATA_PATH)?,
            8 * 1024 * 1024,
        )?)?,
        &catalog.registered_features,
    )?;
    // An empty Java/resource inventory selects only the declared project files.
    // The collector still validates the complete standalone project boundary.
    let selection = select_core_inputs(&metadata, &context, &BTreeSet::new())?;
    let artifacts = collect_core_artifacts(&repository.join(CORE_ROOT), &selection, &context)?;
    let mut outputs = BTreeMap::new();
    for (output, artifact) in artifacts {
        if !wanted.contains(&artifact.source_path) {
            continue;
        }
        let selected = &selection.inputs[&output];
        if selected.template {
            ensure!(
                ["main", "test", "gametest"].into_iter().any(|source_set| {
                    let expected = format!(
                        "gradle/source-excludes/{}/{source_set}-java.txt",
                        context.minecraft_version
                    );
                    output == expected
                        && ["standard", "shared"].into_iter().any(|layout| {
                            artifact.source_path == format!("{CORE_ROOT}/build/{layout}/{expected}")
                        })
                }),
                "release recipe fixture cannot render an arbitrary project role: {output}"
            );
        } else {
            ensure!(
                artifact.source_bytes == artifact.output_bytes,
                "release recipe fixture lost exact-copy project input: {output}"
            );
        }
        ensure!(
            outputs
                .insert(artifact.source_path, artifact.output_bytes)
                .is_none(),
            "release project fixture has an ambiguous authored input"
        );
    }
    ensure!(
        outputs.len() == wanted.len(),
        "release recipe fixture has unselected requested inputs"
    );
    Ok(outputs)
}
