//! Immutable, portable working-tree source evidence. No filesystem IO occurs here.

use eyre::eyre;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;

pub const SCHEMA: &str = "sfm.release-review.working-tree-capture/1";
pub const ID_PREFIX: &str = "working-tree:sha256:";

#[derive(Clone, Debug, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct WorkingTreeCaptureV1 {
    pub schema: String,
    pub id: String,
    pub observed_head_commit: String,
    pub observed_head_tree: String,
    pub captured_at_unix_ms: u64,
    pub consistency: String,
    pub scope_paths: Vec<String>,
    pub excluded_paths: Vec<String>,
    pub include_untracked: bool,
    pub entries: Vec<WorkingTreeEntryV1>,
}

#[derive(Clone, Debug, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct WorkingTreeEntryV1 {
    pub path: String,
    pub kind: String,
    pub tracked: bool,
    #[facet(skip_serializing_if = Option::is_none)]
    pub byte_length: Option<u64>,
    #[facet(skip_serializing_if = Option::is_none)]
    pub sha256: Option<String>,
    pub executable: bool,
    pub materialization: String,
    #[facet(skip_serializing_if = Option::is_none)]
    pub document_revision_id: Option<String>,
    #[facet(skip_serializing_if = Option::is_none)]
    pub diagnostic: Option<String>,
}

impl WorkingTreeCaptureV1 {
    /// Bind the manifest to ordinary portable preview bytes, corpus sources and review units.
    ///
    /// # Errors
    /// Rejects missing or inconsistent content/source authority without consulting the filesystem.
    pub fn validate_against(
        &self,
        lane: &crate::review_session_v1::RevisionLaneV1,
        review: &crate::release_review_v1::ReleaseReviewDocumentV1,
    ) -> eyre::Result<()> {
        use crate::release_review_v1::MaterializationV1;
        use crate::release_review_v1::SnapshotSideV1;
        if self.id != lane.after.id {
            return Err(eyre!("capture snapshot identity mismatch"));
        }
        let entries = self
            .entries
            .iter()
            .map(|entry| (entry.path.as_str(), entry))
            .collect::<BTreeMap<_, _>>();
        let documents = lane
            .after
            .documents
            .iter()
            .map(|doc| (doc.path.as_str(), doc))
            .collect::<BTreeMap<_, _>>();
        for doc in &lane.after.documents {
            let entry = entries
                .get(doc.path.as_str())
                .ok_or_else(|| eyre!("embedded document absent from capture"))?;
            if entry.materialization != "utf8"
                || entry.document_revision_id.as_deref() != Some(doc.id.as_str())
                || entry.sha256.as_deref() != Some(doc.sha256.as_str())
                || entry.byte_length != Some(doc.text.len() as u64)
            {
                return Err(eyre!(
                    "capture manifest disagrees with embedded bytes: {}",
                    doc.path
                ));
            }
        }
        let before = lane
            .before
            .documents
            .iter()
            .map(|doc| doc.path.as_str())
            .collect::<BTreeSet<_>>();
        let represented = review
            .review_units
            .iter()
            .filter(|unit| unit.lane_id == lane.id)
            .flat_map(|unit| unit.path_before.iter().chain(unit.path_after.iter()))
            .map(String::as_str)
            .collect::<BTreeSet<_>>();
        for entry in &self.entries {
            if entry.materialization == "utf8" && !documents.contains_key(entry.path.as_str()) {
                return Err(eyre!("captured UTF-8 document is missing: {}", entry.path));
            }
            let changed = entry.materialization != "unchanged"
                && (entry.kind != "deleted" || before.contains(entry.path.as_str()));
            if changed && !represented.contains(entry.path.as_str()) {
                return Err(eyre!("capture change has no review unit: {}", entry.path));
            }
        }
        if represented.iter().any(|path| !entries.contains_key(path)) {
            return Err(eyre!("review unit is outside captured scope"));
        }
        let mut corpus_paths = BTreeSet::new();
        for corpus in review
            .corpus_documents
            .iter()
            .filter(|c| c.lane_id == lane.id && c.snapshot_side == SnapshotSideV1::After)
        {
            let entry = entries
                .get(corpus.path.as_str())
                .ok_or_else(|| eyre!("after corpus absent from capture"))?;
            corpus_paths.insert(corpus.path.as_str());
            if entry.kind == "deleted"
                || entry.materialization == "unchanged"
                || corpus.source_owner != self.id
                || corpus.source_locator
                    != format!(
                        "working-tree-capture://{}/{}",
                        &self.id[ID_PREFIX.len()..],
                        corpus.path
                    )
                || entry
                    .sha256
                    .as_ref()
                    .is_some_and(|hash| hash != &corpus.sha256)
                || ((entry.materialization == "utf8")
                    != (corpus.materialization == MaterializationV1::Complete))
            {
                return Err(eyre!(
                    "capture corpus source witness mismatch: {}",
                    corpus.path
                ));
            }
        }
        for entry in &self.entries {
            if entry.kind != "deleted"
                && entry.materialization != "unchanged"
                && !corpus_paths.contains(entry.path.as_str())
            {
                return Err(eyre!("captured after corpus is missing: {}", entry.path));
            }
        }
        Ok(())
    }

