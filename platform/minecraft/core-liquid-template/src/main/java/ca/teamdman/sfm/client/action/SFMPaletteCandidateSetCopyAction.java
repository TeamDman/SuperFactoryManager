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
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/** Copies one complete capture-time projection of the palette's candidate set. */
public final class SFMPaletteCandidateSetCopyAction
        implements SFMClientAction<SFMPaletteCandidateSetCopyAction.Host> {
    public static final ResourceLocation ID = new ResourceLocation(SFM.MOD_ID, "palette/candidates/copy");
    private static final String CAPTURE_ARGUMENT = "candidate_set_capture";
    private static final SimpleCommandExceptionType CAPTURE_REQUIRED = new SimpleCommandExceptionType(
            Component.literal("A captured command-palette candidate set is required"));
    private static final SimpleCommandExceptionType CAPTURE_EXPIRED = new SimpleCommandExceptionType(
            Component.literal("The captured command-palette candidate set is no longer available"));

    public enum Projection {
        DISPLAY("display", "Copy all candidate display texts"),
        SURFACE("surface", "Copy all candidate surface values"),
        COMMAND("command", "Copy all canonical candidate commands"),
        DETAILS("details", "Copy complete details for all candidates");

        private final String path;
        private final String displayText;

        Projection(String path, String displayText) {
            this.path = path;
            this.displayText = displayText;
        }
    }

    public interface Host {
        Optional<List<SFMPaletteCandidateInspection>> paletteCandidateSetInspection(long captureId);
    }

    private final Consumer<String> clipboardWriter;

    public SFMPaletteCandidateSetCopyAction() {
        this(text -> Minecraft.getInstance().keyboardHandler.setClipboard(text));
    }

    SFMPaletteCandidateSetCopyAction(Consumer<String> clipboardWriter) {
        this.clipboardWriter = Objects.requireNonNull(clipboardWriter, "clipboardWriter");
    }

    public static List<SFMActionChoice> choices(long captureId, List<SFMPaletteCandidateInspection> candidates) {
        List<SFMPaletteCandidateInspection> captured = List.copyOf(candidates);
        if (captured.isEmpty()) return List.of();
        ArrayList<SFMActionChoice> choices = new ArrayList<>();
        choices.add(choice(captureId, Projection.DISPLAY));
        choices.add(choice(captureId, Projection.SURFACE));
        if (captured.stream().anyMatch(candidate -> candidate.canonicalCommandPayload().isPresent())) {
            choices.add(choice(captureId, Projection.COMMAND));
        }
        choices.add(choice(captureId, Projection.DETAILS));
        return List.copyOf(choices);
    }

    private static SFMActionChoice choice(long captureId, Projection projection) {
        return SFMActionChoice.invoke(ID, captureId + " " + projection.path, projection.displayText);
    }

    @Override
    public Component title() {
        return Component.literal("Copy command-palette candidates");
    }

    @Override
    public Component description() {
        return Component.literal("Copy a complete capture-time projection of every displayed candidate");
    }

    @Override
    public SFMClientActionRequirement<Host> requirement() {
        return context -> context.requireOriginatingHost(
                Host.class,
                Component.literal("Open candidate-set actions from a command palette")
        );
    }

    @Override
    public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        RequiredArgumentBuilder<SFMClientActionSource, Long> capture = RequiredArgumentBuilder.argument(
                CAPTURE_ARGUMENT,
                LongArgumentType.longArg(1)
        );
        for (Projection projection : Projection.values()) {
            capture.then(LiteralArgumentBuilder.<SFMClientActionSource>literal(projection.path)
                    .executes(context -> copy(context, projection)));
        }
        node.then(capture);
    }

    @Override
    public int execute(Host target, CommandContext<SFMClientActionSource> context) throws CommandSyntaxException {
        throw CAPTURE_REQUIRED.create();
    }

    private int copy(CommandContext<SFMClientActionSource> command, Projection projection)
            throws CommandSyntaxException {
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

    int copyProjection(Host host, long captureId, Projection projection, Consumer<Component> feedback)
            throws CommandSyntaxException {
        List<SFMPaletteCandidateInspection> candidates = host.paletteCandidateSetInspection(captureId)
                .orElseThrow(CAPTURE_EXPIRED::create);
        List<String> values = switch (projection) {
            case DISPLAY -> candidates.stream().map(SFMPaletteCandidateInspection::displayRepresentationPayload).toList();
            case SURFACE -> candidates.stream().map(SFMPaletteCandidateInspection::replacementSurfacePayload).toList();
            case COMMAND -> candidates.stream().map(SFMPaletteCandidateInspection::canonicalCommandPayload)
                    .flatMap(Optional::stream).toList();
            case DETAILS -> java.util.stream.IntStream.range(0, candidates.size())
                    .mapToObj(index -> "candidate[" + index + "]" + System.lineSeparator()
                            + candidates.get(index).detailsPayload())
                    .toList();
        };
        if (values.isEmpty()) throw CAPTURE_EXPIRED.create();
        clipboardWriter.accept(String.join(System.lineSeparator(), values));
        feedback.accept(Component.literal("Copied " + values.size() + " command-palette candidate value(s)"));
        return values.size();
    }
}
