package ca.teamdman.sfm.common.authorization;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.net.SFMPacketHandlingContext;
import ca.teamdman.sfm.common.util.SFMEntityUtils;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Server-thread authority for future operator manager reads and edits. */
public final class SFMManagerOperatorAuthorization {
    private SFMManagerOperatorAuthorization() {}

    public enum Status {
        ALLOWED,
        NO_SENDER,
        NOT_SERVER_THREAD,
        DISCONNECTED_SENDER,
        INACTIVE_SENDER,
        SPECTATOR,
        NOT_OPERATOR,
        WRONG_DIMENSION,
        TARGET_UNLOADED,
        NOT_MANAGER,
        STALE_MANAGER
    }

    /** Use an allowed manager only during the same server-thread operation. */
    public record Decision(Status status, @Nullable ManagerBlockEntity manager) {
        public Decision {
            Objects.requireNonNull(status, "status");
            if ((status == Status.ALLOWED) != (manager != null)) {
                throw new IllegalArgumentException("Only an allowed decision may carry a manager");
            }
        }

        public boolean allowed() {
            return status == Status.ALLOWED;
        }
    }

    /** The caller cannot supply a claimed player identity; Forge supplies the actual sender. */
    public static Decision authorize(
            SFMPacketHandlingContext context,
            ResourceLocation dimension,
            BlockPos position
    ) {
        Objects.requireNonNull(context, "context");
        return authorizeSender(context.sender(), dimension, position);
    }

    static Decision authorizeSender(
            @Nullable ServerPlayer sender,
            ResourceLocation dimension,
            BlockPos position
    ) {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(position, "position");
        if (sender == null) return denied(Status.NO_SENDER);
        MinecraftServer server = sender.getServer();
        if (server == null) return denied(Status.DISCONNECTED_SENDER);
        if (!server.isSameThread()) return denied(Status.NOT_SERVER_THREAD);
        if (server.getPlayerList().getPlayer(sender.getUUID()) != sender
            || sender.connection == null
            || !sender.connection.getConnection().isConnected()) {
            return denied(Status.DISCONNECTED_SENDER);
        }
        return authorizeConnectedSender(sender, dimension, position);
    }

    // Kept package-private so no packet handler can skip the connected-sender check.
    static Decision authorizeConnectedSender(
            ServerPlayer sender,
            ResourceLocation dimension,
            BlockPos position
    ) {
        if (!sender.isAlive()) return denied(Status.INACTIVE_SENDER);
        if (sender.isSpectator()) return denied(Status.SPECTATOR);
        if (!sender.hasPermissions(Commands.LEVEL_GAMEMASTERS)) return denied(Status.NOT_OPERATOR);

        ServerLevel level = SFMEntityUtils.getLevel(sender);
        if (!level.dimension().location().equals(dimension)) return denied(Status.WRONG_DIMENSION);
        if (!level.isLoaded(position)) return denied(Status.TARGET_UNLOADED);
        if (!(level.getBlockEntity(position) instanceof ManagerBlockEntity manager)) {
            return denied(Status.NOT_MANAGER);
        }
        if (manager.isRemoved()) return denied(Status.STALE_MANAGER);
        return new Decision(Status.ALLOWED, manager);
    }

    private static Decision denied(Status status) {
        return new Decision(status, null);
    }
}
