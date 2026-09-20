package ca.teamdman.sfm.common.net.multiplayer;

import ca.teamdman.sfm.common.net.*;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.value.SFMValueJsonCodec;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import static ca.teamdman.sfm.common.net.multiplayer.SFMMultiplayerPacketProtocol.*;
import static org.junit.jupiter.api.Assertions.*;

class SFMMultiplayerPacketWireTests {
    private static final UUID SESSION = new UUID(1, 2), LOCAL = new UUID(3, 4), PLAYER = new UUID(5, 6);
    private static final ResourceLocation DIMENSION = new ResourceLocation("minecraft:overworld");
    private static final SFMClientInboxAddress INBOX = new SFMClientInboxAddress(PLAYER, DIMENSION, new ResourceLocation("sfm:test"));
    private static final SFMPacketInventoryAddress TARGET = new SFMPacketInventoryAddress(DIMENSION,
            new BlockPos(7, 8, 9), Optional.of(Direction.NORTH));
    private static final ProgramClaim PROGRAM = new ProgramClaim(new ManagerAddress(DIMENSION, new BlockPos(1, 2, 3)),
            new UUID(9, 10), 5, "a".repeat(64), "b".repeat(64));

    @Test void negotiationAndOfferRoundTripWithoutGrantingPermission() {
        assertEquals(new SFMMultiplayerPacketWire.Negotiate(VERSION, SESSION),
                SFMMultiplayerPacketWire.decodeClient(SFMMultiplayerPacketWire.negotiate(VERSION, SESSION)));
        var offer = new Offer(VERSION, SESSION, MAX_FRAME_BYTES, SFMValueJsonCodec.MAX_ENCODED_UTF8_BYTES);
        assertEquals(new SFMMultiplayerPacketWire.SessionOffer(offer, PLAYER),
                SFMMultiplayerPacketWire.decodeServer(SFMMultiplayerPacketWire.offer(offer, PLAYER)));
    }
    @Test void insertionRetainsExactTargetProgramAndActualFrameLength() {
        byte[] encoded = SFMMultiplayerPacketWire.insert(SESSION, 42, TARGET, Optional.of(PROGRAM), SFMValue.of(0.25));
        var decoded = assertInstanceOf(SFMMultiplayerPacketWire.Insert.class, SFMMultiplayerPacketWire.decodeClient(encoded));
        assertEquals(new Request(SESSION, 42, encoded.length), decoded.request());
        assertEquals(new InventoryScope(TARGET), decoded.target());
        assertEquals(Optional.of(PROGRAM), decoded.program());
        assertNull(decoded.payload().preflight(encoded.length));
        assertEquals(SFMValue.of(0.25), decoded.payload().decode());
    }
    @Test void subscriptionsKeepConnectionNonceSeparateFromLocalInboxEpoch() {
        for (boolean subscribe : new boolean[]{true, false}) {
            byte[] encoded = SFMMultiplayerPacketWire.subscription(SESSION, 7, LOCAL, INBOX, Optional.of(PROGRAM), subscribe);
            var value = assertInstanceOf(SFMMultiplayerPacketWire.Subscription.class, SFMMultiplayerPacketWire.decodeClient(encoded));
            assertEquals(LOCAL, value.localInboxSession());
            assertEquals(PLAYER, value.recipient());
            assertEquals(SESSION, value.request().session());
            assertEquals(Optional.of(PROGRAM), value.program());
            assertEquals(subscribe, value.subscribe());
        }
    }
    @Test void acknowledgementCarriesOnlyActualInsertionAttemptResult() {
        var ack = new Acknowledgement(SESSION, 9, Status.INSERTION_ATTEMPTED, Optional.of(SFMPacketInventoryInserter.Result.INVENTORY_REJECTED));
        assertEquals(new SFMMultiplayerPacketWire.Result(ack), SFMMultiplayerPacketWire.decodeServer(SFMMultiplayerPacketWire.result(ack)));
        assertThrows(IllegalArgumentException.class, () -> new Acknowledgement(SESSION, 9, Status.AUTHORITY_DENIED,
                Optional.of(SFMPacketInventoryInserter.Result.INSERTED)));
    }
    @Test void inboxDeliveryPreservesRecipientAddressAndValue() {
        var result = assertInstanceOf(SFMMultiplayerPacketWire.InboxValue.class,
                SFMMultiplayerPacketWire.decodeServer(SFMMultiplayerPacketWire.inbox(SESSION, LOCAL, INBOX, SFMValue.of("frame"))));
        assertEquals(SESSION, result.session());
        assertEquals(LOCAL, result.localInboxSession());
        assertEquals(INBOX, result.address());
        assertEquals(SFMValue.of("frame"), result.value().currentValue().orElseThrow());
    }
    @Test void envelopeLengthCheckedBeforeCopyAndHeaderDecode() {
        for (int length : new int[]{0, MAX_FRAME_BYTES + 1}) {
            var buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(new byte[length]));
            try {
                assertThrows(IllegalArgumentException.class, () -> SFMMultiplayerPacketWire.copyFrame(buffer));
                assertEquals(0, buffer.readerIndex());
            } finally { buffer.release(); }
        }
    }
    @Test void unknownKindsTruncationAndTrailingBytesAreRejected() {
        assertThrows(RuntimeException.class, () -> SFMMultiplayerPacketWire.decodeClient(new byte[]{127}));
        byte[] negotiation = SFMMultiplayerPacketWire.negotiate(VERSION, SESSION);
        assertThrows(RuntimeException.class, () -> SFMMultiplayerPacketWire.decodeClient(Arrays.copyOf(negotiation, negotiation.length - 1)));
        assertThrows(IllegalArgumentException.class, () -> SFMMultiplayerPacketWire.decodeClient(Arrays.copyOf(negotiation, negotiation.length + 1)));
        byte[] result = SFMMultiplayerPacketWire.result(new Acknowledgement(SESSION, 1, Status.AUTHORITY_DENIED, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> SFMMultiplayerPacketWire.decodeServer(Arrays.copyOf(result, result.length + 1)));
    }
    @Test void malformedJsonIsNotParsedDuringUntrustedHeaderDecoding() {
        byte[] encoded = SFMMultiplayerPacketWire.insert(SESSION, 1, TARGET, Optional.empty(), SFMValue.of("ok"));
        encoded[encoded.length - 1] = (byte)'!';
        var header = assertInstanceOf(SFMMultiplayerPacketWire.Insert.class, SFMMultiplayerPacketWire.decodeClient(encoded));
        assertThrows(IllegalArgumentException.class, () -> header.payload().decode());
    }
    @Test void deferredValueUsesOwnedFrameSnapshot() {
        byte[] encoded = SFMMultiplayerPacketWire.insert(SESSION, 1, TARGET, Optional.empty(), SFMValue.of("ok"));
        var header = assertInstanceOf(SFMMultiplayerPacketWire.Insert.class, SFMMultiplayerPacketWire.decodeClient(encoded));
        Arrays.fill(encoded, (byte) '!');
        assertEquals(SFMValue.of("ok"), header.payload().decode());
    }
}
