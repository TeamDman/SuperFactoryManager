package ca.teamdman.sfm.client.symbol;

import ca.teamdman.sfm.client.context.SFMContextCaptureRequest;
import ca.teamdman.sfm.client.context.SFMContextContribution;
import ca.teamdman.sfm.client.context.SFMContextContributor;
import ca.teamdman.sfm.client.context.SFMContextDocumentProjection;
import ca.teamdman.sfm.client.context.SFMContextOriginId;
import ca.teamdman.sfm.client.context.SFMContextSnapshot;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;

import java.util.Objects;
import java.util.Optional;

/** Stable, testable access to the workspace facts used by asynchronous navigation. */
interface SFMNavigationWorkspaceState {
    SFMContextSnapshot snapshot(SFMScreenMultiplexer workspace);

    Optional<Object> panelEntryIdentity(
            SFMScreenMultiplexer workspace,
            SFMWorkspacePanelId panelId
    );

    default boolean containsPanel(
            SFMScreenMultiplexer workspace,
            SFMWorkspacePanelId panelId
    ) {
        return panelEntryIdentity(workspace, panelId).isPresent();
    }

    /**
     * Returns the captured origin even when its panel is a hidden entry in a
     * stack. Focus and visibility are presentation state, not request identity.
     */
    default Optional<SFMContextContribution> contribution(
            SFMScreenMultiplexer workspace,
            SFMWorkspacePanelId panelId,
            SFMContextOriginId originId,
            SFMContextSnapshot snapshot
    ) {
        return contribution(snapshot, originId);
    }

    static Optional<SFMContextContribution> focusedDocument(SFMContextSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return snapshot.focusedOriginId()
                .flatMap(originId -> contribution(snapshot, originId))
                .filter(value -> value.projection() instanceof SFMContextDocumentProjection);
    }

    static SFMNavigationWorkspaceState production() {
        return new SFMNavigationWorkspaceState() {
            @Override
            public SFMContextSnapshot snapshot(SFMScreenMultiplexer workspace) {
                return workspace.contextSnapshot();
            }

            @Override
            public Optional<Object> panelEntryIdentity(
                    SFMScreenMultiplexer workspace,
                    SFMWorkspacePanelId panelId
            ) {
                return Optional.ofNullable(workspace.panelInstance(panelId));
            }

            @Override
            public Optional<SFMContextContribution> contribution(
                    SFMScreenMultiplexer workspace,
                    SFMWorkspacePanelId panelId,
                    SFMContextOriginId originId,
                    SFMContextSnapshot snapshot
            ) {
                Optional<SFMContextContribution> visible =
                        SFMNavigationWorkspaceState.contribution(snapshot, originId);
                if (visible.isPresent()) return visible;
                Object panel = workspace.panelInstance(panelId);
                if (!(panel instanceof SFMContextContributor contributor)) return Optional.empty();
                SFMContextCaptureRequest request = new SFMContextCaptureRequest(
                        snapshot.captureGeneration(),
                        snapshot.workspaceGeneration(),
                        snapshot.focusGeneration(),
                        snapshot.focusedOriginId()
                );
                return contributor.capture(request).stream()
                        .filter(value -> value.originId().equals(originId))
                        .findFirst();
            }
        };
    }

    private static Optional<SFMContextContribution> contribution(
            SFMContextSnapshot snapshot,
            SFMContextOriginId originId
    ) {
        return snapshot.contributions().stream()
                .filter(value -> value.originId().equals(originId))
                .findFirst();
    }
}
