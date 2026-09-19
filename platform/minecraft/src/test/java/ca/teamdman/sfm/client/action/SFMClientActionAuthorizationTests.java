package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.program.ClientProgramConsentGate;
import ca.teamdman.sfm.client.program.ClientProgramIdentity;
import ca.teamdman.sfm.client.program.ClientProgramWorldIdentity;
import ca.teamdman.sfm.common.net.SFMBoundedEffectBudget;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfml.ast.ProgramExecutionSide;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static ca.teamdman.sfm.client.action.SFMClientActionAuthorizationService.Status.*;
import static ca.teamdman.sfm.client.program.ClientProgramConsentGate.EXECUTE;
import static org.junit.jupiter.api.Assertions.*;

class SFMClientActionAuthorizationTests {
    private static final ResourceLocation SEND = new ResourceLocation("sfm", "packet/send");
    private static final ResourceLocation OVERWORLD = new ResourceLocation("minecraft", "overworld");
    private static final UUID WORLD = UUID.fromString("624dba1d-029d-44e4-8f9e-20b109002a46");
    private static final ClientProgramConsentGate.Policy ALLOW = (identity, permission) -> List.of();
    private static final SFMClientActionDescriptor DESCRIPTOR = new SFMPacketSendAction(
            Optional::empty, (target, value) -> false).programmaticDescriptor().orElseThrow();

    @Test
    void programNeverBorrowsHumanAuthorityAndRevocationAppliesToNextInvocation() {
        Fixture fixture = new Fixture(20);
        assertEquals(AWAITING_CONSENT, fixture.attempt(input(12)).authorization().status());
        assertEquals(0, fixture.calls.get());
        assertTrue(fixture.service.performHuman(DESCRIPTOR, input(12), () -> true, value -> true)
                .localTransportAccepted());
        assertEquals(AWAITING_CONSENT, fixture.attempt(input(12)).authorization().status());
        fixture.approve(EXECUTE);
        assertEquals(AWAITING_CONSENT, fixture.attempt(input(12)).authorization().status());
        fixture.approve(SEND);
        assertTrue(fixture.attempt(input(12)).localTransportAccepted());
        fixture.gate.revoke(fixture.program, SEND);
        assertEquals(AWAITING_CONSENT, fixture.attempt(input(12)).authorization().status());
        assertEquals(1, fixture.calls.get());
    }

    @Test
    void savedApprovalCannotSurviveLiveSourceWorldPositionOrBindingChange() {
        Fixture fixture = new Fixture(20);
        fixture.approveAll();
        List<ClientProgramIdentity> changed = List.of(
                identity("changed source", "bindings", WORLD, new BlockPos(1, 2, 3), Set.of(EXECUTE, SEND)),
                identity("private source", "changed bindings", WORLD, new BlockPos(1, 2, 3), Set.of(EXECUTE, SEND)),
                identity("private source", "bindings", UUID.randomUUID(), new BlockPos(1, 2, 3), Set.of(EXECUTE, SEND)),
                identity("private source", "bindings", WORLD, new BlockPos(2, 2, 3), Set.of(EXECUTE, SEND)),
                identity("private source", "bindings", WORLD, new BlockPos(1, 2, 3), Set.of(EXECUTE, SEND, new ResourceLocation("sfm", "terminal/input")))
        );
        for (ClientProgramIdentity replacement : changed) {
            fixture.current.set(replacement);
            var rejected = fixture.attempt(input(12));
            assertEquals(STALE_PROGRAM_CONTEXT, rejected.authorization().status());
            assertFalse(rejected.transportCalled());
            assertEquals(0, rejected.authorization().trace().chargedBytes());
        }
        fixture.current.set(null); // unloaded manager or disconnected world
        assertEquals(STALE_PROGRAM_CONTEXT, fixture.attempt(input(12)).authorization().status());
        fixture.current.set(fixture.program);
        assertTrue(fixture.attempt(input(12)).localTransportAccepted());
        assertEquals(1, fixture.calls.get());
    }

