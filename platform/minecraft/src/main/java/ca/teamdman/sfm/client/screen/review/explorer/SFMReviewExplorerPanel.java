package ca.teamdman.sfm.client.screen.review.explorer;

import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.text_editor.SFMTextEditorPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelContext;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelIntent;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelIntentResult;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelMetadata;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceSide;
import ca.teamdman.sfm.client.text_editor.SFMTextEditorPanelOpenContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** Explorer-first review surface whose source leaves open through Text Editor v3. */
public final class SFMReviewExplorerPanel implements SFMScreenPanel {
    private static final int ROW_HEIGHT = 18;
    private static final int HEADER_HEIGHT = 37;
    private static final int BACKGROUND = 0xF0202020;
    private static final int HEADER = 0xF02A2A2A;
    private static final int SELECTED = 0xFF264F78;
    private static final int TEXT = 0xFFE8E8E8;
    private static final int MUTED = 0xFFAAAAAA;
    private static final int ERROR = 0xFFFF7777;

    private final SFMReviewExplorerModel model;
    private final String title;
    private @Nullable SFMWorkspacePanelContext hostContext;
    private @Nullable SFMWorkspacePanelId previewSlot;
    private int firstVisibleRow;
    private int listTop;
    private int listBottom;
    private String status = "Space preview · Ctrl+Enter open a new stacked panel";

    public SFMReviewExplorerPanel(String title, SFMReviewExplorerModel model) {
        this.title = title;
        this.model = model;
    }

    public SFMReviewExplorerModel model() { return model; }
    public String status() { return status; }

    @Override public Component title() { return Component.literal(title); }

    @Override
    public Component narration() {
        return Component.literal(title + ". Selected: " + model.selected().label() + ". " + status);
    }

    @Override
    public void opened(Minecraft minecraft, SFMScreenPanelBounds bounds, SFMWorkspacePanelContext context) {
        hostContext = context;
        resize(bounds);
    }

    @Override
    public void resized(Minecraft minecraft, SFMScreenPanelBounds bounds) {
        resize(bounds);
    }

