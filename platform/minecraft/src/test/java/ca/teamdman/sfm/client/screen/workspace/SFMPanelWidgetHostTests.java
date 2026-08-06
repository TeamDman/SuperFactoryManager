package ca.teamdman.sfm.client.screen.workspace;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Optional;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class SFMPanelWidgetHostTests {
    private static final ResourceLocation DEFAULT = new ResourceLocation("sfm", "default");

    @Test
    void traversesForwardReverseAndSkipsHiddenOrDisabledChildren() {
        SFMPanelWidgetHost host = new SFMPanelWidgetHost();
        FakeWidget first = widget("first");
        FakeWidget hidden = widget("hidden");
        hidden.visible = false;
        FakeWidget disabled = widget("disabled");
        disabled.enabled = false;
        FakeWidget last = widget("last");
        host.setChildren(List.of(first, hidden, disabled, last));

        assertTrue(host.changeFocus(true));
        assertEquals(id("first"), host.focusedElementId().orElseThrow());
        assertTrue(host.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0));
        assertEquals(id("last"), host.focusedElementId().orElseThrow());
        assertTrue(host.keyPressed(GLFW.GLFW_KEY_TAB, 0, GLFW.GLFW_MOD_SHIFT));
        assertEquals(id("first"), host.focusedElementId().orElseThrow());
    }

    @Test
    void clickFocusAndEnterSpaceUseTheSameActivation() {
        SFMPanelWidgetHost host = new SFMPanelWidgetHost();
        FakeWidget first = widget("first");
        first.setPanelBounds(new SFMScreenPanelBounds(4, 6, 20, 10));
        FakeWidget second = widget("second");
        second.setPanelBounds(new SFMScreenPanelBounds(30, 6, 20, 10));
        host.setChildren(List.of(first, second));

        assertTrue(host.mouseClicked(35, 10, GLFW.GLFW_MOUSE_BUTTON_LEFT));
        assertEquals(id("second"), host.focusedElementId().orElseThrow());
        assertEquals(1, second.activations);
        assertTrue(host.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
        assertTrue(host.keyPressed(GLFW.GLFW_KEY_SPACE, 0, 0));
        assertEquals(3, second.activations);
    }

    @Test
    void mapsWorkspaceCoordinatesThroughPanelScale() {
        assertArrayEquals(
                new int[]{25, 12},
                SFMPanelWidgetHost.panelCoordinates(
                        new SFMScreenPanelBounds(100, 40, 200, 100),
                        2.0D,
                        150.9D,
                        65.9D
                )
        );
    }

    @Test
    void removingResizingAndRestoringAChildPreservesItsIdentityAndNarration() {
        SFMPanelWidgetHost host = new SFMPanelWidgetHost();
        FakeWidget first = widget("first");
        FakeWidget second = widget("second");
        host.setChildren(List.of(first, second));
        assertTrue(host.focus(id("second")));
        second.setPanelBounds(new SFMScreenPanelBounds(7, 9, 30, 11));
        assertEquals(new SFMScreenPanelBounds(7, 9, 30, 11), second.bounds);
        assertEquals("Narration second", host.focusedNarration().orElseThrow().getString());

        host.setChildren(List.of(first));
        assertTrue(host.focusedChild().isEmpty());
        FakeWidget restored = widget("second");
        host.setChildren(List.of(first, restored));

        assertSame(restored, host.focusedChild().orElseThrow());
        assertTrue(restored.focused);
    }

    @Test
    void reconstructingTheSameStableFocusedElementDoesNotResetContextRevision() {
        SFMPanelWidgetHost host = new SFMPanelWidgetHost();
        FakeWidget first = widget("stable");
        host.setChildren(List.of(first));
        assertTrue(host.focus(first.elementId()));
        long focusedRevision = host.focusRevision();

        host.setChildren(List.of(widget("stable")));

        assertEquals(focusedRevision, host.focusRevision());
        assertEquals(id("stable"), host.focusedElementId().orElseThrow());
    }

    @Test
    void focusedChildMayConsumeTabBeforeHostTraversal() {
        SFMPanelWidgetHost host = new SFMPanelWidgetHost();
        FakeWidget terminal = widget("terminal");
        terminal.tabsToConsume = 2;
        FakeWidget presentation = widget("presentation");
        host.setChildren(List.of(terminal, presentation));
        host.focus(terminal.elementId());

        assertTrue(host.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0));
        assertEquals(terminal.elementId(), host.focusedElementId().orElseThrow());
        assertTrue(host.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0));
        assertEquals(terminal.elementId(), host.focusedElementId().orElseThrow());
        assertTrue(host.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0));
        assertEquals(presentation.elementId(), host.focusedElementId().orElseThrow());
    }

    @Test
    void inactiveHostRemembersFocusWithoutShowingVanillaFocus() {
        SFMPanelWidgetHost host = new SFMPanelWidgetHost();
        FakeWidget child = widget("remembered");
        host.setChildren(List.of(child));
        assertTrue(host.focus(child.elementId()));
        assertTrue(child.focused);

        host.setActive(false);
        assertEquals(child.elementId(), host.focusedElementId().orElseThrow());
        assertFalse(child.focused);

        host.setActive(true);
        assertTrue(child.focused);
    }

    @Test
    void unhandledChildScrollFallsBackToThePanel() {
        SFMPanelWidgetHost host = new SFMPanelWidgetHost();
        FakeWidget child = widget("scroll");
        child.setPanelBounds(new SFMScreenPanelBounds(0, 0, 20, 20));
        host.setChildren(List.of(child));

        assertFalse(host.mouseScrolled(5, 5, 1));
        child.scrollHandled = true;
        assertTrue(host.mouseScrolled(5, 5, 1));
    }

    @Test
    void emptyClickClearsFocusAndPublishesTheExclusiveFocusState() {
        SFMPanelWidgetHost host = new SFMPanelWidgetHost();
        FakeWidget child = widget("focused");
        child.setPanelBounds(new SFMScreenPanelBounds(4, 6, 20, 10));
        host.setChildren(List.of(child));
        List<SFMPanelWidgetHost.FocusState> states = new ArrayList<>();
        host.setFocusStateListener(states::add);
        assertTrue(host.focus(child.elementId()));

        assertFalse(host.mouseClicked(100, 100, GLFW.GLFW_MOUSE_BUTTON_LEFT));

        assertTrue(host.focusedChild().isEmpty());
        assertFalse(child.focused);
        assertNull(states.get(states.size() - 1).focusedElementId());
    }

    @Test
    void deactivatingHostPublishesInactiveStateWithoutForgettingLogicalFocus() {
        SFMPanelWidgetHost host = new SFMPanelWidgetHost();
        FakeWidget child = widget("remembered-inactive");
        host.setChildren(List.of(child));
        assertTrue(host.focus(child.elementId()));
        List<SFMPanelWidgetHost.FocusState> states = new ArrayList<>();
        host.setFocusStateListener(states::add);

        host.setActive(false);

        assertEquals(child.elementId(), host.focusedElementId().orElseThrow());
        assertFalse(states.get(states.size() - 1).active());
        assertEquals(child.elementId(), states.get(states.size() - 1).focusedElementId());
    }

    private static FakeWidget widget(String path) {
        return new FakeWidget(id(path));
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("sfm", "test/" + path);
    }

    private static final class FakeWidget implements SFMPanelWidget {
        private final ResourceLocation id;
        private boolean visible = true;
        private boolean enabled = true;
        private boolean focused;
        private int activations;
        private int tabsToConsume;
        private boolean scrollHandled;
        private SFMScreenPanelBounds bounds = new SFMScreenPanelBounds(0, 0, 1, 1);

        private FakeWidget(ResourceLocation id) {
            this.id = id;
        }

        @Override public ResourceLocation elementId() { return id; }
        @Override public ResourceLocation keyboardUsageSituationId() { return DEFAULT; }
        @Override public Component narration() { return Component.literal("Narration " + id.getPath().substring(5)); }
        @Override public Optional<String> actionDraft() { return Optional.of("sfm action invoke " + id); }
        @Override public void setPanelBounds(SFMScreenPanelBounds bounds) { this.bounds = bounds; }
        @Override public boolean isPanelVisible() { return visible; }
        @Override public boolean isPanelEnabled() { return enabled; }
        @Override public boolean isPanelFocused() { return focused; }
        @Override public void setPanelFocused(boolean focused) { this.focused = focused; }
        @Override public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) { }
        @Override public NarrationPriority narrationPriority() { return focused ? NarrationPriority.FOCUSED : NarrationPriority.NONE; }
        @Override public void updateNarration(NarrationElementOutput output) { }
        @Override public boolean isActive() { return visible && enabled; }
        @Override public boolean isMouseOver(double mouseX, double mouseY) { return bounds.contains(mouseX, mouseY); }
        @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!isMouseOver(mouseX, mouseY) || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
            activations++;
            return true;
        }
        @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            return scrollHandled;
        }
        @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_TAB && tabsToConsume > 0) {
                tabsToConsume--;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_SPACE) {
                activations++;
                return true;
            }
            return false;
        }
    }
}
