package ca.teamdman.sfm.client.program;

import ca.teamdman.sfm.common.program.signature.ProgramAttestation;
import ca.teamdman.sfm.common.program.signature.ProgramSignatureDescriptor;
import ca.teamdman.sfm.common.program.signature.ClientManagerSigningBody;
import ca.teamdman.sfm.common.program.signature.ClientManagerSigningSnapshot;
import ca.teamdman.sfml.ast.ProgramExecutionSide;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static ca.teamdman.sfm.client.program.ClientProgramConsentGate.*;
import static org.junit.jupiter.api.Assertions.*;

class ClientProgramSignerTrustServiceTests {
    @TempDir Path directory;
    private static final String SOURCE = "CLIENT BTW";
    private final AtomicLong clock = new AtomicLong(1000);
    private final ClientProgramIdentity identity = ClientProgramIdentity.fromStoredSource(SOURCE,
            ProgramExecutionSide.CLIENT, ClientProgramWorldIdentity.integrated(new UUID(0, 1)),
            new ResourceLocation("minecraft:overworld"), new BlockPos(1, 2, 3),
            ClientProgramIdentity.CLIENT_MANAGER_RUNTIME, Set.of(EXECUTE, RENDER));

    private ProgramAttestation signed() throws Exception {
        return ProgramAttestation.sign(ProgramSignatureDescriptor.fromSource(SOURCE, identity.runtimeRevision(),
                identity.requestedCapabilities()), KeyPairGenerator.getInstance("Ed25519").generateKeyPair());
    }
    private ClientProgramSignerTrustGrant rule(ProgramAttestation signature, Long expiry) {
        return new ClientProgramSignerTrustGrant(signature.fingerprint(), identity.runtimeRevision(), identity.world(),
                identity.dimension(), identity.managerPosition(), identity.bindingSha256(), identity.requestedCapabilities(), expiry);
    }
    private ClientProgramSignerTrustService trust(Path file, ProgramAttestation signature, AtomicBoolean live) {
        return new ClientProgramSignerTrustService(file, clock::get, id -> live.get() && id.equals(identity)
                ? Optional.of(new ClientProgramSignerTrustService.ObservedProgram(SOURCE, List.of(signature))) : Optional.empty());
    }
    private ClientProgramConsentService consent(ClientProgramSignerAuthority authority) {
        var service = new ClientProgramConsentService(directory.resolve("consent.bin"), clock::get, authority);
        assertTrue(service.observe(identity, SOURCE, "", Map.of()).successful());
        return service;
    }

    @Test void signerRulesPersistButAlwaysRecheckLiveScopeAndExpiry() throws Exception {
        var signature = signed(); var live = new AtomicBoolean(true); Path path = directory.resolve("trust.bin");
        var trust = trust(path, signature, live);
        trust.trust(rule(signature, 2000L));
        var restored = trust(path, signature, live);
        assertTrue(restored.permits(identity, EXECUTE));
        assertTrue(restored.permits(identity, RENDER));
        live.set(false); assertFalse(restored.permits(identity, EXECUTE));
        live.set(true); assertTrue(restored.permits(identity, EXECUTE));
        clock.set(2000); assertFalse(restored.permits(identity, EXECUTE));
    }

    @Test void requestDoesNotDowngradeSignerAndForgetRemovesLocationTrust() throws Exception {
        var signature = signed(); var trust = trust(directory.resolve("trust.bin"), signature, new AtomicBoolean(true));
        trust.trust(rule(signature, null));
        var consent = consent(trust);
        assertFalse(consent.request(identity, EXECUTE).changed());
        assertEquals(Authority.TRUSTED_SIGNER, consent.gate().execution(identity, (id, cap) -> List.of()).authority());
        assertTrue(consent.forget(identity).successful());
        assertTrue(trust.snapshot().isEmpty());
        assertFalse(trust.permits(identity, EXECUTE));
    }

    @Test void stopAndResumeClearPersistedSignerRulesAndDoNotRestoreGrants() throws Exception {
        var signature = signed(); Path path = directory.resolve("trust.bin");
        var trust = trust(path, signature, new AtomicBoolean(true)); trust.trust(rule(signature, null));
        var consent = consent(trust);
        assertTrue(consent.stopAll().successful());
        assertTrue(trust.snapshot().isEmpty());
        assertTrue(ClientProgramSignerTrustStore.load(path).store().snapshot().isEmpty());
        assertTrue(consent.resume().successful());
        assertFalse(consent.gate().execution(identity, (id, cap) -> List.of()).allowed());
    }

