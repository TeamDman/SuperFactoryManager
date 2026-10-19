//! Whether an artifact miss may cross the ordinary acquisition boundary.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(super) enum ResolverAcquisitionPolicy {
    AcquireMissing,
    ExistingOnly,
}
