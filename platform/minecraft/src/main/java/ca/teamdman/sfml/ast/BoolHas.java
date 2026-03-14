package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.resourcetype.ResourceType;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public record BoolHas(
        SetOperator setOperator,

        ResourceAccess resourceAccess,

        ComparisonOperator comparisonOperator,

        NumberExpression quantity,

        ResourceIdSet resourceIdSet,

        With with,

        ResourceIdSet except
) implements BoolExpr {

    @Override
    public boolean test(ProgramContext programContext) {

        AtomicLong overallCount = new AtomicLong(0);
        List<Boolean> satisfactionResults = new ArrayList<>();

        LabelPositionHolder labelPositionHolder = programContext.labelPositionHolder();
        for (LabelExpression labelExpression : resourceAccess.labelExpressions()) {
            for (BlockPos pos : labelExpression.getPositions(labelPositionHolder)) {
                AtomicLong inThisInv = new AtomicLong(0);
                for (ResourceType<?, ?, ?> resourceType : resourceIdSet.getReferencedResourceTypes()) {
                    accumulate(
                            programContext,
                            pos,
                            overallCount,
                            inThisInv,
                            resourceType
                    );
                }
                satisfactionResults.add(comparisonOperator.test(inThisInv.get(), quantity.value()));
            }
        }

        var isOverallSatisfied = this.comparisonOperator.test(overallCount.get(), this.quantity.value());
        return setOperator.test(isOverallSatisfied, satisfactionResults);
    }

    @Override
    public String toString() {

        return setOperator
               + " "
               + resourceAccess
               + " HAS "
               + comparisonOperator
               + " "
               + quantity
               + " "
               + resourceIdSet.toStringCondensed()
               + (with == With.ALWAYS_TRUE ? "" : " " + with.toStringPretty())
               + (except.isEmpty() ? "" : " EXCEPT " + except.toStringCondensed());
    }

    @Override
    public void collectPositions(
            ProgramContext context,
            Consumer<BlockPos> posConsumer
    ) {

        LabelPositionHolder labelPositionHolder = context.labelPositionHolder();
        for (LabelExpression labelExpression : resourceAccess.labelExpressions()) {
            labelExpression.getPositions(labelPositionHolder).forEach(posConsumer);
        }
    }

    @Override
    public List<? extends SfmlAstNode> getChildNodes() {

        return List.of(
                setOperator,
                resourceAccess,
                comparisonOperator,
                quantity,
                resourceIdSet,
                with,
                except
        );
    }

    private <STACK, ITEM, CAP> void accumulate(
            ProgramContext programContext,
            BlockPos pos,
            AtomicLong overallAccumulator,
            AtomicLong invAccumulator,
            ResourceType<STACK, ITEM, CAP> resourceType
    ) {

        resourceType.forEachDirectionalCapability(
                programContext.logger(),
                programContext.level(),
                programContext.network(),
                resourceAccess.sides(),
                pos,
                (direction, cap) -> resourceType
                        .getStacksInSlots(cap, resourceAccess.slots().numberSet())
                        .forEach(stack -> {
                            if (this.resourceIdSet.getMatchingFromStack(stack) != null) {
                                if (with.matchesStack(resourceType, stack)) {
                                    long amount = resourceType.getAmount(stack);
                                    invAccumulator.addAndGet(amount);
                                    overallAccumulator.addAndGet(amount);
                                }
                            }
                        })
        );
    }

}
