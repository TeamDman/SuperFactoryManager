mod legacy_status;

use facet::Facet;
pub(super) use legacy_status::LegacyStatus;

/// Original state schema, retained to qualify the serializer boundary.
#[derive(Facet, Debug, Clone, Default)]
pub(super) struct LegacyState {
    pub(super) status: LegacyStatus,
}
