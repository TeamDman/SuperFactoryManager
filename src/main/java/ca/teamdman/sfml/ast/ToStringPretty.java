package ca.teamdman.sfml.ast;

public interface ToStringPretty {
    default String toStringPretty() {
        return this.toString();
    }
}
