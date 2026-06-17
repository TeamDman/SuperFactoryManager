use facet::Facet;
use std::fmt;
use std::ops::Deref;

#[derive(Facet, Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash)]
#[facet(transparent)]
pub struct CurseforgeGameVersionTypeId(pub u64);

impl From<u64> for CurseforgeGameVersionTypeId {
    fn from(value: u64) -> Self {
        Self(value)
    }
}

impl AsRef<u64> for CurseforgeGameVersionTypeId {
    fn as_ref(&self) -> &u64 {
        &self.0
    }
}

impl Deref for CurseforgeGameVersionTypeId {
    type Target = u64;

    fn deref(&self) -> &Self::Target {
        &self.0
    }
}

impl fmt::Display for CurseforgeGameVersionTypeId {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        self.0.fmt(f)
    }
}
