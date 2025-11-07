package ca.teamdman.sfm.common.command;

import ca.teamdman.sfm.common.net.ClientboundShowChangelogPacket;
import ca.teamdman.sfm.common.registry.SFMPackets;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

public class ChangelogCommand extends CommandBase {
    @Override
    public String getName() {
        return "changelog";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/sfm changelog";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (sender.getCommandSenderEntity() instanceof EntityPlayerMP player) {
            SFMPackets.sendToPlayer(
                    player,
                    new ClientboundShowChangelogPacket()
            );
        }
    }
}
