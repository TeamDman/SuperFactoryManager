package ca.teamdman.sfm.properties;

import ca.teamdman.sfm.SFMProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SFMPropertiesTests {
    @Test
    public void clientRunModeParsesTheSupportedPropertyValues() {
        assertEquals(SFMProperties.ClientRunMode.NONE, SFMProperties.ClientRunMode.fromPropertyValue(""));
        assertEquals(SFMProperties.ClientRunMode.SMOKE, SFMProperties.ClientRunMode.fromPropertyValue("smoke"));
        assertEquals(SFMProperties.ClientRunMode.PUPPET, SFMProperties.ClientRunMode.fromPropertyValue("puppet"));
        assertEquals(SFMProperties.ClientRunMode.GAME_PUPPET, SFMProperties.ClientRunMode.fromPropertyValue("game-puppet"));
        assertEquals(SFMProperties.ClientRunMode.NONE, SFMProperties.ClientRunMode.fromPropertyValue("unknown"));
    }
}
