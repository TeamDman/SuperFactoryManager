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

    @Test
    public void titleScreenFallbackDistinguishesKnownLevelIndependentRenderers() {
        assertFalse(SFMItemIconRenderer.shouldUseTitleScreenFallback(false, true, true),
                "Minecraft's BlockEntityWithoutLevelRenderer should retain the preferred icon");
        assertTrue(SFMItemIconRenderer.shouldUseTitleScreenFallback(false, true, false),
                "an unproven custom renderer should use the declared safe fallback");
        assertFalse(SFMItemIconRenderer.shouldUseTitleScreenFallback(false, false, false),
                "ordinary baked models need no custom-render fallback");
        assertFalse(SFMItemIconRenderer.shouldUseTitleScreenFallback(true, true, false),
                "a loaded world satisfies the context expected by custom renderers");
    }
}
