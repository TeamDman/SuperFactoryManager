use super::SyntaxHighlightRequest;
use super::SyntaxHighlightResult;
use facet::Facet;
use std::fmt;
use std::io::Read;
use std::io::Write;

pub const SYNTAX_SERVER_PROTOCOL_SCHEMA: &str = "sfm.syntax-server/1";
pub const SYNTAX_SERVER_HELLO_SCHEMA: &str = "sfm.syntax-server.hello/1";
pub const SYNTAX_SERVER_HIGHLIGHT_SCHEMA: &str = "sfm.syntax-server.highlight/1";
pub const SYNTAX_SERVER_CANCEL_SCHEMA: &str = "sfm.syntax-server.cancel/1";
pub const SYNTAX_SERVER_PING_SCHEMA: &str = "sfm.syntax-server.ping/1";
pub const SYNTAX_SERVER_SHUTDOWN_SCHEMA: &str = "sfm.syntax-server.shutdown/1";
pub const SYNTAX_SERVER_ERROR_SCHEMA: &str = "sfm.syntax-server.error/1";

pub const DEFAULT_SYNTAX_SERVER_MAX_FRAME_BYTES: usize = 16 * 1024 * 1024;
pub const DEFAULT_SYNTAX_SERVER_MAX_PENDING_REQUESTS: usize = 8;

#[derive(Facet, Clone, Copy, Debug, Eq, PartialEq, Ord, PartialOrd)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum SyntaxServerCapability {
    Highlight,
    Cancellation,
    Ping,
    Shutdown,
}

impl SyntaxServerCapability {
    #[must_use]
    pub const fn all() -> [Self; 4] {
        [
            Self::Highlight,
            Self::Cancellation,
            Self::Ping,
            Self::Shutdown,
        ]
    }
}

#[derive(Facet, Clone, Debug, Eq, PartialEq)]
pub struct SyntaxServerClientHello {
    pub protocol_schema: String,
    pub client_name: String,
    pub client_version: String,
    pub capabilities: Vec<SyntaxServerCapability>,
    pub max_frame_bytes: u64,
}

#[derive(Facet, Clone, Debug, Eq, PartialEq)]
pub struct SyntaxServerHelloOutput {
    pub protocol_schema: String,
    pub server_name: String,
    pub server_version: String,
    pub capabilities: Vec<SyntaxServerCapability>,
    pub max_frame_bytes: u64,
    pub max_pending_requests: u64,
    pub supported_languages: Vec<String>,
    pub request_schema: String,
    pub result_schema: String,
}

#[derive(Facet, Clone, Copy, Debug, Eq, PartialEq, Ord, PartialOrd)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum SyntaxServerCancellationStatus {
    RecordedBeforeRequest,
    CancellationRequested,
    AlreadyTerminal,
    RejectedCapacity,
    StaleGeneration,
}

#[derive(Facet, Clone, Debug, Eq, PartialEq)]
pub struct SyntaxServerCancellationOutput {
    pub request_id: u64,
    pub request_generation: u64,
    pub origin_id: String,
    pub origin_generation: u64,
    pub status: SyntaxServerCancellationStatus,
}

#[derive(Facet, Clone, Copy, Debug, Eq, PartialEq, Ord, PartialOrd)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum SyntaxServerErrorDisposition {
    Request,
    Fatal,
}

#[derive(Facet, Clone, Debug, Eq, PartialEq)]
pub struct SyntaxServerErrorOutput {
    pub code: String,
    pub message: String,
    pub disposition: SyntaxServerErrorDisposition,
    pub request_id: Option<u64>,
    pub request_generation: Option<u64>,
}

