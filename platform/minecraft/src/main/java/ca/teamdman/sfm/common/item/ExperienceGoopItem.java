package ca.teamdman.sfm.common.item;

import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import net.minecraft.world.item.Item;

public class ExperienceGoopItem extends Item {
    @SFMLocalizationDatagen
    public static final LocalizationEntry EXPERIENCE_GOOP_ITEM = new LocalizationEntry(
            () -> SFMItems.EXPERIENCE_GOOP.get().getDescriptionId(),
            () -> "Experience Goop"
    );

    public ExperienceGoopItem(Item.Properties properties) {

        super(properties);
    }

}
