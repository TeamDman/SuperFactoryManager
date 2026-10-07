package ca.teamdman.sfm.client.screen.file_explorer;

import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.presentation.SFMItemIconRenderer;
import ca.teamdman.sfm.client.presentation.SFMResolvedItemIcon;
import ca.teamdman.sfm.client.screen.workspace.SFMFileDropTarget;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.workspace_panel_reopening %}
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
{% endif %}
{% else %}
{% endcase %}
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelContext;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import com.mojang.blaze3d.vertex.PoseStack;
{% when "26.1.2" %}
import net.minecraft.client.gui.GuiGraphicsExtractor;
{% else %}
import net.minecraft.client.gui.GuiGraphics;
{% endcase %}
import net.minecraft.ChatFormatting;
{% case minecraft_version %}
{% when "26.1.2" %}
import net.minecraft.util.Util;
{% else %}
import net.minecraft.Util;
{% endcase %}
import net.minecraft.client.Minecraft;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import net.minecraft.client.gui.GuiComponent;
{% else %}
{% endcase %}
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
    public static final int ROW_HEIGHT = 18;

    private final SFMFileExplorerModel model;
    private final SFMFilePresentationRegistry presentations;
    private final Consumer<SFMFileExplorerModel.OpenIntent> openIntentConsumer;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    private final boolean recipeBacked;
{% else %}
{% endcase %}
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
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        this(source, openIntentConsumer, SFMFilePresentationRegistry.createDefault(), false);
{% else %}
        this(source, openIntentConsumer, SFMFilePresentationRegistry.createDefault());
{% endcase %}
    }

    public SFMFileExplorerPanel(
            SFMFileExplorerSource source,
            Consumer<SFMFileExplorerModel.OpenIntent> openIntentConsumer,
            SFMFilePresentationRegistry presentations
    ) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        this(source, openIntentConsumer, presentations, false);
    }

    SFMFileExplorerPanel(
            SFMFileExplorerSource source,
            Consumer<SFMFileExplorerModel.OpenIntent> openIntentConsumer,
            SFMFilePresentationRegistry presentations,
            boolean recipeBacked
    ) {
{% else %}
{% endcase %}
        this.model = new SFMFileExplorerModel(source);
        this.openIntentConsumer = openIntentConsumer;
        this.presentations = java.util.Objects.requireNonNull(presentations, "presentations");
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        this.recipeBacked = recipeBacked;
{% else %}
{% endcase %}
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
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            case GLFW.GLFW_KEY_SPACE -> activateSelection(false);
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> activateSelection(
                    (modifiers & GLFW.GLFW_MOD_CONTROL) != 0);
{% else %}
            case GLFW.GLFW_KEY_SPACE -> model.toggleSelection();
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> activateSelection();
{% endcase %}
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
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            activateSelection(false);
{% else %}
            activateSelection();
{% endcase %}
        } else if (lastClickIndex == index && clickTime - lastClickTime <= 300L) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            activateSelection(false);
{% else %}
            activateSelection();
{% endcase %}
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
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.workspace_panel_reopening %}
        updateReopenRecipe(result.replacement());
{% endif %}
{% else %}
{% endcase %}
        firstVisibleRow = 0;
        lastClickIndex = -1;
        keepSelectionVisible();
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.workspace_panel_reopening %}
    private void updateReopenRecipe(SFMFileExplorerSource replacement) {
        if (!recipeBacked || hostContext == null
                || !(hostContext.host() instanceof SFMScreenMultiplexer workspace)) return;
        workspace.setPanelReopenRecipe(
                hostContext.panelId(),
                SFMFileExplorerPanelRecipe.from(replacement).orElse(null));
    }
{% endif %}

