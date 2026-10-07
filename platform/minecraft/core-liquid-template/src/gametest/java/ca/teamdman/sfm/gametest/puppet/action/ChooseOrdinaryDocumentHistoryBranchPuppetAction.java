package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.history.document.SFMDocumentHistorySession;
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.text_editor.SFMTextEditorPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Objects;

/** Selects the retained redo child whose descendants contain exact expected text. */
public record ChooseOrdinaryDocumentHistoryBranchPuppetAction(String expectedDescendantText)
        implements SFMPuppetAction {
    public ChooseOrdinaryDocumentHistoryBranchPuppetAction {
        Objects.requireNonNull(expectedDescendantText, "expectedDescendantText");
    }

    @Override
    public String description() {
        return "choose document-history branch leading to " + expectedDescendantText;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (!(Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen palette)) return false;
        SFMScreenMultiplexer workspace = AssertOrdinaryDocumentHistoryPuppetAction.workspace();
        SFMTextEditorPanel editor = AssertOrdinaryDocumentHistoryPuppetAction.editor(workspace);
        SFMDocumentHistorySession session = editor.documentHistorySession();
        String revision = AssertOrdinaryDocumentHistoryPuppetAction
                .candidateLeadingTo(session, expectedDescendantText)
                .orElseThrow(() -> new IllegalStateException(
                        "No redo candidate leads to expected text: " + expectedDescendantText));
        List<String> commands = palette.choiceCommandsForAutomation();
        String command = commands.stream()
                .filter(candidate -> candidate.endsWith(" " + revision))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Constrained redo choices omit revision " + revision + ": " + commands));
        palette.clickChoiceForAutomation(command);
        return true;
    }
}
