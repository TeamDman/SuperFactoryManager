package ca.teamdman.sfm.common.handler;

import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.tutorial.SFMTutorialWorld;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;

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
}