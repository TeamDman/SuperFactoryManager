package ca.teamdman.sfm.client.action;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Changes Minecraft's GUI scale option from the command palette. */
public final class SFMGuiScaleAction implements SFMClientAction<SFMClientActionContext> {
    public enum Operation {
        SET,
        INCREMENT,
        DECREMENT
    }

    private final Operation operation;

    public SFMGuiScaleAction(Operation operation) {
        this.operation = operation;
    }

    @Override
    public Component title() {
        return Component.literal(switch (operation) {
            case SET -> "Set GUI scale";
            case INCREMENT -> "Increment GUI scale";
            case DECREMENT -> "Decrement GUI scale";
        });
    }

    @Override
    public Component description() {
        return Component.literal(switch (operation) {
            case SET -> "Set GUI scale; use 0 for Auto";
            case INCREMENT -> "Increase the GUI scale by one step";
            case DECREMENT -> "Decrease the GUI scale by one step; 1 becomes Auto";
        });
    }

    @Override
    public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return SFMClientActionAvailability::available;
    }

    @Override
    public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        if (operation == Operation.SET) {
            node.then(RequiredArgumentBuilder.<SFMClientActionSource, Integer>argument(
                    "scale", IntegerArgumentType.integer()
            ).suggests((context, builder) -> {
                int maximum = maximumScale(Minecraft.getInstance());
                for (int scale : suggestedScaleValues(maximum)) {
                    builder.suggest(scale);
                }
                return builder.buildFuture();
            }).executes(this::invoke));
        } else {
            node.executes(this::invoke);
        }
    }

    @Override
    public int execute(SFMClientActionContext target, CommandContext<SFMClientActionSource> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Minecraft minecraft = Minecraft.getInstance();
        int maximum = maximumScale(minecraft);
        int requested;
        try {
            requested = switch (operation) {
                case SET -> IntegerArgumentType.getInteger(context, "scale");
                case INCREMENT -> nextScale(minecraft.options.guiScale().get(), maximum, Operation.INCREMENT);
                case DECREMENT -> nextScale(minecraft.options.guiScale().get(), maximum, Operation.DECREMENT);
            };
            requested = validateScale(requested, maximum);
        } catch (IllegalArgumentException error) {
            throw new SimpleCommandExceptionType(Component.literal(error.getMessage())).create();
        }
        minecraft.options.guiScale().set(requested);
        minecraft.options.save();
        minecraft.resizeDisplay();
        context.getSource().sendFeedback(Component.literal("GUI scale set to " + displayScale(requested)));
        return PanelActionSupport.closePaletteAfter(1);
    }

    static int nextScale(int current, int maximum, Operation operation) {
        validateScale(current, maximum);
        return switch (operation) {
            case SET -> current;
            case INCREMENT -> Math.min(maximum, Math.max(1, current + 1));
            case DECREMENT -> Math.max(0, current - 1);
        };
    }

    static int validateScale(int requested, int maximum) {
        if (maximum < 1) {
            throw new IllegalArgumentException("Minecraft reported no valid GUI scale values");
        }
        if (requested < 0 || requested > maximum) {
            throw new IllegalArgumentException(
                    "GUI scale must be between 0 (Auto) and " + maximum
            );
        }
        return requested;
    }

    static int maximumScale(Minecraft minecraft) {
        return minecraft.getWindow().calculateScale(0, minecraft.isEnforceUnicode());
    }

    static List<Integer> suggestedScaleValues(int maximum) {
        List<Integer> values = new ArrayList<>();
        for (int scale = 0; scale <= maximum; scale++) {
            values.add(scale);
        }
        return values;
    }

    private static String displayScale(int scale) {
        return scale == 0 ? "Auto" : Integer.toString(scale);
    }
}
