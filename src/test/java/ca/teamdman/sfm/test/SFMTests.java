package ca.teamdman.sfm.test;

import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.util.SFMComponentUtils;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import ca.teamdman.sfml.ast.*;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SFMTests {
    @Test
    public void componentSubstring() {
        StringBuilder sb = new StringBuilder();
        var component = new TextComponentString("");
        sb.append("hello");
        component.appendSibling(new TextComponentString("hello").setStyle(new Style().setColor(TextFormatting.GRAY)));
        sb.append(" ");
        component.appendSibling(new TextComponentString(" "));
        sb.append("world");
        component.appendSibling(new TextComponentString("world").setStyle(new Style().setColor(TextFormatting.RED)));
        var content = sb.toString();
        for (int start = 0; start < content.length(); start++) {
            for (int end = start; end < content.length(); end++) {
                ITextComponent substring = SFMComponentUtils.substring(component, start, end);
                assertEquals(content.substring(start, end), substring.getUnformattedText());
            }
        }
    }

//    @Test
//    public void roundRobinByBlockDistinct() {
//        LabelAccess labelAccess = new LabelAccess(
//                Stream.of("a", "b", "c").map(Label::new).toList(),
//                new SideQualifier(List.of(Side.BOTTOM)),
//                NumberRangeSet.MAX_RANGE,
//                new RoundRobin(RoundRobin.Behaviour.BY_BLOCK)
//        );
//        LabelPositionHolder labelPositions = LabelPositionHolder.empty();
//        labelPositions.add("a", new BlockPos(0, 0, 0));
//        labelPositions.add("b", new BlockPos(0, 0, 0));
//        labelPositions.add("c", new BlockPos(0, 0, 0));
//        labelPositions.add("c", new BlockPos(0, 1, 0));
//        assertEquals(
//                List.of(Pair.of(new Label("a"), new BlockPos(0, 0, 0))),
//                labelAccess.getLabelledPositions(labelPositions)
//        );
//        // should not repeat the same block
//        assertEquals(
//                List.of(Pair.of(new Label("c"), new BlockPos(0, 1, 0))),
//                labelAccess.getLabelledPositions(labelPositions)
//        );
//    }
//
//    @Test
//    public void understandFastUtilsLongMap() {
//        Map<Long, String> map = new Long2ObjectOpenHashMap<>();
//        map.put(123L, "hi");
//        assertEquals("hi", map.get(123L));
//        assertNull(map.get(124L));
//    }
//
//    @Test
//    public void iForgetIfICanUseResourceLocationsHere() {
//        ResourceLocation bruh = SFMResourceLocation.fromSFMPath("bruh");
//        assertEquals("sfm", bruh.getNamespace());
//        assertEquals("bruh", bruh.getPath());
//    }
}
