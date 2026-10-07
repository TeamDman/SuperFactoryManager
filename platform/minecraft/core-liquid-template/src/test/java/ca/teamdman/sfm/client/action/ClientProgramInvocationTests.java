package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.program.*;
{% if features.packet_actions %}
import ca.teamdman.sfm.common.net.SFMBoundedEffectBudget;
{% endif %}
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.value.SFMValueSchema;
import ca.teamdman.sfml.ast.*;
import ca.teamdman.sfml.program_builder.ProgramBuilder;
{% if features.packet_actions %}
import net.minecraft.core.BlockPos;
{% endif %}
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.*;
{% if features.packet_actions %}
import java.util.concurrent.atomic.AtomicInteger;
{% endif %}
{% if features.packet_actions %}
import java.util.concurrent.atomic.AtomicReference;
{% endif %}

import static ca.teamdman.sfm.client.program.ClientProgramConsentGate.EXECUTE;
{% if features.packet_actions and features.client_frame_render %}
import static ca.teamdman.sfm.client.program.ClientProgramConsentGate.RENDER;
{% endif %}
import static org.junit.jupiter.api.Assertions.*;

class ClientProgramInvocationTests {
{% if features.packet_actions %}
    private static final ResourceLocation SEND = new ResourceLocation("sfm", "packet/send");
{% endif %}
{% if features.packet_actions %}
    private static final ResourceLocation READ = new ResourceLocation("sfm", "fixture/read");
{% endif %}
{% if features.packet_actions and features.client_frame_render %}
    private static final ResourceLocation RED = new ResourceLocation("minecraft", "textures/block/red_concrete.png");
{% endif %}
    private static final SFMValue INPUT = SFMValueSchema.decodeActionJson(
            "{\"dimension\":\"minecraft:overworld\",\"x\":12,\"y\":64,\"z\":-7,\"value\":{\"amount\":1}}");
{% if features.packet_actions %}
    private static final SFMClientActionDescriptor SEND_DESCRIPTOR = new SFMPacketSendAction(
            Optional::empty, (target, value) -> true).programmaticDescriptor().orElseThrow();
{% endif %}
{% if features.packet_actions %}
    private static final SFMValue OK = SFMValue.object(Map.of("status", SFMValue.of("send_attempted"),
            "local_transport_accepted", SFMValue.of(true)));
{% endif %}
{% if features.packet_actions %}
    private static final ClientProgramConsentGate.Policy POLICY = (identity, permission) -> List.of();
{% endif %}

    @Test
    void typedProgramParsesRoundTripsAndCannotExecuteOnServer() {
        String body = body();
        Program program = parse(body);
        assertTrue(new ProgramBuilder(program.toString()).forExecutionSide(ProgramExecutionSide.CLIENT).build().isBuildSuccessful());
        assertFalse(new ProgramBuilder(program.toString()).forExecutionSide(ProgramExecutionSide.SERVER).build().isBuildSuccessful());
        assertFalse(new ProgramBuilder("SERVER BTW EVERY 20 TICKS DO LET request BE JSON \"{}\" END")
                .forExecutionSide(ProgramExecutionSide.SERVER).build().isBuildSuccessful());
        var labels = new ProgramBuilder("EVERY 20 TICKS DO INPUT FROM json OUTPUT TO field END")
                .forExecutionSide(ProgramExecutionSide.SERVER).build();
        assertTrue(labels.isBuildSuccessful(), () -> labels.metadata().errors().toString());
    }

