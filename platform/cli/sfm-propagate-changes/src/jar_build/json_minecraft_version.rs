use crate::branch_targets::MinecraftVersion;
use facet::Facet;

#[derive(Clone, Debug, Facet)]
#[facet(transparent)]
pub(super) struct JsonMinecraftVersion(String);

impl TryFrom<JsonMinecraftVersion> for MinecraftVersion {
    type Error = String;

    fn try_from(value: JsonMinecraftVersion) -> Result<Self, Self::Error> {
        MinecraftVersion::parse(&value.0).map_err(|error| error.to_string())
    }
}

impl TryFrom<&MinecraftVersion> for JsonMinecraftVersion {
    type Error = String;

    fn try_from(value: &MinecraftVersion) -> Result<Self, Self::Error> {
        Ok(Self(value.as_str().to_string()))
    }
}
