//! Bounded diagnostics for registered version checkouts, separate from Git pins.
//! gix discovers worktrees; native Git observes status with no index/ref writes.

use super::candidate_lock::checked_directory;
use super::oracle::in_scope;
use super::projection_catalog::SUPPORTED_TARGETS;
use super::release_baseline::frozen_git_command;
use crate::cancellation::CancellationToken;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;
use gix::bstr::ByteSlice;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::io::Read as _;
use std::io::Seek as _;
use std::path::Path;
use std::process::Child;
use std::process::Stdio;
use std::time::Duration;
use std::time::Instant;

const MAX_WORKTREES: usize = 128;
const MAX_OUTPUT: u64 = 2 * 1024 * 1024;
const MAX_STDERR: u64 = 64 * 1024;
const MAX_RECORDS: usize = 20_000;
const PREFIX: &str = "platform/minecraft/";

#[derive(Clone, Debug, Facet)]
pub struct CheckoutObservations {
    pub schema: String,
    pub method: String,
    pub complete: bool,
    pub ignored_policy: String,
    pub errors: Vec<String>,
    pub targets: Vec<CheckoutObservation>,
}

impl CheckoutObservations {
    #[must_use]
    pub fn summary(&self) -> String {
        let dirty = self
            .targets
            .iter()
            .filter(|row| !row.changes.is_empty())
            .count();
        format!(
            "{}: {dirty} checkouts have in-scope authored changes; committed oracle pins are unchanged; see source_checkout_details",
            if self.complete {
                "inspected"
            } else {
                "incomplete_observation"
            }
        )
    }
}

#[derive(Clone, Debug, Facet)]
pub struct CheckoutObservation {
    pub target_id: String,
    pub registered_checkouts: usize,
    pub branch: Option<String>,
    pub head: Option<String>,
    pub observation: String,
    pub staged: usize,
    pub unstaged: usize,
    pub untracked: usize,
    pub excluded_status_records: usize,
    pub changes: Vec<CheckoutChange>,
    pub ignored_source_paths: Vec<String>,
    pub hidden_index_paths: Vec<String>,
    pub errors: Vec<String>,
}

#[derive(Clone, Debug, Eq, PartialEq, Facet)]
pub struct CheckoutChange {
    pub status: String,
    pub path: String,
    pub original_path: Option<String>,
}

/// Observe existing registered checkouts without changing oracle identities.
///
/// # Errors
/// Cancellation propagates. Discovery and individual checkout failures are
/// explicit report diagnostics; they cannot become a successful clean row.
pub fn observe(root: &Path, cancellation: &CancellationToken) -> Result<CheckoutObservations> {
    cancellation.bail_if_cancelled()?;
    let mut report = CheckoutObservations {
        schema: "sfm:oracle_checkout_observations@1".into(),
        method: "isolated_gix_discovery_and_bounded_read_only_git".into(),
        complete: false,
        ignored_policy: "Ignored source/gradle/codestyles files are listed separately; they are not ordinary untracked authoring. Build/cache/world/IDE output outside oracle scope is not enumerated. Configured external filters are disabled during observation.".into(),
        errors: Vec::new(),
        targets: SUPPORTED_TARGETS.iter().map(|(target, _)| CheckoutObservation {
            target_id: (*target).into(), registered_checkouts: 0, branch: None, head: None,
            observation: "no_registered_checkout".into(), staged: 0, unstaged: 0, untracked: 0,
            excluded_status_records: 0, changes: Vec::new(), ignored_source_paths: Vec::new(),
            hidden_index_paths: Vec::new(), errors: Vec::new(),
        }).collect(),
    };
    let budget = Budget {
        cancellation,
        deadline: Instant::now() + Duration::from_secs(45),
    };
    let repositories = match discover(root, &budget) {
        Ok(repositories) => repositories,
        Err(error) => {
            cancellation.bail_if_cancelled()?;
            report
                .errors
                .push(format!("Checkout discovery failed: {error:#}"));
            return Ok(report);
        }
    };
    for row in &mut report.targets {
        cancellation.bail_if_cancelled()?;
        let Some(repositories) = repositories.get(&row.target_id) else {
            continue;
        };
        row.registered_checkouts = repositories.len();
        if repositories.len() != 1 {
            row.observation = "ambiguous_registered_checkouts".into();
            row.errors
                .push("More than one checkout claims the exact version branch".into());
            continue;
        }
        let repository = &repositories[0];
        match inspect(repository, &budget, row) {
            Ok(()) => row.observation = "inspected".into(),
            Err(error) => {
                cancellation.bail_if_cancelled()?;
                row.observation = "observation_failed".into();
                row.errors.push(format!("{error:#}"));
            }
        }
    }
    report.complete = report.errors.is_empty()
        && report
            .targets
            .iter()
            .all(|row| row.observation == "inspected" && row.errors.is_empty());
    Ok(report)
}

