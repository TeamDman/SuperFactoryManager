package ca.teamdman.sfm.client.explorer;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class SFMCanonicalText {
    private SFMCanonicalText() {
    }

    static void requireValidUnicode(String value, String code) {
        try {
            StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(java.nio.CharBuffer.wrap(value));
        } catch (CharacterCodingException exception) {
            throw new SFMParseException(code, "Text is not valid Unicode", 0);
        }
        if (value.indexOf('\0') >= 0) {
            throw new SFMParseException(code, "Text contains NUL", value.indexOf('\0'));
        }
    }

    static String encodeComponent(String value) {
        requireValidUnicode(value, "text.invalid-unicode");
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        StringBuilder answer = new StringBuilder(bytes.length);
        for (byte current : bytes) {
            int valueByte = Byte.toUnsignedInt(current);
            if (isUnreserved(valueByte)) {
                answer.append((char) valueByte);
            } else {
                answer.append('%');
                answer.append(Character.toUpperCase(Character.forDigit(valueByte >>> 4, 16)));
                answer.append(Character.toUpperCase(Character.forDigit(valueByte & 0xF, 16)));
            }
        }
        return answer.toString();
    }

    static String decodeComponent(String value, int sourceOffset) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(value.length());
        for (int index = 0; index < value.length();) {
            char current = value.charAt(index);
            if (current == '%') {
                if (index + 2 >= value.length()) {
                    throw new SFMParseException(
                            "text.invalid-percent-escape",
                            "Percent escape is incomplete",
                            sourceOffset + index
                    );
                }
                int high = Character.digit(value.charAt(index + 1), 16);
                int low = Character.digit(value.charAt(index + 2), 16);
                if (high < 0 || low < 0) {
                    throw new SFMParseException(
                            "text.invalid-percent-escape",
                            "Percent escape is not hexadecimal",
                            sourceOffset + index
                    );
                }
                bytes.write((high << 4) | low);
                index += 3;
                continue;
            }
            if (current > 0x7F || !isUnreserved(current)) {
                throw new SFMParseException(
                        "text.unescaped-character",
                        "Canonical components must percent-encode reserved and non-ASCII characters",
                        sourceOffset + index
                );
            }
            bytes.write(current);
            index++;
        }
        try {
            String decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes.toByteArray()))
                    .toString();
            requireValidUnicode(decoded, "text.invalid-unicode");
            return decoded;
        } catch (CharacterCodingException exception) {
            throw new SFMParseException(
                    "text.invalid-utf8",
                    "Percent escapes do not decode to valid UTF-8",
                    sourceOffset
            );
        }
    }

    static FunctionCall parseFunction(String text) {
        int firstOpen = text.indexOf('(');
        if (firstOpen <= 0 || !text.endsWith(")")) {
            throw new SFMParseException("expression.invalid-function", "Expected function expression", 0);
        }
        String name = text.substring(0, firstOpen).toLowerCase(Locale.ROOT);
        for (int index = 0; index < name.length(); index++) {
            char current = name.charAt(index);
            if (!(current >= 'a' && current <= 'z') && current != '-' && current != '_') {
                throw new SFMParseException(
                        "expression.invalid-function",
                        "Function name contains an unsupported character",
                        index
                );
            }
        }
        String body = text.substring(firstOpen + 1, text.length() - 1);
        return new FunctionCall(name, splitArguments(body, firstOpen + 1));
    }

    static List<String> splitArguments(String body, int sourceOffset) {
        if (body.isEmpty()) return List.of();
        ArrayList<String> answer = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int index = 0; index < body.length(); index++) {
            char current = body.charAt(index);
            if (current == '(') {
                depth++;
            } else if (current == ')') {
                depth--;
                if (depth < 0) {
                    throw new SFMParseException(
                            "expression.unbalanced",
                            "Expression contains an unmatched closing parenthesis",
                            sourceOffset + index
                    );
                }
            } else if (current == ',' && depth == 0) {
                appendArgument(answer, body, start, index, sourceOffset);
                start = index + 1;
            }
        }
        if (depth != 0) {
            throw new SFMParseException(
                    "expression.unbalanced",
                    "Expression contains an unmatched opening parenthesis",
                    sourceOffset + body.length()
            );
        }
        appendArgument(answer, body, start, body.length(), sourceOffset);
        return List.copyOf(answer);
    }

    private static void appendArgument(
            List<String> answer,
            String body,
            int start,
            int end,
            int sourceOffset
    ) {
        if (start == end) {
            throw new SFMParseException(
                    "expression.empty-argument",
                    "Expression contains an empty argument",
                    sourceOffset + start
            );
        }
        String value = body.substring(start, end);
        if (!value.equals(value.trim())) {
            throw new SFMParseException(
                    "expression.unescaped-whitespace",
                    "Canonical expressions do not contain unescaped whitespace",
                    sourceOffset + start
            );
        }
        answer.add(value);
    }

    private static boolean isUnreserved(int value) {
        return value >= 'a' && value <= 'z'
                || value >= 'A' && value <= 'Z'
                || value >= '0' && value <= '9'
                || value == '-'
                || value == '.'
                || value == '_'
                || value == '~';
    }

    record FunctionCall(String name, List<String> arguments) {
    }
}
