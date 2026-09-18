package ca.teamdman.sfm.client.program;

import ca.teamdman.sfml.ast.ProgramExecutionSide;
import ca.teamdman.sfm.common.value.SFMValue;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static ca.teamdman.sfm.client.program.ClientProgramBlockReadSurface.READ_BOUND;
import static ca.teamdman.sfm.client.program.ClientProgramBlockReadSurface.READ_LOADED;
import static ca.teamdman.sfm.client.program.ClientProgramBlockReadSurface.Status.UNAVAILABLE_BUDGET;
import static ca.teamdman.sfm.client.program.ClientProgramBlockReadSurface.Status.UNAVAILABLE_AWAITING_CONSENT;
import static ca.teamdman.sfm.client.program.ClientProgramBlockReadSurface.Status.UNAVAILABLE_BLOCKED_BY_POLICY;
import static ca.teamdman.sfm.client.program.ClientProgramBlockReadSurface.Status.UNAVAILABLE_DENIED_BY_USER;
import static ca.teamdman.sfm.client.program.ClientProgramBlockReadSurface.Status.UNAVAILABLE_OUT_OF_SCOPE;
import static ca.teamdman.sfm.client.program.ClientProgramBlockReadSurface.Status.UNAVAILABLE_UNDECLARED;
import static ca.teamdman.sfm.client.program.ClientProgramBlockReadSurface.Status.UNAVAILABLE_WORLD_CHANGED;
import static ca.teamdman.sfm.client.program.ClientProgramBlockReadSurface.Status.UNKNOWN_UNLOADED;
import static ca.teamdman.sfm.client.program.ClientProgramConsentGate.Decision.APPROVE;
import static ca.teamdman.sfm.client.program.ClientProgramConsentGate.Decision.DENY;
import static ca.teamdman.sfm.client.program.ClientProgramConsentGate.EXECUTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientProgramBlockReadSurfaceTests {
    private static final BlockPos MANAGER = new BlockPos(12, 64, -7);
    private static final ResourceLocation DIMENSION = new ResourceLocation("minecraft", "overworld");
    private static final ClientProgramConsentGate.Policy ALLOW = (identity, capability) -> List.of();

    private record FakeState(int revision, SFMValue value) {}

    private static final class FakeSource implements ClientProgramBlockReadSurface.Source<FakeState> {
        Object world = new Object();
        ResourceLocation dimension = DIMENSION;
        boolean active = true;
        final Map<BlockPos, FakeState> states = new HashMap<>();
        final Set<BlockPos> loaded = new HashSet<>();
        int samples;
        int projections;

        void put(BlockPos position, int revision, SFMValue value) {
            states.put(position, new FakeState(revision, value));
            loaded.add(position);
        }

        @Override public Object worldIdentity() { return world; }
        @Override public ResourceLocation dimension() { return dimension; }
        @Override public boolean isActive() { return active; }
        @Override public boolean isLoaded(BlockPos position) { return loaded.contains(position); }

        @Override
        public Optional<ClientProgramBlockReadSurface.Sample<FakeState>> sample(BlockPos position) {
            samples++;
            FakeState state = states.get(position);
            return state == null ? Optional.empty()
                    : Optional.of(new ClientProgramBlockReadSurface.Sample<>(state.revision(), state));
        }

        @Override
        public SFMValue project(FakeState state) {
            projections++;
            return state.value();
        }
    }

    private static ClientProgramIdentity identity(Set<ResourceLocation> capabilities) {
        return ClientProgramIdentity.fromStoredSource(
                "CLIENT BTW\nEVERY FRAME FOR displays AS display DO END",
                ProgramExecutionSide.CLIENT,
                ClientProgramWorldIdentity.integrated(UUID.fromString("eeab1b9a-92a5-4b7f-91b9-05b496761a0e")),
                DIMENSION,
                MANAGER,
                ClientProgramIdentity.CLIENT_MANAGER_RUNTIME,
                capabilities
        );
    }

    private static void approve(ClientProgramConsentGate gate, ClientProgramIdentity program,
                                ResourceLocation capability) {
        gate.request(program, capability);
        gate.decide(program, capability, APPROVE);
    }

    @Test
    void consentAndExactBoundScopeFailClosedBeforeReadingWorld() {
        ClientProgramIdentity program = identity(Set.of(EXECUTE, READ_BOUND));
        ClientProgramConsentGate gate = new ClientProgramConsentGate();
        FakeSource source = new FakeSource();
        BlockPos allowed = MANAGER.east();
        BlockPos notBound = MANAGER.west();
        source.put(allowed, 1, SFMValue.of("allowed"));
        source.put(notBound, 1, SFMValue.of("hidden"));
        ClientProgramBlockReadSurface<FakeState> surface = new ClientProgramBlockReadSurface<>(
                program, gate, ALLOW, source, Set.of(allowed), 3
        );

        assertEquals(UNAVAILABLE_AWAITING_CONSENT, surface.readBound(allowed).status());
        assertEquals(UNAVAILABLE_UNDECLARED, surface.readLoaded(allowed).status());
        approve(gate, program, EXECUTE);
        assertEquals(UNAVAILABLE_AWAITING_CONSENT, surface.readBound(allowed).status());
        approve(gate, program, READ_BOUND);
        assertEquals(UNAVAILABLE_OUT_OF_SCOPE, surface.readBound(notBound).status());
        assertEquals(UNAVAILABLE_OUT_OF_SCOPE, surface.readBound(MANAGER.offset(4, 0, 0)).status());
        assertEquals(0, source.samples);
        assertEquals(SFMValue.of("allowed"), surface.readBound(allowed).value().orElseThrow());
        assertEquals(1, source.samples);
        gate.revoke(program, READ_BOUND);
        assertEquals(UNAVAILABLE_AWAITING_CONSENT, surface.readBound(allowed).status());
        gate.request(program, READ_BOUND);
        gate.decide(program, READ_BOUND, DENY);
        assertEquals(UNAVAILABLE_DENIED_BY_USER, surface.readBound(allowed).status());
        assertEquals(1, source.samples);
    }

    @Test
    void perPositionRevisionInvalidatesOnlyChangedStateAndUnloadedNeverReturnsCachedAir() {
        ClientProgramIdentity program = identity(Set.of(EXECUTE, READ_LOADED));
        ClientProgramConsentGate gate = new ClientProgramConsentGate();
        approve(gate, program, EXECUTE);
        approve(gate, program, READ_LOADED);
        FakeSource source = new FakeSource();
        BlockPos first = MANAGER.east();
        BlockPos second = MANAGER.west();
        source.put(first, 1, SFMValue.of("red"));
        source.put(second, 1, SFMValue.of("blue"));
        ClientProgramBlockReadSurface<FakeState> surface = new ClientProgramBlockReadSurface<>(
                program, gate, ALLOW, source, Set.of(), 3
        );

        SFMValue red = surface.readLoaded(first).value().orElseThrow();
        SFMValue blue = surface.readLoaded(second).value().orElseThrow();
        assertSame(red, surface.readLoaded(first).value().orElseThrow());
        assertEquals(2, source.projections);
        source.put(first, 2, SFMValue.of("green"));
        assertEquals(SFMValue.of("green"), surface.readLoaded(first).value().orElseThrow());
        assertSame(blue, surface.readLoaded(second).value().orElseThrow());
        assertEquals(3, source.projections);

        source.loaded.remove(first);
        assertEquals(UNKNOWN_UNLOADED, surface.readLoaded(first).status());
        assertEquals(1, surface.cachedPositions());
        source.loaded.add(first);
        assertEquals(SFMValue.of("green"), surface.readLoaded(first).value().orElseThrow());
        assertEquals(4, source.projections);
    }

    @Test
    void worldSwapAndPolicyRevocationCannotReuseCachedValue() {
        ClientProgramIdentity program = identity(Set.of(EXECUTE, READ_LOADED));
        ClientProgramConsentGate gate = new ClientProgramConsentGate();
        approve(gate, program, EXECUTE);
        approve(gate, program, READ_LOADED);
        FakeSource source = new FakeSource();
        source.put(MANAGER, 1, SFMValue.of(15));
        final boolean[] policyBlocked = {false};
        ClientProgramBlockReadSurface<FakeState> surface = new ClientProgramBlockReadSurface<>(
                program, gate,
                (identity, capability) -> policyBlocked[0] ? List.of("blocked") : List.of(),
                source, Set.of(), 2
        );
        assertEquals(SFMValue.of(15), surface.readLoaded(MANAGER).value().orElseThrow());
        policyBlocked[0] = true;
        assertEquals(UNAVAILABLE_BLOCKED_BY_POLICY, surface.readLoaded(MANAGER).status());
        policyBlocked[0] = false;
        source.world = new Object();
        assertEquals(UNAVAILABLE_WORLD_CHANGED, surface.readLoaded(MANAGER).status());
        assertEquals(0, surface.cachedPositions());
        assertFalse(surface.readLoaded(MANAGER).value().isPresent());
    }

    @Test
    void perFrameBudgetAndRadiusAreBounded() {
        ClientProgramIdentity program = identity(Set.of(EXECUTE, READ_LOADED));
        ClientProgramConsentGate gate = new ClientProgramConsentGate();
        approve(gate, program, EXECUTE);
        approve(gate, program, READ_LOADED);
        FakeSource source = new FakeSource();
        ClientProgramBlockReadSurface<FakeState> surface = new ClientProgramBlockReadSurface<>(
                program, gate, ALLOW, source, Set.of(), 32
        );
        for (int i = 0; i < ClientProgramBlockReadSurface.MAX_UNIQUE_READS_PER_FRAME; i++) {
            BlockPos position = MANAGER.offset(i % 16, 0, i / 16);
            source.put(position, 1, SFMValue.of(i));
            assertEquals(SFMValue.of(i), surface.readLoaded(position).value().orElseThrow());
        }
        BlockPos extra = MANAGER.offset(0, 0, 8);
        source.put(extra, 1, SFMValue.of(128));
        assertEquals(UNAVAILABLE_BUDGET, surface.readLoaded(extra).status());
        assertEquals(SFMValue.of(0), surface.readLoaded(MANAGER).value().orElseThrow());
        surface.beginFrame();
        assertEquals(SFMValue.of(128), surface.readLoaded(extra).value().orElseThrow());
        assertEquals(UNAVAILABLE_OUT_OF_SCOPE, surface.readLoaded(MANAGER.offset(33, 0, 0)).status());
        assertThrows(IllegalArgumentException.class, () -> new ClientProgramBlockReadSurface<>(
                program, gate, ALLOW, source, Set.of(), ClientProgramBlockReadSurface.MAX_RADIUS + 1
        ));
    }

    @Test
    void integerPowerPropertyIsProjectedAsExactLong() {
        assertEquals(SFMValue.of(11), MinecraftClientBlockStateSource.propertyValue(11));
        assertEquals(SFMValue.of(true), MinecraftClientBlockStateSource.propertyValue(true));
        assertEquals(SFMValue.of("north"), MinecraftClientBlockStateSource.propertyValue("north"));
    }
}
