use super::DependencySymbolIndexQueryOutput;
use super::DiagnosticSeverity;
use super::JavaAnalysisContextOutput;
use super::JavaAnalysisDiagnosticOutput;
use super::JavaClasspathMode;
use super::JavaSourceRootOutput;
use super::JavaSymbolDefinitionOutput;
use super::JavaSymbolIdentityOutput;
use super::SymbolQueryCompleteness;
use facet::Facet;
use sha2::Digest as _;
use sha2::Sha256;
use std::collections::BTreeSet;

pub const DEFINITION_AT_POSITION_REQUEST_SCHEMA: &str = "sfm.definition-at-position-request/1";
pub const DEFINITION_AT_POSITION_RESULT_SCHEMA: &str = "sfm.definition-at-position-result/1";

/// Immutable workspace identity carried by an editor-location request.
///
/// Concrete root paths are resolved and authorized outside this DTO. The
/// request carries the ordered, portable root projection so a long-lived
/// worker can reject a request aimed at a different workspace generation.
#[derive(Facet, Clone, Debug, Eq, PartialEq)]
pub struct DefinitionWorkspaceIdentityInput {
    pub branch: String,
    pub classpath_mode: JavaClasspathMode,
    pub source_roots: Vec<JavaSourceRootOutput>,
    pub classpath_fingerprint: String,
    #[facet(default, skip_serializing_if = Option::is_none)]
    pub dependency_index_identity: Option<String>,
    pub workspace_generation: u64,
}

impl DefinitionWorkspaceIdentityInput {
    /// Validate the portable workspace identity without resolving filesystem
    /// authority or consulting mutable process state.
    ///
    /// # Errors
    ///
    /// Returns an error for blank identity fields or duplicate roots.
    pub fn validate(&self) -> eyre::Result<()> {
        require_nonblank(&self.branch, "workspace branch")?;
        require_nonblank(&self.classpath_fingerprint, "classpath fingerprint")?;
        let mut root_ids = BTreeSet::new();
        for root in &self.source_roots {
            require_nonblank(&root.id, "source-root id")?;
            require_nonblank(&root.source_set, "source-root source set")?;
            require_nonblank(&root.path, "source-root path")?;
            if !root_ids.insert(root.id.as_str()) {
                eyre::bail!(
                    "definition workspace contains duplicate source-root id `{}`",
                    root.id
                );
            }
        }
        if self
            .dependency_index_identity
            .as_ref()
            .is_some_and(|identity| identity.trim().is_empty())
        {
            eyre::bail!("dependency-index identity must be omitted rather than blank");
        }
        Ok(())
    }
}

/// Exact current editor document supplied to definition analysis.
#[derive(Facet, Clone, Debug, Eq, PartialEq)]
pub struct DefinitionDocumentInput {
    /// Provider-neutral canonical document address used by the caller.
    pub address: String,
    /// Resolver-issued root identity containing `path`.
    pub root_id: String,
    /// Canonical path relative to `root_id`, using `/` separators.
    pub root_relative_path: String,
    /// Stable workspace report path used by public source spans.
    pub report_path: String,
    pub source_set: String,
    /// Exact current editor snapshot. It may differ from disk and is never
    /// written by definition analysis.
    pub text: String,
    /// `blake3:<hex>` or `sha256:<hex>` over `text` as UTF-8.
    pub content_hash: String,
    /// Hash of the disk snapshot when known, allowing an overlay to state its
    /// relationship to the immutable workspace snapshot explicitly.
    #[facet(default, skip_serializing_if = Option::is_none)]
    pub disk_content_hash: Option<String>,
}

