package ca.teamdman.sfm.client.screen.item_picker;

import ca.teamdman.sfm.client.presentation.SFMItemIcon;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMItemPickerModelTests {
    private static final ResourceLocation PAPER = id("minecraft:paper");
    private static final List<SFMItemPickerEntry> ITEMS = List.of(
            entry("sfm:disk", "SFM Program Disk"),
            entry("minecraft:chest", "Chest"),
            entry("minecraft:compass", "Compass"),
            entry("minecraft:book", "Book"),
            entry("minecraft:diamond", "Diamond"),
            entry("minecraft:paper", "Paper")
    );

    @Test
    public void filtersByAccessibleNameAndRegistryIdCaseInsensitively() {
        SFMItemPickerModel model = model("minecraft:chest");
        model.setQuery("PROGRAM");
        assertEquals(List.of(id("sfm:disk")), model.filtered().stream().map(SFMItemPickerEntry::itemId).toList());
        model.setQuery("MINECRAFT:COMP");
        assertEquals(List.of(id("minecraft:compass")), model.filtered().stream().map(SFMItemPickerEntry::itemId).toList());
    }

    @Test
    public void sfmlWildcardQueriesFilterThroughTheMatcherAst() {
        SFMItemPickerModel model = model("minecraft:chest");
        model.setQuery("minecraft:*o*");
        assertEquals(
                List.of(id("minecraft:compass"), id("minecraft:book"), id("minecraft:diamond")),
                model.filtered().stream().map(SFMItemPickerEntry::itemId).toList()
        );
        assertTrue(model.diagnostic().isEmpty());
    }

    @Test
    public void malformedSfmlQueryIsVisibleInsteadOfBecomingFuzzyText() {
        SFMItemPickerModel model = model("minecraft:chest");
        model.setQuery("minecraft:* WITH TAG #");
        assertTrue(model.filtered().isEmpty());
        assertTrue(model.diagnostic().startsWith("Invalid SFML item matcher:"));
        assertTrue(model.narration().contains("Invalid SFML item matcher"));
    }

    @Test
    public void tagQueryExplainsWhenTitleScreenRegistryTagsAreUnavailable() {
        SFMItemPickerModel model = model("minecraft:chest");
        model.setQuery("* WITH TAG #forge:chests");
        assertTrue(model.filtered().isEmpty());
        assertTrue(model.diagnostic().contains("world or server supplies registry tags"));
    }

    @Test
    public void emptySearchHasClearStateAndNoSelection() {
        SFMItemPickerModel model = model("minecraft:chest");
        model.setQuery("not-present");
        assertTrue(model.filtered().isEmpty());
        assertTrue(model.selection().isEmpty());
        assertTrue(model.interaction().contains("No registry items"));
    }

    @Test
    public void navigationUsesResponsiveColumnCountAndClamps() {
        SFMItemPickerModel model = model("sfm:disk");
        model.move(0, 1, 3);
        assertEquals(id("minecraft:book"), model.selection().orElseThrow().itemId());
        model.move(1, 0, 3);
        assertEquals(id("minecraft:diamond"), model.selection().orElseThrow().itemId());
        model.move(0, 10, 3);
        assertEquals(id("minecraft:paper"), model.selection().orElseThrow().itemId());
    }

    @Test
    public void unavailableCurrentIdReportsDiagnosticAndSelectsFallback() {
        SFMItemPickerModel model = model("missing_theme:gone");
        assertEquals(PAPER, model.selection().orElseThrow().itemId());
        assertTrue(model.diagnostic().contains("missing_theme:gone"));
        assertTrue(model.diagnostic().contains("minecraft:paper"));
        assertTrue(model.narration().contains("Unavailable registry id"));
    }

    @Test
    public void resetClearsSearchAndSelectsTypedFallback() {
        SFMItemPickerModel model = model("minecraft:chest");
        model.setQuery("disk");
        model.resetToFallback();
        assertEquals("", model.query());
        assertEquals(PAPER, model.selection().orElseThrow().itemId());
        assertEquals("Paper", model.selectedIcon().orElseThrow().accessibleLabel());
        assertEquals(PAPER, model.selectedIcon().orElseThrow().fallbackItem());
    }

    @Test
    public void selectedIconPreservesTextIdentityIndependentlyOfItemId() {
        SFMItemPickerModel model = model("sfm:disk");
        SFMItemIcon selected = model.selectedIcon().orElseThrow();
        assertEquals(id("sfm:disk"), selected.requestedItem());
        assertEquals("SFM Program Disk", selected.accessibleLabel());
        assertFalse(selected.accessibleLabel().equals(selected.requestedItem().toString()));
    }

    @Test
    public void panelConfirmAndCancelExposeTypedCallbacksWithoutScreenCoupling() {
        AtomicReference<SFMItemIcon> selected = new AtomicReference<>();
        AtomicBoolean cancelled = new AtomicBoolean();
        SFMItemPickerPanel confirmPanel = new SFMItemPickerPanel(
                ITEMS, new SFMItemIcon(id("minecraft:chest"), PAPER, "Chest"),
                selected::set, () -> cancelled.set(true)
        );
        assertTrue(confirmPanel.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0));
        assertEquals(id("minecraft:chest"), selected.get().requestedItem());
        assertFalse(cancelled.get());

        SFMItemPickerPanel cancelPanel = new SFMItemPickerPanel(
                ITEMS, new SFMItemIcon(id("minecraft:chest"), PAPER, "Chest"),
                ignored -> {}, () -> cancelled.set(true)
        );
        assertTrue(cancelPanel.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0, 0));
        assertTrue(cancelled.get());
    }

    @Test
    public void togglingDenseViewPreservesSearchAndSelectionForThePickerInstance() {
        SFMItemPickerModel model = model("minecraft:chest");
        model.setQuery("minecraft");
        model.move(1, 0, 2);
        ResourceLocation selected = model.selection().orElseThrow().itemId();
        model.toggleViewMode();
        assertEquals(SFMItemPickerModel.ViewMode.DENSE_ICONS, model.viewMode());
        assertEquals("minecraft", model.query());
        assertEquals(selected, model.selection().orElseThrow().itemId());
        assertTrue(model.narration().contains("Dense icons view"));
        model.toggleViewMode();
        assertEquals(SFMItemPickerModel.ViewMode.DETAILED, model.viewMode());
    }

    @Test
    public void controlGInputTogglesPanelModeButPlainGRemainsAvailableToSearchInput() {
        SFMItemPickerPanel panel = new SFMItemPickerPanel(
                ITEMS, new SFMItemIcon(id("minecraft:chest"), PAPER, "Chest"),
                ignored -> {}, () -> {}
        );
        assertFalse(panel.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_G, 0, 0));
        assertEquals(SFMItemPickerModel.ViewMode.DETAILED, panel.model().viewMode());
        assertTrue(panel.keyPressed(
                org.lwjgl.glfw.GLFW.GLFW_KEY_G, 0, org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL
        ));
        assertEquals(SFMItemPickerModel.ViewMode.DENSE_ICONS, panel.model().viewMode());
    }

    @Test public void panelSearchUsesSharedEditingAndGridKeepsDirectionalNavigation() {
        var panel = new SFMItemPickerPanel(ITEMS, new SFMItemIcon(id("minecraft:chest"), PAPER, "Chest"),
                ignored -> {}, () -> {});
        panel.setQueryForAutomation("minecraft:chest");
        panel.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_F, 0, org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL);
        panel.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE, 0, org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL);
        assertEquals("minecraft:", panel.model().query());
        panel.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_A, 0, org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL);
        for (char c : "paper".toCharArray()) panel.charTyped(c, 0);
        assertEquals("paper", panel.model().query());
        panel.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_HOME, 0, 0);
        panel.charTyped('x', 0);
        assertEquals("xpaper", panel.model().query());
        panel.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_Z, 0, org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL);
        assertEquals("paper", panel.model().query());
        panel.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN, 0, 0);
        panel.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT, 0, 0);
        assertEquals("paper", panel.model().query());
    }

    @Test
    public void navigationUsesTheDenseLayoutsIndependentColumnCount() {
        SFMItemPickerModel model = model("sfm:disk");
        SFMItemPickerLayout dense = SFMItemPickerLayout.calculate(
                0, 0, 300, 180, SFMItemPickerModel.ViewMode.DENSE_ICONS
        );
        model.move(0, 1, dense.columns());
        assertEquals(ITEMS.get(ITEMS.size() - 1).itemId(), model.selection().orElseThrow().itemId());
    }

    private static SFMItemPickerModel model(String current) {
        return new SFMItemPickerModel(ITEMS, new SFMItemIcon(id(current), PAPER, "Current icon"));
    }

    private static SFMItemPickerEntry entry(String id, String name) {
        return new SFMItemPickerEntry(id(id), name);
    }

    private static ResourceLocation id(String value) { return new ResourceLocation(value); }
}
