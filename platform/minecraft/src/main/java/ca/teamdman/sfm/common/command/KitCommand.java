package ca.teamdman.sfm.common.command;

import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;

import java.util.Arrays;
import java.util.List;

public class KitCommand extends CommandBase {
    @Override
    public String getName() {

        return "kit";
    }

    @Override
    public String getUsage(ICommandSender sender) {

        return "kit [player]";
    }

    @Override
    public void execute(
            MinecraftServer server,
            ICommandSender sender,
            String[] args
    ) throws CommandException {

        var player = args.length > 0 ? getPlayer(server, sender,  args[0]) : getCommandSenderAsPlayer(sender);
        giveKitToPlayers(sender, player);
    }

    private void giveKitToPlayers(
            ICommandSender sender,
            EntityPlayerMP target
    ) {

        List<ItemStack> kitItems = Arrays.asList(
                new ItemStack(SFMItems.LABEL_GUN),
                new ItemStack(Item.getItemFromBlock(SFMBlocks.MANAGER_BLOCK)),
                new ItemStack(SFMItems.DISK),
                new ItemStack(SFMItems.NETWORK_TOOL),
                new ItemStack(Item.getItemFromBlock(SFMBlocks.CABLE)),
                new ItemStack(Item.getItemFromBlock(Blocks.CHEST))
        );


        for (ItemStack kitItem : kitItems) {
            ItemStack remaining = kitItem.copy();
            boolean addedToInventory = target.addItemStackToInventory(remaining);
            if (!addedToInventory || !remaining.isEmpty()) {
                var droppedItem = target.dropItem(remaining, false);
                if (droppedItem != null) {
                    droppedItem.setNoPickupDelay();
                    droppedItem.setOwner(target.getName());
                }
            }
        }
        notifyCommandListener(sender, this, "sfm.command.kit.success", target.getDisplayName());

    }

}
