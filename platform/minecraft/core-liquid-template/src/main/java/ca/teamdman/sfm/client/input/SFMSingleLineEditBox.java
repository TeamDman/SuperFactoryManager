package ca.teamdman.sfm.client.input;

import ca.teamdman.sfm.mixins.EditBoxAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import java.util.function.Predicate;

/** Vanilla field chrome with the same edit semantics as SFM's canvas fields. */
public class SFMSingleLineEditBox extends EditBox {
    private SFMSingleLineInput buffer;
    private int fieldMaximumLength = 32;
    private final boolean recordHistory;
    private Predicate<String> fieldFilter = value -> true;

    public SFMSingleLineEditBox(Font font, int x, int y, int width, int height, Component message) {
        this(font, x, y, width, height, message, true);
    }

    public SFMSingleLineEditBox(Font font, int x, int y, int width, int height, Component message,
                                boolean recordHistory) {
        super(font, x, y, width, height, message);
        this.recordHistory = recordHistory;
    }

    @Override public void setMaxLength(int maximumLength) {
        // A host reconfiguration starts a new field history domain, just as replacing the widget does.
        if (maximumLength != fieldMaximumLength) buffer = null;
        fieldMaximumLength = maximumLength;
        super.setMaxLength(maximumLength);
    }

    @Override public void setFilter(Predicate<String> filter) {
        super.setFilter(filter);
        fieldFilter = filter;
        buffer = null;
    }

    private SFMSingleLineInput editingBuffer() {
        if (buffer == null) buffer = new SFMSingleLineInput(getValue(), fieldMaximumLength, recordHistory, fieldFilter);
        int anchor = ((EditBoxAccessor) (Object) this).sfm$getHighlightPos();
        buffer.synchronize(getValue(), getCursorPosition(), anchor);
        return buffer;
    }

    private void publish() {
        // Do not refresh expensive suggestion/filter responders for a caret-only operation.
        if (!getValue().equals(buffer.text())) setValue(buffer.text());
        setCursorPosition(buffer.cursor());
        setHighlightPos(buffer.anchor());
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!canConsumeInput()) return super.keyPressed(keyCode, scanCode, modifiers);
        var client = Minecraft.getInstance();
        if (!editingBuffer().keyPressed(keyCode, modifiers,
                client.keyboardHandler::getClipboard, client.keyboardHandler::setClipboard))
            return super.keyPressed(keyCode, scanCode, modifiers);
        publish();
        return true;
    }

    @Override public boolean charTyped(char character, int modifiers) {
        if (!canConsumeInput()) return false;
        if (!editingBuffer().charTyped(character, modifiers)) return false;
        publish();
        return true;
    }
}
