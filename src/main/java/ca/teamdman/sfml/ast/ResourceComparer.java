package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.resourcetype.ResourceType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public record ResourceComparer<STACK, ITEM, CAP>(
        ComparisonOperator op,
        ResourceQuantity quantity,
        ResourceIdentifier<STACK, ITEM, CAP> res
) implements ASTNode {
    public BoolExpr toBooleanExpression(SetOperator setOp, LabelAccess labelAccess, String sourceCode) {
        return new BoolExpr(
                context -> {
                    ResourceType<STACK, ITEM, CAP> type = res.getResourceType();
                    if (type == null) return false;

                    // track how many items seen
                    AtomicLong overallCount = new AtomicLong(0);
                    // track how many inventories satisfied the condition
                    List<Boolean> satisfiedSet = new ArrayList<>();
                    type.forEachCapability(context, labelAccess, (label, pos, direction, cap, count) -> {
                        long inThisInv = 0;
                        for (var stack : (Iterable<STACK>) type.getStacksInSlots(cap, labelAccess.slots())::iterator) {
                            if (this.res.matchesStack(stack)) {
                                inThisInv += type.getAmount(stack);
                                overallCount.addAndGet(type.getAmount(stack));
                            }
                        }
                        satisfiedSet.add(this.op.test(inThisInv, this.quantity.number().value()));
                    });

                    var isOverallSatisfied = this.op.test(overallCount.get(), this.quantity.number().value());
                    return setOp.test(isOverallSatisfied, satisfiedSet);
                },
                sourceCode
        );
    }

    @Override
    public String toString() {
        return op().getSourceCode() + " " + quantity() + " " + res().toStringCondensed();
    }
}
