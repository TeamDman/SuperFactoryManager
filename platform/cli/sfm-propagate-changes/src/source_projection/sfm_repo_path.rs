use super::candidate_lock::checked_directory;
use super::candidate_lock::checked_file;
use super::projection_definitions::CATALOG_PATH;
use super::projection_definitions::ProjectionDefinitions;
use super::projections_document::ProjectionsDocument;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use eyre::eyre;
use gix::discover as discover_repository;
use std::fs;
use std::io::Read as _;
use std::ops::Deref;
use std::path::Path;
use std::path::PathBuf;
use std::str::from_utf8;

pub const MAX_CATALOG_INPUT_BYTES: u64 = 1024 * 1024;

/// Canonical path to the checkout selected for an SFM operation.
///
/// The path is checked independently of the checkout's metadata.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct SfmRepoPath(PathBuf);

impl SfmRepoPath {
    /// Resolve the exact projection key of an existing generated project.
    pub(crate) fn for_projection(project_root: &Path) -> Result<(Self, String)> {
        let (repo, project_root) = Self::containing_projections_file(project_root)?;
        let key = repo
            .read_projections()?
            .key_for_project(&repo, &project_root)?
            .to_owned();
        Ok((repo, key))
    }

    pub(super) fn containing_projections_file(project_root: &Path) -> Result<(Self, PathBuf)> {
        let project_root = checked_directory(project_root)?;
        let ancestor = project_root
            .ancestors()
            .find(|ancestor| ancestor.join(CATALOG_PATH).is_file())
            .ok_or_else(|| eyre!("project has no containing checkout projections catalog"))?;
        let repo = Self::try_from(ancestor)?;
        Ok((repo, project_root))
    }

    /// Read projection names and layout from the checkout's projections file.
    pub(crate) fn read_projections(&self) -> Result<ProjectionDefinitions> {
        let bytes = self.read_metadata(CATALOG_PATH, MAX_CATALOG_INPUT_BYTES)?;
        let text = from_utf8(&bytes).wrap_err("projections catalog is not UTF-8")?;
        let ProjectionsDocument(definitions) = ProjectionsDocument::from_json(text)?;
        definitions.validate_layout()?;
        Ok(definitions)
    }

    /// Resolve an explicit path or discover the enclosing Git worktree.
    ///
    /// # Errors
    /// Rejects a missing worktree or an unsafe or inaccessible directory.
    pub(crate) fn discover(repo_root: Option<&Path>, invocation_dir: &Path) -> Result<Self> {
        if let Some(root) = repo_root {
            return Self::resolve(root, invocation_dir);
        }
        ensure!(
            invocation_dir.is_absolute(),
            "SFM checkout discovery requires an absolute invocation directory"
        );
        let repository = discover_repository(invocation_dir)?;
        let root = repository
            .workdir()
            .ok_or_else(|| eyre!("projection selection requires a repository worktree"))?;
        Self::resolve(root, invocation_dir)
    }

    /// Resolve a relative checkout argument from the explicit invocation directory.
    ///
    /// # Errors
    /// Rejects a missing, unsafe or inaccessible checkout path.
    pub fn resolve(repo_root: &Path, invocation_dir: &Path) -> Result<Self> {
        let candidate = if repo_root == Path::new(".") {
            invocation_dir.to_path_buf()
        } else if repo_root.is_absolute() {
            repo_root.to_path_buf()
        } else {
            invocation_dir.join(repo_root)
        };
        Self::try_from(candidate.as_path())
    }

    #[must_use]
    pub fn as_path(&self) -> &Path {
        self.0.as_path()
    }

    /// Read one bounded metadata file relative to this checked checkout.
    ///
    /// # Errors
    /// Rejects unsafe paths, missing files, I/O failures and oversized inputs.
    pub fn read_metadata(&self, relative: &str, limit: u64) -> Result<Vec<u8>> {
        read_bounded_input(self.as_path(), relative, limit)
    }
}

impl TryFrom<&Path> for SfmRepoPath {
    type Error = eyre::Report;

    fn try_from(path: &Path) -> Result<Self> {
        checked_directory(path)
            .map(Self)
            .wrap_err("invalid source repository root")
    }
}

impl AsRef<Path> for SfmRepoPath {
    fn as_ref(&self) -> &Path {
        self.as_path()
    }
}

impl Deref for SfmRepoPath {
    type Target = Path;

    fn deref(&self) -> &Self::Target {
        self.as_path()
    }
}

impl From<SfmRepoPath> for PathBuf {
    fn from(path: SfmRepoPath) -> Self {
        path.0
    }
}

pub(crate) fn read_bounded_input(root: &Path, relative: &str, limit: u64) -> Result<Vec<u8>> {
    let path = checked_file(root, relative)?;
    let file =
        fs::File::open(&path).wrap_err_with(|| format!("cannot open source input `{relative}`"))?;
    ensure!(
        file.metadata()?.len() <= limit,
        "source input `{relative}` exceeds the {limit}-byte limit"
    );
    let mut bytes = Vec::new();
    file.take(limit.saturating_add(1))
        .read_to_end(&mut bytes)
        .wrap_err_with(|| format!("cannot read source input `{relative}`"))?;
    ensure!(
        bytes.len() as u64 <= limit,
        "source input `{relative}` exceeds the {limit}-byte limit"
    );
    Ok(bytes)
}
