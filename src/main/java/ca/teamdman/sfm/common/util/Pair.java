package ca.teamdman.sfm.common.util;

import ca.teamdman.sfml.ast.ASTNode;
import org.antlr.v4.runtime.ParserRuleContext;

public class Pair<F, S> {
    private final F first;
    private final S second;

    public Pair(F f, S s) {
        first = f;
        second = s;
    }

    public static <F,S>Pair<F,S> of(F first, S second) {
        return new Pair<>(first, second);
    }

    public S getSecond() {
        return second;
    }

    public F getFirst() {
        return first;
    }
}
