package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.explorer.SFMChildRelationRepository;
import ca.teamdman.sfm.client.explorer.SFMExplorerId;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.SFMSelectionRepository;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerSession;
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
                request(path, entry)
        );

        assertEquals(5, choices.size());
        assertEquals("sfm:explorer/row/details/copy", choices.get(0).actionId().toString());
        assertEquals("sfm:path/open", choices.get(1).actionId().toString());
        assertEquals("sfm:review/session/open/view", choices.get(2).actionId().toString());
        assertEquals("sfm:review/evidence/open", choices.get(3).actionId().toString());
        assertEquals("sfm:review/session/open/read_only/view", choices.get(4).actionId().toString());
        assertTrue(choices.stream().allMatch(choice -> choice.command().startsWith("sfm action invoke ")));
    }

    @Test
    void ordinaryJsonAndDirectoriesDoNotPretendToBeReleaseReviews() {
        SFMPath ordinary = SFMPath.fromNative(Path.of("reviews", "candidate.json"));
        var choices = SFMExplorerContextActionRegistry.minecraftDefaults().resolve(request(
                ordinary,
                SFMExplorerEntry.simple(ordinary, "candidate.json", false, Optional.empty())
        ));
        assertEquals(1, choices.size());
        assertEquals("Copy row details", choices.get(0).displayText());
    }

    private static SFMExplorerContextActionProvider.Request request(
            SFMPath path,
            SFMExplorerEntry entry
    ) {
        SFMExplorerId explorerId = new SFMExplorerId("inspection-test");
        SFMExplorerSession session = new SFMExplorerSession(
                explorerId,
                path,
                new SFMSelectionRepository()
        );
        SFMExplorerProjection.Row row = new SFMExplorerProjection.Row(
                path,
                entry,
                0,
                true,
                false,
                entry.sortKey(SFMExplorerEntry.SORT_NAME)
        );
        SFMExplorerRowInspection inspection = SFMExplorerRowInspection.capture(
                session.snapshot(),
                row,
                new SFMChildRelationRepository().snapshot(),
                java.util.List.of(),
                true,
                "body"
        );
        return new SFMExplorerContextActionProvider.Request(
                new SFMClientActionContext(null, () -> true, null),
                explorerId,
                path,
                entry,
                inspection
        );
    }
}
