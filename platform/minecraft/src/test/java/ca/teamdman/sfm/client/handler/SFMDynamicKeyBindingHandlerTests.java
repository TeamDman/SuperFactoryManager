package ca.teamdman.sfm.client.handler;

import ca.teamdman.sfm.client.keybinding.SFMKeyboardUsageContextProvider;
import ca.teamdman.sfm.client.keybinding.SFMKeyboardUsageContextSnapshot;
import ca.teamdman.sfm.client.registry.SFMKeyboardUsageSituations;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

class SFMDynamicKeyBindingHandlerTests {
    @Test
    void acceptsKeyboardContextFromAnyCapableScreen() {
        SFMKeyboardUsageContextSnapshot expected = SFMKeyboardUsageContextSnapshot.testing(
                SFMKeyboardUsageSituations.GLOBAL,
                SFMKeyboardUsageSituations.DEFAULT,
                SFMKeyboardUsageSituations.TEXT_EDITOR,
                SFMKeyboardUsageSituations.TEMPORAL_DOCUMENT
        );

        assertSame(expected, SFMDynamicKeyBindingHandler.contextFor(new ContextScreen(expected)));
    }

    private static final class ContextScreen extends Screen implements SFMKeyboardUsageContextProvider {
        private final SFMKeyboardUsageContextSnapshot snapshot;

        private ContextScreen(SFMKeyboardUsageContextSnapshot snapshot) {
            super(Component.literal("Context screen"));
            this.snapshot = snapshot;
        }

        @Override
        public SFMKeyboardUsageContextSnapshot keyboardUsageContextSnapshot() {
            return snapshot;
        }
    }
}
