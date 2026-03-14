package ca.teamdman.antlr;

import java.util.List;
import java.util.stream.Stream;

public interface IAstNode<T extends IAstNode<?>> {
    List<? extends T> getChildNodes();

    default Stream<? extends T> getDescendantNodes() {

        Stream.Builder<T> builder = Stream.builder();
        for (T s : getChildNodes()) {
            builder.accept(s);
            s.getDescendantNodes().forEach(x -> builder.accept((T) x));
        }
        return builder.build();
    }

    default <T> Stream<T> getDescendantNodes(Class<T> clazz) {

        return getDescendantNodes().filter(clazz::isInstance).map(clazz::cast);
    }

}
