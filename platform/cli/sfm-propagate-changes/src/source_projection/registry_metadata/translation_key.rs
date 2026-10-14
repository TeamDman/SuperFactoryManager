use facet::Facet;

#[derive(Clone, Debug, Eq, PartialEq, Ord, PartialOrd, Facet)]
#[facet(transparent)]
pub struct TranslationKey(pub String);
