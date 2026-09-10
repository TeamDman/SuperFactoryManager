package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.screen.SFMActionChoice;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerRowInspection;
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

/** Copies an immutable capture of one generic Explorer row's diagnostic state. */
public final class SFMExplorerRowCopyDetailsAction implements SFMClientAction<SFMClientActionContext> {
    @ca.teamdman.sfm.common.localization.SFMLocalizationDatagen
    public static final ca.teamdman.sfm.common.localization.LocalizationEntry ENTRY_DETAILS =
            new ca.teamdman.sfm.common.localization.LocalizationEntry("gui.sfm.preview_rule.entry.copy","Copy entry details to clipboard");
    public static final ResourceLocation ID = new ResourceLocation(SFM.MOD_ID, "explorer/row/details/copy");
    private static final int MAXIMUM_CAPTURES = 256;
    private static final String CAPTURE_ARGUMENT = "row_capture";
    private static final SimpleCommandExceptionType CAPTURE_REQUIRED = new SimpleCommandExceptionType(
            Component.literal("An Explorer row capture is required"));
    private static final SimpleCommandExceptionType CAPTURE_EXPIRED = new SimpleCommandExceptionType(
            Component.literal("The captured Explorer row is no longer available"));
    private record Capture(SFMExplorerRowInspection row,String payload) {}
    private static final Map<Long, Capture> CAPTURES = new LinkedHashMap<>();
    private static long nextCaptureId = 1;

    private final Consumer<String> clipboardWriter;

    public SFMExplorerRowCopyDetailsAction() {
        this(text -> Minecraft.getInstance().keyboardHandler.setClipboard(text));
    }

    SFMExplorerRowCopyDetailsAction(Consumer<String> clipboardWriter) {
        this.clipboardWriter = Objects.requireNonNull(clipboardWriter, "clipboardWriter");
    }

    public static SFMActionChoice captureChoice(SFMExplorerRowInspection inspection) {
        long captureId = retain(Objects.requireNonNull(inspection, "inspection"),inspection.detailsPayload());
        return SFMActionChoice.invoke(ID, Long.toString(captureId), "Copy row details");
    }

    public static SFMActionChoice captureChoice(ca.teamdman.sfm.client.theme.preview.SFMItemstackPreviewInspection inspection) {
        long captureId=retain(inspection.row(),inspection.detailsPayload());
        return SFMActionChoice.invoke(ID,Long.toString(captureId),ENTRY_DETAILS.getComponent().getString());
    }

    @Override
    public Component title() {
        return Component.literal("Copy Explorer row details");
    }

    @Override
    public Component description() {
        return Component.literal("Copy deterministic row, view, pagination, loading, and focus diagnostics");
    }

    @Override
    public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return context -> context.originatingHostIsCurrent().getAsBoolean()
                ? SFMClientActionAvailability.available(context)
                : SFMClientActionAvailability.unavailable(
                        SFMClientActionContext.ORIGINATING_HOST_CHANGED.getComponent());
    }

    @Override
    public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        node.then(RequiredArgumentBuilder.<SFMClientActionSource, Long>argument(
                        CAPTURE_ARGUMENT,
                        LongArgumentType.longArg(1)
                )
                .executes(this::copy));
    }

    @Override
    public int execute(
            SFMClientActionContext target,
            CommandContext<SFMClientActionSource> context
    ) throws CommandSyntaxException {
        throw CAPTURE_REQUIRED.create();
    }

    private int copy(CommandContext<SFMClientActionSource> command) throws CommandSyntaxException {
        SFMClientActionAvailability<SFMClientActionContext> availability = requirement().resolve(
                command.getSource().context());
        if (!availability.isAvailable()) {
            throw new SimpleCommandExceptionType(availability.unavailableReason()).create();
        }
        return copyCapture(
                LongArgumentType.getLong(command, CAPTURE_ARGUMENT),
                command.getSource()::sendFeedback
        );
    }

    int copyCapture(long captureId, Consumer<Component> feedback) throws CommandSyntaxException {
        Objects.requireNonNull(feedback, "feedback");
        Capture inspection = lookup(captureId).orElseThrow(CAPTURE_EXPIRED::create);
        clipboardWriter.accept(inspection.payload());
        feedback.accept(Component.literal("Copied Explorer row details"));
        return 1;
    }

    private static synchronized long retain(SFMExplorerRowInspection inspection,String payload) {
        long captureId = nextCaptureId++;
        CAPTURES.put(captureId, new Capture(inspection,payload));
        while (CAPTURES.size() > MAXIMUM_CAPTURES) {
            CAPTURES.remove(CAPTURES.keySet().iterator().next());
        }
        return captureId;
    }

    private static synchronized Optional<Capture> lookup(long captureId) {
        return Optional.ofNullable(CAPTURES.get(captureId));
    }
}
