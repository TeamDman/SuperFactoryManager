use crate::artifact_lock::ArtifactLock;
use crate::cancellation::CancellationToken;
use crate::dependency_inventory::DependencyInventory;
use crate::source_cache::GitSourcePaths;
use crate::source_cache::SourceCacheLayout;
use crate::toolchain_lockfile_schema::version::v3::GitSourceDeclarationV3;
use crate::toolchain_lockfile_schema::version::v3::GitSourceDerivedChecksV3;
use crate::toolchain_lockfile_schema::version::v3::GitSourceProviderV3;
use gix::bstr::ByteSlice;
use std::collections::HashSet;
use std::fs;
use std::io;
use std::io::Read;
use std::path::Path;
use std::path::PathBuf;
use std::sync::atomic::AtomicBool;

/// Canonicalizes the HTTPS remote spelling used to select a managed bare repository.
///
/// Credentials are deliberately rejected: lockfiles must be portable and must never retain
/// secrets. The trailing `.git` spelling is not significant for GitHub-style HTTPS remotes.
///
/// # Errors
///
/// Returns an error when the remote is invalid, non-HTTPS, credential-bearing, or incomplete.
pub fn canonical_https_remote(remote: &str) -> eyre::Result<String> {
    let mut url = gix::url::parse(remote.as_bytes().as_bstr())
        .map_err(|error| eyre::eyre!("Invalid Git remote '{remote}': {error}"))?;
    if url.scheme != gix::url::Scheme::Https {
        eyre::bail!("Git source remotes must use HTTPS: {remote}");
    }
    if url.user.is_some() || url.password.is_some() {
        eyre::bail!("Git source remotes must not contain credentials.");
    }
    let host = url
        .host
        .as_mut()
        .ok_or_else(|| eyre::eyre!("Git source remote has no host: {remote}"))?;
    host.make_ascii_lowercase();
    if url.port == Some(443) {
        url.port = None;
    }
    while url.path.ends_with(b"/") {
        let length = url.path.len() - 1;
        url.path.truncate(length);
    }
    if url.path.ends_with(b".git") {
        let length = url.path.len() - b".git".len();
        url.path.truncate(length);
    }
    if url.path.is_empty() || url.path == b"/" {
        eyre::bail!("Git source remote must name a repository: {remote}");
    }
    String::from_utf8(url.to_bstring().to_vec())
        .map_err(|_error| eyre::eyre!("Git source remote is not valid UTF-8: {remote}"))
}

/// Returns the portable shared-repository and commit-tree cache paths for an HTTPS remote.
///
/// # Errors
///
/// Returns an error when the remote cannot be canonicalized safely.
pub fn managed_repository_paths(remote: &str) -> eyre::Result<GitSourcePaths> {
    Ok(SourceCacheLayout::git(
        &canonical_https_remote(remote)?,
        "unresolved",
    ))
}

pub(crate) fn configure_git_sources(
    inventory: &DependencyInventory,
    remote: &str,
    requested_revision: &str,
    roots: Vec<String>,
    cancellation_token: &CancellationToken,
) -> eyre::Result<GitSourceProviderV3> {
    let remote = canonical_https_remote(remote)?;
    let repository_path = SourceCacheLayout::git(&remote, "unresolved").repository;
    let repository_path = inventory.local_path(&repository_path);
    let commit = resolve_git_revision(
        &remote,
        &repository_path,
        requested_revision,
        cancellation_token,
    )?;
    let paths = SourceCacheLayout::git(&remote, &commit);
    let provider = GitSourceProviderV3 {
        id: "git".to_owned(),
        declaration: GitSourceDeclarationV3 {
            remote_url: remote,
            requested_revision: requested_revision.to_owned(),
            roots,
        },
        derived_checks: GitSourceDerivedChecksV3 {
            commit,
            repository_cache_path: paths.repository,
            tree_cache_path: paths.tree,
        },
    };
    acquire_locked_git_sources(inventory, &provider, cancellation_token)?;
    Ok(provider)
}

/// Validates a Git source revision that may be an immutable commit or explicit tag reference.
///
/// # Errors
///
/// Returns an error if `revision` is neither a full SHA-1 commit nor a `refs/tags/...` reference.
pub(crate) fn validate_requested_git_revision(revision: &str) -> eyre::Result<()> {
    let _ = requested_git_revision(revision)?;
    Ok(())
}

