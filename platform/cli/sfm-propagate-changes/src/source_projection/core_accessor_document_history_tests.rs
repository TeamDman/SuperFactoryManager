//! History-only accessor ownership and matching client mixin registration.
//!
//! These source-only tests invoke the production feature validator, selector,
//! directive scanner/renderer and typed JSON parser. They never open a screen,
//! start Git/Java, collect a project, or attest runtime mixin application.

#![cfg(test)]

use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
use super::core_catalog::CoreCatalog;
use super::core_inputs::CoreProjectInputs;
use super::core_inputs::InputPredicate;
use super::core_inputs::InputVariant;
use super::core_inputs::discover_core_source_files;
use super::core_inputs::select_core_inputs;
use super::core_slice_test_support::CoreTestFixture;
use super::core_slice_test_support::historical_feature_registry;
use super::core_slice_test_support::read_bounded;
use super::provenance::sha256;
use super::render_java_source;
use eyre::Result;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeSet;

const ACCESSOR: &str = "src/main/java/ca/teamdman/sfm/mixins/EditBoxAccessor.java";
const RESOURCE: &str = "src/main/resources/sfm.mixins.json";
const HISTORICAL_LEDGER: &str = "docs/tasks/sfm-core-mixin-closure-slice.json";
const OLD_GUARD: &str = "{% if features.single_line_input or features.typed_command_palette %}";
const NEW_GUARD: &str = "{% if features.single_line_input or features.typed_command_palette or features.document_history %}";
const OWNERS: [&str; 7] = [
    "single_line_input",
    "typed_command_palette",
    "document_history",
    "game_puppet_runtime",
    "keyboard_profiles",
    "client_overlay_input",
    "pointer_modifier_ingress",
];
const TARGETS: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const CLIENT_CLASSES: [&str; 5] = [
    "EditBoxAccessor",
    "MouseHandlerMixin",
    "MouseHandlerInvoker",
    "KeyboardHandlerMixin",
    "KeyboardHandlerInvoker",
];

#[derive(Debug, Facet)]
#[facet(deny_unknown_fields)]
struct Registration {
    required: bool,
    #[facet(rename = "minVersion")]
    min_version: String,
    package: String,
    refmap: String,
    #[facet(rename = "compatibilityLevel")]
    compatibility_level: String,
    mixins: Vec<String>,
    client: Vec<String>,
    server: Vec<String>,
    injectors: Injectors,
}

#[derive(Debug, Facet)]
#[facet(deny_unknown_fields)]
struct Injectors {
    #[facet(rename = "defaultRequire")]
    default_require: usize,
}

struct Fixture {
    core: CoreTestFixture,
    inventory: BTreeSet<String>,
    accessor: String,
    current_resource: String,
    historical_resource: String,
    historical_metadata: CoreProjectInputs,
}

