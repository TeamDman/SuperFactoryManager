use super::DEFINITION_AT_POSITION_RESULT_SCHEMA;
use super::DefinitionAtPositionRequest;
use super::DefinitionAtPositionResult;
use super::SYMBOL_SERVER_CANCEL_SCHEMA;
use super::SYMBOL_SERVER_DEFINITION_SCHEMA;
use super::SYMBOL_SERVER_ERROR_SCHEMA;
use super::SYMBOL_SERVER_HELLO_SCHEMA;
use super::SYMBOL_SERVER_PING_SCHEMA;
use super::SYMBOL_SERVER_PROTOCOL_SCHEMA;
use super::SYMBOL_SERVER_SHUTDOWN_SCHEMA;
use super::SYMBOL_SERVER_USAGE_AT_POSITION_SCHEMA;
use super::SYMBOL_SERVER_WORKSPACE_GENERATION_SCHEMA;
use super::SymbolServerCancellationOutput;
use super::SymbolServerCancellationStatus;
use super::SymbolServerCapability;
use super::SymbolServerClientFrame;
use super::SymbolServerDefinitionCancelledOutput;
use super::SymbolServerDefinitionErrorOutput;
use super::SymbolServerErrorDisposition;
use super::SymbolServerErrorOutput;
use super::SymbolServerFrame;
use super::SymbolServerHelloOutput;
use super::SymbolServerUsageAtPositionCancelledOutput;
use super::SymbolServerUsageAtPositionErrorOutput;
use super::SymbolServerWorkspaceGenerationOutput;
use super::SymbolServerWorkspaceOutput;
use super::USAGE_AT_POSITION_RESULT_SCHEMA;
use super::UsageAtPositionRequest;
use super::UsageAtPositionResult;
use crate::cancellation::CancellationToken;
use std::collections::BTreeMap;
use std::fmt;

pub const DEFAULT_SYMBOL_SERVER_MAX_PENDING_DEFINITIONS: usize = 8;
pub const DEFAULT_SYMBOL_SERVER_MAX_PRE_CANCELLED_REQUESTS: usize = 32;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct SymbolServerLimits {
    pub max_frame_bytes: usize,
    pub max_pending_definitions: usize,
    pub max_pre_cancelled_requests: usize,
}

impl Default for SymbolServerLimits {
    fn default() -> Self {
        Self {
            max_frame_bytes: super::DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES,
            max_pending_definitions: DEFAULT_SYMBOL_SERVER_MAX_PENDING_DEFINITIONS,
            max_pre_cancelled_requests: DEFAULT_SYMBOL_SERVER_MAX_PRE_CANCELLED_REQUESTS,
        }
    }
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct SymbolServerIdentity {
    pub server_name: String,
    pub server_version: String,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum SymbolServerPhase {
    AwaitingHello,
    Ready,
    Draining,
    Closed,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq, Ord, PartialOrd)]
pub struct SymbolServerRequestKey {
    pub request_id: u64,
    pub request_generation: u64,
    pub workspace_generation: u64,
}

impl From<&DefinitionAtPositionRequest> for SymbolServerRequestKey {
    fn from(request: &DefinitionAtPositionRequest) -> Self {
        Self {
            request_id: request.request_id,
            request_generation: request.request_generation,
            workspace_generation: request.workspace.workspace_generation,
        }
    }
}

impl From<&UsageAtPositionRequest> for SymbolServerRequestKey {
    fn from(request: &UsageAtPositionRequest) -> Self {
        Self {
            request_id: request.request_id,
            request_generation: request.request_generation,
            workspace_generation: request.workspace.workspace_generation,
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum SymbolServerRequestKind {
    Definition,
    UsageAtPosition,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct SymbolServerEngineError {
    pub code: String,
    pub message: String,
    pub retryable: bool,
}

impl SymbolServerEngineError {
    #[must_use]
    pub fn new(code: impl Into<String>, message: impl Into<String>, retryable: bool) -> Self {
        Self {
            code: code.into(),
            message: message.into(),
            retryable,
        }
    }
}

#[derive(Clone, Debug)]
pub enum SymbolServerEffect {
    Send(Box<SymbolServerFrame>),
    StartDefinition {
        request: Box<DefinitionAtPositionRequest>,
        cancellation_token: CancellationToken,
    },
    StartUsageAtPosition {
        request: Box<UsageAtPositionRequest>,
        cancellation_token: CancellationToken,
    },
}

impl SymbolServerEffect {
    fn send(frame: SymbolServerFrame) -> Self {
        Self::Send(Box::new(frame))
    }
}

#[derive(Debug, Eq, PartialEq)]
pub enum SymbolServerConfigurationError {
    BlankServerName,
    BlankServerVersion,
    ZeroMaximumFrameBytes,
    FrameMaximumExceedsU32,
    ZeroMaximumPendingDefinitions,
    ZeroMaximumPreCancelledRequests,
    InvalidWorkspace,
}

impl fmt::Display for SymbolServerConfigurationError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::BlankServerName => f.write_str("symbol-server name must not be blank"),
            Self::BlankServerVersion => f.write_str("symbol-server version must not be blank"),
            Self::ZeroMaximumFrameBytes => {
                f.write_str("symbol-server maximum frame bytes must be positive")
            }
            Self::FrameMaximumExceedsU32 => {
                f.write_str("symbol-server maximum frame bytes must be representable by u32")
            }
            Self::ZeroMaximumPendingDefinitions => {
                f.write_str("symbol-server maximum pending definitions must be positive")
            }
            Self::ZeroMaximumPreCancelledRequests => {
                f.write_str("symbol-server maximum pre-cancelled requests must be positive")
            }
            Self::InvalidWorkspace => f.write_str("symbol-server workspace identity is invalid"),
        }
    }
}

impl std::error::Error for SymbolServerConfigurationError {}

struct PendingRequest {
    key: SymbolServerRequestKey,
    kind: SymbolServerRequestKind,
    cancellation_token: CancellationToken,
    terminal_sent: bool,
}

struct PreCancelledRequest {
    key: SymbolServerRequestKey,
    reason: String,
}

/// Pure lifecycle coordinator for one symbol-server connection.
///
/// The state machine owns no threads or I/O. Callers execute its typed start
/// effects and feed eventual completions back through the matching finish
/// method.
pub struct SymbolServerState {
    identity: SymbolServerIdentity,
    limits: SymbolServerLimits,
    parent_cancellation_token: CancellationToken,
    phase: SymbolServerPhase,
    workspace: SymbolServerWorkspaceOutput,
    highest_request_id: Option<u64>,
    negotiated_max_frame_bytes: usize,
    negotiated_capabilities: Vec<SymbolServerCapability>,
    pending: BTreeMap<u64, PendingRequest>,
    pre_cancelled: BTreeMap<u64, PreCancelledRequest>,
    send_shutdown_ack_after_drain: bool,
}

impl SymbolServerState {
    /// Construct a disconnected worker state with validated hard limits.
    ///
    /// # Errors
    ///
    /// Returns an error for blank identity fields or zero/unrepresentable limits.
    pub fn new(
        identity: SymbolServerIdentity,
        workspace: SymbolServerWorkspaceOutput,
        limits: SymbolServerLimits,
        parent_cancellation_token: CancellationToken,
    ) -> Result<Self, SymbolServerConfigurationError> {
        if identity.server_name.trim().is_empty() {
            return Err(SymbolServerConfigurationError::BlankServerName);
        }
        if identity.server_version.trim().is_empty() {
            return Err(SymbolServerConfigurationError::BlankServerVersion);
        }
        if limits.max_frame_bytes == 0 {
            return Err(SymbolServerConfigurationError::ZeroMaximumFrameBytes);
        }
        if u32::try_from(limits.max_frame_bytes).is_err() {
            return Err(SymbolServerConfigurationError::FrameMaximumExceedsU32);
        }
        if limits.max_pending_definitions == 0 {
            return Err(SymbolServerConfigurationError::ZeroMaximumPendingDefinitions);
        }
        if limits.max_pre_cancelled_requests == 0 {
            return Err(SymbolServerConfigurationError::ZeroMaximumPreCancelledRequests);
        }
        workspace
            .validate()
            .map_err(|_validation_error| SymbolServerConfigurationError::InvalidWorkspace)?;
        Ok(Self {
            identity,
            limits,
            parent_cancellation_token,
            phase: SymbolServerPhase::AwaitingHello,
            workspace,
            highest_request_id: None,
            negotiated_max_frame_bytes: limits.max_frame_bytes,
            negotiated_capabilities: Vec::new(),
            pending: BTreeMap::new(),
            pre_cancelled: BTreeMap::new(),
            send_shutdown_ack_after_drain: false,
        })
    }