#[derive(Facet, Clone, Debug, Eq, PartialEq)]
#[facet(tag = "kind", rename_all = "kebab-case")]
#[repr(C)]
pub enum SyntaxServerClientFrame {
    Hello {
        schema: String,
        hello: SyntaxServerClientHello,
    },
    Highlight {
        schema: String,
        request: Box<SyntaxHighlightRequest>,
    },
    Cancel {
        schema: String,
        request_id: u64,
        request_generation: u64,
        origin_id: String,
        origin_generation: u64,
        reason: String,
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

impl SyntaxServerClientFrame {
    #[must_use]
    pub fn hello(hello: SyntaxServerClientHello) -> Self {
        Self::Hello {
            schema: SYNTAX_SERVER_HELLO_SCHEMA.to_owned(),
            hello,
        }
    }

    #[must_use]
    pub fn highlight(request: SyntaxHighlightRequest) -> Self {
        Self::Highlight {
            schema: SYNTAX_SERVER_HIGHLIGHT_SCHEMA.to_owned(),
            request: Box::new(request),
        }
    }

    #[must_use]
    pub fn cancel(
        request_id: u64,
        request_generation: u64,
        origin_id: impl Into<String>,
        origin_generation: u64,
        reason: impl Into<String>,
    ) -> Self {
        Self::Cancel {
            schema: SYNTAX_SERVER_CANCEL_SCHEMA.to_owned(),
            request_id,
            request_generation,
            origin_id: origin_id.into(),
            origin_generation,
            reason: reason.into(),
        }
    }

    #[must_use]
    pub fn ping(nonce: u64) -> Self {
        Self::Ping {
            schema: SYNTAX_SERVER_PING_SCHEMA.to_owned(),
            nonce,
        }
    }

    #[must_use]
    pub fn shutdown(reason: impl Into<String>) -> Self {
        Self::Shutdown {
            schema: SYNTAX_SERVER_SHUTDOWN_SCHEMA.to_owned(),
            reason: reason.into(),
        }
    }

    #[must_use]
    pub fn schema(&self) -> &str {
        match self {
            Self::Hello { schema, .. }
            | Self::Highlight { schema, .. }
            | Self::Cancel { schema, .. }
            | Self::Ping { schema, .. }
            | Self::Shutdown { schema, .. } => schema,
        }
    }

    #[must_use]
    pub const fn expected_schema(&self) -> &'static str {
        match self {
            Self::Hello { .. } => SYNTAX_SERVER_HELLO_SCHEMA,
            Self::Highlight { .. } => SYNTAX_SERVER_HIGHLIGHT_SCHEMA,
            Self::Cancel { .. } => SYNTAX_SERVER_CANCEL_SCHEMA,
            Self::Ping { .. } => SYNTAX_SERVER_PING_SCHEMA,
            Self::Shutdown { .. } => SYNTAX_SERVER_SHUTDOWN_SCHEMA,
        }
    }

    /// Validate the independently versioned schema for this frame kind.
    ///
    /// # Errors
    ///
    /// Returns an error when the frame schema does not match its kind.
    pub fn validate_schema(&self) -> Result<(), SyntaxServerProtocolError> {
        validate_schema(self.schema(), self.expected_schema())
    }
}

#[derive(Facet, Clone, Debug, Eq, PartialEq)]
#[facet(tag = "kind", rename_all = "kebab-case")]
#[repr(C)]
pub enum SyntaxServerFrame {
    Hello {
        schema: String,
        hello: SyntaxServerHelloOutput,
    },
    HighlightResult {
        schema: String,
        result: Box<SyntaxHighlightResult>,
    },
    Cancelled {
        schema: String,
        cancellation: SyntaxServerCancellationOutput,
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
        error: SyntaxServerErrorOutput,
    },
}

impl SyntaxServerFrame {
    #[must_use]
    pub fn schema(&self) -> &str {
        match self {
            Self::Hello { schema, .. }
            | Self::HighlightResult { schema, .. }
            | Self::Cancelled { schema, .. }
            | Self::Pong { schema, .. }
            | Self::Shutdown { schema }
            | Self::Error { schema, .. } => schema,
        }
    }

    #[must_use]
    pub const fn expected_schema(&self) -> &'static str {
        match self {
            Self::Hello { .. } => SYNTAX_SERVER_HELLO_SCHEMA,
            Self::HighlightResult { .. } => SYNTAX_SERVER_HIGHLIGHT_SCHEMA,
            Self::Cancelled { .. } => SYNTAX_SERVER_CANCEL_SCHEMA,
            Self::Pong { .. } => SYNTAX_SERVER_PING_SCHEMA,
            Self::Shutdown { .. } => SYNTAX_SERVER_SHUTDOWN_SCHEMA,
            Self::Error { .. } => SYNTAX_SERVER_ERROR_SCHEMA,
        }
    }

