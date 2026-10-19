//! Test-only ownership: reap the private Rust child even if its parent panics.

use eyre::Result;
use std::process::Child;
use std::process::Command;

pub(super) struct RuntimeFixtureChild {
    child: Child,
}

impl RuntimeFixtureChild {
    pub(super) fn spawn(command: &mut Command) -> Result<Self> {
        Ok(Self {
            child: command.spawn()?,
        })
    }

    pub(super) fn child_mut(&mut self) -> &mut Child {
        &mut self.child
    }
}

impl Drop for RuntimeFixtureChild {
    fn drop(&mut self) {
        if self.child.try_wait().ok().flatten().is_none() {
            let _ = self.child.kill();
            let _ = self.child.wait();
        }
    }
}
