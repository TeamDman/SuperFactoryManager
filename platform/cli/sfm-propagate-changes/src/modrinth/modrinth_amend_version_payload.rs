use facet::Facet;

#[derive(Facet, Debug, Clone)]
pub struct ModrinthAmendVersionPayload {
    pub changelog: String,
}
