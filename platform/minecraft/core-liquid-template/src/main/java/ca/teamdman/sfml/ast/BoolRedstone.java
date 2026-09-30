package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.program.ProgramContext;
import net.minecraft.world.level.Level;

public record BoolRedstone(ComparisonOperator operator, long number) implements BoolExpr {
    @SuppressWarnings("UnnecessaryLocalVariable")
    @Override
    public boolean test(ProgramContext programContext) {
        ManagerBlockEntity manager = programContext.getManager();
        Level level = manager.getLevel();
        assert level != null;
        long lhs = level.getBestNeighborSignal(manager.getBlockPos());
        long rhs = number;
        return operator.test(lhs, rhs);
    }

    @Override
    public String toString() {
        return "REDSTONE " + operator + " " + number;
    }
}
