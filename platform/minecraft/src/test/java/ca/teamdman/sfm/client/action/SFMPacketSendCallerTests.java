package ca.teamdman.sfm.client.action;

import ca.teamdman.sfml.ast.ProgramExecutionSide;
import ca.teamdman.sfm.client.program.ClientProgramConsentGate;
import ca.teamdman.sfm.client.program.ClientProgramIdentity;
import ca.teamdman.sfm.client.program.ClientProgramWorldIdentity;
import ca.teamdman.sfm.common.net.SFMBoundedEffectBudget;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.value.SFMValueJsonCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SFMPacketSendCallerTests {
    @Test
    void typedHandlerForwardsTrustedProgramCallerInsteadOfFallingBackToHuman() {
        var descriptor = new SFMPacketSendAction(Optional::empty, (target, value) -> false)
                .programmaticDescriptor().orElseThrow();
        var authorization = new SFMClientActionAuthorizationService(id -> Optional.of(descriptor),
                new SFMBoundedEffectBudget(4, 4096), () -> 1);
        AtomicBoolean effectsAvailable = new AtomicBoolean(true);
        AtomicReference<Optional<ClientProgramIdentity>> forwarded = new AtomicReference<>();
        var action = new SFMPacketSendAction(Optional::empty,
                (target, value) -> { throw new AssertionError("Program used human transport"); },
                authorization, effectsAvailable::get, (target, value, caller) -> {
                    forwarded.set(caller);
                    return true;
                });
        var identity = ClientProgramIdentity.fromStoredSource("CLIENT BTW", ProgramExecutionSide.CLIENT,
                ClientProgramWorldIdentity.integrated(new UUID(1, 2)), new ResourceLocation("minecraft:overworld"),
                BlockPos.ZERO, ClientProgramIdentity.CLIENT_MANAGER_RUNTIME,
                Set.of(ClientProgramConsentGate.EXECUTE, new ResourceLocation("sfm:packet/send")));
        var input = SFMValueJsonCodec.decode("{\"dimension\":\"minecraft:overworld\",\"x\":0,\"y\":64,\"z\":0,\"value\":null}");
        var context = new SFMClientActionProgrammaticContext(
                SFMClientActionAuthorizationService.PrincipalKind.CLIENT_PROGRAM, Optional.of(identity));
        var result = (SFMValue.ObjectValue) action.programmaticHandler().orElseThrow().invoke(input, context);
        assertEquals(Optional.of(identity), forwarded.get());
        assertEquals(SFMValue.of(true), result.fields().get("local_transport_accepted"));
        effectsAvailable.set(false);
        forwarded.set(null);
        var disabled = (SFMValue.ObjectValue) action.programmaticHandler().orElseThrow().invoke(input, context);
        assertNull(forwarded.get());
        assertEquals(SFMValue.of(false), disabled.fields().get("local_transport_accepted"));
    }
}
