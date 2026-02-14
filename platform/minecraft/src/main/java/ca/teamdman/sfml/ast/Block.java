package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.program.ProgramContext;
import com.github.bsideup.jabel.Desugar;

import java.util.List;

@Desugar
public record Block(List<Statement> statements) implements Statement {
    @Override
    public void tick(ProgramContext context) {
        for (Statement statement : statements) {
            long start = System.nanoTime();
            statement.tick(context);
            float elapsed = (System.nanoTime() - start) / 1_000_000f;
            if (statement instanceof ToStringPretty ps) {
                context.getLogger().info(x -> x.accept(LocalizationKeys.PROGRAM_TICK_STATEMENT_TIME_MS.get(
                        elapsed,
                        ps.toStringPretty()
                )));
            } else {
                context.getLogger().info(x -> x.accept(LocalizationKeys.PROGRAM_TICK_STATEMENT_TIME_MS.get(
                        elapsed,
                        statement.toString()
                )));
            }
        }
    }

    @Override
    public String toString() {
        var rtn = new StringBuilder();
        for (Statement statement : statements) {
            if (statement instanceof InputStatement ins) {
                rtn.append(ins.toStringPretty().trim());
            } else if (statement instanceof OutputStatement outs) {
                rtn.append(outs.toStringPretty().trim());
            } else {
                rtn.append(statement.toString().trim());
            }
            rtn.append("\n");
        }
        return rtn.toString().trim();
    }

    @Override
    public List<Statement> getStatements() {
        return statements;
    }
}
