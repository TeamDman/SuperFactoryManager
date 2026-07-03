use crate::cancellation::CancellationToken;
use crate::cli::jar::BranchSelector;
use crate::jar_build::DependencyAddCommand;
use crate::jar_build::DependencyAddOptions;
use facet::Facet;
use figue as args;
use std::path::PathBuf;

#[derive(Facet, Debug)]
pub struct DependencyAddArgs {
    /// Maven coordinate to accept, for example `mekanism:Mekanism:26.1.2-10.8.0.86`.
    #[facet(args::positional)]
    pub coordinate: String,

    /// Branch selector to update. Must match exactly one worktree.
    #[facet(default, args::named)]
    pub branch: BranchSelector,

    /// Accept byte hash drift when the jar's mod metadata matches.
    #[facet(rename = "weak-mod-metadata", default = false, args::named)]
    pub weak_mod_metadata: bool,

    /// Metadata entry to validate for weak mod metadata entries.
    #[facet(rename = "metadata-path", default, args::named)]
    pub metadata_path: Option<PathBuf>,

    /// Expected mod id for weak mod metadata entries. Defaults to the jar metadata value.
    #[facet(rename = "mod-id", default, args::named)]
    pub mod_id: Option<String>,

    /// Expected mod version for weak mod metadata entries. Defaults to the jar metadata value.
    #[facet(default, args::named)]
    pub version: Option<String>,
}

impl DependencyAddArgs {
    pub(crate) fn into_options(self) -> eyre::Result<DependencyAddOptions> {
        Ok(DependencyAddOptions {
            branch: self.branch.into_query()?,
            coordinate: self.coordinate,
            weak_mod_metadata: self.weak_mod_metadata,
            metadata_path: self.metadata_path,
            mod_id: self.mod_id,
            version: self.version,
        })
    }

    /// # Errors
    ///
    /// Returns an error if the dependency lock entry cannot be updated.
    pub fn invoke(self, cancellation_token: CancellationToken) -> eyre::Result<()> {
        DependencyAddCommand::new(self.into_options()?, cancellation_token).invoke()
    }
}
