use super::DefinitionAtPositionRequest;
use super::DefinitionAtPositionResult;
use super::DefinitionWorkspaceIdentityInput;
use super::JavaSourceWorkspace;
use super::definition_workspace_fingerprint;
use facet::Facet;
use std::fmt;
use std::io::Read;
use std::io::Write;
use std::path::Path;

pub const SYMBOL_SERVER_PROTOCOL_SCHEMA: &str = "sfm.symbol-server/1";
pub const SYMBOL_SERVER_HELLO_SCHEMA: &str = "sfm.symbol-server.hello/1";
pub const SYMBOL_SERVER_DEFINITION_SCHEMA: &str = "sfm.symbol-server.definition/1";
pub const SYMBOL_SERVER_CANCEL_SCHEMA: &str = "sfm.symbol-server.cancel/1";
pub const SYMBOL_SERVER_WORKSPACE_GENERATION_SCHEMA: &str =
    "sfm.symbol-server.workspace-generation/1";
pub const SYMBOL_SERVER_PING_SCHEMA: &str = "sfm.symbol-server.ping/1";
pub const SYMBOL_SERVER_SHUTDOWN_SCHEMA: &str = "sfm.symbol-server.shutdown/1";
pub const SYMBOL_SERVER_ERROR_SCHEMA: &str = "sfm.symbol-server.error/1";

pub const DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES: usize = 16 * 1024 * 1024;

#[derive(Facet, Clone, Copy, Debug, Eq, PartialEq, Ord, PartialOrd)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum SymbolServerCapability {
    DefinitionAtPosition,
    Cancellation,
    WorkspaceGeneration,
    Ping,
    Shutdown,
}

impl SymbolServerCapability {
    #[must_use]
    pub const fn all() -> [Self; 5] {
        [
            Self::DefinitionAtPosition,
            Self::Cancellation,
            Self::WorkspaceGeneration,
            Self::Ping,
            Self::Shutdown,
        ]
    }
}

#[derive(Facet, Clone, Debug, Eq, PartialEq)]
pub struct SymbolServerClientHello {
    pub protocol_schema: String,
    pub client_name: String,
    pub client_version: String,
    pub capabilities: Vec<SymbolServerCapability>,
    pub max_frame_bytes: u64,
}

/// One authoritative filesystem-to-report-root mapping resolved by the
/// worker. It is intentionally disclosed only over this local handshake,
/// never through ordinary analysis telemetry.
#[derive(Facet, Clone, Debug, Eq, PartialEq)]
pub struct SymbolServerWorkspaceRootOutput {
    pub canonical_absolute_path: String,
    pub root_id: String,
    pub source_set: String,
    pub report_root_path: String,
}

/// One already-acquired dependency source root available for exact addressed
/// navigation. Concrete paths are disclosed only in the local worker hello.
#[derive(Facet, Clone, Debug, Eq, PartialEq)]
pub struct SymbolServerDependencySourceRootOutput {
    pub canonical_absolute_path: String,
    pub root_id: String,
    pub source_set: String,
    pub report_prefix: String,
}

/// Exact workspace value Java must copy into subsequent requests, plus the
/// ordered filesystem mapping needed to derive document identity without
/// guessing from display paths.
#[derive(Facet, Clone, Debug, Eq, PartialEq)]
pub struct SymbolServerWorkspaceOutput {
    pub request_workspace: DefinitionWorkspaceIdentityInput,
    pub roots: Vec<SymbolServerWorkspaceRootOutput>,
    #[facet(default)]
    pub dependency_source_roots: Vec<SymbolServerDependencySourceRootOutput>,
}

impl SymbolServerWorkspaceOutput {
    /// Validate the exact one-to-one mapping advertised to clients.
    ///
    /// # Errors
    ///
    /// Returns an error for invalid request identity, ordering disagreement,
    /// non-absolute filesystem paths, or blank mapping fields.
    pub fn validate(&self) -> eyre::Result<()> {
        self.request_workspace.validate()?;
        if self.request_workspace.source_roots.len() != self.roots.len() {
            eyre::bail!(
                "served workspace has {} source roots but {} root mappings",
                self.request_workspace.source_roots.len(),
                self.roots.len()
            );
        }
        for (root, mapping) in self.request_workspace.source_roots.iter().zip(&self.roots) {
            if mapping.root_id.trim().is_empty()
                || mapping.source_set.trim().is_empty()
                || mapping.report_root_path.trim().is_empty()
            {
                eyre::bail!("served workspace root mapping contains a blank identity field");
            }
            if root.id != mapping.root_id
                || root.source_set != mapping.source_set
                || root.path != mapping.report_root_path
            {
                eyre::bail!(
                    "served workspace root mapping `{}` does not match the ordered request root",
                    mapping.root_id
                );
            }
            if !Path::new(&mapping.canonical_absolute_path).is_absolute() {
                eyre::bail!(
                    "served workspace root `{}` path is not absolute",
                    mapping.root_id
                );
            }
        }
        let mut dependency_root_ids = std::collections::BTreeSet::new();
        for root in &self.dependency_source_roots {
            if root.root_id.trim().is_empty()
                || root.source_set.trim().is_empty()
                || root.report_prefix.trim().is_empty()
                || !root.report_prefix.starts_with("dependency/")
            {
                eyre::bail!("served dependency source root contains an invalid identity field");
            }
            if !dependency_root_ids.insert(root.root_id.as_str()) {
                eyre::bail!(
                    "served dependency source root id is duplicated: {}",
                    root.root_id
                );
            }
            if !Path::new(&root.canonical_absolute_path).is_absolute() {
                eyre::bail!(
                    "served dependency source root `{}` path is not absolute",
                    root.root_id
                );
            }
        }
        Ok(())
    }

