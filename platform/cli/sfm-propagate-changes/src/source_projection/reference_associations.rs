//! Executed author declarations of relevance; never causal attribution.
use super::source_reference::SourceReference;
use facet::Facet;
use std::collections::BTreeMap;
#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct ReferenceAssociations {
    pub schema: String,
    pub references: Vec<SourceReference>,
    pub projection_key: String,
    pub context_sha256: String,
    pub declarations: BTreeMap<String, String>,
    /// Resolved guard bindings for this file, not attribution of individual spans.
    pub conditions: BTreeMap<String, bool>,
}
impl ReferenceAssociations {
    /// # Errors
    /// Rejects malformed, duplicate or unsorted reference identities and evidence.
    pub fn validate(&self) -> eyre::Result<()> {
        eyre::ensure!(
            self.schema == "sfm:related-references@1",
            "unsupported reference schema"
        );
        eyre::ensure!(
            !self.declarations.is_empty(),
            "reference declarations require source evidence"
        );
        eyre::ensure!(
            self.references.windows(2).all(|refs| refs[0] < refs[1]),
            "references must be sorted and unique"
        );
        let mut identities = std::collections::BTreeSet::new();
        for reference in &self.references {
            reference.validate()?;
            eyre::ensure!(
                identities.insert(reference.identity()),
                "duplicate GitHub reference identity"
            );
        }
        for digest in std::iter::once(&self.context_sha256).chain(self.declarations.values()) {
            let digest = digest.strip_prefix("sha256:").unwrap_or("");
            eyre::ensure!(
                digest.len() == 64
                    && digest
                        .bytes()
                        .all(|b| b.is_ascii_digit() || (b'a'..=b'f').contains(&b)),
                "invalid reference evidence digest"
            );
        }
        for path in self.declarations.keys() {
            super::promotion::validate_relative_path(path)?;
        }
        Ok(())
    }
}
