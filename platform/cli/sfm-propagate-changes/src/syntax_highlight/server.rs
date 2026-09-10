use super::DEFAULT_SYNTAX_SERVER_MAX_FRAME_BYTES;
use super::DEFAULT_SYNTAX_SERVER_MAX_PENDING_REQUESTS;
use super::SYNTAX_SERVER_CANCEL_SCHEMA;
use super::SYNTAX_SERVER_ERROR_SCHEMA;
use super::SYNTAX_SERVER_HELLO_SCHEMA;
use super::SYNTAX_SERVER_HIGHLIGHT_SCHEMA;
use super::SYNTAX_SERVER_PING_SCHEMA;
use super::SYNTAX_SERVER_PROTOCOL_SCHEMA;
use super::SYNTAX_SERVER_SHUTDOWN_SCHEMA;
use super::SyntaxHighlightDiagnostic;
use super::SyntaxHighlightDiagnosticSeverity;
use super::SyntaxHighlightEngine;
use super::SyntaxHighlightEngineLimits;
use super::SyntaxHighlightEngineMetrics;
use super::SyntaxHighlightLimits;
use super::SyntaxHighlightOutcome;
use super::SyntaxHighlightRequest;
use super::SyntaxHighlightResult;
use super::SyntaxServerCancellationOutput;
use super::SyntaxServerCancellationStatus;
use super::SyntaxServerCapability;
use super::SyntaxServerClientFrame;
use super::SyntaxServerErrorDisposition;
use super::SyntaxServerErrorOutput;
use super::SyntaxServerFrame;
use super::SyntaxServerHelloOutput;
use super::read_syntax_server_client_frame;
use super::write_syntax_server_frame;
use crate::cancellation::CancellationToken;
use std::collections::HashMap;
use std::collections::VecDeque;
use std::io::Read;
use std::io::Write;
use std::sync::Arc;
use std::sync::mpsc;
use std::time::Duration;

const SYNTAX_SERVER_EVENT_POLL_INTERVAL: Duration = Duration::from_millis(10);
const DEFAULT_PRE_CANCELLED_REQUESTS: usize = 32;
const DEFAULT_TERMINAL_HISTORY: usize = 128;
const DEFAULT_EVENT_QUEUE_EVENTS: usize = 4;
const MINIMUM_SYNTAX_SERVER_FRAME_BYTES: usize = 4 * 1024;
const MAX_CANCELLATION_REASON_BYTES: usize = 1024;
const MAX_VALIDATION_MESSAGE_BYTES: usize = 1024;

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct SyntaxServerIdentity {
    pub server_name: String,
    pub server_version: String,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct SyntaxServerLimits {
    pub max_frame_bytes: usize,
    pub max_pending_requests: usize,
    pub max_pre_cancelled_requests: usize,
    pub max_terminal_history: usize,
    pub max_event_queue_events: usize,
}

impl SyntaxServerLimits {
    /// Validate all process-level memory and protocol bounds.
    ///
    /// # Errors
    ///
    /// Returns an error when any bound is zero or cannot fit the wire format.
    pub fn validate(self) -> eyre::Result<()> {
        if self.max_frame_bytes == 0
            || self.max_pending_requests == 0
            || self.max_pre_cancelled_requests == 0
            || self.max_terminal_history == 0
            || self.max_event_queue_events == 0
        {
            eyre::bail!("syntax-server limits must be positive");
        }
        if self.max_frame_bytes < MINIMUM_SYNTAX_SERVER_FRAME_BYTES {
            eyre::bail!(
                "syntax-server frame maximum must be at least {MINIMUM_SYNTAX_SERVER_FRAME_BYTES} bytes"
            );
        }
        if u32::try_from(self.max_frame_bytes).is_err() {
            eyre::bail!("syntax-server frame maximum must fit u32 framing");
        }
        Ok(())
    }
}

impl Default for SyntaxServerLimits {
    fn default() -> Self {
        Self {
            max_frame_bytes: DEFAULT_SYNTAX_SERVER_MAX_FRAME_BYTES,
            max_pending_requests: DEFAULT_SYNTAX_SERVER_MAX_PENDING_REQUESTS,
            max_pre_cancelled_requests: DEFAULT_PRE_CANCELLED_REQUESTS,
            max_terminal_history: DEFAULT_TERMINAL_HISTORY,
            max_event_queue_events: DEFAULT_EVENT_QUEUE_EVENTS,
        }
    }
}

#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct SyntaxServerRuntimeSummary {
    pub terminal_requests: u64,
    pub failed_requests: u64,
    pub cancelled_requests: u64,
    pub query_compilations: u64,
    pub parse_count: u64,
    pub cache_hits: u64,
    pub cache_misses: u64,
    pub cache_evictions: u64,
    pub final_cache_entries: u64,
    pub final_cache_bytes: u64,
}

#[derive(Clone, Debug, Eq, Hash, PartialEq)]
struct SyntaxRequestKey {
    request_id: u64,
    request_generation: u64,
    origin_id: String,
    origin_generation: u64,
}

impl SyntaxRequestKey {
    fn from_request(request: &SyntaxHighlightRequest) -> Self {
        Self {
            request_id: request.request_id,
            request_generation: request.request_generation,
            origin_id: request.origin_id.clone(),
            origin_generation: request.origin_generation,
        }
    }
}

struct PendingRequest {
    key: SyntaxRequestKey,
    request: Arc<SyntaxHighlightRequest>,
    cancellation_token: CancellationToken,
}

struct PreCancelledRequest {
    key: SyntaxRequestKey,
    reason: String,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
enum SyntaxServerPhase {
    AwaitHello,
    Open,
    Draining,
    Closed,
}

struct SyntaxServerSession {
    identity: SyntaxServerIdentity,
    limits: SyntaxServerLimits,
    protocol_limits: SyntaxHighlightLimits,
    phase: SyntaxServerPhase,
    negotiated_max_frame_bytes: usize,
    pending: HashMap<u64, PendingRequest>,
    pre_cancelled: VecDeque<PreCancelledRequest>,
    terminal_history: VecDeque<SyntaxRequestKey>,
    acknowledge_shutdown: bool,
    engine_shutdown_sent: bool,
    summary: SyntaxServerRuntimeSummary,
}

impl SyntaxServerSession {
    fn new(
        identity: SyntaxServerIdentity,
        limits: SyntaxServerLimits,
        protocol_limits: SyntaxHighlightLimits,
    ) -> eyre::Result<Self> {
        limits.validate()?;
        protocol_limits.validate()?;
        if identity.server_name.trim().is_empty() || identity.server_version.trim().is_empty() {
            eyre::bail!("syntax-server identity fields must not be blank");
        }
        Ok(Self {
            identity,
            limits,
            protocol_limits,
            phase: SyntaxServerPhase::AwaitHello,
            negotiated_max_frame_bytes: limits.max_frame_bytes,
            pending: HashMap::new(),
            pre_cancelled: VecDeque::new(),
            terminal_history: VecDeque::new(),
            acknowledge_shutdown: false,
            engine_shutdown_sent: false,
            summary: SyntaxServerRuntimeSummary::default(),
        })
    }

