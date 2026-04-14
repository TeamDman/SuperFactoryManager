package ca.teamdman.sfm.common.ai;

import ca.teamdman.sfm.common.config.SFMConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SFMAIClient {
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 120_000;
    private static final int MAX_TOOL_CALL_ROUNDS = 6;

    public String endpoint() {
        String endpoint = SFMConfig.getOrDefault(SFMConfig.AI_CONFIG.openAICompatibleEndpoint);
        if (endpoint.endsWith("/")) {
            return endpoint.substring(0, endpoint.length() - 1);
        }
        return endpoint;
    }

    public static List<String> endpointCandidates(String endpoint) {
        String normalizedEndpoint = endpoint == null ? "" : endpoint.strip();
        if (normalizedEndpoint.endsWith("/")) {
            normalizedEndpoint = normalizedEndpoint.substring(0, normalizedEndpoint.length() - 1);
        }

        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        if (!normalizedEndpoint.isBlank()) {
            candidates.add(normalizedEndpoint);
        }

        try {
            URL url = new URL(normalizedEndpoint);
            if ("localhost".equalsIgnoreCase(url.getHost())) {
                String portSuffix = url.getPort() >= 0 ? ":" + url.getPort() : "";
                String suffix = url.getFile() == null ? "" : url.getFile();
                candidates.add(url.getProtocol() + "://127.0.0.1" + portSuffix + suffix);
                candidates.add(url.getProtocol() + "://[::1]" + portSuffix + suffix);
            }
        } catch (MalformedURLException ignored) {
        }

        return List.copyOf(candidates);
    }

    public List<String> listModels() throws IOException {
        IOException primaryFailure = null;
        try {
            return parseModelNames(requestJson("GET", "/api/tags", null));
        } catch (IOException exception) {
            primaryFailure = exception;
        }

        try {
            return parseModelNames(requestJson("GET", "/v1/models", null));
        } catch (IOException exception) {
            if (primaryFailure != null) {
                exception.addSuppressed(primaryFailure);
            }
            throw exception;
        }
    }

    public String generate(
            String model,
            String prompt
    ) throws IOException {
        IOException primaryFailure = null;
        try {
            JsonObject request = new JsonObject();
            request.addProperty("model", model);
            request.addProperty("prompt", prompt);
            request.addProperty("stream", false);
            return parseGeneratedText(requestJson("POST", "/api/generate", request.toString()));
        } catch (IOException exception) {
            primaryFailure = exception;
        }

        try {
            JsonObject request = new JsonObject();
            request.addProperty("model", model);
            request.addProperty("stream", false);
            JsonArray messages = new JsonArray();
            JsonObject message = new JsonObject();
            message.addProperty("role", "user");
            message.addProperty("content", prompt);
            messages.add(message);
            request.add("messages", messages);
            return parseGeneratedText(requestJson("POST", "/v1/chat/completions", request.toString()));
        } catch (IOException exception) {
            if (primaryFailure != null) {
                exception.addSuppressed(primaryFailure);
            }
            throw exception;
        }
    }

    public String chatWithTools(
            String model,
            String prompt,
            List<ChatTool> tools,
            ToolCallHandler toolCallHandler
    ) throws IOException {
        JsonArray messages = new JsonArray();
        messages.add(chatMessage("user", prompt));

        for (int round = 0; round < MAX_TOOL_CALL_ROUNDS; round++) {
            JsonObject request = new JsonObject();
            request.addProperty("model", model);
            request.addProperty("stream", false);
            request.add("messages", messages);
            request.add("tools", serializeTools(tools));

            ChatCompletionTurn turn = parseChatCompletionTurn(requestJson("POST", "/v1/chat/completions", request.toString()));
            messages.add(turn.assistantMessage().deepCopy());

            if (turn.toolCalls().isEmpty()) {
                return turn.content() == null ? "" : turn.content();
            }

            for (ToolCall toolCall : turn.toolCalls()) {
                String toolResult;
                try {
                    toolResult = toolCallHandler.handle(toolCall);
                } catch (Exception exception) {
                    toolResult = "Tool execution failed: " + (exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
                }
                messages.add(toolResponseMessage(toolCall.id(), toolResult));
            }
        }

        throw new IOException("Tool call iteration limit reached.");
    }

    public static List<String> parseModelNames(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        Set<String> names = new LinkedHashSet<>();

        JsonArray ollamaModels = root.getAsJsonArray("models");
        if (ollamaModels != null) {
            for (JsonElement modelElement : ollamaModels) {
                if (!modelElement.isJsonObject()) {
                    continue;
                }
                JsonObject model = modelElement.getAsJsonObject();
                if (model.has("name")) {
                    names.add(model.get("name").getAsString());
                } else if (model.has("model")) {
                    names.add(model.get("model").getAsString());
                }
            }
        }

        JsonArray openAiModels = root.getAsJsonArray("data");
        if (openAiModels != null) {
            for (JsonElement modelElement : openAiModels) {
                if (!modelElement.isJsonObject()) {
                    continue;
                }
                JsonObject model = modelElement.getAsJsonObject();
                if (model.has("id")) {
                    names.add(model.get("id").getAsString());
                }
            }
        }

        if (names.isEmpty()) {
            throw new IllegalArgumentException("No models found in AI response.");
        }

        return new ArrayList<>(names);
    }

    public static String parseGeneratedText(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (root.has("response")) {
            return root.get("response").getAsString();
        }

        JsonArray choices = root.getAsJsonArray("choices");
        if (choices != null && choices.size() > 0) {
            JsonObject choice = choices.get(0).getAsJsonObject();
            if (choice.has("message") && choice.get("message").isJsonObject()) {
                JsonObject message = choice.getAsJsonObject("message");
                if (message.has("content")) {
                    return message.get("content").getAsString();
                }
            }
            if (choice.has("text")) {
                return choice.get("text").getAsString();
            }
        }

        throw new IllegalArgumentException("No response text found in AI response.");
    }

    public static ChatCompletionTurn parseChatCompletionTurn(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray choices = root.getAsJsonArray("choices");
        if (choices == null || choices.size() == 0) {
            throw new IllegalArgumentException("No chat completion choices found in AI response.");
        }

        JsonObject choice = choices.get(0).getAsJsonObject();
        JsonObject message = choice.getAsJsonObject("message");
        if (message == null) {
            throw new IllegalArgumentException("No chat completion message found in AI response.");
        }

        String content = null;
        if (message.has("content") && !message.get("content").isJsonNull()) {
            content = message.get("content").getAsString();
        }

        JsonArray toolCalls = message.getAsJsonArray("tool_calls");
        List<ToolCall> parsedToolCalls = new ArrayList<>();
        if (toolCalls != null) {
            for (JsonElement toolCallElement : toolCalls) {
                if (!toolCallElement.isJsonObject()) {
                    continue;
                }
                JsonObject toolCall = toolCallElement.getAsJsonObject();
                JsonObject function = toolCall.getAsJsonObject("function");
                if (function == null || !function.has("name")) {
                    continue;
                }
                String id = toolCall.has("id") ? toolCall.get("id").getAsString() : "tool_call";
                String name = function.get("name").getAsString();
                JsonObject arguments = parseToolArguments(function.has("arguments") && !function.get("arguments").isJsonNull()
                        ? function.get("arguments").getAsString()
                        : "{}");
                parsedToolCalls.add(new ToolCall(id, name, arguments));
            }
        }

        return new ChatCompletionTurn(content, message.deepCopy(), parsedToolCalls);
    }

    private static JsonObject parseToolArguments(String argumentsJson) {
        JsonElement parsed = JsonParser.parseString(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("Tool arguments were not a JSON object.");
        }
        return parsed.getAsJsonObject();
    }

    private static JsonArray serializeTools(List<ChatTool> tools) {
        JsonArray serializedTools = new JsonArray();
        for (ChatTool tool : tools) {
            JsonObject serializedTool = new JsonObject();
            serializedTool.addProperty("type", "function");

            JsonObject function = new JsonObject();
            function.addProperty("name", tool.name());
            function.addProperty("description", tool.description());
            function.add("parameters", tool.parameters().deepCopy());

            serializedTool.add("function", function);
            serializedTools.add(serializedTool);
        }
        return serializedTools;
    }

    private static JsonObject chatMessage(
            String role,
            String content
    ) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content);
        return message;
    }

    private static JsonObject toolResponseMessage(
            String toolCallId,
            String content
    ) {
        JsonObject message = new JsonObject();
        message.addProperty("role", "tool");
        message.addProperty("tool_call_id", toolCallId);
        message.addProperty("content", content);
        return message;
    }

    private String requestJson(
            String method,
            String path,
            String requestBody
    ) throws IOException {
        IOException failure = null;
        List<String> candidates = endpointCandidates(endpoint());
        for (int index = 0; index < candidates.size(); index++) {
            String candidate = candidates.get(index);
            try {
                return requestJsonAtEndpoint(candidate, method, path, requestBody);
            } catch (IOException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
                if (index == 0 && !shouldRetryAlternateEndpoint(exception)) {
                    break;
                }
            }
        }

        if (failure != null) {
            throw failure;
        }
        throw new IOException("No endpoint candidates were available for AI request.");
    }

    private String requestJsonAtEndpoint(
            String endpoint,
            String method,
            String path,
            String requestBody
    ) throws IOException {
        URL url = new URL(endpoint + path);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "SFMDraw/1.0");

        if (requestBody != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(requestBody.getBytes(StandardCharsets.UTF_8));
            }
        }

        int responseCode = connection.getResponseCode();
        InputStream responseStream = responseCode >= 200 && responseCode < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        String responseBody = responseStream == null ? "" : readAll(responseStream);
        if (responseCode < 200 || responseCode >= 300) {
            throw new IOException(method + " " + path + " failed with HTTP " + responseCode + (responseBody.isBlank() ? "" : ": " + responseBody));
        }
        return responseBody;
    }

    private static boolean shouldRetryAlternateEndpoint(IOException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ConnectException) {
                return true;
            }
            if (current.getMessage() != null && current.getMessage().toLowerCase().contains("connection refused")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String readAll(InputStream stream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return builder.toString();
        }
    }

            public record ChatTool(
                String name,
                String description,
                JsonObject parameters
            ) {
            }

            public record ToolCall(
                String id,
                String name,
                JsonObject arguments
            ) {
            }

            public record ChatCompletionTurn(
                String content,
                JsonObject assistantMessage,
                List<ToolCall> toolCalls
            ) {
            }

            @FunctionalInterface
            public interface ToolCallHandler {
            String handle(ToolCall toolCall) throws Exception;
            }
}