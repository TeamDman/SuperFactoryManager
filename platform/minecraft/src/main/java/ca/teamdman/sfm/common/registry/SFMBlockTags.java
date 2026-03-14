package ca.teamdman.sfm.common.registry;

import ca.teamdman.sfm.common.util.SFMResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

///  See also: {@link ca.teamdman.sfm.datagen.SFMBlockTagsDatagen}
public class SFMBlockTags {
    ///  What the anvil must land on for disenchanting to work, traditionally obsidian
    public static final TagKey<Block> ANVIL_DISENCHANTING = BlockTags.create(
            SFMResourceLocation.fromSFMPath("anvil_disenchanting"));

    ///  What the anvil must land on to turn the block into a form, traditionally a metal block
    public static final TagKey<Block> ANVIL_PRINTING_PRESS_FORMING = BlockTags.create(
            SFMResourceLocation.fromSFMPath("anvil_printing_press_forming"));

    @SuppressWarnings("deprecation")
    public static boolean blockHasTag(Block block, TagKey<Block> tag) {
        return block.builtInRegistryHolder().getTagKeys().anyMatch(tag::equals);
    }
}
