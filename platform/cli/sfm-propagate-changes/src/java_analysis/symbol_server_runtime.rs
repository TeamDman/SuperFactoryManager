use super::DefinitionAtPositionEngine;
use super::DefinitionAtPositionEngineTelemetry;
use super::SymbolServerEffect;
use super::SymbolServerEngineError;
use super::SymbolServerFrame;
use super::SymbolServerPhase;
use super::SymbolServerRequestKey;
use super::SymbolServerState;
use super::SymbolServerWorkspaceGenerationOutput;
use super::read_symbol_server_client_frame;
use super::write_symbol_server_frame;
use crate::cancellation::CancellationToken;
use facet::Facet;
use std::io::Read;
use std::io::Write;
use std::sync::Arc;
use std::sync::mpsc;
use std::time::Duration;

const SYMBOL_SERVER_EVENT_POLL_INTERVAL: Duration = Duration::from_millis(10);

#[derive(Facet, Clone, Debug, Eq, PartialEq)]
pub struct SymbolServerRuntimeSummary {
    pub completed_requests: u64,
    pub failed_requests: u64,
    pub final_cache_entries: u64,
    pub final_cache_bytes: u64,
}

enum RuntimeEvent {
    ClientFrame(Box<super::SymbolServerClientFrame>),
    ClientEof,
    ClientProtocolFailure(String),
    DefinitionFinished {
        key: SymbolServerRequestKey,
        completion: Box<Result<super::DefinitionAtPositionResult, SymbolServerEngineError>>,
        telemetry: Option<DefinitionAtPositionEngineTelemetry>,
    },
    UsageAtPositionFinished {
        key: SymbolServerRequestKey,
        completion: Box<Result<super::UsageAtPositionResult, SymbolServerEngineError>>,
        telemetry: Option<DefinitionAtPositionEngineTelemetry>,
    },
    JavaInteractionMapFinished {
        key: SymbolServerRequestKey,
        completion: Box<Result<super::JavaInteractionMapResult, SymbolServerEngineError>>,
        telemetry: Option<DefinitionAtPositionEngineTelemetry>,
    },
}

#[derive(Default)]
struct RuntimeCounters {
    completed_requests: u64,
    failed_requests: u64,
}

/// Drive one framed symbol-server connection with a dedicated blocking reader
/// and bounded per-request engine threads. The state machine remains the sole
/// authority for request admission and terminal responses.
///
/// # Errors
///
/// Returns an error when the reader thread cannot start, a response cannot be
/// written, or workspace-generation cache replacement fails.
pub fn run_symbol_server_runtime<R, W>(
    reader: R,
    writer: &mut W,
    state: &mut SymbolServerState,
    engine: &Arc<DefinitionAtPositionEngine>,
    host_cancellation: &CancellationToken,
    maximum_frame_bytes: usize,
) -> eyre::Result<SymbolServerRuntimeSummary>
where
    R: Read + Send + 'static,
    W: Write,
{
    let (events_tx, events_rx) = mpsc::channel::<RuntimeEvent>();
    spawn_symbol_server_reader(reader, maximum_frame_bytes, &events_tx)?;

    let mut host_shutdown_started = false;
    let mut counters = RuntimeCounters::default();
    while state.phase() != SymbolServerPhase::Closed {
        if host_cancellation.is_cancelled() && !host_shutdown_started {
            host_shutdown_started = true;
            apply_effects(
                state.handle_host_shutdown(
                    host_cancellation
                        .cancellation_reason()
                        .unwrap_or_else(|| "symbol-server host cancelled".to_owned()),
                ),
                writer,
                state,
                engine,
                &events_tx,
            )?;
            continue;
        }

        match events_rx.recv_timeout(SYMBOL_SERVER_EVENT_POLL_INTERVAL) {
            Ok(event) => {
                handle_runtime_event(event, writer, state, engine, &events_tx, &mut counters)?;
            }
            Err(mpsc::RecvTimeoutError::Timeout) => {}
            Err(mpsc::RecvTimeoutError::Disconnected) => {
                apply_effects(state.handle_eof(), writer, state, engine, &events_tx)?;
            }
        }
    }

    let cache = engine.cache_snapshot();
    Ok(SymbolServerRuntimeSummary {
        completed_requests: counters.completed_requests,
        failed_requests: counters.failed_requests,
        final_cache_entries: cache.entries,
        final_cache_bytes: cache.retained_bytes,
    })
}

