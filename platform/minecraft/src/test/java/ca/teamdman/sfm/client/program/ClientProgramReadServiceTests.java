package ca.teamdman.sfm.client.program;

import ca.teamdman.sfm.client.action.SFMClientActionDescriptor;
import ca.teamdman.sfm.client.action.SFMClientProgramReadAction;
import ca.teamdman.sfm.client.net.SFMClientInbox;
import ca.teamdman.sfm.common.net.SFMClientInboxAddress;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.value.SFMValueSchema;
import ca.teamdman.sfml.ast.ProgramExecutionSide;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.*;

import static ca.teamdman.sfm.client.program.ClientProgramBlockReadSurface.READ_BOUND;
import static ca.teamdman.sfm.client.program.ClientProgramBlockReadSurface.READ_LOADED;
import static ca.teamdman.sfm.client.program.ClientProgramConsentGate.EXECUTE;
import static ca.teamdman.sfm.client.program.ClientProgramInboxReadSurface.READ;
import static org.junit.jupiter.api.Assertions.*;

class ClientProgramReadServiceTests {
    private static final ResourceLocation DIMENSION = new ResourceLocation("minecraft", "overworld");
    private static final ResourceLocation CHANNEL = new ResourceLocation("sfm", "read_fixture");
    private static final BlockPos MANAGER = new BlockPos(1, 64, 1);
    private static final BlockPos FIRST = MANAGER.east();
    private static final BlockPos SECOND = MANAGER.west();

    @Test void unchangedBlockStatesReuseProjectionAndOnlyChangedOrUnloadedPositionInvalidates() {
        Fixture fixture = new Fixture();
        var first = field(fixture.block(FIRST), "value");
        var second = field(fixture.block(SECOND), "value");
        fixture.service.maintain(); // A hidden display still maintains authority, but must retain its cache.
        assertSame(first, field(fixture.block(FIRST), "value"));
        assertEquals(2, fixture.observation().blockProjections());
        fixture.source.states.put(FIRST, 7);
        assertEquals(SFMValue.of(7), field(field(field(fixture.block(FIRST), "value"), "properties"), "power"));
        assertSame(second, field(fixture.block(SECOND), "value"));
        assertEquals(3, fixture.observation().blockProjections());
        fixture.source.states.remove(FIRST);
        assertEquals("unknown_unloaded", status(fixture.block(FIRST)));
        assertEquals(1, fixture.observation().cachedPositions());
        fixture.source.states.put(FIRST, 7);
        fixture.block(FIRST);
        assertEquals(4, fixture.observation().blockProjections());
    }

    @Test void exactTargetsAndRadiusRemainRequiredForBothReadPermissions() {
        Fixture fixture = new Fixture();
        assertEquals("unavailable_out_of_scope", status(fixture.block(MANAGER.north())));
        assertEquals("unavailable_out_of_scope", status(fixture.service.block(fixture.identity, FIRST, true, 1)));
        assertEquals("value", status(fixture.service.block(fixture.identity, MANAGER.south(), true, 1)));
        assertEquals("unavailable_out_of_scope", status(fixture.block(new BlockPos(Integer.MAX_VALUE, 64, 1))));
        assertEquals("unavailable_out_of_scope", status(fixture.service.latest(fixture.identity, new ResourceLocation("sfm", "other"))));
        assertEquals(0, fixture.observation().subscriptions());
    }

    @Test void latestModeCachesValuesAndExplicitlyReportsRetainedStreamLoss() {
        Fixture fixture = new Fixture();
        assertEquals("empty", status(fixture.latest()));
        for (int i = 1; i <= 70; i++) fixture.append(i);
        SFMValue value = fixture.latest();
        assertEquals(SFMValue.of(70), field(value, "value"));
        assertEquals(SFMValue.of(70), field(value, "sequence"));
        assertEquals(SFMValue.of("evicted_gap"), field(value, "continuity"));
        assertEquals(SFMValue.of(7), field(value, "oldestSequence"));
        assertEquals(SFMValue.of(false), field(value, "hasMore"));
        assertSame(value, fixture.latest());
        assertEquals(2, fixture.observation().inboxProjections());
        assertEquals(1, fixture.access.opened);
        assertEquals(fixture.recipient, fixture.access.lastAddress.recipient());
        fixture.append(71);
        assertEquals(SFMValue.of("contiguous"), field(fixture.latest(), "continuity"));
        assertEquals(SFMValue.of(71), field(fixture.latest(), "value"));
    }

    @Test void noSessionNeverReturnsStalePayloadAndReconnectReportsSessionChange() {
        Fixture fixture = new Fixture();
        fixture.latest(); fixture.append(1); fixture.latest();
        fixture.access.inbox.endSession();
        assertEquals("unavailable_no_session", status(fixture.latest()));
        assertEquals(SFMValue.nullValue(), field(fixture.latest(), "value"));
        fixture.access.restart();
        var reconnected = fixture.latest();
        assertEquals("empty", status(reconnected));
        assertEquals(SFMValue.of("session_changed"), field(reconnected, "continuity"));
        fixture.append(2);
        assertEquals(SFMValue.of(2), field(fixture.latest(), "value"));
    }

