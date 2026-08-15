package ca.teamdman.sfm.client.screen.text_editor;

import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;

import java.util.Optional;

/** Typed safety surface used by explorer preview ownership. */
public interface SFMTextDocumentPanelState {
    boolean isReadOnly();

    Optional<SFMTextDocumentSnapshot> documentSnapshot();

    /** Moves an already-open immutable document to an exact validated range. */
    default boolean navigateToRange(SFMTextDocumentRange range) {
        return false;
    }
}