    /// Project one resolved workspace into the authoritative worker handshake.
    ///
    /// # Errors
    ///
    /// Returns an error when internal root projections disagree or an absolute
    /// path cannot be represented as UTF-8 for the cross-language protocol.
    pub fn from_workspace(
        workspace: &JavaSourceWorkspace,
        dependency_index_identity: Option<String>,
        workspace_generation: u64,
    ) -> eyre::Result<Self> {
        Self::from_workspace_with_dependency_sources(
            workspace,
            dependency_index_identity,
            workspace_generation,
            &[],
        )
    }

    /// Project a workspace plus already-acquired dependency roots into one
    /// validated local handshake without adding dependencies to live parsing.
    ///
    /// # Errors
    ///
    /// Returns an error when workspace/root projections disagree, a path is
    /// non-absolute or non-UTF-8, or dependency-root identities are invalid.
    pub fn from_workspace_with_dependency_sources(
        workspace: &JavaSourceWorkspace,
        dependency_index_identity: Option<String>,
        workspace_generation: u64,
        dependency_source_roots: &[super::DefinitionDependencySourceRoot],
    ) -> eyre::Result<Self> {
        if workspace.context.source_roots.len() != workspace.root_authorities.len() {
            eyre::bail!(
                "workspace has {} source-root projections but {} filesystem authorities",
                workspace.context.source_roots.len(),
                workspace.root_authorities.len()
            );
        }
        let mut roots = Vec::with_capacity(workspace.context.source_roots.len());
        for (root, authority) in workspace
            .context
            .source_roots
            .iter()
            .zip(&workspace.root_authorities)
        {
            if root.id != authority.root_id
                || root.source_set != authority.source_set
                || root.path != authority.report_root_path
            {
                eyre::bail!(
                    "workspace source-root projection `{}` does not match its filesystem authority",
                    root.id
                );
            }
            let canonical_absolute_path = authority
                .canonical_absolute_path
                .to_str()
                .ok_or_else(|| {
                    eyre::eyre!(
                        "workspace source root `{}` has a non-UTF-8 canonical path",
                        root.id
                    )
                })?
                .to_owned();
            roots.push(SymbolServerWorkspaceRootOutput {
                canonical_absolute_path,
                root_id: root.id.clone(),
                source_set: root.source_set.clone(),
                report_root_path: root.path.clone(),
            });
        }
        let workspace_fingerprint = definition_workspace_fingerprint(
            &workspace.context,
            dependency_index_identity.as_deref(),
        )?;
        let output = Self {
            request_workspace: DefinitionWorkspaceIdentityInput {
                branch: workspace.context.branch.clone(),
                classpath_mode: workspace.context.classpath_mode,
                source_roots: workspace.context.source_roots.clone(),
                classpath_fingerprint: workspace.context.classpath_fingerprint.clone(),
                dependency_index_identity,
                workspace_fingerprint,
                workspace_generation,
            },
            roots,
            dependency_source_roots: dependency_source_roots
                .iter()
                .map(|root| {
                    Ok(SymbolServerDependencySourceRootOutput {
                        canonical_absolute_path: root
                            .canonical_absolute_path
                            .to_str()
                            .ok_or_else(|| {
                                eyre::eyre!(
                                    "dependency source root `{}` has a non-UTF-8 canonical path",
                                    root.root_id
                                )
                            })?
                            .to_owned(),
                        root_id: root.root_id.clone(),
                        source_set: root.source_set.clone(),
                        report_prefix: root.report_prefix.clone(),
                    })
                })
                .collect::<eyre::Result<Vec<_>>>()?,
        };
        output.validate()?;
        Ok(output)
    }
}

#[derive(Facet, Clone, Debug, Eq, PartialEq)]
pub struct SymbolServerHelloOutput {
    pub protocol_schema: String,
    pub server_name: String,
    pub server_version: String,
    pub capabilities: Vec<SymbolServerCapability>,
    pub max_frame_bytes: u64,
    pub max_pending_definitions: u64,
    pub workspace: SymbolServerWorkspaceOutput,
}

#[derive(Facet, Clone, Copy, Debug, Eq, PartialEq, Ord, PartialOrd)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum SymbolServerCancellationStatus {
    RecordedBeforeRequest,
    CancellationRequested,
    AlreadyTerminal,
    RejectedCapacity,
    StaleGeneration,
}

#[derive(Facet, Clone, Debug, Eq, PartialEq)]
pub struct SymbolServerCancellationOutput {
    pub request_id: u64,
    pub request_generation: u64,
    pub workspace_generation: u64,
    pub status: SymbolServerCancellationStatus,
}

#[derive(Facet, Clone, Debug, Eq, PartialEq)]
pub struct SymbolServerDefinitionCancelledOutput {
    pub request_id: u64,
    pub request_generation: u64,
    pub workspace_generation: u64,
    pub reason: String,
}

