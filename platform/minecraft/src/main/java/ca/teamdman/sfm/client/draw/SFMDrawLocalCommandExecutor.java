package ca.teamdman.sfm.client.draw;

import ca.teamdman.sfm.client.screen.SfmDrawScreen;
import ca.teamdman.sfm.common.ai.SFMAIClient;
import ca.teamdman.sfm.common.command.draw.SFMDrawCommandCompletionCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SFMDrawLocalCommandExecutor {
    private static final Pattern RECT_POINT_SELECTOR_PATTERN = Pattern.compile("@rect\\[\\s*(-?(?:\\d+(?:\\.\\d+)?|\\.\\d+))\\s*,\\s*(-?(?:\\d+(?:\\.\\d+)?|\\.\\d+))\\s*\\]");
    private static final Pattern RELATIVE_POINT_SELECTOR_PATTERN = Pattern.compile("@(?:rel|relative)\\[\\s*(-?(?:\\d+(?:\\.\\d+)?|\\.\\d+))\\s*,\\s*(-?(?:\\d+(?:\\.\\d+)?|\\.\\d+))\\s*\\]");
    private static final Pattern NEAREST_TEXT_SELECTOR_PATTERN = Pattern.compile("@nearest\\[(.*)]", Pattern.CASE_INSENSITIVE);
    private static final long MODEL_SUGGESTION_REFRESH_INTERVAL_MS = 30_000L;
    private static final AtomicBoolean MODEL_SUGGESTION_REFRESH_IN_FLIGHT = new AtomicBoolean(false);
    private static volatile List<String> cachedOllamaModelSuggestions = List.of();
    private static volatile long lastModelSuggestionRefreshStartedAt = 0L;

    private SFMDrawLocalCommandExecutor() {
    }

    public static boolean canHandle(String commandBody) {
        List<String> tokens = tokenize(commandBody);
        if (tokens.isEmpty()) {
            return false;
        }
        return switch (tokens.get(0)) {
            case "help", "describe", "context_menu", "split", "open", "move", "ls", "ollama", "rectangle", "box", "concatenate", "width", "reset" -> true;
            default -> false;
        };
    }

    public static boolean tryExecute(
            SfmDrawScreen screen,
            int commandElementId,
            String commandBody
    ) {
        List<String> tokens = tokenize(commandBody);
        if (tokens.isEmpty()) {
            return false;
        }

        return switch (tokens.get(0)) {
            case "help" -> {
                String topic = tokens.size() > 1 ? String.join(" ", tokens.subList(1, tokens.size())) : null;
                screen.appendLocalCommandHelp(commandElementId, topic);
                yield true;
            }
            case "describe" -> {
                if (tokens.size() != 2) {
                    screen.appendCommandOutput(commandElementId, List.of("describe: usage: /sfm draw describe <target>"));
                    yield true;
                }
                screen.appendTargetDescription(commandElementId, tokens.get(1));
                yield true;
            }
            case "context_menu" -> {
                if (tokens.size() != 2) {
                    screen.appendCommandOutput(commandElementId, List.of("context_menu: usage: /sfm draw context_menu <target>"));
                    yield true;
                }
                screen.appendTargetContextMenu(commandElementId, tokens.get(1));
                yield true;
            }
            case "open" -> {
                if (tokens.size() > 2) {
                    screen.appendCommandOutput(commandElementId, List.of("open: usage: /sfm draw open [path]"));
                    yield true;
                }
                String requestedPath = tokens.size() == 2 ? tokens.get(1) : SFMDrawVirtualFileSystem.DEFAULT_CANVAS_NAME;
                SFMDrawWorkspace.openCanvasInScreen(screen, requestedPath, commandElementId);
                yield true;
            }
            case "move" -> {
                if (tokens.size() != 3) {
                    screen.appendCommandOutput(commandElementId, List.of("move: usage: /sfm draw move <from> <to>"));
                    yield true;
                }
                SFMDrawWorkspace.moveCanvas(screen, tokens.get(1), tokens.get(2), commandElementId);
                yield true;
            }
            case "ls" -> {
                if (tokens.size() > 2) {
                    screen.appendCommandOutput(commandElementId, List.of("ls: usage: /sfm draw ls [path]"));
                    yield true;
                }
                String requestedPath = tokens.size() == 2 ? tokens.get(1) : "";
                try {
                    SFMDrawVirtualDirectoryListing listing = SFMDrawVirtualFileSystem.listDirectory(requestedPath);
                    List<String> lines = new ArrayList<>();
                    lines.add("ls: " + listing.target().virtualPath());
                    if (listing.entries().isEmpty()) {
                        lines.add("(empty)");
                    } else {
                        for (SFMDrawVirtualDirectoryEntry entry : listing.entries()) {
                            lines.add("- " + entry.label() + " -> " + entry.virtualPath());
                        }
                    }
                    screen.appendCommandOutput(commandElementId, lines);
                } catch (Exception exception) {
                    screen.appendCommandOutput(commandElementId, List.of("ls: " + exception.getMessage()));
                }
                yield true;
            }
            case "ollama" -> {
                if (tokens.size() < 2) {
                    screen.appendCommandOutput(commandElementId, List.of(
                            "ollama: usage: /sfm draw ollama models",
                            "ollama:    or: /sfm draw ollama run <model> <prompt>"
                    ));
                    yield true;
                }
                yield handleOllama(screen, commandElementId, tokens);
            }
            case "rectangle", "box" -> {
                if (tokens.size() == 2 && "list".equals(tokens.get(1))) {
                    screen.appendBoxList(commandElementId);
                    yield true;
                }
                if (tokens.size() != 5) {
                    screen.appendCommandOutput(commandElementId, List.of(
                            "rectangle: usage: /sfm draw rectangle list",
                            "rectangle:    or: /sfm draw rectangle <x1> <y1> <x2> <y2>"
                    ));
                    yield true;
                }
                try {
                    screen.createBox(
                            Double.parseDouble(tokens.get(1)),
                            Double.parseDouble(tokens.get(2)),
                            Double.parseDouble(tokens.get(3)),
                            Double.parseDouble(tokens.get(4))
                    );
                } catch (NumberFormatException exception) {
                    screen.appendCommandOutput(commandElementId, List.of("rectangle: coordinates must be numbers"));
                }
                yield true;
            }
            case "reset" -> {
                if (tokens.size() != 2 || !"chrome".equals(tokens.get(1))) {
                    screen.appendCommandOutput(commandElementId, List.of("reset: usage: /sfm draw reset chrome"));
                    yield true;
                }
                screen.resetChromeLayout();
                screen.appendCommandOutput(commandElementId, List.of("reset.chrome: reset chrome widget positions"));
                yield true;
            }
            case "concatenate" -> {
                if (tokens.size() < 2) {
                    screen.appendCommandOutput(commandElementId, List.of("concatenate: usage: /sfm draw concatenate <target> [delimiter]"));
                    yield true;
                }
                String selectorToken = tokens.get(1);
                String delimiter = tokens.size() > 2 ? decodeEscapes(String.join(" ", tokens.subList(2, tokens.size()))) : "";
                screen.appendConcatenatedTarget(commandElementId, selectorToken, delimiter);
                yield true;
            }
            case "width" -> {
                if (tokens.size() != 2) {
                    screen.appendCommandOutput(commandElementId, List.of("width: usage: /sfm draw width <target>"));
                    yield true;
                }
                screen.appendTargetWidth(commandElementId, tokens.get(1));
                yield true;
            }
            case "split" -> {
                if (tokens.size() < 2) {
                    screen.appendCommandOutput(commandElementId, List.of("split: usage: /sfm draw split <target> [delimiter]"));
                    yield true;
                }
                String delimiter = tokens.size() > 2 ? decodeEscapes(String.join(" ", tokens.subList(2, tokens.size()))) : "\n";
                screen.splitTargetText(commandElementId, tokens.get(1), delimiter);
                yield true;
            }
            default -> false;
        };
    }

    public static CompletionSuggestions suggestCompletions(
            String commandBody,
            int cursor,
            String rectSelectorSuggestion
    ) {
        String input = commandBody == null ? "" : commandBody;
        int safeCursor = Math.max(0, Math.min(cursor, input.length()));
        int activeTokenStart = activeTokenStart(input, safeCursor);
        int activeTokenEnd = activeTokenEnd(input, safeCursor);
        boolean cursorAfterWhitespace = safeCursor == 0 || Character.isWhitespace(input.charAt(safeCursor - 1));
        if (cursorAfterWhitespace) {
            activeTokenStart = safeCursor;
            activeTokenEnd = safeCursor;
        }

        String activeToken = input.substring(activeTokenStart, activeTokenEnd);
        List<String> tokens = tokenize(input.substring(0, safeCursor));
        if (cursorAfterWhitespace) {
            tokens = new ArrayList<>(tokens);
            tokens.add("");
        }
        if (tokens.isEmpty()) {
            return new CompletionSuggestions(activeTokenStart, activeTokenEnd, List.of());
        }

        List<String> targetSuggestions = targetSuggestions(rectSelectorSuggestion);
        List<String> suggestions = switch (tokens.get(0)) {
            case "help" -> tokens.size() == 2
                    ? SFMDrawCommandCompletionCatalog.filterByPrefix(
                SFMDrawCommandCompletionCatalog.drawHelpTopics(),
                    activeToken.startsWith("/") ? activeToken.substring(1) : activeToken
            )
                    : List.of();
            case "describe", "context_menu", "split", "width" -> tokens.size() == 2
                ? SFMDrawCommandCompletionCatalog.filterByPrefix(targetSuggestions, activeToken)
                : List.of();
            case "open" -> tokens.size() == 2
                    ? SFMDrawCommandCompletionCatalog.filterByPrefix(
                    SFMDrawCommandCompletionCatalog.canvasPathSuggestions(),
                    activeToken
            )
                    : List.of();
            case "move" -> tokens.size() == 2 || tokens.size() == 3
                    ? SFMDrawCommandCompletionCatalog.filterByPrefix(
                    SFMDrawCommandCompletionCatalog.canvasPathSuggestions(),
                    activeToken
            )
                    : List.of();
            case "ls" -> tokens.size() == 2
                    ? SFMDrawCommandCompletionCatalog.filterByPrefix(
                    SFMDrawCommandCompletionCatalog.browsePathSuggestions(),
                    activeToken
            )
                    : List.of();
            case "rectangle", "box" -> tokens.size() == 2
                    ? SFMDrawCommandCompletionCatalog.filterByPrefix(
                    List.of("list"),
                    activeToken
            )
                    : List.of();
            case "reset" -> tokens.size() == 2
                    ? SFMDrawCommandCompletionCatalog.filterByPrefix(
                    List.of("chrome"),
                    activeToken
            )
                    : List.of();
            case "concatenate" -> tokens.size() == 2
                    ? SFMDrawCommandCompletionCatalog.filterByPrefix(targetSuggestions, activeToken)
                    : List.of();
            case "ollama" -> suggestOllamaCompletions(tokens, activeToken);
            default -> tokens.size() == 1
                    ? SFMDrawCommandCompletionCatalog.filterByPrefix(
                    SFMDrawCommandCompletionCatalog.localDrawCommandNames(),
                    activeToken.startsWith("/") ? activeToken.substring(1) : activeToken
            )
                    : List.of();
        };

        return new CompletionSuggestions(activeTokenStart, activeTokenEnd, suggestions);
    }

    private static boolean handleOllama(
            SfmDrawScreen screen,
            int commandElementId,
            List<String> tokens
    ) {
        SFMAIClient client = new SFMAIClient();
        return switch (tokens.get(1)) {
            case "models", "list" -> {
                screen.appendCommandOutput(commandElementId, List.of("ollama.models: querying " + client.endpoint()));
                runAsync(screen, commandElementId, () -> {
                    List<String> lines = new ArrayList<>();
                    lines.add("ollama.models: " + client.endpoint());
                    for (String model : client.listModels()) {
                        lines.add("- " + model);
                    }
                    return lines;
                });
                yield true;
            }
            case "run" -> {
                if (tokens.size() < 4) {
                    screen.appendCommandOutput(commandElementId, List.of("ollama.run: usage: /sfm draw ollama run <model> <prompt>"));
                    yield true;
                }
                String model = tokens.get(2);
                String prompt = String.join(" ", tokens.subList(3, tokens.size()));
                screen.appendCommandOutput(commandElementId, List.of("ollama.run: " + model + " @ " + client.endpoint()));
                int placeholderElementId = screen.appendPendingLlmResponsePlaceholder(commandElementId);
                runAsync(screen, commandElementId, placeholderElementId, () -> runOllamaPrompt(client, model, prompt));
                yield true;
            }
            default -> {
                screen.appendCommandOutput(commandElementId, List.of(
                        "ollama: unknown subcommand: " + tokens.get(1),
                        "ollama: try `models` or `run <model> <prompt>`"
                ));
                yield true;
            }
        };
    }

    private static List<String> runOllamaPrompt(
            SFMAIClient client,
            String model,
            String prompt
    ) throws Exception {
        List<SFMAIClient.ChatTool> allowedTools = SFMDrawAIToolRegistry.allowedTools();
        List<String> lines = new ArrayList<>();

        String response;
        if (allowedTools.isEmpty()) {
            lines.add("ollama.tools: no tools allowed; falling back to plain generation");
            response = client.generate(model, prompt);
        } else {
            lines.add("ollama.tools: " + String.join(", ", allowedTools.stream().map(SFMAIClient.ChatTool::name).toList()));
            response = client.chatWithTools(model, prompt, allowedTools, toolCall -> {
                SFMDrawAIToolRegistry.ToolExecutionResult toolResult = SFMDrawAIToolRegistry.executeAllowed(toolCall);
                synchronized (lines) {
                    lines.add("ollama.tool: " + toolResult.translatedDrawCommand());
                    for (String outputLine : toolResult.outputLines()) {
                        lines.add("  -> " + outputLine);
                    }
                }
                return toolResult.outputText();
            });
        }

        lines.addAll(formatGeneratedText("ollama.run: " + model, response));
        return lines;
    }

    private static void runAsync(
            SfmDrawScreen screen,
            int commandElementId,
            int placeholderElementId,
            ThrowingSupplier<List<String>> task
    ) {
        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return task.get();
                    } catch (Exception exception) {
                        throw new RuntimeException(exception);
                    }
                })
                .whenComplete((lines, throwable) -> Minecraft.getInstance().execute(() -> {
                    if (throwable != null) {
                        List<String> errorLines = List.of("ollama: " + rootMessage(throwable));
                        if (placeholderElementId >= 0 && screen.replaceCommandOutputPlaceholder(placeholderElementId, errorLines)) {
                            return;
                        }
                        screen.appendCommandOutput(commandElementId, errorLines);
                        return;
                    }
                    if (lines == null || lines.isEmpty()) {
                        List<String> emptyLines = List.of("ollama: no output");
                        if (placeholderElementId >= 0 && screen.replaceCommandOutputPlaceholder(placeholderElementId, emptyLines)) {
                            return;
                        }
                        screen.appendCommandOutput(commandElementId, emptyLines);
                        return;
                    }
                    if (Minecraft.getInstance().screen == screen) {
                        if (placeholderElementId >= 0 && screen.replaceCommandOutputPlaceholder(placeholderElementId, lines)) {
                            return;
                        }
                        screen.appendCommandOutput(commandElementId, lines);
                    } else if (Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.displayClientMessage(Component.literal(lines.get(0)), false);
                    }
                }));
    }

    private static void runAsync(
            SfmDrawScreen screen,
            int commandElementId,
            ThrowingSupplier<List<String>> task
    ) {
        runAsync(screen, commandElementId, -1, task);
    }

    private static List<String> formatGeneratedText(
            String header,
            String text
    ) {
        List<String> lines = new ArrayList<>();
        lines.add(header);
        String normalizedText = text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n');
        for (String line : normalizedText.split("\n", -1)) {
            lines.add(line);
        }
        return lines;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null || current.getMessage().isBlank()
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }

    private static List<String> tokenize(String commandBody) {
        String input = commandBody == null ? "" : commandBody.strip();
        if (input.isEmpty()) {
            return List.of();
        }

        List<String> tokens = new ArrayList<>();
        int cursor = 0;
        while (cursor < input.length()) {
            while (cursor < input.length() && Character.isWhitespace(input.charAt(cursor))) {
                cursor++;
            }
            if (cursor >= input.length()) {
                break;
            }

            if (input.charAt(cursor) == '"') {
                StringBuilder token = new StringBuilder();
                cursor++;
                boolean closed = false;
                while (cursor < input.length()) {
                    char current = input.charAt(cursor++);
                    if (current == '\\' && cursor < input.length()) {
                        char escaped = input.charAt(cursor++);
                        if (escaped == '"' || escaped == '\\') {
                            token.append(escaped);
                        } else {
                            token.append('\\');
                            token.append(escaped);
                        }
                        continue;
                    }
                    if (current == '"') {
                        closed = true;
                        break;
                    }
                    token.append(current);
                }
                if (!closed) {
                    return List.of();
                }
                tokens.add(token.toString());
                continue;
            }

            int tokenStart = cursor;
            int bracketDepth = 0;
            while (cursor < input.length()) {
                char current = input.charAt(cursor);
                if (current == '[') {
                    bracketDepth++;
                } else if (current == ']' && bracketDepth > 0) {
                    bracketDepth--;
                } else if (Character.isWhitespace(current) && bracketDepth == 0) {
                    break;
                }
                cursor++;
            }
            tokens.add(input.substring(tokenStart, cursor));
        }
        return tokens;
    }

    private static List<String> suggestOllamaCompletions(
            List<String> tokens,
            String activeToken
    ) {
        if (tokens.size() == 2) {
            return SFMDrawCommandCompletionCatalog.filterByPrefix(
                    List.of("models", "list", "run"),
                    activeToken
            );
        }
        if (tokens.size() == 3 && "run".equals(tokens.get(1))) {
            requestOllamaModelSuggestionRefresh();
            LinkedHashSet<String> suggestions = new LinkedHashSet<>(cachedOllamaModelSuggestions);
            suggestions.addAll(SFMDrawCommandCompletionCatalog.ollamaModelFallbacks());
            return SFMDrawCommandCompletionCatalog.filterByPrefix(List.copyOf(suggestions), activeToken);
        }
        return List.of();
    }

    private static void requestOllamaModelSuggestionRefresh() {
        long now = System.currentTimeMillis();
        if (now - lastModelSuggestionRefreshStartedAt < MODEL_SUGGESTION_REFRESH_INTERVAL_MS) {
            return;
        }
        if (!MODEL_SUGGESTION_REFRESH_IN_FLIGHT.compareAndSet(false, true)) {
            return;
        }

        lastModelSuggestionRefreshStartedAt = now;
        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return new SFMAIClient().listModels();
                    } catch (Exception exception) {
                        return List.<String>of();
                    }
                })
                .whenComplete((models, throwable) -> {
                    if (throwable == null && models != null && !models.isEmpty()) {
                        cachedOllamaModelSuggestions = List.copyOf(models);
                    }
                    MODEL_SUGGESTION_REFRESH_IN_FLIGHT.set(false);
                });
    }

    private static int activeTokenStart(
            String input,
            int cursor
    ) {
        int index = Math.max(0, Math.min(cursor, input.length()));
        while (index > 0 && !Character.isWhitespace(input.charAt(index - 1))) {
            index--;
        }
        return index;
    }

    private static int activeTokenEnd(
            String input,
            int cursor
    ) {
        int index = Math.max(0, Math.min(cursor, input.length()));
        while (index < input.length() && !Character.isWhitespace(input.charAt(index))) {
            index++;
        }
        return index;
    }

    private static List<String> targetSuggestions(String rectSelectorSuggestion) {
        return combineSuggestions(List.of(rectSelectorSuggestion, "@rect[0,4]", "@relative[0,-10]", "@nearest[text]"), List.of());
    }

    private static List<String> combineSuggestions(
            List<String> primary,
            List<String> secondary
    ) {
        LinkedHashSet<String> suggestions = new LinkedHashSet<>();
        suggestions.addAll(primary);
        suggestions.addAll(secondary);
        suggestions.removeIf(value -> value == null || value.isBlank());
        return List.copyOf(suggestions);
    }

    private static RectPointSelector parseRectPointSelector(String selectorToken) {
        Matcher matcher = RECT_POINT_SELECTOR_PATTERN.matcher(selectorToken);
        if (!matcher.matches()) {
            return null;
        }
        return new RectPointSelector(
                Double.parseDouble(matcher.group(1)),
                Double.parseDouble(matcher.group(2))
        );
    }

    private static RelativePointSelector parseRelativePointSelector(String selectorToken) {
        Matcher matcher = RELATIVE_POINT_SELECTOR_PATTERN.matcher(selectorToken);
        if (!matcher.matches()) {
            return null;
        }
        return new RelativePointSelector(
                Double.parseDouble(matcher.group(1)),
                Double.parseDouble(matcher.group(2))
        );
    }

    private static NearestTextSelector parseNearestTextSelector(String selectorToken) {
        Matcher matcher = NEAREST_TEXT_SELECTOR_PATTERN.matcher(selectorToken);
        if (!matcher.matches()) {
            return null;
        }
        return new NearestTextSelector(matcher.group(1));
    }

    private static String decodeEscapes(String value) {
        if (value == null || value.indexOf('\\') < 0) {
            return value == null ? "" : value;
        }

        StringBuilder decoded = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current != '\\' || index + 1 >= value.length()) {
                decoded.append(current);
                continue;
            }

            char escaped = value.charAt(++index);
            switch (escaped) {
                case 'n' -> decoded.append('\n');
                case 'r' -> decoded.append('\r');
                case 't' -> decoded.append('\t');
                case 's' -> decoded.append(' ');
                case '"' -> decoded.append('"');
                case '\\' -> decoded.append('\\');
                default -> decoded.append(escaped);
            }
        }
        return decoded.toString();
    }

    private record RectPointSelector(
            double x,
            double y
    ) {
    }

        private record RelativePointSelector(
            double dx,
            double dy
        ) {
        }

            private record NearestTextSelector(
                String query
            ) {
            }

        public record CompletionSuggestions(
            int start,
            int end,
            List<String> suggestions
        ) {
        }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}