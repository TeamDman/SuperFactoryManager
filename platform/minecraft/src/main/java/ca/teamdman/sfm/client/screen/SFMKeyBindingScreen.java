package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.client.screen.widget.SFMVerticalListViewport;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;

public final class SFMKeyBindingScreen extends Screen {
    static final int LIST_TOP = 64;
    static final int LIST_BOTTOM_MARGIN = 28;
    static final int ROW_STRIDE = 24;
    static final int ROW_HEIGHT = 22;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int SCROLLBAR_GAP = 4;
    private static final int SCROLLBAR_MIN_THUMB_HEIGHT = 12;
    private EditBox search;
    private List<ResourceLocation> visibleActions = List.of();
    private final SFMVerticalListViewport viewport = new SFMVerticalListViewport();
    private String query = "";
    private final boolean pushed;

    public SFMKeyBindingScreen() {
        super(Component.literal("SFM Shortcuts"));
        this.pushed = SFMScreenChangeHelpers.getCurrentScreen() != null;
    }

    @Override
    protected void init() {
        int searchWidth = Math.max(20, Math.min(300, width - 24));
        search = addRenderableWidget(new EditBox(font, (width - searchWidth) / 2, 34, searchWidth, 20,
                Component.literal("Search actions")));
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            refresh();
        });
        setInitialFocus(search);
        refresh();
    }

    private void refresh() {
        ResourceLocation previousSelection = selectedAction();
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        visibleActions = SFMClientActions.registry().keys().stream()
                .filter(id -> {
                    var action = SFMClientActions.registry().get(id);
                    return id.toString().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                            || action != null && action.title().getString().toLowerCase(Locale.ROOT).contains(normalizedQuery);
                })
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList();
        viewport.configure(visibleActions.size(), visibleRowCount(height));
        if (visibleActions.isEmpty()) {
            viewport.select(SFMVerticalListViewport.NO_SELECTION);
            return;
        }
        int selectedIndex = previousSelection == null ? -1 : visibleActions.indexOf(previousSelection);
        if (selectedIndex < 0) selectedIndex = Math.max(0, viewport.selectedRow());
        viewport.select(Math.min(selectedIndex, visibleActions.size() - 1));
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        renderBackground(poseStack);
        Component heading = title.copy().withStyle(ChatFormatting.BOLD);
        SFMFontUtils.draw(poseStack, font, heading, width / 2 - font.width(heading) / 2, 14, 0xFFFFFFFF, true);
        SFMVerticalListViewport.ScrollbarGeometry scrollbar = scrollbarGeometry();
        SFMVerticalListViewport.Bounds rows = rowBounds(scrollbar.visible());
        OptionalInt hoveredRow = viewport.rowAt(mouseX, mouseY, rows, ROW_STRIDE, ROW_HEIGHT);
        for (int index = viewport.firstVisibleRow(); index < viewport.lastVisibleRowExclusive(); index++) {
            ResourceLocation actionId = visibleActions.get(index);
            var action = SFMClientActions.registry().get(actionId);
            if (action == null) continue;
            int rowTop = LIST_TOP + (index - viewport.firstVisibleRow()) * ROW_STRIDE;
            boolean hovered = hoveredRow.isPresent() && hoveredRow.getAsInt() == index;
            boolean selected = viewport.selectedRow() == index;
            int background = selected ? 0xFF315A70 : hovered ? 0xFF404040 : 0xCC252525;
            fill(poseStack, rows.x(), rowTop, rows.x() + rows.width(), rowTop + ROW_HEIGHT, background);
            String actionTitle = font.plainSubstrByWidth(action.title().getString(), Math.max(20, rows.width() - 145));
            SFMFontUtils.draw(poseStack, font, actionTitle, rows.x() + 6, rowTop + 6, 0xFFFFFFFF, false);
            String count = ca.teamdman.sfm.client.keybinding.SFMKeyBindingService.INSTANCE
                    .bindingsForAction(actionId).size() + " bindings   [?]";
            SFMFontUtils.draw(poseStack, font, count, rows.x() + rows.width() - 6 - font.width(count), rowTop + 6,
                    0xFF80D8FF, false);
        }
        renderScrollbar(poseStack, mouseX, mouseY, scrollbar);
        super.render(poseStack, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        SFMVerticalListViewport.ScrollbarGeometry scrollbar = scrollbarGeometry();
        if (viewport.mouseClickedScrollbar(mouseX, mouseY, button, scrollbar)) return true;
        OptionalInt row = actionRowAt(viewport, mouseX, mouseY, width, height, scrollbar.visible());
        if (button == 0 && row.isPresent()) {
            viewport.select(row.getAsInt());
            openSelectedAction();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return viewport.mouseReleasedScrollbar(button) || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return viewport.mouseDraggedScrollbar(mouseY, button, scrollbarGeometry())
                || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (listBounds().contains(mouseX, mouseY) && viewport.scrollWheel(delta)) return true;
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (viewport.itemCount() > 0) {
            if (keyCode == GLFW.GLFW_KEY_UP) {
                viewport.moveSelection(-1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DOWN) {
                viewport.moveSelection(1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
                viewport.pageSelection(-1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
                viewport.pageSelection(1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_HOME) {
                viewport.selectFirst();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_END) {
                viewport.selectLast();
                return true;
            }
        }
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && viewport.selectedRow() != SFMVerticalListViewport.NO_SELECTION) {
            openSelectedAction();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void openSelectedAction() {
        ResourceLocation selected = selectedAction();
        if (selected != null) {
            SFMScreenChangeHelpers.setOrPushScreen(new SFMKeyBindingDetailsScreen(this, selected));
        }
    }

    private ResourceLocation selectedAction() {
        int selected = viewport.selectedRow();
        return selected >= 0 && selected < visibleActions.size() ? visibleActions.get(selected) : null;
    }

    private SFMVerticalListViewport.ScrollbarGeometry scrollbarGeometry() {
        SFMVerticalListViewport.Bounds list = listBounds();
        int trackWidth = Math.min(SCROLLBAR_WIDTH, list.width());
        return viewport.scrollbarGeometry(new SFMVerticalListViewport.Bounds(
                list.x() + list.width() - trackWidth,
                list.y(),
                trackWidth,
                list.height()
        ), SCROLLBAR_MIN_THUMB_HEIGHT);
    }

    private SFMVerticalListViewport.Bounds rowBounds(boolean scrollbarVisible) {
        return rowBounds(width, height, scrollbarVisible);
    }

    private SFMVerticalListViewport.Bounds listBounds() {
        return listBounds(width, height);
    }

    private void renderScrollbar(
            PoseStack poseStack,
            int mouseX,
            int mouseY,
            SFMVerticalListViewport.ScrollbarGeometry geometry
    ) {
        if (!geometry.visible()) return;
        var track = geometry.track();
        var thumb = geometry.thumb();
        fill(poseStack, track.x(), track.y(), track.x() + track.width(), track.y() + track.height(), 0x55303030);
        boolean hovered = thumb.contains(mouseX, mouseY);
        fill(
                poseStack,
                thumb.x(),
                thumb.y(),
                thumb.x() + thumb.width(),
                thumb.y() + thumb.height(),
                hovered || viewport.isScrollbarDragActive() ? 0xFFAAAAAA : 0xFF707070
        );
    }

    static int visibleRowCount(int screenHeight) {
        return Math.max(0, (screenHeight - LIST_TOP - LIST_BOTTOM_MARGIN) / ROW_STRIDE);
    }

    static SFMVerticalListViewport.Bounds listBounds(int screenWidth, int screenHeight) {
        int rowWidth = Math.max(0, Math.min(420, screenWidth - 24));
        int left = (screenWidth - rowWidth) / 2;
        return new SFMVerticalListViewport.Bounds(
                left,
                LIST_TOP,
                rowWidth,
                visibleRowCount(screenHeight) * ROW_STRIDE
        );
    }

    static SFMVerticalListViewport.Bounds rowBounds(int screenWidth, int screenHeight, boolean scrollbarVisible) {
        SFMVerticalListViewport.Bounds list = listBounds(screenWidth, screenHeight);
        int scrollbarSpace = scrollbarVisible ? SCROLLBAR_WIDTH + SCROLLBAR_GAP : 0;
        return new SFMVerticalListViewport.Bounds(
                list.x(),
                list.y(),
                Math.max(0, list.width() - scrollbarSpace),
                list.height()
        );
    }

    static OptionalInt actionRowAt(
            SFMVerticalListViewport viewport,
            double mouseX,
            double mouseY,
            int screenWidth,
            int screenHeight,
            boolean scrollbarVisible
    ) {
        return viewport.rowAt(
                mouseX,
                mouseY,
                rowBounds(screenWidth, screenHeight, scrollbarVisible),
                ROW_STRIDE,
                ROW_HEIGHT
        );
    }

    @Override
    public void onClose() {
        if (pushed) SFMScreenChangeHelpers.popScreen();
        else SFMScreenChangeHelpers.setScreen(null);
    }
}
