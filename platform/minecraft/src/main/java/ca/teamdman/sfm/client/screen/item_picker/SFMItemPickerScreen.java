package ca.teamdman.sfm.client.screen.item_picker;

import ca.teamdman.sfm.client.presentation.SFMItemIcon;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelContext;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/** Full-screen lifecycle wrapper around the composable item-picker panel. */
public final class SFMItemPickerScreen extends Screen {
    private final @Nullable Screen previousScreen;
    private final SFMItemPickerPanel panel;
    private boolean opened;

    public SFMItemPickerScreen(
            @Nullable Screen previousScreen,
            SFMItemIcon current,
            Consumer<SFMItemIcon> onConfirm
    ) {
        super(Component.literal("SFM Item Icon Picker"));
        this.previousScreen = previousScreen;
        this.panel = SFMItemPickerPanel.fromRegistry(current, icon -> {
            onConfirm.accept(icon);
            onClose();
        }, this::onClose);
    }

    public SFMItemPickerPanel panel() { return panel; }

    @Override
    protected void init() {
        super.init();
        SFMScreenPanelBounds bounds = new SFMScreenPanelBounds(0, 0, width, height);
        if (opened) panel.resized(minecraft, bounds);
        else {
            opened = true;
            panel.opened(minecraft, bounds, SFMWorkspacePanelContext.unhosted(new SFMWorkspacePanelId(0)));
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public Component getNarrationMessage() { return panel.narration(); }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return panel.keyPressed(keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        return panel.charTyped(character, modifiers) || super.charTyped(character, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return panel.mouseClicked(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        panel.mouseMoved(mouseX, mouseY);
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalDelta, double delta) {
        return panel.mouseScrolled(mouseX, mouseY, delta) || super.mouseScrolled(mouseX, mouseY, horizontalDelta, delta);
    }

    @Override
    public void onClose() { Minecraft.getInstance().setScreen(previousScreen); }

    @Override
    public void removed() {
        if (opened) panel.closed();
        opened = false;
        super.removed();
    }

    @Override
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        panel.render(graphics, minecraft, new SFMScreenPanelBounds(0, 0, width, height),
                mouseX, mouseY, partialTick, true);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
