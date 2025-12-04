package ca.teamdman.sfm.common.label;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.net.ClientboundLabelGunUseResponsePacket;
import ca.teamdman.sfm.common.net.ServerboundLabelGunUsePacket;
import com.github.bsideup.jabel.Desugar;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import static ca.teamdman.sfm.common.net.ClientboundLabelGunUseResponsePacket.Behaviour.Pulled;
import static ca.teamdman.sfm.common.net.ClientboundLabelGunUseResponsePacket.Behaviour.Pushed;

@Desugar public record LabelGunManagerPushOrPullAction(
        EntityPlayer player,
        World level,
        ServerboundLabelGunUsePacket msg,
        ItemStack gunStack,
        LabelPositionHolder gunLabels,
        ManagerBlockEntity manager
) implements LabelGunPlan {
    @Override
    public void run() {
        if (player instanceof EntityPlayerMP playerMP) {
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
                new ClientboundLabelGunUseResponsePacket(Pulled)
                        .sendToPlayer(playerMP);
            } else {
                // save gun labels to disk
                gunLabels.save(disk);
                // rebuild program
                manager.rebuildProgramAndUpdateDisk();
                // mark manager dirty
                manager.markDirty();
                // give feedback to player
                new ClientboundLabelGunUseResponsePacket(Pushed)
                        .sendToPlayer(playerMP);
            }
        }
    }
}
