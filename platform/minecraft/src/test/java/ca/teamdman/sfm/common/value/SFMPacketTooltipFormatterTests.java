package ca.teamdman.sfm.common.value;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMPacketTooltipFormatterTests {
    @Test
    void compactAndExpandedDescriptionsKeepLargeDataBehindTheMoreInfoGesture() {
        SFMValue value = SFMValue.object(Map.of(
                "job", SFMValue.of("assemble"),
                "quantities", SFMValue.array(List.of(SFMValue.of(2), SFMValue.of(5)))
        ));

        String compact = text(SFMPacketTooltipFormatter.describe(Optional.of(value), false, 11));
        String expanded = text(SFMPacketTooltipFormatter.describe(Optional.of(value), true, 11));

        assertFalse(compact.contains("assemble"));
        assertTrue(expanded.contains("\"job\": \"assemble\""));
        assertTrue(expanded.contains("\"quantities\": ["));
    }

    @Test
    void invalidPacketDataFailsClosedInTheTooltip() {
        String tooltip = text(SFMPacketTooltipFormatter.describe(Optional.empty(), true, 11));

        assertTrue(tooltip.contains("item.sfm.packet.invalid_data"));
    }

    private static String text(List<Component> lines) {
        return lines.stream().map(Component::getString)
                .collect(java.util.stream.Collectors.joining("\n"));
    }
}
