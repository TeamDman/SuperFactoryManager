//! One admitted file and its optional bounded small authority bytes.

use super::HeldForgePreparationInput;

pub(super) struct ForgePreparationCapture {
    pub(super) input: HeldForgePreparationInput,
    pub(super) retained: Option<Vec<u8>>,
}
