package ca.teamdman.sfm.client.text_editor;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Prepare an editor before changing screens. A failed factory is never retried or substituted. */
public final class SFMTextEditorOpenAttempt {
    private SFMTextEditorOpenAttempt() { }

    public static <T> boolean open(Supplier<T> prepare, Consumer<T> present, Consumer<Throwable> failed) {
        T prepared;
        try {
            prepared = Objects.requireNonNull(prepare.get(), "Editor factory returned no screen");
        } catch (RuntimeException | LinkageError failure) {
            failed.accept(failure);
            return false;
        }
        // Presentation can change the screen stack. Its failures are not factory failures,
        // so do not report them as a safe, unchanged-screen recovery or retry the factory.
        present.accept(prepared);
        return true;
    }
}
