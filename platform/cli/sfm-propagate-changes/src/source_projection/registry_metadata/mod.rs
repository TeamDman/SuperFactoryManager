//! Audited registry metadata shared by Java projection and key inventories.
pub mod item_registry;
pub mod java_field_name;
pub mod registry_entry;
pub mod registry_id;
pub mod translation_key;

use eyre::Result;
use eyre::ensure;
use item_registry::ItemRegistry;
use liquid::model::ValueView;

/// Reflect Facet's type/field attributes using the existing direct bridge.
/// # Errors
/// Returns a reflection error when a registry field has an unsupported Liquid shape.
pub fn liquid_object() -> Result<liquid::Object> {
    super::context::reflect_liquid_object(&ItemRegistry::default())
}

/// Opt-in Java metadata tokens avoid interpreting Java's valid `{{` braces.
/// Only an exact reflected scalar path is accepted; no Liquid expressions,
/// escaping rules or Java parser are needed. Ordinary Java remains opaque.
/// # Errors
/// Rejects incomplete tokens and paths absent from the reflected registry.
pub fn substitute(source: &str) -> Result<String> {
    const OPEN: &str = "@@sfm.registry.";
    if !source.contains(OPEN) {
        return Ok(source.to_owned());
    }
    let object = liquid_object()?;
    let mut rest = source;
    let mut output = String::new();
    while let Some(start) = rest.find(OPEN) {
        output.push_str(&rest[..start]);
        rest = &rest[start + OPEN.len()..];
        let end = rest
            .find("@@")
            .ok_or_else(|| eyre::eyre!("unclosed registry token"))?;
        let path = &rest[..end];
        let (entry, field) = path
            .split_once('.')
            .ok_or_else(|| eyre::eyre!("invalid registry path `{path}`"))?;
        ensure!(!field.contains('.'), "invalid registry path `{path}`");
        let entry = object
            .get(entry)
            .and_then(ValueView::as_object)
            .ok_or_else(|| eyre::eyre!("unknown registry entry `{path}`"))?;
        let value = entry
            .get(field)
            .and_then(ValueView::as_scalar)
            .ok_or_else(|| eyre::eyre!("unknown registry field `{path}`"))?;
        output.push_str(&value.to_kstr());
        rest = &rest[end + 2..];
    }
    output.push_str(rest);
    Ok(output)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn facet_attributes_keep_names_ids_and_keys_distinct() {
        let object = liquid_object().unwrap();
        let template = liquid::ParserBuilder::with_stdlib().build().unwrap()
            .parse("{{ label_gun.java_field }}|{{ label_gun.registry_id }}|{{ label_gun.translation_key }}").unwrap();
        assert_eq!(
            template.render(&object).unwrap(),
            "LABEL_GUN|labelgun|item.sfm.labelgun"
        );
        assert_eq!(
            substitute("{{ Java }} @@sfm.registry.experience_shard.registry_id@@").unwrap(),
            "{{ Java }} xp_shard"
        );
        assert!(substitute("@@sfm.registry.typo.registry_id@@").is_err());
        assert!(substitute("@@sfm.registry.label_gun.typo@@").is_err());
        assert!(substitute("@@sfm.registry.label_gun.registry_id").is_err());
    }
}
#[cfg(test)]
mod equivalence {
    use super::super::core_catalog::CoreCatalog;
    use super::super::render_java_source;
    use std::path::Path;

    #[test]
    fn audited_item_slice_is_byte_equivalent_for_every_catalog_projection() {
        let repo = Path::new(env!("CARGO_MANIFEST_DIR"))
            .ancestors()
            .nth(3)
            .unwrap();
        let loaded = CoreCatalog::load(repo, repo).unwrap();
        let source = std::fs::read_to_string(repo.join("platform/minecraft/core-liquid-template/src/main/java/ca/teamdman/sfm/common/registry/registration/SFMItems.java")).unwrap();
        let before = include_str!("sfm_items_before.java.txt");
        assert!(source.contains("@@sfm.registry."));
        for key in loaded.catalog.0.keys() {
            let context = loaded.context(key).unwrap();
            assert_eq!(
                render_java_source(before, &context).unwrap().as_bytes(),
                render_java_source(&source, &context).unwrap().as_bytes(),
                "projection {key}"
            );
        }
    }
}
