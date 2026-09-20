package ca.teamdman.sfm.client.net;

import ca.teamdman.sfm.common.net.ClientboundClientInboxValuePacket;
import ca.teamdman.sfm.common.net.SFMClientInboxAddress;
import ca.teamdman.sfm.common.net.ServerboundClientInboxSubscriptionPacket;
import ca.teamdman.sfm.common.value.SFMValue;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMClientInboxTests {
    private static final ResourceLocation DIMENSION = new ResourceLocation("minecraft", "overworld");
    private static final ResourceLocation CHANNEL = new ResourceLocation("sfm", "dashboard");

    @Test
    void subscriptionsAreReferenceCountedAndIndependentOfProgramSource() {
        SFMClientInbox inbox = new SFMClientInbox();
        SFMClientInboxRuntime runtime = new SFMClientInboxRuntime(inbox);
        Object server = new Object();
        Object level = new Object();
        UUID owner = UUID.randomUUID();
        SFMClientInboxAddress address = new SFMClientInboxAddress(owner, DIMENSION, CHANNEL);
        List<ServerboundClientInboxSubscriptionPacket> sent = new ArrayList<>();
        runtime.observeSessionIdentity(server, level, owner, DIMENSION, true);

        SFMClientInboxRuntime.Subscription original = runtime.subscribe(address, sent::add).orElseThrow();
        SFMClientInboxRuntime.Subscription editedSource = runtime.subscribe(address, sent::add).orElseThrow();
        assertEquals(1, sent.size());
        assertTrue(sent.get(0).subscribe());
        UUID session = runtime.session().orElseThrow();
        assertTrue(runtime.receive(ClientboundClientInboxValuePacket.fromValue(
                session, address, SFMValue.of("red")
        )));

        original.close();
        assertEquals(1, sent.size());
        assertTrue(inbox.isSubscribed(CHANNEL));
        editedSource.close();
        editedSource.close();
        assertEquals(2, sent.size());
        assertFalse(sent.get(1).subscribe());
        assertFalse(inbox.isSubscribed(CHANNEL));
    }

    @Test
    void worldChangeAndWrongRecipientRejectStaleDelivery() {
        SFMClientInboxRuntime runtime = new SFMClientInboxRuntime(new SFMClientInbox());
        Object server = new Object();
        UUID owner = UUID.randomUUID();
        SFMClientInboxAddress address = new SFMClientInboxAddress(owner, DIMENSION, CHANNEL);
        runtime.observeSessionIdentity(server, new Object(), owner, DIMENSION, true);
        UUID oldSession = runtime.session().orElseThrow();
        runtime.subscribe(address, ignored -> { }).orElseThrow();
        assertFalse(runtime.receive(ClientboundClientInboxValuePacket.fromValue(
                oldSession,
                new SFMClientInboxAddress(UUID.randomUUID(), DIMENSION, CHANNEL),
                SFMValue.of("private")
        )));

        runtime.observeSessionIdentity(server, new Object(), owner, DIMENSION, true);
        UUID newSession = runtime.session().orElseThrow();
        assertNotEquals(oldSession, newSession);
        runtime.subscribe(address, ignored -> { }).orElseThrow();
        assertFalse(runtime.receive(ClientboundClientInboxValuePacket.fromValue(
                oldSession, address, SFMValue.of("stale")
        )));
        assertTrue(runtime.page(CHANNEL, Optional.empty(), 10).orElseThrow().entries().isEmpty());

        runtime.observeSessionIdentity(null, null, null, null, false);
        assertTrue(runtime.session().isEmpty());
        assertTrue(runtime.page(CHANNEL, Optional.empty(), 10).isEmpty());
    }

    @Test
    void boundedChannelReportsEvictionCursorAndUnsubscribeReset() {
        SFMClientInbox inbox = new SFMClientInbox();
        UUID session = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        SFMClientInboxAddress address = new SFMClientInboxAddress(owner, DIMENSION, CHANNEL);
        inbox.beginSession(session, owner, DIMENSION);
        assertTrue(inbox.subscribe(CHANNEL));
        assertTrue(inbox.append(session, address, SFMValue.of(0)));
        SFMClientInbox.Cursor stale = inbox.page(CHANNEL, Optional.empty(), 10).orElseThrow().nextCursor();
        for (int i = 1; i <= SFMClientInbox.MAX_ENTRIES_PER_CHANNEL; i++) {
            assertTrue(inbox.append(session, address, SFMValue.of(i)));
        }
        SFMClientInbox.Page overflow = inbox.page(CHANNEL, Optional.of(stale), 100).orElseThrow();
        assertEquals(SFMClientInbox.Continuity.CONTIGUOUS, overflow.continuity());
        assertEquals(SFMClientInbox.MAX_ENTRIES_PER_CHANNEL, overflow.entries().size());
        assertEquals(2, overflow.oldestSequence());

        inbox.unsubscribe(CHANNEL);
        assertFalse(inbox.isSubscribed(CHANNEL));
        assertTrue(inbox.subscribe(CHANNEL));
        SFMClientInbox.Page reopened = inbox.page(CHANNEL, Optional.of(stale), 10).orElseThrow();
        assertEquals(SFMClientInbox.Continuity.SESSION_CHANGED, reopened.continuity());
        assertTrue(reopened.entries().isEmpty());
    }

    @Test
    void staleCursorShowsGapAndOversizedPayloadIsRejected() {
        SFMClientInbox inbox = new SFMClientInbox();
        UUID session = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        SFMClientInboxAddress address = new SFMClientInboxAddress(owner, DIMENSION, CHANNEL);
        inbox.beginSession(session, owner, DIMENSION);
        inbox.subscribe(CHANNEL);
        SFMClientInbox.Cursor cursor = inbox.page(CHANNEL, Optional.empty(), 10).orElseThrow().nextCursor();
        for (int i = 0; i < SFMClientInbox.MAX_ENTRIES_PER_CHANNEL + 2; i++) {
            inbox.append(session, address, SFMValue.of(i));
        }
        assertEquals(
                SFMClientInbox.Continuity.EVICTED_GAP,
                inbox.page(CHANNEL, Optional.of(cursor), 10).orElseThrow().continuity()
        );
        assertFalse(inbox.append(session, address, SFMValue.of("x".repeat(4_000))));
    }
}
