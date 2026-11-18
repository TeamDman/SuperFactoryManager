use facet::Facet;

#[derive(Debug, Default, Facet)]
#[repr(u8)]
#[facet(rename_all = "snake_case")]
pub enum CatalogReleaseOperation {
    #[default]
    Create,
    Amend,
}
