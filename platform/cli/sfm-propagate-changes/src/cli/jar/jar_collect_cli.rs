use crate::cli::jar::get_jar_dir;
use crate::worktree::get_sorted_worktrees;
use eyre::Context;
use facet::Facet;
use tracing::info;
use tracing::warn;

/// Arguments for collecting release jars into the configured jar directory.
#[derive(Facet, Debug)]
pub struct JarCollectArgs;

impl JarCollectArgs {
    /// # Errors
    ///
    /// Returns an error if the jar directory or worktree jars cannot be read or updated.
    pub fn invoke(self) -> eyre::Result<()> {
        let jar_dir = get_jar_dir()?;
        std::fs::create_dir_all(&jar_dir)?;

        let worktrees = get_sorted_worktrees()?;

        if worktrees.is_empty() {
            info!("No worktrees found.");
            return Ok(());
        }

        let mut copied = 0usize;
        let mut skipped = 0usize;

        for wt in worktrees {
            let gradle_properties = wt
                .path
                .join("platform")
                .join("minecraft")
                .join("gradle.properties");
            let libs_dir = wt
                .path
                .join("platform")
                .join("minecraft")
                .join("build")
                .join("libs");

            if !gradle_properties.exists() {
                warn!(
                    branch = %wt.branch,
                    path = %gradle_properties.display(),
                    "Skipping worktree: missing gradle.properties"
                );
                skipped += 1;
                continue;
            }

            if !libs_dir.exists() {
                warn!(
                    branch = %wt.branch,
                    path = %libs_dir.display(),
                    "Skipping worktree: missing build/libs"
                );
                skipped += 1;
                continue;
            }

            let mod_version = super::jar_shared::read_mod_version(&gradle_properties)
                .wrap_err_with(|| {
                    format!(
                        "Failed to read mod_version for branch {} from {}",
                        wt.branch,
                        gradle_properties.display()
                    )
                })?;

            let Some(jar) = super::jar_shared::pick_jar_for_mod_version(&libs_dir, &mod_version)?
            else {
                warn!(
                    branch = %wt.branch,
                    mod_version = %mod_version,
                    path = %libs_dir.display(),
                    "Skipping worktree: no matching jar for mod_version"
                );
                skipped += 1;
                continue;
            };

            let destination = jar_dir.join(
                jar.file_name()
                    .ok_or_else(|| eyre::eyre!("Jar filename missing: {}", jar.display()))?,
            );

            std::fs::copy(&jar, &destination).wrap_err_with(|| {
                format!(
                    "Failed to copy jar from {} to {}",
                    jar.display(),
                    destination.display()
                )
            })?;

            info!(
                branch = %wt.branch,
                mod_version = %mod_version,
                source = %jar.display(),
                dest = %destination.display(),
                "Collected jar"
            );
            copied += 1;
        }

        info!(
            "Collected {copied} jar(s) into {} (skipped {skipped}).",
            jar_dir.display()
        );

        Ok(())
    }
}