    fn record_terminal(&mut self, key: SyntaxRequestKey, outcome: SyntaxHighlightOutcome) {
        self.summary.terminal_requests = self.summary.terminal_requests.saturating_add(1);
        match outcome {
            SyntaxHighlightOutcome::Cancelled => {
                self.summary.cancelled_requests = self.summary.cancelled_requests.saturating_add(1);
            }
            SyntaxHighlightOutcome::Failed => {
                self.summary.failed_requests = self.summary.failed_requests.saturating_add(1);
            }
            SyntaxHighlightOutcome::Highlighted
            | SyntaxHighlightOutcome::UnsupportedLanguage
            | SyntaxHighlightOutcome::InvalidRequest => {}
        }
        self.terminal_history.push_back(key);
        while self.terminal_history.len() > self.limits.max_terminal_history {
            let _ = self.terminal_history.pop_front();
        }
    }

    fn terminal_key(&self, request_id: u64) -> Option<&SyntaxRequestKey> {
        self.terminal_history
            .iter()
            .rev()
            .find(|key| key.request_id == request_id)
    }

    fn start_draining(&mut self, acknowledge_shutdown: bool, cancel_pending: Option<&str>) {
        if self.phase == SyntaxServerPhase::Closed {
            return;
        }
        self.phase = SyntaxServerPhase::Draining;
        self.acknowledge_shutdown |= acknowledge_shutdown;
        if let Some(reason) = cancel_pending {
            for pending in self.pending.values() {
                pending.cancellation_token.request_cancel(reason.to_owned());
            }
        }
    }
}

enum RuntimeEvent {
    ClientFrame(Box<SyntaxServerClientFrame>),
    ClientEof,
    ClientProtocolFailure(String),
    HighlightFinished {
        key: SyntaxRequestKey,
        completion: Box<Result<SyntaxHighlightResult, String>>,
    },
    EngineStopped(SyntaxHighlightEngineMetrics),
}

enum EngineCommand {
    Highlight {
        key: SyntaxRequestKey,
        request: Arc<SyntaxHighlightRequest>,
        cancellation_token: CancellationToken,
    },
    Shutdown,
}

/// Drive one lightweight framed syntax-server connection. A dedicated reader
/// keeps cancellation and ping responsive while one long-lived engine thread
/// owns and reuses the Arborium parser, query cursor, and bounded cache.
///
/// # Errors
///
/// Returns an error for invalid limits/identity, worker startup, protocol
/// writes, or an unexpected worker-channel failure.
pub fn run_syntax_server_runtime<R, W>(
    reader: R,
    writer: &mut W,
    identity: SyntaxServerIdentity,
    server_limits: SyntaxServerLimits,
    engine_limits: SyntaxHighlightEngineLimits,
    host_cancellation: &CancellationToken,
) -> eyre::Result<SyntaxServerRuntimeSummary>
where
    R: Read + Send + 'static,
    W: Write,
{
    let mut session = SyntaxServerSession::new(identity, server_limits, engine_limits.protocol)?;
    let (events_tx, events_rx) =
        mpsc::sync_channel::<RuntimeEvent>(server_limits.max_event_queue_events);
    let engine_tx = spawn_engine_worker(
        engine_limits,
        server_limits.max_pending_requests,
        &events_tx,
    )?;
    spawn_syntax_server_reader(reader, server_limits.max_frame_bytes, &events_tx)?;

    let mut host_shutdown_started = false;
    while session.phase != SyntaxServerPhase::Closed {
        if host_cancellation.is_cancelled() && !host_shutdown_started {
            host_shutdown_started = true;
            session.start_draining(
                false,
                Some(
                    host_cancellation
                        .cancellation_reason()
                        .as_deref()
                        .unwrap_or("syntax-server host cancelled"),
                ),
            );
        }
        maybe_stop_engine(&mut session, &engine_tx)?;

        match events_rx.recv_timeout(SYNTAX_SERVER_EVENT_POLL_INTERVAL) {
            Ok(event) => handle_runtime_event(event, writer, &mut session, &engine_tx)?,
            Err(mpsc::RecvTimeoutError::Timeout) => {}
            Err(mpsc::RecvTimeoutError::Disconnected) => {
                eyre::bail!("syntax-server event channel disconnected before shutdown");
            }
        }
    }

    Ok(session.summary)
}

fn spawn_engine_worker(
    limits: SyntaxHighlightEngineLimits,
    maximum_pending: usize,
    events_tx: &mpsc::SyncSender<RuntimeEvent>,
) -> eyre::Result<mpsc::SyncSender<EngineCommand>> {
    let (commands_tx, commands_rx) = mpsc::sync_channel::<EngineCommand>(maximum_pending);
    let (startup_tx, startup_rx) = mpsc::sync_channel::<Result<(), String>>(1);
    let worker_events = events_tx.clone();
    std::thread::Builder::new()
        .name("sfm-syntax-highlight-engine".to_owned())
        .spawn(move || {
            let mut engine = match SyntaxHighlightEngine::new(limits) {
                Ok(engine) => {
                    let _ = startup_tx.send(Ok(()));
                    engine
                }
                Err(error) => {
                    let _ = startup_tx.send(Err(error.to_string()));
                    return;
                }
            };
            while let Ok(command) = commands_rx.recv() {
                match command {
                    EngineCommand::Highlight {
                        key,
                        request,
                        cancellation_token,
                    } => {
                        let completion = engine
                            .highlight(request.as_ref(), &cancellation_token)
                            .map_err(|error| error.to_string());
                        if worker_events
                            .send(RuntimeEvent::HighlightFinished {
                                key,
                                completion: Box::new(completion),
                            })
                            .is_err()
                        {
                            return;
                        }
                    }
                    EngineCommand::Shutdown => break,
                }
            }
            let _ = worker_events.send(RuntimeEvent::EngineStopped(engine.metrics()));
        })?;
    match startup_rx.recv() {
        Ok(Ok(())) => Ok(commands_tx),
        Ok(Err(message)) => eyre::bail!("syntax-server engine startup failed: {message}"),
        Err(error) => eyre::bail!("syntax-server engine startup channel failed: {error}"),
    }
}

fn spawn_syntax_server_reader<R: Read + Send + 'static>(
    mut reader: R,
    maximum_frame_bytes: usize,
    events_tx: &mpsc::SyncSender<RuntimeEvent>,
) -> std::io::Result<()> {
    let reader_tx = events_tx.clone();
    std::thread::Builder::new()
        .name("sfm-syntax-server-reader".to_owned())
        .spawn(move || {
            loop {
                match read_syntax_server_client_frame(&mut reader, maximum_frame_bytes) {
                    Ok(Some(frame)) => {
                        if reader_tx
                            .send(RuntimeEvent::ClientFrame(Box::new(frame)))
                            .is_err()
                        {
                            break;
                        }
                    }
                    Ok(None) => {
                        let _ = reader_tx.send(RuntimeEvent::ClientEof);
                        break;
                    }
                    Err(error) => {
                        let _ =
                            reader_tx.send(RuntimeEvent::ClientProtocolFailure(error.to_string()));
                        break;
                    }
                }
            }
        })?;
    Ok(())
}

