package ca.teamdman.sfm.client.terminal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TouchDisplayTerminalWorkerProtocolTests {
    static final String READY = "{\"type\":\"ready\",\"version\":1,\"max_line_bytes\":1024,\"max_commands\":4096}";

    @Test void structuredTranscriptUsesRepliesRatherThanPromptsOrEchoedInput() {
        var observation = TouchDisplayTerminalWorkerProtocol.inspect("unstructured shell prompt\n" + READY + "\n"
                + TouchDisplayTerminalWorkerProtocol.touch(7, .25, .75) + "\n"
                + ack(7, 1, .25, .75) + "\n" + render(1));
        assertTrue(observation.ready());
        assertEquals(new TouchDisplayTerminalWorkerProtocol.Ack(7, 1, .25, .75), observation.ack());
        assertEquals(new TouchDisplayTerminalWorkerProtocol.Render(1, "blue"), observation.render());
        assertEquals(0, observation.rejectedRecords());
        assertFalse(observation.ended());
        assertNull(observation.error());
        var echo = TouchDisplayTerminalWorkerProtocol.inspect(TouchDisplayTerminalWorkerProtocol.touch(7, .25, .75));
        assertFalse(echo.ready());
        assertNull(echo.ack());
    }

    @Test void physicalRowWrapsAreJoinedAndIncompleteTrailingReplyWaitsForLaterSnapshot() {
        String complete = ack(3, 1, .125, .875);
        var wrapped = TouchDisplayTerminalWorkerProtocol.inspect(READY.substring(0, 38) + "\r\n"
                + READY.substring(38) + "\r\n" + complete.substring(0, 45) + "\n" + complete.substring(45));
        assertTrue(wrapped.ready());
        assertEquals(3, wrapped.ack().id());
        assertEquals(0, wrapped.rejectedRecords());
        var partial = TouchDisplayTerminalWorkerProtocol.inspect(READY + "\n" + complete.substring(0, 45));
        assertTrue(partial.ready());
        assertNull(partial.ack());
        assertEquals(0, partial.rejectedRecords());
    }

    @Test void newestCounterWinsWithinVisibleSnapshot() {
        var observation = TouchDisplayTerminalWorkerProtocol.inspect(ack(3, 1, .5, .5) + "\n" + render(1)
                + "\n" + ack(4, 2, .25, .75) + "\n" + render(2));
        assertEquals(4, observation.ack().id());
        assertEquals(new TouchDisplayTerminalWorkerProtocol.Render(2, "red"), observation.render());
    }

    @Test void strictScalarSchemaRejectsMalformedOrSpoofedWorkerReplies() {
        String[] invalid = {
                READY.replace("1024", "1025"), READY.replace("\"version\":1", "\"version\":\"1\""),
                READY.replace("\"version\":1", "\"version\":1,\"version\":1"),
                READY.replace("\"version\":1", "\"version\":{}"),
                READY.replace("\"ready\"", "\"unknown\""),
                ack(1, 1, 1.1, .5), ack(1, 4097, .5, .5), ack(-1, 1, .5, .5),
                ack(1, 1, .5, .5).replace("\"id\":1", "\"id\":1.0"),
                ack(1, 1, .5, .5).replace("\"u\":0.5", "\"u\":1e999"),
                render(1).replace("blue", "red"),
                "{\"type\":\"error\",\"version\":1,\"count\":0,\"code\":\"arbitrary text\"}",
                "{\"type\":\"bye\",\"version\":1,\"count\":0,\"reason\":\"arbitrary\"}"
        };
        for (String line : invalid) assertEquals(1, TouchDisplayTerminalWorkerProtocol.inspect(line).rejectedRecords(), line);
    }

    @Test void contentRecordAndCountBoundsRejectWithoutUnboundedAccumulation() {
        assertThrows(IllegalArgumentException.class, () -> TouchDisplayTerminalWorkerProtocol.inspect(null));
        assertThrows(IllegalArgumentException.class, () -> TouchDisplayTerminalWorkerProtocol.inspect("x".repeat(16 * 1024 + 1)));
        assertThrows(IllegalArgumentException.class, () -> TouchDisplayTerminalWorkerProtocol.inspect((READY + "\n").repeat(65)));
        assertEquals(1, TouchDisplayTerminalWorkerProtocol.inspect("{" + "x".repeat(1024) + "}").rejectedRecords());
    }

    @Test void generatedInputIsBoundedFiniteAndCanonicalAtSignedZero() {
        assertEquals("{\"version\":1,\"type\":\"touch\",\"id\":0,\"u\":0.0,\"v\":1.0}",
                TouchDisplayTerminalWorkerProtocol.touch(0, -0.0, 1));
        assertEquals("{\"version\":1,\"type\":\"quit\",\"id\":4294967295}",
                TouchDisplayTerminalWorkerProtocol.quit(TouchDisplayTerminalWorkerProtocol.MAX_ID));
        for (double invalid : new double[]{Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, -.1, 1.1}) {
            assertThrows(IllegalArgumentException.class, () -> TouchDisplayTerminalWorkerProtocol.touch(1, invalid, .5));
        }
        assertThrows(IllegalArgumentException.class, () -> TouchDisplayTerminalWorkerProtocol.quit(-1));
        assertThrows(IllegalArgumentException.class, () -> TouchDisplayTerminalWorkerProtocol.quit(0x1_0000_0000L));
    }

    @Test void endAndErrorAreExplicitObservations() {
        var observation = TouchDisplayTerminalWorkerProtocol.inspect(
                "{\"type\":\"error\",\"version\":1,\"count\":0,\"code\":\"invalid_input\"}\n"
                        + "{\"type\":\"bye\",\"version\":1,\"count\":0,\"reason\":\"eof\"}");
        assertTrue(observation.ended());
        assertEquals("invalid_input", observation.error());
        assertEquals(0, observation.rejectedRecords());
    }

    static String ack(long id, long count, double u, double v) {
        return "{\"type\":\"ack\",\"version\":1,\"id\":" + id + ",\"count\":" + count + ",\"u\":" + u + ",\"v\":" + v + "}";
    }

    static String render(long count) {
        return "{\"type\":\"render\",\"version\":1,\"count\":" + count + ",\"color\":\"" + (count % 2 == 0 ? "red" : "blue") + "\"}";
    }
}
