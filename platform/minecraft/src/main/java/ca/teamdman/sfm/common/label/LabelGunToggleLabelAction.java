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
        LabelGunActions.toggle(level, gunStack, msg.pos(), msg.isContiguousModifierActive());

    }
}
