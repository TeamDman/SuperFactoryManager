//! Read-only local tag preflight for one target of a verified ten-JAR package.

use super::release_inventory_cli::ReleaseInventoryTarget;
use super::release_package_verify_cli::ReleasePackageVerifyArgs;
use super::release_package_verify_cli::VerifiedReleasePackage;
use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use crate::source_projection::candidate_lock::checked_directory;
use crate::source_projection::candidate_lock::checked_file;
use crate::source_projection::manifest::SourceProjectionManifest;
use crate::source_projection::promotion::ensure_closed_destination_inputs;
use crate::source_projection::promotion::validate_relative_path;
use crate::source_projection::provenance::ProjectionProvenance;
use crate::source_projection::provenance::sha256;
use crate::source_projection::sync::MANIFEST_FILE;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;
use figue as args;
use std::collections::BTreeSet;
use std::fs;
use std::path::Path;
use std::path::PathBuf;
use std::process::Command;
use std::process::Output;

const REPORT_SCHEMA: &str = "sfm:source_release_tag_preflight@1";
const REPORT_SCOPE: &str = "read-only local preflight for one verified package target; no credentials, remote checks, tag creation, upload or publication";
const SOURCE_DEFINITION: &str = "platform/minecraft/source-projection.json";
const GENERATED_ROOTS: &str = "platform/minecraft/mc-version";

#[derive(Clone, Debug, Facet)]
pub struct ReleaseTagPreflightArgs {
    /// Git repository root containing the reviewed promoted release commit.
    #[facet(args::named)]
    pub repo_root: PathBuf,
    /// Absolute root of the still-complete ten-JAR package.
    #[facet(args::named)]
    pub package_root: PathBuf,
    /// Separately reviewed SHA-256 of the package completion manifest.
    #[facet(args::named)]
    pub completion_manifest_sha256: String,
    /// One exact projection target ID, such as 1.21.0 (not its MC filename marker).
    #[facet(args::named)]
    pub target_id: String,
    /// Explicitly reviewed, post-promotion release commit, not the package source commit.
    #[facet(args::named)]
    pub reviewed_release_commit: String,
}

#[derive(Debug, Facet)]
#[expect(
    clippy::struct_excessive_bools,
    reason = "independent local verification and explicit remote/publication limits belong in the report"
)]
pub(super) struct ReleaseTagPreflightReport {
    schema: String,
    scope: String,
    pub(super) completion_manifest_sha256: String,
    pub(super) inventory_sha256: String,
    pub(super) package_source_commit: String,
    pub(super) reviewed_release_commit: String,
    pub(super) current_head_checked: bool,
    pub(super) authored_tree_unchanged_outside_generated_roots: bool,
    pub(super) selected_root_provenance_checked: bool,
    pub(super) selected_root_commit_tree_checked: bool,
    pub(super) target_id: String,
    pub(super) minecraft_version: String,
    pub(super) loader: String,
    pub(super) file_name: String,
    pub(super) jar_sha256: String,
    pub(super) local_tag: String,
    pub(super) local_tag_state: String,
    pub(super) local_tag_object_id: Option<String>,
    pub(super) remote_tag_checked: bool,
    pub(super) publication_authorized: bool,
}

impl ReleaseTagPreflightArgs {
    /// Reverify the whole package and inspect one target's committed local release state.
    ///
    /// # Errors
    ///
    /// Rejects changed package bytes, a stale or unpromoted checkout, edited
    /// generated output, and a local tag that points to another commit.
    pub(super) fn invoke_in(self, cancellation: &CancellationToken) -> Result<CliOutput> {
        Ok(CliOutput::facet(self.preflight_in(cancellation)?))
    }

