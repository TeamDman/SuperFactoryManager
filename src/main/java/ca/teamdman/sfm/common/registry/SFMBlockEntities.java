package ca.teamdman.sfm.common.registry;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.blockentity.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.GameRegistry;

public final class SFMBlockEntities {

    public static void initialize() {
        register("manager", ManagerBlockEntity.class);
        register("buffer", BufferBlockEntity.class);
        register("tunnelled_manager", TunnelledManagerBlockEntity.class);
    }

    private static void register(String name, Class<? extends TileEntity> clazz) {
        GameRegistry.registerTileEntity(clazz, new ResourceLocation(SFM.MOD_ID, name));
    }
}