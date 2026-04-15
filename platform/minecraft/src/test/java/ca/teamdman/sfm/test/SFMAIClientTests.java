package ca.teamdman.sfm.test;

import ca.teamdman.sfm.client.draw.SFMDrawLocalCommandExecutor;
import ca.teamdman.sfm.common.ai.SFMAIClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMAIClientTests {
    @Test
    public void parsesOllamaTagModelNames() {
        String json = """
                {
                  "models": [
                    { "name": "gemma3:4b" },
                    { "name": "qwen3:8b" }
                  ]
                }
                """;

        assertEquals(List.of("gemma3:4b", "qwen3:8b"), SFMAIClient.parseModelNames(json));
    }

    @Test
    public void parsesOpenAiModelNames() {
        String json = """
                {
                  "data": [
                    { "id": "gemma4:e4b" },
                    { "id": "llama3.2" }
                  ]
                }
                """;

        assertEquals(List.of("gemma4:e4b", "llama3.2"), SFMAIClient.parseModelNames(json));
    }

  @Test
  public void localhostEndpointCandidatesIncludeLoopbackFallbacks() {
    assertEquals(
        List.of(
            "http://localhost:11434",
            "http://127.0.0.1:11434",
            "http://[::1]:11434"
        ),
        SFMAIClient.endpointCandidates("http://localhost:11434/")
    );
  }

    @Test
    public void parsesOllamaGenerateResponse() {
        String json = """
                {
                  "response": "Why the sky is blue."
                }
                """;

        assertEquals("Why the sky is blue.", SFMAIClient.parseGeneratedText(json));
    }

    @Test
    public void parsesOpenAiChatCompletionResponse() {
        String json = """
                {
                  "choices": [
                    {
                      "message": {
                        "content": "Because Rayleigh scattering."
                      }
                    }
                  ]
                }
                """;

        assertEquals("Because Rayleigh scattering.", SFMAIClient.parseGeneratedText(json));
    }

    @Test
    public void parsesChatCompletionToolCalls() {
        String json = """
                {
                  "choices": [
                    {
                      "message": {
                        "content": null,
                        "tool_calls": [
                          {
                            "id": "call_abc",
                            "type": "function",
                            "function": {
                              "name": "echo",
                              "arguments": "{\\"message\\":\\"hello\\"}"
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
                """;

        SFMAIClient.ChatCompletionTurn turn = SFMAIClient.parseChatCompletionTurn(json);
        assertNull(turn.content());
        assertEquals(1, turn.toolCalls().size());
        assertEquals("call_abc", turn.toolCalls().get(0).id());
        assertEquals("echo", turn.toolCalls().get(0).name());
        assertEquals("hello", turn.toolCalls().get(0).arguments().get("message").getAsString());
    }

    @Test
    public void localCommandTokenizerRejectsMalformedSlashCommandWithoutCrashing() {
        assertFalse(SFMDrawLocalCommandExecutor.canHandle("/help"));
        assertFalse(SFMDrawLocalCommandExecutor.canHandle("//help"));
    }

    @Test
    public void localCommandTokenizerAcceptsRectSelectorSyntax() {
        assertTrue(SFMDrawLocalCommandExecutor.canHandle("concatenate @rect[0,4]"));
    }

    @Test
    public void localCommandTokenizerAcceptsRelativeSelectorSyntax() {
      assertTrue(SFMDrawLocalCommandExecutor.canHandle("concatenate @relative[0,-10]"));
    }

    @Test
    public void localCommandTokenizerAcceptsSlashPathsAndQuotedPrompts() {
        assertTrue(SFMDrawLocalCommandExecutor.canHandle("open /user/home/examples/demo"));
        assertTrue(SFMDrawLocalCommandExecutor.canHandle("ollama run llama3.2 \"summarize /user/home/examples\""));
    }
}