    /// Content identity uses length-framed UTF-8 fields, independent of JSON escaping.
    #[must_use]
    pub fn computed_id(&self) -> String {
        let mut bytes = format!("{SCHEMA}\n").into_bytes();
        frame(&mut bytes, &self.observed_head_commit);
        frame(&mut bytes, &self.observed_head_tree);
        frame(&mut bytes, &self.scope_paths.len().to_string());
        for path in &self.scope_paths {
            frame(&mut bytes, path);
        }
        frame(&mut bytes, &self.excluded_paths.len().to_string());
        for path in &self.excluded_paths {
            frame(&mut bytes, path);
        }
        frame(
            &mut bytes,
            if self.include_untracked {
                "true"
            } else {
                "false"
            },
        );
        frame(&mut bytes, &self.entries.len().to_string());
        for entry in &self.entries {
            frame(&mut bytes, &entry.path);
            frame(&mut bytes, &entry.kind);
            frame(&mut bytes, if entry.tracked { "true" } else { "false" });
            frame(
                &mut bytes,
                &entry
                    .byte_length
                    .map_or_else(|| "-".to_owned(), |n| n.to_string()),
            );
            frame(&mut bytes, entry.sha256.as_deref().unwrap_or("-"));
            frame(&mut bytes, if entry.executable { "true" } else { "false" });
            frame(&mut bytes, &entry.materialization);
        }
        format!("{ID_PREFIX}{}", crate::review_session_v1::sha256(&bytes))
    }

    /// Validate source authority and hash binding without reading a local checkout.
    ///
    /// # Errors
    /// Rejects unsafe or inconsistent manifests and identities.
    pub fn validate(&self) -> eyre::Result<()> {
        if self.schema != SCHEMA
            || self.consistency != "verified_two_pass"
            || self.captured_at_unix_ms > i64::MAX as u64
        {
            return Err(eyre!("unsupported working-tree capture schema/consistency"));
        }
        require_hex(&self.observed_head_commit, 40)?;
        require_hex(&self.observed_head_tree, 40)?;
        if self.scope_paths.is_empty() || self.entries.len() > 100_000 {
            return Err(eyre!("working-tree capture scope/count is invalid"));
        }
        validate_prefixes(&self.scope_paths)?;
        validate_prefixes(&self.excluded_paths)?;
        let scopes: BTreeSet<_> = self.scope_paths.iter().map(String::as_str).collect();
        let exclusions: BTreeSet<_> = self.excluded_paths.iter().map(String::as_str).collect();
        let mut previous: Option<&str> = None;
        let mut total = 0;
        for entry in &self.entries {
            validate_path(&entry.path, false)?;
            if previous.is_some_and(|p| p >= entry.path.as_str()) {
                return Err(eyre!("capture entries must be sorted and unique"));
            }
            previous = Some(&entry.path);
            if !in_prefixes(&scopes, &entry.path)
                || in_prefixes(&exclusions, &entry.path)
                || (!entry.tracked && !self.include_untracked)
            {
                return Err(eyre!(
                    "capture entry is outside its source policy: {}",
                    entry.path
                ));
            }
            entry.validate()?;
            total += entry.byte_length.unwrap_or(0);
        }
        if total > 256 * 1024 * 1024 {
            return Err(eyre!("capture exceeds aggregate read limit"));
        }
        if self.id != self.computed_id() {
            return Err(eyre!("working-tree capture identity mismatch"));
        }
        Ok(())
    }
}

