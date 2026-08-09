package ca.teamdman.sfml.ast;

import java.util.List;
import java.util.Optional;

/**
 * Represents a macro definition in the program.
 * Example:
 * macro smelt(machine: Smeltable, source, dest)
 *     input from source
 *     output to machine using input
 *     forget
 *     input from machine using output
 *     output to dest
 * end
 */
public record MacroDefinition(
        String name,
        List<MacroParameter> parameters,
        List<MacroStatement> body
) implements ASTNode {

    /**
     * Gets a parameter by name.
     */
    public Optional<MacroParameter> getParameter(String paramName) {
        return parameters.stream()
                .filter(p -> p.name().equals(paramName))
                .findFirst();
    }

    /**
     * Gets the index of a parameter by name.
     */
    public int getParameterIndex(String paramName) {
        for (int i = 0; i < parameters.size(); i++) {
            if (parameters.get(i).name().equals(paramName)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("macro ").append(name).append("(");
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(parameters.get(i));
        }
        sb.append(")\n");
        for (MacroStatement stmt : body) {
            sb.append("    ").append(stmt).append("\n");
        }
        sb.append("end");
        return sb.toString();
    }
}
