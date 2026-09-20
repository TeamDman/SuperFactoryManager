package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.value.SFMValueJsonCodec;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMPacketContractTests {
    @Test
    void valueEnvelopeRoundTripsCanonicalJsonAtTheExactByteLimit() {
        SFMValue value = SFMValue.of("a".repeat(SFMValueJsonCodec.MAX_ENCODED_UTF8_BYTES - 2));
        SFMPacketValueEnvelope envelope = SFMPacketValueEnvelope.fromValue(value);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        envelope.encode(buffer);

        assertEquals(envelope, SFMPacketValueEnvelope.decode(buffer));
        assertEquals(Optional.of(value), envelope.currentValue());
        assertEquals(0, buffer.readableBytes());
    }

    @Test
    void versionOneEnvelopeStillDispatchesWhileVersionTwoCarriesDoubles() {
        SFMPacketValueEnvelope old = new SFMPacketValueEnvelope(1, "{\"count\":9007199254740993}");
        FriendlyByteBuf oldBuffer = new FriendlyByteBuf(Unpooled.buffer());
        old.encode(oldBuffer);
        SFMPacketValueEnvelope copiedOld = SFMPacketValueEnvelope.decode(oldBuffer);
        SFMValue exact = SFMValue.object(Map.of("count", SFMValue.of(9_007_199_254_740_993L)));
        AtomicReference<SFMValue> received = new AtomicReference<>();

        assertEquals(old, copiedOld);
        assertEquals(Optional.of(exact), copiedOld.currentValue());
        assertEquals(
                SFMPacketValueDispatch.Result.DISPATCHED,
                SFMPacketValueDispatch.dispatch(true, copiedOld, received::set)
        );
        assertEquals(exact, received.get());
        assertThrows(IllegalArgumentException.class, () -> new SFMPacketValueEnvelope(1, "1.0"));

        SFMPacketValueEnvelope current = SFMPacketValueEnvelope.fromValue(SFMValue.of(0.5));
        assertEquals(2, current.codecVersion());
        assertEquals("0.5", current.canonicalJson());
        assertEquals(Optional.of(SFMValue.of(0.5)), current.currentValue());
    }

    @Test
    void widenedFloatPreservesItsExactBinaryValueAcrossTheEnvelopeWire() {
        double widened = 0.731f;
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        SFMPacketValueEnvelope.fromValue(SFMValue.of(widened)).encode(buffer);

        SFMValue.DoubleValue decoded = (SFMValue.DoubleValue) SFMPacketValueEnvelope
                .decode(buffer)
                .currentValue()
                .orElseThrow();

        assertEquals(Double.doubleToLongBits(widened), Double.doubleToLongBits(decoded.value()));
    }

    @Test
    void valueEnvelopeRejectsNonCanonicalMalformedOversizeAndTruncatedPayloads() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SFMPacketValueEnvelope(
                        SFMValueJsonCodec.VERSION,
                        "{\"z\":0,\"a\":1}"
                )
        );

        FriendlyByteBuf malformed = new FriendlyByteBuf(Unpooled.buffer());
        malformed.writeVarInt(SFMValueJsonCodec.VERSION);
        malformed.writeVarInt(2);
        malformed.writeByte(0xC3);
        malformed.writeByte(0x28);
        assertThrows(IllegalArgumentException.class, () -> SFMPacketValueEnvelope.decode(malformed));

        FriendlyByteBuf oversize = new FriendlyByteBuf(Unpooled.buffer());
        oversize.writeVarInt(SFMValueJsonCodec.VERSION);
        oversize.writeVarInt(SFMValueJsonCodec.MAX_ENCODED_UTF8_BYTES + 1);
        assertThrows(IllegalArgumentException.class, () -> SFMPacketValueEnvelope.decode(oversize));

        FriendlyByteBuf truncated = new FriendlyByteBuf(Unpooled.buffer());
        truncated.writeVarInt(SFMValueJsonCodec.VERSION);
        truncated.writeVarInt(2);
        truncated.writeByte('x');
        assertThrows(IllegalArgumentException.class, () -> SFMPacketValueEnvelope.decode(truncated));
    }

    @Test
    void unsupportedEnvelopeVersionSurvivesTransportButCannotDispatch() {
        SFMPacketValueEnvelope future = new SFMPacketValueEnvelope(
                SFMValueJsonCodec.VERSION + 1,
                "future-format"
        );
        AtomicReference<SFMValue> received = new AtomicReference<>();

        assertTrue(future.currentValue().isEmpty());
        assertEquals(
                SFMPacketValueDispatch.Result.UNSUPPORTED_CODEC_VERSION,
                SFMPacketValueDispatch.dispatch(true, future, received::set)
        );
        assertNull(received.get());
    }

    @Test
    void effectGateIsRecheckedBeforeDispatch() {
        SFMPacketValueEnvelope value = SFMPacketValueEnvelope.fromValue(SFMValue.of("hello"));
        AtomicReference<SFMValue> received = new AtomicReference<>();

        assertEquals(
                SFMPacketValueDispatch.Result.EFFECTS_DISABLED,
                SFMPacketValueDispatch.dispatch(false, value, received::set)
        );
        assertNull(received.get());

        assertEquals(
                SFMPacketValueDispatch.Result.DISPATCHED,
                SFMPacketValueDispatch.dispatch(true, value, received::set)
        );
        assertEquals(SFMValue.of("hello"), received.get());
    }

    @Test
    void observationAndInsertionContractsRoundTripWithExplicitDirections() {
        SFMValue value = SFMValue.object(Map.of(
                "job", SFMValue.of(42),
                "parts", SFMValue.array(List.of(SFMValue.of(true), SFMValue.nullValue()))
        ));
        ClientboundPacketObservationPacket observation =
                ClientboundPacketObservationPacket.fromValue(value);
        ClientboundPacketObservationPacket.Daddy observationDaddy =
                new ClientboundPacketObservationPacket.Daddy();
        FriendlyByteBuf observationBuffer = new FriendlyByteBuf(Unpooled.buffer());
        observationDaddy.encode(observation, observationBuffer);

        assertEquals(SFMPacketDaddy.PacketDirection.CLIENTBOUND, observationDaddy.getPacketDirection());
        assertEquals(observation, observationDaddy.decode(observationBuffer));

        SFMPacketInventoryAddress sidedAddress = new SFMPacketInventoryAddress(
                new ResourceLocation("minecraft", "overworld"),
                new BlockPos(12, -34, 56),
                Optional.of(Direction.WEST)
        );
        ServerboundPacketInsertionPacket insertion =
                ServerboundPacketInsertionPacket.fromValue(sidedAddress, value);
        ServerboundPacketInsertionPacket.Daddy insertionDaddy =
                new ServerboundPacketInsertionPacket.Daddy();
        FriendlyByteBuf insertionBuffer = new FriendlyByteBuf(Unpooled.buffer());
        insertionDaddy.encode(insertion, insertionBuffer);

        assertEquals(SFMPacketDaddy.PacketDirection.SERVERBOUND, insertionDaddy.getPacketDirection());
        assertEquals(insertion, insertionDaddy.decode(insertionBuffer));

        SFMPacketInventoryAddress unsidedAddress = new SFMPacketInventoryAddress(
                new ResourceLocation("minecraft", "the_nether"),
                BlockPos.ZERO,
                Optional.empty()
        );
        FriendlyByteBuf addressBuffer = new FriendlyByteBuf(Unpooled.buffer());
        unsidedAddress.encode(addressBuffer);
        assertEquals(unsidedAddress, SFMPacketInventoryAddress.decode(addressBuffer));
        assertFalse(unsidedAddress.side().isPresent());

        assertThrows(
                IllegalArgumentException.class,
                () -> new SFMPacketInventoryAddress(
                        new ResourceLocation("sfm", "a".repeat(SFMPacketInventoryAddress.MAX_DIMENSION_ID_CHARACTERS)),
                        BlockPos.ZERO,
                        Optional.empty()
                )
        );
    }
}