    @Test
    void typedExpressionsAndComparisonRetainExactSourceLocations() {
        for (String invocation : List.of("INVOKE sfm:packet/send WITH request", "INVOKE \"sfm:packet/send\" WITH request")) {
            String source = "CLIENT BTW\nEVERY FRAME FOR displays AS display DO\n"
                    + body().replace("INVOKE sfm:packet/send WITH request", invocation) + "\nEND";
            var built = new ProgramBuilder(source).forExecutionSide(ProgramExecutionSide.CLIENT).build();
            assertTrue(built.isBuildSuccessful(), () -> built.metadata().errors().toString());
            FrameTrigger frame = (FrameTrigger) built.program().triggers().get(0);
            ASTNode json = ((LetStatement) frame.block().statements().get(0)).expression();
            ASTNode invoke = ((LetStatement) frame.block().statements().get(1)).expression();
            ASTNode field = ((LetStatement) frame.block().statements().get(2)).expression();
            ASTNode comparison = ((IfStatement) frame.block().statements().get(5)).condition();
            for (ASTNode node : List.of(json, invoke, field, comparison)) {
                var context = built.metadata().astBuilder().getContextForNode(node).orElseThrow();
                // Source spans retain the author's spelling, not the canonical quoted formatting.
                assertEquals(node == invoke ? invocation : node.toString(),
                        source.substring(context.start.getStartIndex(), context.stop.getStopIndex() + 1));
                assertTrue(built.metadata().astBuilder().getNodesUnderCursor(context.start.getStartIndex())
                        .stream().anyMatch(mapped -> mapped.getFirst() == node));
                assertTrue(built.metadata().astBuilder().getLineColumnForNode(node).startsWith("Line "));
            }
        }
    }

{% if features.packet_actions %}
    @Test
    void manifestDerivesPermissionsAndExactScopeFromTypedInput() {
        Fixture fixture = new Fixture(body(), OK, SFMClientActionDescriptor.CostClass.SERVER_EFFECT);
{% if features.client_frame_render %}
        assertEquals(Set.of(EXECUTE, SEND, RENDER), fixture.manifest.capabilities());
{% else %}
        assertEquals(Set.of(EXECUTE, SEND), fixture.manifest.capabilities());
{% endif %}
        var scopes = ((SFMClientActionDescriptor.InputCheck.Accepted) SEND_DESCRIPTOR.checkInput(INPUT)).dataScopes();
        assertTrue(fixture.manifest.permits(SEND, scopes.get(0)));
        Map<String, SFMValue> changed = new HashMap<>(((SFMValue.ObjectValue) INPUT).fields());
        changed.put("x", SFMValue.of(13));
        var changedScope = ((SFMClientActionDescriptor.InputCheck.Accepted) SEND_DESCRIPTOR.checkInput(SFMValue.object(changed))).dataScopes().get(0);
        assertFalse(fixture.manifest.permits(SEND, changedScope));
    }

{% endif %}
{% if features.packet_actions %}
    @Test
    void linkerRejectsUnknownActionsIllTypedInputUnboundFieldsAndDynamicTargets() {
        Fixture fixture = new Fixture(body(), OK, SFMClientActionDescriptor.CostClass.SERVER_EFFECT);
        for (String invalid : List.of(
                "LET request BE JSON \"{}\" LET result BE INVOKE sfm:packet/send WITH request",
                "LET request BE JSON \"{}\" LET result BE INVOKE sfm:filesystem/read WITH request",
                "LET result BE FIELD \"missing\" OF unknown",
                "LET request BE JSON \"{}\" LET result BE FIELD \"missing\" OF request",
                request() + " LET result BE INVOKE sfm:packet/send WITH request LET second BE INVOKE sfm:packet/send WITH result",
                "IF TRUE THEN LET hidden BE JSON \"{}\" END LET result BE FIELD \"x\" OF hidden")) {
            assertThrows(IllegalArgumentException.class,
                    () -> ClientProgramActionManifest.compile(parse(invalid), fixture.lookup), invalid);
        }
    }

{% endif %}
{% if features.packet_actions %}
    @Test
    void executionAndActionConsentWorkWithoutDrawingAndResultsAreTyped() {
        Fixture fixture = new Fixture(body(), OK, SFMClientActionDescriptor.CostClass.SERVER_EFFECT);
        fixture.approve(EXECUTE);
        var denied = fixture.evaluate(false);
        assertEquals(SFMValue.of("awaiting_consent"), denied.values().get("gateway"));
        assertEquals(0, fixture.calls.get());
        fixture.approve(SEND);
        var result = fixture.evaluate(false);
        assertTrue(result.image().isEmpty(), "No drawing grant must preserve fallback");
        assertEquals(SFMValue.of("ok"), result.values().get("gateway"));
        assertEquals(SFMValue.of("send_attempted"), result.values().get("status"));
        assertEquals(1, fixture.calls.get());
{% if features.client_frame_render %}
        assertEquals(RED, fixture.evaluate(true).image().orElseThrow());
{% endif %}
        fixture.gate.revoke(fixture.identity, SEND);
        assertEquals(SFMValue.of("awaiting_consent"), fixture.evaluate(false).values().get("gateway"));
{% if features.client_frame_render %}
        assertEquals(2, fixture.calls.get());
{% else %}
        assertEquals(1, fixture.calls.get());
{% endif %}
    }

{% endif %}
{% if features.packet_actions %}
    @Test
    void dispatcherRejectsStaleCallerAndMalformedResultWithoutLeakingException() {
        Fixture stale = new Fixture(body(), OK, SFMClientActionDescriptor.CostClass.SERVER_EFFECT);
        stale.approve(EXECUTE); stale.approve(SEND);
        stale.current.set(null);
        assertEquals(SFMValue.of("stale_program_context"), stale.evaluate(false).values().get("gateway"));
        assertEquals(0, stale.calls.get());
        Fixture malformed = new Fixture(body(), SFMValue.of("not a result"), SFMClientActionDescriptor.CostClass.SERVER_EFFECT);
        malformed.approve(EXECUTE); malformed.approve(SEND);
        assertEquals(SFMValue.of("invalid_result"), malformed.evaluate(false).values().get("gateway"));
        assertEquals(1, malformed.calls.get());
    }

{% endif %}
{% if features.client_frame_render %}
    @Test
    void branchBindingsStayLocalAndExactNumbersDoNotCoerce() {
        Program program = parse("LET value BE JSON \"1\" IF value EQ JSON \"1.0\" THEN "
                + "RENDER IMAGE \"minecraft:textures/block/red_concrete.png\" TO display ELSE "
                + "LET value BE JSON \"2\" END");
        var evaluated = ClientFrameEvaluator.evaluate((FrameTrigger) program.triggers().get(0), 0,
                (id, input) -> { throw new AssertionError(); }, true);
        assertTrue(evaluated.image().isEmpty());
        assertEquals(SFMValue.of(1), evaluated.values().get("value"));
    }

{% endif %}
{% if features.packet_actions %}
    @Test
    void localReadsHaveSeparateBoundedQuotaAndCannotConsumeServerEffectQuota() {
        SFMClientActionDescriptor read = descriptor(READ, SFMClientActionDescriptor.CostClass.LOCAL_READ);
        ClientProgramIdentity identity = identity(Set.of(EXECUTE, SEND, READ));
        ClientProgramConsentGate gate = new ClientProgramConsentGate();
        for (ResourceLocation permission : identity.requestedCapabilities()) {
            gate.request(identity, permission); gate.decide(identity, permission, ClientProgramConsentGate.Decision.APPROVE);
        }
        var service = new SFMClientActionAuthorizationService(id -> Optional.of(id.equals(READ) ? read : SEND_DESCRIPTOR),
                new SFMBoundedEffectBudget(1, 64 * 1024), () -> 1, Optional::of);
        for (int i = 0; i < 2048; i++) assertTrue(service.authorizeProgram(READ, SFMValue.nullValue(), identity,
                gate, POLICY, (ignored, scope) -> true, () -> true).allowed());
        assertEquals(SFMClientActionAuthorizationService.Status.RATE_LIMITED,
                service.authorizeProgram(READ, SFMValue.nullValue(), identity, gate, POLICY,
                        (ignored, scope) -> true, () -> true).status());
        assertTrue(service.authorizeProgram(SEND, INPUT, identity, gate, POLICY,
                (ignored, scope) -> true, () -> true).allowed());
    }

{% endif %}
    @Test
    void actionLiteralUsesStrictJsonAndBindingBudget() {
        assertFalse(new ProgramBuilder("CLIENT BTW EVERY FRAME FOR displays AS display DO LET value BE JSON \"NaN\" END")
                .forExecutionSide(ProgramExecutionSide.CLIENT).build().isBuildSuccessful());
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 65; i++) body.append("LET v").append(i).append(" BE JSON \"null\"\n");
        assertThrows(IllegalArgumentException.class, () -> ClientProgramActionManifest.compile(parse(body.toString()), id -> Optional.empty()));
    }

