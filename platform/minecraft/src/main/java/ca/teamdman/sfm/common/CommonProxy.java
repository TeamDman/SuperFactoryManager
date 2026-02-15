package ca.teamdman.sfm.common;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.registry.*;
import ca.teamdman.sfm.common.registry.internal.InternalRegistryPrimer;
import ca.teamdman.sfm.common.registry.internal.PrimerEventHandler;
import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMCapabilities;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
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

        SFMPackets.registerChannels();
        SFMPackets.register();
//        MessageHandler.init();

        NetworkRegistry.INSTANCE.registerGuiHandler(SFM.MOD_ID, this);

        SFMCapabilities.register();
        SFMBlockEntities.initialize();
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


    public void registerItemModel(Item item) {
    }

    public void registerItemModelWithCustomName(Item item) {
    }

    @Nullable
    @Override
    public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        GuiType type = GuiType.values()[MathHelper.clamp(ID, 0, GuiType.values().length - 1)];
        Class<?> required = type.requiredTileEntity;
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
            case PROVIDER -> {
                return ((IGuiProvider) present).getContainer(ID, player.inventory);
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
        Class<?> required = type.requiredTileEntity;
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
            case PROVIDER -> {
                return ((IGuiProvider) present).getGui(ID, player.inventory);
            }
        }

        return null;
    }

    public enum GuiType {
        PROVIDER(IGuiProvider.class),
//        MANAGER(ManagerBlockEntity.class),
//        OLD_MANAGER(TileEntityManager.class)
        ;

        public final @Nullable Class<?> requiredTileEntity;

        GuiType(@Nullable Class<?> requiredTileEntity) {
            this.requiredTileEntity = requiredTileEntity;
        }
    }
}
