use super::*;

pub(super) fn compare(
    before: &RepositorySnapshotV1,
    after: &RepositorySnapshotV1,
) -> eyre::Result<RepositoryComparisonV1> {
    let before_files = validate_snapshot(before)?;
    let after_files = validate_snapshot(after)?;
    let paths: BTreeSet<&str> = before_files
        .keys()
        .chain(after_files.keys())
        .map(String::as_str)
        .collect();
    let mut changes = Vec::new();
    for path in paths {
        match (before_files.get(path), after_files.get(path)) {
            (Some(left), Some(right)) if left == right => {}
            (Some(left), Some(right)) => changes.push(modified_change(path, left, right)?),
            (Some(left), None) => changes.push(one_sided_change("removed", path, left, false)),
            (None, Some(right)) => changes.push(one_sided_change("added", path, right, true)),
            (None, None) => unreachable!(),
        }
    }
    Ok(RepositoryComparisonV1 {
        schema: COMPARISON_SCHEMA.to_owned(),
        before_snapshot_id: before.id.clone(),
        after_snapshot_id: after.id.clone(),
        file_changes: changes,
    })
}

fn one_sided_change(kind: &str, path: &str, bytes: &[u8], added: bool) -> FileChangeV1 {
    let is_text = std::str::from_utf8(bytes).is_ok();
    let mut diagnostics = Vec::new();
    let operations = if !is_text {
        diagnostics.push(format!("Binary file {kind}: {path}"));
        Vec::new()
    } else if bytes.is_empty() {
        diagnostics.push(format!("Empty file {kind}: {path}"));
        Vec::new()
    } else {
        let selection = TextSelectionV1 {
            path: path.to_owned(),
            start_byte: 0,
            end_byte: bytes.len(),
            sha256: sha256(bytes),
        };
        let operation_kind = if added { "insert" } else { "delete" };
        vec![operation(
            operation_kind,
            if added { None } else { Some(selection.clone()) },
            added.then_some(selection),
        )]
    };
    FileChangeV1 {
        kind: kind.to_owned(),
        before_path: (!added).then(|| path.to_owned()),
        after_path: added.then(|| path.to_owned()),
        binary: !is_text,
        operations,
        diagnostics,
    }
}

fn modified_change(path: &str, before: &[u8], after: &[u8]) -> eyre::Result<FileChangeV1> {
    let (Ok(before_text), Ok(after_text)) =
        (std::str::from_utf8(before), std::str::from_utf8(after))
    else {
        return Ok(FileChangeV1 {
            kind: "modified".to_owned(),
            before_path: Some(path.to_owned()),
            after_path: Some(path.to_owned()),
            binary: true,
            operations: Vec::new(),
            diagnostics: vec![format!("Binary file modified: {path}")],
        });
    };
    let mut prefix = before
        .iter()
        .zip(after)
        .take_while(|(left, right)| left == right)
        .count();
    while !before_text.is_char_boundary(prefix) || !after_text.is_char_boundary(prefix) {
        prefix -= 1;
    }
    let max_suffix = before.len().min(after.len()).saturating_sub(prefix);
    let mut suffix = before
        .iter()
        .rev()
        .zip(after.iter().rev())
        .take(max_suffix)
        .take_while(|(left, right)| left == right)
        .count();
    while !before_text.is_char_boundary(before.len() - suffix)
        || !after_text.is_char_boundary(after.len() - suffix)
    {
        suffix -= 1;
    }
    let before_end = before.len() - suffix;
    let after_end = after.len() - suffix;
    let before_selection = (prefix < before_end).then(|| TextSelectionV1 {
        path: path.to_owned(),
        start_byte: prefix,
        end_byte: before_end,
        sha256: sha256(&before[prefix..before_end]),
    });
    let after_selection = (prefix < after_end).then(|| TextSelectionV1 {
        path: path.to_owned(),
        start_byte: prefix,
        end_byte: after_end,
        sha256: sha256(&after[prefix..after_end]),
    });
    let kind = match (&before_selection, &after_selection) {
        (None, Some(_)) => "insert",
        (Some(_), None) => "delete",
        (Some(_), Some(_)) => "replace",
        (None, None) => eyre::bail!("modified files produced no changed bytes"),
    };
    Ok(FileChangeV1 {
        kind: "modified".to_owned(),
        before_path: Some(path.to_owned()),
        after_path: Some(path.to_owned()),
        binary: false,
        operations: vec![operation(kind, before_selection, after_selection)],
        diagnostics: Vec::new(),
    })
}

