//! Read-only destination and Git policy checks for catalog-owned projections.
//!
//! The catalog supplies the exact nested root and environment. Names do not
//! imply release/dev policy, and no output-root override is accepted here.

use super::candidate_lock::checked_directory;
use super::projection_catalog::ProjectionCatalog;
use super::projection_catalog::ProjectionEnvironment;
use super::projection_catalog::validate_projection_key;
use super::sync::ProjectedArtifact;
use super::sync::validate_catalog_artifacts;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fs;
use std::io::Write as _;
use std::path::Component;
use std::path::Path;
use std::path::PathBuf;
use std::process::Command;
use std::process::Output;
use std::process::Stdio;

const MAX_POLICY_PATH_BYTES: usize = 8 * 1024 * 1024;
const MAX_GIT_OUTPUT_BYTES: usize = 16 * 1024 * 1024;

/// Derive a safe exact root and enforce its catalog-selected Git policy.
///
/// Callers load/validate the feature registry separately before rendering.
/// This function validates all catalog root relationships and all prospective
/// artifacts, inspects existing path components, and only reads Git metadata.
/// It does not create directories, stage files, or change ignore rules.
///
/// # Errors
/// Rejects invalid catalog roots/artifacts, non-worktree roots, aliases,
/// reparse/non-directory components, tracked development files, unignored
/// development outputs, and ignored release outputs. An unborn Git worktree
/// is valid; no release or source history is read.
pub fn catalog_projection_root(
    repo_root: &Path,
    catalog: &ProjectionCatalog,
    key: &str,
    artifacts: &BTreeMap<String, ProjectedArtifact>,
) -> Result<PathBuf> {
    catalog_projection_roots(repo_root, catalog, &[(key, artifacts)])?
        .remove(key)
        .ok_or_else(|| eyre::eyre!("requested root was not validated: {key}"))
}

/// Validate a selected batch with shared Git queries, without reading outputs.
pub(crate) fn catalog_projection_roots(
    repo_root: &Path,
    catalog: &ProjectionCatalog,
    selections: &[(&str, &BTreeMap<String, ProjectedArtifact>)],
) -> Result<BTreeMap<String, PathBuf>> {
    let root = checked_directory(repo_root)?;
    ensure_git_worktree_root(&root)?;
    // This boundary validates layout, not feature registration. The catalog
    // loader owns support/prerequisite checks against its actual registry.
    let features = catalog
        .0
        .values()
        .flat_map(|entry| entry.features.iter().cloned())
        .collect();
    catalog.validate(&features)?;
    let mut destinations = BTreeMap::new();
    let mut policies = BTreeMap::new();
    let mut development_roots = Vec::new();
    let mut policy_paths = BTreeSet::new();
    for &(key, artifacts) in selections {
        let entry = catalog.entry(key)?;
        validate_catalog_artifacts(artifacts)?;
        let relative = catalog.project_dir(key)?;
        inspect_destination_chain(&root, &relative)?;
        let destination = root.join(&relative);
        let relative = relative
            .components()
            .map(|component| match component {
                Component::Normal(name) => name
                    .to_str()
                    .ok_or_else(|| eyre::eyre!("catalog destination is not UTF-8")),
                _ => Err(eyre::eyre!(
                    "catalog destination is not an exact relative path"
                )),
            })
            .collect::<Result<Vec<_>>>()?
            .join("/");
        validate_projection_key(&relative)?;

        if entry.environment == ProjectionEnvironment::Dev {
            development_roots.push(relative.clone());
        }
        let mut paths = artifacts
            .keys()
            .map(|path| format!("{relative}/{path}"))
            .collect::<BTreeSet<_>>();
        // Ignoring only Java files is insufficient for a disposable project.
        paths.insert(format!("{relative}/sfm-projection-ignore-probe"));
        policy_paths.extend(paths.iter().cloned());
        policies.insert(key, (entry.environment, paths));
        destinations.insert(key.to_owned(), destination);
    }
    if !development_roots.is_empty() {
        let mut args = vec!["ls-files", "--cached", "-z", "--"];
        args.extend(development_roots.iter().map(String::as_str));
        let tracked = git_query(&root, &args, None)?;
        ensure!(
            tracked.status.success(),
            "cannot inspect tracked development outputs"
        );
        let tracked = nul_paths(&tracked.stdout)?;
        ensure!(
            tracked.is_empty(),
            "selected development projection contains tracked files; move or review them before generation"
        );
    }

    let mut input = Vec::new();
    for path in &policy_paths {
        input.extend_from_slice(path.as_bytes());
        input.push(0);
        ensure!(
            input.len() <= MAX_POLICY_PATH_BYTES,
            "catalog destination policy paths exceed the {MAX_POLICY_PATH_BYTES}-byte limit"
        );
    }
    let ignored = git_query(
        &root,
        &["check-ignore", "--no-index", "--stdin", "-z"],
        Some(input),
    )?;
    ensure!(
        matches!(ignored.status.code(), Some(0 | 1)),
        "cannot inspect catalog destination ignore policy"
    );
    let ignored = nul_paths(&ignored.stdout)?;
    ensure!(
        ignored.is_subset(&policy_paths),
        "Git ignore result contains an unrequested path"
    );
    for (key, (environment, paths)) in policies {
        match environment {
            ProjectionEnvironment::Dev => ensure!(
                paths.is_subset(&ignored),
                "development projection `{key}` is not completely ignored; add an exact root ignore rule before generation"
            ),
            ProjectionEnvironment::Release => ensure!(
                paths.is_disjoint(&ignored),
                "release projection `{key}` contains ignored outputs; make its complete generated tree trackable before generation"
            ),
        }
    }
    Ok(destinations)
}

