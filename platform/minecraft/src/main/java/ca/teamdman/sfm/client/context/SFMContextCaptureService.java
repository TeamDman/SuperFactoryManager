package ca.teamdman.sfm.client.context;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Pure deterministic gatherer; production code supplies already-visible contributors. */
public final class SFMContextCaptureService {
    private final List<SFMContextContributor> contributors;

    public SFMContextCaptureService(List<SFMContextContributor> contributors) {
        this.contributors = List.copyOf(contributors);
        for (SFMContextContributor contributor : this.contributors) {
            Objects.requireNonNull(contributor, "contributor");
            if (contributor.id().isBlank()) throw new IllegalArgumentException("Contributor id must not be blank");
        }
    }

    public SFMContextSnapshot capture(SFMContextCaptureRequest request) {
        Objects.requireNonNull(request, "request");
        ArrayList<SFMContextContribution> gathered = new ArrayList<>();
        HashSet<SFMContextOriginId> origins = new HashSet<>();
        for (SFMContextContributor contributor : contributors) {
            List<SFMContextContribution> captured = List.copyOf(contributor.capture(request));
            for (SFMContextContribution contribution : captured) {
                if (!contributor.id().equals(contribution.originId().contributorId())) {
                    throw new IllegalArgumentException(
                            "Contributor " + contributor.id() + " returned foreign origin " + contribution.originId()
                    );
                }
                if (!origins.add(contribution.originId())) {
                    throw new IllegalArgumentException("Duplicate context origin " + contribution.originId());
                }
                gathered.add(contribution);
            }
        }
        gathered.sort(Comparator
                .comparingInt((SFMContextContribution value) -> focusRank(request, value.originId()))
                .thenComparing(SFMContextContribution::originId));
        return new SFMContextSnapshot(
                request.captureGeneration(),
                request.workspaceGeneration(),
                request.focusGeneration(),
                request.focusedOriginId(),
                gathered
        );
    }

    private static int focusRank(SFMContextCaptureRequest request, SFMContextOriginId originId) {
        if (request.focusedOriginId().isEmpty()) return 2;
        SFMContextOriginId focused = request.focusedOriginId().orElseThrow();
        if (focused.equals(originId)) return 0;
        return focused.sharesContainerWith(originId) ? 1 : 2;
    }
}
