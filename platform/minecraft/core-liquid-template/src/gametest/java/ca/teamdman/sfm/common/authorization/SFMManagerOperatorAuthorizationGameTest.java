package ca.teamdman.sfm.common.authorization;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.net.SFMPacketHandlingContext;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.util.SFMDist;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

import static ca.teamdman.sfm.common.authorization.SFMManagerOperatorAuthorization.Status;

/** Operator authority checks against the actual integrated sender and loaded world. */
@SFMGameTest(SFMDist.CLIENT)
public final class SFMManagerOperatorAuthorizationGameTest extends SFMGameTestDefinition {
    private static final BlockPos MANAGER = new BlockPos(1, 2, 1);
    private static final BlockPos STONE = new BlockPos(2, 2, 1);

    @Override
    public String template() {
        return "3x3x3";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        helper.setBlock(MANAGER, SFMBlocks.MANAGER.get());
        helper.setBlock(STONE, Blocks.STONE);
        BlockPos managerPos = helper.absolutePos(MANAGER);
        BlockPos stonePos = helper.absolutePos(STONE);
        ManagerBlockEntity manager = helper.getBlockEntity(MANAGER, ManagerBlockEntity.class);
        ServerLevel level = helper.getLevel();
        ResourceLocation dimension = level.dimension().location();

        List<ServerPlayer> players = level.getServer().getPlayerList().getPlayers();
        helper.assertTrue(players.size() == 1, "Authorization test requires one integrated player");
        ServerPlayer actualSender = players.get(0);
        helper.assertTrue(actualSender.getLevel() == level
                        && actualSender.hasPermissions(Commands.LEVEL_GAMEMASTERS)
                        && !actualSender.isSpectator(),
                "Integrated player must be an operator in the test dimension");

        var allowed = SFMManagerOperatorAuthorization.authorize(
                contextFor(actualSender), dimension, managerPos);
        helper.assertTrue(allowed.status() == Status.ALLOWED && allowed.manager() == manager,
                "Actual connected operator must resolve the exact manager");
        assertDenied(helper, Status.NO_SENDER,
                SFMManagerOperatorAuthorization.authorize(contextFor(null), dimension, managerPos));

        // A second ServerPlayer can copy the owner's UUID, but it is not the
        // connected object that Forge delivered as the packet sender.
        FakePlayer impersonator = new FakePlayer(level, actualSender.getGameProfile());
        helper.assertTrue(impersonator.getUUID().equals(actualSender.getUUID()),
                "Impersonator must claim the connected player's UUID");
        assertDenied(helper, Status.DISCONNECTED_SENDER,
                SFMManagerOperatorAuthorization.authorize(contextFor(impersonator), dimension, managerPos));

        // Test the target-policy stage with isolated senders. The public entry
        // point above alone admits a connected player from the server player list.
        PolicyPlayer nonOperator = new PolicyPlayer(level, 0, false);
        PolicyPlayer operator = new PolicyPlayer(level, Commands.LEVEL_GAMEMASTERS, false);
        PolicyPlayer spectator = new PolicyPlayer(level, Commands.LEVEL_GAMEMASTERS, true);
        assertDenied(helper, Status.NOT_OPERATOR,
                SFMManagerOperatorAuthorization.authorizeConnectedSender(nonOperator, dimension, managerPos));
        assertDenied(helper, Status.SPECTATOR,
                SFMManagerOperatorAuthorization.authorizeConnectedSender(spectator, dimension, managerPos));
        assertDenied(helper, Status.WRONG_DIMENSION,
                SFMManagerOperatorAuthorization.authorizeConnectedSender(operator,
                        new ResourceLocation("sfm", "different_dimension"), managerPos));

        BlockPos unloadedPos = managerPos.offset(4096, 0, 4096);
        helper.assertTrue(!level.isLoaded(unloadedPos), "Unloaded target fixture must stay unloaded");
        assertDenied(helper, Status.TARGET_UNLOADED,
                SFMManagerOperatorAuthorization.authorizeConnectedSender(operator, dimension, unloadedPos));
        assertDenied(helper, Status.NOT_MANAGER,
                SFMManagerOperatorAuthorization.authorizeConnectedSender(operator, dimension, stonePos));
        helper.assertTrue(SFMManagerOperatorAuthorization.authorizeConnectedSender(
                        operator, dimension, managerPos).manager() == manager,
                "Operator policy must return the exact loaded manager");
        helper.succeed();
    }

    private static SFMPacketHandlingContext contextFor(@Nullable ServerPlayer sender) {
        return new SFMPacketHandlingContext(() -> null) {
            @Override
            public @Nullable ServerPlayer sender() {
                return sender;
            }
        };
    }

    private static void assertDenied(
            SFMGameTestHelper helper,
            Status expected,
            SFMManagerOperatorAuthorization.Decision decision
    ) {
        helper.assertTrue(decision.status() == expected && !decision.allowed() && decision.manager() == null,
                "Expected " + expected + " without a manager, got " + decision.status());
    }

    private static final class PolicyPlayer extends FakePlayer {
        private final int permissionLevel;
        private final boolean spectator;

        PolicyPlayer(ServerLevel level, int permissionLevel, boolean spectator) {
            super(level, new GameProfile(UUID.randomUUID(), "SFMPolicyTest"));
            this.permissionLevel = permissionLevel;
            this.spectator = spectator;
        }

        @Override
        protected int getPermissionLevel() {
            return permissionLevel;
        }

        @Override
        public boolean isSpectator() {
            return spectator;
        }
    }
}
