package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.screen.review.SFMSourceComparisonPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import net.minecraft.client.Minecraft;

public record ApplySourceReviewFixturePuppetAction(String command) implements SFMPuppetAction {
    @Override
    public String description() { return "apply source-review fixture command " + command; }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (!(Minecraft.getInstance().screen instanceof SFMScreenMultiplexer multiplexer)) {
            throw new IllegalStateException("Expected source-review multiplexer");
        }
        SFMSourceComparisonPanel panel = multiplexer.panels().stream()
                .filter(SFMSourceComparisonPanel.class::isInstance)
                .map(SFMSourceComparisonPanel.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Source-review panel is not open"));
        panel.applyFixtureCommand(command);
        return true;
    }
}
