package ca.teamdman.sfm.common.label;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.net.ClientboundLabelGunUseResponsePacket;
import ca.teamdman.sfm.common.net.ServerboundLabelGunUsePacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public record LabelGunManagerPushOrPullAction(
        Player player,
        Level level,
        ServerboundLabelGunUsePacket msg,
        ItemStack gunStack,
        LabelPositionHolder gunLabels,
        ManagerBlockEntity manager
) implements LabelGunPlan {
    @Override
    public void run() {
        if (msg.isPullModifierActive()) {
            if (LabelGunActions.pull(gunStack, manager).success()) {
                new ClientboundLabelGunUseResponsePacket(ClientboundLabelGunUseResponsePacket.Behaviour.Pulled)
                        .sendToPlayer(player);
            }
        } else {
            if (LabelGunActions.push(gunStack, manager).success()) {
                new ClientboundLabelGunUseResponsePacket(ClientboundLabelGunUseResponsePacket.Behaviour.Pushed)
                        .sendToPlayer(player);
            }
        }
    }
}
