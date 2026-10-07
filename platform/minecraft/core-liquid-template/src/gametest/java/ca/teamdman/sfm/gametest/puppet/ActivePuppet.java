package ca.teamdman.sfm.gametest.puppet;

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.workspace_panels %}
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.MultipleTestTracker;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import net.minecraft.world.phys.AABB;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}

import java.util.HashMap;
import java.util.Map;

public final class ActivePuppet {
    public final SFMDiscoveredGamePuppet definition;

    public final SFMGamePuppetHelper helper;
    public final SFMGamePuppetViewportVariant viewportVariant;
    public final SFMGamePuppetViewportController viewportController;

    public final String worldId;

    public final Map<String, PuppetCaptureState> captures = new HashMap<>();
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.workspace_panels %}
{% if features.terminal_properties or features.terminal_presentation_actions %}
    public final Map<SFMWorkspacePanelId, TerminalPresentationProgress> terminalPresentations =
            new HashMap<>();
{% endif %}
{% endif %}
{% if features.workspace_panels and features.terminal_presentation_actions %}
    public final Map<SFMWorkspacePanelId, Integer> terminalPresentationUiSelections =
            new HashMap<>();
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.workspace_panels %}
{% if features.terminal_properties or features.terminal_presentation_actions %}
    public record TerminalPresentationProgress(
            String generation,
            long fullResyncFrames,
            String sessionId,
            String connectionEpoch,
            String sessionEpoch,
            long terminalSequence,
            long frameSequence,
            int panelWidth,
            int panelHeight,
            int columns,
            int rows,
            int fontPixelSize
    ) {
    }
{% endif %}
{% endif %}

{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
    public boolean worldCreationStarted;

    public boolean worldConfigured;

    public boolean gameTestStartRequested;

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    public String gameTestName;

{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
    public volatile MultipleTestTracker gameTestTracker;

    public volatile BlockPos gameTestOrigin;

    public volatile GameTestInfo gameTestInfo;

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    /** Captured while the structure block still exists; successful tests may remove it. */
    public volatile AABB gameTestBounds;

{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
    public volatile Throwable gameTestStartFailure;

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    public boolean integratedServerPublishRequested;

    public volatile Throwable integratedServerPublishFailure;

{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
    public int nextFigureNumber = 1;

    public int totalActionTicks;

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.terminal_properties %}
    public String terminalPropertiesWaitState = "";
{% endif %}

{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
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