{% else %}
{% endcase %}
    @Override
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% else %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
{% endcase %}
    public void render(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            PoseStack poseStack,
{% when "26.1.2" %}
            GuiGraphicsExtractor graphics,
{% else %}
            GuiGraphics graphics,
{% endcase %}
            Minecraft minecraft,
            SFMScreenPanelBounds bounds,
            int mouseX,
            int mouseY,
            float partialTick,
            boolean focused
    ) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        fillRect(poseStack, layout.content(), PANEL);
        fillRect(poseStack, layout.header(), HEADER);
        drawBorder(poseStack, layout.content());
{% else %}
        fillRect(graphics, layout.content(), PANEL);
        fillRect(graphics, layout.header(), HEADER);
        drawBorder(graphics, layout.content());
{% endcase %}
        int inset = layout.compact() ? 4 : 8;
        int titleY = layout.header().y() + (layout.compact() ? 3 : 6);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        SFMFontUtils.draw(poseStack, minecraft.font, title().copy().withStyle(ChatFormatting.BOLD),
{% else %}
        SFMFontUtils.draw(graphics, minecraft.font, title().copy().withStyle(ChatFormatting.BOLD),
{% endcase %}
                layout.header().x() + inset, titleY, TEXT, true);
        if (!layout.compact()) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            SFMFontUtils.draw(poseStack, minecraft.font, "Source: " + model.sourceName() + " (read-only)",
{% else %}
            SFMFontUtils.draw(graphics, minecraft.font, "Source: " + model.sourceName() + " (read-only)",
{% endcase %}
                    layout.header().x() + inset, titleY + 13, MUTED, true);
        }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        renderRows(poseStack, minecraft);
{% else %}
        renderRows(graphics, minecraft);
{% endcase %}
        int statusColour = model.snapshot().state() == SFMFileExplorerSnapshot.State.ERROR
                || statusMessage.startsWith("Drop rejected") ? ERROR : MUTED;
        String status = model.visibleEntries().isEmpty() && statusMessage.equals("Read-only")
                ? model.stateDescription() : statusMessage;
        if (layout.belowMinimum()) status = "Viewport below supported 180x120 minimum; " + status;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        SFMFontUtils.draw(poseStack, minecraft.font,
{% else %}
        SFMFontUtils.draw(graphics, minecraft.font,
{% endcase %}
                trimToWidth(minecraft, status, layout.status().width() - inset * 2),
                layout.status().x() + inset, layout.status().y() + 2, statusColour, true);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        renderIconTooltip(poseStack, minecraft, mouseX, mouseY);
{% else %}
        renderIconTooltip(graphics, minecraft, mouseX, mouseY);
{% endcase %}
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    private void renderRows(PoseStack poseStack, Minecraft minecraft) {
{% when "26.1.2" %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    private void renderRows(GuiGraphicsExtractor graphics, Minecraft minecraft) {
{% else %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    private void renderRows(GuiGraphics graphics, Minecraft minecraft) {
{% endcase %}
        List<SFMFileExplorerModel.VisibleEntry> rows = model.visibleEntries();
        int end = Math.min(rows.size(), firstVisibleRow + visibleRowCount());
        for (int index = firstVisibleRow; index < end; index++) {
            SFMFileExplorerModel.VisibleEntry row = rows.get(index);
            int y = layout.list().y() + (index - firstVisibleRow) * ROW_HEIGHT;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            if (index == model.selectionIndex()) GuiComponent.fill(poseStack, layout.list().x() + 1, y,
{% else %}
            if (index == model.selectionIndex()) graphics.fill(layout.list().x() + 1, y,
{% endcase %}
                    layout.list().x() + layout.list().width() - 1, y + ROW_HEIGHT, SELECTED);
            SFMFileExplorerEntry entry = row.entry();
            SFMFilePresentation presentation = presentations.presentationFor(entry);
            String disclosure = entry.directory() ? (model.isExpanded(entry) ? "v" : ">") : "";
            int disclosureX = layout.list().x() + 4 + row.depth() * 12;
            int iconX = disclosureX + 10;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            SFMFontUtils.draw(poseStack, minecraft.font, disclosure, disclosureX, y + 5,
{% else %}
            SFMFontUtils.draw(graphics, minecraft.font, disclosure, disclosureX, y + 5,
{% endcase %}
                    presentation.textColour(), true);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            SFMItemIconRenderer.render(poseStack, minecraft, presentation.itemIcon(), iconX, y + 1);
{% else %}
            SFMItemIconRenderer.render(graphics, minecraft, presentation.itemIcon(), iconX, y + 1);
{% endcase %}
            String text = entry.name() + " (" + presentation.kindLabel() + ")";
            Style style = Style.EMPTY.withColor(TextColor.fromRgb(presentation.textColour() & 0xFFFFFF));
            style = switch (presentation.emphasis()) {
                case NORMAL -> style;
                case BOLD -> style.withBold(true);
                case ITALIC -> style.withItalic(true);
            };
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            SFMFontUtils.draw(poseStack, minecraft.font,
{% else %}
            SFMFontUtils.draw(graphics, minecraft.font,
{% endcase %}
                    Component.literal(trimToWidth(
                            minecraft,
                            text,
                            layout.list().x() + layout.list().width() - (iconX + SFMItemIconRenderer.SIZE + 8)
                    )).withStyle(style),
                    iconX + SFMItemIconRenderer.SIZE + 4, y + 5, presentation.textColour(), true);
        }
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    private void renderIconTooltip(PoseStack poseStack, Minecraft minecraft, int mouseX, int mouseY) {
{% when "26.1.2" %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    private void renderIconTooltip(GuiGraphicsExtractor graphics, Minecraft minecraft, int mouseX, int mouseY) {
{% else %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    private void renderIconTooltip(GuiGraphics graphics, Minecraft minecraft, int mouseX, int mouseY) {
{% endcase %}
        if (minecraft.screen == null || !layout.list().contains(mouseX, mouseY)) return;
        int index = firstVisibleRow + (mouseY - layout.list().y()) / ROW_HEIGHT;
        if (index < 0 || index >= model.visibleEntries().size()) return;
        SFMFileExplorerModel.VisibleEntry row = model.visibleEntries().get(index);
        int iconX = layout.list().x() + 14 + row.depth() * 12;
        int iconY = layout.list().y() + (index - firstVisibleRow) * ROW_HEIGHT + 1;
        if (mouseX < iconX || mouseX >= iconX + SFMItemIconRenderer.SIZE
                || mouseY < iconY || mouseY >= iconY + SFMItemIconRenderer.SIZE) return;
        SFMFilePresentation presentation = presentations.presentationFor(row.entry());
        SFMResolvedItemIcon resolved = ca.teamdman.sfm.client.presentation.SFMItemIconResolver.resolve(
                presentation.itemIcon()
        );
        String fallback = resolved.usedFallback() ? " (using fallback item)" : "";
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        minecraft.screen.renderTooltip(
                poseStack,
{% when "26.1.2" %}
        graphics.setTooltipForNextFrame(
                minecraft.font,
{% else %}
        graphics.renderTooltip(
                minecraft.font,
{% endcase %}
                Component.literal(resolved.accessibleLabel() + fallback),
                mouseX,
                mouseY
        );
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    private void activateSelection(boolean focusPreview) {
{% else %}
    private void activateSelection() {
{% endcase %}
        model.selection().map(SFMFileExplorerModel.VisibleEntry::entry).ifPresent(entry -> {
            if (!entry.directory() && !presentations.isTextLike(entry)) {
                statusMessage = "Preview unavailable: " + entry.path() + " is not text-like";
                return;
            }
            model.activateSelection().ifPresent(intent -> {
                statusMessage = "Open requested: " + intent.entry().path() + " (read-only)";
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                openIntentConsumer.accept(new SFMFileExplorerModel.OpenIntent(
                        intent.sourceName(), intent.entry(), focusPreview));
{% else %}
                openIntentConsumer.accept(intent);
{% endcase %}
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

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    private static void fillRect(PoseStack poseStack, SFMFileExplorerLayout.Rect rect, int colour) {
        GuiComponent.fill(poseStack, rect.x(), rect.y(), rect.x() + rect.width(), rect.y() + rect.height(), colour);
{% when "26.1.2" %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    private static void fillRect(GuiGraphicsExtractor graphics, SFMFileExplorerLayout.Rect rect, int colour) {
        graphics.fill(rect.x(), rect.y(), rect.x() + rect.width(), rect.y() + rect.height(), colour);
{% else %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    private static void fillRect(GuiGraphics graphics, SFMFileExplorerLayout.Rect rect, int colour) {
        graphics.fill(rect.x(), rect.y(), rect.x() + rect.width(), rect.y() + rect.height(), colour);
{% endcase %}
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    private static void drawBorder(PoseStack poseStack, SFMFileExplorerLayout.Rect rect) {
{% when "26.1.2" %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    private static void drawBorder(GuiGraphicsExtractor graphics, SFMFileExplorerLayout.Rect rect) {
{% else %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    private static void drawBorder(GuiGraphics graphics, SFMFileExplorerLayout.Rect rect) {
{% endcase %}
        int right = rect.x() + rect.width();
        int bottom = rect.y() + rect.height();
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        GuiComponent.fill(poseStack, rect.x(), rect.y(), right, rect.y() + 1, BORDER);
        GuiComponent.fill(poseStack, rect.x(), bottom - 1, right, bottom, BORDER);
        GuiComponent.fill(poseStack, rect.x(), rect.y(), rect.x() + 1, bottom, BORDER);
        GuiComponent.fill(poseStack, right - 1, rect.y(), right, bottom, BORDER);
{% else %}
        graphics.fill(rect.x(), rect.y(), right, rect.y() + 1, BORDER);
        graphics.fill(rect.x(), bottom - 1, right, bottom, BORDER);
        graphics.fill(rect.x(), rect.y(), rect.x() + 1, bottom, BORDER);
        graphics.fill(right - 1, rect.y(), right, bottom, BORDER);
{% endcase %}
    }
}
