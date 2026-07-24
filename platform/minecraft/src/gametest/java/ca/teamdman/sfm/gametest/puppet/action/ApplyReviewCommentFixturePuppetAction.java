package ca.teamdman.sfm.gametest.puppet.action;
import ca.teamdman.sfm.client.screen.review.comment.SFMReviewCommentPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import net.minecraft.client.Minecraft;
public record ApplyReviewCommentFixturePuppetAction(String command) implements SFMPuppetAction {
 public String description(){return "apply review-comment fixture command "+command;}
 public boolean tick(ISFMGamePuppetRuntime runtime){if(!(Minecraft.getInstance().screen instanceof SFMScreenMultiplexer m))throw new IllegalStateException("Expected multiplexer");var p=m.panels().stream().filter(SFMReviewCommentPanel.class::isInstance).map(SFMReviewCommentPanel.class::cast).findFirst().orElseThrow();p.applyAutomation(command);return true;}
}