    @Test
    void contextIsRecheckedAfterEvaluatingDynamicTargetPolicy() {
        Fixture fixture = new Fixture(1);
        fixture.approveAll();
        var result = fixture.service.performProgram(SEND, input(12), fixture.program, fixture.gate, ALLOW,
                (identity, scope) -> { fixture.current.set(null); return true; }, () -> true,
                value -> { fixture.calls.incrementAndGet(); return true; });
        assertEquals(STALE_PROGRAM_CONTEXT, result.authorization().status());
        assertEquals(0, fixture.calls.get());
        fixture.current.set(fixture.program);
        assertTrue(fixture.attempt(input(12)).localTransportAccepted(), "stale attempt must not charge the quota");
    }

    @Test
    void typedValuesAndExactDynamicScopeAreCheckedBeforeEffect() {
        Fixture fixture = new Fixture(1);
        fixture.approveAll();
        assertEquals(INVALID_INPUT, fixture.attempt(SFMValue.object(Map.of("x", SFMValue.of(0.5))))
                .authorization().status());
        assertEquals(TARGET_UNAUTHORIZED, fixture.attempt(input(13)).authorization().status());
        assertEquals(0, fixture.calls.get());
        var admitted = fixture.attempt(input(12));
        assertEquals(ALLOWED, admitted.authorization().status());
        assertTrue(admitted.authorization().trace().chargedBytes() > 0);
        assertEquals(1, fixture.calls.get());
    }

    @Test
    void undescribedAndUndeclaredActionsFailClosed() {
        Fixture fixture = new Fixture(20);
        fixture.approveAll();
        var unknown = fixture.service.performProgram(new ResourceLocation("sfm", "filesystem/read"), input(12),
                fixture.program, fixture.gate, ALLOW, (identity, scope) -> true, () -> true,
                value -> { throw new AssertionError("undescribed action executed"); });
        assertEquals(ACTION_UNDESCRIBED, unknown.authorization().status());
        ClientProgramIdentity executeOnly = identity("private source", "bindings", WORLD,
                new BlockPos(1, 2, 3), Set.of(EXECUTE));
        fixture.current.set(executeOnly);
        fixture.gate.request(executeOnly, EXECUTE);
        fixture.gate.decide(executeOnly, EXECUTE, ClientProgramConsentGate.Decision.APPROVE);
        var undeclared = fixture.service.authorizeProgram(SEND, input(12), executeOnly,
                fixture.gate, ALLOW, (identity, scope) -> true, () -> true);
        assertEquals(CAPABILITY_UNDECLARED, undeclared.status());
    }

    @Test
    void denialPolicyAndPrivateWorldGateRemainDistinctAndNeverExecute() {
        Fixture fixture = new Fixture(20);
        fixture.approve(EXECUTE);
        fixture.gate.request(fixture.program, SEND);
        fixture.gate.decide(fixture.program, SEND, ClientProgramConsentGate.Decision.DENY);
        assertEquals(DENIED_BY_USER, fixture.attempt(input(12)).authorization().status());
        fixture.gate.reopenDenied(fixture.program, SEND);
        fixture.gate.decide(fixture.program, SEND, ClientProgramConsentGate.Decision.APPROVE);
        assertEquals(BLOCKED_BY_POLICY, fixture.service.authorizeProgram(SEND, input(12), fixture.program,
                fixture.gate, (identity, permission) -> List.of("effects_disabled_by_policy"),
                (identity, scope) -> true, () -> true).status());
        assertEquals(EFFECTS_DISABLED, fixture.service.authorizeProgram(SEND, input(12), fixture.program,
                fixture.gate, ALLOW, (identity, scope) -> true, () -> false).status());
        assertEquals(0, fixture.calls.get());
    }

