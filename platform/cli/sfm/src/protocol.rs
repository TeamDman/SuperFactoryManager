//! Rust-authoritative SFM live-game control contract.

use crate::explorer::{EntitySelector, PathExpression, SelectorDomain, SfmPath};
use facet::Facet;
use std::collections::BTreeSet;
use vox::service;

pub const CONTROL_PROTOCOL_VERSION: u16 = 1;
pub const INSTANCE_DESCRIPTOR_SCHEMA: &str = "sfm.game-instance/1";
pub const MAX_AUTHENTICATION_TOKEN_BYTES: usize = 256;
pub const MAX_ACTION_TOKENS: usize = 128;
pub const MAX_ACTION_TOKEN_BYTES: usize = 4096;
pub const MAX_ACTION_FEEDBACK_ENTRIES: usize = 64;
pub const MAX_REQUEST_ID_BYTES: usize = 128;
pub const MAX_EXPLORER_SELECTOR_BYTES: usize = 4096;
pub const MAX_EXPLORER_PATH_BYTES: usize = 16 * 1024;
pub const MAX_EXPLORER_SETTING_BYTES: usize = 256;
pub const MAX_EXPLORER_TARGET_RESULTS: usize = 256;
pub const MAX_EXPLORER_ROOTS_PER_TARGET: usize = 4096;
pub const MAX_EXPLORER_VISIBLE_PATHS: usize = 512;
pub const MAX_EXPLORER_FEEDBACK_ENTRIES: usize = 128;
pub const MAX_EXPLORER_EVIDENCE_TEXT_BYTES: usize = 16 * 1024;
pub const EXPLORER_CONTROL_CAPABILITY: &str = "explorer.control.v1";

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

    /// Execute one typed explorer query or mutation on the Minecraft client thread.
    async fn explorer_operation(
        &self,
        request: SfmControlExplorerOperationRequest,
    ) -> Result<SfmControlExplorerOperationResult, SfmControlError>;
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

/// Typed explorer operation transported without a recursive wire AST.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Facet)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum SfmControlExplorerOperation {
    List,
    Describe,
    RootList,
    RootAdd,
    RootRemove,
    ViewSet,
    SortSet,
    GroupSet,
    RootHoistSet,
    NodeExpand,
    NodeCollapse,
    NodeToggle,
    NodeRefresh,
}

impl SfmControlExplorerOperation {
    #[must_use]
    pub const fn uses_path(self) -> bool {
        matches!(
            self,
            Self::RootAdd
                | Self::RootRemove
                | Self::NodeExpand
                | Self::NodeCollapse
                | Self::NodeToggle
                | Self::NodeRefresh
        )
    }

    #[must_use]
    pub const fn uses_setting(self) -> bool {
        matches!(
            self,
            Self::ViewSet | Self::SortSet | Self::GroupSet | Self::RootHoistSet
        )
    }

    #[must_use]
    pub const fn supports_open_new(self) -> bool {
        matches!(self, Self::RootAdd)
    }
}

/// Explicit behavior when a set-valued explorer selector matches nothing.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Facet)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum SfmControlExplorerIfNoMatch {
    #[default]
    Fail,
    OpenNew,
}

/// Overall result of one all-or-none explorer operation.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Facet)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum SfmControlExplorerOperationStatus {
    Observed,
    Applied,
    Unchanged,
    NoMatch,
    Rejected,
}

impl SfmControlExplorerOperationStatus {
    /// Stable process status for a rendered explorer operation result.
    ///
    /// Successful observations and idempotent mutations use zero. No-match
    /// follows the query convention used by the SFM tooling, while a typed
    /// rejection uses the ordinary command-failure status.
    #[must_use]
    pub const fn cli_exit_code(self) -> u8 {
        match self {
            Self::Observed | Self::Applied | Self::Unchanged => 0,
            Self::NoMatch => 2,
            Self::Rejected => 1,
        }
    }
}

/// Result classification for one captured explorer target.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Facet)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum SfmControlExplorerTargetOutcome {
    Observed,
    Created,
    Changed,
    Unchanged,
    Rejected,
}

