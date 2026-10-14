use super::key_inventory::KeyInventory;
use super::locale_report::LocaleReport;
use facet::Facet;
use std::collections::BTreeMap;

#[derive(Debug, Facet)]
pub struct AuditReport {
    pub projection_key: String,
    pub inventory: KeyInventory,
    pub english_reference: BTreeMap<String, String>,
    pub locales: BTreeMap<String, LocaleReport>,
    pub missing_by_key: BTreeMap<String, Vec<String>>,
}
