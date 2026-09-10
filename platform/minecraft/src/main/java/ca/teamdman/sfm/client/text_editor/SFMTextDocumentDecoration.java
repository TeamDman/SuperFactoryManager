package ca.teamdman.sfm.client.text_editor;

import java.util.Objects;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/** Independent document overlay that composes with, rather than replaces, syntax foregrounds. */
public record SFMTextDocumentDecoration(
        String id,
        SFMTextDocumentRange range,
        OptionalInt backgroundArgb,
        OptionalInt underlineArgb,
        Optional<String> gutterMarker,
        String narration,
        Optional<InteractiveObject> interactiveObject
) {
    /**
     * Stable object identity and human-first presentation carried by an
     * interactive source decoration.  Rendering remains independent: this is
     * only the bridge back to the object's ordinary action surface.
     */
    public record InteractiveObject(
            String kind,
            String id,
            String headline,
            List<String> detailLines
    ) {
        public InteractiveObject {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(headline, "headline");
            detailLines = List.copyOf(Objects.requireNonNull(detailLines, "detailLines"));
            if (kind.isBlank()) throw new IllegalArgumentException("Interactive object kind must not be blank");
            if (id.isBlank()) throw new IllegalArgumentException("Interactive object id must not be blank");
            if (headline.isBlank()) throw new IllegalArgumentException("Interactive object headline must not be blank");
        }
    }

    public SFMTextDocumentDecoration {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) throw new IllegalArgumentException("Document decoration id must not be blank");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(backgroundArgb, "backgroundArgb");
        Objects.requireNonNull(underlineArgb, "underlineArgb");
        gutterMarker = Objects.requireNonNull(gutterMarker, "gutterMarker")
                .map(String::strip)
                .filter(value -> !value.isEmpty());
        Objects.requireNonNull(narration, "narration");
        Objects.requireNonNull(interactiveObject, "interactiveObject");
    }
}
