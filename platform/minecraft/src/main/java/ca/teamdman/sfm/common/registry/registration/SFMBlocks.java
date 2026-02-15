package ca.teamdman.sfm.common.registry.registration;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.CommonProxy;
import ca.teamdman.sfm.common.block.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import vswe.superfactory.SuperFactoryManager;
import vswe.superfactory.blocks.*;
import vswe.superfactory.interfaces.IItemBlockProvider;
import vswe.superfactory.registry.ClusterRegistry;
import vswe.superfactory.tiles.*;

public class SFMBlocks {

    public static BlockCable CABLE;
    public static Block CABLE_BREAKER;
    public static Block CABLE_BUD;
    public static BlockCableCamouflages CABLE_CAMOUFLAGE;
    public static Block CABLE_CLUSTER;
    public static Block CABLE_INPUT;
    public static Block CABLE_INTAKE;
    public static Block CABLE_OUTPUT;
    public static Block CABLE_RELAY;
    public static Block CABLE_SIGN;
    public static Block MANAGER;
    public static ManagerBlock MANAGER_BLOCK;


    public static void initialize() {
        MANAGER_BLOCK = prepareRegister(new ManagerBlock(), "manager_advanced");
//        BUFFER_BLOCK = prepareRegister(new BufferBlock(BufferBlockTier.Basic), "buffer", ItemBlock::new);

        MANAGER = prepareRegister(new BlockManager(), "manager");
        CABLE = prepareRegister(new BlockCable(), "cable");
        CABLE_RELAY = prepareRegister(new BlockCableRelay(), "cable_relay");
        CABLE_OUTPUT = prepareRegister(new BlockCableOutput(), "cable_output");
        CABLE_INPUT = prepareRegister(new BlockCableInput(), "cable_input");
        CABLE_INTAKE = prepareRegister(new BlockCableIntake(), "cable_intake");
        CABLE_BUD = prepareRegister(new BlockCableBUD(), "cable_bud");
        CABLE_BREAKER = prepareRegister(new BlockCableBreaker(), "cable_breaker");
        CABLE_CLUSTER = prepareRegister(new BlockCableCluster(), "cable_cluster");
        CABLE_CAMOUFLAGE = prepareRegister(new BlockCableCamouflages(), "cable_camouflage");
        CABLE_SIGN = prepareRegister(new BlockCableSign(), "cable_sign");


    }

    private static <T extends Block> T prepareRegister(T block, String name) {
        block.setRegistryName(SFM.MOD_ID, name).setTranslationKey(SFM.LOCALIZATION_KEY + "." + name);
        block.setCreativeTab(SuperFactoryManager.creativeTab);
        ItemBlock itemBlock = block instanceof IItemBlockProvider provider ? provider.getItem() : new ItemBlock(block);
        itemBlock.setRegistryName(block.getRegistryName());
        SFMItems.ITEM_BLOCKS.add(itemBlock);
        return register(block);
    }

    private static <T extends Block> T register(T block) {
        CommonProxy.registryPrimer.register(block);
        return block;
    }

    public static void registerClusters() {
        ClusterRegistry.register(TileEntityBreaker.class, (BlockContainer) SFMBlocks.CABLE_BREAKER);
        ClusterRegistry.register(TileEntityBUD.class, (BlockContainer) SFMBlocks.CABLE_BUD);
        ClusterRegistry.register(new ClusterRegistry.ClusterRegistryMetaSensitive(TileEntityCamouflage.class, (BlockContainer) SFMBlocks.CABLE_CAMOUFLAGE, new ItemStack(SFMBlocks.CABLE_CAMOUFLAGE ,1,0)));
        ClusterRegistry.register(new ClusterRegistry.ClusterRegistryAdvancedSensitive(TileEntityCamouflage.class, (BlockContainer) SFMBlocks.CABLE_CAMOUFLAGE, new ItemStack(SFMBlocks.CABLE_CAMOUFLAGE ,1,1)));
        ClusterRegistry.register(new ClusterRegistry.ClusterRegistryAdvancedSensitive(TileEntityCamouflage.class, (BlockContainer) SFMBlocks.CABLE_CAMOUFLAGE, new ItemStack(SFMBlocks.CABLE_CAMOUFLAGE ,1,2)));
        ClusterRegistry.register(TileEntityInput.class, (BlockContainer) SFMBlocks.CABLE_INPUT);
        ClusterRegistry.register(TileEntityIntake.class, (BlockContainer) SFMBlocks.CABLE_INTAKE);
        ClusterRegistry.register(TileEntityOutput.class, (BlockContainer) SFMBlocks.CABLE_OUTPUT);
        ClusterRegistry.register(TileEntityRelay.class, (BlockContainer) SFMBlocks.CABLE_RELAY);
        ClusterRegistry.register(TileEntitySignUpdater.class, (BlockContainer) SFMBlocks.CABLE_SIGN);
    }
}
