//! Projection-selected key inventory with deliberately explicit coverage limits.
pub mod audit_report;
pub mod format_signature;
pub mod java_token;
pub mod key_inventory;
pub mod locale_report;
pub mod registry_observation;

use super::registry_metadata::java_field_name::JavaFieldName;
use super::registry_metadata::registry_id::RegistryId;
use super::registry_metadata::translation_key::TranslationKey;
use super::sync::ProjectedArtifact;
use audit_report::AuditReport;
use eyre::Result;
use format_signature::FormatSignature;
use java_token::JavaToken;
use java_token::tokens;
use key_inventory::KeyInventory;
use locale_report::LocaleReport;
use std::collections::BTreeMap;

fn argument_end(tokens: &[JavaToken], start: usize) -> usize {
    let mut depth = 0;
    for (index, token) in tokens.iter().enumerate().skip(start) {
        match token.text.as_str() {
            "(" => depth += 1,
            ")" | "," if depth == 0 => return index,
            ")" => depth -= 1,
            _ => (),
        }
    }
    tokens.len()
}

fn resolve_typed_description(expression: &[JavaToken], inventory: &KeyInventory) -> Option<String> {
    use liquid::model::ValueView;
    if expression.len() < 11 {
        return None;
    }
    if !matches!(expression[0].text.as_str(), "SFMItems" | "SFMBlocks") || expression[1].text != "."
    {
        return None;
    }
    if !expression[3..11]
        .iter()
        .map(|token| token.text.as_str())
        .eq([".", "get", "(", ")", ".", "getDescriptionId", "(", ")"])
    {
        return None;
    }
    // Prefer the audited Facet registry for migrated items. Remaining exact
    // field bindings come from selected literal Java registrations, with their
    // weaker provenance retained in registry_ids.
    let object = super::registry_metadata::liquid_object().ok()?;
    let mut base = None;
    if expression[0].text == "SFMItems" {
        for value in object.values() {
            let entry = value.as_object()?;
            if entry.get("java_field")?.as_scalar()?.to_kstr().as_str() == expression[2].text {
                base = Some(
                    entry
                        .get("translation_key")?
                        .as_scalar()?
                        .to_kstr()
                        .to_string(),
                );
            }
        }
    }
    if base.is_none() {
        let file = format!("/{}.java:", expression[0].text);
        base = inventory
            .registry_ids
            .values()
            .find(|entry| entry.java_field.0 == expression[2].text && entry.source.contains(&file))
            .and_then(|entry| entry.translation_key.as_ref())
            .map(|key| key.0.clone());
    }
    let mut key = base?;
    let tail = &expression[11..];
    if !tail.len().is_multiple_of(2) {
        return None;
    }
    for pair in tail.chunks_exact(2) {
        if pair[0].text != "+" || !pair[1].literal {
            return None;
        }
        key.push_str(&pair[1].text);
    }
    Some(key)
}
/// Explicit calls and declarations are observed after version/feature selection.
/// The bounded lexical scanner cannot resolve arbitrary suppliers or Java control
/// flow, so it records expressions and reference-backed family candidates.
pub fn inspect_java(
    inventory: &mut KeyInventory,
    path: &str,
    source: &str,
    english: &BTreeMap<String, String>,
) {
    let tokens = tokens(source);
    for i in 0..tokens.len().saturating_sub(2) {
        let name = tokens[i].text.as_str();
        let localization = name == "LocalizationEntry" && i > 0 && tokens[i - 1].text == "new";
        let component = name == "translatable" && i >= 2 && tokens[i - 2].text == "Component";
        let i18n = name == "get" && i >= 2 && tokens[i - 2].text == "I18n";
        if !(localization || component || i18n) || tokens[i + 1].text != "(" {
            continue;
        }
        let start = i + 2;
        let end = argument_end(&tokens, start);
        let mut expression = &tokens[start..end];
        if expression.len() >= 4
            && expression[..4]
                .iter()
                .map(|token| token.text.as_str())
                .eq(["(", ")", "-", ">"])
        {
            expression = &expression[4..];
        }
        let origin = format!("{path}:{} {name}", tokens[i].line);
        if let Some(key) = resolve_typed_description(expression, inventory) {
            inventory.add(
                &key,
                format!("{origin} typed registry description composition"),
            );
        } else if expression.len() == 1 && expression[0].literal {
            inventory.add(&expression[0].text, origin);
        } else {
            inventory.unresolved.push(format!(
                "{origin}: {}",
                expression
                    .iter()
                    .map(|token| token.text.as_str())
                    .collect::<Vec<_>>()
                    .join(" ")
            ));
            if let Some(prefix) = expression.first().filter(|token| token.literal) {
                let candidates = english
                    .keys()
                    .filter(|key| key.starts_with(&prefix.text))
                    .cloned()
                    .collect();
                inventory
                    .dynamic_candidates
                    .insert(prefix.text.clone(), candidates);
            }
        }
    }
}