fn discover(root: &Path, budget: &Budget<'_>) -> Result<BTreeMap<String, Vec<gix::Repository>>> {
    budget.check()?;
    let options = gix::open::Options::isolated()
        .strict_config(true)
        .config_overrides(["gitoxide.objects.noReplace=true"]);
    let repository = gix::open_opts(root, options)?;
    let proxies = repository.worktrees()?;
    ensure!(
        proxies.len() <= MAX_WORKTREES,
        "Too many registered worktrees to observe"
    );
    let mut repositories = vec![repository.main_repo()?];
    for proxy in proxies {
        budget.check()?;
        repositories.push(proxy.into_repo_with_possibly_inaccessible_worktree()?);
    }
    let mut selected: BTreeMap<String, Vec<gix::Repository>> = BTreeMap::new();
    for mut repository in repositories {
        budget.check()?;
        let _ = repository.clear_namespace();
        if let Some(branch) = repository.head_name()? {
            let branch = branch.as_bstr().to_str()?;
            if let Some(target) = branch
                .strip_prefix("refs/heads/")
                .filter(|target| SUPPORTED_TARGETS.iter().any(|(id, _)| id == target))
            {
                selected
                    .entry(target.to_owned())
                    .or_default()
                    .push(repository);
            }
        }
    }
    Ok(selected)
}

fn inspect(
    repository: &gix::Repository,
    budget: &Budget<'_>,
    row: &mut CheckoutObservation,
) -> Result<()> {
    let branch = repository
        .head_name()?
        .ok_or_else(|| eyre::eyre!("Version checkout became detached"))?;
    row.branch = Some(branch.as_bstr().to_str()?.to_owned());
    row.head = Some(repository.head_id()?.to_string());
    ensure!(
        row.branch.as_deref() == Some(format!("refs/heads/{}", row.target_id).as_str()),
        "Version checkout branch changed during discovery"
    );
    let root = checked_directory(
        repository
            .workdir()
            .ok_or_else(|| eyre::eyre!("Registered checkout has no working directory"))?,
    )?;
    // Reading config key names executes no configured command. Status can run
    // clean/process filters, so every configured driver is overridden first.
    let keys = budget.git(
        &root,
        &["config", "--includes", "--null", "--name-only", "--list"],
        &[],
    )?;
    let filters = disabled_filters(&keys)?;
    let bytes = budget.git(
        &root,
        &[
            "status",
            "--porcelain=v1",
            "-z",
            "--untracked-files=all",
            "--ignore-submodules=all",
            "--",
            "platform/minecraft",
        ],
        &filters,
    )?;
    let (changes, excluded) = parse_status(&bytes)?;
    row.excluded_status_records = excluded;
    for change in &changes {
        let code = change.status.as_bytes();
        if change.status == "??" {
            row.untracked += 1;
        } else {
            row.staged += usize::from(code[0] != b' ');
            row.unstaged += usize::from(code[1] != b' ');
        }
    }
    row.changes = changes;
    let indexed = budget.git(
        &root,
        &["ls-files", "-v", "-z", "--", "platform/minecraft"],
        &filters,
    )?;
    for record in nul_records(&indexed)? {
        ensure!(
            record.len() >= 3 && record[1] == b' ',
            "Malformed index flag record"
        );
        let path = text_path(&record[2..])?;
        if relevant(&path) && (record[0] == b'S' || record[0].is_ascii_lowercase()) {
            row.hidden_index_paths.push(path);
        }
    }
    if !row.hidden_index_paths.is_empty() {
        row.errors.push(
            "Index flags hide in-scope paths from ordinary status; clean authoring is unproven"
                .into(),
        );
    }
    let ignored = budget.git(
        &root,
        &[
            "ls-files",
            "--others",
            "--ignored",
            "--exclude-standard",
            "-z",
            "--",
            "platform/minecraft/src",
            "platform/minecraft/gradle",
            "platform/minecraft/codestyles",
        ],
        &filters,
    )?;
    row.ignored_source_paths = nul_records(&ignored)?
        .into_iter()
        .map(text_path)
        .collect::<Result<_>>()?;
    row.ignored_source_paths.sort();
    row.hidden_index_paths.sort();
    ensure!(
        repository.head_name()?.as_ref() == Some(&branch)
            && repository.head_id()?.to_string() == row.head.as_deref().unwrap_or_default(),
        "Version checkout HEAD changed during observation; rerun before accepting it"
    );
    Ok(())
}

