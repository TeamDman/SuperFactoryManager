package ca.teamdman.sfml.program_builder;

import java.util.List;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

public class ListErrorListener extends BaseErrorListener {

    private final List<String> errors;

    public ListErrorListener(List<String> errors) {
        this.errors = errors;
    }

    @Override
    public void syntaxError(
                            Recognizer<?, ?> recognizer,
                            Object offendingSymbol,
                            int line,
                            int charPositionInLine,
                            String msg,
                            RecognitionException e) {
        errors.add("line " + line + ":" + charPositionInLine + " " + msg);
    }
}
