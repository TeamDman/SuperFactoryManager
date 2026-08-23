package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.action.SFMClientActionContext;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMCommandPaletteScreenTests {
    @Test
    void cancelUsesTheCanonicalPaletteCloseAction() throws Exception {
        Object origin = new Object();
        SFMClientActionContext context = SFMClientActionContext.create(origin, () -> true);
        Consumer<Component> feedback = ignored -> { };
        AtomicReference<String> command = new AtomicReference<>();
        AtomicReference<SFMClientActionContext> capturedContext = new AtomicReference<>();
        AtomicReference<Consumer<Component>> capturedFeedback = new AtomicReference<>();
        AtomicInteger invocations = new AtomicInteger();

        int result = SFMCommandPaletteScreen.invokeCancelAction(
                (actualCommand, actualContext, actualFeedback) -> {
                    invocations.incrementAndGet();
                    command.set(actualCommand);
                    capturedContext.set(actualContext);
                    capturedFeedback.set(actualFeedback);
                    return 1;
                },
                context,
                feedback
        );

        assertEquals(1, result);
        assertEquals(1, invocations.get());
        assertEquals("sfm action invoke sfm:palette/close", command.get());
        assertSame(context, capturedContext.get());
        assertSame(feedback, capturedFeedback.get());
    }

    @Test
    void cleanupAndCloseGatesAreExactlyOnce() {
        SFMCommandPaletteScreen.CloseLifecycle lifecycle =
                new SFMCommandPaletteScreen.CloseLifecycle();
        AtomicInteger cleanups = new AtomicInteger();

        assertTrue(lifecycle.beginClose());
        assertFalse(lifecycle.beginClose());
        assertTrue(lifecycle.runCleanupOnce(cleanups::incrementAndGet));
        assertFalse(lifecycle.runCleanupOnce(cleanups::incrementAndGet));
        assertEquals(1, cleanups.get());
    }

    @Test
    void cancelControlUsesVanillaFocusNarrationAndActivationContracts() {
        AtomicInteger activations = new AtomicInteger();
        Button cancel = SFMCommandPaletteScreen.createCancelButton(
                new SFMCommandPaletteScreen.ControlBounds(10, 20, 80, 20),
                activations::incrementAndGet
        );

        assertEquals("Cancel", cancel.getMessage().getString());
        assertTrue(cancel.changeFocus(true));
        assertTrue(cancel.isFocused());
        assertEquals(NarratableEntry.NarrationPriority.FOCUSED, cancel.narrationPriority());
        cancel.onPress();
        assertEquals(1, activations.get());
    }

    @Test
    void inputExecuteAndCancelRemainVisibleWithoutOverlappingAtSupportedWidths() {
        for (int viewportWidth : new int[]{320, 384, 480, 640, 854, 1280}) {
            int panelTop = 12;
            int panelWidth = SFMCommandPaletteScreen.panelWidth(viewportWidth);
            int panelLeft = (viewportWidth - panelWidth) / 2;
            int panelRight = panelLeft + panelWidth;
            SFMCommandPaletteScreen.ControlsLayout controls =
                    SFMCommandPaletteScreen.controlsLayout(viewportWidth, panelTop);

            assertTrue(controls.input().x() >= panelLeft);
            assertTrue(controls.input().right() <= panelRight);
            assertTrue(controls.execute().x() >= panelLeft);
            assertTrue(controls.execute().right() <= panelRight);
            assertTrue(controls.cancel().x() >= panelLeft);
            assertTrue(controls.cancel().right() <= panelRight);
            assertTrue(controls.execute().right() < controls.cancel().x());
            assertEquals(controls.execute().y(), controls.cancel().y());
            assertTrue(controls.input().y() + controls.input().height()
                    <= controls.execute().y());
            assertTrue(controls.execute().width() > 0);
            assertTrue(controls.cancel().width() > 0);
        }
    }

    @Test
    void homeAndEndMoveTheFocusedInputCaretWithZeroOrManySuggestions() {
        EditBox input = input("alpha beta", 5, 2);

        assertTrue(SFMCommandPaletteScreen.handleCommandInputHomeEnd(
                input, GLFW.GLFW_KEY_HOME, 0, 0));
        assertEquals(0, input.getCursorPosition());
        assertEquals("", input.getHighlighted());

        input.setCursorPosition(3);
        input.setHighlightPos(1);
        assertTrue(SFMCommandPaletteScreen.handleCommandInputHomeEnd(
                input, GLFW.GLFW_KEY_END, 0, 37));
        assertEquals(input.getValue().length(), input.getCursorPosition());
        assertEquals("", input.getHighlighted());
    }

    @Test
    void shiftHomeAndEndExtendTheFocusedInputSelection() {
        EditBox input = input("alpha beta", 5, 2);

        assertTrue(SFMCommandPaletteScreen.handleCommandInputHomeEnd(
                input, GLFW.GLFW_KEY_HOME, GLFW.GLFW_MOD_SHIFT, 0));
        assertEquals(0, input.getCursorPosition());
        assertEquals("al", input.getHighlighted());

        input.setCursorPosition(5);
        input.setHighlightPos(2);
        assertTrue(SFMCommandPaletteScreen.handleCommandInputHomeEnd(
                input, GLFW.GLFW_KEY_END, GLFW.GLFW_MOD_SHIFT, 37));
        assertEquals(input.getValue().length(), input.getCursorPosition());
        assertEquals("pha beta", input.getHighlighted());
    }

    @Test
    void modifiedHomeAndEndRemainAvailableForActionBindings() {
        EditBox input = input("alpha beta", 5, 2);

        assertFalse(SFMCommandPaletteScreen.handleCommandInputHomeEnd(
                input, GLFW.GLFW_KEY_HOME, GLFW.GLFW_MOD_CONTROL, 12));
        assertEquals(5, input.getCursorPosition());
        assertEquals("pha", input.getHighlighted());
    }

    private static EditBox input(String value, int cursor, int anchor) {
        EditBox input = new EditBox(null, 0, 0, 200, 20, Component.empty());
        input.setMaxLength(2048);
        input.setValue(value);
        input.setCursorPosition(cursor);
        input.setHighlightPos(anchor);
        input.setFocused(true);
        return input;
    }
}
