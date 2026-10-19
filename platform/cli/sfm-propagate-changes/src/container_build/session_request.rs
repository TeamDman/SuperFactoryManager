use super::SessionCaptureRequest;
use super::SessionRunTestRequest;
use super::SessionStartRequest;
use super::SessionStopRequest;
use super::SessionTarget;
use facet::Facet;
#[derive(Debug, Facet)]
#[repr(u8)]
pub enum SessionRequest {
    Start(SessionStartRequest),
    Status(SessionTarget),
    Reset(SessionTarget),
    RunTest(SessionRunTestRequest),
    Capture(SessionCaptureRequest),
    Stop(SessionStopRequest),
}
impl SessionRequest {
    pub(crate) fn target(&self) -> &SessionTarget {
        match self {
            Self::Start(r) => &r.target,
            Self::RunTest(r) => &r.target,
            Self::Capture(r) => &r.target,
            Self::Stop(r) => &r.target,
            Self::Status(r) | Self::Reset(r) => r,
        }
    }
}
pub(crate) fn bare_test(test: &str) -> eyre::Result<&str> {
    let bare = test
        .strip_prefix("sfm:")
        .ok_or_else(|| eyre::eyre!("test must be a qualified sfm:<test_name>"))?;
    eyre::ensure!(
        !bare.is_empty()
            && bare.len() <= 128
            && bare.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'_'),
        "invalid game test name"
    );
    Ok(bare)
}
