package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.SFMSelectionPathResolution;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1Kernel;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentPosition;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSelection;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure RCS-1 boundary between mutable editor/explorer selections and immutable
 * release-review witnesses.
 *
 * <p>The current editor panel intentionally does not publish its exact cursor
 * list and the named-selection repository stores paths rather than text
 * ranges.  {@link EditorSelectionSource} and {@link SharedSelectionProjector}
 * make those two missing integration seams explicit without coupling this
 * adapter to either UI implementation.</p>
 */
public final class SFMReleaseReviewSelectionAdapter {
    private SFMReleaseReviewSelectionAdapter() {
    }

    /** Immutable bytes and provenance for one addressed document revision. */
    public record DocumentWitness(
            String documentRevisionId,
            String selectionRevision,
            String sourceAddress,
            String text,
            String sha256
    ) {
        public DocumentWitness {
            documentRevisionId = requireText(documentRevisionId, "document revision id");
            selectionRevision = requireText(selectionRevision, "selection revision");
            sourceAddress = requireText(sourceAddress, "source address");
            text = Objects.requireNonNull(text, "text");
            requireWellFormedUnicode(text);
            sha256 = requireSha256(sha256, "document SHA-256");
            String actual = digestSha256(text.getBytes(StandardCharsets.UTF_8));
            if (!actual.equals(sha256)) {
                throw new IllegalArgumentException("Document SHA-256 is stale for " + documentRevisionId);
            }
        }

        public static DocumentWitness capture(
                String documentRevisionId,
                String selectionRevision,
                String sourceAddress,
                String text
        ) {
            Objects.requireNonNull(text, "text");
            return new DocumentWitness(
                    documentRevisionId,
                    selectionRevision,
                    sourceAddress,
                    text,
                    digestSha256(text.getBytes(StandardCharsets.UTF_8))
            );
        }

        /** Creates a witness from a ready editor snapshot without making it writable. */
        public static DocumentWitness fromSnapshot(
                String documentRevisionId,
                String selectionRevision,
                String sourceAddress,
                SFMTextDocumentSnapshot snapshot
        ) {
            Objects.requireNonNull(snapshot, "snapshot");
            String text = snapshot.displayText();
            String hash = snapshot.sha256().orElseGet(
                    () -> digestSha256(text.getBytes(StandardCharsets.UTF_8))
            );
            return new DocumentWitness(documentRevisionId, selectionRevision, sourceAddress, text, hash);
        }
    }

    /** Exact editor selections for one document, in user-visible order. */
    public record OrderedDocumentSelection(
            DocumentWitness document,
            List<SFMTextDocumentSelection> selections
    ) {
        public OrderedDocumentSelection {
            Objects.requireNonNull(document, "document");
            selections = List.copyOf(Objects.requireNonNull(selections, "selections"));
            if (selections.isEmpty()) {
                throw new IllegalArgumentException("An ordered document selection requires at least one range");
            }
            selections.forEach(selection -> selection.validateAgainst(document.text()));
        }
    }

    /** Complete, generation-consistent input captured from an editor or shared selection. */
    public record SelectionCapture(
            String selectionRevision,
            String sourceExpression,
            List<OrderedDocumentSelection> documents
    ) {
        public SelectionCapture {
            selectionRevision = requireText(selectionRevision, "selection revision");
            sourceExpression = requireText(sourceExpression, "selection source expression");
            documents = List.copyOf(Objects.requireNonNull(documents, "documents"));
            if (documents.isEmpty()) throw new IllegalArgumentException("A selection capture requires a document");

            HashSet<String> documentIds = new HashSet<>();
            HashSet<String> selectionIds = new HashSet<>();
            int primaryCount = 0;
            for (OrderedDocumentSelection document : documents) {
                if (!selectionRevision.equals(document.document().selectionRevision())) {
                    throw new IllegalArgumentException(
                            "Document " + document.document().documentRevisionId()
                                    + " was resolved at a different selection revision"
                    );
                }
                if (!documentIds.add(document.document().documentRevisionId())) {
                    throw new IllegalArgumentException(
                            "Duplicate document revision " + document.document().documentRevisionId()
                    );
                }
                for (SFMTextDocumentSelection selection : document.selections()) {
                    if (!selectionIds.add(selection.id())) {
                        throw new IllegalArgumentException("Duplicate selection id " + selection.id());
                    }
                    if (selection.primary()) primaryCount++;
                }
            }
            if (primaryCount != 1) {
                throw new IllegalArgumentException("A selection capture requires exactly one primary range");
            }
        }
    }

    /** Typed seam for the exact cursor list that is currently private to EditorV3. */
    public interface EditorSelectionSource {
        String selectionRevision();

        String sourceExpression();

        List<OrderedDocumentSelection> orderedDocumentSelections();
    }

