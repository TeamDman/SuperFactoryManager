package ca.teamdman.sfm.client.screen.item_picker;

{% if features.single_line_input %}
import ca.teamdman.sfm.client.input.SFMSingleLineInput;
import ca.teamdman.sfm.client.input.SFMSingleLineInputView;
{% endif %}
import ca.teamdman.sfm.client.presentation.SFMItemIcon;
import ca.teamdman.sfm.client.presentation.SFMItemIconRenderer;
import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelContext;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelIntent;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import com.mojang.blaze3d.vertex.PoseStack;
{% when "26.1.2" %}
import net.minecraft.client.gui.GuiGraphicsExtractor;
{% else %}
import net.minecraft.client.gui.GuiGraphics;
{% endcase %}
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import net.minecraft.client.gui.GuiComponent;
{% when "26.1.2" %}
{% else %}
{% endcase %}
import net.minecraft.network.chat.Component;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import net.minecraft.resources.ResourceLocation;
{% when "26.1.2" %}
import net.minecraft.resources.Identifier;
{% else %}
import net.minecraft.resources.ResourceLocation;
{% endcase %}
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
{% if features.single_line_input %}
    private final SFMSingleLineInput searchInput = new SFMSingleLineInput("");
    private final SFMSingleLineInputView searchView = new SFMSingleLineInputView();
    private boolean searchFocused;
    private boolean searchDragging;
    private Minecraft client;
{% endif %}
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
{% if features.single_line_input %}
        client = minecraft;
{% endif %}
        hostContext = context;
        resize(bounds);
    }

    @Override
    public void resized(Minecraft minecraft, SFMScreenPanelBounds bounds) { resize(bounds); }

    @Override
{% if features.single_line_input %}
    public void closed() { hostContext = null; completed = true; }
{% else %}
    public void closed() { hostContext = null; }
{% endif %}

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
{% if features.single_line_input %}
        if (keyCode == GLFW.GLFW_KEY_F && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            searchFocused = true;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_UP) searchFocused = false;
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) searchFocused = true;
        if (searchFocused && searchInput.keyPressed(keyCode, modifiers,
                () -> client == null ? "" : client.keyboardHandler.getClipboard(),
                value -> { if (client != null) client.keyboardHandler.setClipboard(value); })) {
            publishSearch();
            return true;
        }
{% endif %}
        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT -> model.move(-1, 0, layout.columns());
            case GLFW.GLFW_KEY_RIGHT -> model.move(1, 0, layout.columns());
            case GLFW.GLFW_KEY_UP -> model.move(0, -1, layout.columns());
            case GLFW.GLFW_KEY_DOWN -> model.move(0, 1, layout.columns());
            case GLFW.GLFW_KEY_HOME -> model.selectFirst();
            case GLFW.GLFW_KEY_END -> model.selectLast();
{% if features.single_line_input %}
{% else %}
            case GLFW.GLFW_KEY_BACKSPACE -> model.deleteQueryCharacter();
{% endif %}
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
{% if features.single_line_input %}
        if (!searchInput.charTyped(character, modifiers)) return false;
        searchFocused = true;
        publishSearch();
{% else %}
        if (character < 32 || character == 127) return false;
        model.appendQuery(character);
        keepSelectionVisible();
{% endif %}
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
{% if features.single_line_input %}
        if (layout.search().contains(mouseX, mouseY)) {
            searchFocused = searchDragging = true;
            moveSearchCaret(mouseX, client != null && net.minecraft.client.gui.screens.Screen.hasShiftDown());
            return true;
        }
        searchFocused = false;
{% endif %}
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

{% if features.single_line_input %}
    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (!searchDragging || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        moveSearchCaret(x, true);
        return true;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        boolean consumed = searchDragging;
        searchDragging = false;
        return consumed;
    }

    private void publishSearch() {
        if (model.query().equals(searchInput.text())) return;
        model.setQuery(searchInput.text());
        keepSelectionVisible();
    }

    private void moveSearchCaret(double x, boolean extend) {
        if (client == null) return;
        int inset = layout.compact() ? 5 : 9;
        int at = searchView.indexAt(searchInput, client.font, layout.search().width() - inset * 2,
                x - layout.search().x() - inset);
        searchInput.select(extend ? searchInput.anchor() : at, at);
    }

