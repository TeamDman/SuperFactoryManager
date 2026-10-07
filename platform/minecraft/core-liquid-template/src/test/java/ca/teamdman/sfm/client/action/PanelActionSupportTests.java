package ca.teamdman.sfm.client.action;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PanelActionSupportTests {
    @Test
    void currentPaletteStaysOpenForDirectAndExactSessionTargets() {
        assertTrue(PanelActionSupport.shouldKeepPaletteOpen(
                true, Optional.of("palette-current"), "legacy-direct"));
        assertTrue(PanelActionSupport.shouldKeepPaletteOpen(
                false, Optional.of("palette-current"), "palette-current"));
    }

    @Test
    void constrainedChildPaletteClosesAfterMutatingItsParentSession() {
        assertFalse(PanelActionSupport.shouldKeepPaletteOpen(
                false, Optional.of("palette-child"), "palette-parent"));
    }

    @Test
    void paletteClosesAfterMutatingAWorkspaceDocument() {
        assertFalse(PanelActionSupport.shouldKeepPaletteOpen(
                false, Optional.of("palette-current"), "workspace-document"));
    }
}
