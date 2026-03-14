package ca.teamdman.sfm;

import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.util.SFMComponentUtils;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import ca.teamdman.sfml.ast.Label;
import ca.teamdman.sfml.ast.LabelExpression;
import ca.teamdman.sfml.ast.LabelExpressionSingle;
import ca.teamdman.sfml.ast.RoundRobinBehaviour;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SFMTests {
    @Test
    public void componentSubstring() {

        StringBuilder sb = new StringBuilder();
        var component = Component.literal("");
        sb.append("hello");
        component.append(Component.literal("hello").withStyle(ChatFormatting.GRAY));
        sb.append(" ");
        component.append(" ");
        sb.append("world");
        component.append(Component.literal("world").withStyle(ChatFormatting.RED));
        var content = sb.toString();
        for (int start = 0; start < content.length(); start++) {
            for (int end = start; end < content.length(); end++) {
                MutableComponent substring = SFMComponentUtils.substring(component, start, end);
                assertEquals(content.substring(start, end), substring.getString());
            }
        }
    }

    @Test
    public void roundRobinByBlockDistinct() {

        LabelPositionHolder labelPositions = LabelPositionHolder.empty();
        labelPositions.add("a", new BlockPos(0, 0, 0));
        labelPositions.add("b", new BlockPos(0, 0, 0));
        labelPositions.add("c", new BlockPos(0, 0, 0));
        labelPositions.add("c", new BlockPos(0, 1, 0));
        List<LabelExpression> labelExpressions = List.of(
                new LabelExpressionSingle(new Label("a")),
                new LabelExpressionSingle(new Label("c"))
        );
        assertEquals(
                List.of(new BlockPos(0, 0, 0)),
                RoundRobinBehaviour.BY_BLOCK.getPositions(labelExpressions, labelPositions, 0)
        );
        // should not repeat the same block
        assertEquals(
                List.of(new BlockPos(0, 1, 0)),
                RoundRobinBehaviour.BY_BLOCK.getPositions(labelExpressions, labelPositions, 1)
        );
    }

    @Test
    public void understandFastUtilsLongMap() {

        Map<Long, String> map = new Long2ObjectOpenHashMap<>();
        map.put(123L, "hi");
        assertEquals("hi", map.get(123L));
        assertNull(map.get(124L));
    }

    @Test
    public void iForgetIfICanUseResourceLocationsHere() {

        ResourceLocation bruh = SFMResourceLocation.fromSFMPath("bruh");
        assertEquals("sfm", bruh.getNamespace());
        assertEquals("bruh", bruh.getPath());
    }

}
