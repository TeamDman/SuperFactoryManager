package ca.teamdman.sfm.gametest.puppet.definition;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.gametest.puppet.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;
@SFMGamePuppet
public final class TitleScreenReviewCommentsGamePuppet {
 private TitleScreenReviewCommentsGamePuppet(){}
 public static void run(SFMGamePuppetHelper p){
  p.waitForOverlayToNotBePresent(LoadingOverlay.class);p.waitTicks(20);p.openCommandPalette();
  p.executeCommandPaletteAndWaitForScreen("sfm action invoke sfm:developer/open_comment_review",SFMScreenMultiplexer.class);
  p.applyReviewCommentFixtureCommand("overview");p.capture("review-comments-before-after",cap("Diff-produced comments keep before and after source as first-class selectable documents."));
  p.applyReviewCommentFixtureCommand("disjoint");p.capture("review-comments-disjoint",cap("One ordinary comment can retain disjoint literal ranges across both document revisions."));
  p.applyReviewCommentFixtureCommand("create");p.capture("review-comments-created",cap("A literal UTF-8 selection creates an ordinary human comment whose hashtags come only from its text."));
  p.applyReviewCommentFixtureCommand("edit");p.capture("review-comments-edited",cap("Editing the text re-derives #needs-change without a separately stored tags field."));
  p.applyReviewCommentFixtureCommand("overlap");p.capture("review-comments-overlap",cap("The approved method and audit problem overlap audit(); while both remain independently inspectable."));
  p.applyReviewCommentFixtureCommand("styles");p.capture("review-comments-style-precedence",cap("Ordered rules resolve background, foreground, underline, and gutter channels independently."));
  p.applyReviewCommentFixtureCommand("style-picker");p.setColorInputHex("#AAFF33AA",false);p.capture("review-comments-colour-picker",cap("The reusable Theme Settings colour input edits the visible #problem source background through a typed callback."));
  p.confirmColorInput();p.waitTicks(20);p.applyReviewCommentFixtureCommand("styles");p.waitTicks(20);p.capture("review-comments-colour-applied",cap("The #problem rule now displays the applied background ARGB value without changing comment semantics."));
  p.applyReviewCommentFixtureCommand("f2");p.capture("review-comments-f2-problem",cap("F2 navigates to the next effective comment whose derived hashtags contain #problem."));
  p.capture("review-comments-colour-on-source",cap("The newly selected background channel is visibly applied to the #problem source range."));
  p.applyReviewCommentFixtureCommand("migration:0");p.capture("review-comments-migration-exact",cap("Exact migration retains its UTF-8 witnesses."));
  p.applyReviewCommentFixtureCommand("migration:1");p.capture("review-comments-migration-changed",cap("Content-changed migration suspends approval and requires review."));
  p.applyReviewCommentFixtureCommand("migration:2");p.capture("review-comments-migration-invalid",cap("An invalid literal range remains visible in the migration work queue."));
  p.applyReviewCommentFixtureCommand("legacy");p.capture("review-comments-legacy-projection",cap("The old reviewed/approved/audit ledger remains a read-only compatibility projection, not an authority."));
 }
 private static Component cap(String text){return Component.literal("SFM Review Comments: "+text).withStyle(ChatFormatting.GOLD);}
}