    #[must_use]
    pub const fn phase(&self) -> SymbolServerPhase {
        self.phase
    }

    #[must_use]
    pub fn pending_definition_count(&self) -> usize {
        self.pending
            .values()
            .filter(|pending| pending.kind == SymbolServerRequestKind::Definition)
            .count()
    }

    #[must_use]
    pub fn pending_request_count(&self) -> usize {
        self.pending.len()
    }

    #[must_use]
    pub const fn negotiated_max_frame_bytes(&self) -> usize {
        self.negotiated_max_frame_bytes
    }

    #[must_use]
    pub const fn served_workspace(&self) -> &SymbolServerWorkspaceOutput {
        &self.workspace
    }

    /// Accept one decoded client frame and return deterministic I/O/engine effects.
    #[must_use]
    pub fn handle_frame(&mut self, frame: SymbolServerClientFrame) -> Vec<SymbolServerEffect> {
        if self.phase == SymbolServerPhase::Closed {
            return Vec::new();
        }
        if let Err(error) = frame.validate_schema() {
            return self.fatal_protocol_error("schema-mismatch", error.to_string());
        }
        match self.phase {
            SymbolServerPhase::AwaitingHello => match frame {
                SymbolServerClientFrame::Hello { hello, .. } => self.handle_hello(&hello),
                _ => self.fatal_protocol_error(
                    "hello-required",
                    "symbol-server hello must be the first client frame",
                ),
            },
            SymbolServerPhase::Ready => match frame {
                SymbolServerClientFrame::Hello { .. } => self.fatal_protocol_error(
                    "duplicate-hello",
                    "symbol-server hello may only be sent once",
                ),
                SymbolServerClientFrame::Definition { request, .. } => {
                    self.handle_definition(*request)
                }
                SymbolServerClientFrame::UsageAtPosition { request, .. } => {
                    self.handle_usage_at_position(*request)
                }
                SymbolServerClientFrame::Cancel {
                    request_id,
                    request_generation,
                    workspace_generation,
                    reason,
                    ..
                } => self.handle_cancel(
                    SymbolServerRequestKey {
                        request_id,
                        request_generation,
                        workspace_generation,
                    },
                    reason,
                ),
                SymbolServerClientFrame::WorkspaceGeneration {
                    workspace_generation,
                    ..
                } => self.handle_workspace_generation(workspace_generation),
                SymbolServerClientFrame::Ping { nonce, .. } => {
                    vec![SymbolServerEffect::send(SymbolServerFrame::Pong {
                        schema: SYMBOL_SERVER_PING_SCHEMA.to_owned(),
                        nonce,
                    })]
                }
                SymbolServerClientFrame::Shutdown { reason, .. } => {
                    self.begin_drain(reason, true, true)
                }
            },
            SymbolServerPhase::Draining => vec![Self::protocol_error_effect(
                "server-shutting-down",
                "symbol-server is draining active requests",
                SymbolServerErrorDisposition::Request,
                None,
                None,
            )],
            SymbolServerPhase::Closed => Vec::new(),
        }
    }

    /// Notify the state machine that the peer closed stdin.
    ///
    /// No response frames are emitted because no peer remains to consume them.
    #[must_use]
    pub fn handle_eof(&mut self) -> Vec<SymbolServerEffect> {
        if matches!(
            self.phase,
            SymbolServerPhase::Draining | SymbolServerPhase::Closed
        ) {
            return Vec::new();
        }
        self.begin_drain("symbol-server client reached EOF", false, false)
    }

    /// Convert a framing/decoding failure into one typed fatal response and
    /// begin cancelling active work.
    #[must_use]
    pub fn handle_transport_failure(
        &mut self,
        message: impl Into<String>,
    ) -> Vec<SymbolServerEffect> {
        if self.phase == SymbolServerPhase::Closed {
            return Vec::new();
        }
        self.fatal_protocol_error("invalid-frame", message)
    }

    /// Begin host-initiated shutdown, such as Ctrl+C, without promising a
    /// protocol acknowledgement to a client which may no longer be readable.
    #[must_use]
    pub fn handle_host_shutdown(&mut self, reason: impl Into<String>) -> Vec<SymbolServerEffect> {
        self.begin_drain(reason, false, false)
    }

    /// Finish a previously emitted definition effect.
    ///
    /// Late completions after cancellation are consumed without a second
    /// terminal response.
    #[must_use]
    pub fn finish_definition(
        &mut self,
        key: SymbolServerRequestKey,
        completion: Result<DefinitionAtPositionResult, SymbolServerEngineError>,
    ) -> Vec<SymbolServerEffect> {
        let Some(pending) = self.pending.remove(&key.request_id) else {
            return Vec::new();
        };
        if pending.key != key || pending.kind != SymbolServerRequestKind::Definition {
            self.pending.insert(pending.key.request_id, pending);
            return self.fatal_protocol_error(
                "engine-completion-key-mismatch",
                "definition engine completion identity did not match its pending request",
            );
        }
        let mut effects = Vec::new();
        if !pending.terminal_sent {
            match completion {
                Ok(result) if definition_result_matches_key(&result, key) => {
                    effects.push(SymbolServerEffect::send(
                        SymbolServerFrame::DefinitionResult {
                            schema: SYMBOL_SERVER_DEFINITION_SCHEMA.to_owned(),
                            result: Box::new(result),
                        },
                    ));
                }
                Ok(_) => {
                    effects.push(Self::definition_error_effect(
                        key,
                        "engine-result-identity-mismatch",
                        "definition engine returned a result for a different request",
                        false,
                    ));
                    effects.extend(self.fatal_protocol_error(
                        "engine-result-identity-mismatch",
                        "definition engine returned a result for a different request",
                    ));
                }
                Err(error) => effects.push(Self::definition_error_effect(
                    key,
                    error.code,
                    error.message,
                    error.retryable,
                )),
            }
        }
        effects.extend(self.finish_drain_if_ready());
        effects
    }

    /// Finish a previously emitted usage-at-position effect.
    ///
    /// Late completions after cancellation are consumed without a second
    /// terminal response.
    #[must_use]
    pub fn finish_usage_at_position(
        &mut self,
        key: SymbolServerRequestKey,
        completion: Result<UsageAtPositionResult, SymbolServerEngineError>,
    ) -> Vec<SymbolServerEffect> {
        let Some(pending) = self.pending.remove(&key.request_id) else {
            return Vec::new();
        };
        if pending.key != key || pending.kind != SymbolServerRequestKind::UsageAtPosition {
            self.pending.insert(pending.key.request_id, pending);
            return self.fatal_protocol_error(
                "engine-completion-key-mismatch",
                "usage-at-position engine completion identity did not match its pending request",
            );
        }
        let mut effects = Vec::new();
        if !pending.terminal_sent {
            match completion {
                Ok(result) if usage_at_position_result_matches_key(&result, key) => {
                    effects.push(SymbolServerEffect::send(
                        SymbolServerFrame::UsageAtPositionResult {
                            schema: SYMBOL_SERVER_USAGE_AT_POSITION_SCHEMA.to_owned(),
                            result: Box::new(result),
                        },
                    ));
                }
                Ok(_) => {
                    effects.push(Self::usage_at_position_error_effect(
                        key,
                        "engine-result-identity-mismatch",
                        "usage-at-position engine returned a result for a different request",
                        false,
                    ));
                    effects.extend(self.fatal_protocol_error(
                        "engine-result-identity-mismatch",
                        "usage-at-position engine returned a result for a different request",
                    ));
                }
                Err(error) => effects.push(Self::usage_at_position_error_effect(
                    key,
                    error.code,
                    error.message,
                    error.retryable,
                )),
            }
        }
        effects.extend(self.finish_drain_if_ready());
        effects
    }

