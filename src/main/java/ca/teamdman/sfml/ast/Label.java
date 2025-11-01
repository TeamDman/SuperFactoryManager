package ca.teamdman.sfml.ast;

public record Label(String name) implements ASTNode {
    @Override
    public String toString() {
        return name;
    }

    public static boolean needsQuotes(String label) {
        return !label.matches("[a-zA-Z_][a-zA-Z0-9_]*");
    }
}
