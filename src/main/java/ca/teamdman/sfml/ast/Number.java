package ca.teamdman.sfml.ast;

public record Number(long value) implements ASTNode {
    @Override
    public String toString() {
        return String.valueOf(value);
    }

    public Number add(Number number) {
        return new Number(value + number.value);
    }
}
