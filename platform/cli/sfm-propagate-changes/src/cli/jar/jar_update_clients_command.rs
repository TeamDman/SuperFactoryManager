use crate::cli::client::load_client_targets;
use tracing::warn;

pub(super) fn invoke() -> eyre::Result<()> {
    let targets = load_client_targets()?;
    if targets.is_empty() {
        println!("No tracked clients. Use `sfm-propagate-changes client add <glob>`.");
        return Ok(());
    }

    let mut updated = 0usize;
    let mut skipped = 0usize;

    for target in targets {
        let Some(jar) = super::jar_shared::find_best_jar_for_mc_version(&target.mc_version)? else {
            warn!(
                path = %target.path.display(),
                mc_version = %target.mc_version,
                "Skipping client target: no matching jar found"
            );
            skipped += 1;
            continue;
        };

        let mods_dir = target.path.join(".minecraft").join("mods");
        super::jar_shared::update_mods_folder_with_jar(&mods_dir, &jar)?;
        updated += 1;
    }

    println!("Updated {updated} client target(s), skipped {skipped}.");
    Ok(())
}
