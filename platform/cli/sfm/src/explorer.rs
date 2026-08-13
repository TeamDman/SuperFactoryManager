//! Typed path, expression, and selector contracts shared with the game client.

use std::cmp::Ordering;
use std::fmt::{Display, Formatter};
use std::path::{Component, Path, PathBuf};

/// A stable parse failure suitable for machine-readable diagnostics.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ExplorerParseError {
    /// Stable diagnostic code.
    pub code: &'static str,
    /// Human-readable explanation.
    pub message: String,
    /// Byte offset in the submitted canonical text.
    pub offset: usize,
}

impl ExplorerParseError {
    fn new(code: &'static str, message: impl Into<String>, offset: usize) -> Self {
        Self {
            code,
            message: message.into(),
            offset,
        }
    }
}

impl Display for ExplorerParseError {
    fn fmt(&self, formatter: &mut Formatter<'_>) -> std::fmt::Result {
        write!(
            formatter,
            "{} at byte {}: {}",
            self.code, self.offset, self.message
        )
    }
}

impl std::error::Error for ExplorerParseError {}

/// Resolver-specific kind of one canonical content path.
#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub enum SfmPathKind {
    /// Native filesystem subject.
    File,
    /// Minecraft/SFM registry subject.
    Registry,
    /// Live or pinned named selection subject.
    Selection,
    /// Subject supplied by another registered resolver scheme.
    Contributed,
}

/// One canonical UTF-8 content path.
#[derive(Clone, Debug, PartialEq, Eq, Hash)]
pub struct SfmPath {
    /// Resolver-specific path kind.
    kind: SfmPathKind,
    /// Lowercase resolver scheme.
    scheme: String,
    /// Decoded authority/id component.
    authority: String,
    /// Decoded normalized path segments.
    segments: Vec<String>,
    /// Optional immutable selection revision.
    revision: Option<String>,
    /// Whether the canonical spelling ends in `/`.
    trailing_slash: bool,
}

impl SfmPath {
    /// Parse and validate one canonical resolver path.
    ///
    /// # Errors
    ///
    /// Returns a typed failure for malformed schemes, encoding, or resolver
    /// invariants.
    pub fn parse(text: &str) -> Result<Self, ExplorerParseError> {
        if let Some(offset) = text.find('|') {
            return Err(ExplorerParseError::new(
                "path.pipe-aggregate-forbidden",
                "pipe-concatenated paths are not a path expression",
                offset,
            ));
        }
        if let Some(offset) = text.find(['?', '#']) {
            return Err(ExplorerParseError::new(
                "path.query-fragment-forbidden",
                "content paths do not contain query strings or fragments",
                offset,
            ));
        }
        let (scheme, remainder) = text.split_once("://").ok_or_else(|| {
            ExplorerParseError::new("path.missing-scheme", "expected a resolver path scheme", 0)
        })?;
        if scheme.bytes().any(|byte| byte.is_ascii_uppercase()) {
            return Err(ExplorerParseError::new(
                "path.noncanonical-scheme",
                "path scheme must be lowercase",
                0,
            ));
        }
        if scheme.is_empty() || !valid_scheme(scheme) {
            return Err(ExplorerParseError::new(
                "path.invalid-scheme",
                "unsupported path scheme syntax",
                0,
            ));
        }
        match scheme {
            "file" => Self::parse_file(remainder, scheme.len() + 3),
            "registry" => Self::parse_registry(remainder, scheme.len() + 3),
            "selection" => Self::parse_selection(remainder, scheme.len() + 3),
            _ => Self::parse_contributed(scheme, remainder, scheme.len() + 3),
        }
    }

    /// Convert one native path at the CLI boundary to canonical `file` form.
    ///
    /// # Errors
    ///
    /// Returns a typed failure when the native path is not Unicode, cannot be
    /// made absolute, or traverses above its root.
    pub fn from_native(path: &Path) -> Result<Self, ExplorerParseError> {
        let absolute = if path.is_absolute() {
            path.to_path_buf()
        } else {
            std::env::current_dir()
                .map_err(|error| {
                    ExplorerParseError::new(
                        "path.current-directory-unavailable",
                        error.to_string(),
                        0,
                    )
                })?
                .join(path)
        };
        let native = absolute.to_str().ok_or_else(|| {
            ExplorerParseError::new(
                "path.non-utf8-native-path",
                "native path is not valid Unicode",
                0,
            )
        })?;
        let slashed = native.replace('\\', "/");
        if let Some(without_prefix) = slashed.strip_prefix("//") {
            let (host, remainder) = without_prefix.split_once('/').ok_or_else(|| {
                ExplorerParseError::new("path.invalid-unc", "UNC path requires a share", 0)
            })?;
            let segments = normalize_native_segments(remainder, 1)?;
            if segments.is_empty() {
                return Err(ExplorerParseError::new(
                    "path.invalid-unc",
                    "UNC path requires a share",
                    0,
                ));
            }
            return Self::new(
                SfmPathKind::File,
                "file",
                host.to_ascii_lowercase(),
                segments,
                None,
                false,
            );
        }
        let without_root = slashed.trim_start_matches('/');
        let root_floor = usize::from(
            without_root
                .split('/')
                .find(|segment| !segment.is_empty() && *segment != ".")
                .is_some_and(is_drive_segment),
        );
        let mut segments = normalize_native_segments(without_root, root_floor)?;
        if let Some(first) = segments.first_mut()
            && is_drive_segment(first)
        {
            let drive = first.as_bytes()[0].to_ascii_uppercase() as char;
            first.replace_range(0..1, &drive.to_string());
        }
        let drive_root = segments.len() == 1 && is_drive_segment(&segments[0]);
        Self::new(
            SfmPathKind::File,
            "file",
            String::new(),
            segments,
            None,
            drive_root,
        )
    }

