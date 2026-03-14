package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.program.ProgramContext;

import java.util.List;

public record Block(List<Tickable> statements) implements Tickable {
    @Override
    public void tick(ProgramContext context) {
        for (Tickable statement : statements) {
            long start = System.nanoTime();
            statement.tick(context);
            float elapsed = (System.nanoTime() - start) / 1_000_000f;
            if (statement instanceof ToStringPretty ps) {

                context.logger().info(x -> x.accept(LocalizationKeys.PROGRAM_TICK_STATEMENT_TIME_MS.get(
                        elapsed,
                        ps.toStringPretty()
                )));
            } else {

                context.logger().info(x -> x.accept(LocalizationKeys.PROGRAM_TICK_STATEMENT_TIME_MS.get(
                        elapsed,
                        statement.toString()
                )));
            }
        }
    }

    @Override
    public String toString() {
        var rtn = new StringBuilder();
        for (Tickable statement : statements) {
            if (statement instanceof InputStatement ins) {
                rtn.append(ins.toStringPretty().strip());
            } else if (statement instanceof OutputStatement outs) {
                rtn.append(outs.toStringPretty().strip());
            } else {
                rtn.append(statement.toString().strip());
            }
            rtn.append("\n");
        }
        return rtn.toString().strip();
    }

    @Override
    public List<Tickable> getChildNodes() {
        return statements;
    }
}
