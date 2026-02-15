package ca.teamdman.sfm.common.label;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.item.LabelGunItem;
import ca.teamdman.sfm.common.net.ServerboundLabelGunUsePacket;
import ca.teamdman.sfm.common.util.SFMEntityUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import vswe.superfactory.tiles.TileEntityManager;

import static ca.teamdman.sfm.common.localization.LocalizationKeys.LABEL_GUN_CHAT_SKIPPED_BLOCKS;

public class LabelGunPlanner {
    public static @Nullable LabelGunPlan getLabelGunPlan(
            EntityPlayer player,
            ServerboundLabelGunUsePacket msg,
            boolean doWarning
    ) {
        var gunStack = player.getHeldItem(msg.hand());
        World level = SFMEntityUtils.getLevel(player);
        if (!(gunStack.getItem() instanceof LabelGunItem)) {
            return null;
        }

        var gunLabels = LabelPositionHolder.from(gunStack).toOwned();

        if (
                !msg.isTargetManagerModifierActive()
                        && level.getTileEntity(msg.pos()) instanceof ManagerBlockEntity manager
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


        if (
                !msg.isTargetManagerModifierActive()
                        && level.getTileEntity(msg.pos()) instanceof TileEntityManager manager
        ) {
            return new LabelGunOldManagerPushOrPullAction(
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
            player.sendStatusMessage(LABEL_GUN_CHAT_SKIPPED_BLOCKS.getComponent(
                    targets.warnBecauseNoCableNeighbour().size()
            ), false);
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