{% if features.packet_actions %}
    @Test
    void actionManifestCannotExceedTheConsentStoreCapabilityCeiling() {
        Map<ResourceLocation, SFMClientProgramActionDispatcher.Binding> actions = new HashMap<>();
        StringBuilder body = new StringBuilder("LET request BE JSON \"null\"\n");
        for (int i = 0; i < 32; i++) {
            ResourceLocation id = new ResourceLocation("sfm", "control_" + i);
            ResourceLocation permission = new ResourceLocation("sfm", "data_" + i);
            SFMClientActionDescriptor descriptor = new SFMClientActionDescriptor(id, SFMValueSchema.any(),
                    SEND_DESCRIPTOR.resultSchema(), SFMClientActionDescriptor.ExecutionSide.CLIENT, id,
                    input -> new SFMClientActionDescriptor.InputCheck.Accepted(List.of(
                            new SFMClientActionDescriptor.DataScope(permission, SFMValue.nullValue()))),
                    SFMClientActionDescriptor.CostClass.LOCAL_READ,
                    SFMClientActionDescriptor.Acknowledgement.LOCAL_RESULT_ONLY, SEND_DESCRIPTOR.resultStatuses());
            actions.put(id, new SFMClientProgramActionDispatcher.Binding(descriptor, (input, context) -> OK));
            body.append("LET v").append(i).append(" BE INVOKE ").append(id).append(" WITH request\n");
            if (i == 30) assertEquals(63, ClientProgramActionManifest.compile(parse(body.toString()),
                    action -> Optional.ofNullable(actions.get(action))).capabilities().size());
        }
        var failure = assertThrows(IllegalArgumentException.class, () -> ClientProgramActionManifest.compile(
                parse(body.toString()), action -> Optional.ofNullable(actions.get(action))));
        assertTrue(failure.getMessage().contains("capability budget"));
    }