/// One bounded, generator-safe explorer request.
///
/// `explorer_selector` and `canonical_path` contain canonical text parsed by
/// Rust before transport and independently reparsed by Java. `setting_value`
/// is used only by the four typed projection operations.
#[derive(Clone, Debug, PartialEq, Eq, Facet)]
pub struct SfmControlExplorerOperationRequest {
    pub authentication_token: String,
    pub request_id: String,
    pub protocol_version: u16,
    pub operation: SfmControlExplorerOperation,
    pub explorer_selector: String,
    pub canonical_path: String,
    pub setting_value: String,
    pub if_no_match: SfmControlExplorerIfNoMatch,
}

/// Bounded state and revision evidence for one captured explorer target.
#[derive(Clone, Debug, PartialEq, Eq, Facet)]
#[allow(clippy::struct_excessive_bools)]
pub struct SfmControlExplorerTargetResult {
    pub explorer_id: String,
    pub outcome: SfmControlExplorerTargetOutcome,
    pub message: String,
    pub explorer_revision: i64,
    pub selection_revision_present: bool,
    pub selection_revision: i64,
    pub child_relation_revision_present: bool,
    pub child_relation_revision: i64,
    pub child_relation_request_present: bool,
    pub child_relation_request_id: i64,
    pub focused: bool,
    pub canonical_location_expression: String,
    pub canonical_roots: Vec<String>,
    pub view_id: String,
    pub sort_id: String,
    pub group_id: String,
    pub root_hoist_mode: String,
    pub visible_paths: Vec<String>,
    pub visible_row_count: u32,
    pub pending_request_count: u32,
    pub diagnostic_count: u32,
}

