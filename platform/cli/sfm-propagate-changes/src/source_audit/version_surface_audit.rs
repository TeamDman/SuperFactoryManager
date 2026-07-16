use crate::branch_targets::WorktreeTarget;
use eyre::Context;
use gix::bstr::ByteSlice;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::collections::HashMap;
use std::collections::HashSet;
use std::path::Path;

const BASELINE_BRANCH: &str = "1.19.2";
const CLI_SOURCE_ROOT: &str = "platform/cli/sfm-propagate-changes";
const JAVA_SOURCE_ROOT: &str = "platform/minecraft/src/main/java";
const VERSION_ANNOTATION: &str = "@MCVersionDependentBehaviour";

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct VersionSurfaceAuditReport {
    pub baseline_branch: String,
    pub branches: Vec<VersionSurfaceBranchReport>,
}

impl VersionSurfaceAuditReport {
    #[must_use]
    pub fn cli_warning_count(&self) -> usize {
        self.branches
            .iter()
            .map(VersionSurfaceBranchReport::cli_warning_count)
            .sum()
    }

    #[must_use]
    pub fn java_warning_count(&self) -> usize {
        self.branches
            .iter()
            .map(|branch| branch.unbounded_java_changes.len())
            .sum()
    }
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct VersionSurfaceBranchReport {
    pub branch: String,
    pub cli_source_matches_baseline: bool,
    pub cli_commits: Vec<LaterBranchCliCommit>,
    pub unbounded_java_changes: Vec<UnboundedJavaChange>,
}

impl VersionSurfaceBranchReport {
    #[must_use]
    pub fn cli_warning_count(&self) -> usize {
        self.cli_commits.len() + usize::from(!self.cli_source_matches_baseline)
    }
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct LaterBranchCliCommit {
    pub id: String,
    pub subject: String,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct UnboundedJavaChange {
    pub path: String,
    pub base_range: ChangedLineRange,
    pub target_range: ChangedLineRange,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct ChangedLineRange {
    start: usize,
    count: usize,
}

impl std::fmt::Display for ChangedLineRange {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        if self.count == 0 {
            write!(f, "{} (empty)", self.start)
        } else {
            write!(f, "{}..={}", self.start, self.end())
        }
    }
}

impl ChangedLineRange {
    #[must_use]
    fn end(self) -> usize {
        self.start.saturating_add(self.count.saturating_sub(1))
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
struct AnnotatedRegion {
    start: usize,
    end: usize,
}

impl AnnotatedRegion {
    fn contains_change(self, change: ChangedLineRange) -> bool {
        if change.count == 0 {
            return self.start <= change.start && change.start <= self.end.saturating_add(1);
        }
        self.start <= change.start && change.end() <= self.end
    }
}

/// Compare selected Minecraft version branches against the oldest supported branch.
///
/// The report is intentionally advisory: it exposes deviations that need manual design review
/// without making a source-size audit fail merely because older version work remains to be
/// sequestered.
pub(crate) fn audit_version_surfaces(
    targets: &[WorktreeTarget],
) -> eyre::Result<VersionSurfaceAuditReport> {
    let baseline = targets
        .iter()
        .find(|target| target.branch.as_str() == BASELINE_BRANCH)
        .ok_or_else(|| {
            eyre::eyre!("Version-surface audit requires the {BASELINE_BRANCH} worktree.")
        })?;
    let mut branches = targets
        .iter()
        .filter(|target| target.core && target.branch.as_str() != BASELINE_BRANCH)
        .map(|target| audit_branch(baseline, target))
        .collect::<eyre::Result<Vec<_>>>()?;
    branches.sort_by(|left, right| left.branch.cmp(&right.branch));
    Ok(VersionSurfaceAuditReport {
        baseline_branch: BASELINE_BRANCH.to_owned(),
        branches,
    })
}

fn audit_branch(
    baseline: &WorktreeTarget,
    target: &WorktreeTarget,
) -> eyre::Result<VersionSurfaceBranchReport> {
    let repository = gix::discover(&baseline.worktree_path).wrap_err_with(|| {
        format!(
            "Failed to discover Git repository at {}",
            baseline.worktree_path.display()
        )
    })?;
    let baseline_commit = branch_commit_id(&repository, baseline.branch.as_str())?;
    let target_commit = branch_commit_id(&repository, target.branch.as_str())?;
    Ok(VersionSurfaceBranchReport {
        branch: target.branch.to_string(),
        cli_source_matches_baseline: cli_source_matches_baseline(
            &repository,
            baseline_commit,
            target_commit,
        )?,
        cli_commits: later_branch_cli_commits(&repository, baseline_commit, target_commit)?,
        unbounded_java_changes: unbounded_java_changes(
            &repository,
            baseline_commit,
            target_commit,
        )?,
    })
}

fn cli_source_matches_baseline(
    repository: &gix::Repository,
    baseline: gix::hash::ObjectId,
    target: gix::hash::ObjectId,
) -> eyre::Result<bool> {
    Ok(
        tree_path_id_without_cache(repository, baseline, CLI_SOURCE_ROOT)?
            == tree_path_id_without_cache(repository, target, CLI_SOURCE_ROOT)?,
    )
}

fn later_branch_cli_commits(
    repository: &gix::Repository,
    baseline: gix::hash::ObjectId,
    target: gix::hash::ObjectId,
) -> eyre::Result<Vec<LaterBranchCliCommit>> {
    let baseline_commits = reachable_commit_ids(repository, baseline)?;
    let mut cli_tree_ids = HashMap::new();
    let mut commits = Vec::new();
    for commit_id in commits_exclusive_to_target(repository, target, &baseline_commits)? {
        let commit = repository.find_commit(commit_id).wrap_err_with(|| {
            format!("Failed to read commit {commit_id} while auditing CLI history")
        })?;
        let parents = commit.parent_ids().map(gix::Id::detach).collect::<Vec<_>>();
        if parents.is_empty() {
            continue;
        }
        let result = tree_path_id(repository, commit_id, CLI_SOURCE_ROOT, &mut cli_tree_ids)?;
        let parent_ids = parents
            .into_iter()
            .map(|parent| tree_path_id(repository, parent, CLI_SOURCE_ROOT, &mut cli_tree_ids))
            .collect::<eyre::Result<Vec<_>>>()?;
        if parent_ids.iter().any(|parent| *parent != result)
            && parent_ids.iter().all(|parent| *parent != result)
        {
            commits.push(LaterBranchCliCommit {
                id: commit_id.to_string(),
                subject: commit_subject(&commit)?,
            });
        }
    }
    commits.sort_by(|left, right| left.id.cmp(&right.id));
    Ok(commits)
}

fn unbounded_java_changes(
    repository: &gix::Repository,
    baseline: gix::hash::ObjectId,
    target: gix::hash::ObjectId,
) -> eyre::Result<Vec<UnboundedJavaChange>> {
    let baseline_files = java_blob_ids(repository, baseline)?;
    let target_files = java_blob_ids(repository, target)?;
    let paths = baseline_files
        .keys()
        .chain(target_files.keys())
        .cloned()
        .collect::<BTreeSet<_>>();
    let mut changes = Vec::new();
    for path in paths {
        let baseline_blob = baseline_files.get(&path).copied();
        let target_blob = target_files.get(&path).copied();
        if baseline_blob == target_blob {
            continue;
        }
        let baseline_source = java_source(repository, baseline_blob)?;
        let target_source = java_source(repository, target_blob)?;
        let baseline_regions = baseline_source
            .as_deref()
            .map(annotated_regions)
            .unwrap_or_default();
        let target_regions = target_source
            .as_deref()
            .map(annotated_regions)
            .unwrap_or_default();
        let baseline_source = baseline_source.as_deref().unwrap_or_default();
        let target_source = target_source.as_deref().unwrap_or_default();
        for hunk in java_diff_hunks(baseline_source, target_source) {
            if !hunk_is_java_preamble_only(hunk, baseline_source, target_source)
                && !hunk_is_annotation_bounded(
                    hunk,
                    baseline_source,
                    target_source,
                    &baseline_regions,
                    &target_regions,
                )
            {
                changes.push(UnboundedJavaChange {
                    path: path.clone(),
                    base_range: hunk.base_range,
                    target_range: hunk.target_range,
                });
            }
        }
    }
    Ok(changes)
}

fn branch_commit_id(
    repository: &gix::Repository,
    branch: &str,
) -> eyre::Result<gix::hash::ObjectId> {
    let reference = format!("refs/heads/{branch}");
    let commit = repository
        .find_reference(&reference)
        .wrap_err_with(|| format!("Failed to find branch '{branch}'"))?
        .into_fully_peeled_id()
        .wrap_err_with(|| format!("Failed to resolve branch '{branch}'"))?;
    Ok(commit.detach())
}

fn reachable_commit_ids(
    repository: &gix::Repository,
    start: gix::hash::ObjectId,
) -> eyre::Result<HashSet<gix::hash::ObjectId>> {
    let mut commits = HashSet::new();
    let mut pending = vec![start];
    while let Some(commit_id) = pending.pop() {
        if !commits.insert(commit_id) {
            continue;
        }
        let commit = repository.find_commit(commit_id).wrap_err_with(|| {
            format!("Failed to read commit {commit_id} while walking version history")
        })?;
        pending.extend(commit.parent_ids().map(gix::Id::detach));
    }
    Ok(commits)
}

fn commits_exclusive_to_target(
    repository: &gix::Repository,
    target: gix::hash::ObjectId,
    baseline_commits: &HashSet<gix::hash::ObjectId>,
) -> eyre::Result<Vec<gix::hash::ObjectId>> {
    let mut commits = Vec::new();
    let mut visited = HashSet::new();
    let mut pending = vec![target];
    while let Some(commit_id) = pending.pop() {
        if baseline_commits.contains(&commit_id) || !visited.insert(commit_id) {
            continue;
        }
        let commit = repository.find_commit(commit_id).wrap_err_with(|| {
            format!("Failed to read commit {commit_id} while walking target history")
        })?;
        pending.extend(commit.parent_ids().map(gix::Id::detach));
        commits.push(commit_id);
    }
    Ok(commits)
}

fn tree_path_id(
    repository: &gix::Repository,
    commit_id: gix::hash::ObjectId,
    path: &str,
    cache: &mut HashMap<gix::hash::ObjectId, Option<gix::hash::ObjectId>>,
) -> eyre::Result<Option<gix::hash::ObjectId>> {
    if let Some(id) = cache.get(&commit_id) {
        return Ok(*id);
    }
    let id = tree_path_id_without_cache(repository, commit_id, path)?;
    cache.insert(commit_id, id);
    Ok(id)
}

fn tree_path_id_without_cache(
    repository: &gix::Repository,
    commit_id: gix::hash::ObjectId,
    path: &str,
) -> eyre::Result<Option<gix::hash::ObjectId>> {
    let commit = repository.find_commit(commit_id)?;
    let tree = commit.tree()?;
    Ok(tree
        .lookup_entry_by_path(path)?
        .map(|entry| entry.object_id()))
}

fn commit_subject(commit: &gix::Commit<'_>) -> eyre::Result<String> {
    Ok(commit
        .message_raw()?
        .lines()
        .next()
        .map(|line| line.to_str_lossy().into_owned())
        .unwrap_or_default())
}

fn java_blob_ids(
    repository: &gix::Repository,
    commit_id: gix::hash::ObjectId,
) -> eyre::Result<BTreeMap<String, gix::hash::ObjectId>> {
    let commit = repository.find_commit(commit_id)?;
    let tree = commit.tree()?;
    let Some(root) = tree.lookup_entry_by_path(JAVA_SOURCE_ROOT)? else {
        return Ok(BTreeMap::new());
    };
    let mut files = BTreeMap::new();
    collect_java_blob_ids(repository, root.object_id(), JAVA_SOURCE_ROOT, &mut files)?;
    Ok(files)
}

fn collect_java_blob_ids(
    repository: &gix::Repository,
    tree_id: gix::hash::ObjectId,
    prefix: &str,
    files: &mut BTreeMap<String, gix::hash::ObjectId>,
) -> eyre::Result<()> {
    let tree = repository.find_tree(tree_id)?;
    for entry in tree.iter() {
        let entry = entry?;
        let name = entry.filename().to_str_lossy();
        let path = format!("{prefix}/{name}");
        if entry.mode().is_tree() {
            collect_java_blob_ids(repository, entry.object_id(), &path, files)?;
        } else if Path::new(&path)
            .extension()
            .is_some_and(|extension| extension.eq_ignore_ascii_case("java"))
        {
            files.insert(path, entry.object_id());
        }
    }
    Ok(())
}

fn java_source(
    repository: &gix::Repository,
    blob_id: Option<gix::hash::ObjectId>,
) -> eyre::Result<Option<String>> {
    let Some(blob_id) = blob_id else {
        return Ok(None);
    };
    let mut blob = repository.find_blob(blob_id)?;
    String::from_utf8(blob.take_data())
        .map(Some)
        .map_err(|error| eyre::eyre!("Java source blob {blob_id} is not UTF-8: {error}"))
}

fn java_diff_hunks(baseline: &str, target: &str) -> Vec<JavaDiffHunk> {
    let input = gix::diff::blob::InternedInput::new(baseline, target);
    gix::diff::blob::diff_with_slider_heuristics(gix::diff::blob::Algorithm::Histogram, &input)
        .hunks()
        .map(|hunk| JavaDiffHunk {
            base_range: changed_line_range(hunk.before.start as usize, hunk.before.len()),
            target_range: changed_line_range(hunk.after.start as usize, hunk.after.len()),
        })
        .collect()
}

fn changed_line_range(start: usize, count: usize) -> ChangedLineRange {
    ChangedLineRange {
        start: start + usize::from(count != 0),
        count,
    }
}

fn hunk_is_java_preamble_only(hunk: JavaDiffHunk, baseline: &str, target: &str) -> bool {
    changed_lines(baseline, hunk.base_range)
        .chain(changed_lines(target, hunk.target_range))
        .all(|line| {
            let line = line.trim();
            line.is_empty() || line.starts_with("package ") || line.starts_with("import ")
        })
}

fn changed_lines(source: &str, range: ChangedLineRange) -> impl Iterator<Item = &str> {
    source
        .lines()
        .skip(range.start.saturating_sub(1))
        .take(range.count)
}

#[derive(Clone, Copy, Debug)]
struct JavaDiffHunk {
    base_range: ChangedLineRange,
    target_range: ChangedLineRange,
}

fn hunk_is_annotation_bounded(
    hunk: JavaDiffHunk,
    baseline_source: &str,
    target_source: &str,
    baseline_regions: &[AnnotatedRegion],
    target_regions: &[AnnotatedRegion],
) -> bool {
    change_is_annotation_bounded(baseline_source, hunk.base_range, baseline_regions)
        || change_is_annotation_bounded(target_source, hunk.target_range, target_regions)
}

fn change_is_annotation_bounded(
    source: &str,
    range: ChangedLineRange,
    regions: &[AnnotatedRegion],
) -> bool {
    if range.count == 0 {
        return regions.iter().any(|region| region.contains_change(range));
    }
    source
        .lines()
        .enumerate()
        .skip(range.start.saturating_sub(1))
        .take(range.count)
        .filter(|(_, line)| !line.trim().is_empty())
        .all(|(index, _)| {
            let line = index + 1;
            regions
                .iter()
                .any(|region| region.start <= line && line <= region.end)
        })
}

fn annotated_regions(source: &str) -> Vec<AnnotatedRegion> {
    let lines = source.lines().collect::<Vec<_>>();
    lines
        .iter()
        .enumerate()
        .filter_map(|(index, line)| line.contains(VERSION_ANNOTATION).then_some(index))
        .map(|index| annotated_region(&lines, index))
        .collect()
}

fn annotated_region(lines: &[&str], annotation_index: usize) -> AnnotatedRegion {
    let start = annotation_index + 1;
    let mut braces = 0usize;
    let mut opened = false;
    for (index, line) in lines.iter().enumerate().skip(annotation_index) {
        for character in line.chars() {
            match character {
                '{' => {
                    braces += 1;
                    opened = true;
                }
                '}' if opened => {
                    braces = braces.saturating_sub(1);
                    if braces == 0 {
                        return AnnotatedRegion {
                            start,
                            end: index + 1,
                        };
                    }
                }
                ';' if !opened => {
                    return AnnotatedRegion {
                        start,
                        end: index + 1,
                    };
                }
                _ => {}
            }
        }
    }
    AnnotatedRegion {
        start,
        end: lines.len(),
    }
}

#[cfg(test)]
mod tests {
    use super::AnnotatedRegion;
    use super::ChangedLineRange;
    use super::annotated_regions;
    use super::branch_commit_id;
    use super::cli_source_matches_baseline;
    use super::hunk_is_annotation_bounded;
    use super::hunk_is_java_preamble_only;
    use super::java_diff_hunks;
    use super::later_branch_cli_commits;
    use super::unbounded_java_changes;
    use eyre::Context;
    use std::fs;
    use std::path::Path;
    use std::process::Command;

    #[test]
    fn hunk_ranges_preserve_zero_length_additions() {
        let hunk = java_diff_hunks("first\nsecond\n", "first\ninserted\nsecond\n")
            .into_iter()
            .next()
            .expect("hunk");
        assert_eq!(hunk.base_range, ChangedLineRange { start: 1, count: 0 });
        assert_eq!(hunk.target_range, ChangedLineRange { start: 2, count: 1 });
    }

    #[test]
    fn annotated_method_region_is_bounded_by_its_declaration() {
        let source = r"class Example {
    void common() {}
    @MCVersionDependentBehaviour
    void versioned() {
        int value = 1;
    }
}
";
        assert_eq!(
            annotated_regions(source),
            [AnnotatedRegion { start: 3, end: 6 }]
        );
    }

    #[test]
    fn ignores_java_import_only_hunks() {
        let baseline = "package example;\n\nimport example.OldApi;\n\nclass Example {}\n";
        let target = "package example;\n\nimport example.NewApi;\n\nclass Example {}\n";
        let hunk = java_diff_hunks(baseline, target)
            .into_iter()
            .next()
            .expect("import hunk");
        assert!(hunk_is_java_preamble_only(hunk, baseline, target));
    }

    #[test]
    fn accepts_hunk_spanning_adjacent_annotated_methods() {
        let source = r"class Example {
    @MCVersionDependentBehaviour
    void first() {
        int value = 1;
    }

    @MCVersionDependentBehaviour
    void second() {
        int value = 2;
    }
}
";
        let hunk = super::JavaDiffHunk {
            base_range: ChangedLineRange { start: 2, count: 9 },
            target_range: ChangedLineRange { start: 2, count: 9 },
        };
        let regions = annotated_regions(source);
        assert!(hunk_is_annotation_bounded(
            hunk, source, source, &regions, &regions
        ));
    }

    #[test]
    fn reports_later_cli_commits_and_only_unbounded_java_hunks() -> eyre::Result<()> {
        let directory = tempfile::tempdir()?;
        let root = directory.path();
        fs::create_dir_all(root.join("platform/cli/sfm-propagate-changes/src"))?;
        fs::create_dir_all(root.join("platform/minecraft/src/main/java/example"))?;
        fs::write(
            root.join("platform/cli/sfm-propagate-changes/src/lib.rs"),
            "pub fn original() {}\n",
        )?;
        fs::write(
            root.join("platform/minecraft/src/main/java/example/Example.java"),
            "class Example {\n    void common() {\n        int value = 1;\n    }\n    @MCVersionDependentBehaviour\n    void versioned() {\n        int value = 1;\n    }\n}\n",
        )?;
        run_git(root, &["init"])?;
        run_git(root, &["config", "user.email", "audit@example.invalid"])?;
        run_git(root, &["config", "user.name", "Audit Test"])?;
        run_git(root, &["add", "."])?;
        run_git(root, &["commit", "-m", "baseline"])?;
        run_git(root, &["branch", "1.19.2"])?;
        run_git(root, &["checkout", "-b", "1.20.1"])?;

        fs::write(
            root.join("platform/cli/sfm-propagate-changes/src/lib.rs"),
            "pub fn changed_later() {}\n",
        )?;
        fs::write(
            root.join("platform/minecraft/src/main/java/example/Example.java"),
            "class Example {\n    void common() {\n        int value = 2;\n    }\n    @MCVersionDependentBehaviour\n    void versioned() {\n        int value = 2;\n    }\n}\n",
        )?;
        run_git(root, &["add", "."])?;
        run_git(root, &["commit", "-m", "change later branch"])?;

        let repository = gix::discover(root)?;
        let baseline = branch_commit_id(&repository, "1.19.2")?;
        let target = branch_commit_id(&repository, "1.20.1")?;
        let commits = later_branch_cli_commits(&repository, baseline, target)?;
        assert_eq!(commits.len(), 1);
        assert_eq!(commits[0].subject, "change later branch");
        assert!(!cli_source_matches_baseline(&repository, baseline, target)?);

        let changes = unbounded_java_changes(&repository, baseline, target)?;
        assert_eq!(changes.len(), 1);
        assert_eq!(
            changes[0].path,
            "platform/minecraft/src/main/java/example/Example.java"
        );
        assert_eq!(changes[0].base_range.start, 3);
        assert_eq!(changes[0].target_range.start, 3);
        Ok(())
    }

    #[test]
    fn accepts_cli_changes_that_arrive_via_propagation_merge() -> eyre::Result<()> {
        let directory = tempfile::tempdir()?;
        let root = directory.path();
        fs::create_dir_all(root.join("platform/cli/sfm-propagate-changes/src"))?;
        fs::write(
            root.join("platform/cli/sfm-propagate-changes/src/lib.rs"),
            "pub fn baseline() {}\n",
        )?;
        run_git(root, &["init"])?;
        run_git(root, &["config", "user.email", "audit@example.invalid"])?;
        run_git(root, &["config", "user.name", "Audit Test"])?;
        run_git(root, &["add", "."])?;
        run_git(root, &["commit", "-m", "baseline"])?;
        run_git(root, &["branch", "1.19.2"])?;
        run_git(root, &["checkout", "-b", "1.20.1"])?;

        run_git(root, &["checkout", "1.19.2"])?;
        fs::write(
            root.join("platform/cli/sfm-propagate-changes/src/lib.rs"),
            "pub fn propagated_from_baseline() {}\n",
        )?;
        run_git(root, &["add", "."])?;
        run_git(root, &["commit", "-m", "change baseline CLI"])?;

        run_git(root, &["checkout", "1.20.1"])?;
        run_git(
            root,
            &[
                "merge",
                "--no-ff",
                "1.19.2",
                "-m",
                "Propagate changes: merge 1.19.2 into 1.20.1",
            ],
        )?;

        let repository = gix::discover(root)?;
        let baseline = branch_commit_id(&repository, "1.19.2")?;
        let target = branch_commit_id(&repository, "1.20.1")?;
        assert!(later_branch_cli_commits(&repository, baseline, target)?.is_empty());
        assert!(cli_source_matches_baseline(&repository, baseline, target)?);
        Ok(())
    }

    #[test]
    fn reports_a_cli_change_made_while_resolving_a_merge() -> eyre::Result<()> {
        let directory = tempfile::tempdir()?;
        let root = directory.path();
        fs::create_dir_all(root.join("platform/cli/sfm-propagate-changes/src"))?;
        let cli_source = root.join("platform/cli/sfm-propagate-changes/src/lib.rs");
        fs::write(&cli_source, "pub fn baseline() {}\n")?;
        run_git(root, &["init"])?;
        run_git(root, &["config", "user.email", "audit@example.invalid"])?;
        run_git(root, &["config", "user.name", "Audit Test"])?;
        run_git(root, &["add", "."])?;
        run_git(root, &["commit", "-m", "baseline"])?;
        run_git(root, &["branch", "1.19.2"])?;
        run_git(root, &["checkout", "-b", "1.20.1"])?;

        run_git(root, &["checkout", "1.19.2"])?;
        fs::write(&cli_source, "pub fn propagated_from_baseline() {}\n")?;
        run_git(root, &["add", "."])?;
        run_git(root, &["commit", "-m", "change baseline CLI"])?;

        run_git(root, &["checkout", "1.20.1"])?;
        run_git(root, &["merge", "--no-ff", "--no-commit", "1.19.2"])?;
        fs::write(&cli_source, "pub fn manually_changed_during_merge() {}\n")?;
        run_git(root, &["add", "."])?;
        run_git(
            root,
            &[
                "commit",
                "-m",
                "Propagate changes: merge 1.19.2 into 1.20.1",
            ],
        )?;

        let repository = gix::discover(root)?;
        let baseline = branch_commit_id(&repository, "1.19.2")?;
        let target = branch_commit_id(&repository, "1.20.1")?;
        let commits = later_branch_cli_commits(&repository, baseline, target)?;
        assert_eq!(commits.len(), 1);
        assert_eq!(
            commits[0].subject,
            "Propagate changes: merge 1.19.2 into 1.20.1"
        );
        assert!(!cli_source_matches_baseline(&repository, baseline, target)?);
        Ok(())
    }

    fn run_git(cwd: &Path, args: &[&str]) -> eyre::Result<()> {
        let output = Command::new("git")
            .args(args)
            .current_dir(cwd)
            .output()
            .wrap_err_with(|| format!("Failed to run git {}", args.join(" ")))?;
        if !output.status.success() {
            eyre::bail!(
                "git {} failed: {}",
                args.join(" "),
                String::from_utf8_lossy(&output.stderr).trim()
            );
        }
        Ok(())
    }
}