fn handle_runtime_event<W: Write>(
    event: RuntimeEvent,
    writer: &mut W,
    session: &mut SyntaxServerSession,
    engine_tx: &mpsc::SyncSender<EngineCommand>,
) -> eyre::Result<()> {
    match event {
        RuntimeEvent::ClientFrame(frame) => {
            handle_client_frame(*frame, writer, session, engine_tx)?;
        }
        RuntimeEvent::ClientEof => {
            session.start_draining(false, None);
        }
        RuntimeEvent::ClientProtocolFailure(message) => {
            send_error(
                writer,
                session,
                "protocol-failure",
                message,
                SyntaxServerErrorDisposition::Fatal,
                None,
                None,
            )?;
            session.start_draining(false, Some("syntax-server protocol failed"));
        }
        RuntimeEvent::HighlightFinished { key, completion } => {
            finish_highlight(key, *completion, writer, session)?;
        }
        RuntimeEvent::EngineStopped(metrics) => {
            session.summary.query_compilations = metrics.query_compilations;
            session.summary.parse_count = metrics.parse_count;
            session.summary.cache_hits = metrics.cache_hits;
            session.summary.cache_misses = metrics.cache_misses;
            session.summary.cache_evictions = metrics.cache_evictions;
            session.summary.final_cache_entries = metrics.cache_entries;
            session.summary.final_cache_bytes = metrics.cache_retained_bytes;
            if session.acknowledge_shutdown {
                send_frame(
                    writer,
                    session,
                    &SyntaxServerFrame::Shutdown {
                        schema: SYNTAX_SERVER_SHUTDOWN_SCHEMA.to_owned(),
                    },
                )?;
            }
            session.phase = SyntaxServerPhase::Closed;
        }
    }
    maybe_stop_engine(session, engine_tx)
}

fn handle_client_frame<W: Write>(
    frame: SyntaxServerClientFrame,
    writer: &mut W,
    session: &mut SyntaxServerSession,
    engine_tx: &mpsc::SyncSender<EngineCommand>,
) -> eyre::Result<()> {
    if let Err(error) = frame.validate_schema() {
        send_error(
            writer,
            session,
            "schema-mismatch",
            error.to_string(),
            SyntaxServerErrorDisposition::Fatal,
            None,
            None,
        )?;
        session.start_draining(false, Some("syntax-server schema mismatch"));
        return Ok(());
    }
    if session.phase == SyntaxServerPhase::AwaitHello {
        return if let SyntaxServerClientFrame::Hello { hello, .. } = frame {
            handle_hello(&hello, writer, session)
        } else {
            send_error(
                writer,
                session,
                "hello-required",
                "syntax-server hello must be the first frame",
                SyntaxServerErrorDisposition::Fatal,
                None,
                None,
            )?;
            session.start_draining(false, Some("syntax-server hello missing"));
            Ok(())
        };
    }

    match frame {
        SyntaxServerClientFrame::Hello { .. } => {
            send_error(
                writer,
                session,
                "duplicate-hello",
                "syntax-server hello may only be sent once",
                SyntaxServerErrorDisposition::Fatal,
                None,
                None,
            )?;
            session.start_draining(false, Some("duplicate syntax-server hello"));
        }
        SyntaxServerClientFrame::Highlight { request, .. } => {
            if session.phase == SyntaxServerPhase::Open {
                start_highlight(*request, writer, session, engine_tx)?;
            } else {
                send_error(
                    writer,
                    session,
                    "server-draining",
                    "syntax-server is draining and cannot accept new highlights",
                    SyntaxServerErrorDisposition::Request,
                    Some(request.request_id),
                    Some(request.request_generation),
                )?;
            }
        }
        SyntaxServerClientFrame::Cancel {
            request_id,
            request_generation,
            origin_id,
            origin_generation,
            reason,
            ..
        } => handle_cancel(
            request_id,
            request_generation,
            origin_id,
            origin_generation,
            reason,
            writer,
            session,
        )?,
        SyntaxServerClientFrame::Ping { nonce, .. } => {
            send_frame(
                writer,
                session,
                &SyntaxServerFrame::Pong {
                    schema: SYNTAX_SERVER_PING_SCHEMA.to_owned(),
                    nonce,
                },
            )?;
        }
        SyntaxServerClientFrame::Shutdown { reason, .. } => {
            tracing::info!(
                target: "sfm::syntax_server",
                reason,
                pending_requests = session.pending.len(),
                "syntax-server graceful shutdown requested"
            );
            session.start_draining(true, None);
        }
    }
    Ok(())
}

fn handle_hello<W: Write>(
    hello: &super::SyntaxServerClientHello,
    writer: &mut W,
    session: &mut SyntaxServerSession,
) -> eyre::Result<()> {
    let required = SyntaxServerCapability::all();
    let valid_capabilities = required
        .iter()
        .all(|capability| hello.capabilities.contains(capability));
    let client_maximum = usize::try_from(hello.max_frame_bytes).ok();
    if hello.protocol_schema != SYNTAX_SERVER_PROTOCOL_SCHEMA
        || hello.client_name.trim().is_empty()
        || hello.client_version.trim().is_empty()
        || !valid_capabilities
        || client_maximum.is_none_or(|maximum| maximum < MINIMUM_SYNTAX_SERVER_FRAME_BYTES)
    {
        send_error(
            writer,
            session,
            "incompatible-hello",
            "syntax-server hello has an incompatible protocol, capability set, identity, or frame maximum",
            SyntaxServerErrorDisposition::Fatal,
            None,
            None,
        )?;
        session.start_draining(false, Some("incompatible syntax-server hello"));
        return Ok(());
    }
    session.negotiated_max_frame_bytes = session
        .limits
        .max_frame_bytes
        .min(client_maximum.expect("validated client frame maximum"));
    session.phase = SyntaxServerPhase::Open;
    send_frame(
        writer,
        session,
        &SyntaxServerFrame::Hello {
            schema: SYNTAX_SERVER_HELLO_SCHEMA.to_owned(),
            hello: SyntaxServerHelloOutput {
                protocol_schema: SYNTAX_SERVER_PROTOCOL_SCHEMA.to_owned(),
                server_name: session.identity.server_name.clone(),
                server_version: session.identity.server_version.clone(),
                capabilities: SyntaxServerCapability::all().to_vec(),
                max_frame_bytes: session.negotiated_max_frame_bytes as u64,
                max_pending_requests: session.limits.max_pending_requests as u64,
                supported_languages: super::languages::SUPPORTED
                    .iter()
                    .map(|id| (*id).to_owned())
                    .collect(),
                request_schema: super::SYNTAX_HIGHLIGHT_REQUEST_SCHEMA.to_owned(),
                result_schema: super::SYNTAX_HIGHLIGHT_RESULT_SCHEMA.to_owned(),
            },
        },
    )
}

