package ca.teamdman.sfm.common.program;

/**
 * Execution-local identity for one relation occurrence.
 *
 * <p>Identity is deliberately reference-based. It is not serialized, is not a
 * packet job ID, and does not make structurally equal values nominally
 * different.</p>
 */
public final class ProgramOccurrenceId {
    private ProgramOccurrenceId() {
    }

    public static ProgramOccurrenceId create() {
        return new ProgramOccurrenceId();
    }
}
