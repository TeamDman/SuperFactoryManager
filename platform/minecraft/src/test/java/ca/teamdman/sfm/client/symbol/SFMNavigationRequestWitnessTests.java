package ca.teamdman.sfm.client.symbol;

import ca.teamdman.sfm.client.context.SFMContextContribution;
import ca.teamdman.sfm.client.context.SFMContextCursorProjection;
import ca.teamdman.sfm.client.context.SFMContextDocumentProjection;
import ca.teamdman.sfm.client.context.SFMContextGenerationEvidence;
import ca.teamdman.sfm.client.context.SFMContextOriginId;
import ca.teamdman.sfm.client.context.SFMContextPosition;
import ca.teamdman.sfm.client.context.SFMContextSelectionProjection;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.SFMResolverTextResult;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMNavigationRequestWitnessTests {
    private static final SFMContextOriginId ORIGIN =
            new SFMContextOriginId("sfm:text-editor", "panel-7", "editor-v3");
    private static final SFMWorkspacePanelId PANEL_ID = new SFMWorkspacePanelId(7);
    private static final long REQUEST_GENERATION = 11;
    private static final long PROVIDER_GENERATION = 23;

    @Test
    void cursorSelectionHoverFocusAndCaptureChangesPreserveTheStableWitness() {
        String text = "class Use { Target value; }\n";
        SFMScreenMultiplexer workspace = uninitializedWorkspace();
        Object panelEntry = new Object();
        SFMContextContribution captured = contribution(
                text, 12, 10.25, 30.5, List.of(), 1, PROVIDER_GENERATION);
        SFMNavigationRequestWitness witness = SFMNavigationRequestWitness.capture(
                workspace, PANEL_ID, panelEntry, captured, REQUEST_GENERATION);
        SFMTextDocumentRange selection = new SFMTextDocumentRange(
                SFMTextDocumentRange.positionAtByteOffset(text, 0),
                SFMTextDocumentRange.positionAtByteOffset(text, 5));
        SFMContextContribution repaintedAndMoved = contribution(
                text, 19, 99.75, 105.5, List.of(selection), 999, PROVIDER_GENERATION);

        SFMNavigationRequestWitness.Validation validation = witness.validate(
                workspace,
                Optional.of(panelEntry),
                Optional.of(repaintedAndMoved),
                REQUEST_GENERATION
        );

        assertTrue(validation.isValid(),
                "later cursor, selection, canvas-hover coordinates, and contributor generations are presentation state");
        assertEquals(12, ((SFMContextPosition.Canvas) witness.semanticRequest().position().orElseThrow())
                .textHit().orElseThrow().byteOffset(),
                "the immutable witness must retain the originally submitted semantic position");
    }

    @Test
    void everyFailClosedDimensionProducesItsExactTypedReason() {
        String text = "class Use { Target value; }\n";
        SFMScreenMultiplexer workspace = uninitializedWorkspace();
        Object panelEntry = new Object();
        SFMContextContribution captured = addressedContribution(
                text, "file:///D:/workspace/src/Use.java", 12, 1, PROVIDER_GENERATION);
        SFMNavigationRequestWitness witness = SFMNavigationRequestWitness.capture(
                workspace, PANEL_ID, panelEntry, captured, REQUEST_GENERATION);

        assertReason(
                SFMNavigationRequestWitness.RejectionReason.SUPERSEDED_REQUEST,
                witness.validate(workspace, Optional.of(panelEntry), Optional.of(captured), REQUEST_GENERATION + 1));
        assertReason(
                SFMNavigationRequestWitness.RejectionReason.PANEL_REMOVED,
                witness.validate(workspace, Optional.empty(), Optional.of(captured), REQUEST_GENERATION));
        assertReason(
                SFMNavigationRequestWitness.RejectionReason.PANEL_REPLACED,
                witness.validate(workspace, Optional.of(new Object()), Optional.of(captured), REQUEST_GENERATION));
        assertReason(
                SFMNavigationRequestWitness.RejectionReason.DOCUMENT_REMOVED,
                witness.validate(workspace, Optional.of(panelEntry), Optional.empty(), REQUEST_GENERATION));
        assertReason(
                SFMNavigationRequestWitness.RejectionReason.DOCUMENT_ADDRESS_CHANGED,
                witness.validate(
                        workspace,
                        Optional.of(panelEntry),
                        Optional.of(addressedContribution(
                                text,
                                "file:///D:/workspace/src/Replaced.java",
                                12,
                                2,
                                PROVIDER_GENERATION)),
                        REQUEST_GENERATION));
        assertReason(
                SFMNavigationRequestWitness.RejectionReason.DOCUMENT_CONTENT_CHANGED,
                witness.validate(
                        workspace,
                        Optional.of(panelEntry),
                        Optional.of(addressedContribution(
                                "class Use { Other value; }\n",
                                "file:///D:/workspace/src/Use.java",
                                12,
                                2,
                                PROVIDER_GENERATION)),
                        REQUEST_GENERATION));
        assertReason(
                SFMNavigationRequestWitness.RejectionReason.PROVIDER_GENERATION_CHANGED,
                witness.validate(
                        workspace,
                        Optional.of(panelEntry),
                        Optional.of(addressedContribution(
                                text,
                                "file:///D:/workspace/src/Use.java",
                                12,
                                2,
                                PROVIDER_GENERATION + 1)),
                        REQUEST_GENERATION));
    }

    private static void assertReason(
            SFMNavigationRequestWitness.RejectionReason expected,
            SFMNavigationRequestWitness.Validation validation
    ) {
        assertEquals(Optional.of(expected), validation.rejection());
    }

    private static SFMContextContribution contribution(
            String text,
            int byteOffset,
            double canvasX,
            double canvasY,
            List<SFMTextDocumentRange> selections,
            long generation,
            long providerGeneration
    ) {
        SFMTextDocumentSnapshot baseline = SFMTextDocumentSnapshot.literal(text);
        return contribution(
                baseline,
                text,
                byteOffset,
                canvasX,
                canvasY,
                selections,
                generation,
                providerGeneration
        );
    }

    private static SFMContextContribution addressedContribution(
            String text,
            String address,
            int byteOffset,
            long generation,
            long providerGeneration
    ) {
        SFMTextDocumentSnapshot baseline = new SFMTextDocumentSnapshot(
                SFMTextDocumentSnapshot.State.READY,
                text,
                SFMTextDocumentSnapshot.MutationCapability.READ_ONLY,
                Optional.of(SFMPath.parse(address)),
                Optional.of(SFMPath.parse("file:///D:/workspace/src/")),
                Optional.of(sha256(text)),
                OptionalLong.of(text.getBytes(StandardCharsets.UTF_8).length),
                Optional.empty(),
                Optional.of(SFMResolverTextResult.LineEndingKind.LF),
                Optional.empty(),
                List.of()
        );
        return contribution(
                baseline,
                text,
                byteOffset,
                10.0,
                20.0,
                List.of(),
                generation,
                providerGeneration
        );
    }

    private static SFMContextContribution contribution(
            SFMTextDocumentSnapshot baseline,
            String text,
            int byteOffset,
            double canvasX,
            double canvasY,
            List<SFMTextDocumentRange> selections,
            long generation,
            long providerGeneration
    ) {
        SFMContextDocumentProjection projection = SFMContextDocumentProjection.capture(
                "editor-v3",
                baseline,
                text,
                false,
                baseline.readOnly(),
                List.of(new SFMContextCursorProjection(
                        "primary",
                        new SFMContextPosition.Canvas(
                                canvasX,
                                canvasY,
                                Optional.of(SFMTextDocumentRange.positionAtByteOffset(text, byteOffset))
                        ),
                        true,
                        true
                )),
                selections.isEmpty()
                        ? List.of()
                        : List.of(new SFMContextSelectionProjection("selection", selections, true))
        );
        return new SFMContextContribution(
                ORIGIN,
                new SFMContextGenerationEvidence(
                        generation,
                        generation,
                        generation,
                        providerGeneration
                ),
                projection
        );
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static SFMScreenMultiplexer uninitializedWorkspace() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (SFMScreenMultiplexer) ((Unsafe) field.get(null))
                    .allocateInstance(SFMScreenMultiplexer.class);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Could not allocate a headless workspace test double", failure);
        }
    }
}
