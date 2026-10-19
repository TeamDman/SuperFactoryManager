//! One internally selected producer input, never a user-supplied path admission.

use super::ContentHash;
use std::path::PathBuf;

pub(super) struct ForgePreparationInputRequest {
    pub(super) role: String,
    pub(super) path: PathBuf,
    pub(super) expected: Vec<ContentHash>,
    pub(super) exact_bytes: Option<u64>,
    pub(super) limit: u64,
    pub(super) retain_bytes: bool,
}

impl ForgePreparationInputRequest {
    pub(super) fn new(role: impl Into<String>, path: PathBuf) -> Self {
        Self {
            role: role.into(),
            path,
            expected: Vec::new(),
            exact_bytes: None,
            limit: 128 * 1024 * 1024,
            retain_bytes: false,
        }
    }

    pub(super) fn pinned(mut self, expected: ContentHash) -> Self {
        self.expected.push(expected);
        self
    }

    pub(super) fn sized(mut self, bytes: u64) -> Self {
        self.exact_bytes = Some(bytes);
        self
    }

    pub(super) fn retained(mut self, maximum: u64) -> Self {
        self.limit = maximum;
        self.retain_bytes = true;
        self
    }
}
