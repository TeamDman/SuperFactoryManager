package ca.teamdman.sfm.common.label;

import ca.teamdman.sfm.common.item.LabelGunItem;
import ca.teamdman.sfm.common.net.ServerboundLabelGunUsePacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

public record LabelGunPickLabelAction(
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
        LabelGunActions.pick(level, gunStack, msg.pos(), msg.isContiguousModifierActive());
    }
}
