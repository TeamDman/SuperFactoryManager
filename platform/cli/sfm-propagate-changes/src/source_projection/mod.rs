//! Compile-time source projection for Minecraft version and feature variants.
//!
//! Java bytes are opaque to Liquid except for deliberate, whole-line control
//! directives. A directive-free file is copied exactly; a conditional file is
//! represented as a small Liquid skeleton with its Java chunks as values.

pub mod candidate_lock;
pub mod catalog_owned_project;
pub mod context;
pub mod core_build_seed;
pub mod core_catalog;
pub mod core_features;
#[cfg(windows)]
mod core_input_leases;
pub mod core_inputs;
pub mod core_network_layout;
pub mod core_seed;
pub mod core_version_seed;
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
pub mod sync;
pub mod variant_consolidation;

#[cfg(test)]
mod core_accessor_document_history_tests;
#[cfg(test)]
mod core_action_helpers_slice_tests;
#[cfg(test)]
mod core_action_leaf_slice_tests;
#[cfg(test)]
mod core_action_slice_tests;
#[cfg(test)]
mod core_ast_builder_slice_tests;
#[cfg(test)]
mod core_baseline_client_registration_slice_tests;
#[cfg(test)]
mod core_buffer_manager_slice_tests;
#[cfg(test)]
mod core_button_builder_slice_tests;
#[cfg(test)]
mod core_capture_widget_provider_tests;
#[cfg(test)]
mod core_client_action_registry_slice_tests;
#[cfg(test)]
mod core_client_expression_descriptor_slice_tests;
#[cfg(test)]
mod core_client_manager_provider_slice_tests;
#[cfg(test)]
mod core_client_registration_current_contract;
#[cfg(test)]
mod core_client_registration_slice_tests;
#[cfg(test)]
mod core_command_draft_analysis_provider_tests;
#[cfg(test)]
mod core_computercraft_handles_slice_tests;
#[cfg(test)]
mod core_computercraft_integration_slice_tests;
#[cfg(test)]
mod core_config_slice_tests;
#[cfg(test)]
mod core_confirmation_screen_slice_tests;
#[cfg(test)]
mod core_consent_action_choice_providers_slice_tests;
#[cfg(test)]
mod core_consent_panel_closure_slice_tests;
#[cfg(test)]
mod core_consent_screen_factory_slice_tests;
#[cfg(test)]
mod core_datagen_slice_tests;
#[cfg(test)]
mod core_development_leaf_slice_tests;
#[cfg(test)]
mod core_development_resources_slice_tests;
#[cfg(test)]
mod core_disk_item_slice_tests;
#[cfg(test)]
mod core_document_history_providers_slice_tests;
#[cfg(test)]
mod core_echo_palette_slice_tests;
#[cfg(test)]
mod core_editor_api_slice_tests;
#[cfg(test)]
mod core_editor_models_slice_tests;
#[cfg(test)]
mod core_editor_path_models_slice_tests;
#[cfg(test)]
mod core_editor_screens_slice_tests;
#[cfg(test)]
mod core_event_discovery_slice_tests;
#[cfg(test)]
mod core_fix_slice_tests;
#[cfg(test)]
mod core_form_label_slice_tests;
#[cfg(test)]
mod core_frame_ast_slice_tests;
#[cfg(test)]
mod core_frame_evaluator_manifest_slice_tests;
#[cfg(test)]
mod core_frame_helpers_slice_tests;
#[cfg(test)]
mod core_frame_runtime_slice_tests;
#[cfg(test)]
mod core_gametest_framework_slice_tests;
#[cfg(test)]
mod core_human_action_leaves_slice_tests;
#[cfg(test)]
mod core_icon_rendering_slice_tests;
#[cfg(test)]
mod core_image_resource_slice_tests;
#[cfg(test)]
mod core_input_diagnostics_slice_tests;
#[cfg(test)]
mod core_item_tooltip_slice_tests;
#[cfg(test)]
mod core_keybinding_state_models_slice_tests;
#[cfg(test)]
mod core_keyboard_carriers_slice_tests;
#[cfg(test)]
mod core_keyboard_eight_provider_tests;
#[cfg(test)]
mod core_label_slice_tests;
#[cfg(test)]
mod core_language_slice_tests;
#[cfg(test)]
mod core_localization_changelog_slice_tests;
#[cfg(test)]
mod core_manager_consent_providers_slice_tests;
#[cfg(test)]
mod core_manager_label_linter_slice_tests;
#[cfg(test)]
mod core_mixin_closure_slice_tests;
#[cfg(test)]
mod core_network_inspection_slice_tests;
#[cfg(test)]
mod core_network_slice_tests;
#[cfg(test)]
mod core_network_transport_guard_slice_tests;
#[cfg(test)]
mod core_packet_declaration_slice_tests;
#[cfg(test)]
mod core_packet_providers_slice_tests;
#[cfg(test)]
mod core_packet_statement_slice_tests;
#[cfg(all(test, windows))]
mod core_palette_history_provider_wave_tests;
#[cfg(test)]
mod core_pointer_input_modifiers_slice_tests;
#[cfg(test)]
mod core_program_action_dispatch_slice_tests;
#[cfg(test)]
mod core_program_authorization_current_contract;
#[cfg(test)]
mod core_program_grammar_slice_tests;
#[cfg(test)]
mod core_program_relations_slice_tests;
#[cfg(test)]
mod core_program_runtime_slice_tests;
#[cfg(test)]
mod core_properties_provider_slice_tests;
#[cfg(test)]
mod core_redstone_capability_slice_tests;
#[cfg(test)]
mod core_redstone_resource_slice_tests;
#[cfg(test)]
mod core_registration_slice_tests;
#[cfg(test)]
mod core_release_project_role_fixtures;
#[cfg(test)]
mod core_released_test_gaps_slice_tests;
#[cfg(test)]
mod core_released_tests_slice_tests;
#[cfg(test)]
mod core_resource_first_slice_tests;
#[cfg(test)]
mod core_runtime_closure_slice_tests;
#[cfg(test)]
mod core_screen_panel_protocol_slice_tests;
#[cfg(test)]
mod core_signer_trust_providers_slice_tests;
#[cfg(test)]
mod core_signing_delegates_slice_tests;
#[cfg(test)]
mod core_signing_runtime_ui_slice_tests;
#[cfg(test)]
mod core_signing_transport_slice_tests;
#[cfg(test)]
mod core_single_line_input_slice_tests;
#[cfg(test)]
mod core_slice_test_support;
#[cfg(test)]
mod core_small_three_leaves_slice_tests;
#[cfg(test)]
mod core_startup_registration_slice_tests;
#[cfg(test)]
mod core_terminal_carriers_slice_tests;
#[cfg(test)]
mod core_terminal_model_providers_slice_tests;
#[cfg(test)]
mod core_terminal_presentation_providers_slice_tests;
#[cfg(test)]
mod core_terminal_value_models_slice_tests;
#[cfg(test)]
mod core_text_provider_slice_tests;
#[cfg(test)]
mod core_theme_keyboard_models_slice_tests;
#[cfg(test)]
mod core_theme_preview_five_slice_tests;
#[cfg(test)]
mod core_theme_preview_models_slice_tests;
#[cfg(test)]
mod core_theme_provider_slice_tests;
#[cfg(test)]
mod core_theme_runtime_six_slice_tests;
#[cfg(test)]
mod core_three_surface_provider_tests;
#[cfg(test)]
mod core_tooltip_mode_action_slice_tests;
#[cfg(test)]
mod core_touch_display_slice_tests;
#[cfg(test)]
mod core_transfer_ast_slice_tests;
#[cfg(test)]
mod core_typed_palette_slice_tests;
#[cfg(test)]
mod core_ui_helpers_slice_tests;
#[cfg(test)]
mod core_utility_slice_tests;
#[cfg(test)]
mod core_value_foundation_slice_tests;
#[cfg(test)]
mod core_workspace_factory_registry_slice_tests;
#[cfg(test)]
mod core_workspace_host_current_contract;
#[cfg(test)]
mod core_workspace_host_providers_slice_tests;
#[cfg(test)]
mod core_workspace_host_slice_tests;
#[cfg(test)]
mod core_workspace_layout_leaves_slice_tests;
#[cfg(test)]
mod core_workspace_layout_slice_tests;
#[cfg(test)]
mod core_workspace_navigation_family_tests;
#[cfg(test)]
mod core_workspace_palette_current_contract;
#[cfg(test)]
mod core_workspace_panel_context_slice_tests;
#[cfg(test)]
mod core_workspace_panel_intent_slice_tests;
#[cfg(test)]
mod core_workspace_primitives_slice_tests;
#[cfg(test)]
mod core_workspace_registrar_current_contract;
#[cfg(test)]
mod core_workspace_toast_actions_slice_tests;
#[cfg(test)]
mod core_world_capability_slice_tests;
#[cfg(test)]
mod release_source_parity_test;

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
    let ScannedSource::Template(template) = scan(source)? else {
        return Ok(source.to_owned());
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
    validate_selectors(&template, &globals)?;
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
