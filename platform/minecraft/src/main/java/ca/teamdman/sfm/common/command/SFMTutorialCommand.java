package ca.teamdman.sfm.common.command;

import ca.teamdman.sfm.common.localization.SFMTutorialLocalizationKeys;
import ca.teamdman.sfm.common.registry.registration.SFMTutorialTestChambers;
import ca.teamdman.sfm.common.tutorial.chamber.SFMTutorialTestChamberHelper;
import ca.teamdman.sfm.common.tutorial.lobby.LobbyId;
import ca.teamdman.sfm.common.tutorial.lobby.SFMTutorialLobby;
import ca.teamdman.sfm.common.tutorial.lobby.SFMTutorialLobbyManager;
import ca.teamdman.sfm.common.tutorial.SFMTutorialWorld;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public final class SFMTutorialCommand {
    private static final int TUTORIAL_Y = 80;
    private static final int ROOM_INTERIOR_RADIUS = 3;
    private static final int ROOM_WALL_HEIGHT = 4;
    private static final int PLAYER_SEPARATION_DISTANCE = 20_000;

    private SFMTutorialCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("tutorial")
                .requires(source -> source.hasPermission(Commands.LEVEL_ALL))
                .executes(ctx -> createLobby(ctx.getSource()))
                .then(Commands.literal("lobby")
                        .then(Commands.literal("create")
                    .executes(ctx -> createLobby(ctx.getSource())))
                .then(Commands.literal("list")
                    .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .executes(ctx -> listLobbies(ctx.getSource())))
                .then(Commands.literal("chamber")
                    .then(Commands.literal("success")
                        .then(Commands.argument("lobby_id", IntegerArgumentType.integer(1))
                            .executes(ctx -> markChamberSuccess(
                                ctx.getSource(),
                                LobbyId.fromInt(IntegerArgumentType.getInteger(ctx, "lobby_id"))
                            ))))));
    }

    private static int createLobby(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            SFMCommandUtils.sendFailure(source, SFMTutorialLocalizationKeys.COMMAND_TUTORIAL_ONLY_PLAYER::getComponent);
            return 0;
        }

        if (!player.getInventory().isEmpty()) {
            SFMCommandUtils.sendFailure(
                    source,
                SFMTutorialLocalizationKeys.COMMAND_TUTORIAL_REQUIRES_EMPTY_INVENTORY::getComponent
            );
            return 0;
        }

        ServerLevel tutorialLevel = source.getServer().getLevel(SFMTutorialWorld.TUTORIAL_LEVEL_KEY);
        if (tutorialLevel == null) {
            SFMCommandUtils.sendFailure(
                    source,
                SFMTutorialLocalizationKeys.COMMAND_TUTORIAL_DIMENSION_UNAVAILABLE::getComponent
            );
            return 0;
        }

        BlockPos roomCenter = findLobbyCenter(player, tutorialLevel);
        BlockPos chamberOrigin = roomCenter.offset(-ROOM_INTERIOR_RADIUS, 0, -ROOM_INTERIOR_RADIUS);
        SFMTutorialLobby lobby = SFMTutorialLobbyManager.createLobby(
            player,
            roomCenter,
            chamberOrigin,
            SFMTutorialTestChambers.STARTING_CHAMBER_ID
        );

        tutorialLevel.getChunkAt(roomCenter);
        if (!renderChamber(lobby, tutorialLevel, SFMTutorialTestChambers.STARTING_CHAMBER_ID, source)) {
            return 0;
        }

        SFMTutorialWorld.enforceWorldState(tutorialLevel);
        SFMTutorialWorld.applyNightVision(player);

        double teleportX = roomCenter.getX() + 0.5D;
        double teleportY = roomCenter.getY();
        double teleportZ = roomCenter.getZ() + 0.5D;
        player.teleportTo(tutorialLevel, teleportX, teleportY, teleportZ, player.getYRot(), player.getXRot());

        SFMCommandUtils.sendSuccess(
                source,
                () -> SFMTutorialLocalizationKeys.COMMAND_TUTORIAL_LOBBY_CREATED.getComponent(lobby.id().value())
        );
        return SINGLE_SUCCESS;
    }

    private static int listLobbies(CommandSourceStack source) {
        var lobbies = SFMTutorialLobbyManager.getLobbies();
        if (lobbies.isEmpty()) {
            SFMCommandUtils.sendSuccess(source, SFMTutorialLocalizationKeys.COMMAND_TUTORIAL_LOBBY_LIST_EMPTY::getComponent);
            return SINGLE_SUCCESS;
        }

        SFMCommandUtils.sendSuccess(
                source,
                () -> SFMTutorialLocalizationKeys.COMMAND_TUTORIAL_LOBBY_LIST_HEADER.getComponent().withStyle(ChatFormatting.AQUA)
        );

        for (SFMTutorialLobby lobby : lobbies) {
            String playerNames = lobby.playerIds()
                    .stream()
                    .map(uuid -> source.getServer().getPlayerList().getPlayer(uuid))
                    .filter(Objects::nonNull)
                    .map(serverPlayer -> serverPlayer.getName().getString())
                    .collect(Collectors.joining(", "));
            if (playerNames.isEmpty()) {
                playerNames = "(no online players)";
            }

            String finalPlayerNames = playerNames;
            SFMCommandUtils.sendSuccess(
                    source,
                    () -> SFMTutorialLocalizationKeys.COMMAND_TUTORIAL_LOBBY_LIST_ENTRY.getComponent(
                                    Component.literal(String.valueOf(lobby.id().value())).withStyle(ChatFormatting.GOLD),
                                    Component.literal(finalPlayerNames).withStyle(ChatFormatting.YELLOW)
                            )
                            .withStyle(ChatFormatting.GRAY)
            );
        }

        return SINGLE_SUCCESS;
    }

    private static int markChamberSuccess(CommandSourceStack source, LobbyId lobbyId) {
        SFMTutorialLobby lobby = SFMTutorialLobbyManager
                .getLobby(lobbyId)
                .orElse(null);
        if (lobby == null) {
            SFMCommandUtils.sendFailure(
                    source,
                    () -> SFMTutorialLocalizationKeys.COMMAND_TUTORIAL_LOBBY_NOT_FOUND.getComponent(lobbyId.value())
            );
            return 0;
        }

        var currentDefinition = SFMTutorialTestChambers.registry().get(lobby.currentChamberId());
        if (currentDefinition == null) {
            SFMCommandUtils.sendFailure(
                    source,
                    () -> SFMTutorialLocalizationKeys.COMMAND_TUTORIAL_CHAMBER_NOT_FOUND.getComponent(lobby.currentChamberId())
            );
            return 0;
        }

        ResourceLocation nextChamberId = currentDefinition.nextChamberId();
        if (nextChamberId == null) {
            SFMCommandUtils.sendSuccess(
                    source,
                    () -> SFMTutorialLocalizationKeys.COMMAND_TUTORIAL_CHAMBER_COMPLETE.getComponent(lobbyId.value())
            );
            return SINGLE_SUCCESS;
        }

        ServerLevel tutorialLevel = source.getServer().getLevel(SFMTutorialWorld.TUTORIAL_LEVEL_KEY);
        if (tutorialLevel == null) {
            SFMCommandUtils.sendFailure(
                    source,
                    SFMTutorialLocalizationKeys.COMMAND_TUTORIAL_DIMENSION_UNAVAILABLE::getComponent
            );
            return 0;
        }

        if (!renderChamber(lobby, tutorialLevel, nextChamberId, source)) {
            return 0;
        }

        SFMCommandUtils.sendSuccess(
                source,
                () -> SFMTutorialLocalizationKeys.COMMAND_TUTORIAL_CHAMBER_ADVANCED.getComponent(lobbyId.value(), nextChamberId)
        );
        return SINGLE_SUCCESS;
    }

    private static BlockPos findLobbyCenter(ServerPlayer player, ServerLevel tutorialLevel) {
        List<ServerPlayer> otherPlayers = tutorialLevel.players()
                .stream()
                .filter(other -> !other.getUUID().equals(player.getUUID()))
                .toList();

        if (otherPlayers.isEmpty()) {
            return new BlockPos(0, TUTORIAL_Y, 0);
        }

        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (ServerPlayer otherPlayer : otherPlayers) {
            double x = otherPlayer.getX();
            double z = otherPlayer.getZ();
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }

        minX -= PLAYER_SEPARATION_DISTANCE;
        maxX += PLAYER_SEPARATION_DISTANCE;
        minZ -= PLAYER_SEPARATION_DISTANCE;
        maxZ += PLAYER_SEPARATION_DISTANCE;

        return perimeterPoint(minX, maxX, minZ, maxZ);
    }

    private static BlockPos perimeterPoint(double minX, double maxX, double minZ, double maxZ) {
        double width = Math.max(1D, maxX - minX);
        double depth = Math.max(1D, maxZ - minZ);
        double perimeter = 2D * (width + depth);
        double distance = ThreadLocalRandom.current().nextDouble(0D, perimeter);

        double x;
        double z;

        if (distance < width) {
            x = minX + distance;
            z = minZ;
        } else if (distance < width + depth) {
            x = maxX;
            z = minZ + (distance - width);
        } else if (distance < (2D * width) + depth) {
            x = maxX - (distance - width - depth);
            z = maxZ;
        } else {
            x = minX;
            z = maxZ - (distance - (2D * width) - depth);
        }

        return new BlockPos((int) Math.floor(x), TUTORIAL_Y, (int) Math.floor(z));
    }

    private static boolean renderChamber(
            SFMTutorialLobby lobby,
            ServerLevel level,
            ResourceLocation chamberId,
            CommandSourceStack source
    ) {
        var chamber = SFMTutorialTestChambers.registry().get(chamberId);
        if (chamber == null) {
            SFMCommandUtils.sendFailure(
                    source,
                    () -> SFMTutorialLocalizationKeys.COMMAND_TUTORIAL_CHAMBER_NOT_FOUND.getComponent(chamberId)
            );
            return false;
        }

        clearLobbyArea(level, lobby.roomCenter());
        generateLobbyRoom(level, lobby.roomCenter());
        chamber.run(new SFMTutorialTestChamberHelper(level, lobby.chamberOrigin(), lobby.id()));
        lobby.setCurrentChamberId(chamberId);
        return true;
    }

    private static void clearLobbyArea(ServerLevel level, BlockPos center) {
        int minX = center.getX() - (ROOM_INTERIOR_RADIUS + 1);
        int maxX = center.getX() + 20;
        int minZ = center.getZ() - (ROOM_INTERIOR_RADIUS + 2);
        int maxZ = center.getZ() + (ROOM_INTERIOR_RADIUS + 2);
        int minY = center.getY() - 2;
        int maxY = center.getY() + ROOM_WALL_HEIGHT + 1;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static void generateLobbyRoom(ServerLevel level, BlockPos center) {
        int minX = center.getX() - (ROOM_INTERIOR_RADIUS + 1);
        int maxX = center.getX() + (ROOM_INTERIOR_RADIUS + 1);
        int minZ = center.getZ() - (ROOM_INTERIOR_RADIUS + 1);
        int maxZ = center.getZ() + (ROOM_INTERIOR_RADIUS + 1);
        int floorY = center.getY() - 1;
        int ceilingY = floorY + ROOM_WALL_HEIGHT + 1;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = floorY; y <= ceilingY; y++) {
                    boolean boundaryX = x == minX || x == maxX;
                    boolean boundaryZ = z == minZ || z == maxZ;
                    boolean boundaryY = y == floorY || y == ceilingY;
                    boolean shouldPlaceWall = boundaryX || boundaryZ || boundaryY;

                    BlockPos pos = new BlockPos(x, y, z);
                    if (shouldPlaceWall) {
                        level.setBlock(pos, Blocks.WHITE_CONCRETE.defaultBlockState(), 3);
                    } else {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }
}