package ca.teamdman.sfml;

import ca.teamdman.sfml.program_builder.ProgramBuildResult;
import ca.teamdman.sfml.program_builder.ProgramBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

public class NumExprFunctionsTest {

    private void assertBuilds(String program) {
        ProgramBuildResult result = ProgramBuilder.build(program);
        if (!result.isBuildSuccessful()) {
            StringBuilder sb = new StringBuilder();
            result.metadata().errors().forEach(e -> sb.append(e.getKey()).append("\n"));
            // extra diagnostics
            var ce = SFMLTestHelpers.getCompileErrors(program);
            sb.append("Lexer errors:\n");
            ce.lexerErrors().forEach(s -> sb.append(s).append("\n"));
            sb.append("Parser errors:\n");
            ce.parserErrors().forEach(s -> sb.append(s).append("\n"));
            sb.append("Visit problems:\n");
            ce.visitProblems().forEach(t -> sb.append(t.getClass().getSimpleName()).append(": ").append(t.getMessage()).append("\n"));
            fail("Program failed to build. Errors:\n" + sb);
        }
    }

    @Test
    public void parses_get_label_count_in_has_condition() {
        String prog = """
                NAME "test"
                EVERY 20 TICKS DO
                    IF OVERALL a HAS >= get_label_count(a) THEN
                    END
                END
                """;
        assertBuilds(prog);
    }

    @Test
    public void parses_get_thing_count_with_matchers_and_math() {
        String prog = """
                NAME "test2"
                EVERY 20 TICKS DO
                    IF SOME a HAS > (get_thing_count(a, minecraft:iron_ingot OR minecraft:gold_ingot) + 2 * 3) THEN
                    END
                END
                """;
        assertBuilds(prog);
    }

    @Test
    public void parses_redstone_with_numexpr_and_function() {
        String prog = """
                NAME "test3"
                EVERY 20 TICKS DO
                    IF REDSTONE >= get_label_count(a) + 5 THEN
                    END
                END
                """;
        assertBuilds(prog);
    }

    @Test
    public void parses_output_limit_with_functions_and_ops() {
        String prog = """
                NAME "io-test-out"
                EVERY 20 TICKS DO
                    OUTPUT get_thing_count(a) / (get_label_count(b) + 1) TO c
                END
                """;
        assertBuilds(prog);
    }

    @Test
    public void parses_input_limit_with_functions_and_ops() {
        String prog = """
                NAME "io-test-in"
                EVERY 20 TICKS DO
                    INPUT get_thing_count(a, minecraft:iron_ingot) + 2 FROM b
                END
                """;
        assertBuilds(prog);
    }

    @Test
    public void parses_output_limit_with_function_only() {
        String prog = """
                NAME "io-test-out2"
                EVERY 20 TICKS DO
                    OUTPUT get_label_count(a) TO c
                END
                """;
        assertBuilds(prog);
    }

    @Test
    public void parses_input_limit_with_function_only() {
        String prog = """
                NAME "io-test-in2"
                EVERY 20 TICKS DO
                    INPUT get_label_count(a) FROM b
                END
                """;
        assertBuilds(prog);
    }
}
