package ca.teamdman.sfm.common.label;

import ca.teamdman.sfm.common.net.ServerboundLabelGunUsePacket;
import ca.teamdman.sfm.common.util.BlockPosSet;
import com.github.bsideup.jabel.Desugar;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

@Desugar public record LabelGunToggleLabelAction(
        EntityPlayer player,
        World level,
        ServerboundLabelGunUsePacket msg,
        ItemStack gunStack,
        LabelPositionHolder gunLabels,
        LabelGunPlanTargets targets,
        String activeLabel
) implements LabelGunPlan {
    @Override
    public void run() {
        // if any missing label, make all blocks have label, otherwise remove label from all those blocks
        if (activeLabel.isEmpty()) {
            return;
        }
        BlockPosSet existing = gunLabels.getPositions(activeLabel);
        boolean anyMissing = targets.positions().longStream().anyMatch(p -> !existing.contains(p));

        // apply or strip label from all positions
        if (anyMissing) {
            gunLabels.addAll(activeLabel, targets.positions().blockPosIterator());
        } else {
            targets.positions().forEach(p -> gunLabels.remove(activeLabel, p));
        }
        // write changes to label gun
        gunLabels.save(gunStack);

    }
}
