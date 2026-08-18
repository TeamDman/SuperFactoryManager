use super::DefinitionAtPositionRequest;
use super::DefinitionDependencySourceRoot;
use super::DefinitionDocumentIdentityOutput;
use super::DefinitionDocumentInput;
use super::DefinitionSourceSpanOutput;
use super::DefinitionTextPositionInput;
use super::DefinitionWorkspaceIdentityInput;
use super::DiagnosticSeverity;
use super::JavaAnalysisDiagnosticOutput;
use super::JavaDefinitionResolutionSurface;
use super::JavaResolvedInteractionDocument;
use super::JavaSourceSpanOutput;
use super::JavaSourceWorkspace;
use super::JavaSymbolIdentityOutput;
use super::JavaSymbolUsageOutput;
use super::JavaUsageKind;
use super::ResolutionConfidence;
use super::blake3_content_hash;
use super::contributed_address;
use super::definition_at_position_definition;
use super::sha256_content_hash;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::sync::Arc;
use tree_sitter_patched_arborium::Node;

pub const JAVA_INTERACTION_MAP_SCHEMA: &str = "sfm.java-interaction-map/1";
pub const JAVA_INTERACTION_MAP_REQUEST_SCHEMA: &str = "sfm.java-interaction-map-request/1";
pub const JAVA_INTERACTION_REGION_SCHEMA: &str = "sfm.region/1";
pub const JAVA_INTERACTION_DOMAIN_SCHEMA: &str = "sfm.region-domain/1";
pub const JAVA_INTERACTION_PROJECTION_SCHEMA: &str = "sfm.region-projection/1";
pub const JAVA_INTERACTION_OUTLINK_SCHEMA: &str = "sfm.outlink/1";

pub const JAVA_INTERACTION_MAP_DEFAULT_MAX_REGIONS: u64 = 4_096;
pub const JAVA_INTERACTION_MAP_MAX_REGIONS: u64 = 16_384;
pub const JAVA_INTERACTION_MAP_DEFAULT_MAX_INVENTORY_FILES: u64 = 4_096;
pub const JAVA_INTERACTION_MAP_MAX_INVENTORY_FILES: u64 = 16_384;
pub const JAVA_INTERACTION_MAP_DEFAULT_MAX_ENCODED_BYTES: u64 = 8 * 1024 * 1024;
pub const JAVA_INTERACTION_MAP_MAX_ENCODED_BYTES: u64 = 12 * 1024 * 1024;

#[derive(Facet, Clone, Copy, Debug, Eq, PartialEq)]
pub struct JavaInteractionMapWindowInput {
    pub region_offset: u64,
    pub max_regions: u64,
    pub inventory_offset: u64,
    pub max_inventory_files: u64,
    pub max_encoded_bytes: u64,
}

impl Default for JavaInteractionMapWindowInput {
    fn default() -> Self {
        Self {
            region_offset: 0,
            max_regions: JAVA_INTERACTION_MAP_DEFAULT_MAX_REGIONS,
            inventory_offset: 0,
            max_inventory_files: JAVA_INTERACTION_MAP_DEFAULT_MAX_INVENTORY_FILES,
            max_encoded_bytes: JAVA_INTERACTION_MAP_DEFAULT_MAX_ENCODED_BYTES,
        }
    }
}

impl JavaInteractionMapWindowInput {
    fn validate(self) -> eyre::Result<()> {
        if self.max_regions == 0 || self.max_regions > JAVA_INTERACTION_MAP_MAX_REGIONS {
            eyre::bail!(
                "Java interaction-map max_regions must be in 1..={JAVA_INTERACTION_MAP_MAX_REGIONS}"
            );
        }
        if self.max_inventory_files == 0
            || self.max_inventory_files > JAVA_INTERACTION_MAP_MAX_INVENTORY_FILES
        {
            eyre::bail!(
                "Java interaction-map max_inventory_files must be in 1..={JAVA_INTERACTION_MAP_MAX_INVENTORY_FILES}"
            );
        }
        if self.max_encoded_bytes == 0
            || self.max_encoded_bytes > JAVA_INTERACTION_MAP_MAX_ENCODED_BYTES
        {
            eyre::bail!(
                "Java interaction-map max_encoded_bytes must be in 1..={JAVA_INTERACTION_MAP_MAX_ENCODED_BYTES}"
            );
        }
        Ok(())
    }
}

/// Immutable per-document semantic-map request. A caller may page regions and
/// workspace inventory independently while retaining one document/workspace
/// generation. `known_semantic_fingerprint` permits a zero-payload refresh.
#[derive(Facet, Clone, Debug, Eq, PartialEq)]
pub struct JavaInteractionMapRequest {
    pub schema: String,
    pub request_id: u64,
    pub request_generation: u64,
    pub workspace: DefinitionWorkspaceIdentityInput,
    pub document: DefinitionDocumentInput,
    #[facet(default)]
    pub window: JavaInteractionMapWindowInput,
    #[facet(default, skip_serializing_if = Option::is_none)]
    pub known_semantic_fingerprint: Option<String>,
}

impl JavaInteractionMapRequest {
    #[must_use]
    pub fn new(
        request_id: u64,
        request_generation: u64,
        workspace: DefinitionWorkspaceIdentityInput,
        document: DefinitionDocumentInput,
    ) -> Self {
        Self {
            schema: JAVA_INTERACTION_MAP_REQUEST_SCHEMA.to_owned(),
            request_id,
            request_generation,
            workspace,
            document,
            window: JavaInteractionMapWindowInput::default(),
            known_semantic_fingerprint: None,
        }
    }

    #[must_use]
    pub fn with_window(mut self, window: JavaInteractionMapWindowInput) -> Self {
        self.window = window;
        self
    }

    #[must_use]
    pub fn with_known_semantic_fingerprint(mut self, fingerprint: impl Into<String>) -> Self {
        self.known_semantic_fingerprint = Some(fingerprint.into());
        self
    }

    /// Validate the complete portable request without consulting mutable state.
    ///
    /// # Errors
    ///
    /// Returns an error for a schema mismatch, invalid identity/hash, or an
    /// unbounded page request.
    pub fn validate(&self) -> eyre::Result<()> {
        if self.schema != JAVA_INTERACTION_MAP_REQUEST_SCHEMA {
            eyre::bail!("Java interaction-map request schema is unsupported");
        }
        if self.request_id == 0 {
            eyre::bail!("Java interaction-map request id must be positive");
        }
        self.workspace.validate()?;
        self.document.validate(&self.workspace)?;
        self.window.validate()?;
        if let Some(fingerprint) = &self.known_semantic_fingerprint {
            validate_blake3_fingerprint(fingerprint)?;
        }
        Ok(())
    }

    /// Reuse the established workspace/document validation and warm engine
    /// path without inventing a second source-authority contract.
    pub(crate) fn as_definition_request(&self) -> eyre::Result<DefinitionAtPositionRequest> {
        let position = DefinitionTextPositionInput::from_line_column(&self.document.text, 1, 1)?;
        Ok(DefinitionAtPositionRequest::new(
            self.request_id,
            self.request_generation,
            self.workspace.clone(),
            self.document.clone(),
            position,
        ))
    }
}

#[derive(Facet, Clone, Copy, Debug, Eq, PartialEq, Ord, PartialOrd)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum JavaInteractionMapOutcome {
    Success,
    NotModified,
    StaleDocument,
    InvalidRequest,
    Unavailable,
}

#[derive(Facet, Clone, Copy, Debug, Eq, PartialEq, Ord, PartialOrd)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum JavaInteractionClassificationStatus {
    Actionable,
    ExplicitNoAction,
    Unsupported,
    Unclassified,
}

#[derive(Facet, Clone, Copy, Debug, Eq, PartialEq, Ord, PartialOrd)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum JavaInteractionFileState {
    Covered,
    Partial,
    UnsupportedExtension,
    Missing,
    Stale,
    ParseFailed,
    LayoutFailed,
    IndexFailed,
    Timeout,
    Skipped,
    Failed,
}

#[derive(Facet, Clone, Copy, Debug, Eq, PartialEq, Ord, PartialOrd)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum JavaInteractionReciprocityStatus {
    Verified,
    TypedException,
}

#[derive(Facet, Clone, Copy, Debug, Eq, PartialEq, Ord, PartialOrd)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum JavaInteractionExceptionEffect {
    ApprovedException,
    FailsStrictProfile,
    Informational,
}

#[derive(Facet, Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
pub struct JavaInteractionRegionAxisOutput {
    pub start_inclusive: u64,
    pub end_exclusive: u64,
}

#[derive(Facet, Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
pub struct JavaInteractionDomainOutput {
    pub schema: String,
    pub id: String,
    pub kind: String,
    pub dimensions: u64,
    pub coordinate_kinds: Vec<String>,
    pub authority: String,
    pub snapshot_identity: String,
}

#[derive(Facet, Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
pub struct JavaInteractionProjectionOutput {
    pub schema: String,
    pub id: String,
    pub from_domain_id: String,
    pub to_domain_id: String,
    pub loss: String,
    pub completeness: String,
    pub transform: String,
    pub fingerprint: String,
    pub authority: String,
}

#[derive(Facet, Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
pub struct JavaInteractionRegionOutput {
    pub schema: String,
    pub id: String,
    pub domain_id: String,
    pub representation: String,
    pub bounds: Vec<JavaInteractionRegionAxisOutput>,
    pub edge_policy: String,
    pub semantic_kind: String,
    pub provenance: String,
    pub projection_ids: Vec<String>,
}

impl JavaInteractionRegionOutput {
    #[must_use]
    pub fn start_byte(&self) -> u64 {
        self.bounds.first().map_or(0, |axis| axis.start_inclusive)
    }

    #[must_use]
    pub fn end_byte(&self) -> u64 {
        self.bounds.first().map_or(0, |axis| axis.end_exclusive)
    }
}

#[derive(Facet, Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
pub struct JavaInteractionActionDraftOutput {
    pub action_id: String,
    pub arguments: Vec<String>,
}

#[derive(Facet, Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
pub struct JavaInteractionOutlinkOutput {
    pub schema: String,
    pub id: String,
    pub source_region_id: String,
    pub destination_region_id: String,
    #[facet(default, skip_serializing_if = Option::is_none)]
    pub destination_query: Option<String>,
    pub relation_kind: String,
    pub intent: String,
    pub provider_id: String,
    pub provider_generation: u64,
    pub reason: String,
    pub confidence: String,
    pub completeness: String,
    pub recommended_projection: String,
    pub action_drafts: Vec<JavaInteractionActionDraftOutput>,
    pub provenance: String,
}

#[derive(Facet, Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
pub struct JavaInteractionClassificationOutput {
    pub region_id: String,
    pub status: JavaInteractionClassificationStatus,
    #[facet(default, skip_serializing_if = Option::is_none)]
    pub reason_code: Option<String>,
    pub navigation_outlink_ids: Vec<String>,
    pub contextual_action_ids: Vec<String>,
}

#[derive(Facet, Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
pub struct JavaInteractionExceptionOutput {
    pub id: String,
    pub region_id: String,
    pub code: String,
    pub reason: String,
    pub effect: JavaInteractionExceptionEffect,
    pub witness: String,
}

#[derive(Facet, Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
pub struct JavaInteractionReciprocityOutput {
    pub definition_outlink_id: String,
    pub reference_outlink_id: String,
    pub status: JavaInteractionReciprocityStatus,
    #[facet(default, skip_serializing_if = Option::is_none)]
    pub exception_code: Option<String>,
    pub witness: String,
}

#[derive(Facet, Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
pub struct JavaInteractionFileOutput {
    pub address: String,
    pub resolver_id: String,
    pub root_id: String,
    pub root_relative_path: String,
    pub report_path: String,
    pub source_set: String,
    #[facet(default, skip_serializing_if = Option::is_none)]
    pub content_hash: Option<String>,
    pub state: JavaInteractionFileState,
    #[facet(default, skip_serializing_if = Option::is_none)]
    pub diagnostic: Option<String>,
}

#[derive(Facet, Clone, Copy, Debug, Eq, PartialEq)]
pub struct JavaInteractionMapPageOutput {
    pub region_offset: u64,
    pub returned_regions: u64,
    pub total_regions: u64,
    pub next_region_offset: Option<u64>,
    pub inventory_offset: u64,
    pub returned_inventory_files: u64,
    pub total_inventory_files: u64,
    pub next_inventory_offset: Option<u64>,
    pub encoded_bytes: u64,
}

#[derive(Facet, Clone, Debug, Eq, PartialEq)]
pub struct JavaInteractionMapResult {
    pub schema: String,
    pub request_id: u64,
    pub request_generation: u64,
    pub workspace_generation: u64,
    pub workspace_fingerprint: String,
    pub document_generation: u64,
    pub semantic_generation: u64,
    pub semantic_fingerprint: String,
    pub outcome: JavaInteractionMapOutcome,
    pub document: DefinitionDocumentIdentityOutput,
    pub domains: Vec<JavaInteractionDomainOutput>,
    pub projections: Vec<JavaInteractionProjectionOutput>,
    pub regions: Vec<JavaInteractionRegionOutput>,
    pub classifications: Vec<JavaInteractionClassificationOutput>,
    pub outlinks: Vec<JavaInteractionOutlinkOutput>,
    pub reciprocity: Vec<JavaInteractionReciprocityOutput>,
    pub exceptions: Vec<JavaInteractionExceptionOutput>,
    pub files: Vec<JavaInteractionFileOutput>,
    pub page: JavaInteractionMapPageOutput,
    pub diagnostics: Vec<JavaAnalysisDiagnosticOutput>,
}

