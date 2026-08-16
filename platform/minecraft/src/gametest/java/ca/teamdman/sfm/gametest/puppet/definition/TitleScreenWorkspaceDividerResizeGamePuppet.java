package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetViewportProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;

/** Live visual and machine-readable proof of Track 1b divider capture and resizing. */
@SFMGamePuppet(viewportProfile = SFMGamePuppetViewportProfile.GUI_SCALE_MATRIX)
public final class TitleScreenWorkspaceDividerResizeGamePuppet {
    private static final int TITLE_SCREEN_FADE_IN_TICKS = 20;

    private TitleScreenWorkspaceDividerResizeGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(TITLE_SCREEN_FADE_IN_TICKS);
        exercise(puppet, 2, "two", 36, 0);
        exercise(puppet, 3, "three", 36, 24);
        exercise(puppet, 4, "four", 36, 24);
    }

    private static void exercise(
            SFMGamePuppetHelper puppet,
            int paneCount,
            String label,
            int deltaX,
            int deltaY
    ) {
        String prefix = "workspace-divider-" + label;
        puppet.openWorkspaceDividerFixture(paneCount);
        puppet.waitForScreen(SFMScreenMultiplexer.class);
        puppet.writeWorkspaceDividerEvidence(prefix + "-before", "before", paneCount);
        puppet.capture(prefix + "-before", caption(paneCount, "before pointer hover"));

        puppet.hoverWorkspaceDividerIntersection();
        puppet.waitTicks(2);
        puppet.writeWorkspaceDividerEvidence(prefix + "-hover", "hover", paneCount);
        puppet.capture(prefix + "-hover", caption(paneCount, "hovering the resizable intersection"));

        puppet.dragWorkspaceDividerIntersection(deltaX, deltaY);
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
        puppet.writeWorkspaceDividerEvidence(prefix + "-during", "during", paneCount);
        puppet.capture(prefix + "-during", caption(paneCount, "during pointer-captured resize"));

        puppet.releaseWorkspaceDividerIntersection();
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
        puppet.writeWorkspaceDividerEvidence(prefix + "-after", "after", paneCount);
        puppet.capture(prefix + "-after", caption(paneCount, "after committing divider shares"));
        puppet.closeScreenNaturally();
    }

    private static Component caption(int paneCount, String stage) {
        return Component.literal("SFM ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(paneCount + "-pane workspace " + stage + "."));
    }
}
