package ca.teamdman.sfm.client.action;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PanelScaleActionTests {
    @Test
    void relativeAdjustmentStartsFromTheResolvedAutoPosition() {
        assertEquals(5, PanelScaleAction.adjustedScale(
                PanelScaleAction.Operation.INCREASE,
                null,
                4));
        assertEquals(4, PanelScaleAction.adjustedScale(
                PanelScaleAction.Operation.DECREASE,
                null,
                4));
    }

    @Test
    void adjustmentContinuesNumericallyAfterAutoBecomesExplicit() {
        assertEquals(5, PanelScaleAction.adjustedScale(
                PanelScaleAction.Operation.INCREASE,
                4,
                4));
        assertEquals(3, PanelScaleAction.adjustedScale(
                PanelScaleAction.Operation.DECREASE,
                4,
                4));
    }

    @Test
    void nonRelativeOperationsCannotUseTheAdjustmentHelper() {
        assertThrows(IllegalArgumentException.class, () -> PanelScaleAction.adjustedScale(
                PanelScaleAction.Operation.CLEAR,
                null,
                4));
    }
}
