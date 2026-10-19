use super::MAX_METADATA_BYTES;
use super::Path;
use super::PreflightReport;
use super::exclusion_witness::ExclusionWitness;
use super::issue;
use super::read_input;
use super::read_pinned;
use super::role_binding::RoleBinding;
use super::role_input::RoleInput;
use super::role_selection::RoleSelection;
use super::sha256;
use super::target_recipe::TargetRecipe;
use crate::source_projection::context::ProjectionContext;
use crate::source_projection::core_inputs::CORE_ROOT;
use crate::source_projection::core_inputs::CoreSelection;
use crate::source_projection::core_inputs::render_core_input;
use crate::source_projection::core_inputs::validate_collection_selection;
use std::collections::BTreeSet;

pub(super) fn check(
    report: &mut PreflightReport,
    repo: &Path,
    recipe: &TargetRecipe,
    selected: &RoleSelection,
) -> eyre::Result<(String, Vec<RoleBinding>)> {
    let (context, selection, selection_identity) = selected.for_target(&recipe.target)?;
    eyre::ensure!(
        context.minecraft_version == recipe.minecraft_version,
        "JUnit role context Minecraft version mismatch"
    );
    validate_collection_selection(&selection, &context)?;
    let unique: BTreeSet<_> = recipe
        .role_input_hashes
        .iter()
        .map(|role| role.path.as_str())
        .collect();
    eyre::ensure!(
        unique.len() == recipe.role_input_hashes.len(),
        "duplicate recipe role input path"
    );
    let exclusions: BTreeSet<_> = recipe
        .source_excludes
        .iter()
        .map(|role| role.output.as_str())
        .collect();
    eyre::ensure!(
        recipe.source_excludes.len() == 3 && exclusions.len() == 3,
        "JUnit role exclusions must uniquely cover main/test/gametest"
    );
    let mut bindings = Vec::new();
    for role in &recipe.role_input_hashes {
        let witnesses: Vec<_> = recipe
            .source_excludes
            .iter()
            .filter(|w| w.source_path == role.path)
            .collect();
        let checked = match witnesses.as_slice() {
            [] => exact_role(report, repo, role, &selection),
            [witness] => exclusion_role(report, repo, role, witness, &selection, &context),
            _ => Err(eyre::eyre!("duplicate source-exclusion authored path")),
        };
        match checked {
            Ok(binding) => bindings.push(binding),
            Err(error) => issue(
                report,
                Some(&recipe.target),
                "test_only",
                "test_role_source_mismatch",
                format!("{error:#}"),
            ),
        }
    }
    eyre::ensure!(
        recipe
            .source_excludes
            .iter()
            .all(|w| unique.contains(w.source_path.as_str())),
        "source exclusion is not an explicit recipe role"
    );
    Ok((selection_identity, bindings))
}

fn exact_role(
    report: &mut PreflightReport,
    repo: &Path,
    role: &RoleInput,
    selection: &CoreSelection,
) -> eyre::Result<RoleBinding> {
    let selected: Vec<_> = selection
        .inputs
        .iter()
        .filter(|(_, input)| format!("{CORE_ROOT}/{}", input.input) == role.path)
        .collect();
    eyre::ensure!(
        selected.len() <= 1 && selected.iter().all(|(_, input)| !input.template),
        "unreviewed template or ambiguous selected input outside exact source-exclusion roles: {}",
        role.path
    );
    let raw = read_pinned(report, repo, &role.path, &role.sha256)?;
    Ok(RoleBinding {
        authored_input: role.path.clone(),
        selected_output: selected.first().map(|(output, _)| (*output).clone()),
        declared_template: false,
        authored_sha256: sha256(&raw),
        authored_bytes: raw.len() as u64,
        projected_sha256: sha256(&raw),
        projected_bytes: raw.len() as u64,
        recipe_role_sha256: role.sha256.clone(),
        binding_kind: "exact_recipe_input".to_owned(),
    })
}

pub(super) fn exclusion_role(
    report: &mut PreflightReport,
    repo: &Path,
    role: &RoleInput,
    witness: &ExclusionWitness,
    selection: &CoreSelection,
    context: &ProjectionContext,
) -> eyre::Result<RoleBinding> {
    validate_collection_selection(selection, context)?;
    let source_set = ["main", "test", "gametest"].into_iter().find(|set| {
        witness.output
            == format!(
                "gradle/source-excludes/{}/{set}-java.txt",
                context.minecraft_version
            )
    });
    eyre::ensure!(
        source_set.is_some() && witness.source_path == role.path && witness.sha256 == role.sha256,
        "source-exclusion role path/output/version/hash does not match the unchanged recipe: {}",
        role.path
    );
    let matches: Vec<_> = selection
        .inputs
        .iter()
        .filter(|(_, input)| format!("{CORE_ROOT}/{}", input.input) == role.path)
        .collect();
    eyre::ensure!(
        matches.len() == 1 && matches[0].0 == &witness.output,
        "source-exclusion input/output is not uniquely selected for current context: {}",
        role.path
    );
    let (output, input) = matches[0];
    if input.template {
        eyre::ensure!(
            ["standard", "shared"]
                .into_iter()
                .any(|layout| role.path == format!("{CORE_ROOT}/build/{layout}/{output}")),
            "template rendering is not permitted outside exact frozen source-exclusion paths"
        );
    }
    let raw = read_input(report, repo, &role.path, MAX_METADATA_BYTES)?;
    let artifact = render_core_input(output, input, raw, context)?;
    let projected_sha = sha256(&artifact.output_bytes);
    eyre::ensure!(
        projected_sha == role.sha256,
        "rendered source-exclusion pin mismatch for {}: expected {}, actual {projected_sha}",
        role.path,
        role.sha256
    );
    let patterns: Vec<_> = std::str::from_utf8(&artifact.output_bytes)?
        .lines()
        .map(str::trim)
        .filter(|line| !line.is_empty() && !line.starts_with('#'))
        .map(|line| line.replace('\\', "/"))
        .collect();
    eyre::ensure!(
        patterns == witness.patterns,
        "rendered source-exclusion patterns changed: {}",
        role.path
    );
    Ok(RoleBinding {
        authored_input: artifact.source_path,
        selected_output: Some(output.clone()),
        declared_template: input.template,
        authored_sha256: sha256(&artifact.source_bytes),
        authored_bytes: artifact.source_bytes.len() as u64,
        projected_sha256: projected_sha,
        projected_bytes: artifact.output_bytes.len() as u64,
        recipe_role_sha256: role.sha256.clone(),
        binding_kind: "current_selected_source_exclusion".to_owned(),
    })
}
