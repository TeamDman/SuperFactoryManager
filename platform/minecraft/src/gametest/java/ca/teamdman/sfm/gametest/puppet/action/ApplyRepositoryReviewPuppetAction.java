package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.screen.review.repository.SFMRepositoryReviewChangedFilesPanel;
import ca.teamdman.sfm.client.screen.review.repository.SFMRepositoryReviewWorkspaceModel;
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
        SFMRepositoryReviewWorkspaceModel model = multiplexer.panels().stream()
                .filter(SFMRepositoryReviewChangedFilesPanel.class::isInstance)
                .map(SFMRepositoryReviewChangedFilesPanel.class::cast)
                .map(SFMRepositoryReviewChangedFilesPanel::model)
                .findFirst()
                .orElseThrow();
        if (command.startsWith("select:")) {
            model.selectFile(Integer.parseInt(command.substring(7)));
        } else if (command.startsWith("line:")) {
            String[] parts = command.split(":");
            SFMReviewCommentDataSource.Side side = SFMReviewCommentDataSource.Side.valueOf(
                    parts[1].toUpperCase(Locale.ROOT));
            model.selectSourceLine(side, Integer.parseInt(parts[2]));
        } else if (command.startsWith("search:")) {
            model.setSearch(command.substring(7));
        } else if (command.startsWith("change:")) {
            model.revealFirstChange(SFMReviewCommentDataSource.Side.valueOf(
                    command.substring(7).toUpperCase(Locale.ROOT)));
        } else if (command.startsWith("comment:")) {
            model.beginComment();
            model.setDraft(command.substring(8));
            model.submitComment();
        } else if (command.equals("refresh")) {
            model.refresh();
        } else if (command.startsWith("show:")) {
            model.show(SFMRepositoryReviewWorkspaceModel.Blade.valueOf(command.substring(5).toUpperCase(Locale.ROOT)));
        } else {
            throw new IllegalArgumentException("Unknown repository-review command " + command);
        }
        return true;
    }
}