impl Fixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let inventory = discover_core_source_files(&core.core)?;
        let accessor_bytes = core.read_source(ACCESSOR)?;
        ensure!(
            accessor_bytes.len() == 376
                && sha256(&accessor_bytes)
                    == "sha256:5a40a2cb8f8ca86a301a0d01a1cfb7acbfdff8b4f23e01aa50ee00305b93d3e0",
            "history seam changed the authentic accessor"
        );
        let resource_bytes = read_bounded(&checked_file(&core.core, RESOURCE)?, 4096)?;
        ensure!(
            resource_bytes.len() == 1625
                && sha256(&resource_bytes)
                    == "sha256:56d025c4d4bc0a411bc20cdb136f080f1c372aeed5064e0f25c36cd9dc07fc83",
            "only the reviewed registration guard may change"
        );
        let current_resource = String::from_utf8(resource_bytes)?;
        ensure!(
            current_resource.matches(NEW_GUARD).count() == 1,
            "new registration anchor must remain unique"
        );
        let historical_resource = current_resource.replacen(NEW_GUARD, OLD_GUARD, 1);
        ensure!(
            historical_resource.len() == 1596
                && sha256(historical_resource.as_bytes())
                    == "sha256:ae39118f9b82fa23034556d1764347577858c15fcc8badb18696d64744597c86",
            "registration inverse must recover the entire historical template"
        );
        ensure!(
            core.metadata.source_rules.get(ACCESSOR) == Some(&vec![accessor_rule(true)]),
            "accessor membership must match the reviewed current rule"
        );
        let mut historical_metadata = core.metadata.clone();
        historical_metadata
            .source_rules
            .insert(ACCESSOR.to_owned(), vec![accessor_rule(false)]);
        historical_metadata.validate(&core.features.registered_names())?;
        Ok(Self {
            core,
            inventory,
            accessor: String::from_utf8(accessor_bytes)?,
            current_resource,
            historical_resource,
            historical_metadata,
        })
    }

    fn context(&self, target: &str, names: &BTreeSet<String>) -> Result<ProjectionContext> {
        self.core.context(
            target,
            &names.iter().map(String::as_str).collect::<Vec<_>>(),
        )
    }

    /// Close real prerequisites, or keep the seven owner bits exact while
    /// adding only prerequisites outside that seven-bit audit boundary.
    fn names(&self, mask: usize, preserve_owner_bits: bool) -> Result<BTreeSet<String>> {
        let mut names = OWNERS
            .iter()
            .enumerate()
            .filter(|(bit, _)| mask & (1_usize << *bit) != 0)
            .map(|(_, owner)| (*owner).to_owned())
            .collect::<BTreeSet<_>>();
        let mut pending = names.iter().cloned().collect::<Vec<_>>();
        while let Some(name) = pending.pop() {
            let definition = self
                .core
                .features
                .0
                .get(&name)
                .ok_or_else(|| eyre::eyre!("unregistered audit feature '{name}'"))?;
            for required in &definition.requires {
                if preserve_owner_bits && OWNERS.contains(&required.as_str()) {
                    continue;
                }
                if names.insert(required.clone()) {
                    pending.push(required.clone());
                }
            }
        }
        Ok(names)
    }

    fn registration(&self, source: &str, context: &ProjectionContext) -> Result<Registration> {
        let rendered = render_java_source(source, context)?;
        let registration: Registration = facet_json::from_str(&rendered)?;
        ensure!(
            !registration.required
                && registration.min_version == "0.8.3"
                && registration.package == "ca.teamdman.sfm.mixins"
                && registration.refmap == "sfm.refmap.json"
                && registration.server.is_empty()
                && registration.injectors.default_require == 1,
            "registration's non-owner fields changed"
        );
        let modern = context.minecraft_version == "26.1.2";
        ensure!(
            registration.compatibility_level == if modern { "JAVA_25" } else { "JAVA_17" },
            "registration Java dialect changed"
        );
        ensure!(
            registration.mixins
                == if modern {
                    vec![
                        "HopperIgnoreTunnelledManagerMixin",
                        "StructureTemplateManagerMixin",
                    ]
                } else {
                    vec!["StructureTemplateManagerMixin"]
                },
            "common/server mixin registration changed"
        );
        Ok(registration)
    }

    fn matching_client_classes(
        &self,
        metadata: &CoreProjectInputs,
        source: &str,
        context: &ProjectionContext,
    ) -> Result<Vec<String>> {
        let selection = select_core_inputs(metadata, context, &self.inventory)?;
        let client = self.registration(source, context)?.client;
        ensure!(
            client.iter().collect::<BTreeSet<_>>().len() == client.len()
                && client
                    .iter()
                    .all(|name| CLIENT_CLASSES.contains(&name.as_str())),
            "duplicate or unknown client mixin"
        );
        for name in CLIENT_CLASSES {
            let path = format!("src/main/java/ca/teamdman/sfm/mixins/{name}.java");
            ensure!(
                selection.inputs.contains_key(&path) == client.iter().any(|entry| entry == name),
                "selector and registration disagree for '{name}'"
            );
        }
        Ok(client)
    }

    fn assert_current_and_historical(&self, context: &ProjectionContext) -> Result<bool> {
        let old = self.matching_client_classes(
            &self.historical_metadata,
            &self.historical_resource,
            context,
        )?;
        let current =
            self.matching_client_classes(&self.core.metadata, &self.current_resource, context)?;
        let d2 = matches!(context.minecraft_version.as_str(), "1.19.2" | "1.19.4");
        let gain = d2
            && context.features["document_history"]
            && !context.features["single_line_input"]
            && !context.features["typed_command_palette"];
        let mut expected = old.clone();
        if gain {
            expected.insert(0, "EditBoxAccessor".to_owned());
        }
        ensure!(
            current == expected,
            "history must add only the accessor in its original order"
        );
        if !gain {
            ensure!(
                render_java_source(&self.current_resource, context)?
                    == render_java_source(&self.historical_resource, context)?,
                "old owner masks must retain exact resource output"
            );
        }
        if current.iter().any(|entry| entry == "EditBoxAccessor") {
            ensure!(
                render_java_source(&self.accessor, context)?.as_bytes() == self.accessor.as_bytes(),
                "selected accessor Java changed"
            );
        }
        Ok(gain)
    }
}

fn accessor_rule(current: bool) -> InputVariant {
    let mut any_features = vec![
        "single_line_input".to_owned(),
        "typed_command_palette".to_owned(),
    ];
    if current {
        any_features.push("document_history".to_owned());
    }
    InputVariant {
        input: ACCESSOR.to_owned(),
        when: InputPredicate {
            targets: vec!["1.19.2".to_owned(), "1.19.4".to_owned()],
            all_features: vec![],
            any_features,
            none_features: vec![],
        },
        // Current Java declarations explicitly opt into Liquid. Keep the
        // historical receipt's flag while preserving both exact source bodies.
        template: current,
    }
}

