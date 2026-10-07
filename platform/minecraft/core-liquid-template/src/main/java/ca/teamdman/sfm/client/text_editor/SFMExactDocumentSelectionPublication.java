package ca.teamdman.sfm.client.text_editor;

import ca.teamdman.sfm.client.context.SFMContextTextCoordinates;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Atomic immutable evidence for exact selections in one document revision.
 *
 * <p>The coordinate text is deliberately retained. A canvas projection may
 * omit non-glyph content such as the empty line after a trailing newline, so a
 * range must never be rendered against text reconstructed from glyphs.</p>
 */
public final class SFMExactDocumentSelectionPublication {
    public enum DiagnosticCode {
        MALFORMED_PUBLICATION,
        DOCUMENT_ADDRESS_MISMATCH,
        DOCUMENT_GENERATION_MISMATCH,
        MODEL_CONTENT_REVISION_MISMATCH,
        CURSOR_FINGERPRINT_MISMATCH,
        CONTENT_HASH_MISMATCH
    }

    /** Identity of the complete editor/model state against which ranges were published. */
    public record Identity(
            String documentAddress,
            String contentSha256,
            long documentGeneration,
            long modelContentRevision,
            long cursorFingerprint
    ) {
        public Identity {
            documentAddress = requireText(documentAddress, "documentAddress");
            contentSha256 = requireText(contentSha256, "contentSha256");
            if (documentGeneration < 0L) {
                throw new IllegalArgumentException("documentGeneration must not be negative");
            }
            if (modelContentRevision < 0L) {
                throw new IllegalArgumentException("modelContentRevision must not be negative");
            }
        }

        public static Identity forText(
                String documentAddress,
                String coordinateText,
                long documentGeneration,
                long modelContentRevision,
                long cursorFingerprint
        ) {
            Objects.requireNonNull(coordinateText, "coordinateText");
            return new Identity(
                    documentAddress,
                    SFMContextTextCoordinates.sha256(coordinateText),
                    documentGeneration,
                    modelContentRevision,
                    cursorFingerprint
            );
        }
    }

    public record Diagnostic(DiagnosticCode code, String message) {
        public Diagnostic {
            Objects.requireNonNull(code, "code");
            message = requireText(message, "message");
        }
    }

    /**
     * One atomic state slot. It is empty, contains a drawable publication, or
     * retains the diagnostic that caused the publication to fail closed.
     */
    public record State(
            Optional<SFMExactDocumentSelectionPublication> publication,
            Optional<Diagnostic> diagnostic
    ) {
        public State {
            publication = Objects.requireNonNull(publication, "publication");
            diagnostic = Objects.requireNonNull(diagnostic, "diagnostic");
            if (publication.isPresent() && diagnostic.isPresent()) {
                throw new IllegalArgumentException("A selection state cannot be both published and suspended");
            }
        }

        public static State empty() {
            return new State(Optional.empty(), Optional.empty());
        }

        /** Total publication boundary: malformed input becomes retained diagnostic evidence. */
        public static State publish(
                Identity identity,
                String coordinateText,
                List<SFMTextDocumentSelection> selections
        ) {
            try {
                return new State(
                        Optional.of(new SFMExactDocumentSelectionPublication(
                                identity,
                                coordinateText,
                                selections
                        )),
                        Optional.empty()
                );
            } catch (RuntimeException malformed) {
                return suspended(
                        DiagnosticCode.MALFORMED_PUBLICATION,
                        "Exact document selection publication is malformed: " + failureMessage(malformed)
                );
            }
        }

        /** Resolve against the current editor state without ever returning stale ranges. */
        public State resolve(Identity currentIdentity) {
            Objects.requireNonNull(currentIdentity, "currentIdentity");
            if (publication.isEmpty()) return this;
            Optional<Diagnostic> mismatch = publication.orElseThrow().mismatch(currentIdentity);
            return mismatch.map(State::suspended).orElse(this);
        }

        public State suspend(DiagnosticCode code, String message) {
            return suspended(code, message);
        }

        private static State suspended(Diagnostic diagnostic) {
            return new State(Optional.empty(), Optional.of(diagnostic));
        }

        private static State suspended(DiagnosticCode code, String message) {
            return suspended(new Diagnostic(code, message));
        }
    }

    private final Identity identity;
    private final String coordinateText;
    private final List<SFMTextDocumentSelection> selections;

    private SFMExactDocumentSelectionPublication(
            Identity identity,
            String coordinateText,
            List<SFMTextDocumentSelection> selections
    ) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.coordinateText = Objects.requireNonNull(coordinateText, "coordinateText");
        this.selections = List.copyOf(Objects.requireNonNull(selections, "selections"));
        String actualHash = SFMContextTextCoordinates.sha256(coordinateText);
        if (!identity.contentSha256().equals(actualHash)) {
            throw new IllegalArgumentException(
                    "coordinate text hash does not match publication identity"
            );
        }
        SFMTextDocumentSelection.validateAllAgainst(coordinateText, this.selections);
    }

    public Identity identity() {
        return identity;
    }

    public String coordinateText() {
        return coordinateText;
    }

    public List<SFMTextDocumentSelection> selections() {
        return selections;
    }

    private Optional<Diagnostic> mismatch(Identity current) {
        if (!identity.documentAddress().equals(current.documentAddress())) {
            return Optional.of(new Diagnostic(
                    DiagnosticCode.DOCUMENT_ADDRESS_MISMATCH,
                    "Exact document selection was suspended because the document address changed"
            ));
        }
        if (identity.documentGeneration() != current.documentGeneration()) {
            return Optional.of(new Diagnostic(
                    DiagnosticCode.DOCUMENT_GENERATION_MISMATCH,
                    "Exact document selection was suspended because the document generation changed from "
                            + identity.documentGeneration() + " to " + current.documentGeneration()
            ));
        }
        if (identity.modelContentRevision() != current.modelContentRevision()) {
            return Optional.of(new Diagnostic(
                    DiagnosticCode.MODEL_CONTENT_REVISION_MISMATCH,
                    "Exact document selection was suspended because the model content revision changed from "
                            + identity.modelContentRevision() + " to " + current.modelContentRevision()
            ));
        }
        if (identity.cursorFingerprint() != current.cursorFingerprint()) {
            return Optional.of(new Diagnostic(
                    DiagnosticCode.CURSOR_FINGERPRINT_MISMATCH,
                    "Exact document selection was suspended because the cursor state changed"
            ));
        }
        if (!identity.contentSha256().equals(current.contentSha256())) {
            return Optional.of(new Diagnostic(
                    DiagnosticCode.CONTENT_HASH_MISMATCH,
                    "Exact document selection was suspended because the coordinate text hash changed"
            ));
        }
        return Optional.empty();
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
        return value;
    }

    private static String failureMessage(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
