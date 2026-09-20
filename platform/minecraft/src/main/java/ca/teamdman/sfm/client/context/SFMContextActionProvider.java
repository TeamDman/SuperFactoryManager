package ca.teamdman.sfm.client.context;

import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.screen.SFMActionChoice;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Contributes zero or more self-contained action drafts for one immutable context capture. */
@FunctionalInterface
public interface SFMContextActionProvider {
    List<Offer> offers(Request request);

    record Request(
            SFMClientActionContext actionContext,
            SFMContextSnapshot snapshot,
            Optional<SFMContextContribution> focusedContribution
    ) {
        public Request {
            Objects.requireNonNull(actionContext, "actionContext");
            Objects.requireNonNull(snapshot, "snapshot");
            focusedContribution = Objects.requireNonNull(focusedContribution, "focusedContribution");
            focusedContribution.ifPresent(contribution -> {
                if (snapshot.focusedOriginId().filter(contribution.originId()::equals).isEmpty()
                        || !snapshot.contributions().contains(contribution)) {
                    throw new IllegalArgumentException("Focused contribution must belong to the captured snapshot");
                }
            });
        }

        public static Request capture(SFMClientActionContext context, SFMContextSnapshot snapshot) {
            Optional<SFMContextContribution> focused = snapshot.focusedOriginId().flatMap(origin ->
                    snapshot.contributions().stream()
                            .filter(contribution -> contribution.originId().equals(origin))
                            .findFirst());
            return new Request(context, snapshot, focused);
        }
    }

    record Offer(int rank, SFMActionChoice choice) {
        public Offer {
            Objects.requireNonNull(choice, "choice");
        }
    }
}
