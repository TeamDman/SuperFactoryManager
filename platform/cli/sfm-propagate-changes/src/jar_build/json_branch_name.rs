use crate::branch_targets::BranchName;
use facet::Facet;

#[derive(Clone, Debug, Facet)]
#[facet(transparent)]
pub(super) struct JsonBranchName(String);

impl TryFrom<JsonBranchName> for BranchName {
    type Error = String;

    fn try_from(value: JsonBranchName) -> Result<Self, Self::Error> {
        Ok(BranchName::from(value.0))
    }
}

impl TryFrom<&BranchName> for JsonBranchName {
    type Error = String;

    fn try_from(value: &BranchName) -> Result<Self, Self::Error> {
        Ok(Self(value.as_str().to_string()))
    }
}
