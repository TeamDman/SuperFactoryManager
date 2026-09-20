package ca.teamdman.sfm.client.history.document.runtime;

import ca.teamdman.sfm.client.history.SFMDocumentHistoryHostController;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistorySession;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMDocumentHistoryRuntimeTests {
    @Test
    void registrationFocusMutationAndUnregisterArePushed() {
        SFMDocumentHistoryRuntime runtime = new SFMDocumentHistoryRuntime();
        SFMDocumentHistorySession session = session("sfm:test/editor-1");
        AtomicReference<SFMDocumentHistoryContract.DocumentState> checkedOut = new AtomicReference<>();
        SFMDocumentHistoryHostController controller = new SFMDocumentHistoryHostController(
                session, "test", "input", checkedOut::set);
        ArrayList<SFMDocumentHistoryRuntime.CatalogEvent> events = new ArrayList<>();

        SFMDocumentHistoryRuntime.Subscription subscription = runtime.subscribe(events::add);
        SFMDocumentHistoryRuntime.Registration registration = runtime.register(controller);
        assertEquals(SFMDocumentHistoryRuntime.ChangeKind.CURRENT, events.get(0).kind());
        assertEquals(SFMDocumentHistoryRuntime.ChangeKind.REGISTERED, events.get(1).kind());
        assertSame(controller, runtime.exact("sfm:test/editor-1").orElseThrow()
                .controller().orElseThrow());

        assertTrue(registration.focus());
        assertEquals("sfm:test/editor-1", runtime.focused().orElseThrow().sessionId());
        controller.observeMutation(
                SFMDocumentHistoryContract.MutationKind.TYPE,
                SFMDocumentHistoryContract.EditDirection.FORWARD,
                session.currentState(),
                SFMDocumentHistoryContract.DocumentState.withCaret("hello", 5),
                Optional.of("hello"),
                1,
                "type"
        );
        assertEquals(SFMDocumentHistoryRuntime.ChangeKind.SESSION_CHANGED,
                events.get(events.size() - 1).kind());

        registration.close();
        assertTrue(runtime.sessionIds().isEmpty());
        assertEquals(SFMDocumentHistoryRuntime.ChangeKind.UNREGISTERED,
                events.get(events.size() - 1).kind());
        subscription.close();
        controller.close();
    }

    @Test
    void aViewerMayRegisterAReadOnlySessionWithoutAnOperationController() {
        SFMDocumentHistoryRuntime runtime = new SFMDocumentHistoryRuntime();
        SFMDocumentHistorySession session = session("sfm:test/chamber");
        try (SFMDocumentHistoryRuntime.Registration ignored = runtime.registerFocused(session)) {
            var resolved = runtime.resolve(SFMDocumentHistorySelector.focused());
            assertEquals(SFMDocumentHistoryRuntime.ResolutionStatus.RESOLVED, resolved.status());
            assertTrue(resolved.session().orElseThrow().controller().isEmpty());
        }
    }

    private static SFMDocumentHistorySession session(String id) {
        return SFMDocumentHistorySession.create(
                new SFMDocumentHistoryContract.SessionIdentity(id, id + "/document", Optional.empty()),
                SFMDocumentHistoryContract.DocumentState.withCaret("", 0)
        );
    }
}
