package ca.teamdman.sfm.client.context;

import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMFindReferencesAction;
import ca.teamdman.sfm.client.action.SFMJumpToDefinitionAction;
import ca.teamdman.sfm.client.action.SFMSymbolCopyAction;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.SFMResolverTextResult;
import ca.teamdman.sfm.client.symbol.SFMSymbolInspectionFormatters;
import ca.teamdman.sfm.client.symbol.SFMSymbolInspectionSessions;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMJavaSymbolInspectionContextActionTests {
    @Test
    void identifierOffersNavigationAndSevenCopiesBackedByOneCapture() {
        SFMSymbolInspectionSessions sessions = new SFMSymbolInspectionSessions();
        SFMJavaSymbolContextActionProvider provider =
                new SFMJavaSymbolContextActionProvider(sessions, "1.19.2");

        List<SFMContextActionProvider.Offer> offers = provider.offers(request("class A {}\n", 0, 6));

        assertEquals(9, offers.size());
        assertEquals(SFMJumpToDefinitionAction.ID, offers.get(0).choice().actionId());
        assertEquals(SFMFindReferencesAction.ID, offers.get(1).choice().actionId());
        assertEquals(1, sessions.size());
        List<String> copyCommands = offers.subList(2, offers.size()).stream()
                .map(value -> value.choice().command())
                .toList();
        String sessionArgument = copyCommands.get(0).substring(copyCommands.get(0).lastIndexOf(' ') + 1);
        assertTrue(copyCommands.stream().allMatch(value -> value.endsWith(" " + sessionArgument)));
        long sessionId = Long.parseLong(sessionArgument);
        assertEquals("A", sessions.find(sessionId).orElseThrow().region().selectedText());
        assertEquals(
                List.of(SFMSymbolInspectionFormatters.Projection.values()),
                offers.subList(2, offers.size()).stream()
                        .map(value -> projection(value.choice().actionId().toString()))
                        .toList()
        );

        provider.offers(request("class B {}\n", 0, 6));
        assertEquals("A", sessions.find(sessionId).orElseThrow().region().selectedText());
    }

    @Test
    void punctuationStillOffersTruthfulCopiesWithoutPretendingNavigationExists() {
        SFMSymbolInspectionSessions sessions = new SFMSymbolInspectionSessions();
        SFMJavaSymbolContextActionProvider provider =
                new SFMJavaSymbolContextActionProvider(sessions, "1.19.2");

        List<SFMContextActionProvider.Offer> offers = provider.offers(request("class A;\n", 0, 7));

        assertEquals(7, offers.size());
        assertEquals(1, sessions.size());
        assertTrue(offers.stream().allMatch(value -> value.choice().actionId().getPath()
                .startsWith("symbol/copy/")));
        long session = Long.parseLong(offers.get(0).choice().command()
                .substring(offers.get(0).choice().command().lastIndexOf(' ') + 1));
        assertEquals("java-punctuation", sessions.find(session).orElseThrow().region().semanticKind());
    }

    @Test
    void endOfDocumentCaretIntentionallyRetainsPrecedingIdentifierNavigation() {
        SFMSymbolInspectionSessions sessions = new SFMSymbolInspectionSessions();
        SFMJavaSymbolContextActionProvider provider =
                new SFMJavaSymbolContextActionProvider(sessions, "1.19.2");

        List<SFMContextActionProvider.Offer> offers = provider.offers(request("class End", 0, 9));

        assertEquals(9, offers.size());
        long session = Long.parseLong(offers.get(2).choice().command()
                .substring(offers.get(2).choice().command().lastIndexOf(' ') + 1));
        assertEquals("End", sessions.find(session).orElseThrow().region().selectedText());
        assertEquals("java-identifier", sessions.find(session).orElseThrow().region().semanticKind());
    }

    @Test
    void annotationSigilIsAnIntentionalIdentifierExtentRatherThanGenericPunctuation() {
        SFMSymbolInspectionSessions sessions = new SFMSymbolInspectionSessions();
        SFMJavaSymbolContextActionProvider provider =
                new SFMJavaSymbolContextActionProvider(sessions, "1.19.2");

        List<SFMContextActionProvider.Offer> offers = provider.offers(request("@Deprecated class A {}\n", 0, 0));

        assertEquals(9, offers.size());
        long session = Long.parseLong(offers.get(2).choice().command()
                .substring(offers.get(2).choice().command().lastIndexOf(' ') + 1));
        assertEquals("@Deprecated", sessions.find(session).orElseThrow().region().selectedText());
        assertEquals("java-identifier", sessions.find(session).orElseThrow().region().semanticKind());
    }

    private static SFMSymbolInspectionFormatters.Projection projection(String actionId) {
        for (SFMSymbolInspectionFormatters.Projection value : SFMSymbolInspectionFormatters.Projection.values()) {
            if (SFMSymbolCopyAction.id(value).toString().equals(actionId)) return value;
        }
        throw new AssertionError("Unexpected symbol copy action " + actionId);
    }

    private static SFMContextActionProvider.Request request(String text, int line, int column) {
        SFMPath root = SFMPath.fromNative(Path.of("D:/repo/src"));
        SFMPath path = SFMPath.fromNative(Path.of("D:/repo/src/p/A.java"));
        SFMTextDocumentSnapshot baseline = new SFMTextDocumentSnapshot(
                SFMTextDocumentSnapshot.State.READY,
                text,
                SFMTextDocumentSnapshot.MutationCapability.READ_ONLY,
                Optional.of(path),
                Optional.of(root),
                Optional.of(SFMContextTextCoordinates.sha256(text)),
                OptionalLong.of(text.getBytes(StandardCharsets.UTF_8).length),
                Optional.empty(),
                Optional.of(SFMResolverTextResult.LineEndingKind.LF),
                Optional.empty(),
                List.of()
        );
        var position = SFMContextTextCoordinates.atLineColumn(text, line, column);
        SFMContextDocumentProjection document = SFMContextDocumentProjection.capture(
                "sfm:text-editor-v3",
                baseline,
                text,
                false,
                true,
                List.of(new SFMContextCursorProjection(
                        "primary", new SFMContextPosition.Text(position), true, true)),
                List.of()
        );
        SFMContextOriginId origin = new SFMContextOriginId("sfm:text-editor", "panel-1", "document");
        SFMContextContribution contribution = new SFMContextContribution(
                origin, SFMContextGenerationEvidence.INITIAL, document);
        SFMContextSnapshot snapshot = new SFMContextSnapshot(
                1, 2, 3, Optional.of(origin), List.of(contribution));
        return new SFMContextActionProvider.Request(
                new SFMClientActionContext(new Object(), () -> true, null),
                snapshot,
                Optional.of(contribution)
        );
    }
}
