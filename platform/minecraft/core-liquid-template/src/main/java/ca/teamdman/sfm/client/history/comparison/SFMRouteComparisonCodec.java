package ca.teamdman.sfm.client.history.comparison;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/** Deterministic, versioned UTF-8 serialization for route-comparison sessions. */
public final class SFMRouteComparisonCodec {
    private static final String CODEC_SCHEMA = "sfm.route-comparison-codec/1";
    private static final Base64.Encoder TEXT_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder TEXT_DECODER = Base64.getUrlDecoder();

    private SFMRouteComparisonCodec() {
    }

    public static String encode(SFMRouteComparisonSession session) {
        Objects.requireNonNull(session, "session");
        return String.join("\n",
                field("codec", CODEC_SCHEMA),
                encodedField("schema", session.schema()),
                encodedField("id", session.id()),
                encodedField("left.machine_id", session.left().machineId()),
                encodedField("left.plan_revision_id", session.left().planRevisionId()),
                encodedField("left.route_id", session.left().routeId()),
                encodedField("right.machine_id", session.right().machineId()),
                encodedField("right.plan_revision_id", session.right().planRevisionId()),
                encodedField("right.route_id", session.right().routeId()),
                field("mode", session.mode().name()),
                field("left.cursor", Integer.toString(session.leftCursor())),
                field("right.cursor", Integer.toString(session.rightCursor())),
                field("left.last_position", Integer.toString(session.leftLastPosition())),
                field("right.last_position", Integer.toString(session.rightLastPosition())),
                field("left.disposition", session.leftDisposition().name()),
                field("right.disposition", session.rightDisposition().name()),
                field("revision", Long.toString(session.revision()))
        ) + "\n";
    }

    public static SFMRouteComparisonSession decode(String encoded) {
        Objects.requireNonNull(encoded, "encoded");
        if (encoded.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Route-comparison encoding must use canonical LF line endings");
        }
        String[] lines = encoded.split("\n", -1);
        if (lines.length != 18 || !lines[17].isEmpty()) {
            throw new IllegalArgumentException("Route-comparison encoding must contain 17 fields and a final LF");
        }
        Reader reader = new Reader(lines);
        String codec = reader.plain("codec");
        if (!CODEC_SCHEMA.equals(codec)) {
            throw new IllegalArgumentException("Unsupported route-comparison codec " + codec);
        }
        String schema = reader.text("schema");
        String id = reader.text("id");
        SFMRouteComparisonSession.RouteAddress left = new SFMRouteComparisonSession.RouteAddress(
                reader.text("left.machine_id"),
                reader.text("left.plan_revision_id"),
                reader.text("left.route_id")
        );
        SFMRouteComparisonSession.RouteAddress right = new SFMRouteComparisonSession.RouteAddress(
                reader.text("right.machine_id"),
                reader.text("right.plan_revision_id"),
                reader.text("right.route_id")
        );
        SFMRouteComparisonSession.Mode mode = reader.enumValue("mode", SFMRouteComparisonSession.Mode.class);
        int leftCursor = reader.intValue("left.cursor");
        int rightCursor = reader.intValue("right.cursor");
        int leftLastPosition = reader.intValue("left.last_position");
        int rightLastPosition = reader.intValue("right.last_position");
        SFMRouteComparisonSession.Disposition leftDisposition = reader.enumValue(
                "left.disposition", SFMRouteComparisonSession.Disposition.class);
        SFMRouteComparisonSession.Disposition rightDisposition = reader.enumValue(
                "right.disposition", SFMRouteComparisonSession.Disposition.class);
        long revision = reader.longValue("revision");
        reader.requireComplete();
        return new SFMRouteComparisonSession(
                schema,
                id,
                left,
                right,
                mode,
                leftCursor,
                rightCursor,
                leftLastPosition,
                rightLastPosition,
                leftDisposition,
                rightDisposition,
                revision
        );
    }

    private static String field(String key, String value) {
        return key + "\t" + value;
    }

    private static String encodedField(String key, String value) {
        return field(key, TEXT_ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static String decodeText(String encoded, String key) {
        final byte[] bytes;
        try {
            bytes = TEXT_DECODER.decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid base64 text for " + key, exception);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Invalid UTF-8 text for " + key, exception);
        }
    }

    private static final class Reader {
        private final String[] lines;
        private int index;

        private Reader(String[] lines) {
            this.lines = lines;
        }

        private String plain(String key) {
            String line = lines[index++];
            int separator = line.indexOf('\t');
            if (separator < 0 || !line.substring(0, separator).equals(key)) {
                throw new IllegalArgumentException("Expected route-comparison field " + key);
            }
            return line.substring(separator + 1);
        }

        private String text(String key) {
            return decodeText(plain(key), key);
        }

        private int intValue(String key) {
            String value = plain(key);
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid integer for " + key + ": " + value, exception);
            }
        }

        private long longValue(String key) {
            String value = plain(key);
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid long for " + key + ": " + value, exception);
            }
        }

        private <E extends Enum<E>> E enumValue(String key, Class<E> type) {
            String value = plain(key);
            try {
                return Enum.valueOf(type, value);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Invalid " + key + " value " + value, exception);
            }
        }

        private void requireComplete() {
            if (index != lines.length - 1) {
                throw new IllegalArgumentException("Unexpected trailing route-comparison fields");
            }
        }
    }
}