#[derive(Facet, Clone, Debug, Eq, PartialEq)]
pub struct SymbolServerDefinitionErrorOutput {
    pub request_id: u64,
    pub request_generation: u64,
    pub workspace_generation: u64,
    pub code: String,
    pub message: String,
    pub retryable: bool,
}

#[derive(Facet, Clone, Debug, Eq, PartialEq)]
pub struct SymbolServerWorkspaceGenerationOutput {
    pub workspace: SymbolServerWorkspaceOutput,
    pub cancelled_requests: u64,
}

#[derive(Facet, Clone, Copy, Debug, Eq, PartialEq, Ord, PartialOrd)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum SymbolServerErrorDisposition {
    Request,
    Fatal,
}

#[derive(Facet, Clone, Debug, Eq, PartialEq)]
pub struct SymbolServerErrorOutput {
    pub code: String,
    pub message: String,
    pub disposition: SymbolServerErrorDisposition,
    pub request_id: Option<u64>,
    pub request_generation: Option<u64>,
}

#[derive(Facet, Clone, Debug, Eq, PartialEq)]
#[facet(tag = "kind", rename_all = "kebab-case")]
#[repr(C)]
pub enum SymbolServerClientFrame {
    Hello {
        schema: String,
        hello: SymbolServerClientHello,
    },
    Definition {
        schema: String,
        request: Box<DefinitionAtPositionRequest>,
    },
    Cancel {
        schema: String,
        request_id: u64,
        request_generation: u64,
        workspace_generation: u64,
        reason: String,
    },
    WorkspaceGeneration {
        schema: String,
        workspace_generation: u64,
    },
    Ping {
        schema: String,
        nonce: u64,
    },
    Shutdown {
        schema: String,
        reason: String,
    },
}

impl SymbolServerClientFrame {
    #[must_use]
    pub fn hello(hello: SymbolServerClientHello) -> Self {
        Self::Hello {
            schema: SYMBOL_SERVER_HELLO_SCHEMA.to_owned(),
            hello,
        }
    }

    #[must_use]
    pub fn definition(request: DefinitionAtPositionRequest) -> Self {
        Self::Definition {
            schema: SYMBOL_SERVER_DEFINITION_SCHEMA.to_owned(),
            request: Box::new(request),
        }
    }

    #[must_use]
    pub fn cancel(
        request_id: u64,
        request_generation: u64,
        workspace_generation: u64,
        reason: impl Into<String>,
    ) -> Self {
        Self::Cancel {
            schema: SYMBOL_SERVER_CANCEL_SCHEMA.to_owned(),
            request_id,
            request_generation,
            workspace_generation,
            reason: reason.into(),
        }
    }

    #[must_use]
    pub fn workspace_generation(workspace_generation: u64) -> Self {
        Self::WorkspaceGeneration {
            schema: SYMBOL_SERVER_WORKSPACE_GENERATION_SCHEMA.to_owned(),
            workspace_generation,
        }
    }

    #[must_use]
    pub fn ping(nonce: u64) -> Self {
        Self::Ping {
            schema: SYMBOL_SERVER_PING_SCHEMA.to_owned(),
            nonce,
        }
    }

    #[must_use]
    pub fn shutdown(reason: impl Into<String>) -> Self {
        Self::Shutdown {
            schema: SYMBOL_SERVER_SHUTDOWN_SCHEMA.to_owned(),
            reason: reason.into(),
        }
    }

    #[must_use]
    pub fn schema(&self) -> &str {
        match self {
            Self::Hello { schema, .. }
            | Self::Definition { schema, .. }
            | Self::Cancel { schema, .. }
            | Self::WorkspaceGeneration { schema, .. }
            | Self::Ping { schema, .. }
            | Self::Shutdown { schema, .. } => schema,
        }
    }

    #[must_use]
    pub const fn expected_schema(&self) -> &'static str {
        match self {
            Self::Hello { .. } => SYMBOL_SERVER_HELLO_SCHEMA,
            Self::Definition { .. } => SYMBOL_SERVER_DEFINITION_SCHEMA,
            Self::Cancel { .. } => SYMBOL_SERVER_CANCEL_SCHEMA,
            Self::WorkspaceGeneration { .. } => SYMBOL_SERVER_WORKSPACE_GENERATION_SCHEMA,
            Self::Ping { .. } => SYMBOL_SERVER_PING_SCHEMA,
            Self::Shutdown { .. } => SYMBOL_SERVER_SHUTDOWN_SCHEMA,
        }
    }

    /// Validate the independently versioned schema for this frame kind.
    ///
    /// # Errors
    ///
    /// Returns an error when the frame schema does not match its kind.
    pub fn validate_schema(&self) -> Result<(), SymbolServerProtocolError> {
        if self.schema() == self.expected_schema() {
            Ok(())
        } else {
            Err(SymbolServerProtocolError::SchemaMismatch {
                expected: self.expected_schema(),
                actual: self.schema().to_owned(),
            })
        }
    }
}