    @Test void revocationAndPolicyStopReleaseHiddenSubscriptionsWithoutAnotherRead() {
        Fixture fixture = new Fixture();
        fixture.block(FIRST); fixture.latest(); fixture.append(1); fixture.latest();
        fixture.gate.revoke(fixture.identity, READ);
        fixture.gate.revoke(fixture.identity, READ_BOUND);
        fixture.service.maintain();
        assertEquals(1, fixture.access.closed);
        assertEquals(0, fixture.observation().subscriptions());
        assertEquals(0, fixture.observation().cachedPositions());
        assertEquals("unavailable_awaiting_consent", status(fixture.latest()));
        fixture.approve(READ); fixture.latest();
        assertEquals(2, fixture.access.opened);
        fixture.blocked = true;
        fixture.service.maintain();
        assertEquals(0, fixture.service.activeSessions());
        assertEquals(2, fixture.access.closed);
    }

    @Test void staleIdentityRemovalWorldChangeAndPlayerChangeReleaseWithoutRendering() {
        for (int change = 0; change < 4; change++) {
            Fixture fixture = new Fixture();
            fixture.latest(); fixture.block(FIRST);
            if (change == 0) fixture.live = false; // source, bindings, or manager removal
            if (change == 1) fixture.source.world = new Object();
            if (change == 2) fixture.source.active = false;
            if (change == 3) fixture.currentRecipient = UUID.randomUUID();
            fixture.service.maintain();
            assertEquals(0, fixture.service.activeSessions());
            assertEquals(1, fixture.access.closed);
            if (change != 1) assertEquals("unavailable_no_session", status(fixture.latest()));
        }
    }

    @Test void sessionCapacityFailsClosedWithoutEvictingExistingReaders() {
        Fixture fixture = new Fixture();
        fixture.block(FIRST);
        for (int i = 1; i < ClientProgramReadService.MAX_SESSIONS; i++) {
            var identity = fixture.identity("source-" + i);
            fixture.approve(identity, EXECUTE); fixture.approve(identity, READ_BOUND);
            assertEquals("value", status(fixture.service.block(identity, FIRST, false, 1)));
        }
        // Free a consent-record slot without silently evicting its still-retained read session.
        fixture.gate.store().forget(fixture.identity);
        var overflow = fixture.identity("overflow");
        fixture.approve(overflow, EXECUTE); fixture.approve(overflow, READ_BOUND);
        assertEquals("unavailable_no_session", status(fixture.service.block(overflow, FIRST, false, 1)));
        assertEquals(ClientProgramReadService.MAX_SESSIONS, fixture.service.activeSessions());
        fixture.service.maintain();
        assertEquals("value", status(fixture.service.block(overflow, FIRST, false, 1)));
        fixture.service.close(); fixture.service.close();
        assertEquals(0, fixture.service.activeSessions());
    }

    @Test void machineContractsUseExactTypedScopesLocalReadAndNoRecipientOrDimensionInput() {
        var block = new SFMClientProgramReadAction(false, (identity, input) -> SFMValue.nullValue());
        var inbox = new SFMClientProgramReadAction(true, (identity, input) -> SFMValue.nullValue());
        var blockDescriptor = block.programmaticDescriptor().orElseThrow();
        var inboxDescriptor = inbox.programmaticDescriptor().orElseThrow();
        assertEquals(SFMClientActionDescriptor.CostClass.LOCAL_READ, blockDescriptor.costClass());
        assertEquals(EXECUTE, blockDescriptor.controlPermission());
        assertSame(blockDescriptor, block.programmaticDescriptor().orElseThrow());
        var input = SFMValueSchema.decodeActionJson("{\"x\":1,\"y\":64,\"z\":2,\"scope\":\"loaded\"}");
        var accepted = (SFMClientActionDescriptor.InputCheck.Accepted) blockDescriptor.checkInput(input);
        assertEquals(READ_LOADED, accepted.dataScopes().get(0).permission());
        assertEquals(SFMValueSchema.decodeActionJson("{\"x\":1,\"y\":64,\"z\":2}"), accepted.dataScopes().get(0).subject());
        for (String invalid : List.of("{\"x\":1.0,\"y\":64,\"z\":2,\"scope\":\"bound\"}",
                "{\"x\":2147483648,\"y\":64,\"z\":2,\"scope\":\"bound\"}",
                "{\"x\":1,\"y\":64,\"z\":2,\"scope\":\"bound\",\"dimension\":\"minecraft:the_nether\"}")) {
            assertInstanceOf(SFMClientActionDescriptor.InputCheck.Rejected.class, blockDescriptor.checkInput(SFMValueSchema.decodeActionJson(invalid)));
        }
        for (String invalid : List.of("{\"channel\":\"INVALID\",\"mode\":\"latest\"}",
                "{\"channel\":\"sfm:test\",\"mode\":\"latest\",\"recipient\":\"someone_else\"}")) {
            assertInstanceOf(SFMClientActionDescriptor.InputCheck.Rejected.class, inboxDescriptor.checkInput(SFMValueSchema.decodeActionJson(invalid)));
        }
        Fixture fixture = new Fixture();
        assertTrue(blockDescriptor.checkResult(fixture.block(FIRST)).isEmpty());
        assertTrue(inboxDescriptor.checkResult(fixture.latest()).isEmpty());
        fixture.append(1);
        assertTrue(inboxDescriptor.checkResult(fixture.latest()).isEmpty());
    }

