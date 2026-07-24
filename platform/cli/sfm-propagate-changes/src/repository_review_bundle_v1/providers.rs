use super::*;
use std::io::BufRead;
use std::io::BufReader;
use std::io::Write as _;
use std::path::Component;
use std::process::Command;
use std::process::Stdio;

pub(super) fn load_snapshot(
    provider: SnapshotProviderV1,
    repository: &Path,
    value: &str,
    label: &str,
) -> eyre::Result<RepositorySnapshotV1> {
    let (kind, files) = match provider {
        SnapshotProviderV1::Git => ("git_tree", load_git_files(repository, value)?),
        SnapshotProviderV1::Directory => ("directory", load_directory_files(Path::new(value))?),
    };
    make_snapshot(
        SnapshotSourceV1 {
            kind: kind.to_owned(),
            revision: value.to_owned(),
            label: label.to_owned(),
        },
        files,
    )
}

pub(super) fn make_snapshot(
    source: SnapshotSourceV1,
    mut files: Vec<SnapshotFileV1>,
) -> eyre::Result<RepositorySnapshotV1> {
    files.sort_by(|left, right| left.path.as_bytes().cmp(right.path.as_bytes()));
    let mut snapshot = RepositorySnapshotV1 {
        schema: SNAPSHOT_SCHEMA.to_owned(),
        id: String::new(),
        source,
        files,
    };
    snapshot.id = snapshot_id(&snapshot.files)?;
    validate_snapshot(&snapshot)?;
    Ok(snapshot)
}

pub(super) fn load_directory_files(root: &Path) -> eyre::Result<Vec<SnapshotFileV1>> {
    if !root.is_dir() {
        eyre::bail!("prepared directory does not exist: {}", root.display());
    }
    let mut files = Vec::new();
    let mut total_bytes = 0usize;
    for entry in walkdir::WalkDir::new(root).follow_links(false) {
        let entry = entry.wrap_err_with(|| format!("could not walk {}", root.display()))?;
        if entry.file_type().is_symlink() {
            eyre::bail!(
                "prepared directories may not contain symlinks: {}",
                entry.path().display()
            );
        }
        if !entry.file_type().is_file() {
            continue;
        }
        let relative = entry
            .path()
            .strip_prefix(root)
            .wrap_err("directory entry escaped root")?;
        let path = portable_path(relative)?;
        let metadata = entry
            .metadata()
            .wrap_err_with(|| format!("could not inspect {}", entry.path().display()))?;
        let size = usize::try_from(metadata.len()).wrap_err_with(|| {
            format!(
                "file size does not fit this platform: {}",
                entry.path().display()
            )
        })?;
        if size > MAX_FILE_BYTES {
            eyre::bail!("file {path} exceeds the {MAX_FILE_BYTES}-byte v1 limit before allocation");
        }
        total_bytes = total_bytes
            .checked_add(size)
            .ok_or_else(|| eyre!("snapshot byte count overflow"))?;
        check_snapshot_bounds(files.len() + 1, total_bytes)?;
        let bytes = std::fs::read(entry.path())
            .wrap_err_with(|| format!("could not read {}", entry.path().display()))?;
        files.push(snapshot_file(path, bytes)?);
        check_discovery_bounds(&files)?;
    }
    Ok(files)
}

pub(super) fn load_git_files(
    repository: &Path,
    revision: &str,
) -> eyre::Result<Vec<SnapshotFileV1>> {
    validate_git_revision(revision)?;
    let output = git(repository, &["ls-tree", "-rz", "--full-tree", revision])?;
    let mut entries = Vec::new();
    for record in output
        .split(|byte| *byte == 0)
        .filter(|record| !record.is_empty())
    {
        let tab = record
            .iter()
            .position(|byte| *byte == b'\t')
            .ok_or_else(|| eyre!("malformed git ls-tree record"))?;
        let metadata =
            std::str::from_utf8(&record[..tab]).wrap_err("git emitted non-UTF-8 tree metadata")?;
        let mut fields = metadata.split(' ');
        let _mode = fields.next().ok_or_else(|| eyre!("missing git mode"))?;
        let kind = fields
            .next()
            .ok_or_else(|| eyre!("missing git object kind"))?;
        let oid = fields
            .next()
            .ok_or_else(|| eyre!("missing git object id"))?;
        if kind != "blob" {
            eyre::bail!(
                "unsupported git tree entry kind {kind:?}; submodules are not files in bundle v1"
            );
        }
        let raw_path = &record[tab + 1..];
        let path = std::str::from_utf8(raw_path)
            .wrap_err("git path is not valid UTF-8")?
            .to_owned();
        validate_path(&path)?;
        entries.push((path, oid.to_owned()));
        if entries.len() > MAX_FILES {
            eyre::bail!("snapshot exceeds the {MAX_FILES}-file v1 limit");
        }
    }
    entries.sort_by(|left, right| left.0.as_bytes().cmp(right.0.as_bytes()));
    for pair in entries.windows(2) {
        if pair[0].0 == pair[1].0 {
            eyre::bail!("duplicate git path {}", pair[0].0);
        }
    }
    let blobs = git_cat_file_batch(repository, entries.iter().map(|entry| entry.1.as_str()))?;
    entries
        .into_iter()
        .zip(blobs)
        .map(|((path, _), bytes)| snapshot_file(path, bytes))
        .collect()
}

fn validate_git_revision(revision: &str) -> eyre::Result<()> {
    if revision.is_empty() || revision.starts_with('-') || revision.chars().any(char::is_control) {
        eyre::bail!(
            "Git revision must be non-empty, may not begin with '-', and may not contain control characters"
        );
    }
    Ok(())
}

