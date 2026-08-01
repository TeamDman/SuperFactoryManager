package ca.teamdman.sfm.client.terminal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Deterministic, dependency-free terminal backend for players without Vox. */
public final class SFMJavaLocalTerminalService implements SFMTerminalService {
    private final SFMVirtualFileSystem filesystem;

    public SFMJavaLocalTerminalService() {
        this(new SFMVirtualFileSystem());
        filesystem.put("/workspace/README.txt", "Java-local SFM terminal workspace");
        filesystem.put("/workspace/notes.txt", "This bounded tree is safe to edit in game.");
    }

    public SFMJavaLocalTerminalService(SFMVirtualFileSystem filesystem) {
        this.filesystem = filesystem;
    }

    public SFMVirtualFileSystem filesystem() {
        return filesystem;
    }

    @Override
    public SFMTerminalSession openSession() {
        return new Session();
    }

    private final class Session implements SFMTerminalSession {
        private String workingDirectory = "/";

        @Override
        public SFMTerminalResponse execute(String command) {
            if (command == null || command.isBlank()) return SFMTerminalResponse.ok(List.of(), workingDirectory);
            List<String> tokens = tokenize(command.trim());
            String name = tokens.get(0).toLowerCase(Locale.ROOT);
            try {
                return switch (name) {
                    case "pwd" -> SFMTerminalResponse.ok(List.of(workingDirectory), workingDirectory);
                    case "ls" -> list(tokens);
                    case "cat" -> cat(tokens);
                    case "echo" -> SFMTerminalResponse.ok(List.of(String.join(" ", tokens.subList(1, tokens.size()))), workingDirectory);
                    case "write" -> write(tokens);
                    case "cd" -> changeDirectory(tokens);
                    case "1..100" -> boundedNumberRange(tokens);
                    case "write-host" -> writeHost(tokens);
                    default -> SFMTerminalResponse.error("command not found: " + name, workingDirectory);
                };
            } catch (IllegalArgumentException | IllegalStateException exception) {
                return SFMTerminalResponse.error(exception.getMessage(), workingDirectory);
            }
        }

        @Override
        public String workingDirectory() {
            return workingDirectory;
        }

        private SFMTerminalResponse list(List<String> tokens) {
            if (tokens.size() > 2) return SFMTerminalResponse.error("usage: ls [directory]", workingDirectory);
            String directory = tokens.size() == 1 ? workingDirectory : resolve(tokens.get(1));
            return SFMTerminalResponse.ok(filesystem.list(directory), workingDirectory);
        }

        private SFMTerminalResponse cat(List<String> tokens) {
            if (tokens.size() != 2) return SFMTerminalResponse.error("usage: cat <file>", workingDirectory);
            String value = filesystem.read(resolve(tokens.get(1)));
            return value == null ? SFMTerminalResponse.error("cat: file not found", workingDirectory)
                    : SFMTerminalResponse.ok(List.of(value), workingDirectory);
        }

        private SFMTerminalResponse write(List<String> tokens) {
            if (tokens.size() < 3) return SFMTerminalResponse.error("usage: write <file> <text>", workingDirectory);
            filesystem.put(resolve(tokens.get(1)), String.join(" ", tokens.subList(2, tokens.size())));
            return SFMTerminalResponse.ok(List.of("wrote " + resolve(tokens.get(1))), workingDirectory);
        }

        private SFMTerminalResponse changeDirectory(List<String> tokens) {
            if (tokens.size() != 2) return SFMTerminalResponse.error("usage: cd <directory>", workingDirectory);
            String target = resolve(tokens.get(1));
            if (!target.equals("/") && filesystem.list(target).isEmpty()) {
                return SFMTerminalResponse.error("cd: directory not found", workingDirectory);
            }
            workingDirectory = SFMVirtualFileSystem.normalizeDirectory(target);
            return SFMTerminalResponse.ok(List.of(), workingDirectory);
        }

        private SFMTerminalResponse boundedNumberRange(List<String> tokens) {
            if (tokens.size() != 1) return SFMTerminalResponse.error("usage: 1..100", workingDirectory);
            List<String> lines = new ArrayList<>(100);
            for (int value = 1; value <= 100; value++) lines.add(Integer.toString(value));
            return SFMTerminalResponse.ok(lines, workingDirectory);
        }

        private SFMTerminalResponse writeHost(List<String> tokens) {
            if (tokens.size() < 2) return SFMTerminalResponse.styledOk(
                    List.of(new SFMTerminalLine("", SFMTerminalLine.DEFAULT_COLOR)), workingDirectory);
            int index = 1;
            int color = SFMTerminalLine.DEFAULT_COLOR;
            if (index + 1 < tokens.size() && tokens.get(index).equalsIgnoreCase("-foregroundcolor")) {
                color = parseForegroundColor(tokens.get(index + 1));
                index += 2;
            }
            if (index < tokens.size() && tokens.get(index).equalsIgnoreCase("-nonewline")) index++;
            String message = String.join(" ", tokens.subList(index, tokens.size()));
            return SFMTerminalResponse.styledOk(List.of(new SFMTerminalLine(message, color)), workingDirectory);
        }

        private int parseForegroundColor(String value) {
            return switch (value.toLowerCase(Locale.ROOT)) {
                case "cyan" -> SFMTerminalLine.CYAN;
                case "white" -> SFMTerminalLine.DEFAULT_COLOR;
                case "gray", "grey" -> 0xFFAAAAAA;
                case "red" -> 0xFFFF5555;
                case "green" -> 0xFF55FF55;
                case "yellow" -> 0xFFFFFF55;
                case "blue" -> 0xFF5555FF;
                case "magenta" -> 0xFFFF55FF;
                default -> throw new IllegalArgumentException("unsupported foreground color: " + value);
            };
        }

        private String resolve(String path) {
            return SFMVirtualFileSystem.normalizeDirectory(path.startsWith("/") ? path : workingDirectory + "/" + path);
        }

        private List<String> tokenize(String input) {
            // Deliberately small shell: quoted text is retained without invoking a host shell.
            List<String> result = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            boolean quoted = false;
            for (int i = 0; i < input.length(); i++) {
                char character = input.charAt(i);
                if (character == '"') quoted = !quoted;
                else if (Character.isWhitespace(character) && !quoted) {
                    if (current.length() > 0) { result.add(current.toString()); current.setLength(0); }
                } else current.append(character);
            }
            if (quoted) throw new IllegalArgumentException("unterminated quote");
            if (current.length() > 0) result.add(current.toString());
            return result;
        }
    }
}
