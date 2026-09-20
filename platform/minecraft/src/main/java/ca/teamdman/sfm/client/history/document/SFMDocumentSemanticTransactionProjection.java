package ca.teamdman.sfm.client.history.document;

import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.DocumentHeadMovement;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.DocumentMutation;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.DocumentRevision;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.GroupingPolicy;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.MutationKind;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.MutationStatus;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.RawEventKind;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.RawInputEvent;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.SemanticTransaction;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.SemanticTransactionKind;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.SessionIdentity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Pure, recomputable raw-mutation to semantic-transaction projection. */
public final class SFMDocumentSemanticTransactionProjection {
    private SFMDocumentSemanticTransactionProjection() {
    }

    public static List<SemanticTransaction> project(
            SessionIdentity identity,
            GroupingPolicy policy,
            List<DocumentRevision> revisions,
            List<RawInputEvent> rawEvents,
            List<DocumentMutation> mutations,
            List<DocumentHeadMovement> headMovements
    ) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(policy, "policy");
        Map<String, DocumentRevision> revisionIndex = new HashMap<>();
        revisions.forEach(revision -> revisionIndex.put(revision.id(), revision));
        List<RawInputEvent> orderedRaw = rawEvents.stream()
                .sorted(Comparator.comparingLong(RawInputEvent::sequence))
                .toList();
        List<DocumentHeadMovement> orderedMovements = headMovements.stream()
                .sorted(Comparator.comparingLong(DocumentHeadMovement::sequence))
                .toList();
        List<DocumentMutation> orderedMutations = mutations.stream()
                .sorted(Comparator.comparingLong(DocumentMutation::sequence))
                .toList();

