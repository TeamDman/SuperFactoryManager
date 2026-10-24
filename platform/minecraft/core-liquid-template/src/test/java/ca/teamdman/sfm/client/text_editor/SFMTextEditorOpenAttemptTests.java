package ca.teamdman.sfm.client.text_editor;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SFMTextEditorOpenAttemptTests {
    @Test
    void successfulFactoryPresentsExactlyItsPreparedScreenOnce() {
        Object screen = new Object();
        var calls = new AtomicInteger();
        var shown = new AtomicReference<>();
        assertTrue(SFMTextEditorOpenAttempt.open(() -> { calls.incrementAndGet(); return screen; },
                shown::set, failure -> fail(failure)));
        assertSame(screen, shown.get());
        assertEquals(1, calls.get());
    }

    @Test
    void failingFactoryDoesNotReplaceTheCallerRetryOrChangeItsDocument() {
        for (Throwable failure : new Throwable[] {
                new IllegalStateException("broken addon"), new NoClassDefFoundError("missing addon dependency")
        }) {
            String unsaved = "  α😀\n\nunsaved text\n";
            var currentDocument = new AtomicReference<>(unsaved);
            Object caller = new Object();
            var currentScreen = new AtomicReference<>(caller);
            var calls = new AtomicInteger();
            var reported = new AtomicReference<Throwable>();
            assertFalse(SFMTextEditorOpenAttempt.open(() -> {
                calls.incrementAndGet();
                assertSame(unsaved, currentDocument.get());
                if (failure instanceof RuntimeException exception) throw exception;
                throw (LinkageError) failure;
            }, currentScreen::set, reported::set));
            assertSame(caller, currentScreen.get());
            assertSame(unsaved, currentDocument.get());
            assertSame(failure, reported.get());
            assertEquals(1, calls.get());
        }
    }

    @Test
    void nullFactoryResultIsReportedWithoutPresentation() {
        var reported = new AtomicReference<Throwable>();
        assertFalse(SFMTextEditorOpenAttempt.open(() -> null,
                ignored -> fail("Null screen must not be displayed"), reported::set));
        assertInstanceOf(NullPointerException.class, reported.get());
    }

    @Test
    void presentationFailureIsNotMisreportedAsSafeFactoryRecovery() {
        RuntimeException failure = new IllegalStateException("screen init failed");
        assertSame(failure, assertThrows(RuntimeException.class, () ->
                SFMTextEditorOpenAttempt.open(Object::new, ignored -> { throw failure; },
                        ignored -> fail("Presentation may already have changed the screen stack"))));
    }

    @Test
    void fatalVmErrorsAreNotSwallowed() {
        InternalError failure = new InternalError("unexpected fatal failure");
        assertSame(failure, assertThrows(InternalError.class, () ->
                SFMTextEditorOpenAttempt.open(() -> { throw failure; }, ignored -> fail(), ignored -> fail())));
    }
}
