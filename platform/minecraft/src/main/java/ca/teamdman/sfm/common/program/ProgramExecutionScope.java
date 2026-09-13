package ca.teamdman.sfm.common.program;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Mutable state owned by one trigger execution or one simulated path. */
public final class ProgramExecutionScope {
    private final ProgramVariableEnvironment variables = new ProgramVariableEnvironment();
    private final List<ProgramInputSource> activeInputs = new ArrayList<>();
    private final ProgramEphemeralResourceOwner ephemeralResources = new ProgramEphemeralResourceOwner();
    private boolean freed;

    public ProgramVariableEnvironment variables() {
        return variables;
    }

    public List<ProgramInputSource> activeInputs() {
        return Collections.unmodifiableList(activeInputs);
    }

    public ProgramEphemeralResourceOwner ephemeralResources() {
        return ephemeralResources;
    }

    public void addInput(ProgramInputSource inputSource) {
        checkActive();
        activeInputs.add(Objects.requireNonNull(inputSource));
    }

    public void replaceInputs(Collection<? extends ProgramInputSource> inputSources) {
        checkActive();
        List<ProgramInputSource> replacements = List.copyOf(inputSources);
        activeInputs.clear();
        activeInputs.addAll(replacements);
    }

    public void free() {
        if (freed) {
            return;
        }
        freed = true;
        List<ProgramInputSource> inputs = List.copyOf(activeInputs);
        activeInputs.clear();
        Throwable failure = null;
        for (ProgramInputSource input : inputs) {
            try {
                input.free();
            } catch (RuntimeException | Error cleanupFailure) {
                failure = collectFailure(failure, cleanupFailure);
            }
        }
        try {
            ephemeralResources.free();
        } catch (RuntimeException | Error cleanupFailure) {
            failure = collectFailure(failure, cleanupFailure);
        }
        try {
            variables.free();
        } catch (RuntimeException | Error cleanupFailure) {
            failure = collectFailure(failure, cleanupFailure);
        }
        rethrow(failure);
    }

    public boolean isFreed() {
        return freed;
    }

    private void checkActive() {
        if (freed) {
            throw new IllegalStateException("Execution scope has been freed");
        }
    }

    private static Throwable collectFailure(
            Throwable first,
            Throwable next
    ) {
        if (first == null) {
            return next;
        }
        first.addSuppressed(next);
        return first;
    }

    private static void rethrow(Throwable failure) {
        if (failure == null) {
            return;
        }
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw (Error) failure;
    }
}
