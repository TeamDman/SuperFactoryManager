//! Explicit file membership for newly captured release source comparisons.
use facet::Facet;
#[derive(Debug, Facet)]
#[facet(deny_unknown_fields)]
pub struct ReleaseReportSourcePaths {
    pub before: Option<String>,
    pub after: Option<String>,
}
impl ReleaseReportSourcePaths {
    pub(super) fn validate(&self) -> eyre::Result<()> {
        eyre::ensure!(
            self.before.is_some() || self.after.is_some(),
            "report source paths require a present side"
        );
        for path in self.before.iter().chain(self.after.iter()) {
            crate::source_projection::promotion::validate_relative_path(path)?;
        }
        Ok(())
    }
}
