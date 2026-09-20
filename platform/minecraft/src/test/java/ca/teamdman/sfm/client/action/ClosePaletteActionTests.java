package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.SFMTransientActionScreen;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClosePaletteActionTests {
    @Test
    void executionDismissesTheExactResolvedTransientSurface() {
        AtomicInteger dismissals = new AtomicInteger();
        SFMTransientActionScreen surface = dismissals::incrementAndGet;

        assertEquals(1, new ClosePaletteAction().execute(surface, null));
        assertEquals(1, dismissals.get());
    }
}
