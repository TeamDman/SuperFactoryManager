use facet::Facet;

/// Provider payloads require policy and artifact qualification outside this plan.
#[derive(Clone, Copy, Debug, Eq, Facet, PartialEq)]
#[facet(rename_all = "snake_case")]
#[repr(u8)]
pub enum CatalogReleaseProviderReadiness {
    NotPrepared,
}
