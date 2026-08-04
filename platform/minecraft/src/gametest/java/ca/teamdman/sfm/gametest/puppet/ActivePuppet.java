package ca.teamdman.sfm.gametest.puppet;

import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.MultipleTestTracker;

import java.util.HashMap;
import java.util.Map;

public final class ActivePuppet {
    public final SFMDiscoveredGamePuppet definition;

    public final SFMGamePuppetHelper helper;
    public final SFMGamePuppetViewportVariant viewportVariant;
    public final SFMGamePuppetViewportController viewportController;

    public final String worldId;

    public final Map<String, PuppetCaptureState> captures = new HashMap<>();
    public final Map<SFMWorkspacePanelId, TerminalPresentationProgress> terminalPresentations =
            new HashMap<>();
    public final Map<SFMWorkspacePanelId, Integer> terminalPresentationUiSelections =
            new HashMap<>();

    public record TerminalPresentationProgress(
            String generation,
            long fullResyncFrames,
            String sessionId,
            String connectionEpoch,
            String sessionEpoch,
            long terminalSequence,
            long frameSequence,
            int panelWidth,
            int panelHeight
    ) {
    }

    public boolean worldCreationStarted;

    public boolean worldConfigured;

    public boolean gameTestStartRequested;

    public volatile MultipleTestTracker gameTestTracker;

    public volatile BlockPos gameTestOrigin;

    public volatile GameTestInfo gameTestInfo;

    public volatile Throwable gameTestStartFailure;

    public int nextFigureNumber = 1;

    public int totalActionTicks;

    public boolean failureRecorded;

    public boolean success;
    public boolean declared;
    public boolean viewportPrepared;
    public SFMGamePuppetViewportObservation viewportObservation;

    ActivePuppet(
            SFMDiscoveredGamePuppet definition,
            SFMGamePuppetHelper helper,
            SFMGamePuppetViewportVariant viewportVariant
    ) {

        this.definition = definition;
        this.helper = helper;
        this.viewportVariant = viewportVariant;
        this.viewportController = new SFMGamePuppetViewportController(viewportVariant);
        this.worldId = SFMGamePuppetHarness.WORLD_ID_PREFIX + definition.puppetName() + "_" + viewportVariant.id().replace('@', '_');
    }

}