/// Discover remaining literal registrations without duplicating their IDs in
/// Rust. Block entities have no implicit display key. `BlockItem` keys come from
/// the block registry, which is scanned separately. Nonliteral registrations
/// and overrides are unresolved; this is an audit of a bounded pattern.
fn inspect_registry(inventory: &mut KeyInventory, path: &str, source: &str) {
    let blocks = path.ends_with("/SFMBlocks.java");
    let items = path.ends_with("/SFMItems.java");
    let entities = path.ends_with("/SFMBlockEntities.java");
    if !(blocks || items || entities) {
        return;
    }
    let tokens = tokens(source);
    for i in 0..tokens.len().saturating_sub(3) {
        if tokens[i].text != "register" || tokens[i + 1].text != "(" {
            continue;
        }
        let id = &tokens[i + 2];
        if !id.literal {
            inventory.unresolved.push(format!(
                "{path}:{} registration is a parameter, event-bus call or computed ID",
                tokens[i].line
            ));
            continue;
        }
        let field = tokens[..i]
            .iter()
            .rposition(|token| token.text == "=")
            .and_then(|equal| equal.checked_sub(1))
            .map_or("<unresolved>", |index| tokens[index].text.as_str());
        let following = tokens
            .iter()
            .skip(i + 3)
            .take(12)
            .map(|token| token.text.as_str())
            .collect::<Vec<_>>();
        let block_item = items
            && (following.contains(&"SFMBlocks") || following.contains(&"PrintingPressBlockItem"));
        let key = (!entities).then(|| {
            format!(
                "{}.sfm.{}",
                if blocks || block_item {
                    "block"
                } else {
                    "item"
                },
                id.text
            )
        });
        inventory.registry_ids.insert(format!("{}:sfm:{}", if entities { "block_entity" } else if blocks { "block" } else { "item" }, id.text), registry_observation::RegistryObservation {
            java_field: JavaFieldName(field.into()), registry_id: RegistryId(format!("sfm:{}", id.text)),
            translation_key: key.clone().map(TranslationKey), source: format!("{path}:{}", id.line),
            coverage: "Literal registration pattern; arbitrary description overrides and Java runtime conditions unresolved. IDE-only buffer remains conditional at runtime.".into(),
        });
        if entities {
            continue;
        }
        let following = tokens
            .iter()
            .skip(i + 3)
            .take(12)
            .map(|token| token.text.as_str())
            .collect::<Vec<_>>();
        // Every block has a description; item calls referencing SFMBlocks are
        // covered by the separate block scan. PrintingPressBlockItem inherits
        // its printing_press block description as well.
        if items
            && (following.contains(&"SFMBlocks") || following.contains(&"PrintingPressBlockItem"))
        {
            continue;
        }
        inventory.add(
            &format!("{}.sfm.{}", if blocks { "block" } else { "item" }, id.text),
            format!("{path}:{} implicit registry description", id.line),
        );
    }
}

