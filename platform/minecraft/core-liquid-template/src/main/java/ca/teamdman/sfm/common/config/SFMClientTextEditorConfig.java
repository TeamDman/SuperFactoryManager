package ca.teamdman.sfm.common.config;

import ca.teamdman.sfm.client.registry.SFMTextEditors;
import ca.teamdman.sfm.client.text_editor.ISFMTextEditorRegistration;
import ca.teamdman.sfm.client.text_editor.SFMTextEditorIntellisenseLevel;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
{% case minecraft_version %}
{% when "26.1.2" %}
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
{% else %}
import net.minecraft.resources.ResourceLocation;
{% endcase %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
import net.minecraftforge.common.ForgeConfigSpec;
{% else %}
import net.neoforged.neoforge.common.ModConfigSpec;
{% endcase %}
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
{% case minecraft_version %}
{% when "26.1.2" %}
{% else %}

import java.util.Objects;
{% endcase %}

public class SFMClientTextEditorConfig {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
    public final ForgeConfigSpec.BooleanValue showLineNumbers;
{% if features.canvas_pointer_defaults %}
    public final ForgeConfigSpec.BooleanValue canvasWheelZooms;
    public final ForgeConfigSpec.BooleanValue canvasMiddlePans;
{% endif %}
    public final ForgeConfigSpec.EnumValue<SFMTextEditorIntellisenseLevel> intellisenseLevel;
    public final ForgeConfigSpec.ConfigValue<String> preferredEditor;
{% else %}
    public final ModConfigSpec.BooleanValue showLineNumbers;
    public final ModConfigSpec.EnumValue<SFMTextEditorIntellisenseLevel> intellisenseLevel;
    public final ModConfigSpec.ConfigValue<String> preferredEditor;
{% endcase %}

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
    SFMClientTextEditorConfig(ForgeConfigSpec.Builder builder) {
{% else %}
    SFMClientTextEditorConfig(ModConfigSpec.Builder builder) {
{% endcase %}

        showLineNumbers = builder.define("showLineNumbers", false);
{% if features.canvas_pointer_defaults %}
        canvasWheelZooms = builder.comment("Default for newly opened v3 editors; existing editors keep their own setting.")
                .define("canvasWheelZooms", true);
        canvasMiddlePans = builder.comment("Middle pans and right opens actions; false swaps the two buttons in new v3 editors.")
                .define("canvasMiddlePans", true);
{% endif %}
        intellisenseLevel = builder.defineEnum("intellisenseLevel", SFMTextEditorIntellisenseLevel.OFF);
        preferredEditor = builder.define("preferredEditor", "sfm:v1");
    }


    public static @NotNull ISFMTextEditorRegistration getPreferredTextEditor() {

{% case minecraft_version %}
{% when "26.1.2" %}
        @Nullable Identifier id = SFMResourceLocation.tryParse(SFMConfig.CLIENT_TEXT_EDITOR_CONFIG.preferredEditor.get());
{% else %}
        @Nullable ResourceLocation id = SFMResourceLocation.tryParse(SFMConfig.CLIENT_TEXT_EDITOR_CONFIG.preferredEditor.get());
{% endcase %}
        if (id == null) {
            // Clobber the invalid ID
            SFMTextEditors.V1.getId().ifPresent(defaultId -> {
{% case minecraft_version %}
{% when "26.1.2" %}
                SFMConfig.CLIENT_TEXT_EDITOR_CONFIG.preferredEditor.set(defaultId.identifier().toString());
{% else %}
                SFMConfig.CLIENT_TEXT_EDITOR_CONFIG.preferredEditor.set(defaultId.location().toString());
{% endcase %}
            });
            return SFMTextEditors.V1.get();
        } else {
{% case minecraft_version %}
{% when "26.1.2" %}
            return SFMTextEditors.registry()
                    .get(id).map(Holder.Reference::value)
                    .orElse(SFMTextEditors.V1.get());
{% else %}
            return Objects.requireNonNullElse(
                    SFMTextEditors.registry().get(id),
                    SFMTextEditors.V1.get());
{% endcase %}
        }
    }

}
