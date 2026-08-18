package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceCopyFeedback;
import ca.teamdman.sfm.client.symbol.SFMSymbolInspectionFormatters;
import ca.teamdman.sfm.client.symbol.SFMSymbolInspectionSessions;
import ca.teamdman.sfm.client.symbol.SFMSymbolInspectionSnapshot;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentPosition;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SFMSymbolCopyActionTests {
    @Test
    void commandCopiesExactFormatterBytesAndEmitsOneSharedFeedbackMessage() throws Exception {
        SFMSymbolInspectionSessions sessions = new SFMSymbolInspectionSessions();
        SFMSymbolInspectionSnapshot snapshot = snapshot();
        long sessionId = sessions.capture(snapshot).id();
        ArrayList<String> clipboard = new ArrayList<>();
        ArrayList<Component> confirmations = new ArrayList<>();
        SFMSymbolCopyAction action = new SFMSymbolCopyAction(
                SFMSymbolInspectionFormatters.Projection.DETAILS,
                sessions,
                (context, text, confirmation) -> {
                    clipboard.add(text);
                    confirmations.add(confirmation);
                }
        );
        CommandDispatcher<SFMClientActionSource> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(action.createCommandNode(
                SFMSymbolCopyAction.id(SFMSymbolInspectionFormatters.Projection.DETAILS)).build());
        SFMClientActionContext context = new SFMClientActionContext(
                headlessWorkspace(), () -> true, null);

        int result = dispatcher.execute(
                SFMSymbolCopyAction.id(SFMSymbolInspectionFormatters.Projection.DETAILS)
                        + " " + sessionId,
                new SFMClientActionSource(context)
        );

        assertEquals(1, result);
        assertEquals(List.of(SFMSymbolInspectionFormatters.format(
                snapshot, SFMSymbolInspectionFormatters.Projection.DETAILS)), clipboard);
        assertEquals(List.of("Copied symbol details to the clipboard"),
                confirmations.stream().map(Component::getString).toList());
    }

    @Test
    void missingSessionFailsInsteadOfRecapturingCurrentFocus() {
        SFMSymbolInspectionSessions sessions = new SFMSymbolInspectionSessions();
        SFMSymbolCopyAction action = new SFMSymbolCopyAction(
                SFMSymbolInspectionFormatters.Projection.LINE,
                sessions,
                (context, text, confirmation) -> { }
        );
        CommandDispatcher<SFMClientActionSource> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(action.createCommandNode(
                SFMSymbolCopyAction.id(SFMSymbolInspectionFormatters.Projection.LINE)).build());
        SFMClientActionSource source = new SFMClientActionSource(new SFMClientActionContext(
                headlessWorkspace(), () -> true, null));

        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute(
                SFMSymbolCopyAction.id(SFMSymbolInspectionFormatters.Projection.LINE) + " 999",
                source));
    }

    @Test
    void productionRouteUsesDedicatedCoalescedClipboardConfirmationLane() throws Exception {
        SFMSymbolInspectionSessions sessions = new SFMSymbolInspectionSessions();
        SFMSymbolInspectionSnapshot snapshot = snapshot();
        long firstSession = sessions.capture(snapshot).id();
        long secondSession = sessions.capture(snapshot).id();
        ArrayList<String> clipboard = new ArrayList<>();
        SFMScreenMultiplexer workspace = headlessWorkspace();
        var unrelated = workspace.showWorkspaceToast(
                Component.literal("Unrelated workspace status"), false);
        SFMSymbolCopyAction action = new SFMSymbolCopyAction(
                SFMSymbolInspectionFormatters.Projection.DETAILS,
                sessions,
                SFMWorkspaceCopyFeedback.writingTo(clipboard::add)
        );
        CommandDispatcher<SFMClientActionSource> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(action.createCommandNode(
                SFMSymbolCopyAction.id(SFMSymbolInspectionFormatters.Projection.DETAILS)).build());
        SFMClientActionSource source = new SFMClientActionSource(new SFMClientActionContext(
                workspace, () -> true, null));

        dispatcher.execute(SFMSymbolCopyAction.id(SFMSymbolInspectionFormatters.Projection.DETAILS)
                + " " + firstSession, source);
        var firstConfirmation = workspace.latestWorkspaceToast().orElseThrow();
        dispatcher.execute(SFMSymbolCopyAction.id(SFMSymbolInspectionFormatters.Projection.DETAILS)
                + " " + secondSession, source);
        var secondConfirmation = workspace.latestWorkspaceToast().orElseThrow();

        assertEquals(2, clipboard.size());
        assertEquals("sfm:clipboard-copy-confirmation", secondConfirmation.replacementKey());
        assertTrue(workspace.workspaceToastSnapshot(unrelated).isPresent());
        assertTrue(workspace.workspaceToastSnapshot(firstConfirmation.id()).isEmpty());
        assertEquals(2, workspace.activeWorkspaceToastIds().size());
    }

    private static SFMSymbolInspectionSnapshot snapshot() {
        String hash = "0".repeat(64);
        SFMTextDocumentPosition start = new SFMTextDocumentPosition(0, 0, 0);
        SFMTextDocumentPosition end = new SFMTextDocumentPosition(0, 1, 1);
        return new SFMSymbolInspectionSnapshot(
                SFMSymbolInspectionSnapshot.SCHEMA,
                1, 2, 3,
                "sfm:text-editor/panel-1/document",
                4, 5, 6, 7,
                new SFMSymbolInspectionSnapshot.Document(
                        "sfm:text-editor-v3",
                        "ready",
                        Optional.of("file:///D:/repo/src/p/A.java"),
                        Optional.of("file"),
                        Optional.of("main-java"),
                        Optional.of("file:///D:/repo/src"),
                        Optional.of("p/A.java"),
                        Optional.of("platform/minecraft/src/main/java/p/A.java"),
                        Optional.of("main"),
                        Optional.of(hash),
                        hash,
                        "A",
                        false,
                        true
                ),
                new SFMSymbolInspectionSnapshot.Point(start, Optional.empty()),
                new SFMSymbolInspectionSnapshot.Region(
                        new SFMTextDocumentRange(start, end),
                        "A",
                        Optional.of("region-A"),
                        "java-class-reference",
                        List.of("java-class-reference[region-A]"),
                        List.of(),
                        List.of(), List.of(), List.of(), List.of(), Optional.empty()
                ),
                List.of(SFMSymbolInspectionSnapshot.Symbol.fromAccessTransformerReference("p.A")),
                "resolved",
                "complete",
                List.of(), List.of(), List.of(),
                "sfm-propagate-changes.exe symbol show-definition 'p.A' --branch '1.19.2'"
        );
    }

    private static SFMScreenMultiplexer headlessWorkspace() {
        try {
            java.lang.reflect.Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            return (SFMScreenMultiplexer) unsafe.allocateInstance(SFMScreenMultiplexer.class);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Could not construct a headless workspace", failure);
        }
    }
}
