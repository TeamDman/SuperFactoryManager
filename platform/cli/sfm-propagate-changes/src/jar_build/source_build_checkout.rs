//! Declared source-build origin and its selected local working directories.
//! Admission remains in the producer's existing preflight and checkout operation.

use std::path::Path;

#[derive(Clone, Copy, Debug)]
pub(super) struct SourceBuildCheckout<'a> {
    pub(super) remote_url: &'a str,
    pub(super) commit: &'a str,
    pub(super) checkout_dir: &'a Path,
    pub(super) repository_dir: &'a Path,
}
