package ca.teamdman.sfm.common.value;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SFMValueJsonCodecTests {
    @Test
    void roundTripsEveryValueKindWithCanonicalObjectOrder() {
        LinkedHashMap<String, SFMValue> fields = new LinkedHashMap<>();
        fields.put("z", SFMValue.of("snowman ☃"));
        fields.put("a", SFMValue.array(List.of(
                SFMValue.nullValue(),
                SFMValue.of(true),
                SFMValue.of(false),
                SFMValue.of(Long.MIN_VALUE),
                SFMValue.of(Long.MAX_VALUE)
        )));
        SFMValue value = SFMValue.object(fields);

        String encoded = SFMValueJsonCodec.encode(value);

        assertEquals(
                "{\"a\":[null,true,false,-9223372036854775808,9223372036854775807],"
                + "\"z\":\"snowman ☃\"}",
                encoded
        );
        assertEquals(value, SFMValueJsonCodec.decode(encoded));
        assertEquals(encoded, SFMValueJsonCodec.encode(SFMValueJsonCodec.decode(encoded)));
        assertEquals(
                "{\n"
                + "  \"a\": [\n"
                + "    null,\n"
                + "    true,\n"
                + "    false,\n"
                + "    -9223372036854775808,\n"
                + "    9223372036854775807\n"
                + "  ],\n"
                + "  \"z\": \"snowman ☃\"\n"
                + "}",
                SFMValueJsonCodec.encodePretty(value)
        );
    }

    @Test
    void constructionCopiesCollectionsAndExposesImmutableViews() {
        ArrayList<SFMValue> mutableArray = new ArrayList<>(List.of(SFMValue.of("first")));
        LinkedHashMap<String, SFMValue> mutableObject = new LinkedHashMap<>();
        mutableObject.put("array", SFMValue.array(mutableArray));
        SFMValue.ObjectValue value = (SFMValue.ObjectValue) SFMValue.object(mutableObject);

        mutableArray.add(SFMValue.of("later"));
        mutableObject.put("later", SFMValue.of(true));

        assertEquals("{\"array\":[\"first\"]}", SFMValueJsonCodec.encode(value));
        assertThrows(UnsupportedOperationException.class, () -> value.fields().put("x", SFMValue.of(1)));
        SFMValue.ArrayValue array = (SFMValue.ArrayValue) value.fields().get("array");
        assertThrows(UnsupportedOperationException.class, () -> array.values().add(SFMValue.of(2)));
    }

    @Test
    void rejectsMalformedAndOutOfRangeJson() {
        assertThrows(NullPointerException.class, () -> SFMValueJsonCodec.encode(null));
        for (String invalid : List.of(
                "{",
                "null null",
                "/* comment */ null",
                "{unquoted:true}",
                "{\"x\":1,\"x\":2}",
                "9223372036854775808",
                "-9223372036854775809",
                "1e309",
                "-1e309"
        )) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SFMValueJsonCodec.decode(invalid),
                    invalid
            );
        }
    }

    @Test
    void enforcesByteDepthContainerAndUnicodeLimits() {
        String maximumString = "\"" + "a".repeat(SFMValueJsonCodec.MAX_ENCODED_UTF8_BYTES - 2) + "\"";
        assertEquals(
                SFMValue.of("a".repeat(SFMValueJsonCodec.MAX_ENCODED_UTF8_BYTES - 2)),
                SFMValueJsonCodec.decode(maximumString)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> SFMValueJsonCodec.decode(maximumString + " ")
        );

        String tooDeep = "[".repeat(SFMValueJsonCodec.MAX_NESTING_DEPTH + 2)
                         + "null"
                         + "]".repeat(SFMValueJsonCodec.MAX_NESTING_DEPTH + 2);
        assertThrows(IllegalArgumentException.class, () -> SFMValueJsonCodec.decode(tooDeep));

        String tooMany = "[" + "0,".repeat(SFMValueJsonCodec.MAX_CONTAINER_ENTRIES) + "0]";
        assertThrows(IllegalArgumentException.class, () -> SFMValueJsonCodec.decode(tooMany));

        assertThrows(IllegalArgumentException.class, () -> SFMValue.of("\uD800"));
        assertThrows(
                IllegalArgumentException.class,
                () -> SFMValue.object(Map.of("\uDFFF", SFMValue.nullValue()))
        );
    }
}
