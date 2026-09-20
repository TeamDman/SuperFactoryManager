package ca.teamdman.sfm.common.program;

import ca.teamdman.sfml.ast.Label;

import java.util.Objects;
import java.util.Set;

/** Distinguishes bare {@code FORGET} from selective labelled forgetting. */
public record ProgramInputForgetRequest(
        boolean allInputs,
        Set<Label> labels
) {
    public ProgramInputForgetRequest {
        labels = Set.copyOf(Objects.requireNonNull(labels));
        if (allInputs && !labels.isEmpty()) {
            throw new IllegalArgumentException("An all-input forget request cannot also name labels");
        }
    }

    public static ProgramInputForgetRequest all() {
        return new ProgramInputForgetRequest(true, Set.of());
    }

    public static ProgramInputForgetRequest labels(Set<Label> labels) {
        return new ProgramInputForgetRequest(false, labels);
    }
}