impl JavaInteractionMapResult {
    #[must_use]
    pub fn not_modified(
        request: &JavaInteractionMapRequest,
        semantic_generation: u64,
        semantic_fingerprint: String,
    ) -> Self {
        Self {
            schema: JAVA_INTERACTION_MAP_SCHEMA.to_owned(),
            request_id: request.request_id,
            request_generation: request.request_generation,
            workspace_generation: request.workspace.workspace_generation,
            workspace_fingerprint: request.workspace.workspace_fingerprint.clone(),
            document_generation: request.request_generation,
            semantic_generation,
            semantic_fingerprint,
            outcome: JavaInteractionMapOutcome::NotModified,
            document: DefinitionDocumentIdentityOutput::from(&request.document),
            domains: Vec::new(),
            projections: Vec::new(),
            regions: Vec::new(),
            classifications: Vec::new(),
            outlinks: Vec::new(),
            reciprocity: Vec::new(),
            exceptions: Vec::new(),
            files: Vec::new(),
            page: JavaInteractionMapPageOutput {
                region_offset: request.window.region_offset,
                returned_regions: 0,
                total_regions: 0,
                next_region_offset: None,
                inventory_offset: request.window.inventory_offset,
                returned_inventory_files: 0,
                total_inventory_files: 0,
                next_inventory_offset: None,
                encoded_bytes: 0,
            },
            diagnostics: Vec::new(),
        }
    }

    #[must_use]
    pub fn invalid_request(
        request: &JavaInteractionMapRequest,
        message: impl Into<String>,
    ) -> Self {
        Self::terminal(
            request,
            JavaInteractionMapOutcome::InvalidRequest,
            "java.interaction-map-invalid-request",
            message,
        )
    }

    #[must_use]
    pub fn stale_document(request: &JavaInteractionMapRequest, message: impl Into<String>) -> Self {
        Self::terminal(
            request,
            JavaInteractionMapOutcome::StaleDocument,
            "java.interaction-map-stale-document",
            message,
        )
    }

    #[must_use]
    pub fn unavailable(request: &JavaInteractionMapRequest, message: impl Into<String>) -> Self {
        Self::terminal(
            request,
            JavaInteractionMapOutcome::Unavailable,
            "java.interaction-map-unavailable",
            message,
        )
    }

    fn terminal(
        request: &JavaInteractionMapRequest,
        outcome: JavaInteractionMapOutcome,
        diagnostic_code: &str,
        message: impl Into<String>,
    ) -> Self {
        let semantic_fingerprint = format!("blake3:{}", "0".repeat(64));
        Self {
            schema: JAVA_INTERACTION_MAP_SCHEMA.to_owned(),
            request_id: request.request_id,
            request_generation: request.request_generation,
            workspace_generation: request.workspace.workspace_generation,
            workspace_fingerprint: request.workspace.workspace_fingerprint.clone(),
            document_generation: request.request_generation,
            semantic_generation: 0,
            semantic_fingerprint,
            outcome,
            document: DefinitionDocumentIdentityOutput::from(&request.document),
            domains: Vec::new(),
            projections: Vec::new(),
            regions: Vec::new(),
            classifications: Vec::new(),
            outlinks: Vec::new(),
            reciprocity: Vec::new(),
            exceptions: Vec::new(),
            files: Vec::new(),
            page: JavaInteractionMapPageOutput {
                region_offset: request.window.region_offset,
                returned_regions: 0,
                total_regions: 0,
                next_region_offset: None,
                inventory_offset: request.window.inventory_offset,
                returned_inventory_files: 0,
                total_inventory_files: 0,
                next_inventory_offset: None,
                encoded_bytes: 0,
            },
            diagnostics: vec![JavaAnalysisDiagnosticOutput {
                code: diagnostic_code.to_owned(),
                severity: DiagnosticSeverity::Error,
                message: message.into(),
                span: None,
            }],
        }
    }
}

#[derive(Clone)]
struct RawSyntaxRegion {
    ordinal: usize,
    parent_ordinal: Option<usize>,
    kind: String,
    start: usize,
    end: usize,
    flags: RawSyntaxFlags,
}

#[derive(Clone, Copy)]
struct RawSyntaxFlags(u8);

impl RawSyntaxFlags {
    const NAMED: u8 = 1 << 0;
    const ERROR: u8 = 1 << 1;
    const MISSING: u8 = 1 << 2;
    const LEAF: u8 = 1 << 3;

    fn from_node(node: &Node<'_>) -> Self {
        let mut flags = 0;
        if node.is_named() {
            flags |= Self::NAMED;
        }
        if node.is_error() {
            flags |= Self::ERROR;
        }
        if node.is_missing() {
            flags |= Self::MISSING;
        }
        if node.child_count() == 0 {
            flags |= Self::LEAF;
        }
        Self(flags)
    }

    const fn contains(self, flag: u8) -> bool {
        self.0 & flag != 0
    }
}

struct CompleteMap {
    domains: Vec<JavaInteractionDomainOutput>,
    projections: Vec<JavaInteractionProjectionOutput>,
    regions: Vec<JavaInteractionRegionOutput>,
    classifications: Vec<JavaInteractionClassificationOutput>,
    outlinks: Vec<JavaInteractionOutlinkOutput>,
    reciprocity: Vec<JavaInteractionReciprocityOutput>,
    exceptions: Vec<JavaInteractionExceptionOutput>,
    diagnostics: Vec<JavaAnalysisDiagnosticOutput>,
}

struct OutlinkBuilder {
    semantic_generation: u64,
    provenance: String,
    values: Vec<JavaInteractionOutlinkOutput>,
    seen: BTreeSet<(String, String, String)>,
}

struct OutlinkSpec {
    source_region_id: String,
    destination_region_id: String,
    destination_query: Option<String>,
    relation_kind: String,
    reason: String,
    confidence: String,
    completeness: String,
    recommended_projection: String,
    action_drafts: Vec<JavaInteractionActionDraftOutput>,
}

impl OutlinkBuilder {
    fn new(semantic_generation: u64, semantic_fingerprint: &str) -> Self {
        Self {
            semantic_generation,
            provenance: format!("sfm:rust_java_semantics@{semantic_fingerprint}"),
            values: Vec::new(),
            seen: BTreeSet::new(),
        }
    }

    fn add(&mut self, spec: OutlinkSpec) -> String {
        let key = (
            spec.source_region_id.clone(),
            spec.destination_region_id.clone(),
            spec.relation_kind.clone(),
        );
        if !self.seen.insert(key) {
            return self
                .values
                .iter()
                .find(|value| {
                    value.source_region_id == spec.source_region_id
                        && value.destination_region_id == spec.destination_region_id
                        && value.relation_kind == spec.relation_kind
                })
                .map(|value| value.id.clone())
                .expect("deduplicated outlink remains present");
        }
        let id = stable_id(
            "outlink",
            &[
                &spec.source_region_id,
                &spec.destination_region_id,
                &spec.relation_kind,
            ],
        );
        self.values.push(JavaInteractionOutlinkOutput {
            schema: JAVA_INTERACTION_OUTLINK_SCHEMA.to_owned(),
            id: id.clone(),
            source_region_id: spec.source_region_id,
            destination_region_id: spec.destination_region_id,
            destination_query: spec.destination_query,
            relation_kind: spec.relation_kind,
            intent: "navigate".to_owned(),
            provider_id: "sfm:java_symbols".to_owned(),
            provider_generation: self.semantic_generation,
            reason: spec.reason,
            confidence: spec.confidence,
            completeness: spec.completeness,
            recommended_projection: spec.recommended_projection,
            action_drafts: spec.action_drafts,
            provenance: self.provenance.clone(),
        });
        id
    }
}

/// Build one complete semantic map, then return the independently paged region
/// and inventory windows requested by the caller.
#[expect(
    clippy::too_many_arguments,
    reason = "the builder receives explicit immutable semantic authorities and returns one frozen contract page"
)]
pub(crate) fn build_java_interaction_map(
    request: &JavaInteractionMapRequest,
    resolved: &JavaResolvedInteractionDocument,
    surface: &Arc<JavaDefinitionResolutionSurface>,
    workspace: &JavaSourceWorkspace,
    dependency_source_roots: &[DefinitionDependencySourceRoot],
    inventory: Vec<JavaInteractionFileOutput>,
    semantic_generation: u64,
    semantic_fingerprint: String,
) -> eyre::Result<JavaInteractionMapResult> {
    let complete = build_complete_map(
        request,
        resolved,
        surface,
        workspace,
        dependency_source_roots,
        semantic_generation,
        &semantic_fingerprint,
    )?;
    paginate_complete_map(
        request,
        semantic_generation,
        semantic_fingerprint,
        complete,
        inventory,
    )
}

