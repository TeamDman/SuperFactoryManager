package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.resourcetype.ResourceTypeContainer.ResourceType;

public interface WithClause extends ASTNode, ToStringPretty {

    <STACK> boolean matchesStack(
                                 ResourceType<STACK, ?, ?> resourceType,
                                 STACK stack);
}