impl WorkingTreeEntryV1 {
    fn validate(&self) -> eyre::Result<()> {
        let has_document = self
            .document_revision_id
            .as_ref()
            .is_some_and(|id| !id.trim().is_empty());
        if self.document_revision_id.is_some() != has_document
            || self
                .diagnostic
                .as_ref()
                .is_some_and(|text| text.trim().is_empty())
        {
            return Err(eyre!("empty capture entry document/diagnostic"));
        }
        match self.kind.as_str() {
            "regular_file" => {
                require_hex(
                    self.sha256
                        .as_deref()
                        .ok_or_else(|| eyre!("missing file hash"))?,
                    64,
                )?;
                let size = self.byte_length.ok_or_else(|| eyre!("missing file size"))?;
                if size > 64 * 1024 * 1024 {
                    return Err(eyre!("capture file exceeds read limit"));
                }
                match self.materialization.as_str() {
                    "utf8" if has_document && size <= 4 * 1024 * 1024 => {}
                    "unchanged" if !has_document => {}
                    "binary" if !has_document && self.diagnostic.is_some() => {}
                    "oversized"
                        if !has_document && self.diagnostic.is_some() && size > 4 * 1024 * 1024 => {
                    }
                    _ => return Err(eyre!("invalid regular-file materialization")),
                }
            }
            "deleted" | "symlink" | "gitlink" => {
                if self.sha256.is_some()
                    || self.byte_length.is_some()
                    || has_document
                    || self.executable
                    || self.materialization != "unavailable"
                    || self.diagnostic.is_none()
                    || (self.kind == "deleted" && !self.tracked)
                {
                    return Err(eyre!("invalid absent/unsupported capture entry"));
                }
            }
            _ => return Err(eyre!("unknown capture entry kind")),
        }
        Ok(())
    }
}

fn frame(bytes: &mut Vec<u8>, value: &str) {
    bytes.extend_from_slice(value.len().to_string().as_bytes());
    bytes.push(b':');
    bytes.extend_from_slice(value.as_bytes());
    bytes.push(b'\n');
}

fn in_prefixes(prefixes: &BTreeSet<&str>, path: &str) -> bool {
    prefixes.contains(".")
        || prefixes.contains(path)
        || path
            .match_indices('/')
            .any(|(index, _)| prefixes.contains(&path[..index]))
}

pub(crate) fn validate_path(path: &str, prefix: bool) -> eyre::Result<()> {
    if prefix && path == "." {
        return Ok(());
    }
    if path.len() > 4096
        || path.contains(['\\', ':', '\0'])
        || path.split('/').any(|part| {
            part.is_empty() || part == "." || part == ".." || part.eq_ignore_ascii_case(".git")
        })
    {
        return Err(eyre!("unsafe capture path: {path}"));
    }
    Ok(())
}

fn validate_prefixes(prefixes: &[String]) -> eyre::Result<()> {
    if prefixes.len() > 100_000 {
        return Err(eyre!("too many capture prefixes"));
    }
    let mut previous: Option<&str> = None;
    let mut seen = BTreeSet::new();
    for prefix in prefixes {
        validate_path(prefix, true)?;
        if previous.is_some_and(|p| p >= prefix.as_str()) || in_prefixes(&seen, prefix) {
            return Err(eyre!(
                "capture prefixes must be sorted, unique and nonredundant"
            ));
        }
        previous = Some(prefix);
        seen.insert(prefix.as_str());
    }
    Ok(())
}

/// Decode and validate a portable source manifest without local filesystem access.
///
/// # Errors
/// Rejects malformed JSON, unknown fields, invalid policy, and mismatched identity.
pub fn parse(input: &str) -> eyre::Result<WorkingTreeCaptureV1> {
    validate_wire_types(input)?;
    let capture: WorkingTreeCaptureV1 = facet_json::from_str(input)
        .map_err(|error| eyre!("invalid working-tree capture JSON: {error}"))?;
    capture.validate()?;
    Ok(capture)
}

// The pinned Facet decoder permits scalar string coercion. Inspect raw tokens
// first so Java and Rust agree on the source-authority wire types.
pub(crate) fn validate_wire_types(input: &str) -> eyre::Result<()> {
    type Fields = BTreeMap<String, facet_json::RawJson<'static>>;
    let fields: Fields = facet_json::from_str(input).map_err(|e| eyre!("capture object: {e}"))?;
    for (key, raw) in &fields {
        let text = raw.as_str().trim();
        match key.as_str() {
            "captured_at_unix_ms" => require_number_token(text)?,
            "include_untracked" => require_bool_token(text)?,
            "scope_paths" | "excluded_paths" => {
                let paths: Vec<facet_json::RawJson<'static>> =
                    facet_json::from_str(text).map_err(|e| eyre!("capture path array: {e}"))?;
                for path in paths {
                    require_string_token(path.as_str().trim())?;
                }
            }
            "entries" => {
                let entries: Vec<Fields> =
                    facet_json::from_str(text).map_err(|e| eyre!("capture entry array: {e}"))?;
                for entry in entries {
                    for (key, raw) in entry {
                        let text = raw.as_str().trim();
                        match key.as_str() {
                            "tracked" | "executable" => require_bool_token(text)?,
                            "byte_length" if text != "null" => require_number_token(text)?,
                            "byte_length" => {}
                            "sha256" | "diagnostic" | "document_revision_id" if text == "null" => {}
                            _ => require_string_token(text)?,
                        }
                    }
                }
            }
            _ => require_string_token(text)?,
        }
    }
    Ok(())
}

