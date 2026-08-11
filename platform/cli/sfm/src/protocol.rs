//! Rust-authoritative SFM live-game control contract.

use facet::Facet;
use vox::service;

pub const CONTROL_PROTOCOL_VERSION: u16 = 1;
pub const INSTANCE_DESCRIPTOR_SCHEMA: &str = "sfm.game-instance/1";
pub const MAX_AUTHENTICATION_TOKEN_BYTES: usize = 256;
pub const MAX_ACTION_TOKENS: usize = 128;
pub const MAX_ACTION_TOKEN_BYTES: usize = 4096;
pub const MAX_ACTION_FEEDBACK_ENTRIES: usize = 64;

/// Operations hosted by one live SFM Minecraft client.
#[service]
pub trait SfmControl {
    /// Prove endpoint identity and protocol compatibility without game mutation.
    async fn ping(
        &self,
        request: SfmControlPingRequest,
    ) -> Result<SfmControlPingResult, SfmControlError>;

    /// Return current identity, focus recency, lifecycle, and capabilities.
    async fn describe(
        &self,
        request: SfmControlDescribeRequest,
    ) -> Result<SfmControlDescribeResult, SfmControlError>;

    /// Execute one registered SFM client action on the Minecraft client thread.
    async fn invoke_client_action(
        &self,
        request: SfmControlInvokeClientActionRequest,
    ) -> Result<SfmControlInvokeClientActionResult, SfmControlError>;
}

#[derive(Clone, Debug, PartialEq, Eq, Facet)]
pub struct SfmControlPingRequest {
    pub authentication_token: String,
    pub request_id: String,
    pub protocol_version: u16,
}

#[derive(Clone, Debug, PartialEq, Eq, Facet)]
pub struct SfmControlPingResult {
    pub instance_id: String,
    pub process_id: u32,
    pub process_start_nonce: String,
    pub protocol_version: u16,
    pub request_id: String,
}

#[derive(Clone, Debug, PartialEq, Eq, Facet)]
pub struct SfmControlDescribeRequest {
    pub authentication_token: String,
    pub request_id: String,
    pub protocol_version: u16,
}

#[derive(Clone, Debug, PartialEq, Eq, Facet)]
#[allow(clippy::struct_excessive_bools)]
pub struct SfmControlDescribeResult {
    pub instance_id: String,
    pub process_id: u32,
    pub process_start_nonce: String,
    pub protocol_version: u16,
    pub sfm_version: String,
    pub minecraft_version: String,
    pub lifecycle: SfmControlLifecycle,
    pub focused: bool,
    pub focus_seen: bool,
    pub last_focus_epoch_millis: i64,
    pub current_screen_present: bool,
    pub current_screen: String,
    pub world_present: bool,
    pub world_label: String,
    pub capabilities: Vec<String>,
    pub request_id: String,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Facet)]
#[repr(u8)]
pub enum SfmControlLifecycle {
    Starting,
    Ready,
    Stopping,
}

#[derive(Clone, Debug, PartialEq, Eq, Facet)]
pub struct SfmControlInvokeClientActionRequest {
    pub authentication_token: String,
    pub request_id: String,
    pub protocol_version: u16,
    pub action_tokens: Vec<String>,
}

#[derive(Clone, Debug, PartialEq, Eq, Facet)]
pub struct SfmControlInvokeClientActionResult {
    pub instance_id: String,
    pub process_id: u32,
    pub request_id: String,
    pub canonical_action: String,
    pub result_code: i32,
    pub feedback: Vec<String>,
    pub resulting_screen_present: bool,
    pub resulting_screen: String,
    pub workspace_present: bool,
    pub workspace_panel_count: u32,
}

#[derive(Clone, Debug, PartialEq, Eq, Facet)]
pub struct SfmControlError {
    pub code: SfmControlErrorCode,
    pub message: String,
    pub retryable: bool,
    pub request_id: String,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Facet)]
