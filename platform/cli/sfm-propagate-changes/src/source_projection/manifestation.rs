//! File-scoped, invocation-shared rendering. No historical sources or output writes.

use super::SourceRenderCache;
use super::core_catalog::CoreCatalog;
use super::core_catalog::read_bounded_input;
use super::core_inputs::CORE_METADATA_PATH;
use super::core_inputs::CORE_ROOT;
use super::core_inputs::CoreInputSnapshot;
use super::core_inputs::CoreProjectInputs;
use super::core_inputs::discover_core_source_files;
use super::core_inputs::render_core_input_with;
use super::core_inputs::validate_collected_project;
use super::core_inputs::validate_collection_selection_with_snapshot;
use super::projection_catalog::validate_projection_key;
use super::sync::ProjectedArtifact;
use crate::cancellation::CancellationToken;
use eyre::Result;
use eyre::ensure;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::io::Write as _;
use std::path::Path;
use std::path::PathBuf;

#[derive(Debug, Default, facet::Facet)]
pub(crate) struct ManifestReport {
    pub(crate) projections: Vec<String>,
    pub(crate) changed: Vec<String>,
    pub(crate) removed: Vec<String>,
    pub(crate) unchanged: usize,
    pub(crate) writes_performed: bool,
}

/// Apply only the rendered selection. Git is the contributor-edit authority;
/// ignored disposable outputs are not considered tracked contributor changes.
#[expect(
    clippy::option_option,
    clippy::ref_option,
    reason = "Borrow the CLI's absent/bare/scoped override without changing its three-state contract"
)]
pub(crate) fn manifest_selected(
    repo: &Path,
    projections: &[RenderedProjection],
    allow_dirty: &Option<Option<String>>,
    dry_run: bool,
) -> Result<ManifestReport> {
    if let Some(Some(selector)) = allow_dirty {
        validate_projection_key(selector)?;
    }
    let mut report = ManifestReport::default();
    let mut changes = Vec::new();
    for projection in projections {
        super::sync::inspect_root(&projection.root)?;
        super::sync::validate_catalog_artifacts(&projection.artifacts)?;
        report.projections.push(projection.key.clone());
        for (relative, artifact) in &projection.artifacts {
            super::sync::inspect_output_parents(&projection.root, relative)?;
            let destination = projection.root.join(relative);
            let old = super::sync::read_regular_file_if_present(&destination)?;
            if old.as_deref() == Some(artifact.output_bytes.as_slice()) {
                report.unchanged += 1;
                continue;
            }
            let path = destination
                .strip_prefix(repo)?
                .to_str()
                .ok_or_else(|| eyre::eyre!("non-UTF8 output"))?
                .replace('\\', "/");
            report.changed.push(path.clone());
            changes.push((destination, path, old, Some(&artifact.output_bytes)));
        }
        for relative in &projection.removed {
            super::sync::validate_catalog_output(relative)?;
            ensure!(
                !projection.artifacts.contains_key(relative),
                "output is both selected and excluded: {relative}"
            );
            super::sync::inspect_output_parents(&projection.root, relative)?;
            let destination = projection.root.join(relative);
            if let Some(old) = super::sync::read_regular_file_if_present(&destination)? {
                let path = destination
                    .strip_prefix(repo)?
                    .to_str()
                    .ok_or_else(|| eyre::eyre!("non-UTF8 output"))?
                    .replace('\\', "/");
                report.removed.push(path.clone());
                changes.push((destination, path, Some(old), None));
            }
        }
    }
    if changes.is_empty() {
        return Ok(report);
    }
    let roots = projections
        .iter()
        .map(|projection| {
            projection
                .root
                .strip_prefix(repo)
                .map(|p| p.to_string_lossy().replace('\\', "/"))
        })
        .collect::<std::result::Result<Vec<_>, _>>()?;
    let dirty = dirty_paths(repo, &roots)?;
    for (_, path, _, _) in &changes {
        ensure!(
            !dirty.contains(path) || permits_dirty(allow_dirty, path),
            "dirty projection output `{path}`; use --allow-dirty with an explicit optional path to overwrite"
        );
    }
    if dry_run {
        return Ok(report);
    }
    for (destination, path, old, bytes) in changes {
        let parent = destination
            .parent()
            .ok_or_else(|| eyre::eyre!("output lacks parent"))?;
        std::fs::create_dir_all(parent)?;
        super::sync::inspect_output_parents(repo, &path)?;
        let current = super::sync::read_regular_file_if_present(&destination)?;
        ensure!(
            current == old,
            "output changed during manifestation: {path}"
        );
        if let Some(bytes) = bytes {
            let mut staged = tempfile::NamedTempFile::new_in(parent)?;
            staged.write_all(bytes)?;
            staged.persist(&destination).map_err(|error| error.error)?;
        } else {
            std::fs::remove_file(&destination)?;
        }
    }
    report.writes_performed = true;
    Ok(report)
}