    /// Convert a `file` path back into a native path.
    ///
    /// # Errors
    ///
    /// Returns a typed failure for non-file paths.
    pub fn to_native_path(&self) -> Result<PathBuf, ExplorerParseError> {
        if self.kind != SfmPathKind::File {
            return Err(ExplorerParseError::new(
                "path.not-native",
                "only file paths have a native representation",
                0,
            ));
        }
        if !self.authority.is_empty() {
            validate_native_component(&self.authority, false)?;
            let (share, rest) = self.segments.split_first().ok_or_else(|| {
                ExplorerParseError::new(
                    "path.invalid-unc",
                    "UNC file paths require a share component",
                    0,
                )
            })?;
            validate_native_component(share, false)?;
            let root = PathBuf::from(format!(r"\\{}\{}\", self.authority, share));
            return resolve_native_components(&root, rest);
        }
        if self
            .segments
            .first()
            .is_some_and(|value| is_drive_segment(value))
        {
            let root = PathBuf::from(format!(r"{}\", self.segments[0]));
            return resolve_native_components(&root, &self.segments[1..]);
        }
        resolve_native_components(Path::new("/"), &self.segments)
    }

    /// Render the unique canonical text form.
    #[must_use]
    pub fn canonical(&self) -> String {
        if self.kind == SfmPathKind::Selection {
            let revision = self
                .revision
                .as_ref()
                .map(|value| format!("@{}", encode_component(value)))
                .unwrap_or_default();
            return format!(
                "selection://{}{}",
                encode_component(&self.authority),
                revision
            );
        }
        let mut answer = format!("{}://", self.scheme);
        if !self.authority.is_empty() {
            answer.push_str(&encode_component(&self.authority));
        }
        if self.kind != SfmPathKind::Contributed || !self.segments.is_empty() || self.trailing_slash
        {
            answer.push('/');
        }
        for (index, segment) in self.segments.iter().enumerate() {
            if index > 0 {
                answer.push('/');
            }
            if self.kind == SfmPathKind::File && index == 0 && is_drive_segment(segment) {
                answer.push(segment.as_bytes()[0].to_ascii_uppercase() as char);
                answer.push(':');
            } else {
                answer.push_str(&encode_component(segment));
            }
        }
        if self.trailing_slash && !answer.ends_with('/') {
            answer.push('/');
        }
        answer
    }

    /// Return the final filename extension without a leading dot.
    #[must_use]
    pub fn extension(&self) -> &str {
        let Some(name) = self.segments.last() else {
            return "";
        };
        let Some((stem, extension)) = name.rsplit_once('.') else {
            return "";
        };
        if stem.is_empty() || extension.is_empty() {
            ""
        } else {
            extension
        }
    }

    /// Construct a path from decoded components while enforcing all resolver
    /// invariants and canonical trailing-slash identity rules.
    ///
    /// # Errors
    ///
    /// Returns a typed failure when the kind and scheme disagree or any
    /// resolver-specific invariant is invalid.
    pub fn new(
        kind: SfmPathKind,
        scheme: impl Into<String>,
        authority: impl Into<String>,
        segments: Vec<String>,
        revision: Option<String>,
        trailing_slash: bool,
    ) -> Result<Self, ExplorerParseError> {
        let mut authority = authority.into();
        let mut segments = segments;
        if kind == SfmPathKind::File {
            authority = authority.to_lowercase();
            if let Some(first) = segments.first_mut()
                && is_drive_segment(first)
            {
                let drive = first.as_bytes()[0].to_ascii_uppercase() as char;
                first.replace_range(0..1, &drive.to_string());
            }
        }
        let mut path = Self {
            kind,
            scheme: scheme.into(),
            authority,
            segments,
            revision,
            trailing_slash,
        };
        path.validate()?;
        path.trailing_slash = path.canonical_trailing_slash();
        Ok(path)
    }

    /// Resolver-specific path kind.
    #[must_use]
    pub fn kind(&self) -> SfmPathKind {
        self.kind
    }

    /// Lowercase resolver scheme.
    #[must_use]
    pub fn scheme(&self) -> &str {
        &self.scheme
    }

    /// Decoded authority/id component.
    #[must_use]
    pub fn authority(&self) -> &str {
        &self.authority
    }

    /// Decoded normalized path segments.
    #[must_use]
    pub fn segments(&self) -> &[String] {
        &self.segments
    }

    /// Optional immutable selection revision.
    #[must_use]
    pub fn revision(&self) -> Option<&str> {
        self.revision.as_deref()
    }

    /// Whether the canonical spelling ends in `/`.
    #[must_use]
    pub fn trailing_slash(&self) -> bool {
        self.trailing_slash
    }

    fn parse_file(remainder: &str, source_offset: usize) -> Result<Self, ExplorerParseError> {
        let parsed = parse_authority_path(remainder, source_offset, true)?;
        let mut segments = parsed.segments;
        if let Some(first) = segments.first_mut()
            && is_drive_segment(first)
        {
            let drive = first.as_bytes()[0].to_ascii_uppercase() as char;
            first.replace_range(0..1, &drive.to_string());
        }
        Self::new(
            SfmPathKind::File,
            "file",
            parsed.authority.to_ascii_lowercase(),
            segments,
            None,
            parsed.trailing_slash,
        )
    }

    fn parse_registry(remainder: &str, source_offset: usize) -> Result<Self, ExplorerParseError> {
        let parsed = parse_authority_path(remainder, source_offset, false)?;
        if !parsed.authority.bytes().all(|byte| {
            byte.is_ascii_lowercase() || byte.is_ascii_digit() || b"_.-".contains(&byte)
        }) {
            return Err(ExplorerParseError::new(
                "path.invalid-registry-namespace",
                "registry namespace must use ResourceLocation-compatible lowercase characters",
                source_offset,
            ));
        }
        Self::new(
            SfmPathKind::Registry,
            "registry",
            parsed.authority,
            parsed.segments,
            None,
            parsed.trailing_slash,
        )
    }

    fn parse_selection(remainder: &str, source_offset: usize) -> Result<Self, ExplorerParseError> {
        if remainder.is_empty() || remainder.contains('/') {
            return Err(ExplorerParseError::new(
                "path.invalid-selection",
                "selection path requires one encoded id/name",
                source_offset,
            ));
        }
        let mut pieces = remainder.split('@');
        let encoded_name = pieces.next().unwrap_or_default();
        let encoded_revision = pieces.next();
        if pieces.next().is_some() {
            return Err(ExplorerParseError::new(
                "path.invalid-selection-revision",
                "selection path contains more than one revision separator",
                source_offset + encoded_name.len(),
            ));
        }
        if encoded_name.is_empty() || encoded_revision == Some("") {
            return Err(ExplorerParseError::new(
                "path.invalid-selection",
                "selection id/name and revision must not be empty",
                source_offset,
            ));
        }
        let authority = decode_component(encoded_name, source_offset)?;
        let revision = encoded_revision
            .map(|value| decode_component(value, source_offset + encoded_name.len() + 1))
            .transpose()?;
        Self::new(
            SfmPathKind::Selection,
            "selection",
            authority,
            Vec::new(),
            revision,
            false,
        )
    }

    fn parse_contributed(
        scheme: &str,
        remainder: &str,
        source_offset: usize,
    ) -> Result<Self, ExplorerParseError> {
        let parsed = parse_authority_path(remainder, source_offset, false)?;
        Self::new(
            SfmPathKind::Contributed,
            scheme,
            parsed.authority,
            parsed.segments,
            None,
            parsed.trailing_slash,
        )
    }

    fn canonical_trailing_slash(&self) -> bool {
        match self.kind {
            SfmPathKind::File => {
                self.segments.is_empty()
                    || self.segments.len() == 1
                        && (is_drive_segment(&self.segments[0]) || !self.authority.is_empty())
            }
            SfmPathKind::Registry => self.segments.len() == 1,
            SfmPathKind::Selection => false,
            SfmPathKind::Contributed => self.trailing_slash,
        }
    }

    fn validate(&self) -> Result<(), ExplorerParseError> {
        if !valid_scheme(&self.scheme) {
            return Err(ExplorerParseError::new(
                "path.invalid-scheme",
                "path scheme is not canonical",
                0,
            ));
        }
        let scheme_matches_kind = match self.kind {
            SfmPathKind::File => self.scheme == "file",
            SfmPathKind::Registry => self.scheme == "registry",
            SfmPathKind::Selection => self.scheme == "selection",
            SfmPathKind::Contributed => {
                !matches!(self.scheme.as_str(), "file" | "registry" | "selection")
            }
        };
        if !scheme_matches_kind {
            return Err(ExplorerParseError::new(
                "path.kind-scheme-mismatch",
                "path kind and resolver scheme do not agree",
                0,
            ));
        }
        if self.authority.contains('\0') {
            return Err(ExplorerParseError::new(
                "path.invalid-authority",
                "path authority contains NUL",
                0,
            ));
        }
        if self.segments.iter().any(|segment| {
            segment.is_empty() || segment == "." || segment == ".." || segment.contains('\0')
        }) {
            return Err(ExplorerParseError::new(
                "path.invalid-segment",
                "path segments must be non-empty and normalized",
                0,
            ));
        }
        if self
            .revision
            .as_ref()
            .is_some_and(|value| value.contains('\0'))
        {
            return Err(ExplorerParseError::new(
                "path.invalid-revision",
                "path revision contains NUL",
                0,
            ));
        }
        match self.kind {
            SfmPathKind::File if self.revision.is_some() => Err(ExplorerParseError::new(
                "path.invalid-revision",
                "file paths cannot carry revisions",
                0,
            )),
            SfmPathKind::Registry if self.authority.is_empty() || self.segments.is_empty() => {
                Err(ExplorerParseError::new(
                    "path.invalid-registry",
                    "registry paths require a namespace and registry segment",
                    0,
                ))
            }
            SfmPathKind::Registry
                if !valid_registry_component(&self.authority)
                    || self
                        .segments
                        .iter()
                        .any(|segment| !valid_registry_component(segment)) =>
            {
                Err(ExplorerParseError::new(
                    "path.invalid-registry",
                    "registry paths use ResourceLocation-compatible lowercase segments",
                    0,
                ))
            }
            SfmPathKind::Selection
                if self.authority.is_empty()
                    || !self.segments.is_empty()
                    || self.trailing_slash =>
            {
                Err(ExplorerParseError::new(
                    "path.invalid-selection",
                    "selection paths contain one id/name and an optional revision",
                    0,
                ))
            }
            SfmPathKind::Selection => Ok(()),
            _ if self.revision.is_some() => Err(ExplorerParseError::new(
                "path.invalid-revision",
                "only selection paths may carry a revision",
                0,
            )),
            _ => Ok(()),
        }
    }
}

impl Display for SfmPath {
    fn fmt(&self, formatter: &mut Formatter<'_>) -> std::fmt::Result {
        formatter.write_str(&self.canonical())
    }
}

impl PartialOrd for SfmPath {
    fn partial_cmp(&self, other: &Self) -> Option<Ordering> {
        Some(self.cmp(other))
    }
}

impl Ord for SfmPath {
    fn cmp(&self, other: &Self) -> Ordering {
        self.canonical().cmp(&other.canonical())
    }
}

#[derive(Debug)]
struct ParsedAuthorityPath {
    authority: String,
    segments: Vec<String>,
    trailing_slash: bool,
}

fn parse_authority_path(
    remainder: &str,
    source_offset: usize,
    allow_empty_authority: bool,
) -> Result<ParsedAuthorityPath, ExplorerParseError> {
    let (encoded_authority, mut encoded_path) = remainder
        .split_once('/')
        .map_or((remainder, ""), |(authority, path)| (authority, path));
    if !allow_empty_authority && encoded_authority.is_empty() {
        return Err(ExplorerParseError::new(
            "path.missing-authority",
            "path authority is required",
            source_offset,
        ));
    }
    let authority = decode_component(encoded_authority, source_offset)?;
    let had_slash = remainder.contains('/');
    let trailing_slash = (!encoded_path.is_empty() && encoded_path.ends_with('/'))
        || (encoded_path.is_empty() && had_slash);
    if trailing_slash && !encoded_path.is_empty() {
        encoded_path = &encoded_path[..encoded_path.len() - 1];
    }
    if let Some(relative_offset) = encoded_path.find("//") {
        return Err(ExplorerParseError::new(
            "path.empty-segment",
            "canonical paths do not contain empty segments",
            source_offset + encoded_authority.len() + 1 + relative_offset,
        ));
    }
    let mut segments = Vec::new();
    if !encoded_path.is_empty() {
        let mut segment_offset = source_offset + encoded_authority.len() + 1;
        for encoded_segment in encoded_path.split('/') {
            let segment = if segments.is_empty()
                && allow_empty_authority
                && is_drive_segment(encoded_segment)
            {
                format!(
                    "{}:",
                    encoded_segment.as_bytes()[0].to_ascii_uppercase() as char
                )
            } else {
                decode_component(encoded_segment, segment_offset)?
            };
            if segment == "." || segment == ".." {
                return Err(ExplorerParseError::new(
                    "path.noncanonical-segment",
                    "canonical paths must normalize dot segments",
                    segment_offset,
                ));
            }
            segments.push(segment);
            segment_offset += encoded_segment.len() + 1;
        }
    }
    Ok(ParsedAuthorityPath {
        authority,
        segments,
        trailing_slash,
    })
}

fn normalize_native_segments(
    path: &str,
    root_floor: usize,
) -> Result<Vec<String>, ExplorerParseError> {
    let mut answer = Vec::new();
    for segment in path.split('/') {
        if segment.is_empty() || segment == "." {
            continue;
        }
        if segment == ".." {
            if answer.len() <= root_floor {
                return Err(ExplorerParseError::new(
                    "path.native-traversal-above-root",
                    "native path traverses above its root",
                    0,
                ));
            }
            answer.pop();
        } else {
            answer.push(segment.to_owned());
        }
    }
    Ok(answer)
}

fn resolve_native_components(
    root: &Path,
    components: &[String],
) -> Result<PathBuf, ExplorerParseError> {
    let mut answer = root.to_path_buf();
    for component in components {
        validate_native_component(component, false)?;
        answer.push(component);
    }
    if !answer.starts_with(root) {
        return Err(ExplorerParseError::new(
            "path.native-component-escape",
            "native path components escape the canonical file root",
            0,
        ));
    }
    Ok(answer)
}

fn validate_native_component(
    component: &str,
    allow_drive_prefix: bool,
) -> Result<(), ExplorerParseError> {
    if component.contains(['/', '\\']) {
        return Err(ExplorerParseError::new(
            "path.native-embedded-separator",
            "a canonical file component decodes to a native path separator",
            0,
        ));
    }
    if !allow_drive_prefix && looks_like_drive_prefix(component) {
        return Err(ExplorerParseError::new(
            "path.native-drive-prefix",
            "a drive prefix is only valid as the first local file component",
            0,
        ));
    }
    let mut components = Path::new(component).components();
    if !matches!(components.next(), Some(Component::Normal(_))) || components.next().is_some() {
        return Err(ExplorerParseError::new(
            "path.native-component-escape",
            "a canonical file component is not one relative native component",
            0,
        ));
    }
    Ok(())
}

fn valid_scheme(scheme: &str) -> bool {
    let mut bytes = scheme.bytes();
    bytes.next().is_some_and(|byte| byte.is_ascii_lowercase())
        && bytes.all(|byte| {
            byte.is_ascii_lowercase() || byte.is_ascii_digit() || matches!(byte, b'+' | b'.' | b'-')
        })
}

fn valid_registry_component(value: &str) -> bool {
    !value.is_empty()
        && value.bytes().all(|byte| {
            byte.is_ascii_lowercase() || byte.is_ascii_digit() || b"_.-".contains(&byte)
        })
}

fn is_drive_segment(segment: &str) -> bool {
    let bytes = segment.as_bytes();
    bytes.len() == 2 && bytes[0].is_ascii_alphabetic() && bytes[1] == b':'
}

fn looks_like_drive_prefix(component: &str) -> bool {
    let bytes = component.as_bytes();
    bytes.len() >= 2 && bytes[0].is_ascii_alphabetic() && bytes[1] == b':'
}

fn encode_component(value: &str) -> String {
    let mut answer = String::with_capacity(value.len());
    for byte in value.bytes() {
        if byte.is_ascii_alphanumeric() || matches!(byte, b'-' | b'.' | b'_' | b'~') {
            answer.push(byte as char);
        } else {
            use std::fmt::Write as _;
            write!(&mut answer, "%{byte:02X}").expect("writing to String cannot fail");
        }
    }
    answer
}

fn decode_component(value: &str, source_offset: usize) -> Result<String, ExplorerParseError> {
    let bytes = value.as_bytes();
    let mut decoded = Vec::with_capacity(bytes.len());
    let mut index = 0;
    while index < bytes.len() {
        let current = bytes[index];
        if current == b'%' {
            if index + 2 >= bytes.len() {
                return Err(ExplorerParseError::new(
                    "text.invalid-percent-escape",
                    "percent escape is incomplete",
                    source_offset + index,
                ));
            }
            let high = hex_value(bytes[index + 1]);
            let low = hex_value(bytes[index + 2]);
            let (Some(high), Some(low)) = (high, low) else {
                return Err(ExplorerParseError::new(
                    "text.invalid-percent-escape",
                    "percent escape is not hexadecimal",
                    source_offset + index,
                ));
            };
            decoded.push(high << 4 | low);
            index += 3;
            continue;
        }
        if !current.is_ascii() || !is_unreserved(current) {
            return Err(ExplorerParseError::new(
                "text.unescaped-character",
                "canonical components must percent-encode reserved and non-ASCII characters",
                source_offset + index,
            ));
        }
        decoded.push(current);
        index += 1;
    }
    let decoded = String::from_utf8(decoded).map_err(|_| {
        ExplorerParseError::new(
            "text.invalid-utf8",
            "percent escapes do not decode to valid UTF-8",
            source_offset,
        )
    })?;
    if decoded.contains('\0') {
        return Err(ExplorerParseError::new(
            "text.invalid-unicode",
            "text contains NUL",
            source_offset,
        ));
    }
    Ok(decoded)
}

fn is_unreserved(byte: u8) -> bool {
    byte.is_ascii_alphanumeric() || matches!(byte, b'-' | b'.' | b'_' | b'~')
}

fn hex_value(byte: u8) -> Option<u8> {
    match byte {
        b'0'..=b'9' => Some(byte - b'0'),
        b'a'..=b'f' => Some(byte - b'a' + 10),
        b'A'..=b'F' => Some(byte - b'A' + 10),
        _ => None,
    }
}

/// Entity domain that gives selector syntax its type.
#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub enum SelectorDomain {
    /// Live game processes.
    Game,
    /// Visible split leaves; resolution remains unavailable until X-10.
    Pane,
    /// Stacked panel entries.
    PanelEntry,
    /// Generic explorer sessions.
    Explorer,
    /// Named/versioned selections.
    Selection,
}

impl SelectorDomain {
    fn focus_capable(self) -> bool {
        !matches!(self, Self::Selection)
    }

    fn name_capable(self) -> bool {
        matches!(self, Self::Selection)
    }
}

/// One pure set-valued selector AST node.
#[derive(Clone, Debug, PartialEq, Eq)]
enum SelectorNode {
    /// Exact stable identity.
    Id(String),
    /// Human-assigned selection name.
    Name(String),
    /// Explicitly focused entity in a focus-capable domain.
    Focused,
    /// Every entity in the domain snapshot.
    All,
    /// Set union.
    Union(Vec<Self>),
    /// Set intersection.
    Intersection(Vec<Self>),
    /// Include minus one or more exclusions.
    Difference {
        /// Included selector.
        include: Box<Self>,
        /// Excluded selectors.
        exclude: Vec<Self>,
    },
}

impl SelectorNode {
    fn canonical(&self) -> String {
        match self {
            Self::Id(value) => format!("id({})", encode_component(value)),
            Self::Name(value) => format!("name({})", encode_component(value)),
            Self::Focused => "focused".to_owned(),
            Self::All => "all".to_owned(),
            Self::Union(selectors) => canonical_selector_call("union", selectors),
            Self::Intersection(selectors) => canonical_selector_call("intersection", selectors),
            Self::Difference { include, exclude } => format!(
                "difference({},{})",
                include.canonical(),
                exclude
                    .iter()
                    .map(Self::canonical)
                    .collect::<Vec<_>>()
                    .join(",")
            ),
        }
    }
}

/// A selector paired with the only entity domain in which it may resolve.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct EntitySelector {
    /// Typed entity domain.
    domain: SelectorDomain,
    /// Pure set expression.
    node: SelectorNode,
}

impl EntitySelector {
    /// Parse a selector in one explicit entity domain.
    ///
    /// # Errors
    ///
    /// Returns a typed failure for malformed set syntax or a node unsupported
    /// by the selected domain.
    pub fn parse(domain: SelectorDomain, text: &str) -> Result<Self, ExplorerParseError> {
        let node = parse_selector_node(text)?;
        Self::new(domain, node)
    }

    /// Construct one exact identity selector.
    ///
    /// # Errors
    ///
    /// Returns a typed failure if the identity is empty or contains NUL.
    pub fn exact(
        domain: SelectorDomain,
        id: impl Into<String>,
    ) -> Result<Self, ExplorerParseError> {
        let id = id.into();
        validate_selector_value(&id, "selector.empty-id")?;
        Self::new(domain, SelectorNode::Id(id))
    }

    /// Construct one human-named selection selector.
    ///
    /// # Errors
    ///
    /// Returns a typed failure outside the selection domain or for an invalid
    /// name.
    pub fn named(
        domain: SelectorDomain,
        name: impl Into<String>,
    ) -> Result<Self, ExplorerParseError> {
        let name = name.into();
        validate_selector_value(&name, "selector.empty-name")?;
        Self::new(domain, SelectorNode::Name(name))
    }

    /// Construct the focused selector in a focus-capable domain.
    ///
    /// # Errors
    ///
    /// Returns a typed failure for domains without focus semantics.
    pub fn focused(domain: SelectorDomain) -> Result<Self, ExplorerParseError> {
        Self::new(domain, SelectorNode::Focused)
    }

    /// Construct the selector matching every entity in one domain.
    ///
    /// # Panics
    ///
    /// Panics only if the internal selector validator stops accepting the
    /// domain-independent `all` node, which would violate this type's
    /// constructor invariant.
    #[must_use]
    pub fn all(domain: SelectorDomain) -> Self {
        Self::new(domain, SelectorNode::All).expect("all is valid in every selector domain")
    }

    /// Construct a non-empty union of selectors from one domain.
    ///
    /// # Errors
    ///
    /// Returns a typed failure for no operands or mixed domains.
    pub fn union(domain: SelectorDomain, selectors: Vec<Self>) -> Result<Self, ExplorerParseError> {
        Self::set_operation(domain, selectors, true)
    }

    /// Construct a non-empty intersection of selectors from one domain.
    ///
    /// # Errors
    ///
    /// Returns a typed failure for no operands or mixed domains.
    pub fn intersection(
        domain: SelectorDomain,
        selectors: Vec<Self>,
    ) -> Result<Self, ExplorerParseError> {
        Self::set_operation(domain, selectors, false)
    }

    /// Construct an include-minus-exclusions selector.
    ///
    /// # Errors
    ///
    /// Returns a typed failure for no exclusions or mixed domains.
    pub fn difference(include: Self, exclude: Vec<Self>) -> Result<Self, ExplorerParseError> {
        if exclude.is_empty() {
            return Err(ExplorerParseError::new(
                "selector.difference-arity",
                "difference requires at least one exclude selector",
                0,
            ));
        }
        let domain = include.domain;
        if exclude.iter().any(|selector| selector.domain != domain) {
            return Err(ExplorerParseError::new(
                "selector.domain-mismatch",
                "selector set operations cannot combine entity domains",
                0,
            ));
        }
        Self::new(
            domain,
            SelectorNode::Difference {
                include: Box::new(include.node),
                exclude: exclude.into_iter().map(|selector| selector.node).collect(),
            },
        )
    }

    /// Typed entity domain in which this selector may resolve.
    #[must_use]
    pub fn domain(&self) -> SelectorDomain {
        self.domain
    }

    /// Render the unique canonical selector text.
    #[must_use]
    pub fn canonical(&self) -> String {
        self.node.canonical()
    }

    /// Whether this selector asserts exactly one stable id.
    #[must_use]
    pub fn is_exact_identity(&self) -> bool {
        matches!(self.node, SelectorNode::Id(_))
    }

    fn new(domain: SelectorDomain, node: SelectorNode) -> Result<Self, ExplorerParseError> {
        validate_selector_node(&node)?;
        validate_selector_domain(domain, &node)?;
        Ok(Self { domain, node })
    }

    fn set_operation(
        domain: SelectorDomain,
        selectors: Vec<Self>,
        union: bool,
    ) -> Result<Self, ExplorerParseError> {
        if selectors.is_empty() {
            return Err(ExplorerParseError::new(
                if union {
                    "selector.empty-union"
                } else {
                    "selector.empty-intersection"
                },
                "set operation requires at least one operand",
                0,
            ));
        }
        if selectors.iter().any(|selector| selector.domain != domain) {
            return Err(ExplorerParseError::new(
                "selector.domain-mismatch",
                "selector set operations cannot combine entity domains",
                0,
            ));
        }
        let nodes = selectors
            .into_iter()
            .map(|selector| selector.node)
            .collect();
        Self::new(
            domain,
            if union {
                SelectorNode::Union(nodes)
            } else {
                SelectorNode::Intersection(nodes)
            },
        )
    }
}

impl Display for EntitySelector {
    fn fmt(&self, formatter: &mut Formatter<'_>) -> std::fmt::Result {
        formatter.write_str(&self.canonical())
    }
}

/// One expression that resolves to zero or more concrete paths.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct PathExpression {
    node: PathExpressionNode,
}

#[derive(Clone, Debug, PartialEq, Eq)]
enum PathExpressionNode {
    /// One concrete path.
    Literal(SfmPath),
    /// Members of one or more selected selection objects.
    Members(EntitySelector),
    /// Immediate children of subjects produced by another expression.
    Children(Box<Self>),
    /// Set union.
    Union(Vec<Self>),
    /// Set intersection.
    Intersection(Vec<Self>),
    /// Include minus one or more exclusions.
    Difference {
        /// Included expression.
        include: Box<Self>,
        /// Excluded expressions.
        exclude: Vec<Self>,
    },
}

impl PathExpression {
    /// Parse a canonical path expression.
    ///
    /// # Errors
    ///
    /// Returns a typed failure for malformed paths, set functions, arity, or
    /// selection dereferences.
    pub fn parse(text: &str) -> Result<Self, ExplorerParseError> {
        if let Some(offset) = text.find('|') {
            return Err(ExplorerParseError::new(
                "path-expression.pipe-aggregate-forbidden",
                "pipe-concatenated paths are not a path expression",
                offset,
            ));
        }
        let first_parenthesis = text.find('(');
        let scheme = text.find("://");
        if scheme.is_some()
            && first_parenthesis.is_none_or(|parenthesis| scheme.is_some_and(|at| parenthesis > at))
        {
            return SfmPath::parse(text).map(Self::literal);
        }
        let call = parse_function(text)?;
        match call.name.as_str() {
            "members" => {
                require_arity(&call, 1, "path-expression.arity")?;
                let selector =
                    EntitySelector::parse(SelectorDomain::Selection, &call.arguments[0])?;
                Self::members(selector)
            }
            "children" => {
                require_arity(&call, 1, "path-expression.arity")?;
                Self::parse(&call.arguments[0]).map(Self::children)
            }
            "union" => Self::union(parse_path_operands(&call, "path-expression.empty-union")?),
            "intersection" => Self::intersection(parse_path_operands(
                &call,
                "path-expression.empty-intersection",
            )?),
            "difference" => {
                if call.arguments.len() < 2 {
                    return Err(ExplorerParseError::new(
                        "path-expression.difference-arity",
                        "difference requires an include and at least one exclude expression",
                        0,
                    ));
                }
                let include = Self::parse(&call.arguments[0])?;
                let exclude = call.arguments[1..]
                    .iter()
                    .map(|argument| Self::parse(argument))
                    .collect::<Result<Vec<_>, _>>()?;
                Self::difference(include, exclude)
            }
            _ => Err(ExplorerParseError::new(
                "path-expression.unknown-function",
                format!("unknown path-expression function: {}", call.name),
                0,
            )),
        }
    }

