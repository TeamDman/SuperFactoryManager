package ca.teamdman.sfm.client.tooltip;

import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static ca.teamdman.sfm.client.tooltip.SFMTooltipModeService.Mode.*;
import static org.junit.jupiter.api.Assertions.*;

class SFMTooltipModeServiceTests {
    @Test void automaticModeIsLazyAndFollowsTheCurrentConfiguredInput() {
        AtomicBoolean pressed = new AtomicBoolean();
        AtomicInteger polls = new AtomicInteger();
        var service = new SFMTooltipModeService(() -> { polls.incrementAndGet(); return pressed.get(); });
        assertEquals(AUTO, service.mode());
        assertEquals(0, polls.get());
        assertFalse(service.isExpanded());
        pressed.set(true);
        assertTrue(service.isExpanded());
        pressed.set(false);
        assertFalse(service.isExpanded());
        assertEquals(3, polls.get());
    }

    @Test void everyModeAndKeyStateHasTheExpectedMeaningAndPollingCost() {
        for (var mode : SFMTooltipModeService.Mode.values()) {
            for (boolean pressed : new boolean[]{false, true}) {
                AtomicInteger polls = new AtomicInteger();
                var service = new SFMTooltipModeService(() -> { polls.incrementAndGet(); return pressed; });
                service.setMode(mode);
                assertEquals(0, polls.get(), "Selecting a mode must not inspect input");
                boolean expected = mode == EXPANDED || mode == AUTO && pressed;
                assertEquals(expected, service.isExpanded(), mode + "/" + pressed);
                assertEquals(mode == AUTO ? 1 : 0, polls.get(), mode + " polled unexpectedly");
            }
        }
    }

    @Test void explicitModesNeverPollNativeInputEvenWhenRepeated() {
        var service = new SFMTooltipModeService(() -> { throw new AssertionError("Native input reached"); });
        for (int i = 0; i < 4; i++) {
            service.setMode(EXPANDED);
            assertEquals(EXPANDED, service.mode());
            assertTrue(service.isExpanded());
        }
        for (int i = 0; i < 4; i++) {
            service.setMode(COMPACT);
            assertEquals(COMPACT, service.mode());
            assertFalse(service.isExpanded());
        }
        service.setMode(AUTO);
        assertEquals(AUTO, service.mode());
    }

    @Test void resetRestoresInputInsteadOfLatchingItsPreviousValue() {
        AtomicBoolean pressed = new AtomicBoolean(true);
        AtomicInteger polls = new AtomicInteger();
        var service = new SFMTooltipModeService(() -> { polls.incrementAndGet(); return pressed.get(); });
        service.setMode(COMPACT);
        assertFalse(service.isExpanded());
        service.setMode(AUTO);
        assertEquals(0, polls.get());
        assertTrue(service.isExpanded());
        service.setMode(EXPANDED);
        pressed.set(false);
        service.setMode(AUTO);
        assertFalse(service.isExpanded());
        assertEquals(2, polls.get());
    }

    @Test void nullInputIsRejectedWithoutChangingAnExistingMode() {
        assertThrows(NullPointerException.class, () -> new SFMTooltipModeService(null));
        var service = new SFMTooltipModeService(() -> false);
        service.setMode(EXPANDED);
        assertThrows(NullPointerException.class, () -> service.setMode(null));
        assertEquals(EXPANDED, service.mode());
    }

    @Test void overridesAreInstanceLocalAndNewSessionsDefaultToAutomatic() {
        var first = new SFMTooltipModeService(() -> false);
        first.setMode(EXPANDED);
        var second = new SFMTooltipModeService(() -> false);
        assertEquals(AUTO, second.mode());
        assertFalse(second.isExpanded());
        assertTrue(first.isExpanded());
    }

    @Test void nullPlayerConnectionTransitionsPreserveTitleScreenOverrides() {
        var service = SFMTooltipModeService.INSTANCE;
        var original = service.mode();
        try {
            for (var mode : SFMTooltipModeService.Mode.values()) {
                service.setMode(mode);
                SFMTooltipModeLifecycle.onLogout(new ClientPlayerNetworkEvent.LoggingOut(null, null, null));
                assertEquals(mode, service.mode());
            }
        } finally { service.setMode(original); }
    }

    @Test void actualPlayerSessionLogoutPolicyResetsWithoutPollingInput() {
        var service = new SFMTooltipModeService(() -> { throw new AssertionError("Native input reached"); });
        for (var mode : SFMTooltipModeService.Mode.values()) {
            service.setMode(mode);
            SFMTooltipModeLifecycle.resetAfterLogout(service, false);
            assertEquals(mode, service.mode());
            SFMTooltipModeLifecycle.resetAfterLogout(service, true);
            assertEquals(AUTO, service.mode());
            SFMTooltipModeLifecycle.resetAfterLogout(service, true);
            assertEquals(AUTO, service.mode());
        }
    }
}
