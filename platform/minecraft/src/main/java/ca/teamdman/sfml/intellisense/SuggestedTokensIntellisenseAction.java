package ca.teamdman.sfml.intellisense;

import ca.teamdman.langs.SFMLLexer;
import ca.teamdman.sfml.manipulation.ManipulationResult;
import net.minecraft.network.chat.Component;
import org.antlr.v4.runtime.Vocabulary;

import java.util.List;

public record SuggestedTokensIntellisenseAction(
        Integer nextTokenType,
        List<Integer> followingTokenTypes,
        Vocabulary vocabulary
) implements IntellisenseAction {
    @Override
    public Component getComponent() {
        return Component.literal(getDisplay());
    }

    // If previous line ends with THEN or DO, let x be that lines indent. Suggest x + 4 spaces
    // Circle spinner: after 5 seconds of inactivity, the document will be auto-formatted.
    // Add suggestions for each label in the program

    /*
    - If suggesting NAME nextTokenType and NAME already present, jump cursor to inside existing string nextTokenType followingTokenTypes existing NAME nextTokenType
    - If suggesting NAME nextTokenType and NAME already present but STRING followingTokenTypes name missing, insert string and place cursor inside
     */
    @Override
    public ManipulationResult perform(IntellisenseContext context) {
        MutableProgramString programStringMut = context.createMutableProgramString();
        String word = programStringMut.getWord();
        int cursorInWord = programStringMut.cursorInWord();
        switch (nextTokenType) {
            case SFMLLexer.NAME -> {
                programStringMut.replaceWordAndMoveCursorsToEnd(vocabulary.getSymbolicName(nextTokenType) + " \"\"");
                programStringMut.offsetCursors(-1);
            }
            case SFMLLexer.STRING -> {
                if (word.equals("\"\"")) {
                    if (cursorInWord != 2) {
                        // move the cursor to the right of the last double quote
                        programStringMut.offsetCursors(1);
                    } else {
                        // cursor already at the end of the double quote, insert new line
                        programStringMut.insertTextWithoutMovingCursors("\n");
                        programStringMut.offsetCursors(1);
                    }
                } else if (word.isBlank()) {
                    // insert double quote pair
                    programStringMut.replaceWordAndMoveCursorsToEnd("\"\"");
                    programStringMut.offsetCursors(-1);
                } else if (word.contains("\"")) {
                    int offset = word.length() - cursorInWord;
                    if (offset > 0) {
                        // move to the end of the word
                        programStringMut.offsetCursors(offset);
                    } else {
                        // insert newline
                        programStringMut.insertTextWithoutMovingCursors("\n");
                        programStringMut.offsetCursors(1);
                    }
                } else {
                    // insert a closing quote
                    programStringMut.insertTextWithoutMovingCursors("\"");
                    programStringMut.offsetCursors(1);
                }
            }
            case SFMLLexer.DO -> {
                programStringMut.replaceWordAndMoveCursorsToEnd("DO\n    \nEND");
                programStringMut.offsetCursors(-4);
            }
            default -> programStringMut.replaceWordAndMoveCursorsToEnd(getDisplay() + " ");
        }
        return new ManipulationResult(
                programStringMut.getContent(),
                programStringMut.getCursorPosition(),
                programStringMut.getSelectionCursorPosition()
        );
    }

    private String getDisplay() {
        StringBuilder display = new StringBuilder();
        display.append(vocabulary.getSymbolicName(nextTokenType));
        for (Integer type : followingTokenTypes) {
            display.append(" ");
            display.append(vocabulary.getSymbolicName(type));
        }
        return display.toString();
    }
}
