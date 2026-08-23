package ca.teamdman.sfm.client.context;

import ca.teamdman.sfm.client.action.SFMReleaseReviewAction;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewEditorCapture;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewRuntime;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewV1;
import ca.teamdman.sfm.client.screen.SFMActionChoice;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Offers ordinary approval/blocking comments for exact release-review selections. */
public final class SFMReleaseReviewContextActionProvider implements SFMContextActionProvider {
    public static final String ID = "sfm:release-review-comments";
    private static final ResourceLocation CREATE = new ResourceLocation(
            "sfm", SFMReleaseReviewAction.Kind.COMMENT_CREATE.path());

    @Override
    public List<Offer> offers(Request request) {
        var review = SFMReleaseReviewRuntime.get().document();
        if (review.isEmpty()) return List.of();
        var projection = request.focusedContribution()
                .map(SFMContextContribution::projection)
                .filter(SFMContextDocumentProjection.class::isInstance)
                .map(SFMContextDocumentProjection.class::cast);
        if (projection.isEmpty()
                || !SFMReleaseReviewEditorCapture.isPinnedReleaseReviewDocument(projection.orElseThrow())) {
            return List.of();
        }
        SFMReleaseReviewEditorCapture.Capture capture = SFMReleaseReviewEditorCapture.capture(
                request.actionContext(), request.snapshot(), review.orElseThrow());
        ArrayList<Offer> answer = new ArrayList<>();
        int rank = 0;
        for (SFMReleaseReviewV1.SelectorProposal proposal : capture.proposals().proposals()) {
            String target = label(proposal);
            String proposalArgument = StringArgumentType.escapeIfRequired(proposal.id());
            answer.add(new Offer(rank++, SFMActionChoice.invoke(
                    CREATE,
                    proposalArgument + " #approved Reviewed through the in-game release-review surface.",
                    "Approve · " + target
            )));
            answer.add(new Offer(100 + rank++, SFMActionChoice.invoke(
                    CREATE,
                    proposalArgument + " #needs-change Requires follow-up from the in-game release-review surface.",
                    "Needs change · " + target
            )));
        }
        return List.copyOf(answer);
    }

    private static String label(SFMReleaseReviewV1.SelectorProposal proposal) {
        String kind = proposal.kind() == SFMReleaseReviewV1.SelectorKind.LITERAL
                ? "exact selected bytes"
                : proposal.kind().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return proposal.semanticKey().map(value -> kind + " · " + value).orElse(kind);
    }
}
