package ca.teamdman.sfm.client.screen.workspace;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/** Immutable typed reconstruction data for creating another independent panel. */
public interface SFMPanelReopenRecipe {
    ResourceLocation sceneTypeId();

    default Optional<Component> unavailableReason(SFMPanelReopenContext context) {
        return Optional.empty();
    }

    SFMScreenPanel reopen();
}
