package ca.teamdman.sfm.common.label;

import ca.teamdman.sfm.common.net.ServerboundLabelGunUsePacket;
import ca.teamdman.sfm.common.util.BlockPosSet;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public record LabelGunToggleLabelAction(
        Player player,
        Level level,
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
