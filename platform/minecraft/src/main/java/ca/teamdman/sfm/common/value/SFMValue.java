package ca.teamdman.sfm.common.value;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Immutable JSON-compatible data carried by SFM packet items.
 *
 * <p>This value model is deliberately independent of Minecraft item stacks,
 * UI selections, and application-level Request/Ack/Response conventions.</p>
 */
public sealed interface SFMValue permits
        SFMValue.NullValue,
        SFMValue.BooleanValue,
        SFMValue.LongValue,
        SFMValue.StringValue,
        SFMValue.ArrayValue,
        SFMValue.ObjectValue {

    static SFMValue nullValue() {
        return NullValue.INSTANCE;
    }

    static SFMValue of(boolean value) {
        return new BooleanValue(value);
    }

    static SFMValue of(long value) {
        return new LongValue(value);
    }

    static SFMValue of(String value) {
        return new StringValue(value);
    }

    static SFMValue array(List<? extends SFMValue> values) {
        return new ArrayValue(List.copyOf(Objects.requireNonNull(values, "values")));
    }

    static SFMValue object(Map<String, ? extends SFMValue> fields) {
        Objects.requireNonNull(fields, "fields");
        TreeMap<String, SFMValue> copy = new TreeMap<>();
        fields.forEach(copy::put);
        return new ObjectValue(copy);
    }

    enum NullValue implements SFMValue {
        INSTANCE
    }

    record BooleanValue(boolean value) implements SFMValue {
    }

    record LongValue(long value) implements SFMValue {
    }

    record StringValue(String value) implements SFMValue {
        public StringValue {
            Objects.requireNonNull(value, "value");
            SFMValueJsonCodec.requireWellFormedUnicode(value, "string value");
        }
    }

    record ArrayValue(List<SFMValue> values) implements SFMValue {
        public ArrayValue {
            values = List.copyOf(Objects.requireNonNull(values, "values"));
        }
    }

    record ObjectValue(Map<String, SFMValue> fields) implements SFMValue {
        public ObjectValue {
            Objects.requireNonNull(fields, "fields");
            TreeMap<String, SFMValue> sorted = new TreeMap<>();
            fields.forEach((name, value) -> {
                Objects.requireNonNull(name, "field name");
                Objects.requireNonNull(value, "field value");
                SFMValueJsonCodec.requireWellFormedUnicode(name, "object field name");
                sorted.put(name, value);
            });
            fields = Collections.unmodifiableMap(sorted);
        }
    }
}
