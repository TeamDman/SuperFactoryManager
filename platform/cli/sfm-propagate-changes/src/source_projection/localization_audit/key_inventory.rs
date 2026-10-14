use facet::Facet;
use std::collections::BTreeMap;

#[derive(Debug, Default, Facet)]
pub struct KeyInventory {
    pub required: BTreeMap<String, Vec<String>>,
    pub registry_ids: BTreeMap<String, super::registry_observation::RegistryObservation>,
    pub unresolved: Vec<String>,
    /// Dynamic prefixes are candidate expansions from English, not a proof
    /// that every runtime family member has been discovered.
    pub dynamic_candidates: BTreeMap<String, Vec<String>>,
    pub coverage: String,
}

impl KeyInventory {
    pub fn add(&mut self, key: &str, origin: String) {
        if !key.is_empty() {
            self.required.entry(key.into()).or_default().push(origin);
        }
    }
}
