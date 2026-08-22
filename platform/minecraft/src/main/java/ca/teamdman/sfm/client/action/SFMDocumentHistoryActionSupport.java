package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.history.SFMDocumentHistoryHost;
import ca.teamdman.sfm.client.history.SFMDocumentHistoryHostController;
import ca.teamdman.sfm.client.history.SFMDocumentHistoryTarget;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistorySession;
import ca.teamdman.sfm.client.history.document.runtime.SFMDocumentHistoryRuntime;
import ca.teamdman.sfm.client.history.document.runtime.SFMDocumentHistorySelector;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;

import java.util.Objects;
import java.util.Optional;

/** Selector resolution shared by ordinary document-head actions. */
final class SFMDocumentHistoryActionSupport {
    record Target(
            String sessionId,
            Optional<SFMDocumentHistorySession> session,
            Optional<SFMDocumentHistoryHostController> controller,
            SFMDocumentHistoryTarget target
    ) {
        Target {
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(session, "session");
            Objects.requireNonNull(controller, "controller");
            Objects.requireNonNull(target, "target");
        }
    }

    private SFMDocumentHistoryActionSupport() {
    }

    static Optional<Target> resolve(
            SFMClientActionContext context,
            SFMDocumentHistoryRuntime runtime,
            SFMDocumentHistorySelector selector
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(selector, "selector");
        if (selector.kind() == SFMDocumentHistorySelector.Kind.FOCUSED) {
            CapturedTarget captured = capturedTarget(context);
            if (captured.authoritative()) return captured.target();
            return runtime.focused().flatMap(SFMDocumentHistoryActionSupport::runtimeTarget);
        }
        return runtime.exact(selector.sessionId().orElseThrow())
                .flatMap(SFMDocumentHistoryActionSupport::runtimeTarget);
    }

    /**
     * Resolve the action invocation's captured focus before consulting mutable
     * runtime focus.  An unavailable read-only history host is authoritative:
     * falling through would make {@code focused} mutate some previously focused
     * editor behind the visible read-only document.
     */
    private static CapturedTarget capturedTarget(SFMClientActionContext context) {
        Object host = context.originatingHost();
        if (host instanceof SFMDocumentHistoryHost directHost && !directHost.documentHistoryAvailable()) {
            return CapturedTarget.blocked();
        }
        if (host instanceof SFMDocumentHistoryTarget direct) {
            return CapturedTarget.resolved(target(direct));
        }
        if (host instanceof SFMScreenMultiplexer workspace) {
            Object focusedPanel = workspace.focusedPanelInstance();
            if (focusedPanel instanceof SFMDocumentHistoryHost focusedHost
                    && !focusedHost.documentHistoryAvailable()) return CapturedTarget.blocked();
            if (focusedPanel instanceof SFMDocumentHistoryTarget focused) {
                return CapturedTarget.resolved(target(focused));
            }
        }
        return CapturedTarget.absent();
    }

    private record CapturedTarget(boolean authoritative, Optional<Target> target) {
        private CapturedTarget {
            Objects.requireNonNull(target, "target");
        }

        private static CapturedTarget absent() {
            return new CapturedTarget(false, Optional.empty());
        }

        private static CapturedTarget blocked() {
            return new CapturedTarget(true, Optional.empty());
        }

        private static CapturedTarget resolved(Target target) {
            return new CapturedTarget(true, Optional.of(target));
        }
    }

    private static Target target(SFMDocumentHistoryTarget target) {
        if (target instanceof SFMDocumentHistoryHost host) {
            return new Target(
                    host.documentHistorySessionId(),
                    Optional.of(host.documentHistorySession()),
                    Optional.of(host.documentHistoryController()),
                    target
            );
        }
        return new Target("focused-legacy-document", Optional.empty(), Optional.empty(), target);
    }

    private static Optional<Target> runtimeTarget(SFMDocumentHistoryRuntime.SessionEntry entry) {
        return entry.controller().map(controller -> new Target(
                entry.sessionId(),
                Optional.of(entry.session()),
                Optional.of(controller),
                new ControllerTarget(controller)
        ));
    }

    private record ControllerTarget(SFMDocumentHistoryHostController controller)
            implements SFMDocumentHistoryTarget {
        private ControllerTarget {
            Objects.requireNonNull(controller, "controller");
        }

        @Override
        public ca.teamdman.sfm.client.history.SFMHistoryGraphRuntime.OperationResult undoDocumentHistory() {
            return controller.undo("sfm:document/history/undo");
        }

        @Override
        public ca.teamdman.sfm.client.history.SFMHistoryGraphRuntime.OperationResult redoDocumentHistory(
                String childRevisionId
        ) {
            return controller.redo(
                    childRevisionId == null || childRevisionId.isBlank()
                            ? Optional.empty()
                            : Optional.of(childRevisionId),
                    "sfm:document/history/redo"
            );
        }
    }
}
