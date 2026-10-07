package ca.teamdman.sfm.client.action;

{% if features.command_palette or features.workspace_panel_actions or features.workspace_toast_actions %}
{% if features.client_control_cli or features.explorer_compaction or features.explorer_navigation or features.explorer_search or features.file_explorer or features.java_symbols or features.registry_explorer or features.release_review or features.workspace_counterfactuals %}
import ca.teamdman.sfm.client.explorer.SFMExplorerId;
{% endif %}
{% endif %}
{% if features.command_palette or features.workspace_panel_actions or features.workspace_toast_actions %}
{% if features.client_control_cli or features.explorer_compaction or features.explorer_navigation or features.explorer_search or features.file_explorer or features.java_symbols or features.registry_explorer or features.release_review or features.workspace_counterfactuals %}
import ca.teamdman.sfm.client.explorer.SFMPath;
{% endif %}
{% endif %}
{% if features.command_palette or features.workspace_panel_actions or features.workspace_toast_actions %}
{% if features.client_control_cli or features.explorer_compaction or features.explorer_navigation or features.explorer_search or features.file_explorer or features.java_symbols or features.registry_explorer or features.release_review or features.workspace_counterfactuals %}
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection;
{% endif %}
{% endif %}
{% if features.command_palette or features.workspace_panel_actions or features.workspace_toast_actions %}
{% if features.client_control_cli or features.explorer_compaction or features.explorer_navigation or features.explorer_search or features.file_explorer or features.java_symbols or features.registry_explorer or features.release_review or features.workspace_counterfactuals %}
import ca.teamdman.sfm.client.screen.SFMActionChoice;
{% endif %}
{% endif %}
{% if features.command_palette or features.workspace_panel_actions or features.workspace_toast_actions %}
{% if features.client_control_cli or features.explorer_compaction or features.explorer_navigation or features.explorer_search or features.file_explorer or features.java_symbols or features.registry_explorer or features.release_review or features.workspace_counterfactuals %}
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerRowInspection;
{% endif %}
{% endif %}
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMExplorerRowCopyDetailsActionTests {
{% if features.command_palette or features.workspace_panel_actions or features.workspace_toast_actions %}
{% if features.client_control_cli or features.explorer_compaction or features.explorer_navigation or features.explorer_search or features.file_explorer or features.java_symbols or features.registry_explorer or features.release_review or features.workspace_counterfactuals %}
    @Test
    void capturedContextChoiceCopiesTheImmutableDeterministicPayload() throws Exception {
        SFMExplorerRowInspection inspection = inspection();
        SFMActionChoice choice = SFMExplorerRowCopyDetailsAction.captureChoice(inspection);
        long captureId = Long.parseLong(choice.command().substring(choice.command().lastIndexOf(' ') + 1));
        ArrayList<String> clipboard = new ArrayList<>();
        ArrayList<Component> feedback = new ArrayList<>();

        int result = new SFMExplorerRowCopyDetailsAction(clipboard::add)
                .copyCapture(captureId, feedback::add);

        assertEquals(1, result);
        assertEquals(List.of(inspection.detailsPayload()), clipboard);
        assertEquals(List.of("Copied Explorer row details"), feedback.stream().map(Component::getString).toList());
        assertEquals("sfm:explorer/row/details/copy", choice.actionId().toString());
        assertEquals("Copy row details", choice.displayText());
    }

{% endif %}
{% endif %}
    @Test
    void expiredOrUnknownCaptureFailsWithoutWritingClipboard() {
        ArrayList<String> clipboard = new ArrayList<>();
        SFMExplorerRowCopyDetailsAction action = new SFMExplorerRowCopyDetailsAction(clipboard::add);

        CommandSyntaxException failure = assertThrows(CommandSyntaxException.class, () ->
                action.copyCapture(Long.MAX_VALUE, ignored -> {}));

        assertTrue(failure.getMessage().contains("no longer available"));
        assertTrue(clipboard.isEmpty());
    }

{% if features.command_palette or features.workspace_panel_actions or features.workspace_toast_actions %}
{% if features.client_control_cli or features.explorer_compaction or features.explorer_navigation or features.explorer_search or features.file_explorer or features.java_symbols or features.registry_explorer or features.release_review or features.workspace_counterfactuals %}
    private static SFMExplorerRowInspection inspection() {
        return new SFMExplorerRowInspection(
                new SFMExplorerId("explorer-7"),
                SFMPath.parse("file:///D:/project/SFM.java"),
                "SFM.java",
                SFMExplorerProjection.RowKind.ENTRY,
                SFMExplorerProjection.FilterRole.NONE,
                1,
                false,
                false,
                false,
                true,
                true,
                "body",
                SFMExplorerProjection.Settings.defaults(),
                3,
                4,
                0,
                Optional.empty(),
                List.of(),
                List.of()
        );
    }
{% endif %}
{% endif %}
}