fn disabled_filters(bytes: &[u8]) -> Result<Vec<String>> {
    let mut drivers = BTreeSet::new();
    for key in nul_records(bytes)? {
        let key = std::str::from_utf8(key)?;
        if let Some(rest) = key.strip_prefix("filter.")
            && let Some((driver, member)) = rest.rsplit_once('.')
            && matches!(member, "clean" | "smudge" | "process" | "required")
        {
            ensure!(
                !driver.is_empty() && !driver.chars().any(char::is_control),
                "Invalid filter driver name"
            );
            drivers.insert(driver.to_owned());
        }
    }
    ensure!(
        drivers.len() <= 128,
        "Too many configured filter drivers to inspect safely"
    );
    Ok(drivers
        .into_iter()
        .flat_map(|driver| {
            ["clean", "smudge", "process", "required"]
                .into_iter()
                .flat_map(move |member| {
                    [
                        "-c".into(),
                        format!(
                            "filter.{driver}.{member}={}",
                            if member == "required" { "false" } else { "" }
                        ),
                    ]
                })
        })
        .collect())
}

fn parse_status(bytes: &[u8]) -> Result<(Vec<CheckoutChange>, usize)> {
    let records = nul_records(bytes)?;
    let mut records = records.into_iter();
    let mut changes = Vec::new();
    let mut excluded = 0;
    while let Some(record) = records.next() {
        ensure!(
            record.len() >= 4 && record[2] == b' ',
            "Malformed checkout status record"
        );
        let status = std::str::from_utf8(&record[..2])?.to_owned();
        ensure!(
            record[..2].iter().all(|byte| b" MADRCUT?!".contains(byte)),
            "Unknown checkout status code"
        );
        let path = text_path(&record[3..])?;
        let original_path = if record[..2].contains(&b'R') || record[..2].contains(&b'C') {
            Some(text_path(
                records
                    .next()
                    .ok_or_else(|| eyre::eyre!("Missing rename/copy origin"))?,
            )?)
        } else {
            None
        };
        ensure!(
            status != "!!",
            "Ignored records are not ordinary checkout status"
        );
        if relevant(&path) || original_path.as_deref().is_some_and(relevant) {
            changes.push(CheckoutChange {
                status,
                path,
                original_path,
            });
        } else {
            excluded += 1;
        }
    }
    changes.sort_by(|a, b| {
        a.path
            .cmp(&b.path)
            .then(a.original_path.cmp(&b.original_path))
            .then(a.status.cmp(&b.status))
    });
    Ok((changes, excluded))
}

fn nul_records(bytes: &[u8]) -> Result<Vec<&[u8]>> {
    if bytes.is_empty() {
        return Ok(Vec::new());
    }
    ensure!(
        bytes.last() == Some(&0),
        "Incomplete NUL-delimited Git output"
    );
    let records = bytes[..bytes.len() - 1]
        .split(|byte| *byte == 0)
        .collect::<Vec<_>>();
    ensure!(
        records.len() <= MAX_RECORDS && records.iter().all(|record| !record.is_empty()),
        "Invalid or oversized Git record inventory"
    );
    Ok(records)
}

fn text_path(bytes: &[u8]) -> Result<String> {
    let path = std::str::from_utf8(bytes)
        .wrap_err("Checkout path is not UTF-8; refusing lossy observation")?;
    ensure!(
        !path.starts_with('/') && path.split('/').all(|part| !matches!(part, "" | "." | "..")),
        "Unsafe checkout path"
    );
    Ok(path.to_owned())
}

fn relevant(path: &str) -> bool {
    path.strip_prefix(PREFIX).is_some_and(in_scope)
}

struct Budget<'a> {
    cancellation: &'a CancellationToken,
    deadline: Instant,
}