enum RequestedGitRevision<'a> {
    Commit(&'a str),
    Tag(&'a str),
}

fn requested_git_revision(revision: &str) -> eyre::Result<RequestedGitRevision<'_>> {
    if is_exact_git_commit(revision) {
        return Ok(RequestedGitRevision::Commit(revision));
    }
    let tag = revision.strip_prefix("refs/tags/").ok_or_else(|| {
        eyre::eyre!(
            "--git-revision must be an exact 40-character Git commit or an explicit refs/tags/... reference."
        )
    })?;
    if tag.is_empty()
        || tag.contains('\\')
        || tag
            .split('/')
            .any(|part| part.is_empty() || matches!(part, "." | ".."))
    {
        eyre::bail!("--git-revision contains an invalid Git tag reference.");
    }
    Ok(RequestedGitRevision::Tag(revision))
}

fn is_exact_git_commit(value: &str) -> bool {
    value.len() == 40 && value.bytes().all(|byte| byte.is_ascii_hexdigit())
}

fn resolve_git_revision(
    remote: &str,
    repository_path: &Path,
    requested_revision: &str,
    cancellation_token: &CancellationToken,
) -> eyre::Result<String> {
    match requested_git_revision(requested_revision)? {
        RequestedGitRevision::Commit(commit) => {
            open_locked_bare_repository(remote, repository_path, commit, cancellation_token)?;
            Ok(commit.to_ascii_lowercase())
        }
        RequestedGitRevision::Tag(reference_name) => {
            let _lock = acquire_repository_lock(repository_path)?;
            let repository = open_or_clone_bare_repository(remote, repository_path)?;
            fetch_managed_references(&repository, remote, cancellation_token)?;
            cancellation_token.bail_if_cancelled()?;
            let mut reference = repository.find_reference(reference_name).map_err(|error| {
                eyre::eyre!(
                    "Managed repository {} does not contain requested tag {reference_name}: {error}",
                    repository_path.display()
                )
            })?;
            let commit = reference.peel_to_id().map_err(|error| {
                eyre::eyre!(
                    "Requested Git tag {reference_name} does not resolve to a commit: {error}"
                )
            })?;
            let commit = commit.detach();
            repository.find_commit(commit).map_err(|error| {
                eyre::eyre!(
                    "Requested Git tag {reference_name} does not resolve to a commit: {error}"
                )
            })?;
            Ok(commit.to_string())
        }
    }
}

/// Opens one managed bare repository for `remote`, cloning it once when absent, and verifies
/// that the exact locked commit is available in its shared object database.
///
/// # Errors
///
/// Returns an error if cloning/opening fails, the commit is invalid or absent, or cancellation
/// has been requested.
pub fn open_locked_bare_repository(
    remote: &str,
    repository_path: &Path,
    commit: &str,
    cancellation_token: &CancellationToken,
) -> eyre::Result<gix::Repository> {
    let canonical_remote = canonical_https_remote(remote)?;
    open_bare_repository_for_remote(
        &canonical_remote,
        repository_path,
        commit,
        cancellation_token,
    )
}

fn open_bare_repository_for_remote(
    remote: &str,
    repository_path: &Path,
    commit: &str,
    cancellation_token: &CancellationToken,
) -> eyre::Result<gix::Repository> {
    cancellation_token.bail_if_cancelled()?;
    let _lock = acquire_repository_lock(repository_path)?;
    let repository = open_or_clone_bare_repository(remote, repository_path)?;
    let id = gix::hash::ObjectId::from_hex(commit.as_bytes())
        .map_err(|error| eyre::eyre!("Invalid locked Git commit '{commit}': {error}"))?;
    if repository.find_commit(id).is_err() {
        fetch_managed_references(&repository, remote, cancellation_token)?;
    }
    cancellation_token.bail_if_cancelled()?;
    verify_repository_contains_locked_commit(&repository, repository_path, commit)?;
    Ok(repository)
}

