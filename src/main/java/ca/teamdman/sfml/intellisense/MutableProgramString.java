package ca.teamdman.sfml.intellisense;

import ca.teamdman.sfml.manipulation.ManipulationResult;

@SuppressWarnings("UnusedReturnValue")
public class MutableProgramString {
    private StringBuilder content;
    private int cursorPosition;
    private int selectionCursorPosition;

    public MutableProgramString(
            String content,
            int cursorPosition,
            int selectionCursorPosition
    ) {
        this.content = new StringBuilder(content);
        this.cursorPosition = cursorPosition;
        this.selectionCursorPosition = selectionCursorPosition;
    }

    public int getWordBeginning() {
        int start = cursorPosition;
        while (start > 0 && !Character.isWhitespace(content.charAt(start - 1))) {
            start--;
        }
        return start;
    }

    public int getWordEnd() {
        int end = cursorPosition;
        while (end < content.length() && !Character.isWhitespace(content.charAt(end))) {
            end++;
        }
        return end;
    }

    public String getWord() {
        return content.substring(getWordBeginning(), getWordEnd());
    }

    public MutableProgramString replaceWordAndMoveCursorsToEnd(String suggestion) {
        int start = getWordBeginning();
        int end = getWordEnd();
        content.replace(start, end, suggestion);
        cursorPosition = start + suggestion.length();
        selectionCursorPosition = start + suggestion.length();
        return this;
    }

    public MutableProgramString insertTextWithoutMovingCursors(String text) {
        content.insert(cursorPosition, text);
        return this;
    }

    public String getContent() {
        return content.toString();
    }

    public MutableProgramString setContent(StringBuilder content) {
        this.content = content;
        return this;
    }

    public int getCursorPosition() {
        return cursorPosition;
    }

    public int getSelectionCursorPosition() {
        return selectionCursorPosition;
    }

    public MutableProgramString offsetCursors(int offset) {
        cursorPosition += offset;
        selectionCursorPosition += offset;
        return this;
    }

    public int cursorInWord() {
        return cursorPosition - getWordBeginning();
    }

    public ManipulationResult intoResult() {
        return new ManipulationResult(
                getContent(),
                getCursorPosition(),
                getSelectionCursorPosition()
        );
    }
}