    pub(super) fn preflight_in(
        self,
        cancellation: &CancellationToken,
    ) -> Result<ReleaseTagPreflightReport> {
        cancellation.bail_if_cancelled()?;
        ensure_commit(&self.reviewed_release_commit, "reviewed release commit")?;
        let verified = ReleasePackageVerifyArgs {
            package_root: self.package_root,
            completion_manifest_sha256: self.completion_manifest_sha256,
        }
        .verify_in(cancellation)?;
        let target = verified
            .inventory
            .targets
            .iter()
            .find(|target| target.target_id == self.target_id)
            .ok_or_else(|| eyre::eyre!("unknown verified package target '{}'", self.target_id))?;
        let packaged = verified
            .manifest
            .targets
            .iter()
            .find(|packaged| packaged.target_id == self.target_id)
            .ok_or_else(|| eyre::eyre!("verified package omitted target '{}'", self.target_id))?;

        let root = checked_directory(&self.repo_root)?;
        let source_commit = &verified.manifest.source_commit;
        let head =
            ensure_reviewed_release_checkout(&root, &self.reviewed_release_commit, source_commit)?;
        ensure_selected_promoted_root(&root, &head, &verified, target, cancellation)?;

        let local_tag = format!("{}-{}", verified.manifest.mod_version, self.target_id);
        let (local_tag_state, local_tag_object_id) = inspect_local_tag(&root, &local_tag, &head)?;
        ensure_unconcealed_index(&root)?;
        ensure!(
            git_text(
                &root,
                &["status", "--porcelain=v1", "--untracked-files=all"]
            )?
            .is_empty(),
            "reviewed release worktree changed during local tag preflight"
        );
        ensure!(
            git_text(&root, &["rev-parse", "HEAD"])? == head,
            "current HEAD changed during local tag preflight"
        );

        Ok(ReleaseTagPreflightReport {
            schema: REPORT_SCHEMA.to_owned(),
            scope: REPORT_SCOPE.to_owned(),
            completion_manifest_sha256: verified.completion_manifest_sha256,
            inventory_sha256: verified.manifest.inventory_sha256,
            package_source_commit: source_commit.clone(),
            reviewed_release_commit: head,
            current_head_checked: true,
            authored_tree_unchanged_outside_generated_roots: true,
            selected_root_provenance_checked: true,
            selected_root_commit_tree_checked: true,
            target_id: self.target_id,
            minecraft_version: target.minecraft_version.clone(),
            loader: target.loader.clone(),
            file_name: packaged.file_name.clone(),
            jar_sha256: packaged.sha256.clone(),
            local_tag,
            local_tag_state,
            local_tag_object_id,
            remote_tag_checked: false,
            publication_authorized: false,
        })
    }
}

fn ensure_reviewed_release_checkout(
    root: &Path,
    reviewed_release_commit: &str,
    package_source_commit: &str,
) -> Result<String> {
    let top = git_text(root, &["rev-parse", "--show-toplevel"])?;
    ensure!(
        checked_directory(Path::new(&top))? == root,
        "--repo-root must be the Git worktree root"
    );
    let head = git_text(root, &["rev-parse", "HEAD"])?;
    ensure!(
        head == reviewed_release_commit,
        "current HEAD differs from --reviewed-release-commit"
    );
    ensure_unconcealed_index(root)?;
    ensure!(
        git_text(root, &["status", "--porcelain=v1", "--untracked-files=all"])?.is_empty(),
        "reviewed release worktree is not clean"
    );
    let common_dir = git_text(root, &["rev-parse", "--git-common-dir"])?;
    let common_dir = Path::new(&common_dir);
    let common_dir = if common_dir.is_absolute() {
        common_dir.to_path_buf()
    } else {
        root.join(common_dir)
    };
    ensure!(
        fs::symlink_metadata(common_dir.join("info/grafts"))
            .is_err_and(|error| error.kind() == std::io::ErrorKind::NotFound),
        "repository-local Git grafts are unsupported for release ancestry"
    );
    ensure!(
        package_source_commit != head,
        "reviewed release commit must follow the package source commit after promotion"
    );
    ensure!(
        git_bool(
            root,
            &["merge-base", "--is-ancestor", package_source_commit, &head]
        )?,
        "package source commit is not an ancestor of reviewed release commit"
    );
    // This conservative first contract binds every committed authored input,
    // including any outside the known projection directories. It may reject
    // unrelated release-note edits made after packaging.
    ensure!(
        git_bool(
            root,
            &[
                "diff",
                "--no-ext-diff",
                "--quiet",
                package_source_commit,
                &head,
                "--",
                ".",
                ":(exclude)platform/minecraft/mc-version",
            ],
        )?,
        "committed non-generated files changed since package source commit"
    );
    Ok(head)
}

fn ensure_unconcealed_index(root: &Path) -> Result<()> {
    let entries = git_bytes(root, &["ls-files", "--cached", "-v", "-z"])?;
    ensure!(
        entries.last() == Some(&0),
        "reviewed release index has no terminal NUL"
    );
    for entry in entries[..entries.len() - 1].split(|byte| *byte == 0) {
        ensure!(
            entry.len() >= 3 && entry[1] == b' ',
            "reviewed release index has a malformed tracked entry"
        );
        let relative = std::str::from_utf8(&entry[2..])?;
        ensure!(
            entry[0] == b'H',
            "reviewed release index has a concealed or non-normal tracked entry '{relative}'"
        );
    }
    Ok(())
}