impl DefinitionDocumentInput {
    /// Validate canonical document identity and prove the supplied current
    /// content hash before any parser consumes the text.
    ///
    /// # Errors
    ///
    /// Returns an error for malformed identity, traversal, unsupported hashes,
    /// or content/hash disagreement.
    pub fn validate(&self, workspace: &DefinitionWorkspaceIdentityInput) -> eyre::Result<()> {
        require_nonblank(&self.address, "document address")?;
        require_nonblank(&self.root_id, "document root id")?;
        require_nonblank(&self.root_relative_path, "document root-relative path")?;
        require_nonblank(&self.report_path, "document report path")?;
        require_nonblank(&self.source_set, "document source set")?;
        if self.root_relative_path.contains('\\') || self.report_path.contains('\\') {
            eyre::bail!("definition document paths must use canonical `/` separators");
        }
        for (label, path) in [
            ("root-relative", self.root_relative_path.as_str()),
            ("report", self.report_path.as_str()),
        ] {
            if path
                .split('/')
                .any(|segment| segment.is_empty() || matches!(segment, "." | ".."))
            {
                eyre::bail!("definition document {label} path contains a non-canonical segment");
            }
        }
        let root = workspace
            .source_roots
            .iter()
            .find(|root| root.id == self.root_id)
            .ok_or_else(|| {
                eyre::eyre!(
                    "definition document references unknown source-root id `{}`",
                    self.root_id
                )
            })?;
        if !root.exists {
            eyre::bail!(
                "definition document source root `{}` is unavailable",
                root.id
            );
        }
        if root.source_set != self.source_set {
            eyre::bail!(
                "definition document source set `{}` disagrees with root `{}` source set `{}`",
                self.source_set,
                root.id,
                root.source_set
            );
        }
        validate_content_hash(&self.text, &self.content_hash)?;
        if let Some(disk_hash) = &self.disk_content_hash {
            validate_hash_shape(disk_hash)?;
        }
        Ok(())
    }
}

/// One-based Unicode-scalar source position plus its exact UTF-8 byte offset.
///
/// Line endings may be LF or CRLF. The column counts Unicode scalar values,
/// matching existing Java-analysis span reports rather than UTF-16 code units
/// or terminal cells.
#[derive(Facet, Clone, Copy, Debug, Eq, PartialEq)]
pub struct DefinitionTextPositionInput {
    pub line: u64,
    pub column: u64,
    pub byte_offset: u64,
}

impl DefinitionTextPositionInput {
    /// Derive the UTF-8 byte offset for a one-based Unicode-scalar position.
    ///
    /// # Errors
    ///
    /// Returns an error when the line or column is outside the source.
    pub fn from_line_column(text: &str, line: u64, column: u64) -> eyre::Result<Self> {
        let byte_offset = byte_offset_for_line_column(text, line, column)?;
        Ok(Self {
            line,
            column,
            byte_offset: u64::try_from(byte_offset)
                .map_err(|_| eyre::eyre!("definition byte offset does not fit u64"))?,
        })
    }

    /// Prove that all three coordinate projections identify one insertion
    /// point in the supplied text.
    ///
    /// # Errors
    ///
    /// Returns an error for invalid positions, non-character byte boundaries,
    /// or disagreement between line/column and byte offset.
    pub fn validate(&self, text: &str) -> eyre::Result<()> {
        let expected = byte_offset_for_line_column(text, self.line, self.column)?;
        let supplied = usize::try_from(self.byte_offset)
            .map_err(|_| eyre::eyre!("definition byte offset does not fit usize"))?;
        if supplied > text.len() || !text.is_char_boundary(supplied) {
            eyre::bail!("definition byte offset is not a UTF-8 character boundary");
        }
        if supplied != expected {
            eyre::bail!(
                "definition line/column resolves to byte {expected}, not supplied byte {supplied}"
            );
        }
        Ok(())
    }
}

/// Versioned provider-neutral request shared by direct CLI, worker, and game.
#[derive(Facet, Clone, Debug, Eq, PartialEq)]
pub struct DefinitionAtPositionRequest {
    pub schema: String,
    pub request_id: u64,
    pub request_generation: u64,
    pub workspace: DefinitionWorkspaceIdentityInput,
    pub document: DefinitionDocumentInput,
    pub position: DefinitionTextPositionInput,
}

impl DefinitionAtPositionRequest {
    #[must_use]
    pub fn new(
        request_id: u64,
        request_generation: u64,
        workspace: DefinitionWorkspaceIdentityInput,
        document: DefinitionDocumentInput,
        position: DefinitionTextPositionInput,
    ) -> Self {
        Self {
            schema: DEFINITION_AT_POSITION_REQUEST_SCHEMA.to_owned(),
            request_id,
            request_generation,
            workspace,
            document,
            position,
        }
    }

