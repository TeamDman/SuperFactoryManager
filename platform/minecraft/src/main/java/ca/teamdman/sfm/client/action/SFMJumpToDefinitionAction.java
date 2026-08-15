package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.SFMActionChoice;
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.text_editor.SFMTextDocumentPanelState;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.symbol.SFMJumpToDefinitionController;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

/** Registered F12/palette route into the supervised symbol-navigation provider. */
public final class SFMJumpToDefinitionAction implements SFMClientAction<SFMClientActionContext> {
    public static final ResourceLocation ID = new ResourceLocation("sfm", "symbol/definition/open");
    private static final SimpleCommandExceptionType FAILED = new SimpleCommandExceptionType(
            Component.literal("Jump to definition could not be started"));
    private final SFMJumpToDefinitionController controller;

    public SFMJumpToDefinitionAction() {
        this(SFMJumpToDefinitionController.production(ID));
    }

    SFMJumpToDefinitionAction(SFMJumpToDefinitionController controller) {
        this.controller = Objects.requireNonNull(controller, "controller");
    }

    @Override
    public Component title() {
        return Component.literal("Open symbol definition");
    }

    @Override
    public Component description() {
        return Component.literal("Resolve the symbol at the captured editor cursor and open its source");
    }

    @Override
    public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return context -> {
            if (!context.originatingHostIsCurrent().getAsBoolean()) {
                return SFMClientActionAvailability.unavailable(
                        SFMClientActionContext.ORIGINATING_HOST_CHANGED.getComponent());
            }
            if (!(context.originatingHost() instanceof SFMScreenMultiplexer workspace)
                    || context.originatingPanelId() == null
                    || !(workspace.panelInstance(context.originatingPanelId())
                    instanceof SFMTextDocumentPanelState)) {
                return SFMClientActionAvailability.unavailable(Component.literal(
                        "Focus an SFM text editor before invoking jump to definition"));
            }
            return SFMClientActionAvailability.available(context);
        };
    }

    @Override
    public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        node.executes(this::invoke);
        node.then(LiteralArgumentBuilder.<SFMClientActionSource>literal("offer")
                .executes(this::offer));
        node.then(LiteralArgumentBuilder.<SFMClientActionSource>literal("select")
                .then(RequiredArgumentBuilder.<SFMClientActionSource, Long>argument(
                                "definition_session", LongArgumentType.longArg(1))
                        .then(RequiredArgumentBuilder.<SFMClientActionSource, Integer>argument(
                                        "definition_index", IntegerArgumentType.integer(0))
                                .then(RequiredArgumentBuilder.<SFMClientActionSource, String>argument(
                                                "definition_label", StringArgumentType.greedyString())
                                        .executes(this::select)))));
    }

    @Override
    public int execute(
            SFMClientActionContext target,
            CommandContext<SFMClientActionSource> context
    ) throws CommandSyntaxException {
        if (!controller.begin(target, context.getSource()::sendFeedback)) throw FAILED.create();
        return 1;
    }

    private int offer(CommandContext<SFMClientActionSource> context) throws CommandSyntaxException {
        SFMClientActionContext target = available(context);
        SFMCommandPaletteScreen.openChoices(
                target,
                Component.literal("Editor actions"),
                List.of(SFMActionChoice.invoke(ID, ""))
        );
        return 1;
    }

    private int select(CommandContext<SFMClientActionSource> context) throws CommandSyntaxException {
        SFMClientActionContext target = available(context);
        boolean selected = controller.select(
                target,
                LongArgumentType.getLong(context, "definition_session"),
                IntegerArgumentType.getInteger(context, "definition_index"),
                StringArgumentType.getString(context, "definition_label"),
                context.getSource()::sendFeedback
        );
        if (!selected) throw FAILED.create();
        return 1;
    }

    private SFMClientActionContext available(CommandContext<SFMClientActionSource> context)
            throws CommandSyntaxException {
        SFMClientActionAvailability<SFMClientActionContext> availability =
                requirement().resolve(context.getSource().context());
        if (!availability.isAvailable()) {
            throw new SimpleCommandExceptionType(availability.unavailableReason()).create();
        }
        return availability.target();
    }
}
