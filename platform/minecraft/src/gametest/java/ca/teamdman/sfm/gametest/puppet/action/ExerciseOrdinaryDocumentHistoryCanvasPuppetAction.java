package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.screen.history.document.SFMDocumentHistoryPanel;
import ca.teamdman.sfm.client.screen.history.document.SFMDocumentHistoryViewport;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelMetrics;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetArtifactFormat;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.lwjgl.glfw.GLFW;

/** Exercises zoom and middle-button panning through the real workspace pointer router. */
public record ExerciseOrdinaryDocumentHistoryCanvasPuppetAction(String artifactName)
        implements SFMPuppetAction {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public ExerciseOrdinaryDocumentHistoryCanvasPuppetAction {
        if (artifactName == null || artifactName.isBlank()) {
            throw new IllegalArgumentException("Canvas interaction artifact name must not be blank");
        }
    }

    @Override
    public String description() {
        return "exercise ordinary document-history canvas pan and zoom";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        SFMScreenMultiplexer workspace = AssertOrdinaryDocumentHistoryPuppetAction.workspace();
        if (!(workspace.focusedPanelInstance() instanceof SFMDocumentHistoryPanel panel)) {
            throw new IllegalStateException("Document History must be focused before canvas interaction");
        }
        if (panel.loadStatus() != SFMDocumentHistoryPanel.LoadStatus.READY) return false;
        SFMScreenPanelBounds publishedBounds = panel.readyCanvasBoundsForAutomation().orElse(null);
        if (publishedBounds == null) return false;
        SFMWorkspacePanelMetrics metrics = workspace
                .measure(workspace.focusedPanelId(), publishedBounds)
                .orElse(null);
        if (metrics == null) return false;
        SFMScreenPanelBounds bounds = metrics.globalGuiLogicalBounds();
        double x = bounds.x() + bounds.width() / 2.0D;
        double y = bounds.y() + bounds.height() / 2.0D;
        SFMDocumentHistoryViewport before = panel.viewport();
        require(workspace.mouseScrolled(x, y, 1.0D), "workspace did not route history zoom");
        require(workspace.mouseClicked(x, y, GLFW.GLFW_MOUSE_BUTTON_MIDDLE),
                "workspace did not begin history pan");
        require(workspace.mouseDragged(x + 24.0D, y + 16.0D, GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
                        24.0D, 16.0D),
                "workspace did not route history pan");
        require(workspace.mouseReleased(x + 24.0D, y + 16.0D, GLFW.GLFW_MOUSE_BUTTON_MIDDLE),
                "workspace did not finish history pan");
        SFMDocumentHistoryViewport after = panel.viewport();
        require(after.zoom() != before.zoom(), "history wheel did not change zoom");
        require(after.panX() != before.panX() || after.panY() != before.panY(),
                "history drag did not change pan");
        JsonObject evidence = new JsonObject();
        evidence.addProperty("schema", "sfm.document-history-canvas-interaction/1");
        evidence.add("before", viewport(before));
        evidence.add("after", viewport(after));
        evidence.addProperty("pointer_x", x);
        evidence.addProperty("pointer_y", y);
        evidence.addProperty("geometry_generation", panel.geometryGeneration());
        evidence.addProperty("zoom_changed", true);
        evidence.addProperty("pan_changed", true);
        runtime.writeArtifact(artifactName, SFMGamePuppetArtifactFormat.JSON, GSON.toJson(evidence));
        return true;
    }

    private static JsonObject viewport(SFMDocumentHistoryViewport viewport) {
        JsonObject value = new JsonObject();
        value.addProperty("pan_x", viewport.panX());
        value.addProperty("pan_y", viewport.panY());
        value.addProperty("zoom", viewport.zoom());
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
