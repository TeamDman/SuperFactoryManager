package ca.teamdman.sfm.common.value;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Bounded, diagnostic-producing shapes for machine action inputs and results.
 * This is deliberately distinct from {@link SFMValuePattern}: patterns match
 * packets in programs, while schemas specify an action's complete value contract.
 */
public sealed interface SFMValueSchema permits
        SFMValueSchema.AnySchema,
        SFMValueSchema.NullSchema,
        SFMValueSchema.BooleanSchema,
        SFMValueSchema.NumericSchema,
        SFMValueSchema.StringSchema,
        SFMValueSchema.LiteralSchema,
        SFMValueSchema.ArraySchema,
        SFMValueSchema.UnionSchema,
        SFMValueSchema.OptionalSchema,
        SFMValueSchema.ObjectSchema {
    int MAX_SCHEMA_DEPTH = SFMValueJsonCodec.MAX_NESTING_DEPTH;
    int MAX_ACTION_ENCODED_UTF8_BYTES = 16 * 1024;

    /** JSON-pointer path is empty at the root; code is a stable machine token. */
    record Failure(String code, String path) {
        public Failure {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(path, "path");
            if (!code.matches("[a-z][a-z0-9_]*")) {
                throw new IllegalArgumentException("Invalid schema failure code");
            }
        }
    }

    default Optional<Failure> validate(SFMValue value) {
        Objects.requireNonNull(value, "value");
        try {
            SFMValueJsonCodec.encodeWithByteLimit(value, MAX_ACTION_ENCODED_UTF8_BYTES);
        } catch (IllegalArgumentException invalid) {
            return Optional.of(new Failure("value_out_of_bounds", ""));
        }
        return validateAt(value, "", 0);
    }

    Optional<Failure> validateAt(SFMValue value, String path, int depth);

    static Optional<Failure> tooDeep(String path, int depth) {
        return depth > MAX_SCHEMA_DEPTH
                ? Optional.of(new Failure("schema_too_deep", path))
                : Optional.empty();
    }

    static String child(String path, String key) {
        return path + "/" + key.replace("~", "~0").replace("/", "~1");
    }

    static SFMValueSchema any() { return new AnySchema(); }
    static SFMValueSchema nil() { return new NullSchema(); }
    static SFMValueSchema bool() { return new BooleanSchema(); }
    static SFMValueSchema integer(long min, long max) {
        return new NumericSchema(NumericKind.INTEGER, BigDecimal.valueOf(min), BigDecimal.valueOf(max));
    }
    static SFMValueSchema floating(double min, double max) {
        return new NumericSchema(NumericKind.FLOATING, BigDecimal.valueOf(min), BigDecimal.valueOf(max));
    }
    static SFMValueSchema number(BigDecimal min, BigDecimal max) {
        return new NumericSchema(NumericKind.EITHER, min, max);
    }
    static SFMValueSchema string(int minUtf8Bytes, int maxUtf8Bytes) {
        return new StringSchema(minUtf8Bytes, maxUtf8Bytes);
    }
    static SFMValueSchema literal(SFMValue expected) { return new LiteralSchema(expected); }
    static SFMValueSchema array(SFMValueSchema element, int minItems, int maxItems) {
        return new ArraySchema(element, minItems, maxItems);
    }
    static SFMValueSchema union(List<? extends SFMValueSchema> options) {
        return new UnionSchema(List.copyOf(options));
    }
    static SFMValueSchema optional(SFMValueSchema value) { return new OptionalSchema(value); }
    static SFMValueSchema object(Map<String, Field> fields, boolean allowExtraFields) {
        return new ObjectSchema(fields, allowExtraFields);
    }

    record AnySchema() implements SFMValueSchema {
        @Override public Optional<Failure> validateAt(SFMValue value, String path, int depth) {
            return tooDeep(path, depth);
        }
    }

    record NullSchema() implements SFMValueSchema {
        @Override public Optional<Failure> validateAt(SFMValue value, String path, int depth) {
            return value instanceof SFMValue.NullValue ? tooDeep(path, depth)
                    : Optional.of(new Failure("wrong_type", path));
        }
    }

    record BooleanSchema() implements SFMValueSchema {
        @Override public Optional<Failure> validateAt(SFMValue value, String path, int depth) {
            return value instanceof SFMValue.BooleanValue ? tooDeep(path, depth)
                    : Optional.of(new Failure("wrong_type", path));
        }
    }

    enum NumericKind { INTEGER, FLOATING, EITHER }

    record NumericSchema(NumericKind kind, BigDecimal min, BigDecimal max) implements SFMValueSchema {
        public NumericSchema {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(min, "min");
            Objects.requireNonNull(max, "max");
            if (min.compareTo(max) > 0) throw new IllegalArgumentException("Reversed numeric bounds");
        }

        @Override public Optional<Failure> validateAt(SFMValue value, String path, int depth) {
            Optional<Failure> depthFailure = tooDeep(path, depth);
            if (depthFailure.isPresent()) return depthFailure;
            BigDecimal actual;
            if (value instanceof SFMValue.LongValue integer && kind != NumericKind.FLOATING) {
                actual = BigDecimal.valueOf(integer.value());
            } else if (value instanceof SFMValue.DoubleValue floating && kind != NumericKind.INTEGER) {
                actual = BigDecimal.valueOf(floating.value());
            } else {
                return Optional.of(new Failure("wrong_type", path));
            }
            return actual.compareTo(min) < 0 || actual.compareTo(max) > 0
                    ? Optional.of(new Failure("out_of_range", path)) : Optional.empty();
        }
    }

    record StringSchema(int minUtf8Bytes, int maxUtf8Bytes) implements SFMValueSchema {
        public StringSchema {
            if (minUtf8Bytes < 0 || maxUtf8Bytes < minUtf8Bytes
                    || maxUtf8Bytes > MAX_ACTION_ENCODED_UTF8_BYTES) {
                throw new IllegalArgumentException("Invalid string schema bounds");
            }
        }

        @Override public Optional<Failure> validateAt(SFMValue value, String path, int depth) {
            Optional<Failure> depthFailure = tooDeep(path, depth);
            if (depthFailure.isPresent()) return depthFailure;
            if (!(value instanceof SFMValue.StringValue string)) {
                return Optional.of(new Failure("wrong_type", path));
            }
            int bytes = string.value().getBytes(StandardCharsets.UTF_8).length;
            return bytes < minUtf8Bytes || bytes > maxUtf8Bytes
                    ? Optional.of(new Failure("length_out_of_range", path)) : Optional.empty();
        }
    }

    record LiteralSchema(SFMValue expected) implements SFMValueSchema {
        public LiteralSchema { Objects.requireNonNull(expected, "expected"); }
        @Override public Optional<Failure> validateAt(SFMValue value, String path, int depth) {
            Optional<Failure> depthFailure = tooDeep(path, depth);
            if (depthFailure.isPresent()) return depthFailure;
            return expected.equals(value) ? Optional.empty()
                    : Optional.of(new Failure("wrong_literal", path));
        }
    }

    record ArraySchema(SFMValueSchema element, int minItems, int maxItems) implements SFMValueSchema {
        public ArraySchema {
            Objects.requireNonNull(element, "element");
            if (minItems < 0 || maxItems < minItems || maxItems > SFMValueJsonCodec.MAX_CONTAINER_ENTRIES) {
                throw new IllegalArgumentException("Invalid array schema bounds");
            }
        }

        @Override public Optional<Failure> validateAt(SFMValue value, String path, int depth) {
            Optional<Failure> depthFailure = tooDeep(path, depth);
            if (depthFailure.isPresent()) return depthFailure;
            if (!(value instanceof SFMValue.ArrayValue array)) {
                return Optional.of(new Failure("wrong_type", path));
            }
            if (array.values().size() < minItems || array.values().size() > maxItems) {
                return Optional.of(new Failure("length_out_of_range", path));
            }
            for (int i = 0; i < array.values().size(); i++) {
                Optional<Failure> failure = element.validateAt(array.values().get(i), child(path, Integer.toString(i)), depth + 1);
                if (failure.isPresent()) return failure;
            }
            return Optional.empty();
        }
    }

    record UnionSchema(List<SFMValueSchema> options) implements SFMValueSchema {
        public UnionSchema {
            options = List.copyOf(Objects.requireNonNull(options, "options"));
            if (options.isEmpty() || options.size() > 16) {
                throw new IllegalArgumentException("Union schema must have 1–16 options");
            }
        }

        @Override public Optional<Failure> validateAt(SFMValue value, String path, int depth) {
            Optional<Failure> depthFailure = tooDeep(path, depth);
            if (depthFailure.isPresent()) return depthFailure;
            for (SFMValueSchema option : options) {
                if (option.validateAt(value, path, depth).isEmpty()) return Optional.empty();
            }
            return Optional.of(new Failure("no_union_match", path));
        }
    }

    /** Accepts an explicit null; object-field absence is controlled by {@link Field}. */
    record OptionalSchema(SFMValueSchema value) implements SFMValueSchema {
        public OptionalSchema { Objects.requireNonNull(value, "value"); }
        @Override public Optional<Failure> validateAt(SFMValue candidate, String path, int depth) {
            Optional<Failure> depthFailure = tooDeep(path, depth);
            if (depthFailure.isPresent()) return depthFailure;
            return candidate instanceof SFMValue.NullValue ? Optional.empty()
                    : value.validateAt(candidate, path, depth);
        }
    }

    record Field(SFMValueSchema schema, boolean required) {
        public Field { Objects.requireNonNull(schema, "schema"); }
        public static Field required(SFMValueSchema schema) { return new Field(schema, true); }
        public static Field optional(SFMValueSchema schema) { return new Field(schema, false); }
    }

    record ObjectSchema(Map<String, Field> fields, boolean allowExtraFields) implements SFMValueSchema {
        public ObjectSchema {
            Objects.requireNonNull(fields, "fields");
            TreeMap<String, Field> sorted = new TreeMap<>();
            fields.forEach((key, field) -> sorted.put(Objects.requireNonNull(key), Objects.requireNonNull(field)));
            if (sorted.size() > SFMValueJsonCodec.MAX_CONTAINER_ENTRIES) {
                throw new IllegalArgumentException("Object schema has too many fields");
            }
            fields = Collections.unmodifiableMap(sorted);
        }

        @Override public Optional<Failure> validateAt(SFMValue value, String path, int depth) {
            Optional<Failure> depthFailure = tooDeep(path, depth);
            if (depthFailure.isPresent()) return depthFailure;
            if (!(value instanceof SFMValue.ObjectValue object)) {
                return Optional.of(new Failure("wrong_type", path));
            }
            for (Map.Entry<String, Field> entry : fields.entrySet()) {
                String key = entry.getKey();
                SFMValue fieldValue = object.fields().get(key);
                if (fieldValue == null) {
                    if (entry.getValue().required()) return Optional.of(new Failure("missing_field", child(path, key)));
                } else {
                    Optional<Failure> failure = entry.getValue().schema().validateAt(fieldValue, child(path, key), depth + 1);
                    if (failure.isPresent()) return failure;
                }
            }
            if (!allowExtraFields) {
                for (String key : object.fields().keySet()) {
                    if (!fields.containsKey(key)) return Optional.of(new Failure("extra_field", child(path, key)));
                }
            }
            return Optional.empty();
        }
    }
}