fn start_highlight<W: Write>(
    request: SyntaxHighlightRequest,
    writer: &mut W,
    session: &mut SyntaxServerSession,
    engine_tx: &mpsc::SyncSender<EngineCommand>,
) -> eyre::Result<()> {
    if !request_identity_is_bounded_for_response(&request) {
        return send_error(
            writer,
            session,
            "invalid-request",
            "syntax request identity fields exceed admission bounds",
            SyntaxServerErrorDisposition::Request,
            Some(request.request_id),
            Some(request.request_generation),
        );
    }
    let key = SyntaxRequestKey::from_request(&request);
    if reject_reused_request_key(&key, writer, session)? {
        return Ok(());
    }
    if let Some(index) = session
        .pre_cancelled
        .iter()
        .position(|cancelled| cancelled.key == key)
    {
        let cancelled = session
            .pre_cancelled
            .remove(index)
            .expect("pre-cancel index must remain valid");
        let result = cancelled_result(&request, &cancelled.reason);
        send_highlight_result(writer, session, key, result)?;
        return Ok(());
    }
    if session.pending.len() >= session.limits.max_pending_requests {
        let result = failed_result(
            &request,
            "syntax.server-busy",
            "syntax-server pending request limit is reached",
        );
        send_highlight_result(writer, session, key, result)?;
        return Ok(());
    }
    if request.source.len() > session.protocol_limits.max_source_bytes {
        let result = invalid_request_result(
            &request,
            &format!(
                "syntax source contains {} bytes, exceeding maximum {}",
                request.source.len(),
                session.protocol_limits.max_source_bytes
            ),
        );
        return send_highlight_result(writer, session, key, result);
    }
    if let Err(error) = request.validate(session.protocol_limits) {
        let message = truncate_utf8(&error.to_string(), MAX_VALIDATION_MESSAGE_BYTES);
        let result = invalid_request_result(&request, &message);
        return send_highlight_result(writer, session, key, result);
    }

    let cancellation_token = CancellationToken::new();
    let request = Arc::new(request);
    let command = EngineCommand::Highlight {
        key: key.clone(),
        request: Arc::clone(&request),
        cancellation_token: cancellation_token.clone(),
    };
    session.pending.insert(
        key.request_id,
        PendingRequest {
            key: key.clone(),
            request,
            cancellation_token,
        },
    );
    match engine_tx.try_send(command) {
        Ok(()) => Ok(()),
        Err(mpsc::TrySendError::Full(_)) => {
            let pending = session
                .pending
                .remove(&key.request_id)
                .expect("just-inserted syntax request");
            let result = failed_result(
                &pending.request,
                "syntax.server-busy",
                "syntax-server engine queue is full",
            );
            send_highlight_result(writer, session, key, result)
        }
        Err(mpsc::TrySendError::Disconnected(_)) => {
            let pending = session
                .pending
                .remove(&key.request_id)
                .expect("just-inserted syntax request");
            let result = failed_result(
                &pending.request,
                "syntax.engine-unavailable",
                "syntax-server engine worker is unavailable",
            );
            send_highlight_result(writer, session, key, result)
        }
    }
}

fn reject_reused_request_key<W: Write>(
    key: &SyntaxRequestKey,
    writer: &mut W,
    session: &SyntaxServerSession,
) -> eyre::Result<bool> {
    if let Some(existing) = session.pending.get(&key.request_id) {
        let code = if existing.key == *key {
            "duplicate-request"
        } else {
            "stale-request-generation"
        };
        send_error(
            writer,
            session,
            code,
            "syntax request id is already pending with the same or another generation",
            SyntaxServerErrorDisposition::Request,
            Some(key.request_id),
            Some(key.request_generation),
        )?;
        return Ok(true);
    }
    if let Some(existing) = session.terminal_key(key.request_id) {
        let code = if existing == key {
            "duplicate-terminal-request"
        } else {
            "stale-request-generation"
        };
        send_error(
            writer,
            session,
            code,
            "syntax request id already has a terminal outcome",
            SyntaxServerErrorDisposition::Request,
            Some(key.request_id),
            Some(key.request_generation),
        )?;
        return Ok(true);
    }
    Ok(false)
}

fn handle_cancel<W: Write>(
    request_id: u64,
    request_generation: u64,
    origin_id: String,
    origin_generation: u64,
    reason: String,
    writer: &mut W,
    session: &mut SyntaxServerSession,
) -> eyre::Result<()> {
    if request_id == 0
        || request_generation == 0
        || origin_generation == 0
        || origin_id.trim().is_empty()
        || origin_id.len() > 256
        || reason.len() > MAX_CANCELLATION_REASON_BYTES
    {
        return send_error(
            writer,
            session,
            "invalid-cancellation",
            "syntax cancellation identity or reason exceeds admission bounds",
            SyntaxServerErrorDisposition::Request,
            Some(request_id),
            Some(request_generation),
        );
    }
    let key = SyntaxRequestKey {
        request_id,
        request_generation,
        origin_id,
        origin_generation,
    };
    let status = if let Some(pending) = session.pending.get(&request_id) {
        if pending.key == key {
            pending.cancellation_token.request_cancel(reason);
            SyntaxServerCancellationStatus::CancellationRequested
        } else {
            SyntaxServerCancellationStatus::StaleGeneration
        }
    } else if let Some(terminal) = session.terminal_key(request_id) {
        if terminal == &key {
            SyntaxServerCancellationStatus::AlreadyTerminal
        } else {
            SyntaxServerCancellationStatus::StaleGeneration
        }
    } else if let Some(pre_cancelled) = session
        .pre_cancelled
        .iter()
        .find(|cancelled| cancelled.key.request_id == request_id)
    {
        if pre_cancelled.key == key {
            SyntaxServerCancellationStatus::RecordedBeforeRequest
        } else {
            SyntaxServerCancellationStatus::StaleGeneration
        }
    } else if session.pre_cancelled.len() >= session.limits.max_pre_cancelled_requests {
        SyntaxServerCancellationStatus::RejectedCapacity
    } else {
        session.pre_cancelled.push_back(PreCancelledRequest {
            key: key.clone(),
            reason,
        });
        SyntaxServerCancellationStatus::RecordedBeforeRequest
    };
    send_frame(
        writer,
        session,
        &SyntaxServerFrame::Cancelled {
            schema: SYNTAX_SERVER_CANCEL_SCHEMA.to_owned(),
            cancellation: SyntaxServerCancellationOutput {
                request_id: key.request_id,
                request_generation: key.request_generation,
                origin_id: key.origin_id,
                origin_generation: key.origin_generation,
                status,
            },
        },
    )
}

