//! Podman's deferred OCI spec is admitted separately from runnable state.
#[derive(Clone, Copy)]
pub(super) enum SessionInspectionPhase {
    Created,
    Runtime,
}
impl SessionInspectionPhase {
    pub(super) const fn required_userns(self) -> &'static str {
        match self {
            Self::Created => "",
            Self::Runtime => "private",
        }
    }
}
