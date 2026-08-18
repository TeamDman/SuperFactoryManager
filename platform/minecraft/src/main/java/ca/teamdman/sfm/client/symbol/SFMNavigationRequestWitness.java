package ca.teamdman.sfm.client.symbol;

import ca.teamdman.sfm.client.context.SFMContextContribution;
import ca.teamdman.sfm.client.context.SFMContextCursorProjection;
import ca.teamdman.sfm.client.context.SFMContextDocumentProjection;
import ca.teamdman.sfm.client.context.SFMContextOriginId;
import ca.teamdman.sfm.client.context.SFMContextPosition;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable request identity for asynchronous source navigation.
 *
 * <p>The request position is retained as evidence of what was asked, but is
 * intentionally not compared with the editor's later cursor, selection, hover,
 * focus, or capture generations. Revalidation is limited to the workspace and
 * panel entry that own the request, document resolver/address and exact bytes,
 * semantic-provider generation, and explicit request supersession.</p>
 */
record SFMNavigationRequestWitness(
        Object workspaceIdentity,
        SFMWorkspacePanelId sourcePanelId,
        Object panelEntryIdentity,
        SFMContextOriginId originId,
        DocumentIdentity document,
        SemanticRequest semanticRequest,
        long requestGeneration
) {
    enum RejectionReason {
        SUPERSEDED_REQUEST("a newer explicit request superseded this request"),
        WORKSPACE_REPLACED("the originating workspace instance was replaced"),
        WORKSPACE_NOT_CURRENT("the originating workspace is no longer current"),
        PANEL_REMOVED("the source panel was removed"),
        PANEL_REPLACED("the source panel entry was replaced"),
        DOCUMENT_REMOVED("the source document contribution was removed"),
        DOCUMENT_REPLACED("the source panel no longer projects the captured text document"),
        DOCUMENT_STATE_CHANGED("the source document state changed"),
        DOCUMENT_ADDRESS_CHANGED("the source document resolver/address changed"),
        DOCUMENT_CONTENT_CHANGED("the source document bytes changed"),
        PROVIDER_GENERATION_CHANGED("the semantic provider generation changed");

        private final String description;

        RejectionReason(String description) {
            this.description = description;
        }

        String description() {
            return description;
        }
    }

    record Validation(Optional<RejectionReason> rejection) {
        Validation {
            rejection = Objects.requireNonNull(rejection, "rejection");
        }

        static Validation valid() {
            return new Validation(Optional.empty());
        }

        static Validation rejected(RejectionReason reason) {
            return new Validation(Optional.of(Objects.requireNonNull(reason, "reason")));
        }

        boolean isValid() {
            return rejection.isEmpty();
        }
    }

    record DocumentIdentity(
            String editorId,
            SFMTextDocumentSnapshot.State state,
            Optional<String> address,
            Optional<String> authorizedRoot,
            String contentSha256
    ) {
        DocumentIdentity {
            Objects.requireNonNull(editorId, "editorId");
            Objects.requireNonNull(state, "state");
            address = Objects.requireNonNull(address, "address");
            authorizedRoot = Objects.requireNonNull(authorizedRoot, "authorizedRoot");
            Objects.requireNonNull(contentSha256, "contentSha256");
        }
    }

    /** Exact submitted location plus the provider-neutral semantic relation generation. */
    record SemanticRequest(Optional<SFMContextPosition> position, long providerGeneration) {
        SemanticRequest {
            position = Objects.requireNonNull(position, "position");
            if (providerGeneration < 0) {
                throw new IllegalArgumentException("providerGeneration must not be negative");
            }
        }
    }

    SFMNavigationRequestWitness {
        Objects.requireNonNull(workspaceIdentity, "workspaceIdentity");
        Objects.requireNonNull(sourcePanelId, "sourcePanelId");
        Objects.requireNonNull(panelEntryIdentity, "panelEntryIdentity");
        Objects.requireNonNull(originId, "originId");
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(semanticRequest, "semanticRequest");
        if (requestGeneration <= 0) {
            throw new IllegalArgumentException("requestGeneration must be positive");
        }
    }

    static SFMNavigationRequestWitness capture(
            SFMScreenMultiplexer workspace,
            SFMWorkspacePanelId sourcePanelId,
            Object panelEntryIdentity,
            SFMContextContribution contribution,
            long requestGeneration
    ) {
        if (!(contribution.projection() instanceof SFMContextDocumentProjection document)) {
            throw new IllegalArgumentException("Navigation requests require a document contribution");
        }
        Optional<SFMContextPosition> position = document.cursors().stream()
                .filter(SFMContextCursorProjection::primary)
                .map(SFMContextCursorProjection::position)
                .findFirst();
        return new SFMNavigationRequestWitness(
                workspace,
                sourcePanelId,
                panelEntryIdentity,
                contribution.originId(),
                documentIdentity(document),
                new SemanticRequest(position, contribution.generations().relationGeneration()),
                requestGeneration
        );
    }

    Validation validate(
            SFMScreenMultiplexer currentWorkspace,
            Optional<Object> currentPanelEntry,
            Optional<SFMContextContribution> currentContribution,
            long currentRequestGeneration
    ) {
        if (currentRequestGeneration != requestGeneration) {
            return Validation.rejected(RejectionReason.SUPERSEDED_REQUEST);
        }
        if (currentWorkspace != workspaceIdentity) {
            return Validation.rejected(RejectionReason.WORKSPACE_REPLACED);
        }
        if (currentPanelEntry.isEmpty()) {
            return Validation.rejected(RejectionReason.PANEL_REMOVED);
        }
        if (currentPanelEntry.orElseThrow() != panelEntryIdentity) {
            return Validation.rejected(RejectionReason.PANEL_REPLACED);
        }
        if (currentContribution.isEmpty()) {
            return Validation.rejected(RejectionReason.DOCUMENT_REMOVED);
        }
        SFMContextContribution contribution = currentContribution.orElseThrow();
        if (!(contribution.projection() instanceof SFMContextDocumentProjection currentDocument)) {
            return Validation.rejected(RejectionReason.DOCUMENT_REPLACED);
        }
        DocumentIdentity currentIdentity = documentIdentity(currentDocument);
        if (currentIdentity.state() != document.state()) {
            return Validation.rejected(RejectionReason.DOCUMENT_STATE_CHANGED);
        }
        if (!currentIdentity.editorId().equals(document.editorId())
                || !currentIdentity.address().equals(document.address())
                || !currentIdentity.authorizedRoot().equals(document.authorizedRoot())) {
            return Validation.rejected(RejectionReason.DOCUMENT_ADDRESS_CHANGED);
        }
        if (!currentIdentity.contentSha256().equals(document.contentSha256())) {
            return Validation.rejected(RejectionReason.DOCUMENT_CONTENT_CHANGED);
        }
        if (contribution.generations().relationGeneration() != semanticRequest.providerGeneration()) {
            return Validation.rejected(RejectionReason.PROVIDER_GENERATION_CHANGED);
        }
        return Validation.valid();
    }

    private static DocumentIdentity documentIdentity(SFMContextDocumentProjection document) {
        return new DocumentIdentity(
                document.editorId(),
                document.baseline().state(),
                document.baseline().path().map(value -> value.canonical()),
                document.baseline().authorizedRoot().map(value -> value.canonical()),
                document.currentSha256()
        );
    }
}