    @Test void failedSignerRevocationKeepsDurableStopAndResumeCanRetry() {
        var fail = new AtomicBoolean(true);
        ClientProgramSignerAuthority authority = new ClientProgramSignerAuthority() {
            public boolean permits(ClientProgramIdentity id, ResourceLocation cap) { return true; }
            public void revokeAll() throws IOException { if (fail.get()) throw new IOException("fixture"); }
            public void revokeAtLocation(ClientProgramIdentity id) throws IOException { revokeAll(); }
        };
        var consent = consent(authority);
        assertFalse(consent.stopAll().successful());
        assertTrue(ClientProgramConsentStore.load(directory.resolve("consent.bin"), clock::get).store().stoppedAll());
        assertFalse(consent.resume().successful());
        assertTrue(consent.store().stoppedAll());
        fail.set(false); assertTrue(consent.resume().successful());
        assertFalse(consent.gate().execution(identity, (id, cap) -> List.of()).allowed());
    }

    @Test void failedSaveDisablesInMemoryTrustAndCorruptionDoesNotLoadOldRules() throws Exception {
        var signature = signed(); Path parent = directory.resolve("parent"); Files.writeString(parent, "not a directory");
        var trust = trust(parent.resolve("trust.bin"), signature, new AtomicBoolean(true));
        assertThrows(IOException.class, () -> trust.trust(rule(signature, null)));
        assertThrows(IllegalStateException.class, () -> trust.permits(identity, EXECUTE));
        Path corrupt = directory.resolve("corrupt.bin"); Files.writeString(corrupt, "invalid");
        var restored = trust(corrupt, signature, new AtomicBoolean(true));
        assertTrue(restored.snapshot().isEmpty());
        assertThrows(IllegalStateException.class, () -> restored.permits(identity, EXECUTE));
        assertThrows(IllegalArgumentException.class, () -> new ClientProgramSignerTrustService.ObservedProgram("CLIENT\rBTW", List.of()));
    }

    @Test void explicitSignerReviewRevalidatesLiveSourceAndExpiryBeforePersisting() throws Exception {
        var signature = signed(); var live = new AtomicBoolean(true);
        var trust = trust(directory.resolve("review.bin"), signature, live);
        assertEquals(List.of(signature.fingerprint()), trust.currentSigners(identity));
        live.set(false);
        assertThrows(IllegalStateException.class, () -> trust.trustCurrent(identity, signature.fingerprint(), null));
        assertTrue(trust.snapshot().isEmpty());
        live.set(true);
        assertThrows(IllegalArgumentException.class, () -> trust.trustCurrent(identity, signature.fingerprint(), clock.get()));
        assertThrows(IllegalStateException.class, () -> trust.trustCurrent(identity, signed().fingerprint(), null));
        trust.trustCurrent(identity, signature.fingerprint(), 2000L);
        assertEquals(List.of(rule(signature, 2000L)), trust.snapshot());
        assertTrue(trust.permits(identity, EXECUTE));
    }

    @Test void signerReviewDoesNotOfferHistoricalOrUnmatchedSourceSignatures() throws Exception {
        var signature = signed();
        var trust = new ClientProgramSignerTrustService(directory.resolve("history.bin"), clock::get,
                id -> Optional.of(new ClientProgramSignerTrustService.ObservedProgram(SOURCE + "\n", List.of(signature))));
        assertTrue(trust.currentSigners(identity).isEmpty());
        assertThrows(IllegalStateException.class, () -> trust.trustCurrent(identity, signature.fingerprint(), null));
        assertTrue(trust.snapshot().isEmpty());
    }

