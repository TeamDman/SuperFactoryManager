package ca.teamdman.sfm.client.screen.file_explorer;

import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.workspace.SFMFileDropTarget;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/** Composable read-only explorer content; full-screen hosting is only a compatibility wrapper. */
public final class SFMFileExplorerPanel implements SFMScreenPanel, SFMFileDropTarget {
    private static final int PANEL = 0xF0202020;
    private static final int HEADER = 0xF02A2A2A;
    private static final int BORDER = 0xFF606060;
    private static final int TEXT = 0xFFE8E8E8;
    private static final int MUTED = 0xFFAAAAAA;
    private static final int SELECTED = 0xFF264F78;
    private static final int ERROR = 0xFFFF7777;
    public static final int ROW_HEIGHT = 13;

    private final SFMFileExplorerModel model;
    private final SFMFilePresentationRegistry presentations = SFMFilePresentationRegistry.createDefault();
    private final Consumer<SFMFileExplorerModel.OpenIntent> openIntentConsumer;
    private SFMFileExplorerLayout layout = SFMFileExplorerLayout.calculate(0, 0, 1, 1);
    private @Nullable SFMWorkspacePanelContext hostContext;
    private int firstVisibleRow;
    private long lastClickTime;
    private int lastClickIndex = -1;
    private String statusMessage = "Read-only";
    private boolean loaded;

    public SFMFileExplorerPanel(
            SFMFileExplorerSource source,
            Consumer<SFMFileExplorerModel.OpenIntent> openIntentConsumer
    ) {
        this.model = new SFMFileExplorerModel(source);
        this.openIntentConsumer = openIntentConsumer;
    }

    @Override
    public Component title() {
        return Component.literal("SFM File Explorer");
    }

    @Override
    public Component narration() {
        return Component.literal(title().getString() + ". " + statusMessage + ". "
                + model.selectedNarration(presentations));
    }

    @Override
    public void opened(Minecraft minecraft, SFMScreenPanelBounds bounds, SFMWorkspacePanelContext context) {
        hostContext = context;
        resize(bounds);
        if (!loaded) {
            loaded = true;
            model.reload();
        }
        keepSelectionVisible();
    }

    @Override
    public void resized(Minecraft minecraft, SFMScreenPanelBounds bounds) {
        resize(bounds);
        keepSelectionVisible();
    }

    @Override
    public void closed() {
        hostContext = null;
    }

    public void acceptSnapshot(SFMFileExplorerSnapshot snapshot) {
        model.setSnapshot(snapshot);
        statusMessage = "Read-only";
        firstVisibleRow = 0;
        keepSelectionVisible();
    }

    public SFMFileExplorerModel model() {
        return model;
    }

    public @Nullable SFMWorkspacePanelContext hostContext() {
        return hostContext;
    }

    public void setStatusMessage(String message) {
        statusMessage = message;
    }

