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
        var disk = manager.getDisk();
        if (disk == null) {
            return;
        }
        if (msg.isPullModifierActive()) {
            // start with labels from disk
            var newLabels = LabelPositionHolder.from(disk).toOwned();
            // ensure script-referenced labels are included
            manager.getReferencedLabels().forEach(newLabels::addReferencedLabel);
            // save to gun
            newLabels.save(gunStack);
            // give feedback to player
            new ClientboundLabelGunUseResponsePacket(ClientboundLabelGunUseResponsePacket.Behaviour.Pulled)
                    .sendToPlayer(player);
        } else {
            // save gun labels to disk
            gunLabels.save(disk);
            // rebuild program
            manager.rebuildProgramAndUpdateDisk();
            // mark manager dirty
            manager.setChanged();
            // give feedback to player
            new ClientboundLabelGunUseResponsePacket(ClientboundLabelGunUseResponsePacket.Behaviour.Pushed)
                    .sendToPlayer(player);
        }
    }
}
