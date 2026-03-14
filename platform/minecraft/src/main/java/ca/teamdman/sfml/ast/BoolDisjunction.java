package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.program.ProgramContext;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.function.Consumer;

public record BoolDisjunction(BoolExpr left, BoolExpr right) implements BoolExpr {
    @Override
    public boolean test(ProgramContext programContext) {
        return left.test(programContext) || right.test(programContext);
    }

    @Override
    public String toString() {
        return left + " OR " + right;
    }

    @Override
    public void collectPositions(
            ProgramContext context,
            Consumer<BlockPos> posConsumer
    ) {
        left.collectPositions(context, posConsumer);
        right.collectPositions(context, posConsumer);
    }

    @Override
    public List<BoolExpr> getChildNodes() {

        return List.of(left, right);
    }

}
