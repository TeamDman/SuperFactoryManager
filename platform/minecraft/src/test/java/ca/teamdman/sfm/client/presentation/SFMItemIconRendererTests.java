package ca.teamdman.sfm.client.presentation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMItemIconRendererTests {
    @Test
    public void titleScreenCustomRenderersUseTheirDeclaredFallback() {
        assertTrue(SFMItemIconRenderer.requiresContextFallback(false, true));
        assertFalse(SFMItemIconRenderer.requiresContextFallback(false, false));
        assertFalse(SFMItemIconRenderer.requiresContextFallback(true, true));
    }
}
