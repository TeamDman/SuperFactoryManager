//! Test-only release project inputs from the actual selector and collector.
//! No Git fallback, generated-output reads, writes or dependency acquisition.
//! The immutable checkout's selector inputs are prepared once per test process;
//! requested role bytes are freshly read and rendered for every caller.

use super::context::ProjectionContext;
use super::core_inputs::CoreSelection;
use std::collections::BTreeMap;
use std::sync::OnceLock;

type ReleaseSelections = BTreeMap<String, (ProjectionContext, CoreSelection)>;

fn workspace_repository() -> &'static std::path::Path {
    std::path::Path::new(env!("CARGO_MANIFEST_DIR"))
        .ancestors()
        .nth(3)
        .expect("CLI test crate must be below repository")
}

#[tracing::instrument(name = "frozen_recipe.fixture_roles", skip_all, fields(target))]
pub(super) fn release_project_role_outputs(
    target: &str,
    wanted: &std::collections::BTreeSet<String>,
) -> eyre::Result<std::collections::BTreeMap<String, Vec<u8>>> {
    static SELECTIONS: OnceLock<Result<ReleaseSelections, String>> = OnceLock::new();
    let selections = SELECTIONS
        .get_or_init(|| load_workspace_selections().map_err(|error| format!("{error:?}")))
        .as_ref()
        .map_err(|error| eyre::eyre!("release role fixture setup failed: {error}"))?;
    let (context, selection) = selections
        .get(target)
        .ok_or_else(|| eyre::eyre!("unknown release role fixture target: {target}"))?;
    release_project_role_outputs_selected(workspace_repository(), context, selection, wanted)
}

fn load_workspace_selections() -> eyre::Result<ReleaseSelections> {
    use super::candidate_lock::checked_file;
    use super::core_catalog::CoreCatalog;
    use super::core_inputs::CORE_METADATA_PATH;
    use super::core_inputs::CoreProjectInputs;
    use super::core_slice_test_support::read_bounded;
    use super::projection_catalog::SUPPORTED_TARGETS;
    use std::collections::BTreeSet;

    let repository = workspace_repository();
    let catalog = CoreCatalog::load(repository, repository)?;
    let metadata = CoreProjectInputs::from_json(
        std::str::from_utf8(&read_bounded(
            &checked_file(repository, CORE_METADATA_PATH)?,
            8 * 1024 * 1024,
        )?)?,
        &catalog.registered_features,
    )?;
    // Select against the real metadata, but acquire only the roles this
    // fixture needs. Unrelated project files are not fixture dependencies.
    let selector = metadata.validated_selector(&catalog.registered_features)?;
    let mut selections = BTreeMap::new();
    for (target, _) in SUPPORTED_TARGETS {
        let context = catalog.context(&format!("sfm-4.34.0/mc-{target}"))?;
        let selection = selector.select(&context, &BTreeSet::new())?;
        selections.insert(target.to_owned(), (context, selection));
    }
    Ok(selections)
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
