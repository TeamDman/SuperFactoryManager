package ca.teamdman.sfm.test;

import ca.teamdman.sfm.client.draw.SFMDrawAIToolRegistry;
import ca.teamdman.sfm.common.ai.SFMAIClient;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SFMDrawAIToolRegistryTests {
    @Test
    public void allowlistRegexDefaultsToEchoShape() {
        List<SFMAIClient.ChatTool> tools = SFMDrawAIToolRegistry.allowedTools("^echo$");
        assertEquals(List.of("echo"), tools.stream().map(SFMAIClient.ChatTool::name).toList());
    }

    @Test
    public void disallowingRegexRemovesTools() {
        assertEquals(List.of(), SFMDrawAIToolRegistry.allowedTools("^$"));
    }

    @Test
    public void echoToolTranslatesToDrawEcho() {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("message", "hello world");

        SFMDrawAIToolRegistry.ToolExecutionResult result = SFMDrawAIToolRegistry.executeAllowed(
                new SFMAIClient.ToolCall("call_1", "echo", arguments)
        );

        assertEquals("/sfm draw echo \"hello world\"", result.translatedDrawCommand());
        assertEquals(List.of("hello world"), result.outputLines());
        assertEquals("hello world", result.outputText());
    }
}