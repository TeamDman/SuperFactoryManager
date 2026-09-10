//! Bounded metadata-only source inspection, not review validation or attestation.

use crate::release_review_v1::RepositoryBindingV1;
use crate::release_review_v1::{self};
use eyre::eyre;
use facet_json::RawJson;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::io::Read as _;
use std::path::Path;

/// Read source bindings without evaluating selectors or decoding embedded documents.
///
/// # Errors
/// Refuses oversized/changed files, unsupported schemas and invalid source authority.
pub fn read_bindings(path: &Path) -> eyre::Result<Vec<RepositoryBindingV1>> {
    const LIMIT: u64 = 256 * 1024 * 1024;
    let file = std::fs::File::open(path)?;
    let before = file.metadata()?;
    if !before.is_file() || before.len() > LIMIT {
        return Err(eyre!(
            "freshness review input exceeds {LIMIT} bytes or is not a file"
        ));
    }
    let mut bytes = Vec::new();
    (&file).take(LIMIT + 1).read_to_end(&mut bytes)?;
    let after = file.metadata()?;
    if bytes.len() as u64 != before.len()
        || before.len() != after.len()
        || before.modified()? != after.modified()?
    {
        return Err(eyre!("review changed during freshness read"));
    }
    parse_bindings(std::str::from_utf8(&bytes)?)
}

fn parse_bindings(json: &str) -> eyre::Result<Vec<RepositoryBindingV1>> {
    let root: BTreeMap<String, RawJson<'_>> = facet_json::from_str(json)?;
    let schema = root
        .get("schema")
        .ok_or_else(|| eyre!("missing review schema"))?;
    let schema: String = facet_json::from_str(schema.as_str())?;
    if schema != release_review_v1::SCHEMA && schema != release_review_v1::WORKING_TREE_SCHEMA {
        return Err(eyre!("unsupported source-probe review schema"));
    }
    let bindings = root
        .get("repository_bindings")
        .ok_or_else(|| eyre!("missing review source bindings"))?;
    let raw: Vec<RawJson<'_>> = facet_json::from_str(bindings.as_str())?;
    if raw.is_empty() || raw.len() > 256 {
        return Err(eyre!("freshness requires 1..256 source bindings"));
    }
    let mut result = Vec::new();
    let mut keys = BTreeSet::new();
    for raw in raw {
        let fields: BTreeMap<String, RawJson<'_>> = facet_json::from_str(raw.as_str())?;
        for name in [
            "lane_id",
            "repository_id",
            "root_hint",
            "before_label",
            "before_commit",
            "before_tree",
            "after_label",
            "candidate_commit",
            "candidate_tree",
        ] {
            if let Some(value) = fields.get(name)
                && value.as_str() != "null"
                && !value.as_str().starts_with('"')
            {
                return Err(eyre!("source binding {name} must be a string"));
            }
        }
        if let Some(capture) = fields
            .get("working_tree_capture")
            .filter(|raw| raw.as_str() != "null")
        {
            crate::release_review_capture::validate_wire_types(capture.as_str())?;
        }
        let binding: RepositoryBindingV1 = facet_json::from_str(raw.as_str())?;
        release_review_v1::validate_repository_binding(&binding)?;
        if schema == release_review_v1::SCHEMA && binding.working_tree_capture.is_some() {
            return Err(eyre!("working-tree source requires release-review/2"));
        }
        if !keys.insert((binding.lane_id.clone(), binding.repository_id.clone())) {
            return Err(eyre!("duplicate source binding"));
        }
        result.push(binding);
    }
    Ok(result)
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn metadata_probe_agrees_with_validated_sources_but_does_not_evaluate_review_content() {
        let fixture = include_str!(
            "../../../../docs/architecture/fixtures/release-review-working-tree-v2.json"
        );
        let full = release_review_v1::parse(fixture).unwrap();
        assert_eq!(parse_bindings(fixture).unwrap(), full.repository_bindings);
        let bindings = facet_json::to_string(&full.repository_bindings).unwrap();
        let metadata = format!(
            "{{\"schema\":\"sfm.release-review/2\",\"repository_bindings\":{bindings},\"review_session\":null}}"
        );
        assert_eq!(parse_bindings(&metadata).unwrap(), full.repository_bindings);
        assert!(
            release_review_v1::parse(&metadata).is_err(),
            "a source probe must never count as complete review validation"
        );
        assert!(
            parse_bindings(&metadata.replace("sfm.release-review/2", "sfm.release-review/1"))
                .is_err()
        );
    }
}