#[expect(
    clippy::too_many_lines,
    reason = "the ordered semantic-map assembly is one deterministic transaction whose stage ordering is contract evidence"
)]
fn build_complete_map(
    request: &JavaInteractionMapRequest,
    resolved: &JavaResolvedInteractionDocument,
    surface: &Arc<JavaDefinitionResolutionSurface>,
    workspace: &JavaSourceWorkspace,
    dependency_source_roots: &[DefinitionDependencySourceRoot],
    semantic_generation: u64,
    semantic_fingerprint: &str,
) -> eyre::Result<CompleteMap> {
    let source = resolved.syntax.source.as_str();
    let document_short = short_fingerprint(&request.document.content_hash);
    let utf8_domain_id = format!("utf8:{document_short}");
    let text_domain_id = format!("text:{document_short}");
    let syntax_domain_id = format!("java-syntax:{document_short}");
    let path_domain_id = stable_id("path-domain", &[&request.document.address]);
    let utf8_to_text_projection_id = stable_id(
        "projection",
        &[&utf8_domain_id, &text_domain_id, "utf8-line-column-v1"],
    );
    let utf8_to_syntax_projection_id = stable_id(
        "projection",
        &[&utf8_domain_id, &syntax_domain_id, "arborium-java-v1"],
    );
    let provenance = format!("sfm:rust_java_semantics@{semantic_fingerprint}");
    let domains = vec![
        domain(
            &utf8_domain_id,
            "utf8",
            &["byte-offset"],
            "sfm:rust_java_semantics",
            &request.document.content_hash,
        ),
        domain(
            &text_domain_id,
            "text",
            &["line", "column"],
            "sfm:rust_java_semantics",
            &request.document.content_hash,
        ),
        domain(
            &syntax_domain_id,
            "java-syntax",
            &["node-id"],
            "sfm:rust_java_semantics",
            semantic_fingerprint,
        ),
        domain(
            &path_domain_id,
            "path",
            &["address"],
            "sfm:java_source_resolvers",
            &request.workspace.workspace_fingerprint,
        ),
    ];
    let projections = vec![
        projection(
            &utf8_to_text_projection_id,
            &utf8_domain_id,
            &text_domain_id,
            "lossless",
            "complete",
            "sfm:utf8_line_column_v1",
            &request.document.content_hash,
        ),
        projection(
            &utf8_to_syntax_projection_id,
            &utf8_domain_id,
            &syntax_domain_id,
            "one-to-many",
            "complete",
            "sfm:arborium_java_nodes_v1",
            semantic_fingerprint,
        ),
    ];

    let raw = collect_raw_syntax_regions(resolved.syntax.tree.root_node());
    let mut regions = Vec::new();
    let mut raw_region_ids = BTreeMap::new();
    for node in &raw {
        let text = source.get(node.start..node.end).unwrap_or_default();
        let semantic_kind = semantic_kind(node, text);
        let id = stable_id(
            "region",
            &[
                &document_short,
                &node.start.to_string(),
                &node.end.to_string(),
                &semantic_kind,
                &node.ordinal.to_string(),
            ],
        );
        raw_region_ids.insert(node.ordinal, id.clone());
        regions.push(region(
            id,
            &utf8_domain_id,
            node.start,
            node.end,
            semantic_kind,
            &provenance,
            &[
                utf8_to_text_projection_id.clone(),
                utf8_to_syntax_projection_id.clone(),
            ],
        ));
    }
    let root_region_id = raw_region_ids
        .get(&0)
        .cloned()
        .ok_or_else(|| eyre::eyre!("Arborium Java tree did not expose a root region"))?;

    let mut synthetic_parent = BTreeMap::<String, String>::new();
    add_gap_regions(
        source,
        &raw,
        &raw_region_ids,
        &utf8_domain_id,
        &provenance,
        &utf8_to_text_projection_id,
        &utf8_to_syntax_projection_id,
        &document_short,
        &mut regions,
        &mut synthetic_parent,
    );
    add_signature_regions(
        source,
        &raw,
        &raw_region_ids,
        &utf8_domain_id,
        &provenance,
        &utf8_to_text_projection_id,
        &utf8_to_syntax_projection_id,
        &document_short,
        &mut regions,
        &mut synthetic_parent,
    );

    // Java clients currently identify editor snapshots with SHA-256 while the
    // Rust syntax/index spans retain their established BLAKE3 source hashes.
    // Both hashes attest to these exact request-owned bytes; comparing tagged
    // hashes directly would silently discard every semantic usage region.
    let accepted_source_hashes = BTreeSet::from([
        request.document.content_hash.clone(),
        blake3_content_hash(source),
        sha256_content_hash(source),
    ]);
    let mut symbol_region_ids = Vec::new();
    for (ordinal, usage) in resolved.usages.iter().enumerate() {
        if usage.span.path != request.document.report_path
            || usage.span.source_set != request.document.source_set
            || !accepted_source_hashes.contains(&usage.span.source_hash)
        {
            continue;
        }
        let start = usize::try_from(usage.span.start_byte)
            .map_err(|error| eyre::eyre!("Java usage start does not fit usize: {error}"))?;
        let end = usize::try_from(usage.span.end_byte)
            .map_err(|error| eyre::eyre!("Java usage end does not fit usize: {error}"))?;
        if start > end
            || end > source.len()
            || !source.is_char_boundary(start)
            || !source.is_char_boundary(end)
        {
            continue;
        }
        let semantic_kind = format!("java-{}", usage_kind_name(usage.kind));
        let id = stable_id(
            "region",
            &[
                &document_short,
                &start.to_string(),
                &end.to_string(),
                &semantic_kind,
                &usage.target.canonical_selector(),
                &ordinal.to_string(),
            ],
        );
        let parent = smallest_containing_raw_region(start, end, &raw, &raw_region_ids)
            .unwrap_or_else(|| root_region_id.clone());
        synthetic_parent.insert(id.clone(), parent);
        symbol_region_ids.push((id.clone(), usage));
        if let Some(annotation_region_id) =
            enclosing_annotation_region_id(start, end, &raw, &raw_region_ids)
        {
            // An annotation is one deliberate interaction surface. Clicking
            // `@`, the name, or an argument-bearing annotation's remaining
            // syntax must offer the same resolved symbol relation; this is
            // not a nearest-token recovery rule.
            symbol_region_ids.push((annotation_region_id, usage));
        }
        regions.push(region(
            id,
            &utf8_domain_id,
            start,
            end,
            semantic_kind,
            &provenance,
            &[
                utf8_to_text_projection_id.clone(),
                utf8_to_syntax_projection_id.clone(),
            ],
        ));
    }

    regions.sort_by(|left, right| {
        (
            left.start_byte(),
            left.end_byte(),
            &left.semantic_kind,
            &left.id,
        )
            .cmp(&(
                right.start_byte(),
                right.end_byte(),
                &right.semantic_kind,
                &right.id,
            ))
    });
    regions.dedup_by(|left, right| left.id == right.id);

    let mut outlinks = OutlinkBuilder::new(semantic_generation, semantic_fingerprint);
    add_containment_outlinks(&raw, &raw_region_ids, &synthetic_parent, &mut outlinks);
    add_structural_landmark_outlinks(
        &raw,
        &raw_region_ids,
        &synthetic_parent,
        &regions,
        &mut outlinks,
    );
    outlinks.add(OutlinkSpec {
        source_region_id: root_region_id.clone(),
        destination_region_id: stable_id("path", &[&request.document.address]),
        destination_query: Some(request.document.address.clone()),
        relation_kind: "path".to_owned(),
        reason: "Document semantic root resolves to its authorized source address".to_owned(),
        confidence: "resolved".to_owned(),
        completeness: "complete".to_owned(),
        recommended_projection: "start".to_owned(),
        action_drafts: vec![JavaInteractionActionDraftOutput {
            action_id: "sfm:panel/open".to_owned(),
            arguments: vec![
                "sfm:text_editor".to_owned(),
                request.document.address.clone(),
            ],
        }],
    });

    let mut exceptions = Vec::new();
    add_delimiter_outlinks(
        source,
        &raw,
        &raw_region_ids,
        &mut outlinks,
        &mut exceptions,
    );
    add_declaration_landmark_outlinks(source, &raw, &raw_region_ids, &mut outlinks);
    let mut reciprocity = Vec::new();
    add_symbol_outlinks(
        request,
        surface,
        resolved,
        workspace,
        dependency_source_roots,
        &symbol_region_ids,
        &mut outlinks,
        &mut reciprocity,
        &mut exceptions,
    );

    let mut by_source = BTreeMap::<String, Vec<String>>::new();
    for outlink in &outlinks.values {
        by_source
            .entry(outlink.source_region_id.clone())
            .or_default()
            .push(outlink.id.clone());
    }
    let region_by_id = regions
        .iter()
        .map(|region| (region.id.clone(), region))
        .collect::<BTreeMap<_, _>>();
    for region in &regions {
        let is_whitespace = region_text(source, region)
            .is_some_and(|text| !text.is_empty() && text.chars().all(char::is_whitespace));
        let is_zero_width = region.start_byte() == region.end_byte();
        if !is_whitespace && !is_zero_width && !by_source.contains_key(&region.id) {
            let destination = synthetic_parent
                .get(&region.id)
                .cloned()
                .or_else(|| containing_region_id(region, &regions))
                .unwrap_or_else(|| root_region_id.clone());
            let outlink_id = outlinks.add(OutlinkSpec {
                source_region_id: region.id.clone(),
                destination_region_id: destination,
                destination_query: None,
                relation_kind: "recovery".to_owned(),
                reason: "Explicit syntax recovery route for a non-whitespace Java region"
                    .to_owned(),
                confidence: "recovery".to_owned(),
                completeness: "incomplete".to_owned(),
                recommended_projection: "start".to_owned(),
                action_drafts: Vec::new(),
            });
            by_source
                .entry(region.id.clone())
                .or_default()
                .push(outlink_id);
            exceptions.push(JavaInteractionExceptionOutput {
                id: stable_id("exception", &[&region.id, "recovery-navigation"]),
                region_id: region.id.clone(),
                code: "java.recovery-navigation".to_owned(),
                reason: "No exact semantic relation was available; the map published an explicit recovery relation instead of nearest-token guessing".to_owned(),
                effect: JavaInteractionExceptionEffect::FailsStrictProfile,
                witness: format!("{}..{}", region.start_byte(), region.end_byte()),
            });
        }
    }
    outlinks.values.sort();
    outlinks.values.dedup();
    by_source.clear();
    for outlink in &outlinks.values {
        by_source
            .entry(outlink.source_region_id.clone())
            .or_default()
            .push(outlink.id.clone());
    }

    let mut classifications = regions
        .iter()
        .map(|region| {
            let text = region_text(source, region).unwrap_or_default();
            let whitespace = !text.is_empty() && text.chars().all(char::is_whitespace);
            let zero_width = region.start_byte() == region.end_byte();
            let navigation_outlink_ids = by_source.get(&region.id).cloned().unwrap_or_default();
            if whitespace || zero_width {
                JavaInteractionClassificationOutput {
                    region_id: region.id.clone(),
                    status: JavaInteractionClassificationStatus::ExplicitNoAction,
                    reason_code: Some(if whitespace {
                        "java.whitespace".to_owned()
                    } else {
                        "java.zero-width-recovery-node".to_owned()
                    }),
                    navigation_outlink_ids,
                    contextual_action_ids: Vec::new(),
                }
            } else if navigation_outlink_ids.is_empty() {
                JavaInteractionClassificationOutput {
                    region_id: region.id.clone(),
                    status: JavaInteractionClassificationStatus::Unclassified,
                    reason_code: Some("java.no-navigation-outlink".to_owned()),
                    navigation_outlink_ids,
                    contextual_action_ids: Vec::new(),
                }
            } else {
                JavaInteractionClassificationOutput {
                    region_id: region.id.clone(),
                    status: JavaInteractionClassificationStatus::Actionable,
                    reason_code: None,
                    navigation_outlink_ids,
                    contextual_action_ids: Vec::new(),
                }
            }
        })
        .collect::<Vec<_>>();
    classifications.sort();
    classifications.dedup();
    reciprocity.sort();
    reciprocity.dedup();
    exceptions.sort();
    exceptions.dedup();

    let mut diagnostics = resolved.diagnostics.clone();
    diagnostics.sort_by(|left, right| {
        (&left.code, &left.message, &left.span).cmp(&(&right.code, &right.message, &right.span))
    });
    diagnostics.dedup();

    // This lookup guards against accidentally publishing an outlink from an
    // omitted local region after future builder changes.
    debug_assert!(outlinks.values.iter().all(|outlink| {
        region_by_id.contains_key(&outlink.source_region_id) || outlink.relation_kind == "reference"
    }));

    Ok(CompleteMap {
        domains,
        projections,
        regions,
        classifications,
        outlinks: outlinks.values,
        reciprocity,
        exceptions,
        diagnostics,
    })
}

fn paginate_complete_map(
    request: &JavaInteractionMapRequest,
    semantic_generation: u64,
    semantic_fingerprint: String,
    complete: CompleteMap,
    mut inventory: Vec<JavaInteractionFileOutput>,
) -> eyre::Result<JavaInteractionMapResult> {
    inventory.sort();
    inventory.dedup();
    let total_regions = u64::try_from(complete.regions.len()).unwrap_or(u64::MAX);
    let total_inventory_files = u64::try_from(inventory.len()).unwrap_or(u64::MAX);
    let region_start = usize::try_from(request.window.region_offset).map_err(|error| {
        eyre::eyre!("interaction-map region offset does not fit usize: {error}")
    })?;
    let inventory_start = usize::try_from(request.window.inventory_offset).map_err(|error| {
        eyre::eyre!("interaction-map inventory offset does not fit usize: {error}")
    })?;
    if region_start > complete.regions.len() {
        eyre::bail!("interaction-map region offset exceeds the region count");
    }
    if inventory_start > inventory.len() {
        eyre::bail!("interaction-map inventory offset exceeds the file count");
    }
    let region_end = region_start
        .saturating_add(usize::try_from(request.window.max_regions).unwrap_or(usize::MAX))
        .min(complete.regions.len());
    let inventory_end = inventory_start
        .saturating_add(usize::try_from(request.window.max_inventory_files).unwrap_or(usize::MAX))
        .min(inventory.len());
    let mut selected_regions = complete.regions[region_start..region_end].to_vec();
    let mut selected_inventory = inventory[inventory_start..inventory_end].to_vec();

    let mut result = JavaInteractionMapResult {
        schema: JAVA_INTERACTION_MAP_SCHEMA.to_owned(),
        request_id: request.request_id,
        request_generation: request.request_generation,
        workspace_generation: request.workspace.workspace_generation,
        workspace_fingerprint: request.workspace.workspace_fingerprint.clone(),
        document_generation: request.request_generation,
        semantic_generation,
        semantic_fingerprint,
        outcome: JavaInteractionMapOutcome::Success,
        document: DefinitionDocumentIdentityOutput::from(&request.document),
        domains: complete.domains,
        projections: complete.projections,
        regions: Vec::new(),
        classifications: Vec::new(),
        outlinks: Vec::new(),
        reciprocity: Vec::new(),
        exceptions: Vec::new(),
        files: Vec::new(),
        page: JavaInteractionMapPageOutput {
            region_offset: request.window.region_offset,
            returned_regions: 0,
            total_regions,
            next_region_offset: None,
            inventory_offset: request.window.inventory_offset,
            returned_inventory_files: 0,
            total_inventory_files,
            next_inventory_offset: None,
            encoded_bytes: 0,
        },
        diagnostics: complete.diagnostics,
    };

    install_page_relations(
        &mut result,
        &selected_regions,
        &selected_inventory,
        &complete.classifications,
        &complete.outlinks,
        &complete.reciprocity,
        &complete.exceptions,
    );
    if refresh_encoded_bytes(&mut result)? <= request.window.max_encoded_bytes {
        return Ok(result);
    }

    // Preserve at least one row from every non-empty requested lane so a
    // successful bounded page always advances both advertised cursors. The
    // current document's semantic regions are the primary payload; workspace
    // inventory is independently pageable supporting context. Trim inventory
    // first so a large workspace cannot silently punch holes in the canvas
    // interaction map. Prefix size is monotonic, so find each largest fitting
    // prefix in logarithmic serialization probes.
    if selected_inventory.len() > 1 {
        let fitting = largest_fitting_prefix(selected_inventory.len() - 1, |count| {
            install_page_relations(
                &mut result,
                &selected_regions,
                &selected_inventory[..count],
                &complete.classifications,
                &complete.outlinks,
                &complete.reciprocity,
                &complete.exceptions,
            );
            Ok(refresh_encoded_bytes(&mut result)? <= request.window.max_encoded_bytes)
        })?;
        if let Some(count) = fitting {
            selected_inventory.truncate(count);
            install_page_relations(
                &mut result,
                &selected_regions,
                &selected_inventory,
                &complete.classifications,
                &complete.outlinks,
                &complete.reciprocity,
                &complete.exceptions,
            );
            refresh_encoded_bytes(&mut result)?;
            return Ok(result);
        }
        selected_inventory.truncate(1);
    }

    if selected_regions.len() > 1 {
        let fitting = largest_fitting_prefix(selected_regions.len() - 1, |count| {
            install_page_relations(
                &mut result,
                &selected_regions[..count],
                &selected_inventory,
                &complete.classifications,
                &complete.outlinks,
                &complete.reciprocity,
                &complete.exceptions,
            );
            Ok(refresh_encoded_bytes(&mut result)? <= request.window.max_encoded_bytes)
        })?;
        if let Some(count) = fitting {
            selected_regions.truncate(count);
            install_page_relations(
                &mut result,
                &selected_regions,
                &selected_inventory,
                &complete.classifications,
                &complete.outlinks,
                &complete.reciprocity,
                &complete.exceptions,
            );
            refresh_encoded_bytes(&mut result)?;
            return Ok(result);
        }
        selected_regions.truncate(1);
    }

    install_page_relations(
        &mut result,
        &selected_regions,
        &selected_inventory,
        &complete.classifications,
        &complete.outlinks,
        &complete.reciprocity,
        &complete.exceptions,
    );
    let encoded_bytes = refresh_encoded_bytes(&mut result)?;
    if encoded_bytes > request.window.max_encoded_bytes {
        eyre::bail!(
            "Java interaction-map minimum non-empty page exceeds max_encoded_bytes={} bytes",
            request.window.max_encoded_bytes
        );
    }
    Ok(result)
}

fn largest_fitting_prefix(
    maximum: usize,
    mut fits: impl FnMut(usize) -> eyre::Result<bool>,
) -> eyre::Result<Option<usize>> {
    let mut low = 1_usize;
    let mut high = maximum;
    let mut best = None;
    while low <= high {
        let middle = low + (high - low) / 2;
        if fits(middle)? {
            best = Some(middle);
            low = middle.saturating_add(1);
        } else {
            high = middle.saturating_sub(1);
        }
    }
    Ok(best)
}

fn refresh_encoded_bytes(result: &mut JavaInteractionMapResult) -> eyre::Result<u64> {
    for _ in 0..4 {
        let encoded = facet_json::to_string(&*result)
            .map_err(|error| eyre::eyre!("failed to encode Java interaction map: {error}"))?;
        let encoded_bytes = u64::try_from(encoded.len()).unwrap_or(u64::MAX);
        if result.page.encoded_bytes == encoded_bytes {
            return Ok(encoded_bytes);
        }
        result.page.encoded_bytes = encoded_bytes;
    }
    eyre::bail!("Java interaction-map encoded size did not converge")
}