#[expect(
    clippy::option_option,
    clippy::ref_option,
    reason = "Borrow the CLI's absent/bare/scoped override without changing its three-state contract"
)]
fn permits_dirty(allow: &Option<Option<String>>, path: &str) -> bool {
    match allow {
        None => false,
        Some(None) => true,
        Some(Some(selector)) => {
            path == selector
                || path
                    .strip_prefix(selector)
                    .is_some_and(|tail| tail.starts_with('/'))
        }
    }
}

fn dirty_paths(repo: &Path, roots: &[String]) -> Result<BTreeSet<String>> {
    let output = super::release_baseline::frozen_git_command(repo)
        .args([
            "-c",
            "core.fsmonitor=false",
            "status",
            "--porcelain=v1",
            "-z",
            "--untracked-files=all",
            "--",
        ])
        .args(roots)
        .output()?;
    ensure!(
        output.status.success(),
        "cannot inspect projection Git status: {}",
        String::from_utf8_lossy(&output.stderr)
    );
    parse_dirty_paths(&output.stdout)
}

fn parse_dirty_paths(bytes: &[u8]) -> Result<BTreeSet<String>> {
    let mut paths = BTreeSet::new();
    let mut records = bytes
        .split(|byte| *byte == 0)
        .filter(|record| !record.is_empty());
    while let Some(record) = records.next() {
        ensure!(
            record.len() > 3 && record[2] == b' ',
            "malformed Git status record"
        );
        paths.insert(std::str::from_utf8(&record[3..])?.to_owned());
        if record[..2]
            .iter()
            .any(|status| matches!(status, b'R' | b'C'))
        {
            let original = records
                .next()
                .ok_or_else(|| eyre::eyre!("missing Git rename source"))?;
            paths.insert(std::str::from_utf8(original)?.to_owned());
        }
    }
    Ok(paths)
}

pub(crate) struct RenderedProjection {
    pub(crate) key: String,
    pub(crate) root: PathBuf,
    pub(crate) artifacts: BTreeMap<String, ProjectedArtifact>,
    pub(crate) removed: BTreeSet<String>,
}

pub(crate) fn read_output(root: &Path, output: &str) -> Result<Option<Vec<u8>>> {
    validate_projection_key(output)?;
    super::sync::inspect_output_parents(root, output)?;
    super::sync::read_regular_file_if_present(&root.join(output))
}

/// Render one output for read-only tracing, resolving its input through current
/// membership rules. No unselected source bodies or generated files are read.
pub(crate) fn render_output(
    loaded: &CoreCatalog,
    key: &str,
    output: &str,
) -> Result<ProjectedArtifact> {
    validate_projection_key(output)?;
    let bytes = read_bounded_input(
        &loaded.repo_root,
        CORE_METADATA_PATH,
        super::core_inputs::MAX_CORE_METADATA_BYTES,
    )?;
    let metadata =
        CoreProjectInputs::from_json(std::str::from_utf8(&bytes)?, &loaded.registered_features)?;
    let core = loaded.repo_root.join(CORE_ROOT);
    let context = loaded.context(key)?;
    let inventory = discover_core_source_files(&core)?;
    let mut snapshot = CoreInputSnapshot::default();
    let mut selection = metadata
        .validated_selector(&loaded.registered_features)?
        .select_with_snapshot(&context, &inventory, &mut snapshot)?;
    selection.inputs.retain(|path, _| path == output);
    let input = selection
        .inputs
        .get(output)
        .ok_or_else(|| eyre::eyre!("output is not selected by this projection: {output}"))?;
    validate_collection_selection_with_snapshot(&selection, &context, &mut snapshot)?;
    super::core_inputs::render_core_input(
        output,
        input,
        snapshot.read(&core, &input.input)?,
        &context,
    )
}

