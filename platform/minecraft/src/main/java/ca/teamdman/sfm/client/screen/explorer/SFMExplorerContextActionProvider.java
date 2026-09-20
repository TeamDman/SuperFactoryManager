package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.explorer.SFMExplorerId;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry;
import ca.teamdman.sfm.client.screen.SFMActionChoice;

import java.util.List;
import java.util.Objects;

/** Contributes exact action invocations for one captured generic-explorer row. */
public interface SFMExplorerContextActionProvider {
    enum Target { ROW, ICON, KEYBOARD }
    record Request(
            SFMClientActionContext actionContext,
            SFMExplorerId explorerId,
            SFMPath path,
            SFMExplorerEntry entry,
            SFMExplorerRowInspection inspection,
            Target target,
            java.util.Optional<ca.teamdman.sfm.client.theme.preview.SFMItemstackPreviewInspection> iconInspection
    ) {
        public Request {
            Objects.requireNonNull(actionContext, "actionContext");
            Objects.requireNonNull(explorerId, "explorerId");
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(entry, "entry");
            Objects.requireNonNull(inspection, "inspection");
            Objects.requireNonNull(target); Objects.requireNonNull(iconInspection);
            if(iconInspection.isPresent() && !iconInspection.orElseThrow().row().equals(inspection))
                throw new IllegalArgumentException("Icon evidence must be the same captured row inspection");
            if (!path.equals(entry.path())) throw new IllegalArgumentException("Explorer request path must match entry");
            // Presentation labels can contain a compact chain. Exact addresses,
            // not equality with the terminal entry's label, establish identity.
            if (!explorerId.equals(inspection.explorerId())
                    || !path.equals(inspection.rowAddress())) {
                throw new IllegalArgumentException("Explorer request must agree with its row inspection");
            }
        }
        public Request(SFMClientActionContext context,SFMExplorerId explorer,SFMPath path,SFMExplorerEntry entry,SFMExplorerRowInspection inspection) {
            this(context,explorer,path,entry,inspection,Target.ROW,java.util.Optional.empty());
        }
    }

    String id();

    List<SFMActionChoice> choices(Request request);
}
