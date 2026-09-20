package ca.teamdman.sfm.client.history.chamber;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable text-and-selection state used by the temporal numbering chamber.
 *
 * <p>Text coordinates are Unicode code-point offsets, never UTF-16 code-unit
 * offsets. Revision identity includes the parent revision and action ordering
 * key, while {@link #stateHash()} identifies the resulting text and selection.
 * This lets a planner merge or retain histories according to its own policy
 * without weakening the chamber's immutable revision evidence.</p>
 */
public record SFMChamberDocumentState(
        IdentityScope scope,
        String revisionId,
        Optional<String> parentRevisionId,
        String text,
        Optional<SelectionWitness> selection
) {
    public SFMChamberDocumentState {
        Objects.requireNonNull(scope, "scope");
        revisionId = requireText(revisionId, "revisionId");
        Objects.requireNonNull(parentRevisionId, "parentRevisionId");
        parentRevisionId = parentRevisionId.map(value -> requireText(value, "parentRevisionId"));
        String validatedText = requireWellFormedUtf16(text, "text");
        text = validatedText;
        Objects.requireNonNull(selection, "selection");
        selection.ifPresent(value -> value.validateAgainst(validatedText));
    }

    public static SFMChamberDocumentState root(String text) {
        return root(IdentityScope.testScope(), text);
    }

    public static SFMChamberDocumentState root(IdentityScope scope, String text) {
        Objects.requireNonNull(scope, "scope");
        text = requireWellFormedUtf16(text, "text");
        Optional<SelectionWitness> selection = Optional.empty();
        String stateHash = stateHash(text, selection);
        return new SFMChamberDocumentState(
                scope,
                revisionId(scope, "root", stateHash),
                Optional.empty(),
                text,
                selection
        );
    }

    public SFMChamberDocumentState child(
            String actionOrderingKey,
            String resultingText,
            Optional<SelectionWitness> resultingSelection
    ) {
        actionOrderingKey = requireText(actionOrderingKey, "actionOrderingKey");
        Objects.requireNonNull(resultingText, "resultingText");
        Objects.requireNonNull(resultingSelection, "resultingSelection");
        resultingText = requireWellFormedUtf16(resultingText, "resultingText");
        String resultingStateHash = stateHash(resultingText, resultingSelection);
        return new SFMChamberDocumentState(
                scope,
                revisionId(scope, "child", revisionId, actionOrderingKey, resultingStateHash),
                Optional.of(revisionId),
                resultingText,
                resultingSelection
        );
    }

    public String textHash() {
        return sha256(text);
    }

    public String stateHash() {
        return stateHash(text, selection);
    }

    public int codePointLength() {
        return text.codePointCount(0, text.length());
    }

    public boolean hasSameValue(SFMChamberDocumentState other) {
        Objects.requireNonNull(other, "other");
        return stateHash().equals(other.stateHash());
    }

    static int charIndexAtCodePoint(String value, int codePointOffset) {
        value = requireWellFormedUtf16(value, "value");
        int length = value.codePointCount(0, value.length());
        if (codePointOffset < 0 || codePointOffset > length) {
            throw new IllegalArgumentException(
                    "Code-point offset " + codePointOffset + " is outside [0," + length + "]"
            );
        }
        return value.offsetByCodePoints(0, codePointOffset);
    }

    static String sha256(String value) {
        value = requireWellFormedUtf16(value, "value");
        MessageDigest digest = newSha256();
        updatePart(digest, value);
        return "sha256:" + HexFormat.of().formatHex(digest.digest());
    }

    static String fingerprint(String... parts) {
        MessageDigest digest = newSha256();
        for (String part : parts) {
            updatePart(digest, Objects.requireNonNull(part, "fingerprint part"));
        }
        return "sha256:" + HexFormat.of().formatHex(digest.digest());
    }

    private static String stateHash(String text, Optional<SelectionWitness> selection) {
        return fingerprint(
                "sfm.chamber-document-state/1",
                text,
                selection.map(SelectionWitness::witnessHash).orElse("selection:none")
        );
    }

    private static String revisionId(IdentityScope scope, String... parts) {
        return scope.qualify("revision", fingerprint(parts).substring("sha256:".length()));
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", e);
        }
    }

    private static void updatePart(MessageDigest digest, String value) {
        requireWellFormedUtf16(value, "digest part");
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }

    private static String requireText(String value, String label) {
        value = requireWellFormedUtf16(value, label);
        if (value.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be empty");
        }
        return value;
    }

    static String requireWellFormedUtf16(String value, String label) {
        Objects.requireNonNull(value, label);
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    throw new IllegalArgumentException(label + " contains an unpaired high surrogate at UTF-16 index " + index);
                }
                index++;
            } else if (Character.isLowSurrogate(current)) {
                throw new IllegalArgumentException(label + " contains an unpaired low surrogate at UTF-16 index " + index);
            }
        }
        return value;
    }

    /** Stable authority namespace shared by every externally visible chamber identity. */
    public record IdentityScope(String episodeId, String documentId) {
        public IdentityScope {
            episodeId = requireText(episodeId, "episodeId");
            documentId = requireText(documentId, "documentId");
        }

        public String qualify(String kind, String token) {
            kind = requireText(kind, "identity kind");
            token = requireText(token, "identity token");
            return episodeId + "/document/" + documentId + "/" + kind + "/" + token;
        }

        public static IdentityScope testScope() {
            return new IdentityScope("sfm:test/temporal-numbering", "document-1");
        }
    }

    public enum OrderingPolicy {
        SOURCE_ORDER
    }

    /** An exact half-open source range measured in Unicode code points. */
    public record SourceRegion(
            int startCodePointOffset,
            int endCodePointOffset,
            int lineOneBased,
            int columnCodePointOneBased,
            String expectedText
    ) {
        public SourceRegion {
            if (startCodePointOffset < 0) {
                throw new IllegalArgumentException("startCodePointOffset must not be negative");
            }
            if (endCodePointOffset <= startCodePointOffset) {
                throw new IllegalArgumentException("Source regions must be non-empty and forward");
            }
            if (lineOneBased < 1 || columnCodePointOneBased < 1) {
                throw new IllegalArgumentException("Source positions are one-based");
            }
            expectedText = requireText(expectedText, "expectedText");
            int expectedLength = expectedText.codePointCount(0, expectedText.length());
            if (expectedLength != endCodePointOffset - startCodePointOffset) {
                throw new IllegalArgumentException("Region length must equal expectedText code-point length");
            }
        }

        public String slice(String documentText) {
            documentText = requireWellFormedUtf16(documentText, "documentText");
            int start = charIndexAtCodePoint(documentText, startCodePointOffset);
            int end = charIndexAtCodePoint(documentText, endCodePointOffset);
            return documentText.substring(start, end);
        }

        void validateAgainst(String documentText) {
            int documentLength = documentText.codePointCount(0, documentText.length());
            if (endCodePointOffset > documentLength) {
                throw new IllegalArgumentException("Region extends beyond the document");
            }
            if (!slice(documentText).equals(expectedText)) {
                throw new IllegalArgumentException("Region expected text does not match the document");
            }
            SourcePosition actual = sourcePositionAt(documentText, startCodePointOffset);
            if (actual.lineOneBased() != lineOneBased
                    || actual.columnCodePointOneBased() != columnCodePointOneBased) {
                throw new IllegalArgumentException("Region line/column does not match its code-point offset");
            }
        }

        String canonicalForm() {
            return startCodePointOffset + ":" + endCodePointOffset + ":"
                    + lineOneBased + ":" + columnCodePointOneBased + ":" + expectedText;
        }
    }

    /** A frozen selection result, including the evaluator and exact source-order witness. */
    public record SelectionWitness(
            String sourceTextHash,
            String evaluatorId,
            String evaluatorRevision,
            OrderingPolicy orderingPolicy,
            List<SourceRegion> regions
    ) {
        public SelectionWitness {
            sourceTextHash = requireText(sourceTextHash, "sourceTextHash");
            evaluatorId = requireText(evaluatorId, "evaluatorId");
            evaluatorRevision = requireText(evaluatorRevision, "evaluatorRevision");
            Objects.requireNonNull(orderingPolicy, "orderingPolicy");
            Objects.requireNonNull(regions, "regions");
            regions = List.copyOf(regions);
            if (regions.isEmpty()) {
                throw new IllegalArgumentException("A selection witness must contain at least one region");
            }
            int previousEnd = -1;
            for (SourceRegion region : regions) {
                Objects.requireNonNull(region, "selection region");
                if (region.startCodePointOffset() < previousEnd) {
                    throw new IllegalArgumentException(
                            "Selection witness regions must be non-overlapping and in source order"
                    );
                }
                previousEnd = region.endCodePointOffset();
            }
        }

        public void validateAgainst(String documentText) {
            documentText = requireWellFormedUtf16(documentText, "documentText");
            if (!sourceTextHash.equals(sha256(documentText))) {
                throw new IllegalArgumentException("Selection witness belongs to different document text");
            }
            for (SourceRegion region : regions) {
                region.validateAgainst(documentText);
            }
        }

        public String witnessHash() {
            List<String> parts = new ArrayList<>();
            parts.add("sfm.chamber-selection-witness/1");
            parts.add(sourceTextHash);
            parts.add(evaluatorId);
            parts.add(evaluatorRevision);
            parts.add(orderingPolicy.name());
            for (SourceRegion region : regions) {
                parts.add(Integer.toString(region.startCodePointOffset()));
                parts.add(Integer.toString(region.endCodePointOffset()));
                parts.add(Integer.toString(region.lineOneBased()));
                parts.add(Integer.toString(region.columnCodePointOneBased()));
                parts.add(region.expectedText());
            }
            return fingerprint(parts.toArray(String[]::new));
        }

        String canonicalForm() {
            StringBuilder answer = new StringBuilder()
                    .append(sourceTextHash).append('\n')
                    .append(evaluatorId).append('\n')
                    .append(evaluatorRevision).append('\n')
                    .append(orderingPolicy.name());
            for (SourceRegion region : regions) {
                answer.append('\n').append(region.canonicalForm());
            }
            return answer.toString();
        }
    }

    private record SourcePosition(int lineOneBased, int columnCodePointOneBased) {
    }

    private static SourcePosition sourcePositionAt(String text, int targetCodePointOffset) {
        int line = 1;
        int column = 1;
        int codePointOffset = 0;
        int charIndex = 0;
        boolean previousWasCarriageReturn = false;
        while (charIndex < text.length() && codePointOffset < targetCodePointOffset) {
            int codePoint = text.codePointAt(charIndex);
            if (codePoint == '\r') {
                line++;
                column = 1;
                previousWasCarriageReturn = true;
            } else if (codePoint == '\n') {
                if (!previousWasCarriageReturn) {
                    line++;
                }
                column = 1;
                previousWasCarriageReturn = false;
            } else {
                column++;
                previousWasCarriageReturn = false;
            }
            charIndex += Character.charCount(codePoint);
            codePointOffset++;
        }
        if (codePointOffset != targetCodePointOffset) {
            throw new IllegalArgumentException("Code-point offset extends beyond the document");
        }
        return new SourcePosition(line, column);
    }
}