    /**
     * Immutable context derived from one resolved named-selection revision.
     * The projector supplies ranges because the repository currently owns only
     * its ordered canonical member paths.
     */
    public record SharedSelectionContext(
            String selectionRevision,
            String sourceExpression,
            long repositoryGeneration,
            List<SFMPath> memberPaths
    ) {
        public SharedSelectionContext {
            selectionRevision = requireText(selectionRevision, "shared selection revision");
            sourceExpression = requireText(sourceExpression, "shared selection source expression");
            if (repositoryGeneration < 0) {
                throw new IllegalArgumentException("Repository generation must not be negative");
            }
            memberPaths = List.copyOf(Objects.requireNonNull(memberPaths, "memberPaths"));
        }
    }

    @FunctionalInterface
    public interface SharedSelectionProjector {
        List<OrderedDocumentSelection> project(SharedSelectionContext context) throws Exception;
    }

    @FunctionalInterface
    public interface DocumentResolver {
        Optional<DocumentWitness> resolve(String documentRevisionId);
    }

    public record AdaptedSelection(
            SFMReleaseReviewV1.PinnedSelection pinnedSelection,
            SFMReviewSessionV1.SelectionRule literalRule
    ) {
        public AdaptedSelection {
            Objects.requireNonNull(pinnedSelection, "pinnedSelection");
            Objects.requireNonNull(literalRule, "literalRule");
        }
    }

    public record ReadOnlyRangeView(
            DocumentWitness document,
            SFMTextDocumentSelection selection
    ) {
        public ReadOnlyRangeView {
            Objects.requireNonNull(document, "document");
            Objects.requireNonNull(selection, "selection");
        }
    }

    /** A detached projection; it deliberately exposes no mutation capability. */
    public record ReadOnlySelectionView(
            String selectionRevision,
            String sourceExpression,
            int primaryRangeIndex,
            List<ReadOnlyRangeView> ranges
    ) {
        public ReadOnlySelectionView {
            selectionRevision = requireText(selectionRevision, "selection revision");
            sourceExpression = requireText(sourceExpression, "source expression");
            ranges = List.copyOf(Objects.requireNonNull(ranges, "ranges"));
            if (primaryRangeIndex < 0 || primaryRangeIndex >= ranges.size()) {
                throw new IllegalArgumentException("Primary range index is outside the read-only view");
            }
        }

        public boolean readOnly() {
            return true;
        }
    }

    public static SelectionCapture capture(EditorSelectionSource source) {
        Objects.requireNonNull(source, "source");
        return new SelectionCapture(
                source.selectionRevision(),
                source.sourceExpression(),
                source.orderedDocumentSelections()
        );
    }

    public static SelectionCapture capture(
            SFMSelectionPathResolution resolution,
            SharedSelectionProjector projector
    ) {
        Objects.requireNonNull(resolution, "resolution");
        Objects.requireNonNull(projector, "projector");
        if (!resolution.complete()) {
            throw new IllegalArgumentException("Shared selection resolution is not complete: " + resolution.diagnostics());
        }
        String selectionId = resolution.selectionId().orElseThrow(
                () -> new IllegalArgumentException("Complete shared selection has no selection id")
        ).value();
        long revisionId = resolution.revisionId().orElseThrow(
                () -> new IllegalArgumentException("Complete shared selection has no revision id")
        );
        String revision = selectionId + "@revision-" + revisionId;
        SFMPath pinnedPath = new SFMPath(
                SFMPath.Kind.SELECTION,
                "selection",
                selectionId,
                List.of(),
                Optional.of("revision-" + revisionId),
                false
        );
        SharedSelectionContext context = new SharedSelectionContext(
                revision,
                pinnedPath.canonical(),
                resolution.repositoryGeneration(),
                List.copyOf(resolution.members())
        );
        try {
            return new SelectionCapture(revision, context.sourceExpression(), projector.project(context));
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Shared selection projection failed", exception);
        }
    }

    public static AdaptedSelection adapt(SelectionCapture capture) {
        Objects.requireNonNull(capture, "capture");
        ArrayList<SFMReleaseReviewV1.PinnedSelectionRange> ranges = new ArrayList<>();
        int primaryRangeIndex = -1;
        for (OrderedDocumentSelection documentSelection : capture.documents()) {
            DocumentWitness document = documentSelection.document();
            for (SFMTextDocumentSelection selection : documentSelection.selections()) {
                SFMTextDocumentRange ordered = selection.orderedRange();
                SFMReleaseReviewV1.SelectionDirection direction =
                        selection.anchor().byteOffset() <= selection.active().byteOffset()
                                ? SFMReleaseReviewV1.SelectionDirection.FORWARD
                                : SFMReleaseReviewV1.SelectionDirection.BACKWARD;
                if (selection.primary()) primaryRangeIndex = ranges.size();
                ranges.add(new SFMReleaseReviewV1.PinnedSelectionRange(
                        direction,
                        document.documentRevisionId(),
                        document.sha256(),
                        ordered.start().byteOffset(),
                        ordered.end().byteOffset()
                ));
            }
        }
        SFMReleaseReviewV1.PinnedSelection pinned = new SFMReleaseReviewV1.PinnedSelection(
                capture.selectionRevision(),
                capture.sourceExpression(),
                primaryRangeIndex,
                ranges
        );
        return new AdaptedSelection(pinned, literalRule(pinned, resolver(capture)));
    }

