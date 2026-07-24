package ca.teamdman.sfm.client.screen.workspace.diagnostic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SFMViewportDiagnosticsTests {
    @Test
    void immutableSnapshotPreservesRequestedAndEffectiveScaleSeparately() {
        var diagnostics = new SFMViewportDiagnostics(1280, 720, 2560, 1440, 427, 240, "Auto", 6.0, "wide");
        assertEquals("Auto", diagnostics.requestedGuiScale());
        assertEquals(6.0, diagnostics.effectiveGuiScale());
        assertEquals(2560, diagnostics.framebufferWidth());
    }

    @Test
    void invalidMeasurementsFailAtTheInjectionBoundary() {
        assertThrows(IllegalArgumentException.class,
                () -> new SFMViewportDiagnostics(-1, 720, 1280, 720, 640, 360, "1", 1.0, "wide"));
        assertThrows(IllegalArgumentException.class,
                () -> new SFMViewportDiagnostics(1280, 720, 1280, 720, 640, 360, "Auto", 0.0, "wide"));
    }
}
