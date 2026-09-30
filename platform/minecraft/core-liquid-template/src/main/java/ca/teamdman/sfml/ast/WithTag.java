package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.resourcetype.ResourceType;

public record WithTag(TagMatcher tagMatcher) implements ASTNode, WithClause, ToStringPretty {
    @Override
    public <STACK> boolean matchesStack(
            ResourceType<STACK, ?, ?> resourceType,
            STACK stack
    ) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        return resourceType.getTagsForStack(stack).anyMatch(tagMatcher::testResourceLocation);
{% when '26.1.2' %}
        return resourceType.getTagsForStack(stack).anyMatch(tagMatcher::testIdentifier);
{% endcase %}
    }

    @Override
    public String toString() {
        return "TAG " + tagMatcher;
    }
}
