package ca.teamdman.sfm.client.program;

import ca.teamdman.sfml.ast.ProgramExecutionSide;
import ca.teamdman.sfm.client.net.SFMClientInbox;
import ca.teamdman.sfm.common.net.SFMClientInboxAddress;
import ca.teamdman.sfm.common.value.SFMValue;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static ca.teamdman.sfm.client.program.ClientProgramConsentGate.Decision.APPROVE;
import static ca.teamdman.sfm.client.program.ClientProgramConsentGate.EXECUTE;
import static ca.teamdman.sfm.client.program.ClientProgramInboxReadSurface.READ;
import static ca.teamdman.sfm.client.program.ClientProgramInboxReadSurface.Status.PAGE;
import static ca.teamdman.sfm.client.program.ClientProgramInboxReadSurface.Status.UNAVAILABLE_AWAITING_CONSENT;
import static ca.teamdman.sfm.client.program.ClientProgramInboxReadSurface.Status.UNAVAILABLE_DENIED_BY_USER;
import static ca.teamdman.sfm.client.program.ClientProgramInboxReadSurface.Status.UNAVAILABLE_NO_SESSION;
import static ca.teamdman.sfm.client.program.ClientProgramInboxReadSurface.Status.UNAVAILABLE_OUT_OF_SCOPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ca.teamdman.sfm.client.program.ClientProgramConsentGate.Decision.DENY;

class ClientProgramInboxReadSurfaceTests {
    private static final ResourceLocation DIMENSION = new ResourceLocation("minecraft", "overworld");
    private static final ResourceLocation CHANNEL = new ResourceLocation("sfm", "dashboard");
    private static final UUID RECIPIENT = UUID.fromString("de1baa7a-a3ec-44ca-bf1d-3f2c23c8f923");
    private static final ClientProgramConsentGate.Policy ALLOW = (identity, capability) -> List.of();

    private static ClientProgramIdentity identity() {
        return ClientProgramIdentity.fromStoredSource(
                "CLIENT BTW\nNAME \"dashboard\"", ProgramExecutionSide.CLIENT,
                ClientProgramWorldIdentity.integrated(UUID.randomUUID()), DIMENSION,
                new BlockPos(1, 64, 1), ClientProgramIdentity.CLIENT_MANAGER_RUNTIME,
                Set.of(EXECUTE, READ)
        );
    }

    private static void approve(ClientProgramConsentGate gate, ClientProgramIdentity program,
                                ResourceLocation capability) {
        gate.request(program, capability);
        gate.decide(program, capability, APPROVE);
    }

    private static final class FakeInboxAccess implements ClientProgramInboxReadSurface.InboxAccess {
        final SFMClientInbox inbox = new SFMClientInbox();
        final UUID session = UUID.randomUUID();
        int subscriptions;
        int releases;
        boolean failRelease;
        Runnable beforeRelease = () -> {};

        FakeInboxAccess() {
            inbox.beginSession(session, RECIPIENT, DIMENSION);
        }

        @Override
        public Optional<ClientProgramInboxReadSurface.Subscription> subscribe(SFMClientInboxAddress address) {
            if (!address.recipient().equals(RECIPIENT) || !address.dimension().equals(DIMENSION)) {
                return Optional.empty();
            }
            if (!inbox.subscribe(address.channel())) return Optional.empty();
            subscriptions++;
            return Optional.of(() -> {
                beforeRelease.run();
                releases++;
                inbox.unsubscribe(address.channel());
                if (failRelease) throw new IllegalStateException("Disconnected test transport");
            });
        }

        @Override
        public Optional<SFMClientInbox.Page> page(ResourceLocation channel,
                                                  Optional<SFMClientInbox.Cursor> cursor, int limit) {
            return inbox.page(channel, cursor, limit);
        }
    }

    @Test
    void consentAndExactChannelGateSubscriptionAndRevocationReleasesIt() {
        ClientProgramIdentity program = identity();
        ClientProgramConsentGate gate = new ClientProgramConsentGate();
        FakeInboxAccess access = new FakeInboxAccess();
        ClientProgramInboxReadSurface surface = new ClientProgramInboxReadSurface(
                program, gate, ALLOW, RECIPIENT, Set.of(CHANNEL), access
        );

        assertEquals(UNAVAILABLE_AWAITING_CONSENT, surface.page(CHANNEL, Optional.empty(), 10).status());
        approve(gate, program, EXECUTE);
        assertEquals(UNAVAILABLE_AWAITING_CONSENT, surface.page(CHANNEL, Optional.empty(), 10).status());
        approve(gate, program, READ);
        assertEquals(UNAVAILABLE_OUT_OF_SCOPE, surface.page(new ResourceLocation("sfm", "private"),
                Optional.empty(), 10).status());
        assertEquals(UNAVAILABLE_OUT_OF_SCOPE, surface.page(CHANNEL, Optional.empty(), 101).status());
        assertEquals(0, access.subscriptions);

        assertEquals(PAGE, surface.page(CHANNEL, Optional.empty(), 10).status());
        assertEquals(1, access.subscriptions);
        assertEquals(PAGE, surface.page(CHANNEL, Optional.empty(), 10).status());
        assertEquals(1, access.subscriptions);
        SFMClientInboxAddress address = new SFMClientInboxAddress(RECIPIENT, DIMENSION, CHANNEL);
        assertTrue(access.inbox.append(access.session, address, SFMValue.of("red")));
        SFMClientInbox.Page page = surface.page(CHANNEL, Optional.empty(), 10).page().orElseThrow();
        assertEquals(SFMValue.of("red"), page.entries().get(0).value());

        gate.revoke(program, READ);
        assertEquals(UNAVAILABLE_AWAITING_CONSENT, surface.page(CHANNEL, Optional.empty(), 10).status());
        assertEquals(1, access.releases);
        assertEquals(0, surface.activeSubscriptions());
        assertFalse(access.inbox.isSubscribed(CHANNEL));
        gate.request(program, READ);
        gate.decide(program, READ, DENY);
        assertEquals(UNAVAILABLE_DENIED_BY_USER, surface.page(CHANNEL, Optional.empty(), 10).status());
        surface.close();
        surface.close();
        assertEquals(1, access.releases);
        assertEquals(UNAVAILABLE_NO_SESSION, surface.page(CHANNEL, Optional.empty(), 10).status());
    }

