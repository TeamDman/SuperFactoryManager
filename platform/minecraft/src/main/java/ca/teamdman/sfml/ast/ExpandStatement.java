package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.program.ProgramContext;

import java.util.List;

/**
 * Represents an expand statement that invokes a macro.
 * Example: {@code expand smelt(furnace, ore_chest, ingot_chest)}
 * <p>
 * Macros are expanded at compile time (during AST building). The {@code expandedStatements}
 * field contains the result of macro expansion - concrete InputStatement, OutputStatement,
 * ForgetStatement, and IfStatement instances with parameters substituted.
 *
 * @param macroName          The name of the macro being expanded
 * @param arguments          The arguments passed to the macro
 * @param expandedStatements The statements produced by macro expansion (populated at compile time)
 */
public record ExpandStatement(
        String macroName,
        List<ExpandArgument> arguments,
        List<Statement> expandedStatements
) implements Statement {

    @Override
    public void tick(ProgramContext context) {
        // Execute the expanded statements
        for (Statement statement : expandedStatements) {
            statement.tick(context);
        }
    }

    @Override
    public List<Statement> getStatements() {
        return expandedStatements;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("expand ");
        sb.append(macroName).append("(");
        for (int i = 0; i < arguments.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(arguments.get(i));
        }
        sb.append(")");
        return sb.toString();
    }
}
