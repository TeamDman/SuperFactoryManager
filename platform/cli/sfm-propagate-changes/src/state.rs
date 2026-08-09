use crate::paths::APP_HOME;
use crate::sfm_path::SfmPath;
use eyre::Context;
use facet::Facet;
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
    pub fn load() -> eyre::Result<Self> {
        let state_file = APP_HOME.file_path(STATE_FILE);

        if !state_file.exists() {
            debug!("State file does not exist, returning default state");
            return Ok(Self::default());
        }

        let content = std::fs::read_to_string(&state_file).wrap_err("Failed to read state file")?;

        let state: State = facet_styx::from_str(&content).wrap_err("Failed to parse state file")?;

        Ok(state)
    }

    /// Save state to the state file
    ///
    /// # Errors
    ///
    /// Returns an error if the state file cannot be written.
    pub fn save(&self) -> eyre::Result<()> {
        APP_HOME.ensure_dir()?;

        let state_file = APP_HOME.file_path(STATE_FILE);
        let content = facet_styx::to_string(self).wrap_err("Failed to serialize state to Styx")?;

        std::fs::write(&state_file, content).wrap_err("Failed to write state file")?;

        debug!(?state_file, "State saved");
        Ok(())
    }

    /// Reset state to idle
    ///
    /// # Errors
    ///
    /// Returns an error if the state file cannot be written.
    pub fn reset(&mut self) -> eyre::Result<()> {
        self.status = Status::Idle;
        self.save()
    }
}
