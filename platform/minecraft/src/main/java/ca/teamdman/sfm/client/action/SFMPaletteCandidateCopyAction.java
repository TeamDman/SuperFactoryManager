package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.SFM;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/** Copies one projection of an immutable, capture-time palette candidate. */
public final class SFMPaletteCandidateCopyAction implements SFMClientAction<SFMPaletteCandidateCopyAction.Host> {
    public static final ResourceLocation ID = new ResourceLocation(SFM.MOD_ID, "palette/candidate/copy");
    private static final String CAPTURE_ARGUMENT = "candidate_capture";
    private static final SimpleCommandExceptionType CAPTURE_REQUIRED = new SimpleCommandExceptionType(
            Component.literal("A captured command-palette candidate is required"));
    private static final SimpleCommandExceptionType CAPTURE_EXPIRED = new SimpleCommandExceptionType(
            Component.literal("The captured command-palette candidate is no longer available"));

    public enum Projection {
        DISPLAY("display", "Copy display text"),
        SURFACE("surface", "Copy replacement / surface text"),
        COMMAND("command", "Copy canonical executable command"),
        DETAILS("details", "Copy complete candidate details / help");

        private final String path;
        private final String displayText;

        Projection(String path, String displayText) {
            this.path = path;
            this.displayText = displayText;
        }

        public String path() {
            return path;
        }

        public String displayText() {
            return displayText;
        }
    }

    /** Host seam keeps candidate captures owned by the palette that produced them. */
    public interface Host {
        Optional<SFMPaletteCandidateInspection> paletteCandidateInspection(long captureId);
    }

    private final Consumer<String> clipboardWriter;

    public SFMPaletteCandidateCopyAction() {
        this(text -> Minecraft.getInstance().keyboardHandler.setClipboard(text));
    }

    SFMPaletteCandidateCopyAction(Consumer<String> clipboardWriter) {
        this.clipboardWriter = Objects.requireNonNull(clipboardWriter, "clipboardWriter");
    }

    public static List<SFMActionChoice> choices(
            long captureId,
            SFMPaletteCandidateInspection inspection
    ) {
        Objects.requireNonNull(inspection, "inspection");
        ArrayList<SFMActionChoice> choices = new ArrayList<>();
        choices.add(choice(captureId, Projection.DISPLAY));
        choices.add(choice(captureId, Projection.SURFACE));
        if (inspection.canonicalCommandPayload().isPresent()) {
            choices.add(choice(captureId, Projection.COMMAND));
        }
        choices.add(choice(captureId, Projection.DETAILS));
        return List.copyOf(choices);
    }

    private static SFMActionChoice choice(long captureId, Projection projection) {
        return SFMActionChoice.invoke(
                ID,
                captureId + " " + projection.path(),
                projection.displayText()
        );
    }

    @Override
    public Component title() {
        return Component.literal("Copy command-palette candidate");
    }

    @Override
    public Component description() {
        return Component.literal("Copy an exact capture-time projection of a command-palette candidate");
    }

    @Override
    public SFMClientActionRequirement<Host> requirement() {
        return context -> context.requireOriginatingHost(
                Host.class,
                Component.literal("Open candidate actions from a command-palette row")
        );
    }

    @Override
    public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        RequiredArgumentBuilder<SFMClientActionSource, Long> capture = RequiredArgumentBuilder.argument(
                CAPTURE_ARGUMENT,
                LongArgumentType.longArg(1)
        );
        for (Projection projection : Projection.values()) {
            capture.then(LiteralArgumentBuilder.<SFMClientActionSource>literal(projection.path())
                    .executes(context -> copy(context, projection)));
        }
        node.then(capture);
    }

    @Override
    public int execute(Host target, CommandContext<SFMClientActionSource> context) throws CommandSyntaxException {
        throw CAPTURE_REQUIRED.create();
    }

    private int copy(
            CommandContext<SFMClientActionSource> command,
            Projection projection
    ) throws CommandSyntaxException {
        SFMClientActionAvailability<Host> availability = requirement().resolve(command.getSource().context());
        if (!availability.isAvailable()) {
            throw new SimpleCommandExceptionType(availability.unavailableReason()).create();
        }
        return copyProjection(
                availability.target(),
                LongArgumentType.getLong(command, CAPTURE_ARGUMENT),
                projection,
                command.getSource()::sendFeedback
        );
    }

    int copyProjection(
            Host host,
            long captureId,
            Projection projection,
            Consumer<Component> feedback
    ) throws CommandSyntaxException {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(feedback, "feedback");
        SFMPaletteCandidateInspection inspection = host.paletteCandidateInspection(captureId)
                .orElseThrow(CAPTURE_EXPIRED::create);
        String payload = switch (projection) {
            case DISPLAY -> inspection.displayTextPayload();
            case SURFACE -> inspection.replacementSurfacePayload();
            case COMMAND -> inspection.canonicalCommandPayload().orElseThrow(CAPTURE_EXPIRED::create);
            case DETAILS -> inspection.detailsPayload();
        };
        clipboardWriter.accept(payload);
        feedback.accept(Component.literal("Copied candidate "
                + projection.displayText().substring("Copy ".length()).toLowerCase(Locale.ROOT)));
        return 1;
    }
}
