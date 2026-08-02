package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.network.chat.Component;

/** Rotates visible panel content or per-entry scale assignments. */
public final class RotatePanelAction implements SFMClientAction<SFMScreenMultiplexer> {
    public enum Kind { CONTENT, SCALE }
    public enum Direction { LEFT, RIGHT }

    private final Kind kind;
    private final Direction direction;

    public RotatePanelAction(Kind kind, Direction direction) {
        this.kind = kind;
        this.direction = direction;
    }

    @Override
    public Component title() {
        return Component.literal("Rotate " + kind.name().toLowerCase() + " " + direction.name().toLowerCase());
    }

    @Override
    public Component description() {
        return Component.literal("Rotate visible panel assignments while preserving slot geometry");
    }

    @Override
    public SFMClientActionRequirement<SFMScreenMultiplexer> requirement() {
        return PanelActionSupport::resolve;
    }

    @Override
    public int execute(SFMScreenMultiplexer workspace, CommandContext<SFMClientActionSource> context) {
        int delta = direction == Direction.RIGHT ? 1 : -1;
        boolean changed = kind == Kind.CONTENT
                ? workspace.rotateVisibleContent(delta)
                : workspace.rotateVisibleScale(delta);
        return PanelActionSupport.closePaletteAfter(changed ? 1 : 0);
    }
}
