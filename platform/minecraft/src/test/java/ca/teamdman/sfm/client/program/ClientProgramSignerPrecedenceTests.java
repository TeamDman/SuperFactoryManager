package ca.teamdman.sfm.client.program;

import ca.teamdman.sfml.ast.ProgramExecutionSide;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static ca.teamdman.sfm.client.program.ClientProgramConsentGate.*;
import static org.junit.jupiter.api.Assertions.*;

class ClientProgramSignerPrecedenceTests {
    @TempDir Path directory;
    private final AtomicLong clock = new AtomicLong(1000);
    private final ClientProgramIdentity identity = ClientProgramIdentity.fromStoredSource("CLIENT BTW",
            ProgramExecutionSide.CLIENT, ClientProgramWorldIdentity.integrated(new UUID(0, 1)),
            new ResourceLocation("minecraft:overworld"), new BlockPos(1, 2, 3),
            ClientProgramIdentity.CLIENT_MANAGER_RUNTIME, Set.of(EXECUTE, RENDER));

    private ClientProgramConsentStore observed() {
        var store = new ClientProgramConsentStore(clock::get);
        store.observe(identity, "CLIENT BTW", "", Map.of());
        return store;
    }

    @Test void signerFallbackReportsItsAuthorityWithoutMintingExactConsent() {
        var store = observed();
        var gate = new ClientProgramConsentGate(store, (id, cap) -> cap.equals(EXECUTE));
        var evaluation = gate.execution(identity, (id, cap) -> List.of());
        assertTrue(evaluation.allowed());
        assertEquals(ConsentState.ABSENT, evaluation.consent());
        assertEquals(Authority.TRUSTED_SIGNER, evaluation.authority());
        assertFalse(store.hasRecordedDecision(identity, EXECUTE));
        assertFalse(gate.evaluate(identity, RENDER, (id, cap) -> List.of()).allowed());
    }

    @Test void policyAndStopAllPrecedeSignerLookupAndResumeCannotRestoreObservedGrants() throws Exception {
        var store = observed();
        var calls = new AtomicLong();
        var gate = new ClientProgramConsentGate(store, (id, cap) -> { calls.incrementAndGet(); return true; });
        assertEquals(EffectiveState.BLOCKED_BY_POLICY,
                gate.execution(identity, (id, cap) -> List.of("local_policy")).effective());
        store.setStoppedAll(true);
        assertFalse(gate.execution(identity, (id, cap) -> List.of()).allowed());
        assertEquals(0, calls.get());
        store.save(directory.resolve("stopped.bin"));
        var restored = ClientProgramConsentStore.load(directory.resolve("stopped.bin"), clock::get).store();
        restored.setStoppedAll(false);
        assertFalse(new ClientProgramConsentGate(restored, (id, cap) -> true)
                .execution(identity, (id, cap) -> List.of()).allowed());
    }

    @Test void denialReopeningAndRevocationNeverBorrowSignerAuthority() throws Exception {
        var store = observed();
        var gate = new ClientProgramConsentGate(store, (id, cap) -> true);
        gate.request(identity, EXECUTE);
        assertFalse(gate.execution(identity, (id, cap) -> List.of()).allowed());
        gate.decide(identity, EXECUTE, Decision.DENY);
        assertEquals(EffectiveState.DENIED_BY_USER, gate.execution(identity, (id, cap) -> List.of()).effective());
        gate.reopenDenied(identity, EXECUTE);
        assertFalse(gate.execution(identity, (id, cap) -> List.of()).allowed());
        gate.decide(identity, EXECUTE, Decision.APPROVE);
        assertEquals(Authority.EXACT_PROGRAM, gate.execution(identity, (id, cap) -> List.of()).authority());
        gate.revoke(identity, EXECUTE);
        store.save(directory.resolve("revoked.bin"));
        var restored = ClientProgramConsentStore.load(directory.resolve("revoked.bin"), clock::get).store();
        assertFalse(new ClientProgramConsentGate(restored, (id, cap) -> true)
                .execution(identity, (id, cap) -> List.of()).allowed());
    }

    @Test void expiredApprovalAndDenialCooldownDoNotRevealAHiddenSignerGrant() {
        var store = observed();
        var gate = new ClientProgramConsentGate(store, (id, cap) -> true);
        store.request(identity, EXECUTE);
        store.decide(identity, EXECUTE, Decision.APPROVE, 2000L, null);
        store.request(identity, RENDER);
        store.decide(identity, RENDER, Decision.DENY, null, 2000L);
        clock.set(2000);
        assertFalse(gate.execution(identity, (id, cap) -> List.of()).allowed());
        assertFalse(gate.evaluate(identity, RENDER, (id, cap) -> List.of()).allowed());
    }

    @Test void failedSignerResolutionFailsClosed() {
        var gate = new ClientProgramConsentGate(observed(), (id, cap) -> { throw new IllegalStateException("unavailable"); });
        var evaluation = gate.execution(identity, (id, cap) -> List.of());
        assertEquals(EffectiveState.BLOCKED_BY_POLICY, evaluation.effective());
        assertEquals(List.of("signer_authority_unavailable"), evaluation.policyBlockers());
    }
}
