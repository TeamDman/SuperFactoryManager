package ca.teamdman.sfm.common.program;

import ca.teamdman.sfm.common.value.SFMValue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Trigger-local scalar compatibility access and occurrence-preserving relation bindings. */
public final class ProgramVariableEnvironment {
    private final Map<String, ProgramRelation> relations = new LinkedHashMap<>();
    private boolean freed;

    public void set(
            String name,
            SFMValue value
    ) {
        checkActive();
        setRelation(name, ProgramRelation.singleton(ProgramValueReference.resolved(value)));
    }

    public Optional<SFMValue> get(String name) {
        checkActive();
        ProgramRelation relation = relations.get(normalize(name));
        if (relation == null || relation.rows().size() != 1) {
            return Optional.empty();
        }
        Object value = relation.rows().get(0).value();
        return value instanceof ProgramValueReference reference
               ? Optional.of(reference.get())
               : Optional.empty();
    }

    public void setRelation(
            String name,
            ProgramRelation relation
    ) {
        checkActive();
        relations.put(normalize(name), Objects.requireNonNull(relation));
    }

    public Optional<ProgramRelation> getRelation(String name) {
        checkActive();
        return Optional.ofNullable(relations.get(normalize(name)));
    }

    public Map<String, SFMValue> snapshot() {
        checkActive();
        LinkedHashMap<String, SFMValue> values = new LinkedHashMap<>();
        relations.forEach((name, relation) -> {
            if (relation.rows().size() == 1
                && relation.rows().get(0).value() instanceof ProgramValueReference reference) {
                values.put(name, reference.get());
            }
        });
        return Collections.unmodifiableMap(values);
    }

    public void free() {
        if (freed) {
            return;
        }
        relations.clear();
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

    private static String normalize(String name) {
        return Objects.requireNonNull(name).toLowerCase(java.util.Locale.ROOT);
    }
}
