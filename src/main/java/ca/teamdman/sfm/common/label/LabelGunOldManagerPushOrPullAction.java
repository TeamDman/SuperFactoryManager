package ca.teamdman.sfm.common.label;

import static ca.teamdman.sfm.common.net.ClientboundLabelGunUseResponsePacket.Behaviour.Pushed;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.github.bsideup.jabel.Desugar;

import ca.teamdman.sfm.common.net.ClientboundLabelGunUseResponsePacket;
import ca.teamdman.sfm.common.net.ServerboundLabelGunUsePacket;
import vswe.superfactory.tiles.TileEntityManager;

@Desugar
public record LabelGunOldManagerPushOrPullAction(
                                                 EntityPlayer player,
                                                 World level,
                                                 ServerboundLabelGunUsePacket msg,
                                                 ItemStack gunStack,
                                                 LabelPositionHolder gunLabels,
                                                 TileEntityManager manager)
        implements LabelGunPlan {

    @Override
    public void run() {
        if (player instanceof EntityPlayerMP playerMP) {
            if (msg.isPullModifierActive()) {
                // start with labels from disk
                // var newLabels = LabelPositionHolder.from(disk).toOwned();
                // // ensure script-referenced labels are included
                // manager.getReferencedLabels().forEach(newLabels::addReferencedLabel);
                // // save to gun
                // newLabels.save(gunStack);
                // // give feedback to player
                // new ClientboundLabelGunUseResponsePacket(Pulled)
                // .sendToPlayer(playerMP);
            } else {
                manager.setLabels(gunLabels);
                new ClientboundLabelGunUseResponsePacket(Pushed)
                        .sendToPlayer(playerMP);
            }
        }
    }
}
