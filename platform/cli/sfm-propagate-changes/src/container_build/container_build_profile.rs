use facet::Facet;
#[derive(Clone, Copy, Debug, Default, Eq, Facet, PartialEq)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum ContainerBuildProfile {
    #[default]
    Runtime,
    Tests,
    Developer,
    /// Complete catalog source with isolated in-container generation and diagnostics.
    SupportAgent,
}
impl ContainerBuildProfile {
    pub(crate) const fn stage(self) -> &'static str {
        match self {
            Self::Runtime => "runtime",
            Self::Tests => "tests",
            Self::Developer => "developer",
            Self::SupportAgent => "support-agent",
        }
    }
}
