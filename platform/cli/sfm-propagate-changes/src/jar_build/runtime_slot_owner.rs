//! Persisted process identities for one owner of a persistent runtime slot.

use super::runtime_owner_identity::RuntimeOwnerIdentity;
use eyre::Result;
use facet::Facet;

#[derive(Clone, Debug, Facet)]
#[facet(deny_unknown_fields)]
pub(crate) struct RuntimeSlotOwner {
    pub(crate) schema: String,
    pub(crate) owner_id: String,
    pub(crate) number: u32,
    pub(crate) launcher: RuntimeOwnerIdentity,
    pub(crate) child: Option<RuntimeOwnerIdentity>,
    pub(crate) active: bool,
    /// Persisted before spawning: an unregistered surviving child cannot be disproved.
    #[facet(default)]
    pub(crate) spawn_pending: bool,
}

impl RuntimeSlotOwner {
    pub(super) const SCHEMA: &str = "sfm:runtime_slot_owner@2";
    pub(super) const LEGACY_SCHEMA: &str = "sfm:runtime_slot_owner@1";

    pub(super) fn has_live_process(&self) -> Result<bool> {
        // Never infer absence across the spawn/registration crash window.
        // Only this launcher's explicit spawn failure or exact child registration
        // resolves the uncertainty. Normal release must preserve pending state.
        if self.spawn_pending {
            return Ok(true);
        }
        let launcher = if self.active {
            RuntimeOwnerIdentity::capture(self.launcher.pid)?
        } else {
            None
        };
        let child = self
            .child
            .as_ref()
            .map(|identity| RuntimeOwnerIdentity::capture(identity.pid))
            .transpose()?
            .flatten();
        Ok(self.matches_observed(launcher.as_ref(), child.as_ref()))
    }

    // A normally released launcher can remain alive in its CLI/test process.
    // A surviving recorded child keeps the slot occupied even after release.
    pub(super) fn matches_observed(
        &self,
        launcher: Option<&RuntimeOwnerIdentity>,
        child: Option<&RuntimeOwnerIdentity>,
    ) -> bool {
        self.spawn_pending
            || (self.active && launcher == Some(&self.launcher))
            || self
                .child
                .as_ref()
                .is_some_and(|expected| child == Some(expected))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::path::PathBuf;

    #[test]
    fn reused_pid_with_different_creation_or_executable_is_not_the_owner() {
        let identity = RuntimeOwnerIdentity {
            pid: 41,
            creation_identity: "boot:100".to_owned(),
            executable_path: PathBuf::from("/fixture/java"),
        };
        let owner = RuntimeSlotOwner {
            schema: RuntimeSlotOwner::SCHEMA.to_owned(),
            owner_id: "owner-41-fixture".to_owned(),
            number: 1,
            launcher: identity.clone(),
            child: None,
            active: true,
            spawn_pending: false,
        };
        assert!(owner.matches_observed(Some(&identity), None));
        let mut reused = identity.clone();
        reused.creation_identity = "other-boot:100".to_owned();
        assert!(!owner.matches_observed(Some(&reused), None));
        reused = identity;
        reused.executable_path = PathBuf::from("/fixture/another-java");
        assert!(!owner.matches_observed(Some(&reused), None));
    }

    #[test]
    fn released_launcher_is_ignored_but_matching_child_keeps_slot_busy() {
        let identity = RuntimeOwnerIdentity {
            pid: 42,
            creation_identity: "boot:200".to_owned(),
            executable_path: PathBuf::from("/fixture/java"),
        };
        let mut owner = RuntimeSlotOwner {
            schema: RuntimeSlotOwner::SCHEMA.to_owned(),
            owner_id: "owner-42-fixture".to_owned(),
            number: 1,
            launcher: identity.clone(),
            child: None,
            active: false,
            spawn_pending: false,
        };
        assert!(!owner.matches_observed(Some(&identity), None));
        owner.child = Some(identity.clone());
        assert!(owner.matches_observed(Some(&identity), Some(&identity)));
        assert!(!owner.matches_observed(Some(&identity), None));
    }

    #[test]
    fn unresolved_spawn_stays_busy_without_any_observed_process() {
        let owner = RuntimeSlotOwner {
            schema: RuntimeSlotOwner::SCHEMA.to_owned(),
            owner_id: "owner-42-pending".to_owned(),
            number: 1,
            launcher: RuntimeOwnerIdentity {
                pid: 42,
                creation_identity: "old-creation".to_owned(),
                executable_path: PathBuf::from("/fixture/java"),
            },
            child: None,
            active: false,
            spawn_pending: true,
        };
        assert!(owner.matches_observed(None, None));
        assert!(owner.has_live_process().unwrap());
    }
}
