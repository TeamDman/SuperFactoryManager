//! Private source owner for one admitted all-hit named compilation.
//!
//! The original frozen handle and preparation identity remain unchanged.
//! Ordinary callers retain the strict `FrozenRecipeProject` implementation.

use super::catalog_owned_project::CatalogOwnedProject;
use super::frozen_recipe_project::FrozenRecipeProject;
use super::nfrt_project_owner::NfrtProjectOwner;
use super::nfrt_project_owner::sealed;
use eyre::Result;

pub(crate) struct RetainedNfrtProjectOwner<'a> {
    frozen: &'a FrozenRecipeProject,
}

impl<'a> RetainedNfrtProjectOwner<'a> {
    pub(crate) fn from_admitted(frozen: &'a FrozenRecipeProject) -> Result<Self> {
        frozen.recheck_retained_authority()?;
        Ok(Self { frozen })
    }
}

impl sealed::Sealed for RetainedNfrtProjectOwner<'_> {}

impl NfrtProjectOwner for RetainedNfrtProjectOwner<'_> {
    fn project(&self) -> &CatalogOwnedProject {
        self.frozen.project()
    }

    fn preparation_identity(&self) -> Result<String> {
        self.frozen.receipt().preparation_identity()
    }

    fn recheck_source(&self) -> Result<()> {
        self.frozen.recheck_retained_authority()
    }
}