    /// Render the unique canonical path-expression text.
    #[must_use]
    pub fn canonical(&self) -> String {
        self.node.canonical()
    }

    /// Construct one literal canonical path expression.
    #[must_use]
    pub fn literal(path: SfmPath) -> Self {
        Self {
            node: PathExpressionNode::Literal(path),
        }
    }

    /// Construct a selection-membership dereference.
    ///
    /// # Errors
    ///
    /// Returns a typed failure unless the selector belongs to the selection
    /// domain.
    pub fn members(selector: EntitySelector) -> Result<Self, ExplorerParseError> {
        if selector.domain() != SelectorDomain::Selection {
            return Err(ExplorerParseError::new(
                "path-expression.members-domain",
                "members requires a selection-domain selector",
                0,
            ));
        }
        Self::new(PathExpressionNode::Members(selector))
    }

    /// Construct an immediate-children expression.
    #[must_use]
    pub fn children(expression: Self) -> Self {
        Self {
            node: PathExpressionNode::Children(Box::new(expression.node)),
        }
    }

    /// Construct a non-empty path union.
    ///
    /// # Errors
    ///
    /// Returns a typed failure for no operands.
    pub fn union(expressions: Vec<Self>) -> Result<Self, ExplorerParseError> {
        Self::set_operation(expressions, true)
    }

