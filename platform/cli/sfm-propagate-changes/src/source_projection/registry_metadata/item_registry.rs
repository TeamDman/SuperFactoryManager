use super::registry_entry::RegistryEntry;
use facet::Facet;

/// Audited first slice. Facet's field shape supplies the Liquid object names;
/// there is no second hand-maintained map of those names.
#[derive(Clone, Debug, Facet)]
pub struct ItemRegistry {
    pub label_gun: RegistryEntry,
    pub experience_shard: RegistryEntry,
    pub experience_goop: RegistryEntry,
}

impl Default for ItemRegistry {
    fn default() -> Self {
        Self {
            label_gun: RegistryEntry::item("LABEL_GUN", "labelgun"),
            experience_shard: RegistryEntry::item("EXPERIENCE_SHARD", "xp_shard"),
            experience_goop: RegistryEntry::item("EXPERIENCE_GOOP", "xp_goop"),
        }
    }
}
