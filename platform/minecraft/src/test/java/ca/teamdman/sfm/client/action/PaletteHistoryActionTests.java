package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.command.SFMCommandHistoryService;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaletteHistoryActionTests {
    @Test
    void historyOpenHasOptionalEditorIdForEveryPlacement() {
        for (PaletteHistoryOpenAction.Direction direction : PaletteHistoryOpenAction.Direction.values()) {
            ResourceLocation id = new ResourceLocation("sfm", "palette/history/open/" + direction.name().toLowerCase());
            if (direction == PaletteHistoryOpenAction.Direction.CENTER) {
                id = new ResourceLocation("sfm", "palette/history/open");
            }
            var tree = SFMClientActionDispatcherCompiler.compileCommandTree(List.of(
                    Map.entry(id, new PaletteHistoryOpenAction(direction))));
            var source = new SFMClientActionSource(
                    SFMClientActionContext.create(new Object(), new AtomicBoolean(true)::get));

            String base = "sfm action invoke " + id;
            assertTrue(SFMClientActionExecutor.isExecutable(tree.parse(base, source)));
            assertTrue(SFMClientActionExecutor.isExecutable(tree.parse(base + " sfm:text_editor_v3", source)));
        }
    }

    @Test
    void historyMaintenanceCommandsAreNeverRecorded() {
        assertFalse(SFMCommandHistoryService.isRecordable(
                "sfm action invoke sfm:palette/history/open"));
        assertFalse(SFMCommandHistoryService.isRecordable(
                "sfm action invoke sfm:palette/history/clear"));
        assertTrue(SFMCommandHistoryService.isRecordable(
                "sfm action invoke sfm:echo hello"));
    }
}