    @Override
    public void closed() {
        hostContext = null;
        previewSlot = null;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> model.selectPrevious();
            case GLFW.GLFW_KEY_DOWN -> model.selectNext();
            case GLFW.GLFW_KEY_HOME -> model.selectFirst();
            case GLFW.GLFW_KEY_END -> model.selectLast();
            case GLFW.GLFW_KEY_RIGHT -> model.expandSelection();
            case GLFW.GLFW_KEY_LEFT -> model.collapseSelectionOrSelectParent();
            case GLFW.GLFW_KEY_SPACE -> openSelected(false);
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> openSelected(
                    (modifiers & GLFW.GLFW_MOD_CONTROL) != 0);
            default -> { return false; }
        }
        keepSelectionVisible();
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || mouseY < listTop || mouseY >= listBottom) return false;
        int index = firstVisibleRow + (int) ((mouseY - listTop) / ROW_HEIGHT);
        if (index < 0 || index >= model.visibleNodes().size()) return true;
        model.select(index);
        if (mouseX < 18 + model.visibleNodes().get(index).depth() * 14) {
            model.toggleSelection();
        } else if (model.selectedLeaf() != null) {
            openSelected(false);
        }
        keepSelectionVisible();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int maxFirst = Math.max(0, model.visibleNodes().size() - visibleRowCount());
        firstVisibleRow = Math.max(0, Math.min(maxFirst, firstVisibleRow + (delta > 0 ? -1 : 1)));
        return true;
    }

    @Override
    public void render(PoseStack poseStack, Minecraft minecraft, SFMScreenPanelBounds bounds,
                       int mouseX, int mouseY, float partialTick, boolean focused) {
        GuiComponent.fill(poseStack, bounds.x(), bounds.y(), bounds.x() + bounds.width(),
                bounds.y() + bounds.height(), BACKGROUND);
        GuiComponent.fill(poseStack, bounds.x(), bounds.y(), bounds.x() + bounds.width(),
                bounds.y() + HEADER_HEIGHT, HEADER);
        int x = bounds.x() + 8;
        SFMFontUtils.draw(poseStack, minecraft.font, title, x, bounds.y() + 6, TEXT, true);
        SFMFontUtils.draw(poseStack, minecraft.font,
                minecraft.font.plainSubstrByWidth(status, Math.max(0, bounds.width() - 16)),
                x, bounds.y() + 20, MUTED, false);

        listTop = bounds.y() + HEADER_HEIGHT;
        listBottom = bounds.y() + bounds.height() - 16;
        var visible = model.visibleNodes();
        int end = Math.min(visible.size(), firstVisibleRow + visibleRowCount());
        for (int index = firstVisibleRow; index < end; index++) {
            SFMReviewExplorerModel.VisibleNode row = visible.get(index);
            int y = listTop + (index - firstVisibleRow) * ROW_HEIGHT;
            if (index == model.selectionIndex()) {
                GuiComponent.fill(poseStack, bounds.x() + 1, y, bounds.x() + bounds.width() - 1,
                        y + ROW_HEIGHT, SELECTED);
            }
            int indent = bounds.x() + 7 + row.depth() * 14;
            String disclosure = row.node().expandable() ? (row.node().expanded() ? "v" : ">") : "·";
            int colour = row.node().leaf() != null && row.node().leaf().missing() ? ERROR : TEXT;
            SFMFontUtils.draw(poseStack, minecraft.font, disclosure, indent, y + 5, colour, true);
            String label = minecraft.font.plainSubstrByWidth(row.node().label(),
                    Math.max(0, bounds.x() + bounds.width() - indent - 14));
            SFMFontUtils.draw(poseStack, minecraft.font, label, indent + 10, y + 5, colour, false);
        }
        SFMFontUtils.draw(poseStack, minecraft.font,
                minecraft.font.plainSubstrByWidth("Selected: " + model.selected().label(),
                        Math.max(0, bounds.width() - 16)),
                x, bounds.y() + bounds.height() - 12, MUTED, false);
    }

    private void openSelected(boolean newStack) {
        SFMReviewExplorerModel.SourceLeaf leaf = model.selectedLeaf();
        if (leaf == null) {
            status = "Selected node is a group; expand it and choose a source leaf";
            return;
        }
        if (leaf.missing()) {
            status = "Cannot open tombstone: " + leaf.path();
            return;
        }
        if (hostContext == null) {
            status = "Preview unavailable: explorer is not hosted";
            return;
        }
        SFMTextEditorPanel preview = SFMTextEditorPanel.textEditorV3(new SFMTextEditorPanelOpenContext(
                "sfm:text_editor_v3", leaf.text(), true, "Review · " + leaf.title()
        ));
        SFMWorkspacePanelMetadata metadata = SFMWorkspacePanelMetadata.explorerPreview(hostContext.panelId().toString());
        SFMWorkspacePanelIntentResult result;
        SFMScreenMultiplexer workspace = hostContext.host() instanceof SFMScreenMultiplexer value ? value : null;
        if (newStack) {
            result = hostContext.submit(new SFMWorkspacePanelIntent.OpenAsTab(preview, metadata));
            if (result == SFMWorkspacePanelIntentResult.APPLIED) status = "Opened new panel: " + leaf.title();
            else status = "Open unavailable: " + result;
            return;
        }
        if (workspace != null && previewSlot != null && workspace.containsPanel(previewSlot)) {
            result = workspace.openIntoSlot(previewSlot, preview, metadata);
        } else {
            result = hostContext.submit(new SFMWorkspacePanelIntent.OpenToSide(
                    SFMWorkspaceSide.RIGHT, preview, metadata));
            if (result == SFMWorkspacePanelIntentResult.APPLIED && workspace != null) {
                previewSlot = workspace.focusedPanelId();
            }
        }
        if (result == SFMWorkspacePanelIntentResult.APPLIED) {
            status = "Preview opened: " + leaf.title();
            if (workspace != null) workspace.focusPanel(hostContext.panelId());
        } else {
            status = "Preview unavailable: " + result;
        }
    }

    private void resize(SFMScreenPanelBounds bounds) {
        listTop = bounds.y() + HEADER_HEIGHT;
        listBottom = bounds.y() + bounds.height() - 16;
        keepSelectionVisible();
    }

    private int visibleRowCount() {
        return Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
    }

    private void keepSelectionVisible() {
        int selected = model.selectionIndex();
        int visible = visibleRowCount();
        if (selected < firstVisibleRow) firstVisibleRow = selected;
        if (selected >= firstVisibleRow + visible) firstVisibleRow = selected - visible + 1;
        firstVisibleRow = Math.max(0, firstVisibleRow);
    }
}
