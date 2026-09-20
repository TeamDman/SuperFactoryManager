package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.registry.SFMClientScreenTypes;
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.workspace.SFMClientScreenType;
import ca.teamdman.sfm.client.screen.workspace.SFMPanelReopenRecipe;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelMetadata;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelIntentResult;
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

    private int open(
            CommandContext<SFMClientActionSource> commandContext,
            SFMPanelReopenRecipe recipe
    ) {
        SFMClientActionContext actionContext = commandContext.getSource().context();
        return openPanel(actionContext, recipe.reopen(), direction, recipe);
    }

    /** Shared registered-action panel-opening seam used by typed scene authorities. */
    public static int openPanel(SFMClientActionContext actionContext, SFMScreenPanel panel, Direction direction) {
        return openPanel(actionContext, panel, direction, null);
    }

    public static int openPanel(
            SFMClientActionContext actionContext,
            SFMScreenPanel panel,
            Direction direction,
            @Nullable SFMPanelReopenRecipe reopenRecipe
    ) {
        @Nullable Screen origin = actionContext.originatingHost() instanceof Screen screen ? screen : null;
        // Global shortcuts may be invoked while transient palettes are
        // pushed. A persistent workspace must retain the palette's real
        // origin, never a palette that is about to be removed.
        for (int depth = 0; depth < 16 && origin instanceof SFMCommandPaletteScreen palette; depth++) {
            Object parent = palette.originatingActionContext().originatingHost();
            if (!(parent instanceof Screen parentScreen) || parentScreen == origin) {
                origin = null;
                break;
            }
            origin = parentScreen;
        }
        Minecraft minecraft = Minecraft.getInstance();
        boolean paletteWasOpen = minecraft.screen instanceof SFMCommandPaletteScreen;

        if (origin instanceof SFMScreenMultiplexer workspace) {
            // Mutate the captured workspace while it is still alive underneath
            // the palette. Closing the palette first can cause Minecraft's GUI
            // layer restoration to win over the mutation on the next tick.
            var capturedPanel = actionContext.originatingPanelId();
            SFMWorkspacePanelIntentResult result;
            if (direction == Direction.FOCUSED) {
                result = capturedPanel == null
                        ? workspace.openFocused(panel, SFMWorkspacePanelMetadata.ordinary(), reopenRecipe)
                        : workspace.openIntoSlot(
                                capturedPanel,
                                panel,
                                SFMWorkspacePanelMetadata.ordinary(),
                                reopenRecipe);
            } else {
                SFMWorkspaceSide side = switch (direction) {
                    case LEFT -> SFMWorkspaceSide.LEFT;
                    case RIGHT -> SFMWorkspaceSide.RIGHT;
                    case ABOVE -> SFMWorkspaceSide.ABOVE;
                    case BELOW -> SFMWorkspaceSide.BELOW;
                    case FOCUSED -> throw new AssertionError("Focused panel opening was handled above");
                };
                result = capturedPanel == null
                        ? workspace.openToSide(workspace.focusedPanelId(), side, panel, reopenRecipe)
                        : workspace.openToSide(capturedPanel, side, panel, reopenRecipe);
            }
            if (result != SFMWorkspacePanelIntentResult.APPLIED) return 0;
            if (paletteWasOpen) ((SFMCommandPaletteScreen) minecraft.screen).onClose();
            return 1;
        }

        if (paletteWasOpen) ((SFMCommandPaletteScreen) minecraft.screen).onClose();
        if (direction == Direction.FOCUSED) {
            SFMScreenMultiplexer.openFocused(
                    origin,
                    panel,
                    SFMWorkspacePanelMetadata.ordinary(),
                    reopenRecipe);
        } else {
            SFMWorkspaceSide side = switch (direction) {
                case LEFT -> SFMWorkspaceSide.LEFT;
                case RIGHT -> SFMWorkspaceSide.RIGHT;
                case ABOVE -> SFMWorkspaceSide.ABOVE;
                case BELOW -> SFMWorkspaceSide.BELOW;
                case FOCUSED -> throw new AssertionError("Focused panel opening was handled above");
            };
            SFMScreenMultiplexer.openToSide(origin, side, panel, reopenRecipe);
        }
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
