package ca.teamdman.sfml.ast;

import java.util.List;

public record Label(String value) implements SfmlAstNode {
    @Override
    public String toString() {
        return value;
    }

    public static boolean needsQuotes(String label) {
        return !label.matches("[a-zA-Z_][a-zA-Z0-9_]*");
    }

    @Override
    public List<? extends SfmlAstNode> getChildNodes() {

        return List.of();
    }

}
