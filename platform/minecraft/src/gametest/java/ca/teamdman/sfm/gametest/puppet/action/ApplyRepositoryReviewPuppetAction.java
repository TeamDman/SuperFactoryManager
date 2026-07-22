package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.screen.review.repository.SFMRepositoryReviewPanel;
import ca.teamdman.sfm.client.screen.review.comment.SFMReviewCommentDataSource;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import net.minecraft.client.Minecraft;

import java.util.Locale;

public record ApplyRepositoryReviewPuppetAction(String command) implements SFMPuppetAction {
    @Override
    public String description() {
        return "apply repository review command " + command;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (!(Minecraft.getInstance().screen instanceof SFMScreenMultiplexer multiplexer)) {
            throw new IllegalStateException("Expected repository-review multiplexer");
        }
        SFMRepositoryReviewPanel panel = multiplexer.panels().stream()
                .filter(SFMRepositoryReviewPanel.class::isInstance)
                .map(SFMRepositoryReviewPanel.class::cast)
                .findFirst()
                .orElseThrow();
        if (command.startsWith("select:")) {
            panel.selectFile(Integer.parseInt(command.substring(7)));
        } else if (command.startsWith("line:")) {
            String[] parts = command.split(":");
            SFMReviewCommentDataSource.Side side = SFMReviewCommentDataSource.Side.valueOf(
                    parts[1].toUpperCase(Locale.ROOT));
            panel.selectSourceLine(side, Integer.parseInt(parts[2]));
        } else if (command.startsWith("search:")) {
            panel.setSearch(command.substring(7));
        } else if (command.startsWith("comment:")) {
            panel.beginComment();
            panel.setDraft(command.substring(8));
            panel.submitComment();
        } else if (command.equals("refresh")) {
            panel.refresh();
        } else {
            throw new IllegalArgumentException("Unknown repository-review command " + command);
        }
        return true;
    }
}
