package ca.teamdman.sfm.common.program;

import ca.teamdman.sfm.common.resourcetype.ResourceType;

/** A detached stack snapshot and its eligible observed quantity. */
public record ProgramResourceObservation(
        ResourceType<?, ?, ?> resourceType,
        Object stack,
        long amount
) {
    public ProgramResourceObservation {
        if (amount <= 0) {
            throw new IllegalArgumentException("Observed resource amount must be positive");
        }
    }
}
