package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.history.SFMRouteComparisonRuntime;
import ca.teamdman.sfm.client.history.comparison.SFMRouteComparisonCodec;
import ca.teamdman.sfm.client.history.comparison.SFMRouteComparisonSession;
import ca.teamdman.sfm.client.screen.history.SFMRouteComparisonPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import net.minecraft.client.Minecraft;

import java.util.Objects;

/** Deterministic production-store reset/reload controls for the natural X3 puppet. */
public record RouteComparisonSessionPuppetAction(Operation operation) implements SFMPuppetAction {
    public enum Operation {
        RESET,
        RELOAD
    }

    public RouteComparisonSessionPuppetAction {
        Objects.requireNonNull(operation, "operation");
    }

    @Override
    public String description() {
        return operation.name().toLowerCase(java.util.Locale.ROOT) + " route-comparison session";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        SFMRouteComparisonPanel panel = focusedPanel();
        SFMRouteComparisonRuntime comparisonRuntime = SFMRouteComparisonRuntime.get();
        switch (operation) {
            case RESET -> {
                comparisonRuntime.reset(panel.sessionId());
                AssertRouteComparisonPuppetAction.beginJourney();
            }
            case RELOAD -> {
                SFMRouteComparisonSession before = comparisonRuntime.require(panel.sessionId());
                String canonicalBefore = SFMRouteComparisonCodec.encode(before);
                SFMRouteComparisonSession after = comparisonRuntime.reload(panel.sessionId());
                AssertRouteComparisonPuppetAction.recordReload(
                        canonicalBefore,
                        SFMRouteComparisonCodec.encode(after)
                );
            }
        }
        return true;
    }

    static SFMRouteComparisonPanel focusedPanel() {
        if (!(Minecraft.getInstance().screen instanceof SFMScreenMultiplexer workspace)) {
            throw new IllegalStateException("Expected an SFM workspace for route comparison");
        }
        if (!(workspace.focusedPanelInstance() instanceof SFMRouteComparisonPanel panel)) {
            throw new IllegalStateException("Focused panel is not a Route Comparison");
        }
        return panel;
    }
}
