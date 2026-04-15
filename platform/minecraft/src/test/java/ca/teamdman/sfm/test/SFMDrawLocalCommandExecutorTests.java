package ca.teamdman.sfm.test;

import ca.teamdman.sfm.client.draw.SFMDrawLocalCommandExecutor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMDrawLocalCommandExecutorTests {
    @Test
    public void topLevelLocalSuggestionsIncludeOllama() {
        SFMDrawLocalCommandExecutor.CompletionSuggestions suggestions = SFMDrawLocalCommandExecutor.suggestCompletions(
                "ol",
                2,
                "@rect[1,2]"
        );

        assertTrue(suggestions.suggestions().contains("ollama"));
    }

    @Test
    public void openSuggestionsIncludeDefaultCanvasPaths() {
        SFMDrawLocalCommandExecutor.CompletionSuggestions suggestions = SFMDrawLocalCommandExecutor.suggestCompletions(
                "open ",
                5,
                "@rect[1,2]"
        );

        assertTrue(suggestions.suggestions().contains("canvas"));
        assertTrue(suggestions.suggestions().contains("/user/home/canvas"));
    }

    @Test
    public void concatenateSuggestionsIncludeDynamicRectSelector() {
        SFMDrawLocalCommandExecutor.CompletionSuggestions suggestions = SFMDrawLocalCommandExecutor.suggestCompletions(
                "concatenate ",
                12,
                "@rect[1,2]"
        );

        assertTrue(suggestions.suggestions().contains("@rect[1,2]"));
        assertTrue(suggestions.suggestions().contains("@relative[0,-10]"));
        assertTrue(suggestions.suggestions().contains("@nearest[text]"));
        assertFalse(suggestions.suggestions().contains("concantenate"));
    }

    @Test
    public void rectangleSuggestionsIncludeListSubcommand() {
        SFMDrawLocalCommandExecutor.CompletionSuggestions suggestions = SFMDrawLocalCommandExecutor.suggestCompletions(
                "rectangle ",
                10,
                "@rect[1,2]"
        );

        assertTrue(suggestions.suggestions().contains("list"));
    }

    @Test
    public void helpSuggestionsIncludeCommandTopics() {
        SFMDrawLocalCommandExecutor.CompletionSuggestions suggestions = SFMDrawLocalCommandExecutor.suggestCompletions(
                "help pla",
                8,
                "@rect[1,2]"
        );

        assertTrue(suggestions.suggestions().contains("player"));
    }

    @Test
    public void helpSuggestionsStayCommandOnly() {
        SFMDrawLocalCommandExecutor.CompletionSuggestions suggestions = SFMDrawLocalCommandExecutor.suggestCompletions(
                "help @",
                6,
                "@rect[1,2]"
        );

        assertFalse(suggestions.suggestions().contains("@rect[1,2]"));
        assertFalse(suggestions.suggestions().contains("@nearest[text]"));
    }

    @Test
    public void describeSuggestionsIncludeTargetSelectors() {
        SFMDrawLocalCommandExecutor.CompletionSuggestions suggestions = SFMDrawLocalCommandExecutor.suggestCompletions(
                "describe ",
                9,
                "@rect[1,2]"
        );

        assertTrue(suggestions.suggestions().contains("@rect[1,2]"));
        assertTrue(suggestions.suggestions().contains("@nearest[text]"));
    }

    @Test
    public void ollamaRunSuggestionsIncludeFallbackModels() {
        SFMDrawLocalCommandExecutor.CompletionSuggestions suggestions = SFMDrawLocalCommandExecutor.suggestCompletions(
                "ollama run ",
                11,
                "@rect[1,2]"
        );

        assertTrue(suggestions.suggestions().contains("llama3.2"));
    }

    @Test
    public void typoAliasIsNotHandledAsLocalCommand() {
        assertFalse(SFMDrawLocalCommandExecutor.canHandle("concantenate @rect[1,2]"));
    }

    @Test
    public void relativeSelectorIsHandledAsLocalCommand() {
        assertTrue(SFMDrawLocalCommandExecutor.canHandle("concatenate @relative[0,-10]"));
    }

    @Test
    public void nearestSelectorWithSpacesIsHandledAsLocalCommand() {
        assertTrue(SFMDrawLocalCommandExecutor.canHandle("describe @nearest[name: joe]"));
    }

    @Test
    public void contextMenuCommandIsHandledAsLocalCommand() {
        assertTrue(SFMDrawLocalCommandExecutor.canHandle("context_menu 17"));
    }

    @Test
    public void splitCommandIsHandledAsLocalCommand() {
        assertTrue(SFMDrawLocalCommandExecutor.canHandle("split 17 \\n"));
    }
}