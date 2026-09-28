package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.net.SFMPacketHandlingContext;
import ca.teamdman.sfm.common.net.ServerboundManagerProgramPacket;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;

import java.util.UUID;

/** A manager menu authorizes only its own loaded target, and only while still valid. */
@SFMGameTest
public final class ManagerProgramTargetAuthorizationGameTest extends SFMGameTestDefinition {
    private static final BlockPos OPEN_MANAGER = new BlockPos(1, 2, 1);
    private static final BlockPos OTHER_MANAGER = new BlockPos(2, 2, 1);
    private static final String OPEN_PROGRAM = "NAME \"open\"\nEVERY 20 TICKS DO END";
    private static final String OTHER_PROGRAM = "NAME \"other\"\nEVERY 20 TICKS DO END";
    private static final String FORGED_PROGRAM = "NAME \"forged\"\nEVERY 20 TICKS DO END";
    private static final String ALLOWED_PROGRAM = "NAME \"allowed\"\nEVERY 20 TICKS DO END";
    private static final String STALE_PROGRAM = "NAME \"stale\"\nEVERY 20 TICKS DO END";

    @Override
    public String template() {
        return "3x3x3";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        helper.setBlock(OPEN_MANAGER, SFMBlocks.MANAGER.get());
        helper.setBlock(OTHER_MANAGER, SFMBlocks.MANAGER.get());
        ManagerBlockEntity openManager = helper.getBlockEntity(OPEN_MANAGER, ManagerBlockEntity.class);
        ManagerBlockEntity otherManager = helper.getBlockEntity(OTHER_MANAGER, ManagerBlockEntity.class);
        openManager.setItem(0, new ItemStack(SFMItems.DISK.get()));
        otherManager.setItem(0, new ItemStack(SFMItems.DISK.get()));
        openManager.setProgram(OPEN_PROGRAM);
        otherManager.setProgram(OTHER_PROGRAM);

        BlockPos openPosition = helper.absolutePos(OPEN_MANAGER);
        BlockPos otherPosition = helper.absolutePos(OTHER_MANAGER);
        helper.assertTrue(helper.getLevel().isLoaded(otherPosition)
                        && helper.getLevel().getBlockEntity(otherPosition) == otherManager,
                "The forged target must be a second loaded manager");
        FakePlayer player = new FakePlayer(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "SFMAuthTest"));
        player.setPos(openPosition.getX() + 0.5, openPosition.getY() + 1.0,
                openPosition.getZ() + 0.5);
        helper.assertTrue(!player.isSpectator(), "Test sender must be eligible to edit a manager");
        AbstractContainerMenu originalMenu = player.containerMenu;
        ManagerContainerMenu menu = new ManagerContainerMenu(73, player.getInventory(), openManager);
        player.containerMenu = menu;
        // The packet handler needs only its sender from this context. The fake
        // player has no client connection, so this tests server authority after dispatch.
        SFMPacketHandlingContext context = new SFMPacketHandlingContext(() -> null) {
            @Override
            public ServerPlayer sender() {
                return player;
            }
        };
        ServerboundManagerProgramPacket.Daddy handler = new ServerboundManagerProgramPacket.Daddy();
        try {
            helper.assertTrue(menu.MANAGER_POSITION.equals(openPosition) && menu.stillValid(player),
                    "Fake player must have a valid menu for the first manager");
            handler.handle(new ServerboundManagerProgramPacket(
                    menu.containerId, otherPosition, FORGED_PROGRAM), context);
            helper.assertTrue(OPEN_PROGRAM.equals(openManager.getProgramStringOrEmptyIfNull()),
                    "Forged packet changed the open manager's program");
            helper.assertTrue(OTHER_PROGRAM.equals(otherManager.getProgramStringOrEmptyIfNull()),
                    "Forged packet changed the second manager's program");

            handler.handle(new ServerboundManagerProgramPacket(
                    menu.containerId, openPosition, ALLOWED_PROGRAM), context);
            helper.assertTrue(ALLOWED_PROGRAM.equals(openManager.getProgramStringOrEmptyIfNull()),
                    "Authorized packet did not change the open manager's program");
            helper.assertTrue(OTHER_PROGRAM.equals(otherManager.getProgramStringOrEmptyIfNull()),
                    "Authorized packet changed the second manager's program");

            player.setPos(openPosition.getX() + 32.5, openPosition.getY() + 1.0,
                    openPosition.getZ() + 0.5);
            helper.assertTrue(!menu.stillValid(player), "Menu must be stale when the sender is far away");
            handler.handle(new ServerboundManagerProgramPacket(
                    menu.containerId, openPosition, STALE_PROGRAM), context);
            helper.assertTrue(ALLOWED_PROGRAM.equals(openManager.getProgramStringOrEmptyIfNull()),
                    "Stale menu packet changed the open manager's program");
        } finally {
            player.containerMenu = originalMenu;
        }
        helper.succeed();
    }
}
