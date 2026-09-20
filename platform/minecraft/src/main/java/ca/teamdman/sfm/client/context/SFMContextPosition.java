package ca.teamdman.sfm.client.context;

import ca.teamdman.sfm.client.text_editor.SFMTextDocumentPosition;

import java.util.Objects;
import java.util.Optional;

/** Provider-neutral position that preserves its source coordinate space. */
public sealed interface SFMContextPosition permits SFMContextPosition.Canvas, SFMContextPosition.Text {
    /** True Text Editor v3 canvas coordinate with an optional document hit. */
    record Canvas(
            double x,
            double y,
            Optional<SFMTextDocumentPosition> textHit
    ) implements SFMContextPosition {
        public Canvas {
            if (!Double.isFinite(x) || !Double.isFinite(y)) {
                throw new IllegalArgumentException("Canvas coordinates must be finite");
            }
            textHit = Objects.requireNonNull(textHit, "textHit");
        }
    }

    /** Legacy or derived document-only position. */
    record Text(SFMTextDocumentPosition position) implements SFMContextPosition {
        public Text {
            Objects.requireNonNull(position, "position");
        }
    }
}