#[must_use]
pub fn compare(
    inventory: &KeyInventory,
    english: &BTreeMap<String, String>,
    locale: &BTreeMap<String, String>,
) -> LocaleReport {
    let mut report = LocaleReport::default();
    for key in inventory.required.keys() {
        if !locale.contains_key(key) {
            report.missing.push(key.clone());
        }
    }
    for (key, value) in locale {
        if !inventory.required.contains_key(key) {
            report.extra_observed.push(key.clone());
        }
        let Some(reference) = english.get(key) else {
            continue;
        };
        let source = FormatSignature::parse(reference);
        let translated = FormatSignature::parse(value);
        match (source, translated) {
            (Ok(left), Ok(right)) if left == right => (),
            (Ok(left), Ok(right)) => {
                report.placeholder_problems.insert(
                    key.clone(),
                    format!("argument compatibility differs: {left:?} vs {right:?}"),
                );
            }
            (Err(error), _) => {
                report
                    .placeholder_problems
                    .insert(key.clone(), format!("reference format unresolved: {error}"));
            }
            (_, Err(error)) => {
                report
                    .placeholder_problems
                    .insert(key.clone(), format!("locale format unresolved: {error}"));
            }
        }
    }
    report
}

/// # Errors
/// Rejects non-UTF-8 inputs, invalid selected language JSON or missing English reference.
pub fn audit(
    projection_key: &str,
    artifacts: &BTreeMap<String, ProjectedArtifact>,
) -> Result<AuditReport> {
    let mut languages = BTreeMap::<String, BTreeMap<String, String>>::new();
    for (path, artifact) in artifacts {
        if let Some(locale) = path
            .strip_prefix("src/main/resources/assets/sfm/lang/")
            .or_else(|| path.strip_prefix("src/generated/resources/assets/sfm/lang/"))
            .and_then(|path| path.strip_suffix(".json"))
        {
            languages.insert(
                locale.into(),
                facet_json::from_str(std::str::from_utf8(&artifact.output_bytes)?)?,
            );
        }
    }
    let english = languages
        .get("en_us")
        .ok_or_else(|| eyre::eyre!("selected projection lacks en_us"))?;
    let mut inventory = KeyInventory { coverage: "Audited typed item slice plus bounded literal registry and localization-call discovery. Java suppliers, arbitrary runtime composition/control flow and custom description overrides require review; dynamic candidates are not exhaustive.".into(), ..KeyInventory::default() };
    for (path, artifact) in artifacts {
        if path.starts_with("src/main/java/")
            && std::path::Path::new(path)
                .extension()
                .is_some_and(|extension| extension.eq_ignore_ascii_case("java"))
        {
            inspect_registry(
                &mut inventory,
                path,
                std::str::from_utf8(&artifact.output_bytes)?,
            );
        }
    }
    for (path, artifact) in artifacts {
        if path.starts_with("src/main/java/")
            && std::path::Path::new(path)
                .extension()
                .is_some_and(|extension| extension.eq_ignore_ascii_case("java"))
        {
            let source = std::str::from_utf8(&artifact.output_bytes)?;
            inspect_java(&mut inventory, path, source, english);
        }
    }
    for origins in inventory.required.values_mut() {
        origins.sort();
        origins.dedup();
    }
    inventory.unresolved.sort();
    inventory.unresolved.dedup();
    let locales = languages
        .iter()
        .map(|(locale, values)| (locale.clone(), compare(&inventory, english, values)))
        .collect();
    let missing_by_key = inventory
        .required
        .keys()
        .map(|key| {
            let missing = languages
                .iter()
                .filter(|(_, entries)| !entries.contains_key(key))
                .map(|(locale, _)| locale.clone())
                .collect();
            (key.clone(), missing)
        })
        .collect();
    Ok(AuditReport {
        projection_key: projection_key.into(),
        english_reference: english.clone(),
        missing_by_key,
        inventory,
        locales,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn comments_strings_and_unresolved_families_are_separate() {
        let english = BTreeMap::from([("family.one".into(), "%s".into())]);
        let mut inventory = KeyInventory::default();
        inspect_java(
            &mut inventory,
            "fixture.java",
            r#"// Component.translatable("fake")
          String s = "Component.translatable(ignored)";
          Component.translatable("real", value);
          new LocalizationEntry("family." + name, "value");
          Component.translatable(key.get());"#,
            &english,
        );
        assert_eq!(inventory.required.keys().collect::<Vec<_>>(), vec!["real"]);
        assert_eq!(inventory.unresolved.len(), 2);
        assert_eq!(inventory.dynamic_candidates["family."], vec!["family.one"]);
    }
}

#[cfg(test)]
mod release_selection_tests {
    use super::super::core_catalog::CoreCatalog;
    use super::super::core_inputs::CORE_METADATA_PATH;
    use super::super::core_inputs::CORE_ROOT;
    use super::super::core_inputs::CoreProjectInputs;
    use super::super::core_inputs::discover_core_source_files;
    use super::super::core_inputs::select_core_inputs;
    use super::*;
    use std::path::Path;

    #[test]
    fn every_baseline_keeps_original_locale_bytes_and_release_preserves_valid_portuguese() {
        let repo = Path::new(env!("CARGO_MANIFEST_DIR"))
            .ancestors()
            .nth(3)
            .unwrap();
        let loaded = CoreCatalog::load(repo, repo).unwrap();
        let core = repo.join(CORE_ROOT);
        let metadata = CoreProjectInputs::from_json(
            &std::fs::read_to_string(repo.join(CORE_METADATA_PATH)).unwrap(),
            &loaded.registered_features,
        )
        .unwrap();
        let inventory = discover_core_source_files(&core).unwrap();
        for (target, _) in super::super::projection_catalog::SUPPORTED_TARGETS {
            let baseline = loaded.context(&format!("sfm-4.34.0/mc-{target}")).unwrap();
            let release = loaded.context(&format!("sfm-4.35.0/mc-{target}")).unwrap();
            let old = select_core_inputs(&metadata, &baseline, &inventory).unwrap();
            let new = select_core_inputs(&metadata, &release, &inventory).unwrap();
            let portuguese = "src/main/resources/assets/sfm/lang/pt_br.json";
            let french = "src/main/resources/assets/sfm/lang/fr_fr.json";
            let old_path = &old.inputs[portuguese].input;
            assert!(old_path.starts_with("assets/versioned/"), "{target}");
            assert!(
                !old.inputs.contains_key(french),
                "baseline gained French: {target}"
            );
            assert!(
                new.inputs[portuguese]
                    .input
                    .starts_with("assets/features/release_version_4_35_0/localization/")
            );
            assert!(
                new.inputs.contains_key(french),
                "release lacks French: {target}"
            );
            let existing = facet_json::from_str::<BTreeMap<String, String>>(
                &std::fs::read_to_string(core.join(old_path)).unwrap(),
            )
            .unwrap();
            let merged = facet_json::from_str::<BTreeMap<String, String>>(
                &std::fs::read_to_string(core.join(&new.inputs[portuguese].input)).unwrap(),
            )
            .unwrap();
            for (key, value) in existing {
                assert_eq!(
                    merged.get(&key),
                    Some(&value),
                    "valid old value changed: {target} {key}"
                );
            }
        }
    }

    #[test]
    fn locale_report_keeps_missing_extra_and_placeholder_contracts_separate() {
        let mut inventory = KeyInventory::default();
        inventory.add("message", "fixture".into());
        inventory.add("missing", "fixture".into());
        let english = BTreeMap::from([("message".into(), "%s %d %%".into())]);
        let compatible = BTreeMap::from([
            ("message".into(), "%2$d %1$s %%".into()),
            ("old.valid".into(), "kept".into()),
        ]);
        let report = compare(&inventory, &english, &compatible);
        assert_eq!(report.missing, vec!["missing"]);
        assert_eq!(report.extra_observed, vec!["old.valid"]);
        assert!(report.placeholder_problems.is_empty());
        let incompatible = BTreeMap::from([("message".into(), "%d %s".into())]);
        assert!(
            compare(&inventory, &english, &incompatible)
                .placeholder_problems
                .contains_key("message")
        );
    }
}
