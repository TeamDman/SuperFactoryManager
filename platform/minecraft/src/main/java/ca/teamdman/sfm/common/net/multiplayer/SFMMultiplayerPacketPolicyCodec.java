package ca.teamdman.sfm.common.net.multiplayer;

import ca.teamdman.sfm.common.net.SFMPacketInventoryAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static ca.teamdman.sfm.common.net.multiplayer.SFMMultiplayerPacketProtocol.*;

/** Strict server-owned persistence format; reject the complete authority set on any malformed entry. */
public final class SFMMultiplayerPacketPolicyCodec {
    public static final int VERSION = 1;
    public static final int MAX_ENCODED_BYTES = 4 * 1024 * 1024;
    private SFMMultiplayerPacketPolicyCodec() {}

    public static byte[] encode(List<SFMMultiplayerPacketPolicy.Grant> grants) {
        Objects.requireNonNull(grants);
        require(grants.size() <= SFMMultiplayerPacketPolicy.MAX_GRANTS);
        Set<UUID> ids = new HashSet<>();
        try {
            var bytes = new ByteArrayOutputStream();
            var output = new DataOutputStream(bytes);
            output.writeInt(VERSION);
            output.writeInt(grants.size());
            for (var grant : grants) {
                require(ids.add(grant.id()));
                // One entry is bounded independently before joining the complete bounded document.
                byte[] entry = encodeGrant(grant);
                require(bytes.size() <= MAX_ENCODED_BYTES - entry.length);
                output.write(entry);
            }
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }

    public static List<SFMMultiplayerPacketPolicy.Grant> decode(byte[] bytes) {
        Objects.requireNonNull(bytes);
        require(bytes.length >= 8 && bytes.length <= MAX_ENCODED_BYTES);
        try {
            var input = new DataInputStream(new ByteArrayInputStream(bytes));
            require(input.readInt() == VERSION);
            int count = input.readInt();
            require(count >= 0 && count <= SFMMultiplayerPacketPolicy.MAX_GRANTS);
            var grants = new ArrayList<SFMMultiplayerPacketPolicy.Grant>(count);
            Set<UUID> ids = new HashSet<>();
            for (int index = 0; index < count; index++) {
                var grant = readGrant(input);
                require(ids.add(grant.id()));
                grants.add(grant);
            }
            require(input.available() == 0);
            return List.copyOf(grants);
        } catch (IOException malformed) { throw new IllegalArgumentException("Malformed multiplayer packet policy", malformed); }
    }

    private static byte[] encodeGrant(SFMMultiplayerPacketPolicy.Grant grant) throws IOException {
        var bytes = new ByteArrayOutputStream();
        var output = new DataOutputStream(bytes);
        uuid(output, grant.id());
        uuid(output, grant.player());
        switch (grant.action()) {
            case PACKET_SEND -> {
                output.writeByte(1);
                var target = ((InventoryScope) grant.scope()).target();
                identifier(output, target.dimension());
                position(output, target.position());
                output.writeBoolean(target.side().isPresent());
                if (target.side().isPresent()) string(output, target.side().orElseThrow().getName(), 8);
            }
            case INBOX_SUBSCRIBE -> {
                output.writeByte(2);
                inbox(output, (InboxScope) grant.scope());
            }
            case INBOX_DELIVER -> {
                output.writeByte(3);
                var scope = (DeliveryScope) grant.scope();
                manager(output, scope.publisher());
                inbox(output, scope.inbox());
            }
        }
        output.writeBoolean(grant.program().isPresent());
        if (grant.program().isPresent()) {
            var claim = grant.program().orElseThrow();
            manager(output, claim.manager());
            uuid(output, claim.incarnation());
            output.writeLong(claim.revision());
            string(output, claim.sourceSha256(), 64);
            string(output, claim.bindingsSha256(), 64);
        }
        output.writeBoolean(grant.expiresAtEpochMillis().isPresent());
        if (grant.expiresAtEpochMillis().isPresent()) output.writeLong(grant.expiresAtEpochMillis().getAsLong());
        require(bytes.size() <= 2048);
        return bytes.toByteArray();
    }

    private static SFMMultiplayerPacketPolicy.Grant readGrant(DataInputStream input) throws IOException {
        UUID id = uuid(input), player = uuid(input);
        int actionTag = input.readUnsignedByte();
        Action action;
        Scope scope;
        switch (actionTag) {
            case 1 -> {
                action = Action.PACKET_SEND;
                ResourceLocation dimension = identifier(input);
                BlockPos position = position(input);
                Optional<Direction> side = Optional.empty();
                if (bool(input)) {
                    String name = string(input, 8);
                    Direction direction = Direction.byName(name);
                    require(direction != null && direction.getName().equals(name));
                    side = Optional.of(direction);
                }
                scope = new InventoryScope(new SFMPacketInventoryAddress(dimension, position, side));
            }
            case 2 -> { action = Action.INBOX_SUBSCRIBE; scope = inbox(input); }
            case 3 -> { action = Action.INBOX_DELIVER; scope = new DeliveryScope(manager(input), inbox(input)); }
            default -> throw new IllegalArgumentException("Unsupported policy action");
        }
        Optional<ProgramClaim> program = Optional.empty();
        if (bool(input)) program = Optional.of(new ProgramClaim(manager(input), uuid(input), input.readLong(),
                string(input, 64), string(input, 64)));
        OptionalLong expiry = bool(input) ? OptionalLong.of(input.readLong()) : OptionalLong.empty();
        return new SFMMultiplayerPacketPolicy.Grant(id, player, action, scope, program, expiry);
    }

    private static boolean bool(DataInputStream input) throws IOException {
        int value = input.readUnsignedByte();
        require(value == 0 || value == 1);
        return value == 1;
    }
    private static void uuid(DataOutputStream output, UUID value) throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }
    private static UUID uuid(DataInputStream input) throws IOException { return new UUID(input.readLong(), input.readLong()); }
    private static void position(DataOutputStream output, BlockPos value) throws IOException {
        // Network block positions use a packed long. Reject coordinates that would alias a different target.
        require(BlockPos.of(value.asLong()).equals(value));
        output.writeInt(value.getX()); output.writeInt(value.getY()); output.writeInt(value.getZ());
    }
    private static BlockPos position(DataInputStream input) throws IOException {
        var result = new BlockPos(input.readInt(), input.readInt(), input.readInt());
        require(BlockPos.of(result.asLong()).equals(result));
        return result;
    }
    private static void manager(DataOutputStream output, ManagerAddress value) throws IOException {
        identifier(output, value.dimension()); position(output, value.position());
    }
    private static ManagerAddress manager(DataInputStream input) throws IOException {
        return new ManagerAddress(identifier(input), position(input));
    }
    private static void inbox(DataOutputStream output, InboxScope value) throws IOException {
        identifier(output, value.dimension()); identifier(output, value.channel());
    }
    private static InboxScope inbox(DataInputStream input) throws IOException { return new InboxScope(identifier(input), identifier(input)); }
    private static void identifier(DataOutputStream output, ResourceLocation value) throws IOException {
        string(output, value.toString(), MAX_IDENTIFIER_CHARACTERS);
    }
    private static ResourceLocation identifier(DataInputStream input) throws IOException {
        String text = string(input, MAX_IDENTIFIER_CHARACTERS);
        var value = new ResourceLocation(text);
        require(value.toString().equals(text));
        return value;
    }
    private static void string(DataOutputStream output, String value, int maximum) throws IOException {
        require(value.length() <= maximum);
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        require(bytes.length <= maximum);
        output.writeInt(bytes.length);
        output.write(bytes);
    }
    private static String string(DataInputStream input, int maximum) throws IOException {
        int length = input.readInt();
        require(length >= 0 && length <= maximum && length <= input.available());
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(input.readNBytes(length))).toString();
    }
    private static void require(boolean value) { if (!value) throw new IllegalArgumentException("Invalid multiplayer packet policy encoding"); }
}