fn install_page_relations(
    result: &mut JavaInteractionMapResult,
    selected_regions: &[JavaInteractionRegionOutput],
    selected_inventory: &[JavaInteractionFileOutput],
    all_classifications: &[JavaInteractionClassificationOutput],
    all_outlinks: &[JavaInteractionOutlinkOutput],
    all_reciprocity: &[JavaInteractionReciprocityOutput],
    all_exceptions: &[JavaInteractionExceptionOutput],
) {
    let region_ids = selected_regions
        .iter()
        .map(|region| region.id.as_str())
        .collect::<BTreeSet<_>>();
    let mut outlinks = all_outlinks
        .iter()
        .filter(|outlink| region_ids.contains(outlink.source_region_id.as_str()))
        .cloned()
        .collect::<Vec<_>>();
    let selected_outlink_ids = outlinks
        .iter()
        .map(|outlink| outlink.id.as_str())
        .collect::<BTreeSet<_>>();
    let reciprocity = all_reciprocity
        .iter()
        .filter(|evidence| {
            selected_outlink_ids.contains(evidence.definition_outlink_id.as_str())
                || selected_outlink_ids.contains(evidence.reference_outlink_id.as_str())
        })
        .cloned()
        .collect::<Vec<_>>();
    let reciprocal_ids = reciprocity
        .iter()
        .flat_map(|evidence| {
            [
                evidence.definition_outlink_id.as_str(),
                evidence.reference_outlink_id.as_str(),
            ]
        })
        .collect::<BTreeSet<_>>();
    outlinks.extend(
        all_outlinks
            .iter()
            .filter(|outlink| reciprocal_ids.contains(outlink.id.as_str()))
            .cloned(),
    );
    outlinks.sort();
    outlinks.dedup();
    result.regions = selected_regions.to_vec();
    result.classifications = all_classifications
        .iter()
        .filter(|classification| region_ids.contains(classification.region_id.as_str()))
        .cloned()
        .collect();
    result.outlinks = outlinks;
    result.reciprocity = reciprocity;
    result.exceptions = all_exceptions
        .iter()
        .filter(|exception| region_ids.contains(exception.region_id.as_str()))
        .cloned()
        .collect();
    result.files = selected_inventory.to_vec();
    result.page.returned_regions = u64::try_from(result.regions.len()).unwrap_or(u64::MAX);
    result.page.next_region_offset = request_next_offset(
        result.page.region_offset,
        result.page.returned_regions,
        result.page.total_regions,
    );
    result.page.returned_inventory_files = u64::try_from(result.files.len()).unwrap_or(u64::MAX);
    result.page.next_inventory_offset = request_next_offset(
        result.page.inventory_offset,
        result.page.returned_inventory_files,
        result.page.total_inventory_files,
    );
}

fn request_next_offset(offset: u64, returned: u64, total: u64) -> Option<u64> {
    let next = offset.saturating_add(returned);
    (next < total).then_some(next)
}

fn collect_raw_syntax_regions(root: Node<'_>) -> Vec<RawSyntaxRegion> {
    fn visit(node: Node<'_>, parent_ordinal: Option<usize>, output: &mut Vec<RawSyntaxRegion>) {
        let ordinal = output.len();
        output.push(RawSyntaxRegion {
            ordinal,
            parent_ordinal,
            kind: node.kind().to_owned(),
            start: node.start_byte(),
            end: node.end_byte(),
            flags: RawSyntaxFlags::from_node(&node),
        });
        for index in 0..node.child_count() {
            if let Some(child) = node.child(index) {
                visit(child, Some(ordinal), output);
            }
        }
    }

    let mut output = Vec::new();
    visit(root, None, &mut output);
    output
}

fn semantic_kind(node: &RawSyntaxRegion, text: &str) -> String {
    if node.flags.contains(RawSyntaxFlags::ERROR) || node.kind == "ERROR" {
        return "java-malformed-recovery".to_owned();
    }
    if node.flags.contains(RawSyntaxFlags::MISSING) {
        return "java-missing-syntax".to_owned();
    }
    match node.kind.as_str() {
        "program" => "java-semantic-root",
        "package_declaration" => "java-package-declaration",
        "import_declaration" => "java-import",
        "modifiers" => "java-modifiers",
        "marker_annotation" | "annotation" | "annotation_type_declaration" => "java-annotation",
        "class_declaration" => "java-class-declaration",
        "interface_declaration" => "java-interface-declaration",
        "enum_declaration" => "java-enum-declaration",
        "record_declaration" => "java-record-declaration",
        "field_declaration" | "constant_declaration" | "enum_constant" => "java-field-declaration",
        "local_variable_declaration" => "java-local-declaration",
        "variable_declarator" => "java-variable-declarator",
        "method_declaration" => "java-method-declaration",
        "constructor_declaration" | "compact_constructor_declaration" => {
            "java-constructor-declaration"
        }
        "formal_parameter" | "spread_parameter" | "receiver_parameter" => {
            "java-parameter-declaration"
        }
        "class_body"
        | "interface_body"
        | "enum_body"
        | "annotation_type_body"
        | "constructor_body"
        | "block" => "java-body",
        "scoped_identifier" | "scoped_type_identifier" | "qualified_identifier" => {
            "java-qualified-name"
        }
        "identifier" | "type_identifier" => "java-identifier",
        "line_comment" | "block_comment" => "java-comment",
        kind if kind.ends_with("_statement") || kind == "expression_statement" => "java-statement",
        kind if kind.ends_with("_literal")
            || matches!(
                kind,
                "decimal_integer_literal"
                    | "hex_integer_literal"
                    | "octal_integer_literal"
                    | "binary_integer_literal"
                    | "decimal_floating_point_literal"
                    | "hex_floating_point_literal"
                    | "true"
                    | "false"
                    | "null_literal"
            ) =>
        {
            "java-literal"
        }
        _ if node.flags.contains(RawSyntaxFlags::LEAF) && is_delimiter(text) => "java-delimiter",
        _ if node.flags.contains(RawSyntaxFlags::LEAF) && is_operator(text) => "java-operator",
        _ if node.flags.contains(RawSyntaxFlags::LEAF) && is_punctuation(text) => {
            "java-punctuation"
        }
        _ if node.flags.contains(RawSyntaxFlags::LEAF)
            && !node.flags.contains(RawSyntaxFlags::NAMED) =>
        {
            "java-keyword"
        }
        _ => return format!("java-syntax-{}", node.kind.replace('_', "-")),
    }
    .to_owned()
}

#[expect(
    clippy::too_many_arguments,
    reason = "gap regions need explicit map identities and output ownership"
)]
fn add_gap_regions(
    source: &str,
    raw: &[RawSyntaxRegion],
    raw_region_ids: &BTreeMap<usize, String>,
    domain_id: &str,
    provenance: &str,
    text_projection_id: &str,
    syntax_projection_id: &str,
    document_short: &str,
    regions: &mut Vec<JavaInteractionRegionOutput>,
    synthetic_parent: &mut BTreeMap<String, String>,
) {
    let mut leaves = raw
        .iter()
        .filter(|node| node.flags.contains(RawSyntaxFlags::LEAF) && node.start < node.end)
        .map(|node| (node.start, node.end))
        .collect::<Vec<_>>();
    leaves.sort_unstable();
    leaves.dedup();
    let mut cursor = 0;
    let mut gap_ordinal = 0_u64;
    for (start, end) in leaves
        .into_iter()
        .chain(std::iter::once((source.len(), source.len())))
    {
        if cursor < start {
            for (run_start, run_end, whitespace) in classified_runs(source, cursor, start) {
                let semantic_kind = if whitespace {
                    "java-whitespace"
                } else {
                    "java-malformed-recovery"
                };
                let id = stable_id(
                    "region",
                    &[
                        document_short,
                        &run_start.to_string(),
                        &run_end.to_string(),
                        semantic_kind,
                        &format!("gap-{gap_ordinal}"),
                    ],
                );
                gap_ordinal = gap_ordinal.saturating_add(1);
                let parent =
                    smallest_containing_raw_region(run_start, run_end, raw, raw_region_ids)
                        .unwrap_or_else(|| raw_region_ids[&0].clone());
                synthetic_parent.insert(id.clone(), parent);
                regions.push(region(
                    id,
                    domain_id,
                    run_start,
                    run_end,
                    semantic_kind.to_owned(),
                    provenance,
                    &[
                        text_projection_id.to_owned(),
                        syntax_projection_id.to_owned(),
                    ],
                ));
            }
        }
        cursor = cursor.max(end);
    }
}

#[expect(
    clippy::too_many_arguments,
    reason = "signature regions need explicit map identities and output ownership"
)]
fn add_signature_regions(
    _source: &str,
    raw: &[RawSyntaxRegion],
    raw_region_ids: &BTreeMap<usize, String>,
    domain_id: &str,
    provenance: &str,
    text_projection_id: &str,
    syntax_projection_id: &str,
    document_short: &str,
    regions: &mut Vec<JavaInteractionRegionOutput>,
    synthetic_parent: &mut BTreeMap<String, String>,
) {
    for declaration in raw.iter().filter(|node| {
        matches!(
            node.kind.as_str(),
            "method_declaration" | "constructor_declaration" | "compact_constructor_declaration"
        )
    }) {
        let Some(body) = raw.iter().find(|candidate| {
            candidate.parent_ordinal == Some(declaration.ordinal)
                && matches!(candidate.kind.as_str(), "block" | "constructor_body")
        }) else {
            continue;
        };
        if declaration.start >= body.start {
            continue;
        }
        let id = stable_id(
            "region",
            &[
                document_short,
                &declaration.start.to_string(),
                &body.start.to_string(),
                "java-signature",
                &declaration.ordinal.to_string(),
            ],
        );
        synthetic_parent.insert(id.clone(), raw_region_ids[&declaration.ordinal].clone());
        regions.push(region(
            id,
            domain_id,
            declaration.start,
            body.start,
            "java-signature".to_owned(),
            provenance,
            &[
                text_projection_id.to_owned(),
                syntax_projection_id.to_owned(),
            ],
        ));
    }
}

fn add_containment_outlinks(
    raw: &[RawSyntaxRegion],
    raw_region_ids: &BTreeMap<usize, String>,
    synthetic_parent: &BTreeMap<String, String>,
    outlinks: &mut OutlinkBuilder,
) {
    for node in raw {
        let Some(parent_ordinal) = node.parent_ordinal else {
            continue;
        };
        let child = raw_region_ids[&node.ordinal].clone();
        let parent = raw_region_ids[&parent_ordinal].clone();
        add_containment_pair(outlinks, child, parent);
    }
    for (child, parent) in synthetic_parent {
        add_containment_pair(outlinks, child.clone(), parent.clone());
    }
}

fn add_containment_pair(outlinks: &mut OutlinkBuilder, child: String, parent: String) {
    outlinks.add(OutlinkSpec {
        source_region_id: child.clone(),
        destination_region_id: parent.clone(),
        destination_query: None,
        relation_kind: "containing-region".to_owned(),
        reason: "Arborium parent syntax region".to_owned(),
        confidence: "resolved".to_owned(),
        completeness: "complete".to_owned(),
        recommended_projection: "start".to_owned(),
        action_drafts: Vec::new(),
    });
    outlinks.add(OutlinkSpec {
        source_region_id: parent,
        destination_region_id: child,
        destination_query: None,
        relation_kind: "child-region".to_owned(),
        reason: "Arborium child syntax region".to_owned(),
        confidence: "resolved".to_owned(),
        completeness: "complete".to_owned(),
        recommended_projection: "start".to_owned(),
        action_drafts: Vec::new(),
    });
}

fn add_structural_landmark_outlinks(
    raw: &[RawSyntaxRegion],
    raw_region_ids: &BTreeMap<usize, String>,
    synthetic_parent: &BTreeMap<String, String>,
    regions: &[JavaInteractionRegionOutput],
    outlinks: &mut OutlinkBuilder,
) {
    for signature in regions
        .iter()
        .filter(|region| region.semantic_kind == "java-signature")
    {
        let Some(declaration) = synthetic_parent.get(&signature.id) else {
            continue;
        };
        outlinks.add(OutlinkSpec {
            source_region_id: declaration.clone(),
            destination_region_id: signature.id.clone(),
            destination_query: None,
            relation_kind: "signature".to_owned(),
            reason: "Callable declaration exposes its signature region".to_owned(),
            confidence: "resolved".to_owned(),
            completeness: "complete".to_owned(),
            recommended_projection: "start".to_owned(),
            action_drafts: Vec::new(),
        });
    }

    for node in raw {
        let relation = if is_body_kind(&node.kind) {
            "body"
        } else if is_statement_kind(&node.kind) {
            "statement"
        } else {
            continue;
        };
        let ancestor = nearest_ancestor(raw, node.ordinal, |candidate| match relation {
            "body" => is_declaration_kind(&candidate.kind),
            "statement" => is_body_kind(&candidate.kind),
            _ => false,
        });
        let Some(ancestor) = ancestor else {
            continue;
        };
        outlinks.add(OutlinkSpec {
            source_region_id: raw_region_ids[&ancestor].clone(),
            destination_region_id: raw_region_ids[&node.ordinal].clone(),
            destination_query: None,
            relation_kind: relation.to_owned(),
            reason: format!("Java {relation} landmark derived from the Arborium syntax tree"),
            confidence: "resolved".to_owned(),
            completeness: "complete".to_owned(),
            recommended_projection: "start".to_owned(),
            action_drafts: Vec::new(),
        });
    }
}

fn nearest_ancestor(
    raw: &[RawSyntaxRegion],
    ordinal: usize,
    predicate: impl Fn(&RawSyntaxRegion) -> bool,
) -> Option<usize> {
    let mut current = raw.get(ordinal)?.parent_ordinal;
    while let Some(candidate) = current {
        let node = raw.get(candidate)?;
        if predicate(node) {
            return Some(candidate);
        }
        current = node.parent_ordinal;
    }
    None
}

