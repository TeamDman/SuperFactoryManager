package ca.teamdman.sfm.client.program;

import ca.teamdman.sfml.ast.ProgramExecutionSide;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClientProgramConsentReviewTests {
    @Test void changedSegmentDiffPreservesLeadingTrailingAndChangedSource() {
        assertEquals(List.of("  1 unchanged leading lines", "- 2 | red", "+ 2 | blue", "  1 unchanged trailing lines"),
                ClientProgramConsentReview.diff("start\nred\nend", "start\nblue\nend"));
        assertEquals(List.of("Sources are identical."), ClientProgramConsentReview.diff("same", "same"));
    }
    @Test void scopeSourceAndPriorCapabilitiesAreReviewableWithoutChangingConsent() {
        var store = new ClientProgramConsentStore(() -> 1000);
        var world = ClientProgramWorldIdentity.integrated(new UUID(1, 2));
        var old = ClientProgramIdentity.fromStoredSourceAndBindings("old source", "display=1,2,3", ProgramExecutionSide.CLIENT,
                world, new ResourceLocation("minecraft:overworld"), BlockPos.ZERO, ClientProgramIdentity.CLIENT_MANAGER_RUNTIME,
                Set.of(ClientProgramConsentGate.EXECUTE));
        var current = ClientProgramIdentity.fromStoredSourceAndBindings("new source", "display=1,2,3", ProgramExecutionSide.CLIENT,
                world, new ResourceLocation("minecraft:overworld"), BlockPos.ZERO, ClientProgramIdentity.CLIENT_MANAGER_RUNTIME,
                Set.of(ClientProgramConsentGate.EXECUTE, ClientProgramConsentGate.RENDER));
        store.observe(old, "old source", "display=1,2,3", Map.of("sfm", "old"));
        store.observe(current, "new source", "display=1,2,3", Map.of("sfm", "new"));
        var selected = store.snapshots().get(1);
        var previous = ClientProgramConsentReview.previous(store, selected);
        assertEquals(old, previous.orElseThrow().identity());
        var scope = ClientProgramConsentReview.lines(selected, previous, ClientProgramConsentReview.View.SCOPE);
        assertTrue(scope.contains("+ sfm:touch_display/render"));
        assertTrue(scope.contains("display=1,2,3"));
        assertTrue(ClientProgramConsentReview.lines(selected, previous, ClientProgramConsentReview.View.SOURCE).contains("1 | new source"));
        assertTrue(ClientProgramConsentReview.lines(selected, previous, ClientProgramConsentReview.View.PREVIOUS).contains("1 | old source"));
        assertEquals(ClientProgramConsentGate.ConsentState.ABSENT, store.state(current, ClientProgramConsentGate.EXECUTE));
    }
}
