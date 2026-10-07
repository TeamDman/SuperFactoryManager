package ca.teamdman.sfm.client.screen.item_picker;

import ca.teamdman.sfm.client.presentation.SFMItemIcon;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelContext;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import com.mojang.blaze3d.vertex.PoseStack;
{% when "1.20", "1.20.1" %}
import net.minecraft.client.gui.GuiGraphics;
{% when "26.1.2" %}
import net.minecraft.client.gui.GuiGraphicsExtractor;
{% else %}
import net.minecraft.client.gui.GuiGraphics;
{% endcase %}
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
{% case minecraft_version %}
{% when "26.1.2" %}
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int keyCode = event.key();
        int scanCode = event.scancode();
        int modifiers = event.modifiers();
        return panel.keyPressed(keyCode, scanCode, modifiers) || super.keyPressed(event);
    }
{% else %}
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return panel.keyPressed(keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
    }
{% endcase %}

    @Override
{% case minecraft_version %}
{% when "26.1.2" %}
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        char character = (char) event.codepoint();
        int modifiers = 0;
        return panel.charTyped(character, modifiers) || super.charTyped(event);
    }
{% else %}
    public boolean charTyped(char character, int modifiers) {
        return panel.charTyped(character, modifiers) || super.charTyped(character, modifiers);
    }
{% endcase %}

    @Override
{% case minecraft_version %}
{% when "26.1.2" %}
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        return panel.mouseClicked(mouseX, mouseY, button) || super.mouseClicked(event, doubleClick);
    }
{% else %}
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return panel.mouseClicked(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
    }
{% endcase %}

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        panel.mouseMoved(mouseX, mouseY);
        super.mouseMoved(mouseX, mouseY);
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return panel.mouseScrolled(mouseX, mouseY, delta) || super.mouseScrolled(mouseX, mouseY, delta);
    }
{% when "1.20", "1.20.1" %}
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return panel.mouseScrolled(mouseX, mouseY, delta) || super.mouseScrolled(mouseX, mouseY, delta);
    }
{% when "26.1.2" %}
    @Override
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalDelta, double delta) {
        return panel.mouseScrolled(mouseX, mouseY, delta) || super.mouseScrolled(mouseX, mouseY, horizontalDelta, delta);
    }
{% else %}
    @Override
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalDelta, double delta) {
        return panel.mouseScrolled(mouseX, mouseY, delta) || super.mouseScrolled(mouseX, mouseY, horizontalDelta, delta);
    }
{% endcase %}

    @Override
    public void onClose() { Minecraft.getInstance().setScreen(previousScreen); }

    @Override
    public void removed() {
        if (opened) panel.closed();
        opened = false;
        super.removed();
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        renderBackground(poseStack);
        panel.render(poseStack, minecraft, new SFMScreenPanelBounds(0, 0, width, height),
                mouseX, mouseY, partialTick, true);
        super.render(poseStack, mouseX, mouseY, partialTick);
    }
{% when "1.20", "1.20.1" %}
    @Override
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        panel.render(graphics, minecraft, new SFMScreenPanelBounds(0, 0, width, height),
                mouseX, mouseY, partialTick, true);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
{% when "26.1.2" %}
    @Override
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        panel.render(graphics, minecraft, new SFMScreenPanelBounds(0, 0, width, height),
                mouseX, mouseY, partialTick, true);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
{% else %}
    @Override
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        panel.render(graphics, minecraft, new SFMScreenPanelBounds(0, 0, width, height),
                mouseX, mouseY, partialTick, true);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
{% endcase %}
}
