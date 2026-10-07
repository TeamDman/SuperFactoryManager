package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.common.blockentity.ClientManagerBlockEntity;
import ca.teamdman.sfm.common.containermenu.ClientManagerContainerMenu;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.registry.registration.SFMMenus;
import ca.teamdman.sfm.common.util.SFMDist;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

/**
 * Client-side menu/projection proof. The menu is inspected without opening a
 * screen so ambient GameTest runs retain control of the game window.
 */
@SFMGameTest(SFMDist.CLIENT)
public final class ClientManagerMenuGameTest extends SFMGameTestDefinition {
    private static final BlockPos MANAGER = new BlockPos(1, 2, 1);
    private static final String SOURCE = "CLIENT BTW\n-- synchronized menu fixture\n";

    @Override
    public String template() { return "3x3x3"; }

    @Override
    public int maxTicks() { return 100; }

    @Override
    public void run(SFMGameTestHelper helper) {
        helper.setBlock(MANAGER, SFMBlocks.CLIENT_MANAGER.get());
        ItemStack disk = new ItemStack(SFMItems.DISK.get());
        DiskItem.setProgram(disk, SOURCE);
        disk.getOrCreateTag().putString("unrelated", "server-only");
        LabelPositionHolder.empty().add("display", helper.absolutePos(BlockPos.ZERO)).save(disk);
        helper.getBlockEntity(MANAGER, ClientManagerBlockEntity.class).setDisk(disk);

        Minecraft minecraft = Minecraft.getInstance();
        Screen screenBefore = minecraft.screen;
        helper.succeedWhen(() -> {
            if (minecraft.level == null || minecraft.player == null) return;
            if (!(minecraft.level.getBlockEntity(helper.absolutePos(MANAGER))
                    instanceof ClientManagerBlockEntity manager)) return;
            if (!SOURCE.equals(manager.storedSource())) return;

            var menu = manager.createMenu(31, minecraft.player.getInventory(), minecraft.player);
            helper.assertTrue(menu instanceof ClientManagerContainerMenu,
                    "Client Manager did not create its dedicated menu");
            ClientManagerContainerMenu clientMenu = (ClientManagerContainerMenu) menu;
            helper.assertTrue(clientMenu.getType() == SFMMenus.CLIENT_MANAGER.get(),
                    "Client Manager menu was not registered under the client_manager type");
            ItemStack synchronizedSlot = clientMenu.CONTAINER.getItem(0);
            helper.assertTrue(synchronizedSlot.getItem() == SFMItems.DISK.get()
                            && SOURCE.equals(synchronizedSlot.getOrCreateTag().getString("sfm:program"))
                            && !synchronizedSlot.getOrCreateTag().contains("unrelated"),
                    "Client Manager menu exposed more than the bounded program projection");
            helper.assertTrue(minecraft.screen == screenBefore,
                    "Ambient Client Manager synchronization opened or replaced a screen");
            clientMenu.removed(minecraft.player);
            helper.succeed();
        });
    }
}
