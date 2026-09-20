package ca.teamdman.sfm.client.text_editor;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SFMTextDocumentSaveSessionTests {
    @Test
    void doneWaitsForPersistenceAndClientDeliveryWithoutSubmittingDuplicates() {
        var session = new SFMTextDocumentSaveSession();
        var durable = new CompletableFuture<SFMTextDocumentSaveResult>();
        var client = new ArrayDeque<Runnable>();
        var outcomes = new ArrayList<SFMTextDocumentSaveSession.Completion>();
        var writes = new AtomicInteger();
        java.util.function.Function<String, CompletableFuture<SFMTextDocumentSaveResult>> writer = text -> {
            writes.incrementAndGet();
            assertEquals("note", text);
            return durable;
        };
        assertTrue(session.submit("note", false, writer, client::add, outcomes::add));
        assertFalse(session.submit("note", true, writer, client::add, outcomes::add));
        assertEquals(1, writes.get());
        assertTrue(session.pending());
        assertTrue(outcomes.isEmpty());
        durable.complete(SFMTextDocumentSaveResult.success());
        assertTrue(session.pending(), "the UI remains pending until its own-thread callback");
        assertTrue(outcomes.isEmpty());
        client.remove().run();
        assertFalse(session.pending());
        assertEquals(1, outcomes.size());
        assertTrue(outcomes.get(0).mayClose("note"));
        assertFalse(outcomes.get(0).mayClose("note plus newer edits"));
        assertEquals("note", outcomes.get(0).submittedText());
    }

    @Test
    void doneOnNewerTextDoesNotSilentlySaveOrCloseIt() {
        var session = new SFMTextDocumentSaveSession();
        var durable = new CompletableFuture<SFMTextDocumentSaveResult>();
        var outcomes = new ArrayList<SFMTextDocumentSaveSession.Completion>();
        session.submit("first", false, text -> durable, Runnable::run, outcomes::add);
        assertFalse(session.submit("second", true, text -> fail("must not start a second concurrent save"),
                Runnable::run, outcomes::add));
        durable.complete(SFMTextDocumentSaveResult.success());
        assertFalse(outcomes.get(0).closeRequested());
        assertFalse(outcomes.get(0).mayClose("second"));
        assertTrue(session.submit("second", true, text -> CompletableFuture.completedFuture(
                SFMTextDocumentSaveResult.success()), Runnable::run, outcomes::add));
        assertTrue(outcomes.get(1).mayClose("second"));
    }

    @Test
    void failuresRemainRetryableAndNeverCloseTheEditor() {
        var session = new SFMTextDocumentSaveSession();
        var outcomes = new ArrayList<SFMTextDocumentSaveSession.Completion>();
        session.submit("draft", true, text -> { throw new IllegalStateException("disk unavailable"); },
                Runnable::run, outcomes::add);
        assertFalse(session.pending());
        assertFalse(outcomes.get(0).mayClose("draft"));
        assertEquals("disk unavailable", outcomes.get(0).result().diagnostic().orElseThrow().getString());
        session.submit("draft", true, text -> CompletableFuture.failedFuture(
                new java.util.concurrent.CompletionException(new IllegalStateException("cancelled"))),
                Runnable::run, outcomes::add);
        assertFalse(outcomes.get(1).mayClose("draft"));
        assertEquals("cancelled", outcomes.get(1).result().diagnostic().orElseThrow().getString());
        session.submit("draft", true, text -> CompletableFuture.completedFuture(
                SFMTextDocumentSaveResult.success()), Runnable::run, outcomes::add);
        assertTrue(outcomes.get(2).mayClose("draft"));
    }

    @Test
    void detachedCompletionCannotCloseOrClearANewerSave() {
        var session = new SFMTextDocumentSaveSession();
        var old = new CompletableFuture<SFMTextDocumentSaveResult>();
        var next = new CompletableFuture<SFMTextDocumentSaveResult>();
        var outcomes = new ArrayList<SFMTextDocumentSaveSession.Completion>();
        var client = new ArrayDeque<Runnable>();
        session.submit("old", true, text -> old, client::add, outcomes::add);
        old.complete(SFMTextDocumentSaveResult.success());
        session.detach();
        session.submit("next", true, text -> next, client::add, outcomes::add);
        client.remove().run();
        assertTrue(session.pending());
        assertTrue(outcomes.isEmpty());
        next.complete(SFMTextDocumentSaveResult.success());
        client.remove().run();
        assertEquals(1, outcomes.size());
        assertTrue(outcomes.get(0).mayClose("next"));
    }
}
