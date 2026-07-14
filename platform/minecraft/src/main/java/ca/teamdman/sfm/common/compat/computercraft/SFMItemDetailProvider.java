package ca.teamdman.sfm.common.compat.computercraft;

import ca.teamdman.sfm.common.item.FormItem;
import ca.teamdman.sfm.common.item.LabelGunItem;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import dan200.computercraft.api.detail.DetailProvider;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Adds one namespaced, read-only SFM table to CC:Tweaked's detailed item output.
 */
@MCVersionDependentBehaviour // CC:Tweaked 1.108.0+
final class SFMItemDetailProvider implements DetailProvider<ItemStack> {
    @Override
    public void provideDetails(
            @Nonnull Map<? super String, Object> data,
            @Nonnull ItemStack stack
    ) {

        Map<String, Object> sfm = new LinkedHashMap<>();
        if (stack.getItem() instanceof DiskItem) {
            sfm.put("kind", "program_disk");
            sfm.putAll(SFMComputerCraftData.diskDetails(stack));
        } else if (stack.getItem() instanceof LabelGunItem) {
            sfm.put("kind", "label_gun");
            SFMComputerCraftData.putBoundedText(sfm, "activeLabel", LabelGunItem.getActiveLabel(stack));
            sfm.put("viewMode", LabelGunItem.getViewModeReadOnly(stack).name().toLowerCase(Locale.ROOT));
            SFMComputerCraftData.putLabelDetails(sfm, LabelPositionHolder.fromReadOnly(stack));
        } else if (stack.getItem() instanceof FormItem) {
            sfm.put("kind", "printing_form");
            sfm.put("reference", SFMComputerCraftData.itemSummary(FormItem.getReferenceFromFormReadOnly(stack)));
        } else {
            return;
        }
        data.put("sfm", sfm);
    }
}
