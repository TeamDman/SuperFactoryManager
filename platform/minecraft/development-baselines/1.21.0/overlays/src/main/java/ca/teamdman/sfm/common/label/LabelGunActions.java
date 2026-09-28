package ca.teamdman.sfm.common.label;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.item.LabelGunItem;
import ca.teamdman.sfm.common.util.BlockPosSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

/**
 * Server-side label-gun operations shared by player actions and automation.
 *
 * <p>These methods deliberately work on an item stack instead of a player. That keeps the
 * label gun's game rules in one place while allowing a turtle to operate a gun stored in its
 * inventory.</p>
 */
public final class LabelGunActions {
    private LabelGunActions() {

    }

    public static LabelGunActionResult toggle(
            Level level,
            ItemStack gunStack,
            BlockPos targetPos,
            boolean contiguous
    ) {

        String activeLabel = LabelGunItem.getActiveLabel(gunStack);
        if (activeLabel.isEmpty()) {
            return LabelGunActionResult.successful();
        }
        LabelPositionHolder gunLabels = LabelPositionHolder.from(gunStack).toOwned();
        LabelGunPlanTargets targets = LabelGunPlanTargets.getTargets(level, targetPos, contiguous);
        BlockPosSet existing = gunLabels.getPositions(activeLabel);
        boolean anyMissing = targets.positions().longStream().anyMatch(position -> !existing.contains(position));
        if (anyMissing) {
            gunLabels.addAll(activeLabel, targets.positions().blockPosIterator());
        } else {
            targets.positions().forEach(position -> gunLabels.remove(activeLabel, position));
        }
        gunLabels.save(gunStack);
        return LabelGunActionResult.successful();
    }

    public static LabelGunActionResult clearActive(
            Level level,
            ItemStack gunStack,
            BlockPos targetPos,
            boolean contiguous
    ) {

        String activeLabel = LabelGunItem.getActiveLabel(gunStack);
        if (activeLabel.isEmpty()) {
            return LabelGunActionResult.successful();
        }
        LabelPositionHolder gunLabels = LabelPositionHolder.from(gunStack).toOwned();
        LabelGunPlanTargets.getTargets(level, targetPos, contiguous)
                .positions()
                .forEach(position -> gunLabels.remove(activeLabel, position));
        gunLabels.save(gunStack);
        return LabelGunActionResult.successful();
    }

    public static LabelGunActionResult clearAll(
            Level level,
            ItemStack gunStack,
            BlockPos targetPos,
            boolean contiguous
    ) {

        LabelPositionHolder gunLabels = LabelPositionHolder.from(gunStack).toOwned();
        LabelGunPlanTargets.getTargets(level, targetPos, contiguous)
                .positions()
                .forEach(gunLabels::removeAll);
        gunLabels.save(gunStack);
        return LabelGunActionResult.successful();
    }

    public static LabelGunActionResult pick(
            Level level,
            ItemStack gunStack,
            BlockPos targetPos,
            boolean contiguous
    ) {

        String activeLabel = LabelGunItem.getActiveLabel(gunStack);
        LabelPositionHolder gunLabels = LabelPositionHolder.from(gunStack).toOwned();
        Set<String> allLabels = new HashSet<>();
        LabelGunPlanTargets.getTargets(level, targetPos, contiguous)
                .positions()
                .forEach(position -> allLabels.addAll(gunLabels.getLabels(position)));
        if (allLabels.isEmpty()) {
            return LabelGunActionResult.successful();
        }
        var labels = new ArrayList<>(allLabels);
        labels.sort(Comparator.naturalOrder());
        LabelGunItem.setActiveLabel(gunStack, labels.get((labels.indexOf(activeLabel) + 1) % labels.size()));
        gunLabels.save(gunStack);
        return LabelGunActionResult.successful();
    }

    public static LabelGunActionResult push(
            ItemStack gunStack,
            ManagerBlockEntity manager
    ) {

        ItemStack disk = manager.getDisk();
        if (disk == null) {
            return LabelGunActionResult.failure("no_disk");
        }
        LabelPositionHolder.from(gunStack).toOwned().save(disk);
        manager.ensureRebuildWarnings();
        manager.rebuildProgramAndUpdateDisk();
        manager.setChanged();
        return LabelGunActionResult.successful();
    }

    public static LabelGunActionResult pull(
            ItemStack gunStack,
            ManagerBlockEntity manager
    ) {

        ItemStack disk = manager.getDisk();
        if (disk == null) {
            return LabelGunActionResult.failure("no_disk");
        }
        LabelPositionHolder labels = LabelPositionHolder.from(disk).toOwned();
        manager.getReferencedLabels().forEach(labels::addReferencedLabel);
        labels.save(gunStack);
        return LabelGunActionResult.successful();
    }

    public record LabelGunActionResult(boolean success, String errorCode) {
        public static LabelGunActionResult successful() {

            return new LabelGunActionResult(true, null);
        }

        public static LabelGunActionResult failure(String errorCode) {

            return new LabelGunActionResult(false, errorCode);
        }
    }
}