    /// Validate the independently versioned schema for this frame kind.
    ///
    /// # Errors
    ///
    /// Returns an error when the frame schema does not match its kind.
    pub fn validate_schema(&self) -> Result<(), SyntaxServerProtocolError> {
        validate_schema(self.schema(), self.expected_schema())
    }
}

fn validate_schema(actual: &str, expected: &'static str) -> Result<(), SyntaxServerProtocolError> {
    if actual == expected {
        Ok(())
    } else {
        Err(SyntaxServerProtocolError::SchemaMismatch {
            expected,
            actual: actual.to_owned(),
        })
    }
}

#[derive(Debug)]
pub enum SyntaxServerProtocolError {
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

impl fmt::Display for SyntaxServerProtocolError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::Io(error) => write!(f, "syntax-server frame I/O failed: {error}"),
            Self::TruncatedHeader { received } => write!(
                f,
                "syntax-server frame header ended after {received} of 4 bytes"
            ),
            Self::TruncatedPayload { expected, received } => write!(
                f,
                "syntax-server frame payload ended after {received} of {expected} bytes"
            ),
            Self::EmptyFrame => f.write_str("syntax-server frame payload is empty"),
            Self::FrameTooLarge { length, maximum } => write!(
                f,
                "syntax-server frame length {length} exceeds maximum {maximum}"
            ),
            Self::FrameLengthOverflow { length } => write!(
                f,
                "syntax-server frame length {length} cannot be represented by u32"
            ),
            Self::Encode(message) => write!(f, "could not encode syntax-server JSON: {message}"),
            Self::Decode(message) => write!(f, "could not decode syntax-server JSON: {message}"),
            Self::SchemaMismatch { expected, actual } => write!(
                f,
                "syntax-server frame schema `{actual}` does not match `{expected}`"
            ),
        }
    }
}

impl std::error::Error for SyntaxServerProtocolError {
    fn source(&self) -> Option<&(dyn std::error::Error + 'static)> {
        match self {
            Self::Io(error) => Some(error),
            _ => None,
        }
    }
}

impl From<std::io::Error> for SyntaxServerProtocolError {
    fn from(error: std::io::Error) -> Self {
        Self::Io(error)
    }
}

/// Encode one client frame including its u32 little-endian length prefix.
///
/// # Errors
///
/// Returns an error when JSON encoding fails or the payload exceeds `maximum`.
pub fn encode_syntax_server_client_frame(
    frame: &SyntaxServerClientFrame,
    maximum: usize,
) -> Result<Vec<u8>, SyntaxServerProtocolError> {
    encode_frame(frame, maximum)
}

/// Encode one server frame including its u32 little-endian length prefix.
///
/// # Errors
///
/// Returns an error when JSON encoding fails or the payload exceeds `maximum`.
pub fn encode_syntax_server_frame(
    frame: &SyntaxServerFrame,
    maximum: usize,
) -> Result<Vec<u8>, SyntaxServerProtocolError> {
    encode_frame(frame, maximum)
}

/// Read and decode one client frame. Clean EOF before a header returns `None`.
///
/// # Errors
///
/// Returns an error for I/O failure, truncation, invalid length, or invalid JSON.
pub fn read_syntax_server_client_frame(
    reader: &mut impl Read,
    maximum: usize,
) -> Result<Option<SyntaxServerClientFrame>, SyntaxServerProtocolError> {
    read_frame(reader, maximum)
}

/// Read and decode one server frame. Clean EOF before a header returns `None`.
///
/// # Errors
///
/// Returns an error for I/O failure, truncation, invalid length, or invalid JSON.
pub fn read_syntax_server_frame(
    reader: &mut impl Read,
    maximum: usize,
) -> Result<Option<SyntaxServerFrame>, SyntaxServerProtocolError> {
    read_frame(reader, maximum)
}

/// Write and flush one client frame.
///
/// # Errors
///
/// Returns an error for encoding, bounds, or I/O failure.
pub fn write_syntax_server_client_frame(
    writer: &mut impl Write,
    frame: &SyntaxServerClientFrame,
    maximum: usize,
) -> Result<(), SyntaxServerProtocolError> {
    write_frame(writer, frame, maximum)
}