fn verify_repository_contains_locked_commit(
    repository: &gix::Repository,
    repository_path: &Path,
    commit: &str,
) -> eyre::Result<()> {
    let id = gix::hash::ObjectId::from_hex(commit.as_bytes())
        .map_err(|error| eyre::eyre!("Invalid locked Git commit '{commit}': {error}"))?;
    repository.find_commit(id).map_err(|error| {
        eyre::eyre!(
            "Managed repository {} does not contain locked commit {commit}: {error}",
            repository_path.display()
        )
    })?;
    Ok(())
}

fn acquire_repository_lock(repository_path: &Path) -> eyre::Result<ArtifactLock> {
    let lock_name = repository_path.file_name().ok_or_else(|| {
        eyre::eyre!(
            "Managed Git repository has no file name: {}",
            repository_path.display()
        )
    })?;
    ArtifactLock::acquire(
        repository_path.with_file_name(format!("{}.lock", lock_name.to_string_lossy())),
        repository_path.display().to_string(),
    )
}

#[cfg(windows)]
fn managed_repository_io_path(repository_path: &Path) -> eyre::Result<PathBuf> {
    let parent = repository_path.parent().ok_or_else(|| {
        eyre::eyre!(
            "Managed Git repository has no parent: {}",
            repository_path.display()
        )
    })?;
    let name = repository_path.file_name().ok_or_else(|| {
        eyre::eyre!(
            "Managed Git repository has no name: {}",
            repository_path.display()
        )
    })?;
    Ok(parent.canonicalize()?.join(name))
}
fn open_or_clone_bare_repository(
    remote: &str,
    repository_path: &Path,
) -> eyre::Result<gix::Repository> {
    // Git's objects/pack files can exceed MAX_PATH even when the repository path does not.
    // Keep the portable cache key unchanged; use a verbatim path only for filesystem I/O.
    #[cfg(windows)]
    let io_path = managed_repository_io_path(repository_path)?;
    #[cfg(not(windows))]
    let io_path = repository_path.to_path_buf();
    if repository_path.is_dir() {
        Ok(gix::open(&io_path).map_err(|error| {
            eyre::eyre!(
                "Failed to open managed bare repository {}: {error}",
                repository_path.display()
            )
        })?)
    } else {
        let mut clone = gix::prepare_clone_bare(remote, &io_path)
            .map_err(|error| eyre::eyre!("Failed to prepare bare clone of {remote}: {error}"))?;
        let interrupted = AtomicBool::new(false);
        let (repository, _) = clone
            .fetch_only(gix::progress::Discard, &interrupted)
            .map_err(|error| eyre::eyre!("Failed to clone {remote}: {error:?}"))?;
        Ok(repository)
    }
}

pub(crate) fn materialize_source_build_checkout(
    remote: &str,
    repository_path: &Path,
    commit: &str,
    checkout: &Path,
    cancellation_token: &CancellationToken,
) -> eyre::Result<()> {
    let repository =
        open_bare_repository_for_remote(remote, repository_path, commit, cancellation_token)?;
    let commit_id = gix::hash::ObjectId::from_hex(commit.as_bytes())?;
    let commit = repository.find_commit(commit_id)?;
    materialize_tree(
        &repository,
        commit.tree_id()?.detach(),
        checkout,
        commit_id.to_string().as_str(),
        &[],
        ExistingTreePolicy::ReplaceMutableCheckout,
        cancellation_token,
    )
}

fn fetch_managed_references(
    repository: &gix::Repository,
    remote_url: &str,
    cancellation_token: &CancellationToken,
) -> eyre::Result<()> {
    cancellation_token.bail_if_cancelled()?;
    let mut remote = repository.remote_at(remote_url).map_err(|error| {
        eyre::eyre!("Failed to prepare managed Git fetch from {remote_url}: {error}")
    })?;
    remote
        .replace_refspecs(
            [
                "+refs/heads/*:refs/remotes/origin/*",
                "+refs/tags/*:refs/tags/*",
            ],
            gix::remote::Direction::Fetch,
        )
        .map_err(|error| eyre::eyre!("Failed to configure managed Git fetch refspec: {error}"))?;
    let connection = remote
        .connect(gix::remote::Direction::Fetch)
        .map_err(|error| {
            eyre::eyre!("Failed to connect managed Git fetch from {remote_url}: {error}")
        })?;
    let interrupted = AtomicBool::new(false);
    let prepared = connection
        .prepare_fetch(
            gix::progress::Discard,
            gix::remote::ref_map::Options::default(),
        )
        .map_err(|error| {
            eyre::eyre!("Failed to prepare managed Git fetch from {remote_url}: {error}")
        })?;
    prepared
        .receive(gix::progress::Discard, &interrupted)
        .map_err(|error| {
            eyre::eyre!("Failed to fetch managed Git repository {remote_url}: {error}")
        })?;
    cancellation_token.bail_if_cancelled()
}