    /// Validate every self-contained request invariant without consulting the
    /// filesystem or a worker session.
    ///
    /// # Errors
    ///
    /// Returns an error for incompatible schema, workspace/document mismatch,
    /// hash disagreement, or invalid position.
    pub fn validate(&self) -> eyre::Result<()> {
        if self.schema != DEFINITION_AT_POSITION_REQUEST_SCHEMA {
            eyre::bail!("definition-at-position request schema is unsupported");
        }
        self.workspace.validate()?;
        self.document.validate(&self.workspace)?;
        self.position.validate(&self.document.text)
    }
}

#[derive(Facet, Clone, Copy, Debug, Eq, PartialEq, Ord, PartialOrd)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum DefinitionAtPositionOutcome {
    Success,
    NoSymbol,
    NoDefinition,
    Ambiguous,
    InvalidRequest,
    Unavailable,
}

impl DefinitionAtPositionOutcome {
    #[must_use]
    pub const fn exit_code(self) -> u8 {
        match self {
            Self::Success => 0,
            Self::NoSymbol | Self::NoDefinition => 2,
            Self::Ambiguous => 3,
            Self::InvalidRequest | Self::Unavailable => 4,
        }
    }
}

#[derive(Facet, Clone, Copy, Debug, Eq, PartialEq, Ord, PartialOrd)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum DefinitionRecoveryActionKind {
    Retry,
    RefreshDependencyIndex,
    RestartWorker,
}

#[derive(Facet, Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
pub struct DefinitionRecoveryActionOutput {
    pub kind: DefinitionRecoveryActionKind,
    pub label: String,
    #[facet(default, skip_serializing_if = Option::is_none)]
    pub command: Option<String>,
}

#[derive(Facet, Clone, Debug, Eq, PartialEq)]
pub struct DefinitionDocumentIdentityOutput {
    pub address: String,
    pub root_id: String,
    pub root_relative_path: String,
    pub report_path: String,
    pub source_set: String,
    pub content_hash: String,
    #[facet(default, skip_serializing_if = Option::is_none)]
    pub disk_content_hash: Option<String>,
}

impl From<&DefinitionDocumentInput> for DefinitionDocumentIdentityOutput {
    fn from(document: &DefinitionDocumentInput) -> Self {
        Self {
            address: document.address.clone(),
            root_id: document.root_id.clone(),
            root_relative_path: document.root_relative_path.clone(),
            report_path: document.report_path.clone(),
            source_set: document.source_set.clone(),
            content_hash: document.content_hash.clone(),
            disk_content_hash: document.disk_content_hash.clone(),
        }
    }
}

/// Typed definition-at-position result. Transport telemetry is deliberately
/// absent so direct and worker modes can produce canonical-byte-equivalent
/// values.
#[derive(Facet, Clone, Debug, Eq, PartialEq)]
pub struct DefinitionAtPositionResult {
    pub schema: String,
    pub request_id: u64,
    pub request_generation: u64,
    pub workspace_generation: u64,
    pub outcome: DefinitionAtPositionOutcome,
    pub context: JavaAnalysisContextOutput,
    pub document: DefinitionDocumentIdentityOutput,
    pub position: DefinitionTextPositionInput,
    pub symbols: Vec<JavaSymbolIdentityOutput>,
    pub definitions: Vec<JavaSymbolDefinitionOutput>,
    pub completeness: SymbolQueryCompleteness,
    pub diagnostics: Vec<JavaAnalysisDiagnosticOutput>,
    pub recovery_actions: Vec<DefinitionRecoveryActionOutput>,
    #[facet(default, skip_serializing_if = Option::is_none)]
    pub dependency_index: Option<DependencySymbolIndexQueryOutput>,
}

impl DefinitionAtPositionResult {
    #[must_use]
    pub fn status(&self) -> u8 {
        if self.completeness == SymbolQueryCompleteness::Incomplete {
            5
        } else {
            self.outcome.exit_code()
        }
    }