/// Write and flush one server frame.
///
/// # Errors
///
/// Returns an error for encoding, bounds, or I/O failure.
pub fn write_syntax_server_frame(
    writer: &mut impl Write,
    frame: &SyntaxServerFrame,
    maximum: usize,
) -> Result<(), SyntaxServerProtocolError> {
    write_frame(writer, frame, maximum)
}

fn encode_frame<T: Facet<'static>>(
    value: &T,
    maximum: usize,
) -> Result<Vec<u8>, SyntaxServerProtocolError> {
    let payload = facet_json::to_string(value)
        .map_err(|error| SyntaxServerProtocolError::Encode(error.to_string()))?
        .into_bytes();
    validate_frame_length(payload.len(), maximum)?;
    let length = u32::try_from(payload.len()).map_err(|_conversion_error| {
        SyntaxServerProtocolError::FrameLengthOverflow {
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
) -> Result<Option<T>, SyntaxServerProtocolError> {
    let mut header = [0_u8; 4];
    let header_bytes = read_until_full(reader, &mut header)?;
    if header_bytes == 0 {
        return Ok(None);
    }
    if header_bytes != header.len() {
        return Err(SyntaxServerProtocolError::TruncatedHeader {
            received: header_bytes,
        });
    }
    let length = u32::from_le_bytes(header) as usize;
    validate_frame_length(length, maximum)?;
    let mut payload = vec![0_u8; length];
    let payload_bytes = read_until_full(reader, &mut payload)?;
    if payload_bytes != length {
        return Err(SyntaxServerProtocolError::TruncatedPayload {
            expected: length,
            received: payload_bytes,
        });
    }
    facet_json::from_slice(&payload)
        .map(Some)
        .map_err(|error| SyntaxServerProtocolError::Decode(error.to_string()))
}

fn write_frame<T: Facet<'static>>(
    writer: &mut impl Write,
    value: &T,
    maximum: usize,
) -> Result<(), SyntaxServerProtocolError> {
    writer.write_all(&encode_frame(value, maximum)?)?;
    writer.flush()?;
    Ok(())
}

fn validate_frame_length(length: usize, maximum: usize) -> Result<(), SyntaxServerProtocolError> {
    if length == 0 {
        return Err(SyntaxServerProtocolError::EmptyFrame);
    }
    if length > maximum {
        return Err(SyntaxServerProtocolError::FrameTooLarge { length, maximum });
    }
    if u32::try_from(length).is_err() {
        return Err(SyntaxServerProtocolError::FrameLengthOverflow { length });
    }
    Ok(())
}

fn read_until_full(
    reader: &mut impl Read,
    buffer: &mut [u8],
) -> Result<usize, SyntaxServerProtocolError> {
    let mut received = 0;
    while received < buffer.len() {
        match reader.read(&mut buffer[received..]) {
            Ok(0) => break,
            Ok(count) => received += count,
            Err(error) if error.kind() == std::io::ErrorKind::Interrupted => {}
            Err(error) => return Err(SyntaxServerProtocolError::Io(error)),
        }
    }
    Ok(received)
}

#[cfg(test)]
mod tests {
    use super::super::SYNTAX_HIGHLIGHT_REQUEST_SCHEMA;
    use super::super::SYNTAX_HIGHLIGHT_RESULT_SCHEMA;
    use super::*;
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

    fn hello() -> SyntaxServerClientFrame {
        SyntaxServerClientFrame::hello(SyntaxServerClientHello {
            protocol_schema: SYNTAX_SERVER_PROTOCOL_SCHEMA.to_owned(),
            client_name: "Minecraft\nclient\0🦀".to_owned(),
            client_version: "test".to_owned(),
            capabilities: SyntaxServerCapability::all().to_vec(),
            max_frame_bytes: DEFAULT_SYNTAX_SERVER_MAX_FRAME_BYTES as u64,
        })
    }

    #[test]
    fn syntax_highlight_server_fragmented_unicode_frame_round_trips() {
        let expected = hello();
        let bytes =
            encode_syntax_server_client_frame(&expected, DEFAULT_SYNTAX_SERVER_MAX_FRAME_BYTES)
                .expect("encode frame");
        let mut reader = ChunkedReader {
            inner: Cursor::new(bytes),
            maximum_chunk: 1,
        };

        assert_eq!(
            read_syntax_server_client_frame(&mut reader, DEFAULT_SYNTAX_SERVER_MAX_FRAME_BYTES,)
                .expect("read frame"),
            Some(expected)
        );
        assert!(
            read_syntax_server_client_frame(&mut reader, DEFAULT_SYNTAX_SERVER_MAX_FRAME_BYTES,)
                .expect("clean EOF")
                .is_none()
        );
    }

    #[test]
    fn syntax_highlight_server_coalesced_frames_are_read_independently() {
        let first = hello();
        let second = SyntaxServerClientFrame::ping(42);
        let mut bytes =
            encode_syntax_server_client_frame(&first, DEFAULT_SYNTAX_SERVER_MAX_FRAME_BYTES)
                .expect("first frame");
        bytes.extend(
            encode_syntax_server_client_frame(&second, DEFAULT_SYNTAX_SERVER_MAX_FRAME_BYTES)
                .expect("second frame"),
        );
        let mut reader = Cursor::new(bytes);

        assert_eq!(
            read_syntax_server_client_frame(&mut reader, DEFAULT_SYNTAX_SERVER_MAX_FRAME_BYTES,)
                .expect("first read"),
            Some(first)
        );
        assert_eq!(
            read_syntax_server_client_frame(&mut reader, DEFAULT_SYNTAX_SERVER_MAX_FRAME_BYTES,)
                .expect("second read"),
            Some(second)
        );
    }

    #[test]
    fn syntax_highlight_server_rejects_oversize_and_distinguishes_clean_eof() {
        let mut oversize = Cursor::new(33_u32.to_le_bytes());
        assert!(matches!(
            read_syntax_server_client_frame(&mut oversize, 32),
            Err(SyntaxServerProtocolError::FrameTooLarge {
                length: 33,
                maximum: 32
            })
        ));
        assert!(
            read_syntax_server_client_frame(&mut Cursor::new(Vec::new()), 128)
                .expect("clean EOF")
                .is_none()
        );
        assert!(matches!(
            read_syntax_server_client_frame(&mut Cursor::new(vec![1, 2]), 128),
            Err(SyntaxServerProtocolError::TruncatedHeader { received: 2 })
        ));

        let mut malformed = Vec::from(1_u32.to_le_bytes());
        malformed.push(b'{');
        assert!(matches!(
            read_syntax_server_client_frame(&mut Cursor::new(malformed), 128),
            Err(SyntaxServerProtocolError::Decode(_))
        ));
    }

    #[test]
    fn syntax_highlight_server_schema_and_json_envelopes_are_explicit() {
        let mut wrong = SyntaxServerClientFrame::ping(1);
        if let SyntaxServerClientFrame::Ping { schema, .. } = &mut wrong {
            *schema = SYNTAX_SERVER_CANCEL_SCHEMA.to_owned();
        }
        assert!(matches!(
            wrong.validate_schema(),
            Err(SyntaxServerProtocolError::SchemaMismatch { .. })
        ));

        assert_eq!(
            facet_json::to_string(&SyntaxServerClientFrame::cancel(
                7,
                3,
                "editor:1",
                9,
                "cursor moved"
            ))
            .expect("cancel JSON"),
            r#"{"kind":"cancel","schema":"sfm.syntax-server.cancel/1","request_id":7,"request_generation":3,"origin_id":"editor:1","origin_generation":9,"reason":"cursor moved"}"#
        );
        let server_hello = SyntaxServerFrame::Hello {
            schema: SYNTAX_SERVER_HELLO_SCHEMA.to_owned(),
            hello: SyntaxServerHelloOutput {
                protocol_schema: SYNTAX_SERVER_PROTOCOL_SCHEMA.to_owned(),
                server_name: "sfm-propagate-changes".to_owned(),
                server_version: "test".to_owned(),
                capabilities: SyntaxServerCapability::all().to_vec(),
                max_frame_bytes: 16_777_216,
                max_pending_requests: 8,
                supported_languages: vec!["java".to_owned()],
                request_schema: SYNTAX_HIGHLIGHT_REQUEST_SCHEMA.to_owned(),
                result_schema: SYNTAX_HIGHLIGHT_RESULT_SCHEMA.to_owned(),
            },
        };
        assert!(
            facet_json::to_string(&server_hello)
                .expect("hello JSON")
                .contains("sfm.syntax-highlight.request/1")
        );
    }
}
