package ca.teamdman.sfml.program_builder;

import java.util.Optional;

/**
 * Interface for resolving library block references in SFML programs.
 * Implementations look up library blocks by their label in the cable network.
 */
@FunctionalInterface
public interface LibraryResolver {

    /**
     * Resolves a library block by its label.
     *
     * @param blockLabel the label of the library block (e.g., "factory_config")
     * @return the library definitions from the block, or empty if not found
     */
    Optional<LibraryDefinitions> resolve(String blockLabel);

    /**
     * A resolver that always fails to find libraries.
     */
    LibraryResolver NONE = label -> Optional.empty();
}