{% endif %}
    private static String quoted(SFMValue value) {
        return "\"" + SFMValueSchema.canonicalActionJson(value).replace("\"", "\\\"") + "\"";
    }

{% if features.client_program_reads and features.client_inbox %}
    @Test
    void arbitraryInboxPayloadFieldsAreNullSafeWithoutWideningLiteralActionTargets() {
        var action = new SFMClientProgramReadAction(true, (caller, input) -> SFMValue.nullValue());
        var descriptor = action.programmaticDescriptor().orElseThrow();
        String body = "LET request BE JSON \"{\\\"channel\\\":\\\"sfm:fixture\\\",\\\"mode\\\":\\\"latest\\\"}\"\n"
                + "LET response BE INVOKE sfm:client_inbox/read WITH request\n"
                + "LET result BE FIELD \"result\" OF response\n"
                + "LET payload BE FIELD \"value\" OF result\n"
                + "LET amount BE FIELD \"amount\" OF payload\n"
                + "LET nested BE FIELD \"nested\" OF amount\n";
        Program program = parse(body);
        SFMClientProgramActionDispatcher.Lookup lookup = id -> id.equals(SFMClientProgramReadAction.INBOX)
                ? Optional.of(new SFMClientProgramActionDispatcher.Binding(descriptor, action.programmaticHandler().orElseThrow())) : Optional.empty();
        var manifest = ClientProgramActionManifest.compile(program, lookup);
        assertEquals(Set.of(EXECUTE, ClientProgramInboxReadSurface.READ), manifest.capabilities());
        for (SFMValue payload : List.of(SFMValue.nullValue(), SFMValue.of(4), SFMValue.array(List.of()),
                SFMValue.object(Map.of()), SFMValue.object(Map.of("amount", SFMValue.of(32))))) {
            var response = SFMValue.object(Map.of("status", SFMValue.of("ok"), "result",
                    SFMValue.object(Map.of("value", payload))));
            var evaluated = ClientFrameEvaluator.evaluate((FrameTrigger) program.triggers().get(0), 0,
                    (id, input) -> response, false);
            assertEquals(ClientProgramActionManifest.field(payload, "amount"), evaluated.values().get("amount"));
            assertEquals(SFMValue.nullValue(), evaluated.values().get("nested"));
        }
        assertThrows(IllegalArgumentException.class, () -> ClientProgramActionManifest.compile(
                parse(body + "LET changed BE INVOKE sfm:client_inbox/read WITH payload"), lookup));
    }

