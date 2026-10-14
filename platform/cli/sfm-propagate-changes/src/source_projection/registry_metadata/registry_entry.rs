use super::java_field_name::JavaFieldName;
use super::registry_id::RegistryId;
use super::translation_key::TranslationKey;
use facet::Facet;

/// Explicit overrides are independent of Java spelling. Block-item entries
/// must use their block's description key, never infer an item prefix.
#[derive(Clone, Debug, Facet)]
pub struct RegistryEntry {
    pub java_field: JavaFieldName,
    pub registry_id: RegistryId,
    pub translation_key: TranslationKey,
}

impl RegistryEntry {
    #[must_use]
    pub fn item(field: &str, id: &str) -> Self {
        Self {
            java_field: JavaFieldName(field.into()),
            registry_id: RegistryId(id.into()),
            translation_key: TranslationKey(format!("item.sfm.{id}")),
        }
    }
}