    /// Add one stable diagnostic while retaining deterministic result order.
    #[must_use]
    pub fn with_diagnostic(mut self, diagnostic: JavaAnalysisDiagnosticOutput) -> Self {
        self.diagnostics.push(diagnostic);
        self.diagnostics.sort();
        self.diagnostics.dedup();
        self
    }

    #[must_use]
    pub fn invalid_request(
        request: &DefinitionAtPositionRequest,
        context: JavaAnalysisContextOutput,
        message: impl Into<String>,
    ) -> Self {
        Self {
            schema: DEFINITION_AT_POSITION_RESULT_SCHEMA.to_owned(),
            request_id: request.request_id,
            request_generation: request.request_generation,
            workspace_generation: request.workspace.workspace_generation,
            outcome: DefinitionAtPositionOutcome::InvalidRequest,
            context,
            document: (&request.document).into(),
            position: request.position,
            symbols: Vec::new(),
            definitions: Vec::new(),
            completeness: SymbolQueryCompleteness::Incomplete,
            diagnostics: vec![JavaAnalysisDiagnosticOutput {
                code: "java.definition-position-invalid-request".to_owned(),
                severity: DiagnosticSeverity::Error,
                message: message.into(),
                span: None,
            }],
            recovery_actions: Vec::new(),
            dependency_index: None,
        }
    }
}

#[must_use]
pub fn blake3_content_hash(text: &str) -> String {
    format!("blake3:{}", blake3::hash(text.as_bytes()).to_hex())
}

#[must_use]
pub fn sha256_content_hash(text: &str) -> String {
    format!("sha256:{:x}", Sha256::digest(text.as_bytes()))
}

fn validate_content_hash(text: &str, expected: &str) -> eyre::Result<()> {
    validate_hash_shape(expected)?;
    let actual = if expected.starts_with("blake3:") {
        blake3_content_hash(text)
    } else {
        sha256_content_hash(text)
    };
    if actual != expected {
        eyre::bail!("definition document content hash does not match supplied text");
    }
    Ok(())
}

fn validate_hash_shape(value: &str) -> eyre::Result<()> {
    let (algorithm, digest) = value
        .split_once(':')
        .ok_or_else(|| eyre::eyre!("content hash must include an algorithm prefix"))?;
    let expected_length = match algorithm {
        "blake3" | "sha256" => 64,
        _ => eyre::bail!("unsupported content-hash algorithm `{algorithm}`"),
    };
    if digest.len() != expected_length
        || !digest
            .bytes()
            .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte))
    {
        eyre::bail!("content hash digest must be lowercase hexadecimal");
    }
    Ok(())
}

fn byte_offset_for_line_column(text: &str, line: u64, column: u64) -> eyre::Result<usize> {
    if line == 0 || column == 0 {
        eyre::bail!("definition line and column are one-based");
    }
    let requested_line =
        usize::try_from(line).map_err(|_| eyre::eyre!("definition line does not fit usize"))?;
    let requested_column =
        usize::try_from(column).map_err(|_| eyre::eyre!("definition column does not fit usize"))?;
    let mut current_line = 1_usize;
    let mut line_start = 0_usize;
    for (offset, byte) in text.bytes().enumerate() {
        if current_line == requested_line {
            break;
        }
        if byte == b'\n' {
            current_line += 1;
            line_start = offset + 1;
        }
    }
    if current_line != requested_line {
        eyre::bail!("definition line {line} is outside the document");
    }
    let physical_end = text[line_start..]
        .find('\n')
        .map_or(text.len(), |relative| line_start + relative);
    let logical_end = (physical_end > line_start
        && text.as_bytes().get(physical_end - 1) == Some(&b'\r'))
    .then_some(physical_end - 1)
    .unwrap_or(physical_end);
    let line_text = &text[line_start..logical_end];
    let scalar_index = requested_column - 1;
    let scalar_count = line_text.chars().count();
    if scalar_index > scalar_count {
        eyre::bail!(
            "definition column {column} is outside line {line}; maximum insertion column is {}",
            scalar_count + 1
        );
    }
    let relative = if scalar_index == scalar_count {
        line_text.len()
    } else {
        line_text
            .char_indices()
            .nth(scalar_index)
            .map(|(offset, _)| offset)
            .expect("bounded Unicode scalar index must exist")
    };
    Ok(line_start + relative)
}

