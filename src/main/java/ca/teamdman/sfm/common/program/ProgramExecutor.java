package ca.teamdman.sfm.common.program;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfml.ast.Program;

import java.util.Objects;
import java.util.Set;

public class ProgramExecutor {
    private final Program            PROGRAM;
    private final ManagerBlockEntity MANAGER;

    public ProgramExecutor(Program program, ManagerBlockEntity manager) {
        Objects.requireNonNull(program);
        Objects.requireNonNull(manager);
        this.PROGRAM = program;
        this.MANAGER = manager;
    }

    public void tick() {
        var context = new ProgramContext(MANAGER);

        // update warnings on disk item every 20 seconds
        if (MANAGER
                    .getLevel()
                    .getGameTime() % 20 == 0) {
            MANAGER
                    .getDisk()
                    .ifPresent(PROGRAM::addWarnings);
        }
        PROGRAM.tick(context);
        MANAGER.clearRedstonePulseQueue();
    }

    public Set<String> getReferencedLabels() {
        return PROGRAM.getReferencedLabels();
    }
}