impl Budget<'_> {
    fn check(&self) -> Result<()> {
        self.cancellation.bail_if_cancelled()?;
        ensure!(
            Instant::now() < self.deadline,
            "Checkout observation exceeded its 45-second budget"
        );
        Ok(())
    }

    fn git(&self, root: &Path, args: &[&str], filter_overrides: &[String]) -> Result<Vec<u8>> {
        self.check()?;
        let mut stdout = tempfile::NamedTempFile::new()?;
        let stderr = tempfile::NamedTempFile::new()?;
        let mut command = frozen_git_command(root);
        command
            .args([
                "--no-optional-locks",
                "--no-pager",
                "--literal-pathspecs",
                "-c",
                "core.fsmonitor=false",
                "-c",
                "core.untrackedCache=false",
                "-c",
                "protocol.allow=never",
            ])
            .args(filter_overrides)
            .args(args)
            .env("GIT_TERMINAL_PROMPT", "0")
            .stdin(Stdio::null())
            .stdout(Stdio::from(stdout.reopen()?))
            .stderr(Stdio::from(stderr.reopen()?));
        let mut child = command
            .spawn()
            .wrap_err("Cannot launch checkout Git diagnostic")?;
        let waited = (|| -> Result<std::process::ExitStatus> {
            loop {
                self.check()?;
                ensure!(
                    stdout.as_file().metadata()?.len() <= MAX_OUTPUT
                        && stderr.as_file().metadata()?.len() <= MAX_STDERR,
                    "Checkout Git diagnostic exceeded its output budget"
                );
                if let Some(status) = child.try_wait()? {
                    return Ok(status);
                }
                std::thread::sleep(Duration::from_millis(10));
            }
        })();
        let status = match waited {
            Ok(status) => status,
            Err(error) => {
                reap_owned(&mut child)?;
                return Err(error);
            }
        };
        ensure!(
            status.success(),
            "Checkout Git diagnostic {:?} failed with {status}",
            args.first()
        );
        stdout.as_file_mut().rewind()?;
        let mut bytes = Vec::new();
        stdout
            .as_file_mut()
            .take(MAX_OUTPUT + 1)
            .read_to_end(&mut bytes)?;
        ensure!(
            bytes.len() as u64 <= MAX_OUTPUT,
            "Checkout Git output grew beyond its bound"
        );
        Ok(bytes)
    }
}

