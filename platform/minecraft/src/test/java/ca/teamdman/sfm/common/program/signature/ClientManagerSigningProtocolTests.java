package ca.teamdman.sfm.common.program.signature;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.security.KeyPairGenerator;
import java.util.*;

import static ca.teamdman.sfm.common.program.signature.ClientManagerSigningState.Status.*;
import static org.junit.jupiter.api.Assertions.*;

class ClientManagerSigningProtocolTests {
    private static final List<ResourceLocation> CAPS = List.of(new ResourceLocation("sfm:client_program/execute"));
    private static final UUID PLAYER = new UUID(1, 1);
    private static ClientManagerSigningBody body(String source) {
        return new ClientManagerSigningBody(source, Map.of("displays", List.of(new BlockPos(1, 64, 2).asLong())));
    }
    private static ClientManagerSigningState state() {
        return new ClientManagerSigningState(UUID.randomUUID(), body("CLIENT BTW\n"));
    }
    private static ProgramAttestation sign(ProgramSignatureDescriptor descriptor) throws Exception {
        return ProgramAttestation.sign(descriptor, KeyPairGenerator.getInstance("Ed25519").generateKeyPair());
    }
    private static ClientManagerSigningAcknowledgement review(ClientManagerSigningState state,
                                                               ClientManagerSigningSession session, UUID player) {
        return state.review(session, player, CAPS, 10).acknowledgement().orElseThrow();
    }
    private static ClientManagerSigningState.Status submit(ClientManagerSigningState state,
                                                           ClientManagerSigningSession session,
                                                           ClientManagerSigningAcknowledgement ack,
                                                           ProgramAttestation signed) {
        return state.submit(session, PLAYER, ack.snapshot().incarnation(), ack.snapshot().revision(),
                ack.challenge(), signed, 11);
    }

    @Test
    void acknowledgementContainsExactLfSourceAndEveryImmutableLabelBinding() {
        var values = new ArrayList<>(List.of(2L, 1L, 1L));
        var labels = new HashMap<String, List<Long>>();
        labels.put("unused", values);
        var body = new ClientManagerSigningBody("CLIENT BTW\r\nNAME \"a\rb\"", labels);
        values.clear();
        labels.clear();
        assertEquals("CLIENT BTW\nNAME \"a\nb\"", body.source());
        assertEquals(List.of(1L, 2L), body.labels().get("unused"));
        assertEquals(body, ClientManagerSigningBody.fromDiskTag(body.toDiskProjection()));
        assertThrows(UnsupportedOperationException.class, () -> body.labels().clear());
        assertThrows(UnsupportedOperationException.class, () -> body.labels().get("unused").clear());
        assertThrows(IllegalArgumentException.class, () -> body("\ud800"));
        assertThrows(IllegalArgumentException.class,
                () -> new ClientManagerSigningBody("", Map.of("x", Collections.nCopies(65, 1L))));
    }

    @Test
    void sourceSaveIsCasAndReturnsServerRevisionBeforeIndependentSignatureSubmission() throws Exception {
        var state = state();
        var session = new ClientManagerSigningSession();
        var initial = review(state, session, PLAYER);
        var saved = state.save(session, PLAYER, initial.snapshot().incarnation(), initial.snapshot().revision(),
                "CLIENT BTW\r\nNAME \"new\"\r\n", CAPS, 11);
        assertEquals(SAVED, saved.status());
        var acknowledged = saved.acknowledgement().orElseThrow();
        assertEquals("CLIENT BTW\nNAME \"new\"\n", acknowledged.snapshot().body().source());
        assertEquals(initial.snapshot().body().labels(), acknowledged.snapshot().body().labels());
        assertEquals(initial.snapshot().revision() + 1, acknowledged.snapshot().revision());
        assertEquals(acknowledged.snapshot().body().sourceSha256(), acknowledged.descriptor().sourceSha256());
        assertEquals(STALE_REVISION, state.save(session, PLAYER, initial.snapshot().incarnation(),
                initial.snapshot().revision(), "stale overwrite", CAPS, 12).status());
        var signed = sign(acknowledged.descriptor());
        assertEquals(SIGNED, submit(state, session, acknowledged, signed));
        assertEquals(acknowledged.snapshot().revision(), state.snapshot().revision(), "Appending authorship does not edit content");
        assertEquals(CHALLENGE_UNAVAILABLE, submit(state, session, acknowledged, signed));
    }

