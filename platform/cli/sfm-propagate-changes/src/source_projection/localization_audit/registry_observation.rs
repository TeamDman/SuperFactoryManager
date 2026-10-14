use super::super::registry_metadata::java_field_name::JavaFieldName;
use super::super::registry_metadata::registry_id::RegistryId;
use super::super::registry_metadata::translation_key::TranslationKey;
use facet::Facet;

#[derive(Debug, Facet)]
pub struct RegistryObservation {
    pub java_field: JavaFieldName,
    pub registry_id: RegistryId,
    pub translation_key: Option<TranslationKey>,
    pub source: String,
    pub coverage: String,
}
