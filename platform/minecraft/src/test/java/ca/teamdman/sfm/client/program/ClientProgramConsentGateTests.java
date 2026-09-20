package ca.teamdman.sfm.client.program;

import ca.teamdman.sfml.ast.ProgramExecutionSide;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static ca.teamdman.sfm.client.program.ClientProgramConsentGate.ConsentState.ABSENT;
import static ca.teamdman.sfm.client.program.ClientProgramConsentGate.ConsentState.APPROVED;
import static ca.teamdman.sfm.client.program.ClientProgramConsentGate.ConsentState.DENIED;
import static ca.teamdman.sfm.client.program.ClientProgramConsentGate.ConsentState.PENDING;
import static ca.teamdman.sfm.client.program.ClientProgramConsentGate.Decision.APPROVE;
import static ca.teamdman.sfm.client.program.ClientProgramConsentGate.Decision.DENY;
import static ca.teamdman.sfm.client.program.ClientProgramConsentGate.EffectiveState.ALLOWED;
import static ca.teamdman.sfm.client.program.ClientProgramConsentGate.EffectiveState.BLOCKED_BY_POLICY;
import static ca.teamdman.sfm.client.program.ClientProgramConsentGate.EffectiveState.DENIED_BY_USER;
import static ca.teamdman.sfm.client.program.ClientProgramConsentGate.EffectiveState.AWAITING_CONSENT;
import static ca.teamdman.sfm.client.program.ClientProgramConsentGate.EXECUTE;
import static ca.teamdman.sfm.client.program.ClientProgramConsentGate.RENDER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientProgramConsentGateTests {
    private static final UUID WORLD_ID = UUID.fromString("c94730a6-f3e3-44f2-b97e-c302303eeb49");
    private static final ResourceLocation OVERWORLD = new ResourceLocation("minecraft", "overworld");
    private static final ResourceLocation NETHER = new ResourceLocation("minecraft", "the_nether");
    private static final BlockPos POSITION = new BlockPos(12, 64, -7);
    private static final ClientProgramConsentGate.Policy ALLOW_ALL = (identity, capability) -> List.of();

    private static ClientProgramIdentity identity(String source) {
        return identity(source, ProgramExecutionSide.CLIENT, WORLD_ID, OVERWORLD, POSITION,
                ClientProgramIdentity.CLIENT_MANAGER_RUNTIME, Set.of(EXECUTE, RENDER));
    }

    private static ClientProgramIdentity identity(
            String source,
            ProgramExecutionSide side,
            UUID worldId,
            ResourceLocation dimension,
            BlockPos position,
            String runtime,
            Set<ResourceLocation> capabilities
    ) {
        return ClientProgramIdentity.fromStoredSource(
                source, side, ClientProgramWorldIdentity.integrated(worldId), dimension,
                position, runtime, capabilities
        );
    }

    @Test
    void unapprovedProgramCannotTickAndRenderRequiresItsOwnGrant() {
        ClientProgramConsentGate gate = new ClientProgramConsentGate();
        ClientProgramIdentity program = identity("CLIENT BTW\nEVERY FRAME DO END");

        assertEquals(ABSENT, gate.execution(program, ALLOW_ALL).consent());
        assertEquals(AWAITING_CONSENT, gate.execution(program, ALLOW_ALL).effective());
        assertFalse(gate.execution(program, ALLOW_ALL).allowed());
        assertEquals(new ClientProgramConsentGate.RequestResult(PENDING, true), gate.request(program, EXECUTE));
        assertFalse(gate.execution(program, ALLOW_ALL).allowed());
        gate.decide(program, EXECUTE, APPROVE);
        assertTrue(gate.execution(program, ALLOW_ALL).allowed());
        assertEquals(ALLOWED, gate.execution(program, ALLOW_ALL).effective());
        assertFalse(gate.evaluate(program, RENDER, ALLOW_ALL).allowed());

        gate.request(program, RENDER);
        gate.decide(program, RENDER, APPROVE);
        assertTrue(gate.evaluate(program, RENDER, ALLOW_ALL).allowed());
        gate.revoke(program, EXECUTE);
        assertFalse(gate.execution(program, ALLOW_ALL).allowed());
        assertTrue(gate.evaluate(program, RENDER, ALLOW_ALL).allowed());
    }

    @Test
    void exactSourceScopeAndManifestDetermineIdentity() {
        ClientProgramConsentGate gate = new ClientProgramConsentGate();
        ClientProgramIdentity original = identity("CLIENT BTW\nNAME \"red\"");
        gate.request(original, EXECUTE);
        gate.decide(original, EXECUTE, APPROVE);
        assertEquals(APPROVED, gate.execution(original, ALLOW_ALL).consent());

        List<ClientProgramIdentity> changed = List.of(
                identity("CLIENT BTW\nNAME \"blue\""),
                identity("CLIENT BTW\r\nNAME \"red\""),
                identity("CLIENT BTW\nNAME \"red\"", ProgramExecutionSide.SERVER,
                        WORLD_ID, OVERWORLD, POSITION, ClientProgramIdentity.CLIENT_MANAGER_RUNTIME,
                        Set.of(EXECUTE, RENDER)),
                identity("CLIENT BTW\nNAME \"red\"", ProgramExecutionSide.CLIENT,
                        UUID.randomUUID(), OVERWORLD, POSITION, ClientProgramIdentity.CLIENT_MANAGER_RUNTIME,
                        Set.of(EXECUTE, RENDER)),
                identity("CLIENT BTW\nNAME \"red\"", ProgramExecutionSide.CLIENT,
                        WORLD_ID, NETHER, POSITION, ClientProgramIdentity.CLIENT_MANAGER_RUNTIME,
                        Set.of(EXECUTE, RENDER)),
                identity("CLIENT BTW\nNAME \"red\"", ProgramExecutionSide.CLIENT,
                        WORLD_ID, OVERWORLD, POSITION.east(), ClientProgramIdentity.CLIENT_MANAGER_RUNTIME,
                        Set.of(EXECUTE, RENDER)),
                identity("CLIENT BTW\nNAME \"red\"", ProgramExecutionSide.CLIENT,
                        WORLD_ID, OVERWORLD, POSITION, "sfm:client_manager@2", Set.of(EXECUTE, RENDER)),
                identity("CLIENT BTW\nNAME \"red\"", ProgramExecutionSide.CLIENT,
                        WORLD_ID, OVERWORLD, POSITION, ClientProgramIdentity.CLIENT_MANAGER_RUNTIME,
                        Set.of(EXECUTE))
        );
        for (ClientProgramIdentity altered : changed) {
            assertNotEquals(original, altered);
            assertEquals(ABSENT, gate.state(altered, EXECUTE));
        }
        ClientProgramIdentity anotherServer = ClientProgramIdentity.fromStoredSource(
                "CLIENT BTW\nNAME \"red\"", ProgramExecutionSide.CLIENT,
                new ClientProgramWorldIdentity("EXAMPLE.ORG:25565", WORLD_ID), OVERWORLD, POSITION,
                ClientProgramIdentity.CLIENT_MANAGER_RUNTIME, Set.of(EXECUTE, RENDER)
        );
        assertEquals("example.org:25565", anotherServer.world().serverEndpoint());
        assertEquals(ABSENT, gate.state(anotherServer, EXECUTE));
    }

    @Test
    void requestsAreIdempotentAndDenialDoesNotAutomaticallyReopen() {
        ClientProgramConsentGate gate = new ClientProgramConsentGate();
        ClientProgramIdentity program = identity("CLIENT BTW\nNAME \"button\"");
        assertEquals(new ClientProgramConsentGate.RequestResult(PENDING, true), gate.request(program, EXECUTE));
        assertEquals(new ClientProgramConsentGate.RequestResult(PENDING, false), gate.request(program, EXECUTE));
        gate.decide(program, EXECUTE, DENY);
        assertEquals(new ClientProgramConsentGate.RequestResult(DENIED, false), gate.request(program, EXECUTE));
        assertEquals(DENIED_BY_USER, gate.execution(program, ALLOW_ALL).effective());
        assertThrows(IllegalStateException.class, () -> gate.decide(program, EXECUTE, APPROVE));
        assertEquals(new ClientProgramConsentGate.RequestResult(PENDING, true), gate.reopenDenied(program, EXECUTE));
        gate.decide(program, EXECUTE, APPROVE);
        assertEquals(new ClientProgramConsentGate.RequestResult(APPROVED, false), gate.request(program, EXECUTE));
    }

    @Test
    void policyBlockDoesNotTurnApprovalIntoDenial() {
        ClientProgramConsentGate gate = new ClientProgramConsentGate();
        ClientProgramIdentity program = identity("CLIENT BTW\nNAME \"button\"");
        gate.request(program, EXECUTE);
        gate.decide(program, EXECUTE, APPROVE);

        ClientProgramConsentGate.Evaluation blocked = gate.execution(
                program, (identity, capability) -> List.of("client_policy_disallows_program_execution")
        );
        assertEquals(APPROVED, blocked.consent());
        assertEquals(BLOCKED_BY_POLICY, blocked.effective());
        assertEquals(List.of("client_policy_disallows_program_execution"), blocked.policyBlockers());
        assertFalse(blocked.allowed());
        assertEquals(ALLOWED, gate.execution(program, ALLOW_ALL).effective());
    }

    @Test
    void undeclaredCapabilitiesAndServerExecutionFailClosed() {
        ClientProgramConsentGate gate = new ClientProgramConsentGate();
        ClientProgramIdentity program = identity("CLIENT BTW\nNAME \"button\"");
        ResourceLocation filesystem = new ResourceLocation("sfm", "filesystem/read");
        assertThrows(IllegalArgumentException.class, () -> gate.request(program, filesystem));
        assertThrows(IllegalArgumentException.class, () -> gate.evaluate(program, filesystem, ALLOW_ALL));

        ClientProgramIdentity server = identity("SERVER BTW\nNAME \"button\"", ProgramExecutionSide.SERVER,
                WORLD_ID, OVERWORLD, POSITION, ClientProgramIdentity.CLIENT_MANAGER_RUNTIME,
                Set.of(EXECUTE, RENDER));
        assertThrows(IllegalArgumentException.class, () -> gate.execution(server, ALLOW_ALL));
    }

    @Test
    void exactUtf8BytesProduceStableDigest() {
        ClientProgramIdentity program = identity("abc");
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                program.sourceSha256());
        assertNotEquals(program.sourceSha256(), identity("äbc").sourceSha256());
    }
}