    @Test
    void humanAndEveryProgramShareOneConnectionBudgetEvenWhenTransportRejects() {
        Fixture fixture = new Fixture(2);
        fixture.approveAll();
        var human = fixture.service.performHuman(DESCRIPTOR, input(12), () -> true, value -> false);
        assertTrue(human.transportCalled());
        assertFalse(human.localTransportAccepted());
        assertTrue(fixture.attempt(input(12)).localTransportAccepted());
        assertEquals(RATE_LIMITED, fixture.attempt(input(12)).authorization().status());
        assertEquals(RATE_LIMITED, fixture.service.performHuman(DESCRIPTOR, input(12), () -> true, value -> true)
                .authorization().status());
        fixture.window.incrementAndGet();
        assertTrue(fixture.attempt(input(12)).localTransportAccepted());
        assertEquals(2, fixture.calls.get());
    }

    @Test
    void provenanceIsBoundedAndContainsHashesInsteadOfSourceOrPayload() {
        Fixture fixture = new Fixture(200);
        fixture.approveAll();
        for (int i = 0; i < 140; i++) fixture.attempt(input(12));
        List<SFMClientActionAuthorizationService.Trace> traces = fixture.service.traceSnapshot();
        assertEquals(SFMClientActionAuthorizationService.MAX_TRACES, traces.size());
        var latest = traces.get(traces.size() - 1);
        assertEquals(140, latest.sequence());
        assertEquals(fixture.program.sourceSha256(), latest.program().orElseThrow().sourceSha256());
        assertEquals(fixture.program.bindingSha256(), latest.program().orElseThrow().bindingSha256());
        assertEquals(ClientProgramConsentGate.ConsentState.APPROVED, latest.consent().get(SEND));
        assertEquals(64, latest.scopes().get(0).subjectSha256().length());
        assertFalse(traces.toString().contains("private source"));
        assertFalse(traces.toString().contains("secret payload"));
    }

    private static ClientProgramIdentity identity(String source, String bindings, UUID world, BlockPos position,
                                                   Set<ResourceLocation> permissions) {
        return ClientProgramIdentity.fromStoredSourceAndBindings(source, bindings, ProgramExecutionSide.CLIENT,
                ClientProgramWorldIdentity.integrated(world), OVERWORLD, position,
                ClientProgramIdentity.CLIENT_MANAGER_RUNTIME, permissions);
    }

    private static SFMValue input(int x) {
        return SFMValue.object(Map.of("dimension", SFMValue.of("minecraft:overworld"), "x", SFMValue.of(x),
                "y", SFMValue.of(64), "z", SFMValue.of(-7), "value", SFMValue.of("secret payload")));
    }

    private static final class Fixture {
        private final ClientProgramIdentity program = identity("private source", "bindings", WORLD,
                new BlockPos(1, 2, 3), Set.of(EXECUTE, SEND));
        private final AtomicReference<ClientProgramIdentity> current = new AtomicReference<>(program);
        private final ClientProgramConsentGate gate = new ClientProgramConsentGate();
        private final AtomicLong window = new AtomicLong(1);
        private final AtomicInteger calls = new AtomicInteger();
        private final SFMClientActionAuthorizationService service;

        private Fixture(int maxOperations) {
            service = new SFMClientActionAuthorizationService(
                    id -> id.equals(SEND) ? Optional.of(DESCRIPTOR) : Optional.empty(),
                    new SFMBoundedEffectBudget(maxOperations, 64 * 1024), window::get,
                    ignored -> Optional.ofNullable(current.get()));
        }

        private void approve(ResourceLocation permission) {
            gate.request(program, permission);
            gate.decide(program, permission, ClientProgramConsentGate.Decision.APPROVE);
        }

        private void approveAll() { approve(EXECUTE); approve(SEND); }

        private SFMClientActionAuthorizationService.EffectAttempt attempt(SFMValue input) {
            return service.performProgram(SEND, input, program, gate, ALLOW,
                    (identity, scope) -> ((SFMValue.ObjectValue) scope.subject()).fields().get("x").equals(SFMValue.of(12)),
                    () -> true, value -> { calls.incrementAndGet(); return true; });
        }
    }
}
