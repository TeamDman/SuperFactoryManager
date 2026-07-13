use super::dependency_context::load_inventory;
use crate::cancellation::CancellationToken;
use crate::cli::jar::BranchSelector;
use crate::paths::CacheHome;
use crate::terminal_output::stdout_line;
use facet::Facet;
use figue as args;

#[derive(Facet, Debug)]
pub struct DependencySourceProviderListArgs {
    /// Optional dependency or dependency/component filter.
    #[facet(default, args::positional)]
    pub target: Option<String>,
    /// Branch selector to read. Must match exactly one worktree.
    #[facet(args::named)]
    pub branch: BranchSelector,
}

impl DependencySourceProviderListArgs {
    pub(crate) fn invoke(
        self,
        cancellation_token: &CancellationToken,
        cache_home: &CacheHome,
    ) -> eyre::Result<()> {
        cancellation_token.bail_if_cancelled()?;
        let inventory = load_inventory(self.branch, cache_home)?;
        let mut rows = 0;
        for dependency in inventory.dependencies() {
            for component in &dependency.components {
                let identity = format!("{}/{}", dependency.id, component.id);
                if self
                    .target
                    .as_ref()
                    .is_some_and(|target| target != &dependency.id && target != &identity)
                {
                    continue;
                }
                for provider in inventory.source_providers(component) {
                    rows += 1;
                    let status = provider.status();
                    stdout_line(format!(
                        "{} | {} | {} | priority={} | any={} | locked=yes | status={} | roots={} | unavailable={}",
                        identity,
                        provider.id(),
                        provider.kind().label(),
                        provider.priority(),
                        if provider.priority() == 0 {
                            "selected"
                        } else {
                            "fallback"
                        },
                        status.label(),
                        provider
                            .searchable_roots()
                            .iter()
                            .map(|root| root.display().to_string())
                            .collect::<Vec<_>>()
                            .join(","),
                        provider.unavailable_reason().unwrap_or("-")
                    ))?;
                }
            }
        }
        if rows == 0 {
            stdout_line("No configured source providers matched.")?;
        }
        Ok(())
    }
}
