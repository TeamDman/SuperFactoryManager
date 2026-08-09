#[derive(Clone, Copy, Debug, Eq, PartialEq)]
#[cfg_attr(test, derive(arbitrary::Arbitrary))]
pub enum VersionScope {
    AnyTargetWithMinecraftVersion,
    CoreOnly,
}