fn is_body_kind(kind: &str) -> bool {
    matches!(
        kind,
        "class_body"
            | "interface_body"
            | "enum_body"
            | "annotation_type_body"
            | "constructor_body"
            | "block"
    )
}

fn is_statement_kind(kind: &str) -> bool {
    kind.ends_with("_statement") || kind == "expression_statement"
}

fn add_delimiter_outlinks(
    source: &str,
    raw: &[RawSyntaxRegion],
    raw_region_ids: &BTreeMap<usize, String>,
    outlinks: &mut OutlinkBuilder,
    exceptions: &mut Vec<JavaInteractionExceptionOutput>,
) {
    let mut stacks = BTreeMap::<char, Vec<&RawSyntaxRegion>>::new();
    for node in raw
        .iter()
        .filter(|node| node.flags.contains(RawSyntaxFlags::LEAF))
    {
        let Some(text) = source.get(node.start..node.end) else {
            continue;
        };
        let mut chars = text.chars();
        let Some(character) = chars.next() else {
            continue;
        };
        if chars.next().is_some() {
            continue;
        }
        match character {
            '{' | '(' | '[' => stacks.entry(character).or_default().push(node),
            '}' | ')' | ']' => {
                let opening = match character {
                    '}' => '{',
                    ')' => '(',
                    ']' => '[',
                    _ => unreachable!(),
                };
                let Some(open_node) = stacks.entry(opening).or_default().pop() else {
                    let region_id = raw_region_ids[&node.ordinal].clone();
                    exceptions.push(JavaInteractionExceptionOutput {
                        id: stable_id("exception", &[&region_id, "unmatched-closing-delimiter"]),
                        region_id,
                        code: "java.unmatched-closing-delimiter".to_owned(),
                        reason: "Arborium recovery exposed a closing delimiter without a matching opening delimiter".to_owned(),
                        effect: JavaInteractionExceptionEffect::FailsStrictProfile,
                        witness: format!("byte {}", node.start),
                    });
                    continue;
                };
                let open_id = raw_region_ids[&open_node.ordinal].clone();
                let close_id = raw_region_ids[&node.ordinal].clone();
                for (source_id, destination_id, reason) in [
                    (
                        open_id.clone(),
                        close_id.clone(),
                        "Opening delimiter resolves to its closing delimiter",
                    ),
                    (
                        close_id,
                        open_id,
                        "Closing delimiter resolves to its opening delimiter",
                    ),
                ] {
                    outlinks.add(OutlinkSpec {
                        source_region_id: source_id,
                        destination_region_id: destination_id,
                        destination_query: None,
                        relation_kind: "matching-delimiter".to_owned(),
                        reason: reason.to_owned(),
                        confidence: "resolved".to_owned(),
                        completeness: "complete".to_owned(),
                        recommended_projection: "start".to_owned(),
                        action_drafts: Vec::new(),
                    });
                }
            }
            _ => {}
        }
    }
    for (delimiter, nodes) in stacks {
        for node in nodes {
            let region_id = raw_region_ids[&node.ordinal].clone();
            exceptions.push(JavaInteractionExceptionOutput {
                id: stable_id("exception", &[&region_id, "unmatched-opening-delimiter"]),
                region_id,
                code: "java.unmatched-opening-delimiter".to_owned(),
                reason: format!(
                    "Arborium recovery exposed opening delimiter `{delimiter}` without a matching closing delimiter"
                ),
                effect: JavaInteractionExceptionEffect::FailsStrictProfile,
                witness: format!("byte {}", node.start),
            });
        }
    }
}

fn add_declaration_landmark_outlinks(
    _source: &str,
    raw: &[RawSyntaxRegion],
    raw_region_ids: &BTreeMap<usize, String>,
    outlinks: &mut OutlinkBuilder,
) {
    for declaration in raw.iter().filter(|node| is_declaration_kind(&node.kind)) {
        let descendants = raw
            .iter()
            .filter(|candidate| {
                candidate.flags.contains(RawSyntaxFlags::LEAF)
                    && declaration.start <= candidate.start
                    && candidate.end <= declaration.end
                    && candidate.start < candidate.end
            })
            .collect::<Vec<_>>();
        let Some(first) = descendants.first() else {
            continue;
        };
        let Some(last) = descendants.last() else {
            continue;
        };
        let declaration_id = raw_region_ids[&declaration.ordinal].clone();
        for (target, relation, projection, reason) in [
            (
                raw_region_ids[&first.ordinal].clone(),
                "declaration-start",
                "start",
                "First concrete token in declaration",
            ),
            (
                raw_region_ids[&last.ordinal].clone(),
                "declaration-end",
                "end",
                "Last concrete token in declaration",
            ),
        ] {
            outlinks.add(OutlinkSpec {
                source_region_id: declaration_id.clone(),
                destination_region_id: target,
                destination_query: None,
                relation_kind: relation.to_owned(),
                reason: reason.to_owned(),
                confidence: "resolved".to_owned(),
                completeness: "complete".to_owned(),
                recommended_projection: projection.to_owned(),
                action_drafts: Vec::new(),
            });
        }
    }
}

#[expect(
    clippy::too_many_arguments,
    reason = "symbol relation publication keeps all reciprocal outputs explicit"
)]
fn add_symbol_outlinks(
    request: &JavaInteractionMapRequest,
    surface: &Arc<JavaDefinitionResolutionSurface>,
    resolved: &JavaResolvedInteractionDocument,
    workspace: &JavaSourceWorkspace,
    dependency_source_roots: &[DefinitionDependencySourceRoot],
    symbol_regions: &[(String, &JavaSymbolUsageOutput)],
    outlinks: &mut OutlinkBuilder,
    reciprocity: &mut Vec<JavaInteractionReciprocityOutput>,
    exceptions: &mut Vec<JavaInteractionExceptionOutput>,
) {
    for (source_region_id, usage) in symbol_regions {
        let symbols = BTreeSet::from([usage.target.clone()]);
        let mut definitions = surface.definitions_for_symbols(&symbols);
        definitions.extend(
            resolved
                .local_definitions
                .iter()
                .filter(|definition| definition.symbol == usage.target)
                .cloned(),
        );
        definitions.sort();
        definitions.dedup();
        if definitions.is_empty() {
            exceptions.push(JavaInteractionExceptionOutput {
                id: stable_id("exception", &[source_region_id, "unresolved-definition"]),
                region_id: source_region_id.clone(),
                code: "java.unresolved-definition".to_owned(),
                reason: format!(
                    "No exact declaration was resolved for {}",
                    usage.target.canonical_selector()
                ),
                effect: JavaInteractionExceptionEffect::FailsStrictProfile,
                witness: source_span_witness(&usage.span),
            });
            continue;
        }
        for definition in definitions {
            let mut output = definition_at_position_definition(
                &definition,
                workspace,
                &request
                    .as_definition_request()
                    .expect("validated interaction request has a valid origin position"),
            );
            workspace
                .jdk_sources
                .enrich_span(&mut output.identifier_span);
            workspace
                .jdk_sources
                .enrich_span(&mut output.declaration_span);
            enrich_dependency_source_span(&mut output.identifier_span, dependency_source_roots);
            enrich_dependency_source_span(&mut output.declaration_span, dependency_source_roots);
            let destination = definition_region_id(&output.identifier_span, &usage.target);
            let confidence = confidence_name(usage.confidence).to_owned();
            let completeness = if usage.confidence == ResolutionConfidence::Resolved {
                "complete"
            } else {
                "incomplete"
            }
            .to_owned();
            let selector = usage.target.canonical_selector();
            let definition_id = outlinks.add(OutlinkSpec {
                source_region_id: source_region_id.clone(),
                destination_region_id: destination.clone(),
                destination_query: Some(output.identifier_span.address.clone()),
                relation_kind: "definition".to_owned(),
                reason: format!("Resolved {} definition", usage_kind_name(usage.kind)),
                confidence: confidence.clone(),
                completeness: completeness.clone(),
                recommended_projection: "start".to_owned(),
                action_drafts: vec![JavaInteractionActionDraftOutput {
                    action_id: "sfm:symbol/definition/open".to_owned(),
                    arguments: vec![selector.clone()],
                }],
            });
            let reference_id = outlinks.add(OutlinkSpec {
                source_region_id: destination,
                destination_region_id: source_region_id.clone(),
                destination_query: Some(request.document.address.clone()),
                relation_kind: "reference".to_owned(),
                reason: "Inverse reference witnessed by the same resolved static usage".to_owned(),
                confidence,
                completeness,
                recommended_projection: "start".to_owned(),
                action_drafts: vec![JavaInteractionActionDraftOutput {
                    action_id: "sfm:symbol/references/open".to_owned(),
                    arguments: vec![selector],
                }],
            });
            reciprocity.push(JavaInteractionReciprocityOutput {
                definition_outlink_id: definition_id,
                reference_outlink_id: reference_id,
                status: JavaInteractionReciprocityStatus::Verified,
                exception_code: None,
                witness: source_span_witness(&usage.span),
            });
        }
        if definitions_count_for_target(surface, resolved, &usage.target) > 1 {
            exceptions.push(JavaInteractionExceptionOutput {
                id: stable_id("exception", &[source_region_id, "ambiguous-definition"]),
                region_id: source_region_id.clone(),
                code: "java.ambiguous-definition".to_owned(),
                reason: "Several exact declaration candidates remain and must be presented as a constrained choice".to_owned(),
                effect: JavaInteractionExceptionEffect::Informational,
                witness: source_span_witness(&usage.span),
            });
        }
    }
}

fn definitions_count_for_target(
    surface: &Arc<JavaDefinitionResolutionSurface>,
    resolved: &JavaResolvedInteractionDocument,
    target: &JavaSymbolIdentityOutput,
) -> usize {
    let selected = BTreeSet::from([target.clone()]);
    surface
        .definitions_for_symbols(&selected)
        .into_iter()
        .chain(
            resolved
                .local_definitions
                .iter()
                .filter(|definition| definition.symbol == *target)
                .cloned(),
        )
        .collect::<BTreeSet<_>>()
        .len()
}

fn domain(
    id: &str,
    kind: &str,
    coordinate_kinds: &[&str],
    authority: &str,
    snapshot_identity: &str,
) -> JavaInteractionDomainOutput {
    JavaInteractionDomainOutput {
        schema: JAVA_INTERACTION_DOMAIN_SCHEMA.to_owned(),
        id: id.to_owned(),
        kind: kind.to_owned(),
        dimensions: u64::try_from(coordinate_kinds.len()).unwrap_or(u64::MAX),
        coordinate_kinds: coordinate_kinds
            .iter()
            .map(|value| (*value).to_owned())
            .collect(),
        authority: authority.to_owned(),
        snapshot_identity: snapshot_identity.to_owned(),
    }
}

fn projection(
    id: &str,
    from_domain_id: &str,
    to_domain_id: &str,
    loss: &str,
    completeness: &str,
    transform: &str,
    fingerprint: &str,
) -> JavaInteractionProjectionOutput {
    JavaInteractionProjectionOutput {
        schema: JAVA_INTERACTION_PROJECTION_SCHEMA.to_owned(),
        id: id.to_owned(),
        from_domain_id: from_domain_id.to_owned(),
        to_domain_id: to_domain_id.to_owned(),
        loss: loss.to_owned(),
        completeness: completeness.to_owned(),
        transform: transform.to_owned(),
        fingerprint: fingerprint.to_owned(),
        authority: "sfm:rust_java_semantics".to_owned(),
    }
}

fn region(
    id: String,
    domain_id: &str,
    start: usize,
    end: usize,
    semantic_kind: String,
    provenance: &str,
    projection_ids: &[String],
) -> JavaInteractionRegionOutput {
    JavaInteractionRegionOutput {
        schema: JAVA_INTERACTION_REGION_SCHEMA.to_owned(),
        id,
        domain_id: domain_id.to_owned(),
        representation: "interval".to_owned(),
        bounds: vec![JavaInteractionRegionAxisOutput {
            start_inclusive: u64::try_from(start).unwrap_or(u64::MAX),
            end_exclusive: u64::try_from(end).unwrap_or(u64::MAX),
        }],
        edge_policy: "half-open".to_owned(),
        semantic_kind,
        provenance: provenance.to_owned(),
        projection_ids: projection_ids.to_vec(),
    }
}

fn classified_runs(source: &str, start: usize, end: usize) -> Vec<(usize, usize, bool)> {
    let Some(text) = source.get(start..end) else {
        return Vec::new();
    };
    let mut output = Vec::new();
    let mut run_start = start;
    let mut run_whitespace = None;
    for (relative, character) in text.char_indices() {
        let whitespace = character.is_whitespace();
        if let Some(current) = run_whitespace
            && current != whitespace
        {
            output.push((run_start, start + relative, current));
            run_start = start + relative;
        }
        run_whitespace = Some(whitespace);
    }
    if let Some(whitespace) = run_whitespace {
        output.push((run_start, end, whitespace));
    }
    output
}

fn smallest_containing_raw_region(
    start: usize,
    end: usize,
    raw: &[RawSyntaxRegion],
    raw_region_ids: &BTreeMap<usize, String>,
) -> Option<String> {
    raw.iter()
        .filter(|node| {
            node.start <= start && end <= node.end && (node.start < start || end < node.end)
        })
        .min_by_key(|node| (node.end.saturating_sub(node.start), node.ordinal))
        .and_then(|node| raw_region_ids.get(&node.ordinal))
        .cloned()
}

fn enclosing_annotation_region_id(
    start: usize,
    end: usize,
    raw: &[RawSyntaxRegion],
    raw_region_ids: &BTreeMap<usize, String>,
) -> Option<String> {
    raw.iter()
        .filter(|node| {
            matches!(node.kind.as_str(), "marker_annotation" | "annotation")
                && node.start <= start
                && end <= node.end
                && (node.start < start || end < node.end)
        })
        .min_by_key(|node| (node.end.saturating_sub(node.start), node.ordinal))
        .and_then(|node| raw_region_ids.get(&node.ordinal))
        .cloned()
}

