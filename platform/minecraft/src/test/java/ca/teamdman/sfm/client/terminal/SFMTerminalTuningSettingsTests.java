package ca.teamdman.sfm.client.terminal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SFMTerminalTuningSettingsTests {
    @Test
    void automaticDimensionsClampToProtocolBounds() {
        assertEquals(
                new SFMTerminalTuningSettings.Effective(4096, 4096, 0, 240, 120),
                SFMTerminalTuningSettings.automatic().resolve(8192, 8192, 960, 480)
        );
    }

    @Test
    void manualDimensionsRemainExactWhenAutomaticAllocationIsLarger() {
        SFMTerminalTuningSettings manual = new SFMTerminalTuningSettings(
                2048, 1024, 32, 200, 100);

        assertEquals(
                new SFMTerminalTuningSettings.Effective(2048, 1024, 32, 200, 100),
                manual.resolve(8192, 8192, 960, 480)
        );
    }
}