fn finish_highlight<W: Write>(
    key: SyntaxRequestKey,
    completion: Result<SyntaxHighlightResult, String>,
    writer: &mut W,
    session: &mut SyntaxServerSession,
) -> eyre::Result<()> {
    let Some(pending) = session.pending.remove(&key.request_id) else {
        return Ok(());
    };
    if pending.key != key {
        return Ok(());
    }
    let mut result = match completion {
        Ok(result) => result,
        Err(message) => failed_result(&pending.request, "syntax.engine-failed", &message),
    };
    if pending.cancellation_token.is_cancelled()
        && result.outcome != SyntaxHighlightOutcome::Cancelled
    {
        result = cancelled_result(
            &pending.request,
            pending
                .cancellation_token
                .cancellation_reason()
                .as_deref()
                .unwrap_or("syntax request cancelled"),
        );
    }
    if result.request_id != pending.request.request_id
        || result.request_generation != pending.request.request_generation
        || result.origin_id != pending.request.origin_id
        || result.origin_generation != pending.request.origin_generation
        || result.source_sha256 != pending.request.source_sha256
    {
        result = failed_result(
            &pending.request,
            "syntax.engine-identity-mismatch",
            "syntax engine returned a result for a different immutable request",
        );
    }
    send_highlight_result(writer, session, key, result)
}

fn send_highlight_result<W: Write>(
    writer: &mut W,
    session: &mut SyntaxServerSession,
    key: SyntaxRequestKey,
    result: SyntaxHighlightResult,
) -> eyre::Result<()> {
    let mut frame = SyntaxServerFrame::HighlightResult {
        schema: SYNTAX_SERVER_HIGHLIGHT_SCHEMA.to_owned(),
        result: Box::new(result),
    };
    frame.validate_schema()?;
    let encoded = match encode_server_frame_bounded(&frame, session.negotiated_max_frame_bytes)? {
        BoundedFrameEncoding::Encoded(encoded) => encoded,
        BoundedFrameEncoding::TooLarge => {
            replace_with_oversized_result_failure(
                &mut frame,
                format!(
                    "Encoded syntax result exceeded negotiated frame maximum {}",
                    session.negotiated_max_frame_bytes
                ),
            );
            match encode_server_frame_bounded(&frame, session.negotiated_max_frame_bytes)? {
                BoundedFrameEncoding::Encoded(encoded) => encoded,
                BoundedFrameEncoding::TooLarge => {
                    eyre::bail!(
                        "compact syntax result exceeds negotiated frame maximum {}",
                        session.negotiated_max_frame_bytes
                    );
                }
            }
        }
    };
    let outcome = match &frame {
        SyntaxServerFrame::HighlightResult { result, .. } => result.outcome,
        _ => unreachable!("constructed syntax highlight result frame"),
    };
    writer.write_all(&encoded)?;
    writer.flush()?;
    session.record_terminal(key, outcome);
    Ok(())
}

enum BoundedFrameEncoding {
    Encoded(Vec<u8>),
    TooLarge,
}

struct BoundedFrameWriter {
    framed: Vec<u8>,
    maximum_payload_bytes: usize,
    exceeded: bool,
}

impl BoundedFrameWriter {
    fn new(maximum_payload_bytes: usize) -> Self {
        Self {
            framed: vec![0_u8; 4],
            maximum_payload_bytes,
            exceeded: false,
        }
    }

    fn finish(mut self) -> eyre::Result<Vec<u8>> {
        let payload_length = self.framed.len().saturating_sub(4);
        let payload_length = u32::try_from(payload_length)
            .map_err(|error| eyre::eyre!("syntax result frame length overflow: {error}"))?;
        self.framed[..4].copy_from_slice(&payload_length.to_le_bytes());
        Ok(self.framed)
    }
}

impl Write for BoundedFrameWriter {
    fn write(&mut self, buf: &[u8]) -> std::io::Result<usize> {
        let payload_length = self.framed.len().saturating_sub(4);
        if payload_length.saturating_add(buf.len()) > self.maximum_payload_bytes {
            self.exceeded = true;
            return Err(std::io::Error::new(
                std::io::ErrorKind::FileTooLarge,
                "syntax result frame exceeds negotiated maximum",
            ));
        }
        self.framed.extend_from_slice(buf);
        Ok(buf.len())
    }

    fn flush(&mut self) -> std::io::Result<()> {
        Ok(())
    }
}

fn encode_server_frame_bounded(
    frame: &SyntaxServerFrame,
    maximum_payload_bytes: usize,
) -> eyre::Result<BoundedFrameEncoding> {
    let mut writer = BoundedFrameWriter::new(maximum_payload_bytes);
    match facet_json::to_writer_std(&mut writer, frame) {
        Ok(()) => writer.finish().map(BoundedFrameEncoding::Encoded),
        Err(_error) if writer.exceeded => Ok(BoundedFrameEncoding::TooLarge),
        Err(error) => Err(eyre::eyre!(
            "could not encode bounded syntax result frame: {error}"
        )),
    }
}

fn replace_with_oversized_result_failure(frame: &mut SyntaxServerFrame, message: String) {
    let SyntaxServerFrame::HighlightResult { result, .. } = frame else {
        unreachable!("oversized response fallback only accepts highlight results");
    };
    result.outcome = SyntaxHighlightOutcome::Failed;
    result.complete = false;
    result.spans = Vec::new();
    result.diagnostics = vec![SyntaxHighlightDiagnostic {
        code: "syntax.result-frame-too-large".to_owned(),
        severity: SyntaxHighlightDiagnosticSeverity::Error,
        message,
        start_byte: None,
        end_byte: None,
    }];
}

