package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.explorer.SFMPathExpression;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SFMExplorerLocationEditActionTests {
    @Test
    public void omittedEditorAndEveryExplicitPlacementEditorVariantRemainInvokable() {
        LiteralArgumentBuilder<SFMClientActionSource> builder = LiteralArgumentBuilder.literal(
                "sfm:explorer/location/edit"
        );
        new SFMExplorerLocationEditAction().configureCommandNode(builder);
        var root = builder.build();
        var selector = root.getChild("explorer_selector");

        assertNotNull(selector);
        assertNotNull(selector.getCommand(), "selector-only form must use the preferred editor on the right");
        for (OpenPanelAction.Direction direction : OpenPanelAction.Direction.values()) {
            var placement = selector.getChild(direction.name().toLowerCase(Locale.ROOT));
            assertNotNull(placement, direction.name());
            assertNotNull(placement.getCommand(), "placement-only form must use the preferred editor");
            assertNotNull(placement.getChild("editor_id"), "explicit editor id must remain available");
            assertNotNull(placement.getChild("editor_id").getCommand());
        }
    }

    @Test
    public void virtualDocumentPreservesEveryCanonicalLocationAtomByteForByte() {
        for (String canonical : List.of(
                "file:///C:/repo/SFM.java",
                "union(file:///C:/repo,registry://minecraft/item/)",
                "members(id(explorer-explorer-1-location))",
                "difference(union(file:///C:/%CF%80,registry://minecraft/item/),"
                        + "members(id(explorer-explorer-1-location)))"
        )) {
            SFMPathExpression expression = SFMPathExpression.parse(canonical);
            assertEquals(canonical, SFMExplorerLocationEditAction.locationDocumentText(expression));
        }
    }
}
