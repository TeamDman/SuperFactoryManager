use crate::cli::server::load_server_targets;
use facet::Facet;
use tracing::info;
use tracing::warn;

/// Arguments for updating tracked server mods folders.
#[derive(Facet, Debug)]
pub struct JarUpdateServersArgs;

impl JarUpdateServersArgs {
    /// # Errors
    ///
    /// Returns an error if tracked servers or jar files cannot be updated.
    pub fn invoke(self) -> eyre::Result<()> {
        let targets = load_server_targets()?;
        if targets.is_empty() {
            info!("No tracked servers. Use `sfm-propagate-changes server add <glob>`.");
            return Ok(());
        }

        let mut updated = 0usize;
        let mut skipped = 0usize;

        for target in targets {
            let Some(jar) = super::jar_shared::find_best_jar_for_mc_version(&target.mc_version)?
            else {
                warn!(
                    path = %target.path.display(),
                    mc_version = %target.mc_version,
                    "Skipping server target: no matching jar found"
                );
                skipped += 1;
                continue;
            };

            let mods_dir = target.path.join("mods");
            super::jar_shared::update_mods_folder_with_jar(&mods_dir, &jar)?;
            updated += 1;
        }

        info!("Updated {updated} server target(s), skipped {skipped}.");
        Ok(())
    }
}
