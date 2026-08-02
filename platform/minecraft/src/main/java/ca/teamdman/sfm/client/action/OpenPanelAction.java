package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.registry.SFMClientScreenTypes;
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.workspace.SFMClientScreenType;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelMetadata;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceSide;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Opens a registered typed panel scene through the canonical panel action family. */
public final class OpenPanelAction implements SFMClientAction<SFMClientActionContext> {
    public enum Direction {
        FOCUSED,
        LEFT,
        RIGHT,
        ABOVE,
        BELOW
    }

    private final Direction direction;
    private final Supplier<List<Map.Entry<ResourceLocation, SFMClientScreenType>>> screenTypes;

    public OpenPanelAction(Direction direction) {
        this(direction, OpenPanelAction::registeredScreenTypes);
    }

    OpenPanelAction(
            Direction direction,
            Supplier<List<Map.Entry<ResourceLocation, SFMClientScreenType>>> screenTypes
    ) {
        this.direction = Objects.requireNonNull(direction);
        this.screenTypes = Objects.requireNonNull(screenTypes);
    }

    @Override
    public Component title() {
        return Component.literal(direction == Direction.FOCUSED
                ? "Open panel"
                : "Open panel " + direction.name().toLowerCase());
    }

    @Override
    public Component description() {
        return Component.literal("Open a typed SFM scene in the panel workspace");
    }

    @Override
    public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return context -> context.originatingHostIsCurrent().getAsBoolean()
                ? SFMClientActionAvailability.available(context)
                : SFMClientActionAvailability.unavailable(SFMClientActionContext.ORIGINATING_HOST_CHANGED.getComponent());
    }

    @Override
    public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        for (Map.Entry<ResourceLocation, SFMClientScreenType> registration : screenTypes.get()) {
            node.then(registration.getValue().createCommandNode(registration.getKey(), this::open));
        }
    }

    @Override
    public int execute(
            SFMClientActionContext target,
            CommandContext<SFMClientActionSource> context
    ) throws CommandSyntaxException {
        throw new SimpleCommandExceptionType(Component.literal(
                "Provide a panel scene and any required scene arguments")).create();
    }

    private int open(CommandContext<SFMClientActionSource> commandContext, SFMScreenPanel panel) {
        SFMClientActionContext actionContext = commandContext.getSource().context();
        return openPanel(actionContext, panel, direction);
    }

    static int openPanel(SFMClientActionContext actionContext, SFMScreenPanel panel, Direction direction) {
        @Nullable Screen origin = actionContext.originatingHost() instanceof Screen screen ? screen : null;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof SFMCommandPaletteScreen palette) palette.onClose();
        if (direction == Direction.FOCUSED) {
            SFMScreenMultiplexer.openFocused(origin, panel, SFMWorkspacePanelMetadata.ordinary());
            return 1;
        }
        SFMWorkspaceSide side = switch (direction) {
            case LEFT -> SFMWorkspaceSide.LEFT;
            case RIGHT -> SFMWorkspaceSide.RIGHT;
            case ABOVE -> SFMWorkspaceSide.ABOVE;
            case BELOW -> SFMWorkspaceSide.BELOW;
            case FOCUSED -> throw new AssertionError("Focused panel opening was handled above");
        };
        SFMScreenMultiplexer.openToSide(origin, side, panel);
        return 1;
    }

    private static List<Map.Entry<ResourceLocation, SFMClientScreenType>> registeredScreenTypes() {
        List<Map.Entry<ResourceLocation, SFMClientScreenType>> registrations = new ArrayList<>();
        for (ResourceLocation id : SFMClientScreenTypes.registry().keys()) {
            registrations.add(Map.entry(id, Objects.requireNonNull(SFMClientScreenTypes.registry().get(id))));
        }
        registrations.sort(Comparator.comparing(entry -> entry.getKey().toString()));
        return registrations;
    }
}
