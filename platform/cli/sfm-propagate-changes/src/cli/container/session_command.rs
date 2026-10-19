use facet::Facet;
#[derive(Debug, Facet)]
#[repr(u8)]
pub enum SessionCommand {
    /// Start a local existing image under the fixed disposable sandbox and wait for puppet readiness.
    Start(super::session_start_args::SessionStartArgs),
    /// Read owned worker state and optional puppet readiness.
    Status(super::session_target_args::SessionTargetArgs),
    /// Reset the fixture by rerunning the session's named test in the same world.
    Reset(super::session_target_args::SessionTargetArgs),
    /// Run a qualified SFM game test in the existing disposable world.
    RunTest(super::session_run_test_args::SessionRunTestArgs),
    /// Orbit the camera and export one verified PNG to a new host file.
    Capture(super::session_capture_args::SessionCaptureArgs),
    /// Stop the owned worker, optionally export allowlisted artifacts and remove it.
    Stop(super::session_stop_args::SessionStopArgs),
}
