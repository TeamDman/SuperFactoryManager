package ca.teamdman.sfm.common.config;

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import net.minecraftforge.common.ForgeConfigSpec;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}

import net.neoforged.neoforge.common.ModConfigSpec;
{% endcase %}

public class SFMAIConfig {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
    public final ForgeConfigSpec.ConfigValue<String> openAICompatibleEndpoint;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
    public final ModConfigSpec.ConfigValue<String> openAICompatibleEndpoint;
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
    public SFMAIConfig(ForgeConfigSpec.Builder builder) {
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
    public SFMAIConfig(ModConfigSpec.Builder builder) {
{% endcase %}
        builder.comment("AI Settings");
        openAICompatibleEndpoint = builder
                .comment("The endpoint for an OpenAI compatible API")
                .define("openAICompatibleEndpoint", "http://localhost:11434");
    }
}
