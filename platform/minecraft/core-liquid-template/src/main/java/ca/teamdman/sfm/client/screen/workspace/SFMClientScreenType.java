package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.client.action.SFMClientActionSource;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
{% case minecraft_version %}
{% when "26.1.2" %}
import net.minecraft.resources.Identifier;
{% else %}
import net.minecraft.resources.ResourceLocation;
{% endcase %}

/** A registered typed factory which contributes its arguments to Brigadier. */
public interface SFMClientScreenType {
    LiteralArgumentBuilder<SFMClientActionSource> createCommandNode(
{% case minecraft_version %}
{% when "26.1.2" %}
            Identifier screenTypeId,
{% else %}
            ResourceLocation screenTypeId,
{% endcase %}
            Opener opener
    );

    @FunctionalInterface
    interface Opener {
        int open(
                CommandContext<SFMClientActionSource> context,
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.workspace_panel_reopening %}
                SFMPanelReopenRecipe recipe
{% else %}
                SFMScreenPanel panel
{% endif %}
{% else %}
                SFMScreenPanel panel
{% endcase %}
        ) throws CommandSyntaxException;
    }
}
