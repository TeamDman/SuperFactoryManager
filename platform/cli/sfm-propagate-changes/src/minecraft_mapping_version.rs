use crate::branch_targets::MinecraftVersion;
use chrono::NaiveDate;

/// Parsed mapping versions with the source and target Minecraft versions retained.
#[derive(Clone, Debug, Eq, PartialEq)]
pub enum MinecraftMappingVersion {
    Official(MinecraftVersion),
    Parchment((NaiveDate, MinecraftVersion)),
    CrossVersionParchment {
        mappings: (NaiveDate, MinecraftVersion),
        target: MinecraftVersion,
    },
}
