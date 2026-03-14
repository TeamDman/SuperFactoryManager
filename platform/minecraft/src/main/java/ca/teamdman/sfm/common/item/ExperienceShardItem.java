package ca.teamdman.sfm.common.item;

import ca.teamdman.sfm.common.registry.SFMCreativeTabs;
import net.minecraft.world.item.Item;

public class ExperienceShardItem extends Item {
    public ExperienceShardItem() {
        super(new Item.Properties().tab(SFMCreativeTabs.TAB));
    }
}
