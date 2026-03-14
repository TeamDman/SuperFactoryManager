package ca.teamdman.sfm.common.label;

import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.net.ServerboundLabelGunUsePacket;
import ca.teamdman.sfm.common.util.ConfirmationParams;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.mutable.MutableInt;
import org.jetbrains.annotations.Nullable;

public record LabelGunUnsetBlockLabelsAction(
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
        // we are removing labels
        if (msg.isPickBlockModifierActive()) {
            targets.positions().forEach(p -> gunLabels.remove(activeLabel, p));
        } else {
            targets.positions().forEach(gunLabels::removeAll);
        }
        gunLabels.save(gunStack);
    }

    @Override
    public @Nullable ConfirmationParams getConfirmation() {
        if (targets.positions().size() <= 1) { // TODO: make this a client config
            return null;
        }
        if (msg.isPickBlockModifierActive()) {
            return ConfirmationParams.of(
                    LocalizationKeys.REMOVE_ACTIVE_LABEL_CONFIRM_SCREEN_TITLE.getComponent(activeLabel),
                    LocalizationKeys.REMOVE_ACTIVE_LABEL_CONFIRM_SCREEN_MESSAGE.getComponent(
                            activeLabel,
                            targets.positions().size()
                    )
            );
        } else {
            MutableInt totalLabels = new MutableInt(0);
            gunLabels.forEach((label, pos) -> {
                if (targets.positions().contains(pos)) {
                    totalLabels.increment();
                }
            });
            return ConfirmationParams.of(
                    LocalizationKeys.REMOVE_ALL_LABELS_CONFIRM_SCREEN_TITLE.getComponent(),
                    LocalizationKeys.REMOVE_ALL_LABELS_CONFIRM_SCREEN_MESSAGE.getComponent(
                            totalLabels,
                            targets.positions().size()
                    )
            );
        }
    }
}
