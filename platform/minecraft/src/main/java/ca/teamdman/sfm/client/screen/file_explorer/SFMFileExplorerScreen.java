package ca.teamdman.sfm.client.screen.file_explorer;

import ca.teamdman.sfm.client.screen.SFMFontUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;

/** Full-screen host for the read-only explorer model. */
public class SFMFileExplorerScreen extends Screen {
    private static final int BACKGROUND = 0xE0101010;
    private static final int PANEL = 0xF0202020;
    private static final int HEADER = 0xF02A2A2A;
    private static final int BORDER = 0xFF606060;
    private static final int TEXT = 0xFFE8E8E8;
    private static final int MUTED = 0xFFAAAAAA;
    private static final int SELECTED = 0xFF264F78;
    private static final int ERROR = 0xFFFF7777;
    private static final int ROW_HEIGHT = 13;

    private final @Nullable Screen previousScreen;
    private final SFMFileExplorerModel model;
    private final SFMFilePresentationRegistry presentations;
    private final Consumer<SFMFileExplorerModel.OpenIntent> openIntentConsumer;
    private SFMFileExplorerLayout layout = SFMFileExplorerLayout.calculate(0, 0, 1, 1);
    private int firstVisibleRow;
    private long lastClickTime;
    private int lastClickIndex = -1;
    private String statusMessage = "Read-only";

    public SFMFileExplorerScreen(
            @Nullable Screen previousScreen,
            SFMFileExplorerSource source,
            Consumer<SFMFileExplorerModel.OpenIntent> openIntentConsumer
    ) {
        super(Component.literal("SFM File Explorer"));
        this.previousScreen = previousScreen;
        this.model = new SFMFileExplorerModel(source);
        this.presentations = SFMFilePresentationRegistry.createDefault();
        this.openIntentConsumer = openIntentConsumer;
    }

    public static SFMFileExplorerScreen createFixture(@Nullable Screen previousScreen) {
        return new SFMFileExplorerScreen(previousScreen, new SFMFileExplorerFixtureSource(), intent -> {});
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        layout = SFMFileExplorerLayout.calculate(0, 0, width, height);
        model.reload();
        keepSelectionVisible();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(previousScreen);
    }

    @Override
    public Component getNarrationMessage() {
        return Component.literal(this.title.getString() + ". " + model.selectedNarration(presentations));
    }

