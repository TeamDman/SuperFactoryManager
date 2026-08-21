package ca.teamdman.sfm.client.history;

import java.util.Optional;

/** Panel/content seam for resolving the explicit {@code focused} episode selector. */
public interface SFMEpisodeContext {
    Optional<String> episodeId();
}
