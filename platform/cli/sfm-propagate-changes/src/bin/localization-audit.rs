//! Read-only projection-aware localization audit. No generated tree is written.
use eyre::Result;
use eyre::ensure;
use sfm_propagate_changes::source_projection::core_catalog::CoreCatalog;
use sfm_propagate_changes::source_projection::core_inputs::CORE_METADATA_PATH;
use sfm_propagate_changes::source_projection::core_inputs::CORE_ROOT;
use sfm_propagate_changes::source_projection::core_inputs::CoreProjectInputs;
use sfm_propagate_changes::source_projection::core_inputs::collect_core_artifacts;
use sfm_propagate_changes::source_projection::core_inputs::discover_core_source_files;
use sfm_propagate_changes::source_projection::core_inputs::select_core_inputs;
use sfm_propagate_changes::source_projection::localization_audit;
use std::path::Path;

fn main() -> Result<()> {
    let args = std::env::args().skip(1).collect::<Vec<_>>();
    if args.len() == 4 && args[0] == "--review-candidate" {
        let english = facet_json::from_str::<std::collections::BTreeMap<String, String>>(
            &std::fs::read_to_string(&args[1])?,
        )?;
        let candidate = facet_json::from_str::<std::collections::BTreeMap<String, String>>(
            &std::fs::read_to_string(&args[2])?,
        )?;
        let mut inventory = localization_audit::key_inventory::KeyInventory::default();
        for key in english.keys() {
            inventory.add(key, "English reference".into());
        }
        let report = localization_audit::compare(&inventory, &english, &candidate);
        std::fs::write(&args[3], facet_json::to_string_pretty(&report)?)?;
        println!(
            "{} missing; {} extra; {} placeholder problems",
            report.missing.len(),
            report.extra_observed.len(),
            report.placeholder_problems.len()
        );
        return Ok(());
    }
    ensure!(
        args.len() == 3,
        "usage: localization-audit <repo> <projection-key> <report.json>"
    );
    let repo = Path::new(&args[0]);
    let loaded = CoreCatalog::load(repo, repo)?;
    let context = loaded.context(&args[1])?;
    let metadata = CoreProjectInputs::from_json(
        &std::fs::read_to_string(repo.join(CORE_METADATA_PATH))?,
        &loaded.registered_features,
    )?;
    let core = repo.join(CORE_ROOT);
    let selection = select_core_inputs(&metadata, &context, &discover_core_source_files(&core)?)?;
    let artifacts = collect_core_artifacts(&core, &selection, &context)?;
    let report = localization_audit::audit(&args[1], &artifacts)?;
    std::fs::write(&args[2], facet_json::to_string_pretty(&report)?)?;
    println!(
        "{} required IDs; {} unresolved expressions; {} locales",
        report.inventory.required.len(),
        report.inventory.unresolved.len(),
        report.locales.len()
    );
    Ok(())
}