/// Select exact keys or all (`*`/empty); source paths are relative to the core.
/// Parse configuration once and retain input bytes and compiled templates for
/// this invocation only. Targeted calls never render unrelated output bodies.
pub(crate) fn render_selected(
    repo_root: &Path,
    invocation_dir: &Path,
    files: &[String],
    projections: &[String],
    cancellation: &CancellationToken,
) -> Result<(PathBuf, Vec<RenderedProjection>)> {
    validate_selectors(files)?;
    validate_selectors(projections)?;
    let loaded = CoreCatalog::load(repo_root, invocation_dir)?;
    let bytes = read_bounded_input(
        &loaded.repo_root,
        CORE_METADATA_PATH,
        super::core_inputs::MAX_CORE_METADATA_BYTES,
    )?;
    let metadata =
        CoreProjectInputs::from_json(std::str::from_utf8(&bytes)?, &loaded.registered_features)?;
    let core = loaded.repo_root.join(CORE_ROOT);
    let selector = metadata.validated_selector(&loaded.registered_features)?;
    let inventory = discover_core_source_files(&core)?;
    let mut snapshot = CoreInputSnapshot::default();
    let mut template_cache = SourceRenderCache::default();
    let mut matched_files = BTreeSet::new();
    let mut matched_projections = BTreeSet::new();
    let mut rendered = Vec::new();
    for key in loaded
        .catalog
        .0
        .keys()
        .filter(|key| matches_selector(projections, key))
    {
        cancellation.bail_if_cancelled()?;
        matched_projections.insert(key.clone());
        let context = loaded.context(key)?;
        let mut selection = selector.select_with_snapshot(&context, &inventory, &mut snapshot)?;
        let removed = selection
            .omitted_paths
            .iter()
            .filter(|output| {
                let candidates = metadata
                    .source_rules
                    .get(*output)
                    .or_else(|| metadata.project_files.get(*output));
                if let Some(candidates) = candidates {
                    let mut matched = false;
                    for candidate in candidates {
                        if matches_selector(files, &candidate.input) {
                            matched_files.insert(candidate.input.clone());
                            matched = true;
                        }
                    }
                    matched
                } else {
                    matches_selector(files, output)
                }
            })
            .cloned()
            .collect();
        selection.inputs.retain(|_, input| {
            let selected = matches_selector(files, &input.input);
            if selected {
                matched_files.insert(input.input.clone());
            }
            selected
        });
        validate_collection_selection_with_snapshot(&selection, &context, &mut snapshot)?;
        snapshot.preload(&core, &selection)?;
        let mut artifacts = BTreeMap::new();
        for (output, input) in &selection.inputs {
            cancellation.bail_if_cancelled()?;
            let bytes = snapshot.read(&core, &input.input)?;
            let artifact =
                render_core_input_with(output, input, bytes, &context, |source, context| {
                    template_cache.render(source, context)
                })?;
            artifacts.insert(output.clone(), artifact);
        }
        if selects_all(files) {
            validate_collected_project(&artifacts, &selection, &context)?;
        }
        let root = loaded.repo_root.join(loaded.catalog.project_dir(key)?);
        rendered.push(RenderedProjection {
            key: key.clone(),
            root,
            artifacts,
            removed,
        });
    }
    require_matches(projections, &matched_projections, "projection")?;
    require_matches(files, &matched_files, "core input")?;
    let selected = rendered
        .iter()
        .map(|projection| (projection.key.as_str(), &projection.artifacts))
        .collect::<Vec<_>>();
    super::named_root::catalog_projection_roots(&loaded.repo_root, &loaded.catalog, &selected)?;
    Ok((loaded.repo_root, rendered))
}

fn selects_all(selectors: &[String]) -> bool {
    selectors.is_empty() || selectors.iter().any(|selector| selector == "*")
}

fn matches_selector(selectors: &[String], path: &str) -> bool {
    selects_all(selectors) || selectors.iter().any(|selector| selector == path)
}

fn validate_selectors(selectors: &[String]) -> Result<()> {
    for selector in selectors {
        if selector != "*" {
            validate_projection_key(selector)?;
        }
    }
    ensure!(
        !(selectors.len() > 1 && selects_all(selectors)),
        "* must be used alone"
    );
    Ok(())
}

