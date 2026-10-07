package ca.teamdman.sfm.client.screen.workspace.diagnostic;

import ca.teamdman.sfm.client.action.SFMClientActionSource;
import ca.teamdman.sfm.client.screen.workspace.SFMClientScreenType;
{% if features.workspace_panel_reopening %}
import ca.teamdman.sfm.client.screen.workspace.SFMPanelReopenRecipe;
{% endif %}
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.resources.ResourceLocation;
{% if features.workspace_panel_reopening %}

import java.util.Objects;
{% endif %}

/** Typed, reopenable input diagnostics scene. */
public final class SFMInputDiagnosticsScreenType implements SFMClientScreenType {
    @Override
    public LiteralArgumentBuilder<SFMClientActionSource> createCommandNode(
            ResourceLocation screenTypeId,
            Opener opener
    ) {
        return LiteralArgumentBuilder.<SFMClientActionSource>literal(screenTypeId.toString())
{% if features.workspace_panel_reopening %}
                .executes(context -> opener.open(context, new Recipe(screenTypeId)));
{% else %}
                .executes(context -> opener.open(context, new SFMInputDiagnosticsPanel()));
{% endif %}
    }
{% if features.workspace_panel_reopening %}

    public record Recipe(ResourceLocation sceneTypeId) implements SFMPanelReopenRecipe {
        public Recipe {
            Objects.requireNonNull(sceneTypeId);
        }

        @Override
        public SFMInputDiagnosticsPanel reopen() {
            return new SFMInputDiagnosticsPanel();
        }
    }
{% endif %}
}
