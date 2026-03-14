package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.program.ProgramContext;

import java.util.ArrayDeque;
import java.util.Deque;

public interface Trigger extends Tickable {
    boolean shouldTick(ProgramContext context);

    Block getBlock();

    default int getConditionIndex(IfStatement statement) {
        Deque<SfmlAstNode> toVisit = new ArrayDeque<>();
        toVisit.add(this);
        int seen = 0;
        while (!toVisit.isEmpty()) {
            SfmlAstNode current = toVisit.pollFirst();
            if (current instanceof IfStatement ifStatement) {
                if (ifStatement == statement) {
                    return seen;
                }
                seen++;
            }
            toVisit.addAll(current.getChildNodes());
        }
        return -1;
    }

    default int getConditionCount() {
        Deque<SfmlAstNode> toVisit = new ArrayDeque<>();
        toVisit.add(this);
        int seen = 0;
        while (!toVisit.isEmpty()) {
            SfmlAstNode current = toVisit.pollFirst();
            if (current instanceof IfStatement) {
                seen++;
            }
            toVisit.addAll(current.getChildNodes());
        }
        return seen;
    }
}
