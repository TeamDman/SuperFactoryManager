package ca.teamdman.sfml.test;

import ca.teamdman.langs.SFMLLexer;
import ca.teamdman.langs.SFMLParser;
import ca.teamdman.sfml.ast.ASTBuilder;
import ca.teamdman.sfml.ast.Program;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class SFMLTestHelpers {
    public static CompileErrors getCompileErrors(String input) {
        var lexer = new SFMLLexer(CharStreams.fromString(input));
        var tokens = new CommonTokenStream(lexer);
        var parser = new SFMLParser(tokens);
        var builder = new ASTBuilder();
        var lexerErrors = new ArrayList<String>();
        var parserErrors = new ArrayList<String>();
        var visitProblems = new ArrayList<Throwable>();
        lexer.removeErrorListeners();
        lexer.addErrorListener(new Program.ListErrorListener(lexerErrors));
        parser.removeErrorListeners();
        parser.addErrorListener(new Program.ListErrorListener(parserErrors));
        var context = parser.program();
        if (lexerErrors.isEmpty() && parserErrors.isEmpty()) { // don't build if syntax errors present
            try {
                //noinspection unused
                var program = builder.visitProgram(context);
            } catch (Exception e) {
                visitProblems.add(e);
            }
        }

        return new CompileErrors(lexerErrors, parserErrors, visitProblems);
    }

    public static void assertNoCompileErrors(String program) {
        CompileErrors compileErrors = getCompileErrors(program);
        compileErrors.printStackStraces();
        assertEquals(
                compileErrors,
                CompileErrors.NONE
        );
    }

    public static void assertCompileErrorsPresent(
            String program,
            CompileErrors expected
    ) {
        CompileErrors compileErrors = getCompileErrors(program);
        compileErrors.printStackStraces();
        assertEquals(
                compileErrors,
                expected
        );
    }
    public static void assertCompileErrorsPresent(
            String program,
            Throwable... visitProblems
    ) {
        CompileErrors compileErrors = getCompileErrors(program);
        compileErrors.printStackStraces();
        assertEquals(
                compileErrors,
                new CompileErrors(visitProblems)
        );
    }

    public static void assertCompileErrorsPresent(
            String program
    ) {
        assertNotEquals(
                getCompileErrors(program),
                CompileErrors.NONE
        );
    }

    public static Program compile(String input) {
        var lexer = new SFMLLexer(CharStreams.fromString(input));
        var tokens = new CommonTokenStream(lexer);
        var parser = new SFMLParser(tokens);
        var builder = new ASTBuilder();
        var context = parser.program();
        return builder.visitProgram(context);
    }

    public record CompileErrors(
            List<String> lexerErrors,
            List<String> parserErrors,
            List<Throwable> visitProblems
    ) {
        public static final CompileErrors NONE = new CompileErrors(List.of(), List.of(), List.of());

        public CompileErrors(Throwable... visitProblems) {
            this(List.of(), List.of(), List.of(visitProblems));
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof CompileErrors other)) return false;
            if (!this.lexerErrors.equals(other.lexerErrors)) return false;
            if (!this.parserErrors.equals(other.parserErrors)) return false;
            if (this.visitProblems.size() != other.visitProblems.size()) return false;
            for (int i = 0; i < this.visitProblems.size(); i++) {
                if (!this.visitProblems.get(i).getClass().equals(other.visitProblems.get(i).getClass())) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public String toString() {
            return "CompileErrors{" +
                   "lexerErrors=" + lexerErrors +
                   ", parserErrors=" + parserErrors +
                   ", visitProblems=" + visitProblems +
                   '}';
        }

        public void printStackStraces() {
            System.out.printf("There are %d lexerErrors\n", lexerErrors.size());
            System.out.printf("There are %d parserErrors\n", parserErrors.size());
            System.out.printf("There are %d visitProblems\n", visitProblems.size());
            for (var e : visitProblems) {
                var sw = new StringWriter();
                var pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                System.out.println(sw + "\n");
            }
        }
    }
}
