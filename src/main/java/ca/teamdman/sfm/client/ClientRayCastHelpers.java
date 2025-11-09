package ca.teamdman.sfm.client;

import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.RayTraceResult;
import org.jetbrains.annotations.Nullable;

public class ClientRayCastHelpers {
    public static @Nullable TileEntity getLookBlockEntity() {
        if (!SFMEnvironmentUtils.isClient()) {
            throw new RuntimeException("getLookBlockEntity must be called on client");
        }
        net.minecraft.client.Minecraft mc = Minecraft.getMinecraft();
        RayTraceResult result = mc.objectMouseOver;
        if (result == null) return null;
        if (result.typeOfHit != RayTraceResult.Type.BLOCK) return null;
        var pos = result.getBlockPos();
        return mc.world.getTileEntity(pos);
    }
}