fn cancelled_result(request: &SyntaxHighlightRequest, reason: &str) -> SyntaxHighlightResult {
    SyntaxHighlightResult::terminal(
        request,
        SyntaxHighlightOutcome::Cancelled,
        vec![SyntaxHighlightDiagnostic {
            code: "syntax.cancelled".to_owned(),
            severity: SyntaxHighlightDiagnosticSeverity::Info,
            message: reason.to_owned(),
            start_byte: None,
            end_byte: None,
        }],
    )
}

fn failed_result(
    request: &SyntaxHighlightRequest,
    code: &str,
    message: &str,
) -> SyntaxHighlightResult {
    SyntaxHighlightResult::terminal(
        request,
        SyntaxHighlightOutcome::Failed,
        vec![SyntaxHighlightDiagnostic {
            code: code.to_owned(),
            severity: SyntaxHighlightDiagnosticSeverity::Error,
            message: message.to_owned(),
            start_byte: None,
            end_byte: None,
        }],
    )
}

fn invalid_request_result(
    request: &SyntaxHighlightRequest,
    message: &str,
) -> SyntaxHighlightResult {
    SyntaxHighlightResult::terminal(
        request,
        SyntaxHighlightOutcome::InvalidRequest,
        vec![SyntaxHighlightDiagnostic {
            code: "syntax.invalid-request".to_owned(),
            severity: SyntaxHighlightDiagnosticSeverity::Error,
            message: message.to_owned(),
            start_byte: None,
            end_byte: None,
        }],
    )
}

fn request_identity_is_bounded_for_response(request: &SyntaxHighlightRequest) -> bool {
    request.schema.len() <= 128
        && request.origin_id.len() <= 256
        && request.language.len() <= 64
        && request.source_sha256.len() <= 128
}

fn truncate_utf8(value: &str, maximum_bytes: usize) -> String {
    if value.len() <= maximum_bytes {
        return value.to_owned();
    }
    let mut boundary = maximum_bytes;
    while !value.is_char_boundary(boundary) {
        boundary = boundary.saturating_sub(1);
    }
    format!("{}…", &value[..boundary])
}

fn send_error<W: Write>(
    writer: &mut W,
    session: &SyntaxServerSession,
    code: impl Into<String>,
    message: impl Into<String>,
    disposition: SyntaxServerErrorDisposition,
    request_id: Option<u64>,
    request_generation: Option<u64>,
) -> eyre::Result<()> {
    send_frame(
        writer,
        session,
        &SyntaxServerFrame::Error {
            schema: SYNTAX_SERVER_ERROR_SCHEMA.to_owned(),
            error: SyntaxServerErrorOutput {
                code: code.into(),
                message: message.into(),
                disposition,
                request_id,
                request_generation,
            },
        },
    )
}

fn send_frame<W: Write>(
    writer: &mut W,
    session: &SyntaxServerSession,
    frame: &SyntaxServerFrame,
) -> eyre::Result<()> {
    frame.validate_schema()?;
    write_syntax_server_frame(writer, frame, session.negotiated_max_frame_bytes)?;
    Ok(())
}