    @Test
    void wrongActorReplacementLabelChangeAndExpiryCannotReuseReviewedChallenge() throws Exception {
        var state = state();
        var session = new ClientManagerSigningSession();
        var ack = review(state, session, PLAYER);
        var signature = sign(ack.descriptor());
        assertEquals(CHALLENGE_UNAVAILABLE, state.submit(session, new UUID(2, 2), ack.snapshot().incarnation(),
                ack.snapshot().revision(), ack.challenge(), signature, 11));
        assertEquals(SIGNED, submit(state, session, ack, signature), "Another player cannot consume our challenge");
        ack = review(state, session, PLAYER);
        var replacement = new ClientManagerSigningState(UUID.randomUUID(), ack.snapshot().body());
        assertEquals(STALE_REVISION, submit(replacement, session, ack, signature));
        ack = review(state, session, PLAYER);
        state.replaceBody(new ClientManagerSigningBody(state.snapshot().body().source(), Map.of("displays", List.of(99L))));
        assertEquals(STALE_REVISION, submit(state, session, ack, signature));
        ack = review(state, session, PLAYER);
        assertEquals(CHALLENGE_UNAVAILABLE, state.submit(session, PLAYER, ack.snapshot().incarnation(),
                ack.snapshot().revision(), ack.challenge(), signature, ack.expiresAtTick()));
    }

    @Test
    void independentSignersSurviveAppendAndHistoricalEvidenceSurvivesBodyEdits() throws Exception {
        var state = state();
        var session = new ClientManagerSigningSession();
        var original = state.snapshot().body();
        var aliceAck = review(state, session, PLAYER);
        var alice = sign(aliceAck.descriptor());
        assertEquals(SIGNED, submit(state, session, aliceAck, alice));
        var bobAck = review(state, session, PLAYER);
        assertEquals(SIGNED, submit(state, session, bobAck, sign(bobAck.descriptor())));
        assertEquals(2, state.snapshot().history().size());
        var duplicate = review(state, session, PLAYER);
        assertEquals(ALREADY_SIGNED, submit(state, session, duplicate, alice));
        state.replaceBody(body("CLIENT BTW\n-- changed"));
        assertEquals(2, state.snapshot().history().size());
        assertTrue(state.snapshot().history().stream().noneMatch(entry -> entry.verifies(state.snapshot().descriptor(CAPS))));
        state.replaceBody(original);
        assertTrue(state.snapshot().history().stream().allMatch(entry -> entry.verifies(state.snapshot().descriptor(CAPS))));
    }

    @Test
    void declaredManifestIsAcknowledgedAuthorshipDataNotServerGrantedCapabilityAuthority() throws Exception {
        var state = new ClientManagerSigningState(UUID.randomUUID(), body("CLIENT BTW\n-- asks for rendering later"));
        var session = new ClientManagerSigningSession();
        var ack = review(state, session, PLAYER);
        assertEquals(CAPS, ack.descriptor().capabilities());
        var wider = List.of(CAPS.get(0), new ResourceLocation("sfm:touch_display/render"));
        assertEquals(INVALID_SIGNATURE, submit(state, session, ack, sign(state.snapshot().descriptor(wider))));
        assertTrue(state.snapshot().history().isEmpty());
        // The server deliberately does not parse client actions. The client must compile acknowledged
        // source and compare the exact live manifest before offering Sign or using signer trust.
        assertEquals(CHALLENGE_UNAVAILABLE, submit(state, session, ack, sign(ack.descriptor())));
    }

