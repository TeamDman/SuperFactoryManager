package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.client.context.SFMContextCaptureRequest;
import ca.teamdman.sfm.client.context.SFMContextContribution;
import ca.teamdman.sfm.client.context.SFMContextContributor;
import ca.teamdman.sfm.client.context.SFMContextGenerationEvidence;
import ca.teamdman.sfm.client.context.SFMContextOriginId;
import ca.teamdman.sfm.client.context.SFMContextPathProjection;
import ca.teamdman.sfm.client.explorer.SFMPath;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMScreenMultiplexerContextTests {
    @Test
    void visibleOriginsRemainIndependentWhenFocusChangesAndCapturedContentIsImmutable() {
        FakeContextPanel left = new FakeContextPanel("left", SFMPath.parse("file:///D:/left/A.java"));
        FakeContextPanel right = new FakeContextPanel("right", SFMPath.parse("file:///D:/right/A.java"));
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.sideBySide(left, right);
        SFMWorkspacePanelId leftPanelId = layout.visiblePanels().get(0).id();
        SFMWorkspacePanelId rightPanelId = layout.visiblePanels().get(1).id();
        assertTrue(layout.focus(leftPanelId));

        var first = SFMScreenMultiplexer.captureVisibleContext(
                layout.visiblePanels(),
                layout.panel(layout.focusedPanel()),
                1,
                1,
                1
        );
        assertEquals(2, first.contributions().size());
        assertEquals(left.origin, first.focusedOriginId().orElseThrow());
        assertEquals(0, first.focusRank(left.origin));
        assertEquals(2, first.focusRank(right.origin));

        assertTrue(layout.focus(rightPanelId));
        var second = SFMScreenMultiplexer.captureVisibleContext(
                layout.visiblePanels(),
                layout.panel(layout.focusedPanel()),
                2,
                1,
                2
        );
        assertEquals(right.origin, second.focusedOriginId().orElseThrow());
        assertEquals(2, second.contributions().size(), "focus must not discard the other visible origin");
        assertEquals(0, second.focusRank(right.origin));

        left.replace(SFMPath.parse("file:///D:/left/Changed.java"));
        SFMContextPathProjection retained = (SFMContextPathProjection) first.contributions().get(0).projection();
        assertEquals("file:///D:/left/A.java", retained.path().canonical());
    }

    private static final class FakeContextPanel implements SFMScreenPanel, SFMContextContributor {
        private final SFMContextOriginId origin;
        private final ArrayList<SFMContextContribution> contribution = new ArrayList<>();

        private FakeContextPanel(String panel, SFMPath path) {
            origin = new SFMContextOriginId("fake", panel, "path");
            replace(path);
        }

        private void replace(SFMPath path) {
            contribution.clear();
            contribution.add(new SFMContextContribution(
                    origin,
                    SFMContextGenerationEvidence.INITIAL,
                    new SFMContextPathProjection(path, Optional.empty(), "test")
            ));
        }

        @Override public String id() { return "fake"; }
        @Override public Optional<SFMContextOriginId> focusedOriginId() { return Optional.of(origin); }
        @Override public List<SFMContextContribution> capture(SFMContextCaptureRequest request) {
            return contribution;
        }
        @Override public Component title() { return Component.literal(origin.containerId()); }
        @Override public void render(PoseStack poseStack, Minecraft minecraft, SFMScreenPanelBounds bounds,
                                     int mouseX, int mouseY, float partialTick, boolean focused) { }
    }
}
