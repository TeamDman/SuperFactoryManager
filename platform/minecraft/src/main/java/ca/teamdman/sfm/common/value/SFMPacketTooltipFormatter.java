package ca.teamdman.sfm.common.value;

import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.util.SFMItemUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Registry-free packet tooltip projection shared by runtime and unit tests. */
public final class SFMPacketTooltipFormatter {
    @SFMLocalizationDatagen
    public static final LocalizationEntry PACKET_CONTAINS_DATA = new LocalizationEntry(
            "item.sfm.packet.contains_data",
            "Contains packet data"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry PACKET_INVALID_DATA = new LocalizationEntry(
            "item.sfm.packet.invalid_data",
            "Packet data is missing or invalid"
    );

    private SFMPacketTooltipFormatter() {
    }

    public static List<Component> describe(
            Optional<SFMValue> value,
            boolean expanded,
            int nameLength
    ) {
        if (value.isEmpty()) {
            return List.of(PACKET_INVALID_DATA.getComponent().withStyle(ChatFormatting.RED));
        }
        if (!expanded) {
            return List.of(PACKET_CONTAINS_DATA.getComponent().withStyle(ChatFormatting.GRAY));
        }

        ArrayList<Component> lines = new ArrayList<>();
        lines.add(SFMItemUtils.getRainbow(Math.max(4, nameLength)));
        SFMValueJsonCodec.encodePretty(value.orElseThrow())
                .lines()
                .map(line -> Component.literal(line).withStyle(ChatFormatting.AQUA))
                .forEach(lines::add);
        return List.copyOf(lines);
    }
}
