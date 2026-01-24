package ca.teamdman.sfml.ast;

import java.util.List;

/**
 * Represents an if statement within a macro body.
 */
public record MacroIfStatement(
        BoolExpr condition,
        List<MacroStatement> thenBody,
        List<MacroStatement> elseBody
) implements MacroStatement {

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("if ");
        sb.append(condition).append(" then\n");
        for (MacroStatement stmt : thenBody) {
            sb.append("    ").append(stmt).append("\n");
        }
        if (!elseBody.isEmpty()) {
            sb.append("else\n");
            for (MacroStatement stmt : elseBody) {
                sb.append("    ").append(stmt).append("\n");
            }
        }
        sb.append("end");
        return sb.toString();
    }
}
