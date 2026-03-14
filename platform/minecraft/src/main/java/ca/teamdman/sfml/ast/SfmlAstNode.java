package ca.teamdman.sfml.ast;

import ca.teamdman.antlr.IAstNode;

import java.util.stream.Stream;

public interface SfmlAstNode extends IAstNode<SfmlAstNode> {
    default Stream<ResourceIdentifier<?, ?, ?>> getReferencedIOResourceIds() {
        if (this instanceof IOStatement ioStatement) {
            return ioStatement.resourceLimits().resourceLimitList().stream()
                    .flatMap(resourceLimit -> resourceLimit.resourceIds().stream());
        }
        return getDescendantNodes()
                .filter(IOStatement.class::isInstance)
                .map(IOStatement.class::cast)
                .flatMap(statement -> statement.resourceLimits().resourceLimitList().stream())
                .flatMap(resourceLimit -> resourceLimit.resourceIds().stream());
    }
}
