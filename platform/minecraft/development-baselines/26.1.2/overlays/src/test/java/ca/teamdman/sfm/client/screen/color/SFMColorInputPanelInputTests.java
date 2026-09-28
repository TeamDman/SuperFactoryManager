package ca.teamdman.sfm.client.screen.color;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SFMColorInputPanelInputTests {
    @Test
    void puppetScaleConfirmHitTargetIsNotShadowedByCompactControls() {
        SFMArgbColor initial = new SFMArgbColor(0xFF3366CC);
        AtomicReference<SFMArgbColor> result = new AtomicReference<>();
        SFMColorInputPanel panel = new SFMColorInputPanel(initial, List.of(), result::set, () -> fail("cancelled"));
        panel.resized(null, new SFMScreenPanelBounds(0, 0, 400, 240));

        SFMColorInputPanelLayout.Rect confirm = panel.layout().confirm();
        panel.mouseClicked(confirm.x() + confirm.width() / 2D, confirm.y() + confirm.height() / 2D,
                GLFW.GLFW_MOUSE_BUTTON_LEFT);

        assertEquals(initial, result.get());
        assertEquals(initial, panel.confirmedResult());
    }

    @Test
    void mouseFieldValueRecentResetAndTypedConfirmUseOneModel() {
        SFMArgbColor initial = new SFMArgbColor(0xFF3366CC);
        SFMArgbColor recent = new SFMArgbColor(0x8844CC22);
        AtomicReference<SFMArgbColor> result = new AtomicReference<>();
        SFMColorInputPanel panel = new SFMColorInputPanel(initial, List.of(recent), result::set, () -> fail("cancelled"));
        SFMColorInputPanelLayout layout = panel.layout();

        panel.mouseClicked(layout.hueSaturation().x() + layout.hueSaturation().width() * 0.5D,
                layout.hueSaturation().y() + layout.hueSaturation().height() * 0.5D, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        assertEquals(0.5D, panel.model().current().toHsv().hue(), 0.02D);
        panel.mouseClicked(layout.valueSlider().x() + layout.valueSlider().width() * 0.75D,
                layout.valueSlider().y() + 2, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        assertEquals(0.75D, panel.model().current().toHsv().value(), 0.02D);
        panel.mouseClicked(layout.recents().x() + 2, layout.recents().y() + 2, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        assertEquals(recent, panel.model().current());
        panel.mouseClicked(layout.reset().x() + 2, layout.reset().y() + 2, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        assertEquals(initial, panel.model().current());
        panel.mouseClicked(layout.confirm().x() + 2, layout.confirm().y() + 2, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        assertEquals(initial, result.get());
        assertEquals(initial, panel.confirmedResult());
    }

    @Test
    void tabAndArrowKeyboardNavigationAdjustValueAndCloseCancelsOnce() {
        AtomicBoolean cancelled = new AtomicBoolean();
        SFMColorInputPanel panel = new SFMColorInputPanel(new SFMArgbColor(0xFF808080), List.of(),
                ignored -> fail("confirmed"), () -> assertFalse(cancelled.getAndSet(true)));
        double before = panel.model().current().toHsv().value();
        panel.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0); // FIELD -> VALUE
        panel.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0);
        assertTrue(panel.model().current().toHsv().value() > before);
        panel.closed();
        panel.closed();
        assertTrue(cancelled.get());
    }
}