fn inspect_destination_chain(root: &Path, relative: &Path) -> Result<()> {
    let mut current = root.to_path_buf();
    for component in relative.components() {
        let Component::Normal(name) = component else {
            eyre::bail!("catalog output root contains noncanonical traversal");
        };
        let expected = name
            .to_str()
            .ok_or_else(|| eyre::eyre!("catalog destination component is not UTF-8"))?;
        match fs::symlink_metadata(&current) {
            Ok(metadata) => {
                ensure!(
                    metadata.is_dir() && !is_reparse(&metadata),
                    "catalog output root traverses a reparse point or non-directory"
                );
                for sibling in fs::read_dir(&current)? {
                    let sibling = sibling?;
                    if let Some(actual) = sibling.file_name().to_str()
                        && actual.eq_ignore_ascii_case(expected)
                    {
                        ensure!(
                            actual == expected,
                            "catalog output root has an existing case alias `{actual}` for `{expected}`"
                        );
                    }
                }
            }
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
            Err(error) => return Err(error).wrap_err("cannot inspect catalog output parent"),
        }
        current.push(name);
        match fs::symlink_metadata(&current) {
            Ok(metadata) => ensure!(
                metadata.is_dir() && !is_reparse(&metadata),
                "catalog output root traverses a reparse point or non-directory"
            ),
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
            Err(error) => return Err(error).wrap_err("cannot inspect catalog output component"),
        }
    }
    ensure!(
        current.starts_with(root) && current != root,
        "catalog output root escapes its repository"
    );
    Ok(())
}

fn ensure_git_worktree_root(root: &Path) -> Result<()> {
    let output = git_query(root, &["rev-parse", "--show-toplevel"], None)?;
    ensure!(
        output.status.success(),
        "catalog generation requires a Git worktree"
    );
    let reported = String::from_utf8(output.stdout).wrap_err("Git worktree root is not UTF-8")?;
    ensure!(
        fs::canonicalize(reported.trim())? == root,
        "catalog repository must be the Git worktree root"
    );
    Ok(())
}

