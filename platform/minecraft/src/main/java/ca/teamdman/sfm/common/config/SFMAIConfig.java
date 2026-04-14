package ca.teamdman.sfm.common.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class SFMAIConfig {
    public final ForgeConfigSpec.ConfigValue<String> openAICompatibleEndpoint;
    public final ForgeConfigSpec.ConfigValue<String> toolCallAllowlistRegex;

    public SFMAIConfig(ForgeConfigSpec.Builder builder) {
        builder.comment("AI Settings");
        openAICompatibleEndpoint = builder
                .comment("The endpoint for an OpenAI compatible API")
                .define("openAICompatibleEndpoint", "http://localhost:11434");
        toolCallAllowlistRegex = builder
                .comment("Regex allowlist for AI-exposed draw tool names. Tool names map to `/sfm draw <tool>`.")
                .define("toolCallAllowlistRegex", "^echo$");
    }
}
