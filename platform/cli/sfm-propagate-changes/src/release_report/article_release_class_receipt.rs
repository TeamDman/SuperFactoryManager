use facet::Facet;

/// The stable facts in a class-audit receipt; detailed proof fields remain extensible.
#[derive(Debug, Facet)]
#[expect(
    clippy::struct_excessive_bools,
    reason = "Independent checks are flat fields in the existing audit protocol."
)]
pub(super) struct ArticleReleaseClassReceipt {
    pub schema: String,
    pub projection_key: String,
    pub target: String,
    pub jar: String,
    pub sha256: String,
    pub bytes: u64,
    pub energy_extraction_verified: bool,
    pub label_gun_success_messages_verified: bool,
    pub tunnel_forwarding_preserved: bool,
    pub relay_delivery_status: String,
    /// Static compiled production/loaded-game gate, not a live registry observation.
    pub buffer_production_item_absent: bool,
    pub success: bool,
}
