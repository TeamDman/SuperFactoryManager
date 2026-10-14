//! Executed zero-output declarations, including metadata-selected path moves.
use super::context::ProjectionContext;
use super::provenance::sha256;
use super::reference_associations::ReferenceAssociations;
use super::source_reference::SourceReference;
use std::collections::BTreeMap;
/// Render only executed whole-line reference declarations for this context.
/// # Errors
/// Rejects the same invalid directives/contexts as the ordinary renderer.
pub fn associations(
    source: &str,
    source_path: &str,
    raw: &[u8],
    context: &ProjectionContext,
) -> eyre::Result<ReferenceAssociations> {
    let prepared = super::PreparedSource::parse(source)?;
    let mut conditions = BTreeMap::new();
    if let super::PreparedSource::Template { scanned, .. } = &prepared {
        for name in scanned.referenced_conditions.keys() {
            let (root, key) = name
                .split_once('.')
                .ok_or_else(|| eyre::eyre!("invalid reference guard binding"))?;
            let values = if root == "features" {
                &context.features
            } else {
                &context.targets
            };
            if let Some(value) = values.get(key) {
                conditions.insert(name.clone(), *value);
            }
        }
    }
    let rendered = prepared.render_kind(context, true)?;
    let mut unique = BTreeMap::new();
    for line in rendered.lines() {
        let reference: SourceReference = facet_json::from_str(line)?;
        unique.entry(reference.identity()).or_insert(reference);
    }
    let mut references: Vec<_> = unique.into_values().collect();
    references.sort();
    Ok(ReferenceAssociations {
        schema: "sfm:related-references@1".into(),
        references,
        projection_key: context.projection_key.clone(),
        context_sha256: sha256(facet_json::to_string(context)?.as_bytes()),
        conditions,
        declarations: BTreeMap::from([(source_path.into(), sha256(raw))]),
    })
}
