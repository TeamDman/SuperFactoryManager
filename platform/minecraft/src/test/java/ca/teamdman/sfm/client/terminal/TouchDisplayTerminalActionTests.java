package ca.teamdman.sfm.client.terminal;

import ca.teamdman.sfm.client.action.*;
import ca.teamdman.sfm.client.program.ClientProgramConsentGate;
import ca.teamdman.sfm.client.program.ClientProgramActionManifest;
import ca.teamdman.sfm.common.net.SFMBoundedEffectBudget;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.value.SFMValueSchema;
import ca.teamdman.sfml.ast.ProgramExecutionSide;
import ca.teamdman.sfml.program_builder.ProgramBuilder;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static ca.teamdman.sfm.client.terminal.TouchDisplayTerminalInputQueueTests.*;
import static org.junit.jupiter.api.Assertions.*;

class TouchDisplayTerminalActionTests {
    @Test void ambientFixtureChannelUsesSfmlSafeUuidHexInsteadOfHyphens() {
        String uuid = "01234567-89ab-cdef-0123-456789abcdef";
        String source = "SERVER BTW\nLET owner BE PLAYER OF Player\nEVERY 20 TICKS DO\n"
                + "BROADCAST TO owner CHANNEL sfm:terminal_circuit_%s\nEND";
        assertFalse(new ProgramBuilder(source.formatted(uuid)).forExecutionSide(ProgramExecutionSide.SERVER)
                .build().isBuildSuccessful(), "The reduced original fixture must reproduce the unquoted-hyphen parser failure");
        var fixed = new ProgramBuilder(source.formatted(uuid.replace("-", "")))
                .forExecutionSide(ProgramExecutionSide.SERVER).build();
        assertTrue(fixed.isBuildSuccessful(), () -> fixed.metadata().errors().toString());
    }

    @Test void ambientFixtureClientDeclarationParsesAndLinksItsExactCapabilities() {
        var binding = new TouchDisplayTerminalBinding(OWNER, BINDING.display(),
                new ResourceLocation("sfm", "terminal_circuit_0123456789abcdef0123456789abcdef"), true);
        String source = "CLIENT BTW\nEVERY FRAME FOR displays AS display DO\nLET terminalBinding BE JSON \""
                + SFMValueSchema.canonicalActionJson(binding.value()).replace("\"", "\\\"") + "\"\n"
                + "LET terminalStatus BE INVOKE \"sfm:terminal/display\" WITH terminalBinding\n"
                + "LET inputStatus BE INVOKE \"sfm:terminal/input/status\" WITH terminalBinding\n"
                + "RENDER IMAGE \"minecraft:textures/block/red_concrete.png\" TO display\nEND";
        var built = new ProgramBuilder(source).forExecutionSide(ProgramExecutionSide.CLIENT).build();
        assertTrue(built.isBuildSuccessful(), () -> built.metadata().errors().toString());
        Map<ResourceLocation, SFMClientProgramActionDispatcher.Binding> actions = new HashMap<>();
        for (var kind : List.of(SFMTerminalDisplayAction.Kind.DISPLAY, SFMTerminalDisplayAction.Kind.INPUT_STATUS)) {
            var action = new SFMTerminalDisplayAction(kind);
            var descriptor = action.programmaticDescriptor().orElseThrow();
            actions.put(descriptor.actionId(), new SFMClientProgramActionDispatcher.Binding(descriptor, action.programmaticHandler().orElseThrow()));
        }
        var manifest = ClientProgramActionManifest.compile(built.program(), id -> Optional.ofNullable(actions.get(id)));
        Set<ResourceLocation> expected = new HashSet<>(OWNER.requestedCapabilities());
        expected.add(ClientProgramConsentGate.RENDER);
        assertEquals(expected, manifest.capabilities());
        assertTrue(manifest.permits(SFMTerminalDisplayAction.DISPLAY,
                new SFMClientActionDescriptor.DataScope(TouchDisplayTerminalBroker.READ, binding.value())));
    }

