//! Source-only prepared regression contracts for the authentic workspace family.
//! Production selector runs before any leaf read; production controlled Liquid
//! renders the selected core source. No Git, CLI subprocess, JVM, sync or default
//! mutation. Raw SHA-256/length witnesses were already acquired and sealed.
//! Passing these contracts would prove membership/body rendering, not Java/API,
//! loader, GUI interaction, runtime safety or whole-migration acceptance.
#![cfg(test)]
use super::context::ProjectionContext;
use super::core_client_registration_current_contract::reviewed_pre_workspace_registrar;
use super::core_inputs::CoreSelection;
use super::core_inputs::discover_core_source_files;
use super::core_inputs::select_core_inputs;
use super::core_slice_test_support::CoreTestFixture;
use super::provenance::sha256;
use super::render_java_source;
use eyre::Result;
use eyre::ensure;
use std::collections::BTreeSet;

const ACTION: &str = "src/main/java/ca/teamdman/sfm/client/action/";
const REGISTRAR: &str = "src/main/java/ca/teamdman/sfm/client/action/SFMCommandPaletteActions.java";
const REGISTRATIONS: &str = "src/main/java/ca/teamdman/sfm/client/SFMClientRegistrations.java";
const TARGETS: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
struct Golden {
    name: &'static str,
    family: &'static str,
    bytes: usize,
    digest: &'static str,
}
const GOLDENS: [Golden; 25] = [
    Golden {
        name: "SFMGuiScaleAction.java",
        family: "d2",
        bytes: 4768,
        digest: "sha256:9c690949346b9ae200bd89ebe785f3b23bfe0a2bf591159233159a38eb373c9c",
    },
    Golden {
        name: "ResizeDividersAction.java",
        family: "d2",
        bytes: 4032,
        digest: "sha256:a2c57c468ff721c30c57345c0d1a200ae3680afab3581862b560d7c22fb1ab4c",
    },
    Golden {
        name: "PanelActionSupport.java",
        family: "d2",
        bytes: 5578,
        digest: "sha256:252a17eef3bf1ae620c052783a04603e3abab63b1a488e99df93e8b304a27c71",
    },
    Golden {
        name: "SFMWorkspaceLifecycleActionIds.java",
        family: "d2",
        bytes: 2253,
        digest: "sha256:e392bb436b153374c32efc7c3aa0ddd6cb75956194b9cbdf09f332dd96451062",
    },
    Golden {
        name: "OpenScreenToSideAction.java",
        family: "legacy",
        bytes: 4138,
        digest: "sha256:e84f6e81ce948d57382bde46bbde6979c03ebee4db64bf795fb23fc768daeb96",
    },
    Golden {
        name: "SFMScreenDiagnosticsAction.java",
        family: "d2",
        bytes: 6578,
        digest: "sha256:ae26780d20e83dff88707de8cfd586a0aae685ae6e5e99f928c3be97968cb611",
    },
    Golden {
        name: "SFMPanelEntryInteractionSessionService.java",
        family: "d2",
        bytes: 5336,
        digest: "sha256:fda6d11461b7a73110b3eccf898c7c23f087dd063c4734cb5af403094e6b5f24",
    },
    Golden {
        name: "ClosePanelAction.java",
        family: "d2",
        bytes: 1331,
        digest: "sha256:4aa08df511d384bfc5aaf67aa50b3877cd7659f25f4adfae727baaee0694da39",
    },
    Golden {
        name: "OpenMinecraftScreenAction.java",
        family: "d2",
        bytes: 4013,
        digest: "sha256:960aa192e76d1bfef12c8332fcad5c28bad32fc133a7c7ea92265dc95ae2bf99",
    },
    Golden {
        name: "SFMPanelEntryMoveToAction.java",
        family: "d2",
        bytes: 3758,
        digest: "sha256:17a3574fe8ac622613b32594f592461e32c00593a7734c97d9e6e13aeebd6989",
    },
    Golden {
        name: "MovePanelAction.java",
        family: "d2",
        bytes: 1353,
        digest: "sha256:3eb60636430f5b4346c9edda9c6900beeb8279da930efde09bfbfa77f9389284",
    },
    Golden {
        name: "ToggleMaximizePanelAction.java",
        family: "d2",
        bytes: 1487,
        digest: "sha256:6a7a965ad5e3e1afcb3e0390c5313ae8207a01d90d5cc107ff2a8cc3e8f85c26",
    },
    Golden {
        name: "FocusPanelAction.java",
        family: "d2",
        bytes: 2416,
        digest: "sha256:02784c45a2ae81972bd811073cf2e855a0de04f7793f8dbe8092df0729442084",
    },
    Golden {
        name: "SFMPanelEntryAction.java",
        family: "d2",
        bytes: 4313,
        digest: "sha256:2022bb1832577413a5f8f861fa17ad0817717961f059bbda795826ac11371cb8",
    },
    Golden {
        name: "OpenPanelDiagnosticsAction.java",
        family: "d2",
        bytes: 1359,
        digest: "sha256:2310ed0f8947ddc6c9548a5c4f1f2de7e79b9393baa90e61e017e4cb4a3016fe",
    },
    Golden {
        name: "OpenScreenToSideAction.java",
        family: "26.1.2",
        bytes: 4145,
        digest: "sha256:64ac7cc85a238c3bf80c992beb32f213e6923f0f35c41f7d15747549fb89df03",
    },
    Golden {
        name: "OpenDeveloperPanelAction.java",
        family: "d2",
        bytes: 3218,
        digest: "sha256:8f7193e17de5f9970032f31943041e97a3f34f17f64c8ad28f73254fa2cc58e2",
    },
    Golden {
        name: "DuplicatePanelAction.java",
        family: "d2",
        bytes: 2077,
        digest: "sha256:cb728d4cac90c5246b9ae098908b974596ea5888fa2369338181f28e242b888b",
    },
    Golden {
        name: "SFMWorkspaceLifecycleActions.java",
        family: "d2",
        bytes: 2921,
        digest: "sha256:1be426dff701c250c91083fea7d74146529de1b409bd41dac2e91775a7aa41fe",
    },
    Golden {
        name: "SFMClosePaneAction.java",
        family: "d2",
        bytes: 7208,
        digest: "sha256:0621fa645746a2e752f7416f87e1ef99e4a888a010b0f879d8f0c4ab13dec3f8",
    },
    Golden {
        name: "ResizePanelAction.java",
        family: "d2",
        bytes: 2059,
        digest: "sha256:316c8cb76e5329c2dc6e0f6e3b43baaa5a805a2c4bba77a95e86515676b24955",
    },
    Golden {
        name: "RotatePanelAction.java",
        family: "d2",
        bytes: 1525,
        digest: "sha256:e4422c2ca268b41175bbc328538f0f684d3b848a0047661a87734aeedd21d4fd",
    },
    Golden {
        name: "PanelScaleAction.java",
        family: "d2",
        bytes: 5636,
        digest: "sha256:4617e51e80dcb992e3783164d1841e35ea1fc6be5b2f78532c522d8284fa7706",
    },
    Golden {
        name: "SFMWorkspacePaneCloseSessionService.java",
        family: "d2",
        bytes: 3189,
        digest: "sha256:9e51410ddd51d29e71bee7106340c9b7f6cc8340846025ee231fd9c21c5d233d",
    },
    Golden {
        name: "CloseScreenAction.java",
        family: "d2",
        bytes: 930,
        digest: "sha256:bc9ab16a5806ac88a170c42cd3a0126b35ad2f39c75c600f45353254836899c2",
    },
];
struct Rule {
    name: &'static str,
    targets: &'static [&'static str],
    all: &'static [&'static str],
    any: &'static [&'static str],
}
const RULES: [Rule; 24] = [
    Rule {
        name: "ClosePanelAction.java",
        targets: &["1.19.2", "1.19.4"],
        all: &["client_actions", "workspace_panel_actions"],
        any: &[],
    },
    Rule {
        name: "CloseScreenAction.java",
        targets: &["1.19.2", "1.19.4"],
        all: &[
            "client_actions",
            "client_screen_actions",
            "workspace_panels",
        ],
        any: &[],
    },
    Rule {
        name: "DuplicatePanelAction.java",
        targets: &["1.19.2", "1.19.4"],
        all: &[
            "client_actions",
            "workspace_panel_actions",
            "workspace_panel_reopening",
        ],
        any: &[],
    },
    Rule {
        name: "FocusPanelAction.java",
        targets: &["1.19.2", "1.19.4"],
        all: &["client_actions", "workspace_panel_actions"],
        any: &[],
    },
    Rule {
        name: "MovePanelAction.java",
        targets: &["1.19.2", "1.19.4"],
        all: &[
            "client_actions",
            "workspace_panel_actions",
            "workspace_stack_controls",
        ],
        any: &[],
    },
    Rule {
        name: "OpenDeveloperPanelAction.java",
        targets: &["1.19.2", "1.19.4"],
        all: &[
            "client_actions",
            "workspace_panel_actions",
            "developer_tools",
        ],
        any: &[],
    },
    Rule {
        name: "OpenMinecraftScreenAction.java",
        targets: &["1.19.2", "1.19.4"],
        all: &["client_actions", "client_screen_actions"],
        any: &[],
    },
    Rule {
        name: "OpenPanelDiagnosticsAction.java",
        targets: &["1.19.2", "1.19.4"],
        all: &[
            "client_actions",
            "workspace_panel_actions",
            "typed_command_palette",
        ],
        any: &[],
    },
    Rule {
        name: "OpenScreenToSideAction.java",
        targets: &[
            "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2",
        ],
        all: &["workspace_legacy_open_action"],
        any: &[],
    },
    Rule {
        name: "PanelActionSupport.java",
        targets: &["1.19.2", "1.19.4"],
        all: &["client_actions"],
        any: &[
            "workspace_panel_actions",
            "client_screen_actions",
            "workspace_lifecycle",
        ],
    },
    Rule {
        name: "PanelScaleAction.java",
        targets: &["1.19.2", "1.19.4"],
        all: &[
            "client_actions",
            "workspace_panel_actions",
            "workspace_panel_metadata",
            "workspace_stack_controls",
        ],
        any: &[],
    },
    Rule {
        name: "ResizeDividersAction.java",
        targets: &["1.19.2", "1.19.4"],
        all: &[
            "client_actions",
            "workspace_panel_actions",
            "workspace_dividers",
        ],
        any: &[],
    },
    Rule {
        name: "ResizePanelAction.java",
        targets: &["1.19.2", "1.19.4"],
        all: &[
            "client_actions",
            "workspace_panel_actions",
            "workspace_directional_resize",
        ],
        any: &[],
    },
    Rule {
        name: "RotatePanelAction.java",
        targets: &["1.19.2", "1.19.4"],
        all: &[
            "client_actions",
            "workspace_panel_actions",
            "workspace_stack_controls",
        ],
        any: &[],
    },
    Rule {
        name: "SFMClosePaneAction.java",
        targets: &["1.19.2", "1.19.4"],
        all: &[
            "client_actions",
            "workspace_lifecycle",
            "workspace_panel_actions",
            "workspace_stack_controls",
            "typed_command_palette",
        ],
        any: &[],
    },
    Rule {
        name: "SFMGuiScaleAction.java",
        targets: &["1.19.2", "1.19.4"],
        all: &["client_actions", "client_screen_actions"],
        any: &[],
    },
    Rule {
        name: "SFMPanelEntryAction.java",
        targets: &["1.19.2", "1.19.4"],
        all: &[
            "client_actions",
            "workspace_panel_actions",
            "workspace_stack_controls",
        ],
        any: &["workspace_lifecycle", "workspace_panel_entry_controls"],
    },
    Rule {
        name: "SFMPanelEntryInteractionSessionService.java",
        targets: &["1.19.2", "1.19.4"],
        all: &["workspace_panel_actions", "workspace_stack_controls"],
        any: &["workspace_lifecycle", "workspace_panel_entry_controls"],
    },
    Rule {
        name: "SFMPanelEntryMoveToAction.java",
        targets: &["1.19.2", "1.19.4"],
        all: &[
            "client_actions",
            "workspace_panel_actions",
            "workspace_stack_controls",
        ],
        any: &["workspace_lifecycle", "workspace_panel_entry_controls"],
    },
    Rule {
        name: "SFMScreenDiagnosticsAction.java",
        targets: &["1.19.2", "1.19.4"],
        all: &[
            "client_actions",
            "screen_diagnostics",
            "workspace_panel_actions",
            "editor_overlay_push",
        ],
        any: &[],
    },
    Rule {
        name: "SFMWorkspaceLifecycleActionIds.java",
        targets: &["1.19.2", "1.19.4"],
        all: &[],
        any: &["workspace_lifecycle", "workspace_panel_entry_controls"],
    },
    Rule {
        name: "SFMWorkspaceLifecycleActions.java",
        targets: &["1.19.2", "1.19.4"],
        all: &["client_actions"],
        any: &["workspace_lifecycle", "workspace_panel_entry_controls"],
    },
    Rule {
        name: "SFMWorkspacePaneCloseSessionService.java",
        targets: &["1.19.2", "1.19.4"],
        all: &["workspace_lifecycle", "workspace_stack_controls"],
        any: &[],
    },
    Rule {
        name: "ToggleMaximizePanelAction.java",
        targets: &["1.19.2", "1.19.4"],
        all: &["client_actions", "workspace_panel_actions"],
        any: &[],
    },
];

