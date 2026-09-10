package ca.teamdman.sfm.client.screen;

import java.util.List;

/** Optional structured text contributed to {@code sfm:screen/diagnostics}. */
public interface SFMScreenDiagnosticsContributor {
    List<String> screenDiagnostics();
}
