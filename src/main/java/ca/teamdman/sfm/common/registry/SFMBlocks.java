package ca.teamdman.sfm.common.registry;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.CommonProxy;
import ca.teamdman.sfm.common.block.*;
import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;

import java.util.function.Function;

public class SFMBlocks {

    public static ManagerBlock MANAGER_BLOCK;
    public static BufferBlock BUFFER_BLOCK;
    public static TunnelledManagerBlock TUNNELLED_MANAGER_BLOCK;
    public static TestBarrelBlock TEST_BARREL_BLOCK;
    public static TestBarrelTankBlock TEST_BARREL_TANK_BLOCK;
    public static CableBlock CABLE_BLOCK;

    public static void initialize() {
        MANAGER_BLOCK = prepareRegister(new ManagerBlock(), "manager", ItemBlock::new);
        BUFFER_BLOCK = prepareRegister(new BufferBlock(BufferBlockTier.Basic), "buffer", ItemBlock::new);
        TUNNELLED_MANAGER_BLOCK = prepareRegister(new TunnelledManagerBlock(), "tunnelled_manager", ItemBlock::new);
        TEST_BARREL_BLOCK = prepareRegister(new TestBarrelBlock(), "test_barrel", ItemBlock::new);
        TEST_BARREL_TANK_BLOCK = prepareRegister(new TestBarrelTankBlock(), "test_barrel_tank", ItemBlock::new);
        CABLE_BLOCK = prepareRegister(new CableBlock(), "cable", ItemBlock::new);
    }

    private static <T extends Block> T prepareRegister(T block, String name, Function<Block, ItemBlock> itemBlockFactory) {
        block.setRegistryName(SFM.MOD_ID, name).setTranslationKey(SFM.MOD_ID + "." + name);
        ItemBlock itemBlock = itemBlockFactory.apply(block);
        itemBlock.setRegistryName(block.getRegistryName());
        SFMItems.ITEM_BLOCKS.add(itemBlock);
        return register(block);
    }

    private static <T extends Block> T register(T block) {
        CommonProxy.registryPrimer.register(block);
        return block;
    }
}