fn ensure_selected_promoted_root(
    root: &Path,
    head: &str,
    verified: &VerifiedReleasePackage,
    target: &ReleaseInventoryTarget,
    cancellation: &CancellationToken,
) -> Result<()> {
    let definition_path = checked_file(root, SOURCE_DEFINITION)?;
    let definition = fs::read(definition_path)?;
    ensure!(
        sha256(&definition) == verified.inventory.source_manifest_sha256
            && definition == git_revision_file(root, head, SOURCE_DEFINITION)?,
        "current source definition differs from package or reviewed release HEAD"
    );
    ensure_tracked_worktree_unchanged(root, SOURCE_DEFINITION)?;
    let definition = SourceProjectionManifest::from_json(std::str::from_utf8(&definition)?)?;
    let declared = definition.target(&target.target_id)?;
    let project_dir = format!("{GENERATED_ROOTS}/{}", target.target_id);
    validate_relative_path(&project_dir)?;
    ensure!(
        declared.project_dir == project_dir
            && declared.minecraft_version == target.minecraft_version
            && declared.loader == target.loader,
        "reviewed source definition differs from verified package target mapping"
    );
    ensure_tracked_worktree_unchanged(root, &project_dir)?;
    let project_root = checked_directory(&root.join(&project_dir))?;
    let manifest_relative = format!("{project_dir}/{MANIFEST_FILE}");
    let manifest_bytes = fs::read(checked_file(root, &manifest_relative)?)?;
    ensure!(
        sha256(&manifest_bytes) == target.provenance_manifest_sha256
            && manifest_bytes == git_revision_file(root, head, &manifest_relative)?,
        "selected checked-in provenance differs from package or reviewed release HEAD"
    );
    let provenance = ProjectionProvenance::from_json(std::str::from_utf8(&manifest_bytes)?)?;
    ensure!(
        provenance.target_id == target.target_id
            && provenance.minecraft_version == target.minecraft_version
            && provenance.preset_id == verified.manifest.candidate_preset_id
            && provenance.preset_definition_identity
                == verified.manifest.candidate_definition_identity
            && provenance.files.contains_key("gradle.properties"),
        "selected checked-in provenance identity differs from verified package"
    );
    ensure_committed_owned_root(root, head, &project_dir, &provenance, cancellation)?;
    ensure_closed_destination_inputs(&project_root, &provenance)?;
    for (relative, record) in &provenance.files {
        cancellation.bail_if_cancelled()?;
        let bytes = fs::read(checked_file(&project_root, relative)?)?;
        ensure!(
            sha256(&bytes) == record.output_sha256,
            "selected checked-in output differs from provenance at '{relative}'"
        );
    }
    Ok(())
}

fn ensure_committed_owned_root(
    root: &Path,
    head: &str,
    project_dir: &str,
    provenance: &ProjectionProvenance,
    cancellation: &CancellationToken,
) -> Result<()> {
    let mut expected = BTreeSet::from([format!("{project_dir}/{MANIFEST_FILE}")]);
    for relative in provenance.files.keys() {
        validate_relative_path(relative)?;
        expected.insert(format!("{project_dir}/{relative}"));
    }
    let tree = git_bytes(root, &["ls-tree", "-r", "-z", head, "--", project_dir])?;
    ensure!(
        tree.last() == Some(&0),
        "selected release tree has no terminal NUL"
    );
    let mut committed = BTreeSet::new();
    let mut casefold = BTreeSet::new();
    for entry in tree[..tree.len() - 1].split(|byte| *byte == 0) {
        cancellation.bail_if_cancelled()?;
        let separator = entry
            .iter()
            .position(|byte| *byte == b'\t')
            .ok_or_else(|| eyre::eyre!("malformed selected release tree entry"))?;
        let (metadata, path_with_tab) = entry.split_at(separator);
        let path = &path_with_tab[1..];
        let metadata = std::str::from_utf8(metadata)?;
        let mut fields = metadata.split(' ');
        let (Some(mode), Some(kind), Some(object), None) =
            (fields.next(), fields.next(), fields.next(), fields.next())
        else {
            eyre::bail!("malformed selected release tree metadata");
        };
        ensure!(
            kind == "blob" && matches!(mode, "100644" | "100755"),
            "selected release tree contains a non-regular owned input"
        );
        ensure!(
            matches!(object.len(), 40 | 64) && object.bytes().all(|byte| byte.is_ascii_hexdigit()),
            "selected release tree contains an invalid blob identity"
        );
        let path = std::str::from_utf8(path)?;
        ensure!(
            path.starts_with(&format!("{project_dir}/")),
            "selected release tree escaped its project root"
        );
        let relative = &path[project_dir.len() + 1..];
        validate_relative_path(relative)?;
        ensure!(
            casefold.insert(path.to_lowercase()) && committed.insert(path.to_owned()),
            "selected release tree has a duplicate or case-only input path"
        );
        if relative == "gradlew" {
            ensure!(
                mode == "100755",
                "selected release Gradle wrapper is not executable"
            );
        }
    }
    ensure!(
        committed == expected,
        "selected release HEAD tree does not exactly match provenance-owned paths"
    );
    for (relative, record) in &provenance.files {
        cancellation.bail_if_cancelled()?;
        let committed_path = format!("{project_dir}/{relative}");
        let bytes = git_revision_file(root, head, &committed_path)?;
        ensure!(
            sha256(&bytes) == record.output_sha256,
            "selected release HEAD blob differs from provenance at '{relative}'"
        );
    }
    Ok(())
}

