package ca.teamdman.sfm.common.registry.registration;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.blockentity.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.GameRegistry;
//import vswe.superfactory.SuperFactoryManager;
import vswe.superfactory.tiles.*;

public final class SFMBlockEntities {

    public static void initialize() {
        register("manager_advanced", ManagerBlockEntity.class);

        register("manager", TileEntityManager.class );
        register("cable_relay", TileEntityRelay.class );
        register("cable_output", TileEntityOutput.class );
        register("cable_input", TileEntityInput.class );
        register("cable_intake", TileEntityIntake.class );
        register("cable_bud", TileEntityBUD.class );
        register("cable_breaker", TileEntityBreaker.class );
        register("cable_cluster", TileEntityCluster.class );
        register("cable_camouflage", TileEntityCamouflage.class );
        register("cable_sign", TileEntitySignUpdater.class );
    }

    private static void register(String name, Class<? extends TileEntity> clazz) {
        GameRegistry.registerTileEntity(clazz, new ResourceLocation(SFM.MOD_ID, name));
    }
}