fn require_bool_token(token: &str) -> eyre::Result<()> {
    if !matches!(token, "true" | "false") {
        return Err(eyre!("expected capture Boolean"));
    }
    Ok(())
}

fn require_number_token(token: &str) -> eyre::Result<()> {
    if token.is_empty()
        || !token.bytes().all(|b| b.is_ascii_digit())
        || (token.len() > 1 && token.starts_with('0'))
    {
        return Err(eyre!("expected nonnegative capture integer"));
    }
    Ok(())
}

fn require_string_token(token: &str) -> eyre::Result<()> {
    if !token.starts_with('"') {
        return Err(eyre!("expected capture string"));
    }
    Ok(())
}

/// Encode the manifest's canonical wire field order.
///
/// # Errors
/// Rejects invalid capture evidence or serialization failure.
pub fn to_canonical_json(capture: &WorkingTreeCaptureV1) -> eyre::Result<String> {
    capture.validate()?;
    let mut json = facet_json::to_string_pretty(capture)
        .map_err(|error| eyre!("cannot serialize working-tree capture: {error}"))?;
    json.push('\n');
    Ok(json)
}

fn require_hex(value: &str, length: usize) -> eyre::Result<()> {
    if value.len() != length
        || !value
            .bytes()
            .all(|b| b.is_ascii_digit() || (b'a'..=b'f').contains(&b))
    {
        return Err(eyre!("invalid lowercase capture hash"));
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    const GOLDEN: &str =
        include_str!("../../../../docs/architecture/fixtures/working-tree-capture-v1/capture.json");
    const ENVELOPE: &str =
        include_str!("../../../../docs/architecture/fixtures/release-review-working-tree-v2.json");

    #[test]
    fn shared_v2_envelope_retains_one_comment_authority_and_exact_query() {
        use crate::release_review_v1 as review;
        let document = review::parse(ENVELOPE).unwrap();
        assert_eq!(
            review::to_canonical_json(&document).unwrap(),
            ENVELOPE.replace("\r\n", "\n")
        );
        let capture = document.repository_bindings[0]
            .working_tree_capture
            .as_ref()
            .unwrap();
        assert_eq!(
            capture
                .entries
                .iter()
                .filter(|entry| !entry.tracked)
                .count(),
            2
        );
        assert!(document.repository_bindings[0].candidate_commit.is_none());
        assert!(
            document.repository_bindings[1]
                .working_tree_capture
                .is_none()
        );
        let pinned = review::query(
            &document,
            &format!("effective(#approved) intersect 1.19.2 {}", capture.id),
        )
        .unwrap();
        assert_eq!(
            pinned.review_unit_ids,
            review::query(&document, "effective(#approved) intersect 1.19.2 HEAD")
                .unwrap()
                .review_unit_ids
        );
        assert_eq!(
            review::query(&document, "candidate")
                .unwrap()
                .review_unit_ids,
            review::query(&document, "HEAD").unwrap().review_unit_ids
        );
        assert_eq!(
            review::semantic_state_hash(&document).unwrap(),
            "c3f1c531c093d2b37246723d38772a5841f7f95ecc77880351a55dee106333bd"
        );
    }

    #[test]
    fn v2_source_choices_and_portable_bodies_cannot_disagree() {
        use crate::release_review_v1 as review;
        let base = review::parse(ENVELOPE).unwrap();
        let rejects = |document: &review::ReleaseReviewDocumentV1| {
            assert!(review::to_canonical_json(document).is_err())
        };
        let mut value = base.clone();
        value.schema = review::SCHEMA.into();
        rejects(&value);
        let mut value = base.clone();
        value.repository_bindings[0].candidate_commit = Some("a".repeat(40));
        rejects(&value);
        let mut value = base.clone();
        value.repository_bindings[0].working_tree_capture = None;
        rejects(&value);
        let mut value = base.clone();
        value.review_session.revision_lanes[0].after.id = "different-capture".into();
        rejects(&value);
        let mut value = base.clone();
        value.review_session.revision_lanes[0].after.documents.pop();
        rejects(&value);
        let mut value = base.clone();
        value
            .corpus_documents
            .iter_mut()
            .find(|c| c.lane_id == "1.19.2" && c.snapshot_side == review::SnapshotSideV1::After)
            .unwrap()
            .source_owner = "file://live-checkout".into();
        rejects(&value);
        let mut value = base.clone();
        value.named_queries.push(review::NamedQueryV1 {
            id: "candidate".into(),
            expression: "HEAD".into(),
        });
        rejects(&value);
    }

    #[test]
    fn shared_golden_roundtrip_and_unicode_identity() {
        let capture = parse(GOLDEN).unwrap();
        assert_eq!(
            capture.id,
            "working-tree:sha256:b4744d1e1c7c092e7a40ce9b30b3909c75094649c9ecb854dba98d2dfc6ba6fc"
        );
        assert_eq!(
            to_canonical_json(&capture).unwrap(),
            GOLDEN.replace("\r\n", "\n")
        );
        assert_eq!(capture.entries[4].path, "src/\u{e000}.txt");
        assert_eq!(capture.entries[5].path, "src/\u{10000}.txt");
    }

    #[test]
    fn identity_excludes_observation_time_and_derived_details_but_not_source_facts() {
        let original = parse(GOLDEN).unwrap();
        let mut capture = original.clone();
        capture.captured_at_unix_ms += 1;
        capture.entries[0].document_revision_id = Some("different-derived-id".into());
        capture.entries[1].diagnostic = Some("Localized explanation".into());
        capture.validate().unwrap();
        assert_eq!(capture.computed_id(), original.id);
        capture.entries[0].sha256 = Some("d".repeat(64));
        assert!(capture.validate().is_err());
        assert_ne!(capture.computed_id(), original.id);
    }

    fn rejects(mutator: impl FnOnce(&mut WorkingTreeCaptureV1)) {
        let mut capture = parse(GOLDEN).unwrap();
        mutator(&mut capture);
        capture.id = capture.computed_id(); // Prove validation is more than a hash comparison.
        assert!(
            capture.validate().is_err(),
            "accepted invalid manifest: {capture:?}"
        );
    }

    #[test]
    fn source_policy_and_materialization_fail_closed() {
        rejects(|c| c.entries[0].path = "src/../outside.java".into());
        rejects(|c| c.entries[0].path = "src/.GIT/config".into());
        rejects(|c| c.entries[0].path = "src/private/secret.java".into());
        rejects(|c| c.entries[0].path = "src-other/Escape.java".into());
        rejects(|c| c.entries[0].path = "a".repeat(4097));
        rejects(|c| c.entries.swap(4, 5));
        rejects(|c| c.scope_paths = vec!["src".into(), "src/private".into()]);
        rejects(|c| c.scope_paths.clear());
        rejects(|c| c.include_untracked = false);
        rejects(|c| c.entries[0].byte_length = Some(4 * 1024 * 1024 + 1));
        rejects(|c| c.entries[0].document_revision_id = None);
        rejects(|c| c.entries[1].sha256 = Some("a".repeat(64)));
        rejects(|c| c.entries[1].tracked = false);
        rejects(|c| c.entries[2].diagnostic = None);
        rejects(|c| c.entries[3].materialization = "oversized".into());
        rejects(|c| c.consistency = "best_effort".into());
        rejects(|c| c.captured_at_unix_ms = u64::MAX);
    }

    #[test]
    fn json_fields_and_types_are_not_coerced() {
        for (from, to) in [
            ("\"schema\":", "\"surprise\": false, \"schema\":"),
            (
                "\"include_untracked\": true",
                "\"include_untracked\": \"true\"",
            ),
            ("\"byte_length\": 17", "\"byte_length\": -1"),
            ("\"byte_length\": 17", "\"byte_length\": 1.5"),
            ("\"kind\": \"regular_file\"", "\"kind\": \"unknown\""),
        ] {
            assert!(parse(&GOLDEN.replace(from, to)).is_err(), "accepted {to}");
        }
    }

    #[test]
    fn many_prefixes_use_segment_lookup_without_quadratic_scanning() {
        let prefixes = (0..100_000)
            .map(|i| format!("src/{i:06}"))
            .collect::<Vec<_>>();
        validate_prefixes(&prefixes).unwrap();
        let set = prefixes.iter().map(String::as_str).collect();
        assert!(in_prefixes(&set, "src/050000/File.java"));
        assert!(!in_prefixes(&set, "src/0500000/File.java"));
    }
}
