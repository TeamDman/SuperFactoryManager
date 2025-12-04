package ca.teamdman.sfm.common.label;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.item.LabelGunItem;
import ca.teamdman.sfm.common.net.ServerboundLabelGunUsePacket;
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
        World world = player.getEntityWorld();
        var gunStack = player.getHeldItem(msg.hand());
        if (!(gunStack.getItem() instanceof LabelGunItem)) {
            return null;
        }

        var gunLabels = LabelPositionHolder.from(gunStack).toOwned();

        if (
                !msg.isTargetManagerModifierActive()
                        && world.getTileEntity(msg.pos()) instanceof ManagerBlockEntity manager
        ) {
            return new LabelGunManagerPushOrPullAction(
                    player,
                    world,
                    msg,
                    gunStack,
                    gunLabels,
                    manager
            );
        }


        if (
                !msg.isTargetManagerModifierActive()
                        && world.getTileEntity(msg.pos()) instanceof TileEntityManager manager
        ) {
            return new LabelGunOldManagerPushOrPullAction(
                    player,
                    world,
                    msg,
                    gunStack,
                    gunLabels,
                    manager
            );
        }

        var activeLabel = LabelGunItem.getActiveLabel(gunStack);
        LabelGunPlanTargets targets = LabelGunPlanTargets.getTargets(world, msg);

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
                    world,
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
                        world,
                        msg,
                        gunStack,
                        gunLabels,
                        targets,
                        activeLabel
                );
            } else {
                return new LabelGunToggleLabelAction(
                        player,
                        world,
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