    fn handle_hello(&mut self, hello: &super::SymbolServerClientHello) -> Vec<SymbolServerEffect> {
        if hello.protocol_schema != SYMBOL_SERVER_PROTOCOL_SCHEMA {
            return self.fatal_protocol_error(
                "protocol-schema-mismatch",
                format!(
                    "client protocol `{}` does not match `{SYMBOL_SERVER_PROTOCOL_SCHEMA}`",
                    hello.protocol_schema
                ),
            );
        }
        if hello.client_name.trim().is_empty() || hello.client_version.trim().is_empty() {
            return self.fatal_protocol_error(
                "invalid-client-identity",
                "symbol-server client name and version must not be blank",
            );
        }
        if !hello
            .capabilities
            .contains(&SymbolServerCapability::DefinitionAtPosition)
        {
            return self.fatal_protocol_error(
                "missing-definition-capability",
                "symbol-server client did not advertise definition-at-position",
            );
        }
        let Ok(client_maximum) = usize::try_from(hello.max_frame_bytes) else {
            return self.fatal_protocol_error(
                "invalid-frame-maximum",
                "client maximum frame bytes does not fit this platform",
            );
        };
        if client_maximum == 0 {
            return self.fatal_protocol_error(
                "invalid-frame-maximum",
                "client maximum frame bytes must be positive",
            );
        }
        self.negotiated_max_frame_bytes = self.limits.max_frame_bytes.min(client_maximum);
        self.negotiated_capabilities = SymbolServerCapability::all()
            .into_iter()
            .filter(|capability| hello.capabilities.contains(capability))
            .collect();
        self.phase = SymbolServerPhase::Ready;
        vec![SymbolServerEffect::send(SymbolServerFrame::Hello {
            schema: SYMBOL_SERVER_HELLO_SCHEMA.to_owned(),
            hello: SymbolServerHelloOutput {
                protocol_schema: SYMBOL_SERVER_PROTOCOL_SCHEMA.to_owned(),
                server_name: self.identity.server_name.clone(),
                server_version: self.identity.server_version.clone(),
                capabilities: self.negotiated_capabilities.clone(),
                max_frame_bytes: self.negotiated_max_frame_bytes as u64,
                max_pending_definitions: self.limits.max_pending_definitions as u64,
                workspace: self.workspace.clone(),
            },
        })]
    }

    fn handle_definition(
        &mut self,
        request: DefinitionAtPositionRequest,
    ) -> Vec<SymbolServerEffect> {
        let key = SymbolServerRequestKey::from(&request);
        if let Some(rejection) = self.request_identity_rejection(
            key,
            &request.workspace,
            request.validate(),
            SymbolServerRequestKind::Definition,
        ) {
            return vec![rejection];
        }

        let mut effects = Vec::new();
        if let Some(pre_cancelled) = self.pre_cancelled.remove(&key.request_id) {
            if pre_cancelled.key == key {
                effects.push(Self::definition_cancelled_effect(key, pre_cancelled.reason));
            } else {
                effects.push(Self::definition_error_effect(
                    key,
                    "stale-cancellation-generation",
                    "a pre-request cancellation used a different request or workspace generation",
                    false,
                ));
            }
            return effects;
        }

        if self.pending.len() >= self.limits.max_pending_definitions {
            effects.push(Self::definition_error_effect(
                key,
                "server-busy",
                "symbol-server pending request limit is reached",
                true,
            ));
            return effects;
        }

        let cancellation_token = self.parent_cancellation_token.child_token();
        self.pending.insert(
            key.request_id,
            PendingRequest {
                key,
                kind: SymbolServerRequestKind::Definition,
                cancellation_token: cancellation_token.clone(),
                terminal_sent: false,
            },
        );
        effects.push(SymbolServerEffect::StartDefinition {
            request: Box::new(request),
            cancellation_token,
        });
        effects
    }

    fn handle_usage_at_position(
        &mut self,
        request: UsageAtPositionRequest,
    ) -> Vec<SymbolServerEffect> {
        let key = SymbolServerRequestKey::from(&request);
        if !self
            .negotiated_capabilities
            .contains(&SymbolServerCapability::UsageAtPosition)
        {
            return vec![Self::usage_at_position_error_effect(
                key,
                "capability-not-negotiated",
                "usage-at-position was not negotiated during the symbol-server handshake",
                false,
            )];
        }
        if let Some(rejection) = self.request_identity_rejection(
            key,
            &request.workspace,
            request.validate(),
            SymbolServerRequestKind::UsageAtPosition,
        ) {
            return vec![rejection];
        }

        let mut effects = Vec::new();
        if let Some(pre_cancelled) = self.pre_cancelled.remove(&key.request_id) {
            if pre_cancelled.key == key {
                effects.push(Self::usage_at_position_cancelled_effect(
                    key,
                    pre_cancelled.reason,
                ));
            } else {
                effects.push(Self::usage_at_position_error_effect(
                    key,
                    "stale-cancellation-generation",
                    "a pre-request cancellation used a different request or workspace generation",
                    false,
                ));
            }
            return effects;
        }

        if self.pending.len() >= self.limits.max_pending_definitions {
            effects.push(Self::usage_at_position_error_effect(
                key,
                "server-busy",
                "symbol-server pending request limit is reached",
                true,
            ));
            return effects;
        }

        let cancellation_token = self.parent_cancellation_token.child_token();
        self.pending.insert(
            key.request_id,
            PendingRequest {
                key,
                kind: SymbolServerRequestKind::UsageAtPosition,
                cancellation_token: cancellation_token.clone(),
                terminal_sent: false,
            },
        );
        effects.push(SymbolServerEffect::StartUsageAtPosition {
            request: Box::new(request),
            cancellation_token,
        });
        effects
    }

    fn request_identity_rejection(
        &mut self,
        key: SymbolServerRequestKey,
        request_workspace: &super::DefinitionWorkspaceIdentityInput,
        validation: eyre::Result<()>,
        kind: SymbolServerRequestKind,
    ) -> Option<SymbolServerEffect> {
        let request_label = match kind {
            SymbolServerRequestKind::Definition => "definition",
            SymbolServerRequestKind::UsageAtPosition => "usage-at-position",
        };
        if key.request_id == 0 {
            return Some(Self::protocol_error_effect(
                "invalid-request-id",
                format!("{request_label} request id must be positive"),
                SymbolServerErrorDisposition::Request,
                Some(key.request_id),
                Some(key.request_generation),
            ));
        }
        if self
            .highest_request_id
            .is_some_and(|highest| key.request_id <= highest)
        {
            return Some(Self::protocol_error_effect(
                "stale-request-id",
                "symbol-server request ids must be unique and strictly increasing across request kinds",
                SymbolServerErrorDisposition::Request,
                Some(key.request_id),
                Some(key.request_generation),
            ));
        }
        self.highest_request_id = Some(key.request_id);
        self.pre_cancelled
            .retain(|request_id, _| *request_id >= key.request_id);

        if let Err(error) = validation {
            return Some(Self::request_error_effect(
                kind,
                key,
                format!("invalid-{request_label}-request"),
                error.to_string(),
                false,
            ));
        }
        let workspace_generation = self.workspace.request_workspace.workspace_generation;
        if key.workspace_generation != workspace_generation {
            return Some(Self::request_error_effect(
                kind,
                key,
                "stale-workspace-generation",
                format!(
                    "{request_label} targets workspace generation {}, but server is at {workspace_generation}",
                    key.workspace_generation
                ),
                true,
            ));
        }
        (request_workspace != &self.workspace.request_workspace).then(|| {
            Self::request_error_effect(
                kind,
                key,
                "workspace-identity-mismatch",
                format!(
                    "{request_label} request workspace does not exactly match the worker-served workspace identity"
                ),
                true,
            )
        })
    }

