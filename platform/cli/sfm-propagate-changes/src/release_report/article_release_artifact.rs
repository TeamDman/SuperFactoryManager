use facet::Facet;

/// One release target and the exact local evidence supporting its delivery claims.
#[derive(Debug, Facet)]
#[expect(
    clippy::struct_excessive_bools,
    reason = "Independent checks are flat fields in the reviewed delivery protocol."
)]
#[facet(deny_unknown_fields)]
pub(super) struct ArticleReleaseArtifact {
    pub id: String,
    pub projection: String,
    pub baseline_sha256: String,
    pub candidate_sha256: String,
    pub package_receipt_path: String,
    pub package_receipt_sha256: String,
    pub resource_audit_path: String,
    pub resource_audit_sha256: String,
    pub class_audit_path: String,
    pub class_audit_sha256: String,
    pub energy_extraction_verified: bool,
    pub label_gun_success_messages_verified: bool,
    pub tunnel_forwarding_preserved: bool,
    pub tunnel_relay: String,
    pub french_entries: usize,
    pub portuguese_entries: usize,
    pub portuguese_added_keys: usize,
    pub recipe_count: usize,
    pub recipe_advancements: usize,
    pub buffer_loot_absent: bool,
    /// Compiled production/loaded-game IDE gate; not a live registry observation.
    pub buffer_production_item_absent: bool,
}
