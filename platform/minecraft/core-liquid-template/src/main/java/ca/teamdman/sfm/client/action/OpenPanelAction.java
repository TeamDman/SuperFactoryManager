package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.registry.SFMClientScreenTypes;
{% if features.command_palette %}
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
{% endif %}
import ca.teamdman.sfm.client.screen.workspace.SFMClientScreenType;
{% if features.workspace_panel_reopening %}
import ca.teamdman.sfm.client.screen.workspace.SFMPanelReopenRecipe;
{% endif %}
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
{% if features.workspace_stack_controls %}
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelMetadata;
{% endif %}
{% if features.workspace_stack_controls or features.workspace_directional_opening %}
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelIntentResult;
{% endif %}
{% if features.workspace_directional_opening %}
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceSide;
{% endif %}
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
{% if features.command_palette %}
import net.minecraft.client.Minecraft;
{% endif %}
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
{% if features.workspace_panel_reopening %}
            SFMPanelReopenRecipe recipe
{% else %}
            SFMScreenPanel panel
{% endif %}
    ) {
{% if features.workspace_stack_controls %}
{% else %}
        if (direction == Direction.FOCUSED) {
            commandContext.getSource().sendFeedback(Component.literal(
                    "Focused panel opening requires workspace stack controls"));
            return 0;
        }
{% endif %}
{% if features.workspace_directional_opening %}
{% else %}
        if (direction != Direction.FOCUSED) {
            commandContext.getSource().sendFeedback(Component.literal(
                    "Directional panel opening requires workspace directional opening"));
            return 0;
        }
{% endif %}
        SFMClientActionContext actionContext = commandContext.getSource().context();
{% if features.workspace_panel_reopening %}
        return openPanel(actionContext, recipe.reopen(), direction, recipe);
{% else %}
        return openPanel(actionContext, panel, direction);
{% endif %}
    }

    /** Shared registered-action panel-opening seam used by typed scene authorities. */
    public static int openPanel(SFMClientActionContext actionContext, SFMScreenPanel panel, Direction direction) {
{% if features.workspace_panel_reopening %}
        return openPanel(actionContext, panel, direction, null);
    }

    public static int openPanel(
            SFMClientActionContext actionContext,
            SFMScreenPanel panel,
            Direction direction,
            @Nullable SFMPanelReopenRecipe reopenRecipe
    ) {
{% endif %}
{% if features.workspace_stack_controls %}
{% else %}
        if (direction == Direction.FOCUSED) return 0;
{% endif %}
{% if features.workspace_directional_opening %}
{% else %}
        if (direction != Direction.FOCUSED) return 0;
{% endif %}
{% if features.workspace_stack_controls or features.workspace_directional_opening %}
        @Nullable Screen origin = actionContext.originatingHost() instanceof Screen screen ? screen : null;
{% if features.command_palette %}
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

{% endif %}
        if (origin instanceof SFMScreenMultiplexer workspace) {
            // Mutate the captured workspace while it is still alive underneath
            // the palette. Closing the palette first can cause Minecraft's GUI
            // layer restoration to win over the mutation on the next tick.
            var capturedPanel = actionContext.originatingPanelId();
            SFMWorkspacePanelIntentResult result;
            if (direction == Direction.FOCUSED) {
{% if features.workspace_stack_controls %}
                result = capturedPanel == null
{% if features.workspace_panel_reopening %}
                        ? workspace.openFocused(panel, SFMWorkspacePanelMetadata.ordinary(), reopenRecipe)
{% else %}
                        ? workspace.openFocused(panel, SFMWorkspacePanelMetadata.ordinary())
{% endif %}
                        : workspace.openIntoSlot(
                                capturedPanel,
                                panel,
{% if features.workspace_panel_reopening %}
                                SFMWorkspacePanelMetadata.ordinary(),
                                reopenRecipe);
{% else %}
                                SFMWorkspacePanelMetadata.ordinary());
{% endif %}
{% else %}
                return 0;
{% endif %}
            } else {
{% if features.workspace_directional_opening %}
                SFMWorkspaceSide side = switch (direction) {
                    case LEFT -> SFMWorkspaceSide.LEFT;
                    case RIGHT -> SFMWorkspaceSide.RIGHT;
                    case ABOVE -> SFMWorkspaceSide.ABOVE;
                    case BELOW -> SFMWorkspaceSide.BELOW;
                    case FOCUSED -> throw new AssertionError("Focused panel opening was handled above");
                };
                result = capturedPanel == null
{% if features.workspace_panel_reopening %}
                        ? workspace.openToSide(workspace.focusedPanelId(), side, panel, reopenRecipe)
                        : workspace.openToSide(capturedPanel, side, panel, reopenRecipe);
{% else %}
                        ? workspace.openToSide(workspace.focusedPanelId(), side, panel)
                        : workspace.openToSide(capturedPanel, side, panel);
{% endif %}
{% else %}
                return 0;
{% endif %}
            }
            if (result != SFMWorkspacePanelIntentResult.APPLIED) return 0;
{% if features.command_palette %}
            if (paletteWasOpen) ((SFMCommandPaletteScreen) minecraft.screen).onClose();
{% endif %}
            return 1;
        }

{% if features.command_palette %}
        if (paletteWasOpen) ((SFMCommandPaletteScreen) minecraft.screen).onClose();
{% endif %}
        if (direction == Direction.FOCUSED) {
{% if features.workspace_stack_controls %}
            SFMScreenMultiplexer.openFocused(
                    origin,
                    panel,
{% if features.workspace_panel_reopening %}
                    SFMWorkspacePanelMetadata.ordinary(),
                    reopenRecipe);
{% else %}
                    SFMWorkspacePanelMetadata.ordinary());
{% endif %}
{% else %}
            return 0;
{% endif %}
        } else {
{% if features.workspace_directional_opening %}
            SFMWorkspaceSide side = switch (direction) {
                case LEFT -> SFMWorkspaceSide.LEFT;
                case RIGHT -> SFMWorkspaceSide.RIGHT;
                case ABOVE -> SFMWorkspaceSide.ABOVE;
                case BELOW -> SFMWorkspaceSide.BELOW;
                case FOCUSED -> throw new AssertionError("Focused panel opening was handled above");
            };
{% if features.workspace_panel_reopening %}
            SFMScreenMultiplexer.openToSide(origin, side, panel, reopenRecipe);
{% else %}
            SFMScreenMultiplexer.openToSide(origin, side, panel);
{% endif %}
{% else %}
            return 0;
{% endif %}
        }
        return 1;
{% else %}
        return 0;
{% endif %}
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
