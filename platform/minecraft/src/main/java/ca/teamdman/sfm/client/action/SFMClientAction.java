package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.presentation.SFMItemIcon;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

public interface SFMClientAction<T> {
    Component title();

    Component description();

    /** Presentation metadata; absent icons retain the text-only row fallback. */
    default Optional<SFMItemIcon> itemIcon(SFMClientActionContext context) {
        return Optional.empty();
    }

    SFMClientActionRequirement<T> requirement();

    default boolean isPinnable() {
        return false;
    }

    int execute(
            T target,
            CommandContext<SFMClientActionSource> context
    ) throws CommandSyntaxException;

    default LiteralArgumentBuilder<SFMClientActionSource> createCommandNode(ResourceLocation actionId) {
        return createCommandNode(actionId.toString());
    }

    default LiteralArgumentBuilder<SFMClientActionSource> createCommandNode(String commandLiteral) {
        LiteralArgumentBuilder<SFMClientActionSource> node = LiteralArgumentBuilder
                .<SFMClientActionSource>literal(commandLiteral)
                .requires(source -> requirement().resolve(source.context()).isAvailable());
        configureCommandNode(node);
        return node;
    }

    default void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        node.executes(this::invoke);
    }

    default int invoke(CommandContext<SFMClientActionSource> context) throws CommandSyntaxException {
        SFMClientActionAvailability<T> availability = requirement().resolve(context.getSource().context());
        if (!availability.isAvailable()) {
            throw new SimpleCommandExceptionType(availability.unavailableReason()).create();
        }
        return execute(availability.target(), context);
    }
}
