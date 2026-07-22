package ca.teamdman.sfm.client.screen.review.repository;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.theme.SFMClientThemeService;
import ca.teamdman.sfm.client.theme.SFMColourRole;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Dedicated, non-overlapping home for complete review comment text and ranges. */
public final class SFMRepositoryReviewCommentDetailsPanel implements SFMScreenPanel {
    private final SFMRepositoryReviewWorkspaceModel model;

    public SFMRepositoryReviewCommentDetailsPanel(SFMRepositoryReviewWorkspaceModel model) { this.model = model; }
    public SFMRepositoryReviewWorkspaceModel model() { return model; }
    @Override public Component title() { return Component.literal("Review comment details"); }
    @Override public Component narration() { return Component.literal(model.status()); }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (model.editing()) {
            if (key == GLFW.GLFW_KEY_ESCAPE) model.cancelInput();
            else if (key == GLFW.GLFW_KEY_BACKSPACE) model.backspaceInput();
            else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) model.finishInput();
            else return false;
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) { model.show(SFMRepositoryReviewWorkspaceModel.Blade.AFTER); return true; }
        return false;
    }

    @Override public boolean charTyped(char character, int modifiers) {
        if (!model.editing() || Character.isISOControl(character)) return false;
        model.type(character);
        return true;
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        model.show(SFMRepositoryReviewWorkspaceModel.Blade.COMMENTS);
        return true;
    }

    @Override public void render(PoseStack poseStack, Minecraft minecraft, SFMScreenPanelBounds bounds,
                                 int mouseX, int mouseY, float partialTick, boolean focused) {
        var theme = SFMClientThemeService.active();
        GuiComponent.fill(poseStack, bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(),
                theme.colour(SFMColourRole.PANEL_BACKGROUND));
        SFMRepositoryReviewPanelSupport.renderText(poseStack, minecraft,
                "COMMENT DETAILS · Ctrl+M maximize · Backspace source", bounds.x() + 7, bounds.y() + 7,
                bounds.width() - 14, theme.colour(SFMColourRole.TEXT_ACCENT), true);
        var file = model.activeFile();
        String path = file == null ? "No changed file selected" : SFMRepositoryReviewWorkspaceModel.displayPath(file);
        int y = bounds.y() + 21;
        for (String line : wrap(minecraft, path, bounds.width() - 14)) {
            SFMRepositoryReviewPanelSupport.renderText(poseStack, minecraft, line, bounds.x() + 7, y,
                    bounds.width() - 14, theme.colour(SFMColourRole.TEXT_PRIMARY), false);
            y += 13;
        }
        y += 6;
        if (model.editing()) {
            SFMRepositoryReviewPanelSupport.renderText(poseStack, minecraft, "NEW COMMENT", bounds.x() + 7, y,
                    bounds.width() - 14, 0xFFFFCC55, true);
            y += 14;
            for (String line : wrap(minecraft, model.draft() + "_", bounds.width() - 14)) {
                SFMRepositoryReviewPanelSupport.renderText(poseStack, minecraft, line, bounds.x() + 7, y,
                        bounds.width() - 14, theme.colour(SFMColourRole.TEXT_PRIMARY), false);
                y += 13;
            }
            y += 6;
        }
        var comments = model.commentsForActiveFile().stream()
                .sorted(java.util.Comparator.comparing(comment -> !comment.provenance().startsWith("human")))
                .toList();
        int contentBottom = bounds.y() + bounds.height() - 30;
        for (var comment : comments) {
            if (y + 26 >= contentBottom) break;
            int colour = comment.provenance().startsWith("human") ? 0xFFFFCC55 : 0xFF77AAFF;
            SFMRepositoryReviewPanelSupport.renderText(poseStack, minecraft,
                    comment.provenance().startsWith("human") ? "USER COMMENT" : "GENERATED CHANGE",
                    bounds.x() + 7, y, bounds.width() - 14, colour, true);
            y += 13;
            for (String line : wrap(minecraft, comment.text(), bounds.width() - 14)) {
                if (y + 13 >= contentBottom) break;
                SFMRepositoryReviewPanelSupport.renderText(poseStack, minecraft, line, bounds.x() + 7, y,
                        bounds.width() - 14, colour, false);
                y += 13;
            }
            for (var range : comment.ranges()) {
                if (y + 13 >= contentBottom) break;
                String value = conciseRange(range.documentRevisionId()) + " · UTF-8 ["
                        + range.startByte() + "," + range.endByte() + ")";
                for (String line : wrap(minecraft, value, bounds.width() - 20)) {
                    if (y + 13 >= contentBottom) break;
                    SFMRepositoryReviewPanelSupport.renderText(poseStack, minecraft, line, bounds.x() + 13, y,
                            bounds.width() - 20, theme.colour(SFMColourRole.TEXT_MUTED), false);
                    y += 13;
                }
            }
            y += 7;
            if (y >= contentBottom) break;
        }
        if (comments.isEmpty() && !model.editing()) SFMRepositoryReviewPanelSupport.renderText(poseStack, minecraft,
                "No comments target this file. Select a source line and press N.", bounds.x() + 7, y,
                bounds.width() - 14, theme.colour(SFMColourRole.TEXT_MUTED), false);
        GuiComponent.fill(poseStack, bounds.x(), bounds.y() + bounds.height() - 24,
                bounds.x() + bounds.width(), bounds.y() + bounds.height(),
                theme.colour(SFMColourRole.PANEL_BACKGROUND));
        SFMRepositoryReviewPanelSupport.renderText(poseStack, minecraft,
                model.status() + " · generated " + model.generatedForActive() + " · user " + model.humanCommentCount(),
                bounds.x() + 7, bounds.y() + bounds.height() - 17, bounds.width() - 14,
                theme.colour(SFMColourRole.TEXT_ACCENT), false);
    }

    private static String conciseRange(String documentId) {
        int after = documentId.indexOf(":after:");
        if (after >= 0) return "AFTER · " + documentId.substring(after + 7);
        int before = documentId.indexOf(":before:");
        if (before >= 0) return "BEFORE · " + documentId.substring(before + 8);
        return documentId;
    }

    private static List<String> wrap(Minecraft minecraft, String value, int width) {
        List<String> result = new ArrayList<>();
        String remaining = value;
        while (!remaining.isEmpty()) {
            String line = minecraft.font.plainSubstrByWidth(remaining, Math.max(1, width));
            if (line.isEmpty()) line = remaining.substring(0, 1);
            result.add(line);
            remaining = remaining.substring(line.length()).stripLeading();
        }
        if (result.isEmpty()) result.add("");
        return result;
    }
}