fn context(core: &CoreTestFixture, target: &str, roots: &[&str]) -> Result<ProjectionContext> {
    let mut explicit = roots
        .iter()
        .map(|x| (*x).to_owned())
        .collect::<BTreeSet<_>>();
    loop {
        let mut additions = Vec::new();
        for name in &explicit {
            let definition = core
                .features
                .0
                .get(name)
                .ok_or_else(|| eyre::eyre!("unknown test feature {name}"))?;
            additions.extend(
                definition
                    .requires
                    .iter()
                    .filter(|x| !explicit.contains(*x))
                    .cloned(),
            );
        }
        if additions.is_empty() {
            break;
        }
        explicit.extend(additions);
    }
    let borrowed = explicit.iter().map(String::as_str).collect::<Vec<_>>();
    core.context(target, &borrowed)
}
// One real inventory/selection belongs to one immutable, finalized context.
// This is private test-local preparation, not a shared fixture or output cache.
struct WorkspaceRenderContext<'a> {
    core: &'a CoreTestFixture,
    context: &'a ProjectionContext,
    selection: CoreSelection,
}
impl<'a> WorkspaceRenderContext<'a> {
    fn new(core: &'a CoreTestFixture, context: &'a ProjectionContext) -> Result<Self> {
        let inventory = discover_core_source_files(&core.core)?;
        let selection = select_core_inputs(&core.metadata, context, &inventory)?;
        Ok(Self {
            core,
            context,
            selection,
        })
    }
    fn render(&self, path: &str) -> Result<Option<String>> {
        let Some(input) = self.selection.inputs.get(path) else {
            ensure!(
                self.selection.omitted_paths.contains(path),
                "absence is not explicit: {path}"
            );
            return Ok(None);
        };
        ensure!(input.input == path, "unreviewed alternate input: {path}");
        // Java remains rendered regardless of its marker. Current reviewed
        // registrar metadata is true; its exact historical source proof follows.
        ensure!(input.template, "unexpected template marker: {path}");
        let source = self.core.read_source(path)?;
        if path == REGISTRATIONS {
            reviewed_pre_workspace_registrar(&source)?;
        }
        Ok(Some(render_java_source(
            std::str::from_utf8(&source)?,
            self.context,
        )?))
    }
    fn action(&self, name: &str) -> Result<Option<String>> {
        self.render(&format!("{ACTION}{name}"))
    }
}
fn same(actual: &[String], expected: &[&str]) -> bool {
    actual.len() == expected.len()
        && actual.iter().map(String::as_str).collect::<BTreeSet<_>>()
            == expected.iter().copied().collect()
}
fn enabled(context: &ProjectionContext, feature: &str) -> bool {
    context.features.get(feature).copied().unwrap_or(false)
}
const D2_ROOTS: &[&str] = &[
    "workspace_panel_actions",
    "workspace_lifecycle",
    "workspace_panel_entry_controls",
    "workspace_stack_controls",
    "workspace_panel_metadata",
    "workspace_panel_reopening",
    "workspace_directional_resize",
    "workspace_dividers",
    "workspace_notifications",
    "client_screen_actions",
    "screen_diagnostics",
    "editor_overlay_push",
    "client_overlay_scenes",
    "typed_command_palette",
    "document_history",
    "developer_tools",
];
const LEGACY_ROOTS: &[&str] = &["workspace_legacy_open_action", "command_palette"];
#[test]
fn workspace_family_real_selector_and_renderer_reconstruct_all_25_authentic_bodies() -> Result<()> {
    let core = CoreTestFixture::load()?;
    for target in TARGETS {
        let d2 = matches!(target, "1.19.2" | "1.19.4");
        let ctx = context(&core, target, if d2 { D2_ROOTS } else { LEGACY_ROOTS })?;
        let rendered = WorkspaceRenderContext::new(&core, &ctx)?;
        for rule in RULES {
            let emitted = rendered.action(rule.name)?;
            let family = if d2 {
                "d2"
            } else if target == "26.1.2" {
                "26.1.2"
            } else {
                "legacy"
            };
            if let Some(golden) = GOLDENS
                .iter()
                .find(|g| g.name == rule.name && g.family == family)
            {
                let emitted = emitted.ok_or_else(|| {
                    eyre::eyre!("missing authentic family body {target}/{}", rule.name)
                })?;
                ensure!(
                    emitted.len() == golden.bytes && sha256(emitted.as_bytes()) == golden.digest,
                    "historical body identity changed for {target}/{}",
                    rule.name
                );
                ensure!(
                    !emitted.contains("{%") && !emitted.contains("{%REPLACEME%}"),
                    "unrendered source"
                );
            } else {
                ensure!(
                    emitted.is_none(),
                    "body leaked outside historical target scope"
                );
            }
        }
    }
    Ok(())
}
#[test]
fn workspace_family_exact_24_membership_rules_and_independent_owner_requires_are_preserved()
-> Result<()> {
    let core = CoreTestFixture::load()?;
    for expected in RULES {
        let path = format!("{ACTION}{}", expected.name);
        let variants = core
            .metadata
            .source_rules
            .get(&path)
            .ok_or_else(|| eyre::eyre!("missing rule {path}"))?;
        ensure!(
            variants.len() == 1
                && variants[0].input == path
                && variants[0].template
                && same(&variants[0].when.targets, expected.targets)
                && same(&variants[0].when.all_features, expected.all)
                && same(&variants[0].when.any_features, expected.any)
                && variants[0].when.none_features.is_empty(),
            "workspace rule changed: {path}"
        );
    }
    for (name, requires) in [
        ("workspace_lifecycle", &[][..]),
        ("workspace_dividers", &[][..]),
        ("screen_diagnostics", &[][..]),
        ("client_screen_actions", &["client_actions"][..]),
        (
            "workspace_panel_actions",
            &["client_actions", "workspace_panels"][..],
        ),
        (
            "workspace_panel_entry_controls",
            &[
                "workspace_stack_controls",
                "workspace_panel_actions",
                "command_palette",
            ][..],
        ),
        ("workspace_directional_resize", &["workspace_panels"][..]),
        (
            "workspace_panel_move_gestures",
            &["workspace_panel_entry_controls", "pointer_modifier_ingress"][..],
        ),
        (
            "workspace_legacy_open_action",
            &["client_actions", "workspace_panels"][..],
        ),
    ] {
        ensure!(
            same(&core.features.0[name].requires, requires),
            "owner prerequisite changed: {name}"
        );
    }
    Ok(())
}
#[test]
fn workspace_family_twenty_empty_feature_contexts_omit_all_24_new_inputs() -> Result<()> {
    let core = CoreTestFixture::load()?;
    // Environment is not a feature. Both release and dev empty contexts must omit.
    for target in TARGETS {
        for environment in ["release", "dev"] {
            let mut ctx = context(&core, target, &[])?;
            ctx.environment = environment.to_owned();
            let namespace = if environment == "release" {
                "sfm-4.34.0"
            } else {
                "sfm-dev"
            };
            ctx.projection_key = format!("{namespace}/mc-{target}");
            ctx.preset = ctx.projection_key.clone();
            ensure!(
                ctx.environment == environment,
                "environment context was not assigned"
            );
            let rendered = WorkspaceRenderContext::new(&core, &ctx)?;
            for rule in RULES {
                ensure!(rendered.action(rule.name)?.is_none(), "empty-feature leak");
            }
        }
    }
    Ok(())
}
#[test]
fn workspace_family_optional_host_masks_match_registrar_and_omit_unsupported_operations()
-> Result<()> {
    let core = CoreTestFixture::load()?;
    for target in &TARGETS[..2] {
        for roots in [
            &["workspace_panel_actions"][..],
            &["client_screen_actions"][..],
            &["workspace_lifecycle"][..],
            &["client_actions", "workspace_lifecycle"][..],
            &["workspace_panel_entry_controls"][..],
            &["workspace_panel_entry_controls", "typed_command_palette"][..],
            D2_ROOTS,
        ] {
            let ctx = context(&core, target, roots)?;
            let rendered = WorkspaceRenderContext::new(&core, &ctx)?;
            let registrar = rendered.render(REGISTRAR)?;
            for (name, guard) in [
                (
                    "DuplicatePanelAction.java",
                    enabled(&ctx, "workspace_panel_reopening"),
                ),
                (
                    "MovePanelAction.java",
                    enabled(&ctx, "workspace_stack_controls"),
                ),
                (
                    "ResizePanelAction.java",
                    enabled(&ctx, "workspace_directional_resize"),
                ),
                (
                    "ResizeDividersAction.java",
                    enabled(&ctx, "workspace_dividers"),
                ),
                (
                    "PanelScaleAction.java",
                    enabled(&ctx, "workspace_panel_metadata")
                        && enabled(&ctx, "workspace_stack_controls"),
                ),
                (
                    "RotatePanelAction.java",
                    enabled(&ctx, "workspace_stack_controls"),
                ),
                (
                    "OpenPanelDiagnosticsAction.java",
                    enabled(&ctx, "typed_command_palette"),
                ),
            ] {
                let expected = enabled(&ctx, "workspace_panel_actions") && guard;
                ensure!(
                    rendered.action(name)?.is_some() == expected,
                    "optional host membership mismatch"
                );
                ensure!(
                    registrar
                        .as_ref()
                        .is_some_and(|s| s.contains(name.trim_end_matches(".java")))
                        == expected,
                    "optional registrar dangling reference"
                );
            }
            if enabled(&ctx, "client_actions") && !enabled(&ctx, "workspace_panels") {
                let support = rendered
                    .action("PanelActionSupport.java")?
                    .ok_or_else(|| eyre::eyre!("screen-only support absent"))?;
                ensure!(
                    !support.contains("SFMScreenMultiplexer")
                        && !support.contains("SFMScreenPanel"),
                    "workspace host leaked"
                );
            }
            if roots == &["workspace_lifecycle"][..] {
                let ids = rendered
                    .action("SFMWorkspaceLifecycleActionIds.java")?
                    .ok_or_else(|| eyre::eyre!("bootstrap-free ids absent"))?;
                ensure!(
                    !ids.contains("SFMActionChoice")
                        && !ids.contains("SFMPanelEntryInteractionSessionService"),
                    "action/UI leaked into ids"
                );
            }
        }
    }
    Ok(())
}
#[test]
fn workspace_family_entry_controls_without_lifecycle_keep_real_leases_and_registration()
-> Result<()> {
    let core = CoreTestFixture::load()?;
    for target in &TARGETS[..2] {
        for roots in [
            &["workspace_panel_entry_controls"][..],
            &["workspace_panel_entry_controls", "typed_command_palette"][..],
        ] {
            let ctx = context(&core, target, roots)?;
            let rendered = WorkspaceRenderContext::new(&core, &ctx)?;
            ensure!(
                !enabled(&ctx, "workspace_lifecycle"),
                "test widened lifecycle prerequisite"
            );
            for name in [
                "SFMPanelEntryInteractionSessionService.java",
                "SFMWorkspaceLifecycleActionIds.java",
                "SFMWorkspaceLifecycleActions.java",
                "SFMPanelEntryAction.java",
                "SFMPanelEntryMoveToAction.java",
            ] {
                ensure!(
                    rendered.action(name)?.is_some(),
                    "entry circuit missing: {name}"
                );
            }
            let ids = rendered
                .action("SFMWorkspaceLifecycleActionIds.java")?
                .unwrap();
            ensure!(
                !ids.contains("SFMActionChoice.invoke(PANE_CLOSE"),
                "lifecycle-only choice leaked"
            );
            let registrations = rendered
                .render(REGISTRATIONS)?
                .ok_or_else(|| eyre::eyre!("registration input absent"))?;
            ensure!(
                registrations.contains("SFMWorkspaceLifecycleActions.register(bus)"),
                "entry registrar missing"
            );
            let actions = rendered
                .action("SFMWorkspaceLifecycleActions.java")?
                .unwrap();
            ensure!(
                actions.contains("panel/entry/focus") && !actions.contains("pane/close"),
                "wrong contributor ownership"
            );
            ensure!(
                rendered.action("SFMClosePaneAction.java")?.is_none(),
                "pane-close leaked"
            );
        }
    }
    Ok(())
}
#[test]
fn workspace_family_unsupported_owner_targets_refuse_before_source_reads() -> Result<()> {
    let core = CoreTestFixture::load()?;
    let diagnostics = &core.features.0["screen_diagnostics"];
    assert_eq!(diagnostics.supported_targets, TARGETS);
    assert!(diagnostics.requires.is_empty());
    for target in TARGETS {
        let profile = context(&core, target, &["screen_diagnostics"])?;
        assert!(profile.features["screen_diagnostics"]);
        assert!(!profile.features["workspace_panels"] && !profile.features["client_actions"]);
    }
    for target in &TARGETS[2..] {
        for owner in [
            "workspace_lifecycle",
            "workspace_panel_actions",
            "client_screen_actions",
            "workspace_panel_entry_controls",
        ] {
            ensure!(
                context(&core, target, &[owner]).is_err(),
                "D2 owner accepted unsupported target"
            );
        }
    }
    for target in &TARGETS[..2] {
        ensure!(
            context(&core, target, &["workspace_legacy_open_action"]).is_err(),
            "legacy owner accepted D2"
        );
    }
    Ok(())
}