    fn handle_cancel(
        &mut self,
        key: SymbolServerRequestKey,
        reason: String,
    ) -> Vec<SymbolServerEffect> {
        if self.workspace.request_workspace.workspace_generation != key.workspace_generation {
            return vec![Self::cancellation_ack_effect(
                key,
                SymbolServerCancellationStatus::StaleGeneration,
            )];
        }
        if let Some(pending) = self.pending.get_mut(&key.request_id) {
            if pending.key != key {
                return vec![Self::cancellation_ack_effect(
                    key,
                    SymbolServerCancellationStatus::StaleGeneration,
                )];
            }
            if pending.terminal_sent {
                return vec![Self::cancellation_ack_effect(
                    key,
                    SymbolServerCancellationStatus::AlreadyTerminal,
                )];
            }
            let reason = if reason.trim().is_empty() {
                match pending.kind {
                    SymbolServerRequestKind::Definition => {
                        "definition request cancelled by client".to_owned()
                    }
                    SymbolServerRequestKind::UsageAtPosition => {
                        "usage-at-position request cancelled by client".to_owned()
                    }
                }
            } else {
                reason
            };
            let kind = pending.kind;
            pending.cancellation_token.request_cancel(reason.clone());
            pending.terminal_sent = true;
            return vec![
                Self::cancellation_ack_effect(
                    key,
                    SymbolServerCancellationStatus::CancellationRequested,
                ),
                Self::request_cancelled_effect(kind, key, reason),
            ];
        }
        if self
            .highest_request_id
            .is_some_and(|highest| key.request_id <= highest)
        {
            return vec![Self::cancellation_ack_effect(
                key,
                SymbolServerCancellationStatus::AlreadyTerminal,
            )];
        }
        let reason = if reason.trim().is_empty() {
            "symbol request cancelled by client".to_owned()
        } else {
            reason
        };
        if let Some(existing) = self.pre_cancelled.get(&key.request_id) {
            return vec![Self::cancellation_ack_effect(
                key,
                if existing.key == key {
                    SymbolServerCancellationStatus::RecordedBeforeRequest
                } else {
                    SymbolServerCancellationStatus::StaleGeneration
                },
            )];
        }
        if self.pre_cancelled.len() >= self.limits.max_pre_cancelled_requests {
            return vec![Self::cancellation_ack_effect(
                key,
                SymbolServerCancellationStatus::RejectedCapacity,
            )];
        }
        self.pre_cancelled
            .insert(key.request_id, PreCancelledRequest { key, reason });
        vec![Self::cancellation_ack_effect(
            key,
            SymbolServerCancellationStatus::RecordedBeforeRequest,
        )]
    }

    fn handle_workspace_generation(
        &mut self,
        workspace_generation: u64,
    ) -> Vec<SymbolServerEffect> {
        let current = self.workspace.request_workspace.workspace_generation;
        if workspace_generation < current {
            return vec![Self::protocol_error_effect(
                "stale-workspace-generation",
                format!(
                    "workspace generation {workspace_generation} is older than current generation {current}"
                ),
                SymbolServerErrorDisposition::Request,
                None,
                None,
            )];
        }
        if workspace_generation == current {
            return vec![self.workspace_generation_effect(0)];
        }
        let cancelled = self
            .pending
            .values()
            .filter(|pending| !pending.terminal_sent)
            .count();
        let mut effects = self.cancel_pending_matching(
            "symbol request cancelled by workspace generation replacement",
            true,
            |_| true,
        );
        self.workspace.request_workspace.workspace_generation = workspace_generation;
        self.pre_cancelled.clear();
        effects.push(self.workspace_generation_effect(cancelled));
        effects
    }

    fn begin_drain(
        &mut self,
        reason: impl Into<String>,
        send_shutdown_ack: bool,
        send_request_terminals: bool,
    ) -> Vec<SymbolServerEffect> {
        if self.phase == SymbolServerPhase::Closed {
            return Vec::new();
        }
        self.phase = SymbolServerPhase::Draining;
        self.send_shutdown_ack_after_drain = send_shutdown_ack;
        self.pre_cancelled.clear();
        let mut effects =
            self.cancel_pending_matching(reason.into(), send_request_terminals, |_| true);
        effects.extend(self.finish_drain_if_ready());
        effects
    }

    fn cancel_pending_matching(
        &mut self,
        reason: impl Into<String>,
        send_request_terminals: bool,
        predicate: impl Fn(&PendingRequest) -> bool,
    ) -> Vec<SymbolServerEffect> {
        let reason = reason.into();
        let mut cancelled = Vec::new();
        for pending in self.pending.values_mut() {
            if !pending.terminal_sent && predicate(pending) {
                pending.cancellation_token.request_cancel(reason.clone());
                pending.terminal_sent = true;
                if send_request_terminals {
                    cancelled.push((pending.kind, pending.key, reason.clone()));
                }
            }
        }
        cancelled
            .into_iter()
            .map(|(kind, key, cancellation_reason)| {
                Self::request_cancelled_effect(kind, key, cancellation_reason)
            })
            .collect()
    }

    fn finish_drain_if_ready(&mut self) -> Vec<SymbolServerEffect> {
        if self.phase != SymbolServerPhase::Draining || !self.pending.is_empty() {
            return Vec::new();
        }
        self.phase = SymbolServerPhase::Closed;
        if self.send_shutdown_ack_after_drain {
            self.send_shutdown_ack_after_drain = false;
            vec![SymbolServerEffect::send(SymbolServerFrame::Shutdown {
                schema: SYMBOL_SERVER_SHUTDOWN_SCHEMA.to_owned(),
            })]
        } else {
            Vec::new()
        }
    }

    fn fatal_protocol_error(
        &mut self,
        code: impl Into<String>,
        message: impl Into<String>,
    ) -> Vec<SymbolServerEffect> {
        let mut effects = vec![Self::protocol_error_effect(
            code,
            message,
            SymbolServerErrorDisposition::Fatal,
            None,
            None,
        )];
        effects.extend(self.begin_drain(
            "symbol request cancelled by fatal protocol error",
            false,
            true,
        ));
        effects
    }

    fn protocol_error_effect(
        code: impl Into<String>,
        message: impl Into<String>,
        disposition: SymbolServerErrorDisposition,
        request_id: Option<u64>,
        request_generation: Option<u64>,
    ) -> SymbolServerEffect {
        SymbolServerEffect::send(SymbolServerFrame::Error {
            schema: SYMBOL_SERVER_ERROR_SCHEMA.to_owned(),
            error: SymbolServerErrorOutput {
                code: code.into(),
                message: message.into(),
                disposition,
                request_id,
                request_generation,
            },
        })
    }

    fn definition_error_effect(
        key: SymbolServerRequestKey,
        code: impl Into<String>,
        message: impl Into<String>,
        retryable: bool,
    ) -> SymbolServerEffect {
        SymbolServerEffect::send(SymbolServerFrame::DefinitionFailed {
            schema: SYMBOL_SERVER_DEFINITION_SCHEMA.to_owned(),
            error: SymbolServerDefinitionErrorOutput {
                request_id: key.request_id,
                request_generation: key.request_generation,
                workspace_generation: key.workspace_generation,
                code: code.into(),
                message: message.into(),
                retryable,
            },
        })
    }

    fn usage_at_position_error_effect(
        key: SymbolServerRequestKey,
        code: impl Into<String>,
        message: impl Into<String>,
        retryable: bool,
    ) -> SymbolServerEffect {
        SymbolServerEffect::send(SymbolServerFrame::UsageAtPositionFailed {
            schema: SYMBOL_SERVER_USAGE_AT_POSITION_SCHEMA.to_owned(),
            error: SymbolServerUsageAtPositionErrorOutput {
                request_id: key.request_id,
                request_generation: key.request_generation,
                workspace_generation: key.workspace_generation,
                code: code.into(),
                message: message.into(),
                retryable,
            },
        })
    }