fn git_query(root: &Path, args: &[&str], input: Option<Vec<u8>>) -> Result<Output> {
    let mut command = Command::new("git");
    command
        .arg("-c")
        .arg(format!("safe.directory={}", root.display()));
    // ls-files interprets pathspec magic; its exact directory must be literal.
    // check-ignore instead consumes literal file names and explicitly refuses
    // literal pathspec magic, including inherited GIT_LITERAL_PATHSPECS.
    if args.first() == Some(&"ls-files") {
        command.arg("--literal-pathspecs");
    }
    command
        .arg("-C")
        .arg(root)
        .args(args)
        .env_remove("GIT_DIR")
        .env_remove("GIT_WORK_TREE")
        .env_remove("GIT_INDEX_FILE")
        .env_remove("GIT_COMMON_DIR")
        .env_remove("GIT_PREFIX")
        .env_remove("GIT_LITERAL_PATHSPECS")
        .env_remove("GIT_GLOB_PATHSPECS")
        .env_remove("GIT_NOGLOB_PATHSPECS")
        .env_remove("GIT_ICASE_PATHSPECS")
        .stdin(if input.is_some() {
            Stdio::piped()
        } else {
            Stdio::null()
        })
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());
    let mut child = command
        .spawn()
        .wrap_err("cannot start catalog destination Git query")?;
    let writer = input.map(|bytes| {
        let mut stdin = child.stdin.take().expect("piped Git input was requested");
        std::thread::spawn(move || stdin.write_all(&bytes))
    });
    // wait_with_output drains stdout/stderr while the bounded input is written,
    // preventing full pipes from deadlocking a large but valid catalog.
    let output = child
        .wait_with_output()
        .wrap_err("cannot finish catalog destination Git query")?;
    if let Some(writer) = writer {
        let written = writer
            .join()
            .map_err(|_panic_payload| eyre::eyre!("catalog Git input writer panicked"))?;
        if matches!(output.status.code(), Some(0 | 1)) {
            written.wrap_err("cannot write catalog Git policy paths")?;
        }
    }
    ensure!(
        output.stdout.len() <= MAX_GIT_OUTPUT_BYTES && output.stderr.len() <= MAX_GIT_OUTPUT_BYTES,
        "catalog Git query exceeds the {MAX_GIT_OUTPUT_BYTES}-byte result limit"
    );
    ensure!(
        matches!(output.status.code(), Some(0 | 1)),
        "catalog Git {} query exited {:?}: {}",
        args.first().unwrap_or(&"unknown"),
        output.status.code(),
        String::from_utf8_lossy(&output.stderr).trim()
    );
    Ok(output)
}

fn nul_paths(bytes: &[u8]) -> Result<BTreeSet<String>> {
    if bytes.is_empty() {
        return Ok(BTreeSet::new());
    }
    ensure!(
        bytes.last() == Some(&0),
        "Git path response has no terminal NUL"
    );
    let mut paths = BTreeSet::new();
    for path in bytes[..bytes.len() - 1].split(|byte| *byte == 0) {
        let path = std::str::from_utf8(path).wrap_err("Git policy path is not UTF-8")?;
        ensure!(
            !path.is_empty() && paths.insert(path.to_owned()),
            "Git policy response contains an empty or duplicate path"
        );
    }
    Ok(paths)
}

