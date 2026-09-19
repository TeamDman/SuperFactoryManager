package ca.teamdman.sfm.client.program;

import ca.teamdman.sfml.ast.ProgramExecutionSide;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static ca.teamdman.sfm.client.program.ClientProgramConsentGate.*;
import static org.junit.jupiter.api.Assertions.*;

class ClientProgramConsentStoreTests {
    @TempDir Path directory;
    private static final UUID WORLD = UUID.fromString("48acbd83-826f-4470-878c-16bfce036c79");
    private static final String SOURCE = "CLIENT BTW\nNAME \"red\"";
    private static final String BINDINGS = "display=12,64,7";
    private final AtomicLong clock = new AtomicLong(1000);

    private ClientProgramIdentity identity(String source, String bindings) {
        return ClientProgramIdentity.fromStoredSourceAndBindings(source, bindings, ProgramExecutionSide.CLIENT,
                ClientProgramWorldIdentity.integrated(WORLD), new ResourceLocation("minecraft:overworld"),
                new BlockPos(1, 64, 1), ClientProgramIdentity.CLIENT_MANAGER_RUNTIME, Set.of(EXECUTE, RENDER));
    }

    private ClientProgramConsentStore observed(ClientProgramIdentity identity, String source, String bindings) {
        ClientProgramConsentStore store = new ClientProgramConsentStore(clock::get);
        store.observe(identity, source, bindings, Map.of("minecraft", "1.19.2", "sfm", "test-build"));
        return store;
    }

    @Test
    void restartPreservesExactSourceBindingsDecisionAndHistory() throws IOException {
        ClientProgramIdentity program = identity(SOURCE, BINDINGS);
        ClientProgramConsentStore store = observed(program, SOURCE, BINDINGS);
        store.request(program, EXECUTE);
        store.decide(program, EXECUTE, Decision.APPROVE, 5000L, null);
        store.request(program, RENDER);
        store.decide(program, RENDER, Decision.DENY, null, null);
        Path path = directory.resolve("consents.bin");
        store.save(path);

        var loaded = ClientProgramConsentStore.load(path, clock::get);
        assertTrue(loaded.diagnostics().isEmpty());
        assertEquals(store.snapshots(), loaded.store().snapshots());
        assertEquals(ConsentState.APPROVED, loaded.store().state(program, EXECUTE));
        assertEquals(ConsentState.DENIED, loaded.store().state(program, RENDER));
        assertFalse(loaded.store().request(program, RENDER).created());
        assertEquals(ConsentState.ABSENT, loaded.store().state(identity(SOURCE + "\n", BINDINGS), EXECUTE));
        assertEquals(ConsentState.ABSENT, loaded.store().state(identity(SOURCE, "display=13,64,7"), EXECUTE));
    }

    @Test
    void expiryAndDenialCooldownAreCheckedAtEveryEvaluation() throws IOException {
        var program = identity(SOURCE, BINDINGS);
        var store = observed(program, SOURCE, BINDINGS);
        store.request(program, EXECUTE);
        store.decide(program, EXECUTE, Decision.APPROVE, 2000L, null);
        store.request(program, RENDER);
        store.decide(program, RENDER, Decision.DENY, null, 3000L);
        Path path = directory.resolve("consents.bin");
        store.save(path);
        var gate = new ClientProgramConsentGate(ClientProgramConsentStore.load(path, clock::get).store());
        assertTrue(gate.execution(program, (id, cap) -> List.of()).allowed());
        clock.set(2000);
        assertFalse(gate.execution(program, (id, cap) -> List.of()).allowed());
        assertEquals(ConsentState.DENIED, gate.request(program, RENDER).state());
        clock.set(3000);
        assertTrue(gate.request(program, RENDER).created());
        assertEquals(ConsentState.PENDING, gate.state(program, RENDER));
    }

    @Test
    void sourceHistoryAndVersionsCanBeReviewedWithoutRegrantingChangedSource() {
        var original = identity(SOURCE, BINDINGS);
        var store = observed(original, SOURCE, BINDINGS);
        store.request(original, EXECUTE);
        store.decide(original, EXECUTE, Decision.APPROVE, null, null);
        String changedSource = SOURCE.replace("red", "blue");
        var changed = identity(changedSource, BINDINGS);
        store.observe(changed, changedSource, BINDINGS, Map.of("sfm", "new-build"));
        assertEquals(2, store.atLocation(changed).size());
        assertEquals(SOURCE, store.atLocation(changed).get(0).evidence().orElseThrow().source());
        assertEquals(ConsentState.ABSENT, store.state(changed, EXECUTE));
        store.observe(original, SOURCE, BINDINGS, Map.of("sfm", "new-build"));
        assertEquals(ConsentState.APPROVED, store.state(original, EXECUTE), "Version context alone does not change runtime semantics");
        assertEquals("test-build", store.atLocation(original).get(0).history().get(1).versionContext().get("sfm"));
        assertThrows(IllegalArgumentException.class,
                () -> store.observe(original, changedSource, BINDINGS, Map.of()));
    }

