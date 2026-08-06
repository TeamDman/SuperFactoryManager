package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.function.Consumer;
import org.lwjgl.glfw.GLFW;

/** Vanilla button adapted to panel-local layout plus semantic action metadata. */
public final class SFMPanelActionButton extends Button implements SFMPanelWidget {
    private final ResourceLocation elementId;
    private final ResourceLocation keyboardUsageSituationId;
    private final Supplier<Component> narration;
    private final Supplier<String> actionDraft;
    private final KeyHandler keyHandler;
    private final Consumer<Boolean> focusListener;

    public SFMPanelActionButton(
            ResourceLocation elementId,
            ResourceLocation keyboardUsageSituationId,
            Component message,
            Supplier<Component> narration,
            Supplier<String> actionDraft,
            Runnable activation
    ) {
        this(elementId, keyboardUsageSituationId, message, narration, actionDraft,
                activation, (keyCode, scanCode, modifiers) -> false, ignored -> { });
    }

    public SFMPanelActionButton(
            ResourceLocation elementId,
            ResourceLocation keyboardUsageSituationId,
            Component message,
            Supplier<Component> narration,
            Supplier<String> actionDraft,
            Runnable activation,
            KeyHandler keyHandler,
            Consumer<Boolean> focusListener
    ) {
        super(0, 0, 1, 1, message, ignored -> activation.run());
        this.elementId = Objects.requireNonNull(elementId);
        this.keyboardUsageSituationId = Objects.requireNonNull(keyboardUsageSituationId);
        this.narration = Objects.requireNonNull(narration);
        this.actionDraft = Objects.requireNonNull(actionDraft);
        this.keyHandler = Objects.requireNonNull(keyHandler);
        this.focusListener = Objects.requireNonNull(focusListener);
        Objects.requireNonNull(activation);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyHandler.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (Minecraft.getInstance() == null
                && active && visible
                && (keyCode == GLFW.GLFW_KEY_ENTER
                || keyCode == GLFW.GLFW_KEY_KP_ENTER
                || keyCode == GLFW.GLFW_KEY_SPACE)) {
            onPress();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (Minecraft.getInstance() != null) return super.mouseClicked(mouseX, mouseY, button);
        if (active && visible && button == GLFW.GLFW_MOUSE_BUTTON_LEFT && isMouseOver(mouseX, mouseY)) {
            onPress();
            return true;
        }
        return false;
    }

    @Override
    public ResourceLocation elementId() {
        return elementId;
    }

    @Override
    public ResourceLocation keyboardUsageSituationId() {
        return keyboardUsageSituationId;
    }

    @Override
    public Component narration() {
        return narration.get();
    }

    @Override
    public Optional<String> actionDraft() {
        return Optional.ofNullable(actionDraft.get()).filter(value -> !value.isBlank());
    }

    @Override
    public void setPanelBounds(SFMScreenPanelBounds bounds) {
        this.x = bounds.x();
        this.y = bounds.y();
        this.width = Math.max(0, bounds.width());
        this.height = Math.max(0, bounds.height());
    }

    @Override
    public boolean isPanelVisible() {
        return visible;
    }

    @Override
    public boolean isPanelEnabled() {
        return active;
    }

    @Override
    public boolean isPanelFocused() {
        return isFocused();
    }

    @Override
    @MCVersionDependentBehaviour
    public void setPanelFocused(boolean focused) {
        // AbstractWidget#setFocused is protected in 1.19.2. Keeping this
        // access in one adapter is the cross-version seam for propagated APIs.
        setFocused(focused);
        focusListener.accept(focused);
    }

    @FunctionalInterface
    public interface KeyHandler {
        boolean keyPressed(int keyCode, int scanCode, int modifiers);
    }
}
