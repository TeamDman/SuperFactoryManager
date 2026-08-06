package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.terminal.SFMTerminalPanel;
import ca.teamdman.sfm.client.terminal.SFMTerminalPropertiesPanel;
import ca.teamdman.sfm.client.terminal.SFMTerminalPropertiesSnapshot;
import ca.teamdman.sfm.client.terminal.SFMTerminalTuningChangeResult;
import ca.teamdman.sfm.client.terminal.SFMTerminalTuningOperation;
import ca.teamdman.sfm.client.terminal.SFMTerminalTuningSettings;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;

import java.util.Optional;

/** Mutates one exact focused terminal, or the stable owner of its properties panel. */
public final class TerminalPropertiesAction implements SFMClientAction<SFMScreenMultiplexer> {
    private final SFMTerminalTuningOperation operation;

    public TerminalPropertiesAction(SFMTerminalTuningOperation operation) {
        this.operation = operation;
    }

    @Override
    public Component title() {
        return Component.literal(switch (operation) {
            case SURFACE_AUTO -> "Use allocated terminal surface";
            case SURFACE_SET -> "Set terminal surface";
            case SURFACE_WIDTH_INCREASE -> "Increase terminal surface width";
            case SURFACE_WIDTH_DECREASE -> "Decrease terminal surface width";
            case SURFACE_HEIGHT_INCREASE -> "Increase terminal surface height";
            case SURFACE_HEIGHT_DECREASE -> "Decrease terminal surface height";
            case FONT_AUTO -> "Fit terminal font automatically";
            case FONT_SET -> "Set terminal font pixels";
            case FONT_INCREASE -> "Increase terminal font pixels";
            case FONT_DECREASE -> "Decrease terminal font pixels";
            case CELLS_AUTO -> "Use allocated terminal cell grid";
            case CELLS_SET -> "Set terminal cell grid";
            case COLUMNS_INCREASE -> "Increase terminal columns";
            case COLUMNS_DECREASE -> "Decrease terminal columns";
            case ROWS_INCREASE -> "Increase terminal rows";
            case ROWS_DECREASE -> "Decrease terminal rows";
        });
    }

    @Override
    public Component description() {
        return Component.literal("Tune the focused terminal or the exact terminal owned by its properties panel");
    }

    @Override
    public SFMClientActionRequirement<SFMScreenMultiplexer> requirement() {
        return context -> {
            SFMClientActionAvailability<SFMScreenMultiplexer> workspace =
                    PanelActionSupport.resolve(context);
            if (!workspace.isAvailable()) {
                return SFMClientActionAvailability.unavailable(workspace.unavailableReason());
            }
            return target(workspace.target(), context).isPresent()
                    ? SFMClientActionAvailability.available(workspace.target())
                    : SFMClientActionAvailability.unavailable(Component.literal(
                    "Focus a Rust terminal or its attached terminal-properties panel"));
        };
    }

