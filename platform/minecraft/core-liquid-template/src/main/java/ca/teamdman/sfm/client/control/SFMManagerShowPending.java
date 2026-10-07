package ca.teamdman.sfm.client.control;

import ca.teamdman.sfm.common.net.ClientboundManagerShowPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/** Bounded one-shot correlation; a reply cannot retarget a newer player, world or manager. */
final class SFMManagerShowPending implements AutoCloseable {
    private static final int MAX_PENDING = 16;
    private static final int TIMEOUT_SECONDS = 8;

    static final class CapacityExceededException extends RejectedExecutionException {
        CapacityExceededException() { super("Too many pending manager show requests"); }
    }

    static final class UnavailableException extends IllegalStateException {
        UnavailableException() { super("Manager show requires a connected player in a world"); }
    }

    private record Pending(
            UUID playerId,
            Object player,
            Object connection,
            ResourceLocation worldDimension,
            ResourceLocation targetDimension,
            BlockPos position,
            CompletableFuture<ClientboundManagerShowPacket> result
    ) { }

    private final ConcurrentHashMap<UUID, Pending> requests = new ConcurrentHashMap<>();

    synchronized CompletableFuture<ClientboundManagerShowPacket> register(
            UUID requestId,
            UUID playerId,
            Object player,
            Object connection,
            ResourceLocation worldDimension,
            ResourceLocation targetDimension,
            BlockPos position
    ) {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(playerId);
        Objects.requireNonNull(player);
        Objects.requireNonNull(connection);
        Objects.requireNonNull(worldDimension);
        Objects.requireNonNull(targetDimension);
        Objects.requireNonNull(position);
        if (requests.size() >= MAX_PENDING || requests.containsKey(requestId)) {
            throw new CapacityExceededException();
        }
        CompletableFuture<ClientboundManagerShowPacket> result = new CompletableFuture<>();
        Pending pending = new Pending(playerId, player, connection, worldDimension,
                targetDimension, position.immutable(), result);
        requests.put(requestId, pending);
        result.whenComplete((ignored, failure) -> requests.remove(requestId, pending));
        result.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return result;
    }

    boolean receive(
            ClientboundManagerShowPacket response,
            UUID playerId,
            Object player,
            Object connection,
            ResourceLocation worldDimension
    ) {
        Pending pending = requests.get(response.requestId());
        if (pending == null
            || !pending.targetDimension().equals(response.dimension())
            || !pending.position().equals(response.position())) return false;
        if (!pending.playerId().equals(playerId)
            || pending.player() != player
            || pending.connection() != connection
            || !pending.worldDimension().equals(worldDimension)) {
            if (requests.remove(response.requestId(), pending)) {
                pending.result().completeExceptionally(new IllegalStateException(
                        "Manager show player or world changed while awaiting the server"));
            }
            return false;
        }
        if (!requests.remove(response.requestId(), pending)) return false;
        pending.result().complete(response);
        return true;
    }

    @Override
    public void close() {
        requests.values().forEach(pending -> pending.result().cancel(false));
        requests.clear();
    }
}