/// Materializes a locked commit into an immutable plain-file tree for source search.
pub(crate) fn acquire_locked_git_sources(
    inventory: &DependencyInventory,
    provider: &GitSourceProviderV3,
    cancellation_token: &CancellationToken,
) -> eyre::Result<()> {
    let repository_path = inventory.local_path(&provider.derived_checks.repository_cache_path);
    let output = inventory.local_path(&provider.derived_checks.tree_cache_path);
    let repository = open_locked_bare_repository(
        &provider.declaration.remote_url,
        &repository_path,
        &provider.derived_checks.commit,
        cancellation_token,
    )?;
    let _tree_lock = acquire_tree_lock(&output)?;
    let commit = gix::hash::ObjectId::from_hex(provider.derived_checks.commit.as_bytes()).map_err(
        |error| {
            eyre::eyre!(
                "Invalid locked Git commit '{}': {error}",
                provider.derived_checks.commit
            )
        },
    )?;
    let commit = repository.find_commit(commit).map_err(|error| {
        eyre::eyre!(
            "Failed to read locked Git commit {}: {error}",
            provider.derived_checks.commit
        )
    })?;
    let tree = commit.tree_id().map_err(|error| {
        eyre::eyre!(
            "Failed to read locked Git commit tree {}: {error}",
            provider.derived_checks.commit
        )
    })?;
    if git_tree_matches_commit(
        &repository,
        tree.detach(),
        &output,
        &provider.derived_checks.commit,
        &provider.declaration.roots,
    )? {
        return Ok(());
    }
    if fs::symlink_metadata(&output).is_ok() {
        eyre::bail!(
            "Existing Git source tree differs from its locked commit; refusing to replace a possible live reader: {}",
            output.display()
        );
    }
    materialize_tree(
        &repository,
        tree.detach(),
        &output,
        &provider.derived_checks.commit,
        &provider.declaration.roots,
        ExistingTreePolicy::RejectSharedSource,
        cancellation_token,
    )
}

#[derive(Clone, Copy, Eq, PartialEq)]
enum ExistingTreePolicy {
    ReplaceMutableCheckout,
    RejectSharedSource,
}

fn acquire_tree_lock(tree: &Path) -> eyre::Result<ArtifactLock> {
    let name = tree
        .file_name()
        .ok_or_else(|| eyre::eyre!("Git source tree has no file name"))?;
    ArtifactLock::acquire(
        tree.with_file_name(format!("{}.lock", name.to_string_lossy())),
        tree.display().to_string(),
    )
}

/// Compare the materialized plain-file tree with the exact locked Git tree. The marker alone is
/// not sufficient: a Java file may have been modified or an unexpected file added after publish.
pub(crate) fn locked_git_tree_matches_commit(
    repository_path: &Path,
    output: &Path,
    commit: &str,
    roots: &[String],
) -> bool {
    let Ok(repository) = gix::open(repository_path) else {
        return false;
    };
    let Ok(commit_id) = gix::hash::ObjectId::from_hex(commit.as_bytes()) else {
        return false;
    };
    let Ok(commit_object) = repository.find_commit(commit_id) else {
        return false;
    };
    let Ok(tree) = commit_object.tree_id() else {
        return false;
    };
    git_tree_matches_commit(&repository, tree.detach(), output, commit, roots).unwrap_or(false)
}

const COMPLETION_FILE: &str = ".sfm-git-commit";

