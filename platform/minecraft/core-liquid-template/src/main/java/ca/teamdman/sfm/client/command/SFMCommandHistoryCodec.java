package ca.teamdman.sfm.client.command;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Versioned, line-oriented persistence format for the command history. */
public final class SFMCommandHistoryCodec {
    public static final String HEADER = "sfm-command-history schema=1";

    private SFMCommandHistoryCodec() {
    }

    public static String serialize(List<String> entries) {
        StringBuilder text = new StringBuilder(HEADER).append('\n');
        for (String entry : entries) {
            String normalized = SFMCommandHistory.normalize(entry);
            if (normalized == null) throw new IllegalArgumentException("Invalid command history entry");
            text.append(Base64.getEncoder().encodeToString(normalized.getBytes(StandardCharsets.UTF_8)))
                    .append('\n');
        }
        return text.toString();
    }

    public static ParseResult parse(String text) {
        if (text == null) return ParseResult.corrupt("history is null");
        String[] lines = text.split("\\n", -1);
        if (lines.length == 0 || !HEADER.equals(lines[0])) {
            return ParseResult.corrupt("unsupported or missing history header");
        }
        List<String> entries = new ArrayList<>();
        for (int index = 1; index < lines.length; index++) {
            String line = lines[index].stripTrailing();
            if (line.isEmpty()) continue;
            try {
                byte[] bytes = Base64.getDecoder().decode(line);
                CharBuffer decoded = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes));
                String command = decoded.toString();
                if (SFMCommandHistory.normalize(command) == null) {
                    return ParseResult.corrupt("invalid command history entry at line " + (index + 1));
                }
                entries.add(command);
                if (entries.size() > SFMCommandHistory.MAX_ENTRIES) {
                    entries = new ArrayList<>(entries.subList(
                            entries.size() - SFMCommandHistory.MAX_ENTRIES, entries.size()));
                }
            } catch (IllegalArgumentException | CharacterCodingException exception) {
                return ParseResult.corrupt("invalid encoded history entry at line " + (index + 1));
            }
        }
        return new ParseResult(List.copyOf(entries), false, "");
    }

    public record ParseResult(List<String> entries, boolean corrupt, String diagnostic) {
        public ParseResult {
            entries = List.copyOf(entries);
        }

        private static ParseResult corrupt(String diagnostic) {
            return new ParseResult(List.of(), true, diagnostic);
        }
    }
}