#[test]
fn accessor_history_keeps_original_ledger_and_existing_owner_contracts() -> Result<()> {
    let fixture = Fixture::load()?;
    let ledger = read_bounded(
        &checked_file(&fixture.core.repository, HISTORICAL_LEDGER)?,
        128 * 1024,
    )?;
    ensure!(
        ledger.len() == 89202
            && sha256(&ledger)
                == "sha256:d1e564ee13542a582f1c7cb37c2e4352bec568b430cfcc3e2edbafb49b861679",
        "the six-source historical witness ledger is immutable"
    );
    let expected = [
        ("single_line_input", vec![]),
        ("typed_command_palette", vec!["command_palette"]),
        ("document_history", vec![]),
        ("game_puppet_runtime", vec!["client_properties"]),
        ("keyboard_profiles", vec!["client_actions"]),
        ("client_overlay_input", vec![]),
        ("pointer_modifier_ingress", vec![]),
        (
            "command_palette",
            vec!["client_actions", "client_theme", "keyboard_profiles"],
        ),
    ];
    let (_, historical_features) = historical_feature_registry()?;
    assert_eq!(historical_features.0.len(), 180);
    for (name, required) in expected {
        let definition = &fixture.core.features.0[name];
        assert_eq!(definition.requires, required, "{name}");
        let d2_only = matches!(
            name,
            "single_line_input"
                | "typed_command_palette"
                | "document_history"
                | "client_overlay_input"
                | "pointer_modifier_ingress"
        );
        assert_eq!(
            definition.supported_targets,
            TARGETS[..if d2_only { 2 } else { 10 }],
            "{name}"
        );
    }
    Ok(())
}

#[test]
fn accessor_history_only_d2_selects_authentic_provider_and_one_registration() -> Result<()> {
    let fixture = Fixture::load()?;
    for target in &TARGETS[..2] {
        let context = fixture.core.context(target, &["document_history"])?;
        assert!(!context.features["typed_command_palette"]);
        assert!(!context.features["single_line_input"]);
        assert!(!context.features["keyboard_profiles"]);
        let client = fixture.matching_client_classes(
            &fixture.core.metadata,
            &fixture.current_resource,
            &context,
        )?;
        assert_eq!(client, vec!["EditBoxAccessor"]);
        assert!(fixture.assert_current_and_historical(&context)?);
        let selected = select_core_inputs(&fixture.core.metadata, &context, &fixture.inventory)?;
        assert!(!selected.inputs.contains_key(
            "src/main/java/ca/teamdman/sfm/client/screen/SFMCommandPaletteScreen.java"
        ));
        assert!(
            !selected.inputs.contains_key(
                "src/main/java/ca/teamdman/sfm/client/action/PanelActionSupport.java"
            )
        );
    }
    Ok(())
}

#[test]
fn accessor_history_all_128_root_masks_use_real_prerequisites_selector_and_renderer() -> Result<()>
{
    let fixture = Fixture::load()?;
    let mut total = 0;
    let mut changed = 0;
    for target in &TARGETS[..2] {
        let mut effective = BTreeSet::new();
        for mask in 0..128 {
            let names = fixture.names(mask, false)?;
            let context = fixture.context(target, &names)?;
            effective.insert(
                OWNERS
                    .iter()
                    .map(|name| context.features[*name])
                    .collect::<Vec<_>>(),
            );
            changed += usize::from(fixture.assert_current_and_historical(&context)?);
            total += 1;
        }
        // Typed requires command_palette, which itself requires keyboard_profiles.
        // 32 root masks close to already-existing keyboard-enabled masks.
        assert_eq!(effective.len(), 96);
    }
    assert_eq!((total, changed), (256, 32));
    Ok(())
}

#[test]
fn accessor_history_exact_128_owner_masks_retain_real_missing_prerequisite_refusals() -> Result<()>
{
    let fixture = Fixture::load()?;
    let mut accepted = 0;
    let mut refused = 0;
    for target in &TARGETS[..2] {
        for mask in 0..128 {
            let names = fixture.names(mask, true)?;
            let result = fixture.context(target, &names);
            let typed_without_keys = mask & (1 << 1) != 0 && mask & (1 << 4) == 0;
            if typed_without_keys {
                let error = result.expect_err("exact typed-without-keyboard mask must be refused");
                assert!(error.to_string().contains("requires enabled feature"));
                refused += 1;
            } else {
                let context = result?;
                for (bit, name) in OWNERS.iter().enumerate() {
                    assert_eq!(
                        context.features[*name],
                        mask & (1 << bit) != 0,
                        "{target}/{mask}/{name}"
                    );
                }
                fixture.assert_current_and_historical(&context)?;
                accepted += 1;
            }
        }
    }
    assert_eq!((accepted, refused), (192, 64));
    Ok(())
}

