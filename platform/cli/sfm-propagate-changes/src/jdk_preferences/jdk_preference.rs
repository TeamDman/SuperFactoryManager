use facet::Facet;

#[derive(Clone, Debug, Facet, PartialEq, Eq)]
pub struct JdkPreference {
    pub id: String,
    pub home: String,
    pub major: u32,
    pub vendor: String,
    pub version: String,
    pub build: String,
}
