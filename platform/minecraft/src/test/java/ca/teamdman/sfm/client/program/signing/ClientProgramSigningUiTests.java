package ca.teamdman.sfm.client.program.signing;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClientProgramSigningUiTests {
    @Test void secretBufferNeverFormatsTheSecretAndTransfersThenClearsItsOwnership() {
        var first = new ClientSigningSecretBuffer();
        var second = new ClientSigningSecretBuffer();
        for (char character : "synthetic test phrase".toCharArray()) {
            assertTrue(first.append(character));
            assertTrue(second.append(character));
        }
        assertTrue(first.usable());
        assertTrue(first.matches(second));
        assertFalse(first.toString().contains("synthetic"));
        first.backspace();
        assertFalse(first.matches(second));
        char[] owned = second.consume();
        try { assertArrayEquals("synthetic test phrase".toCharArray(), owned); }
        finally { Arrays.fill(owned, '\0'); }
        assertEquals(0, second.length());
        assertFalse(second.usable());
        first.close();
        assertTrue(first.matches(second));
        assertFalse(first.append('\n'));
        assertFalse(first.append('\0'));
    }

    @Test void secretBufferHasAFixedBoundAndClearAlsoDiscardsTruncatedContent() {
        var buffer = new ClientSigningSecretBuffer();
        for (int i = 0; i < 1024; i++) assertTrue(buffer.append('x'));
        assertFalse(buffer.append('y'));
        buffer.close();
        assertTrue(buffer.append('z'));
        char[] transferred = buffer.consume();
        try { assertArrayEquals(new char[]{'z'}, transferred); }
        finally { Arrays.fill(transferred, '\0'); }
    }

    @Test void penContactAndAccessibleButtonUseTheSameDelayedNoncryptographicCeremony() {
        var ceremony = new ClientProgramSigningCeremony();
        UUID challenge = UUID.randomUUID();
        assertFalse(ceremony.bind(challenge, "fingerprint"));
        ceremony.acknowledge();
        assertTrue(ceremony.acknowledged());
        assertFalse(ceremony.ready());
        for (int i = 0; i < ClientProgramSigningCeremony.REVIEW_DELAY_TICKS; i++) {
            assertTrue(ceremony.bind(challenge, "fingerprint"));
            ceremony.tick();
        }
        assertTrue(ceremony.ready());
        assertFalse(ceremony.bind(challenge, "different key"));
        assertFalse(ceremony.ready());
        assertFalse(ceremony.acknowledged());
        ceremony.acknowledge();
        assertFalse(ceremony.bind(UUID.randomUUID(), "different key"));
        assertFalse(ceremony.acknowledged());
        ceremony.clear();
        ceremony.acknowledge();
        assertFalse(ceremony.ready());
        assertFalse(ceremony.acknowledged());
    }

    @Test void reviewEscapesFormattingAndBidiControlsWithoutNormalizingOrdinaryText() {
        assertEquals("alpha\\t\\u202e\\u00a7\\u0000 beta", ClientProgramSigningReview.visible("alpha\t\u202e\u00a7\u0000 beta"));
        assertEquals("é  -- Exact case and spaces", ClientProgramSigningReview.visible("é  -- Exact case and spaces"));
    }
}
