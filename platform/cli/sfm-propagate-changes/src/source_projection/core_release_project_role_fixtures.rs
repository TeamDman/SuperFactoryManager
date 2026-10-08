//! Test-only release project inputs from the actual selector and collector.
//! No Git fallback, generated-output reads, writes or dependency acquisition.

#[tracing::instrument(name = "frozen_recipe.fixture_roles", skip_all, fields(target))]
pub(super) fn release_project_role_outputs(
    repository: &std::path::Path,
    target: &str,
    wanted: &std::collections::BTreeSet<String>,
) -> eyre::Result<std::collections::BTreeMap<String, Vec<u8>>> {
    use super::candidate_lock::checked_file;
    use super::core_catalog::CoreCatalog;
    use super::core_inputs::CORE_METADATA_PATH;
    use super::core_inputs::CoreProjectInputs;
    use super::core_inputs::select_core_inputs;
    use super::core_slice_test_support::read_bounded;
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
    // Select against the real metadata, but acquire only the roles this
    // fixture needs. Unrelated project files are not fixture dependencies.
    let selection = select_core_inputs(&metadata, &context, &BTreeSet::new())?;
    release_project_role_outputs_selected(repository, &context, &selection, wanted)
}

pub(super) fn release_project_role_outputs_selected(
    repository: &std::path::Path,
    context: &super::context::ProjectionContext,
    selection: &super::core_inputs::CoreSelection,
    wanted: &std::collections::BTreeSet<String>,
) -> eyre::Result<std::collections::BTreeMap<String, Vec<u8>>> {
    use super::candidate_lock::checked_file;
    use super::core_inputs::CORE_ROOT;
    use super::core_inputs::MAX_CORE_FILE_BYTES;
    use super::core_inputs::render_core_input;
    use super::core_inputs::validate_collection_selection;
    use super::core_slice_test_support::read_bounded;
    use eyre::ensure;
    use std::collections::BTreeMap;

    validate_collection_selection(selection, context)?;
    let mut outputs = BTreeMap::new();
    for (output, selected) in &selection.inputs {
        let source_path = format!("{CORE_ROOT}/{}", selected.input);
        if !wanted.contains(&source_path) {
            continue;
        }
        let bytes = read_bounded(
            &checked_file(repository, &source_path)?,
            MAX_CORE_FILE_BYTES,
        )?;
        let artifact = render_core_input(output, selected, bytes, context)?;
        if selected.template {
            ensure!(
                ["main", "test", "gametest"].into_iter().any(|source_set| {
                    let expected = format!(
                        "gradle/source-excludes/{}/{source_set}-java.txt",
                        context.minecraft_version
                    );
                    output == &expected
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
