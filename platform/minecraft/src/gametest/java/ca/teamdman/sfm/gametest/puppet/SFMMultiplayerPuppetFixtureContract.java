package ca.teamdman.sfm.gametest.puppet;

import ca.teamdman.sfm.common.net.SFMPacketInventoryAddress;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.value.SFMValueJsonCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Optional;

/** Only synthetic addresses used by the explicitly selected isolated-world fixture. */
public final class SFMMultiplayerPuppetFixtureContract {
    public static final ResourceLocation DIMENSION = new ResourceLocation("minecraft", "overworld");
    public static final ResourceLocation CHANNEL = new ResourceLocation("sfm", "multiplayer_puppet");
    public static final BlockPos PUBLISHER = new BlockPos(0, 81, 2);
    public static final BlockPos MAILBOX = new BlockPos(2, 81, 2);
    public static final BlockPos ARCHIVE = new BlockPos(4, 81, 2);
    public static final BlockPos DENIED = new BlockPos(6, 81, 2);
    public static final BlockPos DISPLAY = new BlockPos(8, 81, 2);
    public static final BlockPos CLIENT_MANAGER = new BlockPos(8, 81, 4);
    public static final SFMPacketInventoryAddress TARGET = new SFMPacketInventoryAddress(DIMENSION, MAILBOX, Optional.empty());
    public static final SFMValue HUMAN_VALUE = SFMValue.object(Map.of("schema", SFMValue.of("sfm:multiplayer_puppet@1"),
            "producer", SFMValue.of("registered_action")));
    public static final SFMValue PROGRAM_VALUE = SFMValue.object(Map.of("schema", SFMValue.of("sfm:multiplayer_puppet@1"),
            "producer", SFMValue.of("client_manager")));

    private SFMMultiplayerPuppetFixtureContract() { }

    public static String clientSource() {
        SFMValue input = SFMValue.object(Map.of("dimension", SFMValue.of(DIMENSION.toString()),
                "x", SFMValue.of(MAILBOX.getX()), "y", SFMValue.of(MAILBOX.getY()), "z", SFMValue.of(MAILBOX.getZ()),
                "value", PROGRAM_VALUE));
        return "CLIENT BTW\nEVERY FRAME FOR displays AS display DO\n"
                + json("readRequest", SFMValue.object(Map.of("channel", SFMValue.of(CHANNEL.toString()), "mode", SFMValue.of("latest"))))
                + "LET readResponse BE INVOKE sfm:client_inbox/read WITH readRequest\n"
                + "LET readResult BE FIELD \"result\" OF readResponse\n"
                + "LET receivedValue BE FIELD \"value\" OF readResult\n"
                + json("sendRequest", input)
                + "LET sendResponse BE INVOKE sfm:packet/send WITH sendRequest\n"
                + "LET sendGateway BE FIELD \"status\" OF sendResponse\nEND";
    }

    private static String json(String variable, SFMValue value) {
        return "LET " + variable + " BE JSON \"" + SFMValueJsonCodec.encode(value).replace("\\", "\\\\").replace("\"", "\\\"") + "\"\n";
    }
}
