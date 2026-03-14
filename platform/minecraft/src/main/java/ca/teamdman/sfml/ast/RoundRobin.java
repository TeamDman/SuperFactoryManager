package ca.teamdman.sfml.ast;

import java.util.List;

public record RoundRobin(RoundRobinBehaviour behaviour) implements SfmlAstNode {

    @Override
    public String toString() {

        return switch (behaviour) {
            case UNMODIFIED -> "NOT ROUND ROBIN";
            case BY_BLOCK -> "ROUND ROBIN BY BLOCK";
            case BY_LABEL -> "ROUND ROBIN BY LABEL";
        };
    }

    public boolean isModified() {

        return behaviour != RoundRobinBehaviour.UNMODIFIED;
    }

    @Override
    public List<RoundRobinBehaviour> getChildNodes() {

        return List.of(behaviour);
    }

}
