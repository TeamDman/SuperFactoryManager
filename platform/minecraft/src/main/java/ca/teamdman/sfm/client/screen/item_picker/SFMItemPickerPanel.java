package ca.teamdman.sfm.client.screen.item_picker;

import ca.teamdman.sfm.client.presentation.SFMItemIcon;
import ca.teamdman.sfm.client.presentation.SFMItemIconRenderer;
import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelContext;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelIntent;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Searchable ItemStack picker content that can fill a Screen or one multiplexer panel. */
public final class SFMItemPickerPanel implements SFMScreenPanel {
    private static final int PANEL = 0xF0202020;
    private static final int HEADER = 0xF02A2A2A;
    private static final int BORDER = 0xFF606060;
    private static final int TEXT = 0xFFE8E8E8;
    private static final int MUTED = 0xFFAAAAAA;
    private static final int SELECTED = 0xFF264F78;
    private static final int HOVERED = 0xFF343434;
    private static final int ERROR = 0xFFFF7777;
    private static final int SUCCESS = 0xFF72D572;

    private final SFMItemPickerModel model;
    private final Consumer<SFMItemIcon> onConfirm;
    private final Runnable onCancel;
    private SFMItemPickerLayout layout = SFMItemPickerLayout.calculate(0, 0, 1, 1);
    private int firstVisibleRow;
    private int mouseX;
    private int mouseY;
    private boolean completed;
    private SFMWorkspacePanelContext hostContext;
    private SFMScreenPanelBounds currentBounds = new SFMScreenPanelBounds(0, 0, 1, 1);
    private int automationTooltipIndex = -1;

    public SFMItemPickerPanel(
            List<SFMItemPickerEntry> entries,
            SFMItemIcon current,
            Consumer<SFMItemIcon> onConfirm,
            Runnable onCancel
    ) {
        this.model = new SFMItemPickerModel(entries, current);
        this.onConfirm = Objects.requireNonNull(onConfirm, "onConfirm");
        this.onCancel = Objects.requireNonNull(onCancel, "onCancel");
    }

    public static SFMItemPickerPanel fromRegistry(
            SFMItemIcon current,
            Consumer<SFMItemIcon> onConfirm,
            Runnable onCancel
    ) {
        return new SFMItemPickerPanel(SFMItemPickerRegistryEntries.load(), current, onConfirm, onCancel);
    }

    public SFMItemPickerModel model() { return model; }
    public SFMItemPickerLayout layout() { return layout; }

    @Override
    public Component title() { return Component.literal("SFM Item Icon Picker"); }

    @Override
    public Component narration() { return Component.literal(model.narration()); }

    @Override
    public void opened(Minecraft minecraft, SFMScreenPanelBounds bounds, SFMWorkspacePanelContext context) {
        hostContext = context;
        resize(bounds);
    }

    @Override
    public void resized(Minecraft minecraft, SFMScreenPanelBounds bounds) { resize(bounds); }

