package ca.teamdman.sfml.ast;

import java.util.List;

public interface IOStatement extends Statement, ToStringPretty {
    ResourceAccess resourceAccess();
    ResourceLimits resourceLimits();
    boolean each();

    @Override
    default List<? extends SfmlAstNode> getChildNodes() {

        return List.of(resourceLimits(), resourceAccess());
    }

}
