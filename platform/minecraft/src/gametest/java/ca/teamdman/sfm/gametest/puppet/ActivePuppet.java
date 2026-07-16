package ca.teamdman.sfm.gametest.puppet;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.MultipleTestTracker;

import java.util.HashMap;
import java.util.Map;

public final class ActivePuppet {
    public final SFMDiscoveredGamePuppet definition;

    public final SFMGamePuppetHelper helper;

    public final String worldId;

    public final Map<String, PuppetCaptureState> captures = new HashMap<>();

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

    ActivePuppet(
            SFMDiscoveredGamePuppet definition,
            SFMGamePuppetHelper helper
    ) {

        this.definition = definition;
        this.helper = helper;
        this.worldId = SFMGamePuppetHarness.WORLD_ID_PREFIX + definition.puppetName();
    }

}