    @Override
    public void closed() { hostContext = null; }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT -> model.move(-1, 0, layout.columns());
            case GLFW.GLFW_KEY_RIGHT -> model.move(1, 0, layout.columns());
            case GLFW.GLFW_KEY_UP -> model.move(0, -1, layout.columns());
            case GLFW.GLFW_KEY_DOWN -> model.move(0, 1, layout.columns());
            case GLFW.GLFW_KEY_HOME -> model.selectFirst();
            case GLFW.GLFW_KEY_END -> model.selectLast();
            case GLFW.GLFW_KEY_BACKSPACE -> model.deleteQueryCharacter();
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> confirm();
            case GLFW.GLFW_KEY_ESCAPE -> cancel();
            case GLFW.GLFW_KEY_R -> {
                if ((modifiers & GLFW.GLFW_MOD_CONTROL) == 0) return false;
                model.resetToFallback();
            }
            case GLFW.GLFW_KEY_G -> {
                if ((modifiers & GLFW.GLFW_MOD_CONTROL) == 0) return false;
                model.toggleViewMode();
                recalculateLayout();
            }
            default -> { return false; }
        }
        keepSelectionVisible();
        return true;
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (character < 32 || character == 127) return false;
        model.appendQuery(character);
        keepSelectionVisible();
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        int hit = itemIndexAt(mouseX, mouseY);
        if (hit >= 0) {
            model.select(hit);
            keepSelectionVisible();
            return true;
        }
        SFMItemPickerLayout.Rect footer = layout.footer();
        if (!footer.contains(mouseX, mouseY)) return false;
        if (layout.compact()) {
            boolean topRow = mouseY < footer.y() + footer.height() / 2D;
            boolean leftColumn = mouseX < footer.x() + footer.width() / 2D;
            if (topRow && leftColumn) model.resetToFallback();
            else if (topRow) {
                model.toggleViewMode();
                recalculateLayout();
            } else if (leftColumn) cancel();
            else confirm();
        } else {
            int quarter = Math.max(1, footer.width() / 4);
            if (mouseX < footer.x() + quarter) model.resetToFallback();
            else if (mouseX < footer.x() + quarter * 2) {
                model.toggleViewMode();
                recalculateLayout();
            } else if (mouseX < footer.x() + quarter * 3) cancel();
            else confirm();
        }
        keepSelectionVisible();
        return true;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        this.mouseX = (int) mouseX;
        this.mouseY = (int) mouseY;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int maxFirst = Math.max(0, totalRows() - visibleRows());
        firstVisibleRow = Math.max(0, Math.min(maxFirst, firstVisibleRow + (delta > 0 ? -1 : 1)));
        return true;
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
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        fill(poseStack, layout.content(), PANEL);
        fill(poseStack, layout.header(), HEADER);
        border(poseStack, layout.content(), focused ? 0xFF55FFFF : BORDER);
        int inset = layout.compact() ? 5 : 9;
        SFMFontUtils.draw(poseStack, minecraft.font, title().copy().withStyle(ChatFormatting.BOLD),
                layout.header().x() + inset, layout.header().y() + 7, TEXT, true);
        if (!layout.compact()) {
            String count = model.filtered().size() + " / " + model.entries().size() + " items";
            SFMFontUtils.draw(poseStack, minecraft.font, count,
                    layout.header().x() + layout.header().width() - minecraft.font.width(count) - inset,
                    layout.header().y() + 7, MUTED, true);
        }
        renderSearch(poseStack, minecraft, inset);
        renderItems(poseStack, minecraft);
        renderPreview(poseStack, minecraft);
        renderFooter(poseStack, minecraft);
        renderTooltip(poseStack, minecraft);
    }

    public void setQueryForAutomation(String query) {
        automationTooltipIndex = -1;
        model.setQuery(query);
        keepSelectionVisible();
    }

    public void pressForAutomation(int keyCode, int modifiers) {
        if (!keyPressed(keyCode, 0, modifiers)) throw new IllegalStateException("Picker rejected key " + keyCode);
    }

    public void showUnavailableForAutomation(ResourceLocation itemId) {
        automationTooltipIndex = -1;
        model.showUnavailable(itemId);
        keepSelectionVisible();
    }

    public void showSelectionTooltipForAutomation() {
        automationTooltipIndex = model.selectionIndex();
        keepSelectionVisible();
    }

    private void renderSearch(PoseStack poseStack, Minecraft minecraft, int inset) {
        fill(poseStack, layout.search(), 0xFF101010);
        border(poseStack, layout.search(), 0xFF8A8A8A);
        String query = model.query();
        Component value = query.isEmpty()
                ? Component.literal("Search by item name or registry id...").withStyle(ChatFormatting.DARK_GRAY)
                : Component.literal(query + "_");
        SFMFontUtils.draw(poseStack, minecraft.font, value,
                layout.search().x() + inset, layout.search().y() + 7, TEXT, true);
    }

    private void renderItems(PoseStack poseStack, Minecraft minecraft) {
        List<SFMItemPickerEntry> entries = model.filtered();
        int start = firstVisibleRow * layout.columns();
        int end = Math.min(entries.size(), (firstVisibleRow + visibleRows()) * layout.columns());
        for (int index = start; index < end; index++) {
            int visible = index - start;
            int column = visible % layout.columns();
            int row = visible / layout.columns();
            int x = layout.results().x() + column * layout.cellWidth();
            int y = layout.results().y() + row * layout.cellHeight();
            SFMItemPickerLayout.Rect cell = new SFMItemPickerLayout.Rect(
                    x, y, layout.cellWidth(), layout.cellHeight()
            );
            if (index == model.selectionIndex()) fill(poseStack, cell, SELECTED);
            else if (cell.contains(mouseX, mouseY)) fill(poseStack, cell, HOVERED);
            border(poseStack, cell, 0xFF3A3A3A);
            SFMItemPickerEntry entry = entries.get(index);
            if (model.viewMode() == SFMItemPickerModel.ViewMode.DENSE_ICONS) {
                SFMItemIconRenderer.render(minecraft, entry.toIcon(model.fallbackItem()), x + 3, y + 3);
                continue;
            }
            SFMItemIconRenderer.render(minecraft, entry.toIcon(model.fallbackItem()), x + 5, y + 8);
            int textX = x + 26;
            int available = Math.max(1, layout.cellWidth() - 30);
            SFMFontUtils.draw(poseStack, minecraft.font,
                    trim(minecraft, entry.accessibleName(), available), textX, y + 6, TEXT, true);
            SFMFontUtils.draw(poseStack, minecraft.font,
                    trim(minecraft, entry.itemId().toString(), available), textX, y + 18, MUTED, true);
        }
        if (entries.isEmpty()) {
            SFMFontUtils.draw(poseStack, minecraft.font, "No matching registry items",
                    layout.results().x() + 8, layout.results().y() + 10, ERROR, true);
        }
    }

    private void renderPreview(PoseStack poseStack, Minecraft minecraft) {
        if (layout.preview().width() <= 0) return;
        if (layout.compact()) {
            fill(poseStack, layout.preview(), 0xF0282828);
            border(poseStack, layout.preview(), BORDER);
            int x = layout.preview().x() + 5;
            int y = layout.preview().y() + 3;
            model.selection().ifPresent(entry -> {
                if (!model.diagnostic().isEmpty()) {
                    int lineY = y;
                    for (var line : minecraft.font.split(
                            Component.literal(model.diagnostic()),
                            layout.preview().width() - 10
                    )) {
                        SFMFontUtils.draw(poseStack, minecraft.font, line, x, lineY, ERROR, true);
                        lineY += minecraft.font.lineHeight;
                        if (lineY >= layout.preview().y() + layout.preview().height() - 2) break;
                    }
                    return;
                }
                SFMItemIconRenderer.render(minecraft, entry.toIcon(model.fallbackItem()), x, y + 1);
                SFMFontUtils.draw(poseStack, minecraft.font,
                        trim(minecraft, "Current: " + entry.accessibleName(), layout.preview().width() - 28),
                        x + 22, y, TEXT, true);
                SFMFontUtils.draw(poseStack, minecraft.font,
                        trim(minecraft, entry.itemId().toString(), layout.preview().width() - 28),
                        x + 22, y + minecraft.font.lineHeight, MUTED, true);
            });
            return;
        }
        fill(poseStack, layout.preview(), 0xF0282828);
        border(poseStack, layout.preview(), BORDER);
        int x = layout.preview().x() + 10;
        int y = layout.preview().y() + 10;
        SFMFontUtils.draw(poseStack, minecraft.font, "Current selection", x, y, SUCCESS, true);
        model.selection().ifPresent(entry -> {
            SFMItemIconRenderer.render(minecraft, entry.toIcon(model.fallbackItem()), x, y + 18);
            SFMFontUtils.draw(poseStack, minecraft.font,
                    trim(minecraft, entry.accessibleName(), layout.preview().width() - 38),
                    x + 22, y + 22, TEXT, true);
            SFMFontUtils.draw(poseStack, minecraft.font,
                    trim(minecraft, entry.itemId().toString(), layout.preview().width() - 20),
                    x, y + 44, MUTED, true);
            SFMFontUtils.draw(poseStack, minecraft.font,
                    "Fallback: " + model.fallbackItem(), x, y + 60, MUTED, true);
        });
        String diagnostic = model.diagnostic();
        if (!diagnostic.isEmpty()) {
            int lineY = y + 84;
            for (var line : minecraft.font.split(Component.literal(diagnostic), layout.preview().width() - 20)) {
                SFMFontUtils.draw(poseStack, minecraft.font, line, x, lineY, ERROR, true);
                lineY += minecraft.font.lineHeight;
                if (lineY > y + 120) break;
            }
        }
        SFMFontUtils.draw(poseStack, minecraft.font,
                trim(minecraft, model.interaction(), layout.preview().width() - 20),
                x, Math.max(y + 104, layout.preview().y() + layout.preview().height() - 18), MUTED, true);
    }

    private void renderFooter(PoseStack poseStack, Minecraft minecraft) {
        fill(poseStack, layout.footer(), HEADER);
        String toggle = model.viewMode() == SFMItemPickerModel.ViewMode.DETAILED
                ? "Ctrl+G Grid" : "Ctrl+G List";
        if (layout.compact()) {
            int half = Math.max(1, layout.footer().width() / 2);
            int topY = layout.footer().y() + 2;
            int bottomY = layout.footer().y() + 16;
            drawCentered(poseStack, minecraft, "Ctrl+R Reset", layout.footer().x(), half, topY, MUTED);
            drawCentered(poseStack, minecraft, toggle, layout.footer().x() + half,
                    layout.footer().width() - half, topY, MUTED);
            drawCentered(poseStack, minecraft, "Esc Cancel", layout.footer().x(), half, bottomY, MUTED);
            drawCentered(poseStack, minecraft, "Enter Confirm", layout.footer().x() + half,
                    layout.footer().width() - half, bottomY, SUCCESS);
        } else {
            int quarter = Math.max(1, layout.footer().width() / 4);
            int y = layout.footer().y() + 5;
            drawCentered(poseStack, minecraft, "Ctrl+R Reset", layout.footer().x(), quarter, y, MUTED);
            drawCentered(poseStack, minecraft, toggle, layout.footer().x() + quarter, quarter, y, MUTED);
            drawCentered(poseStack, minecraft, "Esc Cancel",
                    layout.footer().x() + quarter * 2, quarter, y, MUTED);
            drawCentered(poseStack, minecraft, "Enter Confirm", layout.footer().x() + quarter * 3,
                    layout.footer().width() - quarter * 3, y, SUCCESS);
            drawCentered(poseStack, minecraft, "Arrow keys navigate • typing filters",
                    layout.footer().x(), layout.footer().width(), y + 14, MUTED);
        }
        if (layout.belowMinimum()) {
            SFMFontUtils.draw(poseStack, minecraft.font, "Viewport below 140x150 minimum",
                    layout.footer().x() + 4, layout.footer().y() - 11, ERROR, true);
        }
    }

    private void renderTooltip(PoseStack poseStack, Minecraft minecraft) {
        int index = automationTooltipIndex >= 0 ? automationTooltipIndex : itemIndexAt(mouseX, mouseY);
        if (minecraft.screen == null) return;
        if (index < 0) {
            if (!layout.preview().contains(mouseX, mouseY)) return;
            model.selection().ifPresent(entry -> {
                java.util.ArrayList<Component> lines = new java.util.ArrayList<>();
                lines.add(Component.literal(entry.accessibleName()).withStyle(ChatFormatting.AQUA));
                lines.add(Component.literal(entry.itemId().toString()).withStyle(ChatFormatting.GRAY));
                if (!model.diagnostic().isEmpty()) {
                    lines.add(Component.literal(model.diagnostic()).withStyle(ChatFormatting.RED));
                }
                minecraft.screen.renderComponentTooltip(poseStack, lines, mouseX, mouseY);
            });
            return;
        }
        SFMItemPickerEntry entry = model.filtered().get(index);
        int tooltipX = mouseX;
        int tooltipY = mouseY;
        if (automationTooltipIndex >= 0) {
            SFMItemPickerLayout.Rect cell = cellForIndex(index);
            tooltipX = cell.x() + cell.width() / 2;
            tooltipY = cell.y() + cell.height() / 2;
        }
        List<String> details = entry.accessibleDetails();
        java.util.ArrayList<Component> lines = new java.util.ArrayList<>();
        lines.add(Component.literal(details.get(0)).withStyle(ChatFormatting.AQUA));
        lines.add(Component.literal(details.get(1)).withStyle(ChatFormatting.GRAY));
        if (index == model.selectionIndex() && !model.diagnostic().isEmpty()) {
            lines.add(Component.literal(model.diagnostic()).withStyle(ChatFormatting.RED));
        }
        minecraft.screen.renderComponentTooltip(poseStack, lines, tooltipX, tooltipY);
    }

    private int itemIndexAt(double x, double y) {
        if (!layout.results().contains(x, y)) return -1;
        int column = (int) (x - layout.results().x()) / Math.max(1, layout.cellWidth());
        int row = (int) (y - layout.results().y()) / layout.cellHeight();
        int index = (firstVisibleRow + row) * layout.columns() + column;
        return index >= 0 && index < model.filtered().size() ? index : -1;
    }

    private void confirm() {
        if (completed) return;
        model.selectedIcon().ifPresent(icon -> {
            completed = true;
            onConfirm.accept(icon);
            closeHostedPanel();
        });
    }

    private void cancel() {
        if (completed) return;
        completed = true;
        onCancel.run();
        closeHostedPanel();
    }

    private void closeHostedPanel() {
        if (hostContext != null) hostContext.submit(new SFMWorkspacePanelIntent.Close());
    }

    private void resize(SFMScreenPanelBounds bounds) {
        currentBounds = bounds;
        recalculateLayout();
    }

    private void recalculateLayout() {
        layout = SFMItemPickerLayout.calculate(
                currentBounds.x(), currentBounds.y(), currentBounds.width(), currentBounds.height(), model.viewMode()
        );
        keepSelectionVisible();
    }

    private int visibleRows() {
        return Math.max(1, layout.results().height() / layout.cellHeight());
    }

    private int totalRows() {
        return (model.filtered().size() + layout.columns() - 1) / layout.columns();
    }

    private void keepSelectionVisible() {
        int row = model.selectionIndex() / Math.max(1, layout.columns());
        if (row < firstVisibleRow) firstVisibleRow = row;
        if (row >= firstVisibleRow + visibleRows()) firstVisibleRow = row - visibleRows() + 1;
        int maximum = Math.max(0, totalRows() - visibleRows());
        firstVisibleRow = Math.max(0, Math.min(firstVisibleRow, maximum));
    }

    private static void fill(PoseStack poseStack, SFMItemPickerLayout.Rect rect, int colour) {
        GuiComponent.fill(poseStack, rect.x(), rect.y(), rect.x() + rect.width(), rect.y() + rect.height(), colour);
    }

    private static void border(PoseStack poseStack, SFMItemPickerLayout.Rect rect, int colour) {
        int right = rect.x() + rect.width();
        int bottom = rect.y() + rect.height();
        GuiComponent.fill(poseStack, rect.x(), rect.y(), right, rect.y() + 1, colour);
        GuiComponent.fill(poseStack, rect.x(), bottom - 1, right, bottom, colour);
        GuiComponent.fill(poseStack, rect.x(), rect.y(), rect.x() + 1, bottom, colour);
        GuiComponent.fill(poseStack, right - 1, rect.y(), right, bottom, colour);
    }

    private static void drawCentered(PoseStack poseStack, Minecraft minecraft, String text,
                                     int x, int width, int y, int colour) {
        SFMFontUtils.draw(poseStack, minecraft.font, text,
                x + Math.max(0, (width - minecraft.font.width(text)) / 2), y, colour, true);
    }

    private static String trim(Minecraft minecraft, String text, int width) {
        if (minecraft.font.width(text) <= width) return text;
        return minecraft.font.plainSubstrByWidth(text, Math.max(0, width - minecraft.font.width("..."))) + "...";
    }

    private SFMItemPickerLayout.Rect cellForIndex(int index) {
        int visible = index - firstVisibleRow * layout.columns();
        int column = Math.max(0, visible % layout.columns());
        int row = Math.max(0, visible / layout.columns());
        return new SFMItemPickerLayout.Rect(
                layout.results().x() + column * layout.cellWidth(),
                layout.results().y() + row * layout.cellHeight(),
                layout.cellWidth(),
                layout.cellHeight()
        );
    }
}
