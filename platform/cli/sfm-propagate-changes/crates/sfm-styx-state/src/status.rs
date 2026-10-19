use crate::PropagationState;
use facet::Facet;

#[derive(Facet, Debug, Clone, Default)]
#[repr(u8)]
pub(crate) enum Status {
    #[default]
    Idle,
    MergingWithConflict {
        source_branch: String,
        source_path: String,
        dest_branch: String,
        dest_path: String,
    },
}

impl From<Status> for PropagationState {
    fn from(status: Status) -> Self {
        match status {
            Status::Idle => Self::Idle,
            Status::MergingWithConflict {
                source_branch,
                source_path,
                dest_branch,
                dest_path,
            } => Self::MergingWithConflict {
                source_branch,
                source_path,
                dest_branch,
                dest_path,
            },
        }
    }
}

impl From<PropagationState> for Status {
    fn from(status: PropagationState) -> Self {
        match status {
            PropagationState::Idle => Self::Idle,
            PropagationState::MergingWithConflict {
                source_branch,
                source_path,
                dest_branch,
                dest_path,
            } => Self::MergingWithConflict {
                source_branch,
                source_path,
                dest_branch,
                dest_path,
            },
        }
    }
}
