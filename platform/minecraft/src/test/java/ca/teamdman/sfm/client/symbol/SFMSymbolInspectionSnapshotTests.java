package ca.teamdman.sfm.client.symbol;

import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.context.SFMContextActionProvider;
import ca.teamdman.sfm.client.context.SFMContextContribution;
import ca.teamdman.sfm.client.context.SFMContextCursorProjection;
import ca.teamdman.sfm.client.context.SFMContextDocumentProjection;
import ca.teamdman.sfm.client.context.SFMContextGenerationEvidence;
import ca.teamdman.sfm.client.context.SFMContextOriginId;
import ca.teamdman.sfm.client.context.SFMContextPosition;
import ca.teamdman.sfm.client.context.SFMContextSnapshot;
import ca.teamdman.sfm.client.context.SFMContextTextCoordinates;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.SFMResolverTextResult;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMSymbolInspectionSnapshotTests {
    @Test
    void unavailableBranchRetainsTruthfulReplayInputsWithoutInventingAVersion() {
        Fixture fixture = fixture("class A {}\n", 0, 6);
        SFMSymbolInspectionSnapshot snapshot = SFMSymbolInspectionSnapshot.capture(
                fixture.request(), Optional.empty(), Optional.empty()).orElseThrow();

        assertTrue(snapshot.diagnostics().stream()
                .anyMatch(value -> value.startsWith("java.replay-branch-unavailable:")));
        assertTrue(snapshot.replayCommand().startsWith("unavailable: no Minecraft branch was configured"));
        assertTrue(snapshot.replayCommand().contains("-Dsfm.symbol.workerBranch=<branch>"));
        assertTrue(snapshot.replayCommand().contains("captured-source='p/Use.java'"));
        assertTrue(snapshot.replayCommand().contains("line=1; column=7"));
        assertTrue(snapshot.replayCommand().contains(
                "current-sha256='" + fixture.document().currentSha256() + "'"));
        assertFalse(snapshot.replayCommand().contains("--branch '1.19.2'"));
    }

    @Test
    void unresolvedUnicodeCrlfPointRetainsTruthfulSourceHashPositionAndReplay() {
        Fixture fixture = fixture("class Café {\r\n  Missing value;\r\n}\r\n", 1, 4);
        SFMSymbolInspectionSnapshot snapshot = SFMSymbolInspectionSnapshot.capture(
                fixture.request(), Optional.empty(), "1.19.2").orElseThrow();

        assertEquals(1, snapshot.point().text().line());
        assertEquals(4, snapshot.point().text().column());
        assertEquals("Missing", snapshot.region().selectedText());
        assertEquals("java-identifier", snapshot.region().semanticKind());
        assertEquals(fixture.document().currentSha256(), snapshot.document().currentSha256());
        assertEquals("file:///D:/repo/src/p/Use.java", snapshot.document().address().orElseThrow());
        assertEquals("p/Use.java", snapshot.document().rootRelativePath().orElseThrow());
        assertTrue(snapshot.replayCommand().contains("--source-path 'p/Use.java'"));
        assertTrue(snapshot.replayCommand().contains("--line 2 --column 5 --branch '1.19.2'"));
        assertTrue(snapshot.diagnostics().stream().anyMatch(value -> value.contains("semantic-map-unavailable")));
        assertTrue(snapshot.exactAccessTransformerReference().isEmpty());
        assertEquals(
                "unavailable: no exact representable static symbol was resolved",
                SFMSymbolInspectionFormatters.format(
                        snapshot,
                        SFMSymbolInspectionFormatters.Projection.ACCESS_TRANSFORMER_REFERENCE)
        );
        String bounds = SFMSymbolInspectionFormatters.format(
                snapshot, SFMSymbolInspectionFormatters.Projection.BOUNDS);
        assertTrue(bounds.contains("canvas-point: (12,34)"));
        assertTrue(bounds.contains("canvas-regions: unavailable"));
    }

    @Test
    void resolvedStaticMethodUsesOneExactSelectorAcrossGranularAndAggregateFormats() {
        Fixture fixture = fixture("class A {\n  void run(String value) {}\n}\n", 1, 7);
        int start = fixture.document().currentText().indexOf("run");
        SFMTextDocumentRange range = SFMContextTextCoordinates.rangeAtUtf16Offsets(
                fixture.document().currentText(), start, start + 3);
        String selector = "p.A run(Ljava/lang/String;)V";
        SFMSymbolInspectionSnapshot.Outlink definition = outlink("definition-1", "definition");
        SFMSymbolInspectionSnapshot.Outlink reference = outlink("reference-1", "reference");
        SFMSymbolInspectionSnapshot.SemanticEvidence evidence = new SFMSymbolInspectionSnapshot.SemanticEvidence(
                fixture.document().currentSha256(),
                fixture.point(),
                new SFMSymbolInspectionSnapshot.DocumentEvidence(
                        Optional.of("file:///D:/repo/src/p/Use.java"),
                        Optional.of("file"),
                        Optional.of("main-java"),
                        Optional.of("p/Use.java"),
                        Optional.of("platform/minecraft/src/main/java/p/Use.java"),
                        Optional.of("main")
                ),
                range,
                Optional.of("java-region-7"),
                "java-method-declaration",
                List.of("java-class-declaration[class-A]", "java-method-declaration[java-region-7]"),
                List.of(new SFMSymbolInspectionSnapshot.Glyph(
                        7, start, "r", new SFMSymbolInspectionSnapshot.Rectangle(10, 20, 16, 29))),
                List.of(selector),
                "resolved",
                "complete",
                List.of(reference, definition),
                List.of("java.fixture: resolved witness"),
                List.of(new SFMSymbolInspectionSnapshot.Rectangle(10, 20, 28, 29)),
                List.of(new SFMSymbolInspectionSnapshot.Rectangle(110, 220, 146, 238)),
                List.of(new SFMSymbolInspectionSnapshot.Rectangle(310, 420, 346, 438)),
                List.of(new SFMSymbolInspectionSnapshot.Rectangle(620, 840, 692, 876)),
                Optional.of(new SFMSymbolInspectionSnapshot.Rectangle(0, 0, 320, 180))
        );
        SFMSymbolInspectionSnapshot snapshot = SFMSymbolInspectionSnapshot.capture(
                fixture.request(), Optional.of(evidence), "1.19.2").orElseThrow();

        assertEquals(Optional.of(selector), snapshot.exactAccessTransformerReference());
        assertEquals("method", snapshot.symbols().get(0).kind());
        assertEquals("p.A", snapshot.symbols().get(0).owner());
        assertEquals("run", snapshot.symbols().get(0).name());
        assertEquals(Optional.of("(Ljava/lang/String;)V"), snapshot.symbols().get(0).descriptor());
        assertEquals(List.of(definition), snapshot.definitionOutlinks());
        assertEquals(List.of(reference), snapshot.referenceOutlinks());

        for (SFMSymbolInspectionFormatters.Projection projection
                : SFMSymbolInspectionFormatters.Projection.values()) {
            assertEquals(
                    SFMSymbolInspectionFormatters.format(snapshot, projection),
                    SFMSymbolInspectionFormatters.format(snapshot, projection),
                    "projection must be deterministic: " + projection
            );
        }
        String details = SFMSymbolInspectionFormatters.format(
                snapshot, SFMSymbolInspectionFormatters.Projection.DETAILS);
        assertTrue(details.contains("file: " + SFMSymbolInspectionFormatters.format(
                snapshot, SFMSymbolInspectionFormatters.Projection.FILE)));
        assertTrue(details.contains("line: " + SFMSymbolInspectionFormatters.format(
                snapshot, SFMSymbolInspectionFormatters.Projection.LINE)));
        assertTrue(details.contains("column: " + SFMSymbolInspectionFormatters.format(
                snapshot, SFMSymbolInspectionFormatters.Projection.COLUMN)));
        assertTrue(details.contains("logical-path: " + SFMSymbolInspectionFormatters.format(
                snapshot, SFMSymbolInspectionFormatters.Projection.LOGICAL_PATH)));
        assertTrue(details.contains("access-transformer-reference: " + selector));
        assertTrue(details.contains("screen-global-regions: [310,420 -> 346,438)"));
        assertTrue(details.contains("framebuffer-pixel-regions: [620,840 -> 692,876)"));
        assertTrue(details.contains("selected-glyphs: 1"));
        assertTrue(details.contains("glyph[0]: ordinal=7, utf16=" + start + ", text=\"r\""));
        for (String line : SFMSymbolInspectionFormatters.format(
                snapshot, SFMSymbolInspectionFormatters.Projection.BOUNDS).split("\\n", -1)) {
            assertTrue(details.contains("  " + line), "aggregate must reuse bounds line bytes: " + line);
        }
    }

    @Test
    void accessTransformerParserDistinguishesTypesFieldsMethodsConstructorsAndOverloads() {
        var type = SFMSymbolInspectionSnapshot.Symbol.fromAccessTransformerReference("p.Outer$Inner");
        var field = SFMSymbolInspectionSnapshot.Symbol.fromAccessTransformerReference("p.A value");
        var method = SFMSymbolInspectionSnapshot.Symbol.fromAccessTransformerReference("p.A run(I)V");
        var overloaded = SFMSymbolInspectionSnapshot.Symbol.fromAccessTransformerReference(
                "p.A run(Ljava/lang/String;)V");
        var constructor = SFMSymbolInspectionSnapshot.Symbol.fromAccessTransformerReference("p.A <init>(I)V");

        assertEquals("type", type.kind());
        assertEquals("Inner", type.name());
        assertEquals("field", field.kind());
        assertEquals("method", method.kind());
        assertEquals(Optional.of("(I)V"), method.descriptor());
        assertEquals(Optional.of("(Ljava/lang/String;)V"), overloaded.descriptor());
        assertEquals("constructor", constructor.kind());
        assertTrue(List.of(type, field, method, overloaded, constructor).stream()
                .allMatch(SFMSymbolInspectionSnapshot.Symbol::accessTransformerRepresentable));
    }

    @Test
    void ambiguousSelectorsNeverGuessAtReferenceAndSessionRetainsExactEarlierCapture() {
        Fixture firstFixture = fixture("class A { A value; }\n", 0, 10);
        SFMTextDocumentRange range = SFMContextTextCoordinates.rangeAtUtf16Offsets(
                firstFixture.document().currentText(), 10, 11);
        SFMSymbolInspectionSnapshot.SemanticEvidence ambiguous = new SFMSymbolInspectionSnapshot.SemanticEvidence(
                firstFixture.document().currentSha256(),
                firstFixture.point(),
                SFMSymbolInspectionSnapshot.DocumentEvidence.empty(),
                range,
                Optional.of("ambiguous-region"),
                "java-type-reference",
                List.of("java-field-declaration[field]", "java-type-reference[ambiguous-region]"),
                List.of(),
                List.of("left.A", "right.A"),
                "partially-resolved",
                "incomplete",
                List.of(),
                List.of("java.ambiguous-type: two candidates"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Optional.empty()
        );
        SFMSymbolInspectionSnapshot first = SFMSymbolInspectionSnapshot.capture(
                firstFixture.request(), Optional.of(ambiguous), "1.19.2").orElseThrow();
        assertTrue(first.exactAccessTransformerReference().isEmpty());
        assertTrue(SFMSymbolInspectionFormatters.format(
                first, SFMSymbolInspectionFormatters.Projection.ACCESS_TRANSFORMER_REFERENCE)
                .contains("ambiguous across 2 symbols"));
        assertTrue(first.replayCommand().contains("--line 1 --column 11"));

        SFMSymbolInspectionSessions sessions = new SFMSymbolInspectionSessions();
        var capturedFirst = sessions.capture(first);
        Fixture secondFixture = fixture("class B {}\n", 0, 6);
        SFMSymbolInspectionSnapshot second = SFMSymbolInspectionSnapshot.capture(
                secondFixture.request(), Optional.empty(), "1.19.2").orElseThrow();
        sessions.capture(second);

        assertEquals(first, sessions.find(capturedFirst.id()).orElseThrow());
        assertFalse(sessions.find(capturedFirst.id()).orElseThrow().document().currentSha256()
                .equals(second.document().currentSha256()));
    }

    @Test
    void localVariableSelectorIsExplicitlyUnavailableToAccessTransformers() {
        Fixture fixture = fixture("class A { void f() { int value = 1; } }\n", 0, 25);
        int start = fixture.document().currentText().indexOf("value");
        SFMTextDocumentRange range = SFMContextTextCoordinates.rangeAtUtf16Offsets(
                fixture.document().currentText(), start, start + "value".length());
        SFMSymbolInspectionSnapshot.SemanticEvidence local = new SFMSymbolInspectionSnapshot.SemanticEvidence(
                fixture.document().currentSha256(),
                fixture.point(),
                SFMSymbolInspectionSnapshot.DocumentEvidence.empty(),
                range,
                Optional.of("local-value"),
                "java-local-variable-declaration",
                List.of("java-method-declaration[f]", "java-local-variable-declaration[local-value]"),
                List.of(),
                List.of("p.A value"),
                "resolved",
                "complete",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Optional.empty()
        );

        SFMSymbolInspectionSnapshot snapshot = SFMSymbolInspectionSnapshot.capture(
                fixture.request(), Optional.of(local), "1.19.2").orElseThrow();

        assertEquals(1, snapshot.symbols().size());
        assertEquals("java-local-variable-declaration", snapshot.symbols().get(0).kind());
        assertFalse(snapshot.symbols().get(0).accessTransformerRepresentable());
        assertEquals(Optional.empty(), snapshot.exactAccessTransformerReference());
        assertTrue(SFMSymbolInspectionFormatters.format(
                snapshot,
                SFMSymbolInspectionFormatters.Projection.ACCESS_TRANSFORMER_REFERENCE
        ).contains("java-local-variable-declaration"));
    }

    private static SFMSymbolInspectionSnapshot.Outlink outlink(String id, String relation) {
        return new SFMSymbolInspectionSnapshot.Outlink(
                id,
                relation,
                Optional.of("file:///D:/repo/src/p/A.java"),
                "sfm:java-symbol-index",
                9,
                "resolved",
                "complete",
                "start",
                "fixture " + relation,
                "fixture"
        );
    }

    private static Fixture fixture(String text, int line, int column) {
        SFMPath root = SFMPath.fromNative(Path.of("D:/repo/src"));
        SFMPath path = SFMPath.fromNative(Path.of("D:/repo/src/p/Use.java"));
        SFMTextDocumentSnapshot baseline = new SFMTextDocumentSnapshot(
                SFMTextDocumentSnapshot.State.READY,
                text,
                SFMTextDocumentSnapshot.MutationCapability.READ_ONLY,
                Optional.of(path),
                Optional.of(root),
                Optional.of(SFMContextTextCoordinates.sha256(text)),
                OptionalLong.of(text.getBytes(StandardCharsets.UTF_8).length),
                Optional.empty(),
                Optional.of(text.contains("\r\n")
                        ? SFMResolverTextResult.LineEndingKind.CRLF
                        : SFMResolverTextResult.LineEndingKind.LF),
                Optional.empty(),
                List.of()
        );
        var point = SFMContextTextCoordinates.atLineColumn(text, line, column);
        SFMContextDocumentProjection document = SFMContextDocumentProjection.capture(
                "sfm:text-editor-v3",
                baseline,
                text,
                false,
                true,
                List.of(new SFMContextCursorProjection(
                        "primary",
                        new SFMContextPosition.Canvas(12, 34, Optional.of(point)),
                        true,
                        true
                )),
                List.of()
        );
        SFMContextOriginId origin = new SFMContextOriginId("sfm:text-editor", "panel-7", "document");
        SFMContextContribution contribution = new SFMContextContribution(
                origin,
                new SFMContextGenerationEvidence(11, 12, 13, 14),
                document
        );
        SFMContextSnapshot snapshot = new SFMContextSnapshot(
                21, 22, 23, Optional.of(origin), List.of(contribution));
        SFMContextActionProvider.Request request = new SFMContextActionProvider.Request(
                new SFMClientActionContext(new Object(), () -> true, null),
                snapshot,
                Optional.of(contribution)
        );
        return new Fixture(request, document, point);
    }

    private record Fixture(
            SFMContextActionProvider.Request request,
            SFMContextDocumentProjection document,
            ca.teamdman.sfm.client.text_editor.SFMTextDocumentPosition point
    ) {
    }
}
