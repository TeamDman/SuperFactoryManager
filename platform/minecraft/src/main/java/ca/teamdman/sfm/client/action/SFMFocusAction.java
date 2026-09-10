package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Moves keyboard focus to a stable element id without relying on Tab traversal. */
public final class SFMFocusAction implements SFMClientAction<SFMFocusTargetHost> {
    @SFMLocalizationDatagen
    public static final LocalizationEntry TITLE = new LocalizationEntry(
            "gui.sfm.client_action.focus.title",
            "Focus UI element"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry DESCRIPTION = new LocalizationEntry(
            "gui.sfm.client_action.focus.description",
            "Move keyboard focus to a stable element without changing its content"
    );

    @Override
    public Component title() {
        return TITLE.getComponent();
    }

    @Override
    public Component description() {
        return DESCRIPTION.getComponent();
    }

    @Override
    public SFMClientActionRequirement<SFMFocusTargetHost> requirement() {
        return context -> {
            if (context.originatingHost() instanceof SFMFocusTargetHost captured) {
                return SFMClientActionAvailability.available(captured);
            }
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null && minecraft.screen instanceof SFMFocusTargetHost current) {
                return SFMClientActionAvailability.available(current);
            }
            return SFMClientActionAvailability.unavailable(
                    Component.literal("The current SFM surface has no addressable focus targets"));
        };
    }

    @Override
    public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        node.then(RequiredArgumentBuilder.<SFMClientActionSource, String>argument(
                        "focus_target",
                        StringArgumentType.word()
                )
                .suggests((context, suggestions) -> {
                    var availability = requirement().resolve(context.getSource().context());
                    if (availability.isAvailable()) {
                        availability.target().focusTargetIds().forEach(suggestions::suggest);
                    }
                    return suggestions.buildFuture();
                })
                .executes(this::invoke));
    }

    @Override
    public int execute(
            SFMFocusTargetHost target,
            CommandContext<SFMClientActionSource> context
    ) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String targetId = StringArgumentType.getString(context, "focus_target");
        if (!target.focusTargetIds().contains(targetId)) {
            throw new SimpleCommandExceptionType(Component.literal(
                    "Unknown focus target `" + targetId + "`; expected one of " + target.focusTargetIds())).create();
        }
        return target.focusTarget(targetId) ? 1 : 0;
    }
}