fn require_nonblank(value: &str, label: &str) -> eyre::Result<()> {
    if value.trim().is_empty() {
        eyre::bail!("{label} must not be blank");
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::super::JavaSourceRootKind;
    use super::super::JavaSourceSpanOutput;
    use super::*;

    fn request(text: &str, line: u64, column: u64) -> DefinitionAtPositionRequest {
        let workspace = DefinitionWorkspaceIdentityInput {
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
            workspace_generation: 7,
        };
        let document = DefinitionDocumentInput {
            address: "file://source/example/A.java".to_owned(),
            root_id: "custom-0".to_owned(),
            root_relative_path: "example/A.java".to_owned(),
            report_path: "source/example/A.java".to_owned(),
            source_set: "custom".to_owned(),
            text: text.to_owned(),
            content_hash: blake3_content_hash(text),
            disk_content_hash: Some(sha256_content_hash(text)),
        };
        let position = DefinitionTextPositionInput::from_line_column(text, line, column)
            .expect("fixture position");
        DefinitionAtPositionRequest::new(11, 3, workspace, document, position)
    }

    #[test]
    fn definition_request_round_trips_through_facet_json() {
        let request = request("class A { String value; }\n", 1, 11);
        request.validate().expect("valid request");
        let encoded = facet_json::to_string(&request).expect("encode request");
        let decoded: DefinitionAtPositionRequest =
            facet_json::from_str(&encoded).expect("decode request");
        assert_eq!(decoded, request);
    }

    #[test]
    fn unicode_scalar_positions_derive_exact_utf8_offsets() {
        let text = "class A {\r\n  String café = \"🦀\";\r\n}\r\n";
        let position =
            DefinitionTextPositionInput::from_line_column(text, 2, 13).expect("Unicode position");
        assert_eq!(
            &text.as_bytes()[position.byte_offset as usize..][..2],
            "é".as_bytes()
        );
        position.validate(text).expect("matching projections");

        let mut mismatched = position;
        mismatched.byte_offset += 1;
        assert!(mismatched.validate(text).is_err());
    }

    #[test]
    fn request_rejects_hash_path_root_and_position_disagreement() {
        let mut value = request("class A {}\n", 1, 7);
        value.document.content_hash = blake3_content_hash("different");
        assert!(value.validate().is_err());

        let mut value = request("class A {}\n", 1, 7);
        value.document.root_relative_path = "example/../A.java".to_owned();
        assert!(value.validate().is_err());

        let mut value = request("class A {}\n", 1, 7);
        value.document.root_id = "missing".to_owned();
        assert!(value.validate().is_err());

        let mut value = request("class A {}\n", 1, 7);
        value.position.byte_offset += 1;
        assert!(value.validate().is_err());
    }

    #[test]
    fn content_hash_contract_accepts_java_sha256_snapshots() {
        let text = "class A {}\n";
        let mut value = request(text, 1, 7);
        value.document.content_hash = sha256_content_hash(text);
        value.validate().expect("SHA-256 snapshot");
    }

    #[test]
    fn position_rejects_line_column_outside_document() {
        assert!(DefinitionTextPositionInput::from_line_column("A\n", 3, 1).is_err());
        assert!(DefinitionTextPositionInput::from_line_column("A\n", 1, 3).is_err());
        assert!(DefinitionTextPositionInput::from_line_column("A\n", 0, 1).is_err());
    }

    #[test]
    fn span_type_remains_available_to_result_consumers() {
        let span = JavaSourceSpanOutput {
            path: "source/A.java".to_owned(),
            source_set: "custom".to_owned(),
            source_hash: blake3_content_hash("A"),
            start_byte: 0,
            end_byte: 1,
            start_line: 1,
            start_column: 1,
            end_line: 1,
            end_column: 2,
        };
        assert_eq!(span.end_byte, 1);
    }
}
