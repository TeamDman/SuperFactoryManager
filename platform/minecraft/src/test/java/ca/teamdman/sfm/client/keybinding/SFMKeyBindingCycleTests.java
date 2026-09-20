package ca.teamdman.sfm.client.keybinding;

import ca.teamdman.sfm.client.registry.SFMKeyboardUsageSituations;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SFMKeyBindingCycleTests {
    @Test
    void cyclesEnabledBindingsInStableIdOrderWithoutChangingCollectionOrder() {
        SFMKeyBinding second = binding("b", 66, true);
        SFMKeyBinding disabled = binding("a", 65, false);
        SFMKeyBinding first = binding("c", 67, true);
        List<SFMKeyBinding> input = List.of(first, disabled, second);

        assertEquals(second, SFMKeyBindingCycle.displayedBinding(input, 0));
        assertEquals(first, SFMKeyBindingCycle.displayedBinding(input, 20));
        assertEquals(second, SFMKeyBindingCycle.displayedBinding(input, 40));
        assertEquals(List.of(first, disabled, second), input);
    }

    private static SFMKeyBinding binding(String id, int keyCode, boolean enabled) {
        return new SFMKeyBinding(
                id,
                "sfm:help",
                "sfm action invoke sfm:help",
                SFMKeyboardUsageSituations.GLOBAL,
                SFMKeySequence.of(SFMKeyStroke.of(keyCode)),
                enabled
        );
    }
}
