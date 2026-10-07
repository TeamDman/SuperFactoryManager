package ca.teamdman.sfm.client.history;

import ca.teamdman.sfm.client.explorer.SFMSelectionId;
import ca.teamdman.sfm.client.explorer.SFMSelectionRepository;
import ca.teamdman.sfm.client.explorer.SFMSelectionRevision;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Typed operation seam later registered by Text Editor v3 and History Graph actions. */
public final class SFMSelectionHistoryActions {
    private SFMSelectionHistoryActions() {
    }

    public enum Operation {
        ENUMERATE("sfm:history/selection/enumerate"),
        CHECKOUT("sfm:history/selection/checkout"),
        UNDO("sfm:history/selection/undo"),
        REDO("sfm:history/selection/redo"),
        REDO_CHILD("sfm:history/selection/redo/child"),
        NAME_HEAD("sfm:history/selection/head/name");

        private final String actionId;

        Operation(String actionId) {
            this.actionId = actionId;
        }

        public String actionId() {
            return actionId;
        }
    }

    public record Request(
            Operation operation,
            SFMSelectionId selectionId,
            Optional<Long> revisionId,
            Optional<String> headName,
            String actor,
            String requestId
    ) {
        public Request {
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(selectionId, "selectionId");
            Objects.requireNonNull(revisionId, "revisionId");
            Objects.requireNonNull(headName, "headName");
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(requestId, "requestId");
            revisionId.ifPresent(value -> {
                if (value <= 0) throw new IllegalArgumentException("Revision id must be positive");
            });
            boolean revisionRequired = operation == Operation.CHECKOUT
                    || operation == Operation.REDO_CHILD
                    || operation == Operation.NAME_HEAD;
            boolean revisionAllowed = revisionRequired || operation == Operation.UNDO;
            if ((revisionRequired && revisionId.isEmpty())
                    || (!revisionAllowed && revisionId.isPresent())) {
                throw new IllegalArgumentException(operation + " revision-id presence is invalid");
            }
            if ((operation == Operation.NAME_HEAD) != headName.isPresent()) {
                throw new IllegalArgumentException(operation + " head-name presence is invalid");
            }
            if (actor.isEmpty() || requestId.isEmpty()) {
                throw new IllegalArgumentException("Action provenance must not be empty");
            }
        }
    }

    public record Result(
            Operation operation,
            SFMSelectionId selectionId,
            List<SFMSelectionRevision> history,
            Optional<SFMSelectionRepository.MutationResult> mutation
    ) {
        public Result {
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(selectionId, "selectionId");
            history = List.copyOf(history);
            Objects.requireNonNull(mutation, "mutation");
            if ((operation == Operation.ENUMERATE) != mutation.isEmpty()) {
                throw new IllegalArgumentException("Only enumerate returns no mutation result");
            }
        }
    }

    public static Result apply(SFMSelectionRepository repository, Request request) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(request, "request");
        Optional<SFMSelectionRepository.MutationResult> mutation = switch (request.operation()) {
            case ENUMERATE -> Optional.empty();
            case CHECKOUT -> Optional.of(repository.checkout(
                    request.selectionId(), request.revisionId().orElseThrow(),
                    request.actor(), request.requestId()
            ));
            case UNDO -> Optional.of(request.revisionId().isPresent()
                    ? repository.undo(request.selectionId(), request.revisionId().orElseThrow(),
                    request.actor(), request.requestId())
                    : repository.undo(request.selectionId(), request.actor(), request.requestId()));
            case REDO -> Optional.of(repository.redo(
                    request.selectionId(), request.actor(), request.requestId()
            ));
            case REDO_CHILD -> Optional.of(repository.redo(
                    request.selectionId(), request.revisionId().orElseThrow(),
                    request.actor(), request.requestId()
            ));
            case NAME_HEAD -> Optional.of(repository.nameHead(
                    request.selectionId(), request.headName().orElseThrow(),
                    request.revisionId().orElseThrow(), request.actor(), request.requestId()
            ));
        };
        return new Result(
                request.operation(),
                request.selectionId(),
                repository.history(request.selectionId()),
                mutation
        );
    }
}
