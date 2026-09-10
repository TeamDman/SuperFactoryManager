package ca.teamdman.sfm.client.review.release_review;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Pure, non-wrapping transitions of the portable work cursor, never the Explorer highlight. */
public final class SFMReleaseReviewWorkQueue {
    public enum Operation { SELECT, NEXT, PREVIOUS, DEFER, RESUME }

    public static final class Unavailable extends IllegalArgumentException {
        public Unavailable(String message) { super(message); }
    }

    private SFMReleaseReviewWorkQueue() { }

    public static String expression(SFMReleaseReviewV1 review) {
        if (SFMReleaseReviewKernel.activeQueryRevisionStale(review)) throw new Unavailable(
                "Active named-query revision is stale; reactivate the query before navigating its work queue");
        return review.resumeState().activeQueryExpression().orElseGet(() ->
                review.resumeState().activeQueryId()
                        .flatMap(id -> review.namedQueries().stream().filter(query -> query.id().equals(id)).findFirst())
                        .map(SFMReleaseReviewV1.NamedQuery::expression).orElse("remaining"));
    }

    public static String current(SFMReleaseReviewV1 review, List<String> queue) {
        String current = review.resumeState().currentUnitId().orElseThrow(() -> new Unavailable(
                "No saved work cursor; activate a queue or resume deferred work"));
        requireMember(queue, current);
        return current;
    }

    public static SFMReleaseReviewV1.ResumeState transition(
            SFMReleaseReviewV1 review, List<String> queue, Operation operation, Optional<String> selected
    ) {
        Objects.requireNonNull(review, "review");
        queue = List.copyOf(queue);
        if (queue.isEmpty()) throw new Unavailable("The active work queue is empty");
        var previous = review.resumeState();
        ArrayList<String> deferred = new ArrayList<>(previous.deferredUnitIds());
        Optional<String> next;
        switch (operation) {
            case SELECT -> {
                String id = selected.orElseThrow(() -> new Unavailable("A stable review-unit id is required"));
                requireMember(queue, id);
                if (previous.currentUnitId().equals(Optional.of(id)) && !deferred.contains(id)) {
                    throw new Unavailable("This is already the saved work cursor; use Show current work to reveal it");
                }
                deferred.remove(id);
                next = Optional.of(id);
            }
            case NEXT, PREVIOUS -> {
                String current = current(review, queue);
                int delta = operation == Operation.NEXT ? 1 : -1;
                int index = queue.indexOf(current) + delta;
                while (index >= 0 && index < queue.size() && deferred.contains(queue.get(index))) index += delta;
                if (index < 0 || index >= queue.size()) throw new Unavailable(
                        operation == Operation.NEXT ? "No next non-deferred work unit; the saved cursor is unchanged"
                                : "No previous non-deferred work unit; the saved cursor is unchanged");
                next = Optional.of(queue.get(index));
            }
            case DEFER -> {
                String current = current(review, queue);
                if (!deferred.contains(current)) deferred.add(current);
                next = queue.subList(queue.indexOf(current) + 1, queue.size()).stream()
                        .filter(id -> !deferred.contains(id)).findFirst();
            }
            case RESUME -> {
                if (deferred.isEmpty()) throw new Unavailable("No deferred review unit is available");
                String id = deferred.get(0);
                requireMember(queue, id); // Never silently discard a deferred item outside this query.
                deferred.remove(0);
                next = Optional.of(id);
            }
            default -> throw new AssertionError(operation);
        }
        return new SFMReleaseReviewV1.ResumeState(previous.activeQueryId(), previous.activeQueryExpression(),
                next, deferred, Math.incrementExact(previous.generation()));
    }

    private static void requireMember(List<String> queue, String id) {
        if (!queue.contains(id)) throw new Unavailable(
                "Review unit is not present in the active work queue: " + id + "; reactivate or choose another query");
    }
}
