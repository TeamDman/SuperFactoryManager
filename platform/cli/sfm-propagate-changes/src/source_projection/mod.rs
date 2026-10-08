//! Compile-time source projection for Minecraft version and feature variants.
//!
//! Java bytes are opaque to Liquid except for deliberate, whole-line control
//! directives. A directive-free file is copied exactly; a conditional file is
//! represented as a small Liquid skeleton with its Java chunks as values.

pub mod candidate_lock;
pub mod catalog_owned_project;
pub mod context;
pub mod core_catalog;
pub mod core_features;
#[cfg(windows)]
mod core_input_leases;
pub mod core_inputs;
pub mod core_network_layout;
pub mod development_baseline;
pub mod development_fixtures;
pub mod development_gradle;
pub(crate) mod development_nfrt_dependencies;
mod directive_scanner;
pub mod frozen_authoring;
pub mod frozen_recipe_project;
pub mod frozen_release;
pub mod inputs;
pub mod manifest;
pub(crate) mod manifestation;
pub mod named_root;
pub mod native_project_target;
pub mod nfrt_child_identity_supplement;
#[cfg(windows)]
pub(crate) mod nfrt_held_producer;
#[cfg(windows)]
pub(crate) mod nfrt_input_store;
#[cfg(windows)]
pub(crate) mod nfrt_producer_output_receipt;
pub(crate) mod nfrt_project_owner;
#[cfg(windows)]
pub(crate) mod nfrt_tool_sdk;
pub mod oracle;
pub mod oracle_checkout;
pub mod oracle_compare;
pub mod oracle_draft;
pub mod oracle_git;
pub mod oracle_index;
pub mod oracle_seed;
pub mod prepared_dependency_inputs;
pub mod project_layout;
pub mod projection_catalog;
pub mod promotion;
pub mod provenance;
pub mod release_apply;
pub mod release_baseline;
pub mod release_jar_absence;
pub mod release_resources;
pub mod release_version;
pub mod released_native_inputs;
pub mod selection;
pub(crate) mod simplify;
mod simplify_flexible_constructor;
pub mod sync;
pub mod variant_consolidation;

#[cfg(test)]
pub(crate) mod legacy_test_fixture;

#[cfg(test)]
mod core_release_project_role_fixtures;
#[cfg(test)]
mod core_slice_test_support;

use context::ProjectionContext;
use context::to_liquid_object;
use directive_scanner::ScannedSource;
use directive_scanner::ScannedTemplate;
use directive_scanner::scan;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use liquid::model::ValueView;

/// Render one primary Java source file for a validated target and preset.
///
/// # Errors
///
/// Fails for malformed directives, unknown feature/target identifiers, absent
/// or non-string case selectors, or a Liquid rendering error. Callers should add the source path to the error
/// context before reporting it to an author.
pub fn render_java_source(source: &str, context: &ProjectionContext) -> Result<String> {
    PreparedSource::parse(source)?.render(context)
}

enum PreparedSource {
    Identity(String),
    Template {
        scanned: ScannedTemplate,
        compiled: liquid::Template,
    },
}

impl PreparedSource {
    fn parse(source: &str) -> Result<Self> {
        let ScannedSource::Template(scanned) = scan(source)? else {
            return Ok(Self::Identity(source.to_owned()));
        };
        let compiled = liquid::ParserBuilder::with_stdlib()
            .build()
            .wrap_err("could not create the source projection parser")?
            .parse(&scanned.skeleton)
            .wrap_err("could not parse source projection directives")?;
        Ok(Self::Template { scanned, compiled })
    }

    fn render(&self, context: &ProjectionContext) -> Result<String> {
        let Self::Template {
            scanned: template,
            compiled,
        } = self
        else {
            let Self::Identity(source) = self else {
                unreachable!()
            };
            return Ok(source.clone());
        };

        for (condition, line) in &template.referenced_conditions {
            let (root, name) = condition
                .split_once('.')
                .ok_or_else(|| eyre::eyre!("invalid projection condition '{condition}'"))?;
            let registered = match root {
                "features" => context.features.contains_key(name),
                "targets" => context.targets.contains_key(name),
                _ => eyre::bail!("invalid projection condition root '{root}'"),
            };
            ensure!(
                registered,
                "line {line}: unknown projection condition '{condition}'"
            );
        }

        let mut globals = to_liquid_object(context)?;
        validate_selectors(template, &globals)?;
        for (index, chunk) in template.chunks.iter().enumerate() {
            globals.insert(
                format!("__sfm_chunk_{index}").into(),
                liquid::model::Value::scalar(chunk.clone()),
            );
        }
        let rendered = compiled
            .render(&globals)
            .wrap_err("could not render source projection directives")?;
        Ok(rendered)
    }
}

