package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMExplorerContextActionRegistryTests {
    @Test
    void trackedReviewFileOffersPlainWritableAndReadOnlyOpenActions() {
        SFMPath path = SFMPath.fromNative(Path.of("reviews", "candidate.sfm-review.json"));
        SFMExplorerEntry entry = SFMExplorerEntry.simple(path, "candidate.sfm-review.json", false, Optional.empty());
        var choices = SFMExplorerContextActionRegistry.minecraftDefaults().resolve(
                new SFMExplorerContextActionProvider.Request(
                        new SFMClientActionContext(null, () -> true, null),
                        path,
                        entry
                )
        );

        assertEquals(3, choices.size());
        assertEquals("sfm:path/open", choices.get(0).actionId().toString());
        assertEquals("sfm:review/session/open/view", choices.get(1).actionId().toString());
        assertEquals("sfm:review/session/open/read_only/view", choices.get(2).actionId().toString());
        assertTrue(choices.stream().allMatch(choice -> choice.command().startsWith("sfm action invoke ")));
    }

    @Test
    void ordinaryJsonAndDirectoriesDoNotPretendToBeReleaseReviews() {
        SFMPath ordinary = SFMPath.fromNative(Path.of("reviews", "candidate.json"));
        assertTrue(SFMExplorerContextActionRegistry.minecraftDefaults().resolve(
                new SFMExplorerContextActionProvider.Request(
                        new SFMClientActionContext(null, () -> true, null),
                        ordinary,
                        SFMExplorerEntry.simple(ordinary, "candidate.json", false, Optional.empty())
                )
        ).isEmpty());
    }
}
