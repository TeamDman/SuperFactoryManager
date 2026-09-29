//! Compile-time source projection for Minecraft version and feature variants.
//!
//! Java bytes are opaque to Liquid except for deliberate, whole-line control
//! directives. A directive-free file is copied exactly; a conditional file is
//! represented as a small Liquid skeleton with its Java chunks as values.

pub mod candidate_lock;
pub mod context;
pub mod development_baseline;
pub mod development_fixtures;
pub mod development_gradle;
mod directive_scanner;
pub mod inputs;
pub mod manifest;
pub mod project_layout;
pub mod promotion;
pub mod provenance;
pub mod release_apply;
pub mod release_baseline;
pub mod release_jar_absence;
pub mod release_resources;
pub mod selection;
pub mod sync;

use context::ProjectionContext;
use context::to_liquid_object;
use directive_scanner::ScannedSource;
use directive_scanner::scan;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;

/// Render one primary Java source file for a validated target and preset.
///
/// # Errors
///
/// Fails for malformed directives, unknown feature/target identifiers, or a
/// Liquid rendering error. Callers should add the source path to the error
/// context before reporting it to an author.
pub fn render_java_source(source: &str, context: &ProjectionContext) -> Result<String> {
    let ScannedSource::Template(template) = scan(source)? else {
        return Ok(source.to_owned());
    };

    for condition in &template.referenced_conditions {
        let (root, name) = condition
            .split_once('.')
            .ok_or_else(|| eyre::eyre!("invalid projection condition '{condition}'"))?;
        let registered = match root {
            "features" => context.features.contains_key(name),
            "targets" => context.targets.contains_key(name),
            _ => eyre::bail!("invalid projection condition root '{root}'"),
        };
        ensure!(registered, "unknown projection condition '{condition}'");
    }

    let mut globals = to_liquid_object(context)?;
    for (index, chunk) in template.chunks.into_iter().enumerate() {
        globals.insert(
            format!("__sfm_chunk_{index}").into(),
            liquid::model::Value::scalar(chunk),
        );
    }
    let parser = liquid::ParserBuilder::with_stdlib()
        .build()
        .wrap_err("could not create the source projection parser")?;
    let rendered = parser
        .parse(&template.skeleton)
        .wrap_err("could not parse source projection directives")?
        .render(&globals)
        .wrap_err("could not render source projection directives")?;
    Ok(rendered)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::BTreeMap;

    fn context() -> ProjectionContext {
        ProjectionContext {
            minecraft_version: "1.19.2".to_owned(),
            preset: "released-4.34.0".to_owned(),
            features: BTreeMap::from([
                ("touch_display".to_owned(), true),
                ("client_manager".to_owned(), false),
            ]),
            targets: BTreeMap::from([
                ("mc_1_19_2".to_owned(), true),
                ("mc_26_1_2".to_owned(), false),
            ]),
        }
    }

    #[test]
    fn original_java_bytes_are_preserved_without_directives() {
        let source = "String[][] array = {{\"a\", \"b\"}};\r\n";
        assert_eq!(render_java_source(source, &context()).unwrap(), source);
    }

    #[test]
    fn feature_and_version_directives_do_not_parse_java_delimiters() {
        let source = "before\r\n{% if features.touch_display %}\r\nint[][] array = {{1, 2}};\r\n{% if targets.mc_1_19_2 %}\r\nlegacy();\r\n{% else %}\r\nmodern();\r\n{% endif %}\r\n{% endif %}\r\n{% if features.client_manager %}\r\nclient();\r\n{% endif %}\r\nafter\r\n";
        assert_eq!(
            render_java_source(source, &context()).unwrap(),
            "before\r\nint[][] array = {{1, 2}};\r\nlegacy();\r\nafter\r\n"
        );
    }

    #[test]
    fn missing_flag_does_not_silently_render_as_disabled() {
        let source = "{% if features.unregistered %}\nmissing();\n{% endif %}\n";
        assert!(
            render_java_source(source, &context())
                .unwrap_err()
                .to_string()
                .contains("unknown projection condition 'features.unregistered'")
        );
    }
}
