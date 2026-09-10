package ca.teamdman.sfm.client.context;

import ca.teamdman.sfm.client.action.SFMReleaseReviewCommentChoiceAction;
import ca.teamdman.sfm.client.action.SFMReleaseReviewCommentDetailsAction;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewGeneratedMarkers;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2Kernel;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewCommentDraftService;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewEditorCapture;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewRuntime;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewSelectionAdapter;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewV1;
import ca.teamdman.sfm.client.screen.SFMActionChoice;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Offers ordinary approval/blocking comments for exact release-review selections. */
public final class SFMReleaseReviewContextActionProvider implements SFMContextActionProvider {
    public static final String ID = "sfm:release-review-comments";
    private static final ResourceLocation OPEN_CHOICES = new ResourceLocation(
            "sfm", SFMReleaseReviewCommentChoiceAction.Kind.OPEN.path());

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
            String target = label(capture.documents(), proposal);
            var draft = SFMReleaseReviewCommentDraftService.get().create(capture, proposal);
            answer.add(new Offer(rank++, SFMActionChoice.invoke(
                    OPEN_CHOICES,
                    draft.id(),
                    "Comment… · " + target
            )));
        }
        for (var comment : review.orElseThrow().reviewSession().comments()) {
            if (SFMReleaseReviewGeneratedMarkers.isChangeMarker(comment)) continue;
            var evaluation = SFMReviewSessionV2Kernel.evaluateComment(review.orElseThrow().reviewSession(), comment);
            if (evaluation.status() != SFMReviewSessionV2Kernel.Status.RESOLVED_EXACTLY
                    && evaluation.status() != SFMReviewSessionV2Kernel.Status.RESOLVED_WITH_RELOCATION) continue;
            if (!overlaps(capture.adapted().pinnedSelection(), evaluation.ranges())) continue;
            for (var choice : SFMReleaseReviewCommentDetailsAction.rowChoices(
                    comment.id(), SFMReleaseReviewRuntime.get().snapshot().openEpoch())) {
                answer.add(new Offer(rank++, new SFMActionChoice(
                        choice.actionId(), choice.command(), choice.displayText() + " · " + comment.id())));
            }
        }
        var path = SFMReleaseReviewRuntime.get().path();
        if (path.isPresent()) {
            for (String document : capture.selection().documents().stream()
                    .map(value -> value.document().documentRevisionId()).distinct().toList()) {
                for (var choice : ca.teamdman.sfm.client.action.SFMReviewStorageAction.choices(
                        path.orElseThrow(), java.util.Optional.of(document))) {
                    answer.add(new Offer(rank++, choice));
                }
            }
        }
        return List.copyOf(answer);
    }

    static boolean overlaps(SFMReleaseReviewV1.PinnedSelection selection,
                            List<ca.teamdman.sfm.client.review.session.SFMReviewSessionV1Kernel.Range> ranges) {
        return selection.ranges().stream().anyMatch(selected -> ranges.stream().anyMatch(range ->
                selected.documentRevisionId().equals(range.documentRevisionId())
                        && range.startByte() < range.endByte()
                        && (selected.startByte() == selected.endByte()
                        ? selected.startByte() >= range.startByte() && selected.startByte() < range.endByte()
                        : selected.startByte() < range.endByte() && range.startByte() < selected.endByte())));
    }

    /**
     * Names what the comment will actually cover before describing why that
     * target was proposed. Definition/reference destinations are evidence;
     * they are never presented as though the comment would attach there.
     */
    static String label(
            SFMReleaseReviewSelectionAdapter.DocumentResolver documents,
            SFMReleaseReviewV1.SelectorProposal proposal
    ) {
        Objects.requireNonNull(documents, "documents");
        Objects.requireNonNull(proposal, "proposal");
        List<ca.teamdman.sfm.client.review.session.SFMReviewSessionV1.LiteralUtf8Range> ranges =
                literalRanges(proposal.selectionRule());
        String target;
        if (ranges.isEmpty()) {
            target = "target " + proposal.literalWitness().sourceExpression();
        } else {
            var first = ranges.get(0);
            String address = documents.resolve(first.documentRevisionId())
                    .map(SFMReleaseReviewSelectionAdapter.DocumentWitness::sourceAddress)
                    .orElse(first.documentRevisionId());
            target = "target " + compactAddress(address)
                    + " [" + first.startByte() + ".." + first.endByte() + ")";
            if (ranges.size() > 1) target += " +" + (ranges.size() - 1) + " range(s)";
        }

        String kind = proposal.kind() == SFMReleaseReviewV1.SelectorKind.LITERAL
                ? "exact selected bytes"
                : proposal.kind().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        String effectiveTarget = target;
        return proposal.semanticKey()
                .map(value -> effectiveTarget + " · " + kind + " evidence → " + value)
                .orElse(effectiveTarget + " · " + kind);
    }

    private static List<ca.teamdman.sfm.client.review.session.SFMReviewSessionV1.LiteralUtf8Range> literalRanges(
            ca.teamdman.sfm.client.review.session.SFMReviewSessionV1.SelectionRule rule
    ) {
        LinkedHashSet<ca.teamdman.sfm.client.review.session.SFMReviewSessionV1.LiteralUtf8Range> answer =
                new LinkedHashSet<>();
        collectLiteralRanges(rule, answer);
        return List.copyOf(answer);
    }

    private static void collectLiteralRanges(
            ca.teamdman.sfm.client.review.session.SFMReviewSessionV1.SelectionRule rule,
            LinkedHashSet<ca.teamdman.sfm.client.review.session.SFMReviewSessionV1.LiteralUtf8Range> output
    ) {
        if (rule instanceof ca.teamdman.sfm.client.review.session.SFMReviewSessionV1.LiteralUtf8Range literal) {
            output.add(literal);
        } else if (rule instanceof ca.teamdman.sfm.client.review.session.SFMReviewSessionV1.Union union) {
            union.rules().forEach(child -> collectLiteralRanges(child, output));
        } else if (rule instanceof ca.teamdman.sfm.client.review.session.SFMReviewSessionV1.Intersection intersection) {
            intersection.rules().forEach(child -> collectLiteralRanges(child, output));
        } else if (rule instanceof ca.teamdman.sfm.client.review.session.SFMReviewSessionV1.Difference difference) {
            collectLiteralRanges(difference.include(), output);
        }
    }

    private static String compactAddress(String address) {
        String normalized = address.replace('\\', '/');
        int query = normalized.indexOf('?');
        if (query >= 0) normalized = normalized.substring(0, query);
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        int separator = normalized.lastIndexOf('/');
        return separator >= 0 && separator + 1 < normalized.length()
                ? normalized.substring(separator + 1)
                : normalized;
    }
}
