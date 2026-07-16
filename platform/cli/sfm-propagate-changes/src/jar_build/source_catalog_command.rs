use super::BuildOptions;
use super::SourceCatalogQuery;
use crate::cancellation::CancellationToken;

#[derive(Debug)]
pub struct SourceCatalogCommand {
    options: BuildOptions,
    query: SourceCatalogQuery,
    cancellation_token: CancellationToken,
}

impl SourceCatalogCommand {
    #[must_use]
    pub fn new(
        options: BuildOptions,
        query: SourceCatalogQuery,
        cancellation_token: CancellationToken,
    ) -> Self {
        Self {
            options,
            query,
            cancellation_token,
        }
    }

    /// # Errors
    ///
    /// Returns an error if the selected branch cannot be planned or catalogued.
    pub fn invoke(self) -> eyre::Result<()> {
        super::engine::invoke_source_catalog(&self.options, &self.query, &self.cancellation_token)
    }
}
