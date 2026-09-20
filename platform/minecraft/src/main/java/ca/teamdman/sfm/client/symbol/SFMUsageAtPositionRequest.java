package ca.teamdman.sfm.client.symbol;

import java.util.Objects;

/** Immutable provider-neutral request for references to the symbol at one editor location. */
public record SFMUsageAtPositionRequest(
        String schema,
        long requestId,
        long requestGeneration,
        SFMDefinitionRequest.Workspace workspace,
        SFMDefinitionRequest.Document document,
        SFMDefinitionRequest.Position position
) {
    public static final String SCHEMA = "sfm.usage-at-position-request/1";

    public SFMUsageAtPositionRequest {
        if (!SCHEMA.equals(schema)) {
            throw new IllegalArgumentException("Unsupported usage-at-position request schema");
        }
        if (requestId < 0 || requestGeneration < 0) {
            throw new IllegalArgumentException("Usage-at-position request identities must not be negative");
        }
        Objects.requireNonNull(workspace, "workspace");
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(position, "position");
        // Definition and usage requests deliberately share this complete
        // immutable location contract. Reuse its validation instead of
        // allowing the two protocol surfaces to drift.
        new SFMDefinitionRequest(
                SFMDefinitionRequest.SCHEMA,
                requestId,
                requestGeneration,
                workspace,
                document,
                position
        );
    }

    public static SFMUsageAtPositionRequest fromDefinition(SFMDefinitionRequest request) {
        Objects.requireNonNull(request, "request");
        return new SFMUsageAtPositionRequest(
                SCHEMA,
                request.requestId(),
                request.requestGeneration(),
                request.workspace(),
                request.document(),
                request.position()
        );
    }

    public SFMDefinitionRequest asDefinitionRequest() {
        return new SFMDefinitionRequest(
                SFMDefinitionRequest.SCHEMA,
                requestId,
                requestGeneration,
                workspace,
                document,
                position
        );
    }
}
