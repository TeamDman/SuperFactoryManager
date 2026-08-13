package ca.teamdman.sfm.client.explorer.action;

import ca.teamdman.sfm.client.explorer.SFMPath;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** Pure compatibility check used while preflighting explorer transactions. */
@FunctionalInterface
public interface SFMExplorerPathPolicy {
    Optional<String> incompatibility(SFMPath path);

    default boolean supports(SFMPath path) {
        return incompatibility(path).isEmpty();
    }

    static SFMExplorerPathPolicy schemes(Set<String> schemes) {
        Objects.requireNonNull(schemes, "schemes");
        Set<String> supported = Set.copyOf(new TreeSet<>(schemes));
        if (supported.isEmpty()) throw new IllegalArgumentException("At least one path scheme is required");
        return path -> supported.contains(Objects.requireNonNull(path, "path").scheme())
                ? Optional.empty()
                : Optional.of("Explorer does not support path scheme `" + path.scheme() + "`");
    }
}
