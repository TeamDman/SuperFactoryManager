package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.resourcetype.ResourceType;

public record WithTag(TagMatcher tagMatcher) implements ASTNode, WithClause, ToStringPretty {
    @Override
    public <ITEM> boolean matchesStack(
            ResourceType<?, ITEM, ?> resourceType,
            ITEM item
    ) {
        return resourceType.getTagsForStack(item).anyMatch(tagMatcher::testResourceLocation);
    }

    @Override
    public String toString() {
        return "TAG " + tagMatcher;
    }
}
