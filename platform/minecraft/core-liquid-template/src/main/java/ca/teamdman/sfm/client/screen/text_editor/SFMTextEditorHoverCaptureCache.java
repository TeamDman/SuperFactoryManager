package ca.teamdman.sfm.client.screen.text_editor;

import ca.teamdman.sfm.client.symbol.SFMSymbolHoverIdentity;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Reuses the expensive document projection for one stable Ctrl-hover target. */
final class SFMTextEditorHoverCaptureCache<T> {
    record Key(
            SFMSymbolHoverIdentity.EditorOrigin editorOrigin,
            long documentGeneration,
            double pointerX,
            double pointerY
    ) {
        Key {
            Objects.requireNonNull(editorOrigin, "editorOrigin");
            if (documentGeneration < 0) throw new IllegalArgumentException("documentGeneration must not be negative");
            if (!Double.isFinite(pointerX) || !Double.isFinite(pointerY)) {
                throw new IllegalArgumentException("Pointer coordinates must be finite");
            }
        }
    }

    private Key key;
    private T value;

    Optional<T> resolve(boolean definitionNavigationRequested, Key requestedKey, Supplier<T> capture) {
        Objects.requireNonNull(requestedKey, "requestedKey");
        Objects.requireNonNull(capture, "capture");
        if (!definitionNavigationRequested) return Optional.empty();
        if (requestedKey.equals(key)) return Optional.of(value);
        T captured = Objects.requireNonNull(capture.get(), "captured hover value");
        key = requestedKey;
        value = captured;
        return Optional.of(captured);
    }

    void clear() {
        key = null;
        value = null;
    }
}
