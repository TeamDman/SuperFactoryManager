package ca.teamdman.sfm.client.action;

import com.mojang.brigadier.StringReader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SFMCanonicalTokenArgumentTests {
    @Test
    void consumesCanonicalSelectorsUrisAndComposedExpressionsWithoutQuotes() throws Exception {
        assertToken("id(explorer%20one)");
        assertToken("file:///C:/Repos/Minecraft/SFM");
        assertToken("members(id(explorer-explorer%20one-location))");
        assertToken("union(file:///C:/a,registry://minecraft/item/)");
    }

    @Test
    void stopsAtTheFlagFollowingAPathExpression() throws Exception {
        StringReader reader = new StringReader(
                "union(file:///C:/a,registry://minecraft/item/) --expected-revision 7"
        );

        assertEquals(
                "union(file:///C:/a,registry://minecraft/item/)",
                SFMCanonicalTokenArgument.token().parse(reader)
        );
        reader.skipWhitespace();
        assertEquals("--expected-revision 7", reader.getRemaining());
    }

    private static void assertToken(String expected) throws Exception {
        StringReader reader = new StringReader(expected);
        assertEquals(expected, SFMCanonicalTokenArgument.token().parse(reader));
        assertEquals(0, reader.getRemainingLength());
    }
}