    @Test
    void sessionLimitsReplaceRepeatedReviewsAndExpireAcrossAllManagers() {
        var session = new ClientManagerSigningSession();
        var manager = state();
        var first = review(manager, session, PLAYER);
        var second = review(manager, session, PLAYER);
        assertEquals(1, session.pendingCount());
        assertNotEquals(first.challenge(), second.challenge());
        for (int i = 1; i < ClientManagerSigningSession.MAX_PENDING_PER_PLAYER; i++) review(state(), session, PLAYER);
        assertEquals(CHALLENGE_UNAVAILABLE, state().review(session, PLAYER, CAPS, 10).status());
        session.forgetPlayer(PLAYER);
        assertEquals(0, session.pendingCount());
        for (int i = 0; i < ClientManagerSigningSession.MAX_PENDING_PER_MANAGER; i++) {
            review(manager, session, new UUID(3, i));
        }
        assertEquals(CHALLENGE_UNAVAILABLE, manager.review(session, new UUID(4, 0), CAPS, 10).status());
        session.clear();
        for (int i = 0; i < ClientManagerSigningSession.MAX_PENDING; i++) review(state(), session, new UUID(5, i));
        assertEquals(ClientManagerSigningSession.MAX_PENDING, session.pendingCount());
        assertEquals(CHALLENGE_UNAVAILABLE, state().review(session, new UUID(6, 0), CAPS, 10).status());
        assertEquals(REVIEWED, state().review(session, new UUID(7, 0), CAPS,
                10 + ClientManagerSigningSession.CHALLENGE_TTL_TICKS).status());
        assertEquals(1, session.pendingCount());
    }

    @Test
    void sessionByteBudgetPreventsLargeSnapshotsAndFailedSaveDoesNotMutateSource() {
        var session = new ClientManagerSigningSession();
        var large = body("\u20ac".repeat(20_000));
        int accepted = 0;
        while (accepted < ClientManagerSigningSession.MAX_PENDING) {
            var state = new ClientManagerSigningState(UUID.randomUUID(), large);
            if (state.review(session, new UUID(8, accepted), CAPS, 10).status() != REVIEWED) break;
            accepted++;
        }
        assertTrue(accepted > 0 && accepted < ClientManagerSigningSession.MAX_PENDING);
        var last = state();
        var before = last.snapshot();
        assertEquals(CHALLENGE_UNAVAILABLE, last.save(session, new UUID(9, 0), before.incarnation(), before.revision(),
                large.source(), CAPS, 10).status());
        assertEquals(before, last.snapshot());
    }

    @Test
    void persistentMetadataRoundTripsWithoutChallengesAndChangedDiskAdvancesRevision() throws Exception {
        var state = state();
        var session = new ClientManagerSigningSession();
        var ack = review(state, session, PLAYER);
        var signature = sign(ack.descriptor());
        assertEquals(SIGNED, submit(state, session, ack, signature));
        var metadata = ClientManagerSigningMetadata.from(state.snapshot());
        var decoded = ClientManagerSigningCodec.decodeMetadata(ClientManagerSigningCodec.encodeMetadata(metadata));
        assertEquals(metadata, decoded);
        var restored = decoded.restoreServerState(state.snapshot().body());
        assertEquals(state.snapshot(), restored.snapshot());
        assertEquals(CHALLENGE_UNAVAILABLE, submit(restored, new ClientManagerSigningSession(), ack, signature));
        var changed = decoded.restoreServerState(body("CLIENT BTW\n-- edited on disk"));
        assertEquals(metadata.revision() + 1, changed.snapshot().revision());
        assertEquals(state.snapshot().history(), changed.snapshot().history());
        assertFalse(metadata.matchesBody(changed.snapshot().body()));
    }

    @Test
    void acknowledgementAndHistoryCodecsRejectWholeMalformedOrForgedPayloads() throws Exception {
        var state = state();
        var session = new ClientManagerSigningSession();
        var ack = review(state, session, PLAYER);
        var signature = sign(ack.descriptor());
        assertEquals(SIGNED, submit(state, session, ack, signature));
        ack = review(state, session, PLAYER);
        byte[] encoded = ClientManagerSigningCodec.encodeAcknowledgement(ack);
        assertEquals(ack, ClientManagerSigningCodec.decodeAcknowledgement(encoded));
        assertThrows(IllegalArgumentException.class,
                () -> ClientManagerSigningCodec.decodeAcknowledgement(Arrays.copyOf(encoded, encoded.length + 1)));
        byte[] wrongVersion = encoded.clone();
        ByteBuffer.wrap(wrongVersion).putInt(2);
        assertThrows(IllegalArgumentException.class, () -> ClientManagerSigningCodec.decodeAcknowledgement(wrongVersion));
        assertThrows(IllegalArgumentException.class,
                () -> ClientManagerSigningCodec.decodeAcknowledgement(new byte[ClientManagerSigningCodec.MAX_ACKNOWLEDGEMENT_BYTES + 1]));
        byte[] history = ProgramAttestationCodec.encodeHistory(state.snapshot().history());
        assertEquals(state.snapshot().history(), ProgramAttestationCodec.decodeHistory(history));
        ByteBuffer.wrap(history).putInt(4, Integer.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, () -> ProgramAttestationCodec.decodeHistory(history));
        byte[] forgedBytes = Base64.getDecoder().decode(signature.signature());
        forgedBytes[0] ^= 1;
        var forged = new ProgramAttestation(signature.descriptor(), signature.publicKey(), Base64.getEncoder().encodeToString(forgedBytes));
        assertThrows(IllegalArgumentException.class,
                () -> ProgramAttestationCodec.decodeHistory(ProgramAttestationCodec.encodeHistory(List.of(signature, forged))));
        assertThrows(IllegalArgumentException.class,
                () -> ProgramAttestationCodec.encodeHistory(Collections.nCopies(33, signature)));
    }

