package ca.teamdman.sfm.client.action;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMActionElementTests {
    private static final ResourceLocation SITUATION = new ResourceLocation("sfm", "default");

    @Test
    void inventoryIsDeterministicAndContainsSemanticMetadata() {
        SFMActionElement edit = element("sfm:manager/edit", "Edit manager");
        SFMActionElement close = element("sfm:panel/close", "Close panel");

        assertEquals(List.of(
                "sfm:manager/edit|sfm:default|sfm action invoke sfm:manager/edit|true|Edit manager",
                "sfm:panel/close|sfm:default|sfm action invoke sfm:panel/close|true|Close panel"
        ), SFMActionElementAudit.inventory(List.of(close, edit)));
        assertTrue(SFMActionElementAudit.errors(List.of(edit)).isEmpty());
    }

    @Test
    void auditRejectsCoordinateActionsAndMissingKeyboardReachability() {
        SFMActionElement invalid = new TestElement(
                new ResourceLocation("sfm", "bad"),
                SITUATION,
                Component.literal("Bad"),
                "sfm action invoke sfm:screen/mouse/click 10 20",
                false);

        List<String> errors = SFMActionElementAudit.errors(List.of(invalid));
        assertTrue(errors.stream().anyMatch(error -> error.contains("coordinate action")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("keyboard reachability")));
    }

    @Test
    void managerEditIsOnlyAvailableForTheCapturedManagerScreen() {
        ManagerEditAction action = new ManagerEditAction();
        assertFalse(action.requirement().resolve(SFMClientActionContext.create(new Object(), () -> true)).isAvailable());
    }

    private static SFMActionElement element(String id, String narration) {
        return new TestElement(
                ResourceLocation.tryParse(id),
                SITUATION,
                Component.literal(narration),
                "sfm action invoke " + id,
                true);
    }

    private record TestElement(
            ResourceLocation elementId,
            ResourceLocation keyboardUsageSituationId,
            Component narration,
            String draft,
            boolean keyboardReachable
    ) implements SFMActionElement {
        @Override
        public Optional<String> actionDraft() {
            return Optional.ofNullable(draft);
        }

        @Override
        public boolean isKeyboardReachable() {
            return keyboardReachable;
        }
    }
}
