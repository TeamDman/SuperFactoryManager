package ca.teamdman.sfm.client.tutorial;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public final class SFMTutorialClientContext {
    private static @Nullable BlockPos chamberOrigin = null;
    private static @Nullable ResourceLocation chamberId = null;

    private SFMTutorialClientContext() {
    }

    public static void clear() {
        chamberOrigin = null;
        chamberId = null;
    }

    public static void set(
            @Nullable BlockPos origin,
            @Nullable ResourceLocation currentChamberId
    ) {
        chamberOrigin = origin;
        chamberId = currentChamberId;
    }

    public static Optional<BlockPos> getChamberOrigin() {
        return Optional.ofNullable(chamberOrigin);
    }

    public static Optional<ResourceLocation> getChamberId() {
        return Optional.ofNullable(chamberId);
    }
}
