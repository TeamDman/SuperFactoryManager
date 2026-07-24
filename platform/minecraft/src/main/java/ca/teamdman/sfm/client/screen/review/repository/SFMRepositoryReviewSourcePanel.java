package ca.teamdman.sfm.client.screen.review.repository;

import ca.teamdman.sfm.client.screen.review.comment.SFMReviewCommentDataSource;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.theme.SFMClientThemeService;
import ca.teamdman.sfm.client.theme.SFMColourRole;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class SFMRepositoryReviewSourcePanel implements SFMScreenPanel {
    private static final int LINE_HEIGHT = 14;
    private final SFMRepositoryReviewWorkspaceModel model;
    private final SFMReviewCommentDataSource.Side side;
    private SFMScreenPanelBounds lastBounds = new SFMScreenPanelBounds(0, 0, 0, 0);

    public SFMRepositoryReviewSourcePanel(SFMRepositoryReviewWorkspaceModel model, SFMReviewCommentDataSource.Side side) {
        this.model = model;
        this.side = side;
    }
    public SFMRepositoryReviewWorkspaceModel model() { return model; }
    public SFMReviewCommentDataSource.Side side() { return side; }
    @Override public Component title() { return Component.literal(side == SFMReviewCommentDataSource.Side.BEFORE ? "Before source" : "After source"); }
    @Override public Component narration() { return Component.literal(model.status()); }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_N) { model.beginComment(); return true; }
        if (key == GLFW.GLFW_KEY_LEFT && modifiers == 0) { model.scrollHorizontal(-4); return true; }
        if (key == GLFW.GLFW_KEY_RIGHT && modifiers == 0) { model.scrollHorizontal(4); return true; }
        if (key == GLFW.GLFW_KEY_UP) { model.scrollSource(-1); return true; }
        if (key == GLFW.GLFW_KEY_DOWN) { model.scrollSource(1); return true; }
        if (key == GLFW.GLFW_KEY_BACKSPACE) { model.show(SFMRepositoryReviewWorkspaceModel.Blade.FILES); return true; }
        return false;
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        model.scrollSource(delta > 0 ? -3 : 3);
        return true;
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        model.show(side == SFMReviewCommentDataSource.Side.BEFORE
                ? SFMRepositoryReviewWorkspaceModel.Blade.BEFORE
                : SFMRepositoryReviewWorkspaceModel.Blade.AFTER);
        int line = model.sourceScroll() + (int) (mouseY - lastBounds.y() - 58) / LINE_HEIGHT;
        return model.selectSourceLine(side, line);
    }

    @Override public void render(PoseStack poseStack, Minecraft minecraft, SFMScreenPanelBounds bounds,
                                 int mouseX, int mouseY, float partialTick, boolean focused) {
        lastBounds = bounds;
        var theme = SFMClientThemeService.active();
        GuiComponent.fill(poseStack, bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(),
                theme.colour(SFMColourRole.PANEL_BACKGROUND));
        var document = model.document(side);
        int accent = side == SFMReviewCommentDataSource.Side.BEFORE ? 0xFFFF7777 : 0xFF55FFFF;
        String label = side.name() + " SOURCE";
        SFMRepositoryReviewPanelSupport.renderText(poseStack, minecraft, label + " · Ctrl+M maximize · N comment",
                bounds.x() + 7, bounds.y() + 7, bounds.width() - 14, accent, true);
        if (document == null) {
            SFMRepositoryReviewPanelSupport.renderText(poseStack, minecraft, "No " + side.name().toLowerCase(Locale.ROOT)
                            + " document for this change", bounds.x() + 7, bounds.y() + 30, bounds.width() - 14,
                    theme.colour(SFMColourRole.TEXT_MUTED), false);
            return;
        }
        SFMRepositoryReviewPanelSupport.renderText(poseStack, minecraft, document.path(), bounds.x() + 7,
                bounds.y() + 21, bounds.width() - 14, theme.colour(SFMColourRole.TEXT_PRIMARY), false);
        SFMRepositoryReviewPanelSupport.renderText(poseStack, minecraft,
                "scroll " + (model.sourceScroll() + 1) + " · horizontal +" + model.horizontalScroll()
                        + " · Backspace files", bounds.x() + 7, bounds.y() + 35, bounds.width() - 14,
                theme.colour(SFMColourRole.TEXT_MUTED), false);
        String[] lines = document.text().split("\\n", -1);
        int y = bounds.y() + 55;
        for (int line = model.sourceScroll(); line < lines.length && y + LINE_HEIGHT < bounds.y() + bounds.height() - 26;
             line++, y += LINE_HEIGHT) {
            int start = SFMRepositoryReviewWorkspaceModel.lineByteStart(lines, line);
            int end = start + lines[line].getBytes(StandardCharsets.UTF_8).length;
            boolean selected = model.selectedRange() != null
                    && model.selectedRange().documentRevisionId().equals(document.id())
                    && model.selectedRange().startByte() == start && model.selectedRange().endByte() == end;
            boolean changed = model.commentsFor(document.id()).stream()
                    .filter(comment -> !comment.provenance().startsWith("human"))
                    .flatMap(comment -> comment.ranges().stream())
                    .anyMatch(range -> range.documentRevisionId().equals(document.id())
                            && range.startByte() < Math.max(start + 1, end) && range.endByte() > start);
            if (changed) GuiComponent.fill(poseStack, bounds.x() + 3, y - 2, bounds.x() + bounds.width() - 3,
                    y + LINE_HEIGHT - 1, side == SFMReviewCommentDataSource.Side.BEFORE ? 0x55441111 : 0x55336677);
            if (selected) GuiComponent.fill(poseStack, bounds.x() + 3, y - 2, bounds.x() + bounds.width() - 3,
                    y + LINE_HEIGHT - 1, 0x885588CC);
            String content = lines[line];
            if (model.horizontalScroll() < content.length()) content = content.substring(model.horizontalScroll());
            else content = "";
            SFMRepositoryReviewPanelSupport.renderText(poseStack, minecraft,
                    String.format("%4d %s%s", line + 1, changed ? "Δ " : "  ", content), bounds.x() + 5, y,
                    bounds.width() - 10, changed ? accent : theme.colour(SFMColourRole.TEXT_PRIMARY), selected || changed);
        }
        SFMRepositoryReviewPanelSupport.renderText(poseStack, minecraft,
                model.status(), bounds.x() + 7, bounds.y() + bounds.height() - 17, bounds.width() - 14,
                theme.colour(SFMColourRole.TEXT_ACCENT), false);
    }
}
