package ca.teamdman.sfml.program_builder;

import ca.teamdman.sfml.ast.MacroDefinition;
import ca.teamdman.sfml.ast.ProtocolDefinition;
import ca.teamdman.sfml.ast.StructDefinition;

import java.util.List;

/**
 * Contains definitions that can be shared via library blocks.
 */
public record LibraryDefinitions(
        List<ProtocolDefinition> protocols,
        List<StructDefinition> structs,
        List<MacroDefinition> macros
) {

    public static final LibraryDefinitions EMPTY = new LibraryDefinitions(
            List.of(),
            List.of(),
            List.of()
    );

    /**
     * Merges this with another LibraryDefinitions, combining all definitions.
     */
    public LibraryDefinitions merge(LibraryDefinitions other) {
        return new LibraryDefinitions(
                concat(protocols, other.protocols),
                concat(structs, other.structs),
                concat(macros, other.macros)
        );
    }

    private static <T> List<T> concat(List<T> a, List<T> b) {
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;
        var result = new java.util.ArrayList<>(a);
        result.addAll(b);
        return List.copyOf(result);
    }
}
