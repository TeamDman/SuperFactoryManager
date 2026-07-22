package ca.teamdman.sfm.client.screen.review.comment;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import net.minecraft.client.gui.screens.Screen;
public final class SFMReviewCommentWorkspace {
 private SFMReviewCommentWorkspace(){}
 public static Screen create(Screen previous){return SFMScreenMultiplexer.create(previous,new SFMReviewCommentPanel(new SFMFixtureReviewCommentDataSource()));}
}