fn is_reparse(metadata: &fs::Metadata) -> bool {
    if metadata.file_type().is_symlink() {
        return true;
    }
    #[cfg(windows)]
    {
        use std::os::windows::fs::MetadataExt as _;
        metadata.file_attributes() & 0x0000_0400 != 0
    }
    #[cfg(not(windows))]
    {
        false
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::source_projection::core_inputs::CORE_ROOT;

    struct Fixture {
        temp: tempfile::TempDir,
        catalog: ProjectionCatalog,
        files: BTreeMap<String, ProjectedArtifact>,
    }

    impl Fixture {
        fn new() -> Self {
            Self::with_temp(tempfile::tempdir().unwrap())
        }

        fn with_temp(temp: tempfile::TempDir) -> Self {
            let init = Command::new("git")
                .arg("-C")
                .arg(temp.path())
                .args(["init", "--quiet"])
                .output()
                .unwrap();
            assert!(
                init.status.success(),
                "Git fixture failed: {}",
                String::from_utf8_lossy(&init.stderr)
            );
            let catalog = ProjectionCatalog::from_json(r#"{
                "release-looking/custom":{"minecraft_version":"1.19.2","environment":"dev","features":[]},
                "sfm-dev/custom":{"minecraft_version":"1.21","environment":"release","features":[]}
            }"#, &BTreeSet::new()).unwrap();
            let files = BTreeMap::from([(
                "src/main/java/Example.java".to_owned(),
                ProjectedArtifact {
                    source_path: format!("{CORE_ROOT}/src/main/java/Example.java"),
                    source_bytes: b"class Example {}\n".to_vec(),
                    output_bytes: b"// generated\nclass Example {}\n".to_vec(),
                    overlay: None,
                },
            )]);
            Self {
                temp,
                catalog,
                files,
            }
        }

        fn root(&self, key: &str) -> Result<PathBuf> {
            catalog_projection_root(self.temp.path(), &self.catalog, key, &self.files)
        }

        fn ignore(&self, text: &str) {
            fs::write(self.temp.path().join(".gitignore"), text).unwrap();
        }
    }

    #[test]
    fn arbitrary_names_obey_explicit_environment_and_do_not_create_roots() {
        let fixture = Fixture::new();
        fixture.ignore("/platform/minecraft/projections/release-looking/custom/\n");
        let roots = catalog_projection_roots(
            fixture.temp.path(),
            &fixture.catalog,
            &[
                ("release-looking/custom", &fixture.files),
                ("sfm-dev/custom", &fixture.files),
            ],
        )
        .unwrap();
        let dev = &roots["release-looking/custom"];
        let release = &roots["sfm-dev/custom"];
        assert!(dev.ends_with("platform/minecraft/projections/release-looking/custom"));
        assert!(release.ends_with("platform/minecraft/projections/sfm-dev/custom"));
        assert!(!dev.exists() && !release.exists());
        assert!(!fixture.temp.path().join("platform").exists());
    }

    #[test]
    fn development_requires_whole_root_ignore_and_refuses_tracked_children() {
        let fixture = Fixture::new();
        assert!(fixture.root("release-looking/custom").is_err());
        fixture.ignore("/platform/minecraft/projections/release-looking/custom/src/\n/platform/minecraft/projections/release-looking/custom/.sfm-source-projection-manifest.json\n");
        assert!(fixture.root("release-looking/custom").is_err());
        fixture.ignore("/platform/minecraft/projections/release-looking/custom/\n");
        let root = fixture.root("release-looking/custom").unwrap();
        fs::create_dir_all(&root).unwrap();
        let tracked = root.join("note.txt");
        fs::write(&tracked, b"tracked fixture\n").unwrap();
        let add = git_query(
            fixture.temp.path(),
            &[
                "add",
                "-f",
                "--",
                "platform/minecraft/projections/release-looking/custom/note.txt",
            ],
            None,
        )
        .unwrap();
        assert!(add.status.success());
        let error = fixture
            .root("release-looking/custom")
            .unwrap_err()
            .to_string();
        assert!(error.contains("tracked files"), "{error}");
        assert_eq!(fs::read(tracked).unwrap(), b"tracked fixture\n");
    }

    #[test]
    fn release_outputs_cannot_be_hidden_by_root_or_single_file_ignore_rules() {
        let fixture = Fixture::new();
        fixture.ignore("/platform/minecraft/projections/sfm-dev/custom/\n");
        assert!(fixture.root("sfm-dev/custom").is_err());
        fixture.ignore("*.java\n");
        assert!(fixture.root("sfm-dev/custom").is_err());
        fixture.ignore("/platform/minecraft/projections/release-looking/custom/\n");
        fixture.root("sfm-dev/custom").unwrap();
    }

    #[test]
    fn catalog_layout_unknown_keys_and_non_git_roots_fail_closed() {
        let mut fixture = Fixture::new();
        assert!(fixture.root("../escape").is_err());
        assert!(fixture.root("unknown").is_err());
        let existing = fixture.catalog.0["sfm-dev/custom"].clone();
        fixture
            .catalog
            .0
            .insert("sfm-dev/custom/child".to_owned(), existing);
        assert!(fixture.root("sfm-dev/custom").is_err());
        let not_git = tempfile::tempdir().unwrap();
        assert!(
            catalog_projection_root(
                not_git.path(),
                &fixture.catalog,
                "sfm-dev/custom",
                &fixture.files
            )
            .is_err()
        );
        assert!(!not_git.path().join("platform").exists());
    }

    #[test]
    fn an_existing_case_alias_or_non_directory_cannot_be_reused() {
        let fixture = Fixture::new();
        let parent = fixture.temp.path().join("platform/minecraft/projections");
        fs::create_dir_all(parent.join("SFM-dev")).unwrap();
        assert!(fixture.root("sfm-dev/custom").is_err());
        fs::remove_dir(parent.join("SFM-dev")).unwrap();
        fs::write(parent.join("sfm-dev"), b"not a directory\n").unwrap();
        assert!(fixture.root("sfm-dev/custom").is_err());
    }

    #[test]
    fn output_and_core_provenance_validation_precede_policy_queries() {
        let mut fixture = Fixture::new();
        let artifact = fixture.files.values().next().unwrap().clone();
        fixture.files.insert("../escape.java".to_owned(), artifact);
        assert!(fixture.root("sfm-dev/custom").is_err());
        let mut fixture = Fixture::new();
        fixture.files.values_mut().next().unwrap().overlay = Some("release-tag".to_owned());
        assert!(fixture.root("sfm-dev/custom").is_err());
    }

    #[cfg(unix)]
    #[test]
    fn named_destination_refuses_symlink_ancestors() {
        use std::os::unix::fs::symlink;
        let fixture = Fixture::new();
        let external = tempfile::tempdir().unwrap();
        fs::create_dir_all(fixture.temp.path().join("platform/minecraft")).unwrap();
        symlink(
            external.path(),
            fixture.temp.path().join("platform/minecraft/projections"),
        )
        .unwrap();
        assert!(fixture.root("sfm-dev/custom").is_err());
        assert!(!external.path().join("sfm-dev").exists());
    }

    #[cfg(windows)]
    #[test]
    fn named_destination_refuses_junction_ancestors() {
        let fixture = Fixture::with_temp(
            tempfile::Builder::new()
                .prefix("sfm named root fixture ")
                .tempdir()
                .unwrap(),
        );
        let external = tempfile::Builder::new()
            .prefix("sfm named root external ")
            .tempdir()
            .unwrap();
        let parent = fixture.temp.path().join("platform/minecraft");
        fs::create_dir_all(&parent).unwrap();
        let junction = parent.join("projections");
        // Pass paths as data, not cmd.exe syntax: nested forward-slash paths
        // are otherwise parsed as switches, and both roots contain spaces.
        let output = Command::new("powershell.exe")
            .args([
                "-NoLogo",
                "-NoProfile",
                "-NonInteractive",
                "-Command",
                "$testJunctionParent = (Resolve-Path -LiteralPath $env:SFM_TEST_NAMED_JUNCTION_PARENT -ErrorAction Stop).ProviderPath; \
                 $testJunctionTarget = (Resolve-Path -LiteralPath $env:SFM_TEST_NAMED_JUNCTION_TARGET -ErrorAction Stop).ProviderPath; \
                 $testJunctionPath = Join-Path -Path $testJunctionParent -ChildPath 'projections'; \
                 New-Item -ItemType Junction -Path $testJunctionPath -Target $testJunctionTarget -ErrorAction Stop | Out-Null",
            ])
            .env("SFM_TEST_NAMED_JUNCTION_PARENT", &parent)
            .env("SFM_TEST_NAMED_JUNCTION_TARGET", external.path())
            .output()
            .unwrap();
        assert!(
            output.status.success(),
            "junction fixture failed: {}",
            String::from_utf8_lossy(&output.stderr)
        );
        let error = fixture.root("sfm-dev/custom").unwrap_err().to_string();
        fs::remove_dir(&junction).unwrap();
        assert!(error.contains("reparse"), "{error}");
        assert!(!external.path().join("sfm-dev").exists());
    }
}
