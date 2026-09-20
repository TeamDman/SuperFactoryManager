package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.text_editor.SFMTextDocumentPanelState;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.symbol.SFMFindReferencesController;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Registered Alt+F7/palette route to a persistent references explorer. */
public final class SFMFindReferencesAction implements SFMClientAction<SFMClientActionContext> {
    public static final ResourceLocation ID = new ResourceLocation("sfm", "symbol/references/open");
    private static final SimpleCommandExceptionType FAILED = new SimpleCommandExceptionType(
            Component.literal("Find references could not be started"));
    private final SFMFindReferencesController controller;

    public SFMFindReferencesAction() {
        this(SFMFindReferencesController.production());
    }

    SFMFindReferencesAction(SFMFindReferencesController controller) {
        this.controller = Objects.requireNonNull(controller, "controller");
    }

    @Override public Component title() {
        return Component.literal("Find symbol references");
    }

    @Override public Component description() {
        return Component.literal("Resolve the symbol at the captured editor cursor into a persistent explorer");
    }

    @Override public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return context -> {
            if (!context.originatingHostIsCurrent().getAsBoolean()) {
                return SFMClientActionAvailability.unavailable(
                        SFMClientActionContext.ORIGINATING_HOST_CHANGED.getComponent());
            }
            if (!(context.originatingHost() instanceof SFMScreenMultiplexer workspace)
                    || context.originatingPanelId() == null
                    || !(workspace.panelInstance(context.originatingPanelId()) instanceof SFMTextDocumentPanelState)) {
                return SFMClientActionAvailability.unavailable(Component.literal(
                        "Focus an SFM text editor before invoking find references"));
            }
            return SFMClientActionAvailability.available(context);
        };
    }

    @Override public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        node.executes(this::invoke);
    }

    @Override public int execute(
            SFMClientActionContext target,
            CommandContext<SFMClientActionSource> context
    ) throws CommandSyntaxException {
        if (!controller.begin(target, context.getSource()::sendFeedback)) throw FAILED.create();
        return 1;
    }
}
