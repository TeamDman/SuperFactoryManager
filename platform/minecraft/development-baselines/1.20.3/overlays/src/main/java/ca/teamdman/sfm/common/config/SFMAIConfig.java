package ca.teamdman.sfm.common.config;


import net.neoforged.neoforge.common.ModConfigSpec;

public class SFMAIConfig {
    public final ModConfigSpec.ConfigValue<String> openAICompatibleEndpoint;

    public SFMAIConfig(ModConfigSpec.Builder builder) {
        builder.comment("AI Settings");
        openAICompatibleEndpoint = builder
                .comment("The endpoint for an OpenAI compatible API")
                .define("openAICompatibleEndpoint", "http://localhost:11434");
    }
}
