use super::difference::FileDifference;
use super::report::ComparisonReport;
use super::request::ComparisonRequest;
use super::snapshot::ComparisonSnapshot;
use crate::cancellation::CancellationToken;
use crate::source_projection::oracle_compare::ComparisonPolicy;
use crate::source_projection::oracle_compare::compare_bytes;
use crate::source_projection::oracle_compare::comparison_inputs;
use crate::source_projection::provenance::sha256;
use eyre::Result;
use eyre::ensure;
use std::collections::BTreeMap;
use std::collections::BTreeSet;

/// Compare captured inputs in deterministic path order, retaining original evidence.
///
/// # Errors
/// Rejects unsupported schema/policy or adapter failures.
pub fn compare(
    request: &ComparisonRequest,
    cancellation: &CancellationToken,
) -> Result<ComparisonReport> {
    ensure!(
        request.schema == "sfm:source-comparison-request@1",
        "unsupported comparison request schema"
    );
    ensure!(
        request.policy.schema_version == 1,
        "unsupported comparison policy"
    );
    ensure!(
        !request.policy.allow_settings_project_identity_override,
        "generic comparison does not infer a target for settings identity overrides"
    );
    let left = super::snapshot(&request.left, cancellation)?;
    let right = super::snapshot(&request.right, cancellation)?;
    let paths: BTreeSet<_> = left.files.keys().chain(right.files.keys()).collect();
    let moves = unique_moves(&left, &right);
    let moved_from: BTreeSet<_> = moves.values().copied().collect();
    let mut differences = Vec::new();
    for path in paths {
        cancellation.bail_if_cancelled()?;
        if moved_from.contains(path.as_str()) {
            continue;
        }
        let old = moves.get(path.as_str()).copied().unwrap_or(path.as_str());
        differences.push(compare_pair(old, path, &left, &right, &request.policy));
    }
    let comparison_identity = sha256(
        facet_json::to_string(&(
            &request,
            &left.identity.snapshot_sha256,
            &right.identity.snapshot_sha256,
        ))?
        .as_bytes(),
    );
    Ok(ComparisonReport {
        comparison_identity,
        schema: "sfm:source-comparison-report@1".into(), request: request.clone(),
        left, right, differences,
        limitations: vec![
            "Pinned Git inputs compare source trees; they do not reconstruct an old Liquid catalog.".into(),
            "Byte allowances are explicit directional views. Original bytes and SHA-256 remain evidence.".into(),
            "Only unique byte-identical removed/added pairs are grouped as moves; ambiguous copies remain separate.".into(),
            "Source changes do not establish behavioral equivalence. No class/debug-metadata classification is inferred.".into(),
            "Windows directory executable modes are unknown. Mode equality is separate from byte equality.".into(),
            "Local inputs are captured during the invocation; concurrently edited directories are not atomic filesystem snapshots.".into(),
            "Read-only captured sides are hash-pinned; OS permissions are not tamper-proof storage.".into(),
            "IntelliJ comparison of left/ and right/ is an independent cross-check.".into(),
        ],
    })
}

fn unique_moves<'a>(
    left: &'a ComparisonSnapshot,
    right: &'a ComparisonSnapshot,
) -> BTreeMap<&'a str, &'a str> {
    let mut removed: BTreeMap<&str, Vec<&str>> = BTreeMap::new();
    let mut added: BTreeMap<&str, Vec<&str>> = BTreeMap::new();
    for (path, file) in &left.files {
        if !right.files.contains_key(path) {
            removed.entry(&file.sha256).or_default().push(path);
        }
    }
    for (path, file) in &right.files {
        if !left.files.contains_key(path) {
            added.entry(&file.sha256).or_default().push(path);
        }
    }
    let mut moves = BTreeMap::new();
    for (hash, old) in &removed {
        if let Some(new) = added.get(hash)
            && old.len() == 1
            && new.len() == 1
            && left.files[old[0]].bytes == right.files[new[0]].bytes
        {
            moves.insert(new[0], old[0]);
        }
    }
    moves
}

fn compare_pair(
    old: &str,
    path: &str,
    left: &ComparisonSnapshot,
    right: &ComparisonSnapshot,
    policy: &ComparisonPolicy,
) -> FileDifference {
    let before = left.files.get(old);
    let after = right.files.get(path);
    let a = before.map_or(&[][..], |file| file.bytes.as_slice());
    let b = after.map_or(&[][..], |file| file.bytes.as_slice());
    let both = before.is_some() && after.is_some();
    let result = compare_bytes(path, a, b, policy);
    let mode_equal = before
        .and_then(|file| file.mode)
        .zip(after.and_then(|file| file.mode))
        .map(|(a, b)| a == b);
    let exact_equal = both && result.exact_equal;
    let normalized_equal = both && result.normalized_equal;
    let status = if old != path {
        "moved"
    } else if before.is_none() {
        "added"
    } else if after.is_none() {
        "removed"
    } else if mode_equal == Some(false) {
        "changed"
    } else if exact_equal {
        "exact"
    } else if normalized_equal {
        "allowed-byte-difference"
    } else {
        "changed"
    };
    let (view_a, view_b) = comparison_inputs(path, a, b, policy);
    let text = |bytes: &[u8]| {
        std::str::from_utf8(bytes)
            .ok()
            .filter(|text| {
                !text
                    .chars()
                    .any(|ch| ch.is_control() && !matches!(ch, '\n' | '\r' | '\t'))
            })
            .map(str::to_owned)
    };
    FileDifference {
        path: path.to_owned(),
        old_path: before.map(|_| old.to_owned()),
        new_path: after.map(|_| path.to_owned()),
        status: status.into(),
        exact_equal,
        normalized_equal,
        mode_equal,
        transformations: result.transformations,
        diagnostics: result.diagnostics,
        before: before.and_then(|_| text(&view_a)),
        after: after.and_then(|_| text(&view_b)),
    }
}
