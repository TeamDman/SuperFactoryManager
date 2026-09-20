package ca.teamdman.sfm.common.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SFMPacketPolicyCommandTests {
    private static final String PLAYER = "00000000-0000-0000-0000-000000000003";
    private static CommandSourceStack source(int permission) {
        return new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, permission,
                "packet-policy-test", Component.literal("packet policy test"), null, null);
    }
    private static boolean complete(String text, int permission) {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        dispatcher.register(Commands.literal("sfm").then(SFMPacketPolicyCommand.tree()));
        var result = dispatcher.parse(text, source(permission));
        return !result.getReader().canRead() && result.getExceptions().isEmpty()
                && result.getContext().getCommand() != null;
    }

    @Test void policyManagementRequiresOwnerLevelFourIncludingReadAndRecovery() {
        var node = SFMPacketPolicyCommand.tree().build();
        for (int level = 0; level < 4; level++) assertFalse(node.canUse(source(level)));
        assertTrue(node.canUse(source(4)));
        assertTrue(complete("sfm packet_policy list", 4));
        assertFalse(complete("sfm packet_policy list", 3));
        assertTrue(complete("sfm packet_policy recover_empty", 4));
        assertFalse(complete("sfm packet_policy recover_empty", 3));
    }

    @Test void grantsNameExactOfflineUuidAndExactInventoryInboxOrPublisherAddress() {
        assertTrue(complete("sfm packet_policy grant inventory " + PLAYER + " minecraft:overworld 1 64 -3 north", 4));
        assertTrue(complete("sfm packet_policy grant inventory " + PLAYER + " minecraft:overworld 1 64 -3 unsided expires_in 30", 4));
        assertTrue(complete("sfm packet_policy grant inbox " + PLAYER + " minecraft:overworld sfm:fixture", 4));
        assertTrue(complete("sfm packet_policy grant delivery " + PLAYER + " minecraft:overworld 1 64 -3 sfm:fixture expires_in 3600", 4));
        assertTrue(complete("sfm packet_policy revoke " + PLAYER, 4));
        assertTrue(complete("sfm packet_policy list 2", 4));
    }

    @Test void selectorsWildcardsRelativeCoordinatesAndUnboundedLifetimesAreNotAccepted() {
        assertFalse(complete("sfm packet_policy grant inbox @a minecraft:overworld sfm:fixture", 4));
        assertFalse(complete("sfm packet_policy grant inbox " + PLAYER + " * sfm:fixture", 4));
        assertFalse(complete("sfm packet_policy grant inventory " + PLAYER + " minecraft:overworld ~ 64 0 north", 4));
        assertFalse(complete("sfm packet_policy grant inventory " + PLAYER + " minecraft:overworld 1.5 64 0 north", 4));
        assertFalse(complete("sfm packet_policy grant inventory " + PLAYER + " minecraft:overworld 1 64 0 all", 4));
        assertFalse(complete("sfm packet_policy grant inbox " + PLAYER + " minecraft:overworld sfm:fixture expires_in 0", 4));
        assertFalse(complete("sfm packet_policy grant inbox " + PLAYER + " minecraft:overworld sfm:fixture expires_in 31536001", 4));
        assertFalse(complete("sfm packet_policy grant inbox " + PLAYER + " minecraft:overworld sfm:fixture forever", 4));
        assertFalse(complete("sfm packet_policy grant inbox " + PLAYER + " minecraft:overworld sfm:fixture", 3));
    }

    @Test void defaultExpiryIsBoundedAndOverflowCannotProduceAnUnboundedGrant() {
        assertEquals(3_601_000, SFMPacketPolicyCommand.expiresAt(1000, SFMPacketPolicyCommand.DEFAULT_EXPIRY_SECONDS));
        assertThrows(IllegalArgumentException.class, () -> SFMPacketPolicyCommand.expiresAt(-1, 30));
        assertThrows(IllegalArgumentException.class, () -> SFMPacketPolicyCommand.expiresAt(0, 0));
        assertThrows(IllegalArgumentException.class, () -> SFMPacketPolicyCommand.expiresAt(0, Long.MAX_VALUE));
        assertThrows(ArithmeticException.class, () -> SFMPacketPolicyCommand.expiresAt(Long.MAX_VALUE, 1));
    }
}
