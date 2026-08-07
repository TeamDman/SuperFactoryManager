package ca.teamdman.sfm.client.keybinding;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SFMKeyBindingListModelTests {
    private static final ResourceLocation ALPHA = new ResourceLocation("sfm", "alpha");
    private static final ResourceLocation BETA = new ResourceLocation("sfm", "beta");
    private static final ResourceLocation GLOBAL = new ResourceLocation("sfm", "global");
    private static final ResourceLocation TERMINAL = new ResourceLocation("sfm", "terminal");

    @Test
    void nameSortIsStableAndTogglesDirection() {
        SFMKeyBindingListModel model = model(Map.of(
                ALPHA, List.of(binding("a", GLOBAL)),
                BETA, List.of(binding("b", GLOBAL), binding("b2", GLOBAL))));
        assertEquals(List.of(ALPHA, BETA), model.visibleActions(id -> id.getPath(), id -> ""));
        model.toggleSort(SFMKeyBindingListModel.SortColumn.BINDING_COUNT);
        assertEquals(List.of(ALPHA, BETA), model.visibleActions(id -> id.getPath(), id -> ""));
        model.toggleSort(SFMKeyBindingListModel.SortColumn.BINDING_COUNT);
        assertEquals(List.of(BETA, ALPHA), model.visibleActions(id -> id.getPath(), id -> ""));
    }

    @Test
    void queryAndSituationFilterPreserveTheSameVisibleContract() {
        SFMKeyBindingListModel model = model(Map.of(
                ALPHA, List.of(binding("a", GLOBAL)),
                BETA, List.of(binding("b", TERMINAL))));
        model.setQuery("alp");
        assertEquals(List.of(ALPHA), model.visibleActions(id -> id.getPath(), id -> ""));
        model.setQuery("");
        model.setSituationFilter(TERMINAL);
        assertEquals(List.of(BETA), model.visibleActions(id -> id.getPath(), id -> ""));
    }

    private static SFMKeyBindingListModel model(Map<ResourceLocation, List<SFMKeyBinding>> bindings) {
        SFMKeyBindingListModel model = new SFMKeyBindingListModel(id -> bindings.getOrDefault(id, List.of()));
        model.setActions(bindings.keySet().stream().toList());
        return model;
    }

    private static SFMKeyBinding binding(String id, ResourceLocation situation) {
        return new SFMKeyBinding(id, "sfm:test", "sfm action invoke sfm:test", situation,
                SFMKeySequence.of(SFMKeyStroke.of(org.lwjgl.glfw.GLFW.GLFW_KEY_A)), true);
    }
}
