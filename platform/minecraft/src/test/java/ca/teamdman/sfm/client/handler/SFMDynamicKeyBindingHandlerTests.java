package ca.teamdman.sfm.client.handler;

import ca.teamdman.sfm.client.keybinding.SFMKeyboardUsageContextProvider;
import ca.teamdman.sfm.client.keybinding.SFMKeyboardUsageContextSnapshot;
import ca.teamdman.sfm.client.registry.SFMKeyboardUsageSituations;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SFMDynamicKeyBindingHandlerTests {
    @Test
    void acceptsKeyboardContextFromAnyCapableScreen() throws Exception {
        SFMKeyboardUsageContextSnapshot expected = SFMKeyboardUsageContextSnapshot.testing(
                SFMKeyboardUsageSituations.GLOBAL,
                SFMKeyboardUsageSituations.DEFAULT,
                SFMKeyboardUsageSituations.TEXT_EDITOR,
                SFMKeyboardUsageSituations.TEMPORAL_DOCUMENT
        );

        ContextScreen screen = allocateWithoutConstructor(ContextScreen.class);
        Field snapshot = ContextScreen.class.getDeclaredField("snapshot");
        snapshot.setAccessible(true);
        snapshot.set(screen, expected);

        assertSame(expected, SFMDynamicKeyBindingHandler.contextFor(screen));
    }

    @Test
    void rawKeyJournalEncodingRetainsThePhysicalScanCode() {
        assertEquals("key=65,scan=30", SFMDynamicKeyBindingHandler.journalKeyCode(65, 30));
    }

    private static <T> T allocateWithoutConstructor(Class<T> type) throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        return type.cast(((Unsafe) unsafeField.get(null)).allocateInstance(type));
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