{% endif %}
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int maxFirst = Math.max(0, totalRows() - visibleRows());
        firstVisibleRow = Math.max(0, Math.min(maxFirst, firstVisibleRow + (delta > 0 ? -1 : 1)));
        return true;
    }

    @Override
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    public void render(
            PoseStack poseStack,
{% when "26.1.2" %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    public void render(
            GuiGraphicsExtractor graphics,
{% else %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    public void render(
            GuiGraphics graphics,
{% endcase %}
            Minecraft minecraft,
            SFMScreenPanelBounds bounds,
            int mouseX,
            int mouseY,
            float partialTick,
            boolean focused
    ) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        fill(poseStack, layout.content(), PANEL);
        fill(poseStack, layout.header(), HEADER);
        border(poseStack, layout.content(), focused ? 0xFF55FFFF : BORDER);
{% when "26.1.2" %}
        fillRect(graphics, layout.content(), PANEL);
        fillRect(graphics, layout.header(), HEADER);
        border(graphics, layout.content(), focused ? 0xFF55FFFF : BORDER);
{% else %}
        fillRect(graphics, layout.content(), PANEL);
        fillRect(graphics, layout.header(), HEADER);
        border(graphics, layout.content(), focused ? 0xFF55FFFF : BORDER);
{% endcase %}
        int inset = layout.compact() ? 5 : 9;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        SFMFontUtils.draw(poseStack, minecraft.font, title().copy().withStyle(ChatFormatting.BOLD),
{% when "26.1.2" %}
        SFMFontUtils.draw(graphics, minecraft.font, title().copy().withStyle(ChatFormatting.BOLD),
{% else %}
        SFMFontUtils.draw(graphics, minecraft.font, title().copy().withStyle(ChatFormatting.BOLD),
{% endcase %}
                layout.header().x() + inset, layout.header().y() + 7, TEXT, true);
        if (!layout.compact()) {
            String count = model.filtered().size() + " / " + model.entries().size() + " items";
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            SFMFontUtils.draw(poseStack, minecraft.font, count,
{% when "26.1.2" %}
            SFMFontUtils.draw(graphics, minecraft.font, count,
{% else %}
            SFMFontUtils.draw(graphics, minecraft.font, count,
{% endcase %}
                    layout.header().x() + layout.header().width() - minecraft.font.width(count) - inset,
                    layout.header().y() + 7, MUTED, true);
        }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        renderSearch(poseStack, minecraft, inset);
        renderItems(poseStack, minecraft);
        renderPreview(poseStack, minecraft);
        renderFooter(poseStack, minecraft);
        renderTooltip(poseStack, minecraft);
{% when "26.1.2" %}
        renderSearch(graphics, minecraft, inset);
        renderItems(graphics, minecraft);
        renderPreview(graphics, minecraft);
        renderFooter(graphics, minecraft);
        renderTooltip(graphics, minecraft);
{% else %}
        renderSearch(graphics, minecraft, inset);
        renderItems(graphics, minecraft);
        renderPreview(graphics, minecraft);
        renderFooter(graphics, minecraft);
        renderTooltip(graphics, minecraft);
{% endcase %}
    }

    public void setQueryForAutomation(String query) {
        automationTooltipIndex = -1;
{% if features.single_line_input %}
        searchInput.setText(query);
{% endif %}
        model.setQuery(query);
        keepSelectionVisible();
    }

    public void pressForAutomation(int keyCode, int modifiers) {
        if (!keyPressed(keyCode, 0, modifiers)) throw new IllegalStateException("Picker rejected key " + keyCode);
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    public void showUnavailableForAutomation(ResourceLocation itemId) {
{% when "26.1.2" %}
    public void showUnavailableForAutomation(Identifier itemId) {
{% else %}
    public void showUnavailableForAutomation(ResourceLocation itemId) {
{% endcase %}
        automationTooltipIndex = -1;
        model.showUnavailable(itemId);
        keepSelectionVisible();
    }

    public void showSelectionTooltipForAutomation() {
        automationTooltipIndex = model.selectionIndex();
        keepSelectionVisible();
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    private void renderSearch(PoseStack poseStack, Minecraft minecraft, int inset) {
        fill(poseStack, layout.search(), 0xFF101010);
        border(poseStack, layout.search(), 0xFF8A8A8A);
{% when "26.1.2" %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    private void renderSearch(GuiGraphicsExtractor graphics, Minecraft minecraft, int inset) {
        fillRect(graphics, layout.search(), 0xFF101010);
        border(graphics, layout.search(), 0xFF8A8A8A);
{% else %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    private void renderSearch(GuiGraphics graphics, Minecraft minecraft, int inset) {
        fillRect(graphics, layout.search(), 0xFF101010);
        border(graphics, layout.search(), 0xFF8A8A8A);
{% endcase %}
{% if features.single_line_input %}
        searchInput.setText(model.query());
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        searchView.render(poseStack, minecraft.font, searchInput,
{% when "26.1.2" %}
        searchView.render(graphics, minecraft.font, searchInput,
{% else %}
        searchView.render(graphics, minecraft.font, searchInput,
{% endcase %}
                layout.search().x() + inset, layout.search().y() + 7, layout.search().width() - inset * 2,
                searchFocused, "Search names, ids, or an SFML matcher...", TEXT, MUTED);
{% else %}
        String query = model.query();
        Component value = query.isEmpty()
                ? Component.literal("Search names, ids, or an SFML matcher...").withStyle(ChatFormatting.DARK_GRAY)
                : Component.literal(query + "_");
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        SFMFontUtils.draw(poseStack, minecraft.font, value,
{% when "26.1.2" %}
        SFMFontUtils.draw(graphics, minecraft.font, value,
{% else %}
        SFMFontUtils.draw(graphics, minecraft.font, value,
{% endcase %}
                layout.search().x() + inset, layout.search().y() + 7, TEXT, true);
{% endif %}
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    private void renderItems(PoseStack poseStack, Minecraft minecraft) {
{% when "26.1.2" %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    private void renderItems(GuiGraphicsExtractor graphics, Minecraft minecraft) {
{% else %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    private void renderItems(GuiGraphics graphics, Minecraft minecraft) {
{% endcase %}
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
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            if (index == model.selectionIndex()) fill(poseStack, cell, SELECTED);
            else if (cell.contains(mouseX, mouseY)) fill(poseStack, cell, HOVERED);
            border(poseStack, cell, 0xFF3A3A3A);
{% when "26.1.2" %}
            if (index == model.selectionIndex()) fillRect(graphics, cell, SELECTED);
            else if (cell.contains(mouseX, mouseY)) fillRect(graphics, cell, HOVERED);
            border(graphics, cell, 0xFF3A3A3A);
{% else %}
            if (index == model.selectionIndex()) fillRect(graphics, cell, SELECTED);
            else if (cell.contains(mouseX, mouseY)) fillRect(graphics, cell, HOVERED);
            border(graphics, cell, 0xFF3A3A3A);
{% endcase %}
            SFMItemPickerEntry entry = entries.get(index);
            if (model.viewMode() == SFMItemPickerModel.ViewMode.DENSE_ICONS) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                SFMItemIconRenderer.render(poseStack, minecraft, entry.toIcon(model.fallbackItem()), x + 3, y + 3);
{% when "26.1.2" %}
                SFMItemIconRenderer.render(graphics, minecraft, entry.toIcon(model.fallbackItem()), x + 3, y + 3);
{% else %}
                SFMItemIconRenderer.render(graphics, minecraft, entry.toIcon(model.fallbackItem()), x + 3, y + 3);
{% endcase %}
                continue;
            }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            SFMItemIconRenderer.render(poseStack, minecraft, entry.toIcon(model.fallbackItem()), x + 5, y + 8);
{% when "26.1.2" %}
            SFMItemIconRenderer.render(graphics, minecraft, entry.toIcon(model.fallbackItem()), x + 5, y + 8);
{% else %}
            SFMItemIconRenderer.render(graphics, minecraft, entry.toIcon(model.fallbackItem()), x + 5, y + 8);
{% endcase %}
            int textX = x + 26;
            int available = Math.max(1, layout.cellWidth() - 30);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            SFMFontUtils.draw(poseStack, minecraft.font,
{% when "26.1.2" %}
            SFMFontUtils.draw(graphics, minecraft.font,
{% else %}
            SFMFontUtils.draw(graphics, minecraft.font,
{% endcase %}
                    trim(minecraft, entry.accessibleName(), available), textX, y + 6, TEXT, true);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            SFMFontUtils.draw(poseStack, minecraft.font,
{% when "26.1.2" %}
            SFMFontUtils.draw(graphics, minecraft.font,
{% else %}
            SFMFontUtils.draw(graphics, minecraft.font,
{% endcase %}
                    trim(minecraft, entry.itemId().toString(), available), textX, y + 18, MUTED, true);
        }
        if (entries.isEmpty()) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            SFMFontUtils.draw(poseStack, minecraft.font, "No matching registry items",
{% when "26.1.2" %}
            SFMFontUtils.draw(graphics, minecraft.font, "No matching registry items",
{% else %}
            SFMFontUtils.draw(graphics, minecraft.font, "No matching registry items",
{% endcase %}
                    layout.results().x() + 8, layout.results().y() + 10, ERROR, true);
        }
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    private void renderPreview(PoseStack poseStack, Minecraft minecraft) {
{% when "26.1.2" %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    private void renderPreview(GuiGraphicsExtractor graphics, Minecraft minecraft) {
{% else %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    private void renderPreview(GuiGraphics graphics, Minecraft minecraft) {
{% endcase %}
        if (layout.preview().width() <= 0) return;
        if (layout.compact()) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            fill(poseStack, layout.preview(), 0xF0282828);
            border(poseStack, layout.preview(), BORDER);
{% when "26.1.2" %}
            fillRect(graphics, layout.preview(), 0xF0282828);
            border(graphics, layout.preview(), BORDER);
{% else %}
            fillRect(graphics, layout.preview(), 0xF0282828);
            border(graphics, layout.preview(), BORDER);
{% endcase %}
            int x = layout.preview().x() + 5;
            int y = layout.preview().y() + 3;
            if (!model.diagnostic().isEmpty()) {
                int lineY = y;
                for (var line : minecraft.font.split(
                        Component.literal(model.diagnostic()),
                        layout.preview().width() - 10
                )) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                    SFMFontUtils.draw(poseStack, minecraft.font, line, x, lineY, ERROR, true);
{% when "26.1.2" %}
                    SFMFontUtils.draw(graphics, minecraft.font, line, x, lineY, ERROR, true);
{% else %}
                    SFMFontUtils.draw(graphics, minecraft.font, line, x, lineY, ERROR, true);
{% endcase %}
                    lineY += minecraft.font.lineHeight;
                    if (lineY >= layout.preview().y() + layout.preview().height() - 2) break;
                }
                return;
            }
            model.selection().ifPresent(entry -> {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                SFMItemIconRenderer.render(poseStack, minecraft, entry.toIcon(model.fallbackItem()), x, y + 1);
                SFMFontUtils.draw(poseStack, minecraft.font,
{% when "26.1.2" %}
                SFMItemIconRenderer.render(graphics, minecraft, entry.toIcon(model.fallbackItem()), x, y + 1);
                SFMFontUtils.draw(graphics, minecraft.font,
{% else %}
                SFMItemIconRenderer.render(graphics, minecraft, entry.toIcon(model.fallbackItem()), x, y + 1);
                SFMFontUtils.draw(graphics, minecraft.font,
{% endcase %}
                        trim(minecraft, "Current: " + entry.accessibleName(), layout.preview().width() - 28),
                        x + 22, y, TEXT, true);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                SFMFontUtils.draw(poseStack, minecraft.font,
{% when "26.1.2" %}
                SFMFontUtils.draw(graphics, minecraft.font,
{% else %}
                SFMFontUtils.draw(graphics, minecraft.font,
{% endcase %}
                        trim(minecraft, entry.itemId().toString(), layout.preview().width() - 28),
                        x + 22, y + minecraft.font.lineHeight, MUTED, true);
            });
            return;
        }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        fill(poseStack, layout.preview(), 0xF0282828);
        border(poseStack, layout.preview(), BORDER);
{% when "26.1.2" %}
        fillRect(graphics, layout.preview(), 0xF0282828);
        border(graphics, layout.preview(), BORDER);
{% else %}
        fillRect(graphics, layout.preview(), 0xF0282828);
        border(graphics, layout.preview(), BORDER);
{% endcase %}
        int x = layout.preview().x() + 10;
        int y = layout.preview().y() + 10;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        SFMFontUtils.draw(poseStack, minecraft.font, "Current selection", x, y, SUCCESS, true);
{% when "26.1.2" %}
        SFMFontUtils.draw(graphics, minecraft.font, "Current selection", x, y, SUCCESS, true);
{% else %}
        SFMFontUtils.draw(graphics, minecraft.font, "Current selection", x, y, SUCCESS, true);
{% endcase %}
        model.selection().ifPresent(entry -> {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            SFMItemIconRenderer.render(poseStack, minecraft, entry.toIcon(model.fallbackItem()), x, y + 18);
            SFMFontUtils.draw(poseStack, minecraft.font,
{% when "26.1.2" %}
            SFMItemIconRenderer.render(graphics, minecraft, entry.toIcon(model.fallbackItem()), x, y + 18);
            SFMFontUtils.draw(graphics, minecraft.font,
{% else %}
            SFMItemIconRenderer.render(graphics, minecraft, entry.toIcon(model.fallbackItem()), x, y + 18);
            SFMFontUtils.draw(graphics, minecraft.font,
{% endcase %}
                    trim(minecraft, entry.accessibleName(), layout.preview().width() - 38),
                    x + 22, y + 22, TEXT, true);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            SFMFontUtils.draw(poseStack, minecraft.font,
{% when "26.1.2" %}
            SFMFontUtils.draw(graphics, minecraft.font,
{% else %}
            SFMFontUtils.draw(graphics, minecraft.font,
{% endcase %}
                    trim(minecraft, entry.itemId().toString(), layout.preview().width() - 20),
                    x, y + 44, MUTED, true);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            SFMFontUtils.draw(poseStack, minecraft.font,
{% when "26.1.2" %}
            SFMFontUtils.draw(graphics, minecraft.font,
{% else %}
            SFMFontUtils.draw(graphics, minecraft.font,
{% endcase %}
                    "Fallback: " + model.fallbackItem(), x, y + 60, MUTED, true);
        });
        String diagnostic = model.diagnostic();
        if (!diagnostic.isEmpty()) {
            int lineY = y + 84;
            for (var line : minecraft.font.split(Component.literal(diagnostic), layout.preview().width() - 20)) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                SFMFontUtils.draw(poseStack, minecraft.font, line, x, lineY, ERROR, true);
{% when "26.1.2" %}
                SFMFontUtils.draw(graphics, minecraft.font, line, x, lineY, ERROR, true);
{% else %}
                SFMFontUtils.draw(graphics, minecraft.font, line, x, lineY, ERROR, true);
{% endcase %}
                lineY += minecraft.font.lineHeight;
                if (lineY > y + 120) break;
            }
        }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        SFMFontUtils.draw(poseStack, minecraft.font,
{% when "26.1.2" %}
        SFMFontUtils.draw(graphics, minecraft.font,
{% else %}
        SFMFontUtils.draw(graphics, minecraft.font,
{% endcase %}
                trim(minecraft, model.interaction(), layout.preview().width() - 20),
                x, Math.max(y + 104, layout.preview().y() + layout.preview().height() - 18), MUTED, true);
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    private void renderFooter(PoseStack poseStack, Minecraft minecraft) {
        fill(poseStack, layout.footer(), HEADER);
{% when "26.1.2" %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    private void renderFooter(GuiGraphicsExtractor graphics, Minecraft minecraft) {
        fillRect(graphics, layout.footer(), HEADER);
{% else %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    private void renderFooter(GuiGraphics graphics, Minecraft minecraft) {
        fillRect(graphics, layout.footer(), HEADER);
{% endcase %}
        String toggle = model.viewMode() == SFMItemPickerModel.ViewMode.DETAILED
                ? "Ctrl+G Grid" : "Ctrl+G List";
        if (layout.compact()) {
            int half = Math.max(1, layout.footer().width() / 2);
            int topY = layout.footer().y() + 2;
            int bottomY = layout.footer().y() + 16;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            drawCentered(poseStack, minecraft, "Ctrl+R Reset", layout.footer().x(), half, topY, MUTED);
            drawCentered(poseStack, minecraft, toggle, layout.footer().x() + half,
{% when "26.1.2" %}
            drawCentered(graphics, minecraft, "Ctrl+R Reset", layout.footer().x(), half, topY, MUTED);
            drawCentered(graphics, minecraft, toggle, layout.footer().x() + half,
{% else %}
            drawCentered(graphics, minecraft, "Ctrl+R Reset", layout.footer().x(), half, topY, MUTED);
            drawCentered(graphics, minecraft, toggle, layout.footer().x() + half,
{% endcase %}
                    layout.footer().width() - half, topY, MUTED);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            drawCentered(poseStack, minecraft, "Esc Cancel", layout.footer().x(), half, bottomY, MUTED);
            drawCentered(poseStack, minecraft, "Enter Confirm", layout.footer().x() + half,
{% when "26.1.2" %}
            drawCentered(graphics, minecraft, "Esc Cancel", layout.footer().x(), half, bottomY, MUTED);
            drawCentered(graphics, minecraft, "Enter Confirm", layout.footer().x() + half,
{% else %}
            drawCentered(graphics, minecraft, "Esc Cancel", layout.footer().x(), half, bottomY, MUTED);
            drawCentered(graphics, minecraft, "Enter Confirm", layout.footer().x() + half,
{% endcase %}
                    layout.footer().width() - half, bottomY, SUCCESS);
        } else {
            int quarter = Math.max(1, layout.footer().width() / 4);
            int y = layout.footer().y() + 5;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            drawCentered(poseStack, minecraft, "Ctrl+R Reset", layout.footer().x(), quarter, y, MUTED);
            drawCentered(poseStack, minecraft, toggle, layout.footer().x() + quarter, quarter, y, MUTED);
            drawCentered(poseStack, minecraft, "Esc Cancel",
{% when "26.1.2" %}
            drawCentered(graphics, minecraft, "Ctrl+R Reset", layout.footer().x(), quarter, y, MUTED);
            drawCentered(graphics, minecraft, toggle, layout.footer().x() + quarter, quarter, y, MUTED);
            drawCentered(graphics, minecraft, "Esc Cancel",
{% else %}
            drawCentered(graphics, minecraft, "Ctrl+R Reset", layout.footer().x(), quarter, y, MUTED);
            drawCentered(graphics, minecraft, toggle, layout.footer().x() + quarter, quarter, y, MUTED);
            drawCentered(graphics, minecraft, "Esc Cancel",
{% endcase %}
                    layout.footer().x() + quarter * 2, quarter, y, MUTED);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            drawCentered(poseStack, minecraft, "Enter Confirm", layout.footer().x() + quarter * 3,
{% when "26.1.2" %}
            drawCentered(graphics, minecraft, "Enter Confirm", layout.footer().x() + quarter * 3,
{% else %}
            drawCentered(graphics, minecraft, "Enter Confirm", layout.footer().x() + quarter * 3,
{% endcase %}
                    layout.footer().width() - quarter * 3, y, SUCCESS);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            drawCentered(poseStack, minecraft, "Arrow keys navigate • typing filters",
{% when "26.1.2" %}
            drawCentered(graphics, minecraft, "Arrow keys navigate • typing filters",
{% else %}
            drawCentered(graphics, minecraft, "Arrow keys navigate • typing filters",
{% endcase %}
                    layout.footer().x(), layout.footer().width(), y + 14, MUTED);
        }
        if (layout.belowMinimum()) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            SFMFontUtils.draw(poseStack, minecraft.font, "Viewport below 140x150 minimum",
{% when "26.1.2" %}
            SFMFontUtils.draw(graphics, minecraft.font, "Viewport below 140x150 minimum",
{% else %}
            SFMFontUtils.draw(graphics, minecraft.font, "Viewport below 140x150 minimum",
{% endcase %}
                    layout.footer().x() + 4, layout.footer().y() - 11, ERROR, true);
        }
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    private void renderTooltip(PoseStack poseStack, Minecraft minecraft) {
{% when "26.1.2" %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    private void renderTooltip(GuiGraphicsExtractor graphics, Minecraft minecraft) {
{% else %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    private void renderTooltip(GuiGraphics graphics, Minecraft minecraft) {
{% endcase %}
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
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                minecraft.screen.renderComponentTooltip(poseStack, lines, mouseX, mouseY);
{% when "26.1.2" %}
                graphics.setComponentTooltipForNextFrame(minecraft.font, lines, mouseX, mouseY);
{% else %}
                graphics.renderComponentTooltip(minecraft.font, lines, mouseX, mouseY);
{% endcase %}
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
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        minecraft.screen.renderComponentTooltip(poseStack, lines, tooltipX, tooltipY);
{% when "26.1.2" %}
        graphics.setComponentTooltipForNextFrame(minecraft.font, lines, tooltipX, tooltipY);
{% else %}
        graphics.renderComponentTooltip(minecraft.font, lines, tooltipX, tooltipY);
{% endcase %}
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

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    private static void fill(PoseStack poseStack, SFMItemPickerLayout.Rect rect, int colour) {
        GuiComponent.fill(poseStack, rect.x(), rect.y(), rect.x() + rect.width(), rect.y() + rect.height(), colour);
{% when "26.1.2" %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    private static void fillRect(GuiGraphicsExtractor graphics, SFMItemPickerLayout.Rect rect, int colour) {
        graphics.fill(rect.x(), rect.y(), rect.x() + rect.width(), rect.y() + rect.height(), colour);
{% else %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    private static void fillRect(GuiGraphics graphics, SFMItemPickerLayout.Rect rect, int colour) {
        graphics.fill(rect.x(), rect.y(), rect.x() + rect.width(), rect.y() + rect.height(), colour);
{% endcase %}
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    private static void border(PoseStack poseStack, SFMItemPickerLayout.Rect rect, int colour) {
{% when "26.1.2" %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    private static void border(GuiGraphicsExtractor graphics, SFMItemPickerLayout.Rect rect, int colour) {
{% else %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    private static void border(GuiGraphics graphics, SFMItemPickerLayout.Rect rect, int colour) {
{% endcase %}
        int right = rect.x() + rect.width();
        int bottom = rect.y() + rect.height();
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        GuiComponent.fill(poseStack, rect.x(), rect.y(), right, rect.y() + 1, colour);
        GuiComponent.fill(poseStack, rect.x(), bottom - 1, right, bottom, colour);
        GuiComponent.fill(poseStack, rect.x(), rect.y(), rect.x() + 1, bottom, colour);
        GuiComponent.fill(poseStack, right - 1, rect.y(), right, bottom, colour);
{% when "26.1.2" %}
        graphics.fill(rect.x(), rect.y(), right, rect.y() + 1, colour);
        graphics.fill(rect.x(), bottom - 1, right, bottom, colour);
        graphics.fill(rect.x(), rect.y(), rect.x() + 1, bottom, colour);
        graphics.fill(right - 1, rect.y(), right, bottom, colour);
{% else %}
        graphics.fill(rect.x(), rect.y(), right, rect.y() + 1, colour);
        graphics.fill(rect.x(), bottom - 1, right, bottom, colour);
        graphics.fill(rect.x(), rect.y(), rect.x() + 1, bottom, colour);
        graphics.fill(right - 1, rect.y(), right, bottom, colour);
{% endcase %}
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    private static void drawCentered(PoseStack poseStack, Minecraft minecraft, String text,
{% when "26.1.2" %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    private static void drawCentered(GuiGraphicsExtractor graphics, Minecraft minecraft, String text,
{% else %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    private static void drawCentered(GuiGraphics graphics, Minecraft minecraft, String text,
{% endcase %}
                                     int x, int width, int y, int colour) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        SFMFontUtils.draw(poseStack, minecraft.font, text,
{% when "26.1.2" %}
        SFMFontUtils.draw(graphics, minecraft.font, text,
{% else %}
        SFMFontUtils.draw(graphics, minecraft.font, text,
{% endcase %}
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