fn reap_owned(child: &mut Child) -> Result<()> {
    let _ = child.kill();
    let deadline = Instant::now() + Duration::from_secs(2);
    loop {
        if child.try_wait()?.is_some() {
            return Ok(());
        }
        ensure!(
            Instant::now() < deadline,
            "Owned checkout Git diagnostic did not terminate; cleanup remains unproven"
        );
        std::thread::sleep(Duration::from_millis(10));
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;

    fn git(root: &Path, args: &[&str]) -> String {
        let output = frozen_git_command(root)
            .args([
                "-c",
                "user.name=Oracle fixture",
                "-c",
                "user.email=oracle@example.invalid",
                "-c",
                "commit.gpgSign=false",
                "-c",
                "core.fsmonitor=false",
            ])
            .args(args)
            .output()
            .unwrap();
        assert!(
            output.status.success(),
            "fixture Git failed: {}",
            String::from_utf8_lossy(&output.stderr)
        );
        String::from_utf8(output.stdout).unwrap().trim().to_owned()
    }

    #[test]
    fn status_parser_preserves_spaces_unicode_and_rename_origin() {
        let bytes = "MM platform/minecraft/src/main/java/A.java\0?? platform/minecraft/src/test/resources/é file.txt\0R  elsewhere/B.java\0platform/minecraft/src/main/java/Old.java\0?? platform/minecraft/build/noise.bin\0".as_bytes();
        let (changes, excluded) = parse_status(bytes).unwrap();
        assert_eq!((changes.len(), excluded), (3, 1));
        assert_eq!(
            changes[0].original_path.as_deref(),
            Some("platform/minecraft/src/main/java/Old.java")
        );
        assert_eq!(
            changes[2].path,
            "platform/minecraft/src/test/resources/é file.txt"
        );
        assert!(parse_status(b"R  platform/minecraft/src/New.java\0").is_err());
        assert!(parse_status(b"?? platform/minecraft/src/a").is_err());
        assert!(parse_status(b"!! platform/minecraft/src/a\0").is_err());
        assert!(parse_status(b"?? platform/minecraft/src/\xff\0").is_err());
    }

    #[test]
    fn observer_preserves_index_refs_sources_and_disables_configured_commands() {
        let temp = tempfile::tempdir().unwrap();
        let linked = tempfile::tempdir().unwrap();
        let root = temp.path();
        git(root, &["init", "--quiet", "--initial-branch=1.19.2"]);
        let source = root.join("platform/minecraft/src/main/java/A.java");
        fs::create_dir_all(source.parent().unwrap()).unwrap();
        fs::write(&source, "class A {}\n").unwrap();
        fs::write(
            root.join(".gitignore"),
            "/platform/minecraft/src/main/antlr/sfml/.antlr/\n",
        )
        .unwrap();
        fs::write(
            root.join(".gitattributes"),
            "platform/minecraft/src/main/java/A.java filter=oracle_fixture\n",
        )
        .unwrap();
        git(root, &["add", "."]);
        git(root, &["commit", "--quiet", "-m", "fixture"]);
        git(
            root,
            &[
                "worktree",
                "add",
                "--quiet",
                "-b",
                "1.19.4",
                linked.path().to_str().unwrap(),
            ],
        );
        fs::write(&source, "class A { int staged; }\n").unwrap();
        git(root, &["add", "platform/minecraft/src/main/java/A.java"]);
        fs::write(&source, "class A { int staged; int unstaged; }\n").unwrap();
        fs::write(
            root.join("platform/minecraft/src/main/java/new file.java"),
            "class B {}\n",
        )
        .unwrap();
        let ignored = root.join("platform/minecraft/src/main/antlr/sfml/.antlr/Generated.java");
        fs::create_dir_all(ignored.parent().unwrap()).unwrap();
        fs::write(ignored, "generated\n").unwrap();
        for key in [
            "filter.oracle_fixture.clean",
            "filter.oracle_fixture.process",
            "core.fsmonitor",
        ] {
            git(
                root,
                &["config", key, "echo unexpected > configured-command-ran"],
            );
        }
        let before_index = fs::read(root.join(".git/index")).unwrap();
        let before_refs = [
            ".git/HEAD",
            ".git/refs/heads/1.19.2",
            ".git/refs/heads/1.19.4",
        ]
        .map(|path| fs::read(root.join(path)).unwrap());
        let linked_index = gix::open_opts(linked.path(), gix::open::Options::isolated())
            .unwrap()
            .git_dir()
            .join("index");
        let before_linked_index = fs::read(&linked_index).unwrap();
        let before_head = git(root, &["rev-parse", "HEAD"]);
        let before_source = fs::read(&source).unwrap();
        let report = observe(root, &CancellationToken::new()).unwrap();
        let row = &report.targets[0];
        assert_eq!(row.observation, "inspected", "{:?}", row.errors);
        assert_eq!((row.staged, row.unstaged, row.untracked), (1, 1, 1));
        assert_eq!(row.branch.as_deref(), Some("refs/heads/1.19.2"));
        assert_eq!(row.head.as_deref(), Some(before_head.as_str()));
        assert_eq!(
            row.ignored_source_paths,
            ["platform/minecraft/src/main/antlr/sfml/.antlr/Generated.java"]
        );
        assert!(!root.join("configured-command-ran").exists());
        assert_eq!(before_index, fs::read(root.join(".git/index")).unwrap());
        assert_eq!(
            before_refs,
            [
                ".git/HEAD",
                ".git/refs/heads/1.19.2",
                ".git/refs/heads/1.19.4"
            ]
            .map(|path| fs::read(root.join(path)).unwrap())
        );
        assert_eq!(before_linked_index, fs::read(linked_index).unwrap());
        assert_eq!(before_head, git(root, &["rev-parse", "HEAD"]));
        assert_eq!(before_source, fs::read(source).unwrap());
        assert_eq!(
            report.targets[1].observation, "inspected",
            "{:?}",
            report.targets[1].errors
        );
        assert_eq!(
            report.targets[1].branch.as_deref(),
            Some("refs/heads/1.19.4")
        );
        assert!(!report.complete); // Eight explicit no-checkout observations.
        assert!(
            report.targets[2..]
                .iter()
                .all(|row| row.observation == "no_registered_checkout")
        );
    }

    #[test]
    fn malformed_filter_names_and_output_framing_refuse_before_status() {
        let overrides = disabled_filters(
            b"filter.example.clean\0filter.example.process\0filter.example.required\0",
        )
        .unwrap();
        assert_eq!(overrides.len(), 8);
        assert!(overrides.contains(&"filter.example.process=".into()));
        assert!(overrides.contains(&"filter.example.required=false".into()));
        assert!(disabled_filters(b"filter.bad\nname.clean\0").is_err());
        assert!(nul_records(b"unterminated").is_err());
    }
}
