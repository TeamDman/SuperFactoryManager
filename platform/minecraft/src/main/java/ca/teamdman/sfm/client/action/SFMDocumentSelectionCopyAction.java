package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.context.SFMContextDocumentProjection;
import ca.teamdman.sfm.client.screen.SFMActionChoice;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/** Mouse-visible equivalent of Ctrl+C over an immutable captured editor selection. */
public final class SFMDocumentSelectionCopyAction implements SFMClientAction<SFMClientActionContext> {
    public static final ResourceLocation ID = new ResourceLocation(SFM.MOD_ID, "document/selection/copy");
    private static final int MAXIMUM_CAPTURES = 128;
    private static final Map<Long, String> CAPTURES = new LinkedHashMap<>();
    private static long nextCaptureId = 1;
    private static final SimpleCommandExceptionType CAPTURE_REQUIRED = new SimpleCommandExceptionType(
            Component.literal("A captured text selection is required"));
    private static final SimpleCommandExceptionType CAPTURE_EXPIRED = new SimpleCommandExceptionType(
            Component.literal("The captured text selection is no longer available"));
    private final Consumer<String> clipboardWriter;

    public SFMDocumentSelectionCopyAction() {
        this(text -> Minecraft.getInstance().keyboardHandler.setClipboard(text));
    }

    SFMDocumentSelectionCopyAction(Consumer<String> clipboardWriter) {
        this.clipboardWriter = Objects.requireNonNull(clipboardWriter, "clipboardWriter");
    }

    public static Optional<SFMActionChoice> captureChoice(SFMContextDocumentProjection projection) {
        String selected = selectedText(Objects.requireNonNull(projection, "projection"));
        if (selected.isEmpty()) return Optional.empty();
        long captureId = retain(selected);
        return Optional.of(SFMActionChoice.invoke(ID, Long.toString(captureId), "Copy selected text"));
    }

    static String selectedText(SFMContextDocumentProjection projection) {
        byte[] bytes = projection.currentText().getBytes(StandardCharsets.UTF_8);
        ArrayList<String> slices = new ArrayList<>();
        projection.selections().forEach(selection -> selection.ranges().forEach(range -> {
            int start = Math.toIntExact(range.start().byteOffset());
            int end = Math.toIntExact(range.end().byteOffset());
            if (start < end) slices.add(new String(bytes, start, end - start, StandardCharsets.UTF_8));
        }));
        return String.join("\n", slices);
    }

    @Override public Component title() { return Component.literal("Copy selected text"); }
    @Override public Component description() {
        return Component.literal("Copy the exact captured editor selection and acknowledge the clipboard update");
    }
    @Override public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return context -> context.originatingHostIsCurrent().getAsBoolean()
                ? SFMClientActionAvailability.available(context)
                : SFMClientActionAvailability.unavailable(SFMClientActionContext.ORIGINATING_HOST_CHANGED.getComponent());
    }
    @Override public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        node.then(RequiredArgumentBuilder.<SFMClientActionSource, Long>argument("selection_capture",
                LongArgumentType.longArg(1)).executes(this::copy));
    }
    @Override public int execute(SFMClientActionContext target, CommandContext<SFMClientActionSource> context)
            throws CommandSyntaxException { throw CAPTURE_REQUIRED.create(); }

    private int copy(CommandContext<SFMClientActionSource> command) throws CommandSyntaxException {
        var availability = requirement().resolve(command.getSource().context());
        if (!availability.isAvailable()) throw new SimpleCommandExceptionType(availability.unavailableReason()).create();
        return copyCapture(LongArgumentType.getLong(command, "selection_capture"), command.getSource()::sendFeedback);
    }

    int copyCapture(long captureId, Consumer<Component> feedback) throws CommandSyntaxException {
        String value = lookup(captureId).orElseThrow(CAPTURE_EXPIRED::create);
        clipboardWriter.accept(value);
        feedback.accept(Component.literal("Copied selected text"));
        return 1;
    }

    private static synchronized long retain(String value) {
        long id = nextCaptureId++;
        CAPTURES.put(id, value);
        while (CAPTURES.size() > MAXIMUM_CAPTURES) CAPTURES.remove(CAPTURES.keySet().iterator().next());
        return id;
    }

    private static synchronized Optional<String> lookup(long id) { return Optional.ofNullable(CAPTURES.get(id)); }
}
