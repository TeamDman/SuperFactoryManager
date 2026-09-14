package ca.teamdman.sfm.common.program;

import ca.teamdman.sfm.common.value.SFMValue;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Supplier;

/** A memoized, execution-local reference to one immutable SFM value. */
public final class ProgramValueReference {
    private @Nullable Supplier<SFMValue> evaluator;
    private @Nullable SFMValue value;
    private @Nullable Throwable failure;
    private boolean resolved;

    private ProgramValueReference(Supplier<SFMValue> evaluator) {
        this.evaluator = Objects.requireNonNull(evaluator);
    }

    public static ProgramValueReference lazy(Supplier<SFMValue> evaluator) {
        return new ProgramValueReference(evaluator);
    }

    public static ProgramValueReference resolved(SFMValue value) {
        return new ProgramValueReference(() -> Objects.requireNonNull(value));
    }

    public SFMValue get() {
        if (!resolved) {
            resolved = true;
            Supplier<SFMValue> pending = Objects.requireNonNull(evaluator);
            evaluator = null;
            try {
                value = Objects.requireNonNull(pending.get(), "Value expression returned null");
            } catch (RuntimeException | Error evaluationFailure) {
                failure = evaluationFailure;
            }
        }
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        return Objects.requireNonNull(value);
    }

    public boolean isResolved() {
        return resolved;
    }
}
