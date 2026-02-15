use crate::propagate;
use facet::Facet;
use figue::{self as args};

/// Options for the merge command
#[derive(Facet, Debug, Default)]
pub struct MergeCommand {
    /// Automatically abort merges that would result in conflicts. Only aborts merges
    /// that we start ourselves - will not abort pre-existing merge conflicts to avoid
    /// losing manual progress.
    #[facet(args::named)]
    pub auto_abort: bool,
}

impl MergeCommand {
    /// # Errors
    ///
    /// This function will return an error if the merge fails.
    pub fn invoke(self) -> eyre::Result<()> {
        propagate::run(propagate::PropagateOptions {
            auto_abort: self.auto_abort,
        })
    }
}
