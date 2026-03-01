package ca.teamdman.sfm.common.command;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.block_network.CableNetworkManager;
import ca.teamdman.sfm.common.block_network.WaterNetworkManager;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.RegisterCommandsEvent;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public final class SFMCacheCommands {
    private SFMCacheCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> bustCableNetworkCache() {
        return Commands.literal("bust_cable_network_cache")
                .requires(source -> source.hasPermission(Commands.LEVEL_ALL))
                .executes(ctx -> {
                    CommandSourceStack source = ctx.getSource();
                    SFM.LOGGER.info(
                            "Busting cable networks - slash command used by {}",
                            source.getTextName()
                    );
                    CableNetworkManager.clear();
                    SFMCommandUtils.sendSuccess(
                            source,
                            LocalizationKeys.COMMAND_BUST_CABLE_NETWORK_CACHE_SUCCESS::getComponent
                    );
                    return SINGLE_SUCCESS;
                });
    }

    public static LiteralArgumentBuilder<CommandSourceStack> bustWaterNetworkCache() {
        return Commands.literal("bust_water_network_cache")
                .requires(source -> source.hasPermission(Commands.LEVEL_ALL))
                .executes(ctx -> {
                    CommandSourceStack source = ctx.getSource();
                    SFM.LOGGER.info(
                            "Busting water networks - slash command used by {}",
                            source.getTextName()
                    );
                    WaterNetworkManager.clear();
                    SFMCommandUtils.sendSuccess(
                            source,
                            LocalizationKeys.COMMAND_BUST_WATER_NETWORK_CACHE_SUCCESS::getComponent
                    );
                    return SINGLE_SUCCESS;
                });
    }

    public static LiteralArgumentBuilder<CommandSourceStack> showBadCableCacheEntries(RegisterCommandsEvent event) {
        return Commands.literal("show_bad_cable_cache_entries")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("block", BlockStateArgument.block(event.getBuildContext()))
                        .executes(ctx -> {
                            ServerLevel level = ctx.getSource().getLevel();
                            CableNetworkManager.getBadCableCachePositions(level).forEach(pos -> {
                                BlockInput block = BlockStateArgument.getBlock(ctx, "block");
                                block.place(level, pos, Block.UPDATE_ALL);
                            });
                            return SINGLE_SUCCESS;
                        }));
    }
}