fn maybe_stop_engine(
    session: &mut SyntaxServerSession,
    engine_tx: &mpsc::SyncSender<EngineCommand>,
) -> eyre::Result<()> {
    if session.phase == SyntaxServerPhase::Draining
        && session.pending.is_empty()
        && !session.engine_shutdown_sent
    {
        session.engine_shutdown_sent = true;
        engine_tx
            .send(EngineCommand::Shutdown)
            .map_err(|error| eyre::eyre!("syntax-server engine shutdown failed: {error}"))?;
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::super::SyntaxHighlightCacheStatus;
    use super::super::SyntaxServerClientHello;
    use super::super::encode_syntax_server_client_frame;
    use super::super::read_syntax_server_frame;
    use super::*;
    use std::io::Cursor;

    fn hello() -> SyntaxServerClientFrame {
        hello_with_maximum(DEFAULT_SYNTAX_SERVER_MAX_FRAME_BYTES)
    }

    fn hello_with_maximum(maximum: usize) -> SyntaxServerClientFrame {
        SyntaxServerClientFrame::hello(SyntaxServerClientHello {
            protocol_schema: SYNTAX_SERVER_PROTOCOL_SCHEMA.to_owned(),
            client_name: "minecraft-test".to_owned(),
            client_version: "1".to_owned(),
            capabilities: SyntaxServerCapability::all().to_vec(),
            max_frame_bytes: maximum as u64,
        })
    }

    fn request(id: u64, generation: u64, source: &str) -> SyntaxHighlightRequest {
        SyntaxHighlightRequest::new(
            id,
            generation,
            "editor:java",
            generation,
            "java",
            source.to_owned(),
            4096,
        )
    }

    fn encode_frames(frames: &[SyntaxServerClientFrame]) -> Vec<u8> {
        encode_frames_with_maximum(frames, DEFAULT_SYNTAX_SERVER_MAX_FRAME_BYTES)
    }

    fn encode_frames_with_maximum(frames: &[SyntaxServerClientFrame], maximum: usize) -> Vec<u8> {
        let mut bytes = Vec::new();
        for frame in frames {
            bytes.extend(encode_syntax_server_client_frame(frame, maximum).expect("client frame"));
        }
        bytes
    }

    fn run(
        frames: &[SyntaxServerClientFrame],
    ) -> (Vec<SyntaxServerFrame>, SyntaxServerRuntimeSummary) {
        run_with_limits(
            frames,
            SyntaxServerLimits::default(),
            SyntaxHighlightEngineLimits::default(),
        )
    }

    fn run_with_limits(
        frames: &[SyntaxServerClientFrame],
        server_limits: SyntaxServerLimits,
        engine_limits: SyntaxHighlightEngineLimits,
    ) -> (Vec<SyntaxServerFrame>, SyntaxServerRuntimeSummary) {
        let input = Cursor::new(encode_frames_with_maximum(
            frames,
            server_limits.max_frame_bytes,
        ));
        let mut output = Vec::new();
        let summary = run_syntax_server_runtime(
            input,
            &mut output,
            SyntaxServerIdentity {
                server_name: "sfm-propagate-changes".to_owned(),
                server_version: "test".to_owned(),
            },
            server_limits,
            engine_limits,
            &CancellationToken::new(),
        )
        .expect("syntax-server runtime");
        let mut decoded = Vec::new();
        let mut cursor = Cursor::new(output);
        while let Some(frame) = read_syntax_server_frame(&mut cursor, server_limits.max_frame_bytes)
            .expect("server frame")
        {
            decoded.push(frame);
        }
        (decoded, summary)
    }

    #[test]
    fn syntax_highlight_server_reuses_one_engine_and_matches_direct_semantics() {
        let source = "package p;\r\nclass Café { String smile = \"🙂\"; }\r\n";
        let first = request(1, 1, source);
        let second = request(2, 2, source);
        let mut direct_engine =
            SyntaxHighlightEngine::new(SyntaxHighlightEngineLimits::default()).expect("engine");
        let direct = direct_engine
            .highlight(&first, &CancellationToken::new())
            .expect("direct result");

        let (frames, summary) = run(&[
            hello(),
            SyntaxServerClientFrame::highlight(first.clone()),
            SyntaxServerClientFrame::highlight(second.clone()),
            SyntaxServerClientFrame::ping(42),
            SyntaxServerClientFrame::shutdown("test complete"),
        ]);
        assert!(
            matches!(frames.first(), Some(SyntaxServerFrame::Hello { hello, .. })
            if hello.supported_languages.iter().map(String::as_str).collect::<Vec<_>>() == super::super::languages::SUPPORTED
                && hello.max_pending_requests == DEFAULT_SYNTAX_SERVER_MAX_PENDING_REQUESTS as u64)
        );
        assert!(
            frames
                .iter()
                .any(|frame| matches!(frame, SyntaxServerFrame::Pong { nonce: 42, .. }))
        );
        assert!(matches!(
            frames.last(),
            Some(SyntaxServerFrame::Shutdown { .. })
        ));

        let results = frames
            .iter()
            .filter_map(|frame| match frame {
                SyntaxServerFrame::HighlightResult { result, .. } => Some(result.as_ref()),
                _ => None,
            })
            .collect::<Vec<_>>();
        assert_eq!(results.len(), 2);
        assert_eq!(results[0].request_id, first.request_id);
        assert_eq!(results[0].outcome, direct.outcome);
        assert_eq!(results[0].spans, direct.spans);
        assert_eq!(results[0].diagnostics, direct.diagnostics);
        assert_eq!(results[0].parser_fingerprint, direct.parser_fingerprint);
        results[0]
            .validate_against(&first, SyntaxHighlightLimits::default())
            .expect("first worker result");
        results[1]
            .validate_against(&second, SyntaxHighlightLimits::default())
            .expect("second worker result");
        assert_eq!(results[1].cache.status, SyntaxHighlightCacheStatus::Hit);
        assert_eq!(summary.terminal_requests, 2);
        assert_eq!(summary.query_compilations, 1);
        assert_eq!(summary.parse_count, 1);
        assert_eq!(summary.cache_hits, 1);
    }

    #[test]
    fn syntax_highlight_server_pre_cancel_is_terminal_and_generation_safe() {
        let target = request(7, 3, "class Cancelled {}");
        let (frames, summary) = run(&[
            hello(),
            SyntaxServerClientFrame::cancel(
                target.request_id,
                target.request_generation,
                target.origin_id.clone(),
                target.origin_generation,
                "cursor moved",
            ),
            SyntaxServerClientFrame::highlight(target.clone()),
            SyntaxServerClientFrame::cancel(
                target.request_id,
                target.request_generation + 1,
                target.origin_id.clone(),
                target.origin_generation + 1,
                "stale cancellation",
            ),
            SyntaxServerClientFrame::shutdown("done"),
        ]);
        assert!(frames.iter().any(|frame| matches!(
            frame,
            SyntaxServerFrame::Cancelled { cancellation, .. }
                if cancellation.status == SyntaxServerCancellationStatus::RecordedBeforeRequest
        )));
        assert!(frames.iter().any(|frame| matches!(
            frame,
            SyntaxServerFrame::HighlightResult { result, .. }
                if result.request_id == 7 && result.outcome == SyntaxHighlightOutcome::Cancelled
        )));
        assert!(frames.iter().any(|frame| matches!(
            frame,
            SyntaxServerFrame::Cancelled { cancellation, .. }
                if cancellation.status == SyntaxServerCancellationStatus::StaleGeneration
        )));
        assert_eq!(summary.cancelled_requests, 1);
        assert_eq!(summary.parse_count, 0);
    }

    #[test]
    fn syntax_highlight_server_during_and_post_cancel_have_stable_terminal_status() {
        let target = request(11, 4, "class Active {}");
        let key = SyntaxRequestKey::from_request(&target);
        let token = CancellationToken::new();
        let mut session = SyntaxServerSession::new(
            SyntaxServerIdentity {
                server_name: "sfm-propagate-changes".to_owned(),
                server_version: "test".to_owned(),
            },
            SyntaxServerLimits::default(),
            SyntaxHighlightLimits::default(),
        )
        .expect("session");
        session.phase = SyntaxServerPhase::Open;
        session.pending.insert(
            target.request_id,
            PendingRequest {
                key: key.clone(),
                request: Arc::new(target.clone()),
                cancellation_token: token.clone(),
            },
        );

        let mut output = Vec::new();
        handle_cancel(
            key.request_id,
            key.request_generation,
            key.origin_id.clone(),
            key.origin_generation,
            "cursor moved".to_owned(),
            &mut output,
            &mut session,
        )
        .expect("during cancellation");
        assert!(token.is_cancelled());
        assert!(matches!(
            read_syntax_server_frame(
                &mut Cursor::new(output),
                DEFAULT_SYNTAX_SERVER_MAX_FRAME_BYTES,
            )
            .expect("cancel frame"),
            Some(SyntaxServerFrame::Cancelled { cancellation, .. })
                if cancellation.status == SyntaxServerCancellationStatus::CancellationRequested
        ));

        let _ = session.pending.remove(&key.request_id);
        session.record_terminal(key.clone(), SyntaxHighlightOutcome::Cancelled);
        let mut post_output = Vec::new();
        handle_cancel(
            key.request_id,
            key.request_generation,
            key.origin_id.clone(),
            key.origin_generation,
            "late".to_owned(),
            &mut post_output,
            &mut session,
        )
        .expect("post cancellation");
        assert!(matches!(
            read_syntax_server_frame(
                &mut Cursor::new(post_output),
                DEFAULT_SYNTAX_SERVER_MAX_FRAME_BYTES,
            )
            .expect("post frame"),
            Some(SyntaxServerFrame::Cancelled { cancellation, .. })
                if cancellation.status == SyntaxServerCancellationStatus::AlreadyTerminal
        ));
    }

    #[test]
    fn syntax_highlight_server_clean_eof_drains_accepted_work_without_fake_ack() {
        let target = request(19, 1, "class Eof {}");
        let (frames, summary) = run(&[hello(), SyntaxServerClientFrame::highlight(target.clone())]);
        assert!(frames.iter().any(|frame| matches!(
            frame,
            SyntaxServerFrame::HighlightResult { result, .. }
                if result.request_id == target.request_id
                    && result.outcome == SyntaxHighlightOutcome::Highlighted
        )));
        assert!(
            !frames
                .iter()
                .any(|frame| matches!(frame, SyntaxServerFrame::Shutdown { .. }))
        );
        assert_eq!(summary.terminal_requests, 1);
    }

    #[test]
    fn syntax_highlight_server_rejects_incompatible_hello_without_index_setup() {
        let incompatible = SyntaxServerClientFrame::hello(SyntaxServerClientHello {
            protocol_schema: "sfm.syntax-server/999".to_owned(),
            client_name: "minecraft-test".to_owned(),
            client_version: "1".to_owned(),
            capabilities: vec![SyntaxServerCapability::Highlight],
            max_frame_bytes: 1024,
        });
        let (frames, summary) = run(&[incompatible]);
        assert!(matches!(
            frames.as_slice(),
            [SyntaxServerFrame::Error { error, .. }]
                if error.disposition == SyntaxServerErrorDisposition::Fatal
                    && error.code == "incompatible-hello"
        ));
        assert_eq!(summary.query_compilations, 1);
        assert_eq!(summary.parse_count, 0);
    }

    #[test]
    fn syntax_highlight_server_pending_limit_returns_typed_terminal_result() {
        let source = format!("class Large {{ /* {} */ }}", "x".repeat(2_000_000));
        let first = request(1, 1, &source);
        let second = request(2, 1, &source.replace("Large", "Second"));
        let limits = SyntaxServerLimits {
            max_pending_requests: 1,
            ..SyntaxServerLimits::default()
        };
        let input = Cursor::new(encode_frames(&[
            hello(),
            SyntaxServerClientFrame::highlight(first),
            SyntaxServerClientFrame::highlight(second),
            SyntaxServerClientFrame::shutdown("done"),
        ]));
        let mut output = Vec::new();
        let summary = run_syntax_server_runtime(
            input,
            &mut output,
            SyntaxServerIdentity {
                server_name: "sfm-propagate-changes".to_owned(),
                server_version: "test".to_owned(),
            },
            limits,
            SyntaxHighlightEngineLimits::default(),
            &CancellationToken::new(),
        )
        .expect("bounded server");
        assert_eq!(summary.terminal_requests, 2);
        assert_eq!(summary.failed_requests, 1);
    }

    #[test]
    fn syntax_highlight_server_contains_oversized_result_and_keeps_serving() {
        let maximum = MINIMUM_SYNTAX_SERVER_FRAME_BYTES;
        let mut fields = String::new();
        for index in 0..100 {
            use std::fmt::Write as _;
            write!(&mut fields, "int field{index} = {index};").expect("write to String");
        }
        let source = format!("class Many {{ {fields} }}");
        let target = request(1, 1, &source);
        let limits = SyntaxServerLimits {
            max_frame_bytes: maximum,
            ..SyntaxServerLimits::default()
        };
        let (frames, summary) = run_with_limits(
            &[
                hello_with_maximum(maximum),
                SyntaxServerClientFrame::highlight(target),
                SyntaxServerClientFrame::ping(77),
                SyntaxServerClientFrame::shutdown("done"),
            ],
            limits,
            SyntaxHighlightEngineLimits::default(),
        );

        assert!(frames.iter().any(|frame| matches!(
            frame,
            SyntaxServerFrame::HighlightResult { result, .. }
                if result.outcome == SyntaxHighlightOutcome::Failed
                    && result.spans.is_empty()
                    && result.diagnostics.iter().any(|diagnostic|
                        diagnostic.code == "syntax.result-frame-too-large")
        )));
        assert!(
            frames
                .iter()
                .any(|frame| matches!(frame, SyntaxServerFrame::Pong { nonce: 77, .. }))
        );
        assert!(matches!(
            frames.last(),
            Some(SyntaxServerFrame::Shutdown { .. })
        ));
        assert_eq!(summary.terminal_requests, 1);
        assert_eq!(summary.failed_requests, 1);
        assert_eq!(summary.parse_count, 1);
    }

    #[test]
    fn syntax_highlight_server_rejects_source_before_engine_retention() {
        let mut engine_limits = SyntaxHighlightEngineLimits::default();
        engine_limits.protocol.max_source_bytes = 32;
        let target = request(
            1,
            1,
            &format!("class TooLarge {{ /* {} */ }}", "x".repeat(64)),
        );
        let (frames, summary) = run_with_limits(
            &[
                hello(),
                SyntaxServerClientFrame::highlight(target),
                SyntaxServerClientFrame::ping(88),
                SyntaxServerClientFrame::shutdown("done"),
            ],
            SyntaxServerLimits::default(),
            engine_limits,
        );

        assert!(frames.iter().any(|frame| matches!(
            frame,
            SyntaxServerFrame::HighlightResult { result, .. }
                if result.outcome == SyntaxHighlightOutcome::InvalidRequest
                    && result.diagnostics.iter().any(|diagnostic|
                        diagnostic.code == "syntax.invalid-request")
        )));
        assert!(
            frames
                .iter()
                .any(|frame| matches!(frame, SyntaxServerFrame::Pong { nonce: 88, .. }))
        );
        assert_eq!(summary.parse_count, 0);
        assert_eq!(summary.terminal_requests, 1);
    }

    #[test]
    fn syntax_highlight_server_bounded_event_queue_preserves_progress() {
        let limits = SyntaxServerLimits {
            max_event_queue_events: 1,
            ..SyntaxServerLimits::default()
        };
        let mut frames = vec![
            hello(),
            SyntaxServerClientFrame::highlight(request(1, 1, "class Queue {}")),
        ];
        frames.extend((0..16).map(SyntaxServerClientFrame::ping));
        frames.push(SyntaxServerClientFrame::shutdown("done"));
        let (responses, summary) =
            run_with_limits(&frames, limits, SyntaxHighlightEngineLimits::default());

        assert_eq!(
            responses
                .iter()
                .filter(|frame| matches!(frame, SyntaxServerFrame::Pong { .. }))
                .count(),
            16
        );
        assert!(matches!(
            responses.last(),
            Some(SyntaxServerFrame::Shutdown { .. })
        ));
        assert_eq!(summary.terminal_requests, 1);
    }

    #[test]
    fn syntax_highlight_server_does_not_retain_oversized_cancellation_reason() {
        let (frames, summary) = run(&[
            hello(),
            SyntaxServerClientFrame::cancel(
                1,
                1,
                "editor:java",
                1,
                "x".repeat(MAX_CANCELLATION_REASON_BYTES + 1),
            ),
            SyntaxServerClientFrame::shutdown("done"),
        ]);
        assert!(frames.iter().any(|frame| matches!(
            frame,
            SyntaxServerFrame::Error { error, .. }
                if error.code == "invalid-cancellation"
                    && error.disposition == SyntaxServerErrorDisposition::Request
        )));
        assert_eq!(summary.terminal_requests, 0);
    }
}