/// Invocation-local parsed templates; context validation still runs on every render.
#[derive(Default)]
pub(super) struct SourceRenderCache(std::collections::BTreeMap<String, PreparedSource>);

impl SourceRenderCache {
    pub(super) fn render(&mut self, source: &str, context: &ProjectionContext) -> Result<String> {
        if !self.0.contains_key(source) {
            self.0
                .insert(source.to_owned(), PreparedSource::parse(source)?);
        }
        self.0.get(source).expect("just inserted").render(context)
    }
}

fn validate_selectors(template: &ScannedTemplate, globals: &liquid::Object) -> Result<()> {
    for (selector, line) in &template.referenced_selectors {
        ensure!(
            globals
                .get(selector.as_str())
                .is_some_and(|value| value.type_name() == "string"),
            "line {line}: projection case selector '{selector}' is absent or not a string"
        );
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::BTreeMap;

    #[test]
    fn parsed_cache_reuses_templates_but_not_context_values_or_validation() {
        let mut cache = SourceRenderCache::default();
        let source = "{% if features.touch_display %}\nyes\n{% else %}\nno\n{% endif %}\n";
        let mut context = context();
        context.features.insert("touch_display".into(), true);
        assert_eq!(cache.render(source, &context).unwrap(), "yes\n");
        context.features.insert("touch_display".into(), false);
        assert_eq!(cache.render(source, &context).unwrap(), "no\n");
        assert_eq!(cache.0.len(), 1);
        context.features.remove("touch_display");
        assert!(cache.render(source, &context).is_err());
    }

    #[test]
    fn registered_boolean_alternatives_render_all_masks_and_validate_inactive_names() {
        let source = "{% if features.touch_display or features.client_manager %}\nyes();\n{% else %}\nno();\n{% endif %}\n";
        for mask in 0_u8..4 {
            let mut context = context();
            context
                .features
                .insert("touch_display".to_owned(), mask & 1 != 0);
            context
                .features
                .insert("client_manager".to_owned(), mask & 2 != 0);
            assert_eq!(
                render_java_source(source, &context).unwrap(),
                if mask == 0 { "no();\n" } else { "yes();\n" }
            );
        }
        let unknown =
            "{% if features.touch_display or features.unregistered %}\nyes();\n{% endif %}\n";
        let error = render_java_source(unknown, &context())
            .unwrap_err()
            .to_string();
        assert!(
            error.contains("line 1: unknown projection condition 'features.unregistered'"),
            "{error}"
        );
        let inactive = "{% if features.client_manager %}\n{% if features.touch_display or features.unregistered %}\nyes();\n{% endif %}\n{% endif %}\n";
        let error = render_java_source(inactive, &context())
            .unwrap_err()
            .to_string();
        assert!(
            error.contains("line 2: unknown projection condition 'features.unregistered'"),
            "{error}"
        );
        let elsif = "{% if features.client_manager %}\nfirst();\n{% elsif features.client_manager or features.touch_display %}\nsecond();\n{% endif %}\n";
        assert_eq!(
            render_java_source(elsif, &context()).unwrap(),
            "second();\n"
        );
    }

    #[test]
    fn registered_conjunctions_render_all_masks_and_validate_inactive_names() {
        let source = "{% if features.touch_display and features.client_manager %}\nyes();\n{% else %}\nno();\n{% endif %}\n";
        for mask in 0_u8..4 {
            let mut context = context();
            context
                .features
                .insert("touch_display".to_owned(), mask & 1 != 0);
            context
                .features
                .insert("client_manager".to_owned(), mask & 2 != 0);
            assert_eq!(
                render_java_source(source, &context).unwrap(),
                if mask == 3 { "yes();\n" } else { "no();\n" }
            );
        }
        let unknown =
            "{% if features.client_manager and features.unregistered %}\nyes();\n{% endif %}\n";
        let error = render_java_source(unknown, &context())
            .unwrap_err()
            .to_string();
        assert!(
            error.contains("line 1: unknown projection condition 'features.unregistered'"),
            "{error}"
        );
        let inactive = "{% if features.client_manager %}\n{% if features.touch_display and features.unregistered %}\nyes();\n{% endif %}\n{% endif %}\n";
        let error = render_java_source(inactive, &context())
            .unwrap_err()
            .to_string();
        assert!(
            error.contains("line 2: unknown projection condition 'features.unregistered'"),
            "{error}"
        );
    }

    fn context() -> ProjectionContext {
        ProjectionContext {
            minecraft_version: "1.19.2".to_owned(),
            preset: "released-4.34.0".to_owned(),
            environment: "release".to_owned(),
            projection_key: "sfm-4.34.0/mc-1.19.2".to_owned(),
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

    #[test]
    fn grouped_version_imports_render_for_each_declared_alternative() {
        let source = "import common.Type;\r\n{% case minecraft_version %}\r\n{% when \"1.19.2\", \"1.19.4\", \"1.20\", \"1.20.1\" %}\r\nimport net.minecraftforge.items.IItemHandler;\r\n{% else %}\r\nimport net.neoforged.neoforge.items.IItemHandler;\r\n{% endcase %}\r\npublic class Example {}";
        for version in ["1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "26.1.2"] {
            let mut context = context();
            context.minecraft_version = version.to_owned();
            let namespace = if matches!(version, "1.19.2" | "1.19.4" | "1.20" | "1.20.1") {
                "net.minecraftforge"
            } else {
                "net.neoforged.neoforge"
            };
            let expected = format!(
                "import common.Type;\r\nimport {namespace}.items.IItemHandler;\r\npublic class Example {{}}"
            );
            let rendered = render_java_source(source, &context).unwrap();
            assert_eq!(rendered, expected, "wrong version branch for {version}");
            assert_eq!(render_java_source(source, &context).unwrap(), rendered);
        }
    }

    #[test]
    fn case_and_feature_blocks_nest_without_interpreting_java_chunks() {
        let source = "{% if features.touch_display %}\n{% case minecraft_version %}\n{% when '1.19.2' or '1.19.4' %}\n{% if features.client_manager %}\nclient();\n{% else %}\nint[][] values = {{1, 2}};\nString literal = \"{% endcase %}\";\n\\{% when 'literal' %}\n{% endif %}\n{% case preset %}\n{% when 'released-4.34.0' %}\nreleased();\n{% else %}\ndev();\n{% endcase %}\n{% else %}\nmodern();\n{% endcase %}\n{% endif %}\n";
        assert_eq!(
            render_java_source(source, &context()).unwrap(),
            "int[][] values = {{1, 2}};\nString literal = \"{% endcase %}\";\n{% when 'literal' %}\nreleased();\n"
        );
    }

    #[test]
    fn unknown_condition_in_unselected_case_still_fails_with_source_line() {
        let source = "{% case minecraft_version %}\n{% when '26.1.2' %}\n{% if features.typo %}\nnever();\n{% endif %}\n{% endcase %}\n";
        assert!(
            render_java_source(source, &context())
                .unwrap_err()
                .to_string()
                .contains("line 3: unknown projection condition 'features.typo'")
        );
    }

    #[test]
    fn case_without_matching_alternative_emits_no_branch() {
        let source = "before\n{% case minecraft_version %}\n{% when '26.1.2' %}\nnewer();\n{% endcase %}\nafter\n";
        assert_eq!(
            render_java_source(source, &context()).unwrap(),
            "before\nafter\n"
        );
    }

    #[test]
    fn environment_and_projection_identity_are_explicit_string_selectors() {
        let source = "{% case environment %}\n{% when 'release' %}\n{% case projection_key %}\n{% when 'sfm-4.34.0/mc-1.19.2' %}\nreleased();\n{% else %}\nother_release();\n{% endcase %}\n{% when 'dev' %}\ndev();\n{% endcase %}\n";
        assert_eq!(
            render_java_source(source, &context()).unwrap(),
            "released();\n"
        );
        let mut development = context();
        development.environment = "dev".to_owned();
        development.projection_key = "sfm-dev/mc-1.19.2".to_owned();
        assert_eq!(
            render_java_source(source, &development).unwrap(),
            "dev();\n"
        );
    }

    #[test]
    fn case_selectors_require_present_string_metadata_even_in_inactive_branches() {
        let source = "{% if features.client_manager %}\n{% case environment %}\n{% when 'release' %}\nrelease();\n{% endcase %}\n{% endif %}\n";
        let ScannedSource::Template(template) = scan(source).unwrap() else {
            panic!("fixture must contain directives");
        };
        for value in [None, Some(liquid::model::Value::scalar(true))] {
            let mut globals = liquid::Object::new();
            if let Some(value) = value {
                globals.insert("environment".into(), value);
            }
            assert!(
                validate_selectors(&template, &globals)
                    .unwrap_err()
                    .to_string()
                    .contains(
                        "line 2: projection case selector 'environment' is absent or not a string"
                    )
            );
        }
        let mut globals = liquid::Object::new();
        globals.insert(
            "environment".into(),
            liquid::model::Value::scalar("release"),
        );
        validate_selectors(&template, &globals).unwrap();
    }
}
