//! Exact view inputs; optional bytes preserve absence independently of empty payloads.
#[derive(Debug, Eq, Ord, PartialEq, PartialOrd)]
pub(super) struct ArchiveResourcePair {
    old_path: Option<String>,
    new_path: Option<String>,
    before: Option<Vec<u8>>,
    after: Option<Vec<u8>>,
}

impl ArchiveResourcePair {
    pub(super) fn new(
        old_path: Option<&str>,
        new_path: Option<&str>,
        before: Option<&[u8]>,
        after: Option<&[u8]>,
    ) -> Self {
        Self {
            old_path: old_path.map(str::to_owned),
            new_path: new_path.map(str::to_owned),
            before: before.map(<[u8]>::to_vec),
            after: after.map(<[u8]>::to_vec),
        }
    }
}