    fn request_error_effect(
        kind: SymbolServerRequestKind,
        key: SymbolServerRequestKey,
        code: impl Into<String>,
        message: impl Into<String>,
        retryable: bool,
    ) -> SymbolServerEffect {
        let code = code.into();
        let message = message.into();
        match kind {
            SymbolServerRequestKind::Definition => {
                Self::definition_error_effect(key, code, message, retryable)
            }
            SymbolServerRequestKind::UsageAtPosition => {
                Self::usage_at_position_error_effect(key, code, message, retryable)
            }
        }
    }

    fn definition_cancelled_effect(
        key: SymbolServerRequestKey,
        reason: impl Into<String>,
    ) -> SymbolServerEffect {
        SymbolServerEffect::send(SymbolServerFrame::DefinitionCancelled {
            schema: SYMBOL_SERVER_DEFINITION_SCHEMA.to_owned(),
            cancellation: SymbolServerDefinitionCancelledOutput {
                request_id: key.request_id,
                request_generation: key.request_generation,
                workspace_generation: key.workspace_generation,
                reason: reason.into(),
            },
        })
    }

    fn usage_at_position_cancelled_effect(
        key: SymbolServerRequestKey,
        reason: impl Into<String>,
    ) -> SymbolServerEffect {
        SymbolServerEffect::send(SymbolServerFrame::UsageAtPositionCancelled {
            schema: SYMBOL_SERVER_USAGE_AT_POSITION_SCHEMA.to_owned(),
            cancellation: SymbolServerUsageAtPositionCancelledOutput {
                request_id: key.request_id,
                request_generation: key.request_generation,
                workspace_generation: key.workspace_generation,
                reason: reason.into(),
            },
        })
    }

    fn request_cancelled_effect(
        kind: SymbolServerRequestKind,
        key: SymbolServerRequestKey,
        reason: impl Into<String>,
    ) -> SymbolServerEffect {
        let reason = reason.into();
        match kind {
            SymbolServerRequestKind::Definition => Self::definition_cancelled_effect(key, reason),
            SymbolServerRequestKind::UsageAtPosition => {
                Self::usage_at_position_cancelled_effect(key, reason)
            }
        }
    }

    fn cancellation_ack_effect(
        key: SymbolServerRequestKey,
        status: SymbolServerCancellationStatus,
    ) -> SymbolServerEffect {
        SymbolServerEffect::send(SymbolServerFrame::Cancelled {
            schema: SYMBOL_SERVER_CANCEL_SCHEMA.to_owned(),
            cancellation: SymbolServerCancellationOutput {
                request_id: key.request_id,
                request_generation: key.request_generation,
                workspace_generation: key.workspace_generation,
                status,
            },
        })
    }

