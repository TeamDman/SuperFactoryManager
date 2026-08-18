package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.context.SFMContextTextCoordinates;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Converts validated UTF-8 style ranges into one immutable canvas-glyph snapshot. */
public final class SFMDrawCanvasRemoteSyntaxStyles {
    public record FormattingSpan(
            int startByte,
            int endByte,
            List<ChatFormatting> formatting
    ) {
        public FormattingSpan {
            if (startByte < 0 || startByte >= endByte) {
                throw new IllegalArgumentException("Syntax style span must be a non-empty positive range");
            }
            formatting = List.copyOf(formatting);
        }
    }

    private SFMDrawCanvasRemoteSyntaxStyles() {
    }

    public static Map<SFMDrawCanvasModel.CanvasGlyph, List<ChatFormatting>> project(
            List<SFMDrawCanvasModel.CanvasGlyph> sourceGlyphs,
            int spaceWidth,
            int lineHeight,
            String exactSource,
            List<FormattingSpan> spans
    ) {
        Objects.requireNonNull(exactSource, "exactSource");
        Objects.requireNonNull(spans, "spans");
        SFMDrawCanvasSyntaxHighlightingHelper.CanvasDocumentProjection projection =
                SFMDrawCanvasSyntaxHighlightingHelper.projectCanvasDocument(
                        sourceGlyphs,
                        spaceWidth,
                        lineHeight
                );
        if (!projection.text().equals(exactSource)) {
            throw new IllegalArgumentException("Canvas projection no longer matches the highlighted source");
        }

        IdentityHashMap<SFMDrawCanvasModel.CanvasGlyph, List<ChatFormatting>> answer =
                new IdentityHashMap<>();
        int previousEnd = 0;
        ArrayList<Integer> byteBoundaries = new ArrayList<>(spans.size() * 2);
        for (FormattingSpan span : spans) {
            if (span.startByte() < previousEnd) {
                throw new IllegalArgumentException("Syntax style spans overlap or are out of order");
            }
            byteBoundaries.add(span.startByte());
            byteBoundaries.add(span.endByte());
            previousEnd = span.endByte();
        }
        List<Integer> utf16Boundaries = SFMContextTextCoordinates.utf16OffsetsAtUtf8Bytes(
                exactSource,
                byteBoundaries
        );
        for (int spanIndex = 0; spanIndex < spans.size(); spanIndex++) {
            FormattingSpan span = spans.get(spanIndex);
            int start = utf16Boundaries.get(spanIndex * 2);
            int end = utf16Boundaries.get(spanIndex * 2 + 1);
            if (end > projection.glyphsByCharIndex().size()) {
                throw new IllegalArgumentException("Syntax style span exceeds the canvas projection");
            }
            for (int index = start; index < end; index++) {
                SFMDrawCanvasModel.CanvasGlyph glyph = projection.glyphsByCharIndex().get(index);
                if (glyph == null) continue;
                List<ChatFormatting> previous = answer.putIfAbsent(glyph, span.formatting());
                if (previous != null && !previous.equals(span.formatting())) {
                    throw new IllegalArgumentException("One canvas glyph crosses incompatible syntax styles");
                }
            }
        }
        return Collections.unmodifiableMap(answer);
    }

    public static Component styledGlyph(
            SFMDrawCanvasModel.CanvasGlyph glyph,
            Map<SFMDrawCanvasModel.CanvasGlyph, List<ChatFormatting>> styles
    ) {
        List<ChatFormatting> formatting = styles.get(glyph);
        if (formatting == null || formatting.isEmpty()) return Component.literal(glyph.text());
        return Component.literal(glyph.text()).withStyle(formatting.toArray(ChatFormatting[]::new));
    }

    /** Materializes immutable styled glyphs once when a syntax publication arrives. */
    public static Map<SFMDrawCanvasModel.CanvasGlyph, Component> styledGlyphs(
            Map<SFMDrawCanvasModel.CanvasGlyph, List<ChatFormatting>> styles
    ) {
        IdentityHashMap<SFMDrawCanvasModel.CanvasGlyph, Component> answer = new IdentityHashMap<>();
        styles.forEach((glyph, ignored) -> answer.put(glyph, styledGlyph(glyph, styles)));
        return Collections.unmodifiableMap(answer);
    }

    public static List<ChatFormatting> parseFormattingNames(List<String> names) {
        ArrayList<ChatFormatting> answer = new ArrayList<>(names.size());
        for (String name : names) {
            ChatFormatting formatting = ChatFormatting.getByName(name);
            if (formatting == null) throw new IllegalArgumentException("Unknown ChatFormatting name: " + name);
            answer.add(formatting);
        }
        return List.copyOf(answer);
    }
}