    public String statusMessage() {
        return statusMessage;
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
            case GLFW.GLFW_KEY_SPACE -> model.toggleSelection();
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> activateSelection();
            default -> { return false; }
        }
        keepSelectionVisible();
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !layout.list().contains(mouseX, mouseY)) return false;
        int index = firstVisibleRow + (int) ((mouseY - layout.list().y()) / ROW_HEIGHT);
        if (index < 0 || index >= model.visibleEntries().size()) return true;
        model.select(index);
        SFMFileExplorerEntry selected = model.visibleEntries().get(index).entry();
        long clickTime = Util.getMillis();
        if (!selected.directory() && presentations.isTextLike(selected)) {
            activateSelection();
        } else if (lastClickIndex == index && clickTime - lastClickTime <= 300L) {
            activateSelection();
        }
        lastClickIndex = index;
        lastClickTime = clickTime;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int maxFirstRow = Math.max(0, model.visibleEntries().size() - visibleRowCount());
        firstVisibleRow = Math.max(0, Math.min(maxFirstRow, firstVisibleRow + (delta > 0 ? -1 : 1)));
        return true;
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        SFMFileExplorerDropResult result = SFMFileExplorerDropPolicy.evaluate(paths);
        statusMessage = result.message();
        if (!result.accepted()) return;
        model.replaceSource(result.replacement());
        firstVisibleRow = 0;
        lastClickIndex = -1;
        keepSelectionVisible();
    }

    @Override
    public void render(
            PoseStack poseStack,
            Minecraft minecraft,
            SFMScreenPanelBounds bounds,
            int mouseX,
            int mouseY,
            float partialTick,
            boolean focused
    ) {
        fillRect(poseStack, layout.content(), PANEL);
        fillRect(poseStack, layout.header(), HEADER);
        drawBorder(poseStack, layout.content());
        int inset = layout.compact() ? 4 : 8;
        int titleY = layout.header().y() + (layout.compact() ? 3 : 6);
        SFMFontUtils.draw(poseStack, minecraft.font, title().copy().withStyle(ChatFormatting.BOLD),
                layout.header().x() + inset, titleY, TEXT, true);
        if (!layout.compact()) {
            SFMFontUtils.draw(poseStack, minecraft.font, "Source: " + model.sourceName() + " (read-only)",
                    layout.header().x() + inset, titleY + 13, MUTED, true);
        }
        renderRows(poseStack, minecraft);
        int statusColour = model.snapshot().state() == SFMFileExplorerSnapshot.State.ERROR
                || statusMessage.startsWith("Drop rejected") ? ERROR : MUTED;
        String status = model.visibleEntries().isEmpty() && statusMessage.equals("Read-only")
                ? model.stateDescription() : statusMessage;
        if (layout.belowMinimum()) status = "Viewport below supported 180x120 minimum; " + status;
        SFMFontUtils.draw(poseStack, minecraft.font,
                trimToWidth(minecraft, status, layout.status().width() - inset * 2),
                layout.status().x() + inset, layout.status().y() + 2, statusColour, true);
    }

    private void renderRows(PoseStack poseStack, Minecraft minecraft) {
        List<SFMFileExplorerModel.VisibleEntry> rows = model.visibleEntries();
        int end = Math.min(rows.size(), firstVisibleRow + visibleRowCount());
        for (int index = firstVisibleRow; index < end; index++) {
            SFMFileExplorerModel.VisibleEntry row = rows.get(index);
            int y = layout.list().y() + (index - firstVisibleRow) * ROW_HEIGHT;
            if (index == model.selectionIndex()) GuiComponent.fill(poseStack, layout.list().x() + 1, y,
                    layout.list().x() + layout.list().width() - 1, y + ROW_HEIGHT, SELECTED);
            SFMFileExplorerEntry entry = row.entry();
            SFMFilePresentation presentation = presentations.presentationFor(entry);
            String disclosure = entry.directory() ? (model.isExpanded(entry) ? "v " : "> ") : "  ";
            String text = "  ".repeat(row.depth()) + disclosure + presentation.icon() + " " + entry.name()
                    + " (" + presentation.kindLabel() + ")";
            Style style = Style.EMPTY.withColor(TextColor.fromRgb(presentation.textColour() & 0xFFFFFF));
            style = switch (presentation.emphasis()) {
                case NORMAL -> style;
                case BOLD -> style.withBold(true);
                case ITALIC -> style.withItalic(true);
            };
            SFMFontUtils.draw(poseStack, minecraft.font,
                    Component.literal(trimToWidth(minecraft, text, layout.list().width() - 8)).withStyle(style),
                    layout.list().x() + 4, y + 2, presentation.textColour(), true);
        }
    }

    private void activateSelection() {
        model.selection().map(SFMFileExplorerModel.VisibleEntry::entry).ifPresent(entry -> {
            if (!entry.directory() && !presentations.isTextLike(entry)) {
                statusMessage = "Preview unavailable: " + entry.path() + " is not text-like";
                return;
            }
            model.activateSelection().ifPresent(intent -> {
                statusMessage = "Open requested: " + intent.entry().path() + " (read-only)";
                openIntentConsumer.accept(intent);
            });
        });
    }

    private void resize(SFMScreenPanelBounds bounds) {
        layout = SFMFileExplorerLayout.calculate(bounds.x(), bounds.y(), bounds.width(), bounds.height());
    }

    private void keepSelectionVisible() {
        int selected = model.selectionIndex();
        int visibleRows = visibleRowCount();
        if (selected < firstVisibleRow) firstVisibleRow = selected;
        if (selected >= firstVisibleRow + visibleRows) firstVisibleRow = selected - visibleRows + 1;
        firstVisibleRow = Math.max(0, firstVisibleRow);
    }

    private int visibleRowCount() { return Math.max(1, layout.list().height() / ROW_HEIGHT); }

    private static String trimToWidth(Minecraft minecraft, String value, int availableWidth) {
        if (availableWidth <= 0) return "";
        if (minecraft.font.width(value) <= availableWidth) return value;
        String suffix = "...";
        return minecraft.font.plainSubstrByWidth(value,
                Math.max(0, availableWidth - minecraft.font.width(suffix))) + suffix;
    }

    private static void fillRect(PoseStack poseStack, SFMFileExplorerLayout.Rect rect, int colour) {
        GuiComponent.fill(poseStack, rect.x(), rect.y(), rect.x() + rect.width(), rect.y() + rect.height(), colour);
    }

    private static void drawBorder(PoseStack poseStack, SFMFileExplorerLayout.Rect rect) {
        int right = rect.x() + rect.width();
        int bottom = rect.y() + rect.height();
        GuiComponent.fill(poseStack, rect.x(), rect.y(), right, rect.y() + 1, BORDER);
        GuiComponent.fill(poseStack, rect.x(), bottom - 1, right, bottom, BORDER);
        GuiComponent.fill(poseStack, rect.x(), rect.y(), rect.x() + 1, bottom, BORDER);
        GuiComponent.fill(poseStack, right - 1, rect.y(), right, bottom, BORDER);
    }
}
