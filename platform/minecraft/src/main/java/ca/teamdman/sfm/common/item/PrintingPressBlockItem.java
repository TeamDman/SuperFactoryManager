package ca.teamdman.sfm.common.item;

import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import net.minecraft.world.item.BlockItem;

public class PrintingPressBlockItem extends BlockItem {
    @SFMLocalizationDatagen
    public static final LocalizationEntry PRINTING_PRESS_TOOLTIP = new LocalizationEntry(
            () -> SFMItems.PRINTING_PRESS.get().getDescriptionId() + ".tooltip",
            () -> "Place with an air gap below a downward facing piston. Extend the piston to use."
    );

    public PrintingPressBlockItem(Properties properties) {

        super(SFMBlocks.PRINTING_PRESS.get(), properties);
    }
}
