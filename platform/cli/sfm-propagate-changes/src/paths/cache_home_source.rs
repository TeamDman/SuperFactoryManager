/// Origin captured together with a cache path at startup.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum CacheHomeSource {
    EnvironmentOverride,
    PersistentSetting,
    PlatformDefault,
    /// A caller supplied a concrete home without platform-default authority.
    Supplied,
}

impl CacheHomeSource {
    #[must_use]
    pub fn label(self) -> &'static str {
        match self {
            Self::EnvironmentOverride => super::APP_CACHE_ENV_VAR,
            Self::PersistentSetting => "persistent setting",
            Self::PlatformDefault => "platform user cache default",
            Self::Supplied => "supplied cache home",
        }
    }
}