    @Test void corruptedExactConsentCannotRestoreSignerAuthorityAndExplicitRecoveryClearsRules() throws Exception {
        var signature = signed();
        for (String prior : List.of("denied", "revoked", "stopped")) {
            Path signerFile = directory.resolve(prior + "-trust.bin");
            Path consentFile = directory.resolve(prior + "-consent.bin");
            var live = new AtomicBoolean(true);
            var trust = trust(signerFile, signature, live);
            trust.trust(rule(signature, null));
            var before = new ClientProgramConsentService(consentFile, clock::get, trust);
            assertTrue(before.observe(identity, SOURCE, "", Map.of()).successful());
            if (prior.equals("denied")) {
                assertTrue(before.reopen(identity, EXECUTE).successful());
                assertTrue(before.decide(identity, EXECUTE, Decision.DENY, null, null).successful());
            } else if (prior.equals("revoked")) {
                assertTrue(before.revoke(identity, EXECUTE).successful());
            } else {
                // Durable stop followed by interrupted signer clearing is a valid crash boundary.
                before.store().setStoppedAll(true);
                assertTrue(before.retrySave().successful());
            }
            assertFalse(before.gate().execution(identity, (id, cap) -> List.of()).allowed());
            Files.writeString(consentFile, "corrupt exact-decision file");
            var restoredTrust = trust(signerFile, signature, live);
            assertTrue(restoredTrust.permits(identity, EXECUTE), "Signer file remains independently valid");
            var restored = new ClientProgramConsentService(consentFile, clock::get, restoredTrust);
            assertTrue(restored.store().stoppedAll());
            assertTrue(restored.diagnostic().contains("explicit recovery"));
            assertEquals(EffectiveState.BLOCKED_BY_POLICY, restored.gate().execution(identity, (id, cap) -> List.of()).effective());
            assertTrue(restored.retrySave().successful());
            assertTrue(new ClientProgramConsentService(consentFile, clock::get, restoredTrust).store().stoppedAll());
            assertTrue(restored.resume().successful());
            assertTrue(ClientProgramSignerTrustStore.load(signerFile).store().snapshot().isEmpty());
            assertFalse(restored.gate().execution(identity, (id, cap) -> List.of()).allowed());
            assertFalse(new ClientProgramConsentService(consentFile, clock::get, trust(signerFile, signature, live))
                    .gate().execution(identity, (id, cap) -> List.of()).allowed());
        }
    }

    @Test void recoveryDoesNotRemoveStopWhenSignerRevocationCannotBeSaved() throws Exception {
        Path consentFile = directory.resolve("broken-consent.bin");
        Files.writeString(consentFile, "invalid");
        ClientProgramSignerAuthority unavailable = new ClientProgramSignerAuthority() {
            public boolean permits(ClientProgramIdentity id, ResourceLocation capability) { return true; }
            public void revokeAll() throws IOException { throw new IOException("fixture write failure"); }
            public void revokeAtLocation(ClientProgramIdentity id) throws IOException { revokeAll(); }
        };
        var restored = new ClientProgramConsentService(consentFile, clock::get, unavailable);
        assertFalse(restored.gate().execution(identity, (id, cap) -> List.of()).allowed());
        assertFalse(restored.resume().successful());
        assertTrue(ClientProgramConsentStore.load(consentFile, clock::get).store().stoppedAll());
        assertFalse(new ClientProgramConsentService(consentFile, clock::get, unavailable)
                .gate().execution(identity, (id, cap) -> List.of()).allowed());
    }

    @Test void unchangedImmutableSigningSnapshotReusesObservedSourceAndHistoryUntilInvalidated() throws Exception {
        var signature = signed();
        var cache = new ClientProgramSignerTrustRuntime.ObservationCache<Object>();
        Object manager = new Object();
        var body = new ClientManagerSigningBody(SOURCE, Map.of());
        var snapshot = new ClientManagerSigningSnapshot(new UUID(1, 2), 3, body, List.of());
        var observed = cache.resolve(manager, identity, snapshot);
        assertSame(observed, cache.resolve(manager, identity, snapshot));
        // Metadata-only publication keeps the body revision but replaces immutable snapshot identity.
        var signed = new ClientManagerSigningSnapshot(snapshot.incarnation(), snapshot.revision(), body, List.of(signature));
        var withSignature = cache.resolve(manager, identity, signed);
        assertNotSame(observed, withSignature);
        assertEquals(List.of(signature), withSignature.attestations());
        assertSame(withSignature, cache.resolve(manager, identity, signed));
        var differentIdentity = ClientProgramIdentity.fromStoredSource(SOURCE, ProgramExecutionSide.CLIENT, identity.world(),
                identity.dimension(), identity.managerPosition(), identity.runtimeRevision(), Set.of(EXECUTE));
        assertNotSame(withSignature, cache.resolve(manager, differentIdentity, signed));
        cache.remove(manager);
        var afterRemoval = cache.resolve(manager, identity, signed);
        assertNotSame(withSignature, afterRemoval);
        assertNotSame(afterRemoval, cache.resolve(new Object(), identity, signed));
        cache.clear();
        assertNotSame(afterRemoval, cache.resolve(manager, identity, signed));
    }
}