#[repr(u8)]
pub enum SfmControlErrorCode {
    AuthenticationFailed,
    ProtocolMismatch,
    InvalidRequest,
    CapacityExceeded,
    ClientUnavailable,
    ActionUnknown,
    ActionIncomplete,
    ActionUnavailable,
    ActionFailed,
    Cancelled,
    ShuttingDown,
    Internal,
}

#[derive(Clone, Debug, PartialEq, Eq, Facet)]
pub struct SfmGameInstanceDescriptor {
    pub schema: String,
    pub protocol_version: u16,
    pub instance_id: String,
    pub process_id: u32,
    pub process_start_nonce: String,
    pub transport: String,
    pub host: String,
    pub port: u16,
    pub authentication_token: String,
    pub created_at_epoch_millis: i64,
}

impl SfmGameInstanceDescriptor {
    /// Validate untrusted descriptor contents before any connection attempt.
    ///
    /// # Errors
    ///
    /// Returns an error when the schema, bounds, or loopback endpoint policy is
    /// invalid.
    pub fn validate(&self) -> eyre::Result<()> {
        eyre::ensure!(
            self.schema == INSTANCE_DESCRIPTOR_SCHEMA,
            "unsupported descriptor schema"
        );
        eyre::ensure!(
            self.protocol_version > 0,
            "protocol version must be positive"
        );
        eyre::ensure!(!self.instance_id.is_empty(), "instance id is empty");
        eyre::ensure!(self.process_id > 0, "process id must be positive");
        eyre::ensure!(
            !self.process_start_nonce.is_empty(),
            "process start nonce is empty"
        );
        eyre::ensure!(self.transport == "tcp", "unsupported descriptor transport");
        eyre::ensure!(
            self.host == "127.0.0.1",
            "control endpoint must be IPv4 loopback"
        );
        eyre::ensure!(self.port > 0, "control endpoint port must be positive");
        eyre::ensure!(
            !self.authentication_token.is_empty()
                && self.authentication_token.len() <= MAX_AUTHENTICATION_TOKEN_BYTES,
            "authentication token length is invalid"
        );
        Ok(())
    }
}

/// Validate bounded action tokens before transport allocation or dispatch.
///
/// # Errors
///
/// Returns an error for an empty, oversized, or NUL-containing action.
pub fn validate_action_tokens(tokens: &[String]) -> eyre::Result<()> {
    eyre::ensure!(
        !tokens.is_empty(),
        "invoke requires a registered SFM client action"
    );
    eyre::ensure!(tokens.len() <= MAX_ACTION_TOKENS, "too many action tokens");
    for token in tokens {
        eyre::ensure!(
            token.len() <= MAX_ACTION_TOKEN_BYTES,
            "action token is too long"
        );
        eyre::ensure!(!token.contains('\0'), "action token contains NUL");
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn descriptor_requires_a_loopback_tcp_endpoint_and_token() {
        let mut descriptor = SfmGameInstanceDescriptor {
            schema: INSTANCE_DESCRIPTOR_SCHEMA.to_owned(),
            protocol_version: CONTROL_PROTOCOL_VERSION,
            instance_id: "instance-1".to_owned(),
            process_id: 42,
            process_start_nonce: "start-1".to_owned(),
            transport: "tcp".to_owned(),
            host: "127.0.0.1".to_owned(),
            port: 51_234,
            authentication_token: "secret".to_owned(),
            created_at_epoch_millis: 1,
        };
        descriptor.validate().expect("valid descriptor");
        descriptor.host = "0.0.0.0".to_owned();
        let _ = descriptor.validate().expect_err("non-loopback descriptor");
    }

    #[test]
    fn action_tokens_are_bounded() {
        validate_action_tokens(&["sfm:panel/open".to_owned(), "sfm:size_display".to_owned()])
            .expect("known-sized action");
        let _ = validate_action_tokens(&[]).expect_err("empty action");
    }
}
