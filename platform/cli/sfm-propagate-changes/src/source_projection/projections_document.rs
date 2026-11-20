use super::projection_definitions::ProjectionDefinitions;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeSet;

/// V1 of `projections.json`, a flat map keyed by projection name.
#[derive(Facet)]
#[facet(transparent)]
pub(crate) struct ProjectionsDocument(pub(super) ProjectionDefinitions);

impl ProjectionsDocument {
    pub(super) fn from_json(input: &str) -> Result<Self> {
        reject_duplicate_definition_keys(input)?;
        facet_json::from_str(input).wrap_err("could not parse projections catalog")
    }
}

/// Facet's map parser can replace a repeated map key. Guard the public root map
/// before that conversion. Facet still owns string decoding and full JSON/type
/// validation; this lexical pass only identifies strings followed by a colon
/// at root-object depth, respecting escaped quotes and nested containers.
pub(super) fn reject_duplicate_definition_keys(input: &str) -> eyre::Result<()> {
    let bytes = input.as_bytes();
    let mut position = 0;
    let mut depth = 0_usize;
    let mut keys = BTreeSet::new();
    while position < bytes.len() {
        match bytes[position] {
            b'{' | b'[' => {
                depth = depth.saturating_add(1);
                position += 1;
            }
            b'}' | b']' => {
                depth = depth.saturating_sub(1);
                position += 1;
            }
            b'"' => {
                let start = position;
                position += 1;
                let mut closed = false;
                while position < bytes.len() {
                    match bytes[position] {
                        b'\\' => position = (position + 2).min(bytes.len()),
                        b'"' => {
                            position += 1;
                            closed = true;
                            break;
                        }
                        _ => position += 1,
                    }
                }
                let mut next = position;
                while next < bytes.len() && bytes[next].is_ascii_whitespace() {
                    next += 1;
                }
                if closed && depth == 1 && bytes.get(next) == Some(&b':') {
                    let key: String = facet_json::from_str(&input[start..position])
                        .wrap_err("invalid projections catalog key")?;
                    ensure!(
                        keys.insert(key.clone()),
                        "duplicate projection key `{key}` in JSON"
                    );
                }
            }
            _ => position += 1,
        }
    }
    Ok(())
}
