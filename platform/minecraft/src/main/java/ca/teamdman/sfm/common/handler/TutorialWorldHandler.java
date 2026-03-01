package ca.teamdman.sfm.common.handler;

import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.client.tutorial.SFMTutorialClientContext;
import ca.teamdman.sfm.common.tutorial.SFMTutorialPlayerContext;
import ca.teamdman.sfm.common.tutorial.SFMTutorialWorld;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;

public class TutorialWorldHandler {
    @SFMSubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.level instanceof ServerLevel level)) {
            return;
        }
        if (!SFMTutorialWorld.isTutorialLevel(level)) {
            return;
        }

        SFMTutorialWorld.enforceWorldState(level);
    }

    @SFMSubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (!SFMTutorialWorld.isTutorialLevel(player.getLevel())) {
            return;
        }

        SFMTutorialWorld.applyNightVision(player);
    }

    @SFMSubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SFMTutorialPlayerContext.syncToClient(player);
        }
    }

    @SFMSubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SFMTutorialPlayerContext.syncToClient(player);
        }
    }

    @SFMSubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SFMTutorialPlayerContext.syncToClient(player);
        }
    }

    @SFMSubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            SFMTutorialClientContext.clear();
        }
    }
}