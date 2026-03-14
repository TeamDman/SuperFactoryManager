package ca.teamdman.sfml;

import ca.teamdman.sfml.ast.LogExpression;
import ca.teamdman.sfml.ast.SFMLProgram;
import ca.teamdman.sfml.program_builder.SFMLProgramBuilder;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SfmlLogExpressionTests {

    @Test
    public void printHelloWorld() {
        String programString = "print \"Hello, world!\"";
        SFMLProgram program = new SFMLProgramBuilder(programString).build().unwrapProgram();

        List<LogExpression> topLevelLogExpressions = program.topLevelLogExpressions();
        assertEquals(1, topLevelLogExpressions.size());

        LogExpression logExpression = topLevelLogExpressions.get(0);
        assertEquals(Level.INFO, logExpression.logLevel().level());
        assertEquals("Hello, world!", logExpression.message().display());
    }

    @Test
    public void infoStatement() {
        String programString = "info \"Info message\"";
        SFMLProgram program = new SFMLProgramBuilder(programString).build().unwrapProgram();

        List<LogExpression> topLevelLogExpressions = program.topLevelLogExpressions();
        assertEquals(1, topLevelLogExpressions.size());

        LogExpression logExpression = topLevelLogExpressions.get(0);
        assertEquals(Level.INFO, logExpression.logLevel().level());
        assertEquals("Info message", logExpression.message().display());
    }

    @Test
    public void debugStatement() {
        String programString = "debug \"Debug message\"";
        SFMLProgram program = new SFMLProgramBuilder(programString).build().unwrapProgram();

        List<LogExpression> topLevelLogExpressions = program.topLevelLogExpressions();
        assertEquals(1, topLevelLogExpressions.size());

        LogExpression logExpression = topLevelLogExpressions.get(0);
        assertEquals(Level.DEBUG, logExpression.logLevel().level());
        assertEquals("Debug message", logExpression.message().display());
    }

    @Test
    public void warnStatement() {
        String programString = "warn \"Warn message\"";
        SFMLProgram program = new SFMLProgramBuilder(programString).build().unwrapProgram();

        List<LogExpression> topLevelLogExpressions = program.topLevelLogExpressions();
        assertEquals(1, topLevelLogExpressions.size());

        LogExpression logExpression = topLevelLogExpressions.get(0);
        assertEquals(Level.WARN, logExpression.logLevel().level());
        assertEquals("Warn message", logExpression.message().display());
    }

    @Test
    public void warningStatement() {
        String programString = "warning \"Warning message\"";
        SFMLProgram program = new SFMLProgramBuilder(programString).build().unwrapProgram();

        List<LogExpression> topLevelLogExpressions = program.topLevelLogExpressions();
        assertEquals(1, topLevelLogExpressions.size());

        LogExpression logExpression = topLevelLogExpressions.get(0);
        assertEquals(Level.WARN, logExpression.logLevel().level());
        assertEquals("Warning message", logExpression.message().display());
    }

    @Test
    public void errorStatement() {
        String programString = "error \"Error message\"";
        SFMLProgram program = new SFMLProgramBuilder(programString).build().unwrapProgram();

        List<LogExpression> topLevelLogExpressions = program.topLevelLogExpressions();
        assertEquals(1, topLevelLogExpressions.size());

        LogExpression logExpression = topLevelLogExpressions.get(0);
        assertEquals(Level.ERROR, logExpression.logLevel().level());
        assertEquals("Error message", logExpression.message().display());
    }

    @Test
    public void traceStatement() {
        String programString = "trace \"Trace message\"";
        SFMLProgram program = new SFMLProgramBuilder(programString).build().unwrapProgram();

        List<LogExpression> topLevelLogExpressions = program.topLevelLogExpressions();
        assertEquals(1, topLevelLogExpressions.size());

        LogExpression logExpression = topLevelLogExpressions.get(0);
        assertEquals(Level.TRACE, logExpression.logLevel().level());
        assertEquals("Trace message", logExpression.message().display());
    }

    @Test
    public void logInfoStatement() {
        String programString = "log info \"Log info message\"";
        SFMLProgram program = new SFMLProgramBuilder(programString).build().unwrapProgram();

        List<LogExpression> topLevelLogExpressions = program.topLevelLogExpressions();
        assertEquals(1, topLevelLogExpressions.size());

        LogExpression logExpression = topLevelLogExpressions.get(0);
        assertEquals(Level.INFO, logExpression.logLevel().level());
        assertEquals("Log info message", logExpression.message().display());
    }

    @Test
    public void logDebugStatement() {
        String programString = "log debug \"Log debug message\"";
        SFMLProgram program = new SFMLProgramBuilder(programString).build().unwrapProgram();

        List<LogExpression> topLevelLogExpressions = program.topLevelLogExpressions();
        assertEquals(1, topLevelLogExpressions.size());

        LogExpression logExpression = topLevelLogExpressions.get(0);
        assertEquals(Level.DEBUG, logExpression.logLevel().level());
        assertEquals("Log debug message", logExpression.message().display());
    }

    @Test
    public void multipleLogExpressions() {
        String programString = """
                print "First"
                debug "Second"
                error "Third"
                """;
        SFMLProgram program = new SFMLProgramBuilder(programString).build().unwrapProgram();

        List<LogExpression> topLevelLogExpressions = program.topLevelLogExpressions();
        assertEquals(3, topLevelLogExpressions.size());

        assertEquals(Level.INFO, topLevelLogExpressions.get(0).logLevel().level());
        assertEquals("First", topLevelLogExpressions.get(0).message().display());

        assertEquals(Level.DEBUG, topLevelLogExpressions.get(1).logLevel().level());
        assertEquals("Second", topLevelLogExpressions.get(1).message().display());

        assertEquals(Level.ERROR, topLevelLogExpressions.get(2).logLevel().level());
        assertEquals("Third", topLevelLogExpressions.get(2).message().display());
    }

    @Test
    public void logExpressionWithTrigger() {
        String programString = """
                print "Before trigger"
                every 20 ticks do
                    input from a
                    output to b
                end
                print "After trigger"
                """;
        SFMLProgram program = new SFMLProgramBuilder(programString).build().unwrapProgram();

        List<LogExpression> topLevelLogExpressions = program.topLevelLogExpressions();
        assertEquals(2, topLevelLogExpressions.size());

        assertEquals("Before trigger", topLevelLogExpressions.get(0).message().display());
        assertEquals("After trigger", topLevelLogExpressions.get(1).message().display());

        // Also verify we have the trigger
        assertEquals(1, program.triggers().size());
    }

    @Test
    public void logAsLabelName() {
        // "log" should be usable as a label name since it's added to the identifier rule
        String programString = """
                every 20 ticks do
                    input from log
                    output to chest
                end
                """;
        SFMLProgram program = new SFMLProgramBuilder(programString).build().unwrapProgram();

        assertEquals(1, program.triggers().size());
        assertTrue(program.referencedLabels().contains("log"));
    }

    @Test
    public void printInsideTriggerBlock() {
        // Log expressions should NOT be allowed inside trigger blocks
        // They are only valid at the top level
        String programString = """
                every 20 ticks do
                    input from a
                    output to b
                    print "hi"
                end
                """;
        // This should fail to compile since print is not a valid statement inside a block
        var result = new SFMLProgramBuilder(programString).build();
        assertTrue(result.isSuccess(), "print inside trigger block should not compile");
    }

    @Test
    public void printNumberExpression() {
        String programString = "print 2+2";
        SFMLProgram program = new SFMLProgramBuilder(programString).build().unwrapProgram();

        List<LogExpression> topLevelLogExpressions = program.topLevelLogExpressions();
        assertEquals(1, topLevelLogExpressions.size());

        LogExpression logExpression = topLevelLogExpressions.get(0);
        assertEquals(Level.INFO, logExpression.logLevel().level());
        assertEquals("4", logExpression.message().display());
    }

    @Test
    public void printComplexNumberExpression() {
        String programString = "print (10 - 2) * 3";
        SFMLProgram program = new SFMLProgramBuilder(programString).build().unwrapProgram();

        List<LogExpression> topLevelLogExpressions = program.topLevelLogExpressions();
        assertEquals(1, topLevelLogExpressions.size());

        LogExpression logExpression = topLevelLogExpressions.get(0);
        assertEquals(Level.INFO, logExpression.logLevel().level());
        assertEquals("24", logExpression.message().display());
    }

    @Test
    public void debugNumberExpression() {
        String programString = "debug 100 / 5";
        SFMLProgram program = new SFMLProgramBuilder(programString).build().unwrapProgram();

        List<LogExpression> topLevelLogExpressions = program.topLevelLogExpressions();
        assertEquals(1, topLevelLogExpressions.size());

        LogExpression logExpression = topLevelLogExpressions.get(0);
        assertEquals(Level.DEBUG, logExpression.logLevel().level());
        assertEquals("20", logExpression.message().display());
    }

    @Test
    public void logInfoNumberExpression() {
        String programString = "log info 2 ^ 10";
        SFMLProgram program = new SFMLProgramBuilder(programString).build().unwrapProgram();

        List<LogExpression> topLevelLogExpressions = program.topLevelLogExpressions();
        assertEquals(1, topLevelLogExpressions.size());

        LogExpression logExpression = topLevelLogExpressions.get(0);
        assertEquals(Level.INFO, logExpression.logLevel().level());
        assertEquals("1024", logExpression.message().display());
    }

    @Test
    public void printModulusExpression() {
        String programString = "print 17 % 5";
        SFMLProgram program = new SFMLProgramBuilder(programString).build().unwrapProgram();

        List<LogExpression> topLevelLogExpressions = program.topLevelLogExpressions();
        assertEquals(1, topLevelLogExpressions.size());

        LogExpression logExpression = topLevelLogExpressions.get(0);
        assertEquals(Level.INFO, logExpression.logLevel().level());
        assertEquals("2", logExpression.message().display());
    }
}
