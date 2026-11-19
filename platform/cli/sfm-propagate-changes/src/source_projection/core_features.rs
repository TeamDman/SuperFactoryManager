use facet::Facet;

pub const FEATURE_DEFINITIONS_PATH: &str =
    "platform/minecraft/core-liquid-template/feature-definitions.json";

#[derive(Clone, Debug, Facet)]
#[facet(deny_unknown_fields)]
pub struct CoreFeatureDefinition {
    pub supported_targets: Vec<String>,
    pub requires: Vec<String>,
}
