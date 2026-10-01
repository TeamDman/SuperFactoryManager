package ca.teamdman.sfm.common.label;

import ca.teamdman.sfm.common.blockentity.ClientManagerBlockEntity;
import ca.teamdman.sfm.common.net.ClientboundLabelGunUseResponsePacket;
import ca.teamdman.sfm.common.net.ServerboundLabelGunUsePacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Reuses the label gun's disk-held bindings for a Client Manager. */
public record LabelGunClientManagerPushOrPullAction(
        Player player,
        ServerboundLabelGunUsePacket message,
        ItemStack gun,
        ClientManagerBlockEntity manager
) implements LabelGunPlan {
    @Override
    public void run() {
        LabelGunActions.LabelGunActionResult result = message.isPullModifierActive()
                ? LabelGunActions.pull(gun, manager)
                : LabelGunActions.push(gun, manager);
        if (result.success()) {
            new ClientboundLabelGunUseResponsePacket(
                    message.isPullModifierActive()
                            ? ClientboundLabelGunUseResponsePacket.Behaviour.Pulled
                            : ClientboundLabelGunUseResponsePacket.Behaviour.Pushed
            ).sendToPlayer(player);
        }
    }
}
