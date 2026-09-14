package ca.teamdman.sfm.common.value;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Structural patterns for immutable SFM values. */
public sealed interface SFMValuePattern permits
        SFMValuePattern.AnyPattern,
        SFMValuePattern.StringPattern,
        SFMValuePattern.GuidPattern,
        SFMValuePattern.LiteralPattern,
        SFMValuePattern.ObjectPattern {
    AnyPattern ANY = new AnyPattern();
    StringPattern STRING = new StringPattern();
    GuidPattern GUID = new GuidPattern();

    default Optional<SFMValueMatch> match(SFMValue value) {
        Objects.requireNonNull(value);
        return matches(value)
               ? Optional.of(new SFMValueMatch(value, Map.of()))
               : Optional.empty();
    }

    boolean matches(SFMValue value);

    record AnyPattern() implements SFMValuePattern {
        @Override
        public boolean matches(SFMValue value) {
            return true;
        }
    }

    record StringPattern() implements SFMValuePattern {
        @Override
        public boolean matches(SFMValue value) {
            return value instanceof SFMValue.StringValue;
        }
    }

    record GuidPattern() implements SFMValuePattern {
        @Override
        public boolean matches(SFMValue value) {
            if (!(value instanceof SFMValue.StringValue stringValue)) {
                return false;
            }
            try {
                return UUID.fromString(stringValue.value()).toString().equalsIgnoreCase(stringValue.value());
            } catch (IllegalArgumentException invalid) {
                return false;
            }
        }
    }

    record LiteralPattern(SFMValue expected) implements SFMValuePattern {
        public LiteralPattern {
            Objects.requireNonNull(expected);
        }

        @Override
        public boolean matches(SFMValue value) {
            return expected.equals(value);
        }
    }

    /** Open object pattern: fields not named here are retained and ignored for matching. */
    record ObjectPattern(Map<String, SFMValuePattern> fields) implements SFMValuePattern {
        public ObjectPattern {
            Objects.requireNonNull(fields);
            LinkedHashMap<String, SFMValuePattern> copy = new LinkedHashMap<>();
            fields.forEach((name, pattern) -> copy.put(
                    Objects.requireNonNull(name),
                    Objects.requireNonNull(pattern)
            ));
            fields = Collections.unmodifiableMap(copy);
        }

        @Override
        public boolean matches(SFMValue value) {
            if (!(value instanceof SFMValue.ObjectValue objectValue)) {
                return false;
            }
            return fields.entrySet().stream().allMatch(required -> {
                SFMValue fieldValue = objectValue.fields().get(required.getKey());
                return fieldValue != null && required.getValue().matches(fieldValue);
            });
        }
    }
}
