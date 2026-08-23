package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry;
import ca.teamdman.sfm.client.screen.SFMActionChoice;

import java.util.List;
import java.util.Objects;

/** Contributes exact action invocations for one captured generic-explorer row. */
public interface SFMExplorerContextActionProvider {
    record Request(
            SFMClientActionContext actionContext,
            SFMPath path,
            SFMExplorerEntry entry
    ) {
        public Request {
            Objects.requireNonNull(actionContext, "actionContext");
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(entry, "entry");
            if (!path.equals(entry.path())) throw new IllegalArgumentException("Explorer request path must match entry");
        }
    }

    String id();

    List<SFMActionChoice> choices(Request request);
}
