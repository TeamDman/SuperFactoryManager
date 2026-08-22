package ca.teamdman.sfm.client.history;

import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.DocumentState;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.EditDirection;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.MutationKind;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.MutationProvenance;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.MutationRequest;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.RawEventKind;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.RawInput;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistorySession;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Reusable live-host adapter around {@link SFMDocumentHistorySession}.
 *
 * <p>Raw events arrive before screen dispatch. The next resulting document
 * mutation drains the corresponding event ids. A consumed undo/redo shortcut
 * is consequently preserved even though the widget never receives it.</p>
 */
public final class SFMDocumentHistoryHostController {
    private final SFMDocumentHistorySession session;
    private final Consumer<DocumentState> checkout;
    private final String actor;
    private final String focusId;
    private final ArrayList<String> pendingRawEventIds = new ArrayList<>();
    private long rawOrdinal;
    private long mutationOrdinal;
    private long operationOrdinal;
    private boolean checkoutInProgress;
    private boolean closed;

    public SFMDocumentHistoryHostController(
            SFMDocumentHistorySession session,
            String actor,
            String focusId,
            Consumer<DocumentState> checkout
    ) {
        this.session = Objects.requireNonNull(session, "session");
        this.actor = requireText(actor, "actor");
        this.focusId = requireText(focusId, "focusId");
        this.checkout = Objects.requireNonNull(checkout, "checkout");
    }

    public SFMDocumentHistorySession session() {
        return session;
    }

    public boolean checkoutInProgress() {
        return checkoutInProgress;
    }

    public void recordRawInput(
            long clientTick,
            RawEventKind kind,
            String source,
            String code,
            Optional<String> text,
            int modifiers,
            boolean consumed,
            boolean delivered
    ) {
        ensureOpen();
        String id = session.identity().qualify("raw-input", Long.toString(++rawOrdinal));
        session.appendRawInput(new RawInput(
                id,
                clientTick,
                Objects.requireNonNull(kind, "kind"),
                requireText(source, "source"),
                requireText(code, "code"),
                Objects.requireNonNull(text, "text"),
                modifiers,
                consumed,
                delivered
        ));
        pendingRawEventIds.add(id);
    }

    /**
     * Appends one explicit host mutation. Programmatic checkout is suppressed
     * and equal before/after states remain raw evidence rather than fabricated
     * document revisions.
     */
    public SFMDocumentHistorySession.AppendResult observeMutation(
            MutationKind kind,
            EditDirection direction,
            DocumentState before,
            DocumentState after,
            Optional<String> changedText,
            long clientTick,
            String requestCause
    ) {
        ensureOpen();
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        Objects.requireNonNull(changedText, "changedText");
        if (checkoutInProgress) {
            throw new IllegalStateException("A programmatic history checkout cannot append another mutation");
        }
        if (!session.currentState().equals(before)) {
            throw new IllegalStateException("Document host diverged from its immutable history head");
        }
        String mutationId = session.identity().qualify("mutation", Long.toString(++mutationOrdinal));
        List<String> rawIds = drainPendingRawEventIds();
        return session.append(new MutationRequest(
                mutationId,
                Objects.requireNonNull(kind, "kind"),
                Objects.requireNonNull(direction, "direction"),
                after,
                Optional.empty(),
                Optional.empty(),
                changedText,
                new MutationProvenance(
                        actor,
                        requireText(requestCause, "requestCause") + "-" + mutationOrdinal,
                        clientTick,
                        focusId,
                        rawIds
                ),
                Optional.empty()
        ));
    }

    public SFMHistoryGraphRuntime.OperationResult undo(String requestCause) {
        ensureOpen();
        SFMDocumentHistorySession.HeadMoveResult result = session.undo(
                actor,
                operationRequest(requestCause),
                drainPendingRawEventIds()
        );
        return applyHeadMove(result);
    }

    public SFMHistoryGraphRuntime.OperationResult redo(
            Optional<String> childRevisionId,
            String requestCause
    ) {
        ensureOpen();
        List<SFMDocumentHistoryContract.DocumentRevision> candidates = session.eligibleRedoChildren();
        if (childRevisionId.isEmpty() && candidates.size() > 1) {
            // The preferred child remains useful evidence, but ordinary UI
            // must expose every retained sibling instead of silently choosing.
            return SFMHistoryGraphRuntime.OperationResult.noChange(
                    "More than one redo child is eligible: "
                            + candidates.stream()
                                    .map(SFMDocumentHistoryContract.DocumentRevision::id)
                                    .sorted()
                                    .collect(java.util.stream.Collectors.joining(", "))
            );
        }
        SFMDocumentHistorySession.HeadMoveResult result = session.redo(
                Objects.requireNonNull(childRevisionId, "childRevisionId"),
                actor,
                operationRequest(requestCause),
                drainPendingRawEventIds()
        );
        return applyHeadMove(result);
    }

    public SFMHistoryGraphRuntime.OperationResult checkout(
            String revisionId,
            String requestCause
    ) {
        ensureOpen();
        SFMDocumentHistorySession.HeadMoveResult result = session.checkout(
                requireText(revisionId, "revisionId"),
                actor,
                operationRequest(requestCause),
                drainPendingRawEventIds()
        );
        return applyHeadMove(result);
    }

    public List<String> pendingRawEventIds() {
        return List.copyOf(pendingRawEventIds);
    }

    public void close() {
        pendingRawEventIds.clear();
        closed = true;
    }

    private SFMHistoryGraphRuntime.OperationResult applyHeadMove(
            SFMDocumentHistorySession.HeadMoveResult result
    ) {
        if (result.status() == SFMDocumentHistorySession.HeadMoveStatus.APPLIED) {
            checkoutInProgress = true;
            try {
                checkout.accept(session.currentState());
            } finally {
                checkoutInProgress = false;
            }
            return SFMHistoryGraphRuntime.OperationResult.applied(result.message());
        }
        if (result.status() == SFMDocumentHistorySession.HeadMoveStatus.NO_CHANGE
                || result.status() == SFMDocumentHistorySession.HeadMoveStatus.AMBIGUOUS) {
            return SFMHistoryGraphRuntime.OperationResult.noChange(result.message());
        }
        return SFMHistoryGraphRuntime.OperationResult.rejected(result.message());
    }

    private List<String> drainPendingRawEventIds() {
        List<String> result = List.copyOf(pendingRawEventIds);
        pendingRawEventIds.clear();
        return result;
    }

    private String operationRequest(String requestCause) {
        return requireText(requestCause, "requestCause") + "-" + (++operationOrdinal);
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Document history host is closed");
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
        return value;
    }
}