#[derive(Facet, Clone, Debug, Eq, PartialEq)]
#[facet(tag = "kind", rename_all = "kebab-case")]
#[repr(C)]
pub enum SymbolServerFrame {
    Hello {
        schema: String,
        hello: SymbolServerHelloOutput,
    },
    DefinitionResult {
        schema: String,
        result: Box<DefinitionAtPositionResult>,
    },
    DefinitionCancelled {
        schema: String,
        cancellation: SymbolServerDefinitionCancelledOutput,
    },
    DefinitionFailed {
        schema: String,
        error: SymbolServerDefinitionErrorOutput,
    },
    Cancelled {
        schema: String,
        cancellation: SymbolServerCancellationOutput,
    },
    WorkspaceGeneration {
        schema: String,
        update: SymbolServerWorkspaceGenerationOutput,
    },
    Pong {
        schema: String,
        nonce: u64,
    },
    Shutdown {
        schema: String,
    },
    Error {
        schema: String,
        error: SymbolServerErrorOutput,
    },
}

impl SymbolServerFrame {
    #[must_use]
    pub fn schema(&self) -> &str {
        match self {
            Self::Hello { schema, .. }
            | Self::DefinitionResult { schema, .. }
            | Self::DefinitionCancelled { schema, .. }
            | Self::DefinitionFailed { schema, .. }
            | Self::Cancelled { schema, .. }
            | Self::WorkspaceGeneration { schema, .. }
            | Self::Pong { schema, .. }
            | Self::Shutdown { schema }
            | Self::Error { schema, .. } => schema,
        }
    }

    #[must_use]
    pub const fn expected_schema(&self) -> &'static str {
        match self {
            Self::Hello { .. } => SYMBOL_SERVER_HELLO_SCHEMA,
            Self::DefinitionResult { .. }
            | Self::DefinitionCancelled { .. }
            | Self::DefinitionFailed { .. } => SYMBOL_SERVER_DEFINITION_SCHEMA,
            Self::Cancelled { .. } => SYMBOL_SERVER_CANCEL_SCHEMA,
            Self::WorkspaceGeneration { .. } => SYMBOL_SERVER_WORKSPACE_GENERATION_SCHEMA,
            Self::Pong { .. } => SYMBOL_SERVER_PING_SCHEMA,
            Self::Shutdown { .. } => SYMBOL_SERVER_SHUTDOWN_SCHEMA,
            Self::Error { .. } => SYMBOL_SERVER_ERROR_SCHEMA,
        }
    }

    /// Validate the independently versioned schema for this frame kind.
    ///
    /// # Errors
    ///
    /// Returns an error when the frame schema does not match its kind.
    pub fn validate_schema(&self) -> Result<(), SymbolServerProtocolError> {
        if self.schema() == self.expected_schema() {
            Ok(())
        } else {
            Err(SymbolServerProtocolError::SchemaMismatch {
                expected: self.expected_schema(),
                actual: self.schema().to_owned(),
            })
        }
    }
}

#[derive(Debug)]
pub enum SymbolServerProtocolError {
    Io(std::io::Error),
    TruncatedHeader {
        received: usize,
    },
    TruncatedPayload {
        expected: usize,
        received: usize,
    },
    EmptyFrame,
    FrameTooLarge {
        length: usize,
        maximum: usize,
    },
    FrameLengthOverflow {
        length: usize,
    },
    Encode(String),
    Decode(String),
    SchemaMismatch {
        expected: &'static str,
        actual: String,
    },
}

impl fmt::Display for SymbolServerProtocolError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::Io(error) => write!(f, "symbol-server frame I/O failed: {error}"),
            Self::TruncatedHeader { received } => write!(
                f,
                "symbol-server frame header ended after {received} of 4 bytes"
            ),
            Self::TruncatedPayload { expected, received } => write!(
                f,
                "symbol-server frame payload ended after {received} of {expected} bytes"
            ),
            Self::EmptyFrame => f.write_str("symbol-server frame payload is empty"),
            Self::FrameTooLarge { length, maximum } => write!(
                f,
                "symbol-server frame length {length} exceeds maximum {maximum}"
            ),
            Self::FrameLengthOverflow { length } => write!(
                f,
                "symbol-server frame length {length} cannot be represented by u32"
            ),
            Self::Encode(message) => {
                write!(f, "could not encode symbol-server JSON: {message}")
            }
            Self::Decode(message) => {
                write!(f, "could not decode symbol-server JSON: {message}")
            }
            Self::SchemaMismatch { expected, actual } => write!(
                f,
                "symbol-server frame schema `{actual}` does not match `{expected}`"
            ),
        }
    }
}

impl std::error::Error for SymbolServerProtocolError {
    fn source(&self) -> Option<&(dyn std::error::Error + 'static)> {
        match self {
            Self::Io(error) => Some(error),
            _ => None,
        }
    }
}

impl From<std::io::Error> for SymbolServerProtocolError {
    fn from(error: std::io::Error) -> Self {
        Self::Io(error)
    }
}

/// Encode one client frame including its u32 little-endian length prefix.
///
/// # Errors
///
/// Returns an error when JSON encoding fails or the payload exceeds `maximum`.
pub fn encode_symbol_server_client_frame(
    frame: &SymbolServerClientFrame,
    maximum: usize,
) -> Result<Vec<u8>, SymbolServerProtocolError> {
    encode_frame(frame, maximum)
}

/// Encode one server frame including its u32 little-endian length prefix.
///
/// # Errors
///
/// Returns an error when JSON encoding fails or the payload exceeds `maximum`.
pub fn encode_symbol_server_frame(
    frame: &SymbolServerFrame,
    maximum: usize,
) -> Result<Vec<u8>, SymbolServerProtocolError> {
    encode_frame(frame, maximum)
}

