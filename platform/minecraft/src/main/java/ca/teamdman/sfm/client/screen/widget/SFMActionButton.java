package ca.teamdman.sfm.client.screen.widget;

import ca.teamdman.sfm.client.action.SFMActionElement;
import ca.teamdman.sfm.client.action.SFMActionElementAudit;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Vanilla-focusable button with a stable semantic action address. */
public final class SFMActionButton extends SFMExtendedButton implements SFMActionElement {
    private final ResourceLocation elementId;
    private final ResourceLocation keyboardUsageSituationId;
    private final Supplier<Component> narration;
    private final Supplier<String> actionDraft;

    public SFMActionButton(
            int x,
            int y,
            int width,
            int height,
            Component message,
            ResourceLocation elementId,
            ResourceLocation keyboardUsageSituationId,
            Supplier<Component> narration,
            Supplier<String> actionDraft,
            Button.OnPress onPress
    ) {
        super(x, y, width, height, message, onPress);
        this.elementId = Objects.requireNonNull(elementId);
        this.keyboardUsageSituationId = Objects.requireNonNull(keyboardUsageSituationId);
        this.narration = Objects.requireNonNull(narration);
        this.actionDraft = Objects.requireNonNull(actionDraft);
        SFMActionElementAudit.requireValid(this);
    }

    @Override public ResourceLocation elementId() { return elementId; }
    @Override public ResourceLocation keyboardUsageSituationId() { return keyboardUsageSituationId; }
    @Override public Component narration() { return narration.get(); }
    @Override public Optional<String> actionDraft() {
        return Optional.ofNullable(actionDraft.get()).filter(value -> !value.isBlank());
    }
    @Override public boolean isKeyboardReachable() { return true; }
}