fn inspect_local_tag(root: &Path, local_tag: &str, head: &str) -> Result<(String, Option<String>)> {
    let tag_ref = format!("refs/tags/{local_tag}");
    ensure!(
        git_output(root, &["check-ref-format", &tag_ref])?
            .status
            .success(),
        "derived local tag has an invalid Git ref name"
    );
    let existence = git_output(root, &["show-ref", "--exists", &tag_ref])?;
    match existence.status.code() {
        Some(0) => {}
        Some(2) => {
            ensure!(
                git_output(root, &["show-ref", "--exists", &tag_ref])?
                    .status
                    .code()
                    == Some(2),
                "local tag changed during preflight"
            );
            return Ok(("absent".to_owned(), None));
        }
        _ => eyre::bail!(
            "cannot inspect local tag '{local_tag}': {}",
            String::from_utf8_lossy(&existence.stderr).trim()
        ),
    }
    let object = git_text(root, &["rev-parse", "--verify", &tag_ref])?;
    inspect_captured_local_tag(root, local_tag, head, object)
}

fn inspect_captured_local_tag(
    root: &Path,
    local_tag: &str,
    head: &str,
    object: String,
) -> Result<(String, Option<String>)> {
    ensure_commit(&object, "local tag object")?;
    let peeled = git_text(
        root,
        &["rev-parse", "--verify", &format!("{object}^{{commit}}")],
    )?;
    ensure!(
        peeled == head,
        "local tag '{local_tag}' does not point to reviewed release commit"
    );
    ensure!(
        git_text(
            root,
            &["rev-parse", "--verify", &format!("refs/tags/{local_tag}")]
        )? == object,
        "local tag '{local_tag}' changed during preflight"
    );
    Ok(("matches-reviewed-release-commit".to_owned(), Some(object)))
}

fn ensure_commit(value: &str, label: &str) -> Result<()> {
    ensure!(
        value.len() == 40
            && value
                .bytes()
                .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte)),
        "invalid {label}"
    );
    Ok(())
}

fn ensure_tracked_worktree_unchanged(root: &Path, relative: &str) -> Result<()> {
    ensure!(
        git_bool(root, &["diff", "--no-ext-diff", "--quiet", "--", relative])?
            && git_bool(
                root,
                &[
                    "diff",
                    "--no-ext-diff",
                    "--cached",
                    "--quiet",
                    "HEAD",
                    "--",
                    relative
                ]
            )?,
        "staged or unstaged checked-in release input differs from HEAD at '{relative}'"
    );
    Ok(())
}

fn git_revision_file(root: &Path, revision: &str, relative: &str) -> Result<Vec<u8>> {
    validate_relative_path(relative)?;
    git_bytes(root, &["show", &format!("{revision}:{relative}")])
}

fn git_text(root: &Path, args: &[&str]) -> Result<String> {
    Ok(String::from_utf8(git_bytes(root, args)?)?.trim().to_owned())
}

fn git_bytes(root: &Path, args: &[&str]) -> Result<Vec<u8>> {
    let output = git_output(root, args)?;
    ensure!(
        output.status.success(),
        "git {:?} failed: {}",
        args,
        String::from_utf8_lossy(&output.stderr).trim()
    );
    Ok(output.stdout)
}

fn git_bool(root: &Path, args: &[&str]) -> Result<bool> {
    let output = git_output(root, args)?;
    match output.status.code() {
        Some(0) => Ok(true),
        Some(1) => Ok(false),
        _ => eyre::bail!(
            "git {:?} failed: {}",
            args,
            String::from_utf8_lossy(&output.stderr).trim()
        ),
    }
}

fn git_output(root: &Path, args: &[&str]) -> Result<Output> {
    sanitized_git_command(root, &[])
        .args(args)
        .output()
        .wrap_err_with(|| format!("could not run git {args:?}"))
}

