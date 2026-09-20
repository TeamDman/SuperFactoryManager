package ca.teamdman.sfm.client.context;

import ca.teamdman.sfm.client.explorer.SFMPath;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SFMContextSnapshotTests {
    private static final SFMPath SAME_PATH = SFMPath.parse("file:///D:/repo/SFM.java");

    @Test
    void independentOriginsRemainDistinctWhenTheyProjectTheSamePath() {
        SFMContextOriginId left = new SFMContextOriginId("panel", "41", "document");
        SFMContextOriginId right = new SFMContextOriginId("panel", "42", "document");
        SFMContextCaptureService service = new SFMContextCaptureService(List.of(
                fake("panel", contribution(left)),
                fake("panel", contribution(right))
        ));

        SFMContextSnapshot snapshot = service.capture(request(Optional.of(left)));

        assertEquals(2, snapshot.contributions().size());
        assertNotEquals(
                snapshot.contributions().get(0).originId(),
                snapshot.contributions().get(1).originId()
        );
        assertEquals(SAME_PATH, ((SFMContextPathProjection) snapshot.contributions().get(0).projection()).path());
        assertEquals(SAME_PATH, ((SFMContextPathProjection) snapshot.contributions().get(1).projection()).path());
    }

    @Test
    void focusRanksEveryOriginWithoutDroppingNonFocusedContributions() {
        SFMContextOriginId focused = new SFMContextOriginId("editor", "workspace-panel-7", "document");
        SFMContextOriginId sibling = new SFMContextOriginId("explorer", "workspace-panel-7", "selection");
        SFMContextOriginId other = new SFMContextOriginId("editor", "workspace-panel-9", "document");
        SFMContextCaptureService service = new SFMContextCaptureService(List.of(
                fake("editor", contribution(other), contribution(focused)),
                fake("explorer", contribution(sibling))
        ));

        SFMContextSnapshot snapshot = service.capture(request(Optional.of(focused)));

        assertEquals(List.of(focused, sibling, other), snapshot.contributions().stream()
                .map(SFMContextContribution::originId)
                .toList());
        assertEquals(0, snapshot.focusRank(focused));
        assertEquals(1, snapshot.focusRank(sibling));
        assertEquals(2, snapshot.focusRank(other));
    }

    @Test
    void captureCopiesContributorAndProjectionCollections() {
        SFMContextOriginId origin = new SFMContextOriginId("panel", "1", "selected");
        ArrayList<SFMContextContribution> mutable = new ArrayList<>(List.of(contribution(origin)));
        SFMContextContributor contributor = new SFMContextContributor() {
            @Override public String id() { return "panel"; }
            @Override public List<SFMContextContribution> capture(SFMContextCaptureRequest request) {
                return mutable;
            }
        };
        ArrayList<SFMContextContributor> registry = new ArrayList<>(List.of(contributor));
        SFMContextCaptureService service = new SFMContextCaptureService(registry);
        registry.clear();

        SFMContextSnapshot snapshot = service.capture(request(Optional.empty()));
        mutable.clear();

        assertEquals(1, snapshot.contributions().size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.contributions().clear());
    }

    private static SFMContextCaptureRequest request(Optional<SFMContextOriginId> focused) {
        return new SFMContextCaptureRequest(13, 8, 5, focused);
    }

    private static SFMContextContribution contribution(SFMContextOriginId id) {
        return new SFMContextContribution(
                id,
                new SFMContextGenerationEvidence(2, 3, 4, 5),
                new SFMContextPathProjection(SAME_PATH, Optional.empty(), "selected")
        );
    }

    private static SFMContextContributor fake(String id, SFMContextContribution... contributions) {
        List<SFMContextContribution> values = List.of(contributions);
        return new SFMContextContributor() {
            @Override public String id() { return id; }
            @Override public List<SFMContextContribution> capture(SFMContextCaptureRequest request) { return values; }
        };
    }
}
