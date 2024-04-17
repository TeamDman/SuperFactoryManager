package ca.teamdman.sfml.ast;

import java.util.List;
import java.util.stream.Stream;

public interface ASTNode {
    default List<Statement> getStatements() {
        return List.of();
    }

    default Stream<Statement> getDescendantStatements() {
        Stream.Builder<Statement> builder = Stream.builder();
        getStatements().forEach(s -> {
            builder.accept(s);
            s.getDescendantStatements().forEach(builder);
        });
        return builder.build();
    }

    default Stream<ResourceIdentifier<?, ?, ?>> getReferencedIOResourceIds() {
        return getDescendantStatements()
                .filter(IOStatement.class::isInstance)
                .map(IOStatement.class::cast)
                .flatMap(statement -> statement.resourceLimits().resourceLimits().stream())
                .map(ResourceLimit::resourceId);
    }
}
