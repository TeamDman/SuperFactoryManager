package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewExplorerRuntime;
import ca.teamdman.sfm.client.screen.SFMActionChoice;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerRowInspection;
import ca.teamdman.sfm.client.theme.preview.SFMItemstackPreviewInspection;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/** Copies the small display/internal identity needed for an ordinary row bug report. */
public final class SFMExplorerRowCopySummaryAction implements SFMClientAction<SFMClientActionContext> {
    public static final ResourceLocation ID = new ResourceLocation(SFM.MOD_ID, "explorer/row/summary/copy");
    private static final int MAXIMUM_CAPTURES = 256;
    private static final SimpleCommandExceptionType CAPTURE_REQUIRED = new SimpleCommandExceptionType(
            Component.literal("An Explorer row summary capture is required"));
    private static final SimpleCommandExceptionType CAPTURE_EXPIRED = new SimpleCommandExceptionType(
            Component.literal("The captured Explorer row summary is no longer available"));
    private static final Map<Long, String> CAPTURES = new LinkedHashMap<>();
    private static long nextCaptureId = 1;
    private final Consumer<String> clipboardWriter;

    public SFMExplorerRowCopySummaryAction() {
        this(text -> Minecraft.getInstance().keyboardHandler.setClipboard(text));
    }

    SFMExplorerRowCopySummaryAction(Consumer<String> clipboardWriter) {
        this.clipboardWriter = Objects.requireNonNull(clipboardWriter, "clipboardWriter");
    }

    public static SFMActionChoice captureChoice(SFMItemstackPreviewInspection inspection) {
        Objects.requireNonNull(inspection, "inspection");
        long id = retain(payload(inspection));
        return SFMActionChoice.invoke(ID, Long.toString(id), "Copy row summary");
    }

    static String payload(SFMItemstackPreviewInspection inspection) {
        SFMExplorerRowInspection row = inspection.row();
        StringBuilder out = new StringBuilder("schema: sfm.explorer-row-summary/1\n");
        line(out, "display.label", row.rowLabel());
        line(out, "display.itemstack.requested", inspection.requested()
                .map(icon -> icon.requestedItem().toString()).orElse("unavailable"));
        line(out, "display.itemstack.rendered", inspection.rendered()
                .map(SFMItemstackPreviewInspection.Rendered::itemId).orElse("unavailable"));
        line(out, "internal.row-address", row.rowAddress().canonical());
        line(out, "internal.subject-path", inspection.subject().path().canonical());
        SFMReleaseReviewExplorerRuntime.get().rowIdentity(row.rowAddress())
                .ifPresentOrElse(identity -> out.append(identity.detailsPayload()).append('\n'), () -> {
                    out.append("review.file-address: unavailable\n");
                    out.append("review.projection: unavailable\n");
                    out.append("review.source-path: unavailable\n");
                    out.append("review.node-id: unavailable\n");
                });
        return out.toString().stripTrailing();
    }

    private static void line(StringBuilder out, String key, String value) {
        out.append(key).append(": \"").append(value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n")).append("\"\n");
    }

    @Override public Component title() { return Component.literal("Copy Explorer row summary"); }
    @Override public Component description() {
        return Component.literal("Copy the row's display label and ItemStack plus its internal and review identities");
    }
    @Override public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return context -> context.originatingHostIsCurrent().getAsBoolean()
                ? SFMClientActionAvailability.available(context)
                : SFMClientActionAvailability.unavailable(SFMClientActionContext.ORIGINATING_HOST_CHANGED.getComponent());
    }
    @Override public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        node.then(RequiredArgumentBuilder.<SFMClientActionSource, Long>argument("row_summary_capture",
                LongArgumentType.longArg(1)).executes(this::copy));
    }
    @Override public int execute(SFMClientActionContext target, CommandContext<SFMClientActionSource> context)
            throws CommandSyntaxException { throw CAPTURE_REQUIRED.create(); }

    private int copy(CommandContext<SFMClientActionSource> command) throws CommandSyntaxException {
        var availability = requirement().resolve(command.getSource().context());
        if (!availability.isAvailable()) throw new SimpleCommandExceptionType(availability.unavailableReason()).create();
        return copyCapture(LongArgumentType.getLong(command, "row_summary_capture"), command.getSource()::sendFeedback);
    }

    int copyCapture(long captureId, Consumer<Component> feedback) throws CommandSyntaxException {
        String payload = lookup(captureId).orElseThrow(CAPTURE_EXPIRED::create);
        clipboardWriter.accept(payload);
        feedback.accept(Component.literal("Copied Explorer row summary"));
        return 1;
    }

    private static synchronized long retain(String payload) {
        long id = nextCaptureId++;
        CAPTURES.put(id, payload);
        while (CAPTURES.size() > MAXIMUM_CAPTURES) CAPTURES.remove(CAPTURES.keySet().iterator().next());
        return id;
    }

    private static synchronized Optional<String> lookup(long id) { return Optional.ofNullable(CAPTURES.get(id)); }
}
