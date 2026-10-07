package ca.teamdman.sfm.client.input;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SFMPointerInputModifiersTests {
    @Test void explicitMasksOverridePhysicalKeysIncludingExplicitZero() {
        for (int mask : new int[]{1, 4}) {
            assertTrue(SFMPointerInputModifiers.isDown(mask, () -> true));
            assertFalse(SFMPointerInputModifiers.isDown(mask, () -> false));
            for (int supplied : new int[]{0, 1, 4, 5}) {
                SFMPointerInputModifiers.during(supplied, () -> {
                    assertEquals((supplied & mask) != 0, SFMPointerInputModifiers.isDown(mask,
                            () -> { throw new AssertionError("explicit input must not poll OS keys"); }));
                    return null;
                });
            }
        }
    }
    @Test void scopesRestoreOnNestedInputAndFailure() {
        assertTrue(SFMPointerInputModifiers.current().isEmpty());
        SFMPointerInputModifiers.during(2, () -> {
            assertEquals(2, SFMPointerInputModifiers.current().orElseThrow());
            assertThrows(IllegalStateException.class, () -> SFMPointerInputModifiers.during(1, () -> {
                assertEquals(1, SFMPointerInputModifiers.current().orElseThrow());
                throw new IllegalStateException("fixture");
            }));
            assertEquals(2, SFMPointerInputModifiers.current().orElseThrow());
            return true;
        });
        assertTrue(SFMPointerInputModifiers.current().isEmpty());
    }
}