#[test]
fn accessor_history_twenty_feature_off_controls_keep_exact_old_outputs_and_omissions() -> Result<()>
{
    let fixture = Fixture::load()?;
    let catalog = CoreCatalog::load(&fixture.core.repository, &fixture.core.repository)?;
    assert_eq!(catalog.catalog.0.len(), 20);
    let mut count = 0;
    for key in catalog.catalog.0.keys() {
        let context = fixture.core.historical_feature_off_catalog_context(key)?;
        assert!(!context.features["document_history"], "{key}");
        assert!(!context.features["single_line_input"], "{key}");
        assert!(!context.features["typed_command_palette"], "{key}");
        assert!(!fixture.assert_current_and_historical(&context)?);
        let selected = select_core_inputs(&fixture.core.metadata, &context, &fixture.inventory)?;
        assert!(!selected.inputs.contains_key(ACCESSOR), "{key}");
        assert!(selected.omitted_paths.contains(ACCESSOR), "{key}");
        count += 1;
    }
    assert_eq!(count, 20);
    Ok(())
}

#[test]
fn accessor_history_current_catalog_follows_configured_owners_and_registration() -> Result<()> {
    let fixture = Fixture::load()?;
    let catalog = CoreCatalog::load(&fixture.core.repository, &fixture.core.repository)?;
    assert_eq!(catalog.catalog.0.len(), 20);
    assert!(catalog.context("sfm-dev/mc-1.19.2")?.features["document_history"]);
    for key in catalog.catalog.0.keys() {
        let context = catalog.context(key)?;
        // Current named selectors stay intact; the strict seam determines whether
        // history adds the accessor or an existing accessor owner already selects it.
        fixture.assert_current_and_historical(&context)?;
        let expected = matches!(context.minecraft_version.as_str(), "1.19.2" | "1.19.4")
            && [
                "single_line_input",
                "typed_command_palette",
                "document_history",
            ]
            .iter()
            .any(|owner| context.features[*owner]);
        let selected = select_core_inputs(&fixture.core.metadata, &context, &fixture.inventory)?;
        assert_eq!(selected.inputs.contains_key(ACCESSOR), expected, "{key}");
        assert_eq!(
            selected.omitted_paths.contains(ACCESSOR),
            !expected,
            "{key}"
        );
    }
    Ok(())
}

#[test]
fn accessor_history_newer_targets_refuse_d2_owners_without_widening_support() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut refused = 0;
    let mut unchanged = 0;
    for target in &TARGETS[2..] {
        for bit in [0, 1, 2, 5, 6] {
            let names = fixture.names(1 << bit, false)?;
            let error = fixture
                .context(target, &names)
                .expect_err("D2 owner must remain unsupported");
            assert!(error.to_string().contains("does not support target"));
            refused += 1;
        }
        for mask in [0, 1 << 3, 1 << 4, (1 << 3) | (1 << 4)] {
            let context = fixture.context(target, &fixture.names(mask, false)?)?;
            assert!(!fixture.assert_current_and_historical(&context)?);
            unchanged += 1;
        }
    }
    assert_eq!((refused, unchanged), (40, 32));
    Ok(())
}

#[test]
fn accessor_history_renderer_refuses_malformed_or_unknown_selected_guards() -> Result<()> {
    let fixture = Fixture::load()?;
    let context = fixture.core.context("1.19.2", &["document_history"])?;
    let unknown = fixture
        .current_resource
        .replace("features.document_history", "features.document_hstory");
    assert!(render_java_source(&unknown, &context).is_err());
    let unterminated = format!(
        "{}{{% if features.document_history %}}\n",
        fixture.current_resource
    );
    assert!(render_java_source(&unterminated, &context).is_err());
    let malformed_accessor = format!("{}{{% if features.document_history %}}\n", fixture.accessor);
    assert!(render_java_source(&malformed_accessor, &context).is_err());
    Ok(())
}

#[test]
fn accessor_history_real_selector_renderer_detect_either_side_of_registration_drift() -> Result<()>
{
    let fixture = Fixture::load()?;
    let context = fixture.core.context("1.19.2", &["document_history"])?;
    assert!(
        fixture
            .matching_client_classes(
                &fixture.historical_metadata,
                &fixture.current_resource,
                &context,
            )
            .is_err()
    );
    assert!(
        fixture
            .matching_client_classes(
                &fixture.core.metadata,
                &fixture.historical_resource,
                &context,
            )
            .is_err()
    );
    fixture.matching_client_classes(&fixture.core.metadata, &fixture.current_resource, &context)?;
    Ok(())
}
