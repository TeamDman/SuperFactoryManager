package ca.teamdman.sfm.common.program;

/** A trigger-local resource that must be released when its execution ends. */
@FunctionalInterface
public interface ProgramEphemeralResource {
    void free();
}