pub(super) fn git(repository: &Path, arguments: &[&str]) -> eyre::Result<Vec<u8>> {
    let output = Command::new("git")
        .arg("-C")
        .arg(repository)
        .args(arguments)
        .output()
        .wrap_err_with(|| format!("could not start git for {}", repository.display()))?;
    if !output.status.success() {
        eyre::bail!(
            "git {} failed: {}",
            arguments.join(" "),
            String::from_utf8_lossy(&output.stderr).trim()
        );
    }
    Ok(output.stdout)
}

fn git_cat_file_batch<'a>(
    repository: &Path,
    object_ids: impl Iterator<Item = &'a str>,
) -> eyre::Result<Vec<Vec<u8>>> {
    let ids: Vec<&str> = object_ids.collect();
    let mut child = Command::new("git")
        .arg("-C")
        .arg(repository)
        .args(["cat-file", "--batch"])
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::inherit())
        .spawn()
        .wrap_err("could not start git cat-file --batch")?;
    let mut input = child
        .stdin
        .take()
        .ok_or_else(|| eyre!("git batch stdin unavailable"))?;
    let output = child
        .stdout
        .take()
        .ok_or_else(|| eyre!("git batch stdout unavailable"))?;
    let mut reader = BufReader::new(output);
    let mut total_bytes = 0usize;
    let result = (|| {
        let mut blobs = Vec::with_capacity(ids.len());
        for expected in ids {
            writeln!(input, "{expected}")?;
            input.flush()?;
            blobs.push(read_git_blob(&mut reader, expected, &mut total_bytes)?);
        }
        Ok::<Vec<Vec<u8>>, eyre::Report>(blobs)
    })();
    drop(input);
    drop(reader);
    if result.is_err() {
        let _ignored = child.kill();
    }
    let status = child
        .wait()
        .wrap_err("could not reap git cat-file --batch")?;
    let blobs = result?;
    if !status.success() {
        eyre::bail!("git cat-file --batch failed with {status}");
    }
    Ok(blobs)
}

pub(super) fn read_git_blob(
    reader: &mut impl BufRead,
    expected: &str,
    total_bytes: &mut usize,
) -> eyre::Result<Vec<u8>> {
    let mut header = String::new();
    if reader.read_line(&mut header)? == 0 || !header.ends_with('\n') {
        eyre::bail!("truncated git batch header");
    }
    let fields: Vec<&str> = header.trim_end_matches('\n').split(' ').collect();
    if fields.len() != 3 || fields[0] != expected || fields[1] != "blob" {
        eyre::bail!("unexpected git batch header {:?}", header.trim_end());
    }
    let size: usize = fields[2].parse().wrap_err("invalid git blob size")?;
    if size > MAX_FILE_BYTES {
        eyre::bail!("git blob exceeds the {MAX_FILE_BYTES}-byte file limit before allocation");
    }
    *total_bytes = total_bytes
        .checked_add(size)
        .ok_or_else(|| eyre!("snapshot byte count overflow"))?;
    check_snapshot_bounds(0, *total_bytes)?;
    let mut bytes = vec![0; size];
    reader
        .read_exact(&mut bytes)
        .wrap_err("truncated git blob payload")?;
    let mut newline = [0];
    reader
        .read_exact(&mut newline)
        .wrap_err("missing git blob terminator")?;
    if newline != [b'\n'] {
        eyre::bail!("invalid git blob terminator");
    }
    Ok(bytes)
}

fn portable_path(path: &Path) -> eyre::Result<String> {
    let mut segments = Vec::new();
    for component in path.components() {
        match component {
            Component::Normal(value) => segments.push(
                value
                    .to_str()
                    .ok_or_else(|| eyre!("path is not valid UTF-8"))?,
            ),
            _ => eyre::bail!("path is not repository-relative: {}", path.display()),
        }
    }
    let value = segments.join("/");
    validate_path(&value)?;
    Ok(value)
}

pub(super) fn snapshot_file(path: String, bytes: Vec<u8>) -> eyre::Result<SnapshotFileV1> {
    validate_path(&path)?;
    if bytes.len() > MAX_FILE_BYTES {
        eyre::bail!("file {path} exceeds the {MAX_FILE_BYTES}-byte v1 limit");
    }
    let hash = sha256(&bytes);
    Ok(match String::from_utf8(bytes.clone()) {
        Ok(text) => SnapshotFileV1 {
            path,
            encoding: "utf8".to_owned(),
            text: Some(text),
            data: None,
            sha256: hash,
        },
        Err(_) => SnapshotFileV1 {
            path,
            encoding: "base64".to_owned(),
            text: None,
            data: Some(BASE64_STANDARD.encode(bytes)),
            sha256: hash,
        },
    })
}

fn check_discovery_bounds(files: &[SnapshotFileV1]) -> eyre::Result<()> {
    let total = files.iter().try_fold(0usize, |total, file| {
        total
            .checked_add(file_bytes(file)?.len())
            .ok_or_else(|| eyre!("snapshot byte count overflow"))
    })?;
    check_snapshot_bounds(files.len(), total)
}

pub(super) fn check_snapshot_bounds(file_count: usize, total_bytes: usize) -> eyre::Result<()> {
    if file_count > MAX_FILES {
        eyre::bail!("snapshot exceeds the {MAX_FILES}-file v1 limit");
    }
    if total_bytes > MAX_SNAPSHOT_BYTES {
        eyre::bail!("snapshot exceeds the {MAX_SNAPSHOT_BYTES}-byte v1 limit");
    }
    Ok(())
}
