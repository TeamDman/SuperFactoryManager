package ca.teamdman.sfm.client.draw;

import ca.teamdman.sfm.common.ai.SFMAIClient;
import ca.teamdman.sfm.common.config.SFMConfig;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class SFMDrawAIToolRegistry {
    private static final Map<String, DrawAITool> TOOLS_BY_NAME = new LinkedHashMap<>();

    static {
        register(new DrawAITool(
                new SFMAIClient.ChatTool(
                        "echo",
                        "Equivalent to /sfm draw echo <message>. Echo the provided message exactly.",
                        requiredStringParameterSchema(
                                "message",
                                "The message to echo back verbatim."
                        )
                )
        ) {
            @Override
            ToolExecutionResult execute(JsonObject arguments) {
                String message = requireString(arguments, "message");
                return new ToolExecutionResult(
                        "echo",
                        "/sfm draw echo " + quoteForCommand(message),
                        List.of(message)
                );
            }
        });
    }

    private SFMDrawAIToolRegistry() {
    }

    public static List<SFMAIClient.ChatTool> allowedTools() {
        return allowedTools(SFMConfig.getOrDefault(SFMConfig.AI_CONFIG.toolCallAllowlistRegex));
    }

    public static List<SFMAIClient.ChatTool> allowedTools(String allowlistRegex) {
        Pattern allowlistPattern = compileAllowlistPattern(allowlistRegex);
        List<SFMAIClient.ChatTool> allowedTools = new ArrayList<>();
        for (DrawAITool tool : TOOLS_BY_NAME.values()) {
            if (allowlistPattern.matcher(tool.definition().name()).matches()) {
                allowedTools.add(tool.definition());
            }
        }
        return allowedTools;
    }

    public static ToolExecutionResult executeAllowed(SFMAIClient.ToolCall toolCall) {
        String allowlistRegex = SFMConfig.getOrDefault(SFMConfig.AI_CONFIG.toolCallAllowlistRegex);
        Pattern allowlistPattern = compileAllowlistPattern(allowlistRegex);
        if (!allowlistPattern.matcher(toolCall.name()).matches()) {
            throw new IllegalArgumentException("Tool blocked by allowlist regex: " + toolCall.name());
        }

        DrawAITool tool = TOOLS_BY_NAME.get(toolCall.name().toLowerCase(Locale.ROOT));
        if (tool == null) {
            throw new IllegalArgumentException("Unknown draw AI tool: " + toolCall.name());
        }

        return tool.execute(toolCall.arguments());
    }

    private static void register(DrawAITool tool) {
        TOOLS_BY_NAME.put(tool.definition().name().toLowerCase(Locale.ROOT), tool);
    }

    private static Pattern compileAllowlistPattern(String allowlistRegex) {
        try {
            return Pattern.compile(allowlistRegex);
        } catch (PatternSyntaxException exception) {
            return Pattern.compile("^echo$");
        }
    }

    private static JsonObject requiredStringParameterSchema(
            String parameterName,
            String description
    ) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();
        JsonObject property = new JsonObject();
        property.addProperty("type", "string");
        property.addProperty("description", description);
        properties.add(parameterName, property);
        schema.add("properties", properties);

        var required = new com.google.gson.JsonArray();
        required.add(parameterName);
        schema.add("required", required);
        return schema;
    }

    private static String requireString(
            JsonObject arguments,
            String key
    ) {
        if (!arguments.has(key) || arguments.get(key).isJsonNull()) {
            throw new IllegalArgumentException("Missing required tool argument: " + key);
        }
        return arguments.get(key).getAsString();
    }

    private static String quoteForCommand(String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private abstract static class DrawAITool {
        private final SFMAIClient.ChatTool definition;

        private DrawAITool(SFMAIClient.ChatTool definition) {
            this.definition = definition;
        }

        public SFMAIClient.ChatTool definition() {
            return definition;
        }

        abstract ToolExecutionResult execute(JsonObject arguments);
    }

    public record ToolExecutionResult(
            String toolName,
            String translatedDrawCommand,
            List<String> outputLines
    ) {
        public String outputText() {
            return String.join("\n", outputLines);
        }
    }
}