use facet::Facet;

/// The development or release policy of a projection.
#[derive(Clone, Copy, Debug, Eq, Facet, PartialEq)]
#[facet(rename_all = "snake_case")]
#[repr(u8)]
pub enum ProjectionEnvironment {
    Release,
    Dev,
}

impl ProjectionEnvironment {
    #[must_use]
    pub const fn as_str(self) -> &'static str {
        match self {
            Self::Release => "release",
            Self::Dev => "dev",
        }
    }
}
