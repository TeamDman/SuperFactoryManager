package ca.teamdman.sfm.client.text_editor;

import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.Optional;

/** Typed result from saving a virtual or host-backed text document. */
public record SFMTextDocumentSaveResult(
        boolean saved,
        Optional<Component> diagnostic
) {
    public SFMTextDocumentSaveResult {
        Objects.requireNonNull(diagnostic, "diagnostic");
        if (saved && diagnostic.isPresent()) {
            throw new IllegalArgumentException("A successful save cannot carry a failure diagnostic");
        }
        if (!saved && diagnostic.isEmpty()) {
            throw new IllegalArgumentException("A rejected save must explain why it was rejected");
        }
    }

    public static SFMTextDocumentSaveResult success() {
        return new SFMTextDocumentSaveResult(true, Optional.empty());
    }

    public static SFMTextDocumentSaveResult rejected(Component diagnostic) {
        return new SFMTextDocumentSaveResult(
                false,
                Optional.of(Objects.requireNonNull(diagnostic, "diagnostic"))
        );
    }
}
