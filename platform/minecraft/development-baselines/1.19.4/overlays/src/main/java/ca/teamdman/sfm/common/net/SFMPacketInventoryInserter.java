package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.item.PacketItem;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.common.value.SFMValue;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** One-shot server-thread insertion into an exactly addressed item handler. */
public final class SFMPacketInventoryInserter {
    private SFMPacketInventoryInserter() {
    }

    /** Internal outcomes only; packet insertion has no acknowledgement protocol. */
    public enum Result {
        INSERTED,
        EFFECTS_DISABLED,
        TARGET_UNAUTHORIZED,
        RATE_LIMITED,
        UNSUPPORTED_CODEC_VERSION,
        NOT_SERVER_THREAD,
        DIMENSION_NOT_FOUND,
        POSITION_NOT_LOADED,
        ITEM_HANDLER_NOT_FOUND,
        INVENTORY_REJECTED,
        ITEM_HANDLER_ERROR
    }

    @MCVersionDependentBehaviour
    public static Result insert(
            MinecraftServer server,
            SFMPacketInventoryAddress target,
            SFMValue value
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(value, "value");

        if (!server.isSameThread()) {
            return Result.NOT_SERVER_THREAD;
        }

        ResourceKey<Level> dimension = ResourceKey.create(
                Registries.DIMENSION,
                target.dimension()
        );
        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            return Result.DIMENSION_NOT_FOUND;
        }
        if (!level.isLoaded(target.position())) {
            return Result.POSITION_NOT_LOADED;
        }

        BlockEntity blockEntity = level.getBlockEntity(target.position());
        if (blockEntity == null) {
            return Result.ITEM_HANDLER_NOT_FOUND;
        }

        IItemHandler handler;
        try {
            handler = lookupRequestedSide(
                    target.side(),
                    side -> blockEntity
                            .getCapability(ForgeCapabilities.ITEM_HANDLER, side)
                            .resolve()
                            .orElse(null)
            );
        } catch (RuntimeException capabilityFailure) {
            return Result.ITEM_HANDLER_ERROR;
        }
        if (handler == null) {
            return Result.ITEM_HANDLER_NOT_FOUND;
        }

        ItemStack packet = PacketItem.create(value);
        try {
            ItemStack remainder = ItemHandlerHelper.insertItem(handler, packet, false);
            return remainder.isEmpty() ? Result.INSERTED : Result.INVENTORY_REJECTED;
        } catch (RuntimeException insertionFailure) {
            return Result.ITEM_HANDLER_ERROR;
        }
    }

    /**
     * Makes the requested lookup once. In particular, an empty side means one
     * unsided lookup and never a scan over the six faces.
     */
    static <T> @Nullable T lookupRequestedSide(
            Optional<Direction> requestedSide,
            Function<@Nullable Direction, @Nullable T> lookup
    ) {
        Objects.requireNonNull(requestedSide, "requestedSide");
        Objects.requireNonNull(lookup, "lookup");
        return lookup.apply(requestedSide.orElse(null));
    }
}
