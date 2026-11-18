use facet::Facet;

/// One endpoint of a parsed bracketed project version range.
///
/// Version text is preserved without assigning numeric or qualifier ordering.
#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[repr(u8)]
pub enum SemverVersionBound {
    Included(String),
    Excluded(String),
    Unbounded,
}
