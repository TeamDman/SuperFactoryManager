use facet::Facet;

/// Source metadata preparation never admits a compiled or packaged artifact.
#[derive(Clone, Copy, Debug, Eq, Facet, PartialEq)]
#[facet(rename_all = "snake_case")]
#[repr(u8)]
pub enum CatalogReleaseArtifactReadiness {
    NotAssessed,
    /// Exact local receipt, current selected inputs and retained JAR bytes checked.
    VerifiedLocalNativeReceipt,
}
