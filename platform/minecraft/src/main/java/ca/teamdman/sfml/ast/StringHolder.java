package ca.teamdman.sfml.ast;

import java.util.List;

public record StringHolder(String value) implements SfmlAstNode, Displayable {
    @Override
    public List<? extends SfmlAstNode> getChildNodes() {

        return List.of();
    }

    @Override
    public String display() {

        return value;
    }

}
