package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.context.SFMContextActionProvider;
import ca.teamdman.sfm.client.context.SFMContextActionRegistry;
import ca.teamdman.sfm.client.context.SFMContextSnapshot;
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.text_editor.SFMTextDocumentPanelState;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Opens the one-to-many contextual action surface for one immutable workspace capture. */
public final class SFMContextActionsOpenAction implements SFMClientAction<SFMClientActionContext> {
    public static final ResourceLocation ID = new ResourceLocation("sfm", "context/actions/open");
    private static final SimpleCommandExceptionType NONE = new SimpleCommandExceptionType(
            Component.literal("No contextual actions are available at the captured position"));

    private final SFMContextActionRegistry registry;

    public SFMContextActionsOpenAction() {
        this(SFMContextActionRegistry.minecraftDefaults());
    }

    SFMContextActionsOpenAction(SFMContextActionRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override public Component title() {
        return Component.literal("Open contextual actions");
    }

    @Override public Component description() {
        return Component.literal("Offer actions derived from the exact focused editor document and position");
    }

    @Override public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return context -> {
            if (!context.originatingHostIsCurrent().getAsBoolean()) {
                return SFMClientActionAvailability.unavailable(
                        SFMClientActionContext.ORIGINATING_HOST_CHANGED.getComponent());
            }
            if (!(context.originatingHost() instanceof SFMScreenMultiplexer workspace)
                    || context.originatingPanelId() == null
                    || !(workspace.panelInstance(context.originatingPanelId())
                    instanceof SFMTextDocumentPanelState)) {
                return SFMClientActionAvailability.unavailable(Component.literal(
                        "Focus an SFM text editor before opening contextual actions"));
            }
            return SFMClientActionAvailability.available(context);
        };
    }

    @Override public int execute(
            SFMClientActionContext target,
            CommandContext<SFMClientActionSource> context
    ) throws CommandSyntaxException {
        SFMScreenMultiplexer workspace = (SFMScreenMultiplexer) target.originatingHost();
        SFMContextSnapshot snapshot = workspace.contextSnapshot();
        SFMContextActionRegistry.Resolution resolution = registry.resolve(
                SFMContextActionProvider.Request.capture(target, snapshot));
        resolution.diagnostics().forEach(diagnostic -> context.getSource().sendFeedback(Component.literal(
                "Context action provider " + diagnostic.providerId() + ": " + diagnostic.message()
        ).withStyle(ChatFormatting.RED)));
        if (resolution.choices().isEmpty()) throw NONE.create();
        SFMCommandPaletteScreen.openChoices(
                target,
                Component.literal("Contextual actions"),
                resolution.choices());
        return 1;
    }
}
