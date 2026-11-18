use crate::branch_targets::select_required_worktree_targets;
use crate::cli::jar::BranchSelector;
use crate::paths::AppHome;
use crate::paths::CacheHome;
use crate::prism::PrismLoaderSelection;
use crate::prism::loader_report_for_target;
use crate::terminal_output::stdout_line;
use facet::Facet;
use figue as args;

/// Arguments for listing Prism loader metadata for selected worktrees.
#[derive(Facet, Debug)]
pub struct LoaderListArgs {
    /// Branch selector used to choose Minecraft versions.
    #[facet(args::named)]
    pub branch: BranchSelector,

    /// Which loader version would be selected by Prism sync.
    #[facet(default = PrismLoaderSelection::Pinned, args::named)]
    pub loader: PrismLoaderSelection,
}

impl LoaderListArgs {
    /// # Errors
    ///
    /// Returns an error if build plans or Prism metadata cannot be read.
    pub fn invoke(self, app_home: &AppHome, cache_home: &CacheHome) -> eyre::Result<()> {
        let query = self.branch.into_query(app_home)?;
        let targets = select_required_worktree_targets(&query, app_home)?;
        stdout_line("branch\tmc\tloader\tpinned\trecommended\tlatest\tselected\tjava\tjava_path")?;
        for target in targets {
            let report = loader_report_for_target(&target, self.loader, cache_home)?;
            stdout_line(format!(
                "{}\t{}\t{} ({})\t{}\t{}\t{}\t{}\t{}\t{}",
                report.branch,
                report.minecraft_version,
                report.loader_name,
                report.loader_uid,
                report.pinned,
                report.recommended.as_deref().unwrap_or("-"),
                report.latest.as_deref().unwrap_or("-"),
                report.selected,
                report.java_major,
                report.java_path.display()
            ))?;
        }
        Ok(())
    }
}
