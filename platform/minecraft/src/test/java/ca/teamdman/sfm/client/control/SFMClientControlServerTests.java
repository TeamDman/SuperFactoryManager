package ca.teamdman.sfm.client.control;

import ca.teamdman.sfm.common.net.ClientboundManagerShowPacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMClientControlServerTests {
    @Test
    void managerShowUsesPositiveSuccessResultCode() {
        for (ClientboundManagerShowPacket.Status status : ClientboundManagerShowPacket.Status.values()) {
            assertEquals(status == ClientboundManagerShowPacket.Status.ALLOWED ? 1 : 0,
                    SFMClientControlServer.managerShowResultCode(status));
        }
    }

    @Test
    void registeredActionIdsRemainLiteralTokens() {
        assertEquals("sfm:panel/open", SFMClientControlServer.escapeCommandToken("sfm:panel/open"));
        assertEquals("sfm:size_display", SFMClientControlServer.escapeCommandToken("sfm:size_display"));
    }

    @Test
    void whitespaceAndQuotesAreEscapedForBrigadier() {
        assertEquals("\"text with spaces\"", SFMClientControlServer.escapeCommandToken("text with spaces"));
        assertEquals("\"say \\\"hi\\\"\"", SFMClientControlServer.escapeCommandToken("say \"hi\""));
        assertEquals("\"D:\\\\Repos\\\\Minecraft SFM\"",
                SFMClientControlServer.escapeCommandToken("D:\\Repos\\Minecraft SFM"));
    }

    @Test
    void validationMeasuresTheFullyEscapedBrigadierToken() {
        String exactBoundary = "\"".repeat(2_047);
        assertEquals(4_096, SFMClientControlServer.escapeCommandToken(exactBoundary).getBytes(
                java.nio.charset.StandardCharsets.UTF_8).length);
        assertTrue(SFMClientControlServer.isValidActionToken(exactBoundary));

        String escapedOversize = "\"".repeat(2_048);
        assertTrue(escapedOversize.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 4_096);
        assertFalse(SFMClientControlServer.isValidActionToken(escapedOversize));

        String quoteHeavyJson = "\"" + "\\\"".repeat(1_023) + "\"";
        assertTrue(quoteHeavyJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 3_072);
        assertFalse(SFMClientControlServer.isValidActionToken(quoteHeavyJson));
    }
}
