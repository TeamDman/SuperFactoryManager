package ca.teamdman.sfm.common.net;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerboundManagerShowPacketTests {
    private static final ResourceLocation DIMENSION = new ResourceLocation("minecraft", "overworld");

    @Test
    void boundaryCoordinatesSurviveTheActualWireCodec() {
        for (BlockPos position : List.of(
                new BlockPos(33_554_431, 2_047, 33_554_431),
                new BlockPos(-33_554_432, -2_048, -33_554_432))) {
            var request = new ServerboundManagerShowPacket(UUID.randomUUID(), DIMENSION, position);
            FriendlyByteBuf wire = new FriendlyByteBuf(Unpooled.buffer());
            try {
                var codec = new ServerboundManagerShowPacket.Daddy();
                codec.encode(request, wire);
                assertEquals(request, codec.decode(wire));
            } finally {
                wire.release();
            }
        }
    }

    @Test
    void firstCoordinatesOutsideTheWireRangeAreRejected() {
        BlockPos passing = new BlockPos(33_554_431, 2_047, 33_554_431);
        assertEquals(passing, BlockPos.of(passing.asLong()));
        for (BlockPos aliased : List.of(
                new BlockPos(33_554_432, 0, 0),
                new BlockPos(-33_554_433, 0, 0),
                new BlockPos(0, 2_048, 0),
                new BlockPos(0, -2_049, 0),
                new BlockPos(0, 0, 33_554_432),
                new BlockPos(0, 0, -33_554_433))) {
            assertNotEquals(aliased, BlockPos.of(aliased.asLong()));
            assertThrows(IllegalArgumentException.class,
                    () -> new ServerboundManagerShowPacket(UUID.randomUUID(), DIMENSION, aliased));
        }
    }
}
