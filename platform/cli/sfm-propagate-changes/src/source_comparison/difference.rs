use facet::Facet;
#[derive(Clone, Debug, PartialEq, Eq, Facet)]
#[facet(deny_unknown_fields)]
pub struct FileDifference {
    pub path: String,
    pub old_path: Option<String>,
    pub new_path: Option<String>,
    /// added, removed, exact, allowed-byte-difference, moved, or changed.
    pub status: String,
    pub exact_equal: bool,
    pub normalized_equal: bool,
    pub mode_equal: Option<bool>,
    pub transformations: Vec<String>,
    pub diagnostics: Vec<String>,
    /// Text view only; binary evidence remains in the snapshots.
    pub before: Option<String>,
    pub after: Option<String>,
}