    /// Construct a non-empty path intersection.
    ///
    /// # Errors
    ///
    /// Returns a typed failure for no operands.
    pub fn intersection(expressions: Vec<Self>) -> Result<Self, ExplorerParseError> {
        Self::set_operation(expressions, false)
    }

    /// Construct one include-minus-exclusions path expression.
    ///
    /// # Errors
    ///
    /// Returns a typed failure for no exclusions.
    pub fn difference(include: Self, exclude: Vec<Self>) -> Result<Self, ExplorerParseError> {
        if exclude.is_empty() {
            return Err(ExplorerParseError::new(
                "path-expression.difference-arity",
                "difference requires at least one exclude expression",
                0,
            ));
        }
        Self::new(PathExpressionNode::Difference {
            include: Box::new(include.node),
            exclude: exclude
                .into_iter()
                .map(|expression| expression.node)
                .collect(),
        })
    }

    fn set_operation(expressions: Vec<Self>, union: bool) -> Result<Self, ExplorerParseError> {
        if expressions.is_empty() {
            return Err(ExplorerParseError::new(
                if union {
                    "path-expression.empty-union"
                } else {
                    "path-expression.empty-intersection"
                },
                "set operation requires at least one operand",
                0,
            ));
        }
        let nodes = expressions
            .into_iter()
            .map(|expression| expression.node)
            .collect();
        Self::new(if union {
            PathExpressionNode::Union(nodes)
        } else {
            PathExpressionNode::Intersection(nodes)
        })
    }

