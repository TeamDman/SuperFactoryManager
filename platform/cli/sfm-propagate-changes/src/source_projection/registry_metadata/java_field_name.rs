use facet::Facet;

#[derive(Clone, Debug, Eq, PartialEq, Ord, PartialOrd, Facet)]
#[facet(transparent)]
pub struct JavaFieldName(pub String);
