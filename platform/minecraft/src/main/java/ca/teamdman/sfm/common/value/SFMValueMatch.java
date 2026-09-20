package ca.teamdman.sfm.common.value;

import java.util.Map;
import java.util.Objects;

/** A successful structural match retaining the complete value and bindings. */
public record SFMValueMatch(
        SFMValue value,
        Map<String, SFMValue> bindings
) {
    public SFMValueMatch {
        Objects.requireNonNull(value);
        bindings = Map.copyOf(Objects.requireNonNull(bindings));
    }
}
