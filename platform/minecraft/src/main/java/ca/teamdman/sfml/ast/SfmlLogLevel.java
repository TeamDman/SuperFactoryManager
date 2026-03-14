package ca.teamdman.sfml.ast;

import org.apache.logging.log4j.Level;

import java.util.List;

public record SfmlLogLevel(Level level) implements SfmlAstNode {
    @Override
    public List<? extends SfmlAstNode> getChildNodes() {
        return List.of();
    }

    @Override
    public String toString() {
        return level.name();
    }
}
