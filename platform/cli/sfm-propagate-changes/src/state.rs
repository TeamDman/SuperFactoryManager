use crate::paths::AppHome;
use crate::sfm_path::SfmPath;
use eyre::Context;
use facet::Facet;
use std::fs::read_to_string;
use std::fs::write;
use tracing::debug;

const STATE_FILE: &str = "state.styx";

/// The state of the propagation process.
#[derive(Facet, Debug, Clone, Default)]
pub struct State {
    /// Current state of the propagation process
    pub status: Status,
}

/// The propagation status
#[derive(Facet, Debug, Clone, Default)]
#[repr(u8)]
pub enum Status {
    /// No active merge in progress
    #[default]
    Idle,
    /// Merging with a conflict that needs user resolution
    MergingWithConflict {
        /// Source branch name
        source_branch: String,
        /// Source worktree path
        source_path: SfmPath,
        /// Destination branch name
        dest_branch: String,
        /// Destination worktree path
        dest_path: SfmPath,
    },
}

impl State {
    /// Load state from the state file
    ///
    /// # Errors
    ///
    /// Returns an error if the state file cannot be read or parsed.
    pub fn load(app_home: &AppHome) -> eyre::Result<Self> {
        let state_file = app_home.file_path(STATE_FILE);

        if !state_file.exists() {
            debug!("State file does not exist, returning default state");
            return Ok(Self::default());
        }

        let content = read_to_string(&state_file).wrap_err("Failed to read state file")?;

        let status = sfm_styx_state::decode(&content).wrap_err("Failed to parse state file")?;
        Ok(Self {
            status: status.into(),
        })
    }

    /// Save state to the state file
    ///
    /// # Errors
    ///
    /// Returns an error if the state file cannot be written.
    pub fn save(&self, app_home: &AppHome) -> eyre::Result<()> {
        app_home.ensure_dir()?;

        let state_file = app_home.file_path(STATE_FILE);
        let content = sfm_styx_state::encode((&self.status).into())
            .wrap_err("Failed to serialize state to Styx")?;

        write(&state_file, content).wrap_err("Failed to write state file")?;

        debug!(?state_file, "State saved");
        Ok(())
    }

    /// Reset state to idle
    ///
    /// # Errors
    ///
    /// Returns an error if the state file cannot be written.
    pub fn reset(&mut self, app_home: &AppHome) -> eyre::Result<()> {
        self.status = Status::Idle;
        self.save(app_home)
    }
}

impl From<sfm_styx_state::PropagationState> for Status {
    fn from(status: sfm_styx_state::PropagationState) -> Self {
        match status {
            sfm_styx_state::PropagationState::Idle => Self::Idle,
            sfm_styx_state::PropagationState::MergingWithConflict {
                source_branch,
                source_path,
                dest_branch,
                dest_path,
            } => Self::MergingWithConflict {
                source_branch,
                source_path: source_path.into(),
                dest_branch,
                dest_path: dest_path.into(),
            },
        }
    }
}

impl From<&Status> for sfm_styx_state::PropagationState {
    fn from(status: &Status) -> Self {
        match status {
            Status::Idle => Self::Idle,
            Status::MergingWithConflict {
                source_branch,
                source_path,
                dest_branch,
                dest_path,
            } => Self::MergingWithConflict {
                source_branch: source_branch.clone(),
                source_path: source_path.0.to_string_lossy().into_owned(),
                dest_branch: dest_branch.clone(),
                dest_path: dest_path.0.to_string_lossy().into_owned(),
            },
        }
    }
}
