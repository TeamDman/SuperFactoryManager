package ca.teamdman.sfm.client.context;

import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMFindReferencesAction;
import ca.teamdman.sfm.client.action.SFMJumpToDefinitionAction;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.screen.SFMActionChoice;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMContextActionRegistryTests {
    private static final SFMContextOriginId ORIGIN = new SFMContextOriginId("editor", "panel-1", "document");

    @Test
    void registrySupportsZeroOneManyDeterministicDedupeAndTypedFailureDiagnostics() {
        SFMActionChoice definition = SFMActionChoice.invoke(SFMJumpToDefinitionAction.ID, "");
        SFMActionChoice references = SFMActionChoice.invoke(SFMFindReferencesAction.ID, "");
        SFMContextActionRegistry registry = SFMContextActionRegistry.builder()
                .register("late", 20, ignored -> List.of(new SFMContextActionProvider.Offer(0, references)))
                .register("early", 10, ignored -> List.of(
                        new SFMContextActionProvider.Offer(20, references),
                        new SFMContextActionProvider.Offer(10, definition)))
                .register("empty", 0, ignored -> List.of())
                .register("broken", 30, ignored -> { throw new IllegalStateException("not indexed"); })
                .build();

        SFMContextActionRegistry.Resolution resolution = registry.resolve(request(javaDocument(
                "class Use { String value; }\n", "String")));

        assertEquals(List.of(definition, references), resolution.choices());
        assertEquals(List.of(new SFMContextActionRegistry.Diagnostic("broken", "not indexed")),
                resolution.diagnostics());
        assertThrows(IllegalArgumentException.class, () -> SFMContextActionRegistry.builder()
                .register("same", 0, ignored -> List.of())
                .register("same", 1, ignored -> List.of()));
    }

    @Test
    void javaProviderUsesTheFocusedPathAddressedDocumentAndExactUnicodeCrlfPosition() {
        String text = "// λ\r\nclass Use { StringBuilder value; }\r\n";
        SFMContextActionRegistry.Resolution resolution = SFMContextActionRegistry.minecraftDefaults()
                .resolve(request(javaDocument(text, "StringBuilder")));

        assertEquals(List.of(
                SFMActionChoice.invoke(SFMJumpToDefinitionAction.ID, ""),
                SFMActionChoice.invoke(SFMFindReferencesAction.ID, "")
        ), resolution.choices());
        assertTrue(resolution.diagnostics().isEmpty());
    }

    @Test
    void javaProviderOffersNothingForWhitespaceNonJavaOrUnfocusedDocuments() {
        SFMContextContribution whitespace = javaDocument("class Use { String value; }\n", "\n");
        SFMContextContribution nonJava = document("class Use {}\n", "Use", "file:///D:/src/Use.txt");

        assertTrue(SFMContextActionRegistry.minecraftDefaults().resolve(request(whitespace)).choices().isEmpty());
        assertTrue(SFMContextActionRegistry.minecraftDefaults().resolve(request(nonJava)).choices().isEmpty());
        SFMContextContribution javaDocument = javaDocument("class Use {}\n", "Use");
        SFMContextSnapshot unfocused = new SFMContextSnapshot(1, 1, 1, Optional.empty(), List.of(javaDocument));
        assertTrue(SFMContextActionRegistry.minecraftDefaults().resolve(new SFMContextActionProvider.Request(
                new SFMClientActionContext(null, () -> true, null), unfocused, Optional.empty()
        )).choices().isEmpty());
    }

    @Test
    void javaProviderSuppressesCommentsStringsCharactersAndTextBlocks() {
        assertTrue(resolveJava("// λ StringBuilder\r\nclass Use {}\r\n", "StringBuilder").isEmpty());
        assertTrue(resolveJava("class Use { /* Object */ int value; }\n", "Object").isEmpty());
        assertTrue(resolveJava(
                "class Use { String text = \"prefix \\\" StringBuilder\"; }\n",
                "StringBuilder"
        ).isEmpty());
        assertTrue(resolveJava("class Use { char letter = 'λ'; }\n", "λ").isEmpty());
        assertTrue(resolveJava(
                "class Use {\r\n String text = \"\"\"\r\n Object\r\n \"\"\";\r\n}\r\n",
                "Object"
        ).isEmpty());
        String escapedTextBlockQuotes = "\\" + "\"\"\"";
        assertTrue(resolveJava(
                "class Use {\r\n String text = \"\"\"\r\n " + escapedTextBlockQuotes
                        + " StringBuilder\r\n \"\"\";\r\n}\r\n",
                "StringBuilder"
        ).isEmpty());
    }

    @Test
    void javaProviderPreservesAnnotationsImportsUnicodeAndCodeAfterEscapedLiterals() {
        assertJavaSymbolOffers("import java.util.List;\r\nclass Use {}\r\n", "List");
        assertJavaSymbolOffers("@Deprecated\r\nclass Use {}\r\n", "Deprecated");
        assertJavaSymbolOffers("class Use { int café; }\r\n", "café");
        assertJavaSymbolOffers(
                "class Use { String uri = \"https://example\"; Object value; char quote = '\\\''; }\r\n",
                "Object"
        );
    }

    private static void assertJavaSymbolOffers(String text, String cursorNeedle) {
        assertEquals(List.of(
                SFMActionChoice.invoke(SFMJumpToDefinitionAction.ID, ""),
                SFMActionChoice.invoke(SFMFindReferencesAction.ID, "")
        ), resolveJava(text, cursorNeedle));
    }

    private static List<SFMActionChoice> resolveJava(String text, String cursorNeedle) {
        return SFMContextActionRegistry.minecraftDefaults()
                .resolve(request(javaDocument(text, cursorNeedle)))
                .choices();
    }

    private static SFMContextActionProvider.Request request(SFMContextContribution contribution) {
        SFMContextSnapshot snapshot = new SFMContextSnapshot(
                1, 1, 1, Optional.of(contribution.originId()), List.of(contribution));
        return SFMContextActionProvider.Request.capture(
                new SFMClientActionContext(null, () -> true, null), snapshot);
    }

    private static SFMContextContribution javaDocument(String text, String cursorNeedle) {
        return document(text, cursorNeedle, "file:///D:/src/Use.java");
    }

    private static SFMContextContribution document(String text, String cursorNeedle, String address) {
        int utf16 = text.indexOf(cursorNeedle);
        if (utf16 < 0) throw new IllegalArgumentException("Cursor needle is absent");
        int byteOffset = text.substring(0, utf16).getBytes(StandardCharsets.UTF_8).length;
        SFMTextDocumentSnapshot literal = SFMTextDocumentSnapshot.literal(text);
        SFMPath path = SFMPath.parse(address);
        SFMPath root = SFMPath.parse("file:///D:/src/");
        SFMTextDocumentSnapshot addressed = new SFMTextDocumentSnapshot(
                literal.state(), literal.text(), literal.mutationCapability(), Optional.of(path), Optional.of(root),
                literal.sha256(), literal.byteLength(), literal.lastModified(), literal.lineEndingKind(),
                literal.targetRange(), literal.diagnostics());
        SFMContextDocumentProjection projection = SFMContextDocumentProjection.capture(
                "editor-v3", addressed, text, false, true,
                List.of(new SFMContextCursorProjection(
                        "primary",
                        new SFMContextPosition.Text(SFMTextDocumentRange.positionAtByteOffset(text, byteOffset)),
                        true,
                        true)),
                List.of());
        return new SFMContextContribution(ORIGIN, SFMContextGenerationEvidence.INITIAL, projection);
    }
}
