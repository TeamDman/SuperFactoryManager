package ca.teamdman.sfm.client.action;

import com.mojang.brigadier.tree.ArgumentCommandNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMGuiScaleActionTests {
    @Test
    void incrementTreatsAutoAsTheFirstExplicitScale() {
        assertEquals(1, SFMGuiScaleAction.nextScale(0, 4, SFMGuiScaleAction.Operation.INCREMENT));
        assertEquals(4, SFMGuiScaleAction.nextScale(4, 4, SFMGuiScaleAction.Operation.INCREMENT));
    }

    @Test
    void decrementReturnsToAutoAtTheMinimumExplicitScale() {
        assertEquals(0, SFMGuiScaleAction.nextScale(1, 4, SFMGuiScaleAction.Operation.DECREMENT));
        assertEquals(2, SFMGuiScaleAction.nextScale(3, 4, SFMGuiScaleAction.Operation.DECREMENT));
    }

    @Test
    void setAndCurrentValuesAcceptAutoButRejectOutOfRangeValues() {
        assertEquals(0, SFMGuiScaleAction.validateScale(0, 4));
        assertEquals(4, SFMGuiScaleAction.validateScale(4, 4));
        assertThrows(IllegalArgumentException.class, () -> SFMGuiScaleAction.validateScale(-1, 4));
        assertThrows(IllegalArgumentException.class, () -> SFMGuiScaleAction.validateScale(5, 4));
    }

    @Test
    void setSuggestsAutoAndEverySupportedExplicitScale() {
        assertEquals(
                java.util.List.of(0, 1, 2, 3, 4),
                SFMGuiScaleAction.suggestedScaleValues(4)
        );
    }

    @Test
    void onlySetExposesARequiredIntegerArgument() {
        var setNode = new SFMGuiScaleAction(SFMGuiScaleAction.Operation.SET)
                .createCommandNode("set")
                .build();
        var incrementNode = new SFMGuiScaleAction(SFMGuiScaleAction.Operation.INCREMENT)
                .createCommandNode("increment")
                .build();

        assertNotNull(setNode.getChild("scale"));
        assertTrue(setNode.getChild("scale") instanceof ArgumentCommandNode<?, ?>);
        assertNotNull(((ArgumentCommandNode<?, ?>) setNode.getChild("scale")).getCustomSuggestions());
        assertNotNull(incrementNode.getCommand());
        assertEquals(0, incrementNode.getChildren().size());
    }
}