fn materialize_tree(
    repository: &gix::Repository,
    tree: gix::hash::ObjectId,
    output: &Path,
    commit: &str,
    roots: &[String],
    existing_tree_policy: ExistingTreePolicy,
    cancellation_token: &CancellationToken,
) -> eyre::Result<()> {
    let parent = output
        .parent()
        .ok_or_else(|| eyre::eyre!("Git tree has no parent"))?;
    fs::create_dir_all(parent)?;
    let temporary = tempfile::Builder::new()
        .prefix(".sfm-git-")
        .tempdir_in(parent)?;
    let staged = temporary.path().join("tree");
    fs::create_dir(&staged)?;
    let (mut stream, _) = repository
        .worktree_stream(tree)
        .map_err(|error| eyre::eyre!("Failed to stream Git tree: {error}"))?;
    while let Some(mut entry) = stream
        .next_entry()
        .map_err(|error| eyre::eyre!("Failed to read Git tree entry: {error}"))?
    {
        cancellation_token.bail_if_cancelled()?;
        let relative = std::str::from_utf8(entry.relative_path().as_ref())
            .map_err(|_error| eyre::eyre!("Git tree contains a non-UTF-8 source path"))?;
        let path = checked_relative_path(relative)?;
        if path == Path::new(COMPLETION_FILE) {
            eyre::bail!("Git source tree conflicts with its completion marker");
        }
        let destination = staged.join(path);
        if entry.mode.is_tree() {
            fs::create_dir_all(destination)?;
        } else if entry.mode.is_link() || entry.mode.is_commit() {
            eyre::bail!("Git source tree contains a link or submodule: {relative}");
        } else if entry.mode.is_blob() {
            if let Some(parent) = destination.parent() {
                fs::create_dir_all(parent)?;
            }
            let mut file = fs::File::create(destination)?;
            io::copy(&mut entry, &mut file)?;
        } else {
            eyre::bail!("Git source tree contains an unsupported entry: {relative}");
        }
    }
    validate_roots(&staged, roots)?;
    fs::write(staged.join(COMPLETION_FILE), commit)?;
    if let Ok(metadata) = fs::symlink_metadata(output) {
        if existing_tree_policy == ExistingTreePolicy::RejectSharedSource {
            eyre::bail!(
                "Git source tree appeared during publication; refusing to replace it: {}",
                output.display()
            );
        }
        if !metadata.file_type().is_dir() {
            eyre::bail!(
                "Existing source-build checkout is not a plain directory: {}",
                output.display()
            );
        }
        fs::remove_dir_all(output)?;
    }
    fs::rename(staged, output)?;
    Ok(())
}

fn git_tree_matches_commit(
    repository: &gix::Repository,
    source_tree: gix::hash::ObjectId,
    output: &Path,
    commit: &str,
    roots: &[String],
) -> eyre::Result<bool> {
    if !fs::symlink_metadata(output).is_ok_and(|metadata| metadata.file_type().is_dir())
        || !fs::symlink_metadata(output.join(COMPLETION_FILE))
            .is_ok_and(|metadata| metadata.file_type().is_file())
        || !fs::read_to_string(output.join(COMPLETION_FILE)).is_ok_and(|actual| actual == commit)
        || validate_roots(output, roots).is_err()
    {
        return Ok(false);
    }
    let (mut stream, _) = repository.worktree_stream(source_tree)?;
    let mut expected = HashSet::new();
    while let Some(mut entry) = stream
        .next_entry()
        .map_err(|error| eyre::eyre!("Failed to read locked Git tree entry: {error}"))?
    {
        let relative = std::str::from_utf8(entry.relative_path().as_ref())
            .map_err(|_error| eyre::eyre!("Git source tree contains a non-UTF-8 path"))?;
        let path = checked_relative_path(relative)?;
        if path == Path::new(COMPLETION_FILE) {
            return Ok(false);
        }
        for ancestor in path.ancestors().skip(1) {
            if ancestor.as_os_str().is_empty() {
                break;
            }
            expected.insert(ancestor.to_path_buf());
        }
        if !expected.insert(path.clone()) {
            return Ok(false);
        }
        let destination = output.join(path);
        let Ok(metadata) = fs::symlink_metadata(&destination) else {
            return Ok(false);
        };
        if entry.mode.is_tree() {
            if !metadata.file_type().is_dir() {
                return Ok(false);
            }
        } else if entry.mode.is_blob() {
            if !metadata.file_type().is_file() {
                return Ok(false);
            }
            let mut file = fs::File::open(destination)?;
            let mut expected_bytes = [0_u8; 16 * 1024];
            let mut actual_bytes = [0_u8; 16 * 1024];
            loop {
                let count = entry.read(&mut expected_bytes)?;
                if count == 0 {
                    if file.read(&mut actual_bytes)? != 0 {
                        return Ok(false);
                    }
                    break;
                }
                if file.read_exact(&mut actual_bytes[..count]).is_err()
                    || expected_bytes[..count] != actual_bytes[..count]
                {
                    return Ok(false);
                }
            }
        } else {
            return Ok(false);
        }
    }
    let mut pending = vec![output.to_path_buf()];
    while let Some(directory) = pending.pop() {
        for item in fs::read_dir(directory)? {
            let item = item?;
            let path = item.path();
            let relative = path.strip_prefix(output)?;
            if relative == Path::new(COMPLETION_FILE) {
                continue;
            }
            if !expected.contains(relative) {
                return Ok(false);
            }
            let kind = item.file_type()?;
            if kind.is_dir() {
                pending.push(path);
            } else if !kind.is_file() {
                return Ok(false);
            }
        }
    }
    Ok(true)
}

