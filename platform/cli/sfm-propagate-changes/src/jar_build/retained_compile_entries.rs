//! Borrowed retained project entries; the tuple API remains unchanged.
use std::path::PathBuf;

pub(super) type RetainedCompileEntries<'a> = Vec<(PathBuf, &'a [u8])>;
