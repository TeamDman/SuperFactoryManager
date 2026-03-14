package ca.teamdman.sfm.common.label;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.item.LabelGunItem;
import ca.teamdman.sfm.common.net.ServerboundLabelGunUsePacket;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import static ca.teamdman.sfm.common.localization.LocalizationKeys.LABEL_GUN_CHAT_SKIPPED_BLOCKS;

public class LabelGunPlanner {
    public static @Nullable LabelGunPlan getLabelGunPlan(
            Player player,
            ServerboundLabelGunUsePacket msg,
            boolean doWarning
    ) {
        var gunStack = player.getItemInHand(msg.hand());
        var level = player.getLevel();
        if (!(gunStack.getItem() instanceof LabelGunItem)) {
            return null;
        }

        var gunLabels = LabelPositionHolder.from(gunStack).toOwned();

        if (
                !msg.isTargetManagerModifierActive()
                && level.getBlockEntity(msg.pos()) instanceof ManagerBlockEntity manager
        ) {
            return new LabelGunManagerPushOrPullAction(
                    player,
                    level,
                    msg,
                    gunStack,
                    gunLabels,
                    manager
            );
        }

        var activeLabel = LabelGunItem.getActiveLabel(gunStack);
        LabelGunPlanTargets targets = LabelGunPlanTargets.getTargets(level, msg);

        // Notify user if any blocks were skipped because they aren't touching cables
        // TODO: highlight skipped blocks in the world
        if (doWarning && !targets.warnBecauseNoCableNeighbour().isEmpty()) {
            player.sendSystemMessage(LABEL_GUN_CHAT_SKIPPED_BLOCKS.getComponent(
                    targets.warnBecauseNoCableNeighbour().size()
            ));
        }

        if (msg.isClearModifierActive()) {
            return new LabelGunUnsetBlockLabelsAction(
                    player,
                    level,
                    msg,
                    gunStack,
                    gunLabels,
                    targets,
                    activeLabel
            );
        } else {
            if (msg.isPickBlockModifierActive()) {
                return new LabelGunPickLabelAction(
                        player,
                        level,
                        msg,
                        gunStack,
                        gunLabels,
                        targets,
                        activeLabel
                );
            } else {
                return new LabelGunToggleLabelAction(
                        player,
                        level,
                        msg,
                        gunStack,
                        gunLabels,
                        targets,
                        activeLabel
                );
            }
        }
    }
}
