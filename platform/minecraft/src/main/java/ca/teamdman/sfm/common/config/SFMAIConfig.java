package ca.teamdman.sfm.common.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class SFMAIConfig {
    public final ForgeConfigSpec.ConfigValue<String> openAICompatibleEndpoint;

    public SFMAIConfig(ForgeConfigSpec.Builder builder) {
        builder.comment("AI Settings");
        openAICompatibleEndpoint = builder
                .comment("The endpoint for an OpenAI compatible API")
                .define("openAICompatibleEndpoint", "http://localhost:11434");
    }
}
