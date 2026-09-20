package ca.teamdman.sfm.common.value;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Contract for the flat, versioned touch packet value. */
class SFMTouchValueTests {
    private static final ResourceLocation DIMENSION = new ResourceLocation("minecraft", "overworld");
    private static final BlockPos POSITION = new BlockPos(12, 64, -7);

    @Test
    void pressProducesFlatVersionedSchemaWithFloatingCoordinates() {
        SFMValue.ObjectValue press = SFMTouchValue.press(
                DIMENSION,
                POSITION,
                Direction.NORTH,
                0.731,
                0.284,
                42L,
                SFMValue.object(Map.of("color", SFMValue.of("red")))
        );

        assertEquals("sfm:touch@1", SFMTouchValue.SCHEMA);
        assertEquals(
                "{\"action\":\"press\",\"contentRevision\":42,"
                + "\"dimension\":\"minecraft:overworld\",\"face\":\"north\","
                + "\"schema\":\"sfm:touch@1\",\"state\":{\"color\":\"red\"},"
                + "\"u\":0.731,\"v\":0.284,\"x\":12,\"y\":64,\"z\":-7}",
                SFMValueJsonCodec.encode(press)
        );
        assertEquals(press, SFMValueJsonCodec.decode(SFMValueJsonCodec.encode(press)));
        assertInstanceOf(SFMValue.DoubleValue.class, press.fields().get("u"));
        assertInstanceOf(SFMValue.DoubleValue.class, press.fields().get("v"));
        assertInstanceOf(SFMValue.LongValue.class, press.fields().get("x"));
        assertInstanceOf(SFMValue.LongValue.class, press.fields().get("contentRevision"));
    }

    @Test
    void zeroAndOneAreValidAndNegativeZeroIsNormalized() {
        SFMValue.ObjectValue press = SFMTouchValue.press(
                DIMENSION,
                POSITION,
                Direction.SOUTH,
                -0.0,
                1.0,
                0L,
                SFMValue.nullValue()
        );

        assertEquals(SFMValue.of(0.0), press.fields().get("u"));
        assertEquals(SFMValue.of(1.0), press.fields().get("v"));
        assertEquals(SFMValue.nullValue(), press.fields().get("state"));
    }

    @Test
    void rejectsNonFiniteOrOutOfRangeCoordinates() {
        for (double invalid : new double[] {
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY,
                -0.000_001,
                1.000_001
        }) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SFMTouchValue.press(
                            DIMENSION, POSITION, Direction.NORTH, invalid, 0.5, 1L, SFMValue.nullValue()
                    ),
                    "u=" + invalid
            );
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SFMTouchValue.press(
                            DIMENSION, POSITION, Direction.NORTH, 0.5, invalid, 1L, SFMValue.nullValue()
                    ),
                    "v=" + invalid
            );
        }
    }

    @Test
    void rejectsStateThatWouldMakeTheCompletePacketExceedTheValueLimit() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SFMTouchValue.press(
                        DIMENSION,
                        POSITION,
                        Direction.NORTH,
                        0.5,
                        0.5,
                        1L,
                        SFMValue.of("x".repeat(3_000))
                )
        );
    }
}
