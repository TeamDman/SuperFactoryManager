package ca.teamdman.sfm.common.util;

public class Pair<F, S> {
    private final F first;
    private final S second;

    public Pair(F f, S s) {
        first = f;
        second = s;
    }

    public S getSecond() {
        return second;
    }

    public F getFirst() {
        return first;
    }
}