fn spawn_symbol_server_reader<R: Read + Send + 'static>(
    mut reader: R,
    maximum_frame_bytes: usize,
    events_tx: &mpsc::Sender<RuntimeEvent>,
) -> std::io::Result<()> {
    let reader_tx = events_tx.clone();
    std::thread::Builder::new()
        .name("sfm-symbol-server-reader".to_owned())
        .spawn(move || {
            loop {
                match read_symbol_server_client_frame(&mut reader, maximum_frame_bytes) {
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
    state: &mut SymbolServerState,
    engine: &Arc<DefinitionAtPositionEngine>,
    events_tx: &mpsc::Sender<RuntimeEvent>,
    counters: &mut RuntimeCounters,
) -> eyre::Result<()> {
    let effects = match event {
        RuntimeEvent::ClientFrame(frame) => state.handle_frame(*frame),
        RuntimeEvent::ClientEof => state.handle_eof(),
        RuntimeEvent::ClientProtocolFailure(message) => state.handle_transport_failure(message),
        RuntimeEvent::DefinitionFinished {
            key,
            completion,
            telemetry,
        } => {
            record_definition_completion(counters, key, completion.as_ref(), telemetry.as_ref());
            state.finish_definition(key, *completion)
        }
        RuntimeEvent::UsageAtPositionFinished {
            key,
            completion,
            telemetry,
        } => {
            record_usage_at_position_completion(
                counters,
                key,
                completion.as_ref(),
                telemetry.as_ref(),
            );
            state.finish_usage_at_position(key, *completion)
        }
        RuntimeEvent::JavaInteractionMapFinished {
            key,
            completion,
            telemetry,
        } => {
            record_java_interaction_map_completion(
                counters,
                key,
                completion.as_ref(),
                telemetry.as_ref(),
            );
            state.finish_java_interaction_map(key, *completion)
        }
    };
    apply_effects(effects, writer, state, engine, events_tx)
}

fn record_java_interaction_map_completion(
    counters: &mut RuntimeCounters,
    key: SymbolServerRequestKey,
    completion: &Result<super::JavaInteractionMapResult, SymbolServerEngineError>,
    telemetry: Option<&DefinitionAtPositionEngineTelemetry>,
) {
    if let Some(telemetry) = telemetry {
        counters.completed_requests = counters.completed_requests.saturating_add(1);
        tracing::info!(
            target: "sfm::symbol_server",
            request_id = key.request_id,
            request_generation = key.request_generation,
            workspace_generation = key.workspace_generation,
            total_micros = telemetry.total_micros,
            source_snapshot_micros = telemetry.source_snapshot_micros,
            fact_parse_micros = telemetry.fact_parse_micros,
            link_micros = telemetry.link_micros,
            lookup_micros = telemetry.lookup_micros,
            fact_cache_hits = telemetry.fact_cache_hits,
            fact_cache_misses = telemetry.fact_cache_misses,
            resolution_cache_hits = telemetry.resolution_cache_hits,
            resolution_cache_misses = telemetry.resolution_cache_misses,
            resolution_cache_entries = telemetry.resolution_cache_entries,
            resolution_declarations = telemetry.resolution_declarations,
            resolution_types = telemetry.resolution_types,
            resolution_fields = telemetry.resolution_fields,
            resolution_methods = telemetry.resolution_methods,
            usage_index_build_micros = telemetry.usage_index_build_micros,
            usage_index_cache_hits = telemetry.usage_index_cache_hits,
            usage_index_candidate_files = telemetry.usage_index_candidate_files,
            usage_index_parsed_files = telemetry.usage_index_parsed_files,
            usage_index_retained_rows = telemetry.usage_index_retained_rows,
            usage_index_cache_entries = telemetry.usage_index_cache_entries,
            usage_index_cache_retained_bytes = telemetry.usage_index_cache_retained_bytes,
            usage_index_cache_evictions = telemetry.usage_index_cache_evictions,
            reparsed_files = telemetry.reparsed_files,
            source_files = telemetry.source_files,
            cache_entries = telemetry.cache.entries,
            cache_bytes = telemetry.cache.retained_bytes,
            outcome = ?completion.as_ref().map(|result| &result.outcome),
            "symbol-server Java interaction map completed"
        );
    } else if completion.is_err() {
        counters.failed_requests = counters.failed_requests.saturating_add(1);
        tracing::warn!(
            target: "sfm::symbol_server",
            request_id = key.request_id,
            request_generation = key.request_generation,
            workspace_generation = key.workspace_generation,
            "symbol-server Java interaction map failed or was cancelled"
        );
    }
}

fn record_usage_at_position_completion(
    counters: &mut RuntimeCounters,
    key: SymbolServerRequestKey,
    completion: &Result<super::UsageAtPositionResult, SymbolServerEngineError>,
    telemetry: Option<&DefinitionAtPositionEngineTelemetry>,
) {
    if let Some(telemetry) = telemetry {
        counters.completed_requests = counters.completed_requests.saturating_add(1);
        tracing::info!(
            target: "sfm::symbol_server",
            request_id = key.request_id,
            request_generation = key.request_generation,
            workspace_generation = key.workspace_generation,
            total_micros = telemetry.total_micros,
            source_snapshot_micros = telemetry.source_snapshot_micros,
            fact_parse_micros = telemetry.fact_parse_micros,
            link_micros = telemetry.link_micros,
            lookup_micros = telemetry.lookup_micros,
            fact_cache_hits = telemetry.fact_cache_hits,
            fact_cache_misses = telemetry.fact_cache_misses,
            resolution_cache_hits = telemetry.resolution_cache_hits,
            resolution_cache_misses = telemetry.resolution_cache_misses,
            resolution_cache_entries = telemetry.resolution_cache_entries,
            resolution_declarations = telemetry.resolution_declarations,
            resolution_types = telemetry.resolution_types,
            resolution_fields = telemetry.resolution_fields,
            resolution_methods = telemetry.resolution_methods,
            usage_index_build_micros = telemetry.usage_index_build_micros,
            usage_index_cache_hits = telemetry.usage_index_cache_hits,
            usage_index_candidate_files = telemetry.usage_index_candidate_files,
            usage_index_parsed_files = telemetry.usage_index_parsed_files,
            usage_index_retained_rows = telemetry.usage_index_retained_rows,
            usage_index_cache_entries = telemetry.usage_index_cache_entries,
            usage_index_cache_retained_bytes = telemetry.usage_index_cache_retained_bytes,
            usage_index_cache_evictions = telemetry.usage_index_cache_evictions,
            reparsed_files = telemetry.reparsed_files,
            source_files = telemetry.source_files,
            cache_entries = telemetry.cache.entries,
            cache_bytes = telemetry.cache.retained_bytes,
            "symbol-server usage-at-position completed"
        );
    } else if completion.is_err() {
        counters.failed_requests = counters.failed_requests.saturating_add(1);
        tracing::warn!(
            target: "sfm::symbol_server",
            request_id = key.request_id,
            request_generation = key.request_generation,
            workspace_generation = key.workspace_generation,
            "symbol-server usage-at-position failed or was cancelled"
        );
    }
}

fn record_definition_completion(
    counters: &mut RuntimeCounters,
    key: SymbolServerRequestKey,
    completion: &Result<super::DefinitionAtPositionResult, SymbolServerEngineError>,
    telemetry: Option<&DefinitionAtPositionEngineTelemetry>,
) {
    if let Some(telemetry) = telemetry {
        counters.completed_requests = counters.completed_requests.saturating_add(1);
        tracing::info!(
            target: "sfm::symbol_server",
            request_id = key.request_id,
            request_generation = key.request_generation,
            workspace_generation = key.workspace_generation,
            total_micros = telemetry.total_micros,
            source_snapshot_micros = telemetry.source_snapshot_micros,
            fact_parse_micros = telemetry.fact_parse_micros,
            link_micros = telemetry.link_micros,
            lookup_micros = telemetry.lookup_micros,
            fact_cache_hits = telemetry.fact_cache_hits,
            fact_cache_misses = telemetry.fact_cache_misses,
            resolution_cache_hits = telemetry.resolution_cache_hits,
            resolution_cache_misses = telemetry.resolution_cache_misses,
            resolution_cache_entries = telemetry.resolution_cache_entries,
            resolution_declarations = telemetry.resolution_declarations,
            resolution_types = telemetry.resolution_types,
            resolution_fields = telemetry.resolution_fields,
            resolution_methods = telemetry.resolution_methods,
            reparsed_files = telemetry.reparsed_files,
            source_files = telemetry.source_files,
            cache_entries = telemetry.cache.entries,
            cache_bytes = telemetry.cache.retained_bytes,
            "symbol-server definition completed"
        );
    } else if completion.is_err() {
        counters.failed_requests = counters.failed_requests.saturating_add(1);
        tracing::warn!(
            target: "sfm::symbol_server",
            request_id = key.request_id,
            request_generation = key.request_generation,
            workspace_generation = key.workspace_generation,
            "symbol-server definition failed or was cancelled"
        );
    }
}

#[expect(
    clippy::too_many_lines,
    reason = "the runtime dispatch keeps every effect variant in one exhaustive queue-draining match"
)]
fn apply_effects<W: Write>(
    effects: Vec<SymbolServerEffect>,
    writer: &mut W,
    state: &mut SymbolServerState,
    engine: &Arc<DefinitionAtPositionEngine>,
    events_tx: &mpsc::Sender<RuntimeEvent>,
) -> eyre::Result<()> {
    let mut pending = std::collections::VecDeque::from(effects);
    while let Some(effect) = pending.pop_front() {
        match effect {
            SymbolServerEffect::Send(frame) => {
                apply_send_frame(frame.as_ref(), writer, state, engine)?;
            }
            SymbolServerEffect::StartDefinition {
                request,
                cancellation_token,
            } => {
                let key = SymbolServerRequestKey::from(request.as_ref());
                let engine = Arc::clone(engine);
                let events_tx = events_tx.clone();
                let spawn = std::thread::Builder::new()
                    .name(format!("sfm-symbol-definition-{}", key.request_id))
                    .spawn(move || {
                        let analyzed =
                            engine.analyze_with_telemetry(request.as_ref(), &cancellation_token);
                        let (completion, telemetry) = match analyzed {
                            Ok(output) => (Ok(output.result), Some(output.telemetry)),
                            Err(error) => (
                                Err(SymbolServerEngineError::new(
                                    "definition-engine-failed",
                                    error.to_string(),
                                    false,
                                )),
                                None,
                            ),
                        };
                        let _ = events_tx.send(RuntimeEvent::DefinitionFinished {
                            key,
                            completion: Box::new(completion),
                            telemetry,
                        });
                    });
                if let Err(error) = spawn {
                    pending.extend(state.finish_definition(
                        key,
                        Err(SymbolServerEngineError::new(
                            "definition-engine-thread-failed",
                            error.to_string(),
                            true,
                        )),
                    ));
                }
            }
            SymbolServerEffect::StartUsageAtPosition {
                request,
                cancellation_token,
            } => {
                let key = SymbolServerRequestKey::from(request.as_ref());
                let engine = Arc::clone(engine);
                let events_tx = events_tx.clone();
                let spawn = std::thread::Builder::new()
                    .name(format!("sfm-symbol-usage-at-position-{}", key.request_id))
                    .spawn(move || {
                        let analyzed = engine
                            .analyze_usages_with_telemetry(request.as_ref(), &cancellation_token);
                        let (completion, telemetry) = match analyzed {
                            Ok(output) => (Ok(output.result), Some(output.telemetry)),
                            Err(error) => (
                                Err(SymbolServerEngineError::new(
                                    "usage-at-position-engine-failed",
                                    error.to_string(),
                                    false,
                                )),
                                None,
                            ),
                        };
                        let _ = events_tx.send(RuntimeEvent::UsageAtPositionFinished {
                            key,
                            completion: Box::new(completion),
                            telemetry,
                        });
                    });
                if let Err(error) = spawn {
                    pending.extend(state.finish_usage_at_position(
                        key,
                        Err(SymbolServerEngineError::new(
                            "usage-at-position-engine-thread-failed",
                            error.to_string(),
                            true,
                        )),
                    ));
                }
            }
            SymbolServerEffect::StartJavaInteractionMap {
                request,
                cancellation_token,
            } => {
                let key = SymbolServerRequestKey::from(request.as_ref());
                let engine = Arc::clone(engine);
                let events_tx = events_tx.clone();
                let spawn = std::thread::Builder::new()
                    .name(format!("sfm-java-interaction-map-{}", key.request_id))
                    .spawn(move || {
                        let analyzed = engine.analyze_interaction_map_with_telemetry(
                            request.as_ref(),
                            &cancellation_token,
                        );
                        let (completion, telemetry) = match analyzed {
                            Ok(output) => (Ok(output.result), Some(output.telemetry)),
                            Err(error) => (
                                Err(SymbolServerEngineError::new(
                                    "java-interaction-map-engine-failed",
                                    error.to_string(),
                                    false,
                                )),
                                None,
                            ),
                        };
                        let _ = events_tx.send(RuntimeEvent::JavaInteractionMapFinished {
                            key,
                            completion: Box::new(completion),
                            telemetry,
                        });
                    });
                if let Err(error) = spawn {
                    pending.extend(state.finish_java_interaction_map(
                        key,
                        Err(SymbolServerEngineError::new(
                            "java-interaction-map-engine-thread-failed",
                            error.to_string(),
                            true,
                        )),
                    ));
                }
            }
        }
    }
    Ok(())
}

fn apply_send_frame<W: Write>(
    frame: &SymbolServerFrame,
    writer: &mut W,
    state: &SymbolServerState,
    engine: &DefinitionAtPositionEngine,
) -> eyre::Result<()> {
    if let SymbolServerFrame::WorkspaceGeneration {
        update: SymbolServerWorkspaceGenerationOutput { workspace, .. },
        ..
    } = frame
    {
        engine.replace_workspace_generation(workspace.request_workspace.workspace_generation)?;
    }
    write_symbol_server_frame(writer, frame, state.negotiated_max_frame_bytes())?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::java_analysis::DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES;
    use crate::java_analysis::DefinitionAtPositionEngineLimits;
    use crate::java_analysis::DefinitionAtPositionRequest;
    use crate::java_analysis::DefinitionDocumentInput;
    use crate::java_analysis::DefinitionTextPositionInput;
    use crate::java_analysis::JavaAnalysisContextOutput;
    use crate::java_analysis::JavaClasspathMode;
    use crate::java_analysis::JavaSourceFile;
    use crate::java_analysis::JavaSourceRootAuthority;
    use crate::java_analysis::JavaSourceRootKind;
    use crate::java_analysis::JavaSourceRootOutput;
    use crate::java_analysis::JavaSourceSetOutput;
    use crate::java_analysis::SYMBOL_SERVER_PROTOCOL_SCHEMA;
    use crate::java_analysis::SymbolServerCapability;
    use crate::java_analysis::SymbolServerClientFrame;
    use crate::java_analysis::SymbolServerClientHello;
    use crate::java_analysis::SymbolServerHelloOutput;
    use crate::java_analysis::SymbolServerWorkspaceOutput;
    use crate::java_analysis::UsageAtPositionRequest;
    use crate::java_analysis::blake3_content_hash;
    use crate::java_analysis::encode_symbol_server_client_frame;
    use crate::java_analysis::read_symbol_server_frame;
    use std::io::Cursor;
    use std::sync::Condvar;
    use std::sync::Mutex;

    #[derive(Default)]
    struct FrameGate {
        complete_frames: Mutex<usize>,
        changed: Condvar,
    }

    struct GatedReader {
        before_wait: Cursor<Vec<u8>>,
        after_wait: Cursor<Vec<u8>>,
        gate: Arc<FrameGate>,
        required_responses: usize,
        wait_complete: bool,
    }

    impl Read for GatedReader {
        fn read(&mut self, buf: &mut [u8]) -> std::io::Result<usize> {
            let read = self.before_wait.read(buf)?;
            if read != 0 {
                return Ok(read);
            }
            if !self.wait_complete {
                let mut responses = self
                    .gate
                    .complete_frames
                    .lock()
                    .unwrap_or_else(std::sync::PoisonError::into_inner);
                while *responses < self.required_responses {
                    responses = self
                        .gate
                        .changed
                        .wait(responses)
                        .unwrap_or_else(std::sync::PoisonError::into_inner);
                }
                self.wait_complete = true;
            }
            self.after_wait.read(buf)
        }
    }

    struct GatedWriter {
        bytes: Vec<u8>,
        parsed_bytes: usize,
        gate: Arc<FrameGate>,
    }

    impl Write for GatedWriter {
        fn write(&mut self, buf: &[u8]) -> std::io::Result<usize> {
            self.bytes.extend_from_slice(buf);
            let mut completed = 0;
            loop {
                if self.bytes.len().saturating_sub(self.parsed_bytes) < 4 {
                    break;
                }
                let length = u32::from_le_bytes(
                    self.bytes[self.parsed_bytes..self.parsed_bytes + 4]
                        .try_into()
                        .expect("four-byte frame header"),
                ) as usize;
                let framed_length = 4_usize.saturating_add(length);
                if self.bytes.len().saturating_sub(self.parsed_bytes) < framed_length {
                    break;
                }
                self.parsed_bytes = self.parsed_bytes.saturating_add(framed_length);
                completed += 1;
            }
            if completed != 0 {
                let mut count = self
                    .gate
                    .complete_frames
                    .lock()
                    .unwrap_or_else(std::sync::PoisonError::into_inner);
                *count += completed;
                self.gate.changed.notify_all();
            }
            Ok(buf.len())
        }

        fn flush(&mut self) -> std::io::Result<()> {
            Ok(())
        }
    }

    fn fixture() -> (
        tempfile::TempDir,
        Arc<DefinitionAtPositionEngine>,
        SymbolServerWorkspaceOutput,
        DefinitionAtPositionRequest,
    ) {
        let directory = tempfile::tempdir().expect("symbol-server fixture");
        let root = directory.path().join("source");
        std::fs::create_dir_all(root.join("p")).expect("p package");
        std::fs::create_dir_all(root.join("q")).expect("q package");
        let declaration = "package p; public class A {}\n";
        let usage = "package q;\nimport p.A;\nclass Use { A value; }\n";
        std::fs::write(root.join("p/A.java"), declaration).expect("declaration source");
        std::fs::write(root.join("q/Use.java"), usage).expect("usage source");
        let canonical_root = dunce::canonicalize(&root).expect("canonical fixture root");
        let source_root = JavaSourceRootOutput {
            id: "custom-0".to_owned(),
            source_set: "custom".to_owned(),
            path: "source".to_owned(),
            kind: JavaSourceRootKind::Custom,
            exists: true,
        };
        let workspace = crate::java_analysis::JavaSourceWorkspace {
            context: JavaAnalysisContextOutput {
                branch: "1.19.2".to_owned(),
                minecraft_version: "1.19.2".to_owned(),
                java_release: "17".to_owned(),
                jdk: "java-17".to_owned(),
                source_roots: vec![source_root],
                source_sets: vec![JavaSourceSetOutput {
                    id: "custom".to_owned(),
                    visible_source_sets: vec!["custom".to_owned()],
                }],
                source_exclusions: Vec::new(),
                classpath_mode: JavaClasspathMode::Isolated,
                classpath_fingerprint: "blake3:isolated".to_owned(),
                parser_fingerprint: "fixture".to_owned(),
                index_fingerprint: "blake3:fixture".to_owned(),
            },
            root_authorities: vec![JavaSourceRootAuthority {
                root_id: "custom-0".to_owned(),
                source_set: "custom".to_owned(),
                canonical_absolute_path: canonical_root,
                report_root_path: "source".to_owned(),
            }],
            files: vec![
                JavaSourceFile {
                    absolute_path: root.join("p/A.java"),
                    root_id: "custom-0".to_owned(),
                    root_relative_path: "p/A.java".to_owned(),
                    report_path: "source/p/A.java".to_owned(),
                    source_set: "custom".to_owned(),
                    source_override: None,
                },
                JavaSourceFile {
                    absolute_path: root.join("q/Use.java"),
                    root_id: "custom-0".to_owned(),
                    root_relative_path: "q/Use.java".to_owned(),
                    report_path: "source/q/Use.java".to_owned(),
                    source_set: "custom".to_owned(),
                    source_override: None,
                },
            ],
            diagnostics: Vec::new(),
            classpath_entries: Vec::new(),
            jdk_sources: crate::java_analysis::JdkSourceDomainState::Disabled,
        };
        let served = SymbolServerWorkspaceOutput::from_workspace(&workspace, None, 0)
            .expect("served workspace");
        let request = DefinitionAtPositionRequest::new(
            1,
            1,
            served.request_workspace.clone(),
            DefinitionDocumentInput {
                address: "file://source/q/Use.java".to_owned(),
                root_id: "custom-0".to_owned(),
                root_relative_path: "q/Use.java".to_owned(),
                report_path: "source/q/Use.java".to_owned(),
                source_set: "custom".to_owned(),
                text: usage.to_owned(),
                content_hash: blake3_content_hash(usage),
                disk_content_hash: Some(blake3_content_hash(usage)),
            },
            DefinitionTextPositionInput::from_line_column(usage, 3, 13).expect("usage position"),
        );
        let engine = Arc::new(
            DefinitionAtPositionEngine::new(
                workspace,
                None,
                None,
                DefinitionAtPositionEngineLimits::default(),
            )
            .expect("definition engine"),
        );
        (directory, engine, served, request)
    }

    fn usage_request_from_definition(
        request: &DefinitionAtPositionRequest,
        request_id: u64,
    ) -> UsageAtPositionRequest {
        UsageAtPositionRequest::new(
            request_id,
            request.request_generation,
            request.workspace.clone(),
            request.document.clone(),
            request.position,
        )
    }

    fn interaction_map_request_from_definition(
        request: &DefinitionAtPositionRequest,
        request_id: u64,
    ) -> crate::java_analysis::JavaInteractionMapRequest {
        crate::java_analysis::JavaInteractionMapRequest::new(
            request_id,
            request.request_generation,
            request.workspace.clone(),
            request.document.clone(),
        )
    }

    #[test]
    fn runtime_handshake_exposes_roots_and_worker_result_matches_direct_engine() {
        let (_directory, engine, served, request) = fixture();
        let direct = engine
            .analyze(&request, &CancellationToken::new())
            .expect("direct engine result");
        let hello = SymbolServerClientFrame::hello(SymbolServerClientHello {
            protocol_schema: SYMBOL_SERVER_PROTOCOL_SCHEMA.to_owned(),
            client_name: "java-test".to_owned(),
            client_version: "1".to_owned(),
            capabilities: SymbolServerCapability::all().to_vec(),
            max_frame_bytes: DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES as u64,
        });
        let mut before_wait =
            encode_symbol_server_client_frame(&hello, DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES)
                .expect("hello frame");
        before_wait.extend(
            encode_symbol_server_client_frame(
                &SymbolServerClientFrame::definition(request.clone()),
                DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES,
            )
            .expect("definition frame"),
        );
        let after_wait = encode_symbol_server_client_frame(
            &SymbolServerClientFrame::shutdown("test complete"),
            DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES,
        )
        .expect("shutdown frame");
        let gate = Arc::new(FrameGate::default());
        let reader = GatedReader {
            before_wait: Cursor::new(before_wait),
            after_wait: Cursor::new(after_wait),
            gate: Arc::clone(&gate),
            required_responses: 2,
            wait_complete: false,
        };
        let mut writer = GatedWriter {
            bytes: Vec::new(),
            parsed_bytes: 0,
            gate,
        };
        let mut state = SymbolServerState::new(
            crate::java_analysis::SymbolServerIdentity {
                server_name: "sfm-propagate-changes".to_owned(),
                server_version: "test".to_owned(),
            },
            served.clone(),
            crate::java_analysis::SymbolServerLimits::default(),
            CancellationToken::new(),
        )
        .expect("server state");

        let summary = run_symbol_server_runtime(
            reader,
            &mut writer,
            &mut state,
            &engine,
            &CancellationToken::new(),
            DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES,
        )
        .expect("symbol-server runtime");

        let mut output = Cursor::new(writer.bytes);
        let hello = read_symbol_server_frame(&mut output, DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES)
            .expect("server hello")
            .expect("hello frame");
        let result = read_symbol_server_frame(&mut output, DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES)
            .expect("server result")
            .expect("result frame");
        let shutdown = read_symbol_server_frame(&mut output, DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES)
            .expect("server shutdown")
            .expect("shutdown frame");
        assert!(matches!(
            hello,
            SymbolServerFrame::Hello {
                hello: SymbolServerHelloOutput { workspace, .. },
                ..
            } if workspace == served
        ));
        let SymbolServerFrame::DefinitionResult { result, .. } = result else {
            panic!("expected definition result frame");
        };
        assert_eq!(result.as_ref(), &direct);
        assert_eq!(
            facet_json::to_string(result.as_ref()).expect("worker result JSON"),
            facet_json::to_string(&direct).expect("direct result JSON")
        );
        assert!(matches!(shutdown, SymbolServerFrame::Shutdown { .. }));
        assert!(
            read_symbol_server_frame(&mut output, DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES)
                .expect("clean output EOF")
                .is_none()
        );
        assert_eq!(summary.completed_requests, 1);
        assert_eq!(summary.failed_requests, 0);
        assert_eq!(state.phase(), SymbolServerPhase::Closed);
    }

    #[test]
    fn runtime_java_interaction_map_matches_direct_engine_and_stays_framed() {
        let (_directory, engine, served, definition_request) = fixture();
        let request = interaction_map_request_from_definition(&definition_request, 3);
        let direct = engine
            .analyze_interaction_map(&request, &CancellationToken::new())
            .expect("direct Java interaction-map result");
        let hello = SymbolServerClientFrame::hello(SymbolServerClientHello {
            protocol_schema: SYMBOL_SERVER_PROTOCOL_SCHEMA.to_owned(),
            client_name: "java-test".to_owned(),
            client_version: "1".to_owned(),
            capabilities: SymbolServerCapability::all().to_vec(),
            max_frame_bytes: DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES as u64,
        });
        let mut before_wait =
            encode_symbol_server_client_frame(&hello, DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES)
                .expect("hello frame");
        before_wait.extend(
            encode_symbol_server_client_frame(
                &SymbolServerClientFrame::java_interaction_map(request),
                DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES,
            )
            .expect("Java interaction-map request frame"),
        );
        let after_wait = encode_symbol_server_client_frame(
            &SymbolServerClientFrame::shutdown("test complete"),
            DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES,
        )
        .expect("shutdown frame");
        let gate = Arc::new(FrameGate::default());
        let reader = GatedReader {
            before_wait: Cursor::new(before_wait),
            after_wait: Cursor::new(after_wait),
            gate: Arc::clone(&gate),
            required_responses: 2,
            wait_complete: false,
        };
        let mut writer = GatedWriter {
            bytes: Vec::new(),
            parsed_bytes: 0,
            gate,
        };
        let mut state = SymbolServerState::new(
            crate::java_analysis::SymbolServerIdentity {
                server_name: "sfm-propagate-changes".to_owned(),
                server_version: "test".to_owned(),
            },
            served,
            crate::java_analysis::SymbolServerLimits::default(),
            CancellationToken::new(),
        )
        .expect("server state");

        let summary = run_symbol_server_runtime(
            reader,
            &mut writer,
            &mut state,
            &engine,
            &CancellationToken::new(),
            DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES,
        )
        .expect("Java interaction-map symbol-server runtime");

        let mut output = Cursor::new(writer.bytes);
        assert!(matches!(
            read_symbol_server_frame(&mut output, DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES)
                .expect("server hello")
                .expect("hello frame"),
            SymbolServerFrame::Hello { .. }
        ));
        let frame = read_symbol_server_frame(&mut output, DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES)
            .expect("map result")
            .expect("map result frame");
        let SymbolServerFrame::JavaInteractionMapResult { result, .. } = frame else {
            panic!("expected Java interaction-map result frame");
        };
        assert_eq!(result.as_ref(), &direct);
        assert_eq!(
            facet_json::to_string(result.as_ref()).expect("worker interaction-map JSON"),
            facet_json::to_string(&direct).expect("direct interaction-map JSON")
        );
        assert!(matches!(
            read_symbol_server_frame(&mut output, DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES)
                .expect("server shutdown")
                .expect("shutdown frame"),
            SymbolServerFrame::Shutdown { .. }
        ));
        assert!(
            read_symbol_server_frame(&mut output, DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES)
                .expect("clean output EOF")
                .is_none()
        );
        assert_eq!(summary.completed_requests, 1);
        assert_eq!(summary.failed_requests, 0);
        assert_eq!(state.phase(), SymbolServerPhase::Closed);
    }

    #[test]
    #[expect(
        clippy::too_many_lines,
        reason = "end-to-end mixed framing is clearest as one contiguous protocol fixture"
    )]
    fn runtime_mixes_definition_and_usage_with_direct_result_framing_parity() {
        let (_directory, engine, served, definition_request) = fixture();
        let usage_request = usage_request_from_definition(&definition_request, 2);
        let direct_definition = engine
            .analyze(&definition_request, &CancellationToken::new())
            .expect("direct definition result");
        let direct_usage = engine
            .analyze_usages(&usage_request, &CancellationToken::new())
            .expect("direct usage result");
        let hello = SymbolServerClientFrame::hello(SymbolServerClientHello {
            protocol_schema: SYMBOL_SERVER_PROTOCOL_SCHEMA.to_owned(),
            client_name: "java-test".to_owned(),
            client_version: "1".to_owned(),
            capabilities: SymbolServerCapability::all().to_vec(),
            max_frame_bytes: DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES as u64,
        });
        let mut before_wait =
            encode_symbol_server_client_frame(&hello, DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES)
                .expect("hello frame");
        before_wait.extend(
            encode_symbol_server_client_frame(
                &SymbolServerClientFrame::definition(definition_request),
                DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES,
            )
            .expect("definition frame"),
        );
        before_wait.extend(
            encode_symbol_server_client_frame(
                &SymbolServerClientFrame::usage_at_position(usage_request),
                DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES,
            )
            .expect("usage frame"),
        );
        let after_wait = encode_symbol_server_client_frame(
            &SymbolServerClientFrame::shutdown("test complete"),
            DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES,
        )
        .expect("shutdown frame");
        let gate = Arc::new(FrameGate::default());
        let reader = GatedReader {
            before_wait: Cursor::new(before_wait),
            after_wait: Cursor::new(after_wait),
            gate: Arc::clone(&gate),
            required_responses: 3,
            wait_complete: false,
        };
        let mut writer = GatedWriter {
            bytes: Vec::new(),
            parsed_bytes: 0,
            gate,
        };
        let mut state = SymbolServerState::new(
            crate::java_analysis::SymbolServerIdentity {
                server_name: "sfm-propagate-changes".to_owned(),
                server_version: "test".to_owned(),
            },
            served,
            crate::java_analysis::SymbolServerLimits::default(),
            CancellationToken::new(),
        )
        .expect("server state");

        let summary = run_symbol_server_runtime(
            reader,
            &mut writer,
            &mut state,
            &engine,
            &CancellationToken::new(),
            DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES,
        )
        .expect("mixed symbol-server runtime");

        let mut output = Cursor::new(writer.bytes);
        assert!(matches!(
            read_symbol_server_frame(&mut output, DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES)
                .expect("server hello")
                .expect("hello frame"),
            SymbolServerFrame::Hello { .. }
        ));
        let first = read_symbol_server_frame(&mut output, DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES)
            .expect("first result")
            .expect("first result frame");
        let second = read_symbol_server_frame(&mut output, DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES)
            .expect("second result")
            .expect("second result frame");
        let mut saw_definition = false;
        let mut saw_usage = false;
        for frame in [first, second] {
            match frame {
                SymbolServerFrame::DefinitionResult { result, .. } => {
                    assert_eq!(result.as_ref(), &direct_definition);
                    assert_eq!(
                        facet_json::to_string(result.as_ref()).expect("worker definition JSON"),
                        facet_json::to_string(&direct_definition).expect("direct definition JSON")
                    );
                    saw_definition = true;
                }
                SymbolServerFrame::UsageAtPositionResult { result, .. } => {
                    assert_eq!(result.as_ref(), &direct_usage);
                    assert_eq!(
                        facet_json::to_string(result.as_ref()).expect("worker usage JSON"),
                        facet_json::to_string(&direct_usage).expect("direct usage JSON")
                    );
                    saw_usage = true;
                }
                other => panic!("unexpected mixed result frame: {other:?}"),
            }
        }
        assert!(saw_definition && saw_usage);
        assert!(matches!(
            read_symbol_server_frame(&mut output, DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES)
                .expect("server shutdown")
                .expect("shutdown frame"),
            SymbolServerFrame::Shutdown { .. }
        ));
        assert!(
            read_symbol_server_frame(&mut output, DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES)
                .expect("clean output EOF")
                .is_none()
        );
        assert_eq!(summary.completed_requests, 2);
        assert_eq!(summary.failed_requests, 0);
        assert_eq!(state.phase(), SymbolServerPhase::Closed);
    }
}
