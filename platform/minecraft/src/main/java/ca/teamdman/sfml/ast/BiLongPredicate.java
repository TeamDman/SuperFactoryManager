package ca.teamdman.sfml.ast;

@FunctionalInterface
public interface BiLongPredicate {
    boolean test(
            long a,
            long b
    );

}
