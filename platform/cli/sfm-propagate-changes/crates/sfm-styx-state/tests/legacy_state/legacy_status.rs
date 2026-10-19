use crate::sfm_path::SfmPath;
use facet::Facet;

/// Original state status, including the path proxy used before the migration.
#[derive(Facet, Debug, Clone, Default)]
#[repr(u8)]
pub(crate) enum LegacyStatus {
    #[default]
    Idle,
    MergingWithConflict {
        source_branch: String,
        source_path: SfmPath,
        dest_branch: String,
        dest_path: SfmPath,
    },
}
