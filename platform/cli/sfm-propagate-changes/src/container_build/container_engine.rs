use facet::Facet;

#[derive(Clone, Copy, Debug, Default, Eq, Facet, PartialEq)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum ContainerEngine {
    #[default]
    Podman,
    Docker,
}
impl ContainerEngine {
    pub(crate) const fn executable(self) -> &'static str {
        match self {
            Self::Podman => "podman",
            Self::Docker => "docker",
        }
    }
}