    private static SFMValue field(SFMValue value, String key) { return ((SFMValue.ObjectValue) value).fields().get(key); }
    private static String status(SFMValue value) { return ((SFMValue.StringValue) field(value, "status")).value(); }

    private static final class Source implements ClientProgramBlockReadSurface.Source<Integer> {
        Object world = new Object();
        boolean active = true;
        final Map<BlockPos, Integer> states = new HashMap<>(Map.of(FIRST, 1, SECOND, 2, MANAGER.south(), 3));
        @Override public Object worldIdentity() { return world; }
        @Override public ResourceLocation dimension() { return DIMENSION; }
        @Override public boolean isActive() { return active; }
        @Override public boolean isLoaded(BlockPos position) { return states.containsKey(position); }
        @Override public Optional<ClientProgramBlockReadSurface.Sample<Integer>> sample(BlockPos position) {
            return Optional.ofNullable(states.get(position)).map(value -> new ClientProgramBlockReadSurface.Sample<>(value, value));
        }
        @Override public SFMValue project(Integer state) {
            return SFMValue.object(Map.of("schema", SFMValue.of("sfm:block_state@1"), "block", SFMValue.of("minecraft:redstone_wire"),
                    "properties", SFMValue.object(Map.of("power", SFMValue.of(state)))));
        }
    }
    private static final class Access implements ClientProgramInboxReadSurface.InboxAccess {
        final SFMClientInbox inbox = new SFMClientInbox();
        final UUID recipient;
        UUID session;
        int opened;
        int closed;
        SFMClientInboxAddress lastAddress;
        Access(UUID recipient) { this.recipient = recipient; restart(); }
        void restart() { session = UUID.randomUUID(); inbox.beginSession(session, recipient, DIMENSION); }
        @Override public Optional<ClientProgramInboxReadSurface.Subscription> subscribe(SFMClientInboxAddress address) {
            if (!address.recipient().equals(recipient) || !address.dimension().equals(DIMENSION) || !inbox.subscribe(address.channel())) {
                return Optional.empty();
            }
            lastAddress = address; opened++;
            return Optional.of(() -> { closed++; inbox.unsubscribe(address.channel()); });
        }
        @Override public Optional<SFMClientInbox.Page> page(ResourceLocation channel, Optional<SFMClientInbox.Cursor> cursor, int limit) {
            return inbox.page(channel, cursor, limit);
        }
    }
    private static final class Fixture {
        final UUID recipient = UUID.randomUUID();
        UUID currentRecipient = recipient;
        final ClientProgramWorldIdentity world = ClientProgramWorldIdentity.integrated(UUID.randomUUID());
        final ClientProgramConsentGate gate = new ClientProgramConsentGate();
        final ClientProgramIdentity identity = identity("initial");
        final Source source = new Source();
        final Access access = new Access(recipient);
        boolean live = true;
        boolean blocked;
        final ClientProgramReadService<Integer> service = new ClientProgramReadService<>(gate,
                (identity, capability) -> blocked ? List.of("fixture_policy") : List.of(), new ClientProgramReadService.Environment<>() {
            @Override public Optional<ClientProgramReadService.Context<Integer>> open(ClientProgramIdentity identity) {
                return live ? Optional.of(new ClientProgramReadService.Context<>(source, recipient,
                        Set.of(FIRST, SECOND, new BlockPos(Integer.MAX_VALUE, 64, 1)), Set.of(MANAGER.south()), Set.of(CHANNEL), access)) : Optional.empty();
            }
            @Override public boolean isCurrent(ClientProgramIdentity identity, ClientProgramReadService.Context<Integer> context) {
                return live && currentRecipient.equals(context.recipient());
            }
        });
        Fixture() { for (var capability : identity.requestedCapabilities()) approve(capability); }
        ClientProgramIdentity identity(String source) {
            return ClientProgramIdentity.fromStoredSource(source, ProgramExecutionSide.CLIENT, world, DIMENSION,
                    MANAGER, ClientProgramIdentity.CLIENT_MANAGER_RUNTIME, Set.of(EXECUTE, READ_BOUND, READ_LOADED, READ));
        }
        void approve(ResourceLocation capability) { approve(identity, capability); }
        void approve(ClientProgramIdentity program, ResourceLocation capability) {
            gate.request(program, capability); gate.decide(program, capability, ClientProgramConsentGate.Decision.APPROVE);
        }
        SFMValue block(BlockPos position) { return service.block(identity, position, false, 1); }
        SFMValue latest() { return service.latest(identity, CHANNEL); }
        void append(int value) { assertTrue(access.inbox.append(access.session,
                new SFMClientInboxAddress(recipient, DIMENSION, CHANNEL), SFMValue.of(value))); }
        ClientProgramReadService.Observation observation() { return service.observation(identity).orElseThrow(); }
    }
}
