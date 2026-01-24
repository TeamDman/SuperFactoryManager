package ca.teamdman.sfml.program_builder;

import java.util.Optional;

/**
 * Interface for resolving file imports in SFML programs.
 * Implementations can resolve imports from the filesystem, resources, or other sources.
 */
@FunctionalInterface
public interface ImportResolver {

    /**
     * Resolves an import path to its SFML source code.
     *
     * @param path the import path (e.g., "machines.sfml")
     * @return the SFML source code, or empty if not found
     */
    Optional<String> resolve(String path);

    /**
     * A resolver that always fails to find imports.
     */
    ImportResolver NONE = path -> Optional.empty();
}