fn containing_region_id(
    target: &JavaInteractionRegionOutput,
    regions: &[JavaInteractionRegionOutput],
) -> Option<String> {
    regions
        .iter()
        .filter(|candidate| {
            candidate.id != target.id
                && candidate.start_byte() <= target.start_byte()
                && target.end_byte() <= candidate.end_byte()
                && (candidate.start_byte() < target.start_byte()
                    || target.end_byte() < candidate.end_byte())
        })
        .min_by_key(|candidate| {
            (
                candidate.end_byte().saturating_sub(candidate.start_byte()),
                candidate.id.as_str(),
            )
        })
        .map(|region| region.id.clone())
}

fn region_text<'source>(
    source: &'source str,
    region: &JavaInteractionRegionOutput,
) -> Option<&'source str> {
    let start = usize::try_from(region.start_byte()).ok()?;
    let end = usize::try_from(region.end_byte()).ok()?;
    source.get(start..end)
}

fn definition_region_id(
    span: &DefinitionSourceSpanOutput,
    symbol: &JavaSymbolIdentityOutput,
) -> String {
    stable_id(
        "java-syntax",
        &[
            &span.resolver_id,
            &span.root_id,
            &span.root_relative_path,
            &span.source_hash,
            &span.start_byte.to_string(),
            &span.end_byte.to_string(),
            &symbol.canonical_selector(),
        ],
    )
}

fn enrich_dependency_source_span(
    span: &mut DefinitionSourceSpanOutput,
    roots: &[DefinitionDependencySourceRoot],
) {
    if span.resolver_id != "dependency-index" {
        return;
    }
    let mut matches = Vec::new();
    for root in roots {
        if root.source_set != span.source_set {
            continue;
        }
        let prefix = root.report_prefix.trim_end_matches('/');
        let Some(relative) = span
            .report_path
            .strip_prefix(prefix)
            .and_then(|tail| tail.strip_prefix('/'))
        else {
            continue;
        };
        if relative.is_empty() || relative.split('/').any(str::is_empty) {
            continue;
        }
        let mut native = root.canonical_absolute_path.clone();
        for segment in relative.split('/') {
            native.push(segment);
        }
        let Ok(source) = std::fs::read_to_string(native) else {
            continue;
        };
        if blake3_content_hash(&source) != span.source_hash {
            continue;
        }
        matches.push((root, relative.to_owned(), source));
    }
    let [(root, relative, source)] = matches.as_slice() else {
        return;
    };
    "dependency-source".clone_into(&mut span.resolver_id);
    span.root_id.clone_from(&root.root_id);
    span.root_relative_path.clone_from(relative);
    span.address = contributed_address("dependency-source", &root.root_id, relative);
    span.source_sha256 = Some(sha256_content_hash(source));
}

fn source_span_witness(span: &JavaSourceSpanOutput) -> String {
    format!(
        "{}:{}:{}..{}:{}",
        span.path, span.start_line, span.start_column, span.end_line, span.end_column
    )
}

fn usage_kind_name(kind: JavaUsageKind) -> &'static str {
    match kind {
        JavaUsageKind::Declaration => "declaration",
        JavaUsageKind::Import => "import",
        JavaUsageKind::TypeReference => "type-reference",
        JavaUsageKind::FieldReference => "field-reference",
        JavaUsageKind::Invocation => "invocation",
        JavaUsageKind::MethodReference => "method-reference",
        JavaUsageKind::LocalReference => "local-reference",
    }
}

fn confidence_name(confidence: ResolutionConfidence) -> &'static str {
    match confidence {
        ResolutionConfidence::Resolved => "resolved",
        ResolutionConfidence::PartiallyResolved => "partially-resolved",
        ResolutionConfidence::Unresolved => "recovery",
    }
}

fn is_declaration_kind(kind: &str) -> bool {
    matches!(
        kind,
        "class_declaration"
            | "interface_declaration"
            | "enum_declaration"
            | "record_declaration"
            | "annotation_type_declaration"
            | "field_declaration"
            | "constant_declaration"
            | "enum_constant"
            | "local_variable_declaration"
            | "method_declaration"
            | "constructor_declaration"
            | "compact_constructor_declaration"
            | "formal_parameter"
            | "spread_parameter"
            | "receiver_parameter"
    )
}

fn is_delimiter(text: &str) -> bool {
    matches!(text, "{" | "}" | "(" | ")" | "[" | "]")
}

fn is_operator(text: &str) -> bool {
    matches!(
        text,
        "+" | "-"
            | "*"
            | "/"
            | "%"
            | "="
            | "=="
            | "!="
            | "<"
            | ">"
            | "<="
            | ">="
            | "&&"
            | "||"
            | "!"
            | "&"
            | "|"
            | "^"
            | "~"
            | "<<"
            | ">>"
            | ">>>"
            | "++"
            | "--"
            | "+="
            | "-="
            | "*="
            | "/="
            | "%="
            | "&="
            | "|="
            | "^="
            | "<<="
            | ">>="
            | ">>>="
            | "->"
            | "::"
            | "?"
            | ":"
    )
}

fn is_punctuation(text: &str) -> bool {
    matches!(text, ";" | "," | "." | "@" | "..." | "_")
}

fn stable_id(prefix: &str, parts: &[&str]) -> String {
    let mut hasher = blake3::Hasher::new();
    for part in parts {
        hasher.update(&u64::try_from(part.len()).unwrap_or(u64::MAX).to_le_bytes());
        hasher.update(part.as_bytes());
    }
    let hash = hasher.finalize();
    format!("{prefix}:{}", &hash.to_hex()[..24])
}

fn short_fingerprint(fingerprint: &str) -> String {
    fingerprint
        .strip_prefix("blake3:")
        .or_else(|| fingerprint.strip_prefix("sha256:"))
        .unwrap_or(fingerprint)
        .chars()
        .take(16)
        .collect()
}