/// Bounded response carrying identity, target capture, revision, and UI evidence.
#[derive(Clone, Debug, PartialEq, Eq, Facet)]
#[allow(clippy::struct_excessive_bools)]
pub struct SfmControlExplorerOperationResult {
    pub instance_id: String,
    pub process_id: u32,
    pub request_id: String,
    pub operation: SfmControlExplorerOperation,
    pub explorer_selector: String,
    pub canonical_path: String,
    pub setting_value: String,
    pub if_no_match: SfmControlExplorerIfNoMatch,
    pub status: SfmControlExplorerOperationStatus,
    pub captured_target_count: u32,
    pub matched_target_count: u32,
    pub changed_target_count: u32,
    pub opened_target_count: u32,
    pub selection_revision_present: bool,
    pub selection_revision: i64,
    pub child_relation_revision_present: bool,
    pub child_relation_revision: i64,
    pub targets: Vec<SfmControlExplorerTargetResult>,
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

const EXPLORER_VIEW_IDS: &[&str] = &["sfm:list", "sfm:small_icons"];
const EXPLORER_SORT_IDS: &[&str] = &["sfm:name", "sfm:extension", "sfm:icon"];
const EXPLORER_GROUP_IDS: &[&str] = &["sfm:hierarchy", "sfm:none"];

/// Validate one frozen built-in explorer projection id.
///
/// # Errors
///
/// Returns an error for an oversized, unqualified, non-canonical, or currently
/// unsupported id.
pub fn validate_explorer_projection_id(value: &str) -> eyre::Result<()> {
    validate_canonical_explorer_projection_id(value)?;
    eyre::ensure!(
        EXPLORER_VIEW_IDS.contains(&value)
            || EXPLORER_SORT_IDS.contains(&value)
            || EXPLORER_GROUP_IDS.contains(&value),
        "unsupported explorer projection id `{value}`"
    );
    Ok(())
}

fn validate_canonical_explorer_projection_id(value: &str) -> eyre::Result<()> {
    validate_bounded_text(
        value,
        3,
        MAX_EXPLORER_SETTING_BYTES,
        "explorer projection id",
    )?;
    let Some((namespace, path)) = value.split_once(':') else {
        eyre::bail!("explorer projection id must be namespaced");
    };
    eyre::ensure!(
        !namespace.is_empty()
            && namespace.bytes().all(|byte| {
                byte.is_ascii_lowercase() || byte.is_ascii_digit() || b"_.-".contains(&byte)
            }),
        "explorer projection id namespace is not canonical"
    );
    eyre::ensure!(
        !path.is_empty()
            && path.bytes().all(|byte| {
                byte.is_ascii_lowercase() || byte.is_ascii_digit() || b"/._-".contains(&byte)
            }),
        "explorer projection id path is not canonical"
    );
    Ok(())
}

fn validate_supported_explorer_projection(
    value: &str,
    supported: &[&str],
    label: &str,
) -> eyre::Result<()> {
    validate_canonical_explorer_projection_id(value)?;
    eyre::ensure!(
        supported.contains(&value),
        "unsupported explorer {label} projection id `{value}`"
    );
    Ok(())
}

fn validate_explorer_projection_for_operation(
    operation: SfmControlExplorerOperation,
    value: &str,
) -> eyre::Result<()> {
    match operation {
        SfmControlExplorerOperation::ViewSet => {
            validate_supported_explorer_projection(value, EXPLORER_VIEW_IDS, "view")
        }
        SfmControlExplorerOperation::SortSet => {
            validate_supported_explorer_projection(value, EXPLORER_SORT_IDS, "sort")
        }
        SfmControlExplorerOperation::GroupSet => {
            validate_supported_explorer_projection(value, EXPLORER_GROUP_IDS, "group")
        }
        _ => eyre::bail!("operation does not select an explorer projection"),
    }
}

/// Validate one explorer request before transport allocation or dispatch.
///
/// # Errors
///
/// Returns an error for oversized fields, non-canonical selector/path text,
/// operation/argument mismatches, or an unsafe exact-id/open-new combination.
pub fn validate_explorer_operation_request(
    request: &SfmControlExplorerOperationRequest,
) -> eyre::Result<()> {
    validate_bounded_text(
        &request.authentication_token,
        1,
        MAX_AUTHENTICATION_TOKEN_BYTES,
        "authentication token",
    )?;
    validate_bounded_text(&request.request_id, 1, MAX_REQUEST_ID_BYTES, "request id")?;
    eyre::ensure!(
        request.protocol_version == CONTROL_PROTOCOL_VERSION,
        "unsupported control protocol version"
    );
    validate_bounded_text(
        &request.explorer_selector,
        1,
        MAX_EXPLORER_SELECTOR_BYTES,
        "explorer selector",
    )?;
    let selector = EntitySelector::parse(SelectorDomain::Explorer, &request.explorer_selector)?;
    eyre::ensure!(
        selector.canonical() == request.explorer_selector,
        "explorer selector is not canonical"
    );

    if request.operation.uses_path() {
        validate_bounded_text(
            &request.canonical_path,
            1,
            MAX_EXPLORER_PATH_BYTES,
            "canonical path",
        )?;
        let path = SfmPath::parse(&request.canonical_path)?;
        eyre::ensure!(
            path.canonical() == request.canonical_path,
            "explorer path is not canonical"
        );
    } else {
        eyre::ensure!(
            request.canonical_path.is_empty(),
            "operation does not accept a path"
        );
    }

    if request.operation.uses_setting() {
        validate_bounded_text(
            &request.setting_value,
            1,
            MAX_EXPLORER_SETTING_BYTES,
            "setting value",
        )?;
        eyre::ensure!(
            !request.setting_value.chars().any(char::is_whitespace),
            "setting value contains whitespace"
        );
    } else {
        eyre::ensure!(
            request.setting_value.is_empty(),
            "operation does not accept a setting value"
        );
    }
    match request.operation {
        SfmControlExplorerOperation::ViewSet
        | SfmControlExplorerOperation::SortSet
        | SfmControlExplorerOperation::GroupSet => {
            validate_explorer_projection_for_operation(request.operation, &request.setting_value)?;
        }
        SfmControlExplorerOperation::RootHoistSet => eyre::ensure!(
            matches!(request.setting_value.as_str(), "auto" | "show-roots"),
            "root hoist setting must be auto or show-roots"
        ),
        _ => {}
    }

    if request.if_no_match == SfmControlExplorerIfNoMatch::OpenNew {
        eyre::ensure!(
            request.operation.supports_open_new(),
            "open-new is supported only by root add"
        );
        eyre::ensure!(
            !selector.is_exact_identity(),
            "open-new cannot be combined with an exact explorer id"
        );
    }
    Ok(())
}

/// Validate an explorer result against the exact request and selected game.
///
/// # Errors
///
/// Returns an error for correlation mismatches, oversized evidence, duplicate
/// target ids, malformed canonical paths/expressions, or inconsistent counts.
pub fn validate_explorer_operation_result(
    result: &SfmControlExplorerOperationResult,
    request: &SfmControlExplorerOperationRequest,
    expected_instance_id: &str,
    expected_process_id: u32,
) -> eyre::Result<()> {
    eyre::ensure!(
        result.instance_id == expected_instance_id,
        "instance id mismatch"
    );
    eyre::ensure!(
        result.process_id == expected_process_id,
        "process id mismatch"
    );
    eyre::ensure!(
        result.request_id == request.request_id,
        "request id mismatch"
    );
    eyre::ensure!(result.operation == request.operation, "operation mismatch");
    eyre::ensure!(
        result.explorer_selector == request.explorer_selector,
        "explorer selector mismatch"
    );
    eyre::ensure!(
        result.canonical_path == request.canonical_path,
        "canonical path mismatch"
    );
    eyre::ensure!(
        result.setting_value == request.setting_value,
        "setting value mismatch"
    );
    eyre::ensure!(
        result.if_no_match == request.if_no_match,
        "if-no-match policy mismatch"
    );
    eyre::ensure!(
        result.targets.len() <= MAX_EXPLORER_TARGET_RESULTS,
        "too many explorer target results"
    );
    eyre::ensure!(
        usize::try_from(result.captured_target_count).ok() == Some(result.targets.len()),
        "captured target count does not match target evidence"
    );
    eyre::ensure!(
        result.changed_target_count <= result.captured_target_count,
        "changed target count exceeds captured targets"
    );
    eyre::ensure!(
        result.opened_target_count <= result.captured_target_count,
        "opened target count exceeds captured targets"
    );
    eyre::ensure!(
        result.feedback.len() <= MAX_EXPLORER_FEEDBACK_ENTRIES,
        "too many explorer feedback entries"
    );
    for feedback in &result.feedback {
        validate_bounded_text(
            feedback,
            0,
            MAX_EXPLORER_EVIDENCE_TEXT_BYTES,
            "feedback entry",
        )?;
    }

    let mut target_ids = BTreeSet::new();
    eyre::ensure!(
        result.selection_revision >= 0,
        "negative selection revision"
    );
    eyre::ensure!(
        result.child_relation_revision >= 0,
        "negative child relation revision"
    );
    for target in &result.targets {
        validate_explorer_target_result(target)?;
        eyre::ensure!(
            target_ids.insert(target.explorer_id.as_str()),
            "duplicate explorer target id"
        );
    }
    validate_bounded_text(
        &result.resulting_screen,
        0,
        MAX_EXPLORER_EVIDENCE_TEXT_BYTES,
        "resulting screen",
    )?;
    if !result.resulting_screen_present {
        eyre::ensure!(
            result.resulting_screen.is_empty(),
            "absent resulting screen must have empty text"
        );
    }
    Ok(())
}

fn validate_explorer_target_result(target: &SfmControlExplorerTargetResult) -> eyre::Result<()> {
    eyre::ensure!(target.explorer_revision >= 0, "negative explorer revision");
    eyre::ensure!(
        target.selection_revision >= 0,
        "negative selection revision"
    );
    eyre::ensure!(
        target.child_relation_revision >= 0,
        "negative child relation revision"
    );
    eyre::ensure!(
        target.child_relation_request_id >= 0,
        "negative child relation request id"
    );
    eyre::ensure!(
        target.child_relation_request_present == (target.child_relation_request_id > 0),
        "child relation request presence does not match its id"
    );
    validate_bounded_text(
        &target.explorer_id,
        1,
        MAX_EXPLORER_SETTING_BYTES,
        "explorer id",
    )?;
    validate_bounded_text(
        &target.message,
        0,
        MAX_EXPLORER_EVIDENCE_TEXT_BYTES,
        "target message",
    )?;
    validate_bounded_text(
        &target.canonical_location_expression,
        1,
        MAX_EXPLORER_PATH_BYTES,
        "location expression",
    )?;
    let location = PathExpression::parse(&target.canonical_location_expression)?;
    eyre::ensure!(
        location.canonical() == target.canonical_location_expression,
        "location expression is not canonical"
    );
    eyre::ensure!(
        target.canonical_roots.len() <= MAX_EXPLORER_ROOTS_PER_TARGET,
        "too many explorer roots"
    );
    for root in &target.canonical_roots {
        validate_bounded_text(root, 1, MAX_EXPLORER_PATH_BYTES, "explorer root")?;
        let path = SfmPath::parse(root)?;
        eyre::ensure!(path.canonical() == *root, "explorer root is not canonical");
    }
    for (value, label) in [
        (&target.view_id, "view id"),
        (&target.sort_id, "sort id"),
        (&target.group_id, "group id"),
        (&target.root_hoist_mode, "root hoist mode"),
    ] {
        validate_bounded_text(value, 1, MAX_EXPLORER_SETTING_BYTES, label)?;
    }
    validate_supported_explorer_projection(&target.view_id, EXPLORER_VIEW_IDS, "view")?;
    validate_supported_explorer_projection(&target.sort_id, EXPLORER_SORT_IDS, "sort")?;
    validate_supported_explorer_projection(&target.group_id, EXPLORER_GROUP_IDS, "group")?;
    eyre::ensure!(
        matches!(target.root_hoist_mode.as_str(), "auto" | "show-roots"),
        "target root hoist mode is invalid"
    );
    eyre::ensure!(
        target.visible_paths.len() <= MAX_EXPLORER_VISIBLE_PATHS,
        "too many visible explorer paths"
    );
    eyre::ensure!(
        usize::try_from(target.visible_row_count).ok() == Some(target.visible_paths.len()),
        "visible row count does not match visible path evidence"
    );
    for visible_path in &target.visible_paths {
        validate_bounded_text(
            visible_path,
            1,
            MAX_EXPLORER_PATH_BYTES,
            "visible explorer path",
        )?;
        let path = SfmPath::parse(visible_path)?;
        eyre::ensure!(
            path.canonical() == *visible_path,
            "visible explorer path is not canonical"
        );
    }
    Ok(())
}

fn validate_bounded_text(
    value: &str,
    minimum_bytes: usize,
    maximum_bytes: usize,
    label: &str,
) -> eyre::Result<()> {
    eyre::ensure!(
        value.len() >= minimum_bytes && value.len() <= maximum_bytes,
        "{label} length is outside {minimum_bytes}..={maximum_bytes} bytes"
    );
    eyre::ensure!(!value.contains('\0'), "{label} contains NUL");
    eyre::ensure!(
        !value.chars().any(char::is_control),
        "{label} contains a control character"
    );
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn explorer_request() -> SfmControlExplorerOperationRequest {
        SfmControlExplorerOperationRequest {
            authentication_token: "secret".to_owned(),
            request_id: "request-1".to_owned(),
            protocol_version: CONTROL_PROTOCOL_VERSION,
            operation: SfmControlExplorerOperation::RootAdd,
            explorer_selector: "focused".to_owned(),
            canonical_path: "file:///D:/Repos/SFM".to_owned(),
            setting_value: String::new(),
            if_no_match: SfmControlExplorerIfNoMatch::OpenNew,
        }
    }

    fn explorer_target() -> SfmControlExplorerTargetResult {
        SfmControlExplorerTargetResult {
            explorer_id: "explorer-1".to_owned(),
            outcome: SfmControlExplorerTargetOutcome::Created,
            message: "opened a generic explorer".to_owned(),
            explorer_revision: 7,
            selection_revision_present: true,
            selection_revision: 11,
            child_relation_revision_present: true,
            child_relation_revision: 13,
            child_relation_request_present: true,
            child_relation_request_id: 17,
            focused: true,
            canonical_location_expression: "file:///D:/Repos/SFM".to_owned(),
            canonical_roots: vec!["file:///D:/Repos/SFM".to_owned()],
            view_id: "sfm:list".to_owned(),
            sort_id: "sfm:name".to_owned(),
            group_id: "sfm:hierarchy".to_owned(),
            root_hoist_mode: "auto".to_owned(),
            visible_paths: vec!["file:///D:/Repos/SFM".to_owned()],
            visible_row_count: 1,
            pending_request_count: 0,
            diagnostic_count: 0,
        }
    }

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

    #[test]
    fn explorer_requests_require_canonical_bounded_arguments() {
        let request = explorer_request();
        validate_explorer_operation_request(&request).expect("valid explorer request");

        let mut exact_open_new = request.clone();
        exact_open_new.explorer_selector = "id(explorer-1)".to_owned();
        let failure = validate_explorer_operation_request(&exact_open_new)
            .expect_err("exact id must not create a replacement");
        assert!(failure.to_string().contains("exact explorer id"));

        let mut noncanonical = request.clone();
        noncanonical.canonical_path = "file:///d:/Repos/SFM".to_owned();
        let _ = validate_explorer_operation_request(&noncanonical)
            .expect_err("lowercase drive spelling is non-canonical");

        let mut unrelated_setting = request;
        unrelated_setting.setting_value = "list".to_owned();
        let _ = validate_explorer_operation_request(&unrelated_setting)
            .expect_err("root add must not carry projection state");
    }

    #[test]
    fn explorer_node_requests_use_the_bounded_canonical_path_slot() {
        for operation in [
            SfmControlExplorerOperation::NodeExpand,
            SfmControlExplorerOperation::NodeCollapse,
            SfmControlExplorerOperation::NodeToggle,
            SfmControlExplorerOperation::NodeRefresh,
        ] {
            let mut request = explorer_request();
            request.operation = operation;
            request.if_no_match = SfmControlExplorerIfNoMatch::Fail;
            validate_explorer_operation_request(&request).expect("valid node request");

            request.canonical_path = String::new();
            let failure = validate_explorer_operation_request(&request)
                .expect_err("node operation requires a path");
            assert!(failure.to_string().contains("canonical path"));
        }
    }

    #[test]
    fn explorer_projection_requests_are_frozen_to_java_builtins() {
        for (operation, accepted) in [
            (
                SfmControlExplorerOperation::ViewSet,
                &["sfm:list", "sfm:small_icons"][..],
            ),
            (
                SfmControlExplorerOperation::SortSet,
                &["sfm:name", "sfm:extension", "sfm:icon"][..],
            ),
            (
                SfmControlExplorerOperation::GroupSet,
                &["sfm:hierarchy", "sfm:none"][..],
            ),
            (
                SfmControlExplorerOperation::RootHoistSet,
                &["auto", "show-roots"][..],
            ),
        ] {
            for value in accepted {
                let mut request = explorer_request();
                request.operation = operation;
                request.canonical_path.clear();
                request.setting_value = (*value).to_owned();
                request.if_no_match = SfmControlExplorerIfNoMatch::Fail;
                validate_explorer_operation_request(&request)
                    .expect("frozen registry contribution id should be accepted");
            }
        }

        for (operation, old_id) in [
            (SfmControlExplorerOperation::ViewSet, "list"),
            (SfmControlExplorerOperation::ViewSet, "small-icons"),
            (SfmControlExplorerOperation::SortSet, "name"),
            (SfmControlExplorerOperation::SortSet, "extension"),
            (SfmControlExplorerOperation::SortSet, "icon"),
            (SfmControlExplorerOperation::GroupSet, "hierarchy"),
            (SfmControlExplorerOperation::GroupSet, "none"),
            (SfmControlExplorerOperation::GroupSet, "path-hierarchy"),
        ] {
            let mut legacy = explorer_request();
            legacy.operation = operation;
            legacy.canonical_path.clear();
            legacy.setting_value = old_id.to_owned();
            legacy.if_no_match = SfmControlExplorerIfNoMatch::Fail;
            let _ = validate_explorer_operation_request(&legacy)
                .expect_err("unqualified compatibility alias must be rejected");
        }

        for (operation, unsupported) in [
            (SfmControlExplorerOperation::ViewSet, "addon:grid"),
            (SfmControlExplorerOperation::ViewSet, "sfm:name"),
            (SfmControlExplorerOperation::SortSet, "addon:name"),
            (SfmControlExplorerOperation::SortSet, "sfm:list"),
            (SfmControlExplorerOperation::GroupSet, "addon:hierarchy"),
            (SfmControlExplorerOperation::GroupSet, "sfm:icon"),
        ] {
            let mut extension = explorer_request();
            extension.operation = operation;
            extension.canonical_path.clear();
            extension.setting_value = unsupported.to_owned();
            extension.if_no_match = SfmControlExplorerIfNoMatch::Fail;
            let failure = validate_explorer_operation_request(&extension)
                .expect_err("canonical but unsupported projection must be rejected");
            assert!(failure.to_string().contains("unsupported explorer"));
        }

        for malformed in [
            "SFM:list",
            "sfm:",
            ":list",
            "sfm:bad value",
            "sfm:bad:value",
        ] {
            let _ = validate_explorer_projection_id(malformed)
                .expect_err("non-canonical contribution id must fail closed");
        }

        let failure = validate_explorer_projection_id("addon:grid")
            .expect_err("canonical extension id is unsupported in the frozen slice");
        assert!(
            failure
                .to_string()
                .contains("unsupported explorer projection")
        );
    }

    #[test]
    fn explorer_result_projection_ids_are_frozen_to_java_builtins() {
        let mut target = explorer_target();
        target.view_id = "addon:grid".to_owned();
        let failure = validate_explorer_target_result(&target)
            .expect_err("canonical but unsupported result view must be rejected");
        assert!(failure.to_string().contains("unsupported explorer view"));

        let mut target = explorer_target();
        target.sort_id = "sfm:list".to_owned();
        let failure = validate_explorer_target_result(&target)
            .expect_err("a view id must not be accepted as a sort id");
        assert!(failure.to_string().contains("unsupported explorer sort"));

        let mut target = explorer_target();
        target.group_id = "addon:hierarchy".to_owned();
        let failure = validate_explorer_target_result(&target)
            .expect_err("canonical but unsupported result group must be rejected");
        assert!(failure.to_string().contains("unsupported explorer group"));
    }

    #[test]
    fn explorer_operation_statuses_have_stable_cli_exit_codes() {
        assert_eq!(
            SfmControlExplorerOperationStatus::Observed.cli_exit_code(),
            0
        );
        assert_eq!(
            SfmControlExplorerOperationStatus::Applied.cli_exit_code(),
            0
        );
        assert_eq!(
            SfmControlExplorerOperationStatus::Unchanged.cli_exit_code(),
            0
        );
        assert_eq!(
            SfmControlExplorerOperationStatus::NoMatch.cli_exit_code(),
            2
        );
        assert_eq!(
            SfmControlExplorerOperationStatus::Rejected.cli_exit_code(),
            1
        );
    }

    #[test]
    fn explorer_results_are_correlated_and_bounded() {
        let request = explorer_request();
        let target = explorer_target();
        let result = SfmControlExplorerOperationResult {
            instance_id: "instance-1".to_owned(),
            process_id: 42,
            request_id: request.request_id.clone(),
            operation: request.operation,
            explorer_selector: request.explorer_selector.clone(),
            canonical_path: request.canonical_path.clone(),
            setting_value: request.setting_value.clone(),
            if_no_match: request.if_no_match,
            status: SfmControlExplorerOperationStatus::Applied,
            captured_target_count: 1,
            matched_target_count: 0,
            changed_target_count: 1,
            opened_target_count: 1,
            selection_revision_present: true,
            selection_revision: 11,
            child_relation_revision_present: true,
            child_relation_revision: 13,
            targets: vec![target.clone()],
            feedback: vec!["root added".to_owned()],
            resulting_screen_present: true,
            resulting_screen: "ca.teamdman.sfm.client.screen.SFMWorkspaceScreen".to_owned(),
            workspace_present: true,
            workspace_panel_count: 1,
        };
        validate_explorer_operation_result(&result, &request, "instance-1", 42)
            .expect("valid result");

        let mut duplicate = result;
        duplicate.targets.push(target);
        duplicate.captured_target_count = 2;
        let failure = validate_explorer_operation_result(&duplicate, &request, "instance-1", 42)
            .expect_err("duplicate target ids are ambiguous");
        assert!(failure.to_string().contains("duplicate explorer target id"));
    }

    #[test]
    fn explorer_visible_path_evidence_is_bounded_canonical_and_counted() {
        let mut target = explorer_target();
        validate_explorer_target_result(&target).expect("valid visible path evidence");

        target.visible_paths = vec!["registry://minecraft/item/".to_owned(); 513];
        target.visible_row_count = 513;
        let failure = validate_explorer_target_result(&target)
            .expect_err("visible path evidence must be bounded");
        assert!(
            failure
                .to_string()
                .contains("too many visible explorer paths")
        );

        target = explorer_target();
        target.visible_row_count = 2;
        let failure = validate_explorer_target_result(&target)
            .expect_err("visible path count must match evidence");
        assert!(failure.to_string().contains("visible row count"));

        target = explorer_target();
        target.visible_paths[0] = "file:///d:/Repos/SFM".to_owned();
        let failure =
            validate_explorer_target_result(&target).expect_err("visible paths must be canonical");
        assert!(failure.to_string().contains("visible explorer path"));
    }
}
