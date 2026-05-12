package ca.teamdman.sfm.common.item;

import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import net.minecraft.world.item.Item;

public class ExperienceShardItem extends Item {
    @SFMLocalizationDatagen
    public static final LocalizationEntry EXPERIENCE_SHARD_ITEM = new LocalizationEntry(
            () -> SFMItems.EXPERIENCE_SHARD.get().getDescriptionId(),
            () -> "Experience Shard"
    );

    public ExperienceShardItem(Item.Properties properties) {

        super(properties);
    }

}
