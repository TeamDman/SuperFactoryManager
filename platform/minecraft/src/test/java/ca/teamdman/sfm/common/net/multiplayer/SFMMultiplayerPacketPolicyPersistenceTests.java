package ca.teamdman.sfm.common.net.multiplayer;

import ca.teamdman.sfm.common.net.SFMPacketInventoryAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.*;

import static ca.teamdman.sfm.common.net.multiplayer.SFMMultiplayerPacketProtocol.*;
import static org.junit.jupiter.api.Assertions.*;

class SFMMultiplayerPacketPolicyPersistenceTests {
    private static final UUID PLAYER = new UUID(2, 3);
    private static final ResourceLocation DIMENSION = new ResourceLocation("minecraft:overworld");
    private static final ResourceLocation CHANNEL = new ResourceLocation("sfm:fixture");
    private static final BlockPos POSITION = new BlockPos(1, 64, -3);
    private static final ManagerAddress MANAGER = new ManagerAddress(DIMENSION, POSITION);
    private static final InboxScope INBOX = new InboxScope(DIMENSION, CHANNEL);
    private static SFMMultiplayerPacketPolicy.Grant grant(long id, Action action, Scope scope) {
        return new SFMMultiplayerPacketPolicy.Grant(new UUID(0, id), PLAYER, action, scope, Optional.empty(), OptionalLong.of(1000));
    }
    private static InventoryScope inventory(Optional<Direction> side) {
        return new InventoryScope(new SFMPacketInventoryAddress(DIMENSION, POSITION, side));
    }

    @Test void strictCodecRoundTripsEveryExactScopeAndOptionalProgramWithoutWidening() {
        var claim = new ProgramClaim(MANAGER, new UUID(5, 6), 42, "a".repeat(64), "b".repeat(64));
        var exactProgram = new SFMMultiplayerPacketPolicy.Grant(new UUID(0, 4), PLAYER, Action.PACKET_SEND,
                inventory(Optional.of(Direction.NORTH)), Optional.of(claim), OptionalLong.of(500));
        var grants = List.of(grant(1, Action.PACKET_SEND, inventory(Optional.empty())),
                grant(2, Action.INBOX_SUBSCRIBE, INBOX),
                grant(3, Action.INBOX_DELIVER, new DeliveryScope(MANAGER, INBOX)), exactProgram);
        var decoded = SFMMultiplayerPacketPolicyCodec.decode(SFMMultiplayerPacketPolicyCodec.encode(grants));
        assertEquals(grants, decoded);
        assertThrows(UnsupportedOperationException.class, decoded::clear);
        var policy = new SFMMultiplayerPacketPolicy();
        decoded.forEach(policy::grant);
        assertTrue(policy.allows(PLAYER, Action.PACKET_SEND, inventory(Optional.empty()), Optional.empty(), 999));
        assertFalse(policy.allows(PLAYER, Action.PACKET_SEND, inventory(Optional.of(Direction.SOUTH)), Optional.empty(), 999));
        assertFalse(policy.allows(new UUID(2, 4), Action.INBOX_SUBSCRIBE, INBOX, Optional.empty(), 1));
        assertFalse(policy.allows(PLAYER, Action.INBOX_SUBSCRIBE,
                new InboxScope(DIMENSION, new ResourceLocation("sfm:other")), Optional.empty(), 1));
        assertFalse(policy.allows(PLAYER, Action.PACKET_SEND, inventory(Optional.empty()), Optional.empty(), 1000));
        assertTrue(policy.allows(PLAYER, Action.PACKET_SEND, inventory(Optional.of(Direction.NORTH)), Optional.of(claim), 499));
        assertFalse(policy.allows(PLAYER, Action.PACKET_SEND, inventory(Optional.of(Direction.NORTH)), Optional.empty(), 499));
        assertFalse(policy.allows(PLAYER, Action.PACKET_SEND, inventory(Optional.of(Direction.NORTH)), Optional.of(claim), 500));
    }

