package ca.teamdman.sfm.client.control;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMClientControlFileBridgeTests {
    @TempDir
    Path temporaryDirectory;

    @Test
    void publishesReadyDescriptorAndExecutesOneRequest() throws Exception {
        SFMClientControlFileBridge bridge = SFMClientControlFileBridge.open(
                temporaryDirectory,
                command -> {
                    JsonObject result = new JsonObject();
                    result.addProperty("command", command);
                    return result;
                }
        );

        JsonObject ready = JsonParser.parseString(Files.readString(
                temporaryDirectory.resolve("ready.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("sfm.client-control-files/1", ready.get("schema").getAsString());
        assertEquals(temporaryDirectory.toAbsolutePath().normalize().toString(), bridge.directory().toString());

        Files.writeString(
                temporaryDirectory.resolve("000001.request.json"),
                "{\"action_tokens\":[\"sfm:echo\",\"hello world\"]}",
                StandardCharsets.UTF_8
        );
        bridge.poll();

        JsonObject response = JsonParser.parseString(Files.readString(
                temporaryDirectory.resolve("000001.response.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("completed", response.get("status").getAsString());
        assertEquals(
                "sfm action invoke sfm:echo \"hello world\"",
                response.get("command").getAsString()
        );
    }

    @Test
    void malformedRequestIsReportedAndDoesNotPreventLaterRequests() throws Exception {
        SFMClientControlFileBridge bridge = SFMClientControlFileBridge.open(
                temporaryDirectory,
                command -> new JsonObject()
        );
        Files.writeString(
                temporaryDirectory.resolve("000001.request.json"),
                "{\"unexpected\":true}",
                StandardCharsets.UTF_8
        );
        bridge.poll();

        JsonObject response = JsonParser.parseString(Files.readString(
                temporaryDirectory.resolve("000001.response.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("error", response.get("status").getAsString());
        assertTrue(response.get("error").getAsString().contains("command"));
    }

    @Test
    void reportsActionExecutionFailureAsError() throws Exception {
        SFMClientControlFileBridge bridge = SFMClientControlFileBridge.open(
                temporaryDirectory,
                command -> {
                    JsonObject result = new JsonObject();
                    result.addProperty("result_code", 0);
                    result.addProperty("error", "unknown action");
                    return result;
                }
        );
        Files.writeString(
                temporaryDirectory.resolve("000001.request.json"),
                "{\"command\":\"sfm action invoke sfm:missing\"}",
                StandardCharsets.UTF_8
        );
        bridge.poll();

        JsonObject response = JsonParser.parseString(Files.readString(
                temporaryDirectory.resolve("000001.response.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("error", response.get("status").getAsString());
        assertEquals("unknown action", response.get("error").getAsString());
    }
}
