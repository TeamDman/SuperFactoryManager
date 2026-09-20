package ca.teamdman.sfm.common.program;

import ca.teamdman.sfm.common.resourcetype.ResourceType;

import java.util.Objects;

/** A copied, single-unit resource value captured by an input binding. */
public record ProgramResourceValue(
        ResourceType<?, ?, ?> resourceType,
        Object stack
) {
    public ProgramResourceValue {
        Objects.requireNonNull(resourceType);
        Objects.requireNonNull(stack);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static ProgramResourceValue fromObservation(ProgramResourceObservation observation) {
        ResourceType type = observation.resourceType();
        return new ProgramResourceValue(type, type.withCount(observation.stack(), 1));
    }
}
