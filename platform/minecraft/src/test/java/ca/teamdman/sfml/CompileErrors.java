package ca.teamdman.sfml;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

public record CompileErrors(
        List<String> lexerErrors,
        List<String> parserErrors,
        List<Throwable> visitProblems
) implements Result {
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
