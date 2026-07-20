package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** The first two-column SFM workspace and sole owner of Minecraft's Screen lifecycle. */
public final class SFMScreenMultiplexer extends Screen {
    private static final int DIVIDER_WIDTH = 2;
    private static final int PANEL_BACKGROUND = 0xE0202020;
    private static final int FOCUSED_BORDER = 0xFF55FFFF;
    private static final int UNFOCUSED_BORDER = 0xFF606060;

    private final @Nullable Screen previousScreen;
    private final List<SFMScreenPanel> panels = new ArrayList<>(2);
    private boolean panelsOpened;
    private boolean closing;
    private int focusedPanel;

    private SFMScreenMultiplexer(
            @Nullable Screen previousScreen,
            SFMScreenPanel left,
            SFMScreenPanel right
    ) {
        super(Component.literal("SFM workspace"));
        this.previousScreen = previousScreen;
        this.panels.add(left);
        this.panels.add(right);
        this.focusedPanel = 1;
    }

    public static void openToSide(@Nullable Screen origin, SFMScreenPanel panel) {
        if (origin instanceof SFMScreenMultiplexer multiplexer) {
            multiplexer.openToSide(panel);
            return;
        }
        SFMScreenChangeHelpers.setScreen(new SFMScreenMultiplexer(
                origin,
                new SFMPreviousScreenPanel(origin),
                panel
        ));
    }

    /** Replaces the right-hand experimental slot; nesting is outside this slice. */
    public void openToSide(SFMScreenPanel panel) {
        panels.get(1).closed();
        panels.set(1, panel);
        focusedPanel = 1;
        if (panelsOpened) panel.opened(Minecraft.getInstance(), bounds(1));
    }

    public int focusedPanel() {
        return focusedPanel;
    }

    public List<SFMScreenPanel> panels() {
        return List.copyOf(panels);
    }

    @Override
    protected void init() {
        super.init();
        for (int i = 0; i < panels.size(); i++) {
            if (panelsOpened) {
                panels.get(i).resized(this.minecraft, bounds(i));
            } else {
                panels.get(i).opened(this.minecraft, bounds(i));
            }
        }
        panelsOpened = true;
    }

    @Override
    public void tick() {
        for (SFMScreenPanel panel : panels) panel.tick();
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public Component getNarrationMessage() {
        return Component.literal("SFM workspace. Focused panel: ").append(panels.get(focusedPanel).narration());
    }

    @Override
    public void onClose() {
        if (closing) return;
        closing = true;
        for (SFMScreenPanel panel : panels) panel.closed();
        SFMScreenChangeHelpers.setScreen(previousScreen);
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(poseStack);
        for (int i = 0; i < panels.size(); i++) {
            SFMScreenPanelBounds bounds = bounds(i);
            fill(poseStack, bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(), PANEL_BACKGROUND);
            int border = i == focusedPanel ? FOCUSED_BORDER : UNFOCUSED_BORDER;
            fill(poseStack, bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + 1, border);
            fill(poseStack, bounds.x(), bounds.y() + bounds.height() - 1, bounds.x() + bounds.width(), bounds.y() + bounds.height(), border);
            fill(poseStack, bounds.x(), bounds.y(), bounds.x() + 1, bounds.y() + bounds.height(), border);
            fill(poseStack, bounds.x() + bounds.width() - 1, bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(), border);
            panels.get(i).render(poseStack, this.minecraft, bounds, mouseX, mouseY, partialTick, i == focusedPanel);
        }
        super.render(poseStack, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_1) {
            focusedPanel = 0;
            return true;
        }
        if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_2) {
            focusedPanel = 1;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return panels.get(focusedPanel).keyPressed(keyCode, scanCode, modifiers)
                || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return panels.get(focusedPanel).keyReleased(keyCode, scanCode, modifiers)
                || super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        return panels.get(focusedPanel).charTyped(character, modifiers)
                || super.charTyped(character, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int panelIndex = panelAt(mouseX, mouseY);
        if (panelIndex >= 0) {
            focusedPanel = panelIndex;
            panels.get(panelIndex).mouseClicked(mouseX, mouseY, button);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        int panelIndex = panelAt(mouseX, mouseY);
        if (panelIndex >= 0) panels.get(panelIndex).mouseMoved(mouseX, mouseY);
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return panels.get(focusedPanel).mouseReleased(mouseX, mouseY, button)
                || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return panels.get(focusedPanel).mouseDragged(mouseX, mouseY, button, dragX, dragY)
                || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int panelIndex = panelAt(mouseX, mouseY);
        if (panelIndex >= 0) focusedPanel = panelIndex;
        return panels.get(focusedPanel).mouseScrolled(mouseX, mouseY, delta)
                || super.mouseScrolled(mouseX, mouseY, delta);
    }

    private int panelAt(double mouseX, double mouseY) {
        for (int i = 0; i < panels.size(); i++) {
            if (bounds(i).contains(mouseX, mouseY)) return i;
        }
        return -1;
    }

    private SFMScreenPanelBounds bounds(int index) {
        int leftWidth = Math.max(1, (this.width - DIVIDER_WIDTH) / 2);
        if (index == 0) return new SFMScreenPanelBounds(0, 0, leftWidth, this.height);
        int rightX = leftWidth + DIVIDER_WIDTH;
        return new SFMScreenPanelBounds(rightX, 0, Math.max(1, this.width - rightX), this.height);
    }
}