    @Test
    void revocationSurvivesRestartAndCorruptionCannotResurrectAnApproval() throws IOException {
        var program = identity(SOURCE, BINDINGS);
        var store = observed(program, SOURCE, BINDINGS);
        store.request(program, EXECUTE);
        store.decide(program, EXECUTE, Decision.APPROVE, null, null);
        Path path = directory.resolve("consents.bin");
        store.save(path);
        store.revokeAll();
        store.save(path);
        assertEquals(ConsentState.ABSENT, ClientProgramConsentStore.load(path, clock::get).store().state(program, EXECUTE));
        byte[] bytes = Files.readAllBytes(path);
        Files.write(path, java.util.Arrays.copyOf(bytes, bytes.length - 1));
        var corrupt = ClientProgramConsentStore.load(path, clock::get);
        assertFalse(corrupt.diagnostics().isEmpty());
        assertTrue(corrupt.store().snapshots().isEmpty());
        assertEquals(ConsentState.ABSENT, corrupt.store().state(program, EXECUTE));
    }

    @Test
    void tamperedSourceAndTrailingDataFailClosed() throws IOException {
        var program = identity(SOURCE, BINDINGS);
        var store = observed(program, SOURCE, BINDINGS);
        store.request(program, EXECUTE);
        store.decide(program, EXECUTE, Decision.APPROVE, null, null);
        Path path = directory.resolve("consents.bin");
        store.save(path);
        byte[] bytes = Files.readAllBytes(path);
        bytes[16] ^= 1; // First source byte follows magic, version, record count and length.
        Files.write(path, bytes);
        assertTrue(ClientProgramConsentStore.load(path, clock::get).store().snapshots().isEmpty());
        store.save(path);
        Files.write(path, new byte[]{0}, java.nio.file.StandardOpenOption.APPEND);
        assertFalse(ClientProgramConsentStore.load(path, clock::get).diagnostics().isEmpty());
    }

    @Test
    void policyRemainsIndependentAndStopAllRevokesEveryCapability() {
        var program = identity(SOURCE, BINDINGS);
        var store = observed(program, SOURCE, BINDINGS);
        var gate = new ClientProgramConsentGate(store);
        for (var capability : program.requestedCapabilities()) {
            gate.request(program, capability);
            gate.decide(program, capability, Decision.APPROVE);
        }
        assertEquals(ConsentState.APPROVED, gate.execution(program, (id, cap) -> List.of("disabled_by_policy")).consent());
        assertEquals(EffectiveState.BLOCKED_BY_POLICY, gate.execution(program, (id, cap) -> List.of("disabled_by_policy")).effective());
        store.revokeAll();
        assertFalse(gate.execution(program, (id, cap) -> List.of()).allowed());
        assertEquals(ConsentState.ABSENT, gate.state(program, RENDER));
    }

    @Test
    void storageAndHistoryBudgetsDoNotSilentlyEvictOldDecisions() throws IOException {
        var program = identity(SOURCE, BINDINGS);
        var store = observed(program, SOURCE, BINDINGS);
        for (int i = 0; i < 40; i++) {
            store.request(program, EXECUTE);
            store.decide(program, EXECUTE, Decision.DENY, null, null);
            store.reopenDenied(program, EXECUTE);
            store.revoke(program, EXECUTE);
        }
        assertEquals(ClientProgramConsentStore.MAX_HISTORY, store.snapshots().get(0).history().size());
        for (int i = 1; i < ClientProgramConsentStore.MAX_PROGRAMS; i++) {
            String source = SOURCE + i;
            store.observe(identity(source, BINDINGS), source, BINDINGS, Map.of());
        }
        assertThrows(IllegalStateException.class, () -> store.observe(identity("overflow", BINDINGS), "overflow", BINDINGS, Map.of()));
        assertTrue(store.forget(program));
        store.observe(identity("overflow", BINDINGS), "overflow", BINDINGS, Map.of());
        assertEquals(ConsentState.ABSENT, store.state(program, EXECUTE));
        assertThrows(IllegalArgumentException.class, () -> new ClientProgramConsentStore.Evidence(
                "é".repeat(ClientProgramConsentStore.MAX_SOURCE_BYTES), "", Map.of(), 1));
        var unevidenced = new ClientProgramConsentStore(clock::get);
        unevidenced.request(program, EXECUTE);
        unevidenced.decide(program, EXECUTE, Decision.APPROVE, null, null);
        assertThrows(IOException.class, () -> unevidenced.save(directory.resolve("unverified.bin")));
    }
}
