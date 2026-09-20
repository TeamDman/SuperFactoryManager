package ca.teamdman.sfm.common.value;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Regression tests for the version-2 floating-point packet-value contract. */
class SFMValueDoubleTests {
    @Test
    void finiteDoublesRoundTripWithoutChangingTheirKind() {
        SFMValue value = SFMValue.object(Map.of(
                "coordinates", SFMValue.array(List.of(
                        SFMValue.of(0.0),
                        SFMValue.of(0.731),
                        SFMValue.of(1.0),
                        SFMValue.of(Double.MIN_VALUE),
                        SFMValue.of(Double.MAX_VALUE)
                )),
                "exactCount", SFMValue.of(9_007_199_254_740_993L)
        ));

        String encoded = SFMValueJsonCodec.encode(value);

        assertEquals(value, SFMValueJsonCodec.decode(encoded));
        assertEquals(value.hashCode(), SFMValueJsonCodec.decode(encoded).hashCode());
        assertEquals(encoded, SFMValueJsonCodec.encode(SFMValueJsonCodec.decode(encoded)));
        assertEquals(2, SFMValueJsonCodec.VERSION);
        assertEquals("1.0", SFMValueJsonCodec.encode(SFMValue.of(1.0)));
        assertEquals("1", SFMValueJsonCodec.encode(SFMValue.of(1L)));
        assertNotEquals(SFMValue.of(1L), SFMValue.of(1.0));
    }

    @Test
    void negativeZeroIsNormalizedAtConstructionAndDecoding() {
        SFMValue.DoubleValue value = assertInstanceOf(
                SFMValue.DoubleValue.class,
                SFMValue.of(-0.0)
        );

        assertEquals(Double.doubleToLongBits(0.0), Double.doubleToLongBits(value.value()));
        assertEquals(SFMValue.of(0.0).hashCode(), value.hashCode());
        assertEquals("0.0", SFMValueJsonCodec.encode(value));
        assertEquals(SFMValue.of(0.0), SFMValueJsonCodec.decode("-0.0"));
    }

    @Test
    void rejectsNonFiniteValuesAtConstructionAndJsonBoundary() {
        for (double invalid : List.of(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException.class, () -> SFMValue.of(invalid));
            assertThrows(IllegalArgumentException.class, () -> new SFMValue.DoubleValue(invalid));
        }
        for (String invalid : List.of("1e309", "-1e309", "NaN", "Infinity")) {
            assertThrows(IllegalArgumentException.class, () -> SFMValueJsonCodec.decode(invalid), invalid);
        }
    }

    @Test
    void jsonNumberSpellingSelectsLongOrDoubleWithoutPrecisionLoss() {
        assertEquals(
                new SFMValue.LongValue(9_007_199_254_740_993L),
                SFMValueJsonCodec.decode("9007199254740993")
        );
        assertEquals(new SFMValue.DoubleValue(1.0), SFMValueJsonCodec.decode("1.0"));
        assertEquals(new SFMValue.DoubleValue(100.0), SFMValueJsonCodec.decode("1e2"));
        assertEquals(new SFMValue.LongValue(-1L), SFMValueJsonCodec.decode("-1"));

        assertInstanceOf(SFMValue.LongValue.class, SFMValueJsonCodec.decode("1"));
        assertInstanceOf(SFMValue.DoubleValue.class, SFMValueJsonCodec.decode("1.0"));
    }

    @Test
    void versionOneStillReadsExactIntegersButRejectsFloatingSpelling() {
        assertEquals(
                SFMValue.of(Long.MAX_VALUE),
                SFMValueJsonCodec.decode("9223372036854775807", 1)
        );
        assertEquals(
                SFMValue.of(Long.MIN_VALUE),
                SFMValueJsonCodec.decode("-9223372036854775808", 1)
        );
        assertEquals(
                SFMValue.object(Map.of("count", SFMValue.of(9_007_199_254_740_993L))),
                SFMValueJsonCodec.decode("{\"count\":9007199254740993}", 1)
        );
        for (String invalid : List.of("1.0", "1e2", "-0.0", "9223372036854775808")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SFMValueJsonCodec.decode(invalid, 1),
                    invalid
            );
        }
    }

    @Test
    void wideningAFloatPreservesItsExactBinaryValueAcrossCanonicalJson() {
        float original = 0.731f;
        double widened = original;

        SFMValue.DoubleValue decoded = assertInstanceOf(
                SFMValue.DoubleValue.class,
                SFMValueJsonCodec.decode(SFMValueJsonCodec.encode(SFMValue.of(widened)))
        );

        assertEquals(Double.doubleToLongBits(widened), Double.doubleToLongBits(decoded.value()));
    }
}
