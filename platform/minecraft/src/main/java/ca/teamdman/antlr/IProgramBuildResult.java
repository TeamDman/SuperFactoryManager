package ca.teamdman.antlr;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/// The potentially unsuccessful result of transforming a {@link String} into a {@link PROGRAM} {@link IAstNode}.
@SuppressWarnings("UnusedReturnValue")
public interface IProgramBuildResult<PROGRAM, METADATA extends IProgramMetadata<?, ?, ?, ?>, SELF extends IProgramBuildResult<PROGRAM, METADATA, SELF>> {

    /// If the compilation failed, this may be {@code null}.
    PROGRAM maybeProgram();

    /// Useful for testing, asserts that program is not null
    default @NotNull PROGRAM unwrapProgram() {
        return Objects.requireNonNull(maybeProgram());
    }

    METADATA metadata();


    default boolean isSuccess() {

        return maybeProgram() != null && metadata().errors().isEmpty();
    }

    default SELF caseSuccess(
            BiConsumer<PROGRAM, METADATA> callback
    ) {

        if (isSuccess()) {
            callback.accept(this.maybeProgram(), this.metadata());
        }
        //noinspection unchecked
        return (SELF) this;
    }

    default SELF caseSuccess(
            Consumer<PROGRAM> callback
    ) {

        if (isSuccess()) {
            callback.accept(this.maybeProgram());
        }
        //noinspection unchecked
        return (SELF) this;
    }

    default SELF caseFailure(
            BiConsumer<@Nullable PROGRAM, METADATA> callback
    ) {

        if (!isSuccess()) {
            callback.accept(this.maybeProgram(), this.metadata());
        }
        //noinspection unchecked
        return (SELF) this;
    }

    default SELF caseFailure(
            Consumer<METADATA> callback
    ) {

        if (!isSuccess()) {
            callback.accept(this.metadata());
        }
        //noinspection unchecked
        return (SELF) this;
    }

    default SELF caseFailure(
            Runnable callback
    ) {

        if (!isSuccess()) {
            callback.run();
        }
        //noinspection unchecked
        return (SELF) this;
    }

}
