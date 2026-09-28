package ca.teamdman.sfm.client.control;

import ca.teamdman.sfm.common.net.ClientboundManagerShowPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMManagerShowPendingTests {
    private static final ResourceLocation OVERWORLD = new ResourceLocation("minecraft", "overworld");
    private static final ResourceLocation NETHER = new ResourceLocation("minecraft", "the_nether");
    private static final BlockPos FIRST = new BlockPos(1, 2, 3);
    private static final BlockPos SECOND = new BlockPos(4, 5, 6);

    @Test
    void responseNeedsMatchingRequestManagerDimensionAndConnectedPlayer() {
        UUID request = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        Object player = new Object();
        Object connection = new Object();
        try (SFMManagerShowPending pending = new SFMManagerShowPending()) {
            var result = pending.register(request, playerId, player, connection, OVERWORLD, OVERWORLD, FIRST);
            assertFalse(pending.receive(reply(UUID.randomUUID(), OVERWORLD, FIRST),
                    playerId, player, connection, OVERWORLD));
            assertFalse(pending.receive(reply(request, NETHER, FIRST),
                    playerId, player, connection, OVERWORLD));
            assertFalse(pending.receive(reply(request, OVERWORLD, SECOND),
                    playerId, player, connection, OVERWORLD));
            assertFalse(result.isDone());

            assertFalse(pending.receive(reply(request, OVERWORLD, FIRST),
                    playerId, new Object(), connection, OVERWORLD));
            assertThrows(CompletionException.class, result::join);
            assertFalse(pending.receive(reply(request, OVERWORLD, FIRST),
                    playerId, player, connection, OVERWORLD));
        }
    }

    @Test
    void matchingReplyCompletesOnceAndCannotBeReused() {
        UUID request = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        Object player = new Object();
        Object connection = new Object();
        try (SFMManagerShowPending pending = new SFMManagerShowPending()) {
            var result = pending.register(request, playerId, player, connection, OVERWORLD, OVERWORLD, FIRST);
            var reply = reply(request, OVERWORLD, FIRST);
            assertTrue(pending.receive(reply, playerId, player, connection, OVERWORLD));
            assertSame(reply, result.join());
            assertFalse(pending.receive(reply, playerId, player, connection, OVERWORLD));

            var next = pending.register(UUID.randomUUID(), playerId, player, connection,
                    OVERWORLD, OVERWORLD, FIRST);
            var unrelatedReply = reply(UUID.randomUUID(), OVERWORLD, FIRST);
            assertFalse(pending.receive(unrelatedReply, playerId, player, connection, OVERWORLD));
            assertFalse(next.isDone());
        }
    }

    @Test
    void changedWorldAndConnectionRejectAnOtherwiseMatchingReply() {
        UUID request = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        Object player = new Object();
        Object connection = new Object();
        try (SFMManagerShowPending pending = new SFMManagerShowPending()) {
            var result = pending.register(request, playerId, player, connection, OVERWORLD, OVERWORLD, FIRST);
            assertFalse(pending.receive(reply(request, OVERWORLD, FIRST),
                    playerId, player, new Object(), OVERWORLD));
            assertThrows(CompletionException.class, result::join);

            UUID nextRequest = UUID.randomUUID();
            var next = pending.register(nextRequest, playerId, player, connection, OVERWORLD, OVERWORLD, FIRST);
            assertFalse(pending.receive(reply(nextRequest, OVERWORLD, FIRST),
                    playerId, player, connection, NETHER));
            assertThrows(CompletionException.class, next::join);
        }
    }

    private static ClientboundManagerShowPacket reply(UUID request, ResourceLocation dimension, BlockPos position) {
        return ClientboundManagerShowPacket.denied(request, dimension, position,
                ClientboundManagerShowPacket.Status.NOT_OPERATOR);
    }
}
