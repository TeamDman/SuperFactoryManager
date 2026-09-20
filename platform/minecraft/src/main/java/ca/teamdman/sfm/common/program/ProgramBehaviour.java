package ca.teamdman.sfm.common.program;

public interface ProgramBehaviour {
    ProgramBehaviour fork();

    /** Whether this behavior may create runtime-only values and resources. */
    default boolean allowsRuntimeMaterialization() {
        return true;
    }
}