    @Test void rejectsDuplicateIdsRatherThanOverwritingTheFirstAuthority() {
        var value = grant(1, Action.INBOX_SUBSCRIBE, INBOX);
        assertThrows(IllegalArgumentException.class, () -> SFMMultiplayerPacketPolicyCodec.encode(List.of(value, value)));
        byte[] one = SFMMultiplayerPacketPolicyCodec.encode(List.of(value));
        int entryBytes = one.length - 8;
        byte[] duplicate = new byte[8 + entryBytes * 2];
        System.arraycopy(one, 0, duplicate, 0, one.length);
        ByteBuffer.wrap(duplicate).putInt(4, 2);
        System.arraycopy(one, 8, duplicate, one.length, entryBytes);
        assertThrows(IllegalArgumentException.class, () -> SFMMultiplayerPacketPolicyCodec.decode(duplicate));
    }

    @Test void unsupportedVersionActionTrailingBytesAndTruncationRejectTheWholeDocument() {
        byte[] valid = SFMMultiplayerPacketPolicyCodec.encode(List.of(grant(1, Action.INBOX_SUBSCRIBE, INBOX)));
        byte[] future = valid.clone();
        ByteBuffer.wrap(future).putInt(0, 2);
        assertThrows(IllegalArgumentException.class, () -> SFMMultiplayerPacketPolicyCodec.decode(future));
        byte[] unknownAction = valid.clone();
        unknownAction[8 + 16 + 16] = 99;
        assertThrows(IllegalArgumentException.class, () -> SFMMultiplayerPacketPolicyCodec.decode(unknownAction));
        assertThrows(IllegalArgumentException.class, () -> SFMMultiplayerPacketPolicyCodec.decode(Arrays.copyOf(valid, valid.length + 1)));
        for (int length : List.of(0, 7, 8, valid.length - 1)) {
            assertThrows(IllegalArgumentException.class, () -> SFMMultiplayerPacketPolicyCodec.decode(Arrays.copyOf(valid, length)));
        }
    }

    @Test void boundsCountsBytesAndNoncanonicalBooleanBeforeAuthorityConstruction() {
        assertThrows(IllegalArgumentException.class, () -> SFMMultiplayerPacketPolicyCodec.decode(
                new byte[SFMMultiplayerPacketPolicyCodec.MAX_ENCODED_BYTES + 1]));
        byte[] count = ByteBuffer.allocate(8).putInt(1).putInt(SFMMultiplayerPacketPolicy.MAX_GRANTS + 1).array();
        assertThrows(IllegalArgumentException.class, () -> SFMMultiplayerPacketPolicyCodec.decode(count));
        var value = grant(1, Action.INBOX_SUBSCRIBE, INBOX);
        assertThrows(IllegalArgumentException.class, () -> SFMMultiplayerPacketPolicyCodec.encode(
                Collections.nCopies(SFMMultiplayerPacketPolicy.MAX_GRANTS + 1, value)));
        byte[] invalidBoolean = SFMMultiplayerPacketPolicyCodec.encode(List.of(value));
        invalidBoolean[invalidBoolean.length - 9] = 2;
        assertThrows(IllegalArgumentException.class, () -> SFMMultiplayerPacketPolicyCodec.decode(invalidBoolean));
    }