    fn new(node: PathExpressionNode) -> Result<Self, ExplorerParseError> {
        validate_path_expression_node(&node)?;
        Ok(Self { node })
    }
}

impl PathExpressionNode {
    fn canonical(&self) -> String {
        match self {
            Self::Literal(path) => path.canonical(),
            Self::Members(selector) => format!("members({})", selector.canonical()),
            Self::Children(expression) => format!("children({})", expression.canonical()),
            Self::Union(expressions) => canonical_path_call("union", expressions),
            Self::Intersection(expressions) => canonical_path_call("intersection", expressions),
            Self::Difference { include, exclude } => format!(
                "difference({},{})",
                include.canonical(),
                exclude
                    .iter()
                    .map(Self::canonical)
                    .collect::<Vec<_>>()
                    .join(",")
            ),
        }
    }
}

fn validate_path_expression_node(node: &PathExpressionNode) -> Result<(), ExplorerParseError> {
    match node {
        PathExpressionNode::Literal(_) => Ok(()),
        PathExpressionNode::Members(selector) => {
            if selector.domain() != SelectorDomain::Selection {
                return Err(ExplorerParseError::new(
                    "path-expression.members-domain",
                    "members requires a selection-domain selector",
                    0,
                ));
            }
            validate_selector_node(&selector.node)?;
            validate_selector_domain(selector.domain, &selector.node)
        }
        PathExpressionNode::Children(expression) => validate_path_expression_node(expression),
        PathExpressionNode::Union(expressions) | PathExpressionNode::Intersection(expressions) => {
            if expressions.is_empty() {
                return Err(ExplorerParseError::new(
                    "path-expression.empty-set-operation",
                    "set operation requires at least one operand",
                    0,
                ));
            }
            for expression in expressions {
                validate_path_expression_node(expression)?;
            }
            Ok(())
        }
        PathExpressionNode::Difference { include, exclude } => {
            if exclude.is_empty() {
                return Err(ExplorerParseError::new(
                    "path-expression.difference-arity",
                    "difference requires at least one exclude expression",
                    0,
                ));
            }
            validate_path_expression_node(include)?;
            for expression in exclude {
                validate_path_expression_node(expression)?;
            }
            Ok(())
        }
    }
}

impl Display for PathExpression {
    fn fmt(&self, formatter: &mut Formatter<'_>) -> std::fmt::Result {
        formatter.write_str(&self.canonical())
    }
}

fn parse_selector_node(text: &str) -> Result<SelectorNode, ExplorerParseError> {
    match text {
        "focused" => Ok(SelectorNode::Focused),
        "all" => Ok(SelectorNode::All),
        _ => {
            let call = parse_function(text)?;
            match call.name.as_str() {
                "id" => decode_selector_scalar(&call, "selector.empty-id").map(SelectorNode::Id),
                "name" => {
                    decode_selector_scalar(&call, "selector.empty-name").map(SelectorNode::Name)
                }
                "union" => {
                    parse_selector_operands(&call, "selector.empty-union").map(SelectorNode::Union)
                }
                "intersection" => parse_selector_operands(&call, "selector.empty-intersection")
                    .map(SelectorNode::Intersection),
                "difference" => {
                    if call.arguments.len() < 2 {
                        return Err(ExplorerParseError::new(
                            "selector.difference-arity",
                            "difference requires an include and at least one exclude selector",
                            0,
                        ));
                    }
                    let include = Box::new(parse_selector_node(&call.arguments[0])?);
                    let exclude = call.arguments[1..]
                        .iter()
                        .map(|argument| parse_selector_node(argument))
                        .collect::<Result<Vec<_>, _>>()?;
                    Ok(SelectorNode::Difference { include, exclude })
                }
                _ => Err(ExplorerParseError::new(
                    "selector.unknown-function",
                    format!("unknown selector function: {}", call.name),
                    0,
                )),
            }
        }
    }
}

fn validate_selector_node(node: &SelectorNode) -> Result<(), ExplorerParseError> {
    match node {
        SelectorNode::Id(value) => validate_selector_value(value, "selector.empty-id"),
        SelectorNode::Name(value) => validate_selector_value(value, "selector.empty-name"),
        SelectorNode::Focused | SelectorNode::All => Ok(()),
        SelectorNode::Union(children) | SelectorNode::Intersection(children) => {
            if children.is_empty() {
                return Err(ExplorerParseError::new(
                    "selector.empty-set-operation",
                    "set operation requires at least one operand",
                    0,
                ));
            }
            for child in children {
                validate_selector_node(child)?;
            }
            Ok(())
        }
        SelectorNode::Difference { include, exclude } => {
            if exclude.is_empty() {
                return Err(ExplorerParseError::new(
                    "selector.difference-arity",
                    "difference requires at least one exclude selector",
                    0,
                ));
            }
            validate_selector_node(include)?;
            for child in exclude {
                validate_selector_node(child)?;
            }
            Ok(())
        }
    }
}

fn validate_selector_domain(
    domain: SelectorDomain,
    node: &SelectorNode,
) -> Result<(), ExplorerParseError> {
    match node {
        SelectorNode::Focused if !domain.focus_capable() => Err(ExplorerParseError::new(
            "selector.focus-unsupported",
            format!("focused is not supported for {domain:?}"),
            0,
        )),
        SelectorNode::Name(_) if !domain.name_capable() => Err(ExplorerParseError::new(
            "selector.name-unsupported",
            "name is only supported for selections",
            0,
        )),
        SelectorNode::Union(children) | SelectorNode::Intersection(children) => {
            for child in children {
                validate_selector_domain(domain, child)?;
            }
            Ok(())
        }
        SelectorNode::Difference { include, exclude } => {
            validate_selector_domain(domain, include)?;
            for child in exclude {
                validate_selector_domain(domain, child)?;
            }
            Ok(())
        }
        _ => Ok(()),
    }
}

fn decode_selector_scalar(
    call: &FunctionCall,
    empty_code: &'static str,
) -> Result<String, ExplorerParseError> {
    require_arity(call, 1, "selector.scalar-arity")?;
    let value = &call.arguments[0];
    if value.contains(['(', ')', ',']) {
        return Err(ExplorerParseError::new(
            "selector.invalid-scalar",
            format!("{} requires one scalar value", call.name),
            0,
        ));
    }
    let decoded = decode_component(value, call.name.len() + 1)?;
    validate_selector_value(&decoded, empty_code)?;
    Ok(decoded)
}

fn validate_selector_value(
    value: &str,
    empty_code: &'static str,
) -> Result<(), ExplorerParseError> {
    if value.is_empty() {
        return Err(ExplorerParseError::new(
            empty_code,
            "selector value must not be empty",
            0,
        ));
    }
    if value.contains('\0') {
        return Err(ExplorerParseError::new(
            "selector.invalid-unicode",
            "selector value contains NUL",
            0,
        ));
    }
    Ok(())
}

fn parse_selector_operands(
    call: &FunctionCall,
    empty_code: &'static str,
) -> Result<Vec<SelectorNode>, ExplorerParseError> {
    if call.arguments.is_empty() {
        return Err(ExplorerParseError::new(
            empty_code,
            "set operation requires at least one operand",
            0,
        ));
    }
    call.arguments
        .iter()
        .map(|argument| parse_selector_node(argument))
        .collect()
}

fn parse_path_operands(
    call: &FunctionCall,
    empty_code: &'static str,
) -> Result<Vec<PathExpression>, ExplorerParseError> {
    if call.arguments.is_empty() {
        return Err(ExplorerParseError::new(
            empty_code,
            "set operation requires at least one operand",
            0,
        ));
    }
    call.arguments
        .iter()
        .map(|argument| PathExpression::parse(argument))
        .collect()
}

fn canonical_selector_call(name: &str, selectors: &[SelectorNode]) -> String {
    format!(
        "{name}({})",
        selectors
            .iter()
            .map(SelectorNode::canonical)
            .collect::<Vec<_>>()
            .join(",")
    )
}

fn canonical_path_call(name: &str, expressions: &[PathExpressionNode]) -> String {
    format!(
        "{name}({})",
        expressions
            .iter()
            .map(PathExpressionNode::canonical)
            .collect::<Vec<_>>()
            .join(",")
    )
}

#[derive(Debug)]
struct FunctionCall {
    name: String,
    arguments: Vec<String>,
}

fn parse_function(text: &str) -> Result<FunctionCall, ExplorerParseError> {
    let first_open = text.find('(').ok_or_else(|| {
        ExplorerParseError::new(
            "expression.invalid-function",
            "expected function expression",
            0,
        )
    })?;
    if first_open == 0 || !text.ends_with(')') {
        return Err(ExplorerParseError::new(
            "expression.invalid-function",
            "expected function expression",
            0,
        ));
    }
    let name = text[..first_open].to_ascii_lowercase();
    if !name
        .bytes()
        .all(|byte| byte.is_ascii_lowercase() || matches!(byte, b'-' | b'_'))
    {
        return Err(ExplorerParseError::new(
            "expression.invalid-function",
            "function name contains an unsupported character",
            0,
        ));
    }
    let body = &text[first_open + 1..text.len() - 1];
    Ok(FunctionCall {
        name,
        arguments: split_arguments(body, first_open + 1)?,
    })
}

fn split_arguments(body: &str, source_offset: usize) -> Result<Vec<String>, ExplorerParseError> {
    if body.is_empty() {
        return Ok(Vec::new());
    }
    let mut answer = Vec::new();
    let mut depth = 0_i32;
    let mut start = 0;
    for (index, byte) in body.bytes().enumerate() {
        match byte {
            b'(' => depth += 1,
            b')' => {
                depth -= 1;
                if depth < 0 {
                    return Err(ExplorerParseError::new(
                        "expression.unbalanced",
                        "expression contains an unmatched closing parenthesis",
                        source_offset + index,
                    ));
                }
            }
            b',' if depth == 0 => {
                push_argument(&mut answer, body, start, index, source_offset)?;
                start = index + 1;
            }
            _ => {}
        }
    }
    if depth != 0 {
        return Err(ExplorerParseError::new(
            "expression.unbalanced",
            "expression contains an unmatched opening parenthesis",
            source_offset + body.len(),
        ));
    }
    push_argument(&mut answer, body, start, body.len(), source_offset)?;
    Ok(answer)
}

fn push_argument(
    answer: &mut Vec<String>,
    body: &str,
    start: usize,
    end: usize,
    source_offset: usize,
) -> Result<(), ExplorerParseError> {
    if start == end {
        return Err(ExplorerParseError::new(
            "expression.empty-argument",
            "expression contains an empty argument",
            source_offset + start,
        ));
    }
    let value = &body[start..end];
    if value.trim() != value {
        return Err(ExplorerParseError::new(
            "expression.unescaped-whitespace",
            "canonical expressions do not contain unescaped whitespace",
            source_offset + start,
        ));
    }
    answer.push(value.to_owned());
    Ok(())
}

fn require_arity(
    call: &FunctionCall,
    expected: usize,
    code: &'static str,
) -> Result<(), ExplorerParseError> {
    if call.arguments.len() == expected {
        Ok(())
    } else {
        Err(ExplorerParseError::new(
            code,
            format!("{} requires exactly {expected} argument(s)", call.name),
            0,
        ))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn canonical_concrete_paths_match_java_fixtures() {
        let fixtures = [
            ("file:///c:/tmp", "file:///C:/tmp"),
            ("file://SERVER/share/dir", "file://server/share/dir"),
            ("registry://minecraft/item", "registry://minecraft/item/"),
            (
                "registry://minecraft/item/minecraft/stick",
                "registry://minecraft/item/minecraft/stick",
            ),
            ("selection://my%20selection", "selection://my%20selection"),
            (
                "selection://review@revision-7",
                "selection://review@revision-7",
            ),
            ("example://authority", "example://authority"),
            ("example://authority/", "example://authority/"),
            (
                "example://authority/some%20path",
                "example://authority/some%20path",
            ),
        ];
        for (input, expected) in fixtures {
            let parsed = SfmPath::parse(input).expect("fixture parses");
            assert_eq!(parsed.canonical(), expected);
            assert_eq!(SfmPath::parse(expected).expect("canonical parses"), parsed);
        }
    }

    #[test]
    fn kind_scheme_and_trailing_slash_identity_match_java() {
        assert_eq!(
            SfmPath::parse("file:///C:/tmp").expect("file path"),
            SfmPath::parse("file:///C:/tmp/").expect("file path with slash")
        );
        assert_eq!(
            SfmPath::parse("registry://minecraft/item/").expect("registry root"),
            SfmPath::parse("registry://minecraft/item").expect("registry root without slash")
        );
        assert_ne!(
            SfmPath::parse("example://authority").expect("authority only"),
            SfmPath::parse("example://authority/").expect("authority root")
        );
        assert_ne!(
            SfmPath::parse("example://authority/path").expect("contributed path"),
            SfmPath::parse("example://authority/path/").expect("contributed path with slash")
        );

        let mismatch = SfmPath::new(
            SfmPathKind::File,
            "registry",
            "minecraft",
            vec!["item".to_owned()],
            None,
            true,
        )
        .expect_err("kind and scheme must agree");
        assert_eq!(mismatch.code, "path.kind-scheme-mismatch");

        let selection_slash = SfmPath::new(
            SfmPathKind::Selection,
            "selection",
            "review",
            Vec::new(),
            None,
            true,
        )
        .expect_err("selection paths cannot have a trailing slash");
        assert_eq!(selection_slash.code, "path.invalid-selection");

        let normalized_file = SfmPath::new(
            SfmPathKind::File,
            "file",
            "",
            vec!["c:".to_owned(), "tmp".to_owned()],
            None,
            true,
        )
        .expect("valid decoded file components");
        assert!(!normalized_file.trailing_slash());
        assert_eq!(normalized_file.canonical(), "file:///C:/tmp");
        assert_eq!(normalized_file.kind(), SfmPathKind::File);
        assert_eq!(normalized_file.scheme(), "file");
        assert_eq!(normalized_file.authority(), "");
        assert_eq!(normalized_file.segments(), ["C:", "tmp"]);
        assert_eq!(normalized_file.revision(), None);

        let parsed_file = SfmPath::parse("file:///C:/tmp").expect("canonical file");
        assert_eq!(normalized_file, parsed_file);
        assert_eq!(normalized_file.cmp(&parsed_file), Ordering::Equal);
        let ordered = std::collections::BTreeSet::from([normalized_file, parsed_file]);
        assert_eq!(ordered.len(), 1);

        let normalized_unc = SfmPath::new(
            SfmPathKind::File,
            "file",
            "SERVER",
            vec!["share".to_owned(), "dir".to_owned()],
            None,
            false,
        )
        .expect("valid decoded UNC components");
        assert_eq!(
            normalized_unc,
            SfmPath::parse("file://server/share/dir").expect("canonical UNC")
        );
    }

    #[test]
    fn native_normalization_preserves_drive_and_unc_share_roots() {
        assert_eq!(
            normalize_native_segments("C:/folder/../file", 1).expect("within drive root"),
            ["C:", "file"]
        );
        assert_eq!(
            normalize_native_segments("share/folder/../file", 1).expect("within UNC share"),
            ["share", "file"]
        );
        assert_eq!(
            normalize_native_segments("C:/../escape", 1)
                .expect_err("drive root cannot be popped")
                .code,
            "path.native-traversal-above-root"
        );
        assert_eq!(
            normalize_native_segments("share/../escape", 1)
                .expect_err("UNC share cannot be popped")
                .code,
            "path.native-traversal-above-root"
        );
    }

    #[cfg(unix)]
    #[test]
    fn malformed_native_os_string_fails_with_typed_non_utf8_diagnostic() {
        use std::ffi::OsString;
        use std::os::unix::ffi::OsStringExt as _;

        let malformed = OsString::from_vec(vec![b'/', b't', b'm', b'p', b'/', 0xFF]);
        let error = SfmPath::from_native(Path::new(malformed.as_os_str()))
            .expect_err("non-UTF-8 native path must fail closed");

        assert_eq!(error.code, "path.non-utf8-native-path");
    }

    #[cfg(windows)]
    #[test]
    fn native_conversion_cannot_pop_windows_roots() {
        assert_eq!(
            SfmPath::from_native(Path::new(r"C:\..\escape"))
                .expect_err("drive root cannot be popped")
                .code,
            "path.native-traversal-above-root"
        );
        assert_eq!(
            SfmPath::from_native(Path::new(r"\\server\share\..\escape"))
                .expect_err("UNC share cannot be popped")
                .code,
            "path.native-traversal-above-root"
        );
    }

    #[test]
    fn native_conversion_rejects_decoded_separators_and_root_changing_components() {
        for canonical in [
            "file:///C:/safe/..%5Cescape",
            "file:///C:/safe/part%2Fescape",
            "file://server%5Cescape/share",
        ] {
            assert_eq!(
                SfmPath::parse(canonical)
                    .expect("canonical path parses")
                    .to_native_path()
                    .expect_err("native boundary rejects embedded separator")
                    .code,
                "path.native-embedded-separator"
            );
        }
        assert_eq!(
            SfmPath::parse("file:///C:/safe/D%3A/escape")
                .expect("canonical path parses")
                .to_native_path()
                .expect_err("nested drive prefix is rejected")
                .code,
            "path.native-drive-prefix"
        );
    }

    #[cfg(windows)]
    #[test]
    fn native_unicode_paths_round_trip_like_java() {
        let root = tempfile::tempdir().expect("temporary directory");
        let native = root.path().join("folder with spaces").join("π.txt");
        let path = SfmPath::from_native(&native).expect("native path converts");
        assert_eq!(path.kind(), SfmPathKind::File);
        assert!(path.canonical().starts_with("file:///"));
        assert!(path.canonical().contains("folder%20with%20spaces"));
        assert!(path.canonical().contains("%CF%80.txt"));
        assert_eq!(
            path.to_native_path().expect("native representation"),
            native
        );
        assert_eq!(
            SfmPath::parse(&path.canonical()).expect("canonical round trip"),
            path
        );
    }

    #[test]
    fn path_expression_matches_java_fixture() {
        let canonical = concat!(
            "difference(union(file:///C:/a,registry://minecraft/item/),",
            "members(union(name(primary),id(selection-2))))"
        );
        let parsed = PathExpression::parse(canonical).expect("expression parses");
        assert_eq!(parsed.canonical(), canonical);
        assert_eq!(
            PathExpression::parse(canonical).expect("round trip"),
            parsed
        );
    }

    #[test]
    fn children_and_set_operations_are_typed_nodes() {
        let parsed = PathExpression::parse(concat!(
            "intersection(children(file:///C:/repo),",
            "union(file:///C:/repo/a,file:///C:/repo/b))"
        ))
        .expect("typed path expression");
        let PathExpressionNode::Intersection(expressions) = &parsed.node else {
            panic!("expected intersection");
        };
        assert!(matches!(expressions[0], PathExpressionNode::Children(_)));
        assert!(matches!(expressions[1], PathExpressionNode::Union(_)));
    }

    #[test]
    fn selector_domains_and_set_nodes_fail_closed() {
        let selector = EntitySelector::parse(
            SelectorDomain::Explorer,
            "difference(all,union(id(explorer-1),focused))",
        )
        .expect("explorer selector");
        assert_eq!(
            selector.canonical(),
            "difference(all,union(id(explorer-1),focused))"
        );
        assert!(EntitySelector::parse(SelectorDomain::Explorer, "name(wrong)").is_err());
        assert!(EntitySelector::parse(SelectorDomain::Selection, "focused").is_err());
        assert!(EntitySelector::parse(SelectorDomain::Explorer, "union()").is_err());
    }

    #[test]
    fn selector_values_and_case_normalization_match_java() {
        let exact = EntitySelector::exact(SelectorDomain::Explorer, "explorer with spaces")
            .expect("exact selector");
        assert_eq!(exact.canonical(), "id(explorer%20with%20spaces)");
        assert!(exact.is_exact_identity());

        let named = EntitySelector::parse(SelectorDomain::Selection, "name(review%20selection)")
            .expect("named selection");
        assert_eq!(named.canonical(), "name(review%20selection)");

        let case_normalized =
            EntitySelector::parse(SelectorDomain::Explorer, "UNION(id(explorer-1),all)")
                .expect("Java parser normalizes function names");
        assert_eq!(case_normalized.canonical(), "union(id(explorer-1),all)");
    }

    #[test]
    fn malformed_encoding_dot_segments_and_pipe_fail_closed() {
        assert_eq!(
            SfmPath::parse("file:///C:/bad%2")
                .expect_err("invalid percent")
                .code,
            "text.invalid-percent-escape"
        );
        assert_eq!(
            SfmPath::parse("file:///C:/a/../b")
                .expect_err("dot segment")
                .code,
            "path.noncanonical-segment"
        );
        assert_eq!(
            PathExpression::parse("file:///C:/a|file:///C:/b")
                .expect_err("pipe")
                .code,
            "path-expression.pipe-aggregate-forbidden"
        );
        assert_eq!(
            SfmPath::parse("file:///C:/%FF")
                .expect_err("invalid UTF-8")
                .code,
            "text.invalid-utf8"
        );
        assert_eq!(
            SfmPath::parse("File:///C:/tmp")
                .expect_err("uppercase scheme")
                .code,
            "path.noncanonical-scheme"
        );
    }

    #[test]
    fn empty_and_malformed_expressions_fail_closed() {
        assert!(PathExpression::parse("union()").is_err());
        assert!(PathExpression::parse("difference(file:///C:/a)").is_err());
        assert!(PathExpression::parse("children(file:///C:/a,file:///C:/b)").is_err());
        assert!(PathExpression::parse("union(file:///C:/a,)").is_err());
        assert!(EntitySelector::parse(SelectorDomain::Explorer, "difference(all)").is_err());
        assert!(EntitySelector::parse(SelectorDomain::Explorer, "id(raw space)").is_err());
    }

    #[test]
    fn validating_constructors_prevent_invalid_programmatic_asts() {
        assert!(EntitySelector::focused(SelectorDomain::Selection).is_err());
        assert!(EntitySelector::named(SelectorDomain::Explorer, "wrong-domain").is_err());
        assert!(EntitySelector::union(SelectorDomain::Explorer, Vec::new()).is_err());
        assert!(
            EntitySelector::difference(EntitySelector::all(SelectorDomain::Explorer), Vec::new())
                .is_err()
        );
        assert!(
            EntitySelector::union(
                SelectorDomain::Explorer,
                vec![EntitySelector::all(SelectorDomain::Selection)]
            )
            .is_err()
        );

        assert!(PathExpression::members(EntitySelector::all(SelectorDomain::Explorer)).is_err());
        assert!(PathExpression::union(Vec::new()).is_err());
        assert!(
            PathExpression::difference(
                PathExpression::literal(SfmPath::parse("file:///C:/a").expect("literal")),
                Vec::new()
            )
            .is_err()
        );
    }
}
