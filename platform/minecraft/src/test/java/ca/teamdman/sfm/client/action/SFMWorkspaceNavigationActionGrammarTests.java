package ca.teamdman.sfm.client.action;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMWorkspaceNavigationActionGrammarTests {
    @Test
    void indexedFocusIsARealHierarchicalActionWithBoundedArgument() {
        ResourceLocation actionId = new ResourceLocation("sfm", "panel/focus/index");
        LiteralCommandNode<SFMClientActionSource> node =
                new FocusPanelAction(FocusPanelAction.Operation.INDEX).createCommandNode(actionId).build();
        ArgumentCommandNode<SFMClientActionSource, Integer> index =
                (ArgumentCommandNode<SFMClientActionSource, Integer>) node.getChildren().iterator().next();
        IntegerArgumentType argumentType = (IntegerArgumentType) index.getType();

        assertEquals("index", index.getName());
        assertEquals(1, IntegerArgumentType.getMinimum(argumentType));
        assertEquals(9, IntegerArgumentType.getMaximum(argumentType));
    }

    @Test
    void traversalActionsHaveDistinctSemanticMetadata() {
        assertTrue(new FocusPanelAction(FocusPanelAction.Operation.NEXT)
                .title().getString().contains("next"));
        assertTrue(new FocusPanelAction(FocusPanelAction.Operation.PREVIOUS)
                .title().getString().contains("previous"));
        assertTrue(new ToggleMaximizePanelAction().description().getString().contains("focused"));
    }
}
