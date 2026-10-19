//! Exact existing nested-tuple wire type for the dependency key descriptor.

use super::super::nfrt_launch_contract::NamedNfrtInvocationInput;

pub(super) type LocalDependencyDescriptor = (
    (String, String, String),
    (String, String, String),
    (Option<String>, NamedNfrtInvocationInput),
);
