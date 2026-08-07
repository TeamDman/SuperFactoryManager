package ca.teamdman.sfm.client.screen.workspace;

import net.minecraft.client.gui.components.Widget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import ca.teamdman.sfm.client.action.SFMActionElement;

import java.util.Optional;

/**
 * One panel-local child which participates in Minecraft-like focus and input.
 *
 * <p>The action and situation metadata is intentionally present before the
 * contextual-binding registry exists. K-3 can project these stable identities
 * without changing the widget contract introduced here.</p>
 */
public interface SFMPanelWidget extends GuiEventListener, Widget, NarratableEntry, SFMActionElement {
    ResourceLocation elementId();

    ResourceLocation keyboardUsageSituationId();

    Component narration();

    Optional<String> actionDraft();

    void setPanelBounds(SFMScreenPanelBounds bounds);

    boolean isPanelVisible();

    boolean isPanelEnabled();

    boolean isPanelFocused();

    void setPanelFocused(boolean focused);

    @Override
    default boolean isKeyboardReachable() {
        return true;
    }
}
