use crate::cli::repo_root::get_legacy_release_repo_root;
use crate::paths::AppHome;
use eyre::Result;
use eyre::WrapErr;
use eyre::bail;
use std::fs;
use std::io::ErrorKind;
use std::path::Path;

/// Observation that neither the invocation checkout nor the selected legacy
/// repository contains the projection catalog. This permits only the existing
/// legacy command routing; it grants no publication or tagging authority.
#[derive(Debug)]
pub(super) struct LegacyReleaseAdmission(());

impl LegacyReleaseAdmission {
    /// # Errors
    /// Rejects catalog checkouts before reading legacy selectors, credentials,
    /// notes or artifacts. Catalog marker presence is sufficient even when its
    /// contents are invalid. Application settings remain unchanged.
    pub(super) fn new(invocation_dir: &Path, app_home: &AppHome) -> Result<Self> {
        Self::check_checkout(invocation_dir, "invocation checkout")?;
        let configured = get_legacy_release_repo_root(app_home)?;
        Self::check_checkout(&configured, "configured legacy repository")?;
        Ok(Self(()))
    }

    fn check_checkout(directory: &Path, origin: &str) -> Result<()> {
        let directory = fs::canonicalize(directory)
            .wrap_err_with(|| format!("cannot inspect {origin} '{}'", directory.display()))?;
        Self::reject_catalog(&directory, origin)?;
        if let Ok(repository) = gix::discover(&directory) {
            if let Some(workdir) = repository.workdir() {
                Self::reject_catalog(workdir, origin)?;
            }
        } else {
            // A malformed or inaccessible Git marker must not hide a catalog
            // above the invocation directory. Non-Git legacy roots remain
            // eligible for the legacy command's own repository validation.
            for ancestor in directory.ancestors().skip(1) {
                Self::reject_catalog(ancestor, origin)?;
            }
        }
        Ok(())
    }

    fn reject_catalog(root: &Path, origin: &str) -> Result<()> {
        let marker = root.join("platform/minecraft/projections.json");
        match fs::symlink_metadata(&marker) {
            Ok(_) => bail!(
                "legacy branch/global-JAR release commands are unavailable in the {origin} '{}': \
                 this checkout uses projections; use `source release plan --projection <key> \
                 --expected-mod-version <version>` for catalog preparation; nothing was published or tagged",
                root.display()
            ),
            Err(error) if error.kind() == ErrorKind::NotFound => Ok(()),
            Err(error) => Err(error)
                .wrap_err_with(|| format!("cannot inspect catalog marker '{}'", marker.display())),
        }
    }
}