    @Test
    void cursorContinuityAndSessionLossRemainVisibleToProgram() {
        ClientProgramIdentity program = identity();
        ClientProgramConsentGate gate = new ClientProgramConsentGate();
        approve(gate, program, EXECUTE);
        approve(gate, program, READ);
        FakeInboxAccess access = new FakeInboxAccess();
        ClientProgramInboxReadSurface surface = new ClientProgramInboxReadSurface(
                program, gate, ALLOW, RECIPIENT, Set.of(CHANNEL), access
        );
        SFMClientInbox.Cursor cursor = surface.page(CHANNEL, Optional.empty(), 10)
                .page().orElseThrow().nextCursor();
        SFMClientInboxAddress address = new SFMClientInboxAddress(RECIPIENT, DIMENSION, CHANNEL);
        for (int i = 0; i < SFMClientInbox.MAX_ENTRIES_PER_CHANNEL + 2; i++) {
            assertTrue(access.inbox.append(access.session, address, SFMValue.of(i)));
        }
        SFMClientInbox.Page gap = surface.page(CHANNEL, Optional.of(cursor), 10).page().orElseThrow();
        assertEquals(SFMClientInbox.Continuity.EVICTED_GAP, gap.continuity());
        assertEquals(10, gap.entries().size());
        access.inbox.endSession();
        assertEquals(UNAVAILABLE_NO_SESSION, surface.page(CHANNEL, Optional.empty(), 10).status());
        assertEquals(1, access.releases);
        surface.close();
    }

    @Test
    void closeDetachesEveryLeaseBeforeCallbacksAndContinuesAfterTransportFailure() {
        assertBulkCleanup(true);
    }

    @Test
    void consentRevocationDetachesEveryLeaseDespiteTransportFailure() {
        assertBulkCleanup(false);
    }

    private static void assertBulkCleanup(boolean close) {
        ClientProgramIdentity program = identity();
        ClientProgramConsentGate gate = new ClientProgramConsentGate();
        approve(gate, program, EXECUTE);
        approve(gate, program, READ);
        FakeInboxAccess access = new FakeInboxAccess();
        ResourceLocation second = new ResourceLocation("sfm", "second");
        ClientProgramInboxReadSurface surface = new ClientProgramInboxReadSurface(
                program, gate, ALLOW, RECIPIENT, Set.of(CHANNEL, second), access);
        assertEquals(PAGE, surface.page(CHANNEL, Optional.empty(), 1).status());
        assertEquals(PAGE, surface.page(second, Optional.empty(), 1).status());
        access.failRelease = true;
        access.beforeRelease = () -> assertEquals(0, surface.activeSubscriptions(), "Detach all leases before calling transport");
        if (close) surface.close();
        else {
            gate.revoke(program, READ);
            assertEquals(UNAVAILABLE_AWAITING_CONSENT, surface.page(CHANNEL, Optional.empty(), 1).status());
        }
        assertEquals(2, access.releases);
        assertEquals(0, surface.activeSubscriptions());
        surface.close();
        assertEquals(2, access.releases, "Release is not retried after an ambiguous network failure");
    }

    @Test
    void staleSingleLeaseFailureStillReturnsNoSessionAndDetachesOwnership() {
        ClientProgramIdentity program = identity();
        ClientProgramConsentGate gate = new ClientProgramConsentGate();
        approve(gate, program, EXECUTE);
        approve(gate, program, READ);
        FakeInboxAccess access = new FakeInboxAccess();
        ClientProgramInboxReadSurface surface = new ClientProgramInboxReadSurface(
                program, gate, ALLOW, RECIPIENT, Set.of(CHANNEL), access);
        assertEquals(PAGE, surface.page(CHANNEL, Optional.empty(), 1).status());
        access.inbox.endSession();
        access.failRelease = true;
        assertEquals(UNAVAILABLE_NO_SESSION, surface.page(CHANNEL, Optional.empty(), 1).status());
        assertEquals(0, surface.activeSubscriptions());
        assertEquals(1, access.releases);
        surface.close();
        assertEquals(1, access.releases);
    }
}
