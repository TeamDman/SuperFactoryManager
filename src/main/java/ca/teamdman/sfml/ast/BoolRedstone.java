package ca.teamdman.sfml.ast;

import net.minecraft.world.World;

import com.github.bsideup.jabel.Desugar;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.program.ProgramContext;

@Desugar
public record BoolRedstone(
                           ComparisonOperator operator, long number)
        implements BoolExpr {

    @SuppressWarnings("UnnecessaryLocalVariable")
    @Override
    public boolean test(ProgramContext programContext) {
        ManagerBlockEntity manager = programContext.getManager();
        World level = manager.getWorld();
        assert level != null;
        long lhs = level.getStrongPower(manager.getPos());
        long rhs = number;
        return operator.test(lhs, rhs);
    }

    @Override
    public String toString() {
        return "REDSTONE " + operator + " " + number;
    }
}
