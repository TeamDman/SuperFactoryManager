package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.resourcetype.ResourceType;

public interface WithClause extends ASTNode, ToStringPretty {
    <ITEM> boolean matchesStack(
            ResourceType<?, ITEM, ?> resourceType,
            ITEM item
    );
}
