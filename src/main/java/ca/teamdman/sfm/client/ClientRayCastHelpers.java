package ca.teamdman.sfm.client;

import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

public class ClientRayCastHelpers {
    public static @Nullable BlockEntity getLookBlockEntity() {
        if (!SFMEnvironmentUtils.isClient()) {
            throw new IllegalCallerException("getLookBlockEntity must be called on client");
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return null;
        HitResult hr = Minecraft.getInstance().hitResult;
        if (hr == null) return null;
        if (hr.getType() != HitResult.Type.BLOCK) return null;
        var pos = ((BlockHitResult) hr).getBlockPos();
        return level.getBlockEntity(pos);
    }
}