    @Override
    public boolean keyPressed(
            int keyCode,
            int scanCode,
            int modifiers
    ) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> model.selectPrevious();
            case GLFW.GLFW_KEY_DOWN -> model.selectNext();
            case GLFW.GLFW_KEY_HOME -> model.selectFirst();
            case GLFW.GLFW_KEY_END -> model.selectLast();
            case GLFW.GLFW_KEY_RIGHT -> model.expandSelection();
            case GLFW.GLFW_KEY_LEFT -> model.collapseSelectionOrSelectParent();
            case GLFW.GLFW_KEY_SPACE -> model.toggleSelection();
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> activateSelection();
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        keepSelectionVisible();
        return true;
    }

    @Override
    public boolean mouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !layout.list().contains(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int index = firstVisibleRow + (int) ((mouseY - layout.list().y()) / ROW_HEIGHT);
        if (index < 0 || index >= model.visibleEntries().size()) return true;
        model.select(index);
        long clickTime = Util.getMillis();
        if (lastClickIndex == index && clickTime - lastClickTime <= 300L) activateSelection();
        lastClickIndex = index;
        lastClickTime = clickTime;
        return true;
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double delta
    ) {
        int maxFirstRow = Math.max(0, model.visibleEntries().size() - visibleRowCount());
        firstVisibleRow = Math.max(0, Math.min(maxFirstRow, firstVisibleRow + (delta > 0 ? -1 : 1)));
        return true;
    }

    @Override
    public void render(
            PoseStack poseStack,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        this.renderBackground(poseStack);
        fill(poseStack, 0, 0, width, height, BACKGROUND);
        fillRect(poseStack, layout.content(), PANEL);
        fillRect(poseStack, layout.header(), HEADER);
        drawBorder(poseStack, layout.content());

        int inset = layout.compact() ? 4 : 8;
        int titleY = layout.header().y() + (layout.compact() ? 3 : 6);
        SFMFontUtils.draw(
                poseStack,
                font,
                this.title.copy().withStyle(ChatFormatting.BOLD),
                layout.header().x() + inset,
                titleY,
                TEXT,
                true
        );
        if (!layout.compact()) {
            SFMFontUtils.draw(
                    poseStack,
                    font,
                    "Source: " + model.sourceName() + " (read-only)",
                    layout.header().x() + inset,
                    titleY + 13,
                    MUTED,
                    true
            );
        }

        renderRows(poseStack);
        int statusColour = model.snapshot().state() == SFMFileExplorerSnapshot.State.ERROR ? ERROR : MUTED;
        String status = model.visibleEntries().isEmpty() ? model.stateDescription() : statusMessage;
        if (layout.belowMinimum()) status = "Viewport below supported 180x120 minimum; " + status;
        SFMFontUtils.draw(
                poseStack,
                font,
                trimToWidth(status, layout.status().width() - inset * 2),
                layout.status().x() + inset,
                layout.status().y() + 2,
                statusColour,
                true
        );
        super.render(poseStack, mouseX, mouseY, partialTick);
    }

    private void renderRows(PoseStack poseStack) {
        List<SFMFileExplorerModel.VisibleEntry> rows = model.visibleEntries();
        int end = Math.min(rows.size(), firstVisibleRow + visibleRowCount());
        for (int index = firstVisibleRow; index < end; index++) {
            SFMFileExplorerModel.VisibleEntry row = rows.get(index);
            int y = layout.list().y() + (index - firstVisibleRow) * ROW_HEIGHT;
            if (index == model.selectionIndex()) {
                fill(poseStack, layout.list().x() + 1, y, layout.list().x() + layout.list().width() - 1, y + ROW_HEIGHT, SELECTED);
            }
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
            SFMFontUtils.draw(
                    poseStack,
                    font,
                    Component.literal(trimToWidth(text, layout.list().width() - 8)).withStyle(style),
                    layout.list().x() + 4,
                    y + 2,
                    presentation.textColour(),
                    true
            );
        }
    }

    private void activateSelection() {
        model.activateSelection().ifPresent(intent -> {
            statusMessage = "Open requested: " + intent.entry().path() + " (read-only)";
            openIntentConsumer.accept(intent);
        });
    }

    private void keepSelectionVisible() {
        int selected = model.selectionIndex();
        int visibleRows = visibleRowCount();
        if (selected < firstVisibleRow) firstVisibleRow = selected;
        if (selected >= firstVisibleRow + visibleRows) firstVisibleRow = selected - visibleRows + 1;
        firstVisibleRow = Math.max(0, firstVisibleRow);
    }

    private int visibleRowCount() {
        return Math.max(1, layout.list().height() / ROW_HEIGHT);
    }

    private String trimToWidth(
            String value,
            int availableWidth
    ) {
        if (availableWidth <= 0) return "";
        if (font.width(value) <= availableWidth) return value;
        String suffix = "...";
        return font.plainSubstrByWidth(value, Math.max(0, availableWidth - font.width(suffix))) + suffix;
    }

    private static void fillRect(
            PoseStack poseStack,
            SFMFileExplorerLayout.Rect rect,
            int colour
    ) {
        fill(poseStack, rect.x(), rect.y(), rect.x() + rect.width(), rect.y() + rect.height(), colour);
    }

    private static void drawBorder(
            PoseStack poseStack,
            SFMFileExplorerLayout.Rect rect
    ) {
        int right = rect.x() + rect.width();
        int bottom = rect.y() + rect.height();
        fill(poseStack, rect.x(), rect.y(), right, rect.y() + 1, BORDER);
        fill(poseStack, rect.x(), bottom - 1, right, bottom, BORDER);
        fill(poseStack, rect.x(), rect.y(), rect.x() + 1, bottom, BORDER);
        fill(poseStack, right - 1, rect.y(), right, bottom, BORDER);
    }
}
