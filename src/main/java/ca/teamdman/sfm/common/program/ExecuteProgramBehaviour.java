package ca.teamdman.sfm.common.program;

public class ExecuteProgramBehaviour implements ProgramBehaviour {
    @Override
    public ProgramBehaviour fork() {
        return this; // this is stateless so this should be fine
    }

}
