package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.program.SimulateExploreAllPathsProgramBehaviour;

import java.util.List;
import java.util.function.Predicate;

public record IfStatement(
        BoolExpr condition,
        Block trueBlock,
        Block falseBlock
) implements SfmlAstNode, Statement, ToStringCondensed {
    @Override
    public void tick(ProgramContext context) {
        Predicate<ProgramContext> condition = this.condition;
        boolean result;

        if (context.behaviour() instanceof SimulateExploreAllPathsProgramBehaviour simulation) {
            condition = ctx -> {

                int conditionIndex = ctx.program().getConditionIndex(this);
                if (conditionIndex == -1) {
                    SFM.LOGGER.warn("Condition index not found for {}", this);
                }
                return simulation.getTriggerPathCount().testBit(conditionIndex);
            };
            result = condition.test(context);
            simulation.onIfStatementExecution(context, this, result);
        } else {
            result = condition.test(context);
        }

        if (result) {
            tickTrueBlock(context);
        } else {
            tickFalseBlock(context);
        }
    }

    @Override
    public String toString() {
        var rtn = "IF " + condition + " THEN\n" + trueBlock.toString().strip().indent(1).stripTrailing();
        if (!falseBlock.getChildNodes().isEmpty()) {
            rtn += "\nELSE\n" + falseBlock.toString().strip().indent(1);
        }
        rtn += "\nEND";
        return rtn.strip();
    }

    @Override
    public List<SfmlAstNode> getChildNodes() {
        return List.of(condition, trueBlock, falseBlock);
    }

    @Override
    public String toStringCondensed() {
        return condition.toString();
    }

    private void tickFalseBlock(ProgramContext context) {

        context.logger().debug(x -> x.accept(
                LocalizationKeys.LOG_PROGRAM_TICK_IF_STATEMENT_WAS_FALSE.get(this.condition.toStringPretty())));
        falseBlock.tick(context);
    }

    private void tickTrueBlock(ProgramContext context) {

        context.logger().debug(x -> x.accept(
                LocalizationKeys.LOG_PROGRAM_TICK_IF_STATEMENT_WAS_TRUE.get(this.condition.toStringPretty())));
        trueBlock.tick(context);
    }
}
