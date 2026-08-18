package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceCopyFeedback;
import ca.teamdman.sfm.client.symbol.SFMSymbolInspectionFormatters;
import ca.teamdman.sfm.client.symbol.SFMSymbolInspectionSessions;
import ca.teamdman.sfm.client.symbol.SFMSymbolInspectionSnapshot;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Objects;

/** Copies one deterministic projection of a capture-time symbol inspection. */
public final class SFMSymbolCopyAction implements SFMClientAction<SFMClientActionContext> {
    private static final String SESSION_ARGUMENT = "symbol_inspection_session";
    private static final SimpleCommandExceptionType SESSION_REQUIRED = new SimpleCommandExceptionType(
            Component.literal("A captured symbol inspection session is required"));
    private static final SimpleCommandExceptionType SESSION_EXPIRED = new SimpleCommandExceptionType(
            Component.literal("The captured symbol inspection session is no longer available"));

    private final SFMSymbolInspectionFormatters.Projection projection;
    private final SFMSymbolInspectionSessions sessions;
    private final SFMWorkspaceCopyFeedback copyFeedback;

    public SFMSymbolCopyAction(SFMSymbolInspectionFormatters.Projection projection) {
        this(projection, SFMSymbolInspectionSessions.shared(), SFMWorkspaceCopyFeedback.production());
    }

    SFMSymbolCopyAction(
            SFMSymbolInspectionFormatters.Projection projection,
            SFMSymbolInspectionSessions sessions,
            SFMWorkspaceCopyFeedback copyFeedback
    ) {
        this.projection = Objects.requireNonNull(projection, "projection");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.copyFeedback = Objects.requireNonNull(copyFeedback, "copyFeedback");
    }

    public static ResourceLocation id(SFMSymbolInspectionFormatters.Projection projection) {
        return new ResourceLocation("sfm", "symbol/copy/" + projection.path());
    }

    @Override
    public Component title() {
        return Component.literal("Copy symbol " + displayName(projection));
    }

    @Override
    public Component description() {
        return Component.literal("Copy the " + displayName(projection)
                + " projection from the exact captured Java editor point");
    }

    @Override
    public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return context -> {
            if (!context.originatingHostIsCurrent().getAsBoolean()) {
                return SFMClientActionAvailability.unavailable(
                        SFMClientActionContext.ORIGINATING_HOST_CHANGED.getComponent());
            }
            if (!(context.originatingHost() instanceof SFMScreenMultiplexer)) {
                return SFMClientActionAvailability.unavailable(Component.literal(
                        "Open symbol inspection from an SFM workspace"));
            }
            return SFMClientActionAvailability.available(context);
        };
    }

    @Override
    public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        node.then(RequiredArgumentBuilder.<SFMClientActionSource, Long>argument(
                        SESSION_ARGUMENT, LongArgumentType.longArg(1))
                .executes(this::copy));
    }

    @Override
    public int execute(
            SFMClientActionContext target,
            CommandContext<SFMClientActionSource> context
    ) throws CommandSyntaxException {
        throw SESSION_REQUIRED.create();
    }

    private int copy(CommandContext<SFMClientActionSource> command) throws CommandSyntaxException {
        SFMClientActionAvailability<SFMClientActionContext> availability =
                requirement().resolve(command.getSource().context());
        if (!availability.isAvailable()) {
            throw new SimpleCommandExceptionType(availability.unavailableReason()).create();
        }
        long sessionId = LongArgumentType.getLong(command, SESSION_ARGUMENT);
        SFMSymbolInspectionSnapshot snapshot = sessions.find(sessionId).orElseThrow(SESSION_EXPIRED::create);
        String clipboard = SFMSymbolInspectionFormatters.format(snapshot, projection);
        copyFeedback.copy(
                availability.target(),
                clipboard,
                Component.literal("Copied symbol " + displayName(projection) + " to the clipboard")
        );
        return 1;
    }

    static String displayName(SFMSymbolInspectionFormatters.Projection projection) {
        return projection.path().replace('-', ' ').toLowerCase(Locale.ROOT);
    }
}
