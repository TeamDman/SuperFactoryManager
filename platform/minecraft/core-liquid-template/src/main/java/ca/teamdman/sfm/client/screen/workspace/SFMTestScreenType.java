package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.client.action.SFMClientActionSource;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
{% case minecraft_version %}
{% when "26.1.2" %}
import net.minecraft.resources.Identifier;
{% else %}
import net.minecraft.resources.ResourceLocation;
{% endcase %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.workspace_panel_reopening %}

import java.util.Objects;
{% endif %}
{% endcase %}

public final class SFMTestScreenType implements SFMClientScreenType {
    @Override
    public LiteralArgumentBuilder<SFMClientActionSource> createCommandNode(
{% case minecraft_version %}
{% when "26.1.2" %}
            Identifier screenTypeId,
{% else %}
            ResourceLocation screenTypeId,
{% endcase %}
            Opener opener
    ) {
        return LiteralArgumentBuilder.<SFMClientActionSource>literal(screenTypeId.toString())
                .then(RequiredArgumentBuilder.<SFMClientActionSource, String>argument(
                                "display_text",
                                StringArgumentType.greedyString()
                        )
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.workspace_panel_reopening %}
                        .executes(context -> {
                            String displayText = StringArgumentType.getString(context, "display_text");
                            return opener.open(context, new Recipe(screenTypeId, displayText));
                        }));
{% else %}
                        .executes(context -> opener.open(
                                context,
                                new SFMTestScreenPanel(StringArgumentType.getString(context, "display_text"))
                        )));
{% endif %}
{% else %}
                        .executes(context -> opener.open(
                                context,
                                new SFMTestScreenPanel(StringArgumentType.getString(context, "display_text"))
                        )));
{% endcase %}
    }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.workspace_panel_reopening %}

    public record Recipe(ResourceLocation sceneTypeId, String displayText) implements SFMPanelReopenRecipe {
        public Recipe {
            Objects.requireNonNull(sceneTypeId);
            Objects.requireNonNull(displayText);
        }

        @Override
        public SFMScreenPanel reopen() {
            return new SFMTestScreenPanel(displayText);
        }
    }
{% endif %}
{% endcase %}
}
