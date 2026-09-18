package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.common.item.PacketItem;
import ca.teamdman.sfm.common.util.SFMDist;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SFMGameTest(SFMDist.CLIENT)
public class PacketItemTooltipGameTest extends SFMGameTestDefinition {
    @Override
    public String template() {
        return "1x1x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        var stack = PacketItem.create(SFMValue.object(Map.of(
                "JobId", SFMValue.of("same"),
                "type", SFMValue.of("Response")
        )));
        ArrayList<Component> compactLines = new ArrayList<>();
        PacketItem.appendTooltipLines(stack, compactLines, false);
        String compact = text(compactLines);
        helper.assertTrue(compact.contains("Contains packet data"),
                "Compact packet tooltip must identify its hidden data");
        helper.assertTrue(!compact.contains("\"JobId\""),
                "Compact packet tooltip must not spill the complete value");

        ArrayList<Component> expandedLines = new ArrayList<>();
        PacketItem.appendTooltipLines(stack, expandedLines, true);
        String expanded = text(expandedLines);
        helper.assertTrue(expanded.contains("\"JobId\": \"same\""),
                "More-info tooltip mode must reveal pretty packet JSON");
        helper.assertTrue(expanded.contains("\"type\": \"Response\""),
                "Expanded packet tooltip must retain the complete value");
        helper.succeed();
    }

    private static String text(List<Component> lines) {
        return lines.stream().map(Component::getString)
                .collect(java.util.stream.Collectors.joining("\n"));
    }
}
