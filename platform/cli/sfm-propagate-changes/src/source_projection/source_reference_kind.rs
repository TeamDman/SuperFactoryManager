use facet::Facet;
#[derive(Clone, Copy, Debug, Eq, Facet, Ord, PartialEq, PartialOrd)]
#[repr(u8)]
pub enum SourceReferenceKind {
    #[facet(rename = "issue")]
    Issue,
    #[facet(rename = "pull_request")]
    PullRequest,
    #[facet(rename = "discussion")]
    Discussion,
}
