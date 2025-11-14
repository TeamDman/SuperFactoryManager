package ca.teamdman.sfm.common;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.screen.ManagerScreen;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.command.SFMCommand;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.net.SFMPacket;
import ca.teamdman.sfm.common.registry.SFMBlockEntities;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import ca.teamdman.sfm.common.registry.SFMCapabilities;
import ca.teamdman.sfm.common.registry.SFMPackets;
import ca.teamdman.sfm.common.registry.internal.InternalRegistryPrimer;
import ca.teamdman.sfm.common.registry.internal.PrimerEventHandler;
import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.network.IGuiHandler;
import net.minecraftforge.fml.common.network.NetworkRegistry;


import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

public class CommonProxy implements IGuiHandler {

    public static CreativeTabs creativeTabsSFM;
    public static InternalRegistryPrimer registryPrimer;

    public CommonProxy() {
        registryPrimer = new InternalRegistryPrimer();
        MinecraftForge.EVENT_BUS.register(new PrimerEventHandler(registryPrimer));
    }


    public void preInit() {
        creativeTabsSFM = new CreativeTabs(SFM.MOD_ID) {
            @Override
            public ItemStack createIcon() {
                return new ItemStack(SFMBlocks.MANAGER_BLOCK);
            }
        };

        NetworkRegistry.INSTANCE.registerGuiHandler(SFM.MOD_ID, this);

        SFMCapabilities.register();
        SFMBlockEntities.initialize();
        SFMPackets.register();
    }

    public void init() {
//        FuelItemHelper.initialize();
//        IntegrationTypeHelper.filterModIdComponents();
//        IntegrationTypeHelper.filterModIdRequirementTypes();


    }

    public void postInit() {

    }

    public void loadComplete() {

//        CompletableFuture.runAsync(() -> BlockArrayCache.buildCache(MachineRegistry.getLoadedMachines()));
    }

    public void registerBlockModel(Block block) {
    }

    public void registerItemModel(Item item) {
    }

    public void registerItemModelWithCustomName(Item item) {
    }

    @Nullable
    @Override
    public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        GuiType type = GuiType.values()[MathHelper.clamp(ID, 0, GuiType.values().length - 1)];
        Class<? extends TileEntity> required = type.requiredTileEntity;
        TileEntity present = null;
        if (required != null) {
            TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
            if (te != null && required.isAssignableFrom(te.getClass())) {
                present = te;
            } else {
                return null;
            }
        }

        switch (type) {
            case MANAGER -> {
                return new ManagerContainerMenu(ID, player.inventory, (ManagerBlockEntity) present);
            }
        }

        return null;
    }

    @Nullable
    @Override
    public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        if (!world.isRemote) {
            return getServerGuiElement(ID, player, world, x, y, z);
        }
        GuiType type = GuiType.values()[MathHelper.clamp(ID, 0, GuiType.values().length - 1)];
        Class<? extends TileEntity> required = type.requiredTileEntity;
        TileEntity present = null;
        if (required != null) {
            TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
            if (te != null && required.isAssignableFrom(te.getClass())) {
                present = te;
            } else {
                return null;
            }
        }

        switch (type) {
            case MANAGER -> {
                return new ManagerScreen(new ManagerContainerMenu(ID, player.inventory, (ManagerBlockEntity) present));
            }
        }

        return null;
    }

    public enum GuiType {

        MANAGER(ManagerBlockEntity.class),

        ;

        public final Class<? extends TileEntity> requiredTileEntity;

        GuiType(@Nullable Class<? extends TileEntity> requiredTileEntity) {
            this.requiredTileEntity = requiredTileEntity;
        }
    }
}