    @Test void rejectsCoordinatePackingAliasesInsteadOfGrantingAnotherInventory() {
        var alias = new InventoryScope(new SFMPacketInventoryAddress(DIMENSION,
                new BlockPos(67_108_864, 64, 0), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> SFMMultiplayerPacketPolicyCodec.encode(
                List.of(grant(1, Action.PACKET_SEND, alias))));
    }

    @Test void savedDataKeepsOneLivePolicyAndMarksOnlySuccessfulMutationsDirty() {
        var owner = new SFMMultiplayerPacketPolicySavedData();
        var canonical = owner.policy();
        assertTrue(canonical.snapshot().isEmpty());
        var grant = grant(1, Action.INBOX_SUBSCRIBE, INBOX);
        owner.setDirty(false);
        owner.grant(grant);
        assertTrue(owner.isDirty());
        assertSame(canonical, owner.policy());
        assertTrue(canonical.allows(PLAYER, Action.INBOX_SUBSCRIBE, INBOX, Optional.empty(), 1));
        var loaded = SFMMultiplayerPacketPolicySavedData.load(owner.save(new CompoundTag()));
        assertFalse(loaded.quarantined());
        assertEquals(List.of(grant), loaded.policy().snapshot());
        assertFalse(loaded.isDirty());
        owner.setDirty(false);
        assertFalse(owner.revoke(new UUID(0, 99)));
        assertFalse(owner.isDirty());
        assertTrue(owner.revoke(grant.id()));
        assertTrue(owner.isDirty());
        assertFalse(canonical.allows(PLAYER, Action.INBOX_SUBSCRIBE, INBOX, Optional.empty(), 1),
                "The already-held transport policy observes revocation immediately");
    }

    @Test void duplicateIdCannotSilentlyChangePlayerScopeOrLifetime() {
        var owner = new SFMMultiplayerPacketPolicySavedData();
        var first = grant(1, Action.INBOX_SUBSCRIBE, INBOX);
        owner.grant(first);
        owner.setDirty(false);
        var replacement = new SFMMultiplayerPacketPolicy.Grant(first.id(), new UUID(8, 8), first.action(),
                first.scope(), Optional.empty(), OptionalLong.empty());
        assertThrows(IllegalArgumentException.class, () -> owner.grant(replacement));
        assertEquals(List.of(first), owner.policy().snapshot());
        assertFalse(owner.isDirty());
    }

    @Test void corruptStoreIsQuarantinedUntilExplicitEmptyRecoveryAndNeverPartiallyLoaded() {
        var tag = new CompoundTag();
        tag.putInt("schema", 1);
        byte[] bad = SFMMultiplayerPacketPolicyCodec.encode(List.of(grant(1, Action.INBOX_SUBSCRIBE, INBOX)));
        tag.putByteArray("grants", Arrays.copyOf(bad, bad.length - 1));
        var data = SFMMultiplayerPacketPolicySavedData.load(tag);
        var held = data.policy();
        assertTrue(data.quarantined());
        assertFalse(data.isDirty(), "Do not overwrite the malformed evidence on an unrelated world save");
        assertTrue(held.snapshot().isEmpty());
        assertFalse(data.diagnostic().isBlank());
        assertThrows(IllegalStateException.class, () -> data.grant(grant(2, Action.INBOX_SUBSCRIBE, INBOX)));
        assertThrows(IllegalStateException.class, () -> data.save(new CompoundTag()));
        assertTrue(data.recoverEmpty());
        assertTrue(data.isDirty());
        assertFalse(data.quarantined());
        assertSame(held, data.policy());
        assertTrue(data.policy().snapshot().isEmpty());
        assertFalse(data.recoverEmpty(), "Recovery never clears a valid policy");
        data.grant(grant(2, Action.INBOX_SUBSCRIBE, INBOX));
        assertEquals(1, held.snapshot().size());
    }

    @Test void missingWrongTypedAndFutureEnvelopeFieldsNeverBecomeAnEmptyButWritablePolicy() {
        var empty = new SFMMultiplayerPacketPolicySavedData().save(new CompoundTag());
        for (int variant = 0; variant < 4; variant++) {
            var invalid = empty.copy();
            switch (variant) {
                case 0 -> invalid.remove("schema");
                case 1 -> invalid.putInt("schema", 2);
                case 2 -> invalid.putString("grants", "not bytes");
                case 3 -> invalid.putBoolean("all_players", true);
            }
            var data = SFMMultiplayerPacketPolicySavedData.load(invalid);
            assertTrue(data.quarantined());
            assertTrue(data.policy().snapshot().isEmpty());
        }
    }
}