fn sanitized_git_command(root: &Path, injected_git_env: &[(&str, &str)]) -> Command {
    let mut command = Command::new("git");
    // Remove inherited repository/index/replace-object overrides. `git -C`
    // alone does not bind probes to the explicitly reviewed worktree.
    for (name, value) in injected_git_env {
        command.env(name, value);
    }
    for (name, _) in std::env::vars_os() {
        if name
            .to_string_lossy()
            .to_ascii_uppercase()
            .starts_with("GIT_")
        {
            command.env_remove(name);
        }
    }
    for (name, _) in injected_git_env {
        command.env_remove(name);
    }
    command
        .env("GIT_NO_REPLACE_OBJECTS", "1")
        .env("GIT_NO_LAZY_FETCH", "1")
        .env("GIT_OPTIONAL_LOCKS", "0")
        .arg("-c")
        .arg("core.fsmonitor=false")
        .arg("-C")
        .arg(root);
    command
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::cli::Cli;
    use crate::cli::Command;
    use crate::cli::output::OutputFormat;
    use crate::cli::source::CandidateVerifyArgs;
    use crate::cli::source::ReleasePackageArgs;
    use crate::cli::source::SourceArgs;
    use crate::cli::source::SourceCommand;
    use crate::cli::source::candidate_lock_cli::verify_candidate_in;
    use crate::cli::source::release_inventory_cli::ReleaseInventory;
    use crate::source_projection::candidate_lock::tests::Fixture;

    fn git(repo: &Path, args: &[&str]) -> String {
        let output = git_output(repo, args).unwrap();
        assert!(
            output.status.success(),
            "git {:?}: {}",
            args,
            String::from_utf8_lossy(&output.stderr)
        );
        String::from_utf8(output.stdout).unwrap().trim().to_owned()
    }

    fn packaged_candidate() -> (Fixture, ReleaseTagPreflightArgs) {
        let fixture = Fixture::new();
        let scratch = fixture.repo().parent().unwrap();
        let lock = scratch.join("reviewed-candidate-lock.json");
        let lock_bytes = facet_json::to_string_pretty(fixture.lock()).unwrap() + "\n";
        fs::write(&lock, &lock_bytes).unwrap();
        let lock_sha256 = sha256(lock_bytes.as_bytes());
        let verified = verify_candidate_in(
            &CancellationToken::new(),
            fixture.repo(),
            fixture.lock(),
            fixture.roots(),
            lock_sha256.clone(),
            None,
        )
        .unwrap();
        let inventory = ReleaseInventory::from_verified(fixture.lock(), &verified).unwrap();
        let inventory_path = scratch.join("frozen-inventory.json");
        fs::write(
            &inventory_path,
            facet_json::to_string_pretty(&inventory).unwrap() + "\n",
        )
        .unwrap();
        let package_root = scratch.join("package");
        ReleasePackageArgs {
            candidate: CandidateVerifyArgs {
                repo_root: fixture.repo().to_path_buf(),
                lock,
                candidate_root: fixture
                    .roots()
                    .iter()
                    .map(|(target, root)| format!("{target}={}", root.display()))
                    .collect(),
            },
            inventory: inventory_path,
            candidate_lock_sha256: lock_sha256,
            output_root: package_root.clone(),
        }
        .invoke_in(&CancellationToken::new(), fixture.repo())
        .unwrap();
        let completion_manifest_sha256 =
            sha256(&fs::read(package_root.join("release-package.json")).unwrap());
        let reviewed_release_commit = git(fixture.repo(), &["rev-parse", "HEAD"]);
        let repo_root = fixture.repo().to_path_buf();
        (
            fixture,
            ReleaseTagPreflightArgs {
                repo_root,
                package_root,
                completion_manifest_sha256,
                target_id: "1.21.0".to_owned(),
                reviewed_release_commit,
            },
        )
    }

    fn promoted_commit(fixture: &Fixture) -> String {
        for (target_id, candidate_root) in fixture.roots() {
            let destination = fixture
                .repo()
                .join(format!("{GENERATED_ROOTS}/{target_id}"));
            let manifest_bytes = fs::read(candidate_root.join(MANIFEST_FILE)).unwrap();
            let manifest =
                ProjectionProvenance::from_json(std::str::from_utf8(&manifest_bytes).unwrap())
                    .unwrap();
            for relative in manifest.files.keys() {
                let output = destination.join(relative);
                fs::create_dir_all(output.parent().unwrap()).unwrap();
                fs::copy(candidate_root.join(relative), output).unwrap();
            }
            fs::write(destination.join(MANIFEST_FILE), manifest_bytes).unwrap();
        }
        git(fixture.repo(), &["add", "--", GENERATED_ROOTS]);
        for (target_id, _) in fixture.roots() {
            git(
                fixture.repo(),
                &[
                    "update-index",
                    "--chmod=+x",
                    "--",
                    &format!("{GENERATED_ROOTS}/{target_id}/gradlew"),
                ],
            );
        }
        git(
            fixture.repo(),
            &["commit", "-qm", "promote synthetic release"],
        );
        git(fixture.repo(), &["rev-parse", "HEAD"])
    }

    #[test]
    fn cli_requires_explicit_target_and_reviewed_release_commit() {
        let parsed = figue::from_slice::<Cli>(&[
            "source",
            "release-tag-preflight",
            "--repo-root",
            "C:/reviewed/repo",
            "--package-root",
            "C:/reviewed/package",
            "--completion-manifest-sha256",
            "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "--target-id",
            "1.21.0",
            "--reviewed-release-commit",
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
        ])
        .into_result()
        .unwrap()
        .get_silent();
        let Command::Source(SourceArgs {
            command: SourceCommand::ReleaseTagPreflight(args),
        }) = parsed.command
        else {
            panic!("expected source release-tag-preflight command");
        };
        assert_eq!(args.target_id, "1.21.0");
        assert!(
            figue::from_slice::<Cli>(&[
                "source",
                "release-tag-preflight",
                "--repo-root",
                "C:/reviewed/repo",
            ])
            .into_result()
            .is_err()
        );
    }

    #[test]
    fn typed_target_has_absent_or_exact_local_tag_without_side_effects() {
        let (fixture, mut args) = packaged_candidate();
        args.repo_root = fixture.repo().to_path_buf();
        assert!(
            args.clone()
                .preflight_in(&CancellationToken::new())
                .unwrap_err()
                .to_string()
                .contains("must follow the package source commit")
        );
        args.reviewed_release_commit = promoted_commit(&fixture);
        let package_entries = fs::read_dir(&args.package_root).unwrap().count();
        let report = args
            .clone()
            .preflight_in(&CancellationToken::new())
            .unwrap();
        assert_eq!(report.target_id, "1.21.0");
        assert_eq!(report.minecraft_version, "1.21");
        assert_eq!(report.local_tag, "4.35.0-1.21.0");
        assert_eq!(report.local_tag_state, "absent");
        assert!(report.selected_root_commit_tree_checked);
        assert!(report.local_tag_object_id.is_none());
        assert!(!report.remote_tag_checked);
        assert!(!report.publication_authorized);
        let rendered = args
            .clone()
            .invoke_in(&CancellationToken::new())
            .unwrap()
            .render(Some(OutputFormat::Json), false)
            .unwrap()
            .unwrap();
        assert!(rendered.contains(REPORT_SCHEMA));
        assert!(rendered.contains("\"local_tag_state\": \"absent\""));
        assert!(!rendered.contains(&fixture.repo().display().to_string()));
        assert!(!rendered.contains(&args.package_root.display().to_string()));
        assert_eq!(
            fs::read_dir(&args.package_root).unwrap().count(),
            package_entries
        );
        assert!(git(fixture.repo(), &["status", "--porcelain"]).is_empty());

        git(
            fixture.repo(),
            &["tag", "4.35.0-1.21.0", &args.reviewed_release_commit],
        );
        let report = args
            .clone()
            .preflight_in(&CancellationToken::new())
            .unwrap();
        assert_eq!(report.local_tag_state, "matches-reviewed-release-commit");
        assert_eq!(
            report.local_tag_object_id,
            Some(args.reviewed_release_commit.clone())
        );

        git(
            fixture.repo(),
            &[
                "tag",
                "-a",
                "4.35.0-1.19.2",
                "-m",
                "synthetic annotated release tag",
                &args.reviewed_release_commit,
            ],
        );
        args.target_id = "1.19.2".to_owned();
        let annotated = args
            .clone()
            .preflight_in(&CancellationToken::new())
            .unwrap();
        assert_eq!(annotated.local_tag_state, "matches-reviewed-release-commit");
        assert_ne!(
            annotated.local_tag_object_id,
            Some(args.reviewed_release_commit.clone())
        );

        git(
            fixture.repo(),
            &["tag", "4.35.0-1.20.1", &fixture.lock().source_commit],
        );
        args.target_id = "1.20.1".to_owned();
        assert!(
            args.clone()
                .preflight_in(&CancellationToken::new())
                .unwrap_err()
                .to_string()
                .contains("does not point to reviewed release commit")
        );
        args.target_id = "unknown".to_owned();
        assert!(args.preflight_in(&CancellationToken::new()).is_err());
    }

    #[test]
    fn rejects_changed_package_generated_inputs_and_authored_tree() {
        let (fixture, mut args) = packaged_candidate();
        args.repo_root = fixture.repo().to_path_buf();
        args.reviewed_release_commit = promoted_commit(&fixture);
        let candidate = fixture.roots()["1.21.0"].join("src/main/java/Candidate.java");
        let generated = fixture
            .repo()
            .join("platform/minecraft/mc-version/1.21.0/src/main/java/Candidate.java");
        fs::write(&generated, b"changed output\n").unwrap();
        assert!(
            args.clone()
                .preflight_in(&CancellationToken::new())
                .is_err()
        );
        fs::copy(candidate, &generated).unwrap();

        let unrelated = fixture.repo().join("untracked-review-note.txt");
        fs::write(&unrelated, b"uncommitted note\n").unwrap();
        assert!(
            args.clone()
                .preflight_in(&CancellationToken::new())
                .unwrap_err()
                .to_string()
                .contains("worktree is not clean")
        );
        fs::remove_file(unrelated).unwrap();

        let extra = generated.parent().unwrap().join("Unowned.java");
        fs::write(&extra, b"class Unowned {}\n").unwrap();
        git(fixture.repo(), &["add", "--", GENERATED_ROOTS]);
        git(fixture.repo(), &["commit", "-qm", "commit unowned input"]);
        args.reviewed_release_commit = git(fixture.repo(), &["rev-parse", "HEAD"]);
        assert!(
            args.clone()
                .preflight_in(&CancellationToken::new())
                .unwrap_err()
                .to_string()
                .contains("HEAD tree does not exactly match provenance-owned paths")
        );
        fs::remove_file(extra).unwrap();
        git(fixture.repo(), &["add", "--", GENERATED_ROOTS]);
        git(fixture.repo(), &["commit", "-qm", "remove unowned input"]);
        args.reviewed_release_commit = git(fixture.repo(), &["rev-parse", "HEAD"]);

        let unselected_jar = args.package_root.join("SFM-MC26.1.2-4.35.0.jar");
        let original_jar = fs::read(&unselected_jar).unwrap();
        fs::write(&unselected_jar, b"changed unselected JAR\n").unwrap();
        assert!(
            args.clone()
                .preflight_in(&CancellationToken::new())
                .unwrap_err()
                .to_string()
                .contains("packaged JAR SHA-256 differs")
        );
        fs::write(unselected_jar, original_jar).unwrap();

        fs::write(&generated, b"committed changed output\n").unwrap();
        git(fixture.repo(), &["add", "--", GENERATED_ROOTS]);
        git(fixture.repo(), &["commit", "-qm", "drift generated output"]);
        args.reviewed_release_commit = git(fixture.repo(), &["rev-parse", "HEAD"]);
        assert!(
            args.clone()
                .preflight_in(&CancellationToken::new())
                .unwrap_err()
                .to_string()
                .contains("release HEAD blob differs from provenance")
        );
        fs::copy(
            fixture.roots()["1.21.0"].join("src/main/java/Candidate.java"),
            &generated,
        )
        .unwrap();
        git(fixture.repo(), &["add", "--", GENERATED_ROOTS]);
        git(
            fixture.repo(),
            &["commit", "-qm", "restore generated output"],
        );
        args.reviewed_release_commit = git(fixture.repo(), &["rev-parse", "HEAD"]);
        assert!(args.clone().preflight_in(&CancellationToken::new()).is_ok());

        fs::write(
            fixture
                .repo()
                .join("platform/minecraft/src/main/java/Candidate.java"),
            b"class Candidate { int changed; }\n",
        )
        .unwrap();
        git(fixture.repo(), &["add", "--", "platform/minecraft/src"]);
        git(fixture.repo(), &["commit", "-qm", "change authored input"]);
        assert!(
            args.clone()
                .preflight_in(&CancellationToken::new())
                .unwrap_err()
                .to_string()
                .contains("current HEAD differs")
        );
        args.reviewed_release_commit = git(fixture.repo(), &["rev-parse", "HEAD"]);
        assert!(
            args.preflight_in(&CancellationToken::new())
                .unwrap_err()
                .to_string()
                .contains("non-generated files changed")
        );
    }

    #[test]
    fn committed_owned_blobs_cannot_hide_behind_assume_unchanged() {
        let (fixture, mut args) = packaged_candidate();
        args.reviewed_release_commit = promoted_commit(&fixture);
        let relative = format!("{GENERATED_ROOTS}/1.21.0/src/main/java/Candidate.java");
        let generated = fixture.repo().join(&relative);
        let original = fs::read(&generated).unwrap();
        fs::write(&generated, b"different committed output\n").unwrap();
        git(fixture.repo(), &["add", "--", &relative]);
        git(fixture.repo(), &["commit", "-qm", "drift a committed blob"]);
        args.reviewed_release_commit = git(fixture.repo(), &["rev-parse", "HEAD"]);
        fs::write(&generated, original).unwrap();
        git(
            fixture.repo(),
            &["update-index", "--assume-unchanged", "--", &relative],
        );
        assert!(git(fixture.repo(), &["status", "--porcelain"]).is_empty());
        assert!(
            args.preflight_in(&CancellationToken::new())
                .unwrap_err()
                .to_string()
                .contains("concealed or non-normal tracked entry")
        );
    }

    #[test]
    fn committed_extra_input_cannot_hide_behind_skip_worktree() {
        let (fixture, mut args) = packaged_candidate();
        args.reviewed_release_commit = promoted_commit(&fixture);
        let relative = format!("{GENERATED_ROOTS}/1.21.0/src/main/java/Hidden.java");
        let extra = fixture.repo().join(&relative);
        fs::write(&extra, b"class Hidden {}\n").unwrap();
        git(fixture.repo(), &["add", "--", &relative]);
        git(fixture.repo(), &["commit", "-qm", "commit hidden input"]);
        args.reviewed_release_commit = git(fixture.repo(), &["rev-parse", "HEAD"]);
        git(
            fixture.repo(),
            &["update-index", "--skip-worktree", "--", &relative],
        );
        fs::remove_file(extra).unwrap();
        assert!(git(fixture.repo(), &["status", "--porcelain"]).is_empty());
        assert!(
            args.preflight_in(&CancellationToken::new())
                .unwrap_err()
                .to_string()
                .contains("concealed or non-normal tracked entry")
        );
    }

    #[test]
    fn rejects_assume_unchanged_authored_edit_hidden_from_git_status() {
        let (fixture, mut args) = packaged_candidate();
        args.reviewed_release_commit = promoted_commit(&fixture);
        assert!(args.clone().preflight_in(&CancellationToken::new()).is_ok());
        let relative = "platform/minecraft/src/main/java/Candidate.java";
        git(
            fixture.repo(),
            &["update-index", "--assume-unchanged", "--", relative],
        );
        fs::write(
            fixture.repo().join(relative),
            b"class Candidate { int hidden; }\n",
        )
        .unwrap();
        assert!(git(fixture.repo(), &["status", "--porcelain"]).is_empty());
        assert!(
            args.preflight_in(&CancellationToken::new())
                .unwrap_err()
                .to_string()
                .contains("concealed or non-normal tracked entry")
        );
    }

    #[test]
    fn rejects_skip_worktree_on_unmodified_unselected_tracked_file() {
        let (fixture, mut args) = packaged_candidate();
        args.reviewed_release_commit = promoted_commit(&fixture);
        assert!(args.clone().preflight_in(&CancellationToken::new()).is_ok());
        git(
            fixture.repo(),
            &["update-index", "--skip-worktree", "--", ".gitignore"],
        );
        assert!(git(fixture.repo(), &["status", "--porcelain"]).is_empty());
        assert!(
            args.preflight_in(&CancellationToken::new())
                .unwrap_err()
                .to_string()
                .contains("concealed or non-normal tracked entry")
        );
    }

    #[test]
    fn corrupt_local_tag_ref_is_not_reported_as_absent() {
        let (fixture, mut args) = packaged_candidate();
        args.reviewed_release_commit = promoted_commit(&fixture);
        let tag_ref = fixture.repo().join(".git/refs/tags/4.35.0-1.21.0");
        fs::create_dir_all(tag_ref.parent().unwrap()).unwrap();
        fs::write(tag_ref, b"not-a-Git-object\n").unwrap();
        assert!(
            args.preflight_in(&CancellationToken::new())
                .unwrap_err()
                .to_string()
                .contains("cannot inspect local tag")
        );
    }

    #[test]
    fn captured_tag_object_cannot_be_mixed_with_a_retargeted_ref() {
        let fixture = Fixture::new();
        let reviewed_release_commit = promoted_commit(&fixture);
        let local_tag = "4.35.0-1.21.0";
        let tag_ref = format!("refs/tags/{local_tag}");
        let source_commit = fixture.lock().source_commit.clone();

        git(fixture.repo(), &["tag", local_tag, &source_commit]);
        let stale_object = git(fixture.repo(), &["rev-parse", &tag_ref]);
        git(
            fixture.repo(),
            &["tag", "-f", local_tag, &reviewed_release_commit],
        );
        assert!(
            inspect_captured_local_tag(
                fixture.repo(),
                local_tag,
                &reviewed_release_commit,
                stale_object,
            )
            .unwrap_err()
            .to_string()
            .contains("does not point to reviewed release commit")
        );

        let matching_object = git(fixture.repo(), &["rev-parse", &tag_ref]);
        git(fixture.repo(), &["tag", "-f", local_tag, &source_commit]);
        assert!(
            inspect_captured_local_tag(
                fixture.repo(),
                local_tag,
                &reviewed_release_commit,
                matching_object,
            )
            .unwrap_err()
            .to_string()
            .contains("changed during preflight")
        );
    }

    #[test]
    fn rejects_repository_local_grafts_before_ancestry_check() {
        let (fixture, mut args) = packaged_candidate();
        args.reviewed_release_commit = promoted_commit(&fixture);
        fs::write(fixture.repo().join(".git/info/grafts"), b"\n").unwrap();
        assert!(
            args.preflight_in(&CancellationToken::new())
                .unwrap_err()
                .to_string()
                .contains("repository-local Git grafts are unsupported")
        );
    }

    #[test]
    fn git_probes_ignore_inherited_git_identity_and_disable_optional_writes() {
        let fixture = Fixture::new();
        let other = Fixture::new();
        let other_git_dir = other.repo().join(".git").display().to_string();
        let mut command = sanitized_git_command(
            fixture.repo(),
            &[
                ("GIT_DIR", &other_git_dir),
                ("GIT_WORK_TREE", &other.repo().display().to_string()),
            ],
        );
        let configured: Vec<_> = command.get_envs().collect();
        assert!(configured.iter().any(|(key, value)| {
            *key == "GIT_NO_REPLACE_OBJECTS" && value.is_some_and(|value| value == "1")
        }));
        assert!(configured.iter().any(|(key, value)| {
            *key == "GIT_OPTIONAL_LOCKS" && value.is_some_and(|value| value == "0")
        }));
        assert!(configured.iter().any(|(key, value)| {
            *key == "GIT_NO_LAZY_FETCH" && value.is_some_and(|value| value == "1")
        }));
        assert!(
            configured
                .iter()
                .any(|(key, value)| *key == "GIT_DIR" && value.is_none())
        );
        assert!(command.get_args().any(|arg| arg == "core.fsmonitor=false"));
        let output = command
            .args(["rev-parse", "--show-toplevel"])
            .output()
            .unwrap();
        assert!(output.status.success());
        assert_eq!(
            checked_directory(Path::new(String::from_utf8(output.stdout).unwrap().trim())).unwrap(),
            checked_directory(fixture.repo()).unwrap()
        );
    }
}
