package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.program.ProgramContext;
import net.minecraft.world.level.Level;

public record BoolRedstone(ComparisonOperator operator, NumExpr rhs) implements BoolExpr {
    public BoolRedstone(ComparisonOperator operator, long number) {
        this(operator, new Number(number));
    }

    @Override
    public boolean test(ProgramContext programContext) {
        ManagerBlockEntity manager = programContext.getManager();
        Level level = manager.getLevel();
        assert level != null;
        long lhs = level.getBestNeighborSignal(manager.getBlockPos());
        long rhsVal = rhs.eval(programContext);
        return operator.test(lhs, rhsVal);
    }

    @Override
    public String toString() {
        return "REDSTONE " + operator + " " + rhs;
    }
}