    fn workspace_generation_effect(&self, cancelled_requests: usize) -> SymbolServerEffect {
        SymbolServerEffect::send(SymbolServerFrame::WorkspaceGeneration {
            schema: SYMBOL_SERVER_WORKSPACE_GENERATION_SCHEMA.to_owned(),
            update: SymbolServerWorkspaceGenerationOutput {
                workspace: self.workspace.clone(),
                cancelled_requests: cancelled_requests as u64,
            },
        })
    }
}

fn definition_result_matches_key(
    result: &DefinitionAtPositionResult,
    key: SymbolServerRequestKey,
) -> bool {
    result.schema == DEFINITION_AT_POSITION_RESULT_SCHEMA
        && result.request_id == key.request_id
        && result.request_generation == key.request_generation
        && result.workspace_generation == key.workspace_generation
}

fn usage_at_position_result_matches_key(
    result: &UsageAtPositionResult,
    key: SymbolServerRequestKey,
) -> bool {
    result.schema == USAGE_AT_POSITION_RESULT_SCHEMA
        && result.request_id == key.request_id
        && result.request_generation == key.request_generation
        && result.workspace_generation == key.workspace_generation
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::java_analysis::DefinitionDocumentInput;
    use crate::java_analysis::DefinitionTextPositionInput;
    use crate::java_analysis::DefinitionWorkspaceIdentityInput;
    use crate::java_analysis::JavaAnalysisContextOutput;
    use crate::java_analysis::JavaClasspathMode;
    use crate::java_analysis::JavaSourceRootKind;
    use crate::java_analysis::JavaSourceRootOutput;
    use crate::java_analysis::blake3_content_hash;

    fn served_workspace(workspace_generation: u64) -> SymbolServerWorkspaceOutput {
        SymbolServerWorkspaceOutput {
            request_workspace: DefinitionWorkspaceIdentityInput {
                branch: "1.19.2".to_owned(),
                classpath_mode: JavaClasspathMode::Isolated,
                source_roots: vec![JavaSourceRootOutput {
                    id: "custom-0".to_owned(),
                    source_set: "custom".to_owned(),
                    path: "source".to_owned(),
                    kind: JavaSourceRootKind::Custom,
                    exists: true,
                }],
                classpath_fingerprint: "blake3:workspace".to_owned(),
                dependency_index_identity: None,
                workspace_fingerprint: concat!(
                    "blake3:",
                    "0000000000000000000000000000000000000000000000000000000000000000"
                )
                .to_owned(),
                workspace_generation,
            },
            roots: vec![super::super::SymbolServerWorkspaceRootOutput {
                canonical_absolute_path: "C:/workspace/source".to_owned(),
                root_id: "custom-0".to_owned(),
                source_set: "custom".to_owned(),
                report_root_path: "source".to_owned(),
            }],
            dependency_source_roots: Vec::new(),
            managed_source_roots: Vec::new(),
        }
    }

    fn state(maximum_pending: usize) -> SymbolServerState {
        SymbolServerState::new(
            SymbolServerIdentity {
                server_name: "sfm-propagate-changes".to_owned(),
                server_version: "test".to_owned(),
            },
            served_workspace(7),
            SymbolServerLimits {
                max_pending_definitions: maximum_pending,
                ..SymbolServerLimits::default()
            },
            CancellationToken::new(),
        )
        .expect("valid server state")
    }

    fn hello() -> SymbolServerClientFrame {
        SymbolServerClientFrame::hello(super::super::SymbolServerClientHello {
            protocol_schema: SYMBOL_SERVER_PROTOCOL_SCHEMA.to_owned(),
            client_name: "test-client".to_owned(),
            client_version: "1".to_owned(),
            capabilities: SymbolServerCapability::all().to_vec(),
            max_frame_bytes: 4096,
        })
    }

    fn request(
        request_id: u64,
        request_generation: u64,
        workspace_generation: u64,
    ) -> DefinitionAtPositionRequest {
        let text = "package example; class A {}\n";
        let source_root = JavaSourceRootOutput {
            id: "custom-0".to_owned(),
            source_set: "custom".to_owned(),
            path: "source".to_owned(),
            kind: JavaSourceRootKind::Custom,
            exists: true,
        };
        DefinitionAtPositionRequest::new(
            request_id,
            request_generation,
            DefinitionWorkspaceIdentityInput {
                branch: "1.19.2".to_owned(),
                classpath_mode: JavaClasspathMode::Isolated,
                source_roots: vec![source_root],
                classpath_fingerprint: "blake3:workspace".to_owned(),
                dependency_index_identity: None,
                workspace_fingerprint: concat!(
                    "blake3:",
                    "0000000000000000000000000000000000000000000000000000000000000000"
                )
                .to_owned(),
                workspace_generation,
            },
            DefinitionDocumentInput {
                address: "file://source/example/A.java".to_owned(),
                root_id: "custom-0".to_owned(),
                root_relative_path: "example/A.java".to_owned(),
                report_path: "source/example/A.java".to_owned(),
                source_set: "custom".to_owned(),
                text: text.to_owned(),
                content_hash: blake3_content_hash(text),
                disk_content_hash: None,
            },
            DefinitionTextPositionInput::from_line_column(text, 1, 24).expect("fixture position"),
        )
    }

    fn result(request: &DefinitionAtPositionRequest) -> DefinitionAtPositionResult {
        DefinitionAtPositionResult::invalid_request(
            request,
            JavaAnalysisContextOutput {
                branch: "1.19.2".to_owned(),
                minecraft_version: "1.19.2".to_owned(),
                java_release: "17".to_owned(),
                jdk: "fixture".to_owned(),
                source_roots: request.workspace.source_roots.clone(),
                source_sets: Vec::new(),
                source_exclusions: Vec::new(),
                classpath_mode: request.workspace.classpath_mode,
                classpath_fingerprint: request.workspace.classpath_fingerprint.clone(),
                parser_fingerprint: "fixture-parser".to_owned(),
                index_fingerprint: "fixture-index".to_owned(),
            },
            "fixture result",
        )
    }

    fn usage_request(
        request_id: u64,
        request_generation: u64,
        workspace_generation: u64,
    ) -> UsageAtPositionRequest {
        let request = request(request_id, request_generation, workspace_generation);
        UsageAtPositionRequest::new(
            request.request_id,
            request.request_generation,
            request.workspace,
            request.document,
            request.position,
        )
    }

    fn usage_result(request: &UsageAtPositionRequest) -> UsageAtPositionResult {
        UsageAtPositionResult::from_definition(result(&request.as_definition_request()))
    }

    fn handshake(state: &mut SymbolServerState, workspace_generation: u64) {
        let effects = state.handle_frame(hello());
        assert_eq!(effects.len(), 1);
        assert!(matches!(
            sent_frame(&effects[0]),
            Some(SymbolServerFrame::Hello {
                hello: SymbolServerHelloOutput {
                    workspace: SymbolServerWorkspaceOutput {
                        request_workspace: DefinitionWorkspaceIdentityInput {
                            workspace_generation: actual_generation,
                            ..
                        },
                        ..
                    },
                    ..
                },
                ..
            }) if *actual_generation == workspace_generation
        ));
        assert_eq!(state.phase(), SymbolServerPhase::Ready);
        assert_eq!(state.negotiated_max_frame_bytes(), 4096);
    }

    fn started(
        effects: &[SymbolServerEffect],
    ) -> Option<(DefinitionAtPositionRequest, CancellationToken)> {
        effects.iter().find_map(|effect| match effect {
            SymbolServerEffect::StartDefinition {
                request,
                cancellation_token,
            } => Some((request.as_ref().clone(), cancellation_token.clone())),
            SymbolServerEffect::Send(_) | SymbolServerEffect::StartUsageAtPosition { .. } => None,
        })
    }

    fn started_usage_at_position(
        effects: &[SymbolServerEffect],
    ) -> Option<(UsageAtPositionRequest, CancellationToken)> {
        effects.iter().find_map(|effect| match effect {
            SymbolServerEffect::StartUsageAtPosition {
                request,
                cancellation_token,
            } => Some((request.as_ref().clone(), cancellation_token.clone())),
            SymbolServerEffect::Send(_) | SymbolServerEffect::StartDefinition { .. } => None,
        })
    }

    fn sent_frame(effect: &SymbolServerEffect) -> Option<&SymbolServerFrame> {
        match effect {
            SymbolServerEffect::Send(frame) => Some(frame.as_ref()),
            SymbolServerEffect::StartDefinition { .. }
            | SymbolServerEffect::StartUsageAtPosition { .. } => None,
        }
    }

    fn terminal_count(effects: &[SymbolServerEffect], request_id: u64) -> usize {
        effects
            .iter()
            .filter(|effect| match sent_frame(effect) {
                Some(SymbolServerFrame::DefinitionResult { result, .. }) => {
                    result.request_id == request_id
                }
                Some(SymbolServerFrame::DefinitionCancelled { cancellation, .. }) => {
                    cancellation.request_id == request_id
                }
                Some(SymbolServerFrame::DefinitionFailed { error, .. }) => {
                    error.request_id == request_id
                }
                Some(SymbolServerFrame::UsageAtPositionResult { result, .. }) => {
                    result.request_id == request_id
                }
                Some(SymbolServerFrame::UsageAtPositionCancelled { cancellation, .. }) => {
                    cancellation.request_id == request_id
                }
                Some(SymbolServerFrame::UsageAtPositionFailed { error, .. }) => {
                    error.request_id == request_id
                }
                _ => false,
            })
            .count()
    }

    #[test]
    fn handshake_is_required_and_schema_failure_is_fatal() {
        let mut state = state(2);
        let effects = state.handle_frame(SymbolServerClientFrame::ping(1));
        assert!(matches!(
            effects.first().and_then(sent_frame),
            Some(SymbolServerFrame::Error {
                error: SymbolServerErrorOutput {
                    disposition: SymbolServerErrorDisposition::Fatal,
                    ..
                },
                ..
            })
        ));
        assert_eq!(state.phase(), SymbolServerPhase::Closed);
    }

    #[test]
    fn pending_limit_is_bounded_and_rejection_is_terminal() {
        let mut state = state(1);
        handshake(&mut state, 7);
        assert!(
            started(&state.handle_frame(SymbolServerClientFrame::definition(request(1, 1, 7))))
                .is_some()
        );

        let effects = state.handle_frame(SymbolServerClientFrame::definition(request(2, 1, 7)));

        assert_eq!(terminal_count(&effects, 2), 1);
        assert_eq!(effects.len(), 1);
        assert!(matches!(
            sent_frame(&effects[0]),
            Some(SymbolServerFrame::DefinitionFailed {
                error: SymbolServerDefinitionErrorOutput { code, retryable: true, .. },
                ..
            }) if code == "server-busy"
        ));
        assert_eq!(state.pending_definition_count(), 1);
    }

    #[test]
    fn pending_limit_is_shared_across_definition_and_usage_requests() {
        let mut state = state(1);
        handshake(&mut state, 7);
        assert!(
            started_usage_at_position(&state.handle_frame(
                SymbolServerClientFrame::usage_at_position(usage_request(1, 1, 7),)
            ))
            .is_some()
        );

        let effects = state.handle_frame(SymbolServerClientFrame::definition(request(2, 1, 7)));

        assert_eq!(terminal_count(&effects, 2), 1);
        assert!(matches!(
            sent_frame(&effects[0]),
            Some(SymbolServerFrame::DefinitionFailed {
                error: SymbolServerDefinitionErrorOutput {
                    code,
                    retryable: true,
                    ..
                },
                ..
            }) if code == "server-busy"
        ));
        assert_eq!(state.pending_request_count(), 1);
        assert_eq!(state.pending_definition_count(), 0);
    }

    #[test]
    fn definition_request_must_copy_the_exact_served_workspace_identity() {
        let mut state = state(2);
        handshake(&mut state, 7);
        let mut mismatched = request(1, 1, 7);
        mismatched.workspace.classpath_fingerprint = "blake3:guessed".to_owned();

        let effects = state.handle_frame(SymbolServerClientFrame::definition(mismatched));

        assert!(started(&effects).is_none());
        assert_eq!(effects.len(), 1);
        assert!(matches!(
            sent_frame(&effects[0]),
            Some(SymbolServerFrame::DefinitionFailed {
                error: SymbolServerDefinitionErrorOutput { code, .. },
                ..
            }) if code == "workspace-identity-mismatch"
        ));
    }

    #[test]
    fn equal_generation_requests_can_complete_out_of_order() {
        let mut state = state(2);
        handshake(&mut state, 7);
        let first = request(1, 4, 7);
        let second = request(2, 4, 7);
        let first_key = SymbolServerRequestKey::from(&first);
        let second_key = SymbolServerRequestKey::from(&second);
        assert!(
            started(&state.handle_frame(SymbolServerClientFrame::definition(first.clone())))
                .is_some()
        );
        assert!(
            started(&state.handle_frame(SymbolServerClientFrame::definition(second.clone())))
                .is_some()
        );

        let second_effects = state.finish_definition(second_key, Ok(result(&second)));
        let first_effects = state.finish_definition(first_key, Ok(result(&first)));

        assert_eq!(terminal_count(&second_effects, 2), 1);
        assert_eq!(terminal_count(&first_effects, 1), 1);
        assert_eq!(state.pending_definition_count(), 0);
    }

    #[test]
    fn definition_and_usage_requests_share_bounds_and_complete_out_of_order() {
        let mut state = state(2);
        handshake(&mut state, 7);
        let definition = request(1, 4, 7);
        let usage = usage_request(2, 4, 7);
        let definition_key = SymbolServerRequestKey::from(&definition);
        let usage_key = SymbolServerRequestKey::from(&usage);

        assert!(
            started(&state.handle_frame(SymbolServerClientFrame::definition(definition.clone())))
                .is_some()
        );
        assert!(
            started_usage_at_position(
                &state.handle_frame(SymbolServerClientFrame::usage_at_position(usage.clone()))
            )
            .is_some()
        );
        assert_eq!(state.pending_request_count(), 2);
        assert_eq!(state.pending_definition_count(), 1);

        let usage_effects = state.finish_usage_at_position(usage_key, Ok(usage_result(&usage)));
        let definition_effects = state.finish_definition(definition_key, Ok(result(&definition)));

        assert!(matches!(
            usage_effects.first().and_then(sent_frame),
            Some(SymbolServerFrame::UsageAtPositionResult { result, .. })
                if result.request_id == 2
        ));
        assert!(matches!(
            definition_effects.first().and_then(sent_frame),
            Some(SymbolServerFrame::DefinitionResult { result, .. })
                if result.request_id == 1
        ));
        assert_eq!(state.pending_request_count(), 0);
    }

    #[test]
    fn usage_capability_is_additive_for_definition_only_clients() {
        let mut state = state(2);
        let effects = state.handle_frame(SymbolServerClientFrame::hello(
            super::super::SymbolServerClientHello {
                protocol_schema: SYMBOL_SERVER_PROTOCOL_SCHEMA.to_owned(),
                client_name: "definition-only-client".to_owned(),
                client_version: "1".to_owned(),
                capabilities: vec![
                    SymbolServerCapability::DefinitionAtPosition,
                    SymbolServerCapability::Cancellation,
                    SymbolServerCapability::WorkspaceGeneration,
                    SymbolServerCapability::Ping,
                    SymbolServerCapability::Shutdown,
                ],
                max_frame_bytes: 4096,
            },
        ));
        assert!(matches!(
            effects.first().and_then(sent_frame),
            Some(SymbolServerFrame::Hello {
                hello: SymbolServerHelloOutput { capabilities, .. },
                ..
            }) if !capabilities.contains(&SymbolServerCapability::UsageAtPosition)
        ));

        let definition = request(1, 1, 7);
        let definition_key = SymbolServerRequestKey::from(&definition);
        assert!(
            started(&state.handle_frame(SymbolServerClientFrame::definition(definition.clone())))
                .is_some()
        );
        assert_eq!(
            terminal_count(
                &state.finish_definition(definition_key, Ok(result(&definition))),
                1
            ),
            1
        );

        let rejected = state.handle_frame(SymbolServerClientFrame::usage_at_position(
            usage_request(2, 1, 7),
        ));
        assert!(matches!(
            rejected.first().and_then(sent_frame),
            Some(SymbolServerFrame::UsageAtPositionFailed {
                error: SymbolServerUsageAtPositionErrorOutput { code, .. },
                ..
            }) if code == "capability-not-negotiated"
        ));
    }

    #[test]
    fn cancellation_before_request_prevents_engine_start() {
        let mut state = state(2);
        handshake(&mut state, 7);
        let cancel =
            state.handle_frame(SymbolServerClientFrame::cancel(1, 3, 7, "no longer needed"));
        assert_eq!(cancel.len(), 1);
        assert!(matches!(
            sent_frame(&cancel[0]),
            Some(SymbolServerFrame::Cancelled {
                cancellation: SymbolServerCancellationOutput {
                    status: SymbolServerCancellationStatus::RecordedBeforeRequest,
                    ..
                },
                ..
            })
        ));

        let effects = state.handle_frame(SymbolServerClientFrame::definition(request(1, 3, 7)));

        assert!(started(&effects).is_none());
        assert_eq!(terminal_count(&effects, 1), 1);
    }

    #[test]
    fn cancellation_before_usage_request_prevents_engine_start() {
        let mut state = state(2);
        handshake(&mut state, 7);
        let cancel = state.handle_frame(SymbolServerClientFrame::cancel(
            1,
            3,
            7,
            "selection changed",
        ));
        assert!(matches!(
            cancel.first().and_then(sent_frame),
            Some(SymbolServerFrame::Cancelled {
                cancellation: SymbolServerCancellationOutput {
                    status: SymbolServerCancellationStatus::RecordedBeforeRequest,
                    ..
                },
                ..
            })
        ));

        let effects = state.handle_frame(SymbolServerClientFrame::usage_at_position(
            usage_request(1, 3, 7),
        ));

        assert!(started_usage_at_position(&effects).is_none());
        assert!(matches!(
            effects.first().and_then(sent_frame),
            Some(SymbolServerFrame::UsageAtPositionCancelled { cancellation, .. })
                if cancellation.request_id == 1
        ));
        assert_eq!(terminal_count(&effects, 1), 1);
    }

    #[test]
    fn cancellation_during_request_emits_one_terminal_and_suppresses_completion() {
        let mut state = state(2);
        handshake(&mut state, 7);
        let request = request(1, 3, 7);
        let key = SymbolServerRequestKey::from(&request);
        let (_, token) =
            started(&state.handle_frame(SymbolServerClientFrame::definition(request.clone())))
                .expect("start effect");

        let cancelled =
            state.handle_frame(SymbolServerClientFrame::cancel(1, 3, 7, "cursor moved"));
        let completion = state.finish_definition(key, Ok(result(&request)));

        assert!(token.is_cancelled());
        assert_eq!(terminal_count(&cancelled, 1), 1);
        assert_eq!(terminal_count(&completion, 1), 0);
    }

    #[test]
    fn cancelling_usage_does_not_cancel_a_mixed_definition_request() {
        let mut state = state(2);
        handshake(&mut state, 7);
        let definition = request(1, 3, 7);
        let usage = usage_request(2, 3, 7);
        let definition_key = SymbolServerRequestKey::from(&definition);
        let usage_key = SymbolServerRequestKey::from(&usage);
        let (_, definition_token) =
            started(&state.handle_frame(SymbolServerClientFrame::definition(definition.clone())))
                .expect("definition start");
        let (_, usage_token) = started_usage_at_position(
            &state.handle_frame(SymbolServerClientFrame::usage_at_position(usage.clone())),
        )
        .expect("usage start");

        let cancelled = state.handle_frame(SymbolServerClientFrame::cancel(
            2,
            3,
            7,
            "selection changed",
        ));

        assert!(!definition_token.is_cancelled());
        assert!(usage_token.is_cancelled());
        assert!(cancelled.iter().any(|effect| matches!(
            sent_frame(effect),
            Some(SymbolServerFrame::UsageAtPositionCancelled { cancellation, .. })
                if cancellation.request_id == 2
        )));
        assert!(
            state
                .finish_usage_at_position(usage_key, Ok(usage_result(&usage)))
                .is_empty()
        );
        assert_eq!(
            terminal_count(
                &state.finish_definition(definition_key, Ok(result(&definition))),
                1
            ),
            1
        );
    }

    #[test]
    fn cancellation_after_completion_only_acknowledges_terminal_state() {
        let mut state = state(2);
        handshake(&mut state, 7);
        let request = request(1, 3, 7);
        let key = SymbolServerRequestKey::from(&request);
        assert!(
            started(&state.handle_frame(SymbolServerClientFrame::definition(request.clone())))
                .is_some()
        );
        let completed = state.finish_definition(key, Ok(result(&request)));
        assert_eq!(terminal_count(&completed, 1), 1);

        let cancelled = state.handle_frame(SymbolServerClientFrame::cancel(1, 3, 7, "late"));

        assert_eq!(terminal_count(&cancelled, 1), 0);
        assert_eq!(cancelled.len(), 1);
        assert!(matches!(
            sent_frame(&cancelled[0]),
            Some(SymbolServerFrame::Cancelled {
                cancellation: SymbolServerCancellationOutput {
                    status: SymbolServerCancellationStatus::AlreadyTerminal,
                    ..
                },
                ..
            })
        ));
    }

    #[test]
    fn independent_request_generations_do_not_cancel_each_other() {
        let mut state = state(3);
        handshake(&mut state, 7);
        let first = request(1, 9, 7);
        let second = request(2, 1, 7);
        let first_key = SymbolServerRequestKey::from(&first);
        let second_key = SymbolServerRequestKey::from(&second);
        let (_, first_token) =
            started(&state.handle_frame(SymbolServerClientFrame::definition(first.clone())))
                .expect("first origin start");
        let (_, second_token) =
            started(&state.handle_frame(SymbolServerClientFrame::definition(second.clone())))
                .expect("second origin start");

        assert!(!first_token.is_cancelled());
        assert!(!second_token.is_cancelled());
        assert_eq!(
            terminal_count(&state.finish_definition(second_key, Ok(result(&second))), 2),
            1
        );
        assert_eq!(
            terminal_count(&state.finish_definition(first_key, Ok(result(&first))), 1),
            1
        );
    }

    #[test]
    fn workspace_generation_update_cancels_old_work_and_rejects_old_snapshot() {
        let mut state = state(3);
        handshake(&mut state, 7);
        let old = request(1, 1, 7);
        let old_key = SymbolServerRequestKey::from(&old);
        let (_, token) =
            started(&state.handle_frame(SymbolServerClientFrame::definition(old.clone())))
                .expect("old start");

        let update = state.handle_frame(SymbolServerClientFrame::workspace_generation(8));
        let late = state.finish_definition(old_key, Ok(result(&old)));
        let stale_effects =
            state.handle_frame(SymbolServerClientFrame::definition(request(2, 2, 7)));

        assert!(token.is_cancelled());
        assert_eq!(terminal_count(&update, 1), 1);
        assert!(update.iter().any(|effect| matches!(
            sent_frame(effect),
            Some(SymbolServerFrame::WorkspaceGeneration {
                update: SymbolServerWorkspaceGenerationOutput {
                    workspace: SymbolServerWorkspaceOutput {
                        request_workspace: DefinitionWorkspaceIdentityInput {
                            workspace_generation: 8,
                            ..
                        },
                        ..
                    },
                    cancelled_requests: 1
                },
                ..
            })
        )));
        assert_eq!(terminal_count(&late, 1), 0);
        assert_eq!(terminal_count(&stale_effects, 2), 1);
    }

    #[test]
    fn workspace_generation_replacement_cancels_mixed_requests_by_terminal_kind() {
        let mut state = state(3);
        handshake(&mut state, 7);
        let definition = request(1, 1, 7);
        let usage = usage_request(2, 1, 7);
        let definition_key = SymbolServerRequestKey::from(&definition);
        let usage_key = SymbolServerRequestKey::from(&usage);
        let (_, definition_token) =
            started(&state.handle_frame(SymbolServerClientFrame::definition(definition.clone())))
                .expect("definition start");
        let (_, usage_token) = started_usage_at_position(
            &state.handle_frame(SymbolServerClientFrame::usage_at_position(usage.clone())),
        )
        .expect("usage start");

        let update = state.handle_frame(SymbolServerClientFrame::workspace_generation(8));

        assert!(definition_token.is_cancelled());
        assert!(usage_token.is_cancelled());
        assert!(update.iter().any(|effect| matches!(
            sent_frame(effect),
            Some(SymbolServerFrame::DefinitionCancelled { cancellation, .. })
                if cancellation.request_id == 1
        )));
        assert!(update.iter().any(|effect| matches!(
            sent_frame(effect),
            Some(SymbolServerFrame::UsageAtPositionCancelled { cancellation, .. })
                if cancellation.request_id == 2
        )));
        assert!(update.iter().any(|effect| matches!(
            sent_frame(effect),
            Some(SymbolServerFrame::WorkspaceGeneration {
                update: SymbolServerWorkspaceGenerationOutput {
                    cancelled_requests: 2,
                    ..
                },
                ..
            })
        )));
        assert!(
            state
                .finish_usage_at_position(usage_key, Ok(usage_result(&usage)))
                .is_empty()
        );
        assert!(
            state
                .finish_definition(definition_key, Ok(result(&definition)))
                .is_empty()
        );
        assert_eq!(state.pending_request_count(), 0);
    }

    #[test]
    fn shutdown_waits_for_cancelled_engine_before_acknowledging() {
        let mut state = state(2);
        handshake(&mut state, 7);
        let request = request(1, 1, 7);
        let key = SymbolServerRequestKey::from(&request);
        let (_, token) =
            started(&state.handle_frame(SymbolServerClientFrame::definition(request.clone())))
                .expect("start");

        let shutdown = state.handle_frame(SymbolServerClientFrame::shutdown("test complete"));
        assert!(token.is_cancelled());
        assert_eq!(state.phase(), SymbolServerPhase::Draining);
        assert_eq!(terminal_count(&shutdown, 1), 1);
        assert!(
            !shutdown.iter().any(|effect| matches!(
                sent_frame(effect),
                Some(SymbolServerFrame::Shutdown { .. })
            ))
        );

        let drained = state.finish_definition(key, Ok(result(&request)));
        assert_eq!(drained.len(), 1);
        assert!(matches!(
            sent_frame(&drained[0]),
            Some(SymbolServerFrame::Shutdown { .. })
        ));
        assert_eq!(state.phase(), SymbolServerPhase::Closed);
    }

    #[test]
    fn eof_cancels_without_writing_and_closes_after_engine_drains() {
        let mut state = state(2);
        handshake(&mut state, 7);
        let request = request(1, 1, 7);
        let key = SymbolServerRequestKey::from(&request);
        let (_, token) =
            started(&state.handle_frame(SymbolServerClientFrame::definition(request.clone())))
                .expect("start");

        assert!(state.handle_eof().is_empty());
        assert!(token.is_cancelled());
        assert_eq!(state.phase(), SymbolServerPhase::Draining);

        assert!(
            state
                .finish_definition(key, Ok(result(&request)))
                .is_empty()
        );
        assert_eq!(state.phase(), SymbolServerPhase::Closed);
    }

    #[test]
    fn eof_waits_for_mixed_engine_cleanup_without_writing_terminals() {
        let mut state = state(2);
        handshake(&mut state, 7);
        let definition = request(1, 1, 7);
        let usage = usage_request(2, 1, 7);
        let definition_key = SymbolServerRequestKey::from(&definition);
        let usage_key = SymbolServerRequestKey::from(&usage);
        let (_, definition_token) =
            started(&state.handle_frame(SymbolServerClientFrame::definition(definition.clone())))
                .expect("definition start");
        let (_, usage_token) = started_usage_at_position(
            &state.handle_frame(SymbolServerClientFrame::usage_at_position(usage.clone())),
        )
        .expect("usage start");

        assert!(state.handle_eof().is_empty());
        assert!(definition_token.is_cancelled());
        assert!(usage_token.is_cancelled());
        assert_eq!(state.phase(), SymbolServerPhase::Draining);
        assert!(
            state
                .finish_usage_at_position(usage_key, Ok(usage_result(&usage)))
                .is_empty()
        );
        assert_eq!(state.phase(), SymbolServerPhase::Draining);
        assert!(
            state
                .finish_definition(definition_key, Ok(result(&definition)))
                .is_empty()
        );
        assert_eq!(state.phase(), SymbolServerPhase::Closed);
    }
}