        ArrayList<SemanticTransaction> answer = new ArrayList<>();
        TransactionBuilder open = null;
        for (DocumentMutation mutation : orderedMutations) {
            SemanticTransactionKind kind = semanticKind(mutation);
            if (open == null || !canMerge(
                    open,
                    mutation,
                    kind,
                    policy,
                    revisionIndex,
                    orderedRaw,
                    orderedMovements
            )) {
                if (open != null) answer.add(open.build(identity, policy));
                open = new TransactionBuilder(mutation, kind, revisionIndex);
            } else {
                open.add(mutation, revisionIndex);
            }
        }
        if (open != null) answer.add(open.build(identity, policy));
        return List.copyOf(answer);
    }

    private static boolean canMerge(
            TransactionBuilder open,
            DocumentMutation next,
            SemanticTransactionKind nextKind,
            GroupingPolicy policy,
            Map<String, DocumentRevision> revisions,
            List<RawInputEvent> rawEvents,
            List<DocumentHeadMovement> headMovements
    ) {
        if (open.kind != nextKind) return false;
        if (next.status() != MutationStatus.APPLIED || open.last.status() != MutationStatus.APPLIED) return false;
        boolean groupable = nextKind == SemanticTransactionKind.TYPE_WORD && policy.groupWordTyping()
                || (nextKind == SemanticTransactionKind.DELETE_BACKWARD
                        || nextKind == SemanticTransactionKind.DELETE_FORWARD)
                        && policy.groupDeletionRuns();
        if (!groupable) return false;
        if (!open.last.afterRevisionId().equals(next.beforeRevisionId())) return false;
        if (open.last.direction() != next.direction()) return false;
        if (!open.last.provenance().focusId().equals(next.provenance().focusId())) return false;
        long tickDelta = next.provenance().clientTick() - open.last.provenance().clientTick();
        if (tickDelta < 0 || tickDelta > policy.idleBoundTicks()) return false;
        DocumentRevision nextBefore = requireRevision(revisions, next.beforeRevisionId());
        if (!open.selectionTopologyHash.equals(nextBefore.state().selectionTopologyHash())) return false;
        if (hasHeadMovementBetween(open.last.sequence(), next.sequence(), headMovements)) return false;
        return !hasBoundaryEventBetween(open.last.sequence(), next.sequence(), rawEvents);
    }

    private static boolean hasHeadMovementBetween(
            long lowerExclusive,
            long upperExclusive,
            List<DocumentHeadMovement> movements
    ) {
        return movements.stream().anyMatch(value ->
                value.sequence() > lowerExclusive && value.sequence() < upperExclusive);
    }

    private static boolean hasBoundaryEventBetween(
            long lowerExclusive,
            long upperExclusive,
            List<RawInputEvent> events
    ) {
        return events.stream().anyMatch(value -> value.sequence() > lowerExclusive
                && value.sequence() < upperExclusive
                && switch (value.input().kind()) {
                    case KEY_RESET, PASTE, COMPLETION, FOCUS, ACTION -> true;
                    case KEY_DOWN, KEY_REPEAT, KEY_UP, CHARACTER, POINTER -> false;
                });
    }

    private static SemanticTransactionKind semanticKind(DocumentMutation mutation) {
        return switch (mutation.kind()) {
            case TYPE -> isWordText(mutation.changedText().orElse(""))
                    ? SemanticTransactionKind.TYPE_WORD
                    : SemanticTransactionKind.TYPE_DELIMITER;
            case DELETE_BACKWARD -> SemanticTransactionKind.DELETE_BACKWARD;
            case DELETE_FORWARD -> SemanticTransactionKind.DELETE_FORWARD;
            case PASTE -> SemanticTransactionKind.PASTE;
            case COMPLETION -> SemanticTransactionKind.COMPLETION;
            case CARET_CHANGE -> SemanticTransactionKind.CARET_CHANGE;
            case SELECTION_CHANGE -> SemanticTransactionKind.SELECTION_CHANGE;
            case FOCUS_CHANGE -> SemanticTransactionKind.FOCUS_CHANGE;
            case ACTION -> SemanticTransactionKind.ACTION;
            case OTHER -> SemanticTransactionKind.OTHER;
            case NO_OP -> SemanticTransactionKind.NO_OP;
        };
    }

    private static boolean isWordText(String value) {
        if (value.isEmpty()) return false;
        return value.codePoints().allMatch(codePoint -> {
            int type = Character.getType(codePoint);
            return Character.isLetterOrDigit(codePoint)
                    || codePoint == '_'
                    || type == Character.NON_SPACING_MARK
                    || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK;
        });
    }

    private static DocumentRevision requireRevision(Map<String, DocumentRevision> revisions, String id) {
        DocumentRevision revision = revisions.get(id);
        if (revision == null) throw new IllegalArgumentException("Unknown document revision " + id);
        return revision;
    }

    private static final class TransactionBuilder {
        private final SemanticTransactionKind kind;
        private final long sequence;
        private final String beforeRevisionId;
        private final String selectionTopologyHash;
        private final ArrayList<String> mutationIds = new ArrayList<>();
        private final LinkedHashSet<String> rawEventIds = new LinkedHashSet<>();
        private final ArrayList<String> stateRevisionIds = new ArrayList<>();
        private final StringBuilder changedText = new StringBuilder();
        private DocumentMutation last;

        private TransactionBuilder(
                DocumentMutation first,
                SemanticTransactionKind kind,
                Map<String, DocumentRevision> revisions
        ) {
            this.kind = kind;
            sequence = first.sequence();
            beforeRevisionId = first.beforeRevisionId();
            selectionTopologyHash = requireRevision(revisions, beforeRevisionId)
                    .state()
                    .selectionTopologyHash();
            stateRevisionIds.add(beforeRevisionId);
            add(first, revisions);
        }

        private void add(DocumentMutation mutation, Map<String, DocumentRevision> revisions) {
            mutationIds.add(mutation.id());
            rawEventIds.addAll(mutation.provenance().rawEventIds());
            if (!stateRevisionIds.get(stateRevisionIds.size() - 1).equals(mutation.beforeRevisionId())) {
                throw new IllegalArgumentException("Semantic transaction mutation chain is discontinuous");
            }
            if (!mutation.afterRevisionId().equals(mutation.beforeRevisionId())) {
                requireRevision(revisions, mutation.afterRevisionId());
                stateRevisionIds.add(mutation.afterRevisionId());
            }
            mutation.changedText().ifPresent(changedText::append);
            last = mutation;
        }

        private SemanticTransaction build(SessionIdentity identity, GroupingPolicy policy) {
            String afterRevisionId = stateRevisionIds.get(stateRevisionIds.size() - 1);
            String id = identity.qualify(
                    "semantic-transaction",
                    SFMDocumentHistoryContract.fingerprint(
                            policy.id(),
                            policy.revision(),
                            String.join("\u0000", mutationIds)
                    ).substring("sha256:".length())
            );
            return new SemanticTransaction(
                    id,
                    sequence,
                    policy.id(),
                    policy.revision(),
                    kind,
                    beforeRevisionId,
                    afterRevisionId,
                    mutationIds,
                    List.copyOf(rawEventIds),
                    stateRevisionIds,
                    label(kind, changedText.toString(), last)
            );
        }
    }

    private static String label(
            SemanticTransactionKind kind,
            String changedText,
            DocumentMutation last
    ) {
        return switch (kind) {
            case TYPE_WORD -> "type \"" + escaped(changedText) + "\"";
            case TYPE_DELIMITER -> delimiterLabel(changedText);
            case DELETE_BACKWARD -> "delete backward " + changedText.codePointCount(0, changedText.length());
            case DELETE_FORWARD -> "delete forward " + changedText.codePointCount(0, changedText.length());
            case PASTE -> "paste \"" + escaped(changedText) + "\"";
            case COMPLETION -> "accept completion \"" + escaped(changedText) + "\"";
            case CARET_CHANGE -> "move caret";
            case SELECTION_CHANGE -> "change selection";
            case FOCUS_CHANGE -> "change focus";
            case ACTION -> last.graphIdentity().intent().actionId();
            case OTHER -> "edit document";
            case NO_OP -> "no change";
        };
    }

    private static String delimiterLabel(String changedText) {
        return switch (changedText) {
            case " " -> "click \"<space>\"";
            case "\t" -> "click \"<tab>\"";
            case "\n", "\r", "\r\n" -> "click \"<enter>\"";
            default -> "type \"" + escaped(changedText) + "\"";
        };
    }

    private static String escaped(String value) {
        return value.replace("\\", "\\\\")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t")
                .replace("\"", "\\\"");
    }
}
