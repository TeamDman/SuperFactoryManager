package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.tooltip.SFMTooltipModeService;
{% if features.structured_action_results %}
import com.google.gson.JsonObject;
{% endif %}
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Objects;

/** Human/palette/CLI choice, deliberately not a script-callable action. */
public final class SFMTooltipModeAction implements SFMClientAction<SFMClientActionContext> {
    public static final ResourceLocation EXPAND = new ResourceLocation("sfm", "tooltip/more_info/expand");
    public static final ResourceLocation COMPACT = new ResourceLocation("sfm", "tooltip/more_info/compact");
    public static final ResourceLocation RESET = new ResourceLocation("sfm", "tooltip/more_info/reset");
    private final SFMTooltipModeService.Mode requested;
    private final SFMTooltipModeService service;

    public SFMTooltipModeAction(SFMTooltipModeService.Mode requested) {
        this(requested, SFMTooltipModeService.INSTANCE);
    }
    public SFMTooltipModeAction(SFMTooltipModeService.Mode requested, SFMTooltipModeService service) {
        this.requested = Objects.requireNonNull(requested); this.service = Objects.requireNonNull(service);
    }
    public static ResourceLocation idFor(SFMTooltipModeService.Mode mode) {
        return switch (Objects.requireNonNull(mode)) { case EXPANDED -> EXPAND; case COMPACT -> COMPACT; case AUTO -> RESET; };
    }
    @Override public Component title() {
        return Component.literal(switch (requested) {
            case EXPANDED -> "Expand tooltip more info";
            case COMPACT -> "Compact tooltip more info";
            case AUTO -> "Reset tooltip more info";
        });
    }
    @Override public Component description() {
        return Component.literal(switch (requested) {
            case EXPANDED -> "Show SFM more-info tooltips without holding a key, until reset or logout";
            case COMPACT -> "Hide SFM more-info tooltips regardless of the key, until reset or logout";
            case AUTO -> "Follow the configured hold-for-more-info key again";
        });
    }
    @Override public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return SFMClientActionAvailability::available;
    }
    @Override public int execute(SFMClientActionContext target, CommandContext<SFMClientActionSource> context) {
        boolean changed = service.mode() != requested;
        service.setMode(requested);
        // Reporting a mode must not poll the keyboard, including after reset to AUTO.
        String mode = requested.name().toLowerCase(Locale.ROOT);
{% if features.structured_action_results %}
        JsonObject result = new JsonObject();
        result.addProperty("status", "ok"); result.addProperty("mode", mode); result.addProperty("changed", changed);
        context.getSource().publishStructuredResult(SFMClientActionStructuredResult.of("sfm.tooltip.mode/1", result));
{% endif %}
        context.getSource().sendFeedback(Component.literal("Tooltip more info: " + mode));
        return 1;
    }
}
