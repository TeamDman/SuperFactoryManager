package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.network.chat.Component;

/** Focuses a visible panel or traverses the workspace's visible panel order. */
public final class FocusPanelAction implements SFMClientAction<SFMScreenMultiplexer> {
    public enum Operation {
        NEXT,
        PREVIOUS,
        INDEX
    }

    private final Operation operation;

    public FocusPanelAction(Operation operation) {
        this.operation = operation;
    }

    @Override
    public Component title() {
        return Component.literal(switch (operation) {
            case NEXT -> "Focus next panel";
            case PREVIOUS -> "Focus previous panel";
            case INDEX -> "Focus panel by index";
        });
    }

    @Override
    public Component description() {
        return Component.literal(switch (operation) {
            case NEXT, PREVIOUS -> "Move workspace focus through visible panels and stacked entries";
            case INDEX -> "Focus a visible panel by its one-based workspace index";
        });
    }

    @Override
    public SFMClientActionRequirement<SFMScreenMultiplexer> requirement() {
        return PanelActionSupport::resolve;
    }

    @Override
    public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        if (operation == Operation.INDEX) {
            node.then(com.mojang.brigadier.builder.RequiredArgumentBuilder
                    .<SFMClientActionSource, Integer>argument(
                            "index", IntegerArgumentType.integer(1, 9))
                    .executes(this::invoke));
        } else {
            node.executes(this::invoke);
        }
    }

    @Override
    public int execute(
            SFMScreenMultiplexer workspace,
            CommandContext<SFMClientActionSource> context
    ) {
        boolean changed = switch (operation) {
            case NEXT -> workspace.traversePanelFocus(1);
            case PREVIOUS -> workspace.traversePanelFocus(-1);
            case INDEX -> workspace.focusVisiblePanel(IntegerArgumentType.getInteger(context, "index"));
        };
        return PanelActionSupport.closePaletteAfter(changed ? 1 : 0);
    }
}