fn operation(
    kind: &str,
    before: Option<TextSelectionV1>,
    after: Option<TextSelectionV1>,
) -> TextOperationV1 {
    let mut framing = Vec::new();
    framing.extend_from_slice(kind.as_bytes());
    framing.push(0);
    for selection in [&before, &after] {
        if let Some(selection) = selection {
            framing.extend_from_slice(selection.path.as_bytes());
            framing.push(0);
            framing.extend_from_slice(&selection.start_byte.to_be_bytes());
            framing.extend_from_slice(&selection.end_byte.to_be_bytes());
            framing.extend_from_slice(selection.sha256.as_bytes());
        }
        framing.push(0xff);
    }
    let digest = sha256(&framing);
    TextOperationV1 {
        id: format!("op-{}", &digest[..24]),
        kind: kind.to_owned(),
        before,
        after,
    }
}

pub(super) fn validate_comparison(
    comparison: &RepositoryComparisonV1,
    before_snapshot: &RepositorySnapshotV1,
    before: &BTreeMap<String, Vec<u8>>,
    after_snapshot: &RepositorySnapshotV1,
    after: &BTreeMap<String, Vec<u8>>,
) -> eyre::Result<()> {
    require_equal(&comparison.schema, COMPARISON_SCHEMA, "comparison schema")?;
    if comparison.before_snapshot_id != before_snapshot.id
        || comparison.after_snapshot_id != after_snapshot.id
    {
        eyre::bail!("comparison has dangling snapshot references");
    }
    let mut previous: Option<Vec<u8>> = None;
    let mut operation_ids = BTreeSet::new();
    for change in &comparison.file_changes {
        let key = change
            .after_path
            .as_ref()
            .or(change.before_path.as_ref())
            .ok_or_else(|| eyre!("file change has no path"))?;
        validate_path(key)?;
        if previous
            .as_ref()
            .is_some_and(|value| value.as_slice() >= key.as_bytes())
        {
            eyre::bail!("file changes are duplicate or not path-sorted at {key}");
        }
        previous = Some(key.as_bytes().to_vec());
        validate_change_paths(change, before, after)?;
        if change.binary && !change.operations.is_empty() {
            eyre::bail!("binary change {key} may not carry text operations");
        }
        if change.binary && change.diagnostics.is_empty() {
            eyre::bail!("binary change {key} requires a diagnostic");
        }
        for operation in &change.operations {
            if !operation_ids.insert(&operation.id) || operation.id.is_empty() {
                eyre::bail!("duplicate or empty operation id {}", operation.id);
            }
            match operation.kind.as_str() {
                "insert" if operation.before.is_none() && operation.after.is_some() => {}
                "delete" if operation.before.is_some() && operation.after.is_none() => {}
                "replace" if operation.before.is_some() && operation.after.is_some() => {}
                other => eyre::bail!(
                    "operation {} has invalid kind/sides {other:?}",
                    operation.id
                ),
            }
            if let Some(selection) = &operation.before {
                validate_selection(selection, before, &operation.id)?;
            }
            if let Some(selection) = &operation.after {
                validate_selection(selection, after, &operation.id)?;
            }
        }
    }
    Ok(())
}

fn validate_change_paths(
    change: &FileChangeV1,
    before: &BTreeMap<String, Vec<u8>>,
    after: &BTreeMap<String, Vec<u8>>,
) -> eyre::Result<()> {
    match change.kind.as_str() {
        "added"
            if change.before_path.is_none()
                && change
                    .after_path
                    .as_ref()
                    .is_some_and(|path| after.contains_key(path)) => {}
        "removed"
            if change.after_path.is_none()
                && change
                    .before_path
                    .as_ref()
                    .is_some_and(|path| before.contains_key(path)) => {}
        "modified" | "unchanged"
            if change
                .before_path
                .as_ref()
                .is_some_and(|path| before.contains_key(path))
                && change
                    .after_path
                    .as_ref()
                    .is_some_and(|path| after.contains_key(path)) => {}
        "renamed"
            if change
                .before_path
                .as_ref()
                .is_some_and(|path| before.contains_key(path))
                && change
                    .after_path
                    .as_ref()
                    .is_some_and(|path| after.contains_key(path)) => {}
        other => eyre::bail!("file change has invalid kind or dangling paths: {other:?}"),
    }
    Ok(())
}

fn validate_selection(
    selection: &TextSelectionV1,
    files: &BTreeMap<String, Vec<u8>>,
    operation_id: &str,
) -> eyre::Result<()> {
    validate_path(&selection.path)?;
    let bytes = files.get(&selection.path).ok_or_else(|| {
        eyre!(
            "operation {operation_id} references missing file {}",
            selection.path
        )
    })?;
    let text = std::str::from_utf8(bytes)
        .wrap_err_with(|| format!("operation {operation_id} selects binary file"))?;
    if selection.start_byte >= selection.end_byte || selection.end_byte > bytes.len() {
        eyre::bail!("operation {operation_id} has invalid or empty range");
    }
    if !text.is_char_boundary(selection.start_byte) || !text.is_char_boundary(selection.end_byte) {
        eyre::bail!("operation {operation_id} range splits UTF-8");
    }
    validate_digest(
        &selection.sha256,
        &sha256(&bytes[selection.start_byte..selection.end_byte]),
        &format!("operation {operation_id} selection"),
    )
}
