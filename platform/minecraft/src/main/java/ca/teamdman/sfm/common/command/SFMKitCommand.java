package ca.teamdman.sfm.common.command;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Collection;
import java.util.List;

public final class SFMKitCommand {
    private SFMKitCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("kit")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(ctx -> giveKitToPlayers(
                        ctx.getSource(),
                        List.of(ctx.getSource().getPlayerOrException())
                ))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> giveKitToPlayers(
                                ctx.getSource(),
                                EntityArgument.getPlayers(ctx, "targets")
                        )));
    }

    private static int giveKitToPlayers(CommandSourceStack source, Collection<ServerPlayer> targets) {
        List<ItemStack> kitItems = List.of(
                new ItemStack(SFMItems.LABEL_GUN.get()),
                new ItemStack(SFMItems.MANAGER.get()),
                new ItemStack(SFMItems.DISK.get()),
                new ItemStack(SFMItems.NETWORK_TOOL.get()),
                new ItemStack(SFMItems.CABLE.get()),
                new ItemStack(Items.CHEST)
        );

        CommandSourceStack giveSource = source.withPermission(Commands.LEVEL_GAMEMASTERS);
        for (ServerPlayer target : targets) {
            for (ItemStack kitItem : kitItems) {
                var itemId = SFMWellKnownRegistries.ITEMS.getId(kitItem.getItem());
                if (itemId == null) {
                    SFM.LOGGER.warn("Skipping kit item without registry id: {}", kitItem);
                    continue;
                }

                String command = "give " + target.getScoreboardName() + " " + itemId + " " + kitItem.getCount();
                source.getServer().getCommands().performPrefixedCommand(giveSource, command);
            }
        }

        SFMCommandUtils.sendSuccess(source, () -> Component.literal("Gave SFM kit to " + targets.size() + " player(s)."));
        return targets.size();
    }
}