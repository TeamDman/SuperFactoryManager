package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.screen.item_picker.SFMItemPickerPanel;
import ca.teamdman.sfm.client.screen.item_picker.SFMItemPickerScreen;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public record ConfigureItemPickerPuppetAction(View view) implements SFMPuppetAction {
    public enum View { GALLERY, SEARCH_DISK, KEYBOARD_SELECTION, UNAVAILABLE, RESET, MULTIPLEXED_SEARCH }

    @Override
    public String description() { return "configure item picker view " + view; }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        SFMItemPickerPanel panel = findPanel();
        if (panel == null) throw new IllegalStateException("Expected item picker panel");
        switch (view) {
            case GALLERY -> {
                panel.setQueryForAutomation("");
                panel.pressForAutomation(GLFW.GLFW_KEY_HOME, 0);
                assertSelected(panel, "sfm:disk");
                requireRegistryEntry(panel, "minecraft:chest");
                requireRegistryEntry(panel, "minecraft:diamond");
            }
            case SEARCH_DISK -> {
                panel.setQueryForAutomation("disk");
                assertSelected(panel, "sfm:disk");
            }
            case KEYBOARD_SELECTION -> {
                panel.setQueryForAutomation("");
                panel.pressForAutomation(GLFW.GLFW_KEY_HOME, 0);
                panel.pressForAutomation(GLFW.GLFW_KEY_RIGHT, 0);
                panel.pressForAutomation(GLFW.GLFW_KEY_RIGHT, 0);
                assertSelected(panel, "minecraft:compass");
                if (!panel.model().interaction().startsWith("Keyboard selected")) {
                    throw new IllegalStateException("Picker did not report keyboard selection");
                }
            }
            case UNAVAILABLE -> {
                panel.showUnavailableForAutomation(new ResourceLocation("missing_theme:unavailable_icon"));
                assertSelected(panel, "minecraft:paper");
                if (!panel.model().diagnostic().contains("missing_theme:unavailable_icon")) {
                    throw new IllegalStateException("Missing item diagnostic was not retained");
                }
            }
            case RESET -> {
                panel.setQueryForAutomation("disk");
                panel.pressForAutomation(GLFW.GLFW_KEY_R, GLFW.GLFW_MOD_CONTROL);
                assertSelected(panel, "minecraft:paper");
                if (!panel.model().query().isEmpty() || !panel.model().diagnostic().isEmpty()) {
                    throw new IllegalStateException("Reset did not restore a clean fallback selection");
                }
            }
            case MULTIPLEXED_SEARCH -> {
                panel.setQueryForAutomation("chest");
                assertSelected(panel, "minecraft:chest");
                if (!(Minecraft.getInstance().screen instanceof SFMScreenMultiplexer multiplexer)
                        || multiplexer.panels().size() != 2) {
                    throw new IllegalStateException("Picker is not hosted beside the prior screen panel");
                }
            }
        }
        return true;
    }

    static @Nullable SFMItemPickerPanel findPanel() {
        if (Minecraft.getInstance().screen instanceof SFMItemPickerScreen screen) return screen.panel();
        if (Minecraft.getInstance().screen instanceof SFMScreenMultiplexer multiplexer) {
            return multiplexer.panels().stream()
                    .filter(SFMItemPickerPanel.class::isInstance)
                    .map(SFMItemPickerPanel.class::cast)
                    .findFirst().orElse(null);
        }
        return null;
    }

    private static void assertSelected(SFMItemPickerPanel panel, String expected) {
        String actual = panel.model().selection().map(entry -> entry.itemId().toString()).orElse("<none>");
        if (!expected.equals(actual)) {
            throw new IllegalStateException("Expected picker selection " + expected + " but found " + actual);
        }
    }

    private static void requireRegistryEntry(SFMItemPickerPanel panel, String expected) {
        if (panel.model().entries().stream().noneMatch(entry -> expected.equals(entry.itemId().toString()))) {
            throw new IllegalStateException("Item registry picker omitted " + expected);
        }
    }
}
