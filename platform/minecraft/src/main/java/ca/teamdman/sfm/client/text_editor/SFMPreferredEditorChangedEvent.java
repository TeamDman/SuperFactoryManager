package ca.teamdman.sfm.client.text_editor;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.Event;

public class SFMPreferredEditorChangedEvent extends Event {
    private final Identifier previousEditorId;

    private final Identifier newEditorId;

    public SFMPreferredEditorChangedEvent(
            Identifier previousEditorId,
            Identifier newEditorId
    ) {

        this.previousEditorId = previousEditorId;
        this.newEditorId = newEditorId;
    }

    public Identifier getPreviousEditorId() {

        return previousEditorId;
    }

    public Identifier getNewEditorId() {

        return newEditorId;
    }
}