fn checked_relative_path(value: &str) -> eyre::Result<PathBuf> {
    let path = Path::new(value);
    if value.is_empty()
        || value.contains('\\')
        || path.is_absolute()
        || path
            .components()
            .any(|part| !matches!(part, std::path::Component::Normal(_)))
    {
        eyre::bail!("Git source tree contains an unsafe path: {value}");
    }
    Ok(path.to_path_buf())
}

fn validate_roots(tree: &Path, roots: &[String]) -> eyre::Result<()> {
    for root in roots {
        let path = checked_relative_path(root)?;
        if !tree.join(path).is_dir() {
            eyre::bail!("Configured Git source root is missing");
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[cfg(windows)]
    #[test]
    fn managed_bare_repository_supports_long_object_paths() {
        let directory = tempfile::tempdir().unwrap();
        let parent = directory.path().join("a".repeat(85)).join("b".repeat(85));
        fs::create_dir_all(&parent).unwrap();
        let repository_path = parent.join("repo.git");
        let object_path = repository_path
            .join("objects")
            .join("ab")
            .join("c".repeat(38));
        assert!(object_path.to_string_lossy().len() > 260);
        let io_path = managed_repository_io_path(&repository_path).unwrap();
        assert!(io_path.to_string_lossy().starts_with(r"\\?\"));
        let repository = gix::init_bare(&io_path).unwrap();
        let blob = repository.write_blob(b"locked source").unwrap();
        assert_eq!(
            repository.find_blob(blob.detach()).unwrap().data,
            b"locked source"
        );
        let reopened = gix::open(&io_path).unwrap();
        assert!(reopened.find_blob(blob.detach()).is_ok());
    }
    #[test]
    fn canonical_https_spelling_shares_one_bare_repository_path() {
        let first = canonical_https_remote("https://GitHub.com/CC-Tweaked/CC-Tweaked.git").unwrap();
        let second = canonical_https_remote("https://github.com/CC-Tweaked/CC-Tweaked").unwrap();
        let trailing_slash =
            canonical_https_remote("https://github.com/CC-Tweaked/CC-Tweaked.git/").unwrap();
        assert_eq!(first, "https://github.com/CC-Tweaked/CC-Tweaked");
        assert_eq!(first, second);
        assert_eq!(first, trailing_slash);
        assert_eq!(
            managed_repository_paths("https://GitHub.com/CC-Tweaked/CC-Tweaked.git")
                .unwrap()
                .repository,
            managed_repository_paths("https://github.com/CC-Tweaked/CC-Tweaked")
                .unwrap()
                .repository
        );
    }

    #[test]
    fn canonicalization_rejects_nonportable_or_non_https_remotes() {
        let _error = canonical_https_remote("git@github.com:CC-Tweaked/CC-Tweaked.git")
            .expect_err("SSH remote must be rejected");
        let _error = canonical_https_remote("https://token@example.com/owner/repo.git")
            .expect_err("credential-bearing remote must be rejected");
        let _error = canonical_https_remote("https://example.com/")
            .expect_err("remote without repository path must be rejected");
    }

    #[test]
    fn requested_revisions_accept_exact_commits_and_explicit_tags_only() {
        assert!(matches!(
            requested_git_revision("f9bb1b497964cccab6cde34e8948333210275f93"),
            Ok(RequestedGitRevision::Commit(_))
        ));
        assert!(matches!(
            requested_git_revision("refs/tags/v1.19.2-1.101.3"),
            Ok(RequestedGitRevision::Tag(_))
        ));
        assert!(requested_git_revision("v1.19.2-1.101.3").is_err());
        assert!(requested_git_revision("refs/tags/../escape").is_err());
    }

    #[test]
    fn managed_repository_requires_the_exact_locked_commit() {
        let directory = tempfile::tempdir().unwrap();
        let repository = gix::init_bare(directory.path()).unwrap();
        let error = verify_repository_contains_locked_commit(
            &repository,
            directory.path(),
            "0000000000000000000000000000000000000000",
        )
        .expect_err("empty repository cannot satisfy a locked commit");
        assert!(
            error.to_string().contains("does not contain locked commit"),
            "unexpected error: {error:?}"
        );
    }

    #[test]
    fn locked_source_tree_rejects_tampering_and_preserves_a_held_reader() {
        use gix::objs::tree::EntryKind;

        let directory = tempfile::tempdir().unwrap();
        let repository = gix::init_bare(directory.path().join("repo")).unwrap();
        let blob = repository
            .write_blob(b"class Example {}\n")
            .unwrap()
            .detach();
        let tree = repository
            .write_object(gix::objs::Tree {
                entries: vec![gix::objs::tree::Entry {
                    mode: EntryKind::Blob.into(),
                    filename: "Example.java".into(),
                    oid: blob,
                }],
            })
            .unwrap()
            .detach();
        let bytes = format!(
            "tree {tree}\nauthor Test <test@example.invalid> 1 +0000\ncommitter Test <test@example.invalid> 1 +0000\n\nsource\n"
        );
        let commit = repository
            .write_object(
                gix::objs::CommitRef::from_bytes(bytes.as_bytes(), repository.object_hash())
                    .unwrap(),
            )
            .unwrap()
            .detach()
            .to_string();
        let output = directory.path().join("tree");
        materialize_tree(
            &repository,
            tree,
            &output,
            &commit,
            &[],
            ExistingTreePolicy::RejectSharedSource,
            &CancellationToken::new(),
        )
        .unwrap();
        assert!(locked_git_tree_matches_commit(
            &directory.path().join("repo"),
            &output,
            &commit,
            &[],
        ));

        let held = fs::File::open(output.join("Example.java")).unwrap();
        fs::write(output.join("Example.java"), b"changed\n").unwrap();
        assert!(!locked_git_tree_matches_commit(
            &directory.path().join("repo"),
            &output,
            &commit,
            &[],
        ));
        let error = materialize_tree(
            &repository,
            tree,
            &output,
            &commit,
            &[],
            ExistingTreePolicy::RejectSharedSource,
            &CancellationToken::new(),
        )
        .expect_err("an existing tree with a held reader must not be replaced");
        assert!(error.to_string().contains("refusing to replace"));
        assert_eq!(fs::read(output.join("Example.java")).unwrap(), b"changed\n");
        drop(held);

        fs::write(output.join("Example.java"), b"class Example {}\n").unwrap();
        fs::write(output.join("Unexpected.java"), b"extra").unwrap();
        assert!(!locked_git_tree_matches_commit(
            &directory.path().join("repo"),
            &output,
            &commit,
            &[],
        ));
    }

    #[test]
    #[ignore = "requires network access to the public Mekanism repository"]
    fn existing_bare_repository_fetches_a_missing_locked_commit() {
        let directory =
            tempfile::tempdir_in(std::env::current_dir().expect("workspace path")).unwrap();
        gix::init_bare(directory.path()).unwrap();
        let commit = "f33ff1f438caa55d58ef1f0a08091997353afcb8";
        let repository = open_locked_bare_repository(
            "https://github.com/mekanism/Mekanism.git",
            directory.path(),
            commit,
            &CancellationToken::new(),
        )
        .expect("managed fetch should populate an existing bare repository");
        repository
            .find_commit(gix::hash::ObjectId::from_hex(commit.as_bytes()).unwrap())
            .expect("fetched exact locked commit");
    }
}
