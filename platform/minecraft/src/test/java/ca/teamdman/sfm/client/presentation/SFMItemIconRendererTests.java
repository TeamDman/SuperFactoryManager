package ca.teamdman.sfm.client.presentation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMItemIconRendererTests {
    @Test
    public void onlyTitleScreenRenderingPaysForCustomModelInspection() {
        assertTrue(SFMItemIconRenderer.shouldInspectCustomRenderer(false));
        assertFalse(SFMItemIconRenderer.shouldInspectCustomRenderer(true));
    }
}
