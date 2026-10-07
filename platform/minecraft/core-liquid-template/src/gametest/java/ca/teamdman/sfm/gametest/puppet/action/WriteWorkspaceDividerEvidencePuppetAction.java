package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceAxis;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceDivider;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceDividerCursor;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceDividerInteraction;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceDividerView;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetArtifactFormat;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Writes and validates non-visual divider/share/bounds evidence beside puppet screenshots. */
public record WriteWorkspaceDividerEvidencePuppetAction(
        String artifactName,
        String stage,
        int paneCount
) implements SFMPuppetAction {
    private static final String SCHEMA = "sfm.workspace-divider-evidence/1";

    public WriteWorkspaceDividerEvidencePuppetAction {
        if (artifactName == null || artifactName.isBlank()) {
            throw new IllegalArgumentException("Workspace divider artifact name must not be blank");
        }
        if (!List.of("before", "hover", "during", "after").contains(stage)) {
            throw new IllegalArgumentException("Unsupported workspace divider evidence stage: " + stage);
        }
        if (paneCount < 2 || paneCount > 4) {
            throw new IllegalArgumentException("Workspace divider evidence requires 2, 3, or 4 panes");
        }
    }

    @Override
    public String description() {
        return "write workspace divider evidence " + artifactName;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (!(Minecraft.getInstance().screen instanceof SFMScreenMultiplexer workspace)) {
            throw new IllegalStateException("Expected SFM workspace for divider evidence");
        }
        SFMWorkspaceDividerInteraction.Snapshot interaction = workspace.dividerInteractionSnapshot();
        validateStage(interaction);

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("schema", SCHEMA);
        evidence.put("stage", stage);
        evidence.put("pane_count", paneCount);
        evidence.put("focused_panel_id", workspace.focusedPanelId().value());
        evidence.put("interaction", interactionEvidence(interaction));
        evidence.put("panels", workspace.panelIds().stream().map(panelId -> panelEvidence(workspace, panelId)).toList());
        evidence.put("dividers", workspace.dividerViews().stream().map(this::dividerEvidence).toList());
        runtime.writeArtifact(
                artifactName,
                SFMGamePuppetArtifactFormat.JSON,
                new GsonBuilder().setPrettyPrinting().create().toJson(evidence));
        return true;
    }

    private void validateStage(SFMWorkspaceDividerInteraction.Snapshot interaction) {
        int expectedDividerCount = paneCount == 2 ? 1 : paneCount == 3 ? 2 : 3;
        SFMWorkspaceDividerCursor expectedCursor = paneCount == 2
                ? SFMWorkspaceDividerCursor.HORIZONTAL_RESIZE
                : SFMWorkspaceDividerCursor.RESIZE_BOTH;
        switch (stage) {
            case "before" -> {
                if (!interaction.hoveredDividerIds().isEmpty()
                        || !interaction.capturedDividerIds().isEmpty()
                        || interaction.cursor() != SFMWorkspaceDividerCursor.DEFAULT) {
                    throw new IllegalStateException("Before evidence unexpectedly contains divider interaction");
                }
            }
            case "hover", "after" -> {
                if (interaction.cursor() != expectedCursor
                        || interaction.hoveredDividerIds().size() != expectedDividerCount
                        || !interaction.capturedDividerIds().isEmpty()) {
                    throw new IllegalStateException("Unexpected divider " + stage + " state: " + interaction);
                }
            }
            case "during" -> {
                if (interaction.cursor() != expectedCursor
                        || interaction.capturedDividerIds().size() != expectedDividerCount
                        || interaction.beforeBounds().equals(interaction.currentBounds())) {
                    throw new IllegalStateException("Unexpected divider drag state: " + interaction);
                }
                long horizontalChanges = interaction.appliedDeltas().entrySet().stream()
                        .filter(entry -> entry.getKey().axis() == SFMWorkspaceAxis.HORIZONTAL)
                        .filter(entry -> entry.getValue() != 0)
                        .count();
                long verticalChanges = interaction.appliedDeltas().entrySet().stream()
                        .filter(entry -> entry.getKey().axis() == SFMWorkspaceAxis.VERTICAL)
                        .filter(entry -> entry.getValue() != 0)
                        .count();
                if (horizontalChanges != (paneCount == 4 ? 2 : 1)
                        || verticalChanges != (paneCount == 2 ? 0 : 1)) {
                    throw new IllegalStateException(
                            "Divider drag did not resize every expected axis/member: " + interaction);
                }
                if (paneCount == 4 && interaction.appliedDeltas().entrySet().stream()
                        .filter(entry -> entry.getKey().axis() == SFMWorkspaceAxis.HORIZONTAL)
                        .map(Map.Entry::getValue)
                        .distinct()
                        .count() != 1) {
                    throw new IllegalStateException("Linked four-pane dividers did not move together");
                }
            }
            default -> throw new IllegalStateException("Unsupported divider stage: " + stage);
        }
    }

    private static Map<String, Object> interactionEvidence(SFMWorkspaceDividerInteraction.Snapshot interaction) {
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("cursor", interaction.cursor().name());
        answer.put("hovered_divider_ids", interaction.hoveredDividerIds().stream().map(Object::toString).toList());
        answer.put("captured_divider_ids", interaction.capturedDividerIds().stream().map(Object::toString).toList());
        answer.put("start_mouse_x", interaction.startMouseX());
        answer.put("start_mouse_y", interaction.startMouseY());
        answer.put("current_mouse_x", interaction.currentMouseX());
        answer.put("current_mouse_y", interaction.currentMouseY());
        answer.put("applied_deltas", stringKeyedDeltas(interaction.appliedDeltas()));
        answer.put("before_bounds", stringKeyedBounds(interaction.beforeBounds()));
        answer.put("current_bounds", stringKeyedBounds(interaction.currentBounds()));
        return answer;
    }

    private static Map<String, Object> panelEvidence(
            SFMScreenMultiplexer workspace,
            SFMWorkspacePanelId panelId
    ) {
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("panel_id", panelId.value());
        answer.put("stack_id", workspace.panelStackId(panelId).map(Object::toString).orElse(null));
        answer.put("bounds", boundsEvidence(workspace.panelBounds(panelId)));
        return answer;
    }

    private Map<String, Object> dividerEvidence(SFMWorkspaceDividerView view) {
        SFMWorkspaceDivider divider = view.logical();
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("id", divider.id().toString());
        answer.put("axis", divider.axis().name());
        answer.put("link_id", divider.linkId() == null ? null : divider.linkId().toString());
        answer.put("line_bounds_logical", boundsEvidence(divider.lineBounds()));
        answer.put("hit_bounds_logical", boundsEvidence(divider.hitBounds()));
        answer.put("line_bounds_physical", boundsEvidence(view.physicalLineBounds()));
        answer.put("hit_bounds_physical", boundsEvidence(view.physicalHitBounds()));
        answer.put("position", divider.position());
        answer.put("minimum_position", divider.minimumPosition());
        answer.put("maximum_position", divider.maximumPosition());
        answer.put("before_pixels", divider.beforePixels());
        answer.put("after_pixels", divider.afterPixels());
        answer.put("before_minimum_pixels", divider.beforeMinimumPixels());
        answer.put("after_minimum_pixels", divider.afterMinimumPixels());
        answer.put("before_share", divider.beforeShare());
        answer.put("after_share", divider.afterShare());
        answer.put("before_track_panels", divider.beforeTrackPanels().stream().map(SFMWorkspacePanelId::value).toList());
        answer.put("after_track_panels", divider.afterTrackPanels().stream().map(SFMWorkspacePanelId::value).toList());
        return answer;
    }

    private static Map<String, Object> stringKeyedDeltas(
            Map<ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceDividerId, Integer> deltas
    ) {
        Map<String, Object> answer = new LinkedHashMap<>();
        deltas.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> answer.put(entry.getKey().toString(), entry.getValue()));
        return answer;
    }

    private static Map<String, Object> stringKeyedBounds(
            Map<SFMWorkspacePanelId, SFMScreenPanelBounds> bounds
    ) {
        Map<String, Object> answer = new LinkedHashMap<>();
        bounds.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparingLong(SFMWorkspacePanelId::value)))
                .forEach(entry -> answer.put(Long.toString(entry.getKey().value()), boundsEvidence(entry.getValue())));
        return answer;
    }

    private static Map<String, Integer> boundsEvidence(SFMScreenPanelBounds bounds) {
        if (bounds == null) return Map.of();
        Map<String, Integer> answer = new LinkedHashMap<>();
        answer.put("x", bounds.x());
        answer.put("y", bounds.y());
        answer.put("width", bounds.width());
        answer.put("height", bounds.height());
        return answer;
    }
}
