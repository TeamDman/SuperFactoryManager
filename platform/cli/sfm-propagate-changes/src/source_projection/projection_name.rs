use super::projection_catalog::validate_projection_key;
use eyre::Result;
use facet::Facet;

/// An exact, portable projection name from `projections.json`.
#[derive(Clone, Debug, Facet)]
#[facet(opaque, proxy = String)]
pub struct ProjectionName(String);

impl ProjectionName {
    /// # Errors
    /// Rejects nonportable projection names.
    pub fn parse(value: &str) -> Result<Self> {
        Self::try_from(value.to_owned())
    }

    #[must_use]
    pub fn as_str(&self) -> &str {
        &self.0
    }
}

impl TryFrom<String> for ProjectionName {
    type Error = eyre::Report;

    fn try_from(value: String) -> Result<Self> {
        validate_projection_key(&value)?;
        Ok(Self(value))
    }
}

impl From<&ProjectionName> for String {
    fn from(name: &ProjectionName) -> Self {
        name.0.clone()
    }
}
