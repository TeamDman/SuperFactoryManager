//! Key digest, requested pin digests, and optional retained small raw bytes.
use super::ContentHash;

pub(super) struct ForgePreparationInputHashes {
    pub(super) sha256: String,
    pub(super) actual: Vec<ContentHash>,
    pub(super) retained: Option<Vec<u8>>,
}
