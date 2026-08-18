package ca.teamdman.sfm.client.control;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SFMClientControlServerTests {
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
}
