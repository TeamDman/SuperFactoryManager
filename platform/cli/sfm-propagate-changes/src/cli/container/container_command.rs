use super::ContainerBuildArgs;
use facet::Facet;

#[derive(Debug, Facet)]
#[repr(u8)]
pub enum ContainerCommand {
    /// Prepare a verified allowlisted context, then optionally build an offline Linux image.
    Build(ContainerBuildArgs),
    /// Run compiled Linux `JUnit` in a bounded disposable worker and export counts/logs before cleanup.
    Test(super::container_test_args::ContainerTestArgs),
    /// Control one owned disposable prebuilt client/world through the local engine API.
    Session(super::session_args::SessionArgs),
}
