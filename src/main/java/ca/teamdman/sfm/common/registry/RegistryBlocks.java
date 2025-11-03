
/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 ******************************************************************************/

package ca.teamdman.sfm.common.registry;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.CommonProxy;
import ca.teamdman.sfm.common.block.BufferBlock;
import ca.teamdman.sfm.common.block.CableBlock;
import ca.teamdman.sfm.common.block.CableFacadeBlock;
import ca.teamdman.sfm.common.block.FancyCableBlock;
import ca.teamdman.sfm.common.block.FancyCableFacadeBlock;
import ca.teamdman.sfm.common.block.ManagerBlock;
import ca.teamdman.sfm.common.block.PrintingPressBlock;
import ca.teamdman.sfm.common.block.TestBarrelBlock;
import ca.teamdman.sfm.common.block.TestBarrelTankBlock;
import ca.teamdman.sfm.common.block.TunnelledManagerBlock;
import ca.teamdman.sfm.common.blockentity.BufferBlockEntity;
import ca.teamdman.sfm.common.blockentity.CableBlockEntity;
import ca.teamdman.sfm.common.blockentity.CableFacadeBlockEntity;
import ca.teamdman.sfm.common.blockentity.FancyCableBlockEntity;
import ca.teamdman.sfm.common.blockentity.FancyCableFacadeBlockEntity;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.blockentity.PrintingPressBlockEntity;
import ca.teamdman.sfm.common.blockentity.TestBarrelBlockEntity;
import ca.teamdman.sfm.common.blockentity.TestBarrelTankBlockEntity;
import ca.teamdman.sfm.common.blockentity.TunnelledManagerBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.GameRegistry;
import vswe.superfactory.interfaces.IItemBlockProvider;


import static ca.teamdman.sfm.common.lib.BlocksSFM.*;

/**
 * This class is part of the Modular Machinery Mod
 * The complete source code for this mod can be found on github.
 * Class: RegistryBlocks
 * Created by HellFirePvP
 * Date: 28.06.2017 / 20:22
 */
public class RegistryBlocks {

    public static void initialize() {
        registerBlocks();
        registerTiles();
    }

    private static void registerBlocks() {
        MANAGER_BLOCK = prepareRegister(new ManagerBlock());
        BUFFER_BLOCK = prepareRegister(new BufferBlock());
        TUNNELLED_MANAGER_BLOCK = prepareRegister(new TunnelledManagerBlock());
        PRINTING_PRESS_BLOCK = prepareRegister(new PrintingPressBlock());
        TEST_BARREL_BLOCK = prepareRegister(new TestBarrelBlock());
        TEST_BARREL_TANK_BLOCK = prepareRegister(new TestBarrelTankBlock());
        CABLE_BLOCK = prepareRegister(new CableBlock());
        CABLE_FACADE_BLOCK = prepareRegister(new CableFacadeBlock());
        FANCY_CABLE_BLOCK = prepareRegister(new FancyCableBlock());
        FANCY_CABLE_FACADE_BLOCK = prepareRegister(new FancyCableFacadeBlock());
    }

    private static void registerTiles() {
        registerTile(ManagerBlockEntity.class);
        registerTile(BufferBlockEntity.class);
        registerTile(TunnelledManagerBlockEntity.class);
        registerTile(PrintingPressBlockEntity.class);
        registerTile(TestBarrelBlockEntity.class);
        registerTile(TestBarrelTankBlockEntity.class);
        registerTile(CableBlockEntity.class);
        registerTile(CableFacadeBlockEntity.class);
        registerTile(FancyCableBlockEntity.class);
        registerTile(FancyCableFacadeBlockEntity.class);
    }

    private static void registerTile(Class<? extends TileEntity> tile, String name) {
        GameRegistry.registerTileEntity(tile, new ResourceLocation(SFM.MOD_ID, name));
    }

    private static void registerTile(Class<? extends TileEntity> tile) {
        registerTile(tile, tile.getSimpleName().toLowerCase());
    }


    private static <T extends Block> T prepareRegister(T block) {
        String name = block.getClass().getSimpleName().toLowerCase();
        block.setRegistryName(SFM.MOD_ID, name).setTranslationKey(SFM.MOD_ID + '.' + name);

        return prepareRegisterWithCustomName(block);
    }

    private static <T extends Block> T prepareRegisterWithCustomName(T block) {
        CommonProxy.registryPrimer.register(block);
        if (block instanceof IItemBlockProvider itemProvider) {
            RegistryItems.ITEM_BLOCKS.add(itemProvider.getItem());
        } else {
            RegistryItems.ITEM_BLOCKS.add(new ItemBlock(block));
        }
        return block;
    }
}

