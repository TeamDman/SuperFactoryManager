package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Applies a GUI-scale override to the focused panel entry. */
public final class PanelScaleAction implements SFMClientAction<SFMScreenMultiplexer> {
    public enum Operation { SET, INCREASE, DECREASE, CLEAR }

    private final Operation operation;

    public PanelScaleAction(Operation operation) {
        this.operation = operation;
    }

    @Override
    public Component title() {
        return Component.literal(switch (operation) {
            case SET -> "Set panel GUI scale";
            case INCREASE -> "Increase panel GUI scale";
            case DECREASE -> "Decrease panel GUI scale";
            case CLEAR -> "Clear panel GUI scale";
        });
    }

    @Override
    public Component description() {
        return Component.literal("Change only the focused panel entry's GUI scale");
    }

    @Override
    public SFMClientActionRequirement<SFMScreenMultiplexer> requirement() {
        return PanelActionSupport::resolve;
    }

    @Override
    public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        if (operation == Operation.SET) {
            node.then(RequiredArgumentBuilder.<SFMClientActionSource, Integer>argument(
                    "scale", IntegerArgumentType.integer(1)
            ).suggests((context, builder) -> {
                int maximum = maximumScale(Minecraft.getInstance());
                for (int scale = 1; scale <= maximum; scale++) builder.suggest(scale);
                return builder.buildFuture();
            }).executes(this::invoke));
        } else {
            node.executes(this::invoke);
        }
    }

    @Override
    public int execute(SFMScreenMultiplexer workspace, CommandContext<SFMClientActionSource> context)
            throws CommandSyntaxException {
        int maximum = maximumScale(Minecraft.getInstance());
        var capturedPanelId = context.getSource().context().originatingPanelId();
        var targetPanelId = capturedPanelId == null
                ? workspace.focusedPanelId()
                : capturedPanelId;
        var targetEntry = workspace.panelSlotEntries(targetPanelId).stream()
                .filter(entry -> entry.id().equals(targetPanelId))
                .findFirst();
        @Nullable Integer currentOverride = targetEntry.isEmpty()
                ? null
                : targetEntry.get().metadata().guiScaleOverride();
        int inheritedScale = inheritedScale(Minecraft.getInstance(), maximum);
        Integer requested = switch (operation) {
            case SET -> IntegerArgumentType.getInteger(context, "scale");
            case INCREASE, DECREASE -> adjustedScale(operation, currentOverride, inheritedScale);
            case CLEAR -> null;
        };
        if (requested != null && (requested < 1 || requested > maximum)) {
            if (operation == Operation.INCREASE || operation == Operation.DECREASE) {
                workspace.showGuiScaleToast(currentOverride, inheritedScale, true);
            }
            throw new SimpleCommandExceptionType(Component.literal(
                    "Panel GUI scale must be between 1 and " + maximum)).create();
        }
        boolean changed = capturedPanelId == null
                ? workspace.setFocusedGuiScale(requested)
                : workspace.setPanelGuiScale(capturedPanelId, requested);
        if (!changed) {
            if (operation == Operation.CLEAR) {
                workspace.showGuiScaleToast(null, inheritedScale, false);
            }
            return 0;
        }
        workspace.showGuiScaleToast(requested, inheritedScale, false);
        context.getSource().sendFeedback(Component.literal(
                requested == null ? "Panel GUI scale cleared" : "Panel GUI scale set to " + requested));
        return PanelActionSupport.closePaletteAfter(1);
    }

    static int maximumScale(Minecraft minecraft) {
        return Math.max(1, minecraft.getWindow().calculateScale(0, minecraft.isEnforceUnicode()));
    }

    /**
     * Auto/inherited occupies the position immediately above the matching
     * explicit scale. If auto currently resolves to 4, increase selects 5,
     * while decrease first pins the visually equivalent explicit 4; further
     * decrements then continue to 3.
     */
    static int adjustedScale(
            Operation operation,
            @Nullable Integer currentOverride,
            int inheritedScale
    ) {
        if (operation != Operation.INCREASE && operation != Operation.DECREASE) {
            throw new IllegalArgumentException("Only relative panel-scale operations can be adjusted");
        }
        if (currentOverride == null) {
            return operation == Operation.INCREASE ? inheritedScale + 1 : inheritedScale;
        }
        return currentOverride + (operation == Operation.INCREASE ? 1 : -1);
    }

    static int inheritedScale(Minecraft minecraft, int maximum) {
        int resolved = (int) Math.round(minecraft.getWindow().getGuiScale());
        return Math.max(1, Math.min(maximum, resolved));
    }
}
