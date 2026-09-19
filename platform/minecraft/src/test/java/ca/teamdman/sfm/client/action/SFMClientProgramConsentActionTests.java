package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.program.*;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfml.ast.ProgramExecutionSide;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

import static ca.teamdman.sfm.client.program.ClientProgramConsentGate.*;
import static org.junit.jupiter.api.Assertions.*;

class SFMClientProgramConsentActionTests {
    @TempDir Path directory;
    private ClientProgramIdentity identity(String source) {
        return ClientProgramIdentity.fromStoredSource(source, ProgramExecutionSide.CLIENT,
                ClientProgramWorldIdentity.integrated(new UUID(1, 2)), new ResourceLocation("minecraft:overworld"),
                BlockPos.ZERO, ClientProgramIdentity.CLIENT_MANAGER_RUNTIME, Set.of(EXECUTE, RENDER));
    }
    private static SFMValue input(String capability) { return SFMValue.object(Map.of("capability", SFMValue.of(capability))); }
    private static String field(SFMValue result, String key) { return ((SFMValue.StringValue) ((SFMValue.ObjectValue) result).fields().get(key)).value(); }

    @Test void requestIsSelfOnlyTypedAndDenialDoesNotReopen() {
        var service = new ClientProgramConsentService(directory.resolve("consents.bin"), () -> 1000);
        var id = identity("source");
        service.observe(id, "source", "", Map.of());
        var action = new SFMClientProgramConsentAction(true, () -> service, (program, capability) -> List.of());
        var handler = action.programmaticHandler().orElseThrow();
        var context = new SFMClientActionProgrammaticContext(SFMClientActionAuthorizationService.PrincipalKind.CLIENT_PROGRAM,
                Optional.of(id));
        var pending = handler.invoke(input(RENDER.toString()), context);
        assertEquals("pending", field(pending, "consent"));
        assertTrue(action.programmaticDescriptor().orElseThrow().checkResult(pending).isEmpty());
        assertEquals(EXECUTE, action.programmaticDescriptor().orElseThrow().controlPermission());
        service.decide(id, RENDER, Decision.DENY, null, null);
        assertEquals("denied", field(handler.invoke(input(RENDER.toString()), context), "consent"));
        assertEquals("invalid_capability", field(handler.invoke(input("sfm:disk/read"), context), "status"));
        assertEquals("invalid_input", field(handler.invoke(SFMValue.object(Map.of("capability", SFMValue.of(RENDER.toString()),
                "source", SFMValue.of("other source"))), context), "status"));
        assertEquals("no_program_caller", field(handler.invoke(input(RENDER.toString()),
                new SFMClientActionProgrammaticContext(SFMClientActionAuthorizationService.PrincipalKind.HUMAN, Optional.empty())), "status"));
        assertEquals(ConsentState.ABSENT, service.gate().state(identity("another program"), RENDER));
    }

    @Test void statusKeepsApprovalSeparateFromPolicyAndStopAllIsCentral() {
        var service = new ClientProgramConsentService(directory.resolve("consents.bin"), () -> 1000);
        var id = identity("source");
        service.observe(id, "source", "", Map.of()); service.request(id, EXECUTE);
        service.decide(id, EXECUTE, Decision.APPROVE, null, null);
        var action = new SFMClientProgramConsentAction(false, () -> service, (program, cap) -> List.of("disabled_by_policy"));
        var output = action.query(id, input(EXECUTE.toString()));
        assertEquals("approved", field(output, "consent"));
        assertEquals("blocked_by_policy", field(output, "effective"));
        assertTrue(action.programmaticDescriptor().orElseThrow().checkResult(output).isEmpty());
        service.stopAll();
        var noCustomPolicy = new SFMClientProgramConsentAction(false, () -> service, (program, cap) -> List.of());
        assertEquals("blocked_by_policy", field(noCustomPolicy.query(id, input(EXECUTE.toString())), "effective"));
        assertTrue(new SFMClientProgramConsentControlAction().programmaticDescriptor().isEmpty());
        assertTrue(new SFMClientProgramConsentControlAction().programmaticHandler().isEmpty());
    }
}
