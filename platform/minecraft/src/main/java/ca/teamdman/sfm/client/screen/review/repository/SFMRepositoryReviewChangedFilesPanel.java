package ca.teamdman.sfm.client.screen.review.repository;

import ca.teamdman.sfm.client.presentation.SFMItemIconRenderer;
import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerEntry;
import ca.teamdman.sfm.client.screen.file_explorer.SFMFilePresentationRegistry;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.theme.SFMClientThemeService;
import ca.teamdman.sfm.client.theme.SFMColourRole;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public final class SFMRepositoryReviewChangedFilesPanel implements SFMScreenPanel {
    private static final int ROW_HEIGHT = 22;
    private final SFMRepositoryReviewWorkspaceModel model;
    private final SFMFilePresentationRegistry presentations = SFMFilePresentationRegistry.createDefault();
    private SFMScreenPanelBounds lastBounds = new SFMScreenPanelBounds(0, 0, 0, 0);

    public SFMRepositoryReviewChangedFilesPanel(SFMRepositoryReviewWorkspaceModel model) { this.model = model; }
    public SFMRepositoryReviewWorkspaceModel model() { return model; }
    @Override public Component title() { return Component.literal("Changed files"); }
    @Override public Component narration() { return Component.literal(model.status()); }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (model.searching()) {
            if (key == GLFW.GLFW_KEY_ESCAPE) model.cancelInput();
            else if (key == GLFW.GLFW_KEY_BACKSPACE) model.backspaceInput();
            else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) model.finishInput();
            else return false;
            return true;
        }
        if (key == GLFW.GLFW_KEY_SLASH) { model.beginSearch(); return true; }
        if (key == GLFW.GLFW_KEY_DOWN) { model.moveSelection(1); return true; }
        if (key == GLFW.GLFW_KEY_UP) { model.moveSelection(-1); return true; }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_RIGHT) {
            model.show(SFMRepositoryReviewWorkspaceModel.Blade.AFTER);
            return true;
        }
        return false;
    }

    @Override public boolean charTyped(char character, int modifiers) {
        if (!model.searching() || Character.isISOControl(character)) return false;
        model.type(character);
        return true;
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        model.show(SFMRepositoryReviewWorkspaceModel.Blade.FILES);
        int row = (int) (mouseY - lastBounds.y() - 58) / ROW_HEIGHT;
        if (row >= 0 && row < model.visibleFiles().size()) {
            model.selectFile(row);
            return true;
        }
        return false;
    }

    @Override public void render(PoseStack poseStack, Minecraft minecraft, SFMScreenPanelBounds bounds,
                                 int mouseX, int mouseY, float partialTick, boolean focused) {
        lastBounds = bounds;
        var theme = SFMClientThemeService.active();
        GuiComponent.fill(poseStack, bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(),
                theme.colour(SFMColourRole.PANEL_BACKGROUND));
        SFMRepositoryReviewPanelSupport.renderText(poseStack, minecraft,
                "REPOSITORY REVIEW · CHANGED FILES", bounds.x() + 7, bounds.y() + 7, bounds.width() - 14,
                theme.colour(SFMColourRole.TEXT_ACCENT), true);
        SFMRepositoryReviewPanelSupport.renderText(poseStack, minecraft,
                model.bundle().summary().beforeLabel() + " → " + model.bundle().summary().afterLabel(),
                bounds.x() + 7, bounds.y() + 20, bounds.width() - 14, theme.colour(SFMColourRole.TEXT_MUTED), false);
        SFMRepositoryReviewPanelSupport.renderText(poseStack, minecraft,
                model.searching() ? "Search: " + model.search() + "_" : "/ search · ↑/↓ browse · Enter inspect",
                bounds.x() + 7, bounds.y() + 36, bounds.width() - 14, theme.colour(SFMColourRole.TEXT_PRIMARY), false);
        var files = model.visibleFiles();
        for (int index = 0; index < files.size(); index++) {
            int y = bounds.y() + 58 + index * ROW_HEIGHT;
            if (y + ROW_HEIGHT >= bounds.y() + bounds.height() - 30) break;
            var file = files.get(index);
            if (index == model.selectedIndex()) GuiComponent.fill(poseStack, bounds.x() + 3, y,
                    bounds.x() + bounds.width() - 3, y + ROW_HEIGHT - 1, theme.colour(SFMColourRole.PANEL_SELECTION));
            String path = SFMRepositoryReviewWorkspaceModel.displayPath(file);
            var presentation = presentations.presentationFor(SFMFileExplorerEntry.file(path,
                    SFMRepositoryReviewPanelSupport.fileName(path)));
            SFMItemIconRenderer.render(minecraft, presentation.itemIcon(), bounds.x() + 7, y + 2);
            SFMRepositoryReviewPanelSupport.renderText(poseStack, minecraft,
                    SFMRepositoryReviewWorkspaceModel.kind(file).toUpperCase(Locale.ROOT) + "  " + path,
                    bounds.x() + 29, y + 6, bounds.width() - 36, presentation.textColour(), index == model.selectedIndex());
        }
        if (files.isEmpty()) SFMRepositoryReviewPanelSupport.renderText(poseStack, minecraft,
                "No changed files match", bounds.x() + 8, bounds.y() + 62, bounds.width() - 16,
                theme.colour(SFMColourRole.TEXT_MUTED), false);
        SFMRepositoryReviewPanelSupport.renderText(poseStack, minecraft,
                model.status(), bounds.x() + 7, bounds.y() + bounds.height() - 17, bounds.width() - 14,
                theme.colour(SFMColourRole.TEXT_ACCENT), false);
    }
}
