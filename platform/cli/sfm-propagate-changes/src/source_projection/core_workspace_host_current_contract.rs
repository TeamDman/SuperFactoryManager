//! Test-only adapter retaining exact prior host identities.
//! The actual current template remains the renderer input. This inverse proves
//! the diagnostics owner split and four typed-palette boundaries since v3.
use super::provenance::sha256;
use eyre::Result;
use eyre::ensure;

const CURRENT_BYTES: usize = 161797;
const CURRENT_SHA: &str = "sha256:2f58e46fca1356fef2526849a11221c8ebea56229dc921b605e6bba7dd54f988";
const PRE_DIAGNOSTICS_BYTES: usize = 161697;
const PRE_DIAGNOSTICS_SHA: &str =
    "sha256:1f8c0250cefdf4a3f65531573923bc71c2e7f7adc48c7412fd6120719d46d348";
const PREVIOUS_BYTES: usize = 161594;
const PREVIOUS_SHA: &str =
    "sha256:258b0a9e5fef738d839c3ae5cb79d864e222b49b238b6921ab441bc91ef7d800";

pub(super) fn reviewed_pre_typed_palette_host(current: &str) -> Result<String> {
    ensure!(
        current.len() == CURRENT_BYTES && sha256(current.as_bytes()) == CURRENT_SHA,
        "current workspace host identity changed outside reviewed boundaries"
    );
    ensure!(
        current
            .matches("features.workspace_screen_diagnostics")
            .count()
            == 10,
        "workspace diagnostics owner inverse cardinality changed"
    );
    let mut previous = current.replace(
        "features.workspace_screen_diagnostics",
        "features.screen_diagnostics",
    );
    ensure!(
        previous.len() == PRE_DIAGNOSTICS_BYTES
            && sha256(previous.as_bytes()) == PRE_DIAGNOSTICS_SHA,
        "workspace diagnostics inverse did not recover the exact typed-palette snapshot"
    );
    for (before, after) in [
        (
            "{% if features.command_palette %}\n            SFMCommandPaletteScreen.openChoices(Component.literal(\"Close SFM workspace\"), escapeChoices());",
            "{% if features.typed_command_palette %}\n            SFMCommandPaletteScreen.openChoices(Component.literal(\"Close SFM workspace\"), escapeChoices());",
        ),
        (
            "{% if features.command_palette %}\n            else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) openWorkspaceToastActions(id);",
            "{% if features.typed_command_palette %}\n            else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) openWorkspaceToastActions(id);",
        ),
        (
            "{% if features.command_palette %}\n\n    private boolean openWorkspaceToastActions(SFMWorkspaceToastQueue.ToastId id) {",
            "{% if features.typed_command_palette %}\n\n    private boolean openWorkspaceToastActions(SFMWorkspaceToastQueue.ToastId id) {",
        ),
        (
            "    private boolean openPanelEntryActions(SFMPanelEntryAffordanceLayout.HitRegion hit) {\n        Optional<SFMPanelEntryInteractionSessionService.Session> captured =",
            "    private boolean openPanelEntryActions(SFMPanelEntryAffordanceLayout.HitRegion hit) {\n{% if features.typed_command_palette %}\n        Optional<SFMPanelEntryInteractionSessionService.Session> captured =",
        ),
        (
            "            throw failure;\n        }\n    }\n{% endif %}\n{% if features.workspace_panel_entry_controls %}",
            "            throw failure;\n        }\n{% else %}\n        return false;\n{% endif %}\n    }\n{% endif %}\n{% if features.workspace_panel_entry_controls %}",
        ),
    ] {
        ensure!(
            previous.matches(after).count() == 1,
            "typed-palette workspace boundary is not unique"
        );
        previous = previous.replacen(after, before, 1);
    }
    ensure!(
        previous.len() == PREVIOUS_BYTES && sha256(previous.as_bytes()) == PREVIOUS_SHA,
        "reviewed workspace inverse did not recover the unchanged v3 source pin"
    );
    Ok(previous)
}

#[test]
fn workspace_diagnostics_requires_explicit_owner_without_neutral_authority() -> Result<()> {
    let core = super::core_slice_test_support::CoreTestFixture::load()?;
    let source = core.read_source(
        "src/main/java/ca/teamdman/sfm/client/screen/workspace/SFMScreenMultiplexer.java",
    )?;
    let source = std::str::from_utf8(&source)?;
    assert_eq!(
        reviewed_pre_typed_palette_host(source)?.len(),
        PREVIOUS_BYTES
    );
    let pre_diagnostics = source.replace(
        "features.workspace_screen_diagnostics",
        "features.screen_diagnostics",
    );
    assert!(reviewed_pre_typed_palette_host(&pre_diagnostics).is_err());
    assert!(reviewed_pre_typed_palette_host(&format!("{source}\n// unexpected change\n")).is_err());
    let definition = &core.features.0["workspace_screen_diagnostics"];
    assert_eq!(definition.supported_targets, vec!["1.19.2", "1.19.4"]);
    assert_eq!(
        definition.requires,
        vec!["workspace_panels", "screen_diagnostics"]
    );
    for target in [
        "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
        "26.1.2",
    ] {
        let neutral = core.context(target, &["workspace_panels", "screen_diagnostics"])?;
        let neutral_body = super::render_java_source(source, &neutral)?;
        assert!(
            !neutral_body
                .contains("import ca.teamdman.sfm.client.screen.SFMScreenDiagnosticsContributor;")
        );
        assert!(!neutral_body.contains("public List<String> screenDiagnostics()"));
        let request = core.context(
            target,
            &[
                "workspace_panels",
                "screen_diagnostics",
                "workspace_screen_diagnostics",
            ],
        );
        if ["1.19.2", "1.19.4"].contains(&target) {
            let body = super::render_java_source(source, &request?)?;
            assert!(
                body.contains(
                    "import ca.teamdman.sfm.client.screen.SFMScreenDiagnosticsContributor;"
                )
            );
            assert!(body.contains("public List<String> screenDiagnostics()"));
            assert!(
                core.context(target, &["workspace_screen_diagnostics"])
                    .is_err()
            );
        } else {
            assert!(request.is_err());
        }
    }
    Ok(())
}
