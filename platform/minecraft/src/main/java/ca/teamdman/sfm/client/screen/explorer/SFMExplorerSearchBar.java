package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.action.SFMExplorerSearchAction;
import ca.teamdman.sfm.client.search.SFMTextMatchOptions;

import java.util.*;

/** Shared bounded hit/render geometry; narrow bars retain an overflow menu, not invisible toggles. */
final class SFMExplorerSearchBar {
    record Button(String option, String label, SFMExplorerPanelViewport.Rect bounds) { }
    record Layout(SFMExplorerPanelViewport.Rect input, List<Button> buttons) { }
    static Layout layout(SFMExplorerPanelViewport.Rect row) {
        int count = row.width() >= 210 ? 7 : row.width() >= 80 ? 1 : 0;
        int inputWidth = Math.max(0, row.width() - count * 16);
        var buttons = new ArrayList<Button>();
        for (int i = 0; i < count; i++) {
            String option = count == 1 || i == 6 ? "menu" : SFMExplorerSearchAction.OPTIONS.get(i);
            String label = switch (option) {
                case "case" -> "Aa"; case "whole-word" -> "W"; case "regex" -> ".*";
                case "fuzzy" -> "~"; case "dot-all" -> "S"; case "highlight" -> "H"; default -> "…";
            };
            buttons.add(new Button(option, label, new SFMExplorerPanelViewport.Rect(
                    row.x() + inputWidth + i * 16, row.y(), 16, row.height())));
        }
        return new Layout(new SFMExplorerPanelViewport.Rect(row.x(), row.y(), inputWidth, row.height()), List.copyOf(buttons));
    }
    static boolean selected(String option, SFMTextMatchOptions options, boolean highlight) {
        return switch (option) {
            case "case" -> options.matchCase(); case "whole-word" -> options.wholeWord();
            case "regex" -> options.mode() == SFMTextMatchOptions.Mode.REGEX;
            case "fuzzy" -> options.mode() == SFMTextMatchOptions.Mode.FUZZY;
            case "dot-all" -> options.dotAll(); case "highlight" -> highlight; default -> false;
        };
    }
}
