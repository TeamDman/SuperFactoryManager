package ca.teamdman.sfm.common.program;

import ca.teamdman.sfm.common.value.SFMValue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Trigger-local value bindings. Relation-valued bindings are introduced in Slice C. */
public final class ProgramVariableEnvironment {
    private final Map<String, SFMValue> values = new LinkedHashMap<>();
    private boolean freed;

    public void set(
            String name,
            SFMValue value
    ) {
        checkActive();
        values.put(Objects.requireNonNull(name), Objects.requireNonNull(value));
    }

    public Optional<SFMValue> get(String name) {
        checkActive();
        return Optional.ofNullable(values.get(name));
    }

    public Map<String, SFMValue> snapshot() {
        checkActive();
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public void free() {
        if (freed) {
            return;
        }
        values.clear();
        freed = true;
    }

    public boolean isFreed() {
        return freed;
    }

    private void checkActive() {
        if (freed) {
            throw new IllegalStateException("Variable environment has been freed");
        }
    }
}