    public static ReadOnlySelectionView reverseProject(
            SFMReleaseReviewV1.PinnedSelection pinned,
            DocumentResolver documents
    ) {
        Objects.requireNonNull(pinned, "pinned");
        Objects.requireNonNull(documents, "documents");
        ArrayList<ReadOnlyRangeView> ranges = new ArrayList<>();
        for (int index = 0; index < pinned.ranges().size(); index++) {
            SFMReleaseReviewV1.PinnedSelectionRange range = pinned.ranges().get(index);
            DocumentWitness document = requireDocument(pinned, range, documents);
            SFMTextDocumentPosition start = SFMTextDocumentRange.positionAtByteOffset(
                    document.text(), range.startByte());
            SFMTextDocumentPosition end = SFMTextDocumentRange.positionAtByteOffset(
                    document.text(), range.endByte());
            boolean primary = index == pinned.primaryRangeIndex();
            SFMTextDocumentSelection selection = range.direction() == SFMReleaseReviewV1.SelectionDirection.FORWARD
                    ? new SFMTextDocumentSelection("release-review-range-" + index, start, end, primary)
                    : new SFMTextDocumentSelection("release-review-range-" + index, end, start, primary);
            ranges.add(new ReadOnlyRangeView(document, selection));
        }
        return new ReadOnlySelectionView(
                pinned.selectionRevision(),
                pinned.sourceExpression(),
                pinned.primaryRangeIndex(),
                ranges
        );
    }

    public static SFMReviewSessionV1.SelectionRule literalRule(
            SFMReleaseReviewV1.PinnedSelection pinned,
            DocumentResolver documents
    ) {
        Objects.requireNonNull(pinned, "pinned");
        Objects.requireNonNull(documents, "documents");
        ArrayList<SFMReviewSessionV1.SelectionRule> rules = new ArrayList<>();
        for (SFMReleaseReviewV1.PinnedSelectionRange range : pinned.ranges()) {
            DocumentWitness document = requireDocument(pinned, range, documents);
            byte[] bytes = document.text().getBytes(StandardCharsets.UTF_8);
            // Boundary validation is intentionally separate from byte slicing.
            SFMTextDocumentRange.positionAtByteOffset(document.text(), range.startByte());
            SFMTextDocumentRange.positionAtByteOffset(document.text(), range.endByte());
            rules.add(new SFMReviewSessionV1.LiteralUtf8Range(
                    range.documentRevisionId(),
                    range.startByte(),
                    range.endByte(),
                    range.documentSha256(),
                    digestSha256(Arrays.copyOfRange(bytes, range.startByte(), range.endByte()))
            ));
        }
        return rules.size() == 1 ? rules.get(0) : new SFMReviewSessionV1.Union(rules);
    }

    public static DocumentResolver resolver(SelectionCapture capture) {
        Objects.requireNonNull(capture, "capture");
        return resolver(capture.documents().stream().map(OrderedDocumentSelection::document).toList());
    }

    public static DocumentResolver resolver(Collection<DocumentWitness> documents) {
        Objects.requireNonNull(documents, "documents");
        Map<String, DocumentWitness> indexed = new HashMap<>();
        for (DocumentWitness document : documents) {
            Objects.requireNonNull(document, "document");
            if (indexed.putIfAbsent(document.documentRevisionId(), document) != null) {
                throw new IllegalArgumentException("Duplicate document revision " + document.documentRevisionId());
            }
        }
        Map<String, DocumentWitness> immutable = Map.copyOf(indexed);
        return id -> Optional.ofNullable(immutable.get(id));
    }

    private static DocumentWitness requireDocument(
            SFMReleaseReviewV1.PinnedSelection pinned,
            SFMReleaseReviewV1.PinnedSelectionRange range,
            DocumentResolver documents
    ) {
        DocumentWitness document = documents.resolve(range.documentRevisionId()).orElseThrow(
                () -> new IllegalArgumentException("Document revision is unavailable: " + range.documentRevisionId())
        );
        if (!document.documentRevisionId().equals(range.documentRevisionId())) {
            throw new IllegalArgumentException("Document resolver returned a different revision identity");
        }
        if (!document.selectionRevision().equals(pinned.selectionRevision())) {
            throw new IllegalArgumentException("Document was resolved at a stale selection revision");
        }
        if (!document.sha256().equals(range.documentSha256())) {
            throw new IllegalArgumentException("Document content hash changed after the selection was pinned");
        }
        return document;
    }

    private static String digestSha256(byte[] bytes) {
        return SFMReviewSessionV1Kernel.sha256(bytes);
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
        return value;
    }

    private static String requireSha256(String value, String label) {
        value = requireText(value, label);
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(label + " must be lowercase SHA-256");
        }
        return value;
    }

    private static void requireWellFormedUnicode(String text) {
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (Character.isHighSurrogate(value)) {
                if (index + 1 >= text.length() || !Character.isLowSurrogate(text.charAt(index + 1))) {
                    throw new IllegalArgumentException("Document text contains an unpaired high surrogate");
                }
                index++;
            } else if (Character.isLowSurrogate(value)) {
                throw new IllegalArgumentException("Document text contains an unpaired low surrogate");
            }
        }
    }
}