fn validate_blake3_fingerprint(value: &str) -> eyre::Result<()> {
    let Some(hex) = value.strip_prefix("blake3:") else {
        eyre::bail!("semantic fingerprint must use the blake3 algorithm");
    };
    if hex.len() != 64 || !hex.bytes().all(|byte| byte.is_ascii_hexdigit()) {
        eyre::bail!("semantic fingerprint must contain exactly 64 hexadecimal digits");
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::cancellation::CancellationToken;
    use crate::java_analysis::DefinitionAtPositionEngine;
    use crate::java_analysis::DefinitionAtPositionEngineLimits;
    use crate::java_analysis::JavaAnalysisContextOutput;
    use crate::java_analysis::JavaClasspathMode;
    use crate::java_analysis::JavaSourceFile;
    use crate::java_analysis::JavaSourceRootAuthority;
    use crate::java_analysis::JavaSourceRootKind;
    use crate::java_analysis::JavaSourceRootOutput;
    use crate::java_analysis::JavaSourceSetOutput;
    use crate::java_analysis::JdkSourceDomainState;
    use crate::java_analysis::SymbolServerWorkspaceOutput;
    use crate::java_analysis::UsageAtPositionRequest;
    use crate::java_analysis::definition_workspace_fingerprint;
    use std::path::PathBuf;

    fn semantic_matrix_fixture() -> (
        tempfile::TempDir,
        JavaSourceWorkspace,
        JavaInteractionMapRequest,
    ) {
        let source_root = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
            .join("tests")
            .join("java_analysis")
            .join("interaction_map_scenarios")
            .join("semantic_matrix")
            .join("source");
        let canonical_root = dunce::canonicalize(&source_root).expect("semantic matrix root");
        let relative_paths = [
            "ca/teamdman/sfm/LexerAdapter.java",
            "ca/teamdman/sfm/LocalizationEntry.java",
            "ca/teamdman/sfm/Mod.java",
        ];
        let files = relative_paths
            .into_iter()
            .map(|relative| JavaSourceFile {
                absolute_path: canonical_root
                    .join(relative.replace('/', std::path::MAIN_SEPARATOR_STR)),
                root_id: "semantic-matrix".to_owned(),
                root_relative_path: relative.to_owned(),
                report_path: format!("scenario/{relative}"),
                source_set: "main".to_owned(),
                source_override: None,
            })
            .collect::<Vec<_>>();
        let jdk_directory = tempfile::tempdir().expect("fixture JDK source tree");
        for (relative, source) in [
            (
                "java.base/java/lang/String.java",
                "package java.lang; public final class String {}\n",
            ),
            (
                "java.base/java/io/Serializable.java",
                "package java.io; public interface Serializable {}\n",
            ),
            (
                "java.base/java/io/Serial.java",
                "package java.io; public @interface Serial {}\n",
            ),
            (
                "java.base/java/io/IOException.java",
                "package java.io; public class IOException {}\n",
            ),
            (
                "java.base/java/lang/StringBuilder.java",
                concat!(
                    "package java.lang;\n",
                    "import java.io.IOException;\n",
                    "public final class StringBuilder { IOException value; }\n",
                ),
            ),
        ] {
            let path = jdk_directory
                .path()
                .join(relative.replace('/', std::path::MAIN_SEPARATOR_STR));
            std::fs::create_dir_all(path.parent().expect("JDK source parent"))
                .expect("JDK source parent");
            std::fs::write(path, source).expect("JDK source fixture");
        }
        let mut context = JavaAnalysisContextOutput {
            branch: "1.19.2".to_owned(),
            minecraft_version: "1.19.2".to_owned(),
            java_release: "17".to_owned(),
            jdk: "fixture".to_owned(),
            source_roots: vec![JavaSourceRootOutput {
                id: "semantic-matrix".to_owned(),
                source_set: "main".to_owned(),
                path: "scenario".to_owned(),
                kind: JavaSourceRootKind::Custom,
                exists: true,
            }],
            source_sets: vec![JavaSourceSetOutput {
                id: "main".to_owned(),
                visible_source_sets: vec!["main".to_owned()],
            }],
            source_exclusions: Vec::new(),
            classpath_mode: JavaClasspathMode::Isolated,
            classpath_fingerprint: format!("blake3:{}", "3".repeat(64)),
            parser_fingerprint: "arborium-java/2.18.1".to_owned(),
            index_fingerprint: format!("blake3:{}", "4".repeat(64)),
        };
        let jdk_sources = JdkSourceDomainState::ready_from_tree("17", jdk_directory.path())
            .expect("fixture JDK source domain");
        jdk_sources.apply_to_context(&mut context);
        let workspace_fingerprint =
            definition_workspace_fingerprint(&context, None).expect("workspace fingerprint");
        let workspace_identity = DefinitionWorkspaceIdentityInput {
            branch: context.branch.clone(),
            classpath_mode: context.classpath_mode,
            source_roots: context.source_roots.clone(),
            classpath_fingerprint: context.classpath_fingerprint.clone(),
            dependency_index_identity: None,
            workspace_fingerprint,
            workspace_generation: 12,
        };
        let document_file = files
            .iter()
            .find(|file| file.root_relative_path.ends_with("LexerAdapter.java"))
            .expect("LexerAdapter fixture");
        let text = std::fs::read_to_string(&document_file.absolute_path)
            .expect("LexerAdapter fixture text");
        let request = JavaInteractionMapRequest::new(
            41,
            7,
            workspace_identity,
            DefinitionDocumentInput {
                address: contributed_address(
                    "workspace",
                    &document_file.root_id,
                    &document_file.root_relative_path,
                ),
                root_id: document_file.root_id.clone(),
                root_relative_path: document_file.root_relative_path.clone(),
                report_path: document_file.report_path.clone(),
                source_set: document_file.source_set.clone(),
                text: text.clone(),
                content_hash: sha256_content_hash(&text),
                disk_content_hash: Some(blake3_content_hash(&text)),
            },
        );
        let workspace = JavaSourceWorkspace {
            context,
            root_authorities: vec![JavaSourceRootAuthority {
                root_id: "semantic-matrix".to_owned(),
                source_set: "main".to_owned(),
                canonical_absolute_path: canonical_root,
                report_root_path: "scenario".to_owned(),
            }],
            files,
            diagnostics: Vec::new(),
            classpath_entries: Vec::new(),
            jdk_sources,
        };
        (jdk_directory, workspace, request)
    }

    fn fixture() -> (
        tempfile::TempDir,
        JavaSourceWorkspace,
        JavaInteractionMapRequest,
    ) {
        let directory = tempfile::tempdir().expect("temp directory");
        let root = directory.path().join("source");
        std::fs::create_dir_all(root.join("example")).expect("source directory");
        let source = concat!(
            "package example;\r\n",
            "import java.util.List;\r\n",
            "@Deprecated public final class SpatialFixture {\r\n",
            "  private final int café = 7;\r\n",
            "  String render(List<String> values) {\r\n",
            "    String local = \"wide: 界\"; // comment\r\n",
            "    if (values.isEmpty() || café > 0) { return local + values.get(0); }\r\n",
            "    return \"none\";\r\n",
            "  }\r\n",
            "  static final class Nested {}\r\n",
            "}\r\n",
        );
        let path = root.join("example").join("SpatialFixture.java");
        std::fs::write(&path, source).expect("fixture source");
        let canonical_root = dunce::canonicalize(&root).expect("canonical root");
        let canonical_path = dunce::canonicalize(&path).expect("canonical path");
        let context = JavaAnalysisContextOutput {
            branch: "1.19.2".to_owned(),
            minecraft_version: "1.19.2".to_owned(),
            java_release: "17".to_owned(),
            jdk: "fixture".to_owned(),
            source_roots: vec![JavaSourceRootOutput {
                id: "source".to_owned(),
                source_set: "main".to_owned(),
                path: "source".to_owned(),
                kind: JavaSourceRootKind::Custom,
                exists: true,
            }],
            source_sets: vec![JavaSourceSetOutput {
                id: "main".to_owned(),
                visible_source_sets: vec!["main".to_owned()],
            }],
            source_exclusions: Vec::new(),
            classpath_mode: JavaClasspathMode::Isolated,
            classpath_fingerprint: format!("blake3:{}", "1".repeat(64)),
            parser_fingerprint: "arborium-java/2.18.1".to_owned(),
            index_fingerprint: format!("blake3:{}", "2".repeat(64)),
        };
        let workspace_fingerprint =
            definition_workspace_fingerprint(&context, None).expect("workspace fingerprint");
        let workspace_identity = DefinitionWorkspaceIdentityInput {
            branch: context.branch.clone(),
            classpath_mode: context.classpath_mode,
            source_roots: context.source_roots.clone(),
            classpath_fingerprint: context.classpath_fingerprint.clone(),
            dependency_index_identity: None,
            workspace_fingerprint,
            workspace_generation: 3,
        };
        let workspace = JavaSourceWorkspace {
            context,
            root_authorities: vec![JavaSourceRootAuthority {
                root_id: "source".to_owned(),
                source_set: "main".to_owned(),
                canonical_absolute_path: canonical_root,
                report_root_path: "source".to_owned(),
            }],
            files: vec![JavaSourceFile {
                absolute_path: canonical_path,
                root_id: "source".to_owned(),
                root_relative_path: "example/SpatialFixture.java".to_owned(),
                report_path: "source/example/SpatialFixture.java".to_owned(),
                source_set: "main".to_owned(),
                source_override: None,
            }],
            diagnostics: Vec::new(),
            classpath_entries: Vec::new(),
            jdk_sources: JdkSourceDomainState::Disabled,
        };
        let request = JavaInteractionMapRequest::new(
            1,
            9,
            workspace_identity,
            DefinitionDocumentInput {
                address: "workspace://source/example/SpatialFixture.java".to_owned(),
                root_id: "source".to_owned(),
                root_relative_path: "example/SpatialFixture.java".to_owned(),
                report_path: "source/example/SpatialFixture.java".to_owned(),
                source_set: "main".to_owned(),
                text: source.to_owned(),
                content_hash: blake3_content_hash(source),
                disk_content_hash: None,
            },
        );
        (directory, workspace, request)
    }

    #[test]
    fn request_is_bounded_and_generation_tagged() {
        let (_directory, _workspace, request) = fixture();
        request.validate().expect("valid request");
        let mut invalid = request.clone();
        invalid.window.max_regions = JAVA_INTERACTION_MAP_MAX_REGIONS + 1;
        assert!(invalid.validate().is_err());
        invalid = request.with_known_semantic_fingerprint(format!("blake3:{}", "a".repeat(64)));
        invalid.validate().expect("valid known fingerprint");
    }

    #[test]
    fn largest_fitting_prefix_is_exact_and_logarithmic() {
        let mut probes = 0_usize;
        let selected = largest_fitting_prefix(16_383, |count| {
            probes += 1;
            Ok(count <= 3_289)
        })
        .expect("prefix search");
        assert_eq!(selected, Some(3_289));
        assert!(probes <= 15, "binary prefix search used {probes} probes");

        assert_eq!(
            largest_fitting_prefix(32, |_| Ok(false)).expect("no fitting prefix"),
            None
        );
        assert_eq!(
            largest_fitting_prefix(32, |_| Ok(true)).expect("all prefixes fit"),
            Some(32)
        );
    }

    #[test]
    fn encoded_budget_preserves_document_regions_before_inventory() {
        let (_jdk_directory, workspace, mut request) = semantic_matrix_fixture();
        let engine = DefinitionAtPositionEngine::new(
            workspace,
            None,
            None,
            DefinitionAtPositionEngineLimits::default(),
        )
        .expect("semantic matrix engine");

        request.window.max_inventory_files = 1;
        let one_inventory = engine
            .analyze_interaction_map(&request, &CancellationToken::new())
            .expect("one-inventory interaction map");
        assert!(one_inventory.page.next_region_offset.is_none());
        assert!(one_inventory.page.next_inventory_offset.is_some());

        request.window.max_inventory_files = JAVA_INTERACTION_MAP_DEFAULT_MAX_INVENTORY_FILES;
        request.window.max_encoded_bytes = one_inventory.page.encoded_bytes;
        let bounded = engine
            .analyze_interaction_map(&request, &CancellationToken::new())
            .expect("inventory-bounded interaction map");
        assert_eq!(bounded.regions, one_inventory.regions);
        assert!(bounded.page.next_region_offset.is_none());
        assert_eq!(bounded.files.len(), 1);
        assert!(bounded.page.next_inventory_offset.is_some());
    }

    #[test]
    fn semantic_map_classifies_fixture_syntax_and_reciprocal_symbols() {
        let (_directory, workspace, request) = fixture();
        let engine = DefinitionAtPositionEngine::new(
            workspace,
            None,
            None,
            DefinitionAtPositionEngineLimits::default(),
        )
        .expect("engine");
        let output = engine
            .analyze_interaction_map_with_telemetry(&request, &CancellationToken::new())
            .expect("interaction map");
        let result = output.result;
        assert_eq!(result.schema, JAVA_INTERACTION_MAP_SCHEMA);
        assert_eq!(result.outcome, JavaInteractionMapOutcome::Success);
        let kinds = result
            .regions
            .iter()
            .map(|region| region.semantic_kind.as_str())
            .collect::<BTreeSet<_>>();
        for required in [
            "java-import",
            "java-annotation",
            "java-modifiers",
            "java-class-declaration",
            "java-field-declaration",
            "java-method-declaration",
            "java-parameter-declaration",
            "java-signature",
            "java-body",
            "java-delimiter",
            "java-statement",
            "java-punctuation",
            "java-operator",
            "java-literal",
            "java-comment",
            "java-qualified-name",
        ] {
            assert!(kinds.contains(required), "missing semantic kind {required}");
        }
        assert!(
            result
                .outlinks
                .iter()
                .any(|link| link.relation_kind == "matching-delimiter")
        );
        for relation in [
            "containing-region",
            "child-region",
            "signature",
            "body",
            "statement",
            "path",
        ] {
            assert!(
                result
                    .outlinks
                    .iter()
                    .any(|link| link.relation_kind == relation),
                "missing structural relation {relation}"
            );
        }
        assert!(
            result
                .outlinks
                .iter()
                .any(|link| link.relation_kind == "definition")
        );
        assert!(!result.reciprocity.is_empty());
        assert!(
            result
                .reciprocity
                .iter()
                .all(|edge| edge.status == JavaInteractionReciprocityStatus::Verified)
        );
        assert!(
            result
                .classifications
                .iter()
                .all(|classification| classification.status
                    != JavaInteractionClassificationStatus::Unclassified)
        );
        assert!(result.page.encoded_bytes <= JAVA_INTERACTION_MAP_DEFAULT_MAX_ENCODED_BYTES);
    }

    #[test]
    fn semantic_map_accepts_an_addressed_jdk_document_without_eager_workspace_admission() {
        let (_jdk_directory, workspace, base_request) = semantic_matrix_fixture();
        let jdk_root = workspace
            .context
            .source_roots
            .iter()
            .find(|root| root.kind == JavaSourceRootKind::Jdk && root.exists)
            .expect("managed JDK source root");
        let relative = "java.base/java/lang/String.java";
        let file = workspace
            .jdk_sources
            .addressed_source_file(&jdk_root.id, relative)
            .expect("addressed JDK source lookup")
            .expect("addressed JDK source");
        let served = SymbolServerWorkspaceOutput::from_workspace(&workspace, None, 12)
            .expect("served workspace with managed JDK root");
        let served_root = served
            .managed_source_roots
            .iter()
            .find(|root| root.root_id == file.root_id)
            .expect("served managed JDK root");
        assert_eq!(
            served_root.report_prefix.as_deref(),
            file.report_path.strip_suffix(&format!("/{relative}"))
        );
        assert!(
            workspace.files.iter().all(|workspace_file| {
                workspace_file.root_id != file.root_id
                    || workspace_file.root_relative_path != file.root_relative_path
            }),
            "managed JDK source must remain outside the eager workspace inventory"
        );
        let text = std::fs::read_to_string(&file.absolute_path).expect("JDK source text");
        let request = JavaInteractionMapRequest::new(
            42,
            8,
            base_request.workspace,
            DefinitionDocumentInput {
                address: contributed_address("jdk-source", &file.root_id, relative),
                root_id: file.root_id.clone(),
                root_relative_path: relative.to_owned(),
                report_path: file.report_path.clone(),
                source_set: file.source_set.clone(),
                text: text.clone(),
                content_hash: blake3_content_hash(&text),
                disk_content_hash: Some(blake3_content_hash(&text)),
            },
        );
        let engine = DefinitionAtPositionEngine::new(
            workspace,
            None,
            None,
            DefinitionAtPositionEngineLimits::default(),
        )
        .expect("semantic matrix engine");

        let result = engine
            .analyze_interaction_map(&request, &CancellationToken::new())
            .expect("JDK interaction map");

        assert_eq!(result.outcome, JavaInteractionMapOutcome::Success);
        assert_eq!(result.document.root_id, file.root_id);
        assert_eq!(result.document.root_relative_path, relative);
        assert!(
            result
                .regions
                .iter()
                .any(|region| region.semantic_kind == "java-class-declaration")
        );
        assert!(
            result
                .outlinks
                .iter()
                .any(|outlink| outlink.relation_kind == "definition")
        );

        let string_offset = text.find("String").expect("String declaration");
        let string_column = text[..string_offset].chars().count() + 1;
        let definition_request = DefinitionAtPositionRequest::new(
            43,
            9,
            request.workspace.clone(),
            request.document.clone(),
            DefinitionTextPositionInput::from_line_column(
                &text,
                1,
                string_column.try_into().expect("String column"),
            )
            .expect("String cursor"),
        );
        let definition = engine
            .analyze(&definition_request, &CancellationToken::new())
            .expect("JDK definition lookup");
        assert_eq!(
            definition.outcome,
            crate::java_analysis::DefinitionAtPositionOutcome::Success
        );
        let references = engine
            .analyze_usages(
                &UsageAtPositionRequest::new(
                    44,
                    10,
                    definition_request.workspace,
                    definition_request.document,
                    definition_request.position,
                ),
                &CancellationToken::new(),
            )
            .expect("JDK reference lookup");
        assert_eq!(
            references.outcome,
            crate::java_analysis::UsageAtPositionOutcome::Success
        );
    }

    #[test]
    fn addressed_jdk_document_seeds_its_own_unseen_import_definitions() {
        let (_jdk_directory, workspace, base_request) = semantic_matrix_fixture();
        let jdk_root = workspace
            .context
            .source_roots
            .iter()
            .find(|root| root.kind == JavaSourceRootKind::Jdk && root.exists)
            .expect("managed JDK source root");
        let relative = "java.base/java/lang/StringBuilder.java";
        let file = workspace
            .jdk_sources
            .addressed_source_file(&jdk_root.id, relative)
            .expect("addressed JDK source lookup")
            .expect("addressed JDK source");
        let text = std::fs::read_to_string(&file.absolute_path).expect("JDK source text");
        let request = JavaInteractionMapRequest::new(
            45,
            11,
            base_request.workspace,
            DefinitionDocumentInput {
                address: contributed_address("jdk-source", &file.root_id, relative),
                root_id: file.root_id.clone(),
                root_relative_path: relative.to_owned(),
                report_path: file.report_path.clone(),
                source_set: file.source_set.clone(),
                text: text.clone(),
                content_hash: blake3_content_hash(&text),
                disk_content_hash: Some(blake3_content_hash(&text)),
            },
        );
        let engine = DefinitionAtPositionEngine::new(
            workspace,
            None,
            None,
            DefinitionAtPositionEngineLimits::default(),
        )
        .expect("semantic matrix engine");

        let result = engine
            .analyze_interaction_map(&request, &CancellationToken::new())
            .expect("JDK interaction map");

        assert_eq!(result.outcome, JavaInteractionMapOutcome::Success);
        let import = result
            .regions
            .iter()
            .find(|region| region_text(&text, region) == Some("java.io.IOException"))
            .expect("full qualified import region");
        assert!(result.outlinks.iter().any(|outlink| {
            outlink.source_region_id == import.id && outlink.relation_kind == "definition"
        }));
        assert!(result.files.iter().any(|candidate| {
            candidate.resolver_id == "jdk-source"
                && candidate.root_relative_path == "java.base/java/io/IOException.java"
                && candidate.state == JavaInteractionFileState::Covered
        }));
        assert!(result.files.iter().any(|candidate| {
            candidate.resolver_id == "jdk-source"
                && candidate.root_id == file.root_id
                && candidate.root_relative_path == relative
                && candidate.state == JavaInteractionFileState::Covered
        }));
    }

    #[test]
    fn semantic_matrix_has_strict_glyph_routes_exact_regressions_and_reciprocal_edges() {
        let (_jdk_directory, workspace, request) = semantic_matrix_fixture();
        let source = request.document.text.clone();
        let engine = DefinitionAtPositionEngine::new(
            workspace,
            None,
            None,
            DefinitionAtPositionEngineLimits::default(),
        )
        .expect("semantic matrix engine");
        let result = engine
            .analyze_interaction_map(&request, &CancellationToken::new())
            .expect("semantic matrix interaction map");
        assert_eq!(result.outcome, JavaInteractionMapOutcome::Success);
        assert!(result.page.next_region_offset.is_none());
        assert!(result.page.next_inventory_offset.is_none());

        let classifications = result
            .classifications
            .iter()
            .map(|classification| (classification.region_id.as_str(), classification))
            .collect::<BTreeMap<_, _>>();
        let exceptions = result
            .exceptions
            .iter()
            .map(|exception| exception.region_id.as_str())
            .collect::<BTreeSet<_>>();
        for (byte_offset, character) in source.char_indices() {
            if character.is_whitespace() {
                continue;
            }
            let witnesses = result
                .regions
                .iter()
                .filter(|region| {
                    region.start_byte() <= byte_offset as u64
                        && (byte_offset as u64) < region.end_byte()
                })
                .collect::<Vec<_>>();
            assert!(
                !witnesses.is_empty(),
                "non-whitespace glyph at byte {byte_offset} has no semantic region"
            );
            assert!(
                witnesses.iter().any(|region| {
                    classifications
                        .get(region.id.as_str())
                        .is_some_and(|classification| {
                            classification.status == JavaInteractionClassificationStatus::Actionable
                                && !classification.navigation_outlink_ids.is_empty()
                        })
                        || exceptions.contains(region.id.as_str())
                }),
                "non-whitespace glyph at byte {byte_offset} has neither navigation nor a typed exception"
            );
        }

        let region_has_definition = |needle: &str| {
            result.regions.iter().any(|region| {
                region_text(&source, region).is_some_and(|text| text == needle)
                    && result.outlinks.iter().any(|outlink| {
                        outlink.source_region_id == region.id
                            && outlink.relation_kind == "definition"
                    })
            })
        };
        for regression in [
            "LexerAdapter",
            "Mod",
            "LocalizationEntry",
            "String",
            "java.io.Serializable",
            "Serial",
            "serialVersionUID",
        ] {
            assert!(
                region_has_definition(regression),
                "exact regression `{regression}` has no definition outlink"
            );
        }

        let outlinks = result
            .outlinks
            .iter()
            .map(|outlink| (outlink.id.as_str(), outlink))
            .collect::<BTreeMap<_, _>>();
        assert!(!result.reciprocity.is_empty());
        for evidence in &result.reciprocity {
            let definition = outlinks
                .get(evidence.definition_outlink_id.as_str())
                .expect("definition reciprocity outlink");
            let reference = outlinks
                .get(evidence.reference_outlink_id.as_str())
                .expect("reference reciprocity outlink");
            assert_eq!(definition.relation_kind, "definition");
            assert_eq!(reference.relation_kind, "reference");
            assert_eq!(definition.source_region_id, reference.destination_region_id);
            assert_eq!(definition.destination_region_id, reference.source_region_id);
            assert_eq!(evidence.status, JavaInteractionReciprocityStatus::Verified);
        }

        for relative in [
            "java.base/java/lang/String.java",
            "java.base/java/io/Serializable.java",
            "java.base/java/io/Serial.java",
        ] {
            assert!(result.files.iter().any(|file| {
                file.resolver_id == "jdk-source"
                    && file.root_relative_path == relative
                    && file.state == JavaInteractionFileState::Covered
            }));
        }
        assert!(
            result
                .files
                .iter()
                .filter(|file| file.resolver_id == "workspace")
                .count()
                >= 3
        );
    }

    #[test]
    fn semantic_map_pages_regions_and_inventory_without_identity_drift() {
        let (_directory, workspace, mut request) = fixture();
        request.window.max_regions = 8;
        request.window.max_inventory_files = 1;
        let engine = DefinitionAtPositionEngine::new(
            workspace,
            None,
            None,
            DefinitionAtPositionEngineLimits::default(),
        )
        .expect("engine");
        let first = engine
            .analyze_interaction_map_with_telemetry(&request, &CancellationToken::new())
            .expect("first page")
            .result;
        assert_eq!(first.regions.len(), 8);
        let next = first.page.next_region_offset.expect("next region page");
        request.window.region_offset = next;
        let second = engine
            .analyze_interaction_map_with_telemetry(&request, &CancellationToken::new())
            .expect("second page")
            .result;
        assert_eq!(first.semantic_fingerprint, second.semantic_fingerprint);
        assert_eq!(first.semantic_generation, second.semantic_generation);
        assert!(
            first
                .regions
                .iter()
                .all(|left| second.regions.iter().all(|right| left.id != right.id))
        );
    }

    #[test]
    fn semantic_map_not_modified_and_encoded_budget_are_incremental_and_bounded() {
        let (_directory, workspace, mut request) = fixture();
        let engine = DefinitionAtPositionEngine::new(
            workspace,
            None,
            None,
            DefinitionAtPositionEngineLimits::default(),
        )
        .expect("engine");
        let full = engine
            .analyze_interaction_map(&request, &CancellationToken::new())
            .expect("full interaction map");
        let budget = full.page.encoded_bytes.saturating_div(2).max(4_096);
        assert!(budget < full.page.encoded_bytes);
        request.window.max_encoded_bytes = budget;
        let bounded = engine
            .analyze_interaction_map(&request, &CancellationToken::new())
            .expect("bounded interaction map");
        assert!(bounded.page.encoded_bytes <= budget);
        assert!(!bounded.regions.is_empty());
        assert!(!bounded.files.is_empty());
        assert!(
            bounded
                .page
                .next_region_offset
                .is_none_or(|next| next > bounded.page.region_offset)
        );
        assert!(
            bounded
                .page
                .next_inventory_offset
                .is_none_or(|next| next > bounded.page.inventory_offset)
        );
        assert!(
            bounded.page.next_region_offset.is_some()
                || bounded.page.next_inventory_offset.is_some()
        );
        assert_eq!(bounded.semantic_fingerprint, full.semantic_fingerprint);
        assert_eq!(bounded.semantic_generation, full.semantic_generation);

        request.known_semantic_fingerprint = Some(full.semantic_fingerprint.clone());
        let unchanged = engine
            .analyze_interaction_map(&request, &CancellationToken::new())
            .expect("not-modified interaction map");
        assert_eq!(unchanged.outcome, JavaInteractionMapOutcome::NotModified);
        assert_eq!(unchanged.semantic_fingerprint, full.semantic_fingerprint);
        assert_eq!(unchanged.semantic_generation, full.semantic_generation);
        assert!(unchanged.regions.is_empty());
        assert!(unchanged.outlinks.is_empty());
    }

    #[test]
    fn non_document_source_change_invalidates_the_semantic_fingerprint() {
        let (_directory, mut workspace, mut request) = fixture();
        let helper_path = workspace.files[0]
            .absolute_path
            .parent()
            .expect("fixture package directory")
            .join("Helper.java");
        std::fs::write(&helper_path, "package example; final class Helper {}\n")
            .expect("helper fixture source");
        workspace.files.push(JavaSourceFile {
            absolute_path: dunce::canonicalize(&helper_path).expect("canonical helper path"),
            root_id: "source".to_owned(),
            root_relative_path: "example/Helper.java".to_owned(),
            report_path: "source/example/Helper.java".to_owned(),
            source_set: "main".to_owned(),
            source_override: None,
        });
        let engine = DefinitionAtPositionEngine::new(
            workspace,
            None,
            None,
            DefinitionAtPositionEngineLimits::default(),
        )
        .expect("engine");
        let first = engine
            .analyze_interaction_map(&request, &CancellationToken::new())
            .expect("first interaction map");
        request.known_semantic_fingerprint = Some(first.semantic_fingerprint.clone());

        std::fs::write(
            &helper_path,
            "package example; final class Helper { int changed; }\n",
        )
        .expect("changed helper fixture source");
        let changed = engine
            .analyze_interaction_map(&request, &CancellationToken::new())
            .expect("changed interaction map");
        assert_eq!(changed.outcome, JavaInteractionMapOutcome::Success);
        assert_ne!(changed.semantic_fingerprint, first.semantic_fingerprint);
        assert_ne!(changed.semantic_generation, first.semantic_generation);
    }

    #[test]
    fn inventory_covers_every_selected_source_set_and_missing_root() {
        let (_directory, mut workspace, mut request) = fixture();
        let fixture_root = workspace.root_authorities[0]
            .canonical_absolute_path
            .parent()
            .expect("fixture directory")
            .to_path_buf();
        for (root_id, source_set, root_name, file_name) in [
            (
                "test-source",
                "test",
                "test-source",
                "SpatialFixtureTest.java",
            ),
            (
                "gametest-source",
                "gametest",
                "gametest-source",
                "SpatialFixtureGameTest.java",
            ),
        ] {
            let root = fixture_root.join(root_name);
            let package = root.join("example");
            std::fs::create_dir_all(&package).expect("source-set package directory");
            let path = package.join(file_name);
            std::fs::write(
                &path,
                format!(
                    "package example; final class {} {{}}\n",
                    file_name.trim_end_matches(".java")
                ),
            )
            .expect("source-set fixture");
            workspace.context.source_roots.push(JavaSourceRootOutput {
                id: root_id.to_owned(),
                source_set: source_set.to_owned(),
                path: root_name.to_owned(),
                kind: JavaSourceRootKind::Custom,
                exists: true,
            });
            workspace.root_authorities.push(JavaSourceRootAuthority {
                root_id: root_id.to_owned(),
                source_set: source_set.to_owned(),
                canonical_absolute_path: dunce::canonicalize(&root)
                    .expect("canonical source-set root"),
                report_root_path: root_name.to_owned(),
            });
            workspace.files.push(JavaSourceFile {
                absolute_path: dunce::canonicalize(&path).expect("canonical source-set file"),
                root_id: root_id.to_owned(),
                root_relative_path: format!("example/{file_name}"),
                report_path: format!("{root_name}/example/{file_name}"),
                source_set: source_set.to_owned(),
                source_override: None,
            });
        }
        workspace.context.source_roots.push(JavaSourceRootOutput {
            id: "missing-generated".to_owned(),
            source_set: "generated".to_owned(),
            path: "missing-generated".to_owned(),
            kind: JavaSourceRootKind::Custom,
            exists: false,
        });
        let source_sets = ["main", "test", "gametest", "generated"]
            .into_iter()
            .map(str::to_owned)
            .collect::<Vec<_>>();
        workspace.context.source_sets = source_sets
            .iter()
            .map(|source_set| JavaSourceSetOutput {
                id: source_set.clone(),
                visible_source_sets: source_sets.clone(),
            })
            .collect();
        request.workspace.source_roots = workspace.context.source_roots.clone();
        request.workspace.workspace_fingerprint =
            definition_workspace_fingerprint(&workspace.context, None)
                .expect("multi-source-set workspace fingerprint");

        let engine = DefinitionAtPositionEngine::new(
            workspace,
            None,
            None,
            DefinitionAtPositionEngineLimits::default(),
        )
        .expect("engine");
        let result = engine
            .analyze_interaction_map(&request, &CancellationToken::new())
            .expect("multi-source-set interaction map");
        let covered_source_sets = result
            .files
            .iter()
            .filter(|file| {
                file.resolver_id == "workspace" && file.state == JavaInteractionFileState::Covered
            })
            .map(|file| file.source_set.as_str())
            .collect::<BTreeSet<_>>();
        assert_eq!(
            covered_source_sets,
            BTreeSet::from(["main", "test", "gametest"])
        );
        assert!(result.files.iter().any(|file| {
            file.root_id == "missing-generated"
                && file.source_set == "generated"
                && file.state == JavaInteractionFileState::Missing
        }));
    }

    #[test]
    fn stale_document_is_a_typed_map_outcome() {
        let (_directory, workspace, mut request) = fixture();
        let target = workspace.files[0].absolute_path.clone();
        request.document.disk_content_hash = Some(request.document.content_hash.clone());
        std::fs::write(&target, "package example; class Changed {}\n")
            .expect("change fixture after capture");
        let engine = DefinitionAtPositionEngine::new(
            workspace,
            None,
            None,
            DefinitionAtPositionEngineLimits::default(),
        )
        .expect("engine");
        let result = engine
            .analyze_interaction_map(&request, &CancellationToken::new())
            .expect("typed stale result");
        assert_eq!(result.outcome, JavaInteractionMapOutcome::StaleDocument);
        assert!(result.regions.is_empty());
        assert!(
            result
                .diagnostics
                .iter()
                .any(|diagnostic| { diagnostic.code == "java.interaction-map-stale-document" })
        );
    }

    #[test]
    fn malformed_non_whitespace_is_never_silent_or_nearest_token_fallback() {
        let (_directory, workspace, mut request) = fixture();
        request.document.text =
            "package example; final class Broken { void run( { int value = ; }".to_owned();
        request.document.content_hash = blake3_content_hash(&request.document.text);
        let engine = DefinitionAtPositionEngine::new(
            workspace,
            None,
            None,
            DefinitionAtPositionEngineLimits::default(),
        )
        .expect("engine");
        let result = engine
            .analyze_interaction_map_with_telemetry(&request, &CancellationToken::new())
            .expect("malformed map")
            .result;
        assert!(
            result
                .regions
                .iter()
                .any(|region| region.semantic_kind == "java-malformed-recovery"
                    || region.semantic_kind == "java-missing-syntax")
        );
        assert!(result.exceptions.iter().any(
            |exception| exception.effect == JavaInteractionExceptionEffect::FailsStrictProfile
        ));
        assert!(
            result
                .classifications
                .iter()
                .all(|classification| classification.status
                    != JavaInteractionClassificationStatus::Unclassified)
        );
    }

    #[test]
    fn half_open_regions_do_not_claim_the_end_boundary() {
        let region = JavaInteractionRegionOutput {
            schema: JAVA_INTERACTION_REGION_SCHEMA.to_owned(),
            id: "fixture".to_owned(),
            domain_id: "utf8:fixture".to_owned(),
            representation: "interval".to_owned(),
            bounds: vec![JavaInteractionRegionAxisOutput {
                start_inclusive: 3,
                end_exclusive: 7,
            }],
            edge_policy: "half-open".to_owned(),
            semantic_kind: "java-identifier".to_owned(),
            provenance: "fixture".to_owned(),
            projection_ids: Vec::new(),
        };
        assert!(region.start_byte() <= 3 && 3 < region.end_byte());
        assert!(!(region.start_byte() <= 7 && 7 < region.end_byte()));
    }

    #[test]
    fn fixture_paths_remain_portable() {
        let (_directory, workspace, request) = fixture();
        assert_eq!(
            workspace.files[0].report_path,
            "source/example/SpatialFixture.java"
        );
        assert_eq!(
            request.document.root_relative_path,
            "example/SpatialFixture.java"
        );
        assert!(!request.document.report_path.contains('\\'));
        let _ = PathBuf::from(request.document.root_relative_path);
    }
}