    @Test
    void malformedSignatureConsumesChallengeAndCopiedIncarnationCannotCrossManagerAddress() throws Exception {
        var state = state();
        var session = new ClientManagerSigningSession();
        String first = "minecraft:overworld/1";
        String second = "minecraft:overworld/2";
        var ack = state.review(session, PLAYER, CAPS, 10, first).acknowledgement().orElseThrow();
        byte[] signed = ProgramAttestationCodec.encode(sign(ack.descriptor()));
        assertEquals(CHALLENGE_UNAVAILABLE, state.submitEncoded(session, PLAYER, ack.snapshot().incarnation(),
                ack.snapshot().revision(), ack.challenge(), signed, 11, second));
        assertEquals(CHALLENGE_UNAVAILABLE, state.submitEncoded(session, PLAYER, ack.snapshot().incarnation(),
                ack.snapshot().revision(), ack.challenge(), signed, 11, first));
        ack = state.review(session, PLAYER, CAPS, 12, first).acknowledgement().orElseThrow();
        assertEquals(INVALID_SIGNATURE, state.submitEncoded(session, PLAYER, ack.snapshot().incarnation(),
                ack.snapshot().revision(), ack.challenge(), new byte[]{0}, 13, first));
        assertEquals(CHALLENGE_UNAVAILABLE, state.submitEncoded(session, PLAYER, ack.snapshot().incarnation(),
                ack.snapshot().revision(), ack.challenge(), signed, 13, first));
        assertTrue(state.snapshot().history().isEmpty());
    }

    @Test
    void staleRevisionRejectsMalformedBytesBeforeParsingThem() {
        var state = state();
        var session = new ClientManagerSigningSession();
        var ack = review(state, session, PLAYER);
        state.replaceBody(body("CLIENT BTW\n-- later"));
        assertEquals(STALE_REVISION, state.submitEncoded(session, PLAYER, ack.snapshot().incarnation(),
                ack.snapshot().revision(), ack.challenge(), new byte[]{0}, 11, ""));
        assertEquals(0, session.pendingCount());
    }

    @Test
    void immutableSnapshotCacheChangesOnlyAfterCommittedBodyOrHistoryChanges() throws Exception {
        var state = state();
        var session = new ClientManagerSigningSession();
        var first = state.snapshot();
        assertSame(first, state.snapshot());
        var ack = review(state, session, PLAYER);
        assertSame(first, ack.snapshot());
        var signature = sign(ack.descriptor());
        assertEquals(SIGNED, submit(state, session, ack, signature));
        var signed = state.snapshot();
        assertNotSame(first, signed);
        assertEquals(first.body(), signed.body());
        assertTrue(first.history().isEmpty(), "Previously issued snapshots must remain immutable");
        assertEquals(List.of(signature), signed.history());
        assertSame(signed, state.snapshot());
        ack = review(state, session, PLAYER);
        assertEquals(ALREADY_SIGNED, submit(state, session, ack, signature));
        assertSame(signed, state.snapshot(), "Duplicate metadata must not invalidate the snapshot");
        assertEquals(CHALLENGE_UNAVAILABLE, submit(state, session, ack, signature));
        assertSame(signed, state.snapshot(), "Rejected requests must not invalidate the snapshot");
        state.replaceBody(body("CLIENT BTW\n-- next"));
        var changed = state.snapshot();
        assertNotSame(signed, changed);
        assertEquals(signed.revision() + 1, changed.revision());
        assertEquals(signed.history(), changed.history());
        assertEquals(first.body(), signed.body(), "An old signed snapshot changed after a body edit");
        assertSame(changed, state.snapshot());
    }
}
