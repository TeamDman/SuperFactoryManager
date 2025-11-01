package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.program.ProgramContext;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public interface Trigger extends Statement {
    boolean shouldTick(ProgramContext context);

    Block getBlock();

    @Override
    default List<Statement> getStatements() {
        return List.of(getBlock());
    }

    default int getConditionIndex(IfStatement statement) {
        Deque<Statement> toVisit = new ArrayDeque<>();
        toVisit.add(this);
        int seen = 0;
        while (!toVisit.isEmpty()) {
            Statement current = toVisit.pollFirst();
            if (current instanceof IfStatement ifStatement) {
                if (ifStatement == statement) {
                    return seen;
                }
                seen++;
            }
            toVisit.addAll(current.getStatements());
        }
        return -1;
    }

    default int getConditionCount() {
        Deque<Statement> toVisit = new ArrayDeque<>();
        toVisit.add(this);
        int seen = 0;
        while (!toVisit.isEmpty()) {
            Statement current = toVisit.pollFirst();
            if (current instanceof IfStatement) {
                seen++;
            }
            toVisit.addAll(current.getStatements());
        }
        return seen;
    }
}
