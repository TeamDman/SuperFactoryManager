package ca.teamdman.sfm.common.label;

import ca.teamdman.sfm.common.item.LabelGunItem;
import ca.teamdman.sfm.common.net.ServerboundLabelGunUsePacket;
import com.github.bsideup.jabel.Desugar;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

@Desugar
public record LabelGunPickLabelAction(
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
        // pick the next label in the list to become active
        Set<String> allLabels = new HashSet<>();
        targets.positions().forEach(p -> allLabels.addAll(gunLabels.getLabels(p)));
        if (allLabels.isEmpty()) {
            return;
        }

        var labelsList = new ArrayList<>(allLabels);
        labelsList.sort(Comparator.naturalOrder());
        var index = (labelsList.indexOf(activeLabel) + 1) % labelsList.size();
        var nextLabel = labelsList.get(index);
        LabelGunItem.setActiveLabel(gunStack, nextLabel);

        // write changes to label gun
        gunLabels.save(gunStack);
    }
}