    @Override
    public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        switch (operation) {
            case SURFACE_SET -> node.then(integer("width", 1, SFMTerminalRasterMaximum.WIDTH)
                    .suggests((context, builder) -> suggestSurface(context, builder, true))
                    .then(integer("height", 1, SFMTerminalRasterMaximum.HEIGHT)
                            .suggests((context, builder) -> suggestSurface(context, builder, false))
                            .executes(this::invoke)));
            case FONT_SET -> node.then(integer(
                    "pixel-size",
                    SFMTerminalTuningSettings.MIN_FONT_PIXEL_SIZE,
                    SFMTerminalTuningSettings.MAX_FONT_PIXEL_SIZE
            ).suggests((context, builder) -> {
                target(context).map(SFMTerminalPanel::propertiesSnapshot)
                        .map(TerminalPropertiesAction::acceptedFont)
                        .ifPresent(builder::suggest);
                for (int size : new int[]{8, 12, 16, 20, 24, 32, 48, 64}) builder.suggest(size);
                return builder.buildFuture();
            }).executes(this::invoke));
            case CELLS_SET -> node.then(integer("columns", 1, SFMTerminalTuningSettings.MAX_COLUMNS)
                    .suggests((context, builder) -> suggestCells(context, builder, true))
                    .then(integer("rows", 1, SFMTerminalTuningSettings.MAX_ROWS)
                            .suggests((context, builder) -> suggestCells(context, builder, false))
                            .executes(this::invoke)));
            default -> node.executes(this::invoke);
        }
    }

    @Override
    public int execute(SFMScreenMultiplexer workspace, CommandContext<SFMClientActionSource> context)
            throws CommandSyntaxException {
        SFMTerminalPanel terminal = target(workspace, context.getSource().context()).orElseThrow(() ->
                new SimpleCommandExceptionType(Component.literal(
                        "Focus a Rust terminal or its terminal-properties panel")).create());
        int first = switch (operation) {
            case SURFACE_SET -> IntegerArgumentType.getInteger(context, "width");
            case FONT_SET -> IntegerArgumentType.getInteger(context, "pixel-size");
            case CELLS_SET -> IntegerArgumentType.getInteger(context, "columns");
            default -> 0;
        };
        int second = switch (operation) {
            case SURFACE_SET -> IntegerArgumentType.getInteger(context, "height");
            case CELLS_SET -> IntegerArgumentType.getInteger(context, "rows");
            default -> 0;
        };
        SFMTerminalTuningChangeResult result = terminal.requestTuning(operation, first, second);
        if (!result.accepted()) {
            throw new SimpleCommandExceptionType(Component.literal(result.message())).create();
        }
        context.getSource().sendFeedback(Component.literal(result.message()));
        return PanelActionSupport.closePaletteAfter(1);
    }

    private static RequiredArgumentBuilder<SFMClientActionSource, Integer> integer(
            String name,
            int minimum,
            int maximum
    ) {
        return RequiredArgumentBuilder.argument(name, IntegerArgumentType.integer(minimum, maximum));
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestSurface(
            CommandContext<SFMClientActionSource> context,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder,
            boolean width
    ) {
        target(context).map(SFMTerminalPanel::propertiesSnapshot)
                .map(SFMTerminalPropertiesSnapshot::effective)
                .map(value -> width ? value.surfaceWidth() : value.surfaceHeight())
                .ifPresent(builder::suggest);
        for (int value : width ? new int[]{640, 1280, 1920, 2560, 3840, 4096}
                : new int[]{360, 720, 1080, 1440, 2054, 2160, 4096}) builder.suggest(value);
        return builder.buildFuture();
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestCells(
            CommandContext<SFMClientActionSource> context,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder,
            boolean columns
    ) {
        target(context).map(SFMTerminalPanel::propertiesSnapshot)
                .map(SFMTerminalPropertiesSnapshot::effective)
                .map(value -> columns ? value.columns() : value.rows())
                .ifPresent(builder::suggest);
        for (int value : columns ? new int[]{40, 80, 100, 120, 160, 200, 240}
                : new int[]{20, 24, 30, 40, 60, 80, 120}) builder.suggest(value);
        return builder.buildFuture();
    }

    private static Optional<SFMTerminalPanel> target(CommandContext<SFMClientActionSource> context) {
        SFMClientActionAvailability<SFMScreenMultiplexer> availability =
                PanelActionSupport.resolve(context.getSource().context());
        return availability.isAvailable()
                ? target(availability.target(), context.getSource().context())
                : Optional.empty();
    }

    private static Optional<SFMTerminalPanel> target(
            SFMScreenMultiplexer workspace,
            SFMClientActionContext context
    ) {
        Object panel = PanelActionSupport.capturedPanel(workspace, context).orElse(null);
        if (panel instanceof SFMTerminalPanel terminal) {
            return Optional.of(terminal);
        }
        if (panel instanceof SFMTerminalPropertiesPanel properties) {
            return properties.ownerTerminal();
        }
        return Optional.empty();
    }

    private static int acceptedFont(SFMTerminalPropertiesSnapshot snapshot) {
        return snapshot.acceptedFrame()
                .map(SFMTerminalPropertiesSnapshot.AcceptedFrame::fontPixelSize)
                .filter(value -> value > 0)
                .orElse(SFMTerminalTuningSettings.MIN_FONT_PIXEL_SIZE);
    }

    private static final class SFMTerminalRasterMaximum {
        private static final int WIDTH = 4096;
        private static final int HEIGHT = 4096;

        private SFMTerminalRasterMaximum() {
        }
    }
}
