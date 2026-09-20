package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.client.action.SFMClientActionContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.Consumer;

/** Small shared seam for clipboard writes followed by workspace-visible confirmation. */
@FunctionalInterface
public interface SFMWorkspaceCopyFeedback {
    void copy(SFMClientActionContext context, String clipboardText, Component confirmation);

    static SFMWorkspaceCopyFeedback production() {
        return writingTo(text -> Minecraft.getInstance().keyboardHandler.setClipboard(text));
    }

    /** Testable construction of the exact production write-and-confirm route. */
    static SFMWorkspaceCopyFeedback writingTo(Consumer<String> clipboardWriter) {
        Objects.requireNonNull(clipboardWriter, "clipboardWriter");
        return (context, clipboardText, confirmation) -> {
            Objects.requireNonNull(context, "context");
            Objects.requireNonNull(clipboardText, "clipboardText");
            Objects.requireNonNull(confirmation, "confirmation");
            clipboardWriter.accept(clipboardText);
            if (context.originatingHost() instanceof SFMScreenMultiplexer workspace) {
                workspace.showClipboardCopyConfirmation(confirmation);
            }
        };
    }
}
