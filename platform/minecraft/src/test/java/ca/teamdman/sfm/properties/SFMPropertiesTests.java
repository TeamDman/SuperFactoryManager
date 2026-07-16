package ca.teamdman.sfm.properties;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SFMPropertiesTests {
    @Test
    public void clientRunModeParsesTheSupportedPropertyValues() {
        assertEquals(SFMProperties.ClientRunMode.NONE, SFMProperties.ClientRunMode.fromPropertyValue(""));
        assertEquals(SFMProperties.ClientRunMode.SMOKE, SFMProperties.ClientRunMode.fromPropertyValue("smoke"));
        assertEquals(SFMProperties.ClientRunMode.PUPPET, SFMProperties.ClientRunMode.fromPropertyValue("puppet"));
        assertEquals(SFMProperties.ClientRunMode.GAME_PUPPET, SFMProperties.ClientRunMode.fromPropertyValue("game-puppet"));
        assertEquals(SFMProperties.ClientRunMode.NONE, SFMProperties.ClientRunMode.fromPropertyValue("unknown"));
    }

    @Test
    public void gamePuppetGameTestIdNormalizesTheSfmNamespaceAndRejectsSelectors() {
        assertEquals(
                "move_1_stack_direct",
                SFMProperties.SFMGameTestId.fromPropertyValue("sfm:move_1_stack_direct").value()
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> SFMProperties.SFMGameTestId.fromPropertyValue("other:move_1_stack_direct")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> SFMProperties.SFMGameTestId.fromPropertyValue("move_*")
        );
    }
}