fn require_matches(selectors: &[String], matched: &BTreeSet<String>, kind: &str) -> Result<()> {
    for selector in selectors.iter().filter(|value| value.as_str() != "*") {
        ensure!(
            matched.contains(selector),
            "unknown or unselected {kind}: {selector}"
        );
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn git(root: &Path, args: &[&str]) {
        let result = super::super::release_baseline::frozen_git_command(root)
            .args([
                "-c",
                "user.name=Fixture",
                "-c",
                "user.email=fixture@example.invalid",
                "-c",
                "commit.gpgSign=false",
                "-c",
                "core.autocrlf=false",
            ])
            .args(args)
            .output()
            .unwrap();
        assert!(
            result.status.success(),
            "{}",
            String::from_utf8_lossy(&result.stderr)
        );
    }

    #[test]
    fn tracked_edits_and_exclusions_use_git_protection_while_ignored_outputs_refresh() {
        let repo = tempfile::tempdir().unwrap();
        git(repo.path(), &["init", "--quiet"]);
        let root = repo.path().join("output");
        std::fs::create_dir_all(root.join("src")).unwrap();
        let file = root.join("src/A.java");
        std::fs::write(&file, "baseline").unwrap();
        git(repo.path(), &["add", "."]);
        git(repo.path(), &["commit", "--quiet", "-m", "fixture"]);
        let mut projection = RenderedProjection {
            key: "example".into(),
            root: root.clone(),
            removed: BTreeSet::new(),
            artifacts: [(
                "src/A.java".into(),
                ProjectedArtifact {
                    source_path: format!("{CORE_ROOT}/src/A.java"),
                    source_bytes: b"next".to_vec(),
                    output_bytes: b"next".to_vec(),
                    overlay: None,
                },
            )]
            .into(),
        };
        for staged in [false, true] {
            std::fs::write(&file, "edited").unwrap();
            if staged {
                git(repo.path(), &["add", "output/src/A.java"]);
            }
            for dry in [false, true] {
                assert!(
                    manifest_selected(repo.path(), std::slice::from_ref(&projection), &None, dry)
                        .is_err()
                );
                assert_eq!(std::fs::read(&file).unwrap(), b"edited");
            }
        }
        let allow = Some(Some("output/src/A.java".into()));
        let preview =
            manifest_selected(repo.path(), std::slice::from_ref(&projection), &allow, true)
                .unwrap();
        assert!(!preview.writes_performed);
        assert_eq!(std::fs::read(&file).unwrap(), b"edited");
        manifest_selected(
            repo.path(),
            std::slice::from_ref(&projection),
            &allow,
            false,
        )
        .unwrap();
        assert_eq!(std::fs::read(&file).unwrap(), b"next");
        projection.artifacts.clear();
        projection.removed.insert("src/A.java".into());
        assert!(
            manifest_selected(repo.path(), std::slice::from_ref(&projection), &None, false)
                .is_err()
        );
        let removed = manifest_selected(
            repo.path(),
            std::slice::from_ref(&projection),
            &allow,
            false,
        )
        .unwrap();
        assert_eq!(removed.removed, ["output/src/A.java"]);
        assert!(!file.exists());

        std::fs::write(repo.path().join(".gitignore"), "/ignored/\n").unwrap();
        projection.root = repo.path().join("ignored");
        std::fs::create_dir_all(projection.root.join("src")).unwrap();
        std::fs::write(projection.root.join("src/A.java"), "disposable").unwrap();
        manifest_selected(repo.path(), &[projection], &None, false).unwrap();
        assert!(!repo.path().join("ignored/src/A.java").exists());
    }

    #[test]
    fn file_render_fans_out_without_reading_unselected_source_bodies() {
        use super::super::core_catalog::write_project_catalog_fixture;
        use super::super::projection_catalog::CATALOG_PATH;
        let repo = tempfile::tempdir().unwrap();
        write_project_catalog_fixture(repo.path(), "a", "1.19.2").unwrap();
        git(repo.path(), &["init", "--quiet"]);
        std::fs::write(
            repo.path().join(CATALOG_PATH),
            r#"{
            "a":{"minecraft_version":"1.19.2","environment":"release","features":[]},
            "b":{"minecraft_version":"1.19.4","environment":"release","features":[]}
        }"#,
        )
        .unwrap();
        std::fs::write(repo.path().join(CORE_METADATA_PATH), r#"{
            "schema_version":1,
            "targets":{"1.19.2":{"java_major":17,"loader":"forge"},"1.19.4":{"java_major":17,"loader":"forge"}},
            "source_rules":{},"project_files":{}
        }"#).unwrap();
        let core = repo.path().join(CORE_ROOT);
        std::fs::create_dir_all(core.join("src/main/java")).unwrap();
        std::fs::write(core.join("src/main/java/A.java"), "class A {}\n").unwrap();
        std::fs::write(core.join("src/main/java/Unselected.java"), [255, 254]).unwrap();
        let cancellation = CancellationToken::new();
        let (_, all) = render_selected(
            repo.path(),
            repo.path(),
            &["src/main/java/A.java".into()],
            &[],
            &cancellation,
        )
        .unwrap();
        assert_eq!(all.len(), 2);
        assert!(all.iter().all(|row| row.artifacts.len() == 1));
        let (_, one) = render_selected(
            repo.path(),
            repo.path(),
            &["src/main/java/A.java".into()],
            &["b".into()],
            &cancellation,
        )
        .unwrap();
        assert_eq!(one.len(), 1);
        assert_eq!(one[0].key, "b");
        assert_eq!(one[0].artifacts, all[1].artifacts);
    }

    #[test]
    fn repeated_manifest_protects_dirty_outputs_and_never_writes_unselected_files() {
        let repo = tempfile::tempdir().unwrap();
        let init = super::super::release_baseline::frozen_git_command(repo.path())
            .args(["init", "--quiet"])
            .output()
            .unwrap();
        assert!(
            init.status.success(),
            "{}",
            String::from_utf8_lossy(&init.stderr)
        );
        let root = repo.path().join("projections/example");
        let mut projection = RenderedProjection {
            key: "example".into(),
            root: root.clone(),
            removed: BTreeSet::new(),
            artifacts: [(
                "src/A.java".into(),
                ProjectedArtifact {
                    source_path: format!("{CORE_ROOT}/src/A.java"),
                    source_bytes: b"first".to_vec(),
                    output_bytes: b"first".to_vec(),
                    overlay: None,
                },
            )]
            .into(),
        };
        let initial =
            manifest_selected(repo.path(), std::slice::from_ref(&projection), &None, false)
                .unwrap();
        assert!(initial.writes_performed);
        std::fs::write(root.join("src/unselected.java"), "keep").unwrap();
        projection
            .artifacts
            .get_mut("src/A.java")
            .unwrap()
            .output_bytes = b"second".to_vec();
        assert!(
            manifest_selected(repo.path(), std::slice::from_ref(&projection), &None, false)
                .is_err()
        );
        assert!(
            manifest_selected(
                repo.path(),
                std::slice::from_ref(&projection),
                &Some(Some("elsewhere".into())),
                false
            )
            .is_err()
        );
        let allowed = Some(Some("projections/example/src/A.java".into()));
        let report = manifest_selected(repo.path(), &[projection], &allowed, false).unwrap();
        assert_eq!(report.changed, ["projections/example/src/A.java"]);
        assert_eq!(std::fs::read(root.join("src/A.java")).unwrap(), b"second");
        assert_eq!(
            std::fs::read(root.join("src/unselected.java")).unwrap(),
            b"keep"
        );
        assert!(!root.join(super::super::sync::MANIFEST_FILE).exists());
    }

    #[test]
    fn dirty_overrides_are_scoped_and_status_includes_both_rename_paths() {
        assert!(!permits_dirty(&None, "a/b"));
        assert!(permits_dirty(&Some(None), "a/b"));
        assert!(permits_dirty(&Some(Some("a".into())), "a/b"));
        assert!(!permits_dirty(&Some(Some("a".into())), "ab/c"));
        let paths = parse_dirty_paths(b" M a\0M  b\0?? c\0R  new\0old\0").unwrap();
        assert_eq!(
            paths,
            ["a", "b", "c", "new", "old"].map(str::to_owned).into()
        );
        assert!(parse_dirty_paths(b"R  new\0").is_err());
    }

    #[test]
    fn exact_selectors_do_not_expand_the_write_set() {
        let selectors = vec!["src/A.java".to_owned()];
        assert!(matches_selector(&selectors, "src/A.java"));
        assert!(!matches_selector(&selectors, "src/B.java"));
        assert!(!matches_selector(&selectors, "src/A.java/extra"));
        assert!(matches_selector(&["*".into()], "src/B.java"));
        assert!(matches_selector(&[], "src/B.java"));
    }

    #[test]
    fn selectors_reject_escape_and_ambiguous_match_all() {
        assert!(validate_selectors(&["../elsewhere".into()]).is_err());
        assert!(validate_selectors(&["*".into(), "src/A.java".into()]).is_err());
        assert!(require_matches(&["missing".into()], &BTreeSet::new(), "input").is_err());
    }
}