/// Read and decode one client frame. Clean EOF before a header returns `None`.
///
/// # Errors
///
/// Returns an error for I/O failure, truncation, an invalid length, or invalid JSON.
pub fn read_symbol_server_client_frame(
    reader: &mut impl Read,
    maximum: usize,
) -> Result<Option<SymbolServerClientFrame>, SymbolServerProtocolError> {
    read_frame(reader, maximum)
}

/// Read and decode one server frame. Clean EOF before a header returns `None`.
///
/// # Errors
///
/// Returns an error for I/O failure, truncation, an invalid length, or invalid JSON.
pub fn read_symbol_server_frame(
    reader: &mut impl Read,
    maximum: usize,
) -> Result<Option<SymbolServerFrame>, SymbolServerProtocolError> {
    read_frame(reader, maximum)
}

/// Write and flush one client frame.
///
/// # Errors
///
/// Returns an error for encoding, bounds, or I/O failure.
pub fn write_symbol_server_client_frame(
    writer: &mut impl Write,
    frame: &SymbolServerClientFrame,
    maximum: usize,
) -> Result<(), SymbolServerProtocolError> {
    write_frame(writer, frame, maximum)
}

/// Write and flush one server frame.
///
/// # Errors
///
/// Returns an error for encoding, bounds, or I/O failure.
pub fn write_symbol_server_frame(
    writer: &mut impl Write,
    frame: &SymbolServerFrame,
    maximum: usize,
) -> Result<(), SymbolServerProtocolError> {
    write_frame(writer, frame, maximum)
}

fn encode_frame<T: Facet<'static>>(
    value: &T,
    maximum: usize,
) -> Result<Vec<u8>, SymbolServerProtocolError> {
    let payload = facet_json::to_string(value)
        .map_err(|error| SymbolServerProtocolError::Encode(error.to_string()))?
        .into_bytes();
    validate_frame_length(payload.len(), maximum)?;
    let length = u32::try_from(payload.len()).map_err(|_conversion_error| {
        SymbolServerProtocolError::FrameLengthOverflow {
            length: payload.len(),
        }
    })?;
    let mut framed = Vec::with_capacity(4 + payload.len());
    framed.extend_from_slice(&length.to_le_bytes());
    framed.extend_from_slice(&payload);
    Ok(framed)
}

fn read_frame<T: Facet<'static>>(
    reader: &mut impl Read,
    maximum: usize,
) -> Result<Option<T>, SymbolServerProtocolError> {
    let mut header = [0_u8; 4];
    let header_bytes = read_until_full(reader, &mut header)?;
    if header_bytes == 0 {
        return Ok(None);
    }
    if header_bytes != header.len() {
        return Err(SymbolServerProtocolError::TruncatedHeader {
            received: header_bytes,
        });
    }
    let length = u32::from_le_bytes(header) as usize;
    validate_frame_length(length, maximum)?;
    let mut payload = vec![0_u8; length];
    let payload_bytes = read_until_full(reader, &mut payload)?;
    if payload_bytes != length {
        return Err(SymbolServerProtocolError::TruncatedPayload {
            expected: length,
            received: payload_bytes,
        });
    }
    facet_json::from_slice(&payload)
        .map(Some)
        .map_err(|error| SymbolServerProtocolError::Decode(error.to_string()))
}

fn write_frame<T: Facet<'static>>(
    writer: &mut impl Write,
    value: &T,
    maximum: usize,
) -> Result<(), SymbolServerProtocolError> {
    writer.write_all(&encode_frame(value, maximum)?)?;
    writer.flush()?;
    Ok(())
}

fn validate_frame_length(length: usize, maximum: usize) -> Result<(), SymbolServerProtocolError> {
    if length == 0 {
        return Err(SymbolServerProtocolError::EmptyFrame);
    }
    if length > maximum {
        return Err(SymbolServerProtocolError::FrameTooLarge { length, maximum });
    }
    if u32::try_from(length).is_err() {
        return Err(SymbolServerProtocolError::FrameLengthOverflow { length });
    }
    Ok(())
}

