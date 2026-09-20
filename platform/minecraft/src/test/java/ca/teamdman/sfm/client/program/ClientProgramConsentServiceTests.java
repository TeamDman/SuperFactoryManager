package ca.teamdman.sfm.client.program;

import ca.teamdman.sfml.ast.ProgramExecutionSide;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static ca.teamdman.sfm.client.program.ClientProgramConsentGate.*;
import static org.junit.jupiter.api.Assertions.*;

class ClientProgramConsentServiceTests {
    @TempDir Path directory;
    private final AtomicLong now = new AtomicLong(1000);
    private ClientProgramIdentity identity(String source) {
        return ClientProgramIdentity.fromStoredSource(source, ProgramExecutionSide.CLIENT,
                ClientProgramWorldIdentity.integrated(new UUID(1, 2)), new ResourceLocation("minecraft:overworld"),
                new BlockPos(1, 64, 1), ClientProgramIdentity.CLIENT_MANAGER_RUNTIME, Set.of(EXECUTE, RENDER));
    }

    @Test
    void passiveObservationAndConcurrentFixtureApprovalNeverBecomeDurable() {
        Path file = directory.resolve("consents.bin");
        var service = new ClientProgramConsentService(file, now::get);
        var real = identity("real");
        var fixture = identity("fixture");
        assertTrue(service.observe(real, "real", "", Map.of()).successful());
        service.observe(fixture, "fixture", "", Map.of());
        service.gate().request(fixture, EXECUTE);
        service.gate().decide(fixture, EXECUTE, Decision.APPROVE);
        assertFalse(Files.exists(file));
        assertTrue(service.request(real, EXECUTE).saved());
        assertTrue(service.decide(real, EXECUTE, Decision.APPROVE, 5000L, null).saved());
        var reloaded = new ClientProgramConsentService(file, now::get);
        assertEquals(1, reloaded.store().snapshots().size());
        assertEquals(ConsentState.APPROVED, reloaded.gate().state(real, EXECUTE));
        assertEquals(ConsentState.ABSENT, reloaded.gate().state(fixture, EXECUTE));
        service.store().forget(fixture);
    }

    @Test
    void denialIsIdempotentAndOnlyExplicitReopenCanReviewAgain() {
        var service = new ClientProgramConsentService(directory.resolve("consents.bin"), now::get);
        var id = identity("source");
        service.observe(id, "source", "", Map.of());
        service.request(id, EXECUTE);
        assertTrue(service.decide(id, EXECUTE, Decision.DENY, null, null).saved());
        int history = service.store().snapshots().get(0).history().size();
        assertFalse(service.request(id, EXECUTE).changed());
        assertEquals(history, service.store().snapshots().get(0).history().size());
        assertEquals(ConsentState.DENIED, service.gate().state(id, EXECUTE));
        assertTrue(service.reopen(id, EXECUTE).changed());
        assertEquals(ConsentState.PENDING, service.gate().state(id, EXECUTE));
    }

    @Test
    void unavailableStorageStopsExecutionAndRetryPersistsStopWithoutRestoringGrant() throws Exception {
        Path badParent = directory.resolve("parent");
        Files.writeString(badParent, "a file is not a directory");
        var service = new ClientProgramConsentService(badParent.resolve("consents.bin"), now::get);
        var id = identity("source");
        service.observe(id, "source", "", Map.of());
        assertEquals(ClientProgramConsentService.Status.PERSISTENCE_FAILED, service.request(id, EXECUTE).status());
        assertTrue(service.store().stoppedAll());
        assertFalse(service.gate().execution(id, (program, cap) -> java.util.List.of()).allowed());
        assertTrue(service.diagnostic().contains("Restart may restore"));
        Files.delete(badParent);
        assertTrue(service.retrySave().saved());
        assertTrue(new ClientProgramConsentService(badParent.resolve("consents.bin"), now::get).store().stoppedAll());
        assertTrue(service.resume().saved());
        assertEquals(ConsentState.ABSENT, service.gate().state(id, EXECUTE));
    }

    @Test
    void invalidAndOverCapacityObservationIsTypedRatherThanThrownIntoRenderer() {
        var service = new ClientProgramConsentService(directory.resolve("consents.bin"), now::get);
        assertEquals(ClientProgramConsentService.Status.INVALID_EVIDENCE,
                service.observe(identity("real"), "wrong", "", Map.of()).status());
        for (int i = 0; i < ClientProgramConsentStore.MAX_PROGRAMS; i++) {
            String source = "source" + i;
            assertTrue(service.observe(identity(source), source, "", Map.of()).successful());
        }
        assertEquals(ClientProgramConsentService.Status.STORE_FULL,
                service.observe(identity("extra"), "extra", "", Map.of()).status());
        assertFalse(Files.exists(directory.resolve("consents.bin")));
    }

    @Test
    void onlyKnownAbsenceIsCleanWhileAnUnreadableStoreRequiresExplicitRecovery() throws Exception {
        var fresh = new ClientProgramConsentService(directory.resolve("absent.bin"), now::get);
        assertTrue(fresh.diagnostic().isEmpty());
        assertFalse(fresh.store().stoppedAll());
        Path notAFile = Files.createDirectory(directory.resolve("consent-directory"));
        var unavailable = new ClientProgramConsentService(notAFile, now::get);
        assertFalse(unavailable.diagnostic().isEmpty());
        assertTrue(unavailable.store().stoppedAll());
        assertFalse(unavailable.resume().successful(), "An unwritable consent destination cannot clear the recovery stop");
        assertTrue(unavailable.store().stoppedAll());
    }
}
