package ca.teamdman.sfm.common.label;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.net.ClientboundLabelGunUseResponsePacket;
import ca.teamdman.sfm.common.net.ServerboundLabelGunUsePacket;
import com.github.bsideup.jabel.Desugar;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import vswe.superfactory.tiles.TileEntityManager;

import static ca.teamdman.sfm.common.net.ClientboundLabelGunUseResponsePacket.Behaviour.Pulled;
import static ca.teamdman.sfm.common.net.ClientboundLabelGunUseResponsePacket.Behaviour.Pushed;

@Desugar public record LabelGunOldManagerPushOrPullAction(
        EntityPlayer player,
        World level,
        ServerboundLabelGunUsePacket msg,
        ItemStack gunStack,
        LabelPositionHolder gunLabels,
        TileEntityManager manager
) implements LabelGunPlan {
    @Override
    public void run() {
        if (player instanceof EntityPlayerMP playerMP) {
            if (msg.isPullModifierActive()) {
                var newLabels = LabelPositionHolder.from(manager).toOwned();
                newLabels.save(gunStack);
                new ClientboundLabelGunUseResponsePacket(Pulled)
                        .sendToPlayer(playerMP);
            } else {
                manager.setLabels(gunLabels);
                new ClientboundLabelGunUseResponsePacket(Pushed)
                        .sendToPlayer(playerMP);
            }
        }
    }
}