fn read_until_full(
    reader: &mut impl Read,
    buffer: &mut [u8],
) -> Result<usize, SymbolServerProtocolError> {
    let mut received = 0;
    while received < buffer.len() {
        match reader.read(&mut buffer[received..]) {
            Ok(0) => break,
            Ok(count) => received += count,
            Err(error) if error.kind() == std::io::ErrorKind::Interrupted => {}
            Err(error) => return Err(SymbolServerProtocolError::Io(error)),
        }
    }
    Ok(received)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::java_analysis::DefinitionDocumentInput;
    use crate::java_analysis::DefinitionTextPositionInput;
    use crate::java_analysis::JavaAnalysisContextOutput;
    use crate::java_analysis::JavaClasspathMode;
    use crate::java_analysis::JavaSourceRootKind;
    use crate::java_analysis::JavaSourceRootOutput;
    use crate::java_analysis::blake3_content_hash;
    use std::io::Cursor;

    struct ChunkedReader {
        inner: Cursor<Vec<u8>>,
        maximum_chunk: usize,
    }

    impl Read for ChunkedReader {
        fn read(&mut self, buf: &mut [u8]) -> std::io::Result<usize> {
            let count = buf.len().min(self.maximum_chunk);
            self.inner.read(&mut buf[..count])
        }
    }

    fn hello() -> SymbolServerClientFrame {
        SymbolServerClientFrame::hello(SymbolServerClientHello {
            protocol_schema: SYMBOL_SERVER_PROTOCOL_SCHEMA.to_owned(),
            client_name: "Minecraft\nclient\0🦀".to_owned(),
            client_version: "test".to_owned(),
            capabilities: SymbolServerCapability::all().to_vec(),
            max_frame_bytes: DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES as u64,
        })
    }

    fn definition_request() -> DefinitionAtPositionRequest {
        let text = "package example;\n// embedded NUL \0 and Unicode 🦀\nclass A {}\n";
        let root = JavaSourceRootOutput {
            id: "custom-0".to_owned(),
            source_set: "custom".to_owned(),
            path: "source".to_owned(),
            kind: JavaSourceRootKind::Custom,
            exists: true,
        };
        DefinitionAtPositionRequest::new(
            7,
            3,
            DefinitionWorkspaceIdentityInput {
                branch: "1.19.2".to_owned(),
                classpath_mode: JavaClasspathMode::Isolated,
                source_roots: vec![root],
                classpath_fingerprint: "blake3:workspace".to_owned(),
                dependency_index_identity: None,
                workspace_fingerprint: concat!(
                    "blake3:",
                    "0000000000000000000000000000000000000000000000000000000000000000"
                )
                .to_owned(),
                workspace_generation: 11,
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
            DefinitionTextPositionInput::from_line_column(text, 3, 7).expect("fixture position"),
        )
    }

    fn definition_result(request: &DefinitionAtPositionRequest) -> DefinitionAtPositionResult {
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
            "fixture terminal result",
        )
    }

    fn served_workspace(workspace_generation: u64) -> SymbolServerWorkspaceOutput {
        let mut request_workspace = definition_request().workspace;
        request_workspace.workspace_generation = workspace_generation;
        SymbolServerWorkspaceOutput {
            request_workspace,
            roots: vec![SymbolServerWorkspaceRootOutput {
                canonical_absolute_path: "C:/workspace/source".to_owned(),
                root_id: "custom-0".to_owned(),
                source_set: "custom".to_owned(),
                report_root_path: "source".to_owned(),
            }],
            dependency_source_roots: Vec::new(),
        }
    }

    #[test]
    fn fragmented_unicode_frame_round_trips() {
        let expected = hello();
        let bytes =
            encode_symbol_server_client_frame(&expected, DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES)
                .expect("encode frame");
        let mut reader = ChunkedReader {
            inner: Cursor::new(bytes),
            maximum_chunk: 1,
        };

        let actual =
            read_symbol_server_client_frame(&mut reader, DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES)
                .expect("read frame")
                .expect("one frame");

        assert_eq!(actual, expected);
        assert!(
            read_symbol_server_client_frame(&mut reader, DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES)
                .expect("clean EOF")
                .is_none()
        );
    }

    #[test]
    fn coalesced_frames_are_read_independently() {
        let first = hello();
        let second = SymbolServerClientFrame::ping(42);
        let mut bytes =
            encode_symbol_server_client_frame(&first, DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES)
                .expect("first frame");
        bytes.extend(
            encode_symbol_server_client_frame(&second, DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES)
                .expect("second frame"),
        );
        let mut reader = Cursor::new(bytes);

        assert_eq!(
            read_symbol_server_client_frame(&mut reader, DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES)
                .expect("first read"),
            Some(first)
        );
        assert_eq!(
            read_symbol_server_client_frame(&mut reader, DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES)
                .expect("second read"),
            Some(second)
        );
    }

    #[test]
    fn definition_request_and_terminal_result_round_trip_as_framed_json() {
        let request = definition_request();
        let client_frame = SymbolServerClientFrame::definition(request.clone());
        let client_bytes =
            encode_symbol_server_client_frame(&client_frame, DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES)
                .expect("definition request frame");
        assert_eq!(
            read_symbol_server_client_frame(
                &mut Cursor::new(client_bytes),
                DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES,
            )
            .expect("decode definition request"),
            Some(client_frame)
        );

        let server_frame = SymbolServerFrame::DefinitionResult {
            schema: SYMBOL_SERVER_DEFINITION_SCHEMA.to_owned(),
            result: Box::new(definition_result(&request)),
        };
        let server_bytes =
            encode_symbol_server_frame(&server_frame, DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES)
                .expect("definition result frame");
        assert_eq!(
            read_symbol_server_frame(
                &mut Cursor::new(server_bytes),
                DEFAULT_SYMBOL_SERVER_MAX_FRAME_BYTES,
            )
            .expect("decode definition result"),
            Some(server_frame)
        );
    }

    #[test]
    fn oversized_frame_is_rejected_from_header_before_payload_allocation() {
        let maximum = 32;
        let mut reader = Cursor::new(33_u32.to_le_bytes());
        assert!(matches!(
            read_symbol_server_client_frame(&mut reader, maximum),
            Err(SymbolServerProtocolError::FrameTooLarge {
                length: 33,
                maximum: 32
            })
        ));
    }

    #[test]
    fn clean_eof_and_truncation_are_distinct() {
        assert!(
            read_symbol_server_client_frame(&mut Cursor::new(Vec::new()), 128)
                .expect("clean EOF")
                .is_none()
        );
        assert!(matches!(
            read_symbol_server_client_frame(&mut Cursor::new(vec![1, 2]), 128),
            Err(SymbolServerProtocolError::TruncatedHeader { received: 2 })
        ));

        let mut truncated_payload = Vec::from(4_u32.to_le_bytes());
        truncated_payload.extend_from_slice(b"{}");
        assert!(matches!(
            read_symbol_server_client_frame(&mut Cursor::new(truncated_payload), 128),
            Err(SymbolServerProtocolError::TruncatedPayload {
                expected: 4,
                received: 2
            })
        ));
    }

    #[test]
    fn malformed_json_and_wrong_kind_schema_are_typed_errors() {
        let mut malformed = Vec::from(1_u32.to_le_bytes());
        malformed.push(b'{');
        assert!(matches!(
            read_symbol_server_client_frame(&mut Cursor::new(malformed), 128),
            Err(SymbolServerProtocolError::Decode(_))
        ));

        let mut frame = SymbolServerClientFrame::ping(1);
        if let SymbolServerClientFrame::Ping { schema, .. } = &mut frame {
            *schema = SYMBOL_SERVER_CANCEL_SCHEMA.to_owned();
        }
        assert!(matches!(
            frame.validate_schema(),
            Err(SymbolServerProtocolError::SchemaMismatch { .. })
        ));
    }

    #[test]
    fn simple_client_json_envelopes_are_stable_for_java_interop() {
        assert_eq!(
            facet_json::to_string(&SymbolServerClientFrame::ping(42)).expect("ping JSON"),
            r#"{"kind":"ping","schema":"sfm.symbol-server.ping/1","nonce":42}"#
        );
        assert_eq!(
            facet_json::to_string(&SymbolServerClientFrame::cancel(
                7,
                3,
                11,
                "cursor\nchanged"
            ))
            .expect("cancel JSON"),
            r#"{"kind":"cancel","schema":"sfm.symbol-server.cancel/1","request_id":7,"request_generation":3,"workspace_generation":11,"reason":"cursor\nchanged"}"#
        );
        assert_eq!(
            facet_json::to_string(&SymbolServerClientFrame::workspace_generation(12))
                .expect("workspace JSON"),
            r#"{"kind":"workspace-generation","schema":"sfm.symbol-server.workspace-generation/1","workspace_generation":12}"#
        );
        assert_eq!(
            facet_json::to_string(&SymbolServerClientFrame::shutdown("done"))
                .expect("shutdown JSON"),
            r#"{"kind":"shutdown","schema":"sfm.symbol-server.shutdown/1","reason":"done"}"#
        );
    }

    #[test]
    fn hello_json_envelopes_are_stable_for_java_interop() {
        assert_eq!(
            facet_json::to_string(&SymbolServerClientFrame::hello(SymbolServerClientHello {
                protocol_schema: SYMBOL_SERVER_PROTOCOL_SCHEMA.to_owned(),
                client_name: "minecraft".to_owned(),
                client_version: "1".to_owned(),
                capabilities: SymbolServerCapability::all().to_vec(),
                max_frame_bytes: 16_777_216,
            }))
            .expect("client hello JSON"),
            r#"{"kind":"hello","schema":"sfm.symbol-server.hello/1","hello":{"protocol_schema":"sfm.symbol-server/1","client_name":"minecraft","client_version":"1","capabilities":["definition-at-position","cancellation","workspace-generation","ping","shutdown"],"max_frame_bytes":16777216}}"#
        );
        assert_eq!(
            facet_json::to_string(&SymbolServerFrame::Hello {
                schema: SYMBOL_SERVER_HELLO_SCHEMA.to_owned(),
                hello: SymbolServerHelloOutput {
                    protocol_schema: SYMBOL_SERVER_PROTOCOL_SCHEMA.to_owned(),
                    server_name: "sfm-propagate-changes".to_owned(),
                    server_version: "1".to_owned(),
                    capabilities: SymbolServerCapability::all().to_vec(),
                    max_frame_bytes: 16_777_216,
                    max_pending_definitions: 8,
                    workspace: served_workspace(7),
                },
            })
            .expect("server hello JSON"),
            r#"{"kind":"hello","schema":"sfm.symbol-server.hello/1","hello":{"protocol_schema":"sfm.symbol-server/1","server_name":"sfm-propagate-changes","server_version":"1","capabilities":["definition-at-position","cancellation","workspace-generation","ping","shutdown"],"max_frame_bytes":16777216,"max_pending_definitions":8,"workspace":{"request_workspace":{"branch":"1.19.2","classpath_mode":"isolated","source_roots":[{"id":"custom-0","source_set":"custom","path":"source","kind":"custom","exists":true}],"classpath_fingerprint":"blake3:workspace","workspace_fingerprint":"blake3:0000000000000000000000000000000000000000000000000000000000000000","workspace_generation":7},"roots":[{"canonical_absolute_path":"C:/workspace/source","root_id":"custom-0","source_set":"custom","report_root_path":"source"}],"dependency_source_roots":[]}}}"#
        );
    }

    #[test]
    fn dependency_source_root_json_is_stable_for_java_interop() {
        let mut workspace = served_workspace(7);
        workspace
            .dependency_source_roots
            .push(SymbolServerDependencySourceRootOutput {
                canonical_absolute_path: "C:/workspace/dependencies/forge".to_owned(),
                root_id: "dependency-source-0".to_owned(),
                source_set: "dependency:forge".to_owned(),
                report_prefix: "dependency/forge/userdev/loader-pipeline".to_owned(),
            });
        workspace.validate().expect("dependency workspace");

        assert_eq!(
            facet_json::to_string(&workspace).expect("dependency workspace JSON"),
            r#"{"request_workspace":{"branch":"1.19.2","classpath_mode":"isolated","source_roots":[{"id":"custom-0","source_set":"custom","path":"source","kind":"custom","exists":true}],"classpath_fingerprint":"blake3:workspace","workspace_fingerprint":"blake3:0000000000000000000000000000000000000000000000000000000000000000","workspace_generation":7},"roots":[{"canonical_absolute_path":"C:/workspace/source","root_id":"custom-0","source_set":"custom","report_root_path":"source"}],"dependency_source_roots":[{"canonical_absolute_path":"C:/workspace/dependencies/forge","root_id":"dependency-source-0","source_set":"dependency:forge","report_prefix":"dependency/forge/userdev/loader-pipeline"}]}"#
        );
    }

    #[test]
    fn simple_server_json_envelopes_are_stable_for_java_interop() {
        let cases = [
            (
                SymbolServerFrame::DefinitionCancelled {
                    schema: SYMBOL_SERVER_DEFINITION_SCHEMA.to_owned(),
                    cancellation: SymbolServerDefinitionCancelledOutput {
                        request_id: 7,
                        request_generation: 3,
                        workspace_generation: 11,
                        reason: "cursor moved".to_owned(),
                    },
                },
                r#"{"kind":"definition-cancelled","schema":"sfm.symbol-server.definition/1","cancellation":{"request_id":7,"request_generation":3,"workspace_generation":11,"reason":"cursor moved"}}"#,
            ),
            (
                SymbolServerFrame::DefinitionFailed {
                    schema: SYMBOL_SERVER_DEFINITION_SCHEMA.to_owned(),
                    error: SymbolServerDefinitionErrorOutput {
                        request_id: 7,
                        request_generation: 3,
                        workspace_generation: 11,
                        code: "server-busy".to_owned(),
                        message: "pending limit reached".to_owned(),
                        retryable: true,
                    },
                },
                r#"{"kind":"definition-failed","schema":"sfm.symbol-server.definition/1","error":{"request_id":7,"request_generation":3,"workspace_generation":11,"code":"server-busy","message":"pending limit reached","retryable":true}}"#,
            ),
            (
                SymbolServerFrame::Cancelled {
                    schema: SYMBOL_SERVER_CANCEL_SCHEMA.to_owned(),
                    cancellation: SymbolServerCancellationOutput {
                        request_id: 7,
                        request_generation: 3,
                        workspace_generation: 11,
                        status: SymbolServerCancellationStatus::CancellationRequested,
                    },
                },
                r#"{"kind":"cancelled","schema":"sfm.symbol-server.cancel/1","cancellation":{"request_id":7,"request_generation":3,"workspace_generation":11,"status":"cancellation-requested"}}"#,
            ),
            (
                SymbolServerFrame::WorkspaceGeneration {
                    schema: SYMBOL_SERVER_WORKSPACE_GENERATION_SCHEMA.to_owned(),
                    update: SymbolServerWorkspaceGenerationOutput {
                        workspace: served_workspace(12),
                        cancelled_requests: 2,
                    },
                },
                r#"{"kind":"workspace-generation","schema":"sfm.symbol-server.workspace-generation/1","update":{"workspace":{"request_workspace":{"branch":"1.19.2","classpath_mode":"isolated","source_roots":[{"id":"custom-0","source_set":"custom","path":"source","kind":"custom","exists":true}],"classpath_fingerprint":"blake3:workspace","workspace_fingerprint":"blake3:0000000000000000000000000000000000000000000000000000000000000000","workspace_generation":12},"roots":[{"canonical_absolute_path":"C:/workspace/source","root_id":"custom-0","source_set":"custom","report_root_path":"source"}],"dependency_source_roots":[]},"cancelled_requests":2}}"#,
            ),
            (
                SymbolServerFrame::Pong {
                    schema: SYMBOL_SERVER_PING_SCHEMA.to_owned(),
                    nonce: 42,
                },
                r#"{"kind":"pong","schema":"sfm.symbol-server.ping/1","nonce":42}"#,
            ),
            (
                SymbolServerFrame::Shutdown {
                    schema: SYMBOL_SERVER_SHUTDOWN_SCHEMA.to_owned(),
                },
                r#"{"kind":"shutdown","schema":"sfm.symbol-server.shutdown/1"}"#,
            ),
            (
                SymbolServerFrame::Error {
                    schema: SYMBOL_SERVER_ERROR_SCHEMA.to_owned(),
                    error: SymbolServerErrorOutput {
                        code: "hello-required".to_owned(),
                        message: "hello must be first".to_owned(),
                        disposition: SymbolServerErrorDisposition::Fatal,
                        request_id: None,
                        request_generation: None,
                    },
                },
                r#"{"kind":"error","schema":"sfm.symbol-server.error/1","error":{"code":"hello-required","message":"hello must be first","disposition":"fatal","request_id":null,"request_generation":null}}"#,
            ),
        ];

        for (frame, expected) in cases {
            assert_eq!(
                facet_json::to_string(&frame).expect("server JSON"),
                expected
            );
        }
    }
}