{% endif %}
    private static String request() { return "LET request BE JSON " + quoted(INPUT); }
    private static String body() {
        return request() + "\nLET response BE INVOKE sfm:packet/send WITH request\n"
               + "LET gateway BE FIELD \"status\" OF response\nLET result BE FIELD \"result\" OF response\n"
               + "LET status BE FIELD \"status\" OF result\nIF status EQ JSON " + quoted(SFMValue.of("send_attempted"))
{% if features.client_frame_render %}
               + " THEN RENDER IMAGE \"minecraft:textures/block/red_concrete.png\" TO display END";
{% else %}
               + " THEN END";
{% endif %}
    }
    private static Program parse(String body) {
        var result = new ProgramBuilder("CLIENT BTW\nEVERY FRAME FOR displays AS display DO\n" + body + "\nEND")
                .forExecutionSide(ProgramExecutionSide.CLIENT).build();
        assertTrue(result.isBuildSuccessful(), () -> result.metadata().errors().toString());
        return result.program();
    }
{% if features.packet_actions %}
    private static ClientProgramIdentity identity(Set<ResourceLocation> permissions) {
        return ClientProgramIdentity.fromStoredSource("fixture", ProgramExecutionSide.CLIENT,
                ClientProgramWorldIdentity.integrated(UUID.fromString("363fc8a1-a30f-452b-9575-5447e3cac479")),
                new ResourceLocation("minecraft", "overworld"), BlockPos.ZERO,
                ClientProgramIdentity.CLIENT_MANAGER_RUNTIME, permissions);
    }
    private static SFMClientActionDescriptor descriptor(ResourceLocation id, SFMClientActionDescriptor.CostClass cost) {
        return new SFMClientActionDescriptor(id, SFMValueSchema.any(), SEND_DESCRIPTOR.resultSchema(),
                SFMClientActionDescriptor.ExecutionSide.CLIENT, id, SFMClientActionDescriptor.ScopeResolver.none(),
                cost, SFMClientActionDescriptor.Acknowledgement.LOCAL_RESULT_ONLY, SEND_DESCRIPTOR.resultStatuses());
    }
    private static final class Fixture {
        final Program program;
        final ClientProgramActionManifest manifest;
        final ClientProgramIdentity identity;
        final ClientProgramConsentGate gate = new ClientProgramConsentGate();
        final AtomicInteger calls = new AtomicInteger();
        final AtomicReference<ClientProgramIdentity> current;
        final SFMClientProgramActionDispatcher.Lookup lookup;
        final SFMClientProgramActionDispatcher dispatcher;
        Fixture(String body, SFMValue output, SFMClientActionDescriptor.CostClass cost) {
            program = parse(body);
            lookup = id -> id.equals(SEND) ? Optional.of(new SFMClientProgramActionDispatcher.Binding(SEND_DESCRIPTOR,
                    (input, context) -> { assertEquals(identityCaller(context), context.caller().orElseThrow()); calls.incrementAndGet(); return output; })) : Optional.empty();
            manifest = ClientProgramActionManifest.compile(program, lookup);
            identity = identity(manifest.capabilities());
            current = new AtomicReference<>(identity);
            var authorization = new SFMClientActionAuthorizationService(id -> id.equals(SEND) ? Optional.of(SEND_DESCRIPTOR) : Optional.empty(),
                    new SFMBoundedEffectBudget(32, 64 * 1024), () -> 1, ignored -> Optional.ofNullable(current.get()));
            dispatcher = new SFMClientProgramActionDispatcher(lookup, authorization);
        }
        private ClientProgramIdentity identityCaller(SFMClientActionProgrammaticContext context) {
            assertEquals(SFMClientActionAuthorizationService.PrincipalKind.CLIENT_PROGRAM, context.principal());
            return identity;
        }
        void approve(ResourceLocation permission) { gate.request(identity, permission); gate.decide(identity, permission, ClientProgramConsentGate.Decision.APPROVE); }
        ClientFrameEvaluator.Evaluation evaluate(boolean render) {
            return ClientFrameEvaluator.evaluate((FrameTrigger) program.triggers().get(0), 0,
                    (id, input) -> dispatcher.invoke(id, input, identity, gate, POLICY,
                            (ignored, scope) -> manifest.permits(id, scope), () -> true), render);
        }
    }
{% endif %}
}