    @Test void idleStatusReadsDoNotConsumeSharedDesktopEffectBudget() {
        Map<net.minecraft.resources.ResourceLocation, SFMClientActionDescriptor> descriptors = new HashMap<>();
        for (var kind : SFMTerminalDisplayAction.Kind.values()) {
            var descriptor = new SFMTerminalDisplayAction(kind).programmaticDescriptor().orElseThrow();
            descriptors.put(descriptor.actionId(), descriptor);
        }
        var service = new SFMClientActionAuthorizationService(id -> Optional.ofNullable(descriptors.get(id)),
                new SFMBoundedEffectBudget(32, 65536), () -> 0, Optional::of);
        var gate = approved();
        for (int i = 0; i < 100; i++) assertTrue(service.authorizeProgram(SFMTerminalDisplayAction.DISPLAY, BINDING.value(), OWNER,
                gate, (owner, cap) -> List.of(), (owner, scope) -> true, () -> true).allowed());
        var input = SFMValue.object(Map.of("binding", BINDING.value(), "u", SFMValue.of(.25), "v", SFMValue.of(.75)));
        AtomicInteger calls = new AtomicInteger();
        for (int i = 0; i < 32; i++) assertTrue(service.performProgram(SFMTerminalDisplayAction.INPUT_EFFECT, input, OWNER,
                gate, (owner, cap) -> List.of(), (owner, scope) -> true, () -> true, ignored -> { calls.incrementAndGet(); return true; }).localTransportAccepted());
        var limited = service.performProgram(SFMTerminalDisplayAction.INPUT_EFFECT, input, OWNER, gate,
                (owner, cap) -> List.of(), (owner, scope) -> true, () -> true, ignored -> { fail("Budget was bypassed"); return true; });
        assertEquals(SFMClientActionAuthorizationService.Status.RATE_LIMITED, limited.authorization().status());
        assertEquals(32, calls.get());
    }

    @Test void inputCannotBorrowReadConsentOrDirectlyInvokeDescriptorOnlyEffect() {
        var action = new SFMTerminalDisplayAction(SFMTerminalDisplayAction.Kind.INPUT_EFFECT);
        assertTrue(action.programmaticHandler().isEmpty());
        var descriptor = action.programmaticDescriptor().orElseThrow();
        var service = new SFMClientActionAuthorizationService(id -> Optional.of(descriptor),
                new SFMBoundedEffectBudget(32, 65536), () -> 0, Optional::of);
        var gate = approved(); gate.revoke(OWNER, TouchDisplayTerminalBroker.INPUT);
        var input = SFMValue.object(Map.of("binding", BINDING.value(), "u", SFMValue.of(.25), "v", SFMValue.of(.75)));
        var result = service.performProgram(SFMTerminalDisplayAction.INPUT_EFFECT, input, OWNER, gate,
                (owner, cap) -> List.of(), (owner, scope) -> true, () -> true, ignored -> { fail("Input consent was borrowed"); return true; });
        assertEquals(SFMClientActionAuthorizationService.Status.AWAITING_CONSENT, result.authorization().status());
        assertFalse(result.transportCalled());
        assertTrue(new SFMTerminalMountsControlAction().programmaticDescriptor().isEmpty());
    }

    @Test void effectScopesAreTheExactStaticBindingAndChannelNotTheTouchPayload() {
        var action = new SFMTerminalDisplayAction(SFMTerminalDisplayAction.Kind.INPUT_EFFECT);
        var input = SFMValue.object(Map.of("binding", BINDING.value(), "u", SFMValue.of(.25), "v", SFMValue.of(.75)));
        var scopes = ((SFMClientActionDescriptor.InputCheck.Accepted) action.programmaticDescriptor().orElseThrow().checkInput(input)).dataScopes();
        assertEquals(3, scopes.size());
        assertEquals(BINDING.value(), scopes.get(0).subject());
        assertEquals(BINDING.value(), scopes.get(1).subject());
        assertEquals(SFMValue.of(CHANNEL.toString()), scopes.get(2).subject());
    }

    private static ClientProgramConsentGate approved() {
        var gate = new ClientProgramConsentGate();
        for (var capability : OWNER.requestedCapabilities()) { gate.request(OWNER, capability); gate.decide(OWNER, capability, ClientProgramConsentGate.Decision.APPROVE); }
        return gate;
    }
}
