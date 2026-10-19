/// Owned persistence data with no reflection types in the public boundary.
#[derive(Debug, PartialEq, Eq)]
pub enum PropagationState {
    Idle,
    MergingWithConflict {
        source_branch: String,
        source_path: String,
        dest_branch: String,
        dest_path: String,
    },
}
